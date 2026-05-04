package dev.zun.flux.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = BackgroundPrimary,
    primaryContainer = PrimaryBlueSoft,
    onPrimaryContainer = PrimaryBlue,
    background = BackgroundTertiary,
    surface = BackgroundPrimary,
    surfaceVariant = BackgroundSecondary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = TextDanger,
)

private val DarkScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = BackgroundPrimary,
    primaryContainer = PrimaryBlueDarkContainer,
    onPrimaryContainer = BackgroundPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = BackgroundPrimary,
    onSurface = BackgroundPrimary,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
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
