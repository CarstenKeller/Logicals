package de.carstenkeller.logicals.web.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.carstenkeller.logicals.core.Candidates
import de.carstenkeller.logicals.core.Hint
import de.carstenkeller.logicals.core.HintAction
import de.carstenkeller.logicals.core.PuzzleOptions
import de.carstenkeller.logicals.core.PuzzleType
import de.carstenkeller.logicals.core.SudokuPuzzle
import de.carstenkeller.logicals.web.Browser
import de.carstenkeller.logicals.web.ui.ArrowBackIcon
import de.carstenkeller.logicals.web.ui.Texte
import de.carstenkeller.logicals.web.ui.Texte.title
import de.carstenkeller.logicals.web.ui.formatDuration
import de.carstenkeller.logicals.web.ui.tabularNumbers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    type: PuzzleType,
    newGame: PuzzleOptions?,
    onBack: () -> Unit,
    onToMenu: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(type, newGame) { GameController(type, newGame, scope) }
    val state by controller.state.collectAsState()
    val elapsed by controller.elapsed.collectAsState()

    // Zeit läuft nur, solange dieser Bildschirm offen und die Seite sichtbar ist.
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(type.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(ArrowBackIcon, contentDescription = Texte.BACK)
                    }
                },
                actions = {
                    val active = state.puzzle != null && !state.solved && !state.lost
                    if (state.puzzle != null) {
                        FilterChip(
                            selected = state.easyMode,
                            onClick = controller::toggleEasyMode,
                            label = { Text(Texte.EASY_MODE) },
                        )
                    }
                    TextButton(onClick = controller::requestHint, enabled = active && !state.hintLoading) {
                        Text("💡 " + Texte.HINT)
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val puzzle = state.puzzle
            when {
                state.loading -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(Texte.GENERATING)
                }
                state.missing || puzzle == null -> Text(
                    Texte.LOAD_FAILED,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Column(Modifier.fillMaxSize()) {
                    Text(
                        text = Texte.TIME_LABEL + "  " + formatDuration(elapsed),
                        style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                    key(state.round) {
                        ZoomPanBox(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) { zoom, viewport ->
                            // Rätsel, die sich an den Platz anpassen, bekommen die gezoomte Fläche.
                            val fit = Modifier.size(viewport * zoom)
                            when (puzzle) {
                                is SudokuPuzzle -> SudokuBoard(puzzle, state, controller::select, fit)
                                // Weitere Rätsel folgen nach dem Prototyp (Phase 4).
                                else -> Text(Texte.NOT_YET_AVAILABLE, Modifier.padding(16.dp))
                            }
                        }
                    }
                    state.hint?.let { hint ->
                        HintCard(hint, onApply = controller::applyHint, onClose = controller::dismissHint)
                    }
                    // Einfach-Modus: nur die für das gewählte Feld möglichen Ziffern freigeben.
                    val allowed = remember(state.easyMode, state.selected, state.entries, puzzle) {
                        val sel = state.selected
                        if (state.easyMode && sel >= 0 && state.entries[sel] == 0) {
                            Candidates.of(puzzle, state.entries.toIntArray(), sel)
                        } else {
                            -1
                        }
                    }
                    NumberPad(
                        maxDigit = puzzle.maxDigit,
                        allowed = allowed,
                        enabled = !state.solved && state.selected >= 0,
                        notesMode = state.notesMode,
                        onDigit = controller::input,
                        onErase = controller::erase,
                        onToggleNotes = controller::toggleNotesMode,
                    )
                }
            }
        }
    }

    // Pro Rätselrunde einmal anzeigen; "Nochmal" startet eine neue Runde.
    var dialogDismissedRound by rememberSaveable { mutableIntStateOf(-1) }
    if ((state.solved || state.lost) && dialogDismissedRound != state.round) {
        AlertDialog(
            onDismissRequest = { dialogDismissedRound = state.round },
            title = { Text(if (state.lost) Texte.LOST_TITLE else Texte.SOLVED_TITLE) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.lost) Texte.LOST_TEXT else Texte.solvedText(formatDuration(elapsed)))
                    if (state.hintsUsed > 0) Text(Texte.hintsUsed(state.hintsUsed))
                }
            },
            confirmButton = {
                TextButton(onClick = controller::playAgain) { Text(Texte.PLAY_AGAIN) }
            },
            dismissButton = {
                TextButton(onClick = onToMenu) { Text(Texte.TO_MENU) }
            },
        )
    }
}

/** Erklärung zum Hinweis mit Aktion; der Text ist scrollbar, damit das Spielfeld sichtbar bleibt. */
@Composable
private fun HintCard(hint: Hint, onApply: () -> Unit, onClose: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("💡 " + hint.title, style = MaterialTheme.typography.titleMedium)
            Column(
                Modifier
                    .heightIn(max = 150.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (line in hint.steps) Text(line, style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text(Texte.HINT_CLOSE) }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onApply) {
                    Text(
                        when (hint.action) {
                            HintAction.PLACE -> Texte.hintApplyPlace(hint.value)
                            HintAction.CLEAR -> Texte.HINT_APPLY_CLEAR
                            HintAction.REVEAL -> Texte.HINT_APPLY_REVEAL
                            HintAction.MARK -> Texte.HINT_APPLY_MARK
                            HintAction.UNMARK -> Texte.HINT_APPLY_UNMARK
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberPad(
    maxDigit: Int,
    /** Bitmaske erlaubter Ziffern, -1 = alle. */
    allowed: Int,
    enabled: Boolean,
    notesMode: Boolean,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    onToggleNotes: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (digit in 1..maxDigit) {
                FilledTonalButton(
                    onClick = { onDigit(digit) },
                    enabled = enabled && allowed and (1 shl digit) != 0,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    Text(digit.toString(), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = notesMode,
                onClick = onToggleNotes,
                label = { Text(Texte.NOTES) },
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onErase, enabled = enabled) {
                Text(Texte.ERASE)
            }
        }
    }
}
