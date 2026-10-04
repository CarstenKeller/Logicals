package de.carstenkeller.logicals.core

/** Vorberechnete Ziffernkombinationen: Bitmasken (Bits 1..9) je (Länge, Summe). */
object KakuroCombos {
    private val table: Array<Array<IntArray>> = Array(10) { len ->
        Array(46) { sum ->
            (0 until 512).map { it shl 1 }
                .filter { it.countOneBits() == len && maskSum(it) == sum }
                .toIntArray()
        }
    }

    fun of(length: Int, sum: Int): IntArray =
        if (length in 1..9 && sum in 0..45) table[length][sum] else IntArray(0)

    /** Ziffern einer Bitmaske, aufsteigend. */
    fun digits(mask: Int): List<Int> = (1..9).filter { mask and (1 shl it) != 0 }

    private fun maskSum(mask: Int): Int = (1..9).sumOf { if (mask and (1 shl it) != 0) it else 0 }
}

/**
 * Zählt Lösungen eines Kakuro (bis zu einem Limit).
 *
 * Jede weiße Zelle hat eine Kandidatenmenge (Bitmaske). Vor jeder Verzweigung wird bis
 * zum Fixpunkt propagiert: vergebene Ziffern streichen, nur Ziffern aus noch möglichen
 * Kombinationen zulassen und Ziffern, die in einer Folge nur noch in eine Zelle passen,
 * direkt setzen. Danach wird an der Zelle mit den wenigsten Kandidaten verzweigt.
 */
class KakuroSolver(
    private val geometry: KakuroGeometry,
    /** Summe je Folge, gleiche Reihenfolge wie [KakuroGeometry.runs]. */
    private val sums: IntArray,
) {
    class Result(val count: Int, val solutions: List<IntArray>, val aborted: Boolean)

    private val runs = geometry.runs.map { it.cells }
    private val combos = Array(runs.size) { KakuroCombos.of(runs[it].size, sums[it]) }
    private val whiteCells = geometry.white.indices.filter { geometry.white[it] }.toIntArray()

    /** Stärke der Propagation; [solve] nutzt immer die volle Stärke. */
    private var cellCheck = true
    private var hiddenSingles = true

    private var nodes = 0L
    private var nodeLimit = 0L
    private var limit = 0
    private var aborted = false
    private val found = ArrayList<IntArray>()

    fun solve(limit: Int = 2, nodeLimit: Long = 200_000L): Result {
        cellCheck = true
        hiddenSingles = true
        nodes = 0
        this.nodeLimit = nodeLimit
        this.limit = limit
        aborted = false
        found.clear()
        val domains = IntArray(geometry.white.size)
        for (cell in whiteCells) domains[cell] = ALL
        if (combos.all { it.isNotEmpty() }) search(domains)
        return Result(found.size, found.toList(), aborted)
    }

    /**
     * Nur logisches Schließen ohne Raten, in der für [level] erlaubten Stärke:
     * - EASY: Kombinationen passend zu Summe und bereits gesetzten Ziffern, vergebene
     *   Ziffern streichen.
     * - MEDIUM: zusätzlich Ziffern, die in jeder Kombination vorkommen und nur noch an
     *   eine Stelle passen, sowie Kombinationen, deren Ziffern keine Zelle mehr aufnimmt.
     * - HARD: zusätzlich Kombinationen verwerfen, für die ein einzelnes Feld keinen
     *   passenden Kandidaten mehr hat.
     * @return Kandidatenmengen je Zelle nach der Propagation oder null bei Widerspruch.
     */
    fun deduce(level: Difficulty = Difficulty.HARD): IntArray? {
        if (combos.any { it.isEmpty() }) return null
        hiddenSingles = level != Difficulty.EASY
        cellCheck = level == Difficulty.HARD
        val domains = IntArray(geometry.white.size)
        for (cell in whiteCells) domains[cell] = ALL
        return if (propagate(domains)) domains else null
    }

    /** @return true, wenn die Suche beendet werden soll. */
    private fun search(domains: IntArray): Boolean {
        if (++nodes > nodeLimit) {
            aborted = true
            return true
        }
        if (!propagate(domains)) return false
        var best = -1
        var bestCount = 10
        for (cell in whiteCells) {
            val n = domains[cell].countOneBits()
            if (n in 2 until bestCount) {
                best = cell
                bestCount = n
                if (n == 2) break
            }
        }
        if (best == -1) {
            found += IntArray(domains.size) { if (domains[it] == 0) 0 else domains[it].countTrailingZeroBits() }
            return found.size >= limit
        }
        val mask = domains[best]
        for (d in 1..9) {
            val bit = 1 shl d
            if (mask and bit == 0) continue
            val copy = domains.copyOf()
            copy[best] = bit
            if (search(copy)) return true
        }
        return false
    }

    /** Propagiert bis zum Fixpunkt (Arbeitsliste betroffener Folgen). @return false bei Widerspruch. */
    private fun propagate(dom: IntArray): Boolean {
        val queued = BooleanArray(runs.size) { true }
        val queue = ArrayDeque<Int>(runs.size)
        for (r in runs.indices) queue.addLast(r)

        fun changed(cell: Int) {
            for (r in intArrayOf(geometry.acrossRunOf[cell], geometry.downRunOf[cell])) {
                if (!queued[r]) {
                    queued[r] = true
                    queue.addLast(r)
                }
            }
        }

        while (queue.isNotEmpty()) {
            val r = queue.removeFirst()
            queued[r] = false
            val cells = runs[r]
            // Vergebene Ziffern (Einzelkandidaten) sammeln und bei anderen streichen.
            var singles = 0
            for (c in cells) {
                val d = dom[c]
                if (d.countOneBits() == 1) {
                    if (singles and d != 0) return false
                    singles = singles or d
                }
            }
            var openUnion = 0
            var openCount = 0
            var newSingle = false
            for (c in cells) {
                var d = dom[c]
                if (d.countOneBits() == 1) continue
                val nd = d and singles.inv()
                if (nd != d) {
                    if (nd == 0) return false
                    dom[c] = nd
                    d = nd
                    changed(c)
                    if (nd.countOneBits() == 1) newSingle = true
                }
                openUnion = openUnion or d
                openCount++
            }
            // Neue Einzelkandidaten: diese Folge ist bereits erneut eingereiht.
            if (newSingle) continue
            // Mögliche Kombinationen bestimmen.
            var allowed = 0
            var required = ALL
            for (combo in combos[r]) {
                if (combo and singles != singles) continue
                val rest = combo and singles.inv()
                if (hiddenSingles && rest and openUnion != rest) continue
                var ok = true
                if (cellCheck) for (c in cells) {
                    val d = dom[c]
                    if (d.countOneBits() != 1 && d and rest == 0) {
                        ok = false
                        break
                    }
                }
                if (!ok) continue
                allowed = allowed or rest
                required = required and rest
            }
            if (allowed == 0) {
                if (openCount > 0) return false
                if (combos[r].none { it == singles }) return false
                continue
            }
            for (c in cells) {
                val d = dom[c]
                if (d.countOneBits() == 1) continue
                val nd = d and allowed
                if (nd != d) {
                    if (nd == 0) return false
                    dom[c] = nd
                    changed(c)
                }
            }
            // Pflichtziffern, die nur noch in eine Zelle passen, dort setzen.
            var req = if (hiddenSingles) required else 0
            while (req != 0) {
                val bit = req.takeLowestOneBit()
                req = req and bit.inv()
                var where = -1
                var count = 0
                for (c in cells) {
                    if (dom[c] and bit != 0) {
                        count++
                        where = c
                    }
                }
                if (count == 0) return false
                if (count == 1 && dom[where] != bit) {
                    dom[where] = bit
                    changed(where)
                }
            }
        }
        return true
    }

    private companion object {
        const val ALL = 0x3FE // Bits 1..9
    }
}
