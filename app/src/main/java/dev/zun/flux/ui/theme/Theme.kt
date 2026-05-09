package dev.zun.flux.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun ZunFluxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZunTypography,
        content = content,
    )
}
