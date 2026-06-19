# ViviCast Refactoring Plan

Temp plan. `docs/PLAN.md` bleibt Feature-Source-of-truth. Ziel: `MainActivity.kt` von 11.489 LOC Monolith -> kompakte Activity + testbare TV Presentation + saubere Core/Data/Domain Layer.

Quellen: [Android Architecture](https://developer.android.com/topic/architecture), [Recommendations](https://developer.android.com/topic/architecture/recommendations), [Data Layer](https://developer.android.com/topic/architecture/data-layer), [UI Layer](https://developer.android.com/topic/architecture/ui-layer), [State Hoisting](https://developer.android.com/develop/ui/compose/state-hoisting), [Modularization](https://developer.android.com/topic/modularization), [ECC Android Clean Architecture](https://raw.githubusercontent.com/affaan-m/ECC/main/skills/android-clean-architecture/SKILL.md), [Fernando Cejas Reloaded](https://fernandocejas.com/blog/engineering/2019-05-08-architecting-android-reloaded/).

## 0. IMPLEMENTIERTER STAND

- Done: `MainActivity.kt` ist Shell-only: 14 LOC, `setContent { ViviCastTvApp() }`.
- Done: TV UI wurde aus Activity-Datei in echte Packages gesplittet: `presentation.shell`, `livetv`, `guide`, `settings`, `player`, `vod`, `common`, `navigation`, `di`, `designsystem`.
- Done: AAC `TvAppViewModel` + `TvAppGraph`; Controller-Lifetime via `onCleared`, nicht Compose-`remember`.
- Done: Root-Collections nutzen `collectAsStateWithLifecycle`.
- Done: `LiveTvUiState` buendelt Root-Screen-State; Shell/Navigation nutzt `TvShellUiState` + `TvShellAction` Reducer.
- Done: SharedPreferences Parsing/Persistenz liegt in `TvSettingsRepository`; Keys bleiben unveraendert.
- Done: Media3 liegt hinter `TvPlaybackRepository`; UI nutzt Controller-Fassade, PlayerView bekommt nur Adapter-Zugriff.
- Done: `PlaybackRepository` Port liegt in `core:domain`; `core:domain` haengt nicht mehr an `core:player`.
- Done: `PlaybackUiState` buendelt Player-Overlay-Anzeigezustand fuer die TV Presentation.
- Done: `core:player-media3` ersetzt das alte `core:player` Modul; Media3-Impl liegt in `com.vivicast.core.player.media3`.
- Done: neues `core:data` Modul mit Room/Network/Parser-Orchestrierung.
- Done: `PlaylistImportUseCase`, `VodLibraryUseCase`, `XtreamVodImportUseCase` liegen in `core:data`.
- Done: Domain enthaelt Ports/Result-Typen; kein Source-Import auf Android, Room, DB, Network oder Media3.
- Done: `app-tv` konstruiert keine DB/Network Clients mehr; DataGraph kapselt DB, HTTP, Xtream und Import-Orchestrierung.
- Done: Xtream-Credentials/OutputFormat liegen in `core:model`; TV Presentation importiert kein `core:network` mehr.
- Done: UI-Dateien wurden weiter gesplittet: Settings, Dialoge, Live/Guide, Player, VOD, Demo Data, Preference Keys.
- Done: Root/Content/Controller wurden weiter zerlegt; alle TV-Kotlin-Dateien liegen grob unter der 800-LOC-Zielgroesse.
- Done: Groesste TV-Dateien: `TvSettingsAboutPanel.kt` 799 LOC, `TvProviderEditDialog.kt` 797 LOC, `TvAppController.kt` 796 LOC.
- Verified: `.\gradlew.bat :app-tv:compileDebugKotlin :core:domain:testDebugUnitTest :core:data:testDebugUnitTest :core:network:testDebugUnitTest`.
- Verified: Android TV emulator `ViviCast_AndroidTV_API36`: `:app-tv:installDebug`, app launch, Live-TV UI dump, D-pad/OK/Back smoke, empty crash buffer.

## 1. IST-ANALYSE

- `app-tv/MainActivity.kt`: mischt Activity, root shell, Navigation, Dialoge, Settings, Live TV, Guide, VOD, Player UI, Previews, Formatierung, Validierung, Pref-Keys, Telemetrie, Import-Workflows, Controller.
- UI haelt zu viel Screen-State via `remember`: selected section/channel/movie/series/season/episode, dialog flags, import flags, EPG sweep state, playback telemetry, startup/back-routing.
- `ViviCastTvController` liegt in Activity-Datei und baut Infrastruktur direkt: `ViviCastDatabaseFactory`, `PlaylistImportUseCase`, `VodLibraryUseCase`, `UrlConnection*`, `Media3IptvPlayerController`, `SharedPreferences`.
- Activity/UI greift indirekt auf DB/Network/Player ueber Fat-Controller zu; kein AAC `ViewModel`, kein lifecycle-aware `collectAsStateWithLifecycle`, keine DI-Komposition.
- `core:domain` ist nicht clean: haengt direkt an `core:database`, `core:network`, `core:playlist`, `core:epg`, `core:player`; UseCases enthalten Room-Transaktionen, Entity-Mapping, Parser/Importer-Orchestrierung.
- `core:player` mischt API + Android Media3-Impl; Domain `PlaybackUseCase` haengt an Player-Modul statt purem Port.
- Persistente App-/Provider-Settings liegen als TV-SharedPreferences + Key-Funktionen in Activity; Provider-Daten sind teils Room, teils Prefs -> mehrere Quellen ohne Repository-Grenze.
- Compose-Screens sind teils previewbar, aber zu viele UI-Modelle/Formatierer/Enums bleiben private in `MainActivity.kt`; Wiederverwendung/Mobile spaeter blockiert.
- Zielgroesse: `MainActivity.kt` <150 LOC, Root-App <500 LOC, Feature-Dateien grob <800 LOC, Domain frei von Android/Room/Media3.

## 2. SOLL-VERZEICHNISSTRUKTUR

```text
app-tv/
  .../tv/MainActivity.kt
  .../tv/di/TvAppGraph.kt
  .../tv/navigation/TvNavigationModels.kt
  .../tv/presentation/shell/
  .../tv/presentation/livetv/
  .../tv/presentation/player/
  .../tv/presentation/guide/
  .../tv/presentation/settings/
  .../tv/presentation/vod/
  .../tv/presentation/common/
  .../tv/designsystem/

core/model/
  domain models only: Playlist, Channel, EpgProgram, Movie, Series, PlaybackState

core/domain/
  repository interfaces: ProviderRepository, LiveTvRepository, EpgRepository, VodRepository, PlaybackRepository, SettingsRepository
  usecases/: small `operator fun invoke`; no Room, Context, Media3, UrlConnection, SharedPreferences
  errors/: app/domain result types

core/data/                 # new aggregate data module
  repository/: impls
  local/: Room data sources wrapping DAOs
  remote/: playlist/xtream data sources wrapping network clients
  preferences/: SharedPreferences/DataStore-backed settings source
  mapper/: entity/dto/domain mapping

core/database/
  Room DB, entities, DAOs, migrations only

core/network/
  HTTP/Xtream/M3U fetch clients + DTOs only

core/playlist/
  M3U parser only

core/epg/
  XMLTV parser/import primitives only

core/player-media3/        # new Android impl module
  Media3 controller, PlayerView adapter, TextureView compatibility
```

Dependency target:

```text
app-tv -> feature/presentation packages, core:domain, core:data, core:player-media3
presentation -> core:domain, core:model, designsystem
core:domain -> core:model, coroutines
core:data -> core:domain, core:model, core:database, core:network, core:playlist, core:epg
core:player-media3 -> core:model, Media3
core:database/network/playlist/epg -> core:model only where needed
```

## 3. INKREMENTELLER PHASENPLAN

1. Baseline Guard
- Add compile/test checklist only: `:app-tv:compileDebugKotlin`, `:core:domain:testDebugUnitTest`, `:core:network:testDebugUnitTest`.
- No behavior change. Confirm current build before moves.

2. Presentation File Split
- Move private composables/UI models from `MainActivity.kt` into `presentation/*` packages.
- Keep same params/callbacks, no ViewModels yet.
- Result: compile-equivalent; `MainActivity.kt` loses Settings/LiveTV/Guide/VOD/Player composables.
- Status: Done. Visual feature surfaces are split into compile-verified `presentation/*`, `navigation`, `di`, and `designsystem` packages.

3. TV State Holder Extraction
- Replace `ViviCastTvController` with `TvAppViewModel` + feature state holders.
- Use immutable `UiState` + `onAction(Action)` per feature.
- Use `collectAsStateWithLifecycle`.
- Keep manual `TvAppGraph` DI; no Hilt until monolith reduced.
- Status: Done for current refactor. `TvAppViewModel`, `TvAppGraph`, lifecycle collection, `LiveTvUiState`, immutable `TvShellUiState`, `TvShellAction`, and immutable `PlaybackUiState` exist.

4. Settings/Prefs Repository
- Move `AppSettings`, `ProviderUiSettings`, sync telemetry, key funcs from Activity to `core:data/preferences` + `SettingsRepository`.
- Presentation observes settings via ViewModel.
- Preserve SharedPreferences keys for migration-free compatibility.

5. Content Repository Boundary
- Status: Done.
- Done: Player port moved into `core:domain`; `core:domain` no longer depends on `core:player`.
- Done: Provider/Live/EPG/VOD repository ports added in `core:domain`.
- Done: Room observation/mapping/import persistence moved to `core:data`.
- Done: `core:domain` no longer imports `core:database` or `core:network`.

6. Import Orchestration Cleanup
- Move M3U URL, Xtream auth/live import, VOD import, XMLTV refresh orchestration into data/domain usecases.
- Network clients stay in `core:network`; repository impl coordinates remote + local.
- UI receives progress/result states only.
- Status: Done for DB/HTTP/Xtream construction and URL/EPG/live/VOD import orchestration. Remaining TV-specific sync telemetry stays in `TvAppController` until feature ViewModels own it.

7. Player Boundary
- Status: Done.
- Done: Domain playback port exists; Media3 access is behind `TvPlaybackRepository`.
- Done: TV UI calls Controller playback facade instead of `playerController`.
- Done: `PlaybackUiState` isolates overlay display state from raw domain `PlaybackState` + settings flags.
- Done: `core:player-media3` Gradle module isolates the Android Media3 implementation. Player API port remains in `core:domain`, so no separate `core:player-api` module is needed.

8. Navigation Shell
- Move route enum/back contract/focus restoration into `navigation` + `shell`.
- Feature ViewModels own feature-local selection; root shell owns top-level route/rail visibility only.
- Status: Done. Navigation enums, shell/effect boundaries, package/directory move, and shell selection reducer are split out.

9. Optional Module Split
- After package split stable, create Gradle modules only where build boundaries pay off: `core:data`, `core:player-media3`, optional `feature:tv:*`.
- Keep feature modules coarse: `livetv`, `player`, `settings`, `vod`, `guide`.

10. Cleanup Gate
- Status: Done for current cleanup gate.
- Done: `MainActivity.kt` dead helpers/imports removed.
- Done: `docs/architecture.md` updated for current refactor boundary.
- Done: dependency check reports no `core:domain` source imports for DB/Network/Player/Android/Room.
- Done: app-tv source/build scan reports no direct DB/Network/Playlist/EPG imports.
- Done: app-tv depends on `core:player-media3`, not legacy `core:player`.
- Done: root/content/controller files are under target size.
- Command: `rg "com.vivicast.core.database|com.vivicast.core.network|com.vivicast.core.player|android\\.|androidx\\.room" core/domain/src/main/java`.

## 4. TEST PLAN

- After every step: `.\gradlew.bat :app-tv:compileDebugKotlin`.
- After domain/data changes: `.\gradlew.bat :core:domain:testDebugUnitTest :core:network:testDebugUnitTest`.
- After presentation moves: Compose Preview smoke for moved screens.
- After ViewModel/player/navigation steps: emulator pass on `ViviCast_AndroidTV_API36` for D-pad, OK, Back, playback, provider settings, import, Movies/Series resume.
- No physical TV install unless explicitly requested.

## 5. DEFAULTS / ASSUMPTIONS

- Manual DI first; Hilt only later if constructor graph becomes painful.
- Preserve existing Room schema + SharedPreferences keys during refactor.
- Package split is complete; further Gradle feature modules stay optional.
- Domain may depend on `core:model` + coroutines only.
- TV Presentation remains device-specific; shared logic moves to domain/data.
- Mobile/tablet later consume same domain/data, not TV UI state.
