package com.trimsytrack.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.trimsytrack.auth.FirebaseEmailService
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

    var choice by remember { mutableStateOf(AuthChoice.ROOT) }
    var mailDialog by remember { mutableStateOf<MailDialog?>(null) }

    var mailEmail by remember { mutableStateOf("") }
    var mailPassword by remember { mutableStateOf("") }

    var showDeleteAccount by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

            scope.launch {
                busy = true
                try {
                    googleService.handleSignInResult(context, result.data)
                    snackbarHostState.showSnackbar("Signed in with Google")
                } catch (t: Throwable) {
                    snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                } finally {
                    busy = false
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
                title = { Text("Account") },
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
                try {
                    val intent = googleService.signInIntent(context)
                    googleLauncher.launch(intent)
                } catch (t: Throwable) {
                    snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                    busy = false
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ListItem(
                leadingContent = {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null)
                },
                headlineContent = {
                    Text(if (currentUser != null) "Signed in" else "Not signed in")
                },
                supportingContent = {
                    Text(currentUser?.email ?: "")
                },
                trailingContent = {
                    if (currentUser != null) {
                        Column {
                            IconButton(
                                onClick = { showDeleteAccount = true },
                                enabled = !busy,
                            ) {
                                Icon(Icons.Filled.DeleteForever, contentDescription = "Delete account")
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            emailService.signOut()
                                            googleService.signOut()
                                            snackbarHostState.showSnackbar("Signed out")
                                        } catch (t: Throwable) {
                                            snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                enabled = !busy,
                            ) {
                                Icon(Icons.Filled.Logout, contentDescription = "Sign out")
                            }
                        }
                    }
                }
            )

            when (choice) {
                AuthChoice.ROOT -> {
                    Button(
                        onClick = { choice = AuthChoice.LOGIN },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Login")
                    }

                    Button(
                        onClick = { choice = AuthChoice.CREATE },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create")
                    }
                }

                AuthChoice.LOGIN -> {
                    Button(
                        onClick = { startGoogle() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Login using Google")
                    }

                    OutlinedButton(
                        onClick = { mailDialog = MailDialog.LOGIN },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Login using Mail")
                    }

                    TextButton(
                        onClick = { choice = AuthChoice.ROOT },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Back")
                    }
                }

                AuthChoice.CREATE -> {
                    Button(
                        onClick = { startGoogle() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create using Google")
                    }

                    OutlinedButton(
                        onClick = { mailDialog = MailDialog.CREATE },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create using Mail")
                    }

                    TextButton(
                        onClick = { choice = AuthChoice.ROOT },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Back")
                    }
                }
            }
        }

        val dialog = mailDialog
        if (dialog != null) {
            val title = if (dialog == MailDialog.LOGIN) "Login using Mail" else "Create using Mail"
            AlertDialog(
                onDismissRequest = { if (!busy) mailDialog = null },
                title = { Text(title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = mailEmail,
                            onValueChange = { mailEmail = it },
                            label = { Text("Email") },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = mailPassword,
                            onValueChange = { mailPassword = it },
                            label = { Text("Password") },
                            singleLine = true,
                            enabled = !busy,
                            visualTransformation = if (mailPassword.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (dialog == MailDialog.LOGIN) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            val e = emailTrimmed(mailEmail)
                                            if (e.isBlank() || !isProbablyValidEmail(e)) {
                                                snackbarHostState.showSnackbar("Enter a valid email")
                                                return@launch
                                            }
                                            emailService.sendPasswordReset(e)
                                            snackbarHostState.showSnackbar("Recovery email sent")
                                        } catch (t: Throwable) {
                                            snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Recover password")
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val e = emailTrimmed(mailEmail)
                                    if (e.isBlank() || !isProbablyValidEmail(e)) {
                                        snackbarHostState.showSnackbar("Enter a valid email")
                                        return@launch
                                    }
                                    if (mailPassword.length < 6) {
                                        snackbarHostState.showSnackbar("Password must be at least 6 characters")
                                        return@launch
                                    }

                                    if (dialog == MailDialog.LOGIN) {
                                        emailService.signInWithEmailPassword(e, mailPassword)
                                        snackbarHostState.showSnackbar("Logged in")
                                    } else {
                                        emailService.createUserWithEmailPassword(e, mailPassword)
                                        snackbarHostState.showSnackbar("Account created")
                                    }

                                    mailDialog = null
                                } catch (t: Throwable) {
                                    snackbarHostState.showSnackbar(t.message ?: t.javaClass.simpleName)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                    ) {
                        Text(if (dialog == MailDialog.LOGIN) "Login" else "Create")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!busy) mailDialog = null },
                        enabled = !busy,
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showDeleteAccount) {
            AlertDialog(
                onDismissRequest = { if (!busy) showDeleteAccount = false },
                title = { Text("Delete account") },
                text = {
                    Text(
                        "This permanently deletes your Firebase account (including ${currentUser?.email.orEmpty()}). " +
                            "You can create a new account again afterwards.",
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    emailService.deleteCurrentUser()
                                    emailService.signOut()
                                    googleService.signOut()
                                    snackbarHostState.showSnackbar("Account deleted")
                                    showDeleteAccount = false
                                } catch (t: Throwable) {
                                    // Common Firebase error: requires-recent-login
                                    snackbarHostState.showSnackbar(
                                        t.message
                                            ?: "Delete failed (try signing in again, then delete)."
                                    )
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!busy) showDeleteAccount = false },
                        enabled = !busy,
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

private enum class AuthChoice {
    ROOT,
    LOGIN,
    CREATE,
}

private enum class MailDialog {
    LOGIN,
    CREATE,
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
