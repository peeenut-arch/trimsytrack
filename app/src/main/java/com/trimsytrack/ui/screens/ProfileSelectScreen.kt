package com.trimsytrack.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trimsytrack.AppGraph
import kotlinx.coroutines.launch

@Composable
fun ProfileSelectScreen(
    onSelectProfile: (profileId: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val profiles by AppGraph.settings.profiles.collectAsState(initial = emptyList())

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newProfileName by rememberSaveable { mutableStateOf("") }
    var newProfilePhotoUri by remember { mutableStateOf<Uri?>(null) }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                newProfilePhotoUri = uri
            }
        },
    )

    LaunchedEffect(Unit) {
        // Soft-migrate legacy single-profile into the list.
        AppGraph.settings.ensureActiveProfileListed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Välj profil",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(4.dp))

        profiles
            .sortedBy { it.createdAtMillis }
            .forEach { profile ->
                ProfileCard(
                    title = profile.name,
                    avatarText = profile.name.trim().take(1).uppercase().ifBlank { "?" },
                    photoUri = profile.photoUri,
                    onClick = { onSelectProfile(profile.id) },
                )
            }

        // Create profile card (identical layout) with '+' avatar.
        ProfileCard(
            title = "Skapa profil",
            avatarText = "+",
            photoUri = null,
            onClick = { showCreateDialog = true },
        )

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Skapa profil") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newProfileName,
                            onValueChange = { newProfileName = it },
                            label = { Text("Namn") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (newProfilePhotoUri != null) {
                                    AsyncImage(
                                        model = newProfilePhotoUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text(
                                        text = newProfileName.trim().take(1).uppercase().ifBlank { "?" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            TextButton(onClick = { pickPhotoLauncher.launch(arrayOf("image/*")) }) {
                                Text(if (newProfilePhotoUri == null) "Välj bild" else "Byt bild")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = newProfileName.trim().isNotBlank(),
                        onClick = {
                            val name = newProfileName.trim()
                            val photo = newProfilePhotoUri?.toString()
                            scope.launch {
                                val newId = AppGraph.settings.createProfile(name = name, photoUri = photo)
                                showCreateDialog = false
                                newProfileName = ""
                                newProfilePhotoUri = null
                                onSelectProfile(newId)
                            }
                        },
                    ) {
                        Text("Skapa")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Avbryt")
                    }
                },
            )
        }
    }
}

@Composable
private fun ProfileCard(
    title: String,
    avatarText: String,
    photoUri: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!photoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = avatarText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
