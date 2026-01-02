package com.trimsytrack.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.location.Geocoder
import androidx.core.app.Person
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.trimsytrack.AppGraph
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.trimsytrack.notifications.Notifications
import com.trimsytrack.notifications.TestPingActionReceiver
import com.trimsytrack.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ProfileLocationScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val activeProfileId by AppGraph.settings.profileId.collectAsState(initial = "")

    // Test ping cooldown should not be persisted.
    var lastTestPingAtMillis by rememberSaveable { mutableStateOf(0L) }

    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val cooldownMillis = 5 * 60 * 1000L
    val remainingMillis = (cooldownMillis - (nowMillis - lastTestPingAtMillis)).coerceAtLeast(0L)
    val canPingNow = remainingMillis == 0L

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }

    var status by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasLocationPermission = granted
            if (!granted) {
                status = "Location permission denied"
            }
        },
    )

    fun formatRemaining(millis: Long): String {
        val totalSeconds = (millis / 1000).toInt().coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    fun pingNow() {
        if (!hasLocationPermission) {
            status = "Location permission required"
            return
        }
        if (activeProfileId.isBlank()) {
            status = "No active profile"
            return
        }
        if (!canPingNow) {
            status = "Try again in ${formatRemaining(remainingMillis)}"
            return
        }

        status = "Pinging…"
        val client = LocationServices.getFusedLocationProviderClient(context)
        @SuppressLint("MissingPermission")
        val task = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        task.addOnSuccessListener { location ->
            if (location == null) {
                status = "Could not get location"
                return@addOnSuccessListener
            }

            val notificationId = 910_001

            val dismissIntent = Intent(context, TestPingActionReceiver::class.java).apply {
                action = TestPingActionReceiver.ACTION_DISMISS
                putExtra(TestPingActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val dismissPi = PendingIntent.getBroadcast(
                context,
                910_002,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val laterIntent = Intent(context, TestPingActionReceiver::class.java).apply {
                action = TestPingActionReceiver.ACTION_LATER
                putExtra(TestPingActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val laterPi = PendingIntent.getBroadcast(
                context,
                910_003,
                laterIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            scope.launch {
                val lat = location.latitude
                val lng = location.longitude

                val addressLine = withContext(Dispatchers.IO) {
                    runCatching {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val results = geocoder.getFromLocation(lat, lng, 1)
                        val addr = results?.firstOrNull()
                        addr?.getAddressLine(0)
                    }.getOrNull()
                }

                Notifications.ensureChannels(context)

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("openTestPing", true)
                    putExtra("testPingAddress", addressLine.orEmpty())
                    putExtra("testPingLat", lat)
                    putExtra("testPingLng", lng)
                }
                val openPi = PendingIntent.getActivity(
                    context,
                    910_001,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val coordsText = buildList {
                    if (!addressLine.isNullOrBlank()) add("Address: $addressLine")
                    add("Lat: $lat")
                    add("Lng: $lng")
                }.joinToString("\n")

                val appPerson = Person.Builder().setName("Trimsy").build()
                val storeName = "Test Store"
                val messageText = "Add business trip at $storeName?"
                val style = NotificationCompat.MessagingStyle(appPerson)
                    .setConversationTitle("Trimsy")
                    .addMessage(messageText, System.currentTimeMillis(), appPerson)
                    .addMessage(coordsText, System.currentTimeMillis(), appPerson)

                val builder = Notifications.baseBuilder(context)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentTitle("Trimsy")
                    .setContentText(messageText)
                    .setContentIntent(openPi)
                    .setStyle(style)
                    .setOnlyAlertOnce(true)
                    .addAction(
                        NotificationCompat.Action.Builder(
                            com.trimsytrack.R.drawable.ic_action_add,
                            "Add",
                            openPi,
                        ).build()
                    )
                    .addAction(
                        NotificationCompat.Action.Builder(
                            com.trimsytrack.R.drawable.ic_action_later,
                            "Later",
                            laterPi,
                        ).build()
                    )
                    .addAction(
                        NotificationCompat.Action.Builder(
                            com.trimsytrack.R.drawable.ic_action_close,
                            "Dismiss",
                            dismissPi,
                        ).build()
                    )

                Notifications.notify(context, notificationId, builder)

                // In-memory cooldown only.
                lastTestPingAtMillis = System.currentTimeMillis()
                status = null
            }
        }.addOnFailureListener {
            status = "Could not get location"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Test Ping",
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Text("Back")
            }
        }

        if (!hasLocationPermission) {
            Text(
                "Allow location to send a test notification.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                Text("Allow location")
            }
        }

        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (!canPingNow) {
            Text(
                "Available again in ${formatRemaining(remainingMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }

        Button(
            onClick = { scope.launch { pingNow() } },
            enabled = hasLocationPermission && canPingNow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test Ping")
        }
    }
}
