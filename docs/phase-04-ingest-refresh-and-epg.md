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
