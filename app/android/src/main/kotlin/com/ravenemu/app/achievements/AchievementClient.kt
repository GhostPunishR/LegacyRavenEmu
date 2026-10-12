package com.ravenemu.app.achievements

import android.content.Context
import android.os.Build
import com.ravenemu.app.BuildConfig
import org.json.JSONObject
import com.ravenemu.emulation.api.EmulatorCore
import com.ravenemu.nativebridge.NativeAchievementsBridge as Native
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/** Owned by the session thread (or the account dialog worker). Never by an HTTP worker. */
internal class AchievementClient(
    context: Context,
    core: EmulatorCore? = null,
    private val coreName: String = "raven-core",
    private val onEvent: (Event) -> Unit,
) : AutoCloseable {
    data class Event(val kind: String, val title: String, val detail: String, val extra: String, val image: String)
    private data class Response(val id: Int, val body: ByteArray, val status: Int)
    private val handle = Native.create().also { check(it != 0L) }
    private val closed = AtomicBoolean(false)
    private val responses = ConcurrentLinkedQueue<Response>()
    private val offlineAwards = if (core != null) AchievementOfflineQueue(context.applicationContext) else null
    private var username: String? = null
    private var warnedAboutOfflineStorage = false
    private val http = Executors.newFixedThreadPool(2) { Thread(it, "RavenEmu-RA-HTTP").apply { isDaemon = true } }
    private val replayWorker = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "RavenEmu-RA-Replay").apply { isDaemon = true }
    }
    private var replayStarted = false
    private val memory = core?.let { target ->
        Native.Memory { address, count ->
            val bytes = ByteArray(count)
            val read = target.readAchievementMemory(address, bytes)
            if (read == count) bytes else bytes.copyOf(read)
        }
    }

    private var status = "Connexion en cours…"
    @Volatile var hardcoreEnabled: Boolean = false
        private set
    fun canPause(): Boolean = !hardcoreEnabled || Native.canPause(handle)
    fun snapshot(): AchievementSnapshot = AchievementSnapshot(
        status, Native.summary(handle).toList(), Native.achievements(handle).asList().chunked(9).map {
            AchievementEntry(it[0], it[1], it[2], it[3], it[4].toInt(), it[5], it[6].toLong(), it[7].toInt(), it[8])
        }, Native.richPresence(handle),
    )

    fun login(username: String, secret: String, token: Boolean) {
        this.username = username
        Native.login(handle, username, secret, token)
    }
    fun load(console: Int, rom: ByteArray) = Native.load(handle, console, rom)
    fun reset() = Native.reset(handle)

    fun tick(frame: Boolean) {
        if (closed.get()) return
        while (true) {
            val response = responses.poll() ?: break
            Native.respond(handle, response.id, response.body, response.status, memory)
        }
        Native.tick(handle, memory, frame)
        hardcoreEnabled = Native.hardcore(handle)
        Native.events(handle).asList().chunked(5).forEach {
            when (it[0]) {
                "login" -> {
                    status = "Chargement des succès…"
                    resendStoredAwards(it[1], it[3])
                }
                "game", "online" -> status = "Connecté • Mode normal (Softcore)"
                "login_error", "game_error", "offline" -> status = it[1]
            }
            onEvent(Event(it[0], it[1], it[2], it[3], it[4]))
        }
        Native.requests(handle).asList().chunked(4).forEach { record ->
            // Journal BEFORE dispatch: process death during a request must not
            // lose a genuine SDK-generated unlock. The SDK remains its owner.
            val account = username
            val stored = if (account != null && AchievementAwardPolicy.isSdkAward(record[1], record[2])) {
                AchievementOfflineQueue.Award(account, record[1], record[2], record[3])
            } else null
            if (stored != null) {
                val persisted = runCatching {
                    offlineAwards?.remember(stored.account, stored.url, stored.post, stored.contentType) == true
                }.getOrDefault(false)
                if (!persisted && !warnedAboutOfflineStorage) {
                    warnedAboutOfflineStorage = true
                    onEvent(Event("notice", "Succès hors ligne",
                        "Le stockage sécurisé est indisponible ou plein : gardez le jeu ouvert jusqu'à la synchronisation.",
                        "", ""))
                }
            }
            http.execute {
                val response = request(record)
                if (stored != null && awardAccepted(response)) {
                    runCatching { offlineAwards?.acknowledge(stored) }
                }
                if (!closed.get()) responses.add(response)
            }
        }
    }

    /** Replay only previously captured SDK awards, after the same account logged in. */
    private fun resendStoredAwards(account: String, token: String) {
        val queue = offlineAwards ?: return
        if (replayStarted) return
        replayStarted = true
        // Capture before loading this session's game, so the SDK and replay
        // worker never both own retries for newly generated awards.
        val pending = runCatching { queue.forAccount(account) }.getOrDefault(emptyList())
        val replay = AwardReplay(pending)
        replayWorker.execute(object : Runnable {
            override fun run() {
                val delay = replay.attempt({ !closed.get() && queue.isCurrent() }) { entry ->
                    val post = AchievementAwardPolicy.authenticatedReplay(entry.url, entry.post, account, token)
                    if (post == null) false
                    else {
                        val result = send(entry.url, post, entry.contentType)
                        if (awardAccepted(result)) runCatching { queue.acknowledge(entry); true }.getOrDefault(false)
                        else false
                    }
                }
                if (delay != null && !closed.get() && queue.isCurrent()) {
                    // Shutdown can race this scheduling; it must not escape the worker.
                    runCatching { replayWorker.schedule(this, delay, TimeUnit.MILLISECONDS) }
                }
            }
        })
    }

    private fun awardAccepted(response: Response): Boolean {
        if (response.status !in 200..299) return false
        return runCatching {
            JSONObject(String(response.body, Charsets.UTF_8)).optBoolean("Success", false)
        }.getOrDefault(false)
    }

    private fun request(record: List<String>): Response =
        send(record[1], record[2], record[3]).copy(id = record[0].toInt())

    private fun send(address: String, post: String, contentType: String): Response {
        var connection: HttpsURLConnection? = null
        return try {
            val url = URL(address)
            require(url.protocol == "https" && url.host == "retroachievements.org" && url.port in listOf(-1, 443))
            connection = url.openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false // Never forward credentials to another host.
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent",
                "RavenEmu/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}) ${coreName}/${BuildConfig.VERSION_NAME}")
            if (post.isNotEmpty()) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(post.toByteArray(Charsets.UTF_8)) }
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
            Response(0, body, status)
        } catch (_: Exception) {
            // The SDK retries transient unlock failures. No credentials or request URLs in logs.
            Response(0, ByteArray(0), -2)
        } finally {
            connection?.disconnect()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        replayWorker.shutdownNow()
        http.shutdownNow()
        responses.clear()
        Native.destroy(handle)
    }
}

