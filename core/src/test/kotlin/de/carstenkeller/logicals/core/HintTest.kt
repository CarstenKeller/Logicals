package de.carstenkeller.logicals.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HintTest {

    /** Löst ein Zahlenrätsel nur mit Hinweisen; jeder Hinweis muss zur Lösung passen. */
    private fun solveWithHints(puzzle: Puzzle, solution: List<Int>): Int {
        var entries = puzzle.initialEntries()
        var fallbacks = 0
        repeat(puzzle.cellCount + 5) {
            if (puzzle.isSolved(entries)) return fallbacks
            val hint = HintFinder.find(puzzle, entries)
            assertNotNull(hint)
            hint!!
            assertEquals(HintAction.PLACE, hint.action)
            assertEquals("${puzzle.kind} ${hint.title}: ${hint.steps}", solution[hint.cell], hint.value)
            assertTrue(hint.steps.isNotEmpty())
            if (hint.title == "Tipp ohne Herleitung") fallbacks++
            entries = entries.copyOf().also { it[hint.cell] = hint.value }
        }
        assertTrue(puzzle.isSolved(entries))
        return fallbacks
    }

    @Test
    fun sudokuSolvableByHints() {
        for (d in Difficulty.entries) {
            val p = SudokuGenerator(Random(d.ordinal)).generate(d)
            val fallbacks = solveWithHints(p, p.solution)
            println("sudoku $d fallbacks=$fallbacks clues=${p.givens.count { it != 0 }}")
            assertEquals(0, fallbacks)
        }
    }

    @Test
    fun kakuroSolvableByHints() {
        for (d in Difficulty.entries) {
            val p = KakuroGenerator(Random(d.ordinal)).generate(10, 10, d).puzzle
            val fallbacks = solveWithHints(p, p.solution)
            println("kakuro $d fallbacks=$fallbacks")
            if (d != Difficulty.HARD) assertEquals("easy/medium kakuro must be explainable", 0, fallbacks)
        }
    }

    @Test
    fun futoshikiAndKenkenSolvableByHints() {
        for (d in Difficulty.entries) {
            for (size in listOf(4, 6, 9)) {
                val f = FutoshikiGenerator(Random(d.ordinal)).generate(size, d)
                assertEquals("futoshiki $size $d", 0, solveWithHints(f, f.solution))
                val k = KenKenGenerator(Random(d.ordinal)).generate(size, d)
                assertEquals("kenken $size $d", 0, solveWithHints(k, k.solution))
            }
        }
    }

    @Test
    fun wrongEntryIsReported() {
        val p = SudokuGenerator(Random(9)).generate(Difficulty.EASY)
        val entries = p.initialEntries()
        val cell = entries.indexOfFirst { it == 0 }
        entries[cell] = p.solution[cell] % 9 + 1
        val hint = HintFinder.find(p, entries)!!
        assertEquals(HintAction.CLEAR, hint.action)
        assertEquals(cell, hint.cell)
    }

    @Test
    fun catsweeperHintsAreSafe() {
        for (d in Difficulty.entries) {
            var p = CatsweeperGenerator(Random(d.ordinal)).generate(12, 12, d)
            var entries = p.initialEntries()
            var guesses = 0
            repeat(1000) {
                if (p.isSolved(entries)) return@repeat
                val hint = HintFinder.find(p, entries)!!
                when (hint.action) {
                    HintAction.REVEAL -> {
                        if (entries.none { it == CatsweeperPuzzle.REVEALED }) p = p.withSafeStart(hint.cell, Random(1))
                        assertFalse("reveal must be safe", p.isDog(hint.cell))
                        if (hint.title == "Hier müsstest du raten") guesses++
                        entries = p.reveal(entries, hint.cell)
                    }
                    HintAction.MARK -> {
                        assertTrue("mark must be a dog", p.isDog(hint.cell))
                        entries = p.toggleMark(entries, hint.cell)
                    }
                    else -> error("unexpected ${hint.action}")
                }
                assertFalse(p.isLost(entries))
            }
            assertTrue(p.isSolved(entries))
            println("catsweeper $d guesses=$guesses")
        }
    }
}
