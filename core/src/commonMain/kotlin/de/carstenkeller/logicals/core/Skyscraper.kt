package de.carstenkeller.logicals.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Skyscraper (Wolkenkratzer): lateinisches Quadrat aus Haushöhen 1..n. Die Zahlen am Rand
 * geben an, wie viele Häuser man von dort aus sieht – höhere verdecken niedrigere.
 *
 * Randhinweise je Zeile/Spalte, 0 = kein Hinweis: [top]/[bottom] je Spalte, [left]/[right] je Zeile.
 */
@Serializable
@SerialName("skyscraper")
data class SkyscraperPuzzle(
    val size: Int,
    val givens: List<Int>,
    val top: List<Int>,
    val bottom: List<Int>,
    val left: List<Int>,
    val right: List<Int>,
    val solution: List<Int>,
    val difficulty: Difficulty,
) : Puzzle() {
    override val kind: PuzzleType get() = PuzzleType.SKYSCRAPER
    override val width: Int get() = size
    override val height: Int get() = size
    override val maxDigit: Int get() = size
    override val options: PuzzleOptions get() = PuzzleOptions(difficulty, size, size)

    /** Alle Linien mit Randhinweisen: Zellen in Blickrichtung vom Anfang aus. */
    val lines: List<SkyscraperLine> by lazy {
        // Achtung: innerhalb von buildList wäre "size" die Größe der entstehenden Liste.
        val n = size
        buildList {
            for (r in 0 until n) {
                add(SkyscraperLine("Zeile ${r + 1}", IntArray(n) { r * n + it }, left[r], right[r], "links", "rechts"))
            }
            for (c in 0 until n) {
                add(SkyscraperLine("Spalte ${c + 1}", IntArray(n) { it * n + c }, top[c], bottom[c], "oben", "unten"))
            }
        }
    }

    override fun isEditable(index: Int): Boolean = givens[index] == 0

    override fun initialEntries(): IntArray = givens.toIntArray()

    override fun conflicts(entries: IntArray): Set<Int> {
        val result = HashSet<Int>()
        latinConflicts(size, entries, result)
        for (line in lines) {
            val values = line.cells.map { entries[it] }
            if (values.any { it == 0 }) continue
            if (!line.accepts(values)) result += line.cells.toList()
        }
        return result
    }
}

/** Eine Zeile oder Spalte mit ihren beiden Randhinweisen (0 = keiner). */
class SkyscraperLine(
    val name: String,
    val cells: IntArray,
    val startClue: Int,
    val endClue: Int,
    val startSide: String,
    val endSide: String,
) {
    fun accepts(values: List<Int>): Boolean =
        (startClue == 0 || visible(values) == startClue) && (endClue == 0 || visible(values.asReversed()) == endClue)

    val hasClue: Boolean get() = startClue != 0 || endClue != 0
}

/** Anzahl der sichtbaren Häuser in Blickrichtung. */
fun visible(values: List<Int>): Int {
    var max = 0
    var count = 0
    for (v in values) if (v > max) {
        max = v
        count++
    }
    return count
}

/**
 * Erzeugt Skyscraper-Rätsel, die sich mit den erklärbaren Hinweis-Schritten vollständig lösen
 * lassen (damit eindeutig).
 *
 * Ablauf: zufälliges lateinisches Quadrat → alle Randhinweise berechnen → falls nötig
 * Vorgaben an Problemstellen ergänzen → erst Vorgaben, dann Randhinweise entfernen,
 * solange es lösbar bleibt →
 * leichtere Stufen erhalten einen Teil der Hinweise zurück (heuristische Abstufung).
 */
class SkyscraperGenerator(private val random: Random = Random.Default) {

    fun generate(size: Int, difficulty: Difficulty): SkyscraperPuzzle {
        val n = size
        val solution = randomLatinSquare(n, random)
        val clues = Array(4) { IntArray(n) }
        for (i in 0 until n) {
            val row = (0 until n).map { solution[i * n + it] }
            val col = (0 until n).map { solution[it * n + i] }
            clues[TOP][i] = visible(col)
            clues[BOTTOM][i] = visible(col.asReversed())
            clues[LEFT][i] = visible(row)
            clues[RIGHT][i] = visible(row.asReversed())
        }
        val givens = IntArray(n * n)

        fun build() = SkyscraperPuzzle(
            n, givens.toList(), clues[TOP].toList(), clues[BOTTOM].toList(),
            clues[LEFT].toList(), clues[RIGHT].toList(), solution.toList(), difficulty,
        )

        // Reichen alle Randhinweise nicht, an der Problemstelle Vorgaben ergänzen.
        while (true) {
            val stuck = logicalStuckCell(build())
            if (stuck == -1) break
            givens[stuck] = solution[stuck]
        }

        // Überflüssige Hinweise entfernen: erst Vorgaben (das Rätsel soll von den
        // Randhinweisen leben), dann Randhinweise.
        for (cell in givens.indices.filter { givens[it] != 0 }.shuffled(random)) {
            val value = givens[cell]
            givens[cell] = 0
            if (logicalStuckCell(build()) != -1) givens[cell] = value
        }
        val removable = buildList {
            for (side in 0 until 4) for (i in 0 until n) add(side to i)
        }.shuffled(random)
        val removed = ArrayList<Pair<Int, Int>>()
        for ((side, i) in removable) {
            val value = clues[side][i]
            clues[side][i] = 0
            if (logicalStuckCell(build()) == -1) removed += side to i else clues[side][i] = value
        }

        // Leichtere Stufen: einige Randhinweise zurück.
        val restore = when (difficulty) {
            Difficulty.EASY -> removed.size * 2 / 3
            Difficulty.MEDIUM -> removed.size / 3
            Difficulty.HARD -> 0
        }
        for ((side, i) in removed.shuffled(random).take(restore)) {
            val line = if (side == TOP || side == BOTTOM) {
                (0 until n).map { solution[it * n + i] }
            } else {
                (0 until n).map { solution[i * n + it] }
            }
            clues[side][i] = visible(if (side == BOTTOM || side == RIGHT) line.asReversed() else line)
        }
        return build()
    }

    private companion object {
        const val TOP = 0
        const val BOTTOM = 1
        const val LEFT = 2
        const val RIGHT = 3
    }
}
