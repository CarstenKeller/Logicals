package de.carstenkeller.logicals.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewPuzzlesTest {

    @Test
    fun futoshikiIsUniqueAndSolutionIsAccepted() {
        for (size in 4..9) {
            for (difficulty in Difficulty.entries) {
                val p = FutoshikiGenerator(Random(size * 10 + difficulty.ordinal)).generate(size, difficulty)
                val result = FutoshikiSolver(size, p.givens.toIntArray(), p.right.toIntArray(), p.down.toIntArray())
                    .solve(nodeLimit = 5_000_000L)
                assertFalse(result.aborted)
                assertEquals(1, result.count, "size $size $difficulty")
                assertTrue(result.solutions[0].contentEquals(p.solution.toIntArray()))
                assertTrue(p.isSolved(p.solution.toIntArray()))
                assertFalse(p.isSolved(p.initialEntries()))
            }
        }
    }

    @Test
    fun futoshikiInequalityConflict() {
        val p = FutoshikiGenerator(Random(5)).generate(5, Difficulty.HARD)
        val i = p.right.indexOfFirst { it != 0 }
        val entries = p.solution.toIntArray()
        // Werte der beiden Zellen tauschen verletzt die Ungleichung.
        val tmp = entries[i]
        entries[i] = entries[i + 1]
        entries[i + 1] = tmp
        assertTrue(i in p.conflicts(entries))
    }

    @Test
    fun kenkenIsUniqueAndSolutionIsAccepted() {
        for (size in 4..9) {
            for (difficulty in Difficulty.entries) {
                val p = KenKenGenerator(Random(size * 10 + difficulty.ordinal)).generate(size, difficulty)
                val covered = p.cages.flatMap { it.cells }.sorted()
                assertEquals((0 until size * size).toList(), covered)
                for (cage in p.cages) assertEquals(cage.target, cage.op.apply(cage.cells.map { p.solution[it] }))
                val result = KenKenSolver(size, p.cages).solve(nodeLimit = 5_000_000L)
                assertFalse(result.aborted)
                assertEquals(1, result.count, "size $size $difficulty")
                assertTrue(p.isSolved(p.solution.toIntArray()))
                assertFalse(p.isSolved(p.initialEntries()))
            }
        }
    }

    @Test
    fun catsweeperRevealAndWin() {
        val p0 = CatsweeperGenerator(Random(2)).generate(12, 9, Difficulty.MEDIUM)
        val start = 4 * 12 + 6
        val p = p0.withSafeStart(start, Random(3))
        assertEquals(p0.dogs.size, p.dogs.size)
        assertFalse(p.isDog(start))
        assertTrue(p.neighbors(start).none { p.isDog(it) })
        var entries = p.reveal(p.initialEntries(), start)
        assertTrue(entries.count { it == CatsweeperPuzzle.REVEALED } > 1, "flood fill opens more than one cell")
        assertFalse(p.isLost(entries))
        // Alle sicheren Felder aufdecken → gewonnen.
        for (i in entries.indices) if (!p.isDog(i)) entries = p.reveal(entries, i)
        assertTrue(p.isSolved(entries))
        // Einen Hund aufdecken → verloren.
        val lost = p.reveal(entries, p.dogs.first())
        assertTrue(p.isLost(lost))
    }

    @Test
    fun catsweeperMarkBlocksReveal() {
        val p = CatsweeperGenerator(Random(4)).generate(8, 8, Difficulty.HARD)
        val dog = p.dogs.first()
        val marked = p.toggleMark(p.initialEntries(), dog)
        assertEquals(CatsweeperPuzzle.MARKED, marked[dog])
        assertFalse(p.isLost(p.reveal(marked, dog)))
    }

    @Test
    fun factoryRespectsOptionsAndJsonRoundTrip() {
        val puzzles = listOf(
            PuzzleFactory.generate(PuzzleType.FUTOSHIKI, PuzzleOptions(Difficulty.EASY, 5, 5), Random(1)),
            PuzzleFactory.generate(PuzzleType.KENKEN, PuzzleOptions(Difficulty.HARD, 6, 6), Random(1)),
            PuzzleFactory.generate(PuzzleType.CATSWEEPER, PuzzleOptions(Difficulty.EASY, 9, 13), Random(1)),
        )
        assertEquals(5, puzzles[0].width)
        assertEquals(6, puzzles[1].width)
        assertEquals(9, puzzles[2].width)
        assertEquals(13, puzzles[2].height)
        for (p in puzzles) {
            val state = GameState.start(p)
            val json = PuzzleJson.encodeToString(GameState.serializer(), state)
            assertEquals(state, PuzzleJson.decodeFromString(GameState.serializer(), json))
            assertEquals(p.options, PuzzleJson.decodeFromString(GameState.serializer(), json).puzzle.options)
        }
    }
}
