package com.neuralroots.kosh.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.isSpecified
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.neuralroots.kosh.R
import com.neuralroots.kosh.data.prefs.ThemeMode

/*
 * Visual language adapted from nullchat.tech:
 *  - warm ink #171412 / cream #F1ECE3, hairline warm borders
 *  - one accent: ember #FF5B1F (with a soft glow), ember-light #FFB27A
 *  - Instrument Serif for display & money, Geist for text, Geist Mono for small uppercase labels
 */

// ---------------------------------------------------------------- Palette (exact nullchat tokens)
val Ink = Color(0xFF171412)          // hsl(24 12.2% 8%)   — dark background / light foreground
val InkCard = Color(0xFF1D1916)      // hsl(26 13.2% 10.4%) — dark card
val InkRaised = Color(0xFF2A2521)    // hsl(27 12% 14.7%)  — dark secondary / muted / accent
val InkBorder = Color(0xFF322C27)    // hsl(27 12.4% 17.5%) — dark border
val InkMuted = Color(0xFFA89E92)     // hsl(33 10.1% 61.2%) — dark muted text
val Cream = Color(0xFFF1ECE3)        // hsl(39 33.3% 91.8%) — light background / dark foreground
val CreamCard = Color(0xFFFAF7F1)    // hsl(40 52.9% 96.7%) — light card
val CreamRaised = Color(0xFFE6DFD2)  // hsl(39 29.4% 86.7%) — light secondary
val CreamBorder = Color(0xFFD9D0C1)  // hsl(37 21.6% 81%)  — light border
val CreamMuted = Color(0xFF6B6157)   // hsl(30 8.1% 38.8%) — light muted text
val Ember = Color(0xFFFF5B1F)        // hsl(16 100% 56.1%) — the accent
val EmberLight = Color(0xFFFFB27A)
val EmberGlow = Color(0x99FF5B1F)    // rgba(255,91,31,.6)

/** Text colour for content placed on the always-dark hero surfaces. */
val HeroText = Cream

// Legacy names kept so existing call sites compile; mapped onto the new palette.
val Indigo = Ember
val IndigoDeep = Color(0xFFD9481A)
val Violet = EmberLight
val Emerald = Color(0xFF8FC79A)
val Coral = Ember
val Amber = Color(0xFFF2B544)
val Sky = Color(0xFFB8B0A4)

// ---------------------------------------------------------------- Fonts

@OptIn(ExperimentalTextApi::class)
private fun geist(weight: Int, res: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val GeistFamily = FontFamily(
    geist(400, R.font.geist), geist(500, R.font.geist), geist(600, R.font.geist), geist(700, R.font.geist),
)
val GeistMonoFamily = FontFamily(
    geist(400, R.font.geist_mono), geist(500, R.font.geist_mono), geist(600, R.font.geist_mono),
)
val SerifFamily = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/**
 * Text scale for the current screen: 1.0 on regular phones (>= 400dp wide), smaller on narrow
 * phones, and compensated when the user's system font size is very large, so layouts never break.
 */
val LocalTextFit = staticCompositionLocalOf { 1f }

private fun textFit(widthDp: Int, fontScale: Float): Float {
    val byWidth = when {
        widthDp < 340 -> 0.84f
        widthDp < 370 -> 0.90f
        widthDp < 400 -> 0.95f
        else -> 1f
    }
    // Large system font sizes already enlarge text; damp display sizes a little more.
    val byFont = if (fontScale > 1.15f) (1.15f / fontScale).coerceAtLeast(0.85f) else 1f
    return byWidth * byFont
}

private fun TextStyle.fit(f: Float): TextStyle = if (f == 1f) this else copy(
    fontSize = fontSize * f,
    lineHeight = if (lineHeight.isSpecified) lineHeight * f else lineHeight,
)

/** Big serif number style for amounts (hero balances, amount inputs). */
val AmountStyle: TextStyle
    @Composable get() = TextStyle(
        fontFamily = SerifFamily, fontSize = 46.sp, lineHeight = 48.sp, letterSpacing = (-0.01).em,
    ).fit(LocalTextFit.current)

/** Small uppercase mono label (pass UPPERCASE text) — the nullchat "(01) HOW IT WORKS" look. */
val MonoLabel: TextStyle
    @Composable get() = TextStyle(
        fontFamily = GeistMonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp,
        letterSpacing = if (LocalTextFit.current < 1f) 0.10.em else 0.14.em,
    )

private val KoshTypography = Typography(
    displayLarge = TextStyle(fontFamily = SerifFamily, fontSize = 64.sp, lineHeight = 60.sp, letterSpacing = (-0.02).em),
    displayMedium = TextStyle(fontFamily = SerifFamily, fontSize = 52.sp, lineHeight = 50.sp, letterSpacing = (-0.02).em),
    displaySmall = TextStyle(fontFamily = SerifFamily, fontSize = 42.sp, lineHeight = 42.sp, letterSpacing = (-0.01).em),
    headlineLarge = TextStyle(fontFamily = SerifFamily, fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-0.01).em),
    headlineMedium = TextStyle(fontFamily = SerifFamily, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(fontFamily = SerifFamily, fontSize = 28.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = SerifFamily, fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.01).em),
    titleSmall = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = GeistFamily, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = GeistFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = GeistFamily, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(fontFamily = GeistMonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.06.em),
    labelSmall = TextStyle(fontFamily = GeistMonoFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.06.em),
)

// ---------------------------------------------------------------- Semantic money colours

@Immutable
data class KoshColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warning: Color,
    val loan: Color,
    /** Hero surfaces are always ink (dark) in both themes, like nullchat's dark bands. */
    val heroStart: Color,
    val heroEnd: Color,
    val subtle: Color,
    val isDark: Boolean,
    val ember: Color = Ember,
    val emberLight: Color = EmberLight,
    val hairline: Color,
    val muted: Color,
)

private val DarkKosh = KoshColors(
    income = Color(0xFF8FC79A), expense = Ember, transfer = EmberLight,
    warning = Color(0xFFF2B544), loan = Color(0xFFD9B48C),
    heroStart = InkCard, heroEnd = Color(0xFF261A13), subtle = InkRaised, isDark = true,
    hairline = InkBorder, muted = InkMuted,
)
private val LightKosh = KoshColors(
    income = Color(0xFF3F7A4C), expense = Color(0xFFD9481A), transfer = Color(0xFFB4642E),
    warning = Color(0xFFB7791F), loan = Color(0xFF8A6440),
    heroStart = Ink, heroEnd = Color(0xFF2A1C14), subtle = CreamRaised, isDark = false,
    hairline = CreamBorder, muted = CreamMuted,
)

val LocalKoshColors = staticCompositionLocalOf { DarkKosh }

object KoshTheme {
    val colors: KoshColors @Composable get() = LocalKoshColors.current
}

private val DarkScheme = darkColorScheme(
    primary = Cream, onPrimary = Ink,
    primaryContainer = Ember, onPrimaryContainer = Ink,
    secondary = Ember, onSecondary = Ink,
    secondaryContainer = Color(0xFF3A2116), onSecondaryContainer = EmberLight,
    tertiary = Ember, onTertiary = Ink,
    tertiaryContainer = Color(0xFF3A2116), onTertiaryContainer = EmberLight,
    error = Color(0xFFD2503A), onError = Cream,
    errorContainer = Color(0xFF3A1A14), onErrorContainer = Color(0xFFFFB4A6),
    background = Ink, onBackground = Cream,
    surface = Ink, onSurface = Cream,
    surfaceVariant = InkRaised, onSurfaceVariant = InkMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = Cream, inverseOnSurface = Ink, inversePrimary = Ink,
    surfaceBright = InkRaised, surfaceDim = Ink,
    surfaceContainerLowest = Color(0xFF110F0D),
    surfaceContainerLow = InkCard,
    surfaceContainer = Color(0xFF221D19),
    surfaceContainerHigh = InkRaised,
    surfaceContainerHighest = Color(0xFF332D28),
    outline = Color(0xFF4A423B), outlineVariant = InkBorder,
    scrim = Color.Black,
)

private val LightScheme = lightColorScheme(
    primary = Ink, onPrimary = Cream,
    primaryContainer = Ember, onPrimaryContainer = Ink,
    secondary = Color(0xFFD9481A), onSecondary = Cream,
    secondaryContainer = Color(0xFFFFDCCB), onSecondaryContainer = Color(0xFF5A1E08),
    tertiary = Color(0xFFD9481A), onTertiary = Cream,
    tertiaryContainer = Color(0xFFFFDCCB), onTertiaryContainer = Color(0xFF5A1E08),
    error = Color(0xFFC8442E), onError = Cream,
    errorContainer = Color(0xFFFBDAD3), onErrorContainer = Color(0xFF5C1408),
    background = Cream, onBackground = Ink,
    surface = Cream, onSurface = Ink,
    surfaceVariant = CreamRaised, onSurfaceVariant = CreamMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = Ink, inverseOnSurface = Cream, inversePrimary = Cream,
    surfaceBright = CreamCard, surfaceDim = CreamRaised,
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = CreamCard,
    surfaceContainer = Color(0xFFEDE7DC),
    surfaceContainerHigh = CreamRaised,
    surfaceContainerHighest = Color(0xFFDDD5C7),
    outline = Color(0xFFB9AE9D), outlineVariant = CreamBorder,
)

private val KoshShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),   // --radius .75rem
    large = RoundedCornerShape(16.dp),    // --radius-2xl
    extraLarge = RoundedCornerShape(24.dp), // --radius-3xl
)

@Composable
fun KoshTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val fit = textFit(cfg.screenWidthDp, cfg.fontScale)
    val typography = remember(fit) {
        if (fit == 1f) KoshTypography else KoshTypography.run {
            copy(
                displayLarge = displayLarge.fit(fit), displayMedium = displayMedium.fit(fit), displaySmall = displaySmall.fit(fit),
                headlineLarge = headlineLarge.fit(fit), headlineMedium = headlineMedium.fit(fit), headlineSmall = headlineSmall.fit(fit),
                titleLarge = titleLarge.fit(fit),
                // Body text is only nudged so it stays readable.
                titleMedium = titleMedium.fit((1f + fit) / 2f), bodyLarge = bodyLarge.fit((1f + fit) / 2f),
            )
        }
    }
    CompositionLocalProvider(
        LocalKoshColors provides if (dark) DarkKosh else LightKosh,
        LocalTextFit provides fit,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = KoshShapes, content = content)
    }
}
