package com.daylyfood.app.ui.theme

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

// Warm "food" palette: orange for energy, leaf green for protein/health actions,
// cream paper background so white cards float.
private val Orange = Color(0xFFE8833A)
private val OrangeContainer = Color(0xFFFFE2C9)
private val Leaf = Color(0xFF5F8D4E)
private val LeafContainer = Color(0xFFDCEBD1)
private val Cream = Color(0xFFFBF7F0)
private val Ink = Color(0xFF2B2620)

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = OrangeContainer,
    onPrimaryContainer = Color(0xFF3F1E05),
    secondary = Leaf,
    onSecondary = Color.White,
    secondaryContainer = LeafContainer,
    onSecondaryContainer = Color(0xFF16280D),
    tertiary = Color(0xFFB25E3B),
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF2ECE1),
    onSurfaceVariant = Color(0xFF5E574C),
    outline = Color(0xFF9A9184),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB077),
    onPrimary = Color(0xFF3F1E05),
    primaryContainer = Color(0xFF5C3210),
    onPrimaryContainer = Color(0xFFFFE2C9),
    secondary = Color(0xFFA8C98F),
    onSecondary = Color(0xFF16280D),
    secondaryContainer = Color(0xFF33421F),
    onSecondaryContainer = Color(0xFFDCEBD1),
    tertiary = Color(0xFFE0A184),
    background = Color(0xFF15120E),
    onBackground = Color(0xFFEDE5D8),
    surface = Color(0xFF1D1915),
    onSurface = Color(0xFFEDE5D8),
    surfaceVariant = Color(0xFF2A241D),
    onSurfaceVariant = Color(0xFFC6BCAD),
    outline = Color(0xFF7A7266),
    error = Color(0xFFF2B8B5),
)

/** Slightly tighter, larger headings than the Material default. */
private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun DaylyFoodTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
