package de.carstenkeller.logicals.web.ui.game

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
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
