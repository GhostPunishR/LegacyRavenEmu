package com.ravenemu.v3.android

import android.content.Context
import com.ravenemu.v3.shared.V3Settings

/** Private V3-only preferences: never reads or changes V2 settings. */
internal class AndroidV3SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ravenemu_v3_preferences", Context.MODE_PRIVATE)

    fun read(): V3Settings = V3Settings(
        audioEnabled = prefs.getBoolean("audio_enabled", true),
        audioVolume = prefs.getInt("audio_volume", 100),
        videoPalette = prefs.getString("video_palette", "original") ?: "original",
        videoBrightness = prefs.getInt("video_brightness", 0),
        videoContrast = prefs.getInt("video_contrast", 100),
        showDiagnostics = prefs.getBoolean("show_diagnostics", true),
        haptics = prefs.getBoolean("haptics", false),
        controlsScale = prefs.getInt("controls_scale", 100),
        controlsGap = prefs.getInt("controls_gap", 46),
        controlsStyle = prefs.getString("controls_style", "classic") ?: "classic",
    ).normalized()

    fun write(settings: V3Settings) {
        val safe = settings.normalized()
        prefs.edit()
            .putBoolean("audio_enabled", safe.audioEnabled)
            .putInt("audio_volume", safe.audioVolume)
            .putString("video_palette", safe.videoPalette)
            .putInt("video_brightness", safe.videoBrightness)
            .putInt("video_contrast", safe.videoContrast)
            .putBoolean("show_diagnostics", safe.showDiagnostics)
            .putBoolean("haptics", safe.haptics)
            .putInt("controls_scale", safe.controlsScale)
            .putInt("controls_gap", safe.controlsGap)
            .putString("controls_style", safe.controlsStyle)
            .apply()
    }
}
