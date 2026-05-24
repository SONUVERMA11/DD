package com.sonu.dd.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extended color scheme that provides DD-specific semantic colors
 * beyond what Material3 offers by default.
 */
@Immutable
data class DDColorScheme(
    val materialScheme: ColorScheme,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentVariant: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val cardBorder: Color,
    val isDark: Boolean
)

val LocalDDColors = staticCompositionLocalOf<DDColorScheme> {
    error("No DDColorScheme provided")
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Theme enum
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
enum class DDTheme(val displayName: String) {
    MIDNIGHT_DARK("Midnight Dark"),
    CONTRAST_DARK("Contrast Dark"),
    SOFT_LIGHT("Soft Light"),
    WARM_SEPIA("Warm Sepia"),
    FOREST_GREEN("Forest Green");

    companion object {
        fun fromOrdinal(ordinal: Int): DDTheme =
            entries.getOrElse(ordinal) { MIDNIGHT_DARK }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Color scheme builders
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

fun midnightDarkScheme(): DDColorScheme = DDColorScheme(
    materialScheme = darkColorScheme(
        primary = MidnightColors.Accent,
        onPrimary = MidnightColors.OnAccent,
        primaryContainer = MidnightColors.AccentVariant,
        secondary = MidnightColors.TextSecondary,
        background = MidnightColors.Background,
        onBackground = MidnightColors.Text,
        surface = MidnightColors.Surface,
        onSurface = MidnightColors.Text,
        surfaceVariant = MidnightColors.SurfaceVariant,
        onSurfaceVariant = MidnightColors.TextSecondary,
        outline = MidnightColors.Outline,
        error = MidnightColors.Error,
        onError = Color.White
    ),
    textSecondary = MidnightColors.TextSecondary,
    textTertiary = MidnightColors.TextTertiary,
    accent = MidnightColors.Accent,
    accentVariant = MidnightColors.AccentVariant,
    onAccent = MidnightColors.OnAccent,
    success = MidnightColors.Success,
    warning = MidnightColors.Warning,
    cardBorder = MidnightColors.CardBorder,
    isDark = true
)

fun contrastDarkScheme(): DDColorScheme = DDColorScheme(
    materialScheme = darkColorScheme(
        primary = ContrastColors.Accent,
        onPrimary = ContrastColors.OnAccent,
        primaryContainer = ContrastColors.AccentVariant,
        secondary = ContrastColors.TextSecondary,
        background = ContrastColors.Background,
        onBackground = ContrastColors.Text,
        surface = ContrastColors.Surface,
        onSurface = ContrastColors.Text,
        surfaceVariant = ContrastColors.SurfaceVariant,
        onSurfaceVariant = ContrastColors.TextSecondary,
        outline = ContrastColors.Outline,
        error = ContrastColors.Error,
        onError = Color.Black
    ),
    textSecondary = ContrastColors.TextSecondary,
    textTertiary = ContrastColors.TextTertiary,
    accent = ContrastColors.Accent,
    accentVariant = ContrastColors.AccentVariant,
    onAccent = ContrastColors.OnAccent,
    success = ContrastColors.Success,
    warning = ContrastColors.Warning,
    cardBorder = ContrastColors.CardBorder,
    isDark = true
)

fun softLightScheme(): DDColorScheme = DDColorScheme(
    materialScheme = lightColorScheme(
        primary = SoftLightColors.Accent,
        onPrimary = SoftLightColors.OnAccent,
        primaryContainer = SoftLightColors.AccentVariant,
        secondary = SoftLightColors.TextSecondary,
        background = SoftLightColors.Background,
        onBackground = SoftLightColors.Text,
        surface = SoftLightColors.Surface,
        onSurface = SoftLightColors.Text,
        surfaceVariant = SoftLightColors.SurfaceVariant,
        onSurfaceVariant = SoftLightColors.TextSecondary,
        outline = SoftLightColors.Outline,
        error = SoftLightColors.Error,
        onError = Color.White
    ),
    textSecondary = SoftLightColors.TextSecondary,
    textTertiary = SoftLightColors.TextTertiary,
    accent = SoftLightColors.Accent,
    accentVariant = SoftLightColors.AccentVariant,
    onAccent = SoftLightColors.OnAccent,
    success = SoftLightColors.Success,
    warning = SoftLightColors.Warning,
    cardBorder = SoftLightColors.CardBorder,
    isDark = false
)

fun warmSepiaScheme(): DDColorScheme = DDColorScheme(
    materialScheme = darkColorScheme(
        primary = SepiaColors.Accent,
        onPrimary = SepiaColors.OnAccent,
        primaryContainer = SepiaColors.AccentVariant,
        secondary = SepiaColors.TextSecondary,
        background = SepiaColors.Background,
        onBackground = SepiaColors.Text,
        surface = SepiaColors.Surface,
        onSurface = SepiaColors.Text,
        surfaceVariant = SepiaColors.SurfaceVariant,
        onSurfaceVariant = SepiaColors.TextSecondary,
        outline = SepiaColors.Outline,
        error = SepiaColors.Error,
        onError = Color.White
    ),
    textSecondary = SepiaColors.TextSecondary,
    textTertiary = SepiaColors.TextTertiary,
    accent = SepiaColors.Accent,
    accentVariant = SepiaColors.AccentVariant,
    onAccent = SepiaColors.OnAccent,
    success = SepiaColors.Success,
    warning = SepiaColors.Warning,
    cardBorder = SepiaColors.CardBorder,
    isDark = true
)

fun forestGreenScheme(): DDColorScheme = DDColorScheme(
    materialScheme = darkColorScheme(
        primary = ForestColors.Accent,
        onPrimary = ForestColors.OnAccent,
        primaryContainer = ForestColors.AccentVariant,
        secondary = ForestColors.TextSecondary,
        background = ForestColors.Background,
        onBackground = ForestColors.Text,
        surface = ForestColors.Surface,
        onSurface = ForestColors.Text,
        surfaceVariant = ForestColors.SurfaceVariant,
        onSurfaceVariant = ForestColors.TextSecondary,
        outline = ForestColors.Outline,
        error = ForestColors.Error,
        onError = Color.White
    ),
    textSecondary = ForestColors.TextSecondary,
    textTertiary = ForestColors.TextTertiary,
    accent = ForestColors.Accent,
    accentVariant = ForestColors.AccentVariant,
    onAccent = ForestColors.OnAccent,
    success = ForestColors.Success,
    warning = ForestColors.Warning,
    cardBorder = ForestColors.CardBorder,
    isDark = true
)

fun DDTheme.toColorScheme(): DDColorScheme = when (this) {
    DDTheme.MIDNIGHT_DARK -> midnightDarkScheme()
    DDTheme.CONTRAST_DARK -> contrastDarkScheme()
    DDTheme.SOFT_LIGHT -> softLightScheme()
    DDTheme.WARM_SEPIA -> warmSepiaScheme()
    DDTheme.FOREST_GREEN -> forestGreenScheme()
}
