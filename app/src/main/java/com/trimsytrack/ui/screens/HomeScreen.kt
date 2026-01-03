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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import coil.compose.AsyncImage
import com.trimsytrack.AppGraph
import com.trimsytrack.R
import com.trimsytrack.ui.components.HomeTileIds
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onAddTrip: (withMedia: Boolean) -> Unit,
    onReviewPlaces: () -> Unit,
    onJournal: () -> Unit,
    onCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfiles: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val homeTileIconImages by AppGraph.settings.homeTileIconImages.collectAsState(initial = emptyMap())
    val darkModeEnabled by AppGraph.settings.darkModeEnabled.collectAsState(initial = false)

    val activeProfileId by AppGraph.settings.profileId.collectAsState(initial = "")
    val profileName by AppGraph.settings.profileName.collectAsState(initial = "")
    val profiles by AppGraph.settings.profiles.collectAsState(initial = emptyList())
    val activeProfilePhotoUri = remember(activeProfileId, profiles) {
        profiles.firstOrNull { it.id == activeProfileId }?.photoUri
    }

    val menuExpanded = remember { mutableStateOf(false) }
    
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
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp),
        ) {
            IconButton(
                onClick = { menuExpanded.value = true },
                modifier = Modifier.size(44.dp),
            ) {
                if (!activeProfilePhotoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = activeProfilePhotoUri,
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
                            profileName.trim().take(1).ifBlank { "?" }.uppercase(),
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
                            Text(profileName.ifBlank { "Profile" })
                            Text(
                                if (activeProfileId.isBlank()) "No profile selected" else "Active profile",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            )
                        }
                    },
                    onClick = {},
                    enabled = false,
                )

                Divider()

                DropdownMenuItem(
                    text = { Text("Profil") },
                    onClick = {
                        menuExpanded.value = false
                        onOpenProfiles()
                    },
                )

                DropdownMenuItem(
                    text = { Text(if (darkModeEnabled) "Light" else "Dark") },
                    trailingIcon = {
                        Switch(
                            checked = darkModeEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { AppGraph.settings.setDarkModeEnabled(enabled) }
                            },
                        )
                    },
                    onClick = {
                        scope.launch { AppGraph.settings.setDarkModeEnabled(!darkModeEnabled) }
                    },
                )

                DropdownMenuItem(
                    text = { Text("Inställningar") },
                    onClick = {
                        menuExpanded.value = false
                        onOpenSettings()
                    },
                )
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
