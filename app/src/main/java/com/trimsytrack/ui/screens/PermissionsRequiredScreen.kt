package com.trimsytrack.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            missing = AppPermissionChecks.missingCritical(context)
        },
    )

    fun requestNow() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionsLauncher.launch(perms.toTypedArray())
    }

    val requestBackgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            missing = AppPermissionChecks.missingCritical(context)
        },
    )

    fun requestBackgroundLocationNow() {
        if (android.os.Build.VERSION.SDK_INT < 29) return
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

    LaunchedEffect(Unit) {
        // In case permissions were granted just before navigation.
        missing = AppPermissionChecks.missingCritical(context)
        if (missing.isEmpty()) onContinue()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                missing = AppPermissionChecks.missingCritical(context)
                if (missing.isEmpty()) onContinue()
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

        if (missing.isEmpty()) {
            Text("All set.")
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
            return@Column
        }

        missing.forEach { item ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(item.title, fontWeight = FontWeight.SemiBold)
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
                when (item.key) {
                    "location_services" -> {
                        OutlinedButton(onClick = ::openLocationSettings) {
                            Text("Open Location settings")
                        }
                    }
                    "location_permission_background" -> {
                        OutlinedButton(
                            onClick = ::requestBackgroundLocationNow,
                        ) {
                            Text("Request 'Always allow' location")
                        }
                        OutlinedButton(onClick = ::openAppSettings) {
                            Text("Open app settings")
                        }
                    }
                    "notifications_disabled" -> {
                        OutlinedButton(onClick = ::openNotificationSettings) {
                            Text("Open notification settings")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = ::requestNow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Request permissions")
        }

        OutlinedButton(
            onClick = {
                openAppSettings()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open app settings")
        }

        OutlinedButton(
            onClick = {
                missing = AppPermissionChecks.missingCritical(context)
                if (missing.isEmpty()) onContinue()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("I enabled them")
        }
    }
}
