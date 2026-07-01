# Architecture Remediation Completion Report

> Final read-only audit of the P0–P3 architecture refactoring roadmap. No code/gradle/test changes.
> Basis: HEAD `698c54b7` (P3-09 committed), working tree clean.

## 1. Executive Summary

**Gesamtstatus: abgeschlossen** (alle geplanten Pflichtschritte umgesetzt; nur bewusst-optionale
Punkte offen).

Die im ursprünglichen Audit benannten God-Files und Layer-Verletzungen sind behoben. Die
Presentation-Schicht folgt jetzt durchgängig dem AndroidX-ViewModel/UiState-Muster; die
Playback-Orchestrierung und der Provider-Verbindungstest liegen in testbaren `:data`-Klassen; ein
detekt-Gate schützt gegen neue God-Files.

**Wichtigste Verbesserungen:**
- 6/6 Feature-Bereiche mit ViewModel + immutablem UiState; keine direkten Repository-Flows/CRUD mehr in Composables.
- `MainActivity.kt` (2299 Z.) und `SettingsRoute.kt` (4879 Z.) mechanisch entflochten.
- Playback-Request-Building + Progress/History-Persistenz in `:data:playback` (Unit-getestet, injizierbare Clock).
- Provider-Connection-Test als UseCase in `:data:provider` (Unit-getestet, kein Netzwerk).
- `VivicastComponents.kt` (1704 Z.) in 9 kohäsive Designsystem-Dateien gesplittet.
- detekt-Gate + Baseline; lifecycle-aware Flow-Collection app-weit.
- Injizierbare IO-Dispatcher in worker/xtream.

**Wichtigste verbleibende Risiken:** gering. Ein paar bewusst app-hoisted Bereiche (Backup, PIN, About,
WatchNext) sind Context/SAF/Keystore-gebunden und bleiben schwerer testbar; die detekt-Baseline (36)
toleriert bekannte große Composables/Routes. Keine offene Architekturverletzung.

## 2. Roadmap-Abgleich

| Schritt | Status | Nachweis | Bemerkung |
|--------|--------|----------|-----------|
| P0-01 SettingsRoute split | ✅ | `feature/settings/…` viele Panels statt einer Datei | mechanisch |
| P0-01b EPG/Provider Subsplit | ✅ | `EpgSettingsPanel`, `EpgSourceEditor`, `ProviderSettingsPanel`, `ProviderAddFlow`, `ProviderEditor`, `ProviderDialogs` | — |
| P0-02 MainActivity entflechten | ✅ | `SettingsPreferenceMappers.kt`, `AppDialogs.kt`, `PlaybackOrchestration.kt` | — |
| P1-03a Playback Scope Audit | ✅ | `docs/PLAYBACK-ORCHESTRATION-MIGRATION-PLAN.md` | — |
| P1-03b Movie/Episode RequestFactory | ✅ | `PlaybackRequestFactory.movieRequest/episodeRequest` | +18 Tests |
| P1-03c Channel/Catch-up RequestFactory | ✅ | `PlaybackRequestFactory.channelRequest/catchUpRequest` + `canStartCatchUp` | — |
| P1-03d PlaybackProgressRecorder | ✅ | `PlaybackProgressRecorder.record` | +13 Tests |
| P1-03e App-Wiring final | ✅ | `PlaybackOrchestration.kt` nur Wiring; Smoke-Suite grün | — |
| P1-03f Image-Resolver | 🟡 | `resolveXxxImageModel` noch in `PlaybackOrchestration.kt` | bewusst optional/offen |
| P1-04a SearchViewModel | ✅ | `SearchViewModel`+`SearchUiState` | — |
| P1-04b HomeViewModel | ✅ | `HomeViewModel`+`HomeUiState` | — |
| P1-04c MoviesViewModel | ✅ | `MoviesViewModel`+`MoviesUiState` | Detail-Auto-Close-Guard (P1-04c-fix) |
| P1-04d SeriesViewModel | ✅ | `SeriesViewModel`+`SeriesUiState` | — |
| P1-04e LiveTvViewModel | ✅ | `LiveTvViewModel`+`LiveTvUiState` | — |
| P1-04f SettingsViewModel | ✅ | `SettingsViewModel`+`SettingsUiState` (f1–f5) | `docs/SETTINGS-APP-HOISTED-DECISIONS.md` |
| P1-05 collectAsStateWithLifecycle | ✅ | 6 Routes lifecycle-aware; nur PlayerRoute nutzt `collectAsState()` | begründete Ausnahme |
| P2-06 Designsystem-Split | ✅ | 9 `Vivicast*.kt`; `VivicastComponents.kt` entfernt | — |
| P2-07 Provider-Connection-Test-UseCase | ✅ | `TestProviderConnectionUseCase` in `:data:provider` | +11 Tests |
| P2-08 detekt-Gate | ✅ | `config/detekt/*`, `docs/DETEKT-GATE.md` | Baseline 36 |
| P3-09 Dispatcher-Injektion | ✅ | `ioDispatcher` in worker/xtream Fetcher/Transport | — |

## 3. Presentation Layer Status

| Feature | ViewModel | UiState | Direkte Repo-Flows in UI? | Direkte Repo-CRUD in UI? | Bemerkung |
|--------|-----------|---------|---------------------------|--------------------------|-----------|
| search | ✅ | ✅ | ❌ | ❌ | Route liest `uiState` via `collectAsStateWithLifecycle` |
| home | ✅ | ✅ | ❌ | ❌ | dto |
| movies | ✅ | ✅ | ❌ | ❌ | dto |
| series | ✅ | ✅ | ❌ | ❌ | dto |
| live-tv | ✅ | ✅ | ❌ | ❌ | dto |
| settings | ✅ | ✅ | ❌ | ❌ | Repos nur an VM-Factory gereicht; Panels frei |
| player | (Controller) | — | Player-State via `collectAsState()` | ❌ | Echtzeit-Player, bewusste Ausnahme (kein Repo) |

Verbleibende `LaunchedEffect`/`launch`/`rememberCoroutineScope` in Routes/Panels sind Fokus-/Dialog-/
Navigations- oder lokalisiertes-Messaging (bei `suspend Result`-VM-Events) — akzeptabel.

## 4. Settings Status

- **Im ViewModel:** General, Appearance, Playback, EPG-global, Diagnostics-DataStore, Cache/Maintenance,
  EPG-Quellen (+CRUD), Provider↔EPG-Links, Manual-Mapping (+Channel-Reset), Provider-Overview-Liste,
  Provider-CRUD (getCredentials/create/update/setEnabled/delete).
- **Bewusst app-hoisted (lt. `SETTINGS-APP-HOISTED-DECISIONS.md`):** Backup (SAF/Passphrase), Parental/PIN
  (Keystore/`syncWatchNext`/Context-Strings), About (`context.aboutAppState()`), Diagnostics-Export &
  Support-Copy (SAF/Clipboard), Global Refresh / ProviderSaved / BackgroundRefresh-Scheduler
  (WorkManager), Clear History, Connection-Test (Netzwerk → jetzt P2-07-UseCase, App-Callback),
  M3U-Picker (SAF), Locale/Recreate (Activity).
- **Reste:** Keine direkten Repository-Flows/CRUD in Panels (verifiziert leer). Keine neuen
  detekt-Verstöße. Ungenutzte `collectAsState`-Imports wurden in P1-05 entfernt; verbleibende
  ungenutzte Imports nach P2-06-Splits sind Warnungen (detekt-Style aus), kein Gate-Problem.

## 5. Playback Status

- **RequestFactory:** Movie/Episode/Channel/Catch-up-Request-Building vollständig in
  `PlaybackRequestFactory` (`:data:playback`). Kein `PlaybackRequest(`-Konstruktor mehr im App-Modul.
- **ProgressRecorder:** Progress-/Channel-History-Persistenz vollständig in `PlaybackProgressRecorder`.
- **`PlaybackOrchestration.kt`:** nur noch 4 Image-Resolver + dünne `open*`/`create*`-Delegation +
  `savePlaybackProgress`-Delegation + `clearHistory`.
- **App-hoisted:** `playerController.play`, `timeshiftConfig()`-Mapping, Player-State-Loop,
  Throttle-Map, WatchNext-Sync (`app/system/`), `clearHistory`, Image-Resolver.
- **PlayerController/PlayerRoute:** unverändert.
- **P1-03f Image-Resolver:** 🟡 optional/offen — reine `core:cache`-Reads, kein Playback-Bezug; kein
  zwingender Bedarf.
- **Tests:** `:data:playback` Unit 48 grün (Factory 18, Recorder 13, Rules 6, Resolver 11); Smoke-Suite
  (M3u/ProtectionGate/WatchNext, 8 Tests) in P1-03e auf Emulator grün.

## 6. AppContainer / Provider Connection Status

- Verbindungstestlogik aus `AppContainer` **raus** → `TestProviderConnectionUseCase` (`:data:provider`).
- `AppContainer.testProviderConnection` delegiert nur noch (`runCatching { useCase.test() }.fold(...)`).
- Deutsche UI-Message-Mappings (`toProviderConnectionMessage`) bleiben bewusst im App-Modul.
- `DefaultXtreamClient`/`OkHttpTextFetcher` im AppContainer nur noch als **Wiring** (Konstruktion der
  Impls für UseCase + Katalog-Import), keine Netzwerk-/Parser-Ablauflogik.

## 7. Dispatcher Status

- **Kein** hartkodiertes `withContext(Dispatchers.IO)` mehr in `worker/RefreshExecution.kt` oder
  `iptv/xtream/XtreamContracts.kt`. Verbleibende `Dispatchers.IO` dort sind **nur** Default-Werte für
  `ioDispatcher: CoroutineDispatcher = Dispatchers.IO`.
- **Außerhalb P3-09-Scope (nur dokumentiert, nicht geändert):** `BootLaunchReceiver.kt`,
  `VivicastApplication.kt` (`CoroutineScope(Dispatchers.IO)`), `AndroidTvSearchSuggestionProvider.kt`
  (`runBlocking(Dispatchers.IO)`). Das sind Android-Entry-Points (Boot-Receiver, Application.onCreate,
  ContentProvider), keine injizierbaren Services — bewusst belassen.

## 8. Detekt / File Size Status

- **detekt grün** (`./gradlew detekt` SUCCESS).
- **Baseline: 36 Einträge** — bekannte große Composables/Routes (`PlayerRoute`, `VivicastApp`,
  `RoomLiveTvRoute`/`RoomMoviesRoute`/`RoomSeriesRoute`/`HomeContent`, Settings-Composables), DAOs
  (`CatalogDao`, `EpgDao`), `SettingsViewModel` (TooManyFunctions), `StandardBackupRestoreValidator`.
- Keine neuen nicht-baselined God-Files.
- `VivicastComponents.kt` **entfernt**; 9 kohäsive Designsystem-Dateien (Surfaces/Layout/Badges/Panels/
  Dialogs/Inputs/Cards/Navigation/Player) + `VivicastTheme.kt`.

## 9. Build/Test Status

| Befehl | Ergebnis | Bemerkung |
|-------|----------|-----------|
| `detekt` | ✅ SUCCESS | grün mit Baseline |
| `assembleDebug` | ✅ SUCCESS | — |
| `test` (alle Module) | ✅ SUCCESS | keine Failures |
| connectedDebugAndroidTest | nicht in diesem Lauf | Smoke-Suite zuletzt in P1-03e grün |

## 10. Bewusst offene optionale Punkte

- **P1-03f Image-Resolver** → optional nach `:data:media` (kein Playback-Bezug, geringer Nutzen).
- **Backup/Security-Konzept** (Backup + Parental/PIN) separat — SAF/Keystore/Dialog, App-hoisted.
- **About-State-Provider** optional (nicht-Context-Provider), geringer Nutzen.
- **Weitere Dispatcher-Stellen** (BootReceiver/Application/SearchProvider) — Entry-Points, außerhalb Scope.
- **CI-Integration** für `detekt`/`test` — bislang nur lokal.
- **detekt-Baseline schrittweise abbauen** beim natürlichen Anfassen großer Dateien.

## 11. Empfehlungen für die nächste Entwicklungsphase

- **Codestandard:** neue Feature-Bildschirme = ViewModel + immutabler UiState + `collectAsStateWithLifecycle`;
  keine Repository-Flows/CRUD in Composables; Data-Logik in `:data`/UseCases mit Unit-Tests + injizierter
  Clock/Dispatcher.
- **Claude Code bei neuen Features:** App-hoisted nur für SAF/Keystore/Context/Scheduler/Navigation;
  lokalisierte Strings nie in ViewModel/`:data`.
- **Vor größeren Commits:** `./gradlew.bat detekt assembleDebug test` grün halten; Baseline nur mit
  Begründung erweitern.
- **Referenzdokumente:** `ARCHITECTURE-AUDIT.md`, `ARCHITECTURE-REFACTORING-PLAN.md`,
  `SETTINGS-APP-HOISTED-DECISIONS.md`, `PLAYBACK-ORCHESTRATION-MIGRATION-PLAN.md`, `DETEKT-GATE.md`, und
  dieser Report.

**Fazit:** Die Architektur ist aus aktueller Sicht **stabil genug für weitere Feature-Entwicklung**.

## 12. Git-Status

`git status --short` (vor Erstellung dieses Reports): **clean** (HEAD `698c54b7`). Dieser Lauf erzeugt
nur diese eine neue Datei unter `docs/`.
