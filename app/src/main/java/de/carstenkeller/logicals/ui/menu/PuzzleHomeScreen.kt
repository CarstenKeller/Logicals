package de.carstenkeller.logicals.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.carstenkeller.logicals.R
import de.carstenkeller.logicals.core.GameState
import de.carstenkeller.logicals.core.KakuroGenerator
import de.carstenkeller.logicals.core.KakuroPuzzle
import de.carstenkeller.logicals.core.PuzzleType
import de.carstenkeller.logicals.core.SudokuDifficulty
import de.carstenkeller.logicals.core.SudokuPuzzle
import de.carstenkeller.logicals.data.GameRepository
import de.carstenkeller.logicals.data.Settings
import de.carstenkeller.logicals.ui.formatDuration
import de.carstenkeller.logicals.ui.titleRes
import kotlin.math.roundToInt

data class NewGameOptions(
    val difficulty: SudokuDifficulty = SudokuDifficulty.MEDIUM,
    val width: Int = 10,
    val height: Int = 10,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleHomeScreen(
    type: PuzzleType,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onNewGame: (NewGameOptions) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { GameRepository.get(context) }
    val settings = remember { Settings(context) }
    val saved by remember(type) { repository.observe(type) }.collectAsState(initial = null)

    var difficulty by rememberSaveable { mutableStateOf(settings.sudokuDifficulty) }
    var width by rememberSaveable { mutableIntStateOf(settings.kakuroWidth) }
    var height by rememberSaveable { mutableIntStateOf(settings.kakuroHeight) }
    var confirmDiscard by remember { mutableStateOf(false) }

    fun startNew() {
        settings.sudokuDifficulty = difficulty
        settings.kakuroWidth = width
        settings.kakuroHeight = height
        onNewGame(NewGameOptions(difficulty, width, height))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(type.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ContinueCard(saved, onContinue)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.new_game), style = MaterialTheme.typography.titleLarge)
                    when (type) {
                        PuzzleType.SUDOKU -> DifficultySelector(difficulty) { difficulty = it }
                        PuzzleType.KAKURO -> {
                            SizeSlider(stringResource(R.string.kakuro_columns, width), width) { width = it }
                            SizeSlider(stringResource(R.string.kakuro_rows, height), height) { height = it }
                            Text(
                                stringResource(R.string.kakuro_size_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(
                        onClick = { if (saved != null) confirmDiscard = true else startNew() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.generate))
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    startNew()
                }) { Text(stringResource(R.string.discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ContinueCard(saved: GameState?, onContinue: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.continue_game), style = MaterialTheme.typography.titleLarge)
            if (saved == null) {
                Text(stringResource(R.string.no_saved_game), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(describe(saved), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.continue_details, formatDuration(saved.elapsedMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FilledTonalButton(
                onClick = onContinue,
                enabled = saved != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.continue_game)) }
        }
    }
}

@Composable
private fun describe(state: GameState): String = when (val p = state.puzzle) {
    is SudokuPuzzle -> stringResource(p.difficulty.labelRes)
    is KakuroPuzzle -> "${p.width} × ${p.height}"
}

val SudokuDifficulty.labelRes: Int
    get() = when (this) {
        SudokuDifficulty.EASY -> R.string.difficulty_easy
        SudokuDifficulty.MEDIUM -> R.string.difficulty_medium
        SudokuDifficulty.HARD -> R.string.difficulty_hard
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DifficultySelector(selected: SudokuDifficulty, onSelect: (SudokuDifficulty) -> Unit) {
    Text(stringResource(R.string.difficulty), style = MaterialTheme.typography.titleSmall)
    val options = SudokuDifficulty.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, difficulty ->
            SegmentedButton(
                selected = difficulty == selected,
                onClick = { onSelect(difficulty) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(difficulty.labelRes)) }
        }
    }
}

@Composable
private fun SizeSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = KakuroGenerator.MIN_SIZE.toFloat()..KakuroGenerator.MAX_SIZE.toFloat(),
            steps = KakuroGenerator.MAX_SIZE - KakuroGenerator.MIN_SIZE - 1,
        )
    }
}
