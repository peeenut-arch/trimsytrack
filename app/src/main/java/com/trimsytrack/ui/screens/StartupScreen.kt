package com.trimsytrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trimsytrack.AppGraph
import com.trimsytrack.backend.BackendBlockedException
import com.trimsytrack.system.HardBlockCode
import kotlinx.coroutines.launch

private sealed interface StartupState {
    data object Loading : StartupState
    data class NeedsProfile(val message: String? = null) : StartupState
    data class Blocked(val code: HardBlockCode, val message: String) : StartupState
    data class Error(val message: String) : StartupState
    data class Ready(val profileId: String) : StartupState
}

@Composable
fun StartupScreen(
    onReady: (profileId: String) -> Unit,
    onNeedsProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state: StartupState by remember { mutableStateOf(StartupState.Loading) }
    var lastError: String? by remember { mutableStateOf(null) }

    suspend fun runHandshake() {
        state = StartupState.Loading
        lastError = null

        try {
            val result = AppGraph.systemCallables.handshakeGet()

            // Identity is email-anchored; treat missing email as blocked.
            if (result.normalizedEmail.isBlank()) {
                state = StartupState.Blocked(
                    code = HardBlockCode.EMAIL_REQUIRED,
                    message = "This account has no email address. Please sign in with an email-based account.",
                )
                return
            }

            AppGraph.settings.setBackendProtocolVersion(result.protocolVersion)
            AppGraph.settings.setBackendIdentityEmail(result.normalizedEmail)

            if (!result.profileExists || result.profileId.isNullOrBlank()) {
                state = StartupState.NeedsProfile("Profile required")
                return
            }

            val profileId = result.profileId
            AppGraph.settings.activateProfile(profileId)

            // Best-effort: cache profile objects for faster startup.
            runCatching {
                val profile = AppGraph.systemCallables.profileGet()
                AppGraph.settings.setBackendProfileJson(profile.toString())
            }
            runCatching {
                val media = AppGraph.systemCallables.profileMediaGet()
                AppGraph.settings.setBackendProfileMediaJson(media.toString())
            }

            state = StartupState.Ready(profileId)
        } catch (e: BackendBlockedException) {
            val hard = AppGraph.systemCallables.hardBlockCodeOrNull(e.machineCode)
            when (hard) {
                HardBlockCode.PROFILE_REQUIRED -> state = StartupState.NeedsProfile(e.message)
                HardBlockCode.EMAIL_REQUIRED -> state = StartupState.Blocked(hard, e.message)
                HardBlockCode.ACCOUNT_CONFLICT -> state = StartupState.Blocked(hard, e.message)
                null -> state = StartupState.Error(e.message)
            }
        } catch (t: Throwable) {
            val msg = t.message ?: "Startup failed"
            lastError = msg
            state = StartupState.Error(msg)
        }
    }

    LaunchedEffect(Unit) {
        runHandshake()
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is StartupState.Ready -> onReady(s.profileId)
            is StartupState.NeedsProfile -> onNeedsProfile()
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                StartupState.Loading -> {
                    CircularProgressIndicator()
                    Text("Checking account…")
                }

                is StartupState.Blocked -> {
                    Text("Blocked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(s.message)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onSignOut) { Text("Sign out") }
                }

                is StartupState.Error -> {
                    Text("Startup error", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(s.message)
                    Spacer(Modifier.height(6.dp))
                    RowButtons(
                        onRetry = { scope.launch { runHandshake() } },
                        onSignOut = onSignOut,
                    )
                }

                is StartupState.NeedsProfile -> {
                    Text("Profile required", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(s.message ?: "You must create a profile to continue.")
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = onSignOut) { Text("Sign out") }
                }

                is StartupState.Ready -> {
                    CircularProgressIndicator()
                    Text("Loading profile…")
                }
            }

            if (!lastError.isNullOrBlank() && state !is StartupState.Loading) {
                Text(lastError!!, color = MaterialTheme.colorScheme.error)
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
