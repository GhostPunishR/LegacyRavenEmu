package com.ravenemu.v3.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val RavenPurple = Color(0xFFAD92FF)
private val RavenDark = Color(0xFF111019)
private enum class RavenTab(val title: String, val glyph: String) {
    LIBRARY("Bibliothèque", "G"),
    PLAYER("Jouer", "P"),
    SETTINGS("Réglages", "S"),
}

/** Exactly the same Compose hierarchy is called by Android and the iOS host. */
@Composable
fun RavenEmuApp() {
    var current by remember { mutableStateOf(RavenTab.LIBRARY) }
    var darkMode by remember { mutableStateOf(true) }
    val colors = if (darkMode) {
        darkColorScheme(primary = RavenPurple, background = RavenDark, surface = Color(0xFF1E1B2A))
    } else {
        lightColorScheme(primary = Color(0xFF6745BD), surface = Color(0xFFF7F5FD))
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text("RAVENEMU", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("V3 · Aperçu multiplateforme", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    RavenTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == current,
                            onClick = { current = tab },
                            icon = { Text(tab.glyph, fontWeight = FontWeight.Bold) },
                            label = { Text(tab.title) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (current) {
                    RavenTab.LIBRARY -> LibraryScreen()
                    RavenTab.PLAYER -> PlayerScreen(onOpenLibrary = { current = RavenTab.LIBRARY })
                    RavenTab.SETTINGS -> SettingsScreen(
                        darkMode = darkMode,
                        onDarkModeChange = { darkMode = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen() {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<GamePlatform?>(null) }
    val games = LibraryCatalog.search(emptyList(), query, selected)

    Text("Mes jeux", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    TextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Rechercher un jeu") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text("Tous") })
        GamePlatform.entries.forEach { platform ->
            FilterChip(
                selected = selected == platform,
                onClick = { selected = platform },
                label = { Text(platform.name) },
            )
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Bibliothèque vide", style = MaterialTheme.typography.titleLarge)
            Text(
                if (games.isEmpty()) "L'import de ROMs sera connecté au moteur multiplateforme."
                else "${games.size} jeux",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlayerScreen(onOpenLibrary: () -> Unit) {
    Text("Lecteur", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Card(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(230.dp).background(Color(0xFF161824)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RAVEN", color = RavenPurple, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text("Aucune partie en cours", color = Color.White)
            }
        }
    }
    Text("L'affichage du jeu, les commandes et l'audio seront branchés sur RavenEmu-Cores.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = onOpenLibrary) { Text("Retour à la bibliothèque") }
}

@Composable
private fun SettingsScreen(darkMode: Boolean, onDarkModeChange: (Boolean) -> Unit) {
    Text("Réglages", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Mode sombre", style = MaterialTheme.typography.titleMedium)
                Text("Même thème sur les deux plateformes", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
        }
    }
    Text("Les préférences persistantes seront ajoutées avec le stockage natif.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
