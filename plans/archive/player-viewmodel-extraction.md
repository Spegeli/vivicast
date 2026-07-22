# Plan — Player orchestration → activity-scoped PlayerViewModel (Option B)

> Status: **✅ DONE (2026-07-22)** — all 7 steps executed; gates (detekt/assembleDebug/test) green; 8 VM unit tests;
> physical-TV verified on SHIELD `.12` (Live-TV commit→fullscreen→BACK, Home→channel→BACK, VOD, zap, auto-next).
> Ran BEFORE C2 (per user decision 2026-07-22: "erst PlayerViewModel (B), dann C2"). Not yet committed at close of
> the session that wrote this. No behaviour regressions found.
> Skills: android-architecture, android-state, compose-state-holder-ui-split, compose-side-effects.

## Goal

Move the **player orchestration** (open/zap/auto-next/commit-preview/progress-save-trigger/derived state) out of
`MainActivity.VivicastApp` (the blast-radius file) into a testable, activity-scoped `PlayerViewModel` in `:app`.

**Non-goal:** changing the single ExoPlayer connection's scope. The connection stays a singleton in
`AppContainer.playerController`. This preserves the ADR-013 single-active-playback invariant and the seamless
preview↔fullscreen handoff exactly as verified on the physical TV.

## Why (honest framing)

- The current App-hoisted player state **already survives navigation** — `VivicastApp` is the root composable above
  the `NavHost`, so the `remember`-ed vars span every destination. So the VM is **not** needed for lifetime/handoff.
- The real wins: (1) MainActivity shrinks, (2) player logic becomes unit-testable, (3) idiomatic home (matches
  StreamVault `PlayerViewModel` / OwnTV VM-ownership).
- It does **not** fix the C2 two-pointer bug — but it puts `committedLiveChannel` into the VM, which is where C2's
  "one identity" fix then lands naturally (see §C2 fold-in).

## Amends a documented rule (must be acknowledged)

CLAUDE.md currently states: *"App-hoisted stays App-hoisted: … playerController.play, player state loop / WatchNext /
throttle map …"*. Option B **narrows** that rule to:

> The player **connection** (`AppContainer.playerController`) and all **Context/Activity/View-bound** player effects
> (ActivityResult external player, PIN gate, embedded SurfaceView + attach, WatchNext, diagnostics logging,
> Locale/recreate, navigation, the `onStop` save) stay App-hoisted. The player **orchestration state + pure decision
> logic** move to an activity-scoped `PlayerViewModel`.

Doc-update step (§6) rewrites this rule in CLAUDE.md + notes it in `docs/ARCHITECTURE-SETTINGS-HOISTING.md` scope and
the nav-migration plan.

## Move / Stay boundary (the crux)

### MOVES into `PlayerViewModel` (pure — no Context/Activity/View/nav)
| Piece | Current site |
|---|---|
| `committedLiveChannel` (preview identity — also the C2 pointer) | MainActivity:265 |
| `livePlaybackChannels` (zap list) | :262 |
| `nextAutoNextEpisode` + its derivation effect | :398, :985 |
| `automaticProgressSaveTimes` (internal) + progress-save `collectLatest` loop | :396, :976 |
| `startInternalPlayback`, `playNextAutoNextEpisode` | :578, :585 |
| `openChannel`, `zapChannel`, `commitLivePreview`, `stopLivePreview`, `openLivePlayer` | :597–650 |
| `openCatchUp` (no PIN gate today) | :741 |
| **internal-player branch** of movie/episode playback | :668, :729 |
| Derived UiState: `committedChannel`, `zap list`, `nextEpisodeTitle`, `keepPlayingOnClose = request.mediaType==Channel` | scattered |

VM deps (all non-Context, via factory): `playerController`, the `:data:playback` open/factory use-cases currently
fronted by `AppContainer.open*Playback`, `mediaRepository` (nextEpisode/getChannel), `savePlaybackProgress` recorder,
`userPreferencesStore`/`preferences` reads (autoNext, externalPlayer). VM lives in `:app` (app-level orchestration,
not a single feature) — `app/.../player/PlayerViewModel.kt` + `PlayerUiState` + factory.

### STAYS App-side (Context / Activity / ActivityResult / View / nav / lifecycle)
- **Navigation** — `navigateToPlayer` + all `navController` (hard arch rule: no nav in VM). VM signals "start" via a
  one-shot effect the App collects → App runs `navController.navigate(Player(...))`.
- **PIN gate** — `requestProtectionUnlock` wrapping movie/episode/series/deep-link opens (Keystore/PIN App-hoisted).
- **External player** — `launchExternalPlayback` (ActivityResult) + `pendingExternalPlaybackRequest` dialog.
- **Embedded preview** — `livePreviewSurface` (SurfaceView+Context), the attach `DisposableEffect` (:1005), AndroidView slot.
- **WatchNext** `syncWatchNext`; **diagnostics** player-state logging (:358–391); **deep-links**; **`onStop`** save+stop (:230).

### Split entry points (thin App wrappers → VM)
`openMovie` / `openEpisode` / `openCatchUp` keep their **App-side shell** (PIN gate + external-player-preference
branch). The **Internal** branch delegates to the VM; External/AskEveryTime stays App-side (ActivityResult). Nav still
fires App-side off the VM's "started" signal.

## Audit-confirmed boundary (4-agent sweep, 2026-07-22)

**Funnel confirmed:** playback core = `PlaybackOrchestration.kt` extensions, called from **exactly one place**
(MainActivity); `playerController.play()` exists only there. Only Home + MovieDetail + SeriesDetail + Live-TV trigger
playback; Movies/Series **lists** + **all Search** are navigation-only (no direct play).

**VM constructor (final):**
```
PlayerViewModel(
  playerController: VivicastPlayerController,       // built App-side (Media3 needs Context) — injected as-is
  playbackRequestFactory: PlaybackRequestFactory,
  playbackProgressRecorder: PlaybackProgressRecorder,
  playbackRepository: PlaybackRepository,           // clearHistory / recent channels
)
```
Move the `PlaybackOrchestration.kt` bodies verbatim, swap receiver `AppContainer.` → injected fields. `:app` already
depends on `:core:player` + `:data:playback` — no new module edges. VM state: `committedLiveChannel`,
`livePlaybackChannels`, `nextAutoNextEpisode`, `automaticProgressSaveTimes`. VM emits a one-shot **navigate-to-player**
effect; App runs the actual `navController.navigate`. `syncWatchNext` is NOT called by the VM (auto-fires via the
`SystemIntegrationPlaybackRepository` wrapper on every progress write).

**DO-NOT-FORGET (audit-flagged; live in MainActivity, silently break if dropped in the move):**
1. **Two singleton-push effects** — `updateGlobalUserAgent` (:941) + `updatePlaybackTuning` (:947). Feed **all**
   class-a settings (buffer, decoders, passthrough, preferred audio/subtitle language, global UA) into the controller
   singleton. **KEEP App-side, must stay hosted** — if the gutted composition drops them, the singleton reverts to
   default UA + default `PlaybackTuning()` and every one of those settings silently stops applying.
2. **AFR** — `afrEnabled` arg (:1699) → `PlayerRoute.setFrameRate` (:288). Re-thread the flag (stays settings-sourced).
3. **Auto-next** — flags (:1697-98) stay settings-sourced; the resolver effect (:985) + `playNextAutoNextEpisode`
   (:585) MOVE to the VM.
4. **externalPlayer decision** — branch at openMovie (:659) / openEpisode (:720); the choice dialog + ActivityResult
   launch STAY App-side. **Channels (:597) + catch-up (:741) ignore this pref — always internal. Preserve the
   asymmetry.**
5. **resumeLastChannelOnStart** — startup effect (:820) → `resolveResumableLastChannel`. The resolve MOVES; the
   `topNavigationFocusRequester.requestFocus()` (:828) stays App-side.

**STAYS App-side (confirmed by audit):** external-player ActivityResult (:320, :535) + choice dialog (:1842); `onStop`
save+stop (:230); ProcessLifecycle `release()` (VivicastApplication:51 — already outside MainActivity); PIN gate +
`protection*` extensions; SurfaceView + attach (:309, :1005); nav / onClose / BackHandler / `committedLiveChannel`-focus;
WatchNext (auto via repo wrapper; reads the progress DB, not the live player loop); the two push effects (#1);
diagnostics logging (:358-393 — observational; keep App-side reading the singleton state, moving it buys little).

**Test impact:** only **ProtectionGateTest** hard-couples to moving code — it asserts
`PinSecurityState.protectionAreaForRoute/Movie/Series/Episode` at MainActivity:1967-1994, **package `com.vivicast.tv`**.
Keep those extensions in that package (they're PIN → stay App-side anyway). `PlayerRouteFocusTest` stays green iff the
`PlayerRoute` signature is unchanged (it is — same args, new sources). `M3uPlaybackSmokeTest` uses
`AppContainer.playerController` directly — unaffected. Data/core player tests are independent.

**Stale docs to fix (step 6):** CLAUDE.md mentions `timeshiftConfig()` — **no such symbol exists** (timeshift is
controller-auto-detected). `SettingsPreferenceMappers.kt:206` comment claims audio/subtitle seeding is "Phase 2" — it
is live; ignore the comment.

## Invariants to preserve (regression guards)
1. **One connection** — VM calls `playerController.play/stop`; never a 2nd instance. Connection stays in AppContainer.
2. **Seamless handoff** — `openLivePlayer` "already-committed ⇒ just navigate (no re-play)" logic moves verbatim.
3. **Surface attach stays App-side** — the DisposableEffect reads VM `committedChannel` + nav state; unchanged mechanics.
4. **`onStop` save** — still App-side (Activity override); reads `playerController.state` directly (already does).
5. **Auto-next / progress cadence** — the collectLatest loop + nextEpisode derivation move as-is, keyed identically.

## Steps
1. **Scaffold** `PlayerUiState` (immutable) + `PlayerViewModel` (one `StateFlow<PlayerUiState>` per android-state) +
   factory in `:app`. No behavior yet; build green.
2. **Move state + loops** — committedLiveChannel, livePlaybackChannels, nextAutoNextEpisode, progress-save loop,
   nextEpisode derivation. MainActivity reads them via `collectAsStateWithLifecycle`.
3. **Move orchestration fns** — channel/zap/commit/stop/openLivePlayer/catchup/auto-next + internal movie/episode.
   App keeps thin PIN/external/nav wrappers; VM exposes a `navigateToPlayer` one-shot effect the App collects.
4. **Rewire** MainActivity call sites (the ~15 `appContainer.playerController.state.value.request?.let { navigateToPlayer }`
   collapse into the VM signal). Delete the now-dead `remember` vars.
5. **Tests** — unit-test the VM (zap wrap-around, keepPlayingOnClose, auto-next pick, commit/stop preview, progress
   trigger) with fakes. This is the payoff of the extraction.
6. **Docs** — rewrite the App-hoisted rule in CLAUDE.md; note in ARCHITECTURE-SETTINGS-HOISTING.md + nav plan.
7. **Gates + physical-TV verify** — detekt/assembleDebug/test green; then live TV: Live-TV commit→fullscreen→BACK,
   Home→channel→BACK, VOD play/resume, zap, auto-next, external player, onStop-save. adb logcat throughout.

## C2 fold-in — what actually happened (this section was a PREDICTION; corrected 2026-07-22)
The prediction below was **wrong** about the mechanism. C2 did NOT do "plain select-by-id + delete `liveTvSearchTarget`".

**Actual C2 (done, TV-verified):** `liveTvSearchTarget` was **KEPT** and extended with an **`activate` flag**
(Search/deep-link = true → EPG focus; player-close return = false → list-row focus). The wrong-channel-on-return bug was
NOT a one-pointer issue — it was an **async-load race** in `LiveTvViewModel`: `ensureCategorySelected` + the channel
auto-select in `rebuild()` set "first entry" while categories/channels were still empty-loading, clobbering the onTarget
target (only looked right when the target happened to be the first channel). Fixed by guarding both on the list being
actually loaded (`categories.isNotEmpty()` / `channels.isNotEmpty()`) + onTarget pre-expands the target provider. Plus a
cascade of nav/focus fixes (navigateTab without save/restore; `pendingReturnToLiveTvFocus` to suppress transient
route-switches; `focusRoute` skip-if-already-current; top-nav `focusProperties{enter}`+`focusGroup` so UP out of a
category lands on the active tab). Full detail in `nav-migration-jetpack-compose.md` (C2) + the memory
`nav-migration-jetpack-compose`.

*Original (incorrect) prediction, kept for the record:* "pass the committed channel id down to LiveTvRoute; give the VM a
plain select-by-id (un-overloaded onTarget); delete `liveTvSearchTarget`."

## Cost / risk
- **Big diff in the blast-radius file** (again). Highest-risk piece: the surface-attach DisposableEffect + the
  handoff — must stay behaviorally identical (physical-TV re-verify is mandatory, not optional).
- **Does not advance the visible focus rebuild** on its own; it's a structural prerequisite the user chose to front-load.
- Reversible: if it regresses the handoff, revert to the App-hoisted vars (they're a pure lift).
