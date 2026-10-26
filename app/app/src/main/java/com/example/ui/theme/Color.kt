package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// True Google Pay (GPay India / Tez UX) Light Color Palette
val GPayWhite = Color(0xFFFFFFFF)
val GPayOffWhite = Color(0xFFF8F9FA)
val GPaySurface = Color(0xFFFFFFFF)
val GPaySurfaceVariant = Color(0xFFF1F3F4)
val GPayBorder = Color(0xFFDADCE0)

// Google Brand Accents
val GPayBlue = Color(0xFF1A73E8)
val GPayBlueDark = Color(0xFF1557B0)
val GPayBlueLight = Color(0xFF4285F4)
val GPayPillFill = Color(0xFFE8F0FE)

val GPayGreen = Color(0xFF1E8E3E)
val GPayGreenAccent = Color(0xFF34A853)
val GPayGreenLight = Color(0xFFE6F4EA)

val GPayRed = Color(0xFFD93025)
val GPayRedAccent = Color(0xFFEA4335)
val GPayRedLight = Color(0xFFFCE8E6)

val GPayYellow = Color(0xFFF9AB00)
val GPayYellowAccent = Color(0xFFFBBC04)
val GPayYellowLight = Color(0xFFFEF7E0)

// Typography Colors
val GPayPrimaryText = Color(0xFF202124)
val GPaySecondaryText = Color(0xFF5F6368)
val GPayTertiaryText = Color(0xFF80868B)

// Dark Theme Equivalents (adhering to Google Material 3 Dark Guidelines)
val GPayDarkBackground = Color(0xFF131314)
val GPayDarkSurface = Color(0xFF1E1F20)
val GPayDarkBorder = Color(0xFF3C4043)
val GPayDarkPrimaryText = Color(0xFFE8EAED)
val GPayDarkSecondaryText = Color(0xFF9AA0A6)
val GPayDarkBlue = Color(0xFF8AB4F8)
val GPayDarkPillFill = Color(0xFF174EA6)

// Authentic GPay Contact Avatar Palette
val GPayAvatarColors = listOf(
    Color(0xFF1A73E8), // Google Blue
    Color(0xFF1E8E3E), // Google Green
    Color(0xFF9334E6), // Deep Purple
    Color(0xFFE8710A), // Warm Orange
    Color(0xFF007B83), // Teal
    Color(0xFFC5221F), // Crimson
    Color(0xFF185ABC), // Royal Blue
    Color(0xFF137333)  // Forest Green
)

fun avatarColorForName(name: String): Color {
    if (name.isBlank()) return GPayBlue
    val hash = name.trim().uppercase().fold(0) { acc, c -> acc * 31 + c.code }
    return GPayAvatarColors[(hash and Int.MAX_VALUE) % GPayAvatarColors.size]
}
