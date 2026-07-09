# Plan: Auto-resume last watched channel on startup + player engine start-contract fix

Status: **completed** · Created 2026-07-09 · Completed 2026-07-09

Gates green: `test` (all modules), `detekt`, `assembleDebug`. New unit tests:
`PlaybackStartGateTest` (ready/error/timeout), `StreamReachabilityProbeTest`
(200/302/404/500/connect-failure). Emulator smoke (`M3uPlaybackSmokeTest`) +
manual dead-URL resume still pending a running emulator.

Two coupled changes agreed with the user:

1. **Feature** — optional "resume last watched channel on startup".
2. **Bug fix** — the reconnect loop on HTTP 404/500 (player never settles into the error
   dialog, oscillates on "Reconnecting…").

## 1. Auto-resume last watched channel

### Behaviour
- New toggle under **Einstellungen → Allgemein**: „Zuletzt gesehenen Sender beim Start fortsetzen"
  (default **off**).
- On cold start, if the toggle is on **and** history is enabled **and** there is a last-watched
  channel that still resolves and is reachable → auto-open it. Otherwise land on Home (default).
- Refresh (playlist/EPG) stays **parallel/non-blocking** — resume does NOT wait for it. The stream
  URL resolves straight from Room, so playback needs no fresh refresh.
- **Dead-channel guard (preflight liveness check):** before opening the player we probe the resolved
  stream URL with a short-timeout GET. Reachable → play. Dead/timeout/unresolvable → silently land on
  Home (no raw 400/500). Chosen over "open then fall back" so there's no error flash and we sidestep
  the (now-fixed) reconnect behaviour on the resume path.

### Touch points
- `core/datastore` `GeneralPreferences.resumeLastChannelOnStart` (+ DataStore key read/write).
- `feature/settings` `GeneralSettingsState.resumeLastChannelOnStart`, `SettingsViewModel`
  map + `onResumeLastChannelChanged` (pure persistence, no app side effect),
  `GeneralSettingsPanel` toggle row, `SettingsRoute` wiring.
- Strings `settings_resume_last_channel` (+ help) in `core/designsystem` (de + en).
- `data/playback` `StreamReachabilityProbe` — pure, injectable `httpStatus` lambda; reachable =
  status in 200..399. Unit-tested; no okhttp dep in the module.
- `app` `AppContainer.streamReachabilityProbe` wires the lambda to the shared OkHttpClient
  (short callTimeout knob).
- `app` `PlaybackOrchestration.resolveResumableLastChannel()` — app-hoisted orchestration:
  last history row → `mediaRepository.getChannel` → `channelRequest` (timeshift null) → probe →
  Channel or null.
- `app` `MainActivity` regular-start effect: Home first, then (if enabled) resume or focus nav.

## 2. Engine start-contract fix (reconnect loop)

### Root cause
`PlaybackEngine.start()` is supposed to mean "start and confirm playback began, or throw." The fakes
honour that. `Media3PlaybackEngine.start()` violates it: `player.prepare()` is async, so for HTTP
404/500 `start()` returns *success* and the real error arrives later via `onPlayerError`. The
controller's throw-based retry accounting therefore never trips, and every incoming error spawns a
fresh reconnect (attempt reset to 1) → infinite Reconnecting↔Playing oscillation; the error dialog
only ever appears for synchronous start failures.

### Fix (smallest blast radius)
Fix the liar, not the (correct, already-tested) controller. Make `Media3PlaybackEngine.start()`
suspend until ExoPlayer reports `STATE_READY` (success) or throw on `onPlayerError` / timeout.
- `PlaybackStartGate` + `CompletableDeferred.awaitStartedOrThrow(timeoutMillis)` (pure, unit-tested).
- Listener: `STATE_READY → gate.onReady()`, `onPlayerError → gate.onError(e)` (still also emits to
  `playbackErrorEvents` for mid-watch death → reconnect).
- `START_READY_TIMEOUT_MILLIS` ≈ 10 s — only bites on a connected-but-silent endpoint; a real
  404/500/dead-host errors out in <1 s via `onPlayerError`, so the common case stays fast.
- Interface, controller, and all existing controller tests are **unchanged** (fakes already honour
  the contract).

### Why this also fixes siblings
- Timeshift start-fallback (`startWithRetries` `timeshiftFallbackRequest`) currently also never
  triggers for HTTP failures (same cause) — fixed for free.
- Re-entrancy stops: during a reconnect, status stays `Starting`, so the error-flow guard swallows
  duplicate events and the single reconnect loop counts to `maxReconnectAttempts` and terminates.

### Visible behaviour change (approved)
- No more instant fake "Playing" on a black screen — status stays Starting/buffering until real
  `STATE_READY`.
- Dead stream → error dialog after the retry budget instead of an endless reconnect.

## Validation
- `.\gradlew.bat test` (new probe test + gate/await test + existing controller/settings tests green)
- `.\gradlew.bat detekt`, `.\gradlew.bat assembleDebug`
- Emulator smoke where available: `M3uPlaybackSmokeTest`; manual dead-URL resume → lands on Home.
