package com.ravenemu.v3.android

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/**
 * One explicit save-state slot per ROM, independent from in-game battery saves.
 * The core validates the embedded ROM hash and state format during restoration.
 */
internal class GbSaveStateStore(private val directory: File) {
    fun read(romKey: String): ByteArray? {
        // AtomicFile.openRead can recover its backup after an interrupted
        // write, even when the primary file is missing. Do not pre-check it.
        return try {
            AtomicFile(fileFor(romKey)).openRead().use { stream ->
                val bytes = stream.readBytes()
                require(bytes.size in 1..MAX_STATE_BYTES) {
                    "État de sauvegarde invalide ou trop volumineux."
                }
                bytes
            }
        } catch (_: FileNotFoundException) {
            null
        }
    }

    fun write(romKey: String, bytes: ByteArray) {
        require(bytes.size in 1..MAX_STATE_BYTES) {
            "État de sauvegarde invalide ou trop volumineux."
        }
        check(directory.isDirectory || directory.mkdirs()) {
            "Impossible de créer le dossier des save states."
        }
        val atomic = AtomicFile(fileFor(romKey))
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    internal fun fileFor(romKey: String): File {
        require(romKey.length == 64 &&
            romKey.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Identifiant de ROM invalide."
        }
        return File(directory, "$romKey.slot0.state")
    }

    companion object {
        private const val MAX_STATE_BYTES = 4 * 1024 * 1024
    }
}
