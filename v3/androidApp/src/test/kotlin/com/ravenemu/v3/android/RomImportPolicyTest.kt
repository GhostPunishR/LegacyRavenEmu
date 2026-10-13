package com.ravenemu.v3.android

import com.ravenemu.v3.shared.RomImportPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomImportPolicyTest {
    @Test
    fun acceptsGbAndGbcFilesRegardlessOfCase() {
        assertTrue(RomImportPolicy.acceptsGameBoyFile("jeu.gb"))
        assertTrue(RomImportPolicy.acceptsGameBoyFile("JEU.GB"))
        assertTrue(RomImportPolicy.acceptsGameBoyFile("jeu.gbc"))
        assertTrue(RomImportPolicy.acceptsGameBoyFile("JEU.GBC"))
    }

    @Test
    fun rejectsOtherFormatsAndCompressedFiles() {
        assertFalse(RomImportPolicy.acceptsGameBoyFile("jeu.gbc.zip"))
        assertFalse(RomImportPolicy.acceptsGameBoyFile("jeu.gba"))
        assertFalse(RomImportPolicy.acceptsGameBoyFile("jeu.gb.zip"))
        assertFalse(RomImportPolicy.acceptsGameBoyFile("jeu.txt"))
        assertFalse(RomImportPolicy.acceptsGameBoyFile(""))
    }

    @Test
    fun theMaximumSizeAllowsLargeGbButNotUnlimitedFiles() {
        assertTrue(RomImportPolicy.maxRomBytes >= 8 * 1024 * 1024)
        assertTrue(RomImportPolicy.maxRomBytes <= 16 * 1024 * 1024)
    }
}
