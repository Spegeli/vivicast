# Phase 07 - VOD, Series, History, and Android TV

## Goal

Complete the user-facing movie and series flows on real local data, then integrate history, Continue Watching, trailers, deep links, voice search, global Android TV search, and Watch Next.

## Affected Modules

- `:feature:movies`
- `:feature:series`
- `:feature:search`
- `:feature:settings`
- `:feature:player`
- `:core:database`
- `:core:datastore`
- `:data:media`
- `:data:favorites`
- `:data:playback`
- `:domain`
- `:app`

## Concrete Tasks

- Replace movie and series demo catalog reads with repository/use-case backed data.
- Implement movie overview:
  - hero info updates on poster focus
  - provider categories unchanged
  - Favorites category
  - Continue category when playback progress exists
  - poster fallback with visible title
  - rating, year, genre, runtime, and description where available
- Implement movie detail:
  - play
  - continue
  - play from beginning
  - trailer
  - favorite toggle
  - watched state
- Implement series overview and detail:
  - hero info updates on focus
  - seasons
  - episodes
  - continue exact season/episode/position
  - fallback episode labels when metadata is incomplete
  - automatic next episode countdown setting and overlay
- Implement history:
  - last channel
  - recently watched channels
  - movie progress
  - series episode progress
  - clear history actions by scope
- Implement Continue Watching:
  - movies inside Movies
  - series inside Series
  - no global Continue Watching area in the app
- Implement trailer handling through YouTube only:
  - valid YouTube URL opens directly
  - missing URL opens YouTube search for title trailer
  - non-YouTube URL is ignored and replaced by YouTube search
  - missing YouTube app shows a clear dialog
- Implement Android TV voice search in the app search screen.
- Implement Android TV global search for Live-TV, movies, and series; exclude EPG results.
- Implement deep links for channel, movie, and series targets.
- Implement Watch Next and Android TV Continue Watching for movies and series only.
- Add focus restore for overview-to-detail-to-overview and player-to-detail flows.
- Add emulator smoke tests for D-Pad navigation, detail pages, trailer fallback, voice search entry, deep links, and Watch Next publishing where feasible.

## Current Progress

Implemented and validated:

- Movies already render provider-owned Room categories, cached poster/backdrop images, and provider-scoped favorites with a demo fallback.
- Movies now observe `PlaybackRepository.observeContinueWatching` and add the optional `Fortsetzen` category when non-completed movie progress exists.
- Movies sort `Fortsetzen` items by most recent playback progress and show stored progress percent on the card metadata.
- The Movies hero now displays `Fortsetzen` and `Von Anfang an` for movies with open progress; playback resumes from stored movie progress only on the continue path.
- TV action pills now have bounded row widths so multiple hero actions remain visible together.
- Movies continue-watching UI has Compose Android instrumentation coverage with fake repositories.

Validated with:

- `.\gradlew.bat :feature:movies:compileDebugKotlin :app:compileDebugKotlin`
- `.\gradlew.bat :feature:movies:compileDebugAndroidTestKotlin`
- `.\gradlew.bat :feature:movies:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.movies.MoviesRouteContinueTest"`
- `.\gradlew.bat assembleDebug`

Still open:

- Movie detail screen with trailer, favorite, watched-state actions, and detail-level playback controls.
- Series continue-watching from episode progress, including exact season/episode/position metadata.
- History clear actions and Android TV integrations from ADR-008.

## Definition of Done

- Movies and series use local persisted provider data, not demo-only data.
- Movie and series detail pages support PRD actions.
- Continue Watching and watched state are persisted and reflected in UI.
- Trailers follow ADR-007 and never open arbitrary non-YouTube URLs.
- Android TV voice search, global search, deep links, and Watch Next follow ADR-008.
- Provider isolation is preserved across VOD, series, favorites, history, and progress.
- The app builds with `.\gradlew.bat assembleDebug`.
- No backup/restore, diagnostics export, release-store work, or non-v1 external metadata provider is implemented in this phase.

## References

- `external-docs/prd/PRD-v1/03-ux-movies-series.md`
- `external-docs/prd/PRD-v1/04-ux-search-settings-player.md`
- `external-docs/prd/PRD-v1/05-iptv-epg-favorites.md`
- `external-docs/prd/PRD-v1/08-android-tv-security.md`
- `external-docs/architecture/decisions/ADR-001-provider-isolation.md`
- `external-docs/architecture/decisions/ADR-005-local-search.md`
- `external-docs/architecture/decisions/ADR-007-trailer-strategy.md`
- `external-docs/architecture/decisions/ADR-008-android-tv-integration.md`
- `external-docs/design/design-system/05-screen-patterns.md`
- `external-docs/design/mockups/high-fidelity/02-ui-direction-decisions.md`
