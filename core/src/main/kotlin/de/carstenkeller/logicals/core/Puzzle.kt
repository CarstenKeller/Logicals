package de.carstenkeller.logicals.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** Schwierigkeitsstufen, gemeinsam für alle Rätselarten. */
enum class Difficulty { EASY, MEDIUM, HARD }

/**
 * Die verfügbaren Rätselarten samt ihrer Einstellmöglichkeiten für neue Rätsel.
 *
 * @property sizeRange erlaubte Kantenlängen, null = feste Größe
 * @property rectangular true: Breite und Höhe getrennt wählbar, sonst quadratisch
 */
enum class PuzzleType(
    val hasDifficulty: Boolean,
    val sizeRange: IntRange?,
    val rectangular: Boolean,
    val defaultWidth: Int,
    val defaultHeight: Int,
) {
    SUDOKU(hasDifficulty = true, sizeRange = null, rectangular = false, defaultWidth = 9, defaultHeight = 9),
    KAKURO(hasDifficulty = false, sizeRange = 4..30, rectangular = true, defaultWidth = 10, defaultHeight = 10),
    FUTOSHIKI(hasDifficulty = true, sizeRange = 4..9, rectangular = false, defaultWidth = 6, defaultHeight = 6),
    KENKEN(hasDifficulty = true, sizeRange = 4..9, rectangular = false, defaultWidth = 5, defaultHeight = 5),
    CATSWEEPER(hasDifficulty = true, sizeRange = 5..30, rectangular = true, defaultWidth = 10, defaultHeight = 14),
}

/** Einstellungen für ein neues Rätsel. Bei quadratischen Rätseln zählt nur [width]. */
data class PuzzleOptions(
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val width: Int,
    val height: Int,
)

/**
 * Gemeinsame Sicht auf ein rechteckiges Rätsel.
 *
 * Zellen werden zeilenweise indiziert (`index = row * width + col`). Einträge des Spielers
 * liegen als IntArray gleicher Länge vor. Bei Zahlenrätseln ist 0 "leer", Catsweeper
 * kodiert dort den Zustand der Zelle (siehe [CatsweeperPuzzle]).
 */
@Serializable
sealed class Puzzle {
    abstract val kind: PuzzleType
    abstract val width: Int
    abstract val height: Int

    val cellCount: Int get() = width * height

    /** Größte eintragbare Ziffer (bestimmt das Ziffernfeld). */
    open val maxDigit: Int get() = 9

    /** Einstellungen, mit denen ein gleichartiges neues Rätsel erzeugt werden kann. */
    abstract val options: PuzzleOptions

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
    open fun isSolved(entries: IntArray): Boolean {
        for (i in 0 until cellCount) {
            if (isEditable(i) && entries[i] == 0) return false
        }
        return conflicts(entries).isEmpty()
    }

    /** Verloren (nur bei Rätseln, die man verlieren kann, z. B. Catsweeper). */
    open fun isLost(entries: IntArray): Boolean = false
}

/** Erzeugt Rätsel aller Arten. */
object PuzzleFactory {
    fun generate(type: PuzzleType, options: PuzzleOptions, random: Random = Random.Default): Puzzle {
        val range = type.sizeRange
        val w = if (range != null) options.width.coerceIn(range) else type.defaultWidth
        val h = when {
            range == null -> type.defaultHeight
            type.rectangular -> options.height.coerceIn(range)
            else -> w
        }
        return when (type) {
            PuzzleType.SUDOKU -> SudokuGenerator(random).generate(options.difficulty)
            PuzzleType.KAKURO -> KakuroGenerator(random).generate(w, h).puzzle
            PuzzleType.FUTOSHIKI -> FutoshikiGenerator(random).generate(w, options.difficulty)
            PuzzleType.KENKEN -> KenKenGenerator(random).generate(w, options.difficulty)
            PuzzleType.CATSWEEPER -> CatsweeperGenerator(random).generate(w, h, options.difficulty)
        }
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

/** Zufälliges lateinisches Quadrat der Größe [n] (jede Ziffer 1..n einmal pro Zeile/Spalte). */
internal fun randomLatinSquare(n: Int, random: Random): IntArray {
    val grid = IntArray(n * n)
    val rows = IntArray(n)
    val cols = IntArray(n)

    fun fill(i: Int): Boolean {
        if (i == n * n) return true
        val r = i / n
        val c = i % n
        for (d in (1..n).shuffled(random)) {
            val bit = 1 shl d
            if ((rows[r] or cols[c]) and bit != 0) continue
            grid[i] = d
            rows[r] = rows[r] or bit
            cols[c] = cols[c] or bit
            if (fill(i + 1)) return true
            rows[r] = rows[r] and bit.inv()
            cols[c] = cols[c] and bit.inv()
        }
        grid[i] = 0
        return false
    }

    check(fill(0))
    return grid
}
