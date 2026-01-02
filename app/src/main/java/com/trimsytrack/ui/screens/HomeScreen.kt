package com.trimsytrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.trimsytrack.auth.FirebaseEmailService
import com.trimsytrack.auth.GoogleSignInService
import com.trimsytrack.AppGraph
import com.trimsytrack.R
import com.trimsytrack.ui.components.HomeTileIds

@Composable
fun HomeScreen(
    onAddTrip: (withMedia: Boolean) -> Unit,
    onReviewPlaces: () -> Unit,
    onJournal: () -> Unit,
    onCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val homeTileIconImages by AppGraph.settings.homeTileIconImages.collectAsState(initial = emptyMap())

    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { a ->
            currentUser.value = a.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val menuExpanded = remember { mutableStateOf(false) }
    val emailService = remember { FirebaseEmailService() }
    val googleService = remember { GoogleSignInService() }
    
    @Composable
    fun HomeIconButton(
        iconResId: Int,
        iconImageUri: String?,
        onClick: () -> Unit,
    ) {
        val size = 130.dp
        Box(
            modifier = Modifier
                .size(size)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (!iconImageUri.isNullOrBlank()) {
                AsyncImage(
                    model = iconImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 22.dp),
    ) {
        val user = currentUser.value
        if (user == null) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp)
                    .size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp),
            ) {
                IconButton(
                    onClick = { menuExpanded.value = true },
                    modifier = Modifier.size(44.dp),
                ) {
                    val photo = user.photoUrl?.toString()
                    if (!photo.isNullOrBlank()) {
                        AsyncImage(
                            model = photo,
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (user.displayName?.trim()?.take(1)
                                    ?: user.email?.trim()?.take(1)
                                    ?: "?")
                                    .uppercase(),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded.value,
                    onDismissRequest = { menuExpanded.value = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(user.displayName?.takeIf { it.isNotBlank() } ?: "")
                                Text(
                                    user.email.orEmpty(),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                )
                            }
                        },
                        onClick = {},
                        enabled = false,
                    )
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Inställningar") },
                        onClick = {
                            menuExpanded.value = false
                            onOpenSettings()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Konto") },
                        onClick = {
                            menuExpanded.value = false
                            onOpenAccount()
                        },
                    )
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Logga ut") },
                        onClick = {
                            menuExpanded.value = false
                            runCatching { emailService.signOut() }
                            runCatching { googleService.signOut() }
                        },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            HomeIconButton(
                iconResId = R.drawable.trip,
                iconImageUri = homeTileIconImages[HomeTileIds.ManualTrip],
                onClick = { onAddTrip(false) },
            )

            HomeIconButton(
                iconResId = R.drawable.notifications,
                iconImageUri = homeTileIconImages[HomeTileIds.ReviewPlaces],
                onClick = onReviewPlaces,
            )

            HomeIconButton(
                iconResId = R.drawable.journal,
                iconImageUri = homeTileIconImages[HomeTileIds.Journal],
                onClick = onJournal,
            )

            HomeIconButton(
                iconResId = R.drawable.camera,
                iconImageUri = homeTileIconImages[HomeTileIds.Camera],
                onClick = onCamera,
            )
        }
    }
}
