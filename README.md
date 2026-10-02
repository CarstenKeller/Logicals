# Logicals

Android-App für Logikrätsel (Kotlin, Jetpack Compose).

## Funktionen

- **Hauptmenü** mit Auswahl der Rätselart (aktuell Sudoku und Kakuro).
- Pro Rätselart: **begonnenes Rätsel fortsetzen** oder **neues Rätsel generieren**.
  - Sudoku: Schwierigkeit Leicht / Mittel / Schwer (über Anzahl der Vorgaben, Lösung immer eindeutig).
  - Kakuro: Spalten und Zeilen frei wählbar (4–30, inklusive Summenzeile/-spalte).
- **Rätseldauer** oberhalb des Rätsels; die Zeit läuft nur, solange der Rätselbildschirm aktiv ist
  (stoppt beim Verlassen des Bildschirms und wenn die App in den Hintergrund geht).
- Kakuro-Spielfeld lässt sich in **alle Richtungen verschieben** (auch diagonal, mit Schwung),
  wenn es nicht auf den Bildschirm passt.
- Notizmodus, Konfliktmarkierung, Hilfszeile mit möglichen Kakuro-Kombinationen.
- Spielstand wird nach jeder Eingabe automatisch gespeichert (JSON im App-Speicher).

## Aufbau

| Modul  | Inhalt |
|--------|--------|
| `core` | Reines Kotlin: Modelle, Generatoren, Löser, Regelprüfung, Tests (`./gradlew :core:test`). |
| `app`  | Android-App (Compose, Navigation, ViewModel, Speicherung). |

Neue Rätselarten: Eintrag in `PuzzleType`, Unterklasse von `Puzzle` in `core`,
Generator ergänzen und ein Spielfeld in `app/ui/game` hinzufügen.

### Generatoren

- **Sudoku**: zufällige vollständige Lösung, dann Ziffern entfernen, solange die Lösung eindeutig bleibt.
- **Kakuro**: Gitterstruktur würfeln und reparieren (keine Folgen der Länge 1, max. 9, zusammenhängend),
  zufällig füllen, anschließend Ziffern per lokaler Suche so anpassen, dass das Rätsel rein durch logisches
  Schließen lösbar und damit eindeutig ist. Gelingt das im Zeitbudget nicht (selten, nur bei sehr großen
  Gittern), wird ein regelkonformes Rätsel mit Hinweis geliefert; die App erkennt jede regelkonforme Lösung an.

## Bauen

Voraussetzung: Android SDK (Android Studio) und JDK 17.

```
./gradlew :app:assembleDebug
```

Die GitHub-Action `Build` führt die Tests aus und veröffentlicht das Debug-APK als GitHub-Release.

**Download der aktuellen Version:** https://github.com/CarstenKeller/Logicals/releases/latest (Datei `logicals-build-<Nummer>.apk`)
