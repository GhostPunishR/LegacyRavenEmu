package com.ravenemu.v3.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ravenemu.v3.shared.V3RetroSnapshot
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Owns the official SDK on one dedicated thread (V2's ownership rule).
 * HTTP never calls the native client directly. Memory reads only observe the
 * active native GB core under AndroidGbSession's existing lock.
 */
internal class V3AchievementsController(
    context: Context,
    private val session: AndroidGbSession,
    private val onSnapshot: (V3RetroSnapshot) -> Unit,
    private val onEvent: (V3AchievementClient.Event) -> Unit,
) {
    private val account = V3AchievementAccount(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "RavenEmu-V3-RetroAchievements").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var client: V3AchievementClient? = null // dedicated SDK thread only
    private var currentRom: ByteArray? = null
    private var connected = false
    private var currentUser = ""
    private var lastSnapshotMs = 0L
    private var stopped = false

    /** Persisted token is used only after a prior, explicit login. */
    suspend fun restore() = withContext(dispatcher) {
        if (stopped) return@withContext
        account.read()?.let { start(it.username, it.token, true) }
    }

    suspend fun login(username: String, password: String) = withContext(dispatcher) {
        require(username.isNotBlank() && password.isNotEmpty()) {
            "Saisissez votre nom d'utilisateur et mot de passe."
        }
        if (stopped) return@withContext
        start(username.trim(), password, false)
    }

    suspend fun attachRom(rom: ByteArray) = withContext(dispatcher) {
        if (stopped) return@withContext
        val id = GbBatteryStore.keyForRom(rom)
        val currentId = currentRom?.let(GbBatteryStore::keyForRom)
        if (id == currentId) return@withContext
        currentRom = rom.copyOf()
        // New ROM means a new SDK game session, including pending award
        // requests. Never let the old game's award be associated with a
        // different cartridge.
        val credentials = account.read()
        if (credentials != null) start(credentials.username, credentials.token, true)
        else if (client != null) {
            client?.close()
            client = null
            connected = false
            publish(V3RetroSnapshot(status = "Non connecté"))
        }
    }

    suspend fun frame() = withContext(dispatcher) {
        if (!stopped) poll(frame = true)
    }

    suspend fun idle() = withContext(dispatcher) {
        if (!stopped) poll(frame = false)
    }

    suspend fun resetProgress() = withContext(dispatcher) {
        if (!stopped) client?.reset()
    }

    suspend fun disconnect() = withContext(dispatcher) {
        account.clear()
        client?.close()
        client = null
        connected = false
        currentUser = ""
        publish(V3RetroSnapshot(status = "Non connecté"))
    }

    private fun start(username: String, secret: String, token: Boolean) {
        client?.close()
        connected = false
        currentUser = username
        val fresh = V3AchievementClient(
            readMemory = { address, count -> session.readAchievementMemory(address, count) },
            onEvent = { event -> handleEvent(event) },
        )
        client = fresh
        fresh.login(username, secret, token)
        publish(V3RetroSnapshot(status = "Connexion en cours…", busy = true))
        // Never retain the plaintext password; it is needed only for this
        // immediate native SDK call. The account saves only the returned token.
    }

    private fun handleEvent(event: V3AchievementClient.Event) {
        if (event.kind == "login") {
            val name = event.title.ifBlank { currentUser }
            val token = event.extra
            if (token.isNotEmpty()) {
                account.save(name, token, event.detail.ifBlank { name }, event.image)
            }
            connected = true
            currentRom?.let { client?.load(NativeCore.GB, it) }
        }
        if (event.kind == "login_error") {
            connected = false
        }
        val snapshot = client?.snapshot() ?: V3RetroSnapshot()
        publish(snapshot.copy(error = if (event.kind.endsWith("_error")) event.title else null))
        main.post { onEvent(event) }
    }

    private fun poll(frame: Boolean) {
        val c = client ?: return
        c.tick(frame && connected && currentRom != null)
        val now = System.nanoTime() / 1_000_000L
        if (now - lastSnapshotMs >= 1200L) {
            lastSnapshotMs = now
            publish(c.snapshot())
        }
    }

    private fun publish(snapshot: V3RetroSnapshot) {
        main.post { if (!stopped) onSnapshot(snapshot) }
    }

    fun close() {
        if (stopped) return
        stopped = true
        try {
            executor.submit {
                client?.close()
                client = null
                currentRom = null
            }.get(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // Teardown errors must not crash or block app shutdown.
        } finally {
            dispatcher.close()
        }
    }
}
