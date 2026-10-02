package de.carstenkeller.logicals.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import de.carstenkeller.logicals.core.KakuroCombos
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
                    when (puzzle) {
                        is SudokuPuzzle -> SudokuBoard(puzzle, state, viewModel::select, boardModifier)
                        is KakuroPuzzle -> {
                            KakuroBoard(puzzle, state, viewModel::select, boardModifier)
                            KakuroHint(puzzle, state)
                        }
                        null -> Unit
                    }
                    NumberPad(
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

    var solvedDialogDismissed by rememberSaveable { mutableStateOf(false) }
    if (state.solved && !solvedDialogDismissed) {
        AlertDialog(
            onDismissRequest = { solvedDialogDismissed = true },
            title = { Text(stringResource(R.string.solved_title)) },
            text = { Text(stringResource(R.string.solved_text, formatDuration(elapsed))) },
            confirmButton = {
                TextButton(onClick = onToMenu) { Text(stringResource(R.string.to_menu)) }
            },
            dismissButton = {
                TextButton(onClick = { solvedDialogDismissed = true }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

/** Zeigt für die gewählte Zelle die noch möglichen Ziffernkombinationen ihrer Folgen. */
@Composable
private fun KakuroHint(puzzle: KakuroPuzzle, state: GameUiState) {
    val selected = state.selected
    val text = if (selected >= 0 && puzzle.isEditable(selected)) {
        val geometry = puzzle.geometry
        listOf(geometry.acrossRunOf[selected] to "→", geometry.downRunOf[selected] to "↓")
            .joinToString("    ") { (runId, arrow) ->
                val run = geometry.runs[runId]
                val sum = puzzle.sumOf(run)
                var used = 0
                for (c in run.cells) if (state.entries[c] != 0) used = used or (1 shl state.entries[c])
                val combos = KakuroCombos.of(run.cells.size, sum)
                    .filter { it and used == used }
                    .joinToString(" ") { KakuroCombos.digits(it).joinToString("") }
                "$arrow $sum/${run.cells.size}: ${combos.ifEmpty { "–" }}"
            }
    } else {
        ""
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(36.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberPad(
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
            for (digit in 1..9) {
                FilledTonalButton(
                    onClick = { onDigit(digit) },
                    enabled = enabled,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.8f),
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
