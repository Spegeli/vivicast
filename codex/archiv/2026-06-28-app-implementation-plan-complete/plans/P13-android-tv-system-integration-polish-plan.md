# Package 13 - Android TV System Integration and Polish Plan

## Status

- Active package: Package 13 - Android TV System Integration and Polish
- State: done
- Last completed step: Package 13 Android TV system integration and polish validation completed.
- Last validated state: CH+/CH-/Back/focus tests, missing system-target QA, Android TV Search provider smoke check, and 720p/1080p/4K/large-font display smoke checks passed on the Android TV emulator.
- Next concrete step: Start Package 14 - Test Hardening, Performance, and Release Readiness.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `../vivicast-docs/codex/README.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/prd/PRD-v1/08-android-tv-security.md`
- `../vivicast-docs/architecture/decisions/ADR-008-android-tv-integration.md`
- `../vivicast-docs/prd/PRD-v1/13-test-strategy.md`
- `../vivicast-docs/design/interaction/README.md`
- `../vivicast-docs/design/interaction/nav.md`
- `../vivicast-docs/design/interaction/focus.md`
- `../vivicast-docs/design/interaction/01-live-tv-adaptive-columns.md`
- `../vivicast-docs/design/interaction/02-player-timeline-controls.md`

## Affected Masterplan Package

- Package 13 - Android TV System Integration and Polish

## Concrete Implementation Scope

- Apply stored regular app start destination only for normal launcher starts without explicit system target.
- Keep `Home` as default and keep explicit deep links, Android TV Search, and Watch Next targets ahead of the stored start area.
- Complete stable-key deep-link handling for `channel`, `movie`, `series`, and `episode`.
- Avoid silent fallback to Home when a system target is unavailable.
- Harden Android TV Search suggestion wiring against v1 constraints already present in the app.
- Add Watch Next only if the current app foundation can do it without violating protection, provider status, or stable-key rules.
- Polish CH+/CH-, focus, Back, empty/error states, large font, overscan, long text, and target resolutions in focused slices.
- Document manual Android TV QA where system UI or focus behavior is not stable enough for automation.

## Non-Scope

- No docs-repo edits.
- No product-label, navigation, PIN, backup/restore, playback, or data-model rule changes beyond Package 13 alignment.
- No broad visual redesign in this package; visual work is limited to Android TV polish and documented target checks.
- No publication of protected content, EPG, Catch-Up, Live-TV progress, external-player progress, or pending restore references into Watch Next.
- No provider-specific headers, cookies, or User-Agent settings.

## Affected App Modules and Files

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/vivicast/tv/MainActivity.kt`
- `app/src/main/java/com/vivicast/tv/search/AndroidTvSearchSuggestionProvider.kt`
- `data/media/src/main/java/com/vivicast/tv/data/media/MediaRepository.kt`
- `data/media/src/main/java/com/vivicast/tv/data/media/RoomMediaRepository.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/dao/CatalogDao.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/dao/AndroidTvSearchDao.kt`
- `feature/live-tv/src/main/java/com/vivicast/tv/feature/livetv/LiveTvRoute.kt`
- `feature/player/src/main/java/com/vivicast/tv/feature/player/PlayerRoute.kt`
- Related focused unit or instrumented tests in `app/src/androidTest`, `data/media/src/androidTest`, `feature/live-tv/src/androidTest`, and `feature/player/src/androidTest`.

## Initial App Inspection

- Existing Android TV launcher entry is present.
- Existing Android TV Search suggestion provider is present and backed by a derived Room index for channels, movies, and series.
- Existing search index deep links use stable keys and exclude EPG results.
- Protection-aware Android TV Search rebuild exists for movies, series, and adult content.
- `episode` deep-link manifest and handling were missing before the first Package 13 slice.
- Normal app start initialized the selected route as `Home` and did not apply the stored `startDestination` before the first Package 13 slice.
- CH+/CH- is already handled in the Live-TV browser channel list and Player zapping path.
- Watch Next publication and cleanup/invalidation are now implemented through `WatchNextSynchronizer`, `AndroidTvWatchNextPublisher`, and repository wrappers for provider/playback changes.
- Settings still shows boot/autostart as prepared but not active.

## Technical Approach

- Keep changes incremental and source-backed.
- First slice:
  - Add `episode` as accepted `vivicast://` host.
  - Add repository lookup by `providerStableKey + episodeStableKey`.
  - Route episode deep links to the series area with season/episode context.
  - Apply stored `startDestination` once after preferences load and only if no explicit deep-link target is present.
  - Keep unavailable system targets visible through a controlled message/state, not silent Home fallback.
- Later Package 13 slices:
  - Evaluate Watch Next publication against current playback progress and protection state.
  - Harden Android TV Search provider result caps and cleanup paths if gaps remain.
  - Run targeted CH+/CH-, Back, focus, large-font, overscan, and resolution QA.
  - Add focused automated tests where app code exposes stable test hooks.

## Risks and Assumptions

- Android TV system surfaces such as global search and Watch Next may need manual emulator QA because platform UI is not always stable through instrumentation.
- Watch Next must not be added as a shortcut if the app cannot also clean stale/protected/provider-deleted entries correctly.
- Deep-link unavailable handling must not expose protected titles or secret/provider data.
- Existing large dirty worktree is expected from earlier packages; Package 13 edits must stay scoped.

## Relevant Tests and QA

- Compile checks:
  - `.\gradlew.bat :app:compileDebugKotlin`
  - `.\gradlew.bat :data:media:compileDebugKotlin :data:media:compileDebugAndroidTestKotlin`
- Focused instrumentation:
  - `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomMediaRepositoryTest"`
  - app-level deep-link/start-destination tests if feasible with existing harnesses.
- Manual Android TV QA:
  - launcher regular start destination.
  - deep links for channel, movie, series, episode.
  - unavailable/missing system target.
  - CH+/CH- in Live-TV browser and Player.
  - Back/focus paths, large font, overscan, 720p, 1080p, 4K checks for touched surfaces.

## Completed Work

- Read Package 13 final docs and interaction specs.
- Inspected manifest, MainActivity, Android TV Search provider, media repository, search DAO, Live-TV CH-key handling, Player CH-key handling, and Settings autostart placeholder.
- Created Package 13 technical plan and set the main app implementation plan to Package 13 in progress.
- Added `episode` as a supported `vivicast://` deep-link host.
- Added stable episode lookup by `providerStableKey + episodeStableKey`.
- Reworked regular app start handling so the stored start destination is applied once after preferences load and only when no explicit system target is present.
- Routed episode deep links into the existing series detail context with target season/episode.
- Changed stable movie/series lookups so adult/protected content can still be resolved and gated by the current local PIN/protection state instead of being treated as missing.
- Added a controlled unavailable dialog for missing/deactivated/credential-missing system targets instead of only falling through silently.
- Extended focused repository and protection tests for episode stable links and adult series protection.
- Inspected Watch Next feasibility against the current repository and platform foundation. The safe next implementation must create a central publisher/invalidation path first, not a one-off publisher.
- Added Settings `Startbereich` row under `Einstellungen > Allgemein`, wired it to the existing DataStore `startDestination`, and kept changes non-immediate for the current session.
- Validated that `Startbereich` cycles through allowed v1 values and that regular app restart applies the stored value while explicit system targets keep priority.
- Added a central Watch Next synchronizer and Android TV publisher for movies and series episodes.
- Added app-layer repository wrappers that sync Watch Next after movie/episode progress writes, progress deletion, provider playback cleanup, movie/series progress cleanup, provider status/active/enabled changes, provider save/update/create, and provider deletion.
- Wired Watch Next cleanup after restore completion and after PIN/protection rule changes.
- Kept Watch Next candidates limited to productive movie/episode progress; pending progress, Live-TV, series container progress, completed movies, protected content, disabled/unavailable providers, and missing provider/media targets are not published.
- Implemented completed-episode handling so Watch Next can publish the next available episode at position 0 and removes the series when there is no next episode.
- Added focused Android tests for Watch Next candidate creation, protection filtering, next-episode behavior, wrapper sync triggers, publisher empty-sync runtime behavior, and playback repository Watch Next source filtering.
- Added an explicit Live-TV browser CH+/CH- instrumentation test that verifies CH-Down/CH-Up move focus within the visible channel list without starting playback.
- Reused the existing Player focus suite to validate CH+/CH- direct zapping callbacks and overlay/Back behavior.
- Manually validated missing Android TV system targets for `channel`, `movie`, `series`, and `episode` deep links on the emulator; each showed a controlled unavailable state instead of silently falling back to Home.
- Smoke-tested the Android TV Search suggestion provider through `content query` on the installed app; the empty emulator database returned no results without crashing.
- Ran 720p, 1080p, 4K, and large-font 1080p smoke checks for normal Home launch and missing-target unavailable dialog; each retained visible Home/unavailable UI and a focused action node.
- Restored emulator display settings after resolution/font QA.

## Validation Log

- Passed: `.\gradlew.bat :app:compileDebugKotlin :data:media:compileDebugKotlin :data:media:compileDebugAndroidTestKotlin --console=plain`.
- Passed: `.\gradlew.bat :app:compileDebugAndroidTestKotlin :data:media:compileDebugAndroidTestKotlin --console=plain`.
- Passed: `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomMediaRepositoryTest" --console=plain` with 7 tests on Android TV emulator.
- Passed: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.ProtectionGateTest" --console=plain` with 2 tests on Android TV emulator.
- Passed: targeted `git diff --check` for Package 13 touched files with only LF/CRLF warnings.
- Passed: `.\gradlew.bat :feature:settings:compileDebugKotlin :app:compileDebugKotlin --console=plain`.
- Passed: `.\gradlew.bat :feature:settings:compileDebugAndroidTestKotlin :app:compileDebugKotlin --console=plain`.
- Passed: `.\gradlew.bat :app:installDebug --console=plain`.
- Passed: manual Android TV emulator QA for missing episode deep link: `vivicast://episode/missing-provider/missing-episode` showed `Inhalt nicht verfügbar` with focused `Schließen` and no crash.
- Passed: manual Android TV emulator QA for Settings `Startbereich`: row visible in `Einstellungen > Allgemein`, D-Pad changed value to `Live-TV`, process restart opened Live-TV, then local emulator setting was reset to `Home` and `Hintergrundaktualisierung` was confirmed as `Ein`.

- Passed: `.\gradlew.bat :data:playback:compileDebugKotlin :app:compileDebugKotlin :feature:movies:compileDebugAndroidTestKotlin :feature:series:compileDebugAndroidTestKotlin --console=plain`.
- Passed: `.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain`.
- Passed: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.system.WatchNextIntegrationTest" --console=plain` with 5 tests on the Android TV emulator.
- Passed: `.\gradlew.bat :data:playback:compileDebugAndroidTestKotlin :data:playback:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.playback.RoomPlaybackRepositoryTest" --console=plain` with 8 tests on the Android TV emulator.
- Failed then fixed: first `.\gradlew.bat :feature:live-tv:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.livetv.LiveTvRouteFocusTest" --console=plain` run exposed that the new CH+/CH- test used a one-channel category and did not explicitly focus the row before key input; fixed the test fixture and focus setup.
- Passed: `.\gradlew.bat :feature:live-tv:compileDebugAndroidTestKotlin --console=plain`.
- Passed: `.\gradlew.bat :feature:live-tv:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.livetv.LiveTvRouteFocusTest" --console=plain` with 8 tests on the Android TV emulator.
- Passed: `.\gradlew.bat :feature:player:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.player.PlayerRouteFocusTest" --console=plain` with 13 tests on the Android TV emulator.
- Passed: `.\gradlew.bat :app:installDebug --console=plain` before manual Android TV system-target QA.
- Passed: manual Android TV emulator QA for missing `vivicast://channel/...`, `vivicast://movie/...`, `vivicast://series/...`, and `vivicast://episode/...` targets; UI dumps confirmed unavailable state text and a focused, clickable dialog action.
- Passed: `adb shell content query --uri "content://com.vivicast.tv.search/search_suggest_query/dune?limit=5"` returned `No result found.` without crash on the empty emulator database.
- Passed: `adb logcat -b crash -d -t 100` returned no crash entries after system-target and search-provider QA.
- Passed: manual Android TV emulator display smoke checks at 720p, 1080p, 4K, and large-font 1080p; UI dumps confirmed Home visibility, unavailable dialog visibility, and focused node presence.
- Passed: emulator display reset verification: `adb shell wm size` returned `Physical size: 1920x1080`, `adb shell settings get system font_scale` returned `1.0`.
- Passed: `adb logcat -b crash -d -t 100` returned no crash entries after display smoke checks.

## Still Open

- None for Package 13.

## Open Owner Questions

- None.
