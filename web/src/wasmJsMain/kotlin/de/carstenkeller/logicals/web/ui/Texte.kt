package de.carstenkeller.logicals.web.ui

import de.carstenkeller.logicals.core.Difficulty
import de.carstenkeller.logicals.core.PuzzleType

/** UI-Texte der Web-App; entsprechen app/src/main/res/values/strings.xml. */
object Texte {
    const val APP_NAME = "Logicals"
    const val MENU_SUBTITLE = "Wähle ein Logikrätsel"
    const val NOT_YET_AVAILABLE = "Im Web-Prototyp noch nicht verfügbar."
    fun version(name: String) = "Version $name"

    fun sizeLabel(n: Int) = "Größe: $n × $n"
    fun columnsLabel(n: Int) = "Spalten: $n"
    fun rowsLabel(n: Int) = "Zeilen: $n"

    const val HINT = "Hinweis"
    const val EASY_MODE = "Einfach"
    const val HINT_CLOSE = "Schließen"
    fun hintApplyPlace(value: Int) = "$value eintragen"
    const val HINT_APPLY_CLEAR = "Löschen"
    const val HINT_APPLY_REVEAL = "Aufdecken"
    const val HINT_APPLY_MARK = "🦴 Markieren"
    const val HINT_APPLY_UNMARK = "Knochen entfernen"
    fun hintsUsed(n: Int) = "Genutzte Hinweise: $n"

    const val THEME_TITLE = "Darstellung"
    const val THEME_SYSTEM = "System"
    const val THEME_LIGHT = "Hell"
    const val THEME_DARK = "Dunkel"

    const val ZOOM_RESET = "Zoom zurücksetzen"

    const val BACK = "Zurück"
    const val CONTINUE_GAME = "Fortsetzen"
    fun continueDetails(time: String) = "Bisherige Zeit: $time"
    const val NO_SAVED_GAME = "Kein begonnenes Rätsel vorhanden"
    const val NEW_GAME = "Neues Rätsel"
    const val GENERATE = "Rätsel generieren"
    const val DIFFICULTY = "Schwierigkeit"
    const val KAKURO_SIZE_HINT = "Inklusive der obersten Zeile und linken Spalte mit Summenfeldern."
    const val DISCARD_TITLE = "Begonnenes Rätsel verwerfen?"
    const val DISCARD_TEXT = "Dein aktuelles Rätsel und die bisherige Zeit gehen verloren."
    const val DISCARD_CONFIRM = "Neu beginnen"
    const val CANCEL = "Abbrechen"

    const val GENERATING = "Rätsel wird erzeugt …"
    const val LOAD_FAILED = "Es ist kein Spielstand vorhanden."
    const val TIME_LABEL = "Zeit"
    const val ERASE = "Löschen"
    const val NOTES = "Notizen"
    const val SOLVED_TITLE = "Gelöst!"
    fun solvedText(time: String) = "Du hast das Rätsel in $time gelöst."
    const val LOST_TITLE = "Wuff! 🐶"
    const val LOST_TEXT = "Ein Hund hat deine Katze erwischt."
    const val PLAY_AGAIN = "Nochmal"
    const val TO_MENU = "Zum Menü"

    val PuzzleType.title: String
        get() = when (this) {
            PuzzleType.SUDOKU -> "Sudoku"
            PuzzleType.KAKURO -> "Kakuro"
            PuzzleType.FUTOSHIKI -> "Futoshiki"
            PuzzleType.KENKEN -> "KenKen"
            PuzzleType.SKYSCRAPER -> "Skyscraper"
            PuzzleType.CATSWEEPER -> "Catsweeper"
        }

    val PuzzleType.description: String
        get() = when (this) {
            PuzzleType.SUDOKU -> "9×9-Gitter: Jede Ziffer genau einmal pro Zeile, Spalte und Block."
            PuzzleType.KAKURO -> "Zahlenkreuzworträtsel: Ziffern ergeben die Summen, keine Wiederholung pro Folge."
            PuzzleType.FUTOSHIKI ->
                "Jede Ziffer einmal pro Zeile und Spalte, dazu Größer-/Kleiner-Zeichen zwischen Feldern."
            PuzzleType.KENKEN ->
                "Jede Ziffer einmal pro Zeile und Spalte; die Zahlen in jedem Käfig ergeben das angegebene Rechenergebnis."
            PuzzleType.SKYSCRAPER ->
                "Wolkenkratzer der Höhen 1 bis N, jede Höhe einmal pro Zeile und Spalte. Die Zahlen am Rand sagen, " +
                    "wie viele Häuser man von dort sieht – höhere verdecken niedrigere."
            PuzzleType.CATSWEEPER ->
                "🐱 Decke die Katzen auf, ohne einen der versteckten Hunde 🐶 zu wecken. Zahlen zeigen, " +
                    "wie viele Hunde eine Katze umzingeln."
        }

    val Difficulty.label: String
        get() = when (this) {
            Difficulty.EASY -> "Leicht"
            Difficulty.MEDIUM -> "Mittel"
            Difficulty.HARD -> "Schwer"
        }
}

/** Formatiert eine Dauer als mm:ss bzw. h:mm:ss. */
fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    fun two(v: Long) = v.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
}
