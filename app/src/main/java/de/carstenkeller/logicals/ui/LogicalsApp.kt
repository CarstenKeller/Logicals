package de.carstenkeller.logicals.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.carstenkeller.logicals.core.PuzzleType
import de.carstenkeller.logicals.ui.game.GameScreen
import de.carstenkeller.logicals.ui.menu.MainMenuScreen
import de.carstenkeller.logicals.ui.menu.NewGameOptions
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

    fun newGame(type: PuzzleType, options: NewGameOptions) =
        "game/${type.name}?${GameArgs.NEW}=true&${GameArgs.DIFFICULTY}=${options.difficulty.name}" +
            "&${GameArgs.WIDTH}=${options.width}&${GameArgs.HEIGHT}=${options.height}"
}

@Composable
fun LogicalsApp() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.MENU) {
        composable(Routes.MENU) {
            MainMenuScreen(onSelect = { nav.navigate(Routes.home(it)) })
        }
        composable(
            Routes.HOME,
            arguments = listOf(navArgument(GameArgs.TYPE) { type = NavType.StringType }),
        ) { entry ->
            val type = PuzzleType.valueOf(requireNotNull(entry.arguments?.getString(GameArgs.TYPE)))
            PuzzleHomeScreen(
                type = type,
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
            GameScreen(
                onBack = { nav.popBackStack() },
                onToMenu = { nav.popBackStack(Routes.MENU, inclusive = false) },
            )
        }
    }
}
