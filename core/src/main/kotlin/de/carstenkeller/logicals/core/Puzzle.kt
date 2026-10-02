package de.carstenkeller.logicals.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Die verfügbaren Rätselarten. Neue Arten werden hier und in [Puzzle] ergänzt. */
enum class PuzzleType { SUDOKU, KAKURO }

/**
 * Gemeinsame Sicht auf ein rechteckiges Zahlenrätsel.
 *
 * Zellen werden zeilenweise indiziert (`index = row * width + col`). Einträge des Spielers
 * liegen als IntArray gleicher Länge vor, 0 bedeutet "leer".
 */
@Serializable
sealed class Puzzle {
    abstract val kind: PuzzleType
    abstract val width: Int
    abstract val height: Int

    val cellCount: Int get() = width * height

    /** Darf der Spieler in diese Zelle eine Zahl eintragen? */
    abstract fun isEditable(index: Int): Boolean

    /** Startbelegung (z. B. vorgegebene Sudoku-Ziffern). */
    abstract fun initialEntries(): IntArray

    /**
     * Indizes der Zellen, die gegen eine Regel verstoßen. Kann auch nicht editierbare
     * Zellen (z. B. Kakuro-Summenfelder) enthalten.
     */
    abstract fun conflicts(entries: IntArray): Set<Int>

    /** Gelöst, wenn alle Felder gefüllt sind und keine Regel verletzt ist. */
    fun isSolved(entries: IntArray): Boolean {
        for (i in 0 until cellCount) {
            if (isEditable(i) && entries[i] == 0) return false
        }
        return conflicts(entries).isEmpty()
    }
}

/** Spielstand inklusive bisher benötigter Zeit. */
@Serializable
data class GameState(
    val puzzle: Puzzle,
    val entries: List<Int>,
    /** Notizen je Zelle als Bitmaske (Bit n = Ziffer n). */
    val notes: List<Int>,
    val elapsedMillis: Long,
) {
    companion object {
        fun start(puzzle: Puzzle): GameState = GameState(
            puzzle = puzzle,
            entries = puzzle.initialEntries().toList(),
            notes = List(puzzle.cellCount) { 0 },
            elapsedMillis = 0L,
        )
    }
}

val PuzzleJson: Json = Json {
    ignoreUnknownKeys = true
}
