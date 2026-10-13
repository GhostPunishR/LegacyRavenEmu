package com.ravenemu.v3.shared

/**
 * V3 preferences: portable value object. Android stores this in private
 * SharedPreferences; other hosts can supply their own persistence adapter.
 * Defaults deliberately preserve the already validated V3 picture and sound.
 */
data class V3Settings(
    val audioEnabled: Boolean = true,
    val audioVolume: Int = 100,
    val videoPalette: String = "original",
    val videoBrightness: Int = 0,
    val videoContrast: Int = 100,
    val showDiagnostics: Boolean = true,
    val haptics: Boolean = false,
    val controlsScale: Int = 100,
    val controlsGap: Int = 46,
    val controlsStyle: String = "classic",
    /**
     * Recherche des jaquettes sur `thumbnails.libretro.com`. Désactivée par
     * défaut : chaque image demandée indique à ce serveur tiers un jeu de la
     * bibliothèque, avec l'adresse IP de l'appareil. L'utilisateur doit
     * pouvoir l'accepter en connaissance de cause, pas la découvrir.
     */
    val onlineCovers: Boolean = false,
) {
    fun normalized(): V3Settings = copy(
        audioVolume = audioVolume.coerceIn(0, 100),
        videoPalette = videoPalette.takeIf { it in listOf("original", "dmg", "pocket") } ?: "original",
        videoBrightness = videoBrightness.coerceIn(-50, 50),
        videoContrast = videoContrast.coerceIn(50, 150),
        controlsScale = controlsScale.coerceIn(80, 115),
        controlsGap = controlsGap.coerceIn(20, 80),
        controlsStyle = controlsStyle.takeIf { it in listOf("classic", "contrast") } ?: "classic",
    )
}

/** Apply an optional display treatment after the native GB frame. */
object GbDisplayFilter {
    fun apply(pixels: IntArray, settings: V3Settings) {
        val profile = settings.videoPalette
        val brightness = settings.videoBrightness
        val contrast = settings.videoContrast
        if (profile == "original" && brightness == 0 && contrast == 100) return
        for (index in pixels.indices) {
            val color = pixels[index]
            val alpha = color and -0x1000000
            val sourceR = (color ushr 16) and 255
            val sourceG = (color ushr 8) and 255
            val sourceB = color and 255
            var r = sourceR
            var g = sourceG
            var b = sourceB
            if (profile != "original") {
                val luminance = (sourceR * 30 + sourceG * 59 + sourceB * 11) / 100
                if (profile == "dmg") {
                    // Retro LCD green with a dark-green shadow color.
                    r = 15 + luminance * 140 / 255
                    g = 56 + luminance * 132 / 255
                    b = 15 + luminance * 85 / 255
                } else {
                    r = luminance
                    g = luminance
                    b = luminance
                }
            }
            fun adjust(channel: Int): Int =
                (((channel - 128) * contrast) / 100 + 128 + brightness * 2)
                    .coerceIn(0, 255)
            pixels[index] = alpha or
                (adjust(r) shl 16) or (adjust(g) shl 8) or adjust(b)
        }
    }
}
