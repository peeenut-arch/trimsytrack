package com.trimsytrack.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.trimsytrack.AppGraph
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val user = remember { FirebaseAuth.getInstance().currentUser }

    val accountPictureUri by AppGraph.settings.accountPictureUri.collectAsState(initial = null)

    val choosePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            scope.launch {
                AppGraph.settings.setAccountPictureUri(uri.toString())
            }
        },
    )

    val effectivePicture: String? = remember(accountPictureUri, user) {
        accountPictureUri
            ?: user?.photoUrl?.toString()?.trim()?.ifBlank { null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (effectivePicture != null) {
                AsyncImage(
                    model = effectivePicture,
                    contentDescription = "Account picture",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "No account picture",
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = user?.email?.takeIf { it.isNotBlank() } ?: "(no email)",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = user?.uid?.takeIf { it.isNotBlank() } ?: "(no uid)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { choosePictureLauncher.launch(arrayOf("image/*")) },
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Set picture")
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = accountPictureUri != null,
                    onClick = {
                        scope.launch { AppGraph.settings.setAccountPictureUri(null) }
                    },
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Clear")
                }
            }

            Spacer(Modifier.height(6.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSignOut,
            ) {
                Text("Sign out")
            }
        }
    }
}
