package de.carstenkeller.logicals.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

enum class KenKenOp(val symbol: String) {
    NONE(""), ADD("+"), SUB("−"), MUL("×"), DIV("÷");

    /** Ergebnis der Operation für vollständig belegte Werte, null wenn nicht anwendbar. */
    fun apply(values: List<Int>): Int? = when (this) {
        NONE -> values.singleOrNull()
        ADD -> values.sum()
        MUL -> values.fold(1) { acc, v -> acc * v }
        SUB -> if (values.size == 2) kotlin.math.abs(values[0] - values[1]) else null
        DIV -> if (values.size == 2) {
            val hi = maxOf(values[0], values[1])
            val lo = minOf(values[0], values[1])
            if (hi % lo == 0) hi / lo else null
        } else {
            null
        }
    }
}

/** Ein Käfig: zusammenhängende Zellen, deren Werte mit [op] [target] ergeben. */
@Serializable
data class KenKenCage(val cells: List<Int>, val op: KenKenOp, val target: Int) {
    val label: String get() = "$target${op.symbol}"
}

/** KenKen: lateinisches Quadrat, unterteilt in Rechenkäfige. */
@Serializable
@SerialName("kenken")
data class KenKenPuzzle(
    val size: Int,
    val cages: List<KenKenCage>,
    val solution: List<Int>,
    val difficulty: Difficulty,
) : Puzzle() {
    override val kind: PuzzleType get() = PuzzleType.KENKEN
    override val width: Int get() = size
    override val height: Int get() = size
    override val maxDigit: Int get() = size
    override val options: PuzzleOptions get() = PuzzleOptions(difficulty, size, size)

    /** Käfig-Index je Zelle. */
    val cageOf: IntArray by lazy {
        IntArray(size * size).also { arr -> cages.forEachIndexed { k, cage -> cage.cells.forEach { arr[it] = k } } }
    }

    override fun isEditable(index: Int): Boolean = true

    override fun initialEntries(): IntArray = IntArray(size * size)

    override fun conflicts(entries: IntArray): Set<Int> {
        val result = HashSet<Int>()
        latinConflicts(size, entries, result)
        for (cage in cages) {
            val values = cage.cells.map { entries[it] }
            if (values.any { it == 0 }) continue
            if (cage.op.apply(values) != cage.target) result += cage.cells
        }
        return result
    }
}

/**
 * Alle Wertetupel (in Reihenfolge von [KenKenCage.cells]), die das Rechenziel erfüllen
 * und keine Ziffer doppelt in einer Zeile oder Spalte des Käfigs haben.
 */
internal fun kenkenTuples(cage: KenKenCage, n: Int): List<IntArray> {
    val cells = cage.cells
    val result = ArrayList<IntArray>()
    val current = IntArray(cells.size)
    fun rec(p: Int) {
        if (p == cells.size) {
            if (cage.op.apply(current.toList()) == cage.target) result += current.copyOf()
            return
        }
        for (v in 1..n) {
            var ok = true
            for (q in 0 until p) {
                val sameLine = cells[q] / n == cells[p] / n || cells[q] % n == cells[p] % n
                if (sameLine && current[q] == v) {
                    ok = false
                    break
                }
            }
            if (!ok) continue
            current[p] = v
            rec(p + 1)
        }
    }
    rec(0)
    return result
}

/** Zählt Lösungen eines KenKen: Pro Käfig werden alle passenden Wertetupel vorberechnet. */
internal class KenKenSolver(private val n: Int, private val cages: List<KenKenCage>) {
    class Result(val count: Int, val solutions: List<IntArray>, val aborted: Boolean)

    private val cageOf = IntArray(n * n).also { arr -> cages.forEachIndexed { k, c -> c.cells.forEach { arr[it] = k } } }
    private val posInCage = IntArray(n * n).also { arr -> cages.forEach { c -> c.cells.forEachIndexed { p, cell -> arr[cell] = p } } }
    private val tuples: List<List<IntArray>> = cages.map { kenkenTuples(it, n) }

    private val grid = IntArray(n * n)
    private val rows = IntArray(n)
    private val cols = IntArray(n)
    private val found = ArrayList<IntArray>()
    private var nodes = 0L
    private var nodeLimit = 0L
    private var aborted = false

    fun solve(limit: Int = 2, nodeLimit: Long = 300_000L): Result {
        grid.fill(0)
        rows.fill(0)
        cols.fill(0)
        found.clear()
        nodes = 0
        aborted = false
        this.nodeLimit = nodeLimit
        if (tuples.any { it.isEmpty() }) return Result(0, emptyList(), false)
        search(limit)
        return Result(found.size, found.toList(), aborted)
    }

    /** Werte für Zelle [i], die mit den bereits gesetzten Käfigzellen verträglich sind. */
    private fun cageMask(i: Int): Int {
        val k = cageOf[i]
        val cells = cages[k].cells
        val p = posInCage[i]
        var mask = 0
        outer@ for (t in tuples[k]) {
            for (q in cells.indices) {
                val v = grid[cells[q]]
                if (v != 0 && t[q] != v) continue@outer
            }
            mask = mask or (1 shl t[p])
        }
        return mask
    }

    private fun search(limit: Int): Boolean {
        if (++nodes > nodeLimit) {
            aborted = true
            return true
        }
        var best = -1
        var bestMask = 0
        var bestCount = Int.MAX_VALUE
        for (i in grid.indices) {
            if (grid[i] != 0) continue
            val m = cageMask(i) and (rows[i / n] or cols[i % n]).inv()
            val cnt = Integer.bitCount(m)
            if (cnt == 0) return false
            if (cnt < bestCount) {
                best = i
                bestMask = m
                bestCount = cnt
                if (cnt == 1) break
            }
        }
        if (best == -1) {
            found += grid.copyOf()
            return found.size >= limit
        }
        val r = best / n
        val c = best % n
        for (d in 1..n) {
            val bit = 1 shl d
            if (bestMask and bit == 0) continue
            grid[best] = d
            rows[r] = rows[r] or bit
            cols[c] = cols[c] or bit
            val stop = search(limit)
            rows[r] = rows[r] and bit.inv()
            cols[c] = cols[c] and bit.inv()
            grid[best] = 0
            if (stop) return true
        }
        return false
    }
}

/**
 * Erzeugt KenKens mit eindeutiger Lösung.
 *
 * Ablauf: zufälliges lateinisches Quadrat → zufällige zusammenhängende Käfige →
 * passende Rechenarten wählen → solange die erklärbaren Hinweis-Schritte nicht alles lösen,
 * am Feld, an dem sie hängen bleiben, die Rechenart ändern oder den Käfig teilen. Die Schwierigkeit steuert Käfiggröße und
 * Anteil an Einzelfeldern (heuristisch).
 */
class KenKenGenerator(private val random: Random = Random.Default) {

    fun generate(size: Int, difficulty: Difficulty): KenKenPuzzle {
        while (true) {
            attempt(size, difficulty)?.let { return it }
        }
    }

    private fun attempt(n: Int, difficulty: Difficulty): KenKenPuzzle? {
        val solution = randomLatinSquare(n, random)
        val maxCage = when (difficulty) {
            Difficulty.EASY -> 3
            Difficulty.MEDIUM -> 3
            Difficulty.HARD -> 4
        }
        val singleChance = when (difficulty) {
            Difficulty.EASY -> 0.15
            Difficulty.MEDIUM -> 0.07
            Difficulty.HARD -> 0.03
        }
        val groups = partition(n, maxCage, singleChance)
        val cages = groups.map { makeCage(it, solution, difficulty) }.toMutableList()

        repeat(n * n * 2) {
            val candidate = KenKenPuzzle(n, cages.toList(), solution.toList(), difficulty)
            // Fertig, wenn die erklärbaren Hinweis-Schritte alles lösen (dann ist es auch eindeutig).
            val cell = logicalStuckCell(candidate)
            if (cell == -1) return candidate
            val k = cages.indexOfFirst { cell in it.cells }
            val cage = cages[k]
            val alternatives = opsFor(cage.cells.map { solution[it] }).filter { it != cage.op }
            if (cage.cells.size > 1 && (alternatives.isEmpty() || random.nextBoolean())) {
                // Käfig teilen: die Zelle herauslösen, Rest in zusammenhängende Teile zerlegen.
                cages.removeAt(k)
                cages += makeCage(listOf(cell), solution, difficulty)
                for (part in components(cage.cells - cell, n)) cages += makeCage(part, solution, difficulty)
            } else if (alternatives.isNotEmpty()) {
                val op = alternatives.random(random)
                cages[k] = KenKenCage(cage.cells, op, op.apply(cage.cells.map { solution[it] })!!)
            } else {
                return null
            }
        }
        return null
    }

    /** Zufällige Zerlegung in zusammenhängende Gruppen. */
    private fun partition(n: Int, maxCage: Int, singleChance: Double): List<List<Int>> {
        val owner = IntArray(n * n) { -1 }
        val groups = ArrayList<MutableList<Int>>()
        for (start in (0 until n * n).shuffled(random)) {
            if (owner[start] != -1) continue
            val target = if (random.nextDouble() < singleChance) 1 else 2 + random.nextInt(maxCage - 1)
            val group = mutableListOf(start)
            owner[start] = groups.size
            while (group.size < target) {
                val frontier = group.flatMap { neighbors(it, n) }.filter { owner[it] == -1 }.distinct()
                if (frontier.isEmpty()) break
                val next = frontier.random(random)
                owner[next] = groups.size
                group += next
            }
            groups += group
        }
        // Eingeschlossene Restzellen bleiben als Einzelfelder bestehen.
        return groups.map { it.sorted() }
    }

    private fun neighbors(i: Int, n: Int): List<Int> = buildList {
        val r = i / n
        val c = i % n
        if (r > 0) add(i - n)
        if (r < n - 1) add(i + n)
        if (c > 0) add(i - 1)
        if (c < n - 1) add(i + 1)
    }

    private fun components(cells: List<Int>, n: Int): List<List<Int>> {
        val remaining = cells.toMutableSet()
        val result = ArrayList<List<Int>>()
        while (remaining.isNotEmpty()) {
            val start = remaining.first()
            val comp = mutableListOf(start)
            remaining -= start
            var idx = 0
            while (idx < comp.size) {
                for (nb in neighbors(comp[idx], n)) {
                    if (nb in remaining) {
                        remaining -= nb
                        comp += nb
                    }
                }
                idx++
            }
            result += comp.sorted()
        }
        return result
    }

    private fun opsFor(values: List<Int>): List<KenKenOp> = when (values.size) {
        1 -> listOf(KenKenOp.NONE)
        2 -> buildList {
            add(KenKenOp.ADD)
            add(KenKenOp.MUL)
            add(KenKenOp.SUB)
            if (KenKenOp.DIV.apply(values) != null) add(KenKenOp.DIV)
        }
        else -> listOf(KenKenOp.ADD, KenKenOp.MUL)
    }

    private fun makeCage(cells: List<Int>, solution: IntArray, difficulty: Difficulty): KenKenCage {
        val values = cells.map { solution[it] }
        val ops = opsFor(values)
        val op = when {
            ops.size == 1 -> ops[0]
            // Leichte Rätsel bevorzugen Addition, schwerere mischen alle Rechenarten.
            difficulty == Difficulty.EASY && random.nextDouble() < 0.6 -> KenKenOp.ADD
            KenKenOp.DIV in ops && random.nextDouble() < 0.5 -> KenKenOp.DIV
            else -> ops.random(random)
        }
        return KenKenCage(cells.sorted(), op, op.apply(values)!!)
    }
}
