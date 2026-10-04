package de.carstenkeller.logicals.web.data

import de.carstenkeller.logicals.core.Difficulty
import de.carstenkeller.logicals.core.PuzzleOptions
import de.carstenkeller.logicals.core.PuzzleType

/** Darstellung: dem System folgen oder fest hell/dunkel. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Merkt sich die Darstellung und pro Rätselart die zuletzt gewählten Optionen für neue Rätsel.
 * Schlüssel wie in den SharedPreferences der Android-App, mit Präfix "settings.".
 */
object Settings {
    private fun key(name: String) = "settings.$name"

    var themeMode: ThemeMode
        get() = Storage.get(key("theme_mode"))
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
        set(value) = Storage.set(key("theme_mode"), value.name)

    /** "Einfach"-Modus für Zahlenrätsel: unmögliche Ziffern ausgrauen, Einzige automatisch setzen. */
    var easyMode: Boolean
        get() = Storage.get(key("easy_mode")) == "true"
        set(value) = Storage.set(key("easy_mode"), value.toString())

    fun options(type: PuzzleType): PuzzleOptions {
        val prefix = type.name.lowercase()
        val difficulty = Storage.get(key("${prefix}_difficulty"))
            ?.let { name -> Difficulty.entries.firstOrNull { it.name == name } }
            ?: Difficulty.MEDIUM
        val range = type.sizeRange
        fun size(name: String, default: Int): Int {
            val value = Storage.get(key("${prefix}_$name"))?.toIntOrNull() ?: default
            return if (range != null) value.coerceIn(range) else value
        }
        return PuzzleOptions(difficulty, size("width", type.defaultWidth), size("height", type.defaultHeight))
    }

    fun save(type: PuzzleType, options: PuzzleOptions) {
        val prefix = type.name.lowercase()
        Storage.set(key("${prefix}_difficulty"), options.difficulty.name)
        Storage.set(key("${prefix}_width"), options.width.toString())
        Storage.set(key("${prefix}_height"), options.height.toString())
    }
}
