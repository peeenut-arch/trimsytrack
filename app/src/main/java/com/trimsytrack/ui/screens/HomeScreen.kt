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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import coil.compose.AsyncImage
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
) {
    val homeTileIconImages by AppGraph.settings.homeTileIconImages.collectAsState(initial = emptyMap())
    
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
