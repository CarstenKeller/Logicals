package de.carstenkeller.logicals.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PuzzleTest {

    @Test
    fun sudokuIsUniqueAndSolutionIsAccepted() {
        for (difficulty in Difficulty.entries) {
            repeat(5) { seed ->
                val puzzle = SudokuGenerator(Random(seed)).generate(difficulty)
                val givens = puzzle.givens.toIntArray()
                assertEquals(1, SudokuSolver.countSolutions(givens))
                assertTrue(puzzle.givens.count { it != 0 } >= difficulty.sudokuClues)
                assertFalse(puzzle.isSolved(puzzle.initialEntries()))
                assertTrue(puzzle.isSolved(puzzle.solution.toIntArray()))
            }
        }
    }

    @Test
    fun sudokuConflictsAreDetected() {
        val puzzle = SudokuGenerator(Random(1)).generate(Difficulty.EASY)
        val entries = puzzle.initialEntries()
        val empty = entries.indexOfFirst { it == 0 }
        val row = empty / 9
        val other = (0 until 9).map { row * 9 + it }.first { entries[it] != 0 }
        entries[empty] = entries[other]
        assertTrue(empty in puzzle.conflicts(entries))
        assertTrue(other in puzzle.conflicts(entries))
    }

    @Test
    fun kakuroInAllSizesIsValid() {
        val sizes = listOf(4 to 4, 5 to 7, 8 to 8, 10 to 10, 12 to 9, 15 to 15)
        for ((w, h) in sizes) {
            repeat(3) { seed ->
                val generated = KakuroGenerator(Random(seed)).generate(w, h)
                val puzzle = generated.puzzle
                assertEquals(w * h, puzzle.cells.size)
                for (run in puzzle.geometry.runs) assertTrue(run.cells.size in 2..9)
                val solution = puzzle.solution.toIntArray()
                assertTrue("solution must satisfy rules", puzzle.isSolved(solution))
                assertFalse(puzzle.isSolved(puzzle.initialEntries()))
                assertTrue("${w}x$h seed $seed should be unique", generated.unique)
                assertTrue(puzzle.unique)
                val sums = IntArray(puzzle.geometry.runs.size) { puzzle.sumOf(puzzle.geometry.runs[it]) }
                val result = KakuroSolver(puzzle.geometry, sums).solve()
                assertFalse(result.aborted)
                assertEquals(1, result.count)
                assertTrue(result.solutions[0].contentEquals(solution))
            }
        }
    }

    @Test
    fun kakuroConflictsAreDetected() {
        val puzzle = KakuroGenerator(Random(3)).generate(6, 6).puzzle
        val entries = puzzle.solution.toIntArray()
        val run = puzzle.geometry.runs.first()
        entries[run.cells[0]] = entries[run.cells[1]]
        val conflicts = puzzle.conflicts(entries)
        assertTrue(run.cells[0] in conflicts)
        assertTrue(run.clueIndex in conflicts)
        assertFalse(puzzle.isSolved(entries))
    }

    @Test
    fun gameStateSurvivesJsonRoundTrip() {
        val kakuro = KakuroGenerator(Random(7)).generate(7, 6).puzzle
        val sudoku = SudokuGenerator(Random(7)).generate(Difficulty.MEDIUM)
        for (puzzle in listOf<Puzzle>(kakuro, sudoku)) {
            val state = GameState.start(puzzle).copy(elapsedMillis = 12345)
            val json = PuzzleJson.encodeToString(GameState.serializer(), state)
            val back = PuzzleJson.decodeFromString(GameState.serializer(), json)
            assertEquals(state, back)
            assertEquals(puzzle.kind, back.puzzle.kind)
        }
        val json = PuzzleJson.encodeToString(GameState.serializer(), GameState.start(kakuro))
        assertFalse("geometry must not be serialized", json.contains("geometry"))
        assertTrue(PuzzleJson.decodeFromString(GameState.serializer(), json).puzzle is KakuroPuzzle)
        assertTrue(PuzzleJson.decodeFromString(GameState.serializer(), PuzzleJson.encodeToString(GameState.serializer(), GameState.start(sudoku))).puzzle is SudokuPuzzle)
    }
}
