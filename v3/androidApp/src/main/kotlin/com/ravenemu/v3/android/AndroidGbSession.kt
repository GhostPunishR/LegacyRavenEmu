package com.ravenemu.v3.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.ravenemu.v3.shared.GamePlatform
import com.ravenemu.v3.shared.GbButton
import com.ravenemu.v3.shared.LoadedGameInfo
import com.ravenemu.v3.shared.RomImportPolicy
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File

/**
 * Owns one GB emulation core for the current Activity.
 * ROMs are selected on demand; battery-backed game saves survive process restarts.
 * Native state is always protected by lock, while disk writes use saveLock.
 */
class AndroidGbSession(private val resolver: ContentResolver, filesDir: File) : Closeable {
    private val lock = Any()
    private val saveLock = Any()
    private val batteryStore = GbBatteryStore(File(filesDir, "gb-battery"))
    private var closed = false
    private var active: NativeCore? = null
    private var activeRomKey: String? = null
    private val heldButtons = mutableSetOf<GbButton>()

    /** Must be called off the UI thread. */
    fun load(uri: Uri): LoadedGameInfo {
        val filename = displayName(uri)
        require(RomImportPolicy.acceptsGameBoyFile(filename)) {
            "Pour cette étape, sélectionnez une ROM Game Boy au format .gb."
        }
        val bytes = readBounded(uri)
        val romKey = GbBatteryStore.keyForRom(bytes)
        val replacement = NativeCore(NativeCore.GB)
        var transferred = false
        try {
            return synchronized(saveLock) {
                // Flush the previous game BEFORE reading this ROM's save: a
                // re-import of the same ROM must not load a stale snapshot.
                saveBatteryIfDirtyLocked()
                val savedBattery = batteryStore.read(romKey)
                replacement.load(bytes, savedBattery)
                val result = LoadedGameInfo(
                    filename = filename,
                    platform = GamePlatform.GB,
                    width = replacement.width,
                    height = replacement.height,
                )
                synchronized(lock) {
                    check(!closed) { "La session a été fermée." }
                    val previous = active
                    heldButtons.clear()
                    active = replacement
                    activeRomKey = romKey
                    transferred = true
                    previous?.close()
                }
                result
            }
        } finally {
            if (!transferred) replacement.close()
        }
    }

    /**
     * Commit changed battery RAM using AtomicFile, then acknowledge exactly
     * the generation persisted. No disk IO runs while holding the native lock.
     * Safe to call from a periodic IO worker, ROM replacement, or lifecycle.
     */
    fun saveBatteryIfDirty(): Boolean = synchronized(saveLock) {
        saveBatteryIfDirtyLocked()
    }

    private fun saveBatteryIfDirtyLocked(): Boolean {
        val pending = synchronized(lock) {
            if (closed) return@synchronized null
            val core = active ?: return@synchronized null
            val key = activeRomKey ?: return@synchronized null
            if (!core.batteryDirty()) return@synchronized null
            val snapshot = core.batterySnapshotWithGeneration() ?: return@synchronized null
            Triple(core, key, snapshot)
        } ?: return false

        val (core, key, snapshot) = pending
        batteryStore.write(key, snapshot.bytes)
        synchronized(lock) {
            if (!closed && active === core) {
                core.acknowledgeBattery(snapshot.generation)
            }
        }
        Log.d(TAG, "Battery saved for ROM ${key.take(12)}")
        return true
    }

    /**
     * Advance exactly one frame, then drain interleaved stereo PCM16 from the
     * same native handle under one lock. No second emulation clock is started.
     * Returns null if no ROM is active.
     */
    fun nextFrameWithAudioInto(pixels: IntArray, audio: ShortArray): Int? =
        synchronized(lock) {
            check(!closed) { "La session a été fermée." }
            val core = active ?: return@synchronized null
            core.frameInto(pixels)
            core.readAudioInto(audio)
        }

    fun refreshRateHz(): Double? = synchronized(lock) {
        if (closed) null else active?.fps
    }

    fun audioRateHz(): Int? = synchronized(lock) {
        if (closed) null else active?.audioRate
    }

    /**
     * Input is ordered on the UI thread and serialized with native frames.
     * The bridge requires all operations on one native handle to be serialized.
     */
    fun setButton(button: GbButton, pressed: Boolean): Boolean = synchronized(lock) {
        if (closed) return@synchronized false
        val core = active ?: return@synchronized false
        if ((button in heldButtons) == pressed) return@synchronized true
        core.button(button.nativeCode, pressed)
        if (pressed) heldButtons.add(button) else heldButtons.remove(button)
        true
    }

    /** Prevent stuck buttons after screen exit, app pause or cancelled touches. */
    fun releaseAllButtons() {
        synchronized(lock) {
            if (!closed) {
                val core = active
                for (button in heldButtons) core?.button(button.nativeCode, false)
            }
            heldButtons.clear()
        }
    }

    private fun displayName(uri: Uri): String {
        val name = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
        return name?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Impossible de lire le nom du fichier.")
    }

    private fun readBounded(uri: Uri): ByteArray {
        val stream = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Impossible d'ouvrir cette ROM.")
        return stream.use { input ->
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                if (read == 0) continue
                require(read <= RomImportPolicy.maxRomBytes - total) {
                    "Cette ROM dépasse la limite de 16 Mio."
                }
                output.write(chunk, 0, read)
                total += read
            }
            require(total > 0) { "Le fichier est vide." }
            output.toByteArray()
        }
    }

    override fun close() {
        synchronized(saveLock) {
            try {
                saveBatteryIfDirtyLocked()
            } catch (error: Exception) {
                Log.e(TAG, "Failed to persist battery on session close", error)
            } finally {
                synchronized(lock) {
                    closed = true
                    heldButtons.clear()
                    active?.close()
                    active = null
                    activeRomKey = null
                }
            }
        }
    }

    private companion object {
        const val TAG = "RavenEmuSaves"
    }
}
