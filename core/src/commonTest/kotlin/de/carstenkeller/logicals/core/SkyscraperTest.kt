package de.carstenkeller.logicals.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkyscraperTest {

    @Test
    fun visibleCountsTallerBuildings() {
        assertEquals(4, visible(listOf(1, 2, 3, 4)))
        assertEquals(1, visible(listOf(4, 1, 2, 3)))
        assertEquals(2, visible(listOf(2, 1, 4, 3)))
    }

    @Test
    fun generatedPuzzlesAreSolvableByHints() {
        for (size in 4..7) for (d in Difficulty.entries) {
            val p = SkyscraperGenerator(Random(size * 7 + d.ordinal)).generate(size, d)
            val solution = p.solution.toIntArray()
            assertTrue(p.isSolved(solution))
            assertFalse(p.isSolved(p.initialEntries()))
            // Randhinweise passen zur Lösung.
            assertEquals(2 * size, p.lines.size)
            for (line in p.lines) assertTrue(line.accepts(line.cells.map { solution[it] }))
            // Nur mit Hinweisen lösen, jeder Schritt muss stimmen.
            var entries = p.initialEntries()
            while (!p.isSolved(entries)) {
                val hint = HintFinder.find(p, entries)!!
                assertEquals(HintAction.PLACE, hint.action)
                assertTrue(hint.title != "Tipp ohne Herleitung", "size $size $d: ${hint.title}")
                assertEquals(solution[hint.cell], hint.value)
                entries = entries.copyOf().also { it[hint.cell] = hint.value }
            }
            val clues = (p.top + p.bottom + p.left + p.right).count { it != 0 }
            println("skyscraper $size $d clues=$clues givens=${p.givens.count { it != 0 }}")
            assertTrue(clues >= 3, "ein Skyscraper braucht Randhinweise")
            assertTrue(p.givens.count { it != 0 } < size * size / 4, "Randhinweise sollen tragen, nicht Vorgaben")
        }
    }

    @Test
    fun wrongLineIsAConflict() {
        val p = SkyscraperGenerator(Random(3)).generate(5, Difficulty.EASY)
        val entries = p.solution.toIntArray()
        // Zwei Felder einer Zeile tauschen: Zeilen bleiben lateinisch, Spalten nicht.
        val tmp = entries[0]
        entries[0] = entries[1]
        entries[1] = tmp
        assertFalse(p.isSolved(entries))
        assertTrue(p.conflicts(entries).isNotEmpty())
    }

    @Test
    fun jsonRoundTrip() {
        val p = PuzzleFactory.generate(PuzzleType.SKYSCRAPER, PuzzleOptions(Difficulty.MEDIUM, 6, 6), Random(2))
        val state = GameState.start(p)
        val json = PuzzleJson.encodeToString(GameState.serializer(), state)
        assertEquals(state, PuzzleJson.decodeFromString(GameState.serializer(), json))
        assertEquals(6, p.width)
    }
}
