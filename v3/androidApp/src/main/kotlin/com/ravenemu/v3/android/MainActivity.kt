package com.ravenemu.v3.android

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import com.ravenemu.v3.shared.GbButton
import com.ravenemu.v3.shared.GbVideoMetrics
import com.ravenemu.v3.shared.GbVideoMetricsAccumulator
import com.ravenemu.v3.shared.LoadedGameInfo
import com.ravenemu.v3.shared.RavenEmuApp
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
            var loadedGame by remember { mutableStateOf<LoadedGameInfo?>(null) }
            var loading by remember { mutableStateOf(false) }
            var importError by remember { mutableStateOf<String?>(null) }
            var videoEnabled by remember { mutableStateOf(false) }
            var frame by remember { mutableStateOf<ImageBitmap?>(null) }
            var videoError by remember { mutableStateOf<String?>(null) }
            var videoMetrics by remember { mutableStateOf<GbVideoMetrics?>(null) }
            var audioStatus by remember { mutableStateOf<String?>(null) }
            var stateOperationBusy by remember { mutableStateOf(false) }
            var restoringState by remember { mutableStateOf(false) }
            var pausedForGameMenu by remember { mutableStateOf(false) }
            var stateRevision by remember { mutableStateOf(0) }
            val scope = rememberCoroutineScope()
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
                        output = AndroidGbAudioOutput(applicationContext, rate, targetFps)
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
                            loadedGame = withContext(Dispatchers.IO) {
                                gameSession.load(uri)
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
                onImportRom = {
                    if (!loading) documentPicker.launch(arrayOf("*/*"))
                },
                loadedGame = loadedGame,
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
                        // Preserve a useful report before closing the track.
                        activeAudio?.let {
                            lastAudioReport = it.exportReport()
                            it.stopNow()
                        }
                        gameSession.releaseAllButtons()
                    }
                    pausedForGameMenu = open
                },
                onSaveState = {
                    if (!stateOperationBusy && loadedGame != null && videoEnabled) {
                        stateOperationBusy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { gameSession.saveStateSlot() }
                                Toast.makeText(
                                    applicationContext, "État sauvegardé (emplacement 1)",
                                    Toast.LENGTH_SHORT,
                                ).show()
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
                onLoadState = {
                    if (!stateOperationBusy && loadedGame != null && videoEnabled) {
                        stateOperationBusy = true
                        restoringState = true // cancel video loop and freshen AudioTrack on resume
                        activeAudio?.stopNow()
                        gameSession.releaseAllButtons()
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { gameSession.loadStateSlot() }
                                Toast.makeText(
                                    applicationContext, "État chargé (emplacement 1)",
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
                                // Even a very fast load must restart the audio pipeline.
                                stateRevision++
                                restoringState = false
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
