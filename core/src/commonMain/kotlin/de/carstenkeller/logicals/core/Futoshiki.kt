package de.carstenkeller.logicals.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Futoshiki: lateinisches Quadrat (jede Ziffer 1..n einmal pro Zeile und Spalte) mit
 * Ungleichungen zwischen benachbarten Feldern.
 *
 * [right] und [down] kodieren die Relation zur rechten bzw. unteren Nachbarzelle:
 * 0 = keine, [LESS] = diese Zelle ist kleiner, [GREATER] = diese Zelle ist größer.
 */
@Serializable
@SerialName("futoshiki")
data class FutoshikiPuzzle(
    val size: Int,
    val givens: List<Int>,
    val right: List<Int>,
    val down: List<Int>,
    val solution: List<Int>,
    val difficulty: Difficulty,
) : Puzzle() {
    override val kind: PuzzleType get() = PuzzleType.FUTOSHIKI
    override val width: Int get() = size
    override val height: Int get() = size
    override val maxDigit: Int get() = size
    override val options: PuzzleOptions get() = PuzzleOptions(difficulty, size, size)

    override fun isEditable(index: Int): Boolean = givens[index] == 0

    override fun initialEntries(): IntArray = givens.toIntArray()

    override fun conflicts(entries: IntArray): Set<Int> {
        val result = HashSet<Int>()
        latinConflicts(size, entries, result)
        for (i in entries.indices) {
            val a = entries[i]
            if (a == 0) continue
            if (i % size < size - 1) checkRelation(right[i], a, entries[i + 1], i, i + 1, result)
            if (i / size < size - 1) checkRelation(down[i], a, entries[i + size], i, i + size, result)
        }
        return result
    }

    private fun checkRelation(rel: Int, a: Int, b: Int, i: Int, j: Int, out: MutableSet<Int>) {
        if (b == 0 || rel == 0) return
        if ((rel == LESS && a >= b) || (rel == GREATER && a <= b)) {
            out += i
            out += j
        }
    }

    companion object {
        const val LESS = 1
        const val GREATER = 2
    }
}

/** Doppelte Ziffern in Zeilen und Spalten eines n×n-Gitters. */
internal fun latinConflicts(n: Int, entries: IntArray, out: MutableSet<Int>) {
    for (line in 0 until n) {
        for (a in 0 until n) {
            for (b in a + 1 until n) {
                val r1 = line * n + a
                val r2 = line * n + b
                if (entries[r1] != 0 && entries[r1] == entries[r2]) {
                    out += r1
                    out += r2
                }
                val c1 = a * n + line
                val c2 = b * n + line
                if (entries[c1] != 0 && entries[c1] == entries[c2]) {
                    out += c1
                    out += c2
                }
            }
        }
    }
}

/**
 * Zählt Lösungen eines Futoshiki. Vor jeder Verzweigung wird propagiert: vergebene
 * Ziffern aus Zeile/Spalte streichen, Ziffern mit nur einem möglichen Platz setzen und
 * Ungleichungen über Minimum/Maximum der Nachbarkandidaten einschränken.
 */
internal class FutoshikiSolver(
    private val n: Int,
    private val givens: IntArray,
    private val right: IntArray,
    private val down: IntArray,
) {
    class Result(val count: Int, val solutions: List<IntArray>, val aborted: Boolean)

    private val all = ((1 shl (n + 1)) - 1) and 1.inv()
    /** Ungleichungen als Paare (kleinere Zelle, größere Zelle). */
    private val lessPairs: List<IntArray> = buildList {
        for (i in 0 until n * n) {
            if (i % n < n - 1 && right[i] != 0) add(if (right[i] == FutoshikiPuzzle.LESS) intArrayOf(i, i + 1) else intArrayOf(i + 1, i))
            if (i / n < n - 1 && down[i] != 0) add(if (down[i] == FutoshikiPuzzle.LESS) intArrayOf(i, i + n) else intArrayOf(i + n, i))
        }
    }
    private val lines: List<IntArray> = buildList {
        for (r in 0 until n) add(IntArray(n) { r * n + it })
        for (c in 0 until n) add(IntArray(n) { it * n + c })
    }
    private val found = ArrayList<IntArray>()
    private var nodes = 0L
    private var nodeLimit = 0L
    private var aborted = false

    fun solve(limit: Int = 2, nodeLimit: Long = 200_000L): Result {
        found.clear()
        nodes = 0
        aborted = false
        this.nodeLimit = nodeLimit
        val dom = IntArray(n * n) { if (givens[it] != 0) 1 shl givens[it] else all }
        search(dom, limit)
        return Result(found.size, found.toList(), aborted)
    }

    private fun search(dom: IntArray, limit: Int): Boolean {
        if (++nodes > nodeLimit) {
            aborted = true
            return true
        }
        if (!propagate(dom)) return false
        var best = -1
        var bestCount = Int.MAX_VALUE
        for (i in dom.indices) {
            val cnt = dom[i].countOneBits()
            if (cnt in 2 until bestCount) {
                best = i
                bestCount = cnt
                if (cnt == 2) break
            }
        }
        if (best == -1) {
            found += IntArray(dom.size) { dom[it].countTrailingZeroBits() }
            return found.size >= limit
        }
        for (d in 1..n) {
            if (dom[best] and (1 shl d) == 0) continue
            val copy = dom.copyOf()
            copy[best] = 1 shl d
            if (search(copy, limit)) return true
        }
        return false
    }

    private fun propagate(dom: IntArray): Boolean {
        var changed = true
        while (changed) {
            changed = false
            for (line in lines) {
                // Vergebene Ziffern bei den anderen Zellen streichen.
                var singles = 0
                for (c in line) {
                    val d = dom[c]
                    if (d.countOneBits() == 1) {
                        if (singles and d != 0) return false
                        singles = singles or d
                    }
                }
                var union = 0
                for (c in line) {
                    val d = dom[c]
                    if (d.countOneBits() != 1) {
                        val nd = d and singles.inv()
                        if (nd == 0) return false
                        if (nd != d) {
                            dom[c] = nd
                            changed = true
                        }
                    }
                    union = union or dom[c]
                }
                if (union != all) return false
                // Ziffern mit nur einem möglichen Platz setzen.
                for (v in 1..n) {
                    val bit = 1 shl v
                    if (singles and bit != 0) continue
                    var where = -1
                    var count = 0
                    for (c in line) if (dom[c] and bit != 0) {
                        count++
                        where = c
                    }
                    if (count == 0) return false
                    if (count == 1 && dom[where] != bit) {
                        dom[where] = bit
                        changed = true
                    }
                }
            }
            for (pair in lessPairs) {
                val a = pair[0]
                val b = pair[1]
                // a < b: a kleiner als größter Kandidat von b, b größer als kleinster von a.
                val maxB = 31 - dom[b].countLeadingZeroBits()
                val minA = dom[a].countTrailingZeroBits()
                val na = dom[a] and ((1 shl maxB) - 1)
                val nb = dom[b] and ((1 shl (minA + 1)) - 1).inv()
                if (na == 0 || nb == 0) return false
                if (na != dom[a] || nb != dom[b]) {
                    dom[a] = na
                    dom[b] = nb
                    changed = true
                }
            }
        }
        return true
    }
}

/**
 * Erzeugt Futoshikis mit eindeutiger Lösung.
 *
 * Ablauf: zufälliges lateinisches Quadrat → so lange Ungleichungen (bevorzugt) bzw.
 * Vorgaben hinzufügen, bis die erklärbaren Hinweis-Schritte das Rätsel lösen (damit ist die
 * Lösung eindeutig) → überflüssige Hinweise entfernen →
 * je nach Schwierigkeit wieder einige Vorgaben ergänzen (heuristische Abstufung).
 */
class FutoshikiGenerator(private val random: Random = Random.Default) {

    fun generate(size: Int, difficulty: Difficulty): FutoshikiPuzzle {
        while (true) {
            attempt(size, difficulty)?.let { return it }
        }
    }

    private fun attempt(n: Int, difficulty: Difficulty): FutoshikiPuzzle? {
        val solution = randomLatinSquare(n, random)
        val givens = IntArray(n * n)
        val right = IntArray(n * n)
        val down = IntArray(n * n)

        fun relation(a: Int, b: Int) = if (a < b) FutoshikiPuzzle.LESS else FutoshikiPuzzle.GREATER

        // Startmenge: ein Teil der möglichen Ungleichungen.
        for (i in solution.indices) {
            if (i % n < n - 1 && random.nextDouble() < 0.25) right[i] = relation(solution[i], solution[i + 1])
            if (i / n < n - 1 && random.nextDouble() < 0.25) down[i] = relation(solution[i], solution[i + n])
        }

        // Hinweise ergänzen, bis die erklärbaren Schritte das Rätsel vollständig lösen.
        repeat(n * n * 3) {
            val cell = stuckCell(n, solution, givens, right, down, difficulty)
            if (cell == -1) return finish(n, solution, givens, right, down, difficulty)
            if (random.nextInt(4) == 0) {
                givens[cell] = solution[cell]
            } else {
                val options = buildList {
                    if (cell % n < n - 1 && right[cell] == 0) add(cell to "r")
                    if (cell % n > 0 && right[cell - 1] == 0) add(cell - 1 to "r")
                    if (cell / n < n - 1 && down[cell] == 0) add(cell to "d")
                    if (cell / n > 0 && down[cell - n] == 0) add(cell - n to "d")
                }
                if (options.isEmpty()) {
                    givens[cell] = solution[cell]
                } else {
                    val (i, dir) = options.random(random)
                    if (dir == "r") right[i] = relation(solution[i], solution[i + 1])
                    else down[i] = relation(solution[i], solution[i + n])
                }
            }
        }
        return null
    }

    private fun stuckCell(
        n: Int, solution: IntArray, givens: IntArray, right: IntArray, down: IntArray, difficulty: Difficulty,
    ): Int = logicalStuckCell(
        FutoshikiPuzzle(n, givens.toList(), right.toList(), down.toList(), solution.toList(), difficulty),
    )

    private fun finish(
        n: Int,
        solution: IntArray,
        givens: IntArray,
        right: IntArray,
        down: IntArray,
        difficulty: Difficulty,
    ): FutoshikiPuzzle {
        // Überflüssige Hinweise entfernen (Vorgaben zuerst, damit Ungleichungen tragen).
        val clues = buildList {
            for (i in givens.indices) if (givens[i] != 0) add(Triple(0, i, givens[i]))
        }.shuffled(random) + buildList {
            for (i in right.indices) if (right[i] != 0) add(Triple(1, i, right[i]))
            for (i in down.indices) if (down[i] != 0) add(Triple(2, i, down[i]))
        }.shuffled(random)
        for ((kind, i, value) in clues) {
            val array = when (kind) {
                0 -> givens
                1 -> right
                else -> down
            }
            array[i] = 0
            if (stuckCell(n, solution, givens, right, down, difficulty) != -1) array[i] = value
        }
        // Leichtere Stufen bekommen zusätzliche Vorgaben.
        val extra = when (difficulty) {
            Difficulty.EASY -> n * n / 4
            Difficulty.MEDIUM -> n / 2
            Difficulty.HARD -> 0
        }
        val empty = givens.indices.filter { givens[it] == 0 }.shuffled(random)
        for (i in empty.take(extra)) givens[i] = solution[i]
        return FutoshikiPuzzle(n, givens.toList(), right.toList(), down.toList(), solution.toList(), difficulty)
    }
}
