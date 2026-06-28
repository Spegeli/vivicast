# Package 6 - Settings, Provider Management, and Data Maintenance UI

## Status

- Package: Package 6 - Settings, Provider Management, and Data Maintenance UI
- State: done
- Last completed step: Package 6 acceptance pass completed with selective history deletion, honest Backup section, and `\u00dcber die App` app information rows.
- Last validated state: Package 6 final pass passed Settings compile, Settings androidTest compile, app debug build, playback repository Android tests, and diff check.
- Next concrete step: Start Package 7 technical plan for Home, History, Favorites, and Continue.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `AGENTS.md`
- `README.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/prd/PRD-v1/04-search-settings-player-requirements.md`
- `../vivicast-docs/prd/PRD-v1/10-backup-import-requirements.md`
- `../vivicast-docs/prd/PRD-v1/11-about-app-requirements.md`
- `../vivicast-docs/prd/PRD-v1/07-background-jobs-performance.md`
- `../vivicast-docs/prd/PRD-v1/12-parser-source-contracts.md`
- `../vivicast-docs/design/screens/07-settings.md`
- `../vivicast-docs/design/screens/08-playlist-epg.md`
- `../vivicast-docs/design/wireframes/05-settings.md`
- `../vivicast-docs/design/components/settings.md`
- `../vivicast-docs/design/components/about-app.md`

## Scope

- Keep the existing Settings screen foundation.
- Replace app-local demo settings group usage with the final Settings groups.
- Split `Speicher & Verlauf` from `Über die App`.
- Add visible EPG retention rows backed by DataStore.
- Pass EPG retention values into EPG refresh cleanup.
- Keep provider-specific User-Agent absent from playlist and EPG forms.

## Non-Scope

- No full Backup/Restore implementation.
- No PIN storage and protection implementation.
- No full diagnostics ZIP export implementation.
- No redesign of all settings visuals.
- No docs-repo changes.

## Affected App Modules and Files

- `codex/plans/APP-IMPLEMENTATION-PLAN.md`
- `codex/plans/P06-settings-provider-management-plan.md`
- `core/datastore/src/main/java/com/vivicast/tv/core/datastore/UserPreferencesStore.kt`
- `core/datastore/src/main/java/com/vivicast/tv/core/datastore/DataStoreUserPreferencesStore.kt`
- `worker/src/main/java/com/vivicast/tv/worker/RefreshExecution.kt`
- `worker/src/test/java/com/vivicast/tv/worker/RefreshExecutionTest.kt`
- `app/src/main/java/com/vivicast/tv/di/AppContainer.kt`
- `app/src/main/java/com/vivicast/tv/MainActivity.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/VivicastDatabase.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/dao/PlaybackDao.kt`
- `data/playback/src/main/java/com/vivicast/tv/data/playback/PlaybackRepository.kt`
- `data/playback/src/main/java/com/vivicast/tv/data/playback/RoomPlaybackRepository.kt`
- `data/playback/src/androidTest/java/com/vivicast/tv/data/playback/RoomPlaybackRepositoryTest.kt`
- `feature/movies/src/androidTest/java/com/vivicast/tv/feature/movies/MoviesRouteContinueTest.kt`
- `feature/settings/src/main/java/com/vivicast/tv/feature/settings/SettingsRoute.kt`
- `feature/settings/src/androidTest/java/com/vivicast/tv/feature/settings/SettingsPlaybackPanelTest.kt`
- `feature/search/src/androidTest/java/com/vivicast/tv/feature/search/SearchRouteFocusTest.kt`

## Technical Approach

- Use existing DataStore repository and Settings composables.
- Use the existing `VivicastSettingsRow` pattern; no new settings abstraction.
- Keep EPG retention values clamped to 1..14 at storage and refresh boundaries.
- Use suspend providers in `DefaultEpgRefresher` so the worker reads current DataStore values when refresh runs.
- Keep the connection-test implementation in the app composition layer so Settings only depends on callbacks and provider request models.
- Reuse the existing central OkHttp client, M3U parser, Xtream client, and WorkManager playlist refresh scheduler.
- Use existing playback/search storage for Settings history deletion; add only the minimal repository/DAO delete methods needed by the UI.
- Keep Backup rows non-destructive until the dedicated Backup/Restore backend exists.

## Risks and Assumptions

- The current Settings screen already contains provider and EPG management foundations but is still incomplete for full Package 6.
- Backup, PIN, and diagnostics UI will stay as honest placeholders until their backend pieces exist.
- Existing source files contain older mojibake-visible strings; this package only fixes touched visible labels where practical.

## Validation Plan

- `.\gradlew.bat :core:datastore:compileDebugKotlin`
- `.\gradlew.bat :worker:testDebugUnitTest`
- `.\gradlew.bat :feature:settings:compileDebugKotlin`
- `.\gradlew.bat :feature:settings:compileDebugAndroidTestKotlin`
- `.\gradlew.bat :feature:search:compileDebugAndroidTestKotlin`
- `.\gradlew.bat :data:playback:compileDebugAndroidTestKotlin`
- `.\gradlew.bat :feature:movies:compileDebugAndroidTestKotlin`
- `.\gradlew.bat :data:playback:connectedDebugAndroidTest`
- `.\gradlew.bat :app:assembleDebug`
- `git diff --check`

## Progress

- Completed: Package 6 source and app-code inspection.
- Completed: Final Settings groups replace the old demo-backed settings group list in `SettingsRoute`.
- Completed: `Speicher & Verlauf` is separate from `\u00dcber die App`; Backup, PIN, and diagnostics remain honest placeholders until their backends are in scope.
- Completed: EPG Settings rows now expose interval, past/future retention, app-start trigger, playlist-change trigger, and manual refresh.
- Completed: EPG interval and retention values persist in DataStore and EPG cleanup reads current values at refresh execution time.
- Completed: Search androidTest fake updated for the expanded `UserPreferencesStore` interface.
- Validated: `.\gradlew.bat :core:datastore:compileDebugKotlin` passed.
- Validated: `.\gradlew.bat :worker:testDebugUnitTest` passed.
- Validated: `.\gradlew.bat :feature:settings:compileDebugAndroidTestKotlin` passed.
- Validated: `.\gradlew.bat :feature:search:compileDebugAndroidTestKotlin` passed.
- Validated: `.\gradlew.bat :app:assembleDebug` passed.
- Validated: `git diff --check` passed with only existing LF/CRLF warnings.
- Completed: Full provider-management flow was re-aligned in later P06 slices, including explicit connection-test behavior and final empty/error states.
- Completed later: Backup/Restore and PIN protection were implemented in Package 12.
- Still open outside P06: diagnostics export remains a dedicated follow-up item; see `codex/plans/P16-remaining-realignment-release-candidate-plan.md`.
- Completed: Provider-management UI now uses visible Wiedergabelisten wording for the main management surface and delete flow.
- Completed: Duplicate provider names are blocked before save instead of being described as allowed.
- Completed: M3U forms no longer expose Live-TV/Filme/Serien import toggles; Xtream keeps those toggles.
- Completed: Provider save requires an explicit local form check after field changes.
- Validated: `.\gradlew.bat :feature:settings:compileDebugAndroidTestKotlin` passed.
- Validated: `.\gradlew.bat :app:assembleDebug` passed.
- Validated: `git diff --check` passed with only existing LF/CRLF warnings.
- Completed: Provider connection testing now performs a real network request through the existing central OkHttp-backed M3U/Xtream clients before save can proceed.
- Completed: Existing providers keep their passed connection state for metadata-only edits; changing URL/access fields requires a new test.
- Completed: Successful provider save enqueues the provider playlist refresh/import worker.
- Validated: `.\gradlew.bat :feature:settings:compileDebugKotlin` passed.
- Validated: `.\gradlew.bat :feature:settings:compileDebugAndroidTestKotlin` passed.
- Validated: `.\gradlew.bat :app:assembleDebug` passed.
- Validated: `git diff --check` passed with only existing LF/CRLF warnings.
- Completed: Cache clearing now uses a confirmation dialog instead of immediate deletion.
- Completed: `Speicher & Verlauf` now exposes confirmed full history deletion wired to provider playback/live-TV history and DataStore search history.
- Validated: `.\gradlew.bat :feature:settings:compileDebugKotlin` passed.
- Validated: `.\gradlew.bat :feature:settings:compileDebugAndroidTestKotlin` passed.
- Validated: `.\gradlew.bat :app:assembleDebug` passed.
- Validated: `git diff --check` passed with only existing LF/CRLF warnings.
- Completed: Selective history deletion is available for Live-TV, Filme, Serien, Suche, and full history.
- Completed: Playback repository and DAO expose the minimal delete paths for Live-TV history, movie progress, and series/episode progress.
- Completed: Backup section now shows the required v1 surfaces as honest prepared rows without storing a persistent backup type.
- Completed: `\u00dcber die App` now receives app version, package name, database version, and Android version from the app layer.
- Validated: `.\gradlew.bat :data:playback:compileDebugAndroidTestKotlin` passed.
- Validated: `.\gradlew.bat :feature:settings:compileDebugKotlin` passed.
- Validated: `.\gradlew.bat :feature:settings:compileDebugAndroidTestKotlin` passed.
- Validated: `.\gradlew.bat :feature:movies:compileDebugAndroidTestKotlin` passed.
- Validated: `.\gradlew.bat :data:playback:connectedDebugAndroidTest` passed on Android TV emulator.
- Validated: `.\gradlew.bat :app:assembleDebug` passed.
- Validated: `git diff --check` passed with only existing LF/CRLF warnings.
