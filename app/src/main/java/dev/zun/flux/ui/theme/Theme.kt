package dev.zun.flux.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = BackgroundPrimary,
    background = BackgroundTertiary,
    surface = BackgroundPrimary,
    surfaceVariant = BackgroundSecondary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = TextDanger,
)

private val DarkScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = BackgroundPrimary,
    background = TextPrimary,
    surface = TextPrimary,
    surfaceVariant = TextSecondary,
    onBackground = BackgroundPrimary,
    onSurface = BackgroundPrimary,
    onSurfaceVariant = BackgroundSecondary,
    error = TextDanger,
)

@Composable
fun ZunFluxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ZunTypography,
        content = content,
    )
}
