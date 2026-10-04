package de.carstenkeller.logicals.web.data

import de.carstenkeller.logicals.core.GameState
import de.carstenkeller.logicals.core.PuzzleJson
import de.carstenkeller.logicals.core.PuzzleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hält pro Rätselart genau einen begonnenen Spielstand, gespeichert als JSON im selben Format
 * wie die Dateien der Android-App (Schlüssel "save.<art>").
 *
 * Der Browser schreibt localStorage synchron; ein Hintergrund-Schreiber wie auf Android ist
 * daher nicht nötig.
 */
object GameRepository {
    private val states = PuzzleType.entries.associateWith { type -> MutableStateFlow(read(type)) }

    fun observe(type: PuzzleType): StateFlow<GameState?> = states.getValue(type)

    fun load(type: PuzzleType): GameState? = states.getValue(type).value

    fun save(state: GameState) {
        states.getValue(state.puzzle.kind).value = state
        Storage.set(keyFor(state.puzzle.kind), PuzzleJson.encodeToString(GameState.serializer(), state))
    }

    fun delete(type: PuzzleType) {
        states.getValue(type).value = null
        Storage.remove(keyFor(type))
    }

    private fun keyFor(type: PuzzleType) = "save.${type.name.lowercase()}"

    private fun read(type: PuzzleType): GameState? {
        val json = Storage.get(keyFor(type)) ?: return null
        return try {
            PuzzleJson.decodeFromString(GameState.serializer(), json)
        } catch (e: Exception) {
            println("Spielstand für $type ist unlesbar und wird verworfen: ${e.message}")
            Storage.remove(keyFor(type))
            null
        }
    }
}
