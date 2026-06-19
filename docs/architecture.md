# ViviCast Architecture

ViviCast is a pure Android project. Kotlin Multiplatform is intentionally not used because no iOS, desktop, or web app is planned.

## Principles

- Android TV is the first MVP target.
- Core IPTV logic is shared across form factors.
- UI is native per form factor: Compose for TV first, mobile/tablet Compose later.
- Compose screens should separate state/intent presentation from Activity, Room, network, navigation, and Media3 wiring so important surfaces remain independently previewable and testable.
- Representative Preview data should cover normal content and relevant empty, loading, error, long-text, and dense-data states without accessing production dependencies.
- Room is the local single source of truth for playlists, channels, EPG, favorites, recents, movies, series, seasons, episodes, and playback progress.
- Lightweight TV-local app and provider UI preferences are currently stored separately from Room, including startup behavior, TV-navigation behavior, playback/session flags, and TV density/visibility preferences.
- Startup behavior is now intentionally constrained: the non-resume fallback is always the Live TV browser rather than a user-selectable destination.
- Debug builds may still seed a local demo provider and VOD sample library; production onboarding should replace that path later.
- Media3/ExoPlayer is wrapped behind a project-owned player interface.
- The current player interface now exposes shared live/VOD playback identity, position/duration state, and first audio/subtitle track discovery and selection for the TV overlay.
- The current TV playback layer also exposes first overlay transport actions for pause, stop, and retry/resume behavior, plus persisted visibility control for guide, favorite, favorite-feedback, track-action, progress, description, and lower now/next UI as well as a persisted recoverable-retry delay, a persisted leave-Live-TV playback behavior, first lifecycle counters for starts/buffering, control-event timestamps, and first playback-error streak tracking.
- The current TV playback flow also has a first persisted recoverable-error auto-retry behavior wired at the UI/session layer.
- The current TV playback flow also collects first session-level playback telemetry for pause/stop/retry/error diagnostics.
- Stability comes before aggressive channel-switch optimization.
- Provider management is a Settings concern: Live TV can switch sources quickly, but adding, editing, naming, and syncing providers belongs in Settings.

## Modules

- `app-tv`: Android TV application, TV manifest, focus-first UI.
- `app-mobile`: Future mobile/tablet application shell.
- `core:model`: Stable domain models used everywhere.
- `core:data`: Repository/data graph implementation, Room-backed local orchestration, HTTP/Xtream orchestration, playlist/VOD/EPG imports.
- `core:database`: Room entities, DAOs, and database entry point.
- `core:network`: HTTP abstractions for playlist, XMLTV, and Xtream requests.
- `core:playlist`: M3U/M3U8 parsing.
- `core:epg`: XMLTV import and EPG matching.
- `core:player-media3`: Android Media3 implementation and player adapter.
- `core:domain`: Pure ports and result types. Source must not import Android, Room, database, network, or Media3 APIs.

Current TV app split:

- `MainActivity.kt`: shell-only Activity.
- `di/TvAppGraph.kt`: manual DI for the TV app.
- `navigation/TvNavigationModels.kt`: TV routes/sections and navigation focus models.
- `presentation/shell`: TV root, lifecycle-aware collection, root effects/dialog hosting, `TvShellUiState`, and `TvShellAction`.
- `presentation/settings`: Settings UI plus SharedPreferences-backed app/provider settings and sync telemetry. Existing keys are preserved.
- `presentation/player`: Player UI, immutable `PlaybackUiState`, and the TV-facing playback repository.
- `presentation/livetv`, `presentation/guide`, `presentation/vod`, `presentation/common`: feature UI surfaces, shared rows/dialog controls, preview data, and formatters.
- `designsystem/TvDesignSystem.kt`: shared TV color tokens.
- `core:data/ViviCastDataGraph.kt`: owns DB, HTTP, Xtream clients, content repositories, and import use cases for the TV app.

## MVP Data Flow

1. UI requests provider setup from Settings or the current import dialogs.
2. Domain layer calls the selected provider connection method: M3U URL/file or Xtream. Xtream-style `get.php?username=...&password=...` playlist URLs can be auto-promoted into the Xtream import path from the M3U URL flow.
3. Parser streams records without loading huge files into memory.
4. Database layer writes channels/categories/programmes in batches.
5. UI observes Room-backed state through repositories.
6. Player receives the selected live stream, movie stream, or episode stream URL through domain/player APIs.

## Provider Model Direction

The user-facing entity is a provider. A provider has a display name chosen by the user, one connection method, optional EPG source settings, sync metadata, persisted content-scope flags, and imported channels/categories.

- M3U provider: display name, playlist URL or file reference, optional EPG URL.
- Xtream provider: display name, server URL, username, password, optional EPG source if not supplied by the provider, and a stored API scope for live-only versus live-plus-VOD metadata probing.
- The current import flow can detect Xtream-style playlist URLs during M3U setup and store them as Xtream providers instead of plain M3U URL providers.
- Live TV browser: shows provider/source chips only for fast filtering and switching.
- Settings > Providers: creates, edits, deletes, renames, refreshes, and validates providers.
- Current TV implementation already stores provider source URI, Xtream credentials when applicable, enabled state, separate Live TV and Movies/Series scope flags, hidden groups, group order, logo priority, custom user-agent, Xtream output format, refresh preferences, EPG assignments, EPG import timing preferences with startup-time and in-session due checks, local sync/import telemetry including catch-up/archive hints plus refresh/EPG timing, counters, streaks, error summaries, health-rate/activity summaries, latest EPG sweep summaries, EPG coverage/next-due summaries, editable file-based M3U source references, playback/session preferences, overlay visibility preferences, recoverable-retry delay, leave-Live-TV playback behavior, first recoverable-error retry behavior, first session-level playback telemetry and lifecycle/error-streak counters, and the current Phase-2 Live TV / Guide / shell remote-settings baseline including Back-routing, filter-order, Guide-open provider/category handling, and Settings startup/back behavior.

## MVP UI Flow

1. Live TV browser is the default working surface.
2. Provider import and management live in Settings and import dialogs.
3. Channel list supports quick switching with a preview/watch panel.
4. Favorites, recent channels, and guide exist today as TV surfaces, with guide and recents also reachable from playback UI.
5. Movies and Series already use provider-grouped list/detail browsing with poster-first rows, poster-free detail panes, and shared playback/resume handling.
6. Player overlay and guide continue to evolve toward the final fullscreen TV model.

UI implementation boundary:

- Route/container composables collect persisted state, own side effects, and connect repositories, navigation, imports, and playback.
- Screen/section composables receive immutable display state plus event callbacks and contain the visual hierarchy.
- Preview fixtures provide deterministic local display states and no-op callbacks.
- Android Studio Preview validates composition and visual states; the Android TV emulator remains authoritative for focus movement, remote keys, Back routing, lifecycle, persistence, and playback integration.
- Deep Settings areas use a shared list-detail state contract: the overview owns section/source selection, detail state hides the Settings rail, and `Back` or left-edge exit restores the originating overview.
- The primary app rail is now either fully shown or fully hidden; the earlier icon-collapsed intermediate state is no longer part of the TV shell contract.

Remote-input note:

- The current Settings layer already covers the intended Phase-2 remote baseline.
- Richer fullscreen-player remote behavior belongs to Phase 4.
- Richer Guide/grid navigation behavior belongs to Phase 5.

Navigation direction:

- There is no separate Home section in the product direction.
- The intended long-term primary navigation is `Search`, `Live TV`, `Movies`, `Series`, `Settings`.
- The current primary rail already uses `Search`, `Live TV`, `Movies`, `Series`, `Settings`; favorites, recents, and guide remain internal/contextual shell features that should continue moving out of legacy paths.
- The current Search destination is still a placeholder shell route; global cross-library query behavior remains a later phase.
- Current Settings UX should be treated as production-facing product UI, not as an internal admin panel waiting for a later cleanup pass.

## Later Growth

The architecture leaves room for multiple playlists, multiple EPG sources, catch-up metadata, PVR, multiview, deeper VOD metadata/search, mobile PiP, and optional Play Store distribution without rebuilding the core.
