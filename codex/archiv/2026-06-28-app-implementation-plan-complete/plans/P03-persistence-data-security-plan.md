# Package 3 - Persistence, Data Model, and Security Foundation

## Status

- Affected masterplan package: Package 3 - Persistence, Data Model, and Security Foundation
- State: done
- Last completed step: Package 3 persistence, stable identity, DataStore, and secure M3U reference baseline implemented.
- Last validated state: `.\gradlew.bat :app:compileDebugKotlin`, `.\gradlew.bat :core:cache:testDebugUnitTest :core:database:testDebugUnitTest`, `.\gradlew.bat :data:playback:testDebugUnitTest :data:media:compileDebugAndroidTestKotlin :data:provider:compileDebugAndroidTestKotlin`, `.\gradlew.bat :app:assembleDebug`, and `git diff --check` passed; `git diff --check` emitted LF/CRLF warnings only.
- Next concrete step: Start Package 4 provider/parser/import foundation from the final docs.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `AGENTS.md`
- `README.md`
- `../vivicast-docs/codex/README.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/prd/PRD-v1/06-data-model.md`
- `../vivicast-docs/prd/PRD-v1/08-android-tv-security.md`
- `../vivicast-docs/prd/PRD-v1/10-backup-import-requirements.md`
- `../vivicast-docs/prd/PRD-v1/13-test-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-010-stable-identities-and-restore-keys.md`
- `../vivicast-docs/architecture/decisions/ADR-014-security-data-network-backup.md`

## Scope

- Re-align existing Room entities with the final stable identity model:
  - provider `stableKey`
  - provider-related media `stableKey`
  - backup/restore reference keys such as `mediaStableKey`, `channelStableKey`, and pending flags
- Add missing baseline data model tables that are directly required by PRD 6 for Package 3.
- Keep secrets out of Room and map provider secret references to `sourceConfigKey`.
- Re-align DataStore preferences with the final PRD registry where this affects persistence foundations.
- Update directly affected repositories and schema tests so the app compiles against the new foundation.

## Non-Scope

- No docs-repo changes.
- No full backup/export/import implementation.
- No PIN implementation.
- No Android TV Search Provider or Watch Next publication.
- No final FTS search ranking implementation.
- No visual redesign or screen polish.
- No external metadata provider integration.

## Affected App Files

- `domain/src/main/java/com/vivicast/tv/domain/model/VivicastModels.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/model/VivicastEntities.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/VivicastDatabase.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/VivicastDatabaseFactory.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/dao/*.kt`
- `core/datastore/src/main/java/com/vivicast/tv/core/datastore/UserPreferencesStore.kt`
- `core/datastore/src/main/java/com/vivicast/tv/core/datastore/DataStoreUserPreferencesStore.kt`
- `data/provider/src/main/java/com/vivicast/tv/data/provider/RoomProviderRepository.kt`
- `data/media/src/main/java/com/vivicast/tv/data/media/RoomCatalogImportRepository.kt`
- `data/favorites/src/main/java/com/vivicast/tv/data/favorites/RoomFavoritesRepository.kt`
- `data/playback/src/main/java/com/vivicast/tv/data/playback/RoomPlaybackRepository.kt`
- `data/epg/src/main/java/com/vivicast/tv/data/epg/RoomEpgRepository.kt`
- `app/src/main/java/com/vivicast/tv/di/AppContainer.kt`
- `app/src/main/java/com/vivicast/tv/di/SecureM3uStreamReferenceStore.kt`
- `core/cache/src/main/java/com/vivicast/tv/core/cache/M3uStreamReferenceStore.kt`

## Technical Approach

- Preserve existing repositories and module boundaries.
- Prefer additive schema alignment plus a single Room migration over broad rewrites.
- Keep local Room `id` fields for relations, but add stable keys used by backup/restore and provider refresh matching.
- Rename provider secret reference semantics from `credentialsKey` to `sourceConfigKey` in the Room/domain model while keeping existing secret storage behavior.
- Move search history foundation to Room; keep UI-facing interfaces compiling until the search package can consume the Room table directly.
- Remove DataStore dependence for media cache size; use a fixed internal cache limit until final cache policy is implemented.

## Completed Work

- Added Room schema version 2 with migration from version 1.
- Added stable identity columns for providers, catalog media, EPG sources/programs, and EPG channels.
- Added pending restore reference fields for favorites, playback progress, channel history, and manual EPG mappings.
- Added baseline `epg_channels` and `search_history` tables.
- Added movie/series/episode `ageRating` and `isAdult` fields.
- Added v2 schema tests for tables, stable keys, pending restore fields, and provider isolation indexes.
- Added DataStore baseline keys for start destination, global User-Agent, Auto-Next, EPG retention/refresh, and diagnostics.
- Stopped persisting freely configurable cache size, recent-history limit, and watched-threshold values in DataStore.
- Replaced the app's plaintext file-backed M3U stream reference store with a Keystore-backed secure implementation.
- Updated affected repository mappings and tests for the new persistence foundation.

## Risks and Assumptions

- Existing local installs on schema version 1 migrate to version 2 with local IDs as fallback stable keys for old rows.
- Some DAO queries can continue using local IDs for now, but backup/restore and final search packages must use stable reference keys.
- Search history still has a temporary DataStore-facing UI compatibility path; the Room `search_history` table is now present for the final search package.
- The provider secret reference name `credentialsKey` still appears in compatibility code, but secrets are stored through `SecureValueStore`, not Room.
- FTS is intentionally deferred to the search package.

## Validation Plan

- Passed: `.\gradlew.bat :app:compileDebugKotlin`
- Passed: `.\gradlew.bat :core:database:testDebugUnitTest`
- Passed: `.\gradlew.bat :core:cache:testDebugUnitTest :core:database:testDebugUnitTest`
- Passed: `.\gradlew.bat :data:playback:testDebugUnitTest :data:media:compileDebugAndroidTestKotlin :data:provider:compileDebugAndroidTestKotlin`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: `git diff --check` with LF/CRLF warnings only.

## Open Owner Questions

- None.
