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
- Controller unit coverage verifies stale start cancellation, 5-retry exhaustion, and release lifecycle behavior.
- Stream resolver unit coverage verifies Xtream URL generation, inactive provider blocking, missing VOD extension handling, and the M3U no-Room-URL boundary.

Validated with:

- `.\gradlew.bat :core:player:compileDebugKotlin :core:player:testDebugUnitTest`
- `.\gradlew.bat :data:playback:testDebugUnitTest :data:playback:compileDebugKotlin :app:compileDebugKotlin`
- `.\gradlew.bat :app:compileDebugKotlin`

Still open:

- Player UI/controller state integration using resolved runtime streams.
- M3U playback stream reference handling without storing final stream URLs in Room.
- Playback progress persistence for movies and episodes.
- Channel zapping, reconnect, timeshift, and error dialogs.

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
