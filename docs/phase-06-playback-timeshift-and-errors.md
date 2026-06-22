# Phase 06 - Playback, Timeshift, and Errors

## Goal

Implement real playback with Media3 and the PRD-defined player behavior for Live-TV, movies, series, catch-up, retries, reconnect, and timeshift.

This phase turns the player overlay from demo behavior into controlled playback behavior.

## Affected Modules

- `:core:player`
- `:feature:player`
- `:feature:live-tv`
- `:feature:movies`
- `:feature:series`
- `:core:database`
- `:core:datastore`
- `:core:network`
- `:data:media`
- `:data:playback`
- `:domain`
- `:app`

## Concrete Tasks

- Implement a Media3-backed `VivicastPlayerController`.
- Generate stream URLs dynamically from secure provider configuration and media metadata; do not persist final stream URLs in Room.
- Support playback entry points:
  - Live-TV channel
  - movie
  - series episode
  - catch-up item when provider and EPG support it
- Enforce single active playback.
- Preserve player UI direction:
  - fullscreen first
  - no permanent UI
  - OK opens overlay
  - Back closes overlay or returns to the originating screen
  - timeline is the central focused control
- Implement timeline behavior:
  - VOD timeline uses full duration
  - Live-TV without timeshift shows current program progress and disables seek actions
  - Live-TV with timeshift shows the configured timeshift window
  - OK on timeline toggles pause/play where technically supported
  - Left/Right seek when seeking is supported
- Implement stream info badges:
  - quality
  - FPS
  - audio
- Implement CH+ and CH- for Live-TV channel zapping.
- Cancel stale start requests during rapid channel zapping and start only the latest channel.
- Implement retry and reconnect behavior:
  - 5 sender-start retries
  - 5 stream-abort reconnect attempts
  - error dialog after exhaustion
- Store playback progress for movies and episodes.
- Support configurable completed thresholds for watched status.
- Add error dialogs with user actions:
  - retry
  - choose another channel
  - close
- Add tests around start cancellation, retry limits, progress persistence, and controller lifecycle.

## Current Progress

Implemented and validated:

- `DefaultVivicastPlayerController` exposes state, playback start, pause/resume, seek, stop, and release operations.
- `Media3PlaybackEngine` wraps ExoPlayer for dynamic runtime media item playback without storing final stream URLs in Room.
- `DefaultPlaybackStreamResolver` resolves Xtream live, movie, and episode playback URLs from secure provider credentials plus local media metadata at runtime.
- M3U playback resolution remains blocked until per-channel stream references can be provided outside Room; M3U final stream URLs are still not persisted.
- `AppContainer` now provides a lazy Media3-backed `VivicastPlayerController`.
- `PlayerRoute` can render controller state, toggle pause/resume, seek through the controller, and stop playback when the player closes while keeping the existing timeline-centered overlay behavior.
- App wiring now passes the shared `VivicastPlayerController` into the fullscreen player route.
- Live-TV channel, movie, and series episode actions now resolve runtime streams and start `VivicastPlayerController` playback requests before opening the fullscreen player.
- Series now exposes a compact season/episode selector for imported episodes so playback starts from an actual episode item, not a series container.
- `RoomPlaybackRepository` now maps the existing Room `playback_progress` and `channel_history` tables to domain models for continue-watching and recent-channel use.
- The Media3 controller now publishes current position and duration, and app wiring persists/restores movie and episode progress with a fixed 90 percent completed threshold.
- Fullscreen Player CH+/CH- now zaps through the current Live-TV channel list with wrap-around and starts only the selected next/previous channel through the shared controller path.
- Media3 playback errors now flow into `DefaultVivicastPlayerController`, which performs up to 5 reconnect attempts for stream aborts before entering `PlaybackStatus.Error`.
- The fullscreen player now shows a focused error dialog with retry, choose another channel, and close actions.
- Eligible past EPG items can now launch Xtream catch-up playback through runtime-generated timeshift URLs; final stream URLs are still not stored in Room.
- Live channel playback now maps stored ADR-006 timeshift settings into `PlaybackRequest`, exposes the configured storage/window in player state, starts at the live edge, tracks a bounded live-edge offset for seek/pause state, resets the window on new channel requests, and offers a fullscreen Live action when playback is behind the live edge.
- Media3 playback now includes HLS support, a 120-minute back buffer for live playback, and an app-cache-backed Media3 `SimpleCache` path for ADR-006 internal-storage timeshift mode.
- AndroidTest APK target SDKs are set to 36 across instrumentation-tested modules to avoid emulator "built for an older Android version" dialogs during local QA.
- Player timeline OK handling now toggles once through the focus-surface click path; Left/Right remain direct seek keys.
- Controller unit coverage verifies stale start cancellation, 5-retry exhaustion, and release lifecycle behavior.
- Controller unit coverage verifies stream-abort reconnect success and reconnect exhaustion.
- Controller unit coverage verifies live timeshift window initialization, seek offset tracking, and returning to the live edge.
- Stream resolver unit coverage verifies Xtream URL generation, inactive provider blocking, missing VOD extension handling, and the M3U no-Room-URL boundary.
- Stream resolver unit coverage verifies Xtream catch-up URL generation from an EPG time window.
- Player instrumentation coverage verifies overlay focus restore, Back behavior, controller pause/resume, controller seek, and stop-on-close.
- Player instrumentation coverage verifies CH+/CH- callback routing.
- Player instrumentation coverage verifies error dialog focus and retry action routing.
- Player instrumentation coverage verifies that a behind-live timeshift state exposes the fullscreen Live action.
- Playback repository instrumentation coverage verifies progress scoping, continue-watching ordering, recent-channel limits, and provider cleanup.

Validated with:

- `.\gradlew.bat :core:player:compileDebugKotlin :core:player:testDebugUnitTest`
- `.\gradlew.bat :data:playback:testDebugUnitTest :data:playback:compileDebugKotlin :app:compileDebugKotlin`
- `.\gradlew.bat :app:compileDebugKotlin`
- `.\gradlew.bat :feature:player:compileDebugKotlin :app:compileDebugKotlin :feature:player:compileDebugAndroidTestKotlin`
- `.\gradlew.bat :core:designsystem:compileDebugKotlin :feature:player:compileDebugAndroidTestKotlin :feature:player:connectedDebugAndroidTest`
- `.\gradlew.bat :data:provider:compileDebugAndroidTestKotlin :data:media:compileDebugAndroidTestKotlin :data:epg:compileDebugAndroidTestKotlin :data:favorites:compileDebugAndroidTestKotlin :feature:live-tv:compileDebugAndroidTestKotlin :feature:search:compileDebugAndroidTestKotlin :iptv:xtream:compileDebugAndroidTestKotlin`
- `.\gradlew.bat :feature:live-tv:compileDebugKotlin :feature:live-tv:compileDebugAndroidTestKotlin :feature:movies:compileDebugKotlin :app:compileDebugKotlin`
- `.\gradlew.bat :feature:series:compileDebugKotlin :app:compileDebugKotlin`
- `.\gradlew.bat :data:playback:testDebugUnitTest :data:playback:compileDebugAndroidTestKotlin :core:player:testDebugUnitTest :app:compileDebugKotlin`
- `.\gradlew.bat :data:playback:connectedDebugAndroidTest`
- `.\gradlew.bat :feature:player:connectedDebugAndroidTest`
- `.\gradlew.bat :feature:live-tv:compileDebugKotlin :feature:player:compileDebugKotlin :feature:player:compileDebugAndroidTestKotlin :app:compileDebugKotlin`
- `.\gradlew.bat :core:player:testDebugUnitTest :feature:player:compileDebugKotlin :feature:player:compileDebugAndroidTestKotlin :app:compileDebugKotlin :feature:player:connectedDebugAndroidTest`
- `.\gradlew.bat :data:playback:testDebugUnitTest :feature:live-tv:compileDebugKotlin :app:compileDebugKotlin`
- `.\gradlew.bat :core:player:testDebugUnitTest :feature:player:compileDebugAndroidTestKotlin :app:compileDebugKotlin`
- `.\gradlew.bat :feature:player:connectedDebugAndroidTest`
- `.\gradlew.bat :core:player:compileDebugKotlin`
- `.\gradlew.bat :core:player:testDebugUnitTest :app:compileDebugKotlin`
- `.\gradlew.bat assembleDebug`

Still open:

- M3U playback stream reference handling without storing final stream URLs in Room.
- Real live-stream timeshift smoke coverage once a safe stream source can be resolved without storing final M3U URLs in Room.
- Configurable completed-threshold settings beyond the current fixed 90 percent threshold.

## Definition of Done

- Media3 playback works for locally imported provider content.
- The player overlay remains TV-first and timeline-centered.
- Timeshift follows ADR-006 and discards the buffer on channel change.
- Rapid channel switching does not leave multiple active playback attempts.
- Playback progress is persisted for movies and episodes.
- Errors are recoverable and do not corrupt local data.
- D-Pad, OK, Back, CH+, and CH- pass emulator smoke testing.
- The app builds with `.\gradlew.bat assembleDebug`.
- No Android TV Watch Next, global search integration, backup/restore, or external metadata provider is implemented in this phase.

## References

- `external-docs/prd/PRD-v1/02-ux-live-tv.md`
- `external-docs/prd/PRD-v1/03-ux-movies-series.md`
- `external-docs/prd/PRD-v1/04-ux-search-settings-player.md`
- `external-docs/prd/PRD-v1/07-background-jobs-performance.md`
- `external-docs/architecture/decisions/ADR-006-timeshift-strategy.md`
- `external-docs/design/interaction/02-player-timeline-controls.md`
- `external-docs/design/mockups/high-fidelity/02-ui-direction-decisions.md`
