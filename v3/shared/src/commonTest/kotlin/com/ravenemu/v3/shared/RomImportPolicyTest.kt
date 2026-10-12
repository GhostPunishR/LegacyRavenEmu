package com.ravenemu.v3.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RomImportPolicyTest {
    @Test
    fun acceptsCaseInsensitiveGameBoyExtension() {
        assertTrue(RomImportPolicy.acceptsGameBoyFile("Tetris.gb"))
        assertTrue(RomImportPolicy.acceptsGameBoyFile("TETRIS.GB"))
    }

    @Test
    fun otherSystemsAndUnknownFilesAreNotAccidentallyLoadedAsDmg() {
        assertFalse(RomImportPolicy.acceptsGameBoyFile("game.gbc"))
        assertFalse(RomImportPolicy.acceptsGameBoyFile("game.gba"))
        assertFalse(RomImportPolicy.acceptsGameBoyFile("game.gb.zip"))
        assertFalse(RomImportPolicy.acceptsGameBoyFile("game"))
    }

    @Test
    fun romByteLimitIsExplicit() {
        assertTrue(RomImportPolicy.maxRomBytes >= 8 * 1024 * 1024)
    }
}
