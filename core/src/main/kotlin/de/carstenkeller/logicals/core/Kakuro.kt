package de.carstenkeller.logicals.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Eine Kakuro-Zelle. Weiße Zellen nehmen Ziffern auf, schwarze Zellen tragen ggf.
 * Summen für die rechts ([across]) bzw. darunter ([down]) beginnende Zellfolge (0 = keine).
 */
@Serializable
data class KakuroCell(
    val white: Boolean,
    val across: Int = 0,
    val down: Int = 0,
)

/** Eine zusammenhängende Folge weißer Zellen mit gemeinsamer Summe. */
class KakuroRun(
    val clueIndex: Int,
    val horizontal: Boolean,
    val cells: IntArray,
)

/** Zellfolgen eines Kakuro-Gitters. Unabhängig von den Summen, nur aus der Weiß/Schwarz-Struktur. */
class KakuroGeometry(val width: Int, val height: Int, val white: BooleanArray) {
    val runs: List<KakuroRun>
    /** Index der waagerechten Folge je Zelle, -1 für schwarze Zellen. */
    val acrossRunOf = IntArray(width * height) { -1 }
    /** Index der senkrechten Folge je Zelle, -1 für schwarze Zellen. */
    val downRunOf = IntArray(width * height) { -1 }

    init {
        val list = ArrayList<KakuroRun>()
        for (r in 0 until height) {
            var c = 0
            while (c < width) {
                if (white[r * width + c] && (c == 0 || !white[r * width + c - 1])) {
                    val start = c
                    while (c < width && white[r * width + c]) c++
                    val cells = IntArray(c - start) { r * width + start + it }
                    val id = list.size
                    cells.forEach { acrossRunOf[it] = id }
                    list += KakuroRun(clueIndex = r * width + start - 1, horizontal = true, cells = cells)
                } else {
                    c++
                }
            }
        }
        for (c in 0 until width) {
            var r = 0
            while (r < height) {
                if (white[r * width + c] && (r == 0 || !white[(r - 1) * width + c])) {
                    val start = r
                    while (r < height && white[r * width + c]) r++
                    val cells = IntArray(r - start) { (start + it) * width + c }
                    val id = list.size
                    cells.forEach { downRunOf[it] = id }
                    list += KakuroRun(clueIndex = (start - 1) * width + c, horizontal = false, cells = cells)
                } else {
                    r++
                }
            }
        }
        runs = list
    }
}

@Serializable
@SerialName("kakuro")
data class KakuroPuzzle(
    override val width: Int,
    override val height: Int,
    val cells: List<KakuroCell>,
    /** Lösung des Generators (0 für schwarze Zellen). Wird nur als Referenz gespeichert. */
    val solution: List<Int>,
    /** false, wenn der Generator keine eindeutige Lösung garantieren konnte. */
    val unique: Boolean = true,
) : Puzzle() {
    override val kind: PuzzleType get() = PuzzleType.KAKURO
    override val options: PuzzleOptions get() = PuzzleOptions(Difficulty.MEDIUM, width, height)

    val geometry: KakuroGeometry by lazy {
        KakuroGeometry(width, height, BooleanArray(cells.size) { cells[it].white })
    }

    fun sumOf(run: KakuroRun): Int =
        if (run.horizontal) cells[run.clueIndex].across else cells[run.clueIndex].down

    override fun isEditable(index: Int): Boolean = cells[index].white

    override fun initialEntries(): IntArray = IntArray(cells.size)

    override fun conflicts(entries: IntArray): Set<Int> {
        val result = HashSet<Int>()
        for (run in geometry.runs) {
            val target = sumOf(run)
            var sum = 0
            var filled = 0
            for (a in run.cells.indices) {
                val va = entries[run.cells[a]]
                if (va == 0) continue
                sum += va
                filled++
                for (b in a + 1 until run.cells.size) {
                    if (entries[run.cells[b]] == va) {
                        result += run.cells[a]
                        result += run.cells[b]
                    }
                }
            }
            val complete = filled == run.cells.size
            if (sum > target || (complete && sum != target)) {
                result += run.clueIndex
                run.cells.filterTo(result) { entries[it] != 0 }
            }
        }
        return result
    }
}
