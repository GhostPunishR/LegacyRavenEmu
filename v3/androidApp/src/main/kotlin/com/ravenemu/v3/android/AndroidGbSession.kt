package com.ravenemu.v3.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.ravenemu.v3.shared.GamePlatform
import com.ravenemu.v3.shared.GbButton
import com.ravenemu.v3.shared.LoadedGameInfo
import com.ravenemu.v3.shared.RomImportPolicy
import java.io.ByteArrayOutputStream
import java.io.Closeable

/**
 * Owns one GB emulation core for the current Activity.
 * The selected file is loaded into memory only, not added to a persistent library.
 */
class AndroidGbSession(private val resolver: ContentResolver) : Closeable {
    private val lock = Any()
    private var closed = false
    private var active: NativeCore? = null
    private val heldButtons = mutableSetOf<GbButton>()

    /** Must be called off the UI thread. */
    fun load(uri: Uri): LoadedGameInfo {
        val filename = displayName(uri)
        require(RomImportPolicy.acceptsGameBoyFile(filename)) {
            "Pour cette étape, sélectionnez une ROM Game Boy au format .gb."
        }
        val bytes = readBounded(uri)
        val replacement = NativeCore(NativeCore.GB)
        var transferred = false
        try {
            replacement.load(bytes)
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
                transferred = true
                previous?.close()
            }
            return result
        } finally {
            if (!transferred) replacement.close()
        }
    }

    /**
     * Writes exactly one native frame into a reusable caller-owned buffer.
     * Serialize against ROM replacement, import and Activity shutdown.
     */
    fun nextFrameInto(output: IntArray): Boolean = synchronized(lock) {
        check(!closed) { "La session a été fermée." }
        val core = active ?: return@synchronized false
        core.frameInto(output)
        true
    }

    fun refreshRateHz(): Double? = synchronized(lock) {
        if (closed) null else active?.fps
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
        synchronized(lock) {
            closed = true
            heldButtons.clear()
            active?.close()
            active = null
        }
    }
}
