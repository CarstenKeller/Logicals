package de.carstenkeller.logicals.ui.game

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.carstenkeller.logicals.core.CatsweeperPuzzle
import de.carstenkeller.logicals.core.Difficulty
import de.carstenkeller.logicals.core.GameState
import de.carstenkeller.logicals.core.Hint
import de.carstenkeller.logicals.core.HintAction
import de.carstenkeller.logicals.core.HintFinder
import de.carstenkeller.logicals.core.Puzzle
import de.carstenkeller.logicals.core.PuzzleFactory
import de.carstenkeller.logicals.core.PuzzleOptions
import de.carstenkeller.logicals.core.PuzzleType
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
import kotlin.random.Random

data class GameUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val puzzle: Puzzle? = null,
    val entries: List<Int> = emptyList(),
    val notes: List<Int> = emptyList(),
    val conflicts: Set<Int> = emptySet(),
    val selected: Int = -1,
    val notesMode: Boolean = false,
    /** Catsweeper: Tippen markiert statt aufzudecken. */
    val markMode: Boolean = false,
    val solved: Boolean = false,
    val lost: Boolean = false,
    /** Zählt neu gestartete Rätsel, damit Dialoge pro Rätsel neu erscheinen. */
    val round: Int = 0,
    /** Aktuell angezeigter Hinweis. */
    val hint: Hint? = null,
    val hintLoading: Boolean = false,
    val hintsUsed: Int = 0,
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
            val puzzle = withContext(Dispatchers.Default) { PuzzleFactory.generate(type, argOptions()) }
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
        show(game, round = 0)
    }

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

    private fun show(game: GameState, round: Int) {
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
            lost = game.puzzle.isLost(entries),
            round = round,
            hintsUsed = game.hintsUsed,
        )
        if (screenActive) startTimer()
    }

    /** Startet ein neues Rätsel mit denselben Einstellungen wie das aktuelle. */
    fun playAgain() {
        val current = _state.value
        val puzzle = current.puzzle ?: return
        stopTimer()
        _state.value = GameUiState(loading = true, round = current.round)
        viewModelScope.launch {
            val next = withContext(Dispatchers.Default) { PuzzleFactory.generate(type, puzzle.options) }
            val game = GameState.start(next)
            repository.save(game)
            savedStateHandle[KEY_STARTED] = true
            show(game, round = current.round + 1)
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
        if (s.puzzle == null || s.solved || s.lost || runningSince != null) return
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

    // ------------------------------------------------------------------ Zahlenrätsel

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

    // ------------------------------------------------------------------ Catsweeper

    fun toggleMarkMode() = _state.update { it.copy(markMode = !it.markMode) }

    /** Tippen: aufdecken (bzw. markieren im Markiermodus). */
    fun catTap(index: Int) {
        if (_state.value.markMode) catMark(index) else catReveal(index)
    }

    private fun catReveal(index: Int) {
        val s = _state.value
        var puzzle = s.puzzle as? CatsweeperPuzzle ?: return
        if (s.solved || s.lost) return
        val current = s.entries.toIntArray()
        // Der erste Zug trifft nie einen Hund.
        if (current.none { it == CatsweeperPuzzle.REVEALED }) {
            puzzle = puzzle.withSafeStart(index, Random.Default)
        }
        val next = puzzle.reveal(current, index)
        applyEntries(s.copy(puzzle = puzzle), next.toList(), s.notes)
    }

    /** Lang drücken: Knochen-Markierung setzen oder entfernen. */
    fun catMark(index: Int) {
        val s = _state.value
        val puzzle = s.puzzle as? CatsweeperPuzzle ?: return
        if (s.solved || s.lost) return
        val next = puzzle.toggleMark(s.entries.toIntArray(), index)
        applyEntries(s, next.toList(), s.notes)
    }

    // ------------------------------------------------------------------ Hinweise

    fun requestHint() {
        val s = _state.value
        val puzzle = s.puzzle ?: return
        if (s.solved || s.lost || s.hintLoading) return
        _state.value = s.copy(hintLoading = true)
        val entries = s.entries.toIntArray()
        viewModelScope.launch {
            val hint = withContext(Dispatchers.Default) { HintFinder.find(puzzle, entries) }
            _state.update {
                // Inzwischen geänderter Stand: Hinweis verwerfen.
                if (it.entries != s.entries || it.puzzle != puzzle) return@update it.copy(hintLoading = false)
                it.copy(
                    hint = hint,
                    hintLoading = false,
                    hintsUsed = if (hint != null) it.hintsUsed + 1 else it.hintsUsed,
                    selected = if (hint != null && puzzle.isEditable(hint.cell) && puzzle !is CatsweeperPuzzle) {
                        hint.cell
                    } else {
                        it.selected
                    },
                )
            }
            persist()
        }
    }

    fun dismissHint() = _state.update { it.copy(hint = null) }

    /** Führt die vorgeschlagene Aktion des Hinweises aus. */
    fun applyHint() {
        val s = _state.value
        val hint = s.hint ?: return
        val puzzle = s.puzzle ?: return
        _state.value = s.copy(hint = null)
        when (hint.action) {
            HintAction.PLACE -> {
                val entries = s.entries.toMutableList().also { it[hint.cell] = hint.value }
                val notes = s.notes.toMutableList().also { it[hint.cell] = 0 }
                applyEntries(_state.value, entries, notes)
            }
            HintAction.CLEAR -> {
                val entries = s.entries.toMutableList().also { it[hint.cell] = 0 }
                applyEntries(_state.value, entries, s.notes)
            }
            HintAction.REVEAL -> if (puzzle is CatsweeperPuzzle) catReveal(hint.cell)
            HintAction.MARK, HintAction.UNMARK -> if (puzzle is CatsweeperPuzzle) catMark(hint.cell)
        }
    }

    // ------------------------------------------------------------------ gemeinsam

    private fun applyEntries(s: GameUiState, entries: List<Int>, notes: List<Int>) {
        val puzzle = s.puzzle ?: return
        val array = entries.toIntArray()
        val solved = puzzle.isSolved(array)
        val lost = !solved && puzzle.isLost(array)
        _state.value = s.copy(
            hint = null,
            entries = entries,
            notes = notes,
            conflicts = puzzle.conflicts(array),
            solved = solved,
            lost = lost,
            selected = if (solved || lost) -1 else s.selected,
        )
        if (solved || lost) {
            stopTimer()
            repository.delete(type)
        } else {
            persist()
        }
    }

    private fun persist() {
        val s = _state.value
        val puzzle = s.puzzle ?: return
        if (s.solved || s.lost) return
        repository.save(GameState(puzzle, s.entries, s.notes, currentElapsed(), s.hintsUsed))
    }

    private companion object {
        const val KEY_STARTED = "started"
    }
}
