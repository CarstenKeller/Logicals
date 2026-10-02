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
import de.carstenkeller.logicals.core.Difficulty
import de.carstenkeller.logicals.core.GameState
import de.carstenkeller.logicals.core.PuzzleOptions
import de.carstenkeller.logicals.core.PuzzleType
import de.carstenkeller.logicals.data.GameRepository
import de.carstenkeller.logicals.data.Settings
import de.carstenkeller.logicals.ui.formatDuration
import de.carstenkeller.logicals.ui.labelRes
import de.carstenkeller.logicals.ui.titleRes
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleHomeScreen(
    type: PuzzleType,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onNewGame: (PuzzleOptions) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { GameRepository.get(context) }
    val settings = remember { Settings(context) }
    val saved by remember(type) { repository.observe(type) }.collectAsState(initial = null)

    val initial = remember(type) { settings.options(type) }
    var difficulty by rememberSaveable { mutableStateOf(initial.difficulty) }
    var width by rememberSaveable { mutableIntStateOf(initial.width) }
    var height by rememberSaveable { mutableIntStateOf(initial.height) }
    var confirmDiscard by remember { mutableStateOf(false) }

    fun startNew() {
        val options = PuzzleOptions(difficulty, width, if (type.rectangular) height else width)
        settings.save(type, options)
        onNewGame(options)
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
                    if (type.hasDifficulty) DifficultySelector(difficulty) { difficulty = it }
                    val range = type.sizeRange
                    if (range != null) {
                        if (type.rectangular) {
                            SizeSlider(stringResource(R.string.columns_label, width), width, range) { width = it }
                            SizeSlider(stringResource(R.string.rows_label, height), height, range) { height = it }
                        } else {
                            SizeSlider(stringResource(R.string.size_label, width), width, range) { width = it }
                        }
                    }
                    if (type == PuzzleType.KAKURO) {
                        Text(
                            stringResource(R.string.kakuro_size_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
private fun describe(state: GameState): String {
    val p = state.puzzle
    val size = "${p.width} × ${p.height}"
    return if (p.kind.hasDifficulty) "$size · ${stringResource(p.options.difficulty.labelRes)}" else size
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DifficultySelector(selected: Difficulty, onSelect: (Difficulty) -> Unit) {
    Text(stringResource(R.string.difficulty), style = MaterialTheme.typography.titleSmall)
    val options = Difficulty.entries
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
private fun SizeSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}
