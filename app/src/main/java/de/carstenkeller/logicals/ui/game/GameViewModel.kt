package de.carstenkeller.logicals.ui.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.carstenkeller.logicals.core.Difficulty
import de.carstenkeller.logicals.core.PuzzleOptions
import de.carstenkeller.logicals.core.PuzzleType
import de.carstenkeller.logicals.data.GameRepository
import de.carstenkeller.logicals.data.Settings
import de.carstenkeller.logicals.ui.GameArgs
import kotlinx.coroutines.Dispatchers

/**
 * Hält den gemeinsamen [GameController] über Konfigurationswechsel hinweg. Die Spiellogik selbst
 * liegt in shared-ui und wird auch von der Web-App genutzt.
 */
class GameViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    val type = PuzzleType.valueOf(requireNotNull(savedStateHandle.get<String>(GameArgs.TYPE)))

    val controller = GameController(
        type = type,
        // Nach einem Prozessneustart nicht erneut generieren, sondern fortsetzen.
        newGame = if (savedStateHandle.get<Boolean>(GameArgs.NEW) == true &&
            savedStateHandle.get<Boolean>(KEY_STARTED) != true
        ) {
            argOptions()
        } else {
            null
        },
        scope = viewModelScope,
        repository = GameRepository.get(application),
        settings = Settings(application),
        computeContext = Dispatchers.Default,
        onNewGameSaved = { savedStateHandle[KEY_STARTED] = true },
    )

    private fun argOptions(): PuzzleOptions {
        val difficulty = savedStateHandle.get<String>(GameArgs.DIFFICULTY)
            ?.let { name -> Difficulty.entries.firstOrNull { it.name == name } }
            ?: Difficulty.MEDIUM
        return PuzzleOptions(
            difficulty = difficulty,
            width = savedStateHandle.get<Int>(GameArgs.WIDTH) ?: type.defaultWidth,
            height = savedStateHandle.get<Int>(GameArgs.HEIGHT) ?: type.defaultHeight,
        )
    }

    private companion object {
        const val KEY_STARTED = "started"
    }
}
