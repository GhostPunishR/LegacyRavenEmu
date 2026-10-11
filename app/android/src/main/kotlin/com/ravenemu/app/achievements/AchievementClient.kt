package com.ravenemu.app.achievements

import com.ravenemu.app.BuildConfig
import com.ravenemu.emulation.api.EmulatorCore
import com.ravenemu.nativebridge.NativeAchievementsBridge as Native
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/** Owned by the session thread (or the account dialog worker). Never by an HTTP worker. */
internal class AchievementClient(
    core: EmulatorCore? = null,
    private val onEvent: (Event) -> Unit,
) : AutoCloseable {
    data class Event(val kind: String, val title: String, val detail: String, val extra: String, val image: String)
    private data class Response(val id: Int, val body: ByteArray, val status: Int)
    private val handle = Native.create().also { check(it != 0L) }
    private val closed = AtomicBoolean(false)
    private val responses = ConcurrentLinkedQueue<Response>()
    private val http = Executors.newFixedThreadPool(2) { Thread(it, "RavenEmu-RA-HTTP").apply { isDaemon = true } }
    private val memory = core?.let { target ->
        Native.Memory { address, count ->
            val bytes = ByteArray(count)
            val read = target.readAchievementMemory(address, bytes)
            if (read == count) bytes else bytes.copyOf(read)
        }
    }

    private var status = "Connexion en cours…"
    fun snapshot(): AchievementSnapshot = AchievementSnapshot(
        status, Native.summary(handle).toList(), Native.achievements(handle).asList().chunked(9).map {
            AchievementEntry(it[0], it[1], it[2], it[3], it[4].toInt(), it[5], it[6].toLong(), it[7].toInt(), it[8])
        },
    )

    fun login(username: String, secret: String, token: Boolean) = Native.login(handle, username, secret, token)
    fun load(console: Int, rom: ByteArray) = Native.load(handle, console, rom)
    fun reset() = Native.reset(handle)

    fun tick(frame: Boolean) {
        if (closed.get()) return
        while (true) {
            val response = responses.poll() ?: break
            Native.respond(handle, response.id, response.body, response.status, memory)
        }
        Native.tick(handle, memory, frame)
        Native.events(handle).asList().chunked(5).forEach {
            when (it[0]) {
                "login" -> status = "Chargement des succès…"
                "game", "online" -> status = "Connecté • Mode normal (Softcore)"
                "login_error", "game_error", "offline" -> status = it[1]
            }
            onEvent(Event(it[0], it[1], it[2], it[3], it[4]))
        }
        Native.requests(handle).asList().chunked(4).forEach { record ->
            http.execute {
                val response = request(record)
                if (!closed.get()) responses.add(response)
            }
        }
    }

    private fun request(record: List<String>): Response {
        var connection: HttpsURLConnection? = null
        return try {
            val url = URL(record[1])
            require(url.protocol == "https" && url.host == "retroachievements.org" && url.port in listOf(-1, 443))
            connection = url.openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false // Never forward credentials to another host.
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "RavenEmu/${BuildConfig.VERSION_NAME} (Android; rcheevos/12.5.0)")
            if (record[2].isNotEmpty()) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", record[3])
                connection.outputStream.use { it.write(record[2].toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    check(output.size() + read <= 8 * 1024 * 1024)
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: ByteArray(0)
            Response(record[0].toInt(), body, status)
        } catch (_: Exception) {
            // The SDK retries transient unlock failures. No credentials or request URLs in logs.
            Response(record[0].toInt(), ByteArray(0), -2)
        } finally {
            connection?.disconnect()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        http.shutdownNow()
        responses.clear()
        Native.destroy(handle)
    }
}
