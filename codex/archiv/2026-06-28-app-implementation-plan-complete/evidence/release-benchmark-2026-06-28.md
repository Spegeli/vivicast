# Vivicast Release Benchmark Evidence - 2026-06-28

## Status

- State: done for the current pre-release Package 15 evidence package.
- Scope: PRD 13 release benchmark/evidence follow-up after Package 14.
- Caveat: this file is evidence for current debug/emulator runs. It is not a final v1 release benchmark report; strict release-candidate gaps are listed below.

## Sources Checked

- `AGENTS.md`
- `README.md`
- `../vivicast-docs/codex/README.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/prd/PRD-v1/09-implementation-and-dod.md`
- `../vivicast-docs/prd/PRD-v1/13-test-strategy.md`

## Reference Profile

- ADB serial: `emulator-5554`
- Device model: `AOSP TV on x86`
- Android version: `16`
- API level: `36`
- Display size: `1920x1080`
- Density: `320`
- Device memory: `MemTotal` 2,020,264 KB; `MemAvailable` 666,404 KB during baseline capture.
- Build type measured: debug install on Android TV emulator.

## Baseline Runtime Evidence

- Build/install: `.\gradlew.bat :app:installDebug --console=plain` passed.
- Standard package launch attempt: `adb shell monkey -p com.vivicast.tv 1` did not find a launchable activity. This is not an app crash; the manifest exposes `LEANBACK_LAUNCHER`, not the regular `LAUNCHER` category.
- Leanback package-only launch attempt: `adb shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -p com.vivicast.tv` did not start the activity on this emulator, even though `cmd package resolve-activity` reported `com.vivicast.tv/.MainActivity`.
- Valid baseline launch: `adb shell am start -n com.vivicast.tv/.MainActivity` started Vivicast successfully.
- UI state: Home was visible with package `com.vivicast.tv`, main navigation `Home`, `Live-TV`, `Filme`, `Serien`, `Suche`, `Einstellungen`, focused `Home`, and the empty-state copy/action for no playlist.
- Crash sanity: `logcat -b crash` was empty after the successful Home launch.
- Startup log: ActivityTaskManager reported `Displayed com.vivicast.tv/.MainActivity for user 0: +1s640ms`.
- Memory baseline from `dumpsys meminfo com.vivicast.tv`:
  - `TOTAL PSS`: 89,112 KB
  - `TOTAL RSS`: 195,896 KB
  - Java Heap PSS/RSS: 12,736 KB / 26,708 KB
  - Native Heap PSS/RSS: 10,996 KB / 11,868 KB
  - Views: 8
  - Activities: 1
- Quick frame sample from `dumpsys gfxinfo com.vivicast.tv`:
  - Total frames rendered: 20
  - Janky frames: 2 (10.00%)
  - 50th percentile: 23 ms
  - 90th percentile: 850 ms
  - Caveat: this is a tiny cold-start/Home sample, not a repeated PRD 13 smoothness benchmark.

## Large Fixture Evidence

### Local Room/Search 10,000-Per-Type Fixture

- Test added: `data/media/src/androidTest/java/com/vivicast/tv/data/media/RoomMediaRepositoryTest.kt`
- Test run: `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomMediaRepositoryTest#largeFixtureSearchAndDatabaseBudgetsStayWithinPrd13Targets" --console=plain`
- Result: passed on `ViviCast_AndroidTV_API36(AVD) - 16`.
- Fixture: synthetic local in-memory Room catalog with 10,000 channels, 10,000 movies, and 10,000 series plus the existing seed catalog; no private provider data.
- Measured logcat line:
  - `largeFixture searchP95=23ms searchP99=25ms channelPageP95=5ms moviePageP95=4ms seriesPageP95=4ms movieDetailP95=1ms seriesDetailP95=1ms`
- PRD 13 comparison:
  - Search p95 budget <= 500 ms: measured 23 ms.
  - Search p99 budget <= 1,000 ms: measured 25 ms.
  - Database first-page p95 budget <= 300 ms: measured 4-5 ms.
  - Database detail p95 budget <= 150 ms: measured 1 ms for movie and series detail lookup.
- Caveat: this closes the first local Room/Search benchmark slice for the measured synthetic fixture. It does not cover full provider import, XMLTV, EPG, restore, or UI smoothness budgets.

### EPG Now/Next and Day View Fixture

- Test added: `data/epg/src/androidTest/java/com/vivicast/tv/data/epg/RoomEpgRepositoryTest.kt`
- Test run: `.\gradlew.bat :data:epg:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.epg.RoomEpgRepositoryTest#largeFixtureEpgNowNextAndDayViewStayWithinPrd13Targets" --console=plain`
- Result: passed on `ViviCast_AndroidTV_API36(AVD) - 16`.
- Fixture: synthetic in-memory Room EPG with 50 channels and 7 days of half-hour programs, 16,800 EPG programs total; no private provider data.
- Measured logcat line:
  - `largeFixture nowNextP95=83ms dayViewP95=3ms`
- PRD 13 comparison:
  - EPG Now/Next for 50 visible channels budget <= 500 ms p95: measured 83 ms.
  - EPG day view for one channel budget <= 500 ms p95: measured 3 ms.
- Caveat: this covers the runtime query path for the measured EPG fixture. XMLTV import and cleanup are measured separately below with the Owner-approved 50,000-program scope for this run.

### M3U Parse and Room Commit 10,000-Channel Fixture

- Test added: `data/media/src/androidTest/java/com/vivicast/tv/data/media/RoomCatalogImportRepositoryTest.kt`
- Test run: `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomCatalogImportRepositoryTest#largeFixtureM3uParseAndCommitStaysWithinPrd13Budget" --console=plain`
- Result: passed on `ViviCast_AndroidTV_API36(AVD) - 16`.
- Fixture: synthetic generated M3U with 10,000 channels and 20 groups; no private provider data.
- Measured logcat line:
  - `largeFixtureM3u parseMs=439 importMs=2115 totalMs=2554 channels=10000 categories=20`
- PRD 13 comparison:
  - M3U 10,000-channel import budget <= 120,000 ms: measured 2,554 ms for generated local string parse plus Room commit.
- Caveat: this covers parser plus repository/Room commit and stream-reference replacement. It does not yet measure a local MockWebServer download path.

### Xtream Metadata Room Commit Large Fixture

- Test added: `data/media/src/androidTest/java/com/vivicast/tv/data/media/RoomCatalogImportRepositoryTest.kt`
- Test run: `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomCatalogImportRepositoryTest#largeFixtureXtreamMetadataCommitStaysWithinPrd13Budget" --console=plain`
- Result: passed on `ViviCast_AndroidTV_API36(AVD) - 16`.
- Fixture: synthetic in-memory Xtream metadata with 10,000 live entries, 50,000 movies, 20,000 series, and 90 categories; no private provider data.
- Measured logcat line:
  - `largeFixtureXtream importMs=15839 live=10000 movies=50000 series=20000 categories=90`
- PRD 13 comparison:
  - Xtream metadata import budget <= 900,000 ms: measured 15,839 ms for repository/Room commit of already parsed metadata.
- Caveat: this covers the largest local commit portion. It does not yet measure local MockWebServer transport, JSON parsing, or per-series detail endpoint collection.

### XMLTV Commit and Cleanup 50,000-Program Fixture

- Test added: `data/epg/src/androidTest/java/com/vivicast/tv/data/epg/RoomEpgRepositoryTest.kt`
- Test run: `.\gradlew.bat :data:epg:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.epg.RoomEpgRepositoryTest#largeFixtureXmltvCommitAndCleanupStaysWithinPrd13Budget" --console=plain`
- Result: passed on `ViviCast_AndroidTV_API36(AVD) - 16`.
- Fixture: synthetic lazy XMLTV document with 50 channels and 50,000 programs; no private provider data.
- Measured logcat line:
  - `largeFixtureXmltv importMs=6787 cleanupMs=115 totalMs=6902 imported=50000 deleted=30700 remaining=19300`
- Scope decision:
  - Owner reduced this check from the original PRD 13 3,000,000-program release target to 50,000 programs with a 10-minute maximum for this ongoing pre-release build phase.
- Comparison for this run:
  - XMLTV 50,000-program import plus cleanup budget <= 600,000 ms: measured 6,902 ms.
- Caveat: this closes the XMLTV check for the Owner-approved 50,000-program scope. The original PRD 13 3,000,000-program budget remains a later release-candidate benchmark if the Owner wants strict PRD-scale evidence before publication.

### Standard Restore Large User-Data Fixture

- Test added: `app/src/androidTest/java/com/vivicast/tv/backup/StandardBackupTest.kt`
- Test run: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest#restorerHandlesLargeUserDataFixture" --console=plain`
- Result: passed on `ViviCast_AndroidTV_API36(AVD) - 16`.
- Fixture: synthetic Standard-Backup restore with 1 provider, 1,000 favorites, 2,000 playback-progress entries, and 2,000 Live-TV history entries; all values are public synthetic references.
- Measured logcat line:
  - `largeFixtureRestore restoreMs=725 providers=1 favorites=1000 progress=2000 history=2000 pendingFavorites=1000 pendingProgress=2000 pendingHistory=2000`
- PRD 13 comparison:
  - Standard-Backup large user-data fixture must validate and attempt internal safety backup before replacing local data: measured test asserts one safety-backup attempt, old provider secret deletion, provider replacement, restored user data counts, pending references, PIN reset, and selected-provider reset.
- Caveat: this is a Standard-Backup restore benchmark using the current restore model. It does not cover encrypted Full-Backup large-fixture decryption time.

### Central Screen Memory and UI Cycle Smoke

- Flow: installed current debug app, launched `com.vivicast.tv/.MainActivity`, reset `gfxinfo`, then drove 10 D-Pad navigation cycles across the main top navigation and back to Home.
- Result: completed on `ViviCast_AndroidTV_API36(AVD) - 16` without crash-buffer entries.
- Memory before:
  - `TOTAL PSS`: 97,404 KB
  - `TOTAL RSS`: 192,360 KB
  - Java Heap PSS/RSS: 12,888 KB / 26,964 KB
  - Native Heap PSS/RSS: 11,176 KB / 12,012 KB
  - Views: 7
  - Activities: 1
- Memory after:
  - `TOTAL PSS`: 101,807 KB
  - `TOTAL RSS`: 208,936 KB
  - Java Heap PSS/RSS: 13,376 KB / 33,992 KB
  - Native Heap PSS/RSS: 13,900 KB / 14,704 KB
  - Views: 7
  - Activities: 1
- Delta:
  - `TOTAL PSS`: +4,403 KB
  - `TOTAL RSS`: +16,576 KB
  - Java Heap PSS: +488 KB
  - Native Heap PSS: +2,724 KB
- Quick `gfxinfo` after the flow:
  - Total frames rendered: 2,215
  - Janky frames: 30 (1.35%) by modern metric
  - 50th percentile: 20 ms
  - 90th percentile: 23 ms
  - 95th percentile: 32 ms
  - 99th percentile: 42 ms
- UI dump: package `com.vivicast.tv` visible with `Home`, `Live-TV`, `Filme`, `Serien`, `Suche`, and `Einstellungen`; Home focused at the end of the run.
- Caveat: this is a debug/emulator smoke measurement on the current empty local data state, not a full large-content scroll benchmark.

## PRD 13 Budget Coverage

| Area | Current evidence | Status |
| --- | --- | --- |
| Search large fixture p95/p99 | 10,000-per-type local Room synthetic fixture measured search p95 23 ms and p99 25 ms. | Covered for local Room/Search fixture |
| Database first page/detail large fixture p95 | 10,000-per-type local Room synthetic fixture measured first-page p95 4-5 ms and detail p95 1 ms for movie/series. | Covered for measured local fixture; channel/episode direct-detail budget still needs explicit coverage if required |
| M3U import 10,000 channels through mockserver | 10,000-channel synthetic M3U parse plus Room commit measured 2,554 ms. MockServer download path is not included yet. | Partial |
| Xtream metadata import large fixture | 10,000 live, 50,000 movies, and 20,000 series synthetic Xtream metadata commit measured 15,839 ms. MockServer transport/JSON path is not included yet. | Partial |
| XMLTV import and cleanup | Owner-approved 50,000-program synthetic XMLTV fixture measured import 6,787 ms, cleanup 115 ms, total 6,902 ms. | Covered for 50k scope; original 3,000,000-program PRD release target deferred |
| EPG now/next and day view | 50-channel / 7-day synthetic Room EPG fixture measured Now/Next p95 83 ms and day-view p95 3 ms. | Covered for measured query fixture |
| Restore large user-data fixture | Standard-Backup restore measured 725 ms for 1,000 favorites, 2,000 playback-progress entries, and 2,000 Live-TV history entries. | Covered for Standard-Backup large user-data fixture |
| Memory central-screen cycles | 10 D-Pad central-screen navigation cycles measured `TOTAL PSS` +4,403 KB and stable Views/Activities. | Covered for current empty-data debug smoke |
| Runtime baseline Home memory/frame snapshot | Captured debug/emulator Home baseline with crash sanity, UI dump, meminfo, and quick gfxinfo sample. | Partial |

## Artifacts

- `codex/evidence/artifacts/2026-06-28-baseline-home/`: first standard package launch attempt; stayed on Android TV launcher because no regular launcher activity resolved.
- `codex/evidence/artifacts/2026-06-28-baseline-home-leanback/`: Leanback package-only launch attempt; resolver found the activity but `am start -p` did not start it on this emulator.
- `codex/evidence/artifacts/2026-06-28-baseline-home-component/`: valid Home baseline through explicit `com.vivicast.tv/.MainActivity` component launch.
- `codex/evidence/artifacts/2026-06-28-room-search-large-fixture/logcat-vivicast-benchmark.txt`: local Room/Search 10,000-per-type benchmark log line.
- `codex/evidence/artifacts/2026-06-28-epg-large-fixture/logcat-vivicast-benchmark.txt`: EPG Now/Next and day-view benchmark log line.
- `codex/evidence/artifacts/2026-06-28-m3u-import-large-fixture/logcat-vivicast-benchmark.txt`: M3U parse plus Room commit benchmark log line.
- `codex/evidence/artifacts/2026-06-28-xtream-metadata-large-fixture/logcat-vivicast-benchmark.txt`: Xtream metadata Room commit benchmark log line.
- `codex/evidence/artifacts/2026-06-28-xmltv-50k-fixture/logcat-vivicast-benchmark.txt`: XMLTV 50,000-program commit plus cleanup benchmark log line.
- `codex/evidence/artifacts/2026-06-28-xmltv-50k-fixture/test-result.xml`: focused Android test result for the XMLTV 50,000-program check.
- `codex/evidence/artifacts/2026-06-28-restore-large-user-fixture/logcat-vivicast-benchmark.txt`: Standard-Backup large user-data restore benchmark log line.
- `codex/evidence/artifacts/2026-06-28-restore-large-user-fixture/test-result.xml`: focused Android test result for the restore benchmark.
- `codex/evidence/artifacts/2026-06-28-memory-ui-cycles/`: 10-cycle central navigation meminfo, gfxinfo, framestats, UI dump, launch output, crash buffer, and logcat tail.

## Known Gaps

- Large synthetic fixtures are not yet present as reusable app-repo benchmark fixtures.
- The Home baseline is useful smoke/performance evidence but cannot close full release-candidate benchmark requirements by itself.
- M3U MockServer download timing remains open even though parser plus Room commit now has first evidence.
- Xtream MockServer transport/JSON timing remains open even though the large metadata commit now has first evidence.
- Full PRD 13 XMLTV 3,000,000-program release-candidate timing remains deferred by Owner decision for this ongoing pre-release build phase.
- Encrypted Full-Backup large-fixture timing remains open if strict Full-Backup performance evidence is required before release.
- Central-screen UI smoothness under populated large provider data remains open; the current evidence is an empty-data smoke run.
