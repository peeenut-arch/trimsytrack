package com.trimsytrack.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.trimsytrack.auth.FirebaseEmailService
import com.trimsytrack.auth.GoogleAuthCollision
import com.trimsytrack.auth.GoogleSignInService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onBack: () -> Unit,
    initialEmailLink: String?,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val logTag = "AuthPasswordless"

    val emailService = remember { FirebaseEmailService() }
    val googleService = remember { GoogleSignInService() }

    val snackbarHostState = remember { SnackbarHostState() }

    val currentUser = rememberFirebaseUser()

    var didAutoContinue by remember { mutableStateOf(false) }

    var step by remember { mutableStateOf<AuthStep>(AuthStep.Email) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf<String?>(null) }

    var emailLink by remember { mutableStateOf("") }
    var didConsumeInitialLink by remember { mutableStateOf(false) }

    var pendingLinkCredential by remember { mutableStateOf<AuthCredential?>(null) }
    var pendingLinkProviderLabel by remember { mutableStateOf<String?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                scope.launch {
                    busy = false
                    busyLabel = null
                    snackbarHostState.showSnackbar("Avbruten")
                }
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                busy = true
                busyLabel = "Loggar in…"
                try {
                    googleService.handleSignInResult(context, result.data)
                    snackbarHostState.showSnackbar("Klart!")
                } catch (t: Throwable) {
                    if (t is GoogleAuthCollision) {
                        email = t.email
                        pendingLinkCredential = t.pendingCredential
                        pendingLinkProviderLabel = "Google"
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

        fun debugError(t: Throwable): String {
            val code = (t as? FirebaseAuthException)?.errorCode
            val msg = t.message?.takeIf { it.isNotBlank() }
            return buildString {
                append("Misslyckades")
                if (!code.isNullOrBlank()) append(" ($code)")
                if (!msg.isNullOrBlank()) append(": $msg")
            }
        }

        LaunchedEffect(initialEmailLink) {
            if (didConsumeInitialLink) return@LaunchedEffect
            val link = initialEmailLink?.trim().orEmpty()
            if (link.isBlank()) return@LaunchedEffect

            // Only prefill if it looks like a Firebase email-link.
            if (emailService.isSignInWithEmailLink(link)) {
                emailLink = link
                didConsumeInitialLink = true
                Log.i(logTag, "Prefilled email link from intent")
                snackbarHostState.showSnackbar("Länk mottagen. Ange e‑post och slutför.")
            }
        }

        // If already signed in, jump straight into the app.
        LaunchedEffect(currentUser?.uid) {
            if (currentUser == null) return@LaunchedEffect
            if (busy) return@LaunchedEffect
            if (didAutoContinue) return@LaunchedEffect
            didAutoContinue = true
            onContinue()
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
                        onClick = onContinue,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Fortsätt")
                    }

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

                // Passwordless email-link (magic link) flow
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val e = emailTrimmed(email)
                            if (e.isBlank() || !isProbablyValidEmail(e)) {
                                snackbarHostState.showSnackbar("Ange en giltig e‑post")
                                return@launch
                            }

                            busy = true
                            busyLabel = "Skickar länk…"
                            try {
                                val continueUrl = context.getString(com.trimsytrack.R.string.email_link_continue_url)
                                Log.i(logTag, "Sending passwordless link email=$e continueUrl=$continueUrl")
                                emailService.sendPasswordlessSignInLink(
                                    email = e,
                                    continueUrl = continueUrl,
                                    androidPackageName = context.packageName,
                                )
                                snackbarHostState.showSnackbar("Länk skickad. Kolla din e‑post.")
                            } catch (t: Throwable) {
                                Log.w(logTag, "Failed to send passwordless link", t)
                                snackbarHostState.showSnackbar(debugError(t))
                            } finally {
                                busy = false
                                busyLabel = null
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Skicka inloggningslänk (utan lösenord)")
                }

                OutlinedTextField(
                    value = emailLink,
                    onValueChange = { emailLink = it },
                    label = { Text("Klistra in länken här (om den inte öppnar appen)") },
                    singleLine = false,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        scope.launch {
                            val e = emailTrimmed(email)
                            val link = emailLink.trim()
                            if (e.isBlank() || !isProbablyValidEmail(e)) {
                                snackbarHostState.showSnackbar("Ange samma e‑post som du skickade länken till")
                                return@launch
                            }
                            if (link.isBlank()) {
                                snackbarHostState.showSnackbar("Ingen länk")
                                return@launch
                            }
                            if (!emailService.isSignInWithEmailLink(link)) {
                                snackbarHostState.showSnackbar("Länken ser inte ut som en giltig inloggningslänk")
                                return@launch
                            }

                            busy = true
                            busyLabel = "Loggar in…"
                            try {
                                Log.i(logTag, "Completing passwordless sign-in email=$e")
                                emailService.signInWithPasswordlessEmailLink(e, link)
                                snackbarHostState.showSnackbar("Klart!")
                            } catch (t: Throwable) {
                                Log.w(logTag, "Failed to complete passwordless sign-in", t)
                                snackbarHostState.showSnackbar(debugError(t))
                            } finally {
                                busy = false
                                busyLabel = null
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Slutför med länk")
                }

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
            val providerLabel = pendingLinkProviderLabel
            val linkCredential = pendingLinkCredential
            AlertDialog(
                onDismissRequest = {
                    if (!busy) {
                        showLinkDialog = false
                        pendingLinkCredential = null
                        pendingLinkProviderLabel = null
                    }
                },
                title = { Text("Fortsätt") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (!email.trim().isNullOrBlank()) {
                                "Ange lösenord för ${email.trim()} för att länka ${providerLabel ?: "konto"}."
                            } else {
                                "Ange lösenord för att länka ${providerLabel ?: "konto"}."
                            },
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
                            scope.launch {
                                val credential = linkCredential
                                if (credential == null) {
                                    snackbarHostState.showSnackbar("Något gick fel")
                                    return@launch
                                }

                                val e = emailTrimmed(email)
                                if (e.isBlank() || !isProbablyValidEmail(e)) {
                                    snackbarHostState.showSnackbar(
                                        "Kan inte länka utan e-post. Logga in med ditt befintliga sätt först."
                                    )
                                    return@launch
                                }

                                if (password.length < 8) {
                                    passwordError = "Minst 8 tecken"
                                    return@launch
                                }
                                busy = true
                                busyLabel = "Loggar in…"
                                try {
                                    emailService.signInWithEmailPassword(e, password)
                                    emailService.linkCurrentUserWithCredential(credential)
                                    snackbarHostState.showSnackbar("Klart!")
                                    showLinkDialog = false
                                    pendingLinkCredential = null
                                    pendingLinkProviderLabel = null
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
                        onClick = {
                            if (!busy) {
                                showLinkDialog = false
                                pendingLinkCredential = null
                                pendingLinkProviderLabel = null
                            }
                        },
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

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
