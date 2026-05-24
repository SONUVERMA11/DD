package com.sonu.dd.core.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Root theme composable for DD app.
 * Applies the selected DDTheme via CompositionLocalProvider.
 */
@Composable
fun DDAppTheme(
    theme: DDTheme = DDTheme.MIDNIGHT_DARK,
    followSystem: Boolean = false,
    content: @Composable () -> Unit
) {
    val resolvedTheme = if (followSystem) {
        if (isSystemInDarkTheme()) DDTheme.MIDNIGHT_DARK else DDTheme.SOFT_LIGHT
    } else {
        theme
    }

    val ddColors = remember(resolvedTheme) { resolvedTheme.toColorScheme() }

    // Animate color transitions for smooth theme switching
    val animatedBackground by animateColorAsState(
        targetValue = ddColors.materialScheme.background,
        animationSpec = tween(300),
        label = "bg_anim"
    )
    val animatedSurface by animateColorAsState(
        targetValue = ddColors.materialScheme.surface,
        animationSpec = tween(300),
        label = "surface_anim"
    )
    val animatedPrimary by animateColorAsState(
        targetValue = ddColors.materialScheme.primary,
        animationSpec = tween(300),
        label = "primary_anim"
    )
    val animatedOnBackground by animateColorAsState(
        targetValue = ddColors.materialScheme.onBackground,
        animationSpec = tween(300),
        label = "onBg_anim"
    )
    val animatedOnSurface by animateColorAsState(
        targetValue = ddColors.materialScheme.onSurface,
        animationSpec = tween(300),
        label = "onSurface_anim"
    )
    val animatedAccent by animateColorAsState(
        targetValue = ddColors.accent,
        animationSpec = tween(300),
        label = "accent_anim"
    )

    val animatedScheme = ddColors.materialScheme.copy(
        background = animatedBackground,
        surface = animatedSurface,
        primary = animatedPrimary,
        onBackground = animatedOnBackground,
        onSurface = animatedOnSurface,
    )

    val animatedDDColors = ddColors.copy(
        materialScheme = animatedScheme,
        accent = animatedAccent
    )

    CompositionLocalProvider(LocalDDColors provides animatedDDColors) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = DDTypography,
            content = content
        )
    }
}

/**
 * Convenience accessor for DD extended colors anywhere in the composition tree.
 */
object DDThemeColors {
    val current: DDColorScheme
        @Composable
        get() = LocalDDColors.current
}
