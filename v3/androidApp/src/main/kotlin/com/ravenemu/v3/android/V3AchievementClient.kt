// Port of V2 RA client transport, keeping native rcheevos as sole award authority.
package com.ravenemu.v3.android

import com.ravenemu.v3.shared.V3RetroSnapshot
import com.ravenemu.v3.shared.V3AchievementEntry

import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/** Owned by the session thread (or the account dialog worker). Never by an HTTP worker. */
internal class V3AchievementClient(
    private val readMemory: ((Int, Int) -> ByteArray)? = null,
    private val onEvent: (Event) -> Unit,
) : AutoCloseable {
    data class Event(val kind: String, val title: String, val detail: String, val extra: String, val image: String)
    private data class Response(val id: Int, val body: ByteArray, val status: Int)
    private val handle = NativeAchievementsBridge.create().also { check(it != 0L) }
    private val closed = AtomicBoolean(false)
    private val responses = ConcurrentLinkedQueue<Response>()
    private val http = Executors.newFixedThreadPool(2) { Thread(it, "RavenEmu-RA-HTTP").apply { isDaemon = true } }
    private val memory = readMemory?.let { reader ->
        NativeAchievementsBridge.Memory { address, count -> reader(address, count) }
    }

    private var status = "Connexion en cours…"
    fun snapshot(): V3RetroSnapshot {
        val summary = NativeAchievementsBridge.summary(handle).toList()
        val achievements = NativeAchievementsBridge.achievements(handle).toList()
            .chunked(9).filter { it.size == 9 }.mapNotNull { a ->
                runCatching {
                    V3AchievementEntry(
                        id = a[0], title = a[1], description = a[2],
                        points = a[3].toInt(), state = a[4].toInt(),
                        progress = a[5], unlockedAt = a[6].toLong(),
                        bucket = a[7].toInt(), badgeUrl = a[8],
                    )
                }.getOrNull()
            }
        return V3RetroSnapshot(
            status = status,
            displayName = summary.getOrElse(0) { "" },
            softcorePoints = summary.getOrElse(1) { "0" }.toIntOrNull() ?: 0,
            hardcorePoints = summary.getOrElse(2) { "0" }.toIntOrNull() ?: 0,
            gameTitle = summary.getOrElse(3) { "" },
            gameId = summary.getOrElse(4) { "0" }.toIntOrNull() ?: 0,
            unlockedCount = summary.getOrElse(5) { "0" }.toIntOrNull() ?: 0,
            totalCount = summary.getOrElse(6) { "0" }.toIntOrNull() ?: 0,
            unlockedPoints = summary.getOrElse(7) { "0" }.toIntOrNull() ?: 0,
            totalPoints = summary.getOrElse(8) { "0" }.toIntOrNull() ?: 0,
            username = summary.getOrElse(9) { "" },
            avatarUrl = summary.getOrElse(10) { "" },
            entries = achievements,
        )
    }

    fun login(username: String, secret: String, token: Boolean) = NativeAchievementsBridge.login(handle, username, secret, token)
    fun load(console: Int, rom: ByteArray) = NativeAchievementsBridge.load(handle, console, rom)
    fun reset() = NativeAchievementsBridge.reset(handle)

    fun tick(frame: Boolean) {
        if (closed.get()) return
        while (true) {
            val response = responses.poll() ?: break
            NativeAchievementsBridge.respond(handle, response.id, response.body, response.status, memory)
        }
        NativeAchievementsBridge.tick(handle, memory, frame)
        NativeAchievementsBridge.events(handle).asList().chunked(5).forEach {
            when (it[0]) {
                "login" -> status = "Chargement des succès…"
                "game", "online" -> status = "Connecté • Mode normal (Softcore)"
                "login_error", "game_error", "offline" -> status = it[1]
            }
            onEvent(Event(it[0], it[1], it[2], it[3], it[4]))
        }
        NativeAchievementsBridge.requests(handle).asList().chunked(4).forEach { record ->
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
            connection.setRequestProperty("User-Agent", "RavenEmu/V3-Preview (Android; rcheevos/12.5.0)")
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
        NativeAchievementsBridge.destroy(handle)
    }
}
