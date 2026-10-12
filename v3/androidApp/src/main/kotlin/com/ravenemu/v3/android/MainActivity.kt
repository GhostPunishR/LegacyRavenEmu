package com.ravenemu.v3.android

import android.graphics.Bitmap
import android.os.Bundle
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

    override fun onResume() {
        super.onResume()
        foreground = true
    }

    override fun onPause() {
        foreground = false
        activeAudio?.stopNow()
        gameSession.releaseAllButtons()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        gameSession = AndroidGbSession(contentResolver)

        setContent {
            var loadedGame by remember { mutableStateOf<LoadedGameInfo?>(null) }
            var loading by remember { mutableStateOf(false) }
            var importError by remember { mutableStateOf<String?>(null) }
            var videoEnabled by remember { mutableStateOf(false) }
            var frame by remember { mutableStateOf<ImageBitmap?>(null) }
            var videoError by remember { mutableStateOf<String?>(null) }
            var videoMetrics by remember { mutableStateOf<GbVideoMetrics?>(null) }
            var audioStatus by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(videoEnabled, loadedGame, foreground) {
                frame = null
                videoMetrics = null
                if (!videoEnabled || !foreground || loadedGame == null) return@LaunchedEffect

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
                var output: AndroidGbAudioOutput? = null

                try {
                    try {
                        val rate = gameSession.audioRateHz()
                            ?: throw IllegalStateException("Aucune piste audio GB active.")
                        output = AndroidGbAudioOutput(applicationContext, rate, targetFps)
                        activeAudio = output
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
                                output?.close()
                                output = null
                            }
                            val sample = stats.record(
                                System.nanoTime(),
                                captured.coreTimeNanos,
                                captured.conversionTimeNanos,
                            )
                            if (sample != null) videoMetrics = sample
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
                    frame = null
                    videoMetrics = null
                    audioStatus = null
                },
                onButtonChange = { button: GbButton, pressed: Boolean ->
                    if (videoEnabled && foreground) gameSession.setButton(button, pressed)
                },
                videoFrame = frame,
                videoError = videoError,
                videoMetrics = videoMetrics,
                audioStatus = audioStatus,
            )
        }
    }

    override fun onDestroy() {
        activeAudio?.stopNow()
        gameSession.close()
        super.onDestroy()
    }
}
