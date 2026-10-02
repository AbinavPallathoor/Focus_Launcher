package com.focuslauncher.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Pure black / white / dark-grey palette — no accent colors, except the
// terminal-green used only for the radial menu's hacker-style scramble text.
val PureBlack = Color(0xFF000000)
val PureWhite = Color(0xFFFFFFFF)
val SubtextGrey = Color(0xFFA9A9A9)
val HackerGreen = Color(0xFF39FF14)

// Platform-built-in monospace — guaranteed to render correctly offline with
// no Play Services/network dependency (a downloadable Google Font silently
// falls back to the proportional system default when it can't be fetched,
// which is not an acceptable fallback for a "monospace everywhere" app).
val GeistMonoFamily = FontFamily.Monospace

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = GeistMonoFamily, fontSize = 28.sp, color = PureWhite),
    headlineMedium = TextStyle(fontFamily = GeistMonoFamily, fontSize = 20.sp, color = PureWhite),
    bodyLarge = TextStyle(fontFamily = GeistMonoFamily, fontSize = 18.sp, color = PureWhite),
    bodyMedium = TextStyle(fontFamily = GeistMonoFamily, fontSize = 15.sp, color = SubtextGrey),
    bodySmall = TextStyle(fontFamily = GeistMonoFamily, fontSize = 13.sp, color = SubtextGrey),
)

private val AppColorScheme = darkColorScheme(
    background = PureBlack,
    surface = PureBlack,
    onBackground = PureWhite,
    onSurface = PureWhite,
    primary = PureWhite,
    onPrimary = PureBlack,
)

@Composable
fun FocusLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}
