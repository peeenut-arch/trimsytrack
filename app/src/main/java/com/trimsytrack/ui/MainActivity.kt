package com.trimsytrack.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.R
import com.trimsytrack.ui.theme.TrimsyTheme
import android.content.Intent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val currentIntentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        currentIntentState.value = intent
        setContent {
            val darkModeEnabled by AppGraph.settings.darkModeEnabled.collectAsState(initial = true)
            val useNewUi by AppGraph.settings.useNewUi.collectAsState(initial = false)

            // Full-screen in-app splash so the provided 1080x1920 splash.png is shown as intended.
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                // Keep it long enough to be visible but short enough to not feel sluggish.
                delay(650)
                showSplash = false
            }

            TrimsyTheme(darkTheme = darkModeEnabled, useNewUi = useNewUi) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        AppNavHost(intent = currentIntentState.value ?: intent)
                    }

                    if (showSplash) {
                        Image(
                            painter = painterResource(id = R.drawable.splash),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            // Crop preserves aspect ratio (no vertical squish) while filling the screen.
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (!BuildConfig.DEBUG) return

        val extrasKeys = runCatching { data?.extras?.keySet()?.joinToString(prefix = "[", postfix = "]") }.getOrNull()
        Log.d(
            "TrimsyTrack",
            "onActivityResult rc=$requestCode result=$resultCode dataNull=${data == null} " +
                "action=${data?.action} data=${data?.dataString} type=${data?.type} extras=$extrasKeys",
        )
    }
}
