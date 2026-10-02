package de.carstenkeller.logicals.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.carstenkeller.logicals.R
import de.carstenkeller.logicals.core.CatsweeperPuzzle
import de.carstenkeller.logicals.core.FutoshikiPuzzle
import de.carstenkeller.logicals.core.Hint
import de.carstenkeller.logicals.core.HintAction
import de.carstenkeller.logicals.core.KakuroCombos
import de.carstenkeller.logicals.core.KenKenPuzzle
import de.carstenkeller.logicals.core.SkyscraperPuzzle
import de.carstenkeller.logicals.core.KakuroPuzzle
import de.carstenkeller.logicals.core.SudokuPuzzle
import de.carstenkeller.logicals.ui.formatDuration
import de.carstenkeller.logicals.ui.titleRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    onBack: () -> Unit,
    onToMenu: () -> Unit,
    viewModel: GameViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()

    // Zeit läuft nur, solange dieser Bildschirm sichtbar und aktiv ist.
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        onPauseOrDispose { viewModel.onScreenPaused() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(viewModel.type.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val canHint = state.puzzle != null && !state.solved && !state.lost && !state.hintLoading
                    TextButton(onClick = viewModel::requestHint, enabled = canHint) {
                        Text("💡 " + stringResource(R.string.hint))
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
                    Text(stringResource(R.string.generating))
                }
                state.missing || puzzle == null -> Text(
                    stringResource(R.string.load_failed),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Column(Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.time_label) + "  " + formatDuration(elapsed),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                    if (puzzle is KakuroPuzzle && !puzzle.unique) {
                        Text(
                            stringResource(R.string.kakuro_not_unique),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    val boardModifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                    // Gitter mit fester Zellgröße dürfen auch kleiner werden (Überblick).
                    val fixedCells = puzzle is KakuroPuzzle || puzzle is CatsweeperPuzzle
                    key(state.round) {
                        ZoomPanBox(
                            modifier = boardModifier,
                            minZoom = if (fixedCells) 0.4f else 1f,
                            maxZoom = if (fixedCells) 3f else 4f,
                        ) { zoom, viewport ->
                            // Rätsel, die sich an den Platz anpassen, bekommen die gezoomte Fläche.
                            val fit = Modifier.size(viewport * zoom)
                            when (puzzle) {
                                is SudokuPuzzle -> SudokuBoard(puzzle, state, viewModel::select, fit)
                                is KakuroPuzzle -> KakuroBoard(puzzle, state, viewModel::select, zoom = zoom)
                                is FutoshikiPuzzle -> FutoshikiBoard(puzzle, state, viewModel::select, fit)
                                is KenKenPuzzle -> KenKenBoard(puzzle, state, viewModel::select, fit)
                                is SkyscraperPuzzle -> SkyscraperBoard(puzzle, state, viewModel::select, fit)
                                is CatsweeperPuzzle -> CatsweeperBoard(
                                    puzzle, state, viewModel::catTap, viewModel::catMark, zoom = zoom,
                                )
                                null -> Unit
                            }
                        }
                    }
                    if (puzzle is KakuroPuzzle && state.hint == null) KakuroHint(puzzle, state)
                    state.hint?.let { hint ->
                        HintCard(hint, onApply = viewModel::applyHint, onClose = viewModel::dismissHint)
                    }
                    if (puzzle is CatsweeperPuzzle) {
                        CatsweeperBar(
                            remaining = puzzle.dogs.size - state.entries.count { it == CatsweeperPuzzle.MARKED },
                            markMode = state.markMode,
                            enabled = !state.solved && !state.lost,
                            onToggleMark = viewModel::toggleMarkMode,
                        )
                    } else {
                        NumberPad(
                            maxDigit = puzzle.maxDigit,
                            enabled = !state.solved && state.selected >= 0,
                            notesMode = state.notesMode,
                            onDigit = viewModel::input,
                            onErase = viewModel::erase,
                            onToggleNotes = viewModel::toggleNotesMode,
                        )
                    }
                }
            }
        }
    }

    // Pro Rätselrunde einmal anzeigen; "Nochmal" startet eine neue Runde.
    var dialogDismissedRound by rememberSaveable { mutableIntStateOf(-1) }
    if ((state.solved || state.lost) && dialogDismissedRound != state.round) {
        AlertDialog(
            onDismissRequest = { dialogDismissedRound = state.round },
            title = { Text(stringResource(if (state.lost) R.string.lost_title else R.string.solved_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state.lost) {
                            stringResource(R.string.lost_text)
                        } else {
                            stringResource(R.string.solved_text, formatDuration(elapsed))
                        },
                    )
                    if (state.hintsUsed > 0) Text(stringResource(R.string.hints_used, state.hintsUsed))
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::playAgain) { Text(stringResource(R.string.play_again)) }
            },
            dismissButton = {
                TextButton(onClick = onToMenu) { Text(stringResource(R.string.to_menu)) }
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
                TextButton(onClick = onClose) { Text(stringResource(R.string.hint_close)) }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onApply) {
                    Text(
                        when (hint.action) {
                            HintAction.PLACE -> stringResource(R.string.hint_apply_place, hint.value)
                            HintAction.CLEAR -> stringResource(R.string.hint_apply_clear)
                            HintAction.REVEAL -> stringResource(R.string.hint_apply_reveal)
                            HintAction.MARK -> stringResource(R.string.hint_apply_mark)
                            HintAction.UNMARK -> stringResource(R.string.hint_apply_unmark)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatsweeperBar(remaining: Int, markMode: Boolean, enabled: Boolean, onToggleMark: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.catsweeper_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = markMode,
                onClick = onToggleMark,
                enabled = enabled,
                label = { Text(stringResource(R.string.catsweeper_mark)) },
            )
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.catsweeper_remaining, remaining), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Zeigt für die gewählte Zelle die noch möglichen Ziffernkombinationen ihrer Folgen,
 * je Richtung eine eigene Zeile mit Umbruch, damit lange Listen vollständig lesbar sind.
 */
@Composable
private fun KakuroHint(puzzle: KakuroPuzzle, state: GameUiState) {
    val selected = state.selected
    val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (selected < 0 || !puzzle.isEditable(selected)) return@Column
        val geometry = puzzle.geometry
        for ((runId, arrow) in listOf(geometry.acrossRunOf[selected] to "→", geometry.downRunOf[selected] to "↓")) {
            val run = geometry.runs[runId]
            val sum = puzzle.sumOf(run)
            var used = 0
            for (c in run.cells) if (state.entries[c] != 0) used = used or (1 shl state.entries[c])
            val combos = KakuroCombos.of(run.cells.size, sum)
                .filter { it and used == used }
                .joinToString(" ") { KakuroCombos.digits(it).joinToString("") }
            Row {
                Text("$arrow ${sum}/${run.cells.size}", style = style, modifier = Modifier.width(64.dp))
                Text(combos.ifEmpty { "–" }, style = style, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberPad(
    maxDigit: Int,
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
                    enabled = enabled,
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
                label = { Text(stringResource(R.string.notes)) },
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onErase, enabled = enabled) {
                Text(stringResource(R.string.erase))
            }
        }
    }
}
