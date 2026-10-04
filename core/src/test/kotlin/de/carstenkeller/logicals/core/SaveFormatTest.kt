package de.carstenkeller.logicals.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sichert das Speicherformat: Die Spielstände unten wurden mit Kotlin 2.1.21 und
 * kotlinx-serialization 1.8.1 (Android-App bis Build 13) erzeugt. Sie müssen sich weiterhin
 * laden lassen und beim erneuten Speichern zeichengleich herauskommen.
 */
class SaveFormatTest {
    @Test
    fun oldSaveGamesLoadAndEncodeIdentically() {
        assertEquals(PuzzleType.entries.toSet(), SAVES.map { it.first }.toSet())
        for ((type, json) in SAVES) {
            val state = PuzzleJson.decodeFromString(GameState.serializer(), json)
            assertEquals(type, state.puzzle.kind)
            assertEquals(61_234L, state.elapsedMillis)
            assertEquals(2, state.hintsUsed)
            assertEquals(state.puzzle.cellCount, state.entries.size)
            assertEquals(10, state.notes.last { it != 0 })
            assertEquals(json, PuzzleJson.encodeToString(GameState.serializer(), state))
        }
    }

    private companion object {
        val SAVES = listOf(
        PuzzleType.SUDOKU to """{"puzzle":{"type":"sudoku","givens":[6,3,4,2,0,0,7,5,0,0,0,7,3,5,0,9,0,4,0,2,5,1,0,0,0,0,6,0,0,0,7,8,5,0,0,0,0,0,0,0,0,3,0,0,0,7,5,9,0,0,0,2,8,0,1,0,2,0,0,4,3,6,0,3,4,0,8,0,0,5,0,7,5,0,8,0,3,0,0,4,0],"solution":[6,3,4,2,9,8,7,5,1,8,1,7,3,5,6,9,2,4,9,2,5,1,4,7,8,3,6,2,6,3,7,8,5,4,1,9,4,8,1,9,2,3,6,7,5,7,5,9,4,6,1,2,8,3,1,9,2,5,7,4,3,6,8,3,4,6,8,1,2,5,9,7,5,7,8,6,3,9,1,4,2],"difficulty":"EASY"},"entries":[6,3,4,2,1,0,7,5,0,0,0,7,3,5,0,9,0,4,0,2,5,1,0,0,0,0,6,0,0,0,7,8,5,0,0,0,0,0,0,0,0,3,0,0,0,7,5,9,0,0,0,2,8,0,1,0,2,0,0,4,3,6,0,3,4,0,8,0,0,5,0,7,5,0,8,0,3,0,0,4,0],"notes":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,10],"elapsedMillis":61234,"hintsUsed":2}""",
        PuzzleType.KAKURO to """{"puzzle":{"type":"kakuro","width":5,"height":5,"cells":[{"white":false},{"white":false},{"white":false,"down":13},{"white":false,"down":19},{"white":false,"down":23},{"white":false},{"white":false,"across":23,"down":20},{"white":true},{"white":true},{"white":true},{"white":false,"across":13},{"white":true},{"white":true},{"white":true},{"white":true},{"white":false,"across":28},{"white":true},{"white":true},{"white":true},{"white":true},{"white":false,"across":11},{"white":true},{"white":true},{"white":true},{"white":false}],"solution":[0,0,0,0,0,0,0,6,8,9,0,4,2,1,6,0,9,4,7,8,0,7,1,3,0],"difficulty":"EASY"},"entries":[0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],"notes":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,10,0],"elapsedMillis":61234,"hintsUsed":2}""",
        PuzzleType.FUTOSHIKI to """{"puzzle":{"type":"futoshiki","size":5,"givens":[0,3,4,0,0,2,0,0,0,0,3,1,0,0,5,0,0,0,3,0,0,0,1,0,3],"right":[0,1,0,2,0,0,0,0,0,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0],"down":[0,0,0,0,0,0,0,0,0,0,2,0,1,0,0,0,0,0,1,0,0,0,0,0,0],"solution":[5,3,4,2,1,2,5,3,1,4,3,1,2,4,5,1,4,5,3,2,4,2,1,5,3],"difficulty":"EASY"},"entries":[1,3,4,0,0,2,0,0,0,0,3,1,0,0,5,0,0,0,3,0,0,0,1,0,3],"notes":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,10,0],"elapsedMillis":61234,"hintsUsed":2}""",
        PuzzleType.KENKEN to """{"puzzle":{"type":"kenken","size":5,"cages":[{"cells":[10,11],"op":"DIV","target":3},{"cells":[3,4,9],"op":"MUL","target":8},{"cells":[0,1,6],"op":"ADD","target":13},{"cells":[8,13],"op":"ADD","target":5},{"cells":[18],"op":"NONE","target":3},{"cells":[15,16],"op":"ADD","target":5},{"cells":[2,7,12],"op":"ADD","target":9},{"cells":[17,21,22],"op":"ADD","target":8},{"cells":[19,23,24],"op":"ADD","target":10},{"cells":[14],"op":"NONE","target":5},{"cells":[5],"op":"NONE","target":2},{"cells":[20],"op":"NONE","target":4}],"solution":[5,3,4,2,1,2,5,3,1,4,3,1,2,4,5,1,4,5,3,2,4,2,1,5,3],"difficulty":"EASY"},"entries":[1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],"notes":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,10],"elapsedMillis":61234,"hintsUsed":2}""",
        PuzzleType.SKYSCRAPER to """{"puzzle":{"type":"skyscraper","size":5,"givens":[0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0],"top":[1,2,2,3,3],"bottom":[2,0,2,0,2],"left":[0,2,3,3,2],"right":[4,2,1,3,0],"solution":[5,3,4,2,1,2,5,3,1,4,3,1,2,4,5,1,4,5,3,2,4,2,1,5,3],"difficulty":"EASY"},"entries":[1,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0],"notes":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,10],"elapsedMillis":61234,"hintsUsed":2}""",
        PuzzleType.CATSWEEPER to """{"puzzle":{"type":"catsweeper","width":5,"height":5,"dogs":[3,12,21],"difficulty":"EASY"},"entries":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],"notes":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,10],"elapsedMillis":61234,"hintsUsed":2}""",
        )
    }
}
