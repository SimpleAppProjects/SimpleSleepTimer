package com.thewizrd.simplesleeptimer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

@Composable
fun WearAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = dynamicColorScheme(LocalContext.current) ?: wearColorScheme,
        typography = WearTypography,
        content = content
    )
}