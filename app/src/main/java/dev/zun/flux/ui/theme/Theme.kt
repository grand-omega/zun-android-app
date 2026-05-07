package dev.zun.flux.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }

        darkTheme -> DarkScheme

        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZunTypography,
        content = content,
    )
}
