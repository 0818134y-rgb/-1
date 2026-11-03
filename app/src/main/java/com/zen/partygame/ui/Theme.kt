package com.zen.partygame.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Night = Color(0xFF15102D)
val NightCard = Color(0xFF221F45)
val Lantern = Color(0xFFFFB347)
val Berry = Color(0xFFFF5C8A)
val Mint = Color(0xFF5CE1B9)
val Soft = Color(0xFFE9E6FF)
val Muted = Color(0xFFA6A1D0)

private val scheme = darkColorScheme(
    primary = Lantern, onPrimary = Color(0xFF2B1700),
    secondary = Berry, onSecondary = Color.White,
    tertiary = Mint, onTertiary = Color(0xFF00281C),
    background = Night, onBackground = Soft,
    surface = Night, onSurface = Soft,
    surfaceVariant = NightCard, onSurfaceVariant = Muted,
    surfaceContainer = NightCard, surfaceContainerHigh = Color(0xFF2C2858),
    error = Color(0xFFFF6B6B),
)

private val type = Typography(
    displayLarge = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Black),
    displayMedium = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Black),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun PartyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type) {
        // 文字の色の既定値を明るい色に（背景の上に直接置いた見出しが暗くならないように）
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
