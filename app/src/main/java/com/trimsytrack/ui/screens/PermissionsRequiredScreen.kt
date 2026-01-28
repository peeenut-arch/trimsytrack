package com.trimsytrack.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trimsytrack.system.AppPermissionChecks

@Composable
fun PermissionsRequiredScreen(
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var missing by remember { mutableStateOf(AppPermissionChecks.missingCritical(context)) }
    var batteryMissing by remember { mutableStateOf(!AppPermissionChecks.isBatteryOptimizationDisabled(context)) }

    var didContinue by rememberSaveable { mutableStateOf(false) }
    var didTryBackgroundLocation by rememberSaveable { mutableStateOf(false) }

    fun continueOnce() {
        if (didContinue) return
        didContinue = true
        onContinue()
    }

    val requestFineLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            missing = AppPermissionChecks.missingCritical(context)
            batteryMissing = !AppPermissionChecks.isBatteryOptimizationDisabled(context)
            if (missing.isEmpty()) continueOnce()
        },
    )

    val requestNotificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            missing = AppPermissionChecks.missingCritical(context)
            batteryMissing = !AppPermissionChecks.isBatteryOptimizationDisabled(context)
            if (missing.isEmpty()) continueOnce()
        },
    )

    val requestBackgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            missing = AppPermissionChecks.missingCritical(context)
            batteryMissing = !AppPermissionChecks.isBatteryOptimizationDisabled(context)
            if (missing.isEmpty()) continueOnce()
        },
    )

    fun requestBackgroundLocationNow() {
        if (android.os.Build.VERSION.SDK_INT < 29) return
        didTryBackgroundLocation = true
        requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    LaunchedEffect(Unit) {
        // In case permissions were granted just before navigation.
        missing = AppPermissionChecks.missingCritical(context)
        batteryMissing = !AppPermissionChecks.isBatteryOptimizationDisabled(context)
        if (missing.isEmpty()) continueOnce()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                missing = AppPermissionChecks.missingCritical(context)
                batteryMissing = !AppPermissionChecks.isBatteryOptimizationDisabled(context)
                if (missing.isEmpty()) continueOnce()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Permissions needed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "TrimsyTRACK needs these turned on to work reliably. After reinstall, Android may reset them — so we check on every login.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )

        Spacer(Modifier.height(4.dp))

        // Wizard: pick the next missing requirement in a deterministic order.
        val nextMissing = remember(missing, didTryBackgroundLocation) {
            val byKey = missing.associateBy { it.key }
            listOfNotNull(
                byKey["location_permission_foreground"],
                byKey["notifications_permission"],
                byKey["location_services"],
                byKey["notifications_disabled"],
                byKey["location_permission_background"],
            ).firstOrNull()
        }

        if (nextMissing == null) {
            Text("All set.")
            if (batteryMissing) {
                Text(
                    text = "Battery optimization is still recommended for best reliability. You can adjust it later in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            }
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
            return@Column
        }

        Text(nextMissing.title, fontWeight = FontWeight.SemiBold)
        Text(
            nextMissing.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )

        Spacer(Modifier.height(6.dp))

        val buttonText = when (nextMissing.key) {
            "location_permission_foreground" -> "Grant location permission"
            "notifications_permission" -> "Grant notifications permission"
            "location_services" -> "Open Location settings"
            "notifications_disabled" -> "Open notification settings"
            "location_permission_background" -> if (!didTryBackgroundLocation) "Grant 'Always allow' location" else "Open app settings"
            else -> "Continue"
        }

        val onClick = when (nextMissing.key) {
            "location_permission_foreground" -> ({ requestFineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) })
            "notifications_permission" -> ({
                if (Build.VERSION.SDK_INT >= 33) {
                    requestNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Should not happen (gated in AppPermissionChecks), but keep it safe.
                    missing = AppPermissionChecks.missingCritical(context)
                    batteryMissing = !AppPermissionChecks.isBatteryOptimizationDisabled(context)
                    if (missing.isEmpty()) continueOnce()
                }
            })
            "location_services" -> ({ openLocationSettings() })
            "notifications_disabled" -> ({ openNotificationSettings() })
            "location_permission_background" -> ({
                if (!didTryBackgroundLocation) requestBackgroundLocationNow() else openAppSettings()
            })
            else -> ({
                missing = AppPermissionChecks.missingCritical(context)
                batteryMissing = !AppPermissionChecks.isBatteryOptimizationDisabled(context)
                if (missing.isEmpty()) continueOnce()
            })
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
            ) {
                Text(buttonText)
            }
        }

        Text(
            text = "After enabling it, return to this screen — it will continue automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        )
    }
}
