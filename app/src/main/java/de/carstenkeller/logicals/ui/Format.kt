package de.carstenkeller.logicals.ui

import androidx.annotation.StringRes
import de.carstenkeller.logicals.R
import de.carstenkeller.logicals.core.Difficulty
import de.carstenkeller.logicals.core.PuzzleType
import java.util.Locale

/** Formatiert eine Dauer als mm:ss bzw. h:mm:ss. */
fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}

@get:StringRes
val PuzzleType.titleRes: Int
    get() = when (this) {
        PuzzleType.SUDOKU -> R.string.sudoku
        PuzzleType.KAKURO -> R.string.kakuro
        PuzzleType.FUTOSHIKI -> R.string.futoshiki
        PuzzleType.KENKEN -> R.string.kenken
        PuzzleType.SKYSCRAPER -> R.string.skyscraper
        PuzzleType.CATSWEEPER -> R.string.catsweeper
    }

@get:StringRes
val PuzzleType.descriptionRes: Int
    get() = when (this) {
        PuzzleType.SUDOKU -> R.string.sudoku_description
        PuzzleType.KAKURO -> R.string.kakuro_description
        PuzzleType.FUTOSHIKI -> R.string.futoshiki_description
        PuzzleType.KENKEN -> R.string.kenken_description
        PuzzleType.SKYSCRAPER -> R.string.skyscraper_description
        PuzzleType.CATSWEEPER -> R.string.catsweeper_description
    }

@get:StringRes
val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
    }
