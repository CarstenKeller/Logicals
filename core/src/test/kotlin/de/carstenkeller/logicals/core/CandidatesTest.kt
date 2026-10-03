package de.carstenkeller.logicals.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CandidatesTest {

    /** Bei einem korrekten Teilstand darf die richtige Ziffer nie ausgeschlossen sein. */
    private fun check(puzzle: Puzzle, solution: List<Int>, seed: Int): Pair<Int, Int> {
        val random = Random(seed)
        val entries = solution.toIntArray()
        val blanks = (0 until puzzle.cellCount).filter { puzzle.isEditable(it) && random.nextBoolean() }
        blanks.forEach { entries[it] = 0 }
        var reduced = 0
        for (i in blanks) {
            val mask = Candidates.of(puzzle, entries, i)
            assertTrue("${puzzle.kind}: Lösung ${solution[i]} in Feld $i ausgegraut", mask and (1 shl solution[i]) != 0)
            if (Integer.bitCount(mask) < puzzle.maxDigit) reduced++
        }
        return reduced to blanks.size
    }

    @Test
    fun solutionDigitIsNeverExcluded() {
        repeat(5) { s ->
            val puzzles = listOf(
                SudokuGenerator(Random(s)).generate(Difficulty.MEDIUM).let { it to it.solution },
                FutoshikiGenerator(Random(s)).generate(6, Difficulty.HARD).let { it to it.solution },
                KenKenGenerator(Random(s)).generate(6, Difficulty.HARD).let { it to it.solution },
                SkyscraperGenerator(Random(s)).generate(6, Difficulty.HARD).let { it to it.solution },
                KakuroGenerator(Random(s)).generate(9, 9, Difficulty.MEDIUM).puzzle.let { it to it.solution },
            )
            for ((p, sol) in puzzles) {
                val (reduced, total) = check(p, sol, s)
                assertTrue("${p.kind}: Modus schränkt nichts ein", total == 0 || reduced > 0)
            }
        }
    }

    @Test
    fun sudokuExcludesRowColumnAndBox() {
        val p = SudokuGenerator(Random(1)).generate(Difficulty.EASY)
        val entries = p.initialEntries()
        val i = entries.indexOfFirst { it == 0 }
        val mask = Candidates.of(p, entries, i)
        val unitDigits = SudokuPuzzle.UNITS.filter { i in it }.flatMap { u -> u.map { entries[it] } }.filter { it != 0 }.toSet()
        for (d in unitDigits) assertEquals(0, mask and (1 shl d))
        assertEquals(0, Candidates.of(p, entries, entries.indexOfFirst { it != 0 }))
    }
}
