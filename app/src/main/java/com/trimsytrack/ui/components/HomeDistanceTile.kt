package com.trimsytrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trimsytrack.ui.theme.TrimsyGreen
import java.util.Locale

@Composable
fun HomeDistanceTile(
    distanceMeters: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconBoxSize: Dp = 124.dp,
    badgeHorizontalPadding: Dp = 10.dp,
    badgeVerticalPadding: Dp = 5.dp,
    badgeTextStyle: TextStyle? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val green = TrimsyGreen

    val kmValue = remember(distanceMeters) {
        if (distanceMeters <= 0 || distanceMeters == Int.MAX_VALUE) {
            "—"
        } else {
            val km = distanceMeters / 1000.0
            if (km < 10) String.format(Locale.getDefault(), "%.1f", km) else String.format(Locale.getDefault(), "%.0f", km)
        }
    }

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        onClick = { if (enabled) onClick() },
        enabled = enabled,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(iconBoxSize),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = "Home",
                    tint = green,
                    modifier = Modifier.fillMaxSize(),
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = green,
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = kmValue,
                        style = badgeTextStyle ?: MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = badgeHorizontalPadding, vertical = badgeVerticalPadding),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
