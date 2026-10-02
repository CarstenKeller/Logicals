package de.carstenkeller.logicals.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import de.carstenkeller.logicals.core.CatsweeperPuzzle
import de.carstenkeller.logicals.core.FutoshikiPuzzle
import de.carstenkeller.logicals.core.KenKenPuzzle
import de.carstenkeller.logicals.core.SkyscraperPuzzle

// ---------------------------------------------------------------------- Hilfen

@Composable
private fun numberCellColors(
    editable: Boolean,
    index: Int,
    state: GameUiState,
    related: Boolean,
): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    val value = state.entries[index]
    val selectedValue = if (state.selected >= 0) state.entries[state.selected] else 0
    val background = hintBackground(state, index) ?: when {
        index == state.selected -> colors.primaryContainer
        value != 0 && value == selectedValue -> colors.secondaryContainer
        related -> colors.surfaceVariant
        else -> colors.surface
    }
    val text = when {
        index in state.conflicts -> colors.error
        !editable -> colors.onSurface
        else -> colors.primary
    }
    return background to text
}

private fun sameLine(a: Int, b: Int, n: Int) = a >= 0 && (a / n == b / n || a % n == b % n)

// ---------------------------------------------------------------------- Futoshiki

private const val FUTOSHIKI_GAP = 0.45f

@Composable
fun FutoshikiBoard(
    puzzle: FutoshikiPuzzle,
    state: GameUiState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = puzzle.size
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val available = min(maxWidth, maxHeight) - 8.dp
        val cell = available / (n + FUTOSHIKI_GAP * (n - 1))
        val gap = cell * FUTOSHIKI_GAP
        val symbolSize = with(LocalDensity.current) { (gap * 0.9f).toSp() }
        val symbolColor = MaterialTheme.colorScheme.onSurface
        Column {
            for (r in 0 until n) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (c in 0 until n) {
                        val i = r * n + c
                        val (background, textColor) =
                            numberCellColors(puzzle.isEditable(i), i, state, sameLine(state.selected, i, n))
                        NumberCell(
                            size = cell,
                            value = state.entries[i],
                            notes = state.notes[i],
                            background = background,
                            textColor = textColor,
                            bold = !puzzle.isEditable(i),
                            border = MaterialTheme.colorScheme.outline,
                            onClick = { onSelect(i) },
                        )
                        if (c < n - 1) {
                            Box(Modifier.size(gap, cell), contentAlignment = Alignment.Center) {
                                val symbol = when (puzzle.right[i]) {
                                    FutoshikiPuzzle.LESS -> "<"
                                    FutoshikiPuzzle.GREATER -> ">"
                                    else -> ""
                                }
                                Text(symbol, fontSize = symbolSize, color = symbolColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (r < n - 1) {
                    Row {
                        for (c in 0 until n) {
                            val i = r * n + c
                            Box(Modifier.size(cell, gap), contentAlignment = Alignment.Center) {
                                // Spitze zeigt zur kleineren Zahl: ∧ = oben kleiner.
                                val symbol = when (puzzle.down[i]) {
                                    FutoshikiPuzzle.LESS -> "∧"
                                    FutoshikiPuzzle.GREATER -> "∨"
                                    else -> ""
                                }
                                Text(symbol, fontSize = symbolSize, color = symbolColor, fontWeight = FontWeight.Bold)
                            }
                            if (c < n - 1) Spacer(Modifier.size(gap))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- KenKen

@Composable
fun KenKenBoard(
    puzzle: KenKenPuzzle,
    state: GameUiState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = puzzle.size
    val cageOf = puzzle.cageOf
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val boardSize = min(maxWidth, maxHeight) - 8.dp
        val cell = boardSize / n
        val labelSize = with(LocalDensity.current) { (cell * 0.2f).toSp() }
        val lineColor = MaterialTheme.colorScheme.onSurface

        Box(Modifier.size(boardSize)) {
            Column {
                for (r in 0 until n) {
                    Row {
                        for (c in 0 until n) {
                            val i = r * n + c
                            val (background, textColor) =
                                numberCellColors(true, i, state, sameLine(state.selected, i, n))
                            NumberCell(
                                size = cell,
                                value = state.entries[i],
                                notes = state.notes[i],
                                background = background,
                                textColor = textColor,
                                bold = false,
                                onClick = { onSelect(i) },
                            )
                        }
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val step = size.width / n
                val thin = 0.75.dp.toPx()
                val thick = 2.5.dp.toPx()
                val thinColor = lineColor.copy(alpha = 0.35f)
                for (r in 0 until n) for (c in 0 until n) {
                    val i = r * n + c
                    if (c < n - 1) {
                        val cage = cageOf[i] != cageOf[i + 1]
                        drawLine(
                            if (cage) lineColor else thinColor,
                            Offset((c + 1) * step, r * step),
                            Offset((c + 1) * step, (r + 1) * step),
                            if (cage) thick else thin,
                        )
                    }
                    if (r < n - 1) {
                        val cage = cageOf[i] != cageOf[i + n]
                        drawLine(
                            if (cage) lineColor else thinColor,
                            Offset(c * step, (r + 1) * step),
                            Offset((c + 1) * step, (r + 1) * step),
                            if (cage) thick else thin,
                        )
                    }
                }
                drawRect(lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(thick))
            }
            for (cage in puzzle.cages) {
                val first = cage.cells.min()
                Text(
                    cage.label,
                    fontSize = labelSize,
                    lineHeight = labelSize,
                    fontWeight = FontWeight.SemiBold,
                    color = if (cage.cells.any { it in state.conflicts }) MaterialTheme.colorScheme.error else lineColor,
                    modifier = Modifier.offset(x = cell * (first % n) + 3.dp, y = cell * (first / n) + 2.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- Skyscraper

@Composable
fun SkyscraperBoard(
    puzzle: SkyscraperPuzzle,
    state: GameUiState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = puzzle.size
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Gitter plus je eine Randzeile/-spalte für die Hinweise.
        val cell = (min(maxWidth, maxHeight) - 8.dp) / (n + 2)
        val clueSize = with(LocalDensity.current) { (cell * 0.45f).toSp() }
        val clueColor = MaterialTheme.colorScheme.onSurfaceVariant

        @Composable
        fun Clue(value: Int) {
            Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                if (value != 0) Text(value.toString(), fontSize = clueSize, fontWeight = FontWeight.Bold, color = clueColor)
            }
        }

        Column {
            Row {
                Spacer(Modifier.size(cell))
                for (c in 0 until n) Clue(puzzle.top[c])
                Spacer(Modifier.size(cell))
            }
            for (r in 0 until n) {
                Row {
                    Clue(puzzle.left[r])
                    for (c in 0 until n) {
                        val i = r * n + c
                        val (background, textColor) =
                            numberCellColors(puzzle.isEditable(i), i, state, sameLine(state.selected, i, n))
                        NumberCell(
                            size = cell,
                            value = state.entries[i],
                            notes = state.notes[i],
                            background = background,
                            textColor = textColor,
                            bold = !puzzle.isEditable(i),
                            border = MaterialTheme.colorScheme.outline,
                            onClick = { onSelect(i) },
                        )
                    }
                    Clue(puzzle.right[r])
                }
            }
            Row {
                Spacer(Modifier.size(cell))
                for (c in 0 until n) Clue(puzzle.bottom[c])
                Spacer(Modifier.size(cell))
            }
        }
    }
}

// ---------------------------------------------------------------------- Catsweeper

private val CatCellSize = 40.dp

private val NumberColorsLight = listOf(
    Color(0xFF1E66F5), Color(0xFF2E7D32), Color(0xFFD32F2F), Color(0xFF283593),
    Color(0xFF8E24AA), Color(0xFF00838F), Color(0xFF424242), Color(0xFF757575),
)
private val NumberColorsDark = listOf(
    Color(0xFF82AAFF), Color(0xFF81C784), Color(0xFFEF9A9A), Color(0xFF9FA8DA),
    Color(0xFFCE93D8), Color(0xFF80DEEA), Color(0xFFE0E0E0), Color(0xFFBDBDBD),
)

@Composable
fun CatsweeperBoard(
    puzzle: CatsweeperPuzzle,
    state: GameUiState,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val numberColors = if (colors.surface.luminance() > 0.5f) NumberColorsLight else NumberColorsDark
    val emojiSize = with(LocalDensity.current) { (CatCellSize * 0.5f).toSp() }
    val numberSize = with(LocalDensity.current) { (CatCellSize * 0.5f).toSp() }
    val gameOver = state.lost || state.solved

    FreeScrollBox(modifier) {
        Column(Modifier.padding(8.dp)) {
            for (r in 0 until puzzle.height) {
                Row {
                    for (c in 0 until puzzle.width) {
                        val i = r * puzzle.width + c
                        CatCell(
                            highlight = hintBackground(state, i),
                            entry = state.entries[i],
                            dog = puzzle.isDog(i),
                            count = puzzle.dogCounts[i],
                            gameOver = gameOver,
                            hiddenColor = colors.secondaryContainer,
                            revealedColor = colors.surface,
                            borderColor = colors.outline,
                            caughtColor = colors.errorContainer,
                            numberColor = numberColors[(puzzle.dogCounts[i] - 1).coerceIn(0, 7)],
                            emojiSize = emojiSize,
                            numberSize = numberSize,
                            onTap = { onTap(i) },
                            onLongPress = { onLongPress(i) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatCell(
    highlight: Color?,
    entry: Int,
    dog: Boolean,
    count: Int,
    gameOver: Boolean,
    hiddenColor: Color,
    revealedColor: Color,
    borderColor: Color,
    caughtColor: Color,
    numberColor: Color,
    emojiSize: androidx.compose.ui.unit.TextUnit,
    numberSize: androidx.compose.ui.unit.TextUnit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val revealed = entry == CatsweeperPuzzle.REVEALED
    val background = highlight ?: when {
        revealed && dog -> caughtColor
        revealed -> revealedColor
        else -> hiddenColor
    }
    Box(
        Modifier
            .size(CatCellSize)
            .background(background)
            .border(0.5.dp, borderColor)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        contentAlignment = Alignment.Center,
    ) {
        when {
            entry == CatsweeperPuzzle.MARKED -> Text("🦴", fontSize = emojiSize)
            revealed && dog -> Text("🐶", fontSize = emojiSize)
            revealed && count == 0 -> Text("🐱", fontSize = emojiSize, modifier = Modifier.alpha(0.45f))
            revealed -> Text(count.toString(), fontSize = numberSize, color = numberColor, fontWeight = FontWeight.Bold)
            gameOver && dog -> Text("🐶", fontSize = emojiSize)
        }
    }
}
