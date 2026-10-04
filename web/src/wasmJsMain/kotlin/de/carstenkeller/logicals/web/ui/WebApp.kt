package de.carstenkeller.logicals.web.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import de.carstenkeller.logicals.core.PuzzleOptions
import de.carstenkeller.logicals.core.PuzzleType
import de.carstenkeller.logicals.data.ThemeMode
import de.carstenkeller.logicals.ui.game.GameController
import de.carstenkeller.logicals.ui.game.GameScreen
import de.carstenkeller.logicals.ui.menu.MainMenuScreen
import de.carstenkeller.logicals.ui.menu.PuzzleHomeScreen
import de.carstenkeller.logicals.web.Browser
import de.carstenkeller.logicals.web.data.GameRepository
import de.carstenkeller.logicals.web.data.Settings
import de.carstenkeller.logicals.web.resources.Res
import de.carstenkeller.logicals.web.resources.noto_color_emoji
import de.carstenkeller.logicals.web.resources.noto_sans_math_symbols
import de.carstenkeller.logicals.web.resources.roboto_bold
import de.carstenkeller.logicals.web.resources.roboto_medium
import de.carstenkeller.logicals.web.resources.roboto_regular
import de.carstenkeller.logicals.web.ui.theme.LogicalsTheme
import de.carstenkeller.logicals.web.ui.theme.backgroundColor
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

/** Bildschirme der App; ersetzt die Navigation (navigation-compose) der Android-App. */
private sealed interface Screen {
    data object Menu : Screen
    data class Home(val type: PuzzleType) : Screen
    /** [newGame] = null: gespeicherten Stand fortsetzen. */
    data class Game(val type: PuzzleType, val newGame: PuzzleOptions?) : Screen
}

/** Rückstapel mit eindeutiger Nummer je Eintrag, damit ein neuer Bildschirm neuen Zustand bekommt. */
private class BackStack {
    private var nextId = 0
    var entries by mutableStateOf(listOf(nextId++ to (Screen.Menu as Screen)))
        private set

    fun push(screen: Screen) {
        entries = entries + (nextId++ to screen)
    }

    fun pop() {
        if (entries.size > 1) entries = entries.dropLast(1)
    }

    fun popToMenu() {
        entries = entries.take(1)
    }
}

@Composable
fun WebApp() {
    WithFonts { fontFamily ->
        var themeMode by remember { mutableStateOf(Settings.themeMode) }
        val dark = when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        // Seitenhintergrund und Statusleiste an die gewählte Darstellung anpassen.
        LaunchedEffect(dark) { Browser.setPageColor(cssColor(backgroundColor(dark).toArgb())) }
        LaunchedEffect(Unit) { Browser.hideLoadingScreen() }

        LogicalsTheme(fontFamily = fontFamily, dark = dark) {
            val stack = remember { BackStack() }
            val (id, screen) = stack.entries.last()
            key(id) {
                when (screen) {
                    Screen.Menu -> MainMenuScreen(
                        onSelect = { stack.push(Screen.Home(it)) },
                        themeMode = themeMode,
                        onThemeModeChange = {
                            themeMode = it
                            Settings.themeMode = it
                        },
                        versionName = Browser.version,
                    )
                    is Screen.Home -> PuzzleHomeScreen(
                        type = screen.type,
                        repository = GameRepository,
                        settings = Settings,
                        onBack = stack::pop,
                        onContinue = { stack.push(Screen.Game(screen.type, newGame = null)) },
                        onNewGame = { options -> stack.push(Screen.Game(screen.type, options)) },
                    )
                    is Screen.Game -> GameRoute(
                        type = screen.type,
                        newGame = screen.newGame,
                        onBack = stack::pop,
                        onToMenu = stack::popToMenu,
                    )
                }
            }
        }
    }
}

/** Spielbildschirm; die Zeit läuft nur, solange er offen und die Seite sichtbar ist. */
@Composable
private fun GameRoute(type: PuzzleType, newGame: PuzzleOptions?, onBack: () -> Unit, onToMenu: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember(type, newGame) {
        GameController(type, newGame, scope, repository = GameRepository, settings = Settings)
    }
    DisposableEffect(controller) {
        if (!Browser.pageHidden) controller.onScreenResumed()
        val unregister = Browser.onVisibilityChange {
            if (Browser.pageHidden) controller.onScreenPaused() else controller.onScreenResumed()
        }
        onDispose {
            unregister()
            controller.onScreenPaused()
        }
    }
    GameScreen(controller, onBack = onBack, onToMenu = onToMenu)
}

/**
 * Lädt die mitgelieferten Schriften, bevor etwas gezeichnet wird. Symbol- und Emoji-Schrift
 * werden als Ersatzschriften registriert; ohne sie lädt Compose fehlende Zeichen von
 * fonts.gstatic.com nach (offline nicht verfügbar).
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun WithFonts(content: @Composable (FontFamily) -> Unit) {
    val regular by preloadFont(Res.font.roboto_regular, FontWeight.Normal)
    val medium by preloadFont(Res.font.roboto_medium, FontWeight.Medium)
    val bold by preloadFont(Res.font.roboto_bold, FontWeight.Bold)
    val symbols by preloadFont(Res.font.noto_sans_math_symbols)
    val emoji by preloadFont(Res.font.noto_color_emoji)
    val resolver = LocalFontFamilyResolver.current
    var fallbacksReady by remember { mutableStateOf(false) }

    val fallbacks = listOfNotNull(symbols, emoji)
    LaunchedEffect(fallbacks.size) {
        if (fallbacks.size == 2) {
            fallbacks.forEach { font: Font -> resolver.preload(FontFamily(font)) }
            println("Ersatzschriften registriert")
            fallbacksReady = true
        }
    }
    val r = regular
    val m = medium
    val b = bold
    if (fallbacksReady && r != null && m != null && b != null) {
        val family = remember(r, m, b) { FontFamily(r, m, b) }
        content(family)
    }
}

private fun cssColor(argb: Int): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')
