package com.ravenemu.v3.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Background = Color(0xFF0B0C12)
private val Surface = Color(0xFF1A1B28)
private val TextMain = Color(0xFFF5F2FF)
private val Muted = Color(0xFFAAA2BC)
private val Accent = Color(0xFFA78BFA)
private val Outline = Color(0xFF3C4052)

private enum class Page(val label: String) {
    HOME("Paramètres"),
    RETRO("RetroAchievements"),
    GENERAL("Général"),
    AUDIO("Audio"),
    VIDEO("Vidéo"),
    INTERFACE("Interface"),
    ABOUT("À propos"),
}

/** Shared V2-inspired settings navigation. Settings are supplied by the host. */
@Composable
fun V3SettingsScreen(
    settings: V3Settings,
    onSettingsChange: ((V3Settings) -> Unit)?,
    appVersion: String,
    onOpenLink: ((String) -> Unit)?,
    retroSnapshot: V3RetroSnapshot = V3RetroSnapshot(),
    retroBusy: Boolean = false,
    onRetroLogin: ((String, String) -> Unit)? = null,
    onRetroDisconnect: (() -> Unit)? = null,
    onRetroRetry: (() -> Unit)? = null,
    aboutOnly: Boolean,
    onBack: () -> Unit,
) {
    var page by remember(aboutOnly) {
        mutableStateOf(if (aboutOnly) Page.ABOUT else Page.HOME)
    }
    val update: (V3Settings) -> Unit = { updated ->
        onSettingsChange?.invoke(updated.normalized())
    }
    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            Modifier.fillMaxWidth().height(54.dp).background(Surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (page == Page.HOME || aboutOnly) onBack()
                    else page = Page.HOME
                },
                modifier = Modifier.size(48.dp),
            ) { Text("‹", color = TextMain, fontSize = 30.sp) }
            Text(page.label, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (page) {
                Page.HOME -> {
                    if (onRetroLogin != null) {
                        CategoryRow("RetroAchievements", "Compte, succès et progression") {
                            page = Page.RETRO
                        }
                    }
                    CategoryRow("Général", "Pause et diagnostics") { page = Page.GENERAL }
                    CategoryRow("Audio", "Son du jeu et volume") { page = Page.AUDIO }
                    CategoryRow("Vidéo", "Couleurs, luminosité et contraste") { page = Page.VIDEO }
                    CategoryRow("Interface", "Vibrations, commandes et style") { page = Page.INTERFACE }
                    CategoryRow("À propos", "Version, auteur, confidentialité et licences") {
                        page = Page.ABOUT
                    }
                }
                Page.RETRO -> {
                    V3RetroAchievementsContent(
                        snapshot = retroSnapshot,
                        loginBusy = retroBusy,
                        onLogin = onRetroLogin,
                        onDisconnect = onRetroDisconnect,
                        onRetry = onRetroRetry,
                        onOpenLink = onOpenLink,
                    )
                }
                Page.GENERAL -> {
                    GroupTitle("Émulation")
                    SettingDescription(
                        "Pause automatique",
                        "Le jeu et son audio sont suspendus lorsque RavenEmu passe en arrière-plan ou lorsque le panneau de jeu est ouvert. Ce comportement est fixe pour protéger la partie.",
                    )
                    GroupTitle("Diagnostic")
                    SettingSwitch(
                        "Afficher les performances",
                        "FPS, temps du moteur et conversion vidéo dans le lecteur.",
                        settings.showDiagnostics,
                        onSettingsChange != null,
                    ) { update(settings.copy(showDiagnostics = it)) }
                    Text("Le diagnostic audio reste exportable depuis le menu du jeu.",
                        color = Muted, fontSize = 12.sp)
                }
                Page.AUDIO -> {
                    GroupTitle("Son du jeu")
                    SettingSwitch(
                        "Activer le son",
                        "Conserve l'horloge d'émulation et coupe uniquement la sortie audio.",
                        settings.audioEnabled,
                        onSettingsChange != null,
                    ) { update(settings.copy(audioEnabled = it)) }
                    SettingSlider(
                        "Volume", "${settings.audioVolume} %",
                        settings.audioVolume, 0..100,
                        settings.audioEnabled && onSettingsChange != null,
                    ) { update(settings.copy(audioVolume = it)) }
                    Text("Le fondu de démarrage de 180 ms reste inchangé.",
                        color = Muted, fontSize = 12.sp)
                }
                Page.VIDEO -> {
                    GroupTitle("Écran Game Boy")
                    SettingDescription(
                        "Profil de couleurs",
                        "Original préserve les couleurs actuelles ; DMG simule un LCD vert ; Pocket utilise le gris.",
                    )
                    ChoiceRow("Original", settings.videoPalette == "original",
                        onSettingsChange != null) {
                        update(settings.copy(videoPalette = "original"))
                    }
                    ChoiceRow("DMG vert", settings.videoPalette == "dmg",
                        onSettingsChange != null) {
                        update(settings.copy(videoPalette = "dmg"))
                    }
                    ChoiceRow("Pocket gris", settings.videoPalette == "pocket",
                        onSettingsChange != null) {
                        update(settings.copy(videoPalette = "pocket"))
                    }
                    GroupTitle("Ajustements")
                    SettingSlider("Luminosité", "${settings.videoBrightness}",
                        settings.videoBrightness, -50..50, onSettingsChange != null) {
                        update(settings.copy(videoBrightness = it))
                    }
                    SettingSlider("Contraste", "${settings.videoContrast} %",
                        settings.videoContrast, 50..150, onSettingsChange != null) {
                        update(settings.copy(videoContrast = it))
                    }
                    TextButton(onClick = {
                        update(settings.copy(
                            videoPalette = "original", videoBrightness = 0, videoContrast = 100,
                        ))
                    }, enabled = onSettingsChange != null) {
                        Text("Rétablir l'affichage d'origine", color = Accent)
                    }
                }
                Page.INTERFACE -> {
                    GroupTitle("Commandes tactiles")
                    SettingSwitch(
                        "Retour haptique",
                        "Petite vibration à la pression des boutons.",
                        settings.haptics,
                        onSettingsChange != null,
                    ) { update(settings.copy(haptics = it)) }
                    SettingSlider(
                        "Taille des commandes", "${settings.controlsScale} %",
                        settings.controlsScale, 80..115, onSettingsChange != null,
                    ) { update(settings.copy(controlsScale = it)) }
                    SettingSlider(
                        "Espacement vertical", "${settings.controlsGap} dp",
                        settings.controlsGap, 20..80, onSettingsChange != null,
                    ) { update(settings.copy(controlsGap = it)) }
                    GroupTitle("Style des boutons")
                    ChoiceRow("Classique (défaut)", settings.controlsStyle == "classic",
                        onSettingsChange != null) {
                        update(settings.copy(controlsStyle = "classic"))
                    }
                    ChoiceRow("Contraste élevé", settings.controlsStyle == "contrast",
                        onSettingsChange != null) {
                        update(settings.copy(controlsStyle = "contrast"))
                    }
                    Text("Le gestionnaire de skins Delta et le déplacement libre des boutons seront migrés séparément.",
                        color = Muted, fontSize = 12.sp)
                    TextButton(onClick = {
                        update(settings.copy(
                            haptics = false, controlsScale = 100,
                            controlsGap = 46, controlsStyle = "classic",
                        ))
                    }, enabled = onSettingsChange != null) {
                        Text("Réinitialiser les commandes", color = Accent)
                    }
                }
                Page.ABOUT -> {
                    GroupTitle("RavenEmu")
                    SettingDescription("Version installée", appVersion)
                    SettingDescription("Créateur", "GhostPunishR")
                    SettingDescription("Version du projet", "V3 — préversion Android / iOS")
                    SettingDescription("Moteurs natifs", "RavenEmu-Cores 0.1.0 (révision épinglée)")
                    SettingDescription("Licence du projet", "GNU AGPL-3.0")
                    GroupTitle("Informations légales")
                    ExternalRow("Conditions d'utilisation", onOpenLink,
                        "https://github.com/GhostPunishR/RavenEmu/blob/main/TERMS.md")
                    ExternalRow("Politique de confidentialité", onOpenLink,
                        "https://github.com/GhostPunishR/RavenEmu/blob/main/PRIVACY.md")
                    ExternalRow("Licence AGPL-3.0", onOpenLink,
                        "https://github.com/GhostPunishR/RavenEmu/blob/main/LICENSE")
                    GroupTitle("Code source")
                    ExternalRow("RavenEmu — GitHub", onOpenLink,
                        "https://github.com/GhostPunishR/RavenEmu")
                    ExternalRow("RavenEmu-Cores — GitHub", onOpenLink,
                        "https://github.com/GhostPunishR/RavenEmu-Cores")
                    Text("Aucune ROM commerciale ni BIOS propriétaire n'est distribué avec RavenEmu.",
                        color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(title: String) {
    Text(title, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    HorizontalDivider(color = Outline)
}

@Composable
private fun CategoryRow(title: String, summary: String, onClick: () -> Unit) {
    val icon = when (title) {
        "RetroAchievements" -> V2IconType.ABOUT
        "Général" -> V2IconType.GENERAL
        "Audio" -> V2IconType.AUDIO
        "Vidéo" -> V2IconType.VIDEO
        "Interface" -> V2IconType.INTERFACE
        else -> V2IconType.ABOUT
    }
    Row(
        Modifier.fillMaxWidth()
            .border(1.dp, Outline.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .background(Surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(42.dp)
                .background(Accent.copy(alpha = 0.13f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { V2Icon(icon, Accent) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = TextMain, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold)
            Text(summary, color = Muted, fontSize = 12.sp)
        }
        Text("›", color = Accent, fontSize = 23.sp)
    }
}

@Composable
private fun SettingDescription(title: String, summary: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = TextMain, fontSize = 15.sp)
        Text(summary, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SettingSwitch(
    title: String, summary: String, checked: Boolean, enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SettingDescription(title, summary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingSlider(
    title: String, valueText: String, value: Int, range: IntRange,
    enabled: Boolean, onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = TextMain, fontSize = 15.sp)
            Text(valueText, color = Accent, fontSize = 14.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
private fun ChoiceRow(
    title: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().background(if (selected) Surface else Background)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "●" else "○", color = Accent, fontSize = 17.sp)
        Spacer(Modifier.width(12.dp))
        Text(title, color = TextMain, fontSize = 14.sp)
    }
}

@Composable
private fun ExternalRow(title: String, open: ((String) -> Unit)?, url: String) {
    TextButton(onClick = { open?.invoke(url) }, enabled = open != null) {
        Text(title + " ↗", color = Accent, fontSize = 14.sp)
    }
}
