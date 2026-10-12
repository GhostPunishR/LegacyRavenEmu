package com.ravenemu.v3.android

import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.ravenemu.v3.shared.GameEntry
import com.ravenemu.v3.shared.GamePlatform
import com.ravenemu.v3.shared.GbDisplayFilter
import com.ravenemu.v3.shared.GbButton
import com.ravenemu.v3.shared.GbStateSlot
import com.ravenemu.v3.shared.GbVideoMetrics
import com.ravenemu.v3.shared.GbVideoMetricsAccumulator
import com.ravenemu.v3.shared.LoadedGameInfo
import com.ravenemu.v3.shared.RavenEmuApp
import com.ravenemu.v3.shared.V3Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

private data class CapturedGbFrame(
    val bitmap: ImageBitmap,
    val coreTimeNanos: Long,
    val conversionTimeNanos: Long,
    val audioFailure: String?,
)

class MainActivity : ComponentActivity() {
    private lateinit var gameSession: AndroidGbSession
    private var foreground by mutableStateOf(false)
    private var activeAudio: AndroidGbAudioOutput? = null
    private var lastAudioReport: String = "Aucune session audio enregistrée.\n"

    override fun onResume() {
        super.onResume()
        foreground = true
    }

    override fun onPause() {
        foreground = false
        activeAudio?.stopNow()
        gameSession.releaseAllButtons()
        // A lifecycle stop can terminate the process soon after this callback;
        // commit changed SRAM before returning rather than relying on a job.
        try {
            gameSession.saveBatteryIfDirty()
        } catch (error: Exception) {
            Log.e("RavenEmuSaves", "Cannot save game on pause", error)
            Toast.makeText(this, "Échec de la sauvegarde du jeu", Toast.LENGTH_LONG).show()
        }
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        gameSession = AndroidGbSession(contentResolver, filesDir)

        setContent {
            val settingsStore = remember { AndroidV3SettingsStore(applicationContext) }
            val romLibrary = remember { GbRomLibrary(File(filesDir, "gb-library"), contentResolver) }
            var libraryGames by remember { mutableStateOf(emptyList<GameEntry>()) }
            var activeGameId by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(romLibrary) {
                try {
                    libraryGames = withContext(Dispatchers.IO) {
                        romLibrary.list().map { GameEntry(it.id, it.filename, GamePlatform.GB) }
                    }
                } catch (error: Exception) {
                    Log.e("RavenEmuLibrary", "Cannot open library index", error)
                }
            }
            var userSettings by remember { mutableStateOf(settingsStore.read()) }
            val appVersion = remember {
                runCatching {
                    packageManager.getPackageInfo(packageName, 0).versionName
                }.getOrNull() ?: "0.1.0-preview"
            }
            var loadedGame by remember { mutableStateOf<LoadedGameInfo?>(null) }
            var loading by remember { mutableStateOf(false) }
            var importError by remember { mutableStateOf<String?>(null) }
            var videoEnabled by remember { mutableStateOf(false) }
            var frame by remember { mutableStateOf<ImageBitmap?>(null) }
            var videoError by remember { mutableStateOf<String?>(null) }
            var videoMetrics by remember { mutableStateOf<GbVideoMetrics?>(null) }
            var audioStatus by remember { mutableStateOf<String?>(null) }
            var stateOperationBusy by remember { mutableStateOf(false) }
            var stateSlotsLoading by remember { mutableStateOf(false) }
            var stateSlots by remember { mutableStateOf(emptyList<GbStateSlot>()) }
            var restoringState by remember { mutableStateOf(false) }
            var pausedForGameMenu by remember { mutableStateOf(false) }
            var stateRevision by remember { mutableStateOf(0) }
            val scope = rememberCoroutineScope()
            val refreshSlots: () -> Unit = {
                stateSlotsLoading = true
                stateSlots = emptyList()
                scope.launch {
                    try {
                        stateSlots = withContext(Dispatchers.IO) {
                            val dateFormat = DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM, DateFormat.SHORT,
                            )
                            gameSession.listStateSlots().map { stored ->
                                val thumbnail = stored.preview?.let { bytes ->
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        ?.asImageBitmap()
                                }
                                GbStateSlot(
                                    index = stored.slot,
                                    savedAt = if (!stored.occupied) null else
                                        stored.savedAtMillis?.let {
                                            dateFormat.format(Date(it))
                                        } ?: "Date inconnue",
                                    thumbnail = thumbnail,
                                )
                            }
                        }
                    } catch (error: Exception) {
                        Log.e("RavenEmuSaves", "Cannot list save slots", error)
                        Toast.makeText(
                            applicationContext,
                            "Impossible de lire les emplacements",
                            Toast.LENGTH_LONG,
                        ).show()
                    } finally {
                        stateSlotsLoading = false
                    }
                }
            }
            val exportAudio = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/plain"),
            ) { uri ->
                if (uri != null) {
                    try {
                        contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(lastAudioReport.toByteArray(Charsets.UTF_8))
                        } ?: Log.e("RavenEmuAudio", "Unable to open diagnostic destination")
                    } catch (error: Exception) {
                        Log.e("RavenEmuAudio", "Unable to save audio report", error)
                    }
                }
            }

            LaunchedEffect(
                videoEnabled, loadedGame, foreground,
                restoringState, stateRevision, pausedForGameMenu,
            ) {
                if (!videoEnabled || loadedGame == null) {
                    frame = null
                    videoMetrics = null
                    return@LaunchedEffect
                }
                // Keep the last image visible behind the ROM-style menu sheet.
                // A game menu pauses both the native clock and AudioTrack,
                // preventing UI interaction from building an audio backlog.
                if (!foreground || restoringState || pausedForGameMenu) return@LaunchedEffect
                videoMetrics = null

                videoError = null
                audioStatus = null
                val currentGame = loadedGame ?: return@LaunchedEffect
                val playbackSettings = userSettings
                val targetFps = (gameSession.refreshRateHz() ?: 59.7).coerceIn(1.0, 120.0)
                val periodNanos = (1_000_000_000.0 / targetFps).roundToLong()
                val stats = GbVideoMetricsAccumulator(targetFps)
                val pixels = IntArray(currentGame.width * currentGame.height)
                // More than one GB frame of interleaved stereo, reused per frame.
                val samples = ShortArray(4096)
                var due = System.nanoTime()
                var lastBatteryCheckNanos = due
                var output: AndroidGbAudioOutput? = null

                try {
                    try {
                        val rate = gameSession.audioRateHz()
                            ?: throw IllegalStateException("Aucune piste audio GB active.")
                        output = AndroidGbAudioOutput(
                            applicationContext, rate, targetFps,
                            playbackSettings.audioEnabled, playbackSettings.audioVolume,
                        )
                        activeAudio = output
                        lastAudioReport = "Session audio démarrée; diagnostic en cours.\n"
                        audioStatus = "Audio GB optimisé (préversion)"
                    } catch (error: Exception) {
                        audioStatus = "Audio indisponible : ${error.message ?: "Erreur AudioTrack"}"
                    }

                    while (isActive) {
                        try {
                            // One synchronized native frame + PCM read, then one
                            // non-blocking audio submission on the worker.
                            val sink = output
                            val captured = withContext(Dispatchers.Default) {
                                val nativeStart = System.nanoTime()
                                val sampleCount =
                                    gameSession.nextFrameWithAudioInto(pixels, samples)
                                        ?: return@withContext null
                                val nativeElapsed = System.nanoTime() - nativeStart
                                var audioFailure: String? = null
                                if (sink != null) {
                                    try {
                                        sink.submit(samples, sampleCount)
                                    } catch (error: Exception) {
                                        audioFailure = error.message ?: "Sortie audio interrompue"
                                        sink.stopNow()
                                    }
                                }
                                val conversionStart = System.nanoTime()
                                GbDisplayFilter.apply(pixels, playbackSettings)
                                val image = Bitmap.createBitmap(
                                    pixels, currentGame.width, currentGame.height,
                                    Bitmap.Config.ARGB_8888,
                                ).asImageBitmap()
                                CapturedGbFrame(
                                    bitmap = image,
                                    coreTimeNanos = nativeElapsed,
                                    conversionTimeNanos = System.nanoTime() - conversionStart,
                                    audioFailure = audioFailure,
                                )
                            } ?: break

                            frame = captured.bitmap
                            if (captured.audioFailure != null) {
                                audioStatus = "Audio interrompu : ${captured.audioFailure}"
                                if (activeAudio === output) activeAudio = null
                                output?.let { lastAudioReport = it.exportReport() }
                                output?.close()
                                output = null
                            }
                            val now = System.nanoTime()
                            if (now - lastBatteryCheckNanos >= 5_000_000_000L) {
                                lastBatteryCheckNanos = now
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        gameSession.saveBatteryIfDirty()
                                    } catch (error: Exception) {
                                        Log.e("RavenEmuSaves", "Battery autosave failed", error)
                                    }
                                }
                            }
                            val sample = stats.record(
                                now,
                                captured.coreTimeNanos,
                                captured.conversionTimeNanos,
                            )
                            if (sample != null) {
                                videoMetrics = sample
                                if (captured.audioFailure == null) {
                                    output?.let {
                                        lastAudioReport = it.exportReport()
                                        Log.d("RavenEmuAudio", it.diagnostics())
                                    }
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            videoError = error.message ?: "Erreur de rendu Game Boy."
                            frame = null
                            break
                        }

                        due += periodNanos
                        val remaining = due - System.nanoTime()
                        if (remaining > 0) {
                            delay((remaining / 1_000_000L).coerceAtLeast(1L))
                        } else if (remaining < -periodNanos) {
                            due = System.nanoTime()
                        }
                    }
                } finally {
                    if (activeAudio === output) activeAudio = null
                    output?.let { lastAudioReport = it.exportReport() }
                    output?.close()
                }
            }

            val documentPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null && !loading) {
                    loading = true
                    importError = null
                    scope.launch {
                        try {
                            val (info, stored) = withContext(Dispatchers.IO) {
                                val (name, bytes) = romLibrary.readSelected(uri)
                                // Validate with native RavenGB before indexing the cartridge.
                                val info = gameSession.loadBytes(name, bytes)
                                val stored = romLibrary.add(name, bytes)
                                info to stored
                            }
                            loadedGame = info
                            activeGameId = stored.id
                            libraryGames = withContext(Dispatchers.IO) {
                                romLibrary.list().map {
                                    GameEntry(it.id, it.filename, GamePlatform.GB)
                                }
                            }
                        } catch (exception: Exception) {
                            importError = exception.message ?: "Échec du chargement de la ROM."
                        } finally {
                            loading = false
                        }
                    }
                }
            }

            RavenEmuApp(
                settings = userSettings,
                onSettingsChange = { updated ->
                    userSettings = updated.normalized()
                    settingsStore.write(userSettings)
                },
                appVersion = appVersion,
                onOpenLink = { url ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (error: Exception) {
                        Log.e("RavenEmuSettings", "Cannot open information link", error)
                        Toast.makeText(
                            applicationContext, "Impossible d'ouvrir le lien",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                onImportRom = {
                    if (!loading) documentPicker.launch(arrayOf("*/*"))
                },
                loadedGame = loadedGame,
                libraryGames = libraryGames,
                onLaunchLibraryGame = { id, onReady ->
                    if (!loading && !videoEnabled) {
                        loading = true
                        importError = null
                        scope.launch {
                            try {
                                if (activeGameId != id || loadedGame == null) {
                                    val entry = libraryGames.firstOrNull { it.id == id }
                                        ?: error("Ce jeu n'est plus dans la bibliothèque.")
                                    val result = withContext(Dispatchers.IO) {
                                        gameSession.loadBytes(entry.title, romLibrary.readRom(id))
                                    }
                                    loadedGame = result
                                    activeGameId = id
                                }
                                onReady()
                            } catch (error: Exception) {
                                Log.e("RavenEmuLibrary", "Cannot load stored GB ROM", error)
                                importError = error.message ?: "Impossible de lancer ce jeu."
                            } finally {
                                loading = false
                            }
                        }
                    }
                },
                importBusy = loading,
                importError = importError,
                onStartVideo = { videoEnabled = true },
                onStopVideo = {
                    activeAudio?.stopNow()
                    gameSession.releaseAllButtons()
                    videoEnabled = false
                    pausedForGameMenu = false
                    frame = null
                    videoMetrics = null
                    audioStatus = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            gameSession.saveBatteryIfDirty()
                        } catch (error: Exception) {
                            Log.e("RavenEmuSaves", "Battery save on player exit failed", error)
                        }
                    }
                },
                onButtonChange = { button: GbButton, pressed: Boolean ->
                    if (videoEnabled && foreground && !pausedForGameMenu) {
                        gameSession.setButton(button, pressed)
                    }
                },
                videoFrame = frame,
                videoError = videoError,
                videoMetrics = videoMetrics,
                onExportAudioDiagnostics = {
                    lastAudioReport = activeAudio?.exportReport() ?: lastAudioReport
                    exportAudio.launch("ravenemu-audio-diagnostic.txt")
                },
                onGameMenuVisibilityChange = { open ->
                    if (open) {
                        refreshSlots()
                        // Preserve a useful report before closing the track.
                        activeAudio?.let {
                            lastAudioReport = it.exportReport()
                            it.stopNow()
                        }
                        gameSession.releaseAllButtons()
                    }
                    pausedForGameMenu = open
                },
                stateSlots = stateSlots,
                stateSlotsLoading = stateSlotsLoading,
                stateOperationBusy = stateOperationBusy,
                onSaveState = { slot ->
                    if (!stateOperationBusy && loadedGame != null && videoEnabled) {
                        stateOperationBusy = true
                        val screenshot = frame?.asAndroidBitmap()
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val preview = screenshot?.let { image ->
                                        val miniature = Bitmap.createScaledBitmap(
                                            image, 160, 144, false,
                                        )
                                        try {
                                            ByteArrayOutputStream().use { output ->
                                                if (miniature.compress(
                                                        Bitmap.CompressFormat.PNG, 100, output,
                                                    )
                                                ) output.toByteArray() else null
                                            }
                                        } finally {
                                            if (miniature !== image) miniature.recycle()
                                        }
                                    }
                                    gameSession.saveStateSlot(slot, preview)
                                }
                                Toast.makeText(
                                    applicationContext,
                                    "État sauvegardé (emplacement ${slot + 1})",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                refreshSlots()
                            } catch (error: Exception) {
                                Log.e("RavenEmuSaves", "Save state failed", error)
                                Toast.makeText(
                                    applicationContext,
                                    "Échec du save state : ${error.message ?: "erreur inconnue"}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } finally {
                                stateOperationBusy = false
                            }
                        }
                    }
                },
                onLoadState = { slot ->
                    if (!stateOperationBusy && loadedGame != null && videoEnabled) {
                        stateOperationBusy = true
                        restoringState = true
                        activeAudio?.stopNow()
                        gameSession.releaseAllButtons()
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { gameSession.loadStateSlot(slot) }
                                Toast.makeText(
                                    applicationContext, "État chargé (emplacement ${slot + 1})",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } catch (error: Exception) {
                                Log.e("RavenEmuSaves", "Load state failed", error)
                                Toast.makeText(
                                    applicationContext,
                                    "Chargement impossible : ${error.message ?: "erreur inconnue"}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } finally {
                                stateRevision++
                                restoringState = false
                                stateOperationBusy = false
                            }
                        }
                    }
                },
                onDeleteState = { slot ->
                    if (!stateOperationBusy && loadedGame != null && videoEnabled) {
                        stateOperationBusy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { gameSession.deleteStateSlot(slot) }
                                Toast.makeText(
                                    applicationContext,
                                    "Emplacement ${slot + 1} supprimé",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                refreshSlots()
                            } catch (error: Exception) {
                                Log.e("RavenEmuSaves", "Delete state failed", error)
                                Toast.makeText(
                                    applicationContext,
                                    "Suppression impossible : ${error.message ?: "erreur inconnue"}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } finally {
                                stateOperationBusy = false
                            }
                        }
                    }
                },
            )
        }
    }

    override fun onDestroy() {
        activeAudio?.stopNow()
        gameSession.close()
        super.onDestroy()
    }
}
