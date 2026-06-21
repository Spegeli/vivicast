# Phase 04 - Ingest, Refresh, and EPG

## Goal

Implement local catalog ingest for M3U, Xtream, and XMLTV, with delta synchronization, EPG source management, and WorkManager refresh orchestration.

This phase stores real provider catalog data locally but does not implement full playback.

## Affected Modules

- `:iptv:m3u`
- `:iptv:xtream`
- `:iptv:xmltv`
- `:core:network`
- `:core:database`
- `:core:security`
- `:data:provider`
- `:data:epg`
- `:data:media`
- `:worker`
- `:feature:settings`

## Concrete Tasks

- Implement M3U parsing for channels, categories, logos, catch-up hints, and stream metadata needed by PRD v1.
- Implement Xtream client calls for live categories, live streams, VOD categories, VOD items, series, seasons, and episodes.
- Implement XMLTV parsing for EPG channels and programs.
- Keep parser and client outputs provider-isolated.
- Convert parser/client output into Room import batches.
- Implement delta synchronization:
  - add new items
  - update changed items
  - delete removed provider-owned items
  - clean related favorites, playback progress, and history for removed content
- Implement EPG source management:
  - independent EPG sources
  - provider priority mapping
  - automatic mapping
  - manual mapping support hooks
  - keep old EPG when refresh fails
- Implement global refresh order from ADR-003:
  - collect due playlists
  - refresh playlists
  - collect EPG sources
  - deduplicate EPG sources
  - refresh EPG
  - apply EPG mapping
  - refresh logos
  - clean cache
- Add WorkManager workers:
  - playlist refresh
  - EPG refresh
  - logo refresh
  - cache cleanup
- Ensure app startup never blocks when local data already exists.
- Add diagnostics events with redaction of credentials, tokens, and secret URLs.
- Add tests with local fixture files only; do not commit private provider data.
- Use these public test sources for real M3U/XMLTV ingest checks when useful:
  - M3U: `https://raw.githubusercontent.com/josxha/german-tv-m3u/main/german-tv.m3u`
  - EPG XMLTV: `https://iptv-epg.org/files/epg-de.xml`
- Do not block M3U/XMLTV work on Xtream real credentials. Xtream-specific real integration testing requires separate user-provided credentials later.

## Current Progress

Implemented and validated:

- M3U parser in `:iptv:m3u` for live channel entries, categories, logos, channel numbers, catch-up hints, stable IDs, and skipped incomplete entries.
- XMLTV parser in `:iptv:xmltv` for channels, programmes, titles, descriptions, categories, icons, skipped invalid programmes, and Android-compatible secure parser setup.
- Xtream client foundation in `:iptv:xtream`:
  - typed credential model
  - transport abstraction for fixture tests and later HTTP use
  - OkHttp transport without logging or persisting credential-bearing URLs
  - PRD endpoint request methods for live categories, live streams, VOD categories, VOD streams, series categories, series lists, and series details
- Xtream parser in `:iptv:xtream`:
  - category, live stream, VOD item, series list, season, and episode DTOs
  - tolerant parsing of common Xtream field variants
  - local AndroidTest fixtures only, with no real provider data
- Room-backed M3U live-channel import in `:data:media`:
  - provider-isolated category and channel IDs
  - uncategorized fallback
  - delta add/update/remove behavior
  - cleanup of favorites, playback progress, channel history, EPG mappings, and EPG programmes for removed channels
  - no stream URLs stored in Room
- Room-backed Xtream catalog import in `:data:media`:
  - provider-isolated live, movie, and series categories
  - live channel, VOD movie, series, season, and episode imports
  - deterministic local IDs without storing stream URLs
  - delta add/update/remove behavior
  - cleanup of favorites and playback progress for removed VOD and series content
  - cleanup of episode playback progress for removed episodes
- Room-backed XMLTV EPG import in `:data:epg`:
  - EPG source metadata stored independently with URL key only
  - provider/source priority links
  - automatic channel mapping by XMLTV ID or display name
  - manual mapping preservation hooks
  - provider-scoped programme replacement for shared EPG sources
  - source time shift applied during import
  - catch-up availability derived from local channel capability
- Refresh orchestration foundation in `:worker`:
  - ADR-003 order for playlist, EPG, mapping, logo, and cache steps
  - shared EPG source de-duplication per refresh cycle
  - failure continuation so old usable data is not cleared by orchestration failures
  - diagnostic events with URL, username, password, token, auth, and key redaction
  - worker job names and input keys for later WorkManager wrappers

Validated with:

- `.\gradlew.bat :iptv:m3u:testDebugUnitTest`
- `.\gradlew.bat :iptv:xmltv:testDebugUnitTest`
- `.\gradlew.bat :iptv:xtream:testDebugUnitTest`
- `.\gradlew.bat :iptv:xtream:connectedDebugAndroidTest`
- `.\gradlew.bat :data:media:connectedDebugAndroidTest`
- `.\gradlew.bat :data:epg:connectedDebugAndroidTest`
- `.\gradlew.bat :worker:testDebugUnitTest`
- `.\gradlew.bat assembleDebug`

Still open:

- XMLTV import orchestration from encrypted EPG source URLs.
- Concrete WorkManager playlist, EPG, logo, and cache worker wrappers.
- Network-backed refresh execution wired to secure provider and EPG source credentials.
- Real public M3U/XMLTV smoke checks through refresh orchestration.

## Definition of Done

- M3U, Xtream, and XMLTV imports can populate Room from controlled local test fixtures.
- Refresh failures keep old usable data.
- Shared EPG sources are refreshed once per cycle.
- Provider refresh does not interrupt an active stream contractually.
- Worker scheduling follows user settings.
- Sensitive values are redacted from logs and diagnostics.
- The app builds with `.\gradlew.bat assembleDebug`.
- No Media3 playback, timeshift buffer, Watch Next, backup export, or external metadata provider is implemented in this phase.

## References

- `external-docs/prd/PRD-v1/05-iptv-epg-favorites.md`
- `external-docs/prd/PRD-v1/06-data-model.md`
- `external-docs/prd/PRD-v1/07-background-jobs-performance.md`
- `external-docs/prd/PRD-v1/08-android-tv-security.md`
- `external-docs/architecture/decisions/ADR-002-epg-strategy.md`
- `external-docs/architecture/decisions/ADR-003-refresh-strategy.md`
- `external-docs/architecture/decisions/ADR-009-provider-deletion-and-favorites.md`
