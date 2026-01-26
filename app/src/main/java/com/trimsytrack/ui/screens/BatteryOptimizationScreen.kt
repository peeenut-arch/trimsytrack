package com.trimsytrack.ui.screens

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryOptimizationScreen(
    onOpenBatterySettings: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val ignoring = isIgnoringBatteryOptimizations(context)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Battery optimization") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "To capture pings reliably, TrimsyTRACK should be allowed to run in the background.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    "Your Android version does not use the modern battery-optimization system."
                } else if (ignoring) {
                    "Status: Unrestricted (battery optimization disabled for this app)."
                } else {
                    "Status: Optimized (Android may stop background tracking)."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onOpenBatterySettings,
                modifier = Modifier.fillMaxWidth(),
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M,
            ) {
                Text("Open battery settings")
            }

            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
