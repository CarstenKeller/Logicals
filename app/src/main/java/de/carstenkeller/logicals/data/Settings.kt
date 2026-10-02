package de.carstenkeller.logicals.data

import android.content.Context
import de.carstenkeller.logicals.core.KakuroGenerator
import de.carstenkeller.logicals.core.SudokuDifficulty

/** Merkt sich die zuletzt gewählten Optionen für neue Rätsel. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var sudokuDifficulty: SudokuDifficulty
        get() = prefs.getString(KEY_DIFFICULTY, null)
            ?.let { name -> SudokuDifficulty.entries.firstOrNull { it.name == name } }
            ?: SudokuDifficulty.MEDIUM
        set(value) = prefs.edit().putString(KEY_DIFFICULTY, value.name).apply()

    var kakuroWidth: Int
        get() = prefs.getInt(KEY_WIDTH, 10).coerceIn(KakuroGenerator.MIN_SIZE, KakuroGenerator.MAX_SIZE)
        set(value) = prefs.edit().putInt(KEY_WIDTH, value).apply()

    var kakuroHeight: Int
        get() = prefs.getInt(KEY_HEIGHT, 10).coerceIn(KakuroGenerator.MIN_SIZE, KakuroGenerator.MAX_SIZE)
        set(value) = prefs.edit().putInt(KEY_HEIGHT, value).apply()

    private companion object {
        const val KEY_DIFFICULTY = "sudoku_difficulty"
        const val KEY_WIDTH = "kakuro_width"
        const val KEY_HEIGHT = "kakuro_height"
    }
}
