package com.ravenemu.v3.shared

/** Game Boy and Game Boy Color import; GBA is not enabled yet. */
object RomImportPolicy {
    const val maxRomBytes: Int = 16 * 1024 * 1024

    fun acceptsGameBoyFile(filename: String): Boolean {
        val name = filename.substringAfterLast('/').trim()
        return name.endsWith(".gb", ignoreCase = true) ||
            name.endsWith(".gbc", ignoreCase = true)
    }

    /**
     * The cartridge decides, not the file extension. Header byte 0x143 is
     * 0x80 for a color game that also runs on a Game Boy and 0xC0 for a Game
     * Boy Color exclusive: both run on RavenCore's GBC core, in color, and
     * RetroAchievements lists them under Game Boy Color. Any other value is
     * a monochrome cartridge for the GB core. Same rule as RavenCore's header.
     */
    fun platformOf(rom: ByteArray): GamePlatform {
        val flag = if (rom.size > CGB_FLAG) rom[CGB_FLAG].toInt() and 0xFF else 0
        return if (flag == 0x80 || flag == 0xC0) GamePlatform.GBC else GamePlatform.GB
    }

    private const val CGB_FLAG = 0x143
}

data class LoadedGameInfo(
    val filename: String,
    val platform: GamePlatform,
    val width: Int,
    val height: Int,
)
