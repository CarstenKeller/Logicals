package de.carstenkeller.logicals.core

import kotlin.random.Random

/**
 * Erzeugt Kakuros in beliebiger Größe.
 *
 * [width] und [height] zählen das gesamte Gitter inklusive der obersten Zeile und
 * linken Spalte, die nur Summenfelder enthalten.
 *
 * Ablauf: Gitterstruktur würfeln und reparieren → zufällig mit Ziffern füllen →
 * Ziffern per lokaler Suche so verändern, dass das Rätsel allein durch logisches
 * Schließen lösbar ist (damit ist die Lösung eindeutig). Gelingt das nicht innerhalb
 * des Zeitbudgets, wird das zuletzt erzeugte, regelkonforme Rätsel mit
 * `unique = false` geliefert. Die Lösungsprüfung der App arbeitet regelbasiert, daher
 * wird auch dann jede korrekte Lösung anerkannt.
 */
class KakuroGenerator(private val random: Random = Random.Default) {

    class Generated(val puzzle: KakuroPuzzle, val unique: Boolean)

    private var level = Difficulty.HARD
    private var puzzleDifficulty = Difficulty.HARD
    private var maxRun = 9
    private var preferredRun = 6

    fun generate(
        width: Int,
        height: Int,
        difficulty: Difficulty = Difficulty.HARD,
        timeBudgetMillis: Long = 20_000L,
    ): Generated {
        // Leicht: nur einfache Schlüsse nötig. Mittel und Schwer: alle Techniken; Mittel
        // aber mit kürzeren Folgen (weniger Kombinationen je Summe).
        level = if (difficulty == Difficulty.EASY) Difficulty.EASY else Difficulty.HARD
        puzzleDifficulty = difficulty
        // Kürzere Folgen haben weniger Kombinationen und sind leichter zu durchschauen.
        when (difficulty) {
            Difficulty.EASY -> { maxRun = 6; preferredRun = 6 }
            Difficulty.MEDIUM -> { maxRun = 6; preferredRun = 6 }
            Difficulty.HARD -> { maxRun = 9; preferredRun = 6 }
        }
        require(width in MIN_SIZE..MAX_SIZE && height in MIN_SIZE..MAX_SIZE) {
            "Größe muss zwischen $MIN_SIZE und $MAX_SIZE liegen"
        }
        val deadline = System.nanoTime() + timeBudgetMillis * 1_000_000L
        var fallback: KakuroPuzzle? = null
        while (true) {
            val white = layout(width, height)
            if (white != null) {
                val geometry = KakuroGeometry(width, height, white)
                val fill = fill(geometry)
                if (fill != null) {
                    val (puzzle, unique) = makeUnique(geometry, fill, deadline)
                    if (unique) return Generated(puzzle, true)
                    fallback = puzzle
                }
            }
            if (System.nanoTime() > deadline) {
                if (fallback != null) return Generated(fallback.copy(unique = false), false)
                // Notbremse: die Struktur des schweren Modus gelingt praktisch immer.
                maxRun = 9
                preferredRun = 6
            }
        }
    }

    // ---------------------------------------------------------------- Struktur

    private fun layout(w: Int, h: Int): BooleanArray? {
        val white = BooleanArray(w * h)
        for (r in 1 until h) for (c in 1 until w) white[r * w + c] = true
        val interior = (w - 1) * (h - 1)
        val density = 0.16 + random.nextDouble() * 0.08
        // Punktsymmetrisch (180°) schwarze Felder setzen.
        for (r in 1 until h) for (c in 1 until w) {
            val i = r * w + c
            val j = (h - r) * w + (w - c)
            if (i > j) continue
            if (random.nextDouble() < density) {
                white[i] = false
                white[j] = false
            }
        }
        repeat(w * h * 4) {
            if (!repairStep(w, h, white)) {
                val count = white.count { it }
                return if (count >= interior * 0.4 && count >= 4) white else null
            }
        }
        return null
    }

    /** Führt eine Reparatur aus. @return false, wenn die Struktur bereits gültig ist. */
    private fun repairStep(w: Int, h: Int, white: BooleanArray): Boolean {
        val geometry = KakuroGeometry(w, h, white)
        // Zu lange Folgen teilen.
        for (run in geometry.runs) {
            val len = run.cells.size
            if (len >= 5 && (len > maxRun || (len > preferredRun && random.nextInt(3) == 0))) {
                val pos = 2 + random.nextInt(len - 4)
                white[run.cells[pos]] = false
                return true
            }
        }
        // Folgen der Länge 1 beseitigen: Nachbarfeld weiß machen oder Zelle schwärzen.
        for (run in geometry.runs) {
            if (run.cells.size != 1) continue
            val cell = run.cells[0]
            val r = cell / w
            val c = cell % w
            val candidates = if (run.horizontal) {
                listOf(r to c - 1, r to c + 1)
            } else {
                listOf(r - 1 to c, r + 1 to c)
            }.filter { (rr, cc) -> rr in 1 until h && cc in 1 until w && !white[rr * w + cc] }
            if (candidates.isNotEmpty() && random.nextBoolean()) {
                val (rr, cc) = candidates.random(random)
                white[rr * w + cc] = true
            } else {
                white[cell] = false
            }
            return true
        }
        // Nur die größte zusammenhängende weiße Fläche behalten.
        val component = IntArray(w * h) { -1 }
        val sizes = ArrayList<Int>()
        for (start in white.indices) {
            if (!white[start] || component[start] != -1) continue
            val id = sizes.size
            var size = 0
            val stack = ArrayDeque<Int>()
            stack.addLast(start)
            component[start] = id
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                size++
                val r = cur / w
                val c = cur % w
                for ((rr, cc) in arrayOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)) {
                    if (rr !in 0 until h || cc !in 0 until w) continue
                    val n = rr * w + cc
                    if (white[n] && component[n] == -1) {
                        component[n] = id
                        stack.addLast(n)
                    }
                }
            }
            sizes += size
        }
        if (sizes.size > 1) {
            val keep = sizes.indices.maxBy { sizes[it] }
            for (i in white.indices) if (white[i] && component[i] != keep) white[i] = false
            return true
        }
        return false
    }

    // ---------------------------------------------------------------- Füllen

    private fun fill(geometry: KakuroGeometry): IntArray? {
        val values = IntArray(geometry.white.size)
        val used = IntArray(geometry.runs.size)
        val cells = geometry.white.indices.filter { geometry.white[it] }
        var nodes = 0

        fun recurse(): Boolean {
            if (++nodes > 200_000) return false
            var best = -1
            var bestMask = 0
            var bestCount = 10
            for (cell in cells) {
                if (values[cell] != 0) continue
                val mask = ALL and (used[geometry.acrossRunOf[cell]] or used[geometry.downRunOf[cell]]).inv()
                val n = Integer.bitCount(mask)
                if (n == 0) return false
                if (n < bestCount) {
                    best = cell
                    bestMask = mask
                    bestCount = n
                }
            }
            if (best == -1) return true
            val h = geometry.acrossRunOf[best]
            val v = geometry.downRunOf[best]
            for (d in (1..9).filter { bestMask and (1 shl it) != 0 }.shuffled(random)) {
                val bit = 1 shl d
                values[best] = d
                used[h] = used[h] or bit
                used[v] = used[v] or bit
                if (recurse()) return true
                used[h] = used[h] and bit.inv()
                used[v] = used[v] and bit.inv()
                values[best] = 0
            }
            return false
        }

        return if (recurse()) values else null
    }

    // ---------------------------------------------------------------- Eindeutigkeit

    /**
     * Lokale Suche: Ziffern werden so lange verändert, bis das Rätsel allein durch
     * logisches Schließen ([KakuroSolver.deduce]) vollständig lösbar ist. Ein so lösbares
     * Rätsel hat genau eine Lösung und kommt ohne Raten aus.
     */
    private fun makeUnique(geometry: KakuroGeometry, fill: IntArray, deadline: Long): Pair<KakuroPuzzle, Boolean> {
        val whiteCells = geometry.white.indices.filter { geometry.white[it] }
        var unresolved = unresolvedCells(geometry, fill)
        var stale = 0
        while (unresolved.isNotEmpty()) {
            if (System.nanoTime() > deadline || stale > MAX_STALE_STEPS) {
                return build(geometry, fill, sums(geometry, fill)) to isUnique(geometry, fill)
            }
            val backup = fill.copyOf()
            // Meist an einer noch unbestimmten Zelle ansetzen, gelegentlich irgendwo.
            val cell = if (random.nextInt(4) != 0) unresolved.random(random) else whiteCells.random(random)
            if (!mutate(geometry, fill, cell)) {
                stale++
                continue
            }
            val next = unresolvedCells(geometry, fill)
            if (next.size <= unresolved.size) {
                if (next.size < unresolved.size) stale = 0 else stale++
                unresolved = next
            } else {
                backup.copyInto(fill)
                stale++
            }
        }
        return build(geometry, fill, sums(geometry, fill)) to true
    }

    /** Zellen, die logisches Schließen nicht eindeutig bestimmt. */
    private fun unresolvedCells(geometry: KakuroGeometry, fill: IntArray): List<Int> {
        val domains = KakuroSolver(geometry, sums(geometry, fill)).deduce(level)
            ?: error("Propagation widerspricht der eigenen Lösung")
        return geometry.white.indices.filter { geometry.white[it] && Integer.bitCount(domains[it]) != 1 }
    }

    /** Prüft per Suche, ob das (nicht rein logisch lösbare) Rätsel trotzdem eindeutig ist. */
    private fun isUnique(geometry: KakuroGeometry, fill: IntArray): Boolean {
        val result = KakuroSolver(geometry, sums(geometry, fill)).solve(limit = 2, nodeLimit = 20_000L)
        return !result.aborted && result.count == 1
    }

    /**
     * Ändert die Lösung an [cell]: entweder eine neue, in beiden Folgen freie Ziffer
     * oder Tausch mit einer anderen Zelle derselben Folge (falls regelkonform).
     */
    private fun mutate(geometry: KakuroGeometry, fill: IntArray, cell: Int): Boolean {
        if (random.nextBoolean()) {
            var usedMask = 0
            for (c in geometry.runs[geometry.acrossRunOf[cell]].cells) usedMask = usedMask or (1 shl fill[c])
            for (c in geometry.runs[geometry.downRunOf[cell]].cells) usedMask = usedMask or (1 shl fill[c])
            val options = (1..9).filter { usedMask and (1 shl it) == 0 }
            if (options.isNotEmpty()) {
                fill[cell] = options.random(random)
                return true
            }
        }
        val horizontal = random.nextBoolean()
        val run = geometry.runs[if (horizontal) geometry.acrossRunOf[cell] else geometry.downRunOf[cell]]
        val other = run.cells.filter { it != cell }.random(random)
        // Beim Tausch innerhalb einer Folge bleibt diese gültig; die Querfolgen prüfen.
        val crossOf = if (horizontal) geometry.downRunOf else geometry.acrossRunOf
        val a = fill[cell]
        val b = fill[other]
        if (geometry.runs[crossOf[cell]].cells.any { it != cell && fill[it] == b }) return false
        if (geometry.runs[crossOf[other]].cells.any { it != other && fill[it] == a }) return false
        fill[cell] = b
        fill[other] = a
        return true
    }

    private fun sums(geometry: KakuroGeometry, fill: IntArray): IntArray =
        IntArray(geometry.runs.size) { r -> geometry.runs[r].cells.sumOf { fill[it] } }

    private fun build(geometry: KakuroGeometry, fill: IntArray, sums: IntArray): KakuroPuzzle {
        val across = IntArray(fill.size)
        val down = IntArray(fill.size)
        geometry.runs.forEachIndexed { i, run ->
            if (run.horizontal) across[run.clueIndex] = sums[i] else down[run.clueIndex] = sums[i]
        }
        val cells = List(fill.size) { i ->
            if (geometry.white[i]) KakuroCell(white = true) else KakuroCell(false, across[i], down[i])
        }
        return KakuroPuzzle(geometry.width, geometry.height, cells, fill.toList(), difficulty = puzzleDifficulty)
    }

    companion object {
        val MIN_SIZE = PuzzleType.KAKURO.sizeRange!!.first
        val MAX_SIZE = PuzzleType.KAKURO.sizeRange!!.last
        private const val MAX_STALE_STEPS = 20_000
        private const val ALL = 0x3FE
    }
}
