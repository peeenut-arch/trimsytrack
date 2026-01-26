package com.trimsytrack.ui.screens

import androidx.compose.runtime.Composable

@Deprecated(
    message = "Renamed to AccountLocationScreen",
    replaceWith = ReplaceWith("AccountLocationScreen(onBack)"),
)
@Composable
fun ProfileLocationScreen(
    onBack: () -> Unit,
) {
    AccountLocationScreen(onBack = onBack)
}
