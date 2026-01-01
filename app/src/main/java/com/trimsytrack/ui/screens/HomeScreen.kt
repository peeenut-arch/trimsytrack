package com.trimsytrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import com.trimsytrack.AppGraph
import com.trimsytrack.ui.components.HomeTileIds
import com.trimsytrack.ui.components.LargeActionTile

@Composable
fun HomeScreen(
    onManualTrip: () -> Unit,
    onReviewPlaces: () -> Unit,
    onJournal: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val homeTileIconImages by AppGraph.settings.homeTileIconImages.collectAsState(initial = emptyMap())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 22.dp),
    ) {
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            LargeActionTile(
                label = "Manual Trip",
                baseColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.Add,
                frameIcon = Icons.Outlined.Add,
                iconImageUri = homeTileIconImages[HomeTileIds.ManualTrip],
                onClick = onManualTrip,
            )

            LargeActionTile(
                label = "Review Places",
                baseColor = MaterialTheme.colorScheme.secondary,
                icon = Icons.Rounded.Place,
                frameIcon = Icons.Outlined.Place,
                iconImageUri = homeTileIconImages[HomeTileIds.ReviewPlaces],
                onClick = onReviewPlaces,
            )

            LargeActionTile(
                label = "Journal",
                baseColor = MaterialTheme.colorScheme.tertiary,
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                frameIcon = Icons.AutoMirrored.Outlined.MenuBook,
                iconImageUri = homeTileIconImages[HomeTileIds.Journal],
                onClick = onJournal,
            )
        }
    }
}
