# Package 9 - VOD Movies and Series Plan

## Status

- Package: Package 9 - VOD Movies and Series
- State: done
- Last completed step: Package 9 Auto-Next rules handoff completed: settings defaults now match PRD (`Aus`, 10 seconds), the countdown value is persisted, and the Player Auto-Next display remains explicitly deferred to Package 11.
- Last validated state: Package 9 final pass passed datastore/settings/app compile checks, Settings connected Android test on the TV emulator, app debug build, and targeted diff check with only LF/CRLF warnings.
- Next concrete step: Start Package 10 - Search and Android TV Search.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `../vivicast-docs/prd/PRD-v1/03-movies-series-requirements.md`
- `../vivicast-docs/prd/PRD-v1/05-iptv-epg-favorites.md`
- `../vivicast-docs/architecture/decisions/ADR-007-trailer-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-013-player-playback-progress.md`
- `../vivicast-docs/design/screens/04-movies.md`
- `../vivicast-docs/design/screens/05-series.md`
- `../vivicast-docs/design/interaction/focus.md`
- `../vivicast-docs/prd/PRD-v1/04-search-settings-player-requirements.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`

## Masterplan Package

- Package 9 - VOD Movies and Series

## Concrete Scope

- Keep movies and series as separate main areas.
- Reuse the existing Room-backed media, favorites, playback, and provider foundations where they match final docs.
- Re-align VOD categories:
  - global categories first: `Favoriten`, then `Fortsetzen` when applicable
  - provider categories remain unmerged
  - fresh content focus starts on the first provider category, not on global categories
- Add package slices for movie detail-first behavior, series detail/episode behavior, favorites, watched/progress actions, and tests.

## Non-Scope

- No docs-repo changes.
- No full player package refactor.
- No Package 11 Auto-Next player overlay or actual media-end transition implementation.
- No external metadata providers.
- No new backend, account system, cloud sync, telemetry, or automatic provider merging.
- No full visual polish pass against high-fidelity PNGs in this first slice.

## Affected App Areas

- `feature/movies/src/main/java/com/vivicast/tv/feature/movies/MoviesRoute.kt`
- `feature/series/src/main/java/com/vivicast/tv/feature/series/SeriesRoute.kt`
- `feature/movies/src/androidTest/java/com/vivicast/tv/feature/movies/MoviesRouteContinueTest.kt`
- `feature/settings/src/main/java/com/vivicast/tv/feature/settings/SettingsRoute.kt`
- `feature/settings/src/androidTest/java/com/vivicast/tv/feature/settings/SettingsPlaybackPanelTest.kt`
- `core/datastore/src/main/java/com/vivicast/tv/core/datastore/UserPreferencesStore.kt`
- `core/datastore/src/main/java/com/vivicast/tv/core/datastore/DataStoreUserPreferencesStore.kt`
- `app/src/main/java/com/vivicast/tv/MainActivity.kt`

## Technical Approach

- Make small VOD alignment slices and validate each slice.
- Prefer existing repository APIs before adding new abstractions.
- Keep provider isolation intact: do not merge same-named provider categories.
- Treat demo fallback removal as part of the broader DemoCatalog cleanup unless a reachable runtime path depends on it.
- For progress/watched behavior, use `PlaybackRepository` and the existing 95 percent completion rule instead of UI-only state.

## Risks and Assumptions

- Movies/Series now use a left category column with VOD-capable provider switching. Full provider tree expansion polish can still be refined later if the later visual QA pass requires it.
- Auto-Next player display, media-end detection, and transition behavior remain Package 11 scope because the current player does not yet expose a true media-ended state or episode queue handoff.

## Validation Plan

- Compile affected feature modules after each slice.
- Update or add focused Android tests for:
  - VOD global category visibility and non-initial focus
  - movie continue progress display
  - movie detail-first open behavior
  - series episode selection/play behavior
  - watched/unwatched progress actions
- Run app assemble after structural route changes.
- Run focused Android TV emulator tests for Movies, Series, Settings, and playback/progress paths touched by the package.

## Progress

- Done: VOD docs and current implementation inspected.
- Done: First category re-alignment slice:
  - Movies show `Favoriten` before `Fortsetzen`.
  - Movies and Series select the first provider category as fresh content focus when provider categories exist.
  - Global categories remain visible and selectable.
- Done: Movie detail-first slice:
  - Movie poster OK/click opens a detail surface before playback.
  - Overview hero no longer exposes playback or favorite actions.
  - Detail surface exposes `Fortsetzen`/`Von Anfang an` or `Abspielen`, `Zu Favoriten`/`Favorit`, and `Zurück`.
  - Back closes the detail surface.
- Done: First series detail and episode-row slice:
  - Series poster OK/click opens a detail surface before episode playback.
  - Overview hero no longer exposes direct episode playback.
  - Detail surface shows favorite/back actions, season selector, and episode actions.
  - Episode OK/click starts playback from the detail surface.
- Done: Movie watched/unwatched slice:
  - Movie detail reads the concrete progress record when opened, including completed records.
  - `Als gesehen markieren` writes a completed movie progress record.
  - Completed movie details show `Gesehen`, `Von Anfang an`, and `Als ungesehen markieren`.
  - `Als ungesehen markieren` deletes only that movie progress record.
- Done: Episode watched/unwatched slice:
  - SeriesRoute now receives `PlaybackRepository` from the app container.
  - Selected episode progress is loaded from Room-backed playback state.
  - `Als gesehen markieren` writes a completed episode progress record.
  - Completed selected episodes show `Gesehen` and `Als ungesehen markieren`.
  - `Als ungesehen markieren` deletes only that episode progress record.
- Done: Movie trailer action slice:
  - Movie detail exposes a `Trailer` action.
  - Valid `youtube.com`, `www.youtube.com`, and `youtu.be` trailer URLs open through YouTube app packages only.
  - Missing or non-YouTube trailer URLs fall back to YouTube search for `<Titel> Trailer`.
  - If the YouTube app is unavailable, the detail page shows a local missing-app hint.
- Done: Real series continue slice:
  - Series observes non-completed episode playback progress for the active provider.
  - Episode progress is resolved through `MediaRepository.getEpisode()` and mapped back to one continue target per series.
  - The `Fortsetzen` category appears only when at least one real series continue target exists.
  - Continue series cards show episode progress metadata and progress percentage.
  - Series detail exposes a `Fortsetzen` action that starts the stored target episode through the existing episode player path.
- Done: First VOD category-column slice:
  - Movie categories moved from a horizontal row to a fixed left column.
  - Series categories moved from a horizontal row to a fixed left column.
  - Existing category selection, detail opening, progress, favorite, and continue behavior stayed unchanged.
- Done: VOD provider switching slice:
  - Movie left column shows only providers with movies enabled.
  - Series left column shows only providers with series enabled.
  - Provider selection resets the active category and selected poster item.
  - VOD screens no longer fall back to providers that do not expose the current VOD type.
- Done: Auto-Next settings rules and Package 11 handoff:
  - Auto-Next now defaults to `Aus`.
  - The configured countdown defaults to 10 seconds and is clamped to `5`, `10`, `15`, or `30`.
  - `Einstellungen > Wiedergabe` exposes `Automatisch nächste Folge` and `Countdown nächste Folge`.
  - The countdown remains visible and stored when Auto-Next is disabled.
  - The actual Auto-Next player display and media-end behavior are explicitly Package 11 scope.
- Done: Package 9 final status.

## Validation Log

- Passed: `.\gradlew.bat :feature:movies:compileDebugKotlin :feature:series:compileDebugKotlin`
- Passed: `.\gradlew.bat :feature:movies:compileDebugAndroidTestKotlin`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Package 9 first category slice.
- Passed: `.\gradlew.bat :feature:movies:compileDebugKotlin :feature:movies:compileDebugAndroidTestKotlin`
- Passed: `.\gradlew.bat :feature:movies:connectedDebugAndroidTest`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Package 9 movie detail-first slice.
- Passed: `.\gradlew.bat :feature:series:compileDebugKotlin :feature:series:compileDebugAndroidTestKotlin`
- Passed: `.\gradlew.bat :feature:series:connectedDebugAndroidTest`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Package 9 series detail slice.
- Passed: `.\gradlew.bat :data:playback:compileDebugKotlin :data:playback:compileDebugAndroidTestKotlin :feature:movies:compileDebugKotlin :feature:movies:compileDebugAndroidTestKotlin`
- Passed: `.\gradlew.bat :feature:movies:connectedDebugAndroidTest`
- Passed: `.\gradlew.bat :data:playback:connectedDebugAndroidTest`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Package 9 movie watched/unwatched slice.
- Passed: `.\gradlew.bat :feature:series:compileDebugKotlin :feature:series:compileDebugAndroidTestKotlin :app:compileDebugKotlin`
- Passed: `.\gradlew.bat :feature:series:connectedDebugAndroidTest`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Package 9 episode watched/unwatched slice.
- Passed: `.\gradlew.bat :feature:movies:compileDebugKotlin :feature:movies:compileDebugAndroidTestKotlin :app:compileDebugKotlin`
- Passed: `.\gradlew.bat :feature:movies:connectedDebugAndroidTest`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Package 9 trailer slice.
- Passed: `.\gradlew.bat :feature:series:compileDebugKotlin :feature:series:compileDebugAndroidTestKotlin :app:compileDebugKotlin`
- Passed: `.\gradlew.bat :feature:series:connectedDebugAndroidTest`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Package 9 series continue slice.
- Passed: `.\gradlew.bat :feature:movies:compileDebugKotlin :feature:series:compileDebugKotlin :feature:movies:compileDebugAndroidTestKotlin :feature:series:compileDebugAndroidTestKotlin`
- Passed: `.\gradlew.bat :feature:movies:connectedDebugAndroidTest`
- Passed: `.\gradlew.bat :feature:series:connectedDebugAndroidTest`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Package 9 category-column slice.
- Passed: `.\gradlew.bat :feature:movies:compileDebugKotlin :feature:series:compileDebugKotlin :feature:movies:compileDebugAndroidTestKotlin :feature:series:compileDebugAndroidTestKotlin`
- Passed: `.\gradlew.bat :feature:movies:connectedDebugAndroidTest`
- Passed: `.\gradlew.bat :feature:series:connectedDebugAndroidTest`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Package 9 provider switching slice.
- Passed: `.\gradlew.bat :core:datastore:compileDebugKotlin :feature:settings:compileDebugKotlin :feature:settings:compileDebugAndroidTestKotlin :app:compileDebugKotlin`
- Passed: `.\gradlew.bat :core:datastore:compileDebugKotlin :feature:settings:compileDebugKotlin :feature:settings:compileDebugAndroidTestKotlin :app:assembleDebug`
- Passed: `.\gradlew.bat :feature:settings:connectedDebugAndroidTest`
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Package 9 Auto-Next settings rules and handoff.
