package de.carstenkeller.logicals.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import de.carstenkeller.logicals.core.KakuroPuzzle
import de.carstenkeller.logicals.core.SudokuPuzzle

// ---------------------------------------------------------------------- Sudoku

@Composable
fun SudokuBoard(
    puzzle: SudokuPuzzle,
    state: GameUiState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val boardSize = min(maxWidth, maxHeight) - 8.dp
        val cellSize = boardSize / 9
        val colors = MaterialTheme.colorScheme
        val selected = state.selected
        val selectedValue = if (selected >= 0) state.entries[selected] else 0
        val lineColor = colors.onSurface

        Box(Modifier.size(boardSize)) {
            Column {
                for (r in 0 until 9) {
                    Row {
                        for (c in 0 until 9) {
                            val i = r * 9 + c
                            val value = state.entries[i]
                            val related = selected >= 0 && isRelated(selected, i)
                            val background = hintBackground(state, i) ?: when {
                                i == selected -> colors.primaryContainer
                                value != 0 && value == selectedValue -> colors.secondaryContainer
                                related -> colors.surfaceVariant
                                else -> colors.surface
                            }
                            val textColor = when {
                                i in state.conflicts -> colors.error
                                !puzzle.isEditable(i) -> colors.onSurface
                                else -> colors.primary
                            }
                            NumberCell(
                                size = cellSize,
                                value = value,
                                notes = state.notes[i],
                                background = background,
                                textColor = textColor,
                                bold = !puzzle.isEditable(i),
                                onClick = { onSelect(i) },
                            )
                        }
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val step = size.width / 9
                for (k in 0..9) {
                    val thick = k % 3 == 0
                    val stroke = if (thick) 2.5.dp.toPx() else 0.75.dp.toPx()
                    val color = if (thick) lineColor else lineColor.copy(alpha = 0.4f)
                    drawLine(color, Offset(k * step, 0f), Offset(k * step, size.height), stroke)
                    drawLine(color, Offset(0f, k * step), Offset(size.width, k * step), stroke)
                }
            }
        }
    }
}

private fun isRelated(a: Int, b: Int): Boolean {
    val ra = a / 9
    val ca = a % 9
    val rb = b / 9
    val cb = b % 9
    return ra == rb || ca == cb || (ra / 3 == rb / 3 && ca / 3 == cb / 3)
}

// ---------------------------------------------------------------------- Kakuro

private val KakuroCellSize = 46.dp

@Composable
fun KakuroBoard(
    puzzle: KakuroPuzzle,
    state: GameUiState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
) {
    val cellSize = KakuroCellSize * zoom
    val colors = MaterialTheme.colorScheme
    val geometry = puzzle.geometry
    val selected = state.selected
    val highlightedRuns = remember(selected, puzzle) {
        if (selected >= 0 && puzzle.isEditable(selected)) {
            setOf(geometry.acrossRunOf[selected], geometry.downRunOf[selected])
        } else {
            emptySet()
        }
    }
    val blackColor = if (colors.surface.luminance() > 0.5f) Color(0xFF37474F) else Color(0xFF101418)
    val clueTextColor = Color(0xFFECEFF1)
    val clueSize = with(LocalDensity.current) { (cellSize * 0.3f).toSp() }

    Box(modifier) {
        Column(
            Modifier
                .padding(12.dp)
                .border(1.5.dp, colors.onSurface),
        ) {
            for (r in 0 until puzzle.height) {
                Row {
                    for (c in 0 until puzzle.width) {
                        val i = r * puzzle.width + c
                        val cell = puzzle.cells[i]
                        if (cell.white) {
                            val inRun = geometry.acrossRunOf[i] in highlightedRuns ||
                                geometry.downRunOf[i] in highlightedRuns
                            NumberCell(
                                size = cellSize,
                                value = state.entries[i],
                                notes = state.notes[i],
                                background = hintBackground(state, i) ?: when {
                                    i == selected -> colors.primaryContainer
                                    inRun -> colors.surfaceVariant
                                    else -> colors.surface
                                },
                                textColor = if (i in state.conflicts) colors.error else colors.primary,
                                bold = false,
                                border = colors.outline,
                                onClick = { onSelect(i) },
                            )
                        } else {
                            ClueCell(
                                cellSize = cellSize,
                                across = cell.across,
                                down = cell.down,
                                background = if (i in state.conflicts) colors.error else blackColor,
                                textColor = clueTextColor,
                                fontSize = clueSize,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClueCell(cellSize: Dp, across: Int, down: Int, background: Color, textColor: Color, fontSize: TextUnit) {
    Box(
        Modifier
            .size(cellSize)
            .background(background)
            .border(0.5.dp, Color.Black.copy(alpha = 0.5f)),
    ) {
        if (across != 0 || down != 0) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(
                    textColor.copy(alpha = 0.6f),
                    Offset.Zero,
                    Offset(size.width, size.height),
                    1.dp.toPx(),
                )
            }
        }
        if (across != 0) {
            Text(
                across.toString(),
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp),
            )
        }
        if (down != 0) {
            Text(
                down.toString(),
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 2.dp, start = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------- gemeinsam

/** Hervorhebung für den aktuell angezeigten Hinweis (Zielfeld kräftig, beteiligte Felder hell). */
@Composable
internal fun hintBackground(state: GameUiState, index: Int): Color? {
    val hint = state.hint ?: return null
    val colors = MaterialTheme.colorScheme
    return when (index) {
        hint.cell -> colors.tertiary.copy(alpha = 0.55f).compositeOver(colors.surface)
        in hint.related -> colors.tertiaryContainer.copy(alpha = 0.7f).compositeOver(colors.surface)
        else -> null
    }
}

@Composable
internal fun NumberCell(
    size: Dp,
    value: Int,
    notes: Int,
    background: Color,
    textColor: Color,
    bold: Boolean,
    onClick: () -> Unit,
    border: Color? = null,
) {
    val density = LocalDensity.current
    val valueSize = with(density) { (size * 0.6f).toSp() }
    val noteSize = with(density) { (size * 0.22f).toSp() }
    Box(
        Modifier
            .size(size)
            .background(background)
            .then(if (border != null) Modifier.border(0.5.dp, border) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (value != 0) {
            Text(
                value.toString(),
                color = textColor,
                fontSize = valueSize,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        } else if (notes != 0) {
            Column(Modifier.fillMaxSize().padding(1.dp)) {
                for (row in 0 until 3) {
                    Row(Modifier.weight(1f)) {
                        for (col in 0 until 3) {
                            val digit = row * 3 + col + 1
                            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (notes and (1 shl digit) != 0) {
                                    Text(
                                        digit.toString(),
                                        fontSize = noteSize,
                                        lineHeight = noteSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
