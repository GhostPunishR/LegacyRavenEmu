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
)

class MainActivity : ComponentActivity() {
    private lateinit var gameSession: AndroidGbSession
    private var foreground by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        foreground = true
    }

    override fun onPause() {
        foreground = false
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
            val scope = rememberCoroutineScope()

            LaunchedEffect(videoEnabled, loadedGame, foreground) {
                frame = null
                videoMetrics = null
                if (!videoEnabled || !foreground || loadedGame == null) return@LaunchedEffect

                videoError = null
                val currentGame = loadedGame ?: return@LaunchedEffect
                val targetFps = (gameSession.refreshRateHz() ?: 59.7).coerceIn(1.0, 120.0)
                val periodNanos = (1_000_000_000.0 / targetFps).roundToLong()
                val stats = GbVideoMetricsAccumulator(targetFps)
                val pixels = IntArray(currentGame.width * currentGame.height)
                var due = System.nanoTime()

                while (isActive) {
                    try {
                        // Only emulation and pixel conversion run on the worker;
                        // Compose state is assigned on the UI coroutine.
                        val captured = withContext(Dispatchers.Default) {
                            val nativeStart = System.nanoTime()
                            val hasFrame = gameSession.nextFrameInto(pixels)
                            val nativeElapsed = System.nanoTime() - nativeStart
                            if (!hasFrame) null else {
                                val conversionStart = System.nanoTime()
                                val image = Bitmap.createBitmap(
                                    pixels,
                                    currentGame.width,
                                    currentGame.height,
                                    Bitmap.Config.ARGB_8888,
                                ).asImageBitmap()
                                CapturedGbFrame(
                                    image,
                                    nativeElapsed,
                                    System.nanoTime() - conversionStart,
                                )
                            }
                        } ?: break

                        frame = captured.bitmap
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
                        // If frames are slower than real time, show the measured
                        // speed instead of trying to process an infinite backlog.
                        due = System.nanoTime()
                    }
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
                    gameSession.releaseAllButtons()
                    videoEnabled = false
                    frame = null
                    videoMetrics = null
                },
                onButtonChange = { button: GbButton, pressed: Boolean ->
                    if (videoEnabled && foreground) {
                        gameSession.setButton(button, pressed)
                    }
                },
                videoFrame = frame,
                videoError = videoError,
                videoMetrics = videoMetrics,
            )
        }
    }

    override fun onDestroy() {
        gameSession.close()
        super.onDestroy()
    }
}
