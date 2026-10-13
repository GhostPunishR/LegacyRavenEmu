package com.ravenemu.v3.shared

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class V3SettingsTest {
    @Test
    fun defaultsPreserveValidatedV3Behavior() {
        val defaults = V3Settings()
        assertTrue(defaults.audioEnabled)
        assertEquals(100, defaults.audioVolume)
        assertEquals("original", defaults.videoPalette)
        assertEquals(0, defaults.videoBrightness)
        assertEquals(100, defaults.videoContrast)
        assertEquals(100, defaults.controlsScale)
        assertEquals(46, defaults.controlsGap)
        assertEquals("classic", defaults.controlsStyle)

        val frame = intArrayOf(0xff102030.toInt(), 0xff8899aa.toInt())
        val reference = frame.copyOf()
        GbDisplayFilter.apply(frame, defaults)
        assertContentEquals(reference, frame)
    }

    /**
     * Chaque jaquette demandée indique à un serveur tiers un jeu de la
     * bibliothèque, avec l'adresse IP de l'appareil. La recherche doit donc
     * être un choix de l'utilisateur, jamais un comportement par défaut, y
     * compris pour des réglages relus ou normalisés.
     */
    @Test
    fun onlineCoversStayOffUnlessChosen() {
        assertFalse(V3Settings().onlineCovers)
        assertFalse(V3Settings().normalized().onlineCovers)
        assertTrue(V3Settings(onlineCovers = true).normalized().onlineCovers)
    }

    @Test
    fun importedPreferencesAreClamped() {
        val settings = V3Settings(
            audioVolume = 150, videoPalette = "unsupported",
            videoBrightness = -200, videoContrast = 400,
            controlsScale = 900, controlsGap = -5, controlsStyle = "unknown",
        ).normalized()
        assertEquals(100, settings.audioVolume)
        assertEquals("original", settings.videoPalette)
        assertEquals(-50, settings.videoBrightness)
        assertEquals(150, settings.videoContrast)
        assertEquals(115, settings.controlsScale)
        assertEquals(20, settings.controlsGap)
        assertEquals("classic", settings.controlsStyle)
    }

    @Test
    fun pocketPaletteProducesGrayWithoutDroppingAlpha() {
        val frame = intArrayOf(0x7f20a0ff.toInt())
        GbDisplayFilter.apply(frame, V3Settings(videoPalette = "pocket"))
        val color = frame.single()
        assertEquals(0x7f, (color ushr 24) and 255)
        assertEquals((color ushr 16) and 255, (color ushr 8) and 255)
        assertEquals((color ushr 8) and 255, color and 255)
    }
}
