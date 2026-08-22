package com.voltai.doai.presentation.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme as LegacyMaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val VoltDarkColorScheme = darkColorScheme(
    primary = VoltElectricLime,
    onPrimary = VoltDarkBackground,
    primaryContainer = VoltDarkElevatedSurface,
    onPrimaryContainer = VoltElectricLimeBright,
    secondary = VoltElectricLimeBright,
    onSecondary = VoltDarkBackground,
    background = VoltDarkBackground,
    onBackground = VoltTextPrimary,
    surface = VoltDarkSurface,
    onSurface = VoltTextPrimary,
    surfaceVariant = VoltDarkElevatedSurface,
    onSurfaceVariant = VoltTextSecondary,
    outline = VoltDarkBorder,
    error = VoltError
)

private val VoltLegacyColors = darkColors(
    primary = VoltElectricLime,
    primaryVariant = VoltElectricLimeBright,
    secondary = VoltElectricLimeBright,
    background = VoltDarkBackground,
    surface = VoltDarkSurface,
    onPrimary = VoltDarkBackground,
    onSecondary = VoltDarkBackground,
    onBackground = VoltTextPrimary,
    onSurface = VoltTextPrimary,
    error = VoltError
)

private val VoltShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun VoltTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    Material3Theme(
        colorScheme = VoltDarkColorScheme,
        typography = VoltTypography,
        shapes = VoltShapes
    ) {
        LegacyMaterialTheme(
            colors = VoltLegacyColors,
            typography = VoltLegacyTypography,
            shapes = androidx.compose.material.Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(20.dp)
            ),
            content = content
        )
    }
}