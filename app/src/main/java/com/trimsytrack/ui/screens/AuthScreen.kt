package com.trimsytrack.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.trimsytrack.auth.FirebaseEmailService
import com.trimsytrack.auth.GoogleAuthCollision
import com.trimsytrack.auth.GoogleSignInService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val emailService = remember { FirebaseEmailService() }
    val googleService = remember { GoogleSignInService() }

    val snackbarHostState = remember { SnackbarHostState() }

    val currentUser = rememberFirebaseUser()

    var step by remember { mutableStateOf<AuthStep>(AuthStep.Email) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf<String?>(null) }

    var pendingGoogleCollision by remember { mutableStateOf<GoogleAuthCollision?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

            scope.launch {
                busy = true
                busyLabel = "Loggar in…"
                try {
                    googleService.handleSignInResult(context, result.data)
                    snackbarHostState.showSnackbar("Klart!")
                } catch (t: Throwable) {
                    if (t is GoogleAuthCollision) {
                        pendingGoogleCollision = t
                        showLinkDialog = true
                    } else {
                        snackbarHostState.showSnackbar("Något gick fel")
                    }
                } finally {
                    busy = false
                    busyLabel = null
                }
            }
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        },
    ) { padding ->
        fun emailTrimmed(value: String): String = value.trim()
        fun isProbablyValidEmail(value: String): Boolean {
            val v = value.trim()
            return v.contains("@").and(v.contains("."))
        }

        fun startGoogle() {
            scope.launch {
                busy = true
                busyLabel = "Loggar in…"
                try {
                    val intent = googleService.signInIntent(context)
                    googleLauncher.launch(intent)
                } catch (t: Throwable) {
                    snackbarHostState.showSnackbar("Något gick fel")
                    busy = false
                    busyLabel = null
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (currentUser != null) {
                    Text(
                        currentUser.displayName?.takeIf { it.isNotBlank() } ?: "Inloggad",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        currentUser.email.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                busyLabel = "Loggar ut…"
                                try {
                                    emailService.signOut()
                                    googleService.signOut()
                                    snackbarHostState.showSnackbar("Klart!")
                                } catch (_: Throwable) {
                                    snackbarHostState.showSnackbar("Något gick fel")
                                } finally {
                                    busy = false
                                    busyLabel = null
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (busy && busyLabel != null) busyLabel!! else "Logga ut")
                    }
                    return@Box
                }

                // Google primary CTA
                Button(
                    onClick = { startGoogle() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color.White, CircleShape)
                                .border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("G", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.size(10.dp))
                        Text("Fortsätt med Google")
                    }
                }

                Text(
                    "eller",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Enpost") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (step is AuthStep.Password) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                        },
                        label = { Text("Lösenord") },
                        singleLine = true,
                        enabled = !busy,
                        isError = passwordError != null,
                        supportingText = {
                            if (passwordError != null) Text(passwordError.orEmpty())
                        },
                        visualTransformation = if (password.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    TextButton(
                        onClick = {
                            scope.launch {
                                val e = emailTrimmed(email)
                                if (e.isBlank() || !isProbablyValidEmail(e)) {
                                    snackbarHostState.showSnackbar("Något gick fel")
                                    return@launch
                                }
                                busy = true
                                busyLabel = "Skickar…"
                                try {
                                    // Do not leak whether the email exists.
                                    runCatching { emailService.sendPasswordReset(e) }
                                    snackbarHostState.showSnackbar("Klart!")
                                } finally {
                                    busy = false
                                    busyLabel = null
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Glömt lösenord")
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            val e = emailTrimmed(email)
                            if (e.isBlank() || !isProbablyValidEmail(e)) {
                                snackbarHostState.showSnackbar("Något gick fel")
                                return@launch
                            }

                            if (step is AuthStep.Email) {
                                busy = true
                                busyLabel = "Loggar in…"
                                try {
                                    val methods = emailService.fetchSignInMethods(e)
                                    when {
                                        methods.contains("password") -> {
                                            step = AuthStep.Password(existingAccount = true)
                                            password = ""
                                            passwordError = null
                                        }
                                        methods.isEmpty() -> {
                                            step = AuthStep.Password(existingAccount = false)
                                            password = ""
                                            passwordError = null
                                        }
                                        else -> {
                                            // e.g. Google-only: continue with Google automatically.
                                            startGoogle()
                                        }
                                    }
                                } catch (_: Throwable) {
                                    snackbarHostState.showSnackbar("Något gick fel")
                                } finally {
                                    busy = false
                                    busyLabel = null
                                }
                                return@launch
                            }

                            val passwordStep = step as AuthStep.Password
                            if (password.length < 8) {
                                passwordError = "Minst 8 tecken"
                                return@launch
                            }

                            busy = true
                            busyLabel = "Loggar in…"
                            try {
                                if (passwordStep.existingAccount) {
                                    emailService.signInWithEmailPassword(e, password)
                                } else {
                                    emailService.createUserWithEmailPassword(e, password)
                                }
                                snackbarHostState.showSnackbar("Klart!")
                            } catch (t: Throwable) {
                                // Avoid leaking details.
                                snackbarHostState.showSnackbar("Fel enpost eller lösenord")
                            } finally {
                                busy = false
                                busyLabel = null
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(10.dp))
                        }
                        Text(if (busyLabel != null) busyLabel!! else "Fortsätt")
                    }
                }

                Text(
                    "Genom att fortsätta godkänner du villkoren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (showLinkDialog) {
            val collision = pendingGoogleCollision
            AlertDialog(
                onDismissRequest = { if (!busy) showLinkDialog = false },
                title = { Text("Fortsätt") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Ange lösenord för ${collision?.email.orEmpty()} för att länka Google.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                passwordError = null
                            },
                            label = { Text("Lösenord") },
                            singleLine = true,
                            enabled = !busy,
                            isError = passwordError != null,
                            supportingText = {
                                if (passwordError != null) Text(passwordError.orEmpty())
                            },
                            visualTransformation = if (password.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val c = collision ?: return@Button
                            scope.launch {
                                if (password.length < 8) {
                                    passwordError = "Minst 8 tecken"
                                    return@launch
                                }
                                busy = true
                                busyLabel = "Loggar in…"
                                try {
                                    emailService.signInWithEmailPassword(c.email, password)
                                    emailService.linkCurrentUserWithCredential(c.pendingCredential)
                                    snackbarHostState.showSnackbar("Klart!")
                                    showLinkDialog = false
                                    pendingGoogleCollision = null
                                    password = ""
                                    passwordError = null
                                } catch (_: Throwable) {
                                    snackbarHostState.showSnackbar("Fel enpost eller lösenord")
                                } finally {
                                    busy = false
                                    busyLabel = null
                                }
                            }
                        },
                        enabled = !busy,
                    ) {
                        Text(if (busyLabel != null) busyLabel!! else "Fortsätt")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!busy) showLinkDialog = false },
                        enabled = !busy,
                    ) {
                        Text("Avbryt")
                    }
                },
            )
        }
    }
}

private sealed interface AuthStep {
    data object Email : AuthStep
    data class Password(val existingAccount: Boolean) : AuthStep
}

@Composable
private fun rememberFirebaseUser(): FirebaseUser? {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { a ->
            user = a.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    return user
}
