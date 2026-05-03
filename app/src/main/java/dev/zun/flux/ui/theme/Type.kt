package dev.zun.flux.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ZunTypography =
    Typography(
        // Display sizes — reserved for true status moments (mid-generation %,
        // big completion ticks). Used sparingly so they keep their punch.
        displayLarge = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Medium),
        displayMedium = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Medium),
        displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Medium),
        headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium),
        headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
        titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
        labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )

/**
 * Switches a text style to a monospaced font. Used for technical readouts —
 * percentages, durations, batch counters — so digits feel like instrument
 * panel output rather than prose.
 */
fun TextStyle.tabular(): TextStyle = copy(fontFamily = FontFamily.Monospace)
