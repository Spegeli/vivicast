# Phase 05 - Live-TV, Favorites, and Search

## Goal

Connect the existing Live-TV, favorites, and search UI to local persisted data while preserving the TV-first interaction model and provider isolation.

This phase replaces demo-backed Live-TV/search behavior with Room-backed app behavior. Playback may still use a preview/player contract until Phase 06.

## Affected Modules

- `:feature:live-tv`
- `:feature:search`
- `:feature:settings`
- `:core:designsystem`
- `:core:database`
- `:core:datastore`
- `:data:provider`
- `:data:epg`
- `:data:media`
- `:data:favorites`
- `:domain`
- `:app`

## Concrete Tasks

- Replace Live-TV demo catalog reads with repository/use-case backed data.
- Preserve the adaptive Live-TV column model:
  - category mode: provider/categories, channel list, preview/details
  - channel mode: channel list, channel EPG, preview/details
- Implement provider tree state persistence for expanded/collapsed providers.
- Show provider-owned categories unchanged and add provider-owned Favorites first.
- Map uncategorized channels to the internal category and display it as "Nicht kategorisiert".
- Keep focus behavior:
  - category focus updates channel list immediately
  - channel focus updates EPG/details
  - focus does not auto-start preview
  - preview starts only through OK according to the user setting
- Implement channel cards with logo fallback, current program, progress, catch-up indicator, and favorite state.
- Implement EPG panel for current, previous, and upcoming programs with missing-EPG fallback.
- Implement favorite toggling for channels, movies, and series using providerId, mediaId, and mediaType.
- Implement local app search over channels, movies, series, and EPG through Room only.
- Apply 300 ms search debounce.
- Implement search history with a maximum of 20 entries.
- Keep search result rows horizontal and remove all "show all" style actions.
- Add D-Pad and focus-restore tests for Live-TV, search, dialogs, and provider/category transitions.

## Current Progress

Implemented and validated:

- Room-backed `MediaRepository` implementation for categories, channels, movies, series, seasons, episodes, and local search.
- Local Room search DAO queries for channels, movies, series, and EPG programs.
- AppContainer wiring for `RoomMediaRepository`.
- Live-TV can render provider, category, and channel columns from local Room data while preserving the demo route fallback for isolated previews/usages.
- Live-TV empty states handle no-provider and no-channel startup without blocking app launch.
- Channel logo image loading resolves cached local files through `MediaCacheStore.getEntry` before the UI receives an image model.
- Design-system hero, poster, search-result, and channel-logo image slots support local file-backed Coil models while keeping existing drawable resource fallbacks.
- Room-backed `FavoritesRepository` implementation stores provider-scoped favorites by `providerId`, `mediaType`, and `mediaId`.
- Live-TV adds a provider-owned Favorites category before imported categories and filters it locally by channel favorite IDs.
- Live-TV channel cards now show favorite state, focused-channel current program title/progress, and catch-up state from local repositories.
- Live-TV preview can toggle the selected channel favorite and shows current/next EPG details from the local EPG repository.
- Live-TV channel-mode EPG panel now renders past/current/upcoming local EPG programs for the focused channel with missing-EPG fallback.
- Search is wired to `RoomMediaRepository.search` for local channels, movies, series, and EPG results.
- Search applies the ADR-005 300 ms debounce before querying Room.
- Search history is persisted through DataStore, capped to 20 distinct entries, and supports per-entry removal plus clear-all.
- Movies are wired to local providers, provider-owned Room categories, cached poster/backdrop images, and provider-scoped movie favorites.
- Series are wired to local providers, provider-owned Room categories, cached poster/backdrop images, and provider-scoped series favorites.
- Movies and Series preserve the demo route fallback for isolated previews/usages.
- Poster cards can update their hero on focus for TV-first browsing.
- Live-TV provider rows now support expanded/collapsed tree state persisted through DataStore.
- Live-TV categories are nested under the active expanded provider, matching the PRD provider tree model.
- Live-TV provider tree and provider/category transitions now have Compose Android instrumentation coverage for collapsed/expanded state, category filtering, preview non-autostart, and channel EPG column opening.
- Search now requests initial focus on the text field, hides history during active result display, and keeps result groups vertically scrollable so all local result types remain reachable.
- Search focus and history behavior now has Compose Android instrumentation coverage for initial focus, debounce/local results, EPG group scrolling, history selection, single delete, and clear-all.
- Settings destructive Provider and EPG source delete dialogs now request focus on "Abbrechen", handle TV Back key dismissal, expose stable test tags, and use AndroidTest target SDK 36 to avoid emulator old-target warnings.
- Top navigation now exposes stable tags and has Compose Android instrumentation coverage for focus-driven main-area selection, matching the top-navigation model in the wireframes.

Validated with:

- `.\gradlew.bat :core:database:compileDebugKotlin :data:media:compileDebugKotlin :core:designsystem:compileDebugKotlin :feature:live-tv:compileDebugKotlin :app:compileDebugKotlin`
- `.\gradlew.bat :data:media:connectedDebugAndroidTest`
- `.\gradlew.bat :data:favorites:connectedDebugAndroidTest`
- `.\gradlew.bat :core:datastore:compileDebugKotlin :feature:search:compileDebugKotlin :app:compileDebugKotlin`
- `.\gradlew.bat assembleDebug :app:installDebug`
- Android TV emulator launch and Live-TV empty-state visual smoke check
- Screenshot: `docs/phase-05-live-tv-room-empty-smoke.png`
- Screenshot: `docs/phase-05-live-tv-favorites-epg-smoke.png`
- Android TV emulator Search input/history empty-result smoke check
- Screenshot: `docs/phase-05-search-room-smoke.png`
- `.\gradlew.bat :core:designsystem:compileDebugKotlin :feature:movies:compileDebugKotlin :feature:series:compileDebugKotlin :app:compileDebugKotlin`
- `.\gradlew.bat :app:installDebug`
- Android TV emulator Movies/Series empty-state visual smoke check
- Screenshot: `docs/phase-05-movies-room-smoke.png`
- Screenshot: `docs/phase-05-series-room-smoke.png`
- `.\gradlew.bat :core:datastore:compileDebugKotlin :feature:live-tv:compileDebugKotlin :app:compileDebugKotlin`
- Android TV emulator Live-TV provider-tree empty-state visual smoke check
- Screenshot: `docs/phase-05-provider-tree-empty-smoke.png`
- `.\gradlew.bat :feature:live-tv:compileDebugAndroidTestKotlin :feature:live-tv:connectedDebugAndroidTest`
- `.\gradlew.bat :feature:search:compileDebugKotlin :feature:search:compileDebugAndroidTestKotlin :feature:search:connectedDebugAndroidTest`
- `.\gradlew.bat :app:compileDebugKotlin`
- `.\gradlew.bat :feature:settings:compileDebugKotlin :feature:settings:compileDebugAndroidTestKotlin :feature:settings:connectedDebugAndroidTest`
- `.\gradlew.bat :app:compileDebugKotlin`
- `.\gradlew.bat :core:designsystem:compileDebugKotlin :core:designsystem:compileDebugAndroidTestKotlin :core:designsystem:connectedDebugAndroidTest`
- `.\gradlew.bat :app:compileDebugKotlin`

Still open:

- Remaining Phase 05 focus-restore gaps should be reviewed for app-shell or player-overlay coverage before moving to Phase 06 playback.

## Definition of Done

- Live-TV displays real locally imported provider data.
- Provider isolation is visible and technically preserved.
- Favorites are provider-scoped and stored by IDs, not names.
- Search is local-only and does not send provider queries.
- Search covers channels, movies, series, and EPG.
- Existing Phase 2C visual direction remains intact.
- D-Pad focus, Back behavior, and focus restore pass emulator smoke testing.
- The app builds with `.\gradlew.bat assembleDebug`.
- No full Media3 playback, timeshift, Watch Next, backup/restore, or external metadata provider is implemented in this phase.

## References

- `external-docs/prd/PRD-v1/02-ux-live-tv.md`
- `external-docs/prd/PRD-v1/04-ux-search-settings-player.md`
- `external-docs/prd/PRD-v1/05-iptv-epg-favorites.md`
- `external-docs/prd/PRD-v1/06-data-model.md`
- `external-docs/architecture/decisions/ADR-001-provider-isolation.md`
- `external-docs/architecture/decisions/ADR-005-local-search.md`
- `external-docs/architecture/decisions/ADR-009-provider-deletion-and-favorites.md`
- `external-docs/design/interaction/01-live-tv-adaptive-columns.md`
- `external-docs/design/design-system/04-focus-navigation.md`
- `external-docs/design/mockups/high-fidelity/02-ui-direction-decisions.md`
