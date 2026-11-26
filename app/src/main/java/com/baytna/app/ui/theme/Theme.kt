package com.baytna.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Emerald = Color(0xFF16A36A)
private val EmeraldDark = Color(0xFF0E7A4F)

// Light theme text colors are darker than the brand tones so that text and white-on-primary buttons stay at
// 4.5:1 or more on every light surface (background, cards and the high container).
private val EmeraldText = Color(0xFF0E7A4F)
private val DarkBackground = Color(0xFF101713)
private val DarkSurface = Color(0xFF19231D)
private val LightBackground = Color(0xFFF7F9F7)
private val TextDark = Color(0xFF202A24)

/** Semantic finance colors, independent of the Material scheme. */
@Immutable
data class FinanceColors(val income: Color, val expense: Color, val warning: Color, val transfer: Color)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceColors(income = Emerald, expense = Color(0xFFE36D6D), warning = Color(0xFFE6A23C), transfer = Color(0xFF5B8DEF))
}

private val LightColors = lightColorScheme(
    primary = EmeraldText,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFDD),
    onPrimaryContainer = Color(0xFF00391F),
    secondary = Color(0xFF4E6356),
    background = LightBackground,
    onBackground = TextDark,
    surface = LightBackground,
    onSurface = TextDark,
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDF2EE),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3CC68A),
    onPrimary = Color(0xFF00391F),
    primaryContainer = EmeraldDark,
    onPrimaryContainer = Color(0xFFCDEFDD),
    secondary = Color(0xFFB4CCBC),
    background = DarkBackground,
    onBackground = Color(0xFFE1E8E3),
    surface = DarkBackground,
    onSurface = Color(0xFFE1E8E3),
    surfaceContainer = DarkSurface,
    surfaceContainerLow = DarkSurface,
    surfaceContainerHigh = Color(0xFF223028),
    error = Color(0xFFFF8A80),
)

@Composable
fun BaytnaTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val finance = if (darkTheme) {
        FinanceColors(income = Color(0xFF3CC68A), expense = Color(0xFFF08A8A), warning = Color(0xFFF0B85C), transfer = Color(0xFF8AB0F5))
    } else {
        FinanceColors(income = EmeraldText, expense = Color(0xFFB03E3E), warning = Color(0xFF8F6210), transfer = Color(0xFF3563C4))
    }
    CompositionLocalProvider(LocalFinanceColors provides finance) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}
