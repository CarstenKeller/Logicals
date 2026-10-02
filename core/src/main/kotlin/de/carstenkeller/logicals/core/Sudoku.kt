package de.carstenkeller.logicals.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Schwierigkeit über die Anzahl der vorgegebenen Ziffern (weniger = schwerer). */
enum class SudokuDifficulty(val targetClues: Int) {
    EASY(38),
    MEDIUM(31),
    HARD(25),
}

@Serializable
@SerialName("sudoku")
data class SudokuPuzzle(
    val givens: List<Int>,
    val solution: List<Int>,
    val difficulty: SudokuDifficulty,
) : Puzzle() {
    override val kind: PuzzleType get() = PuzzleType.SUDOKU
    override val width: Int get() = 9
    override val height: Int get() = 9

    override fun isEditable(index: Int): Boolean = givens[index] == 0

    override fun initialEntries(): IntArray = givens.toIntArray()

    override fun conflicts(entries: IntArray): Set<Int> {
        val result = HashSet<Int>()
        for (unit in UNITS) {
            for (a in unit.indices) {
                val va = entries[unit[a]]
                if (va == 0) continue
                for (b in a + 1 until unit.size) {
                    if (entries[unit[b]] == va) {
                        result += unit[a]
                        result += unit[b]
                    }
                }
            }
        }
        return result
    }

    companion object {
        /** Alle 27 Einheiten: 9 Zeilen, 9 Spalten, 9 Blöcke. */
        val UNITS: List<IntArray> = buildList {
            for (r in 0 until 9) add(IntArray(9) { r * 9 + it })
            for (c in 0 until 9) add(IntArray(9) { it * 9 + c })
            for (b in 0 until 9) {
                val r0 = (b / 3) * 3
                val c0 = (b % 3) * 3
                add(IntArray(9) { (r0 + it / 3) * 9 + c0 + it % 3 })
            }
        }
    }
}

/** Backtracking-Löser mit Bitmasken und "kleinste Kandidatenmenge zuerst". */
object SudokuSolver {
    private const val ALL = 0x3FE // Bits 1..9

    /** Zählt Lösungen bis höchstens [limit]. */
    fun countSolutions(grid: IntArray, limit: Int = 2): Int = Search(grid, limit, null).run()

    /** Liefert eine (bei [random] zufällige) Lösung oder null. */
    fun solve(grid: IntArray, random: Random? = null): IntArray? {
        val search = Search(grid, 1, random)
        search.run()
        return search.first
    }

    private class Search(grid: IntArray, private val limit: Int, private val random: Random?) {
        private val cells = grid.copyOf()
        private val rows = IntArray(9)
        private val cols = IntArray(9)
        private val boxes = IntArray(9)
        private var valid = true
        private var count = 0
        var first: IntArray? = null

        init {
            for (i in 0 until 81) {
                val v = cells[i]
                if (v == 0) continue
                val bit = 1 shl v
                val r = i / 9
                val c = i % 9
                val b = (r / 3) * 3 + c / 3
                if ((rows[r] or cols[c] or boxes[b]) and bit != 0) valid = false
                rows[r] = rows[r] or bit
                cols[c] = cols[c] or bit
                boxes[b] = boxes[b] or bit
            }
        }

        fun run(): Int {
            if (!valid) return 0
            recurse()
            return count
        }

        /** @return true, wenn die Suche abgebrochen werden soll. */
        private fun recurse(): Boolean {
            var best = -1
            var bestMask = 0
            var bestCount = 10
            for (i in 0 until 81) {
                if (cells[i] != 0) continue
                val r = i / 9
                val c = i % 9
                val b = (r / 3) * 3 + c / 3
                val mask = ALL and (rows[r] or cols[c] or boxes[b]).inv()
                val n = Integer.bitCount(mask)
                if (n == 0) return false
                if (n < bestCount) {
                    best = i
                    bestMask = mask
                    bestCount = n
                    if (n == 1) break
                }
            }
            if (best == -1) {
                count++
                if (first == null) first = cells.copyOf()
                return count >= limit
            }
            val r = best / 9
            val c = best % 9
            val b = (r / 3) * 3 + c / 3
            val digits = (1..9).filter { bestMask and (1 shl it) != 0 }.let {
                if (random != null) it.shuffled(random) else it
            }
            for (d in digits) {
                val bit = 1 shl d
                rows[r] = rows[r] or bit
                cols[c] = cols[c] or bit
                boxes[b] = boxes[b] or bit
                cells[best] = d
                val stop = recurse()
                rows[r] = rows[r] and bit.inv()
                cols[c] = cols[c] and bit.inv()
                boxes[b] = boxes[b] and bit.inv()
                cells[best] = 0
                if (stop) return true
            }
            return false
        }
    }
}

/** Erzeugt Sudokus mit genau einer Lösung. */
class SudokuGenerator(private val random: Random = Random.Default) {

    fun generate(difficulty: SudokuDifficulty): SudokuPuzzle {
        val solution = requireNotNull(SudokuSolver.solve(IntArray(81), random))
        val grid = solution.copyOf()
        var clues = 81
        for (i in (0 until 81).shuffled(random)) {
            if (clues <= difficulty.targetClues) break
            val backup = grid[i]
            grid[i] = 0
            if (SudokuSolver.countSolutions(grid, 2) == 1) {
                clues--
            } else {
                grid[i] = backup
            }
        }
        return SudokuPuzzle(grid.toList(), solution.toList(), difficulty)
    }
}
