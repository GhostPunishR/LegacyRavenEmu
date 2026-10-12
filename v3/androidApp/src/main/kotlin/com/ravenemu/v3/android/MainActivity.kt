package com.ravenemu.v3.android

import android.os.Bundle
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToLong
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ravenemu.v3.shared.LoadedGameInfo
import com.ravenemu.v3.shared.RavenEmuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var gameSession: AndroidGbSession
    private var foreground by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        foreground = true
    }

    override fun onPause() {
        foreground = false
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameSession = AndroidGbSession(contentResolver)

        setContent {
            var loadedGame by remember { mutableStateOf<LoadedGameInfo?>(null) }
            var loading by remember { mutableStateOf(false) }
            var importError by remember { mutableStateOf<String?>(null) }
            var videoEnabled by remember { mutableStateOf(false) }
            var frame by remember { mutableStateOf<ImageBitmap?>(null) }
            var videoError by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            // The coroutine is active only on the player screen while the
            // Activity is in the foreground. Native calls and buffer creation
            // happen off the main thread, protected by AndroidGbSession's lock.
            LaunchedEffect(videoEnabled, loadedGame, foreground) {
                if (!videoEnabled || !foreground || loadedGame == null) {
                    frame = null
                    return@LaunchedEffect
                }
                videoError = null
                val fps = gameSession.refreshRateHz() ?: 59.7
                val periodNanos = (1_000_000_000.0 / fps.coerceIn(1.0, 120.0)).roundToLong()
                var due = System.nanoTime()
                while (isActive) {
                    try {
                        val rendered = withContext(Dispatchers.Default) {
                            gameSession.nextFrame()?.let { native ->
                                Bitmap.createBitmap(
                                    native.argb,
                                    native.width,
                                    native.height,
                                    Bitmap.Config.ARGB_8888,
                                ).asImageBitmap()
                            }
                        } ?: break
                        frame = rendered
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
                        // Don't accumulate a backlog when the phone is slow.
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
                    videoEnabled = false
                    frame = null
                },
                videoFrame = frame,
                videoError = videoError,
            )
        }
    }

    override fun onDestroy() {
        gameSession.close()
        super.onDestroy()
    }
}
