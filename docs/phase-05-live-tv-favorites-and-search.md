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

Still open:

- Movie and series favorites are not wired into their feature UIs yet.
- Provider expanded/collapsed tree state persistence is not implemented yet.

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
