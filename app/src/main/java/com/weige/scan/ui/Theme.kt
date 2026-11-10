package com.weige.scan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 品牌色：琥珀。深浅两套都围绕它，界面才像同一个产品 */
val Amber = Color(0xFFFF7A18)
val AmberDeep = Color(0xFFE2530A)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFE85D04),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2CC),
    onPrimaryContainer = Color(0xFF4A1B00),
    secondary = Color(0xFF6E6259),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E7DC),
    onSecondaryContainer = Color(0xFF2A211B),
    background = Color(0xFFFFFBF7),
    onBackground = Color(0xFF1E1A17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1A17),
    surfaceVariant = Color(0xFFF6EDE4),
    onSurfaceVariant = Color(0xFF5C5047),
    outline = Color(0xFFDDCEC1),
    outlineVariant = Color(0xFFEDE1D6),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1200),
    primaryContainer = Color(0xFF5A2400),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFD3C3B7),
    onSecondary = Color(0xFF33291F),
    secondaryContainer = Color(0xFF3A2F26),
    onSecondaryContainer = Color(0xFFEFE0D4),
    background = Color(0xFF0A0908),
    onBackground = Color(0xFFF7F1EA),
    surface = Color(0xFF14110F),
    onSurface = Color(0xFFF7F1EA),
    surfaceVariant = Color(0xFF221C17),
    onSurfaceVariant = Color(0xFFC6B8AC),
    outline = Color(0xFF4A3E35),
    outlineVariant = Color(0xFF30281F),
    error = Color(0xFFFF897D),
    onError = Color(0xFF4A0500),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun WeigeScanTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
