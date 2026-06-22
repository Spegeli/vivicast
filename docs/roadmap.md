# Vivicast Roadmap

## Source Rules

This roadmap is derived from the read-only documentation clone in `external-docs/`.

Authoritative sources:

- `external-docs/prd/PRD-v1/`
- `external-docs/architecture/decisions/`
- `external-docs/architecture/diagrams/`
- `external-docs/design/`

Do not use `external-docs/codex/`. Ignore every reference to `codex/`, even when another source mentions it.

`external-docs/` is read-only project context. Do not edit, stage, or commit it.

## Current State

The Android TV foundation already exists and must not be replanned from zero.

Existing modules:

- `:app`
- `:core:cache`
- `:core:common`
- `:core:designsystem`
- `:core:database`
- `:core:datastore`
- `:core:network`
- `:core:player`
- `:core:security`
- `:data:provider`
- `:data:epg`
- `:data:media`
- `:data:favorites`
- `:data:playback`
- `:domain`
- `:feature:live-tv`
- `:feature:movies`
- `:feature:series`
- `:feature:search`
- `:feature:settings`
- `:feature:player`
- `:iptv:m3u`
- `:iptv:xtream`
- `:iptv:xmltv`
- `:worker`

Implemented or partially implemented:

- Android application ID and package namespace use `com.vivicast.tv`.
- Android TV Leanback manifest and launcher entry exist.
- Compose for TV UI demo exists for Live-TV, Movies, Series, Search, Settings, and Player Overlay.
- `:core:designsystem` contains TV-oriented tokens, focus surfaces, cards, rows, hero panels, settings rows, and player overlay components.
- `:data:media` contains local demo catalog data and bundled demo assets.
- Gradle is configured with Compose, Room, DataStore, Media3, WorkManager, Retrofit, OkHttp, Coil, and TV Material dependencies.

Still stubbed or missing:

- Dependency injection is manual through `AppContainer`.
- Room has PRD v1 entities, DAOs, indices, schema export, and provider repository wiring.
- DataStore has typed preferences support for local app preferences.
- Security has a `SecureValueStore` contract and Android Keystore-backed implementation.
- Provider configuration is persisted locally with credentials stored outside Room.
- EPG, media, favorites, and playback repositories are still contracts or minimal implementations; `:data:media` now includes Room-backed M3U live and Xtream catalog import paths, and `:data:epg` now includes a Room-backed XMLTV import path.
- M3U and XMLTV modules include parser implementations; Xtream has a request/transport client foundation and response parser.
- Player has only a controller contract, with no Media3 playback implementation.
- Worker contains job-name constants, concrete WorkManager refresh wrappers, request scheduling, network-backed refresh runner wiring, a testable refresh orchestrator, diagnostics redaction foundation, and media image cache refresh for logos, posters, backdrops, season images, and episode thumbnails.
- `:core:cache` contains the file-backed media cache foundation for logo/poster/backdrop/image storage, local file lookup, stats, clear, and size cleanup.
- Settings exposes refresh controls, EPG source/provider priority management, manual EPG channel mapping, and cache maintenance hooks for local cache stats, size limits, logo refresh, cleanup, and clearing.
- Existing UI uses demo data and must later be connected to local persisted data.

## Phase Overview

| Phase | File | Status | Purpose |
| --- | --- | --- | --- |
| 01 | `docs/phase-01-docs-and-foundation-alignment.md` | Complete | Align local docs, ignored source clone, current module audit, and planning rules. |
| 02 | `docs/phase-02-data-model-and-persistence.md` | Complete | Implement the PRD Room/DataStore data foundation. |
| 03 | `docs/phase-03-provider-security-and-configuration.md` | Complete | Add provider management and encrypted credential storage. |
| 04 | `docs/phase-04-ingest-refresh-and-epg.md` | Complete | Add M3U, Xtream, XMLTV ingest, refresh scheduling, and EPG storage. |
| 05 | `docs/phase-05-live-tv-favorites-and-search.md` | Complete | Connect Live-TV, favorites, and local search to persisted data. |
| 06 | `docs/phase-06-playback-timeshift-and-errors.md` | Active | Add Media3 playback, timeline behavior, retries, reconnect, and timeshift. |
| 07 | `docs/phase-07-vod-series-history-and-android-tv.md` | Planned | Complete movies, series, history, Continue Watching, and Android TV integration. |
| 08 | `docs/phase-08-backup-privacy-polish-and-release.md` | Planned | Add backup/restore, diagnostics, privacy hardening, performance polish, and release readiness. |

## Working Rules

- Work phase by phase.
- Change only files required for the active phase.
- Work autonomously through the roadmap. After completing a task, validate it and continue with the next task unless a real blocker requires user input.
- Validate each meaningful implementation step before moving on. Use compile checks, unit tests, Android TV emulator smoke tests, and screenshots when UI, focus, or visual behavior changes.
- Local commits are allowed after validated milestones. Pushes still require explicit user approval.
- The implementation may choose the DI approach. Prefer the simplest approach that fits the current codebase and record an ADR before adding a DI framework or changing the app-wide DI strategy.
- Keep `docs/roadmap.md` and the active phase file current when meaningful progress is made.
- Record local architecture decisions or deviations as ADRs under `docs/decisions/`.
- Never bind real provider data, tokens, credentials, or private playlists into tests, docs, screenshots, or demo data.
- Public M3U/XMLTV test URLs from the user may be used for later real ingest checks; no Xtream Codes credentials are currently available.
- Do not add a server backend, account system, cloud sync, telemetry, external metadata provider integration, or automatic provider merging for v1.
- `AGENTS.md` may be updated directly when it is genuinely useful for reliable long-running work. Keep edits minimal and additive; do not rewrite or remove major guidance without explicit user approval.
