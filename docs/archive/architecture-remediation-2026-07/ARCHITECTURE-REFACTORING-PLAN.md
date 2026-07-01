# Architecture Refactoring Plan – ViviCast

> Planungsdokument. **Kein Code wurde geändert.** Reihenfolge ist risikoorientiert (erst mechanische
> Datei-Splits mit Testabsicherung, dann strukturelle Layer-Einführung).
>
> Basis: [ARCHITECTURE-AUDIT.md](ARCHITECTURE-AUDIT.md) · Splits: [ARCHITECTURE-FILE-SPLIT-PLAN.md](ARCHITECTURE-FILE-SPLIT-PLAN.md)

---

## Zielbild

**Gewählte Strategie: Single-Module-Gedanke NICHT nötig – Multi-Module beibehalten und *innerhalb* der
Feature-Module den fehlenden Presentation-State-Holder-Layer ergänzen; zwei God-Files auflösen.**

Begründung: Der Modulschnitt (30 Module, Now-in-Android-konform, kein Zyklus, korrekte Abhängigkeitsrichtung)
ist bereits gut. Das Problem ist **nicht** die Modularisierung, sondern (a) fehlende ViewModels/State-Holder
und (b) zwei überladene Dateien. Beides wird *innerhalb* der bestehenden Module gelöst – ohne neue Module,
ohne Package-Verschiebungen zwischen Modulen, ohne DI-Framework-Wechsel.

Nicht Teil des Zielbilds: Hilt-Migration, flächendeckende UseCases, `Either`-Fehlerbehandlung,
Player-Neuarchitektur, UI-Redesign, neue Module.

---

## Refactoring-Roadmap

Reihenfolge bewusst: **erst risikoarme Extraktion (kein Verhaltenswechsel), dann Layer-Einführung.** Die
Datei-Splits (P0-01/02) machen die spätere State-Holder-Einführung (P1-*) überhaupt erst handhabbar.

### P0-01 – `SettingsRoute.kt` datei-splitten
- **Ziel:** 4.879-Zeilen-Datei in kohäsive Panel-Dateien im selben Package zerlegen (reine Extraktion).
- **Warum nötig:** höchstes Merge-/Regressionsrisiko, unlesbar, blockiert jede Settings-Weiterarbeit.
- **Betroffen:** nur `feature/settings/src/main/java/com/vivicast/tv/feature/settings/` (neue Dateien im
  selben Package; `internal`/`private`-Sichtbarkeiten bleiben gültig, da Package identisch).
- **Risiko:** niedrig (kein Verhaltenswechsel, nur Verschiebung; Compose-Previews/Test-Tags bleiben).
- **Schwierigkeit:** mittel (viel Code, aber mechanisch).
- **Abhängigkeiten:** keine.
- **Akzeptanzkriterien:** identisches Verhalten; `SettingsRoute.kt` < ~400 Z.; jede Panel-Datei < ~500 Z.;
  alle Settings-`androidTest`s grün.
- **Checks danach:** `.\gradlew.bat :feature:settings:assembleDebug`, dann
  `.\gradlew.bat :feature:settings:connectedDebugAndroidTest` (Emulator).
- **NICHT Teil:** keine State-Holder-Einführung, keine Logikänderung, keine Model-Umbenennung.

### P0-02 – `MainActivity.kt` entflechten (Extraktion)
- **Ziel:** Datei in vier Teile trennen: (a) `MainActivity`+`VivicastApp` (Navigations-Host), (b)
  `SettingsPreferenceMappers.kt`, (c) `PlaybackOrchestration.kt` (die `AppContainer`-Extensions), (d)
  `AppDialogs.kt`.
- **Warum nötig:** zentrale Fehlerquelle; Voraussetzung für P1-03 (Playback-UseCases).
- **Betroffen:** nur `app/src/main/java/com/vivicast/tv/` (neue Dateien im selben Package `com.vivicast.tv`).
- **Risiko:** niedrig–mittel (viele `private` Top-Level-Funktionen → beim Verschieben ggf. `internal`
  innerhalb des Moduls; Package bleibt gleich).
- **Schwierigkeit:** mittel.
- **Abhängigkeiten:** keine (kann parallel zu P0-01).
- **Akzeptanzkriterien:** `MainActivity.kt` < ~800 Z.; App startet, Navigation + Playback unverändert.
- **Checks danach:** `.\gradlew.bat :app:assembleDebug`; App-Smoke-Test (`M3uPlaybackSmokeTest`,
  `ProtectionGateTest`, `WatchNextIntegrationTest`).
- **NICHT Teil:** noch keine Verlagerung der Playback-Logik in `data`/`domain` (das ist P1-03) – hier nur
  Datei-Trennung innerhalb `:app`.

### P1-03 – Playback-Orchestrierung in UseCases/Repository ziehen
- **Ziel:** `openChannelPlayback`, `openMoviePlayback`, `createMoviePlaybackRequest`, `openEpisodePlayback`,
  `openCatchUpPlayback`, `savePlaybackProgress`, `clearHistory` von `AppContainer`-Extensions in echte,
  testbare UseCases (`:domain` oder `:data:playback`) überführen.
- **Warum nötig:** derzeit untestbare Business-Logik im UI-Modul.
- **Betroffen:** die in P0-02 extrahierte `PlaybackOrchestration.kt`; `:data:playback`; `:domain`.
- **Risiko:** mittel (Verhaltenserhalt bei Stream-Auflösung/Progress kritisch).
- **Schwierigkeit:** mittel–hoch.
- **Abhängigkeiten:** nach P0-02.
- **Akzeptanzkriterien:** neue UseCase-Unit-Tests mit Fake-Repos grün; Playback-Smoke-Tests grün.
- **Checks:** `.\gradlew.bat :data:playback:test :domain:test`, dann `:app:connectedDebugAndroidTest`.

### P1-04 – State-Holder pro Feature einführen (schrittweise, feature-für-feature)
- **Ziel:** je Feature ein `XxxViewModel`/State-Holder mit `StateFlow<XxxUiState>`; Repositories aus den
  Composables in den Holder ziehen; Route erhält `uiState` + Event-Lambdas.
- **Warum nötig:** Kern-Gap – Screen-Logik testbar + lifecycle-sicher machen.
- **Betroffen:** ein Feature nach dem anderen (Vorschlag-Reihenfolge: `search` → `home` → `movies` →
  `series` → `live-tv`; `settings` zuletzt, weil größter Umfang).
- **Risiko:** mittel (pro Feature isoliert → kleiner Blast-Radius).
- **Schwierigkeit:** mittel.
- **Abhängigkeiten:** P0-01 (für settings), sonst unabhängig.
- **Akzeptanzkriterien:** Composable ruft keine Repository-Methode mehr direkt; State-Holder-Unit-Test mit
  Fake-Repo; bestehende Fokus-`androidTest`s grün.
- **Checks:** `.\gradlew.bat :feature:<name>:test :feature:<name>:connectedDebugAndroidTest`.
- **NICHT Teil:** kein Hilt (Konstruktion vorerst weiter über `AppContainer`/Factory).

### P1-05 – `collectAsState` → `collectAsStateWithLifecycle`
- **Ziel:** lifecycle-aware Collection überall.
- **Warum:** Google-Empfehlung; verhindert Sammeln im Hintergrund.
- **Betroffen:** alle Feature-Routes + `MainActivity:399` (idealerweise nach P1-04, wo Collection in
  Holder/Route zusammengeführt ist).
- **Risiko:** niedrig. **Schwierigkeit:** niedrig.
- **Akzeptanzkriterien:** kein `androidx.compose.runtime.collectAsState`-Import mehr in Feature-Routes.
- **Checks:** `.\gradlew.bat assembleDebug`.

### P2-06 – `VivicastComponents.kt` nach Kategorie splitten
- **Ziel:** 1.704-Zeilen-Designsystem in Dateien pro Komponentengruppe (Surfaces, Cards, Dialogs, TextFields,
  Hero/Poster, Badges). Reine Extraktion.
- **Risiko:** niedrig (kohäsiv). **Schwierigkeit:** niedrig. **Abhängigkeiten:** keine.
- **Checks:** `.\gradlew.bat :core:designsystem:assembleDebug` +
  `:core:designsystem:connectedDebugAndroidTest`.

### P2-07 – Provider-Verbindungstest aus `AppContainer` extrahieren
- **Ziel:** `testProviderConnection/testM3uConnection/testXtreamConnection` (AppContainer Z. 291–336) in ein
  `TestProviderConnectionUseCase` (`:data:provider`).
- **Risiko:** niedrig. **Schwierigkeit:** niedrig. **Abhängigkeiten:** keine.
- **Akzeptanzkriterien:** `AppContainer` nur noch Verdrahtung; UseCase mit Fake-Transport unit-getestet.

### P2-08 – detekt + Zeilenlimit-Gate
- **Ziel:** detekt im Root-Build, Regel `LongMethod`/`LargeClass`/File-Length als Warnung/Fehler.
- **Warum:** verhindert Wiederentstehen von God-Files.
- **Risiko:** niedrig (nur Build-Config, additiv). **Schwierigkeit:** niedrig.
- **Checks:** `.\gradlew.bat detekt` (neue Task).

### P3-09 – Dispatcher injizieren (worker/iptv)
- **Ziel:** `Dispatchers.IO` in `worker/RefreshExecution.kt` (Z. 312/334) und
  `iptv/xtream/XtreamContracts.kt` (Z. 103) als injizierten `CoroutineDispatcher` (Muster wie Player).
- **Risiko:** niedrig. **Schwierigkeit:** niedrig. Optional/kosmetisch.

---

## Empfohlene erste 3 Umsetzungsschritte (besonders konkret)

### 1. P0-01 – `SettingsRoute.kt` splitten
- **Warum genau diese Reihenfolge:** größter einzelner Schmerzpunkt, rein mechanisch, **null** Verhaltensänderung,
  vollständig durch bestehende `SettingsDialogFocusTest`/`SettingsGeneralPanelTest`/`SettingsPlaybackPanelTest`/
  `SettingsRouteInitialSectionTest` abgesichert. Blockiert nichts, macht aber alles Folgende lesbar.
- **Nutzen:** Datei von 4.879 auf ~400 Zeilen; jedes Settings-Panel isoliert bearbeitbar.
- **Risiko:** minimal – Package bleibt identisch, daher bleiben `internal`/`private`-Referenzen gültig.
- **Erfolg erkennbar an:** `SettingsRoute.kt` enthält nur noch Einstieg + Sektions-Routing;
  `:feature:settings:connectedDebugAndroidTest` grün; keine geänderten Test-Tags.

### 2. P0-02 – `MainActivity.kt` entflechten
- **Warum diese Reihenfolge:** zweiter God-File; die Extraktion der `PlaybackOrchestration.kt` ist die
  Voraussetzung, um in Schritt-danach (P1-03) die Logik testbar zu machen.
- **Nutzen:** `MainActivity` wird zum reinen Navigations-Host; Mapper/Playback/Dialoge getrennt sichtbar.
- **Risiko:** niedrig–mittel – auf Verhaltenserhalt bei Playback/PIN achten; durch `M3uPlaybackSmokeTest`,
  `ProtectionGateTest`, `WatchNextIntegrationTest` abgedeckt.
- **Erfolg erkennbar an:** App startet, Navigation/Playback/PIN unverändert; `MainActivity.kt` < ~800 Z.

### 3. P1-04 (Pilot: `:feature:search`) – erster State-Holder
- **Warum diese Reihenfolge:** `SearchRoute` ist der kleinste Screen (393 Z., nur `mediaRepository`), ideal als
  Referenz-Implementierung des State-Holder-Musters, das dann auf die übrigen Features kopiert wird.
- **Nutzen:** etabliert `StateFlow<SearchUiState>` + Fake-Repo-Unit-Test als Vorlage; beweist das Muster mit
  minimalem Risiko.
- **Risiko:** niedrig (ein kleines Feature, isoliert).
- **Erfolg erkennbar an:** `SearchRoute` ruft `mediaRepository` nicht mehr direkt; neuer
  `SearchViewModelTest` (unit) grün; `SearchRouteFocusTest` (androidTest) grün.

---

## Nicht sofort umsetzen (bewusst später)

- **Hilt-Migration** – erst nach P1-04, wenn ViewModels existieren.
- **Flächendeckende UseCases** – nur die drei benannten (Playback, EPG-Mapping, Verbindungstest).
- **Neue Module / Modul-Umschnitt** – nicht nötig, aktueller Schnitt ist gut.
- **Player-Neuarchitektur** – Player ist Referenzqualität, nicht anfassen.
- **`Either`/funktionale Fehlerbehandlung** – `AppResult` behalten.
- **UI-/TV-Design-Überarbeitung** – außerhalb dieses Architektur-Scopes.
- **`RoomCatalogImportRepository`-Split** – kohäsiv, nur optional (P2, siehe Split-Plan).

---

## Validierungsplan

Tatsächlich vorhandene bzw. Standard-AGP-Tasks (Windows-Wrapper `.\gradlew.bat`):

| Zweck | Befehl |
|---|---|
| Kompilieren (alles) | `.\gradlew.bat assembleDebug` |
| Unit-Tests (alles) | `.\gradlew.bat test` |
| Unit-Tests je Modul | `.\gradlew.bat :feature:search:test` / `:data:playback:test` / `:core:player:test` |
| Instrumented-Tests (Emulator nötig) | `.\gradlew.bat connectedDebugAndroidTest` bzw. je Modul `:feature:settings:connectedDebugAndroidTest` |
| Android-Lint (Standard-AGP) | `.\gradlew.bat :app:lintDebug` |
| detekt | **existiert noch nicht** – erst nach P2-08 verfügbar (`.\gradlew.bat detekt`) |

Hinweise: Es gibt **kein** detekt/ktlint/CI im Repo (Stand Audit) – nicht erfinden, sondern in P2-08 hinzufügen.
Emulator via `scripts\start-tv-emulator.ps1` (siehe `CLAUDE.md`). Nach jedem P0/P1-Schritt mindestens
`assembleDebug` + die betroffenen `androidTest`s des jeweiligen Moduls laufen lassen.
