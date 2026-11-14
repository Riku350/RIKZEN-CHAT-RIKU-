package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val CinzelFontFamily = FontFamily(
    Font(R.font.cinzel, FontWeight.Normal),
    Font(R.font.cinzel, FontWeight.SemiBold),
    Font(R.font.cinzel, FontWeight.Bold)
)

val PlusJakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

fun buildRikzenTypography(scale: Float = 1.0f): Typography {
    fun s(v: Float) = (v * scale).sp
    return Typography(
        displayLarge = TextStyle(
            fontFamily = CinzelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = s(36f),
            lineHeight = s(44f),
            letterSpacing = 2.sp
        ),
        displayMedium = TextStyle(
            fontFamily = CinzelFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(28f),
            lineHeight = s(34f),
            letterSpacing = 1.5.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = CinzelFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = s(24f),
            lineHeight = s(30f),
            letterSpacing = 1.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = s(20f),
            lineHeight = s(26f)
        ),
        titleLarge = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(18f),
            lineHeight = s(24f)
        ),
        titleMedium = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(16f),
            lineHeight = s(22f)
        ),
        titleSmall = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = s(14f),
            lineHeight = s(20f)
        ),
        bodyLarge = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = s(15f),
            lineHeight = s(22f)
        ),
        bodyMedium = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = s(14f),
            lineHeight = s(20f)
        ),
        bodySmall = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = s(12f),
            lineHeight = s(16f)
        ),
        labelLarge = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = s(14f),
            lineHeight = s(20f),
            letterSpacing = 0.2.sp
        ),
        labelMedium = TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = s(12f),
            lineHeight = s(16f),
            letterSpacing = 0.4.sp
        ),
        labelSmall = TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = s(11f),
            lineHeight = s(14f),
            letterSpacing = 0.3.sp
        )
    )
}
