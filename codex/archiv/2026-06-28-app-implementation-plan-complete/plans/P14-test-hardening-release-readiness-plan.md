# Package 14 - Test Hardening, Performance, and Release Readiness Plan

## Status

- Active package: Package 14 - Test Hardening, Performance, and Release Readiness
- State: done
- Last completed step: Completed PRD 13 release-readiness gap assessment and documented the remaining release-blocking benchmark evidence.
- Last validated state: Full `testDebugUnitTest`, Room migration Android tests, Xtream MockWebServer Android tests, M3U 10k parser smoke, Room media repository windowed-query Android tests, feature compile/androidTest compile, Live-TV/Filme/Serien connected tests, and `:app:assembleDebug` pass; full PRD 13 benchmark evidence is documented as a release blocker.
- Next concrete step: Before a v1 release candidate, run a dedicated benchmark/evidence pass on the documented reference profile or record an explicit Owner risk decision for each missed PRD 13 budget.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `../vivicast-docs/codex/README.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/prd/PRD-v1/09-implementation-and-dod.md`
- `../vivicast-docs/prd/PRD-v1/13-test-strategy.md`

## Affected Masterplan Package

- Package 14 - Test Hardening, Performance, and Release Readiness

## Concrete Implementation Scope

- Build an evidence-driven gap inventory against the v1 DoD and PRD 13 test strategy.
- Run the existing focused test suites that are already meaningful for release readiness.
- Add missing low-risk automated tests where clear gaps are found and the behavior is already implemented.
- Prefer strengthening existing parser, repository, backup/restore, focus, system-integration, and redaction tests over adding broad new harnesses.
- Document manual Android TV QA evidence for system surfaces, D-Pad/focus paths, font/resolution checks, and known limitations.
- Identify performance-budget coverage gaps and add the smallest useful measurements or explicit risk notes.

## Non-Scope

- No docs-repo edits.
- No new product scope.
- No broad UI redesign or feature implementation.
- No large fixture generation unless it is directly needed for a specific PRD 13 budget check.
- No release tagging, commit, branch, push, pull request, or publication.

## Affected App Modules and Files

- Existing tests under `app/src/androidTest`, `core/*/src/test`, `core/*/src/androidTest`, `data/*/src/test`, `data/*/src/androidTest`, `feature/*/src/androidTest`, `iptv/*/src/test`, and `worker/src/test`.
- Existing app-repo working plans under `codex/plans/`.
- Potential targeted test fixtures under existing module test resources if a concrete gap requires them.

## Initial App Inspection

- Earlier packages already added focused tests for parser slices, data repositories, settings flows, Live-TV, VOD, Search, Player, Backup/Restore, protection gates, Android TV Search, Watch Next, and Android TV focus paths.
- Package 14 must not assume that this is release-complete until the coverage inventory is checked against PRD 13.
- Performance budgets and large-fixture coverage are likely the largest remaining release-readiness gap.
- Current test dependencies are intentionally lean: JUnit4, AndroidX test runner/ext/core, Compose UI test, Room testing, and coroutine test support where already needed.
- No MockK/Mockito, Robolectric, Espresso, UIAutomator, MockWebServer, screenshot test, or benchmark framework is currently wired.

## Test Inventory

- Existing automated coverage includes parser tests for M3U/XMLTV/Xtream, worker refresh/orchestration tests, Room repository tests, backup/restore roundtrips, PIN/security tests, playback/progress tests, Live-TV focus and CH+/CH- tests, Player focus tests, Search focus tests, Android TV Search provider checks, Watch Next integration tests, and protection gate tests.
- Room database schema export and migrations `1->2`, `2->3`, `3->4`, and `4->5` exist. Package 14 now adds explicit `MigrationTestHelper` coverage for each step and for a small v1 fixture migrated to the latest schema.
- Parser golden coverage exists, but PRD 13 large-fixture and performance-budget evidence is not yet documented.
- Xtream network/mockserver coverage now uses MockWebServer in Android tests for real OkHttp success and HTTP error paths. M3U/XMLTV fetcher paths still rely on worker fake fetchers plus parser fixtures.
- M3U parser now has generated 10,000-channel smoke coverage with a generous parser-only timing guard. This does not replace the full PRD 13 10,000-channel download/parse/stage/commit import benchmark.
- Room media repository now exposes windowed first-page paths for channels, movies, and series and uses a direct database lookup for Series detail resolution. The focused Android test gives smoke-level p95 timing evidence on an in-memory 1,000-item-per-type synthetic catalog, but it is not a substitute for full large-fixture benchmark reports on the documented reference profile.
- Live-TV, Filme, and Serien routes now use the windowed repository page paths for normal catalog categories and direct lookup paths for favorites, continue, target, and detail resolution. Non-special categories expose a `Mehr laden` entry so additional provider content is reachable without collecting the full provider catalog in UI state.
- Manual Android TV QA evidence exists from Package 13 for deep links, search-provider smoke, D-Pad/focus paths, display sizes, and large font.
- Next validation should first reuse the existing test stack before adding any new test infrastructure.

## Technical Approach

- First create a test inventory from Gradle test tasks and existing test file names.
- Map existing coverage to PRD 13 categories:
  - parser golden tests
  - mockserver/network tests
  - database/migration tests
  - refresh/atomicity tests
  - backup/restore roundtrips
  - player/progress tests
  - Android TV focus/system-integration tests
  - protection/redaction/security tests
  - list/paging/UI performance tests
  - measurable performance budgets
- Run high-value existing suites in batches and record failures before changing code.
- Fix only clear technical failures or obvious missing tests that do not alter product behavior.
- Keep all evidence in this plan and the main implementation plan.

## Risks and Assumptions

- Full PRD 13 release readiness may expose gaps that are too large for one small implementation slice.
- Large-fixture and performance-budget work may require dedicated benchmark infrastructure rather than ordinary unit tests.
- Android TV system UI surfaces may remain partly manual because emulator automation is not always stable.
- Existing dirty worktree is expected from prior packages; Package 14 edits must stay scoped.

## Reference Profile Used During Package 14

- ADB target: `emulator-5554`
- Model: `AOSP TV on x86`
- Android: `16`, API `36`
- Display: `1920x1080`, density `320`
- Memory: `2020264 kB`
- Caveat: this is the Android TV emulator profile used for automated and smoke validation. It is documented as a working reference profile, but it is not yet a full release benchmark report with repeated runs, medians, p95/p99, heap peaks, and large PRD 13 fixtures.

## Release Readiness Evidence Matrix

| PRD 13 area | Package 14 evidence | Release status |
| --- | --- | --- |
| Full unit test graph | `testDebugUnitTest` passes. | Covered for current automated unit tests. |
| Parser golden tests | Existing M3U/XMLTV/Xtream unit tests pass; M3U has generated 10,000-channel parser-only smoke coverage. | Partially covered; not a full large import benchmark. |
| Mockserver network paths | Xtream `player_api.php` success and HTTP 403 are covered through MockWebServer Android tests. | Partially covered; M3U/XMLTV mockserver, timeout, redirect, slow/partial response matrix is still open. |
| Room migrations | Migration Android tests cover schema steps, stable keys, pending-reference fields, Search FTS rebuild, and Android TV Search rebuild. | Covered for small fixtures; large-fixture migration benchmark is open. |
| Database first page/detail budgets | Room media Android test measures smoke-level p95 for 1,000 channels, movies, and series plus Series detail lookup. | Partially covered; PRD 13 large fixtures are open. |
| UI windowing / no full provider library in UI state | Live-TV, Filme, and Serien now use windowed page queries and direct lookup paths. | Covered for touched catalog UI paths. |
| Search budget | Search implementation and focus tests exist; result caps are enforced by repository paths. | Functional coverage exists; large-fixture p95/p99 search benchmark is open. |
| Import budgets | M3U parser-only 10,000-channel smoke exists. | Release-blocking gap for full M3U/Xtream/XMLTV download, parse, stage, commit benchmark. |
| EPG budgets | Existing EPG repository/import tests exist. | Release-blocking gap for large Now/Next, day view, cleanup, and XMLTV import benchmarks. |
| Backup/restore roundtrips | Existing Package 12 automated roundtrips exist. | Functional coverage exists; large user-data fixture benchmark is open. |
| Memory budgets | No dedicated meminfo, heap, Perfetto, or repeated central-screen cycle report was produced in Package 14. | Release-blocking gap before v1 release candidate. |
| Android TV focus/resolution | Package 13 emulator QA plus Package 14 connected feature tests pass. | Covered for smoke/focus paths tested; real-device QA remains a release QA item if required. |

## Release Decision

- Package 14 is complete as test hardening and evidence inventory.
- Vivicast is not yet a v1 release candidate because PRD 13 requires measured large-fixture performance budgets before release.
- The next work should be a dedicated release benchmark pass, not more production feature code.

## Relevant Tests and QA

- Existing targeted unit and Android tests across parser, data, app, feature, and worker modules.
- `.\gradlew.bat testDebugUnitTest --console=plain` if the current project/task graph supports it reliably.
- Focused connected Android tests for app/system integration and key feature modules.
- Manual Android TV QA evidence for remaining system UI and display checks.
- Targeted `git diff --check` for files touched during Package 14.

## Completed Work

- Created Package 14 technical plan.
- Completed existing test setup and coverage inventory.
- Added `VivicastDatabaseMigrationTest` for Room migration validation, stable-key carry-forward, pending-reference fields, Search FTS rebuild, and Android TV Search rebuild.
- Wired `:core:database` Android test runner, Room testing dependency, AndroidX test dependencies, and schema assets.
- Added `OkHttpXtreamTransportTest` with local MockWebServer coverage for Xtream `player_api.php` success and HTTP 403 error mapping.
- Added `mockwebserver3` as a test-only dependency and allowed cleartext only in the `:iptv:xtream` androidTest manifest for localhost mockserver traffic.
- Added a generated 10,000-channel M3U parser smoke test without committing a large fixture file.
- Added Room DAO and media repository page methods for channel, movie, and series first-page queries.
- Changed Series detail lookup from full-provider list filtering to a direct indexed Room query.
- Added Android test coverage for windowed catalog pages, negative limit coercion, direct Series detail lookup, and smoke-level p95 timing against PRD 13 first-page/detail budgets.
- Re-aligned Live-TV, Filme, and Serien UI state to use windowed repository pages for normal catalog categories.
- Changed VOD favorites/continue UI lists to use direct media lookups instead of full-provider list filtering.
- Added `Mehr laden` entries for non-special Live-TV, Filme, and Serien catalog pages.
- Changed target and detail resolution in Filme and Serien to load direct repository items when the item is outside the current visible page.
- Updated the Series route Android test fake for direct `getSeries` lookup used by the windowed UI state.
- Ran the full debug unit test graph and documented the current Android TV emulator reference profile used for Package 14 validation.
- Documented the PRD 13 release-readiness evidence matrix and separated completed Package 14 evidence from release-blocking benchmark gaps.

## Validation Log

- Checked: existing release-relevant tests and dependencies inventory against PRD 13 and PRD 9; main gaps are migration tests, mockserver/network harness, large-fixture/performance evidence, and benchmark/screenshot infrastructure if later required.
- Passed: `.\gradlew.bat :iptv:m3u:testDebugUnitTest :iptv:xmltv:testDebugUnitTest :iptv:xtream:testDebugUnitTest :worker:testDebugUnitTest :core:security:testDebugUnitTest :core:cache:testDebugUnitTest :core:database:testDebugUnitTest :core:player:testDebugUnitTest :data:playback:testDebugUnitTest --console=plain`.
- Failed then fixed: first `:core:database:connectedDebugAndroidTest` migration run could not find exported schemas in Android test assets; fixed by wiring `core/database/schemas` into the `androidTest` assets source set.
- Passed: `.\gradlew.bat :core:database:compileDebugAndroidTestKotlin --console=plain`.
- Passed: `.\gradlew.bat :core:database:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.core.database.VivicastDatabaseMigrationTest" --console=plain` with 2 tests on the Android TV emulator.
- Passed: `.\gradlew.bat :app:assembleDebug --console=plain`.
- Passed: targeted `git diff --check` with only LF/CRLF warning for `core/database/build.gradle.kts`.
- Failed then redirected: attempted a JVM local HTTP server test for Xtream, but Android unit tests did not expose `com.sun.net.httpserver`; replaced with MockWebServer.
- Failed then redirected: attempted OkHttp/MockWebServer in JVM unit tests, but Android's mocked `Log` made real OkHttp unsuitable there; moved real HTTP coverage to an Android test.
- Failed then fixed: first Xtream MockWebServer Android run hit test-APK cleartext policy for localhost; fixed with a test-only `android:usesCleartextTraffic="true"` manifest in `:iptv:xtream`.
- Passed: `.\gradlew.bat :iptv:xtream:testDebugUnitTest --console=plain`.
- Passed: `.\gradlew.bat :iptv:xtream:compileDebugAndroidTestKotlin --console=plain`.
- Passed: `.\gradlew.bat :iptv:xtream:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.iptv.xtream.OkHttpXtreamTransportTest" --console=plain` with 2 tests on the Android TV emulator.
- Passed: `.\gradlew.bat :app:assembleDebug --console=plain` after Xtream mockserver coverage.
- Passed: targeted `git diff --check` with only LF/CRLF warnings for touched P14 files.
- Passed: `.\gradlew.bat :iptv:m3u:testDebugUnitTest --console=plain` after adding generated 10,000-channel parser smoke coverage.
- Passed: `.\gradlew.bat :app:assembleDebug --console=plain` after M3U large-fixture smoke coverage.
- Passed: targeted `git diff --check` with only LF/CRLF warnings for touched P14 files.
- Passed: `.\gradlew.bat :core:database:compileDebugKotlin :data:media:compileDebugKotlin :data:media:compileDebugAndroidTestKotlin --console=plain` after adding Room-backed windowed catalog query paths.
- Passed: `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomMediaRepositoryTest" --console=plain` with 8 tests on the Android TV emulator after windowed catalog query coverage.
- Passed: `.\gradlew.bat :app:assembleDebug --console=plain` after windowed catalog query coverage.
- Passed: targeted `git diff --check` with only LF/CRLF warnings for touched P14 files after windowed catalog query coverage.
- Failed then fixed: first `:feature:series:connectedDebugAndroidTest` run after UI windowing missed the continue-card progress text because the Android test fake did not implement the direct `getSeries` path; fixed by adding the fake lookup.
- Passed: `.\gradlew.bat :feature:live-tv:compileDebugKotlin :feature:movies:compileDebugKotlin :feature:series:compileDebugKotlin --console=plain`.
- Passed: `.\gradlew.bat :feature:live-tv:compileDebugAndroidTestKotlin :feature:movies:compileDebugAndroidTestKotlin :feature:series:compileDebugAndroidTestKotlin --console=plain`.
- Passed: `.\gradlew.bat :feature:live-tv:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.livetv.LiveTvRouteFocusTest" --console=plain` with 8 tests on the Android TV emulator.
- Passed: `.\gradlew.bat :feature:movies:connectedDebugAndroidTest --console=plain` with 2 tests on the Android TV emulator.
- Passed: `.\gradlew.bat :feature:series:connectedDebugAndroidTest --console=plain` with 3 tests on the Android TV emulator.
- Passed: `.\gradlew.bat :app:assembleDebug --console=plain` after UI windowing.
- Passed: targeted `git diff --check` with only LF/CRLF warnings for touched P14 UI, test, and plan files after UI windowing.
- Passed: `.\gradlew.bat testDebugUnitTest --console=plain` for the full debug unit test graph.
- Checked: `adb devices`, Android version/API, display size/density, and memory for the Package 14 Android TV emulator reference profile.

## Release-Blocking Gaps Before v1 Release

- Superseded partly by Package 15: local Room/Search, EPG query, M3U parse plus Room commit, Xtream metadata commit, XMLTV 50,000-program import/cleanup, Standard-Backup restore, and empty-data memory/UI smoke evidence now exist.
- Still open for strict release candidate: M3U MockServer download timing.
- Still open for strict release candidate: Xtream MockServer/JSON timing across categories/live/VOD/series/details plus timeout/redirect/slow/partial response matrix.
- Still open for strict release candidate: full PRD 13 XMLTV 3,000,000-program benchmark unless explicitly risk-accepted by the Owner.
- Still open for strict release candidate: encrypted Full-Backup large-fixture timing if strict Full-Backup performance evidence is required.
- Still open for strict release candidate: central-screen UI smoothness under populated large provider data; current evidence is an empty-data debug/emulator smoke.

## Open Owner Questions

- None.
