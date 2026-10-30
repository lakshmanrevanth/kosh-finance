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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuralroots.kosh.data.prefs.ThemeMode

// Brand palette
val Indigo = Color(0xFF5B5BD6)
val IndigoDeep = Color(0xFF3E3EB8)
val Violet = Color(0xFF8B5CF6)
val Emerald = Color(0xFF12B76A)
val Coral = Color(0xFFF04438)
val Amber = Color(0xFFF79009)
val Sky = Color(0xFF0BA5EC)

/** Semantic money colours used across the app. Read via `KoshTheme.colors`. */
@Immutable
data class KoshColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warning: Color,
    val loan: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val subtle: Color,
    val isDark: Boolean,
)

private val LightKosh = KoshColors(
    income = Color(0xFF079455), expense = Color(0xFFD92D20), transfer = Color(0xFF0086C9),
    warning = Color(0xFFDC6803), loan = Color(0xFF7A5AF8),
    heroStart = Color(0xFF4F46E5), heroEnd = Color(0xFF9333EA), subtle = Color(0xFFF1F1F8), isDark = false,
)
private val DarkKosh = KoshColors(
    income = Color(0xFF47CD89), expense = Color(0xFFF97066), transfer = Color(0xFF36BFFA),
    warning = Color(0xFFFDB022), loan = Color(0xFF9B8AFB),
    heroStart = Color(0xFF3730A3), heroEnd = Color(0xFF7E22CE), subtle = Color(0xFF1C1D26), isDark = true,
)

val LocalKoshColors = staticCompositionLocalOf { LightKosh }

object KoshTheme {
    val colors: KoshColors @Composable get() = LocalKoshColors.current
}

private val LightScheme = lightColorScheme(
    primary = Indigo, onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4FB), onPrimaryContainer = Color(0xFF1E1B6B),
    secondary = Violet, onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE5FE), onSecondaryContainer = Color(0xFF2E1065),
    tertiary = Emerald, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FADF), onTertiaryContainer = Color(0xFF053321),
    error = Coral, onError = Color.White,
    background = Color(0xFFF7F7FB), onBackground = Color(0xFF101828),
    surface = Color(0xFFF7F7FB), onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFEDEDF5), onSurfaceVariant = Color(0xFF475467),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF2F2F8),
    surfaceContainerHigh = Color(0xFFECECF4),
    surfaceContainerHighest = Color(0xFFE6E6EF),
    outline = Color(0xFFD0D5DD), outlineVariant = Color(0xFFE4E7EC),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8E8EF5), onPrimary = Color(0xFF15134D),
    primaryContainer = Color(0xFF2E2C80), onPrimaryContainer = Color(0xFFE4E4FB),
    secondary = Color(0xFFB794F6), onSecondary = Color(0xFF2E1065),
    secondaryContainer = Color(0xFF4C1D95), onSecondaryContainer = Color(0xFFEDE5FE),
    tertiary = Color(0xFF47CD89), onTertiary = Color(0xFF053321),
    tertiaryContainer = Color(0xFF074D31), onTertiaryContainer = Color(0xFFD1FADF),
    error = Color(0xFFF97066), onError = Color(0xFF55160C),
    background = Color(0xFF0C0D12), onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF0C0D12), onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF1F2029), onSurfaceVariant = Color(0xFF98A2B3),
    surfaceContainerLowest = Color(0xFF08090C),
    surfaceContainerLow = Color(0xFF14151C),
    surfaceContainer = Color(0xFF181920),
    surfaceContainerHigh = Color(0xFF1F2029),
    surfaceContainerHighest = Color(0xFF282933),
    outline = Color(0xFF475467), outlineVariant = Color(0xFF2A2C36),
)

private val KoshTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Big tabular number style for amounts. */
val AmountStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)

private val KoshShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun KoshTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
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
    CompositionLocalProvider(LocalKoshColors provides if (dark) DarkKosh else LightKosh) {
        MaterialTheme(colorScheme = scheme, typography = KoshTypography, shapes = KoshShapes, content = content)
    }
}
