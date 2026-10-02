package de.carstenkeller.logicals.ui.game

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.carstenkeller.logicals.core.GameState
import de.carstenkeller.logicals.core.KakuroGenerator
import de.carstenkeller.logicals.core.Puzzle
import de.carstenkeller.logicals.core.PuzzleType
import de.carstenkeller.logicals.core.SudokuDifficulty
import de.carstenkeller.logicals.core.SudokuGenerator
import de.carstenkeller.logicals.data.GameRepository
import de.carstenkeller.logicals.ui.GameArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GameUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val puzzle: Puzzle? = null,
    val entries: List<Int> = emptyList(),
    val notes: List<Int> = emptyList(),
    val conflicts: Set<Int> = emptySet(),
    val selected: Int = -1,
    val notesMode: Boolean = false,
    val solved: Boolean = false,
)

class GameViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repository = GameRepository.get(application)
    val type = PuzzleType.valueOf(requireNotNull(savedStateHandle.get<String>(GameArgs.TYPE)))

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _elapsed = MutableStateFlow(0L)
    /** Bisherige Rätseldauer in Millisekunden; läuft nur, solange der Bildschirm aktiv ist. */
    val elapsed: StateFlow<Long> = _elapsed.asStateFlow()

    private var accumulated = 0L
    private var runningSince: Long? = null
    private var tickJob: Job? = null
    private var screenActive = false

    init {
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        // Nach einem Prozessneustart nicht erneut generieren, sondern fortsetzen.
        val generateNew = savedStateHandle.get<Boolean>(GameArgs.NEW) == true &&
            savedStateHandle.get<Boolean>(KEY_STARTED) != true
        val game = if (generateNew) {
            val puzzle = withContext(Dispatchers.Default) { generate() }
            GameState.start(puzzle).also {
                repository.save(it)
                savedStateHandle[KEY_STARTED] = true
            }
        } else {
            repository.load(type)
        }
        if (game == null) {
            _state.value = GameUiState(loading = false, missing = true)
            return
        }
        accumulated = game.elapsedMillis
        _elapsed.value = accumulated
        val entries = game.entries.toIntArray()
        _state.value = GameUiState(
            loading = false,
            puzzle = game.puzzle,
            entries = game.entries,
            notes = game.notes,
            conflicts = game.puzzle.conflicts(entries),
            solved = game.puzzle.isSolved(entries),
        )
        if (screenActive) startTimer()
    }

    private fun generate(): Puzzle = when (type) {
        PuzzleType.SUDOKU -> {
            val difficulty = savedStateHandle.get<String>(GameArgs.DIFFICULTY)
                ?.let { name -> SudokuDifficulty.entries.firstOrNull { it.name == name } }
                ?: SudokuDifficulty.MEDIUM
            SudokuGenerator().generate(difficulty)
        }
        PuzzleType.KAKURO -> {
            val w = savedStateHandle.get<Int>(GameArgs.WIDTH) ?: 10
            val h = savedStateHandle.get<Int>(GameArgs.HEIGHT) ?: 10
            KakuroGenerator().generate(
                w.coerceIn(KakuroGenerator.MIN_SIZE, KakuroGenerator.MAX_SIZE),
                h.coerceIn(KakuroGenerator.MIN_SIZE, KakuroGenerator.MAX_SIZE),
            ).puzzle
        }
    }

    // ------------------------------------------------------------------ Timer

    /** Vom Bildschirm bei ON_RESUME aufgerufen. */
    fun onScreenResumed() {
        screenActive = true
        startTimer()
    }

    /** Vom Bildschirm bei ON_PAUSE aufgerufen (Bildschirm verlassen, App im Hintergrund). */
    fun onScreenPaused() {
        screenActive = false
        stopTimer()
        persist()
    }

    private fun startTimer() {
        val s = _state.value
        if (s.puzzle == null || s.solved || runningSince != null) return
        runningSince = SystemClock.elapsedRealtime()
        tickJob = viewModelScope.launch {
            while (isActive) {
                _elapsed.value = currentElapsed()
                delay(250)
            }
        }
    }

    private fun stopTimer() {
        runningSince?.let { accumulated += SystemClock.elapsedRealtime() - it }
        runningSince = null
        tickJob?.cancel()
        tickJob = null
        _elapsed.value = accumulated
    }

    private fun currentElapsed(): Long =
        accumulated + (runningSince?.let { SystemClock.elapsedRealtime() - it } ?: 0L)

    // ------------------------------------------------------------------ Eingaben

    fun select(index: Int) {
        val puzzle = _state.value.puzzle ?: return
        if (!puzzle.isEditable(index)) return
        _state.update { it.copy(selected = if (it.selected == index) -1 else index) }
    }

    fun toggleNotesMode() = _state.update { it.copy(notesMode = !it.notesMode) }

    fun input(digit: Int) {
        val s = _state.value
        val puzzle = s.puzzle ?: return
        if (s.solved || s.selected < 0 || !puzzle.isEditable(s.selected)) return
        val i = s.selected
        if (s.notesMode) {
            if (s.entries[i] != 0) return
            val notes = s.notes.toMutableList()
            notes[i] = notes[i] xor (1 shl digit)
            _state.value = s.copy(notes = notes)
            persist()
            return
        }
        val entries = s.entries.toMutableList()
        entries[i] = if (entries[i] == digit) 0 else digit
        applyEntries(s, entries, s.notes)
    }

    fun erase() {
        val s = _state.value
        val puzzle = s.puzzle ?: return
        if (s.solved || s.selected < 0 || !puzzle.isEditable(s.selected)) return
        val entries = s.entries.toMutableList().also { it[s.selected] = 0 }
        val notes = s.notes.toMutableList().also { it[s.selected] = 0 }
        applyEntries(s, entries, notes)
    }

    private fun applyEntries(s: GameUiState, entries: List<Int>, notes: List<Int>) {
        val puzzle = s.puzzle ?: return
        val array = entries.toIntArray()
        val solved = puzzle.isSolved(array)
        _state.value = s.copy(
            entries = entries,
            notes = notes,
            conflicts = puzzle.conflicts(array),
            solved = solved,
            selected = if (solved) -1 else s.selected,
        )
        if (solved) {
            stopTimer()
            repository.delete(type)
        } else {
            persist()
        }
    }

    private fun persist() {
        val s = _state.value
        val puzzle = s.puzzle ?: return
        if (s.solved) return
        repository.save(GameState(puzzle, s.entries, s.notes, currentElapsed()))
    }

    private companion object {
        const val KEY_STARTED = "started"
    }
}
