# Architecture File Split Plan – ViviCast

> Planungsdokument. **Kein Code wurde geändert.** Zeilenzahlen via `wc -l`, Stand 2026-07-01.
>
> Basis: [ARCHITECTURE-AUDIT.md](ARCHITECTURE-AUDIT.md) · Roadmap: [ARCHITECTURE-REFACTORING-PLAN.md](ARCHITECTURE-REFACTORING-PLAN.md)

---

## Zusammenfassung

- **Auffällige große Dateien (Produktivcode):** 6 über der 300-Zeilen-Prüfschwelle relevant für Splits.
- **Kritischste Split-Kandidaten:** `SettingsRoute.kt` (4.879) und `MainActivity.kt` (2.299) – beide P0.
- **Empfohlene Reihenfolge:** SettingsRoute → MainActivity → VivicastComponents; Import-/EPG-Repos nur optional.
- **Wichtigste Risiken:** Verlust von `internal`/`private`-Sichtbarkeit bei Modulwechsel (hier vermieden, da
  alle Splits *innerhalb desselben Package/Moduls* bleiben); Compose-Preview- und Test-Tag-Bezüge.

Alle vorgeschlagenen Splits sind **reine Extraktionen ohne Verhaltensänderung** und bleiben im selben Gradle-Modul
und Package → keine `import`-Brüche über Modulgrenzen, keine DI-Änderung.

---

## Split-Kandidaten

| Datei | Zeilen | Problem | Empfohlene Aufteilung | Priorität | Risiko | Reihenfolge |
|---|---|---|---|---|---|---|
| `feature/settings/…/SettingsRoute.kt` | 4.879 | ~9 Bereiche + UI-Modelle + Add-Wizard in einer Datei | Modelle + 9 Panel-/Dialog-Dateien | **P0** | niedrig | 1 |
| `app/…/MainActivity.kt` | 2.299 | Activity + Nav-Host + 35 Mapper + Playback-Orchestrierung + Dialoge | 4 Dateien (Host/Mapper/Playback/Dialoge) | **P0** | niedrig–mittel | 2 |
| `core/designsystem/…/VivicastComponents.kt` | 1.704 | ~40 Composables in einer Datei (kohäsiv) | 6 Dateien nach Komponentengruppe | **P2** | niedrig | 3 |
| `feature/live-tv/…/LiveTvRoute.kt` | 878 | Screen + State + 4 Repos + EPG-Logik | erst mit State-Holder (P1-04) entzerren | **P2** | mittel | nach P1-04 |
| `feature/series/…/SeriesRoute.kt` | 738 | Screen + State + Detail/Season/Episode | erst mit State-Holder (P1-04) | **P2** | mittel | nach P1-04 |
| `data/media/…/RoomCatalogImportRepository.kt` | 550 | Import-Steuerung + Diff + Upserts + alle DTO→Entity-Mapper | optional: Mapper in `CatalogEntityMappers.kt` | **P3** | niedrig | optional |
| `worker/…/RefreshExecution.kt` | 448 | 15+ Klassen/Interfaces (Runner, Refresher, Fetcher, Guards) | optional: Fetcher/Refresher trennen | **P3** | niedrig | optional |

Nicht gelistete Dateien 300–450 Z. (`RoomEpgRepository` 438, `SettingsRoute`-nahe Panels, `PlayerRoute` 662,
`MoviesRoute` 656) sind kohäsiv genug bzw. werden über P1-04 (State-Holder) mitentlastet – siehe „Nicht aufteilen".

---

## Detailplan pro Datei

### `feature/settings/src/main/java/com/vivicast/tv/feature/settings/SettingsRoute.kt`

- **Aktuelle Zeilenzahl:** 4.879.
- **Aktuelle Verantwortlichkeiten:** (1) alle Settings-UI-Modelle (Z. 101–271: ~15 data classes + ~20 enums);
  (2) `SettingsRoute`-Einstieg + Sektions-Routing (287); (3) Panels: Playback (543), General (1101),
  Appearance (3690), EPG (1494) inkl. Global (1705) + manuellem EPG-Mapping (1827–2168) + EPG-Quellen-CRUD
  (2171–2464), Provider (2466) inkl. Overview/List/Card + Add-Wizard `ProviderAddFlow` (2961–3247) +
  `ProviderEditor` (3347–3625), Parental/PIN (3779–4021), Backup (4024–4193), About (4196–4367),
  Maintenance/Diagnose (4369–4563); (4) diverse `label()`/`next()`-Enum-Helfer, Icons, Dialoge, Test-Tags.
- **Warum auffällig:** > 800 → „sehr wahrscheinlich aufteilen"; bündelt mehrere eigenständige Feature-Bereiche.
- **Zwingend oder optional:** **zwingend (P0).**
- **Empfohlene neue Dateien (selbes Package):**
  - `SettingsModels.kt` – alle `data class`/`enum` aus Z. 101–271.
  - `GeneralSettingsPanel.kt`, `AppearanceSettingsPanel.kt`, `PlaybackSettingsPanel.kt`.
  - `EpgSettingsPanel.kt` (Global + `AdjustableSettingsRow`), `EpgSourceEditor.kt` (Quellen-CRUD +
    `DeleteEpgSourceDialog`), `ManualEpgMappingPanel.kt` (Manual-Mapping-Trio).
  - `ProviderSettingsPanel.kt` (Overview/List/Card), `ProviderAddFlow.kt` (Wizard + `ConnectionTestButton` +
    `ProviderEditor` + `ProviderTextField` + `DeleteProviderDialog`).
  - `ParentalControlPanel.kt` (inkl. `PinDialog` + PIN-Tags), `BackupSettingsPanel.kt`,
    `AboutSettingsPanel.kt`, `MaintenanceSettingsPanel.kt`.
  - Enum-`label()`/`next()`-Helfer jeweils zur zugehörigen Panel-Datei.
- **Was in `SettingsRoute.kt` bleibt:** `settingsSectionsList()`, `SettingsRoute`, `SettingsPanelTitle`,
  `SettingsSectionIcon`/`SettingsRowIcon`, Sektions-Routing. Ziel < ~400 Z.
- **Migrationsreihenfolge:** erst `SettingsModels.kt` (breakt sonst nichts), dann Panel für Panel; nach jedem
  Panel kompilieren.
- **Akzeptanzkriterien:** identisches Verhalten, alle Test-Tags unverändert, jede neue Datei < ~500 Z.
- **Checks:** `.\gradlew.bat :feature:settings:assembleDebug` + `:feature:settings:connectedDebugAndroidTest`.
- **Risiko:** niedrig – Package bleibt, `internal`/`private` bleiben gültig. Achten auf Compose-Previews (falls
  vorhanden) und die vielen `*Tag()`-Funktionen (müssen weiterhin `public`/gleiche Signatur bleiben, da Tests
  sie referenzieren).

### `app/src/main/java/com/vivicast/tv/MainActivity.kt`

- **Aktuelle Zeilenzahl:** 2.299.
- **Aktuelle Verantwortlichkeiten:** `MainActivity` (145); `VivicastApp` Nav-/App-State-Composable
  (188–1372, ~1.180 Z.); ~35 Preference↔UI-Mapper (1464–2293); Playback-Orchestrierung als
  `AppContainer`-Extensions (Bild-Resolver 1786–1837, `openChannelPlayback` 1838, `openMoviePlayback` 1873,
  `createMoviePlaybackRequest` 1884, `openEpisodePlayback` 1921, `openCatchUpPlayback` 1963,
  `savePlaybackProgress` 2009, `clearHistory` 2076); Dialoge (`ProtectionUnlockDialog` 1570,
  `ExternalPlayerChoiceDialog` 1695, `StandardRestoreConfirmDialog` 1722, …).
- **Warum auffällig:** > 800 + mehrere Verantwortlichkeiten/Layer (UI + Mapping + Business-Logik).
- **Zwingend oder optional:** **zwingend (P0).**
- **Empfohlene neue Dateien (selbes Package `com.vivicast.tv`):**
  - `SettingsPreferenceMappers.kt` – die ~35 `toSettings*`/`toDataStore*`-Funktionen (1464–2293, ohne
    Playback-Extensions).
  - `PlaybackOrchestration.kt` – alle `AppContainer.*Playback*`/Bild-Resolver/`savePlaybackProgress`/
    `clearHistory`-Extensions (1786–2091). **Diese Datei ist zugleich Ausgangspunkt für P1-03** (Verlagerung
    in UseCases).
  - `AppDialogs.kt` – die App-Level-Dialoge (1570–1785).
  - `MainActivity.kt` behält `MainActivity` + `VivicastApp` + `AppDestination`/Target-Datenklassen +
    `PinSecurityState.protectionArea*`-Helfer.
- **Was extrahiert wird / was bleibt:** siehe oben. `VivicastApp` bleibt vorerst als Host (Aufteilung des
  Hosts selbst erst sinnvoll nach P1-04, wenn State-Holder existieren).
- **Migrationsreihenfolge:** Mapper → Dialoge → Playback-Extensions; nach jedem Block kompilieren.
- **Akzeptanzkriterien:** `MainActivity.kt` < ~800 Z.; App startet; Navigation/Playback/PIN/Backup unverändert.
- **Checks:** `.\gradlew.bat :app:assembleDebug`; Smoke-`androidTest`s (`M3uPlaybackSmokeTest`,
  `ProtectionGateTest`, `WatchNextIntegrationTest`).
- **Risiko:** niedrig–mittel – `private` Top-Level-Funktionen werden beim Verschieben in andere Datei ggf.
  `internal` (Modul-intern ok). Auf `AppContainer`-Extension-Empfänger achten (bleiben Extensions auf
  `AppContainer`). **Kein** Verhaltenswechsel in diesem Schritt (Logikverlagerung erst P1-03).

### `core/designsystem/src/main/java/com/vivicast/tv/core/designsystem/VivicastComponents.kt`

- **Aktuelle Zeilenzahl:** 1.704.
- **Aktuelle Verantwortlichkeiten:** ~40 wiederverwendbare Composables (Screen/Background, Surfaces/Focus,
  Cards/Poster/Hero, Dialogs/TextField/Buttons, Badges, Search-Result, ProgressLine).
- **Warum auffällig:** > 800, aber **kohäsiv** (alles Designsystem) → Größe ist der einzige Grund.
- **Zwingend oder optional:** **optional (P2).**
- **Empfohlene neue Dateien:** `VivicastSurfaces.kt`, `VivicastCards.kt` (Content/Poster/Hero),
  `VivicastDialogs.kt` (Dialog/Actions/Error), `VivicastInputs.kt` (TextField/ButtonRow),
  `VivicastBadges.kt` (Status/StreamInfo), `VivicastLayout.kt` (Screen/Background/Section/Body). `Vivicast`-Prefix
  beibehalten.
- **Was bleibt:** nichts Spezifisches – Datei kann vollständig aufgeteilt oder als schlanke Sammel-Datei geleert
  werden.
- **Migrationsreihenfolge:** gruppenweise; nach jeder Gruppe kompilieren.
- **Akzeptanzkriterien:** kein Consumer-Import bricht (öffentliche Signaturen unverändert);
  `VivicastTopNavigationFocusTest` grün.
- **Checks:** `.\gradlew.bat :core:designsystem:assembleDebug` + `:core:designsystem:connectedDebugAndroidTest`.
- **Risiko:** niedrig; auf Preview-Funktionen und öffentliche API-Stabilität achten (viele Feature-Module
  importieren diese Composables).

### `feature/live-tv/…/LiveTvRoute.kt` (878) und `feature/series/…/SeriesRoute.kt` (738)

- **Verantwortlichkeiten:** Screen-Rendering + lokaler State + direkte Repository-Beobachtung + (LiveTv)
  EPG-Auswahl-Logik / (Series) Season-/Episode-Detail-Logik.
- **Warum auffällig:** groß **und** mit vermischter State-/Datenlogik (nicht nur Größe).
- **Empfehlung:** **nicht isoliert splitten.** Diese Dateien werden über **P1-04 (State-Holder)** natürlich
  entzerrt: State + Repository-Beobachtung wandert in den Holder, das Composable schrumpft. Ein Split *vor*
  P1-04 würde Arbeit doppelt machen. → **Reihenfolge: nach P1-04.**
- **Risiko bei vorzeitigem Split:** mittel (State-Hoisting/Focus-Restorer-Bezüge). Deshalb warten.

### `data/media/…/RoomCatalogImportRepository.kt` (550)

- **Verantwortlichkeiten:** Import-Steuerung (M3U/Xtream), Kategorie-Diff, Upserts, **alle** DTO→Entity-Mapper
  (`toEntity`) + ID-/Stable-Key-Generierung.
- **Empfehlung:** **optional (P3).** Bei Bedarf `CatalogEntityMappers.kt` (die `toEntity`-Extensions +
  ID-Helfer) herausziehen. Kohäsiv genug, um vorerst zu bleiben. Gut durch
  `RoomCatalogImportRepositoryTest` abgesichert, falls doch gesplittet.

### `worker/…/RefreshExecution.kt` (448)

- **Verantwortlichkeiten:** enthält 15+ Klassen/Interfaces (Runner, PlaylistSource, Playlist-/EPG-/Logo-Refresher,
  Fetcher, Guards, Exceptions).
- **Empfehlung:** **optional (P3).** Bei Bedarf nach Verantwortung trennen (`Fetchers.kt`, `Refreshers.kt`,
  `RefreshExceptions.kt`). Aktuell akzeptabel, da alle Klassen zum Refresh-Ablauf gehören und unit-getestet sind
  (`RefreshExecutionTest`).

---

## Nicht aufteilen (trotz Größe)

| Datei | Zeilen | Warum vorerst nicht splitten |
|---|---|---|
| `core/player/…/VivicastPlayerController.kt` | 657 | Referenzqualität: klar getrennte Rollen (Controller-Interface, `PlaybackRequest`/`VivicastPlayerState`, Default-Impl). Interface+Impl+State bewusst zusammen. Eigener Unit-Test. Split brächte Risiko ohne Nutzen. |
| `feature/player/…/PlayerRoute.kt` | 662 | Ein kohärenter Screen; steuert Player **korrekt** nur über `playerController.state`. Overlay-Logik gehört zusammen. Erst mit P1-04 minimal entlasten, kein eigener Split. |
| `feature/movies/…/MoviesRoute.kt` | 656 | Wie LiveTv/Series: über P1-04 (State-Holder) entlasten, nicht separat splitten. |
| `core/database/…/VivicastMigrations.kt` | 687 | Migrationen sind naturgemäß lang und müssen zusammen/chronologisch stehen. Splitten erhöht Fehlerrisiko bei Schemaänderungen. Nicht anfassen. |
| `core/database/…/model/VivicastEntities.kt` | 393 | Alle Room-Entities gebündelt – kohäsiv, gut so. |
| `data/epg/…/RoomEpgRepository.kt` | 438 | Eine Verantwortung (EPG-Persistenz/-Import), gut getestet (`RoomEpgRepositoryTest`). Kein Split nötig. |
| `domain/…/VivicastModels.kt` | 228 | Zentrale Domain-Modelle bewusst an einem Ort. Unter Schwelle. |
