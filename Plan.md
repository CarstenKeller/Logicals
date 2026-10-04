# Plan: Logicals als Web-App (GitHub Pages) für iPhone-Nutzer

Stand: 2026-10-04 · Ausgangspunkt: Branch `main` (Android-App Build 13, Versionsname 1.0.13)

## Ziel

Die bestehende Android-App bleibt unverändert erhalten und wird weiter gepflegt. Zusätzlich gibt es eine
Web-App mit demselben Funktionsumfang, die auf iPhones (iOS 26+, Safari) läuft, sich „Zum
Home-Bildschirm“ hinzufügen lässt und offline funktioniert. Gehostet wird sie kostenlos über
**GitHub Pages**, veröffentlicht automatisch per GitHub Actions.

Nicht-Ziele: native iOS-App, App Store, Server-Backend, Benutzerkonten, Synchronisation zwischen Geräten.

## Grundsatzentscheidungen (bereits getroffen)

1. **Ein Repo, ein Hauptbranch, mehrere Module** – kein langlebiger Web-Branch.
   - `core/` → wird zu **Kotlin Multiplatform** (JVM für Android + Web-Target). Rätsel-Logik,
     Generatoren, Hinweise, Einfach-Modus und Tests existieren nur einmal.
   - `app/` → Android-App, funktional unverändert.
   - `web/` → neue Web-App.
   - Arbeit in Feature-Branches, Merge nach `main` per Pull Request.
2. **Hosting: GitHub Pages** (nicht auf einem privaten Gerät). Reine statische Seite, HTTPS inklusive.
   Erwartete URL: `https://carstenkeller.github.io/Logicals/` (Unterpfad beachten!).
3. **UI-Ansatz: zuerst Prototyp mit Compose Multiplatform (Web/Wasm)**, weil sich die vorhandene
   Compose-Oberfläche weitgehend wiederverwenden lässt. iOS 26+ unterstützt WasmGC (laut Kenntnisstand
   seit Safari/iOS 18.2) – **vor Beginn verifizieren**.
   Fallback (Variante B), falls der Prototyp auf dem iPhone nicht überzeugt: gemeinsamer Kern nach JS/Wasm
   kompiliert + schlanke eigene HTML/CSS-Oberfläche.

## Vor dem Start zu prüfen (Wissensstand des Planerstellers kann veraltet sein)

Ergebnis der Prüfung am 2026-10-04: Compose MP 1.12.1 (Web: Beta), Kotlin 2.4.20 (unterstützt AGP 8.11.1 und
Gradle 8.14.3); JetBrains lifecycle 2.11.0 und navigation-compose 2.9.2 gibt es für wasmJs; WasmGC ab Safari 18.2;
Home-Bildschirm-Web-Apps sind von der 7-Tage-Löschung ausgenommen; Emoji erfordern eine mitgelieferte Schrift
(Compose Web nutzt nicht die Apple-Emoji-Schrift); Bundle-Größe wird in Phase 2 gemessen.

- [x] Aktueller Status von **Compose Multiplatform für Web (wasmJs)**: stabil/Beta? Welche Version passt
      zu welcher Kotlin-Version? (Projekt nutzt derzeit Kotlin 2.1.21, AGP 8.11.1, Gradle 8.14.3.)
- [x] Sind `lifecycle-viewmodel`, `navigation-compose` (JetBrains-Varianten) für wasmJs verfügbar, oder
      ist eine eigene einfache Navigation/State-Haltung sinnvoller?
- [x] Safari iOS 26: WasmGC, Speicher-Verhalten (Wird `localStorage`/IndexedDB einer Web-App gelöscht,
      wenn sie nicht zum Home-Bildschirm hinzugefügt wurde? Laut Kenntnisstand: ja, nach Inaktivität möglich.)
- [x] Emoji-Darstellung (🐱🐶🦴💡) in Compose Web: werden Emoji-Schriften benötigt/mitgeladen?
- [ ] Download-Größe des Wasm-Bundles (Ziel: Erstladen auf dem Handy in vertretbarer Zeit).

## Phasen

### Phase 0 – Vorbereitung (durch den Nutzer, einmalig in GitHub)
- Settings → General → Default branch auf **`main`** stellen (falls noch nicht geschehen).
- Settings → Pages → Source: **„GitHub Actions“** auswählen (sonst kann der Workflow nicht deployen).
- Alter Branch `claude/android-logic-puzzle-app-4zboyu` kann danach gelöscht werden.

### Phase 1 – `core` zu Kotlin Multiplatform (Android bleibt grün) – erledigt
- Plugin `org.jetbrains.kotlin.multiplatform`, Targets: `jvm()` und `wasmJs { browser() }`
  (ggf. zusätzlich `js(IR)`, falls für Variante B nötig).
- Quellen nach `core/src/commonMain/kotlin`, Tests nach `core/src/commonTest/kotlin`
  (`kotlin.test` statt JUnit-Asserts; `kotlin.test` bietet assertEquals/assertTrue/assertFalse).
- JVM-spezifische Aufrufe ersetzen (vollständige Liste, Stand heute):
  - `Integer.bitCount(x)` → `x.countOneBits()`
  - `Integer.numberOfTrailingZeros(x)` → `x.countTrailingZeroBits()`
  - `Integer.numberOfLeadingZeros(x)` → `x.countLeadingZeroBits()`
  - `Integer.lowestOneBit(x)` → `x.takeLowestOneBit()`
  - `System.nanoTime()` (nur `KakuroGenerator`, Zeitbudget) → `kotlin.time.TimeSource.Monotonic`
- `kotlinx.serialization` funktioniert multiplatform; JSON-Format der Spielstände **unverändert lassen**
  (bestehende Android-Spielstände müssen weiter ladbar sein).
- `app` hängt weiter an `project(":core")` (JVM-Variante wird automatisch gewählt).
- Abnahme: alle bisherigen Core-Tests laufen auf JVM **und** wasmJs; Android-CI-Build grün; APK verhält
  sich unverändert.

### Phase 2 – Prototyp `web/` (nur Hauptmenü + Sudoku) – umgesetzt, Test auf iPhone offen
- Neues Modul `web` mit Compose Multiplatform, Target `wasmJs { browser() }`, Ausgabe als statische Seite.
- Wiederverwendung: Board-Composables, Theme, NumberPad, Timer-Logik – soweit ohne Android-APIs möglich.
  Gemeinsam nutzbare UI später ggf. in ein Modul `shared-ui` (commonMain) verschieben, damit Android und
  Web denselben Code verwenden. Für den Prototyp darf zunächst kopiert werden; Entscheidung in Phase 3.
- Ersetzen von Android-Spezifika:
  - Speicherung (`GameRepository` mit Dateien, `Settings` mit SharedPreferences) → `localStorage`
    (gleiches JSON wie auf Android).
  - `ViewModel`/`SavedStateHandle`/`LifecycleResumeEffect` → einfache State-Holder; Timer pausiert bei
    `document.visibilitychange` (Seite unsichtbar) statt Activity-Lifecycle.
  - Navigation → einfache Bildschirm-Zustandsmaschine (oder JetBrains navigation-compose, falls verfügbar).
  - Strings aus `strings.xml` → Compose-Resources oder Kotlin-Konstanten (Deutsch).
- PWA-Grundlagen: `manifest.webmanifest` (Name, Icon, `display: standalone`, `start_url`/`scope` relativ
  zum Unterpfad `/Logicals/`), Apple-Touch-Icon, Meta-Tags für iOS; Service Worker für Offline-Cache.
- GitHub-Actions-Job: Web bauen und mit `actions/upload-pages-artifact` + `actions/deploy-pages` nach
  Pages deployen – **nur bei Push auf `main`**.
- Abnahme: Seite lädt auf iPhone (iOS 26) in Safari, Sudoku spielbar inkl. Zoom/Verschieben, Hinweis,
  Einfach-Modus, Timer, Speichern/Fortsetzen; „Zum Home-Bildschirm“ startet im Vollbild; offline nutzbar.
  **Test auf echtem iPhone durch den Nutzer/Familie** (in der Cloud-Umgebung nicht möglich).

### Phase 3 – Entscheidung A/B – Variante A gewählt, umgesetzt
- Gemeinsames Modul `shared-ui` (Android-Bibliothek + wasmJs) mit allen Bildschirmen, Spielfeldern,
  Spiellogik (`GameController`) und Texten; `app/` und `web/` enthalten nur noch Plattformteile.
- Compose Multiplatform 1.11.1 statt 1.12.x: Compose 1.12 verlangt auf Android compileSdk 37 und AGP 9.1+.
  Material3 1.9.0 (stabil). Android: compileSdk 36, targetSdk/minSdk unverändert.
- Prototyp gut → Variante A fortsetzen, gemeinsame UI nach `shared-ui` (commonMain) ziehen, sodass Android
  und Web dieselben Composables nutzen.
- Prototyp nicht gut (Ladezeit, Gesten, Darstellung) → Variante B: `core` als JS/Wasm-Bibliothek + eigene
  HTML/CSS/TS-Oberfläche in `web/`.

### Phase 4 – Vollständige Portierung
- Alle Rätsel: Sudoku, Kakuro, Futoshiki, KenKen, Skyscraper, Catsweeper.
- Alle Funktionen: Hauptmenü, Fortsetzen/Neu mit Optionen (Größe, Schwierigkeit), Timer (pausiert beim
  Verlassen), Notizen, Konfliktmarkierung, 💡-Hinweise mit Erklärung und Hervorhebung, Einfach-Modus,
  Zwei-Finger-Zoom + Verschieben, Hell/Dunkel/System, Abschlussdialog mit „Nochmal“.
- iOS-spezifisch prüfen: Safari-eigene Gesten (Doppeltipp-Zoom, Pinch-Zoom der Seite, Langdruck-Menü,
  Text-Auswahl) im Spielfeld unterdrücken (`touch-action`, `user-select`, Viewport-Meta), sichere Bereiche
  (Notch/Home-Indikator).
- Abnahme: Funktionsgleichheit mit Android, Tests grün, Pages-Deployment automatisch.

### Phase 5 – Verteilung & Doku
- README: Abschnitt Web-App mit URL und Anleitung „Safari → Teilen → Zum Home-Bildschirm“.
- Hinweis an Nutzer: Spielstände liegen nur lokal im Browser des Geräts.

## Risiken & Gegenmaßnahmen

| Risiko | Gegenmaßnahme |
|---|---|
| Compose Web noch nicht ausgereift / Version passt nicht zu Kotlin 2.1.21 | Kotlin-Version gemeinsam anheben (Android-CI prüft mit); sonst Variante B |
| Wasm-Build braucht Node.js/Binaryen-Downloads, die in der Cloud-Umgebung blockiert sein können | Web-Build dann ausschließlich in GitHub Actions validieren (wie bisher beim Android-Build) |
| Safari löscht Browserspeicher | Nutzer auf „Zum Home-Bildschirm“ hinweisen |
| Unterpfad `/Logicals/` bricht Pfade/Service Worker | Alle Pfade relativ; Scope explizit setzen; nach Deploy prüfen |
| Doppelte UI-Pflege (Variante B) | Möglichst viel Logik (inkl. Kandidaten, Hinweise, Validierung) im gemeinsamen `core` halten |
