# Package 15 - Release Benchmark Evidence Plan

## Status

- Active package: Package 15 - Release Benchmark Evidence
- State: done
- Last completed step: Completed the final P15 validation and completion audit; Package 15 is closed as the current pre-release benchmark evidence package.
- Last validated state: Debug install/Home baseline passed; local Room/Search large fixture passed; EPG fixture passed; M3U parse plus Room commit fixture passed; Xtream metadata commit fixture passed; XMLTV 50,000-program commit plus cleanup fixture passed; Standard-Backup restore fixture passed; central-screen memory/UI smoke passed with `TOTAL PSS` +4,403 KB and no crash-buffer entries; final `:app:assembleDebug` and targeted `git diff --check` passed.
- Next concrete step: Continue with the next Owner-selected implementation or hardening task; strict release-candidate benchmark gaps are documented below.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `AGENTS.md`
- `README.md`
- `../vivicast-docs/codex/README.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/prd/PRD-v1/09-implementation-and-dod.md`
- `../vivicast-docs/prd/PRD-v1/13-test-strategy.md`

## Affected Masterplan Package

- Follow-up evidence package after Package 14 - Test Hardening, Performance, and Release Readiness.
- The docs masterplan ends at Package 14; this app-repo Package 15 does not add product scope and only works down the PRD 13 release-blocking evidence list documented in Package 14.

## Concrete Implementation Scope

- Document the Android TV emulator reference profile used for release benchmark evidence.
- Collect the smallest useful runtime evidence first:
  - debug install and launch
  - Home baseline `meminfo`
  - quick `gfxinfo` / framestats snapshot
  - app crash/log sanity where needed
- Reuse existing automated tests and instrumentation benchmarks where they already exist.
- Record which PRD 13 budget areas still require dedicated large-fixture harness work.

## Non-Scope

- No docs-repo edits.
- No product behavior changes.
- No new feature implementation.
- No private playlist, EPG, credential, token, or provider data in fixtures, logs, reports, or screenshots.
- No broad benchmark framework unless a concrete PRD 13 budget cannot be measured with existing Gradle/ADB tools.
- No release tagging, commit, branch, push, pull request, or publication.

## Affected App Modules and Files

- `codex/plans/APP-IMPLEMENTATION-PLAN.md`
- `codex/plans/P15-release-benchmark-evidence-plan.md`
- `codex/evidence/release-benchmark-2026-06-28.md`
- Build/test/runtime artifacts under ignored Gradle and Android output locations.
- `data/media/src/androidTest/java/com/vivicast/tv/data/media/RoomMediaRepositoryTest.kt`
- `data/media/src/androidTest/java/com/vivicast/tv/data/media/RoomCatalogImportRepositoryTest.kt`
- `data/epg/src/androidTest/java/com/vivicast/tv/data/epg/RoomEpgRepositoryTest.kt`

## Technical Approach

- Start with existing app and emulator tooling instead of adding new infrastructure.
- Store human-readable evidence in `codex/evidence/release-benchmark-2026-06-28.md`.
- Use Android TV emulator `emulator-5554` unless unavailable.
- Use `gfxinfo` for quick frame evidence and `meminfo` for quick memory snapshots.
- Use Perfetto or heap dumps only after a focused flow shows a problem or a PRD 13 budget requires deeper evidence.

## Risks and Assumptions

- A debug emulator measurement is useful evidence but not a final production benchmark.
- Empty or small local app data cannot satisfy large-fixture PRD 13 budgets.
- Full M3U/Xtream/XMLTV import and EPG-scale budgets likely require separate synthetic fixture generation and import harness work.
- Owner explicitly reduced the XMLTV check for this ongoing pre-release build phase from the original PRD 13 3,000,000-program target to a 50,000-program fixture with a 10-minute maximum; the strict 3,000,000-program benchmark is deferred until a later release-candidate decision.
- Some PRD 13 budgets may remain open until a release-candidate build and fixture set exist.

## Relevant Tests, Measurements, and QA

- `.\gradlew.bat :app:installDebug --console=plain`
- ADB reference profile checks.
- `adb shell dumpsys meminfo com.vivicast.tv`
- `adb shell dumpsys gfxinfo com.vivicast.tv`
- `adb shell dumpsys gfxinfo com.vivicast.tv framestats`
- `git diff --check` for touched plan/evidence files.

## Completed Work

- Created Package 15 technical plan.
- Created `codex/evidence/release-benchmark-2026-06-28.md`.
- Installed the debug app on the Android TV emulator with `.\gradlew.bat :app:installDebug --console=plain`.
- Captured reference device/runtime profile under `codex/evidence/artifacts/2026-06-28-baseline-home-component/`.
- Confirmed Vivicast Home starts through direct component launch on the TV emulator.
- Recorded that plain package launch and `am start -p` Leanback launch did not resolve/start the app on this emulator, while `cmd package resolve-activity` correctly reports `com.vivicast.tv/.MainActivity`.
- Added `largeFixtureSearchAndDatabaseBudgetsStayWithinPrd13Targets` to the existing Room media repository Android test.
- Captured first PRD 13 local Room/Search large-fixture evidence with 10,000 synthetic channels, 10,000 synthetic movies, and 10,000 synthetic series.
- Added `largeFixtureEpgNowNextAndDayViewStayWithinPrd13Targets` to the existing Room EPG repository Android test.
- Captured PRD 13 EPG query evidence with 50 synthetic channels and 7 days of half-hour programs.
- Added `largeFixtureM3uParseAndCommitStaysWithinPrd13Budget` to the existing Room catalog import Android test.
- Captured PRD 13 M3U parse plus Room commit evidence with 10,000 synthetic channels and 20 groups.
- Added `largeFixtureXtreamMetadataCommitStaysWithinPrd13Budget` to the existing Room catalog import Android test.
- Captured PRD 13 Xtream metadata commit evidence with 10,000 synthetic live entries, 50,000 synthetic movies, 20,000 synthetic series, and 90 categories.
- Added `largeFixtureXmltvCommitAndCleanupStaysWithinPrd13Budget` to the existing Room EPG repository Android test, scoped by Owner decision to 50,000 synthetic XMLTV programs and a 10-minute maximum for this run.
- Optimized XMLTV repository import to avoid materializing all programs in memory by streaming into Room insert chunks, using source/channel stable keys for imported entities, and adding cleanup outside the retention window.
- Captured XMLTV 50,000-program commit plus cleanup evidence under `codex/evidence/artifacts/2026-06-28-xmltv-50k-fixture/`.
- Added `restorerHandlesLargeUserDataFixture` to the existing Standard Backup Android test.
- Captured Standard-Backup restore evidence with 1,000 favorites, 2,000 playback-progress entries, and 2,000 Live-TV history entries under `codex/evidence/artifacts/2026-06-28-restore-large-user-fixture/`.
- Captured a 10-cycle central-screen memory/UI smoke run under `codex/evidence/artifacts/2026-06-28-memory-ui-cycles/`.

## Validation Log

- Passed: `.\gradlew.bat :app:installDebug --console=plain`.
- Checked: `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER com.vivicast.tv` returned `com.vivicast.tv/.MainActivity`.
- Not valid for app baseline: `adb shell monkey -p com.vivicast.tv 1` and `adb shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -p com.vivicast.tv` stayed on the Android TV launcher because this app exposes only a Leanback launcher category and the emulator did not resolve the package-only start.
- Passed: `adb shell am start -n com.vivicast.tv/.MainActivity`; process PID was present and the UI dump contained package `com.vivicast.tv` with `Home`, `Live-TV`, `Filme`, `Serien`, `Suche`, and `Einstellungen`.
- Passed: `adb logcat -b crash -d` after Home launch returned an empty crash buffer.
- Captured: Home `dumpsys meminfo` baseline with `TOTAL PSS` 89,112 KB and `TOTAL RSS` 195,896 KB.
- Captured with caveat: Home `dumpsys gfxinfo` cold-start sample with 20 frames, 2 janky frames by modern metric, 50th percentile 23 ms. This is too small to close PRD 13 frame-smoothness budgets.
- Passed: `.\gradlew.bat :data:media:compileDebugAndroidTestKotlin --console=plain`.
- Passed: `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomMediaRepositoryTest#largeFixtureSearchAndDatabaseBudgetsStayWithinPrd13Targets" --console=plain` with 1 test on the Android TV emulator.
- Captured: `VivicastBenchmark` log line under `codex/evidence/artifacts/2026-06-28-room-search-large-fixture/logcat-vivicast-benchmark.txt`: search p95 23 ms, search p99 25 ms, channel first-page p95 5 ms, movie first-page p95 4 ms, series first-page p95 4 ms, movie detail p95 1 ms, series detail p95 1 ms.
- Passed: `.\gradlew.bat :app:assembleDebug --console=plain`.
- Passed: `.\gradlew.bat :data:epg:compileDebugAndroidTestKotlin --console=plain`.
- Failed then fixed: first focused EPG benchmark run expected 49 programs for a one-day window, but `observeProgramsForChannel` intentionally includes `startTime <= toMillis`, so the boundary program made the correct count 50.
- Passed: `.\gradlew.bat :data:epg:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.epg.RoomEpgRepositoryTest#largeFixtureEpgNowNextAndDayViewStayWithinPrd13Targets" --console=plain` with 1 test on the Android TV emulator.
- Captured: `VivicastBenchmark` log line under `codex/evidence/artifacts/2026-06-28-epg-large-fixture/logcat-vivicast-benchmark.txt`: EPG Now/Next p95 83 ms and day-view p95 3 ms.
- Passed: targeted `git diff --check` with only an LF/CRLF warning for `RoomEpgRepositoryTest.kt`.
- Failed then fixed: first focused M3U benchmark compile used a non-existent `M3uImportResult` type; fixed to use the existing `CatalogImportResult`.
- Passed: `.\gradlew.bat :data:media:compileDebugAndroidTestKotlin --console=plain`.
- Passed: `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomCatalogImportRepositoryTest#largeFixtureM3uParseAndCommitStaysWithinPrd13Budget" --console=plain` with 1 test on the Android TV emulator.
- Captured: `VivicastBenchmark` log line under `codex/evidence/artifacts/2026-06-28-m3u-import-large-fixture/logcat-vivicast-benchmark.txt`: M3U parse 439 ms, Room commit 2,115 ms, total 2,554 ms for 10,000 channels and 20 categories.
- Passed: `.\gradlew.bat :data:media:compileDebugAndroidTestKotlin --console=plain` after adding the Xtream metadata benchmark.
- Passed: `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomCatalogImportRepositoryTest#largeFixtureXtreamMetadataCommitStaysWithinPrd13Budget" --console=plain` with 1 test on the Android TV emulator.
- Captured: `VivicastBenchmark` log line under `codex/evidence/artifacts/2026-06-28-xtream-metadata-large-fixture/logcat-vivicast-benchmark.txt`: Xtream metadata Room commit 15,839 ms for 10,000 live entries, 50,000 movies, 20,000 series, and 90 categories.
- Passed: `.\gradlew.bat :core:database:compileDebugKotlin :data:epg:compileDebugKotlin :data:epg:compileDebugAndroidTestKotlin --console=plain` after the XMLTV streaming import and cleanup changes.
- Aborted: the first 3,000,000-program XMLTV benchmark exceeded the practical timing for this ongoing pre-release pass; Owner then reduced the check target to 50,000 programs and a 10-minute maximum.
- Passed: `.\gradlew.bat :data:epg:compileDebugAndroidTestKotlin --console=plain` after changing the XMLTV benchmark fixture to 50,000 programs.
- Passed: `.\gradlew.bat :data:epg:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.epg.RoomEpgRepositoryTest#largeFixtureXmltvCommitAndCleanupStaysWithinPrd13Budget" --console=plain` with 1 test on the Android TV emulator.
- Captured: `VivicastBenchmark` log line under `codex/evidence/artifacts/2026-06-28-xmltv-50k-fixture/logcat-vivicast-benchmark.txt`: XMLTV import 6,787 ms, cleanup 115 ms, total 6,902 ms for 50,000 programs.
- Failed then fixed: first `:app:compileDebugAndroidTestKotlin` after adding the restore benchmark had a duplicate test header from a patch placement error; removed the duplicate header.
- Passed: `.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain` after the restore benchmark addition.
- Passed: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest#restorerHandlesLargeUserDataFixture" --console=plain` with 1 test on the Android TV emulator.
- Captured: `VivicastBenchmark` log line under `codex/evidence/artifacts/2026-06-28-restore-large-user-fixture/logcat-vivicast-benchmark.txt`: Standard-Backup restore 725 ms for 1 provider, 1,000 favorites, 2,000 playback-progress entries, and 2,000 Live-TV history entries.
- Passed: `.\gradlew.bat :app:installDebug --console=plain` before the memory/UI cycle smoke.
- Captured: 10-cycle central-screen navigation flow under `codex/evidence/artifacts/2026-06-28-memory-ui-cycles/`; `TOTAL PSS` rose from 97,404 KB to 101,807 KB, Views stayed 7, Activities stayed 1, and crash buffer was empty.
- Captured: `gfxinfo` for the same flow with 2,215 frames, 30 modern janky frames (1.35%), p50 20 ms, p90 23 ms, p95 32 ms, p99 42 ms.
- Passed: targeted `git diff --check` for Package 15 touched files with only LF/CRLF warnings before marking Package 15 done.
- Passed: `.\gradlew.bat :app:assembleDebug --console=plain` before marking Package 15 done.
- Done: Package 15 - Release Benchmark Evidence.

## Release-Candidate Gaps / Deferred

- Build reusable large synthetic benchmark fixtures without committing private provider data.
- Measure PRD 13 large-fixture budgets for M3U MockServer download timing and Xtream MockServer/JSON timing if strict transport-path evidence is required.
- Full PRD 13 XMLTV 3,000,000-program release-candidate timing remains deferred by explicit Owner decision for this ongoing pre-release build phase.
- Encrypted Full-Backup large-fixture timing remains open if strict Full-Backup performance evidence is required before release.
- Central-screen UI smoothness under populated large provider data remains open; current evidence is an empty-data debug/emulator smoke.
- Add explicit channel and episode direct-detail benchmark coverage if the Owner wants every detail-lookup subtype measured separately.
- Decide later whether to turn these release-candidate gaps into a dedicated Package 16 or handle them directly before publication.

## Open Owner Questions

- None.
