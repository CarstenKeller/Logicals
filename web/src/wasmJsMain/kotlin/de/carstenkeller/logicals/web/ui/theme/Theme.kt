package de.carstenkeller.logicals.web.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import de.carstenkeller.logicals.ui.theme.LogicalsDarkColors
import de.carstenkeller.logicals.ui.theme.LogicalsLightColors

@Composable
fun LogicalsTheme(fontFamily: FontFamily, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) LogicalsDarkColors else LogicalsLightColors,
        typography = Typography().withFontFamily(fontFamily),
        content = content,
    )
}

/** Hintergrundfarbe der Seite außerhalb der Compose-Fläche (Ränder, Statusleiste). */
fun backgroundColor(dark: Boolean): Color = if (dark) LogicalsDarkColors.background else LogicalsLightColors.background

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
