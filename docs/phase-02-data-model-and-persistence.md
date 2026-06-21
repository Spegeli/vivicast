# Phase 02 - Data Model and Persistence

## Goal

Implement the local data foundation required by PRD v1: Room for catalog/user state, DataStore for app settings, and repository contracts that can later serve real provider data without changing UI ownership boundaries.

This phase does not implement network ingest or playback.

## Status

Complete as of 2026-06-21.

Implemented:

- PRD Room entities for providers, catalog, EPG, favorites, playback progress, and history.
- DAOs for providers, catalog, EPG, favorites, playback, and local search.
- Provider-isolation and local-search indices.
- Room schema export under `:core:database`.
- Typed DataStore preferences and concrete `DataStoreUserPreferencesStore`.
- Domain models and repository contracts for provider, EPG, media, favorites, and playback.
- Manual persistence wiring through `AppContainer`.
- ADR for local ID and secret-storage strategy.
- Unit tests for exported schema coverage, provider-isolation indices, and secret-field exclusion.

Validation:

- `.\gradlew.bat :core:database:testDebugUnitTest`
- `.\gradlew.bat assembleDebug`

## Affected Modules

- `:core:database`
- `:core:datastore`
- `:core:common`
- `:domain`
- `:data:provider`
- `:data:epg`
- `:data:media`
- `:data:favorites`
- `:data:playback`
- `:app`

## Concrete Tasks

- Define Room entities from PRD chapter 6:
  - provider
  - category
  - channel
  - movie
  - series
  - season
  - episode
  - EPG source
  - provider-to-EPG-source priority
  - EPG program
  - EPG channel mapping
  - favorite
  - playback progress
  - channel history
- Add DAOs for provider, catalog, EPG, favorites, playback progress, history, and search queries.
- Add required indices for large libraries:
  - provider/category/name lookups
  - playback provider and completion lookups
  - EPG channel/time range lookups
  - local search lookups
- Decide and document the local ID strategy before implementing destructive data operations.
- Add Room database creation and schema export.
- Add initial migration policy for v1 development.
- Expand domain models and repository interfaces so features can stop depending on demo-only models later.
- Implement DataStore settings for PRD-defined app, visual, playback, history, cache, and parental-control settings.
- Keep sensitive provider values out of Room and DataStore unless they are non-secret references to Keystore keys.
- Choose the DI approach during implementation. Prefer the simplest approach that fits the current codebase; record an ADR before introducing a DI framework or changing the app-wide DI strategy.
- Add unit tests for DAO behavior that affects provider isolation, deletion, favorites, playback progress, and search indices.

## Definition of Done

- Room has concrete entities, DAOs, indices, and a non-empty `VivicastDatabase`.
- DataStore has a concrete implementation and typed settings model.
- Provider credentials and secret URLs are not stored in Room.
- Repository interfaces map clearly to PRD data areas.
- The app still builds with `.\gradlew.bat assembleDebug`.
- Existing demo UI still compiles, even if it remains demo-backed until later phases.
- No provider network ingest, parsers, or Media3 playback are implemented in this phase.

## References

- `external-docs/prd/PRD-v1/05-iptv-epg-favorites.md`
- `external-docs/prd/PRD-v1/06-data-model.md`
- `external-docs/prd/PRD-v1/07-background-jobs-performance.md`
- `external-docs/prd/PRD-v1/08-android-tv-security.md`
- `external-docs/architecture/decisions/ADR-001-provider-isolation.md`
- `external-docs/architecture/decisions/ADR-002-epg-strategy.md`
- `external-docs/architecture/decisions/ADR-005-local-search.md`
- `external-docs/architecture/decisions/ADR-009-provider-deletion-and-favorites.md`
