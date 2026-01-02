package com.trimsytrack.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.trimsytrack.AppGraph
import com.trimsytrack.ui.theme.TrimsyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkModeEnabled by AppGraph.settings.darkModeEnabled.collectAsState(initial = false)

            TrimsyTheme(darkTheme = darkModeEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    AppNavHost(intent = intent)
                }
            }
        }
    }
}
