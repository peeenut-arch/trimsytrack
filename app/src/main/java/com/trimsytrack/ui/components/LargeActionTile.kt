package com.trimsytrack.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
fun LargeActionTile(
    label: String,
    baseColor: Color,
    icon: ImageVector,
    frameIcon: ImageVector = icon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(46.dp),
    tileSize: Dp = 184.dp,
    iconSize: Dp = 78.dp,
    iconImageUri: String? = null,
    @DrawableRes iconResId: Int? = null,
) {
    // Rule: icons are always white and flat (no shadow).
    val contentColor = Color.White

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = baseColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .size(tileSize)
                    .clip(shape)
                    .background(baseColor)
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = shape),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    if (!iconImageUri.isNullOrBlank()) {
                        val imageSize = iconSize * 0.90f
                        Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = iconImageUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(imageSize)
                                    .clip(shape),
                                contentScale = ContentScale.Crop,
                            )
                            Icon(
                                imageVector = frameIcon,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else if (iconResId != null) {
                        Image(
                            painter = painterResource(id = iconResId),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
        )
    }
}
