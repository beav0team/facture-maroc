package com.yassine.facturemaroc.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The shop's colours, in one place.
 *
 * A blue that reads as "banking document" rather than "phone toy", on a very
 * light grey page so white cards separate without needing shadows, which stay
 * barely visible on purpose: a list of invoices should look like paper, not
 * like a stack of floating tiles.
 */
private val Blue = Color(0xFF1D4ED8)
private val BlueDeep = Color(0xFF12337E)
private val BlueWash = Color(0xFFDCE6FF)
private val Teal = Color(0xFF0F766E)
private val TealWash = Color(0xFFCCFBF1)
private val Amber = Color(0xFFB45309)
private val AmberWash = Color(0xFFFEF3C7)
private val Ink = Color(0xFF101828)
private val Muted = Color(0xFF5B6472)
private val Page = Color(0xFFF5F7FB)
private val Card = Color(0xFFFFFFFF)
private val Hairline = Color(0xFFE3E8F0)
private val Danger = Color(0xFFB42318)

private val LightScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueWash,
    onPrimaryContainer = Color(0xFF0A2A6B),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = TealWash,
    onSecondaryContainer = Color(0xFF053B36),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = AmberWash,
    onTertiaryContainer = Color(0xFF4A2A05),
    background = Page,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDF1F7),
    onSurfaceVariant = Muted,
    outline = Color(0xFFC9D1DE),
    outlineVariant = Hairline,
    error = Danger,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E1),
    onErrorContainer = Color(0xFF5C0F08),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DB7FF),
    onPrimary = Color(0xFF06215C),
    primaryContainer = Color(0xFF173A86),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF7DD8CE),
    onSecondary = Color(0xFF00332E),
    secondaryContainer = Color(0xFF124E48),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFF5C26B),
    onTertiary = Color(0xFF3F2600),
    tertiaryContainer = Color(0xFF5C3B0A),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF0D131F),
    onBackground = Color(0xFFE8ECF4),
    surface = Color(0xFF151D2B),
    onSurface = Color(0xFFE8ECF4),
    surfaceVariant = Color(0xFF202A3B),
    onSurfaceVariant = Color(0xFFA9B3C4),
    outline = Color(0xFF3C4A61),
    outlineVariant = Color(0xFF27344A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1710),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun FactureMarocTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
