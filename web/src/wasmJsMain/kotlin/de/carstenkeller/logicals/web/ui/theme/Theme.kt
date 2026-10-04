package de.carstenkeller.logicals.web.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

// Farben wie in der Android-App (dort ab Android 12 durch dynamische Farben ersetzt).
private val LightColors = lightColorScheme(
    primary = Color(0xFF3949AB),
    secondary = Color(0xFF00897B),
    tertiary = Color(0xFFF4511E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FA8DA),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFFFAB91),
)

@Composable
fun LogicalsTheme(fontFamily: FontFamily, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography().withFontFamily(fontFamily),
        content = content,
    )
}

/** Hintergrundfarbe der Seite außerhalb der Compose-Fläche (Ränder, Statusleiste). */
fun backgroundColor(dark: Boolean): Color = if (dark) DarkColors.background else LightColors.background

/** Die Web-App bringt ihre Schrift mit; ohne sie würde Compose Schriften aus dem Netz nachladen. */
private fun Typography.withFontFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = displayLarge.f(),
        displayMedium = displayMedium.f(),
        displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(),
        headlineMedium = headlineMedium.f(),
        headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(),
        titleMedium = titleMedium.f(),
        titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(),
        bodyMedium = bodyMedium.f(),
        bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(),
        labelMedium = labelMedium.f(),
        labelSmall = labelSmall.f(),
    )
}
