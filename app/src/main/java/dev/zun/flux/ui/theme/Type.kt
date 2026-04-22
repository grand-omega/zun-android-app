package dev.zun.flux.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ZunTypography =
    Typography(
        titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
        labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
