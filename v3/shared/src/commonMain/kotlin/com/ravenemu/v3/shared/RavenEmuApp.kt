package com.ravenemu.v3.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
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
private val RavenSurface = Color(0xFF161821)
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
    importBusy: Boolean = false,
    importError: String? = null,
    onStartVideo: (() -> Unit)? = null,
    onStopVideo: (() -> Unit)? = null,
    videoFrame: ImageBitmap? = null,
    videoError: String? = null,
    videoMetrics: GbVideoMetrics? = null,
    onButtonChange: ((GbButton, Boolean) -> Unit)? = null,
) {
    var screen by remember { mutableStateOf(RavenScreen.LIBRARY) }
    var search by remember { mutableStateOf("") }
    var listMode by remember { mutableStateOf(false) }
    var gameDetails by remember { mutableStateOf<GameEntry?>(null) }
    val games = if (loadedGame == null) emptyList() else listOf(
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
                RavenScreen.SETTINGS -> V2Subscreen(
                    title = "Réglages",
                    onBack = { screen = RavenScreen.LIBRARY },
                ) {
                    Text("RavenEmu V3 Preview", fontSize = 16.sp, color = RavenText)
                    Text(
                        "L'interface reprend RavenEmu 0.8.1. Les réglages du moteur, les skins et les comptes seront migrés après le lecteur.",
                        fontSize = 14.sp,
                        color = RavenMuted,
                    )
                }
                RavenScreen.ABOUT -> V2Subscreen(
                    title = "À propos",
                    onBack = { screen = RavenScreen.LIBRARY },
                ) {
                    Text("RavenEmu", color = RavenText, fontSize = 20.sp)
                    Text("Préversion V3 Android / iOS", color = RavenMuted, fontSize = 14.sp)
                    Text(
                        "Le lecteur Game Boy et ses commandes tactiles sont disponibles sur Android. L'audio et les sauvegardes restent à intégrer.",
                        color = RavenMuted,
                        fontSize = 14.sp,
                    )
                }
                RavenScreen.PLAYER -> GbVideoScreen(
                    title = loadedGame?.filename.orEmpty(),
                    frame = videoFrame,
                    error = videoError,
                    metrics = videoMetrics,
                    onButtonChange = onButtonChange,
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
                    Text("ROM chargée dans RavenGB.", color = RavenText)
                    Text(
                        "La vidéo et les commandes Game Boy sont disponibles sur Android. Audio et sauvegardes à venir.",
                        color = RavenMuted,
                        fontSize = 13.sp,
                    )
                    if (onStartVideo != null && loadedGame != null) {
                        TextButton(onClick = {
                            gameDetails = null
                            onStartVideo()
                            screen = RavenScreen.PLAYER
                        }) {
                            Text("Ouvrir le lecteur GB", color = RavenAccent)
                        }
                    }
                    TextButton(onClick = { gameDetails = null }) {
                        Text("Fermer", color = RavenAccent)
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
@Composable
private fun GbVideoScreen(
    title: String,
    frame: ImageBitmap?,
    error: String?,
    metrics: GbVideoMetrics?,
    onButtonChange: ((GbButton, Boolean) -> Unit)?,
    onBack: () -> Unit,
) {
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
            )
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (frame != null) {
                Image(
                    bitmap = frame,
                    contentDescription = "Image de la Game Boy",
                    modifier = Modifier.fillMaxSize(),
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
        if (metrics != null) {
            Text(
                "Frames calculées : ${oneDecimal(metrics.actualFps)} / ${oneDecimal(metrics.targetFps)} FPS",
                color = RavenAccent,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
            )
            Text(
                "Moteur : ${oneDecimal(metrics.coreMs)} ms/image | Conversion : ${oneDecimal(metrics.conversionMs)} ms/image",
                color = RavenMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
            )
        }
        if (onButtonChange != null) {
            GbTouchControls(
                onButton = onButtonChange,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        Text(
            "Commandes GB tactiles. Audio et sauvegardes à venir.",
            color = RavenMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 4.dp),
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
            fontWeight = FontWeight.Medium,
            color = RavenText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                RavenIcon(RavenGlyph.SETTINGS, RavenText)
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
            .height(42.dp)
            .background(RavenSurface, RoundedCornerShape(12.dp))
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
                    Modifier.fillMaxWidth().clickable { onSelect(entry) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverPlaceholder(
                        entry.platform, Modifier.width(44.dp).height(52.dp))
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
                    CoverPlaceholder(entry.platform,
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

/* No invented artwork. A neutral placeholder is used until covers are migrated. */
@Composable
private fun CoverPlaceholder(platform: GamePlatform, modifier: Modifier) {
    Box(modifier.background(RavenSurface), contentAlignment = Alignment.Center) {
        Text(
            platform.name,
            color = RavenMuted.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
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
