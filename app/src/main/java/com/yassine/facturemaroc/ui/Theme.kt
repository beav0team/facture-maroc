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
 *
 * Every M3 role is filled, including the tonal surface ladder
 * (`surfaceContainerLowest` … `surfaceContainerHighest`). Components ask the
 * theme for their surface instead of screens inventing a grey, so dark mode
 * stays tonally consistent instead of being tuned by eye per screen.
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
    inversePrimary = Color(0xFF9DB7FF),
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
    surfaceTint = Blue,
    surfaceDim = Color(0xFFDCE1EA),
    surfaceBright = Color(0xFFFBFCFE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF1F4F9),
    surfaceContainerHigh = Color(0xFFE9EEF6),
    surfaceContainerHighest = Color(0xFFE2E8F2),
    inverseSurface = Color(0xFF26303F),
    inverseOnSurface = Color(0xFFF1F4F9),
    outline = Color(0xFFC9D1DE),
    outlineVariant = Hairline,
    error = Danger,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E1),
    onErrorContainer = Color(0xFF5C0F08),
    scrim = Color.Black,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DB7FF),
    onPrimary = Color(0xFF06215C),
    primaryContainer = Color(0xFF173A86),
    onPrimaryContainer = Color(0xFFDCE6FF),
    inversePrimary = Blue,
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
    surfaceTint = Color(0xFF9DB7FF),
    surfaceDim = Color(0xFF0B1019),
    surfaceBright = Color(0xFF333F52),
    surfaceContainerLowest = Color(0xFF080D16),
    surfaceContainerLow = Color(0xFF111826),
    surfaceContainer = Color(0xFF171F2E),
    surfaceContainerHigh = Color(0xFF1D2636),
    surfaceContainerHighest = Color(0xFF242E40),
    inverseSurface = Color(0xFFE8ECF4),
    inverseOnSurface = Color(0xFF151D2B),
    outline = Color(0xFF3C4A61),
    outlineVariant = Color(0xFF27344A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1710),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

/**
 * M3's full type scale, with the shop's own voice.
 *
 * Roles that a screen does not override inherit from here, which is the point:
 * a heading should not need a local `fontSize` to look like a heading. Display
 * and headline are tight and heavy (a document, not a dashboard); body copy is
 * left at system sizes for legibility at 360 dp.
 */
private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.0).sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.8).sp,
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
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

/**
 * Money and quantity figures.
 *
 * M3 has no role for a number, so the shop gets one: tabular figures
 * (`tnum`) keep columns aligned so two amounts of different widths still line
 * up digit for digit, which is what makes a list of invoices scannable.
 * Deliberately not a bundled font — the system face already has tabular
 * digits, and an offline app should not carry a 400 KB font for that.
 */
val Typography.numerals: TextStyle
    get() = titleMedium.copy(fontFeatureSettings = "tnum")

/** Small numerals for secondary amounts (a line under a total, a stock count). */
val Typography.numeralsSmall: TextStyle
    get() = bodyMedium.copy(fontFeatureSettings = "tnum")

/** Large numerals for the one dominant amount a screen is built around. */
val Typography.numeralsLarge: TextStyle
    get() = headlineMedium.copy(fontFeatureSettings = "tnum")

/** Shape carries hierarchy: a card is softer than a chip, a sheet softer still. */
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
