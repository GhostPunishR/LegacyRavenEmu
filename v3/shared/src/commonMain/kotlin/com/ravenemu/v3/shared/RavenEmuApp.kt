package com.ravenemu.v3.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/*
 * RavenEmu 0.8.1 visual reference:
 * app/android/src/main/res/layout/activity_main.xml
 * app/android/src/main/res/layout/item_rom_grid.xml
 * app/android/src/main/res/values/colors.xml
 *
 * Reimplemented with shared Compose so Android and iOS show the same screens.
 */
private val RavenBackground = Color(0xFF0B0C12)
private val RavenSurface = Color(0xFF191B28)
private val RavenAccent = Color(0xFFA78BFA)
private val RavenText = Color(0xFFF5F2FF)
private val RavenSecondary = Color(0xFFCBC4DC)
private val RavenMuted = Color(0xFFAAA2BC)
private val RavenOutline = Color(0xFF3C4052)
private val RavenDanger = Color(0xFFFF928B)

private enum class RavenScreen { LIBRARY, SETTINGS, ABOUT, PLAYER }
private enum class RavenGlyph { SETTINGS, ADD, MORE, SEARCH, BACK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RavenEmuApp(
    onImportRom: (() -> Unit)? = null,
    loadedGame: LoadedGameInfo? = null,
    libraryGames: List<GameEntry> = emptyList(),
    libraryCovers: Map<String, ImageBitmap> = emptyMap(),
    onChooseCover: ((String) -> Unit)? = null,
    onRemoveCover: ((String) -> Unit)? = null,
    coverBusy: Boolean = false,
    onLaunchLibraryGame: ((String, () -> Unit) -> Unit)? = null,
    importBusy: Boolean = false,
    importError: String? = null,
    onStartVideo: (() -> Unit)? = null,
    onStopVideo: (() -> Unit)? = null,
    videoFrame: ImageBitmap? = null,
    videoError: String? = null,
    videoMetrics: GbVideoMetrics? = null,
    onButtonChange: ((GbButton, Boolean) -> Unit)? = null,
    onExportAudioDiagnostics: (() -> Unit)? = null,
    onSaveState: ((Int) -> Unit)? = null,
    onLoadState: ((Int) -> Unit)? = null,
    onDeleteState: ((Int) -> Unit)? = null,
    stateSlots: List<GbStateSlot> = emptyList(),
    stateSlotsLoading: Boolean = false,
    stateOperationBusy: Boolean = false,
    onGameMenuVisibilityChange: ((Boolean) -> Unit)? = null,
    settings: V3Settings = V3Settings(),
    onSettingsChange: ((V3Settings) -> Unit)? = null,
    appVersion: String = "V3 Preview",
    onOpenLink: ((String) -> Unit)? = null,
) {
    var screen by remember { mutableStateOf(RavenScreen.LIBRARY) }
    var search by remember { mutableStateOf("") }
    var listMode by remember { mutableStateOf(false) }
    var gameDetails by remember { mutableStateOf<GameEntry?>(null) }
    val games = if (libraryGames.isNotEmpty()) libraryGames
        else if (loadedGame == null) emptyList() else listOf(
            GameEntry("active", loadedGame.filename, loadedGame.platform),
        )
    val pages = LibraryNavigation.pages(games)
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(pages.size) {
        if (pagerState.currentPage >= pages.size) pagerState.scrollToPage(0)
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = RavenAccent,
        background = RavenBackground,
        surface = RavenSurface,
        onBackground = RavenText,
        onSurface = RavenText,
        onPrimary = RavenBackground,
    )) {
        Column(
            Modifier.fillMaxSize().background(RavenBackground)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            when (screen) {
                RavenScreen.LIBRARY -> {
                    LibraryToolbar(
                        title = pages.getOrElse(pagerState.currentPage) { pages.first() }.title,
                        importEnabled = onImportRom != null && !importBusy,
                        listMode = listMode,
                        onSettings = { screen = RavenScreen.SETTINGS },
                        onAdd = { onImportRom?.invoke() },
                        onToggleView = { listMode = !listMode },
                        onAbout = { screen = RavenScreen.ABOUT },
                    )
                    SearchField(query = search, onChange = { search = it })
                    if (importBusy) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Chargement de la ROM...", fontSize = 12.sp, color = RavenMuted)
                        }
                    }
                    if (importError != null) {
                        Text(
                            importError,
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            color = RavenDanger,
                            fontSize = 12.sp,
                        )
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) { index ->
                        val page = pages[index]
                        val entries = LibraryNavigation.gamesForPage(games, page, search)
                        LibraryPageContent(
                            entries = entries,
                            covers = libraryCovers,
                            emptyMessage = when {
                                search.isNotBlank() -> "Aucun jeu ne correspond à cette recherche."
                                games.isEmpty() && onImportRom != null ->
                                    "Aucun jeu détecté.\nAjoutez une ROM avec le bouton + de la barre."
                                games.isEmpty() -> "Aucun jeu détecté.\nImport iOS à venir."
                                else -> "Aucun jeu ${page.title} dans votre bibliothèque."
                            },
                            listMode = listMode,
                            onSelect = { gameDetails = it },
                        )
                    }
                    PageIndicator(pages.size, pagerState.currentPage)
                    Text(
                        "RavenEmu By GhostPunishR",
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        color = RavenMuted,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
                RavenScreen.SETTINGS -> V3SettingsScreen(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    appVersion = appVersion,
                    onOpenLink = onOpenLink,
                    aboutOnly = false,
                    onBack = { screen = RavenScreen.LIBRARY },
                )
                RavenScreen.ABOUT -> V3SettingsScreen(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    appVersion = appVersion,
                    onOpenLink = onOpenLink,
                    aboutOnly = true,
                    onBack = { screen = RavenScreen.LIBRARY },
                )
                RavenScreen.PLAYER -> GbVideoScreen(
                    settings = settings,
                    title = loadedGame?.filename.orEmpty(),
                    frame = videoFrame,
                    error = videoError,
                    metrics = videoMetrics,
                    onButtonChange = onButtonChange,
                    onExportAudioDiagnostics = onExportAudioDiagnostics,
                    onSaveState = onSaveState,
                    onLoadState = onLoadState,
                    onDeleteState = onDeleteState,
                    stateSlots = stateSlots,
                    stateSlotsLoading = stateSlotsLoading,
                    stateOperationBusy = stateOperationBusy,
                    onGameMenuVisibilityChange = onGameMenuVisibilityChange,
                    onBack = {
                        onStopVideo?.invoke()
                        screen = RavenScreen.LIBRARY
                    },
                )
            }
        }
        if (gameDetails != null) {
            ModalBottomSheet(
                onDismissRequest = { gameDetails = null },
                containerColor = RavenSurface,
                contentColor = RavenText,
            ) {
                val entry = gameDetails!!
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(entry.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(platformLabel(entry.platform), color = RavenSecondary, fontSize = 13.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CoverArtwork(
                            entry.platform, libraryCovers[entry.id],
                            Modifier.width(90.dp).height(105.dp),
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                if (libraryCovers.containsKey(entry.id))
                                    "Jaquette personnalisée" else "Aucune jaquette",
                                color = RavenMuted, fontSize = 13.sp,
                            )
                            if (onChooseCover != null) {
                                OutlinedButton(
                                    onClick = { onChooseCover(entry.id) },
                                    enabled = !coverBusy && !importBusy,
                                ) { Text("Choisir une image", fontSize = 12.sp) }
                            }
                        }
                    }
                    if (libraryCovers.containsKey(entry.id) && onRemoveCover != null) {
                        TextButton(
                            onClick = { onRemoveCover(entry.id) },
                            enabled = !coverBusy,
                        ) { Text("Retirer la jaquette", color = RavenAccent) }
                    }
                    Text(
                        "Vidéo, commandes et audio Game Boy disponibles sur Android. La sauvegarde interne du jeu est automatique et un save state manuel est disponible dans le menu ⋮.",
                        color = RavenMuted,
                        fontSize = 13.sp,
                    )
                    if (onStartVideo != null &&
                        (onLaunchLibraryGame != null || loadedGame != null)
                    ) {
                        Button(
                            onClick = {
                                gameDetails = null
                                if (onLaunchLibraryGame != null) {
                                    onLaunchLibraryGame(entry.id) {
                                        onStartVideo()
                                        screen = RavenScreen.PLAYER
                                    }
                                } else {
                                    onStartVideo()
                                    screen = RavenScreen.PLAYER
                                }
                            },
                            enabled = !importBusy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RavenAccent,
                                contentColor = RavenBackground,
                            ),
                        ) {
                            Text("Jouer")
                        }
                    }
                    OutlinedButton(
                        onClick = { gameDetails = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Fermer")
                    }
                }
            }
        }
    }
}


/**
 * Shared GB player. Android supplies native frames and touch input,
 * while the iOS app still needs native session integration.
 * No audio output or file-save behavior is implied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GbVideoScreen(
    settings: V3Settings,
    title: String,
    frame: ImageBitmap?,
    error: String?,
    metrics: GbVideoMetrics?,
    onButtonChange: ((GbButton, Boolean) -> Unit)?,
    onExportAudioDiagnostics: (() -> Unit)?,
    onSaveState: ((Int) -> Unit)?,
    onLoadState: ((Int) -> Unit)?,
    onDeleteState: ((Int) -> Unit)?,
    stateSlots: List<GbStateSlot>,
    stateSlotsLoading: Boolean,
    stateOperationBusy: Boolean,
    onGameMenuVisibilityChange: ((Boolean) -> Unit)?,
    onBack: () -> Unit,
) {
    var gameMenuOpen by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableStateOf(0) }
    var confirmation by remember { mutableStateOf<Pair<Int, String>?>(null) }
    fun setGameMenu(open: Boolean) {
        if (gameMenuOpen == open) return
        gameMenuOpen = open
        onGameMenuVisibilityChange?.invoke(open)
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).background(RavenBackground),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                RavenIcon(RavenGlyph.BACK, RavenText)
            }
            Text(
                title,
                color = RavenText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            if (onExportAudioDiagnostics != null || onSaveState != null || onLoadState != null) {
                IconButton(
                    onClick = { setGameMenu(true) },
                    modifier = Modifier.size(48.dp),
                ) {
                    RavenIcon(RavenGlyph.MORE, RavenText)
                }
            }
        }
        // The debug bar is optional; enabled by default for the V3 preview.
        if (settings.showDiagnostics) Row(
            modifier = Modifier.fillMaxWidth().height(32.dp)
                .background(RavenSurface).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "FPS ${metrics?.let { oneDecimal(it.actualFps) } ?: "--"} / ${metrics?.let { oneDecimal(it.targetFps) } ?: "--"}",
                color = RavenAccent, fontSize = 11.sp, maxLines = 1,
            )
            Text(
                "Moteur ${metrics?.let { oneDecimal(it.coreMs) } ?: "--"} ms",
                color = RavenSecondary, fontSize = 10.sp, maxLines = 1,
            )
            Text(
                "Conv. ${metrics?.let { oneDecimal(it.conversionMs) } ?: "--"} ms",
                color = RavenMuted, fontSize = 10.sp, maxLines = 1,
            )
        }
        // Pin the GB picture to the top of its available area rather than
        // vertically centering it. The remaining space sits below the video,
        // allowing the thumb controls to remain anchored near the safe bottom.
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.TopCenter,
        ) {
            val pictureHeight = minOf(maxHeight, maxWidth * (144f / 160f))
            val pictureWidth = pictureHeight * (160f / 144f)
            if (frame != null) {
                Image(
                    bitmap = frame,
                    contentDescription = "Image de la Game Boy",
                    modifier = Modifier.width(pictureWidth).height(pictureHeight),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            } else {
                Text(
                    if (error == null) "Préparation de l'image du jeu..." else error,
                    color = if (error == null) RavenMuted else RavenDanger,
                    modifier = Modifier.padding(20.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (onButtonChange != null) {
            GbTouchControls(
                onButton = onButtonChange,
                haptics = settings.haptics,
                scalePercent = settings.controlsScale,
                verticalGapDp = settings.controlsGap,
                highContrast = settings.controlsStyle == "contrast",
                // Keep the lower pills slightly above the system navigation bar,
                // matching the requested default control placement.
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }

    }

    if (gameMenuOpen) {
        ModalBottomSheet(
            onDismissRequest = { setGameMenu(false) },
            containerColor = RavenSurface,
            contentColor = RavenText,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Menu du jeu", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(title, color = RavenSecondary, fontSize = 13.sp)
                Text("Partie en pause · 4 emplacements de sauvegarde",
                    color = RavenMuted, fontSize = 12.sp)
                // Compact switcher: only the selected slot is expanded.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (index in 0 until 4) {
                        val occupied = stateSlots.firstOrNull { it.index == index }?.occupied == true
                        val active = selectedSlot == index
                        Box(
                            Modifier.weight(1f).height(44.dp)
                                .background(
                                    if (active) RavenAccent else Color(0xFF292A38),
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { selectedSlot = index },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("${index + 1}",
                                    color = if (active) RavenBackground else RavenText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold)
                                if (occupied) {
                                    Box(Modifier.size(5.dp).background(
                                        if (active) RavenBackground else RavenAccent, CircleShape))
                                }
                            }
                        }
                    }
                }
                if (stateSlotsLoading) {
                    Row(
                        Modifier.fillMaxWidth().height(96.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Chargement des emplacements…", color = RavenMuted, fontSize = 12.sp)
                    }
                } else {
                    val entry = stateSlots.firstOrNull { it.index == selectedSlot }
                    val occupied = entry?.occupied == true
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF202230)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier.width(94.dp).height(84.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xFF10111B))
                                        .border(1.dp, RavenOutline, RoundedCornerShape(9.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (entry?.thumbnail != null) {
                                        Image(entry.thumbnail,
                                            "Aperçu de l'emplacement ${selectedSlot + 1}",
                                            Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                            filterQuality = FilterQuality.None)
                                    } else {
                                        Text("GB", color = RavenMuted, fontSize = 16.sp)
                                    }
                                }
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text("Emplacement ${selectedSlot + 1}",
                                        color = RavenText, fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp)
                                    Text(entry?.savedAt ?: "Vide",
                                        color = RavenMuted, fontSize = 12.sp)
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilledTonalButton(
                                    modifier = Modifier.weight(1f),
                                    enabled = !stateOperationBusy && onSaveState != null,
                                    onClick = {
                                        if (occupied) confirmation = selectedSlot to "overwrite"
                                        else onSaveState?.invoke(selectedSlot)
                                    },
                                ) { Text("Sauver", fontSize = 12.sp) }
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    enabled = occupied && !stateOperationBusy && onLoadState != null,
                                    onClick = {
                                        onLoadState?.invoke(selectedSlot)
                                        setGameMenu(false)
                                    },
                                ) { Text("Charger", fontSize = 12.sp) }
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    enabled = occupied && !stateOperationBusy && onDeleteState != null,
                                    onClick = { confirmation = selectedSlot to "delete" },
                                ) { Text("Supprimer", fontSize = 11.sp) }
                            }
                        }
                    }
                }
                if (onExportAudioDiagnostics != null) {
                    OutlinedButton(
                        onClick = onExportAudioDiagnostics,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Exporter le diagnostic audio") }
                }
                Button(
                    onClick = { setGameMenu(false) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RavenAccent, contentColor = RavenBackground,
                    ),
                ) { Text("Reprendre le jeu") }
            }
        }
    }
    val action = confirmation
    if (action != null) {
        val index = action.first
        val deleting = action.second == "delete"
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (deleting) "Supprimer la sauvegarde ?" else "Écraser la sauvegarde ?") },
            text = {
                Text(
                    if (deleting)
                        "Supprimer définitivement l'emplacement ${index + 1} ? La sauvegarde classique du jeu restera intacte."
                    else
                        "Remplacer l'état de l'emplacement ${index + 1} par la partie actuelle ? Cette action est irréversible."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !stateOperationBusy,
                    onClick = {
                        confirmation = null
                        if (deleting) onDeleteState?.invoke(index)
                        else onSaveState?.invoke(index)
                    },
                ) { Text(if (deleting) "Supprimer" else "Écraser") }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) { Text("Annuler") }
            },
            containerColor = RavenSurface,
            titleContentColor = RavenText,
            textContentColor = RavenSecondary,
        )
    }
}


@Composable
private fun LibraryToolbar(
    title: String,
    importEnabled: Boolean,
    listMode: Boolean,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onToggleView: () -> Unit,
    onAbout: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        Text(
            title,
            Modifier.align(Alignment.Center).padding(horizontal = 104.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = RavenText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
            // Exact V2 toolbar icon, white and borderless; + and ⋮ unchanged.
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                V2Icon(V2IconType.SETTINGS, RavenText)
            }
        }
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
            IconButton(
                onClick = onAdd,
                enabled = importEnabled,
                modifier = Modifier.size(48.dp),
            ) {
                RavenIcon(RavenGlyph.ADD, if (importEnabled) RavenAccent else RavenMuted)
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(48.dp),
                ) { RavenIcon(RavenGlyph.MORE, RavenText) }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(RavenSurface),
                ) {
                    DropdownMenuItem(
                        text = { Text(if (listMode) "Vue grille" else "Vue liste", color = RavenText) },
                        onClick = { menuOpen = false; onToggleView() },
                    )
                    DropdownMenuItem(
                        text = { Text("À propos", color = RavenText) },
                        onClick = { menuOpen = false; onAbout() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = RavenText, fontSize = 15.sp),
        cursorBrush = SolidColor(RavenAccent),
        modifier = Modifier.fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .height(44.dp)
            .border(1.dp, RavenOutline.copy(alpha = 0.8f),
                RoundedCornerShape(14.dp))
            .background(RavenSurface, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        decorationBox = { field ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RavenIcon(RavenGlyph.SEARCH, RavenMuted)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Rechercher un jeu", color = RavenMuted, fontSize = 15.sp)
                    }
                    field()
                }
            }
        },
    )
}

@Composable
private fun LibraryPageContent(
    entries: List<GameEntry>,
    covers: Map<String, ImageBitmap>,
    emptyMessage: String,
    listMode: Boolean,
    onSelect: (GameEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                emptyMessage,
                Modifier.padding(32.dp),
                textAlign = TextAlign.Center,
                color = RavenMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
        }
    } else if (listMode) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(RavenSurface.copy(alpha = 0.75f),
                            RoundedCornerShape(10.dp))
                        .clickable { onSelect(entry) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverArtwork(
                        entry.platform, covers[entry.id],
                        Modifier.width(44.dp).height(52.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(entry.title, color = RavenText, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(platformLabel(entry.platform), color = RavenMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 106.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                Column(
                    modifier = Modifier.clickable { onSelect(entry) }
                        .padding(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CoverArtwork(entry.platform, covers[entry.id],
                        Modifier.fillMaxWidth().aspectRatio(6f / 7f))
                    Spacer(Modifier.height(6.dp))
                    Text(entry.title, color = RavenSecondary, fontSize = 11.sp,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Display imported artwork; never substitute invented or downloaded game covers. */
@Composable
private fun CoverArtwork(
    platform: GamePlatform,
    bitmap: ImageBitmap?,
    modifier: Modifier,
) {
    if (bitmap == null) {
        CoverPlaceholder(platform, modifier)
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = "Jaquette personnalisée",
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, RavenOutline.copy(alpha = 0.75f),
                    RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

/* No invented artwork. A neutral placeholder is used until covers are migrated. */
@Composable
private fun CoverPlaceholder(platform: GamePlatform, modifier: Modifier) {
    Box(
        modifier.border(1.dp, RavenOutline.copy(alpha = 0.75f),
            RoundedCornerShape(10.dp))
            .background(RavenSurface, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.width(24.dp).height(3.dp)
                .background(RavenAccent.copy(alpha = 0.65f), CircleShape))
            Text(
                platform.name,
                color = RavenSecondary.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pageCount > 1) {
            repeat(pageCount) { index ->
                Box(
                    Modifier.padding(horizontal = 4.dp)
                        .size(if (index == currentPage) 7.dp else 5.dp)
                        .background(
                            if (index == currentPage) RavenAccent else RavenOutline,
                            CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun V2Subscreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            RavenIcon(RavenGlyph.BACK, RavenText)
        }
        Text(title, fontSize = 20.sp, color = RavenText, fontWeight = FontWeight.Medium)
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

private fun platformLabel(platform: GamePlatform): String = when (platform) {
    GamePlatform.GB -> "Game Boy"
    GamePlatform.GBC -> "Game Boy Color"
    GamePlatform.GBA -> "Game Boy Advance"
}

/* Vector icons render consistently on Android and iOS without system glyphs. */
@Composable
private fun RavenIcon(icon: RavenGlyph, tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val line = 2.dp.toPx()
        when (icon) {
            RavenGlyph.ADD -> {
                drawLine(tint, Offset(cx, size.height * .16f),
                    Offset(cx, size.height * .84f), strokeWidth = line)
                drawLine(tint, Offset(size.width * .16f, cy),
                    Offset(size.width * .84f, cy), strokeWidth = line)
            }
            RavenGlyph.MORE -> {
                for (fraction in listOf(.24f, .5f, .76f)) {
                    drawCircle(tint, radius = 1.8.dp.toPx(),
                        center = Offset(cx, size.height * fraction))
                }
            }
            RavenGlyph.SEARCH -> {
                drawCircle(tint, radius = size.minDimension * .27f,
                    center = Offset(cx * .88f, cy * .86f),
                    style = Stroke(width = line))
                drawLine(tint, Offset(size.width * .63f, size.height * .63f),
                    Offset(size.width * .85f, size.height * .85f), strokeWidth = line)
            }
            RavenGlyph.BACK -> {
                drawLine(tint, Offset(size.width * .6f, size.height * .18f),
                    Offset(size.width * .28f, cy), strokeWidth = line)
                drawLine(tint, Offset(size.width * .28f, cy),
                    Offset(size.width * .6f, size.height * .82f), strokeWidth = line)
            }
            RavenGlyph.SETTINGS -> {
                drawCircle(tint, radius = size.minDimension * .28f,
                    center = Offset(cx, cy), style = Stroke(width = line))
                drawCircle(tint, radius = size.minDimension * .09f,
                    center = Offset(cx, cy), style = Stroke(width = line))
                for (i in 0..7) {
                    val angle = i.toDouble() * PI / 4
                    val x = cos(angle).toFloat()
                    val y = sin(angle).toFloat()
                    drawLine(tint,
                        Offset(cx + x * size.minDimension * .32f,
                            cy + y * size.minDimension * .32f),
                        Offset(cx + x * size.minDimension * .44f,
                            cy + y * size.minDimension * .44f),
                        strokeWidth = line)
                }
            }
        }
    }
}

private fun oneDecimal(value: Double): String =
    ((value * 10.0).roundToInt() / 10.0).toString()
