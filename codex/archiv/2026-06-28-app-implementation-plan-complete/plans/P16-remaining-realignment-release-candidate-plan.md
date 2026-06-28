# Package 16 - Remaining Re-Alignment Plan

## Status

- Active package: Package 16 - Remaining Re-Alignment
- State: done
- Last completed step: Completed Slice 3 by aligning provider/EPG source secret references to `sourceConfigKey`, renaming EPG program channel storage to `epgChannelId`, and adding Room schema version 6 migration coverage.
- Last validated state: `.\gradlew.bat :app:assembleDebug --console=plain`, `.\gradlew.bat :core:database:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.core.database.VivicastDatabaseMigrationTest" --console=plain`, focused provider/EPG/media repository connected tests, and `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest" --console=plain` passed on `emulator-5554`; `git diff --check` passed with only LF/CRLF warnings.
- Next concrete step: Package 16 is complete; wait for the Owner's next package or task.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `AGENTS.md`
- `README.md`
- `../vivicast-docs/codex/README.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/prd/PRD-v1/04-search-settings-player-requirements.md`
- `../vivicast-docs/prd/PRD-v1/06-data-model.md`
- `../vivicast-docs/prd/PRD-v1/07-background-jobs-performance.md`
- `../vivicast-docs/prd/PRD-v1/08-android-tv-security.md`
- `../vivicast-docs/prd/PRD-v1/09-implementation-and-dod.md`
- `../vivicast-docs/prd/PRD-v1/11-about-app-requirements.md`
- `../vivicast-docs/prd/PRD-v1/13-test-strategy.md`
- `../vivicast-docs/design/screens/07-settings.md`
- `../vivicast-docs/design/components/about-app.md`
- `../vivicast-docs/design/wireframes/08-about-app.md`

## Affected Masterplan Package

- Follow-up package after the final docs masterplan Packages 0-14 and app-repo Package 15.
- This package does not add product scope. It closes the remaining app/code re-alignment leftovers selected by the Owner.
- Per Owner decision, only Slices 1-3 are part of this package; previously discussed Slices 4+5 were removed from the plan and are not tracked as open work here.

## Concrete Implementation Scope

### Slice 1 - Remove Product Demo Fallbacks

- Remove productive fallback calls to `DemoMoviesRoute`, `DemoSeriesRoute`, and `DemoSearchRoute`.
- Remove the dead private `DemoLiveTvRoute` source block and remaining productive `DemoCatalog` dependency.
- Keep explicit empty/loading/error states when repositories are unavailable.
- Do not remove test fakes.
- Status: done.

### Slice 2 - Diagnostics and Support Contract

- Implement `Ueber die App > Diagnose und Support` according to PRD 4, PRD 7, PRD 8, PRD 11, PRD 13, coding rules, and design sources.
- Persist diagnostics logging enabled/retention settings through the existing DataStore fields.
- Store sanitized diagnostics sessions and segments only in private app storage.
- Export a ZIP with `vivicast-diagnostics.log` and `diagnostics-metadata.json`.
- Never show log file contents in the app.
- Add support-information copy for non-private visible support data only.
- Status: done.

### Slice 3 - Source-Aligned Storage Names and EPG Schema

- Replace remaining compatibility naming such as `credentialsKey` with final `sourceConfigKey` wording where this can be done without breaking backup/migration contracts.
- Re-align `epg_programs` from provider-related compatibility storage toward source-related EPG storage.
- Add or update Room migration and schema tests for any schema change.
- Keep provider isolation and restore stable-key behavior intact.
- Status: done.

## Non-Scope

- No docs-repo edits.
- No new product scope beyond active PRD/ADR/design/test sources.
- No release publishing, tag, branch, PR, push, or remote action.
- No public-list Android TV QA in this package.
- No strict release-candidate benchmark work in this package.
- No private provider data, credentials, tokens, private playlist URLs, private screenshots, or downloaded public-list files committed to the repo.

## Affected App Modules and Files

- `codex/plans/APP-IMPLEMENTATION-PLAN.md`
- `codex/plans/P16-remaining-realignment-release-candidate-plan.md`
- `data/media/src/main/java/com/vivicast/tv/data/media/DemoCatalog.kt`
- `feature/live-tv/src/main/java/com/vivicast/tv/feature/livetv/LiveTvRoute.kt`
- `feature/movies/src/main/java/com/vivicast/tv/feature/movies/MoviesRoute.kt`
- `feature/series/src/main/java/com/vivicast/tv/feature/series/SeriesRoute.kt`
- `feature/search/src/main/java/com/vivicast/tv/feature/search/SearchRoute.kt`
- `feature/player/src/main/java/com/vivicast/tv/feature/player/PlayerRoute.kt`
- `feature/settings/src/main/java/com/vivicast/tv/feature/settings/SettingsRoute.kt`
- `core/datastore/`
- `core/database/`
- `core/security/`
- `core/network/`
- `data/provider/`
- `data/epg/`
- `app/src/main/java/com/vivicast/tv/`
- focused tests under affected modules.

## Technical Approach

- Work slice by slice, with a short implementation plan refresh before each slice.
- Prefer deleting demo fallbacks over adding new indirection.
- Reuse existing DataStore, Room, Secret Store, backup, Watch Next, network, and Settings patterns.
- Add only focused tests that fail if the changed behavior regresses.
- For schema changes, update migrations and schema validation in the same slice.

## Completed Work

- Slice 1: replaced Movies, Series, and Search productive demo fallback routes with explicit unavailable/empty states.
- Slice 1: removed the dead private Live-TV demo route block.
- Slice 1: replaced the Player's `DemoCatalog` default state with neutral fallback playback state.
- Slice 1: deleted `data/media` `DemoCatalog.kt` and unreferenced `demo_*` media assets.
- Slice 1: verified no source references remain for `DemoCatalog`, demo route functions, demo media models, or demo media resources outside generated build artifacts.
- Slice 2: added `diagnosticsLoggingEnabled` and `diagnosticsRetentionDays` persistence with legacy `diagnostics_enabled` fallback.
- Slice 2: added `Ueber die App` Diagnostics/Support UI rows for logging, retention, ZIP export, and support-information copy.
- Slice 2: added private app-storage diagnostics sessions/segments and ZIP export with `vivicast-diagnostics.log` plus `diagnostics-metadata.json`.
- Slice 2: added Android instrumented coverage that verifies required ZIP entries and redaction of URLs, tokens, and provider names.
- Slice 3: renamed current Provider and EPG source secret reference fields from compatibility names to `sourceConfigKey` across entities, domain models, repositories, backup/restore code, worker code, and tests.
- Slice 3: renamed EPG program source channel storage from `externalChannelId` to `epgChannelId` and added the source-aligned unique/index shape `epgSourceId + epgChannelId + stableKey`.
- Slice 3: bumped Room schema to version 6, exported `core/database/schemas/.../6.json`, added `Migration5To6`, and preserved old `credentialsKey`/`urlKey`/`externalChannelId` only in legacy migration fixtures, migration copy SQL, and backup rejection tests.

## Risks and Assumptions

- Diagnostics is larger than the other leftovers because it has storage, redaction, ZIP export, lifecycle, retention, and UI requirements.
- Slice 3 keeps `providerId` and `channelId` on `epg_programs` as local join fields for the current Live-TV/search/player paths; the source-aligned identity and channel storage are now in place, but a future deeper query refactor can remove remaining provider-local convenience joins if needed.
- Public-list Android TV QA and strict release-candidate benchmark evidence are intentionally out of scope for this package by Owner decision.

## Relevant Tests, Measurements, and Android TV QA

- `.\gradlew.bat :app:assembleDebug --console=plain`
- Targeted compile/test tasks for each affected module.
- Room migration/schema tests for schema slices.
- Focused Settings and route Android tests for UI slices.
- Targeted `git diff --check`.
- Passed: `.\gradlew.bat :core:database:compileDebugKotlin :core:database:compileDebugAndroidTestKotlin :data:provider:compileDebugKotlin :data:epg:compileDebugKotlin :data:media:compileDebugKotlin :app:compileDebugKotlin --console=plain`.
- Passed: `.\gradlew.bat :core:database:testDebugUnitTest :core:database:compileDebugAndroidTestKotlin :data:provider:compileDebugAndroidTestKotlin :data:epg:compileDebugAndroidTestKotlin :data:media:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :worker:testDebugUnitTest :data:playback:testDebugUnitTest --console=plain`.
- Passed: `.\gradlew.bat :core:database:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.core.database.VivicastDatabaseMigrationTest" --console=plain`.
- Passed: `.\gradlew.bat :data:provider:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.provider.RoomProviderRepositoryTest" --console=plain`.
- Passed: `.\gradlew.bat :data:epg:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.epg.RoomEpgRepositoryTest" --console=plain`.
- Passed: `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomMediaRepositoryTest" --console=plain`.
- Passed: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest" --console=plain`.
- Passed: `.\gradlew.bat :app:assembleDebug --console=plain`.
- Passed: `git diff --check` with only LF/CRLF warnings.

## Open Owner Questions

- None for the current Package 16 scope.
