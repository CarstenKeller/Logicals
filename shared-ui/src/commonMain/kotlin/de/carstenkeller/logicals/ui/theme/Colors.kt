package de.carstenkeller.logicals.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Farben der App (Android ab 12 nutzt stattdessen die dynamischen Systemfarben). */
val LogicalsLightColors = lightColorScheme(
    primary = Color(0xFF3949AB),
    secondary = Color(0xFF00897B),
    tertiary = Color(0xFFF4511E),
)

val LogicalsDarkColors = darkColorScheme(
    primary = Color(0xFF9FA8DA),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFFFAB91),
)
