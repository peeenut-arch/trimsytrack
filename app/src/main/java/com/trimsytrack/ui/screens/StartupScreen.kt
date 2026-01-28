package com.trimsytrack.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.R
import com.trimsytrack.backend.BackendBlockedException
import com.trimsytrack.data.driverdata.DriverDataSnapshotUploadWorker
import com.trimsytrack.data.trackevents.TrackEventsCapabilityProbeWorker
import com.trimsytrack.data.trackevents.TrackEventsOutboxWorker
import com.trimsytrack.debug.DebuggLogStore
import com.trimsytrack.system.BackendBaselineProbe
import com.trimsytrack.system.HardBlockCode
import com.trimsytrack.ui.theme.TrimsyGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface StartupState {
    data object Loading : StartupState
    data class Blocked(val code: HardBlockCode, val message: String) : StartupState
    data class Error(val message: String) : StartupState
    data object Ready : StartupState
}

@Composable
fun StartupScreen(
    onOpenAuth: () -> Unit,
    onReady: () -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var state: StartupState by remember { mutableStateOf(StartupState.Loading) }
    var lastError: String? by remember { mutableStateOf(null) }

    suspend fun runHandshake() {
        state = StartupState.Loading
        lastError = null

        for (attempt in 0..1) {
            try {
                val result = AppGraph.systemCallables.handshakeGet()

                Log.i(
                    "Startup",
                    "Handshake ok: uid=${result.identityUid} email=${result.identityEmail ?: ""} writesEnabled=${result.writesEnabled} safetyMode=${result.safetyModeEnabled}",
                )
                DebuggLogStore.add(
                    tag = "Handshake",
                    message = "ok uid=${result.identityUid} email=${result.identityEmail ?: ""} writesEnabled=${result.writesEnabled} safety=${result.safetyModeEnabled}",
                )

                // Identity is UID-anchored; treat missing UID as blocked.
                if (result.identityUid.isBlank()) {
                    Log.w("Startup", "Blocked: missing uid")
                    state = StartupState.Blocked(
                        code = HardBlockCode.ACCOUNT_CONFLICT,
                        message = "This account has no uid. Please sign out and sign in again.",
                    )
                    return
                }

                AppGraph.settings.setBackendProtocolVersion(result.protocolVersion)
                AppGraph.settings.setBackendProtocolSupportedRange(
                    minSupported = result.protocol?.minSupported,
                    maxSupported = result.protocol?.maxSupported,
                )
                AppGraph.settings.setBackendIdentityUid(result.identityUid)
                AppGraph.settings.setBackendIdentityEmail(result.identityEmail.orEmpty())
                // Handshake is authoritative; do not allow a locally latched safety flag to persist.
                AppGraph.settings.setBackendWritesEnabled(result.writesEnabled)
                AppGraph.settings.setBackendSafetyModeEnabled(result.safetyModeEnabled)
                AppGraph.settings.setBackendSafetyModeReason(result.safetyModeReason.orEmpty())
                AppGraph.settings.setBackendDeploymentMetadata(
                    service = result.deployment?.service,
                    revision = result.deployment?.revision,
                    functionTarget = result.deployment?.functionTarget,
                    serverTimeIso = result.deployment?.serverTimeIso,
                )

                // Backend-advertised capability: if present, treat as authoritative.
                result.capabilities?.trackEvents?.let { supported ->
                    runCatching { AppGraph.settings.setTrackEventsBackendSupported(supported) }
                    if (supported) {
                        runCatching { TrackEventsCapabilityProbeWorker.cancelScheduled(context) }
                        runCatching { TrackEventsOutboxWorker.schedulePeriodic(context) }
                    } else {
                        runCatching { TrackEventsOutboxWorker.cancelScheduled(context) }
                        runCatching { TrackEventsCapabilityProbeWorker.cancelScheduled(context) }
                    }
                }

                // Self-heal fallback: if TrackEvents was disabled (e.g. due to 404) and the
                // backend does not advertise the capability, probe at low frequency.
                if (result.capabilities?.trackEvents == null) {
                    val trackEventsSupported = runCatching { AppGraph.settings.trackEventsBackendSupported.first() }.getOrDefault(true)
                    if (!trackEventsSupported) {
                        runCatching { TrackEventsCapabilityProbeWorker.schedulePeriodic(context, reason = "startup") }
                    }
                }

                // Deterministic protocol gating (no retry loop): if server declares a supported range
                // and this client is outside it, the user must update the app.
                result.protocol?.let { range ->
                    val v = com.trimsytrack.system.SystemCallablesService.CLIENT_PROTOCOL_VERSION
                    if (v < range.minSupported || v > range.maxSupported) {
                        state = StartupState.Blocked(
                            code = HardBlockCode.CLIENT_UPDATE_REQUIRED,
                            message = "App update required. Server supports protocol ${range.minSupported}..${range.maxSupported} but this app is $v.",
                        )
                        return
                    }
                }

                // Baseline verification: detect misdeploys early (e.g. missing canonical route).
                runCatching {
                    withContext(Dispatchers.IO) { BackendBaselineProbe.verifyCanonicalRouteBaseline() }
                }.onFailure { t ->
                    if (t is BackendBlockedException && t.machineCode?.trim()?.uppercase() == "ROUTE_NOT_FOUND") {
                        val base = BuildConfig.BACKEND_API_BASE.trim()
                        val deploy = result.deployment
                        state = StartupState.Error(
                            "Backend mismatch detected (canonical route missing).\n\n" +
                                "baseUrl=$base\n" +
                                "service=${deploy?.service ?: "-"} revision=${deploy?.revision ?: "-"} target=${deploy?.functionTarget ?: "-"}\n\n" +
                                "This usually means you are hitting the wrong backend or an old revision that lacks drivingTripCreate."
                        )
                        return
                    }

                    // Any other probe failure is treated as transient.
                    Log.w("Startup", "Baseline probe failed", t)
                }

                // One-time migration for older installs: claim legacy unscoped rows under this UID.
                runCatching {
                    withContext(Dispatchers.IO) {
                        val uid = result.identityUid.trim()
                        val legacyUid = "default"

                        AppGraph.db.storeDao().claimUnscoped(uid)
                        AppGraph.db.tripDao().claimUnscoped(uid)
                        AppGraph.db.promptDao().claimUnscoped(uid)
                        AppGraph.db.runDao().claimUnscoped(uid)
                        AppGraph.db.attachmentDao().claimUnscoped(uid)
                        AppGraph.db.distanceCacheDao().claimUnscoped(uid)
                        AppGraph.db.visitedStoreDao().claimUnscoped(uid)

                        // Additional legacy migration: older installs used uid='default'.
                        // If the current uid has no trips but 'default' does, move the legacy data over.
                        if (uid.isNotBlank() && uid != legacyUid) {
                            val currentTrips = AppGraph.db.tripDao().countAll(uid)
                            val legacyTrips = AppGraph.db.tripDao().countAll(legacyUid)
                            if (currentTrips == 0 && legacyTrips > 0) {
                                Log.w(
                                    "Startup",
                                    "Rekeying legacy uid='$legacyUid' to uid='$uid' (legacyTrips=$legacyTrips)",
                                )
                                AppGraph.db.storeDao().rekeyUid(oldUid = legacyUid, newUid = uid)
                                AppGraph.db.tripDao().rekeyUid(oldUid = legacyUid, newUid = uid)
                                AppGraph.db.promptDao().rekeyUid(oldUid = legacyUid, newUid = uid)
                                AppGraph.db.runDao().rekeyUid(oldUid = legacyUid, newUid = uid)
                                if (AppGraph.db.attachmentDao().countAll(uid) == 0 && AppGraph.db.attachmentDao().countAll(legacyUid) > 0) {
                                    AppGraph.db.attachmentDao().rekeyUid(oldUid = legacyUid, newUid = uid)
                                }
                                AppGraph.db.distanceCacheDao().rekeyUid(oldUid = legacyUid, newUid = uid)
                                AppGraph.db.visitedStoreDao().rekeyUid(oldUid = legacyUid, newUid = uid)
                            }
                        }
                    }
                }.onFailure { t ->
                    Log.w("Startup", "Failed to claim unscoped rows", t)
                }

                state = StartupState.Ready
                return
            } catch (e: BackendBlockedException) {
                val firebaseProjectId = runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull()
                Log.e(
                    "Startup",
                    "Handshake blocked: backendCode=${e.backendCode} httpStatus=${e.httpStatus} machineCode=${e.machineCode} region=${BuildConfig.BACKEND_FUNCTIONS_REGION} projectId=$firebaseProjectId",
                    e,
                )
                DebuggLogStore.add(
                    tag = "Handshake",
                    message = "blocked http=${e.httpStatus} backendCode=${e.backendCode} machineCode=${e.machineCode} msg=${e.message}",
                )

                val hard = AppGraph.systemCallables.hardBlockCodeOrNull(e.machineCode)
                when (hard) {
                    HardBlockCode.EMAIL_REQUIRED -> state = StartupState.Blocked(hard, e.message)
                    HardBlockCode.ACCOUNT_CONFLICT -> state = StartupState.Blocked(hard, e.message)
                    HardBlockCode.CLIENT_UPDATE_REQUIRED -> state = StartupState.Blocked(
                        hard,
                        e.message.trim().ifBlank { "App update required." },
                    )
                    HardBlockCode.UID_DATA_MISSING -> state = StartupState.Blocked(
                        hard,
                        e.message.trim().ifBlank { "Account not provisioned in backend." } +
                            "\n\nThis usually means the backend user record (uid_state/{uid}) is missing. Provision this account, then reopen the app.",
                    )
                    HardBlockCode.UID_DELETED -> state = StartupState.Blocked(hard, e.message)
                    null -> {
                        val hint = if (e.backendCode?.trim()?.uppercase() == "NOT_FOUND") {
                            val project = firebaseProjectId ?: "(unknown)"
                            "\n\nNot found usually means the callable isn’t deployed in this Firebase project/region.\n" +
                                "projectId=$project\n" +
                                "region=${BuildConfig.BACKEND_FUNCTIONS_REGION}\n" +
                                "If you have multiple apps (“trio”), double-check this app’s google-services.json and your functions deploy target."
                        } else {
                            ""
                        }

                        state = StartupState.Error(e.message + hint)
                    }
                }
                return
            } catch (t: Throwable) {
                Log.e("Startup", "Handshake failed", t)
                DebuggLogStore.add(
                    tag = "Handshake",
                    message = "failed ${t.javaClass.simpleName}:${t.message}",
                )
                val msg = t.message ?: "Startup failed"
                lastError = msg
                state = StartupState.Error(msg)
                return
            }
        }
    }

    LaunchedEffect(Unit) {
        // If there's no Firebase user, don't show an error UI; send user to Auth.
        // Also skip handshake in this state (it will 401 anyway).
        if (FirebaseAuth.getInstance().currentUser == null) {
            onOpenAuth()
            return@LaunchedEffect
        }

        runCatching { DriverDataSnapshotUploadWorker.schedulePeriodic(context) }

        runHandshake()
    }

    var isCrosschecking by remember { mutableStateOf(false) }
    var crosscheckMessage by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is StartupState.Ready) {
            isCrosschecking = true
            crosscheckMessage = "Checking your data and cross-referencing with the cloud…"
            scope.launch {
                crosscheckMessage = "Comparing your device data with the cloud…"

                val action = runCatching {
                    withContext(Dispatchers.IO) { AppGraph.driverDataRepository.reconcileOnLoginAndMaybeRestore() }
                }.getOrElse { t ->
                    Log.e("Startup", "AppData reconcile failed", t)
                    isCrosschecking = false
                    val prefix = t::class.simpleName?.takeIf { it.isNotBlank() }?.let { "$it: " } ?: ""
                    state = StartupState.Error(
                        "Cloud sync check failed. Please check your connection and tap Retry.\n\n" +
                            prefix + (t.message ?: "Unknown error")
                    )
                    return@launch
                }

                crosscheckMessage = when (action) {
                    "restored" -> "Your data was restored from the cloud."
                    "uploaded" -> "Your latest data was synced to the cloud."
                    "no_cloud_backup" -> "No cloud backup was found for this account."
                    else -> "Your data is up to date."
                }

                crosscheckMessage = "Verifying region files…"
                val fileAction = runCatching {
                    withContext(Dispatchers.IO) { AppGraph.driverDataRepository.verifyAndRepairRegionFilesFromCloud() }
                }.getOrElse { t ->
                    Log.e("Startup", "Region file verify/repair failed", t)
                    isCrosschecking = false
                    val prefix = t::class.simpleName?.takeIf { it.isNotBlank() }?.let { "$it: " } ?: ""
                    state = StartupState.Error(
                        "Cloud file check failed. Please check your connection and tap Retry.\n\n" +
                            prefix + (t.message ?: "Unknown error")
                    )
                    return@launch
                }

                crosscheckMessage = when (fileAction) {
                    "REPAIRED" -> "Downloaded missing files from the cloud."
                    "OK_CACHED" -> "Files verified."
                    "OK" -> "Files verified."
                    "NO_CLOUD_BACKUP" -> "No cloud files to restore."
                    else -> "Files checked ($fileAction)."
                }

                DebuggLogStore.add(
                    tag = "DriverData",
                    message = "region verify action=$fileAction",
                )

                DebuggLogStore.add(
                    tag = "DriverData",
                    message = "login reconcile action=$action",
                )

                isCrosschecking = false
                onReady()
            }
        }
    }

    val terminalStatus: String? = when {
        isCrosschecking -> crosscheckMessage
        state is StartupState.Loading -> "[handshake] contacting backend…"
        state is StartupState.Ready -> "[startup] finalizing…"
        else -> null
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.splash),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Place the "terminal" status box under the centered splash artwork.
        // The splash bitmap already contains the logo; we avoid rendering a second logo on top.
        if (terminalStatus != null) {
            TerminalStatusBox(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = maxHeight * 0.66f)
                    .fillMaxWidth(0.43f),
                message = terminalStatus,
                showSpinner = isCrosschecking || state is StartupState.Loading || state is StartupState.Ready,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            when {
                state is StartupState.Blocked -> {
                    Spacer(Modifier.height(18.dp))
                    val s = state as StartupState.Blocked
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Blocked",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(s.message)
                            Button(onClick = onSignOut) { Text("Sign out") }
                        }
                    }
                }

                state is StartupState.Error -> {
                    Spacer(Modifier.height(18.dp))
                    val s = state as StartupState.Error
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Startup error", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(s.message)
                            RowButtons(
                                onRetry = { scope.launch { runHandshake() } },
                                onSignOut = onSignOut,
                            )
                            if (!lastError.isNullOrBlank()) {
                                Text(lastError!!, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalStatusBox(
    modifier: Modifier = Modifier,
    message: String,
    showSpinner: Boolean,
) {
    val textColor = Color.White
    val shape = MaterialTheme.shapes.medium

    var typedMessage by remember(message) { mutableStateOf("") }
    LaunchedEffect(message) {
        // Typewriter effect (slower) so the status is readable.
        typedMessage = ""
        for (i in 0 until message.length) {
            typedMessage = message.substring(0, i + 1)
            delay(55)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.72f),
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "trimsy:~$",
                color = textColor.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = textColor,
                    )
                    Spacer(Modifier.size(10.dp))
                }

                Text(
                    text = typedMessage,
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.35.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RowButtons(
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(onClick = onRetry) { Text("Retry") }
        OutlinedButton(onClick = onSignOut) { Text("Sign out") }
    }
}
