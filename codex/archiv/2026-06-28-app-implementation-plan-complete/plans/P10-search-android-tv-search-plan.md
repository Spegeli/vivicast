# Package 10 - Search and Android TV Search Plan

## Status

- Package: Package 10 - Search and Android TV Search
- State: done
- Last completed step: Search result actions and Android TV system search exposure are wired through stable-key deep links for Channel, Movie, and Series; internal EPG results open Live-TV sender mode and focus the matching programme.
- Last validated state: Database/media/app compile, focused Room media repository Android test, Search connected Android test, Live-TV connected Android test, app assemble/install, Android TV suggestion-provider smoke query, and targeted diff check passed after the final Package 10 slice.
- Next concrete step: Start Package 11 - Player, Playback, Catch-Up, Timeshift, and Progress.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/prd/PRD-v1/04-search-settings-player-requirements.md`
- `../vivicast-docs/prd/PRD-v1/06-data-model.md`
- `../vivicast-docs/prd/PRD-v1/08-android-tv-security.md`
- `../vivicast-docs/architecture/decisions/ADR-005-local-search.md`
- `../vivicast-docs/architecture/decisions/ADR-008-android-tv-integration.md`
- `../vivicast-docs/design/screens/06-search.md`
- `../vivicast-docs/design/wireframes/04-search.md`

## Masterplan Package

- Package 10 - Search and Android TV Search

## Concrete Scope

- Re-align internal app search with Room-backed v1 behavior:
  - groups: `Kanäle`, `Filme`, `Serien`, `EPG`
  - no episode result group
  - 300 ms debounce
  - max 20 results per group
  - EPG results only after the documented minimum query length
  - search history max 20 entries, visible even while results are visible
- Move search history persistence to Room `search_history`.
- Add or rework the search repository boundary so UI does not write search history through DataStore.
- Replace primary `LIKE` search with Room FTS4-backed search in scoped slices.
- Prepare Android TV system search as a separate derived-index path that excludes EPG, episodes, Catch-Up, protected content, pending references, and disabled providers.

## Non-Scope

- No docs-repo changes.
- No PIN/protection implementation beyond preserving the required filtering hooks and documenting missing enforcement.
- No Watch Next implementation; that belongs to later Android TV integration/player packages unless a minimal data index is required for Package 10.
- No full visual redesign beyond matching the documented Search layout and labels.
- No provider/network search.

## Affected App Areas

- `feature/search/src/main/java/com/vivicast/tv/feature/search/SearchRoute.kt`
- `feature/search/src/androidTest/java/com/vivicast/tv/feature/search/SearchRouteFocusTest.kt`
- `feature/live-tv/src/main/java/com/vivicast/tv/feature/livetv/LiveTvRoute.kt`
- `feature/live-tv/src/androidTest/java/com/vivicast/tv/feature/livetv/LiveTvRouteFocusTest.kt`
- `feature/movies/src/main/java/com/vivicast/tv/feature/movies/MoviesRoute.kt`
- `feature/series/src/main/java/com/vivicast/tv/feature/series/SeriesRoute.kt`
- `app/src/main/java/com/vivicast/tv/search/AndroidTvSearchSuggestionProvider.kt`
- `app/src/main/res/xml/searchable.xml`
- `app/src/main/AndroidManifest.xml`
- `data/media/src/main/java/com/vivicast/tv/data/media/MediaRepository.kt`
- `data/media/src/main/java/com/vivicast/tv/data/media/RoomMediaRepository.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/dao/SearchDao.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/dao/CatalogDao.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/dao/EpgDao.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/model/VivicastEntities.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/VivicastDatabase.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/VivicastMigrations.kt`
- `app/src/main/java/com/vivicast/tv/MainActivity.kt`

## Technical Approach

- Slice 1: Search history Room alignment.
  - Add DAO operations for `search_history`.
  - Add a repository-facing API for observe/add/delete/clear history.
  - Keep max 20 and normalization in one data-layer path.
  - Update SearchRoute and maintenance clearing to use Room, not DataStore.
- Slice 2: Search UI behavior alignment.
  - Keep history visible with filled query and visible results.
  - Keep labels German and exact: `Kanäle`, `Filme`, `Serien`, `EPG`.
  - Replace visible `Voice` with the documented German voice-search action label when wiring the action.
- Slice 3: Internal FTS foundation.
  - Add FTS4 entities/tables for channel, movie, series, and EPG program search.
  - Rebuild/populate FTS rows from productive data during provider and EPG commits.
  - Replace primary search DAO calls with `MATCH` queries and deterministic ranking/order.
- Slice 4: Android TV system search foundation.
  - Add derived system-search data model or sync path only for Channel, Movie, Series.
  - Use stable references/deep links, no stream URLs, tokens, cookies, headers, or local Room IDs.
  - Exclude EPG, episodes, Catch-Up, pending references, disabled providers, and protected content.

## Risks and Assumptions

- Internal search now uses Room FTS4 as the primary path; remaining `LIKE` queries are legacy/helper paths and must not replace the FTS path for large result groups.
- `SearchHistoryEntity` now stores `normalizedQuery` and `lastUsedAt`, and Search UI history writes go through Room.
- Android TV system search depends on stable references and protection state. Full protection enforcement may need Package 12 hooks, so Package 10 should create safe exclusion points rather than publish protected content blindly.
- Current demo fallback remains in `SearchRoute`; reachable app wiring already passes repositories, but the fallback should be removed during the broader demo cleanup or when tests no longer depend on it.

## Validation Plan

- Compile after each slice:
  - `.\gradlew.bat :core:database:compileDebugKotlin`
  - `.\gradlew.bat :data:media:compileDebugKotlin`
  - `.\gradlew.bat :feature:search:compileDebugKotlin :feature:search:compileDebugAndroidTestKotlin`
  - `.\gradlew.bat :app:assembleDebug`
- Run focused tests:
  - `.\gradlew.bat :core:database:testDebugUnitTest`
  - `.\gradlew.bat :data:media:connectedDebugAndroidTest`
  - `.\gradlew.bat :feature:search:connectedDebugAndroidTest`
- Run targeted `git diff --check` after each completed slice.
- Use Android TV emulator QA for focus, keyboard/back behavior, history chips, and result row navigation once UI behavior changes.

## Progress

- Done: Package 10 docs and current implementation inspected.
- Done: Technical plan created.
- Done: Search history Room alignment.
- Done: Search UI behavior alignment.
- Done: Internal FTS foundation.
- Done: Android TV system search foundation.
- Done: Search result actions.
- Done: Android TV system search provider and stable-key deep-link opening.

## Validation Log

- Passed: `.\gradlew.bat :core:database:compileDebugKotlin :data:media:compileDebugKotlin :feature:search:compileDebugKotlin :feature:search:compileDebugAndroidTestKotlin :app:compileDebugKotlin` after Search history Room alignment.
- Passed: `.\gradlew.bat :core:database:testDebugUnitTest` after database schema/migration changes.
- Passed: `.\gradlew.bat :feature:search:connectedDebugAndroidTest` with 4 tests on Android TV emulator after Search history UI wiring.
- Passed: `.\gradlew.bat :app:assembleDebug` after Search history Room alignment.
- Passed: `adb -s emulator-5554 shell am instrument -w -e class com.vivicast.tv.data.media.RoomMediaRepositoryTest com.vivicast.tv.data.media.test/androidx.test.runner.AndroidJUnitRunner` with 3 tests after installing `data/media` AndroidTest APK.
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Search history Room alignment.
- Blocked unrelated: full `.\gradlew.bat :data:media:connectedDebugAndroidTest` still fails in `RoomCatalogImportRepositoryTest` on existing catalog-import remote-id expectations, not in the new Search history path.
- Passed: `.\gradlew.bat :feature:search:compileDebugKotlin :feature:search:compileDebugAndroidTestKotlin` after Search UI label alignment.
- Passed: `.\gradlew.bat :feature:search:connectedDebugAndroidTest` with 4 tests on Android TV emulator after Search UI label alignment.
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Search UI label alignment.
- Passed: `.\gradlew.bat :core:database:compileDebugKotlin` after adding FTS4 entities and database version 4.
- Passed: `.\gradlew.bat :data:media:compileDebugKotlin :data:media:compileDebugAndroidTestKotlin` after switching repository search to FTS.
- Passed: `.\gradlew.bat :core:database:testDebugUnitTest` after schema 4 export and FTS schema test coverage.
- Passed: `adb -s emulator-5554 shell am instrument -w -e class com.vivicast.tv.data.media.RoomMediaRepositoryTest com.vivicast.tv.data.media.test/androidx.test.runner.AndroidJUnitRunner` with 3 tests after FTS query and EPG minimum-length coverage.
- Passed: `.\gradlew.bat :feature:search:connectedDebugAndroidTest` with 4 tests on Android TV emulator after FTS foundation.
- Passed: `.\gradlew.bat :app:assembleDebug` after FTS foundation.
- Passed: targeted `git diff --check` with only LF/CRLF warnings after FTS foundation.
- Passed: `.\gradlew.bat :core:database:compileDebugKotlin :data:media:compileDebugKotlin :data:media:compileDebugAndroidTestKotlin :data:provider:compileDebugKotlin` after Android TV system search derived-index foundation.
- Passed: `.\gradlew.bat :core:database:testDebugUnitTest` after database version 5 schema export and Android TV search schema coverage.
- Passed: `adb -s emulator-5554 shell am instrument -w -e class com.vivicast.tv.data.media.RoomMediaRepositoryTest com.vivicast.tv.data.media.test/androidx.test.runner.AndroidJUnitRunner` with 4 tests after Android TV search derived-index coverage.
- Passed: `.\gradlew.bat :data:provider:connectedDebugAndroidTest` with 4 tests on Android TV emulator after provider-triggered index rebuild wiring.
- Passed: `.\gradlew.bat :feature:search:connectedDebugAndroidTest` with 4 tests on Android TV emulator after Android TV system search foundation.
- Passed: `.\gradlew.bat :app:assembleDebug` after Android TV system search foundation.
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Android TV system search foundation.
- Passed: `.\gradlew.bat :feature:search:compileDebugKotlin :feature:search:compileDebugAndroidTestKotlin :feature:live-tv:compileDebugKotlin :feature:live-tv:compileDebugAndroidTestKotlin :feature:movies:compileDebugKotlin :feature:series:compileDebugKotlin :app:compileDebugKotlin` after Search result actions.
- Passed: targeted `git diff --check` with only LF/CRLF warnings after Search result actions.
- Passed: `.\gradlew.bat :feature:search:connectedDebugAndroidTest` with 5 tests on Android TV emulator after Search result actions.
- Passed: `.\gradlew.bat :feature:live-tv:connectedDebugAndroidTest` with 7 tests on Android TV emulator after EPG target focus wiring.
- Passed: `.\gradlew.bat :core:database:compileDebugKotlin :data:media:compileDebugKotlin :app:compileDebugKotlin` after Android TV system search provider/deep-link wiring.
- Passed: `.\gradlew.bat :data:media:compileDebugAndroidTestKotlin :app:compileDebugKotlin` after Android TV suggestion/stable-key repository tests.
- Passed: `.\gradlew.bat :core:database:testDebugUnitTest` after final Package 10 database DAO changes.
- Passed: `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomMediaRepositoryTest"` with 5 focused tests on Android TV emulator.
- Passed: `.\gradlew.bat :feature:search:connectedDebugAndroidTest :feature:live-tv:connectedDebugAndroidTest` with 5 Search tests and 7 Live-TV tests on Android TV emulator.
- Passed: `.\gradlew.bat :app:assembleDebug` after final Package 10 wiring.
- Passed: `.\gradlew.bat :app:installDebug` after final Package 10 wiring.
- Passed: `adb shell content query --uri "content://com.vivicast.tv.search/search_suggest_query/dune?limit=5"` returned no crash and no result on the empty installed app database.
- Passed: targeted `git diff --check` with only LF/CRLF warnings after final Package 10 wiring.

## Package Done Notes

- Android TV system search publishes only the derived Room index for Channel, Movie, and Series.
- EPG results remain internal only and open Live-TV sender mode with programme focus.
- Deep links use provider/media stable keys and contain no Room IDs, stream URLs, tokens, headers, cookies, or credentials.
- Full runtime PIN-gated protected-content enforcement still belongs to the later PIN/protection package. Package 10 keeps the current conservative guard by excluding adult-tagged movies/series from the system-search index.
