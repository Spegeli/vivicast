# ViviCast Plan

## Current Direction

Vivicast is now aligned to the PRD v1 and ADR roadmap from the read-only docs repository.

Use `docs/roadmap.md` as the detailed implementation roadmap. Use the active `docs/phase-XX-*.md` file for long sessions.

## Current Phase

Phase 05 - Live-TV, Favorites, and Search

## Current Status

Done:

- Phase 1 Android TV foundation exists.
- Phase 2/2C demo UI and reusable TV design-system components exist.
- `Spegeli/vivicast-docs` is cloned locally into `external-docs/`.
- `external-docs/` is ignored by Git and must remain read-only context.
- New PRD/ADR-based roadmap and phase files were created under `docs/`.
- Phase 02 data model and persistence foundation is implemented:
  - Room PRD entities, DAOs, indices, schema export, and database factory
  - typed DataStore preferences implementation
  - domain models and repository contracts
  - manual app container persistence wiring
  - local ID and secret-storage ADR
  - schema/security unit tests
- Phase 03 provider security and configuration is implemented:
  - Android Keystore-backed secure value storage
  - Room-backed provider repository with credentials stored outside Room
  - local Settings provider create/edit/disable/delete flow for M3U and Xtream configuration
  - provider lifecycle and deletion side-effect instrumentation tests
  - Android TV emulator smoke test for D-Pad Settings/provider flow
- Phase 04 ingest foundation is partially implemented:
  - M3U parser for live channel metadata, groups, logos, catch-up hints, and skipped-entry handling
  - XMLTV parser for channels, programmes, metadata, and platform-tolerant secure parser setup
  - Xtream client request foundation for PRD live, VOD, series, and series-info endpoints with transport abstraction
  - Xtream response parser for categories, live streams, VOD items, series lists, seasons, and episodes from local fixtures
  - Room-backed M3U live-channel import with provider isolation, delta updates, and removed-channel side-effect cleanup
  - Room-backed Xtream catalog import for live channels, VOD, series, seasons, and episodes with delta cleanup
  - Room-backed XMLTV EPG import with independent EPG source metadata, provider links, automatic mappings, time shift, and provider-scoped program replacement
  - refresh orchestration foundation in `:worker` with ADR-003 order, EPG source deduplication, failure continuation, and diagnostics redaction
  - concrete WorkManager refresh worker wrappers and request scheduling foundation for global, playlist, EPG, logo, and cache jobs
  - network-backed refresh runner foundation wired through `AppContainer` for M3U, Xtream, and XMLTV runtime imports
  - public M3U/XMLTV refresh smoke check passed through the refresh orchestrator using the user-approved public URLs
  - secure EPG source repository and Settings EPG source management UI are wired through `AppContainer`
  - Settings EPG UI exposes a basic provider-to-EPG source priority assignment action for saved EPG sources
  - EPG provider priorities can be reordered and unlinked with compacted priorities
  - manual EPG channel mapping repository APIs can set, clear, and reapply mappings through the XMLTV import path
  - Android TV emulator D-Pad smoke check passed for the Settings EPG master-detail view
  - Settings Allgemein refresh controls are wired to DataStore and WorkManager scheduling
  - Android TV emulator D-Pad smoke check passed for Settings Allgemein refresh focus and manual refresh enqueue
  - media cache foundation exists in `:core:cache` with file-backed storage, URL hashing, stats, clear, and size cleanup
  - media cache entries can be resolved as local files through a validated lookup API without exposing source URLs
  - media image refresh workers now cache changed channel logos, movie posters/backdrops, series posters/backdrops, season images, and episode thumbnails
  - cache cleanup workers enforce the configured DataStore size limit
  - Settings "Über die App" exposes cache stats, cache size selection, media image refresh scheduling, cache cleanup scheduling, and local cache clear actions
  - Android TV emulator D-Pad smoke check passed for the Settings cache maintenance view
  - Settings EPG exposes manual channel-to-EPG mapping controls for imported provider channels and linked EPG sources
- Phase 05 Live-TV data wiring has started:
  - `RoomMediaRepository` exposes Room-backed categories, channels, movies, series, seasons, episodes, and local search results
  - Live-TV can now render provider/category/channel columns from local Room data through `AppContainer`
  - Live-TV keeps the old demo route as a fallback when repositories are not provided
  - cached channel logos resolve to local files before reaching UI image loading
  - reusable TV artwork components can load local file-backed images through Coil while preserving demo resource fallbacks
  - `RoomFavoritesRepository` stores provider-scoped favorites by media type and media ID
  - Live-TV now shows a provider-owned Favorites category, channel favorite state, and local favorite toggling
  - Live-TV now renders current/next/local EPG data for the focused channel through the local EPG repository
  - Search now queries `RoomMediaRepository.search` locally with 300 ms debounce
  - Search history persists in DataStore with a maximum of 20 entries and supports per-entry removal plus clear-all

Still important:

- Movies, Series, and non-channel favorites are not fully Room-backed yet.
- Manual EPG mapping currently uses explicit external XMLTV channel ID entry; a persisted XMLTV channel candidate picker can be added later if operator comfort becomes a priority.
- Media3 playback is not complete yet.
- The complete `external-docs/codex/` folder and all links to it are ignored as binding sources.

## Working Rules

- Treat `docs/roadmap.md` as the long-form roadmap.
- Treat this file as the short startup pointer.
- Work phase by phase.
- Operate autonomously through the roadmap: after finishing a task, validate it, document the result, and continue with the next task unless blocked by missing credentials, OS permissions, external account/login steps, or explicit user direction.
- Validate implementation work before moving on. Use builds, tests, emulator smoke tests, and visual screenshot checks when UI or focus behavior changes.
- Local commits are allowed when they preserve a validated work state. Pushes still require explicit user approval.
- The implementation may choose the DI approach. Prefer the simplest approach that fits the codebase; record an ADR if introducing a DI framework or changing the app-wide DI strategy.
- Keep the active phase docs current when meaningful progress is made.
- Record architecture decisions or deviations as ADRs under `docs/decisions/`.
- Treat `external-docs/` as read-only and never commit it.
- Start Android TV emulator testing through `scripts\start-tv-emulator.ps1`.
- Do not install APKs on the physical Android TV unless explicitly requested.
- `AGENTS.md` may be updated directly when it is genuinely useful for reliable long-running work. Keep edits minimal and additive; do not rewrite or remove major guidance without explicit user approval.

## Public Test Sources

These public URLs may be used later for real M3U/XMLTV tests:

- M3U: `https://raw.githubusercontent.com/josxha/german-tv-m3u/main/german-tv.m3u`
- EPG XMLTV: `https://iptv-epg.org/files/epg-de.xml`

No Xtream Codes test credentials are available yet. Ask the user only when Xtream-specific real integration testing becomes necessary.

## Next Steps

1. Continue Phase 05 by wiring Movies/Series and non-channel favorites to local repositories.
2. Add provider expanded/collapsed tree state persistence where the final provider tree UI needs it.
3. Keep provider/security boundaries intact: no plaintext credentials in Room, logs, screenshots, docs, or demo data.

## Last Updated

2026-06-22
