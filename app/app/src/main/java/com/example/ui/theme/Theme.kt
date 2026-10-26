package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GPayLightColorScheme = lightColorScheme(
    primary = GPayBlue,
    onPrimary = Color.White,
    primaryContainer = GPayPillFill,
    onPrimaryContainer = GPayBlue,
    secondary = GPayGreen,
    onSecondary = Color.White,
    secondaryContainer = GPayGreenLight,
    onSecondaryContainer = GPayGreen,
    tertiary = GPayYellow,
    onTertiary = GPayPrimaryText,
    tertiaryContainer = GPayYellowLight,
    onTertiaryContainer = GPayPrimaryText,
    error = GPayRed,
    onError = Color.White,
    errorContainer = GPayRedLight,
    onErrorContainer = GPayRed,
    background = GPayWhite,
    onBackground = GPayPrimaryText,
    surface = GPaySurface,
    onSurface = GPayPrimaryText,
    surfaceVariant = GPayOffWhite,
    onSurfaceVariant = GPaySecondaryText,
    outline = GPayBorder,
    outlineVariant = GPaySurfaceVariant
)

private val GPayDarkColorScheme = darkColorScheme(
    primary = GPayDarkBlue,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = GPayDarkPillFill,
    onPrimaryContainer = Color(0xFFD2E3FC),
    secondary = GPayGreenAccent,
    onSecondary = Color(0xFF0D652D),
    secondaryContainer = Color(0xFF0D652D),
    onSecondaryContainer = GPayGreenLight,
    tertiary = GPayYellowAccent,
    onTertiary = Color(0xFF3C2F00),
    error = GPayRedAccent,
    onError = Color(0xFF690005),
    background = GPayDarkBackground,
    onBackground = GPayDarkPrimaryText,
    surface = GPayDarkSurface,
    onSurface = GPayDarkPrimaryText,
    surfaceVariant = Color(0xFF28292A),
    onSurfaceVariant = GPayDarkSecondaryText,
    outline = GPayDarkBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Pure Google Pay Light Palette by default per specification
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        GPayDarkColorScheme
    } else {
        GPayLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
