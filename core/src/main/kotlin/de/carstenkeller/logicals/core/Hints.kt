package de.carstenkeller.logicals.core

/** Was der Hinweis vorschlägt. */
enum class HintAction { PLACE, CLEAR, REVEAL, MARK, UNMARK }

/**
 * Ein Hinweis: ein Feld, eine Aktion und eine Erklärung, wie man selbst darauf kommt.
 *
 * @property value einzutragende Ziffer bei [HintAction.PLACE]
 * @property steps Erklärung in einzelnen Sätzen/Schritten
 * @property related Felder, die für die Erklärung hervorgehoben werden
 */
data class Hint(
    val cell: Int,
    val action: HintAction,
    val value: Int = 0,
    val title: String,
    val steps: List<String>,
    val related: Set<Int> = emptySet(),
)

object HintFinder {
    /** Nächster sinnvoller Schritt für den aktuellen Stand, null wenn das Spiel vorbei ist. */
    fun find(puzzle: Puzzle, entries: IntArray): Hint? {
        if (puzzle.isSolved(entries) || puzzle.isLost(entries)) return null
        return when (puzzle) {
            is SudokuPuzzle -> SudokuHinter(puzzle, entries).find()
            is KakuroPuzzle -> KakuroHinter(puzzle, entries).find()
            is FutoshikiPuzzle -> FutoshikiHinter(puzzle, entries).find()
            is KenKenPuzzle -> KenKenHinter(puzzle, entries).find()
            is CatsweeperPuzzle -> CatsweeperHinter(puzzle, entries).find()
        }
    }
}

/**
 * Prüft, ob sich ein Zahlenrätsel allein mit den erklärbaren Hinweis-Schritten lösen lässt.
 * @return -1 wenn ja, sonst ein Feld, an dem die Logik hängen bleibt.
 */
internal fun logicalStuckCell(puzzle: Puzzle): Int {
    val entries = puzzle.initialEntries()
    return when (puzzle) {
        is SudokuPuzzle -> SudokuHinter(puzzle, entries).solveLogically()
        is KakuroPuzzle -> KakuroHinter(puzzle, entries).solveLogically()
        is FutoshikiPuzzle -> FutoshikiHinter(puzzle, entries).solveLogically()
        is KenKenPuzzle -> KenKenHinter(puzzle, entries).solveLogically()
        is CatsweeperPuzzle -> -1
    }
}

internal fun cellName(index: Int, width: Int) = "Zeile ${index / width + 1}, Spalte ${index % width + 1}"

internal fun digitList(mask: Int): String {
    val digits = KakuroCombos.digits(mask)
    return if (digits.size <= 1) digits.joinToString("") else
        digits.dropLast(1).joinToString(", ") + " und " + digits.last()
}

// ====================================================================== Zahlenrätsel

/** Eine Gruppe von Feldern mit verschiedenen Ziffern; [complete]: jede Ziffer genau einmal. */
internal class HintUnit(val name: String, val cells: IntArray, val complete: Boolean)

internal sealed class HintStep {
    class Place(
        val cell: Int,
        val digit: Int,
        val title: String,
        val lines: List<String>,
        val related: Set<Int>,
    ) : HintStep()

    /** Streicht Kandidaten: Zelle → Bitmaske der entfernten Ziffern. */
    class Eliminate(val removals: Map<Int, Int>, val text: String, val related: Set<Int>) : HintStep()
}

/**
 * Gemeinsame Hinweis-Logik für Zahlenrätsel.
 *
 * Kandidaten werden aus den eingetragenen Zahlen berechnet (nicht aus den Notizen).
 * Gesucht wird in einfachen Schritten: versteckter Einzelner, nackter Einzelner,
 * rätselspezifische Ausschlüsse und nackte Paare. Ausschlüsse werden gesammelt, bis
 * sich ein Feld eindeutig füllen lässt; die dafür relevanten Ausschlüsse erscheinen in
 * der Erklärung.
 */
internal abstract class NumberHinter(
    protected val puzzle: Puzzle,
    protected val entries: IntArray,
    protected val units: List<HintUnit>,
    /** Referenzlösung für die Fehlerprüfung, null wenn sie nicht eindeutig ist. */
    private val checkSolution: List<Int>?,
    /** Lösung für den Notfall-Tipp. */
    private val fallbackSolution: List<Int>,
) {
    protected val width = puzzle.width
    protected val all: Int = ((1 shl (puzzle.maxDigit + 1)) - 1) and 1.inv()
    private val elim = IntArray(puzzle.cellCount)
    private val history = ArrayList<HintStep.Eliminate>()
    protected val unitsOf: Array<MutableList<HintUnit>> = Array(puzzle.cellCount) { mutableListOf() }

    init {
        for (u in units) for (c in u.cells) unitsOf[c] += u
    }

    protected open fun techniques(): List<() -> HintStep?> = emptyList()

    protected fun isOpen(i: Int) = puzzle.isEditable(i) && entries[i] == 0
    protected fun name(i: Int) = cellName(i, width)

    protected fun unitDigits(u: HintUnit, except: Int = -1): Int {
        var m = 0
        for (c in u.cells) if (c != except && entries[c] != 0) m = m or (1 shl entries[c])
        return m
    }

    /** Kandidaten eines offenen Felds; für gefüllte Felder deren Ziffer. */
    protected fun cand(i: Int): Int {
        if (!isOpen(i)) return if (entries[i] != 0) 1 shl entries[i] else 0
        var m = all and elim[i].inv()
        for (u in unitsOf[i]) m = m and unitDigits(u, i).inv()
        return m
    }

    /** Erstellt einen Ausschluss-Schritt nur, wenn er tatsächlich etwas streicht. */
    protected fun eliminate(removals: Map<Int, Int>, text: String, related: Set<Int>): HintStep.Eliminate? {
        val effective = removals.mapValues { (c, m) -> m and cand(c) }.filterValues { it != 0 }
        return if (effective.isEmpty()) null else HintStep.Eliminate(effective, text, related)
    }

    fun find(): Hint? {
        mistake()?.let { return it }
        val cells = 0 until puzzle.cellCount
        if (cells.none { isOpen(it) }) return null
        repeat(300) {
            if (cells.any { isOpen(it) && cand(it) == 0 }) return fallback()
            val step = hiddenSingle()
                ?: nakedSingle()
                ?: techniques().firstNotNullOfOrNull { it() }
                ?: nakedPair()
                ?: return fallback()
            when (step) {
                is HintStep.Place ->
                    return Hint(step.cell, HintAction.PLACE, step.digit, step.title, step.lines, step.related + step.cell)
                is HintStep.Eliminate -> {
                    for ((c, m) in step.removals) elim[c] = elim[c] or m
                    history += step
                }
            }
        }
        return fallback()
    }

    /**
     * Löst ausschließlich mit den erklärbaren Schritten (ändert [entries]).
     * @return -1 wenn vollständig gelöst, sonst ein offenes Feld, an dem es nicht weitergeht.
     */
    fun solveLogically(): Int {
        val cells = 0 until puzzle.cellCount
        repeat(puzzle.cellCount * 30) {
            val open = cells.filter { isOpen(it) }
            if (open.isEmpty()) return -1
            open.firstOrNull { cand(it) == 0 }?.let { return it }
            val step = hiddenSingle()
                ?: nakedSingle()
                ?: techniques().firstNotNullOfOrNull { it() }
                ?: nakedPair()
                ?: return open.minBy { Integer.bitCount(cand(it)) }
            when (step) {
                is HintStep.Place -> entries[step.cell] = step.digit
                is HintStep.Eliminate -> for ((c, m) in step.removals) elim[c] = elim[c] or m
            }
        }
        return cells.firstOrNull { isOpen(it) } ?: -1
    }

    // ------------------------------------------------------------------ Fehler

    private fun mistake(): Hint? {
        val conflicts = puzzle.conflicts(entries)
        val wrongByRule = (0 until puzzle.cellCount).firstOrNull {
            puzzle.isEditable(it) && entries[it] != 0 && it in conflicts
        }
        if (wrongByRule != null) {
            return Hint(
                wrongByRule, HintAction.CLEAR, title = "Hier stimmt etwas nicht",
                steps = listOf(
                    "Die ${entries[wrongByRule]} in diesem Feld verstößt gegen eine Regel (rot markiert).",
                    "Lösche sie und überlege dieses Feld neu.",
                ),
                related = conflicts,
            )
        }
        val sol = checkSolution ?: return null
        val wrong = (0 until puzzle.cellCount).firstOrNull {
            puzzle.isEditable(it) && entries[it] != 0 && entries[it] != sol[it]
        } ?: return null
        return Hint(
            wrong, HintAction.CLEAR, title = "Hier stimmt etwas nicht",
            steps = listOf(
                "Die ${entries[wrong]} passt zwar zu den bisher eingetragenen Zahlen, ist aber nicht richtig.",
                "Weiter hinten führt sie zu einem Widerspruch – lösche sie lieber jetzt.",
            ),
            related = setOf(wrong),
        )
    }

    private fun fallback(): Hint {
        val open = (0 until puzzle.cellCount).filter { isOpen(it) }
        val target = open.minBy { Integer.bitCount(cand(it)).let { n -> if (n == 0) 99 else n } }
        return Hint(
            target, HintAction.PLACE, fallbackSolution[target],
            title = "Tipp ohne Herleitung",
            steps = listOf(
                "Mit den Lösungsschritten, die ich erklären kann, finde ich gerade keinen weiteren sicheren Schluss.",
                "Hier gehört die ${fallbackSolution[target]} hin. Darauf kommt man nur mit einer " +
                    "fortgeschritteneren Überlegung oder indem man mehrere Schritte vorausdenkt.",
            ),
        )
    }

    // ------------------------------------------------------------------ Grundtechniken

    private fun relevantFor(cell: Int): List<HintStep.Eliminate> =
        history.filter { (it.removals[cell] ?: 0) != 0 }

    private fun nakedSingle(): HintStep.Place? {
        for (i in 0 until puzzle.cellCount) {
            if (!isOpen(i)) continue
            val m = cand(i)
            if (Integer.bitCount(m) != 1) continue
            val d = Integer.numberOfTrailingZeros(m)
            val lines = ArrayList<String>()
            val taken = unitsOf[i].mapNotNull { u ->
                val ds = unitDigits(u, i)
                if (ds == 0) null else "${u.name}: ${digitList(ds)}"
            }
            if (taken.isNotEmpty()) lines += "Schon vergeben sind – ${taken.joinToString("; ")}."
            val rel = relevantFor(i)
            if (rel.isNotEmpty()) {
                lines += if (taken.isNotEmpty()) "Außerdem fallen durch diese Überlegungen Ziffern weg:"
                else "Durch diese Überlegungen fallen Ziffern weg:"
                rel.forEach { lines += "• ${it.text}" }
            }
            lines += "Für dieses Feld bleibt damit nur die $d."
            val related = unitsOf[i].flatMap { it.cells.toList() }.toSet() + rel.flatMap { it.related }
            return HintStep.Place(i, d, "Nur noch eine Ziffer möglich", lines, related)
        }
        return null
    }

    private fun hiddenSingle(): HintStep.Place? {
        for (u in units) {
            if (!u.complete) continue
            val present = unitDigits(u)
            for (d in 1..puzzle.maxDigit) {
                val bit = 1 shl d
                if (present and bit != 0) continue
                val places = u.cells.filter { isOpen(it) && cand(it) and bit != 0 }
                if (places.size != 1) continue
                val target = places[0]
                // Warum die Ziffer in den anderen freien Feldern nicht geht.
                val blockers = LinkedHashSet<String>()
                val others = u.cells.filter { it != target && isOpen(it) }
                for (c in others) {
                    unitsOf[c].firstOrNull { it !== u && unitDigits(it) and bit != 0 }?.let { blockers += it.name }
                }
                val rel = history.filter { st -> others.any { c -> (st.removals[c] ?: 0) and bit != 0 } }
                val lines = ArrayList<String>()
                lines += "In ${u.name} fehlt noch die $d."
                if (others.isEmpty()) lines += "Dies ist das letzte freie Feld in ${u.name}."
                if (blockers.isNotEmpty()) {
                    lines += "Für die anderen freien Felder dort scheidet sie aus, weil die $d schon in " +
                        "${blockers.joinToString(", ")} steht."
                }
                if (rel.isNotEmpty()) {
                    lines += "Dazu kommen diese Überlegungen:"
                    rel.forEach { lines += "• ${it.text}" }
                }
                lines += "Also bleibt für die $d in ${u.name} nur dieses Feld."
                val related = u.cells.toSet() + rel.flatMap { it.related }
                return HintStep.Place(target, d, "Die $d hat nur einen Platz", lines, related)
            }
        }
        return null
    }

    private fun nakedPair(): HintStep.Eliminate? {
        for (u in units) {
            val pairs = u.cells.filter { isOpen(it) && Integer.bitCount(cand(it)) == 2 }
            for (a in pairs.indices) for (b in a + 1 until pairs.size) {
                val m = cand(pairs[a])
                if (cand(pairs[b]) != m) continue
                val removals = u.cells
                    .filter { it != pairs[a] && it != pairs[b] && isOpen(it) }
                    .associateWith { m }
                val step = eliminate(
                    removals,
                    "In ${u.name} kommen für ${name(pairs[a])} und ${name(pairs[b])} nur ${digitList(m)} " +
                        "in Frage. Diese beiden Ziffern sind dort also vergeben und fallen in den übrigen Feldern weg.",
                    u.cells.toSet(),
                )
                if (step != null) return step
            }
        }
        return null
    }
}

/** Zeilen und Spalten eines n×n-Gitters als Einheiten. */
internal fun latinUnits(n: Int): List<HintUnit> = buildList {
    for (r in 0 until n) add(HintUnit("Zeile ${r + 1}", IntArray(n) { r * n + it }, true))
    for (c in 0 until n) add(HintUnit("Spalte ${c + 1}", IntArray(n) { it * n + c }, true))
}

// ---------------------------------------------------------------------- Sudoku

private val BoxNames = listOf(
    "Block oben links", "Block oben Mitte", "Block oben rechts",
    "Block Mitte links", "Block Mitte", "Block Mitte rechts",
    "Block unten links", "Block unten Mitte", "Block unten rechts",
)

internal class SudokuHinter(p: SudokuPuzzle, entries: IntArray) : NumberHinter(
    p, entries,
    latinUnits(9) + (0 until 9).map { HintUnit(BoxNames[it], SudokuPuzzle.UNITS[18 + it], true) },
    p.solution, p.solution,
) {
    private val rows = units.subList(0, 9)
    private val cols = units.subList(9, 18)
    private val boxes = units.subList(18, 27)

    override fun techniques(): List<() -> HintStep?> = listOf(::pointing, ::claiming)

    /** Liegt eine Ziffer im Block nur in einer Zeile/Spalte, fällt sie dort außerhalb des Blocks weg. */
    private fun pointing(): HintStep? {
        for (box in boxes) for (d in 1..9) {
            val bit = 1 shl d
            val cells = box.cells.filter { isOpen(it) && cand(it) and bit != 0 }
            if (cells.size < 2) continue
            for ((lines, lineOf) in listOf(rows to { c: Int -> c / 9 }, cols to { c: Int -> c % 9 })) {
                val idx = cells.map(lineOf).distinct().singleOrNull() ?: continue
                val line = lines[idx]
                val removals = line.cells.filter { it !in box.cells && isOpen(it) }.associateWith { bit }
                eliminate(
                    removals,
                    "In ${box.name} kann die $d nur in ${line.name} stehen. Deshalb ist die $d in ${line.name} " +
                        "außerhalb dieses Blocks ausgeschlossen.",
                    box.cells.toSet() + line.cells.toSet(),
                )?.let { return it }
            }
        }
        return null
    }

    /** Liegt eine Ziffer in einer Zeile/Spalte nur in einem Block, fällt sie im Rest des Blocks weg. */
    private fun claiming(): HintStep? {
        for (line in rows + cols) for (d in 1..9) {
            val bit = 1 shl d
            val cells = line.cells.filter { isOpen(it) && cand(it) and bit != 0 }
            if (cells.size < 2) continue
            val b = cells.map { (it / 27) * 3 + (it % 9) / 3 }.distinct().singleOrNull() ?: continue
            val box = boxes[b]
            val removals = box.cells.filter { it !in line.cells && isOpen(it) }.associateWith { bit }
            eliminate(
                removals,
                "In ${line.name} kann die $d nur innerhalb von ${box.name} stehen. Deshalb ist sie in den " +
                    "übrigen Feldern dieses Blocks ausgeschlossen.",
                box.cells.toSet() + line.cells.toSet(),
            )?.let { return it }
        }
        return null
    }
}

// ---------------------------------------------------------------------- Futoshiki

internal class FutoshikiHinter(private val p: FutoshikiPuzzle, entries: IntArray) : NumberHinter(
    p, entries, latinUnits(p.size), p.solution, p.solution,
) {
    /** Paare (kleiner, größer). */
    private val pairs: List<Pair<Int, Int>> = buildList {
        val n = p.size
        for (i in 0 until n * n) {
            if (i % n < n - 1 && p.right[i] != 0) {
                add(if (p.right[i] == FutoshikiPuzzle.LESS) i to i + 1 else i + 1 to i)
            }
            if (i / n < n - 1 && p.down[i] != 0) {
                add(if (p.down[i] == FutoshikiPuzzle.LESS) i to i + n else i + n to i)
            }
        }
    }

    override fun techniques(): List<() -> HintStep?> = listOf(::inequality)

    private fun describe(cell: Int, mask: Int): String =
        if (!isOpen(cell)) "dort steht die ${entries[cell]}" else "dort ist höchstens die ${highest(mask)} möglich"

    private fun highest(mask: Int) = 31 - Integer.numberOfLeadingZeros(mask)
    private fun lowest(mask: Int) = Integer.numberOfTrailingZeros(mask)

    private fun inequality(): HintStep? {
        for ((small, big) in pairs) {
            val cs = cand(small)
            val cb = cand(big)
            if (cs == 0 || cb == 0) continue
            if (isOpen(small)) {
                val maxBig = highest(cb)
                val removeMask = all and ((1 shl maxBig) - 1).inv()
                val text = "${name(small)} muss kleiner sein als ${name(big)} – ${describe(big, cb)}. " +
                    "Also scheidet in ${name(small)} ${digitList(removeMask and cs)} aus."
                eliminate(mapOf(small to removeMask), text, setOf(small, big))?.let { return it }
            }
            if (isOpen(big)) {
                val minSmall = lowest(cs)
                val removeMask = all and ((1 shl (minSmall + 1)) - 1)
                val detail = if (!isOpen(small)) "dort steht die ${entries[small]}" else "dort ist mindestens die $minSmall nötig"
                val text = "${name(big)} muss größer sein als ${name(small)} – $detail. " +
                    "Also scheidet in ${name(big)} ${digitList(removeMask and cb)} aus."
                eliminate(mapOf(big to removeMask), text, setOf(small, big))?.let { return it }
            }
        }
        return null
    }
}

// ---------------------------------------------------------------------- KenKen

internal class KenKenHinter(private val p: KenKenPuzzle, entries: IntArray) : NumberHinter(
    p, entries, latinUnits(p.size), p.solution, p.solution,
) {
    private val tuples = p.cages.map { kenkenTuples(it, p.size) }

    override fun techniques(): List<() -> HintStep?> = listOf(::cage)

    private fun format(cage: KenKenCage, values: List<Int>): String = when (cage.op) {
        KenKenOp.NONE -> "${values[0]}"
        KenKenOp.ADD -> values.joinToString("+")
        KenKenOp.MUL -> values.joinToString("×")
        KenKenOp.SUB -> values.sortedDescending().joinToString("−")
        KenKenOp.DIV -> values.sortedDescending().joinToString("÷")
    }

    private fun cage(): HintStep? {
        // Käfige mit wenigen Möglichkeiten zuerst – die sind am leichtesten zu sehen.
        val order = p.cages.indices
            .filter { k -> p.cages[k].cells.any { isOpen(it) } }
            .map { k -> k to tuples[k].filter { t -> t.indices.all { cand(p.cages[k].cells[it]) and (1 shl t[it]) != 0 } } }
            .sortedBy { it.second.size }
        for ((k, consistent) in order) {
            if (consistent.isEmpty()) continue
            val cage = p.cages[k]
            val removals = HashMap<Int, Int>()
            cage.cells.forEachIndexed { pos, c ->
                if (!isOpen(c)) return@forEachIndexed
                var allowed = 0
                for (t in consistent) allowed = allowed or (1 shl t[pos])
                removals[c] = all and allowed.inv()
            }
            val combos = consistent.map { t -> t.sorted() }.distinct()
            val text = if (cage.cells.size == 1) {
                "Der Käfig „${cage.label}“ besteht nur aus einem Feld – dort steht also die ${cage.target}."
            } else {
                val shown = combos.take(8).joinToString(", ") { format(cage, it) } + if (combos.size > 8) ", …" else ""
                "Käfig „${cage.label}“ (${cage.cells.size} Felder): Passend zu Zeilen und Spalten gehen nur $shown. " +
                    "Andere Ziffern fallen in diesem Käfig weg."
            }
            eliminate(removals, text, cage.cells.toSet())?.let { return it }
        }
        return null
    }
}

// ---------------------------------------------------------------------- Kakuro

internal class KakuroHinter(private val p: KakuroPuzzle, entries: IntArray) : NumberHinter(
    p, entries,
    p.geometry.runs.map { run ->
        val dir = if (run.horizontal) "waagerecht" else "senkrecht"
        HintUnit("$dir ${p.sumOf(run)} (${run.cells.size} Felder)", run.cells, false)
    },
    if (p.unique) p.solution else null,
    p.solution,
) {
    private val runs = p.geometry.runs

    override fun techniques(): List<() -> HintStep?> = listOf(::requiredDigit, ::combinations)

    /** Kombinationen einer Folge, die zu eingetragenen Ziffern und Kandidaten passen. */
    private fun validCombos(r: Int): Pair<Int, List<Int>> {
        val cells = runs[r].cells
        var placed = 0
        var openUnion = 0
        for (c in cells) if (entries[c] != 0) placed = placed or (1 shl entries[c]) else openUnion = openUnion or cand(c)
        val combos = KakuroCombos.of(cells.size, p.sumOf(runs[r])).filter { combo ->
            if (combo and placed != placed) return@filter false
            val rest = combo and placed.inv()
            rest and openUnion == rest && cells.all { c -> entries[c] != 0 || cand(c) and rest != 0 }
        }
        return placed to combos
    }

    private fun comboText(combos: List<Int>): String =
        combos.take(8).joinToString(", ") { KakuroCombos.digits(it).joinToString("+") } + if (combos.size > 8) ", …" else ""

    /** Eine Ziffer, die in jeder möglichen Kombination vorkommt und nur in ein Feld passt. */
    private fun requiredDigit(): HintStep? {
        for (r in runs.indices) {
            val (placed, combos) = validCombos(r)
            if (combos.isEmpty()) continue
            var required = all
            for (c in combos) required = required and c
            required = required and placed.inv()
            for (d in KakuroCombos.digits(required)) {
                val bit = 1 shl d
                val places = runs[r].cells.filter { isOpen(it) && cand(it) and bit != 0 }
                if (places.size != 1) continue
                val unit = units[r]
                val lines = listOf(
                    "Für ${unit.name} kommen nur diese Kombinationen in Frage: ${comboText(combos)}.",
                    "Die $d steckt in jeder davon, muss also in diese Folge.",
                    "In den anderen freien Feldern der Folge ist die $d nicht möglich – bleibt nur dieses Feld.",
                )
                return HintStep.Place(places[0], d, "Die $d muss hierhin", lines, runs[r].cells.toSet())
            }
        }
        return null
    }

    /** Ziffern streichen, die in keiner möglichen Kombination einer Folge vorkommen. */
    private fun combinations(): HintStep? {
        // Folgen mit wenigen Kombinationen zuerst – die sind am leichtesten zu sehen.
        val order = runs.indices.map { it to validCombos(it) }.sortedBy { it.second.second.size }
        for ((r, pc) in order) {
            val (placed, combos) = pc
            if (combos.isEmpty()) continue
            var allowed = 0
            for (c in combos) allowed = allowed or c
            allowed = allowed and placed.inv()
            val removals = runs[r].cells.filter { isOpen(it) }.associateWith { all and allowed.inv() }
            val already = if (placed != 0) " (eingetragen ist schon ${digitList(placed)})" else ""
            val text = "${units[r].name}: möglich sind nur ${comboText(combos)}$already. " +
                "Also kommen in den freien Feldern nur ${digitList(allowed)} in Frage."
            eliminate(removals, text, runs[r].cells.toSet())?.let { return it }
        }
        return null
    }
}

// ====================================================================== Catsweeper

internal class CatsweeperHinter(private val p: CatsweeperPuzzle, private val entries: IntArray) {
    private fun name(i: Int) = cellName(i, p.width)
    private fun hidden(i: Int) = p.neighbors(i).filter { entries[it] == CatsweeperPuzzle.HIDDEN }
    private fun marked(i: Int) = p.neighbors(i).count { entries[it] == CatsweeperPuzzle.MARKED }

    fun find(): Hint? {
        wrongMark()?.let { return it }
        if (entries.none { it == CatsweeperPuzzle.REVEALED }) {
            val center = (p.height / 2) * p.width + p.width / 2
            return Hint(
                center, HintAction.REVEAL, title = "Einfach loslegen",
                steps = listOf("Der erste Zug ist immer sicher – tippe irgendwo, zum Beispiel hier in die Mitte."),
            )
        }
        val numbers = entries.indices.filter {
            entries[it] == CatsweeperPuzzle.REVEALED && !p.isDog(it) && p.dogCounts[it] > 0 && hidden(it).isNotEmpty()
        }
        for (i in numbers) {
            val count = p.dogCounts[i]
            val h = hidden(i)
            val m = marked(i)
            if (count - m == 0) {
                return Hint(
                    h.first(), HintAction.REVEAL, title = "Hier ist eine Katze",
                    steps = listOf(
                        "Die $count bei ${name(i)} hat schon $m markierte Hunde um sich.",
                        "Mehr Hunde gibt es dort nicht – alle anderen verdeckten Nachbarn sind sicher.",
                    ),
                    related = p.neighbors(i).toSet() + i,
                )
            }
            if (count - m == h.size) {
                val markedText = if (m > 0) " und $m markierte" else ""
                return Hint(
                    h.first(), HintAction.MARK, title = "Hier liegt ein Hund",
                    steps = listOf(
                        "Die $count bei ${name(i)} hat genau ${h.size} verdeckte$markedText Nachbarn.",
                        "Die fehlenden ${count - m} Hunde müssen also genau dort liegen – markiere sie mit 🦴.",
                    ),
                    related = p.neighbors(i).toSet() + i,
                )
            }
        }
        // Teilmengen: Die verdeckten Nachbarn von A liegen alle auch um B.
        for (a in numbers) for (b in numbers) {
            if (a == b) continue
            if (kotlin.math.abs(a / p.width - b / p.width) > 2 || kotlin.math.abs(a % p.width - b % p.width) > 2) continue
            val ha = hidden(a).toSet()
            val hb = hidden(b).toSet()
            if (!hb.containsAll(ha) || ha.size == hb.size) continue
            val ra = p.dogCounts[a] - marked(a)
            val rb = p.dogCounts[b] - marked(b)
            val diff = hb - ha
            val base = listOf(
                "Alle verdeckten Nachbarn der ${p.dogCounts[a]} bei ${name(a)} sind auch Nachbarn der " +
                    "${p.dogCounts[b]} bei ${name(b)}.",
                "Um ${name(a)} fehlen noch $ra Hunde, um ${name(b)} noch $rb.",
            )
            val related = ha + hb + a + b
            if (rb == ra) {
                return Hint(
                    diff.first(), HintAction.REVEAL, title = "Hier ist eine Katze",
                    steps = base + "Die fehlenden Hunde von ${name(b)} liegen also alle im gemeinsamen Bereich – " +
                        "die übrigen Nachbarn von ${name(b)} sind sicher.",
                    related = related,
                )
            }
            if (rb - ra == diff.size) {
                return Hint(
                    diff.first(), HintAction.MARK, title = "Hier liegt ein Hund",
                    steps = base + "Im gemeinsamen Bereich liegen höchstens $ra davon. Die restlichen ${rb - ra} " +
                        "müssen in den ${diff.size} übrigen Nachbarn von ${name(b)} liegen.",
                    related = related,
                )
            }
        }
        return guess()
    }

    private fun wrongMark(): Hint? {
        val i = entries.indices.firstOrNull { entries[it] == CatsweeperPuzzle.MARKED && !p.isDog(it) } ?: return null
        return Hint(
            i, HintAction.UNMARK, title = "Falscher Knochen",
            steps = listOf(
                "Unter diesem Knochen liegt gar kein Hund.",
                "Entferne die Markierung, sonst führen dich die Zahlen in der Umgebung in die Irre.",
            ),
            related = setOf(i),
        )
    }

    private fun guess(): Hint {
        val hiddenSafe = entries.indices.filter { entries[it] == CatsweeperPuzzle.HIDDEN && !p.isDog(it) }
        val frontier = hiddenSafe.filter { c -> p.neighbors(c).any { entries[it] == CatsweeperPuzzle.REVEALED } }
        val target = (frontier.ifEmpty { hiddenSafe }).first()
        return Hint(
            target, HintAction.REVEAL, title = "Hier müsstest du raten",
            steps = listOf(
                "Mit sicheren Schlüssen aus den Zahlen geht es gerade nicht weiter – an dieser Stelle " +
                    "müsste man raten.",
                "Ich verrate dir ein sicheres Feld: Hier sitzt eine Katze.",
            ),
        )
    }
}
