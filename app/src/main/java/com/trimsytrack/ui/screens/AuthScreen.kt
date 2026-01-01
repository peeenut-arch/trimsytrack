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
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
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

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

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
        fun emailTrimmed(): String = email.trim()
        fun isProbablyValidEmail(value: String): Boolean {
            val v = value.trim()
            return v.contains("@").and(v.contains("."))
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
            )

            Button(
                onClick = {
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
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue with Google")
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Sign in with email",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                "Use the password you chose when you created the account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = if (password.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val e = emailTrimmed()
                            if (e.isBlank()) {
                                snackbarHostState.showSnackbar("Enter your email")
                                return@launch
                            }
                            if (!isProbablyValidEmail(e)) {
                                snackbarHostState.showSnackbar("Enter a valid email")
                                return@launch
                            }
                            if (password.length < 6) {
                                snackbarHostState.showSnackbar("Password must be at least 6 characters")
                                return@launch
                            }
                            emailService.signInWithEmailPassword(e, password)
                            snackbarHostState.showSnackbar("Signed in")
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
                Text("Sign in with email")
            }

            TextButton(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val e = emailTrimmed()
                            if (e.isBlank()) {
                                snackbarHostState.showSnackbar("Enter your email")
                                return@launch
                            }
                            if (!isProbablyValidEmail(e)) {
                                snackbarHostState.showSnackbar("Enter a valid email")
                                return@launch
                            }

                            emailService.sendPasswordReset(e)
                            snackbarHostState.showSnackbar("Password reset link sent (check your email)")
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
                Text("Forgot password? Send reset link")
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Create a new account",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                "You choose a password (Firebase doesn’t email you one).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm password") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = if (confirmPassword.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val e = emailTrimmed()
                            if (e.isBlank()) {
                                snackbarHostState.showSnackbar("Enter your email")
                                return@launch
                            }
                            if (!isProbablyValidEmail(e)) {
                                snackbarHostState.showSnackbar("Enter a valid email")
                                return@launch
                            }
                            if (password.length < 6) {
                                snackbarHostState.showSnackbar("Password must be at least 6 characters")
                                return@launch
                            }
                            if (password != confirmPassword) {
                                snackbarHostState.showSnackbar("Passwords do not match")
                                return@launch
                            }

                            emailService.createUserWithEmailPassword(e, password)
                            runCatching { emailService.sendEmailVerification() }
                            snackbarHostState.showSnackbar("Account created (verification email sent if supported)")
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
                Text("Create account with email")
            }
        }
    }
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
