package com.trimsytrack.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun TrimsyActionTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onBackground,
            disabledContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        ),
    ) {
        Text(text)
    }
}

@Composable
fun TrimsyWhiteRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
) {
    RadioButton(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        colors = RadioButtonDefaults.colors(
            selectedColor = Color.White,
            unselectedColor = Color.White,
            disabledSelectedColor = Color.White.copy(alpha = 0.4f),
            disabledUnselectedColor = Color.White.copy(alpha = 0.4f),
        ),
    )
}

@Composable
fun TrimsyPillSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(BorderStroke(1.dp, Color.Black.copy(alpha = 0.12f)), shape),
        shape = shape,
        color = Color.White,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.65f),
            )
            Spacer(Modifier.size(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                cursorBrush = SolidColor(Color.Black),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            color = Color.Black.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    inner()
                },
            )
        }
    }
}
