package de.carstenkeller.logicals.data

import de.carstenkeller.logicals.core.GameState
import de.carstenkeller.logicals.core.PuzzleOptions
import de.carstenkeller.logicals.core.PuzzleType
import kotlinx.coroutines.flow.Flow

/** Darstellung: dem System folgen oder fest hell/dunkel. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Hält pro Rätselart genau einen begonnenen Spielstand (JSON wie [de.carstenkeller.logicals.core.PuzzleJson]).
 * Android speichert in Dateien, die Web-App im localStorage des Browsers.
 */
interface GameStore {
    fun observe(type: PuzzleType): Flow<GameState?>

    suspend fun load(type: PuzzleType): GameState?

    fun save(state: GameState)

    fun delete(type: PuzzleType)
}

/** Einstellungen, die die gemeinsame Oberfläche liest und schreibt. */
interface PuzzleSettings {
    /** "Einfach"-Modus für Zahlenrätsel: unmögliche Ziffern ausgrauen, Einzige automatisch setzen. */
    var easyMode: Boolean

    /** Zuletzt gewählte Optionen für neue Rätsel dieser Art. */
    fun options(type: PuzzleType): PuzzleOptions

    fun save(type: PuzzleType, options: PuzzleOptions)
}
