package com.ravenemu.v3.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RomImportPolicyTest {
    @Test
    fun acceptsCaseInsensitiveGameBoyAndColorExtensions() {
        assertTrue(RomImportPolicy.acceptsGameBoyFile("Tetris.gb"))
        assertTrue(RomImportPolicy.acceptsGameBoyFile("TETRIS.GB"))
        assertTrue(RomImportPolicy.acceptsGameBoyFile("Tetris DX.gbc"))
        assertTrue(RomImportPolicy.acceptsGameBoyFile("ZELDA.GBC"))
    }

    @Test
    fun otherSystemsAndUnknownFilesAreNotAccidentallyLoaded() {
        assertFalse(RomImportPolicy.acceptsGameBoyFile("game.gbc.zip"))
        assertFalse(RomImportPolicy.acceptsGameBoyFile("game.gba"))
        assertFalse(RomImportPolicy.acceptsGameBoyFile("game.gb.zip"))
        assertFalse(RomImportPolicy.acceptsGameBoyFile("game"))
    }

    @Test
    fun theCartridgeHeaderChoosesTheConsoleNotTheExtension() {
        fun rom(flag: Int) = ByteArray(0x150).also { it[0x143] = flag.toByte() }
        assertEquals(GamePlatform.GB, RomImportPolicy.platformOf(rom(0x00)))
        assertEquals(GamePlatform.GBC, RomImportPolicy.platformOf(rom(0x80)))
        assertEquals(GamePlatform.GBC, RomImportPolicy.platformOf(rom(0xC0)))
        // Only the two documented values mark a color cartridge.
        assertEquals(GamePlatform.GB, RomImportPolicy.platformOf(rom(0x88)))
        assertEquals(GamePlatform.GB, RomImportPolicy.platformOf(ByteArray(0x100)))
    }

    @Test
    fun romByteLimitIsExplicit() {
        assertTrue(RomImportPolicy.maxRomBytes >= 8 * 1024 * 1024)
    }
}
