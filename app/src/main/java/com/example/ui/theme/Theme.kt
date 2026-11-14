package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class AccentPreset(
    val primary: Color,
    val primaryDark: Color,
    val containerDark: Color,
    val containerLight: Color,
    val onContainerLight: Color,
    val label: String
) {
    CHAMPAGNE_GOLD(ChampagneGold, DeepGold, GoldTintSurface, Color(0xFFF5E9C4), Color(0xFF2B2002), "Champagne Gold"),
    EMERALD(RikzenEmerald, RikzenEmeraldDark, Color(0xFF0E291A), Color(0xFFDCFCE7), Color(0xFF052E16), "Emerald"),
    PLATINUM(RikzenPlatinum, Color(0xFF475569), Color(0xFF1E293B), Color(0xFFE2E8F0), Color(0xFF0F172A), "Platinum")
}
enum class FontSizePreset(val scale: Float, val label: String) {
    COMPACT(0.92f, "Compact"),
    STANDARD(1.0f, "Standard"),
    LARGE(1.14f, "Large")
}

private val RikzenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RikzenChatTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentPreset: AccentPreset = AccentPreset.CHAMPAGNE_GOLD,
    fontSizePreset: FontSizePreset = FontSizePreset.STANDARD,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = remember(isDark, accentPreset) {
        if (isDark) {
            darkColorScheme(
                primary = accentPreset.primary,
                onPrimary = ObsidianBlack,
                primaryContainer = accentPreset.containerDark,
                onPrimaryContainer = if (accentPreset == AccentPreset.CHAMPAGNE_GOLD) BrightGold else accentPreset.primary,
                secondary = RikzenEmerald,
                onSecondary = ObsidianBlack,
                secondaryContainer = Color(0xFF0E2B1B),
                onSecondaryContainer = RikzenEmerald,
                tertiary = BrightGold,
                onTertiary = ObsidianBlack,
                background = ObsidianBlack,
                onBackground = IvoryWhite,
                surface = ObsidianSurface,
                onSurface = IvoryWhite,
                surfaceVariant = ObsidianSurfaceVariant,
                onSurfaceVariant = SilverMuted,
                surfaceContainerLowest = ObsidianBlack,
                surfaceContainerLow = ObsidianSurface,
                surfaceContainer = ObsidianSurfaceVariant,
                surfaceContainerHigh = ObsidianElevated,
                outline = ObsidianOutline,
                outlineVariant = Color(0xFF232230),
                error = CrimsonDanger,
                onError = Color.White
            )
        } else {
            lightColorScheme(
                primary = accentPreset.primaryDark,
                onPrimary = Color.White,
                primaryContainer = accentPreset.containerLight,
                onPrimaryContainer = accentPreset.onContainerLight,
                secondary = RikzenEmeraldDark,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFDCFCE7),
                onSecondaryContainer = Color(0xFF052E16),
                tertiary = ChampagneGold,
                onTertiary = ObsidianBlack,
                background = AlabasterBg,
                onBackground = InkCharcoal,
                surface = AlabasterSurface,
                onSurface = InkCharcoal,
                surfaceVariant = AlabasterSurfaceVariant,
                onSurfaceVariant = InkMuted,
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = AlabasterBg,
                surfaceContainer = AlabasterSurfaceVariant,
                surfaceContainerHigh = Color(0xFFE7DFCB),
                outline = AlabasterOutline,
                outlineVariant = Color(0xFFE8E2D4),
                error = CrimsonDanger,
                onError = Color.White
            )
        }
    }

    val typography = remember(fontSizePreset) {
        buildRikzenTypography(fontSizePreset.scale)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, isDark) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
            onDispose { }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = RikzenShapes,
        content = content
    )
}
