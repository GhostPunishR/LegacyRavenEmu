package com.ravenemu.v3.android

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameSession = AndroidGbSession(contentResolver)

        setContent {
            var loadedGame by remember { mutableStateOf<LoadedGameInfo?>(null) }
            var loading by remember { mutableStateOf(false) }
            var importError by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

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
            )
        }
    }

    override fun onDestroy() {
        gameSession.close()
        super.onDestroy()
    }
}
