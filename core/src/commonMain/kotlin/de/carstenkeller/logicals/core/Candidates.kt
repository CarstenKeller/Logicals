package de.carstenkeller.logicals.core

/**
 * Mögliche Ziffern für ein Feld im "Einfach"-Modus.
 *
 * Ausgeschlossen wird nur, was direkt aus den eingetragenen Zahlen und den Regeln folgt –
 * keine mehrstufigen Schlussketten (sonst würde der Modus das Rätsel selbst lösen).
 */
object Candidates {

    /** Bitmaske (Bit n = Ziffer n) der möglichen Ziffern; 0 für nicht editierbare Felder. */
    fun of(puzzle: Puzzle, entries: IntArray, index: Int): Int {
        if (!puzzle.isEditable(index)) return 0
        val all = ((1 shl (puzzle.maxDigit + 1)) - 1) and 1.inv()
        return when (puzzle) {
            is SudokuPuzzle -> all and sudokuTaken(entries, index).inv()
            is FutoshikiPuzzle -> futoshiki(puzzle, entries, index, all)
            is KenKenPuzzle -> kenken(puzzle, entries, index, all)
            is SkyscraperPuzzle -> skyscraper(puzzle, entries, index, all)
            is KakuroPuzzle -> kakuro(puzzle, entries, index)
            is CatsweeperPuzzle -> 0
        }
    }

    private fun sudokuTaken(entries: IntArray, index: Int): Int {
        var m = 0
        for (unit in SudokuPuzzle.UNITS) {
            if (index !in unit) continue
            for (c in unit) if (c != index && entries[c] != 0) m = m or (1 shl entries[c])
        }
        return m
    }

    /** Ziffern, die in Zeile oder Spalte eines n×n-Gitters schon stehen. */
    private fun latinTaken(n: Int, entries: IntArray, index: Int): Int {
        val r = index / n
        val c = index % n
        var m = 0
        for (k in 0 until n) {
            val rowCell = r * n + k
            val colCell = k * n + c
            if (rowCell != index && entries[rowCell] != 0) m = m or (1 shl entries[rowCell])
            if (colCell != index && entries[colCell] != 0) m = m or (1 shl entries[colCell])
        }
        return m
    }

    private fun futoshiki(p: FutoshikiPuzzle, entries: IntArray, index: Int, all: Int): Int {
        val n = p.size
        var mask = all and latinTaken(n, entries, index).inv()
        val r = index / n
        val c = index % n

        /** Diese Zelle muss kleiner ([smaller]) bzw. größer sein als [neighbor]. */
        fun restrict(neighbor: Int, smaller: Boolean) {
            val v = entries[neighbor]
            var allowed = 0
            for (d in 1..n) {
                val ok = when {
                    v != 0 -> if (smaller) d < v else d > v
                    else -> if (smaller) d < n else d > 1
                }
                if (ok) allowed = allowed or (1 shl d)
            }
            mask = mask and allowed
        }
        if (c < n - 1 && p.right[index] != 0) restrict(index + 1, p.right[index] == FutoshikiPuzzle.LESS)
        if (c > 0 && p.right[index - 1] != 0) restrict(index - 1, p.right[index - 1] == FutoshikiPuzzle.GREATER)
        if (r < n - 1 && p.down[index] != 0) restrict(index + n, p.down[index] == FutoshikiPuzzle.LESS)
        if (r > 0 && p.down[index - n] != 0) restrict(index - n, p.down[index - n] == FutoshikiPuzzle.GREATER)
        return mask
    }

    private fun kenken(p: KenKenPuzzle, entries: IntArray, index: Int, all: Int): Int {
        val n = p.size
        val cage = p.cages[p.cageOf[index]]
        val pos = cage.cells.indexOf(index)
        var cageMask = 0
        for (t in kenkenTuples(cage, n)) {
            val fits = cage.cells.indices.all { q -> q == pos || entries[cage.cells[q]] == 0 || entries[cage.cells[q]] == t[q] }
            if (fits) cageMask = cageMask or (1 shl t[pos])
        }
        return all and latinTaken(n, entries, index).inv() and cageMask
    }

    private fun skyscraper(p: SkyscraperPuzzle, entries: IntArray, index: Int, all: Int): Int {
        val n = p.size
        var mask = all and latinTaken(n, entries, index).inv()
        for (line in p.lines) {
            val pos = line.cells.indexOf(index)
            if (pos < 0 || !line.hasClue) continue
            // Nur Ziffern, mit denen sich die Linie passend zu Hinweisen und Einträgen füllen lässt.
            var allowed = 0
            val current = IntArray(n)
            fun rec(k: Int, used: Int) {
                if (k == n) {
                    if (line.accepts(current.toList())) allowed = allowed or (1 shl current[pos])
                    return
                }
                val fixed = if (line.cells[k] == index) 0 else entries[line.cells[k]]
                for (d in 1..n) {
                    val bit = 1 shl d
                    if (used and bit != 0 || (fixed != 0 && fixed != d)) continue
                    if (k == pos && mask and bit == 0) continue
                    current[k] = d
                    rec(k + 1, used or bit)
                }
            }
            rec(0, 0)
            mask = mask and allowed
        }
        return mask
    }

    private fun kakuro(p: KakuroPuzzle, entries: IntArray, index: Int): Int {
        val geometry = p.geometry
        var mask = 0x3FE
        for (runId in intArrayOf(geometry.acrossRunOf[index], geometry.downRunOf[index])) {
            val run = geometry.runs[runId]
            var placed = 0
            for (c in run.cells) if (c != index && entries[c] != 0) placed = placed or (1 shl entries[c])
            var allowed = 0
            for (combo in KakuroCombos.of(run.cells.size, p.sumOf(run))) {
                if (combo and placed == placed) allowed = allowed or combo
            }
            mask = mask and allowed and placed.inv()
        }
        return mask
    }
}
