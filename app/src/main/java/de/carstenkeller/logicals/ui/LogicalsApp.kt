package de.carstenkeller.logicals.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.carstenkeller.logicals.BuildConfig
import de.carstenkeller.logicals.core.PuzzleOptions
import de.carstenkeller.logicals.core.PuzzleType
import de.carstenkeller.logicals.data.GameRepository
import de.carstenkeller.logicals.data.Settings
import de.carstenkeller.logicals.data.ThemeMode
import de.carstenkeller.logicals.ui.game.GameScreen
import de.carstenkeller.logicals.ui.game.GameViewModel
import de.carstenkeller.logicals.ui.menu.MainMenuScreen
import de.carstenkeller.logicals.ui.menu.PuzzleHomeScreen

object GameArgs {
    const val TYPE = "type"
    const val NEW = "new"
    const val DIFFICULTY = "difficulty"
    const val WIDTH = "w"
    const val HEIGHT = "h"
}

private object Routes {
    const val MENU = "menu"
    const val HOME = "home/{${GameArgs.TYPE}}"
    const val GAME = "game/{${GameArgs.TYPE}}?${GameArgs.NEW}={${GameArgs.NEW}}" +
        "&${GameArgs.DIFFICULTY}={${GameArgs.DIFFICULTY}}" +
        "&${GameArgs.WIDTH}={${GameArgs.WIDTH}}&${GameArgs.HEIGHT}={${GameArgs.HEIGHT}}"

    fun home(type: PuzzleType) = "home/${type.name}"

    fun continueGame(type: PuzzleType) = "game/${type.name}?${GameArgs.NEW}=false"

    fun newGame(type: PuzzleType, options: PuzzleOptions) =
        "game/${type.name}?${GameArgs.NEW}=true&${GameArgs.DIFFICULTY}=${options.difficulty.name}" +
            "&${GameArgs.WIDTH}=${options.width}&${GameArgs.HEIGHT}=${options.height}"
}

@Composable
fun LogicalsApp(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val repository = remember { GameRepository.get(context) }
    val settings = remember { Settings(context) }
    NavHost(navController = nav, startDestination = Routes.MENU) {
        composable(Routes.MENU) {
            MainMenuScreen(
                onSelect = { nav.navigate(Routes.home(it)) },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                versionName = BuildConfig.VERSION_NAME,
            )
        }
        composable(
            Routes.HOME,
            arguments = listOf(navArgument(GameArgs.TYPE) { type = NavType.StringType }),
        ) { entry ->
            val type = PuzzleType.valueOf(requireNotNull(entry.arguments?.getString(GameArgs.TYPE)))
            PuzzleHomeScreen(
                type = type,
                repository = repository,
                settings = settings,
                onBack = { nav.popBackStack() },
                onContinue = { nav.navigate(Routes.continueGame(type)) },
                onNewGame = { options -> nav.navigate(Routes.newGame(type, options)) },
            )
        }
        composable(
            Routes.GAME,
            arguments = listOf(
                navArgument(GameArgs.TYPE) { type = NavType.StringType },
                navArgument(GameArgs.NEW) { type = NavType.BoolType; defaultValue = false },
                navArgument(GameArgs.DIFFICULTY) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(GameArgs.WIDTH) { type = NavType.IntType; defaultValue = 10 },
                navArgument(GameArgs.HEIGHT) { type = NavType.IntType; defaultValue = 10 },
            ),
        ) {
            val viewModel: GameViewModel = viewModel()
            // Zeit läuft nur, solange dieser Bildschirm sichtbar und aktiv ist.
            LifecycleResumeEffect(viewModel) {
                viewModel.controller.onScreenResumed()
                onPauseOrDispose { viewModel.controller.onScreenPaused() }
            }
            GameScreen(
                controller = viewModel.controller,
                onBack = { nav.popBackStack() },
                onToMenu = { nav.popBackStack(Routes.MENU, inclusive = false) },
            )
        }
    }
}
