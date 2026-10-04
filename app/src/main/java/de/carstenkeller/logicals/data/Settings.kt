package de.carstenkeller.logicals.data

import android.content.Context
import de.carstenkeller.logicals.core.Difficulty
import de.carstenkeller.logicals.core.PuzzleOptions
import de.carstenkeller.logicals.core.PuzzleType

/** Merkt sich die Darstellung und pro Rätselart die zuletzt gewählten Optionen für neue Rätsel. */
class Settings(context: Context) : PuzzleSettings {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = prefs.getString("theme_mode", null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
        set(value) = prefs.edit().putString("theme_mode", value.name).apply()

    override var easyMode: Boolean
        get() = prefs.getBoolean("easy_mode", false)
        set(value) = prefs.edit().putBoolean("easy_mode", value).apply()

    override fun options(type: PuzzleType): PuzzleOptions {
        val key = type.name.lowercase()
        val difficulty = prefs.getString("${key}_difficulty", null)
            ?.let { name -> Difficulty.entries.firstOrNull { it.name == name } }
            ?: Difficulty.MEDIUM
        val range = type.sizeRange
        val width = prefs.getInt("${key}_width", type.defaultWidth).let { if (range != null) it.coerceIn(range) else it }
        val height = prefs.getInt("${key}_height", type.defaultHeight).let { if (range != null) it.coerceIn(range) else it }
        return PuzzleOptions(difficulty, width, height)
    }

    override fun save(type: PuzzleType, options: PuzzleOptions) {
        val key = type.name.lowercase()
        prefs.edit()
            .putString("${key}_difficulty", options.difficulty.name)
            .putInt("${key}_width", options.width)
            .putInt("${key}_height", options.height)
            .apply()
    }
}
