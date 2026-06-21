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
  - manual mapping repository APIs for setting and clearing per-provider channel mappings
  - manual mappings override automatic matches and can be cleared so the next import recreates automatic mappings
  - provider-scoped programme replacement for shared EPG sources
  - source time shift applied during import
  - catch-up availability derived from local channel capability
- Refresh orchestration foundation in `:worker`:
  - ADR-003 order for playlist, EPG, mapping, logo, and cache steps
  - shared EPG source de-duplication per refresh cycle
  - failure continuation so old usable data is not cleared by orchestration failures
  - diagnostic events with URL, username, password, token, auth, and key redaction
- Concrete WorkManager foundation in `:worker`:
  - global, playlist, EPG, logo, and cache `CoroutineWorker` wrappers
  - process-local runner registry for current manual DI strategy
  - unique WorkManager names and input keys for provider and EPG source refreshes
  - periodic background refresh enable/disable scheduling hook
  - network constraints for remote refresh jobs and no network constraint for cache cleanup
- Media cache foundation in `:core:cache`:
  - file-backed cache store for channel logos, movie posters, series posters, season images, and episode images
  - dedicated cache directories for movie and series backdrops
  - source URLs are hashed for cache keys and are not stored as plaintext cache metadata
  - cached entries can be resolved to local files through `MediaCacheStore.getEntry`
  - cache lookups update last-access metadata so size cleanup can keep recently used files
  - stats, clear, owner replacement, and least-recently-accessed size cleanup behavior
- Logo/cache worker runtime:
  - media image refresh now reads Room image URLs for channel logos, movie posters/backdrops, series posters/backdrops, season images, and episode thumbnails
  - per-image download failures are isolated so one broken image does not fail the whole refresh
  - cache cleanup uses the configured DataStore cache size limit
- Network-backed refresh execution foundation:
  - runtime M3U playlist fetch, parse, and Room import from encrypted provider credentials
  - runtime Xtream live, VOD, series, season, and episode fetch/parse/import path from encrypted provider credentials
  - runtime XMLTV fetch, parse, and provider-scoped Room import from encrypted EPG source URL keys
  - provider status transitions for refreshing, active, connection error, and invalid credentials
  - `AppContainer` runner registration for WorkManager workers without introducing a DI framework
  - logo/cache implementations wired through `AppContainer`
- Secure EPG source management:
  - `SecureEpgSourceRepository` stores EPG source URLs through `SecureValueStore` and keeps only URL keys in Room
  - source save/delete operations clean linked provider priorities, mappings, and programs for removed sources
  - Settings exposes local EPG source create/edit/delete controls with active state and time shift controls
  - Settings exposes a basic saved-source to provider priority assignment action using the existing repository link hook
  - Settings exposes provider EPG priority reorder and unlink actions for saved EPG sources
  - provider EPG priority edits are transactional and compact remaining priorities after removal
  - Settings EPG view preserves Android TV master-detail D-Pad navigation by opening text-field editors only after explicit add/select actions
- Refresh settings hooks:
  - `AppContainer` exposes a WorkManager-backed `RefreshWorkScheduler`
  - Settings Allgemein reads `backgroundRefreshEnabled`, `launchOnBoot`, and `rememberSorting` from DataStore-backed preferences
  - background refresh enablement updates DataStore and schedules/cancels the periodic global refresh worker
  - manual "Jetzt aktualisieren" action enqueues the existing global refresh worker path
  - Settings now opens on Allgemein to match the PRD/wireframe settings entry point
- Cache maintenance settings hooks:
  - Settings "Über die App" reads media cache stats from `MediaCacheStore`
  - cache size selection updates DataStore-backed cache preferences
  - media image refresh and cache cleanup actions enqueue the existing WorkManager jobs
  - local cache clear removes stored media cache files and reloads cache stats
  - settings section matching uses the existing settings-section order instead of a hard-coded localized label

Validated with:

- `.\gradlew.bat :iptv:m3u:testDebugUnitTest`
- `.\gradlew.bat :iptv:xmltv:testDebugUnitTest`
- `.\gradlew.bat :iptv:xtream:testDebugUnitTest`
- `.\gradlew.bat :iptv:xtream:connectedDebugAndroidTest`
- `.\gradlew.bat :data:media:connectedDebugAndroidTest`
- `.\gradlew.bat :data:epg:connectedDebugAndroidTest`
- `.\gradlew.bat :worker:testDebugUnitTest`
- `.\gradlew.bat :worker:compileDebugKotlin`
- `.\gradlew.bat :app:compileDebugKotlin`
- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat :feature:settings:compileDebugKotlin :app:compileDebugKotlin`
- `.\gradlew.bat :data:epg:connectedDebugAndroidTest :worker:testDebugUnitTest assembleDebug`
- `.\gradlew.bat :feature:settings:compileDebugKotlin :data:epg:compileDebugKotlin`
- `.\gradlew.bat :data:epg:connectedDebugAndroidTest assembleDebug`
- `.\gradlew.bat :app:compileDebugKotlin :feature:settings:compileDebugKotlin :worker:testDebugUnitTest`
- `.\gradlew.bat :app:installDebug`
- `.\gradlew.bat :data:epg:compileDebugKotlin :data:epg:connectedDebugAndroidTest`
- `.\gradlew.bat :app:compileDebugKotlin :feature:settings:compileDebugKotlin assembleDebug`
- `.\gradlew.bat :core:cache:testDebugUnitTest :worker:testDebugUnitTest :worker:compileDebugKotlin`
- `.\gradlew.bat :core:cache:testDebugUnitTest :worker:testDebugUnitTest :app:compileDebugKotlin assembleDebug`
- `.\gradlew.bat :feature:settings:compileDebugKotlin :app:installDebug`
- `.\gradlew.bat :core:database:compileDebugKotlin :worker:compileDebugKotlin :worker:testDebugUnitTest :core:cache:testDebugUnitTest`
- `.\gradlew.bat :core:cache:testDebugUnitTest :worker:testDebugUnitTest`
- Android TV emulator install, launch, and D-Pad smoke test for Settings EPG master-detail navigation
- Android TV emulator install, launch, and D-Pad smoke test for Settings Allgemein refresh focus and manual refresh enqueue
- Android TV emulator D-Pad smoke test for Settings cache maintenance navigation, cache job actions, and focus visibility
- Screenshot: `docs/phase-04-settings-epg-smoke.png`
- Screenshot: `docs/phase-04-epg-priority-smoke.png`
- Screenshot: `docs/phase-04-settings-refresh-smoke.png`
- Screenshot: `docs/phase-04-cache-settings-smoke.png`
- Temporary non-committed public refresh smoke JUnit run:
  - M3U: `https://raw.githubusercontent.com/josxha/german-tv-m3u/main/german-tv.m3u`
  - XMLTV: `https://iptv-epg.org/files/epg-de.xml`
  - validated fetch, parse, and ADR-003 orchestration without committing downloaded provider data

Still open:

- Full end-user manual mapping UI is still open; the repository and import behavior are ready for it.
- Cached assets are not yet wired into UI image loading; current Phase 04 work prepares local storage, lookup, and worker maintenance only.

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
