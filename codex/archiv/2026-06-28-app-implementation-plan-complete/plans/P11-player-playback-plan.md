# Package 11 - Player, Playback, Catch-Up, Timeshift, and Progress Plan

## Status

- Package: Package 11 - Player, Playback, Catch-Up, Timeshift, and Progress
- State: done
- Last completed step: Player overlay `Audio`, `Untertitel`, and `Bildformat` actions now open session-only option dialogs and call the player controller instead of acting as stubs.
- Last validated state: `:core:player:compileDebugKotlin`, `:feature:player:compileDebugKotlin`, `:feature:player:compileDebugAndroidTestKotlin`, `:core:player:testDebugUnitTest`, focused `:feature:player:connectedDebugAndroidTest`, and targeted `git diff --check` passed after the Player option slice.
- Next concrete step: Start Package 12 by reading backup, restore, PIN, protection, security, and dialog-state sources and creating the Package 12 technical plan.
- Open blockers: None.
- Open Owner questions: None.

## Sources Read

- `AGENTS.md`
- `README.md`
- `../vivicast-docs/codex/README.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/prd/PRD-v1/02-live-tv-requirements.md`
- `../vivicast-docs/prd/PRD-v1/03-movies-series-requirements.md`
- `../vivicast-docs/prd/PRD-v1/04-search-settings-player-requirements.md`
- `../vivicast-docs/prd/PRD-v1/06-data-model.md`
- `../vivicast-docs/prd/PRD-v1/13-test-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-010-stable-identities-and-restore-keys.md`
- `../vivicast-docs/architecture/decisions/ADR-006-timeshift-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-013-player-playback-progress.md`
- `../vivicast-docs/design/screens/03-player.md`
- `../vivicast-docs/design/wireframes/06-player.md`
- `../vivicast-docs/design/components/player.md`
- `../vivicast-docs/architecture/diagrams/05-player-progress-flow.md`
- `../vivicast-docs/design/interaction/02-player-timeline-controls.md`

## Scope

- Re-align current internal player behavior with the final v1 player/progress contract.
- Preserve Live-TV as channel history only and prevent Catch-Up from creating VOD progress.
- Enforce automatic VOD progress creation threshold and save cadence.
- Align retry/reconnect counts and delays.
- Align player overlay/error labels, default hidden overlay behavior, and no-EPG-chip rule.
- Continue with Catch-Up, Timeshift, external-player, Auto-Next, lifecycle stop/save, and global User-Agent stream policy in small slices.

## Non-Scope

- No docs-repo changes.
- No backup/restore or PIN implementation.
- No visual polish outside Player.
- No provider-specific header, cookie, or User-Agent feature.
- No committed public playlist or EPG fixture downloads.

## Affected App Modules and Files

- `app/src/main/java/com/vivicast/tv/MainActivity.kt`
- `core/player/src/main/java/com/vivicast/tv/core/player/VivicastPlayerController.kt`
- `feature/player/src/main/java/com/vivicast/tv/feature/player/PlayerRoute.kt`
- `data/playback/src/main/java/com/vivicast/tv/data/playback/`
- `data/playback/src/test/java/com/vivicast/tv/data/playback/`
- Related app container / stream resolver files as later slices require.

## Technical Approach

- Keep the current player foundation and re-align behavior incrementally.
- Put small pure playback-progress rules in `:data:playback` so they are testable without emulator UI.
- Keep `MainActivity` as the current playback orchestration point until a broader player/use-case extraction is justified.
- Later align retry/reconnect in `DefaultVivicastPlayerController` without changing public API unless needed.
- Later adjust player UI labels and overlay behavior in `:feature:player`.

## Risks and Assumptions

- `PlaybackRequest` now carries stable references and navigation context, but return-target behavior itself still depends on later Player close/navigation wiring.
- The global User-Agent is now applied centrally at runtime; the Settings UI text field remains outside this Player slice unless the Settings package is reopened.
- External-player handoff covers persisted `External` mode and `AskEveryTime` for movies and individual episodes only.
- M3U Catch-Up placeholder names are implemented for common start/end/duration Unix-second forms; add more aliases only when a required fixture or public source needs them.

## Relevant Validation

- `.\gradlew.bat :data:playback:testDebugUnitTest :app:compileDebugKotlin`
- `.\gradlew.bat :core:player:testDebugUnitTest` if player-controller tests are added.
- `.\gradlew.bat :feature:player:compileDebugKotlin :feature:player:compileDebugAndroidTestKotlin`
- `.\gradlew.bat :app:assembleDebug`
- Android TV emulator QA for OK/Back overlay, CH+/CH-, retry/error dialog, lifecycle/background behavior, and Player focus paths once UI behavior changes are implemented.
- Targeted `git diff --check`.

## Completed Work

- Package 11 source refresh completed.
- Current implementation inspected for player controller, player route, playback repository, and app-level progress save wiring.
- Added testable automatic VOD progress rules in `:data:playback`.
- App-level internal VOD progress writes now create records only after 10 seconds position or 1 percent known-duration progress.
- Existing automatic VOD progress now saves on a 10-second cadence during playback and force-saves on pause.
- Automatic completion now uses the central fixed 95 percent threshold constant; no configurable threshold is used.
- Player start now uses a maximum of 5 total attempts including the first attempt.
- Player retry delays now follow the documented sequence: 0.5 s, 1 s, 2 s, 4 s.
- Controller tests now cover the corrected start-attempt budget and default retry delay sequence.
- Player now starts with no visible overlay UI.
- OK opens the Player overlay and focuses the Timeline.
- Back hides the overlay before leaving the Player.
- Overlay auto-hides after 5 seconds of inactivity.
- Timeline handles OK directly as Play/Pause, matching the documented D-Pad contract.
- The old visible hidden-overlay helper panel was removed.
- Error dialog action label now uses `Anderen Sender wählen`.
- Media3 `STATE_ENDED` now emits a playback-scoped media-end event.
- Player controller now publishes `PlaybackStatus.Ended` only for the active playback request and ignores stale media-end events.
- Ended playback state carries a full-duration position when duration is known so VOD completion writes are deterministic.
- Internal movie and episode playback now force-saves progress and sets `isCompleted = true` on actual media end, even below the normal automatic progress creation threshold.
- Live-TV and Catch-Up still do not create `PlaybackProgressEntity` records from media-end handling.
- Player close/dispose paths now call a pre-stop hook with the last controller state so app-level VOD progress can force-save before the controller is stopped.
- App backgrounding now force-saves eligible internal VOD progress and stops internal playback through `MainActivity.onStop`.
- Forced exit/background saves still respect the automatic progress creation minimum; only actual media-end may create a completed VOD progress record below that minimum.
- Player focus tests now assert that the pre-stop hook sees the running playback state before the controller becomes idle.
- Reconnect attempts now set `VivicastPlayerState.isReconnecting = true` while keeping the existing retry/reconnect loop.
- Player UI now shows a non-blocking `Verbindung wird wiederhergestellt...` hint during reconnect and does not show the error dialog unless reconnect is exhausted.
- Core player tests cover reconnect state publication while reconnect is in progress.
- Player focus tests cover the reconnect hint without a blocking error dialog.
- `PlaybackRequest` now includes `providerStableKey`, `mediaStableKey`, `origin`, `returnTarget`, and optional `epgProgramStableKey`.
- Stream resolution now returns `providerStableKey` from the resolved provider so requests are not limited to local Room IDs.
- App playback starts now fill stable media references from `Channel`, `Movie`, `Episode`, and Catch-Up EPG context without persisting stream URLs.
- Live-TV history and VOD progress writes now persist the request stable media key alongside the local entity ID.
- Stream resolver unit tests cover provider stable-key propagation for Xtream and M3U playback starts.
- OkHttp clients now apply the current global User-Agent centrally to parser, refresh, and connection-test network calls.
- Media3 stream starts now create their HTTP data source with the same central global User-Agent provider.
- `AppContainer` keeps a single runtime User-Agent policy synchronized from DataStore and shared by OkHttp and Media3.
- `PlaybackRequest` still has no header, cookie, provider-specific User-Agent, or provider-specific network policy fields.
- Movies and episodes now resolve a full `PlaybackRequest` and launch Android external playback when the persisted playback preference is `External`.
- External playback stops any active internal playback first and shows the documented return notice that progress could not be determined automatically.
- External playback does not call the internal player controller, so automatic progress, completion, Watch Next, and Auto-Next flows are not entered from external returns.
- Live-TV and Catch-Up still use the internal player path.
- Settings now exposes External Player as `Intern`, `Extern`, and `Immer fragen` without adding provider-specific playback controls.
- `AskEveryTime` resolves the same immutable `PlaybackRequest` as normal movie/episode playback, then lets the user choose internal or external playback.
- The settings feature keeps its own small UI enum and maps to the DataStore enum in the app layer so `:feature:settings` does not take a new DataStore dependency.
- The media repository now resolves the next available episode by provider, series, season number, and episode number.
- Internal episode playback now wires Auto-Next only for Vivicast's internal player path; external player handoff still bypasses progress, completion, Watch Next, and Auto-Next.
- Player now shows `Nächste Folge abspielen` with `Zurück` after actual media end when Auto-Next is disabled.
- Player now shows `Nächste Folge in X` with `Zurück` during the configured final countdown window when Auto-Next is enabled.
- Auto-Next starts the next episode once per playback only after actual media end; the 95 percent progress threshold does not trigger Auto-Next.
- Auto-Next Back/`Zurück` stops playback and returns to the Series area.
- Catch-Up app starts now reject current/future programmes, invalid EPG windows, mismatched channel/program context, non-Catch-Up programmes, and windows outside the channel Catch-Up day range.
- Stream resolution now rejects invalid Xtream Catch-Up windows instead of creating a malformed timeshift URL.
- M3U Catch-Up no longer falls back to the Live-TV stream URL; it now requires stored `catchup-source` metadata and resolves `default` or `append` templates just-in-time.
- Playback settings now expose the required v1 Timeshift toggle, maximum duration, and storage rows with defaults `Ein`, `30 Minuten`, and `Automatisch`.
- Playback preferences now persist `timeshiftEnabled` with default `true`; disabling it results in Live-TV starting without a Timeshift config.
- Live-TV Timeshift start failure now falls back to the same internal Live-TV stream without Timeshift or seek, so storage/stream Timeshift problems do not abort Live-TV playback when normal Live playback works.
- Series target navigation now accepts optional target season and episode IDs and consumes the target only after the exact nested context is restored.
- Auto-Next Back now resolves the ended episode and its series category, then returns to the Series detail page with the same series, season, and episode selected.
- The series route instrumentation test now covers exact target restoration across a second season/episode.
- `M3uChannel` now carries `catchupMode` and `catchupSource`; M3U Catch-Up is marked available only when supported mode, days, and template are present.
- `M3uStreamReferenceStore` now stores Stream URL plus optional Catch-Up mode/source outside Room, including the app's secure store implementation.
- M3U `default` Catch-Up resolves the template as the playback URL; M3U `append` appends the rendered template to the live stream URL.
- M3U Catch-Up template rendering supports common `{start}`/`${start}`, `{end}`/`${end}`, `{duration}`/`${duration}`, `{duration_minutes}`/`${duration_minutes}`, `utc`, `lutc`, and `timestamp` aliases from the EPG window.
- `RoomCatalogImportRepositoryTest` stale M3U remote-ID expectations were aligned with the existing stable `channel:tvg-id:*` parser contract.
- Player state now carries session-only audio, subtitle, and aspect-ratio selections with PRD defaults `Systemstandard`, `Aus`, and `Anpassen`.
- Media3 playback now applies selected audio and subtitle language preferences through track selection parameters and applies aspect-ratio mode through video scaling mode.
- The Player overlay `Audio`, `Untertitel`, and `Bildformat` chips now open D-Pad-focusable dialogs with PRD-aligned audio/subtitle labels and session-only selection behavior.
- Player focus tests now cover the audio, subtitle, and aspect-ratio dialogs and assert that the controller receives the selected session options.

## Still Open

- None for Package 11.

## Owner Questions

- None.

## Validation Log

- Passed: `.\gradlew.bat :data:playback:testDebugUnitTest :app:compileDebugKotlin`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only the existing LF/CRLF warning for `MainActivity.kt`.
- Passed: `.\gradlew.bat :core:player:testDebugUnitTest :app:compileDebugKotlin`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings for the touched Player files.
- Passed: `.\gradlew.bat :data:media:compileDebugKotlin :feature:series:compileDebugKotlin :feature:series:compileDebugAndroidTestKotlin :app:compileDebugKotlin`
- Passed after quoting the Gradle runner property correctly: `.\gradlew.bat :feature:series:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.series.SeriesRouteDetailTest#targetSeriesSeasonAndEpisodeOpensExactEpisodeDetail"` on Android TV emulator.
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings for the Auto-Next Series return slice.
- Passed after one placeholder replacement order iteration: `.\gradlew.bat :core:cache:testDebugUnitTest :iptv:m3u:testDebugUnitTest :data:playback:testDebugUnitTest :data:media:compileDebugKotlin :app:compileDebugKotlin`.
- Passed: `.\gradlew.bat :data:media:compileDebugAndroidTestKotlin :app:assembleDebug`.
- Passed after updating stale stable-ID expectations: `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomCatalogImportRepositoryTest"` on Android TV emulator.
- Passed: `.\gradlew.bat :feature:player:compileDebugKotlin :feature:player:compileDebugAndroidTestKotlin :app:compileDebugKotlin`
- Passed after one test/code iteration: `.\gradlew.bat :feature:player:connectedDebugAndroidTest` with 9 tests on the Android TV emulator.
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings for the touched design system and Player files.
- Passed: `.\gradlew.bat :core:player:testDebugUnitTest :data:playback:testDebugUnitTest :feature:player:compileDebugKotlin :app:compileDebugKotlin`
- Passed: `.\gradlew.bat :feature:player:compileDebugAndroidTestKotlin :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings for the touched Player/progress files after the media-end slice.
- Passed: `.\gradlew.bat :core:player:testDebugUnitTest :data:playback:testDebugUnitTest :feature:player:compileDebugKotlin :feature:player:compileDebugAndroidTestKotlin :app:compileDebugKotlin`
- Passed: `.\gradlew.bat :feature:player:connectedDebugAndroidTest` with 9 tests on the Android TV emulator after the exit/background save slice.
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: targeted `git diff --check` with only LF/CRLF warnings for the touched Player/app files after the exit/background save slice.
- Passed: `.\gradlew.bat :core:player:testDebugUnitTest :feature:player:compileDebugKotlin :feature:player:compileDebugAndroidTestKotlin :app:compileDebugKotlin`
- Passed: `.\gradlew.bat :feature:player:connectedDebugAndroidTest :app:assembleDebug` with 10 Player tests on the Android TV emulator after the reconnect hint slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings for the touched Player files after the reconnect hint slice.
- Passed: `.\gradlew.bat :data:playback:testDebugUnitTest :core:player:testDebugUnitTest :app:compileDebugKotlin` after the PlaybackRequest stable-reference slice.
- Passed: `.\gradlew.bat :feature:player:compileDebugAndroidTestKotlin :app:assembleDebug` after the PlaybackRequest stable-reference slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings for touched PlaybackRequest and stream resolver files after the PlaybackRequest stable-reference slice.
- Passed: `.\gradlew.bat :core:network:compileDebugKotlin :core:player:testDebugUnitTest :app:compileDebugKotlin` after the global User-Agent stream-policy slice.
- Passed: `.\gradlew.bat :app:assembleDebug` after the global User-Agent stream-policy slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings for touched network/player/app-container files after the global User-Agent stream-policy slice.
- Passed: `.\gradlew.bat :app:compileDebugKotlin` after the external-player runtime slice.
- Passed: `.\gradlew.bat :app:assembleDebug` after the external-player runtime slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings for `MainActivity.kt` and plan files after the external-player runtime slice.
- Passed after one module-boundary iteration: `.\gradlew.bat :feature:settings:compileDebugKotlin :app:compileDebugKotlin` after the external-player settings/prompt slice.
- Passed: `.\gradlew.bat :feature:settings:compileDebugAndroidTestKotlin :app:assembleDebug` after the external-player settings/prompt slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings for `MainActivity.kt`, `SettingsRoute.kt`, and plan files after the external-player settings/prompt slice.
- Passed: `.\gradlew.bat :core:database:compileDebugKotlin :data:media:compileDebugKotlin :feature:player:compileDebugKotlin :feature:player:compileDebugAndroidTestKotlin :data:media:compileDebugAndroidTestKotlin :app:compileDebugKotlin` after the Auto-Next slice.
- Passed: `.\gradlew.bat :feature:player:connectedDebugAndroidTest` with 12 tests on the Android TV emulator after the Auto-Next slice.
- Passed: `.\gradlew.bat --% :data:media:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomMediaRepositoryTest` with 6 tests on the Android TV emulator after adding next-episode repository coverage.
- Passed: `.\gradlew.bat :app:assembleDebug` after the Auto-Next slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings for the Auto-Next touched files.
- Note: full `.\gradlew.bat :data:media:connectedDebugAndroidTest` currently has two failures in `RoomCatalogImportRepositoryTest`; the isolated `RoomMediaRepositoryTest` for this slice passes, and the full-run report was overwritten by the isolated successful run.
- Passed: `.\gradlew.bat :data:playback:testDebugUnitTest :app:compileDebugKotlin` after the Catch-Up guardrail slice.
- Passed: `.\gradlew.bat :core:datastore:compileDebugKotlin :feature:settings:compileDebugKotlin :feature:settings:compileDebugAndroidTestKotlin :data:playback:testDebugUnitTest :app:compileDebugKotlin` after the Timeshift settings slice.
- Passed after one offscreen assertion update: `.\gradlew.bat :feature:settings:connectedDebugAndroidTest :app:assembleDebug` with 4 Settings tests on the Android TV emulator.
- Passed: `.\gradlew.bat :core:player:testDebugUnitTest :app:compileDebugKotlin` after the Timeshift fallback slice.
- Passed: `.\gradlew.bat :app:assembleDebug` after the Timeshift fallback slice.
- Passed: `.\gradlew.bat :core:player:compileDebugKotlin :feature:player:compileDebugKotlin :feature:player:compileDebugAndroidTestKotlin` after the Player option slice.
- Passed: `.\gradlew.bat :core:player:testDebugUnitTest :feature:player:testDebugUnitTest` after the Player option slice.
- Passed: `.\gradlew.bat :feature:player:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.player.PlayerRouteFocusTest"` with 13 tests on the Android TV emulator after the Player option slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings for the touched Player files after the Player option slice.
