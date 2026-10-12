package com.ravenemu.v3.shared

/** Only Game Boy (DMG) import is enabled in the first playable V3 milestone. */
object RomImportPolicy {
    const val maxRomBytes: Int = 16 * 1024 * 1024

    fun acceptsGameBoyFile(filename: String): Boolean =
        filename.substringAfterLast('/').trim().endsWith(".gb", ignoreCase = true)
}

data class LoadedGameInfo(
    val filename: String,
    val platform: GamePlatform,
    val width: Int,
    val height: Int,
)
