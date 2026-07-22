# Navigation Rebuild → Jetpack Navigation Compose (type-safe), state-of-the-art / greenfield

> Status: **GO'd + IN PROGRESS** on branch `nav-rebuild-jetpack`. Whole-app navigation rebuild off the
> custom `selectedRoute` solution onto **Jetpack Navigation Compose, type-safe `@Serializable` routes** (NOT
> Nav3, NOT custom), plus a clean-rebuilt, **separate** TV D-pad focus layer.
>
> **Phase status (updated 2026-07-22; details + commit refs in §5):**
> - **A1 — nav spine** ✅ DONE (`4fc5246`) — NavHost + ShellGraph + tabs + tab-root BACK shadow, verified.
> - **A2 — top-nav focus rebuild** ⏸ mostly folded into A1 — BUT the 2026-07-22 C2 work added the missing
>   top-nav **enter-focus**: UP out of the Live-TV category column now lands on the **active** tab (not Home,
>   leftmost) via `focusProperties { enter = selectedFocusRequester }` + `.focusGroup()` on `VivicastTopNavigation`.
> - **B(detail) — Movies + Series detail as destinations** ✅ DONE (`a13600f` movies, `6934286` series).
> - **B(player) — Player ORCHESTRATION → activity-scoped `PlayerViewModel`** ✅ DONE + committed `9660191` —
>   inserted this session per user decision ("erst PlayerViewModel, dann C2"); NOT in the original A–E phasing.
>   `plans/player-viewmodel-extraction.md`. Orchestration (build+play, auto-save + auto-next loops, zap,
>   committed-preview identity) moved out of the ~2000-line MainActivity into `app/.../player/`; the ExoPlayer
>   **connection stays the AppContainer singleton**; 8 VM unit tests; CLAUDE.md App-hoisted rule amended.
> - **C1 — Player destination + one-connection handoff** ✅ DONE + committed `9660191`, TV-verified on SHIELD
>   `.12`. **TWO deviations from the plan:** (1) §3.4 planned "hoist both SurfaceViews to App level" — we did
>   **NOT** (an always-composed App-level SurfaceView broke video on the physical TV); the **overlay render is KEPT**,
>   zero-flash comes from the preview↔fullscreen overlap. (2) §3.5's tab pattern `saveState`+`restoreState` was
>   **REMOVED** — the top-level Player entangled with it (bounce back to fullscreen; registry inconsistency → later
>   tab switches no-op'd → "can't leave Live-TV"). `navigateTab` is now plain `popUpTo(shell-start)+launchSingleTop`;
>   cost = a tab's sub-stack (Movies→Detail) isn't preserved across tab switches (accepted).
> - **C2 — Live-TV return-flow + nav/focus + full nav audit** ✅ **DONE**, TV-verified on SHIELD `.12`. Core
>   (committed `9660191`): bounce loop, wrong-channel-on-return (an **async-load race** in
>   `ensureCategorySelected`/`rebuild` auto-select), return→Home / can't-leave-Live-TV, a Compose self-nav crash.
>   Approach **deviated** from §4: `liveTvSearchTarget` **KEPT** + `activate` flag;
>   `focusChannelOnReturnSignal`/`pendingLiveTvChannelFocus` **deleted** (+ `pendingReturnToLiveTvFocus` added).
>   Then a **full instrumented Live-TV nav audit** (33 screenshots + `[ltv]` `vcLog` traces) found + FIXED
>   (uncommitted, TV-verified): the **RIGHT-from-low-category 2-press bug** (direction-aware content-Row `enter`
>   + deterministic `right =` to the selected channel) + **LEFT-lands-on-provider**; **Sender-Modus restored on
>   return** (BACK re-fullscreens the still-playing channel, targets the committed one); **cold-load "Wird geladen…"
>   loading state** (no more "Keine Sender"/"Keine Programminformationen" flash, one-way latch); **Favoriten
>   empty-state text**; **preview panel black bg** (`setZOrderOnTop`); **Home DOWN → first row element**. The
>   `[ltv]`/`[player-nav]`/`[topnav]` `vcLog` instrumentation is **kept in** (debug-gated) for future debugging.
>   Full write-up: `plans/live-tv-states.md` "Nav-Audit 2026-07-22"; Home focus in `plans/home-screen-states.md` V4.
> - **D — Settings inner NavHost** ✅ DONE (`adeb84c`/`8686931`/`7c054fc` + focus rework — see §5).
>   **Scope deviation:** only **Playlists** sub-views were promoted to inner-nav destinations; **EPG/About kept
>   their local overlays** (deliberate; "optional later promotion" still open). Went **beyond** the plan: rail
>   RIGHT scroll-to-first-row via `SettingsDetailList`, off-screen/sub-view focus-recovery, and a debug-logging
>   module `:core:logging`/`vcLog`.
>   **Deep-link polish (`927cbeb`):** Home empty-state buttons deep-link into Settings reliably — a generic
>   **`SettingsEntryAction`** (section + optional sub-view, cross-panel by design) + a **fresh per-entry inner
>   NavController** (non-saveable) so a deep-link always starts on its target section. Fixes: the add-editor's
>   Cancel/Save landing on General instead of the Playlists overview, and a stale section rendering under the
>   deep-linked title for a frame ("General flashes before Playlists"). Return focus preserved (Cancel→Add row,
>   Save→new provider card). Retired the old route-bounce/remount approach →
>   `plans/archive/settings-navigation-deeplinks.md`. Residual open: add-editor **open latency** (overview
>   visible while the heavy editor composes) → `plans/settings-add-editor-open-latency.md`.
> - **E — cleanup** (typed deep-link finalization, episode resolver bridge, dead-code sweep, ktlint unused-imports,
>   detekt-baseline regen, doc-sync + close-out) ⬜ **OPEN** — the last remaining nav-migration phase.
>
> **A–D + C1 + C2 are all DONE + TV-verified; Live-TV is functionally complete (nav audit fixes shipped).**
> **The ONLY remaining nav-migration work is Phase E (cleanup/close-out)** + optional EPG/About inner-nav
> promotion. Commit state: B(player)+C1+C2-core committed as `9660191`; the nav-audit fixes (RIGHT/LEFT focus,
> Sender-Modus-on-return, cold-load loading, Favoriten text, preview bg, Home focus — 9 files) are **staged/
> uncommitted, awaiting the commit**. Two Live-TV micro-polish items were **discarded** by the user (provider-
> expand ~100ms flash; provider-focus-with-favorites). Physical-TV device note: `.12` (SHIELD) is the sanctioned
> autonomous test device; `.40` is the user's.
>
> **Governing principle (user, 2026-07-20): build it the way a greenfield app modeled on AOSP JetStream +
> current android.com best practice would be built. Parity-first is DROPPED; rework/risk explicitly
> accepted.** The app's **features/behavior stay as they are** — this is a nav + focus **architecture**
> rebuild, not a feature rework.
>
> **VALIDATED 2026-07-20** by a 5-agent adversarial review (API-correctness, codebase blast-radius, phasing,
> cross-cutting flows, player-handoff feasibility). All findings folded in below; §12 maps each. Highlights
> the review caught: a tab-root BACK regression, build/compile-breaking version+API errors, deep-link vs.
> PIN/recreate mechanics, the player-surface hoisting, and that Movies/Series detail is VM state (not a
> composable-local). Memory: `nav-migration-jetpack-compose.md`. Load skills `android-navigation` +
> `android-navigation-type-safe` (+ `compose-focus-navigation`) before implementation.

---

## 0. Scope + boundaries

- **Scope of THIS task = the navigation + focus ARCHITECTURE only.** Features/behavior stay as now. The open
  Baustellen in Live-TV / Movies / Series (feature completion) are a **SEPARATE, LATER** effort on top of this
  new foundation (nav/focus first, so feature work isn't built on the old nav and thrown away). The Live-TV
  focus rebuild here touches focus **structure** (and designs out the RIGHT-bug), NOT the deferred feature
  polish in `plans/live-tv-states.md`.
- **Functional anchor = "the app as it is now."** Current visible behavior is preserved; where the review
  found a nav pattern would silently change behavior (tab-root BACK, adult-gate timing, preview zero-flash),
  the default is **preserve current behavior**, called out inline.
- **`vivicast-docs` = strong reference, NOT a 100% hard gate** (user, 2026-07-20 — relaxes CLAUDE.md's
  "changing docs → STOP"). Followed by default; greenfield judgment may deviate from a doc detail where it
  serves the nav/focus goal. Do NOT rewrite `vivicast-docs`; record deviations in §10; no hard-STOP.
- **NO DI migration** (manual `AppContainer`; VMs scoped to nav entries via manual factories — §3.3). **NO new
  Gradle module** without asking. Immutable UiState + `StateFlow`; `collectAsStateWithLifecycle`; no Repo
  Flows/CRUD in Composables; **navigation outside ViewModels**; App-hoisted stays App-hoisted; strings only in
  `:core:designsystem` (de+en); `detekt` green. No commit/push without approval.
- **Renames allowed (app is pre-live / in development, user 2026-07-20).** Where a symbol's name no longer
  fits its function after the rebuild (focus signals, route helpers, `*SearchTarget`, `keepPlayingOnClose`,
  etc.), rename it for correctness — but **grep every usage first (cross-module) and update all references** so
  the rename fully lands. No backward-compat burden (no live users).

---

## 1. Sources (trust: android.com / AOSP JetStream > local IPTV apps > blogs)

Nav API (type-safety / nested-graphs / multi-back-stack / deep-link guides; releases — **2.8.0** introduced
type-safe routes, **2.9.8** latest stable, 2.10.0 is alpha and needs AGP 9.2). TV focus / TV Material3
(change-focus-behavior guide, tv/playback/compose). **Focus APIs `Modifier.focusRestorer()` + the
`focusProperties { onEnter/onExit }` receiver form stabilized in Compose-UI 1.8.0** (NOT 1.7.0 — review
correction); our BOM `2026.05.00` pins **Compose 1.11.0** (material3 1.4.0), well past that. **JetStream**
(AOSP tv-samples) = TV UX/focus/player-as-destination precedent (lags: string routes + pre-1.8 focus form →
take UX, take the API from android.com). Local refs (hints + smells §9): StreamVault, AerioTV.

---

## 2. Complete Route Inventory (from code — the whole app; validated accurate except where noted)

### 2.1 Top-level (today `selectedRoute: String` + `destinations`)
`home` · `live-tv` · `movies` · `series` · `search` · `settings`, rendered under `VivicastTopNavigation`. No
back stack; App `BackHandler` (`MainActivity:1431`) = focus-nav / double-back-exit.

### 2.2 Player (today overlay `if (playerVisible) PlayerRoute(...)`, drawn above the Column)
App-hoisted single `VivicastPlayerController`; own 4-level `BackHandler` (optionPanel→autoNext→overlay→close);
5 `FocusRequester`s; **surface API is `attachVideoSurface(SurfaceView)`/`detachVideoSurface()`** (ExoPlayer's
`setVideoSurfaceView`/`clearVideoSurface` are engine-internal); `keepPlayingOnClose` = Live-TV
preview↔fullscreen **one-connection surface handoff**; no VM. **Three player exits** (review): `navigateUp`/
close (origin), Auto-Next `Zurück`→series, and **`onChooseAnotherChannel`→live-tv** (`MainActivity:1500`).

### 2.3 Feature-internal sub-views (today local state — TO BE PROMOTED to destinations)
- **Home** — none.
- **Movies** — grid ⇄ **movie detail**. **Detail open/close + target are VM state** (`MoviesViewModel`
  `detailMovieIdFlow`, `onOpenDetail`/`onCloseDetail`, `onTarget`, `consumedTargetMovieId`); `selectedMovieId`
  is only the grid hero highlight (review corrected the plan's earlier "composable-local detail"). 1
  `BackHandler` gated on `uiState.detailMovieId`.
- **Series** — grid ⇄ **series detail** (season/episode **in-page**, no episode screen). **Entirely VM-driven**
  incl. the **staged target latch** `trySeriesTargetStage→trySeasonTargetStage→tryEpisodeTargetStage→
  consumeTarget` (`SeriesViewModel`). 1 flat `BackHandler` gated on `detailSeriesId`.
- **Live-TV** — adaptive columns f(`focusedArea`): `[K|S|P]`/`[S|E|P]`/`[E|P]`; K provider/category, S channel,
  E EPG(+catch-up row), P preview (never focus target). 1 `BackHandler`. **Cross-module focus signal
  `focusChannelOnReturnSignal`/`focusLiveChannelSignal`** (App↔`LiveTvRoute`) focuses the committed channel on
  fullscreen-return (review named this — the plan must give it a home). Targets provider/category/channel/
  epgProgram/epgStartTime.
- **Search** — field + history + 4 result groups; **produces** the live-tv/movies/series targets.
- **Settings** — section rail (9) + detail pane; sub-view selection = local `show*`; nested `BackHandler`s
  (`SettingsRoute:295` + 6 in Provider/Epg panels + 2 About + 2 Diagnostics); `collapseSubViewSignal`;
  focus-park (`onParkFocusBeforeEditor`) + **`pendingOverviewFocus`** which restores focus to the **specific
  edited item** (new card after save / neighbour after delete) — a generic `focusRestorer` will NOT reproduce
  this (review).

### 2.4 App-hoisted dialogs (stay App-hoisted)
ProtectionUnlock · **SystemTargetUnavailable** · FilePicker · Backup Export/Import passphrase · FileSaved ·
ExternalPlayerChoice · StandardRestoreConfirm · ParentalReactivationHint.

### 2.5 Cross-cutting flows (validated — see §3.6–§3.8)
Player↔Live-TV preview handoff (one connection) · PIN gate (route gate + **play-time per-item adult gate**) ·
deep links `vivicast://…` (+ WatchNext + system search; stable keys already) · language `recreate()` re-entry ·
top-nav focus + double-back-exit · resume-last-channel · **deep-link-suppresses-regular-start**
(`explicitSystemTargetSeen`) · cross-route targets (today **Room ids**).

### 2.6 Behaviors the rebuild preserves (from "keep the app as it is" + `vivicast-docs` as reference)
Most hold because the app already works this way; docs back them (reference, not gate — §0).
1. **Top nav visible on every main area AND detail; ONLY the Player is full-screen without it** → detail
   destinations live **inside** the shell, not full-screen.
2. **Movie/series detail = distinct destinations** (OK opens, BACK→grid).
3. **Series detail = ONE destination** (season/episode in-page; no episode screen; restore season/episode
   context on return).
4. **Settings = rail (9 final groups) + detail pane; sub-editors = nested sub-pages with Back-to-parent**
   (Playlist-Aktionen→Editor; Gruppen inlay; EPG editor/Unter-Panel; About Unterseiten). BACK closes
   dialog/detail before leaving Settings to the top nav (**not** to Home — see 1a).
5. **Player = full-screen destination** carrying origin+return (ADR-013 `PlaybackRequest`, single-playback
   invariant). CH-zap/overlay/auto-next player-internal; Auto-Next `Zurück`→series detail with context.
6. **Live-TV focus envelope**: mode-driven columns, initial focus = first category of first provider,
   focus-on-category updates list, first OK→Sender-Modus+EPG+preview, stepwise back
   EPG→Senderliste→Provider→Top-Nav, non-blocking focus, lazy EPG for selected channel.
7. **Deep-links/routes use STABLE keys, never Room ids, no silent Home fallback** (ADR-008/010). Episode →
   series detail with context.
8. **Main-area switch does NOT restore the left area's internal focus** → tab re-entry = area **initial**
   focus (PRD).
9. No dedicated nav/back-stack ADR → nav **library** choice is free.

---

## 3. Target architecture (greenfield, doc-conform, validated)

### 3.1 Dependencies / Gradle (review-corrected)
- Add to the catalog (none exist today): `androidx.navigation:navigation-compose` **2.9.8**; the
  `org.jetbrains.kotlin.plugin.serialization` plugin **= Kotlin 2.2.21**; the `kotlinx-serialization-json`
  **runtime on its own 1.x line (~1.9.x)** — **NOT** `:2.2.21` (that doesn't exist; plugin version ≠ runtime
  version). Nav 2.9.8 keeps **AGP 8.13.2** valid; lifecycle 2.10.0 + BOM 1.11.0 resolve forward cleanly.
- **Route ownership + per-module wiring (decide up front):** declare the route classes + host the
  `composable<T>{}` builders in the **`app` module** (only `app` gets the serialization plugin + navigation +
  json runtime; feature modules keep exposing plain callbacks and Composables — they need neither navigation
  nor serialization). This keeps the blast radius in `app` and avoids touching every `feature/*` Gradle file.
  (Revisit only if a feature must own a typed route.)

### 3.2 Graph shape — outer NavHost + persistent chrome; nested typed graphs; Settings inner NavHost
- **Chrome vs content.** Top nav bar = persistent chrome drawn **around** the content NavHost (`Scaffold`-style
  wrapper); suppressed only on the Player destination. Chrome test uses
  `navController.currentBackStackEntryAsState()` + `entry?.destination?.hasRoute<Player>()` (NOT `dest is
  Player` — a destination isn't the route object; review).
- **Detail destinations live inside the shell** (nav bar persists, §2.6-1). Route args = **stable keys**.
- **Settings** = one shell destination rendering the **persistent rail** + an **inner `NavHost`** for the
  detail pane (section panels + sub-editors as destinations, real Back-to-parent, rail stays visible).

```kotlin
@Serializable object ShellGraph
@Serializable object Home
@Serializable object LiveTv                 // deep-target args via §3.6 bridge (stable keys)
@Serializable object MoviesGraph;  @Serializable object MoviesList
@Serializable data class MovieDetail(val providerStableKey: String, val movieStableKey: String)
@Serializable object SeriesGraph;  @Serializable object SeriesList
@Serializable data class SeriesDetail(val providerStableKey: String, val seriesStableKey: String,
                                      val seasonKey: String? = null, val episodeKey: String? = null)
@Serializable object Search
@Serializable object Settings               // rail + INNER NavHost
// Player = DECOMPOSED primitive args (no custom NavType): mediaType, providerStableKey, mediaStableKey,
// origin, returnTarget, startPositionMs, epgProgramId? … — all primitives/String? so type-safe routes work
@Serializable data class Player(val mediaType: String, val providerStableKey: String, val mediaStableKey: String,
                                val origin: String, val returnTarget: String, val startPositionMs: Long = 0L,
                                val epgProgramId: String? = null)
```
- **Non-primitive route args need a custom `NavType`** (review). Avoid it: **decompose the `PlaybackRequest`
  into primitive/`String?` args** (above). The App rebuilds the `PlaybackRequest` from the args on Player
  entry (also fixes process-death, §3.4).
- **Nullable `String?` args**: known Nav quirk where absent → literal `"null"` (issuetracker 348936238) —
  treat `null` / missing / `"null"` identically in the destination VM, and add a round-trip test.
- **Sub-views become destinations** (movie/series detail; Settings panels/editors/About sub-pages in the inner
  host). Series season/episode stay **in-page** (args, not sub-destinations). Promoting Movies/Series detail =
  **VM surgery** (remove `detail*Flow`, `onOpenDetail`/`onCloseDetail`, the staged-target latch), not deleting
  a local var (review Gap A) — one VM-refactor task per feature.

### 3.3 Manual-DI VM scoping (no Hilt — the linchpin, validated correct)
- **Per-screen VM**: `viewModel(factory = FooFactory(appContainer…))` in `composable<Foo>{}` (entry-scoped;
  cleared on pop).
- **Shared VM across a nested graph** (list+detail share the VM; direct/deep-link entry still resolves): `val
  parent = remember(entry){ navController.getBackStackEntry(MoviesGraph) }; viewModel(parent, factory = …)`.
  **Guardrail:** `getBackStackEntry(Graph)` **throws `IllegalArgumentException`** if the graph isn't on the
  back stack — only call it from a destination nested **under** that graph (safe here). Introduce
  `MoviesGraph`/`SeriesGraph` (List-only child) **already in Phase A1** and scope the VM to the graph from the
  start, so Phase B only *adds* the Detail destination (no re-scoping; review 2c).

### 3.4 Player destination + one-connection preview handoff (validated FEASIBLE, with must-resolves)
Feasible with **no reconnect / no 2nd connection** — `attachVideoSurface` re-targets ExoPlayer output without
re-prepare/stop. But the destination model reintroduces surface-teardown hazards the overlay engineered away;
resolve these **before Phase C1** (review):
1. **Hoist BOTH SurfaceViews above the content NavHost** (App level), never inside a `composable<T>{}` (a
   `remember { SurfaceView() }` in a destination is destroyed+recreated per visit). The preview surface is
   already App-level; the fullscreen surface currently lives **inside** `PlayerRoute` → move it up.
2. **Zero-flash requires keeping both surfaces alive + toggling visibility** across preview↔fullscreen
   (today's behavior). Default = **match today's zero-flash** (keep-as-is); the alternative (accept a
   `surfaceCreated` recreate gap = black flash on navigateUp) is a visible regression → not chosen.
3. **Keep the `setZOrderMediaOverlay(true)` + visibility rule** — do NOT just delete it. §4.1's
   "delete `playerVisible`/`keepPlayingOnClose`" is only half true: the bool becomes **nav-state-derived**,
   but the attach+**visibility** effect must be rehomed (else the "frozen corner" returns). Two distinct
   SurfaceViews (preview on-top; fullscreen behind-window).
4. **Stop-on-leave must be back-stack-aware**: stop only when Player is NOT on the back stack AND Live-TV is
   not current — else navigating LiveTv→Player stops its own stream (today's `if (selectedRoute!="live-tv")
   stopLivePreview()` fires on the exact handoff transition).
5. **Player-from-committed-preview ADOPTS the running stream, never re-`play()`** (a `LaunchedEffect(Unit){
   play() }` on entry = reconnect + 2nd connection = ADR-013 violation). The Player destination distinguishes
   "adopt" (channel already committed) from "start fresh" via its nav arg / an App-hoisted owner read.
6. **Relocate the keep-playing/stop decision to App-hoisted nav state**, read reliably at Player dispose (not
   from mid-transition NavController state).
7. **Player-pop focus carve-out**: navigateUp must restore the committed-channel focus (today
   `focusLiveChannelSignal`) — this **contradicts** the "re-entry = area initial focus, no cross-tab
   restorer" rule (§3.5/§4), so the Player-pop case is an explicit exception. Give
   `focusChannelOnReturnSignal` a home (review Gap G).
8. **Verify on a physical TV** (emulator can't decode the streams; §7). `adb logcat` + temp `Log.d` at
   attach/detach + surfaceCreated/Destroyed.

### 3.5 Tab pattern + back-stack + tab-root BACK + re-entry focus
- Three-flag `navigate` per tab: `launchSingleTop=true; restoreState=true;
  popUpTo(graph.findStartDestination().id){saveState=true}`. `saveState/restoreState` ON → tab **VM/data
  lifetime** survives (matches today's Activity-scoped survival — verify by init-count log).
- **Tab-root BACK — regression to prevent (review 1a).** The three-flag pattern makes the stack `[Home,
  <tab>]`, so NavHost's back callback would pop `<tab>→Home`. Today root BACK **focuses the top nav then
  double-back-exits** (no tab-to-tab). **Keep today's behavior**: a higher-priority `BackHandler` (gated on
  "current destination is a top-level route with an empty tab-internal back stack") runs the focus-nav /
  double-back-exit policy and **consumes** the event so NavHost never pops to Home. This is foundational —
  wire it in **A1**; Phase D's inner Settings NavHost depends on the same shadowing (BACK from a Settings
  section root → top-nav gear, not Home).
- **Re-entry focus = area INITIAL focus, not restored** (§2.6-8). `focusRestorer` is for **within-screen** row
  return + the **nav bar** (`onEnter=active`), NOT cross-tab content. saveState restores data; the destination
  re-applies its documented initial focus on entry (verify saveState doesn't pin stale focus). **Exception:**
  the Player-pop→Live-TV case restores the committed channel (§3.4-7). **Settings editor-return needs
  specific-item focus** (`pendingOverviewFocus`: new card after save / neighbour after delete), which a
  generic restorer won't do — keep a per-item focus target, not a blanket `focusRestorer` (review Risk C).

### 3.6 Deep links (system / WatchNext / search) — typed where safe, manual bridge where required
Confirmed already-correct: resolve uses **stable keys** (`get*ByStableKeys`), no silent Home fallback, and the
`vivicast://{host}/{provider}/{media}` URI shape is **identical** across manifest, WatchNext, and system
search. But two review findings force a hybrid:
- **Protected + PIN-timing (Defect B):** `navDeepLink<T>` auto-navigates **synchronously at NavHost init**,
  bypassing `navigateProtected` and the async `pinSecurityLoaded` gate → a protected target renders fail-open
  for ≥1 frame. So **do NOT attach `navDeepLink` to protected destinations.** Keep the current **manual bridge**:
  `intent.data` → (await `pinSecurityLoaded`) → resolve stable keys → `navigateProtected(typedRoute, area)`.
  Reconciles §3.6/§3.7 (they were mutually exclusive as first written). Typed `navDeepLink<T>` may still be
  used for **unprotected** targets (channel) if desired, but the uniform manual bridge is simplest and keeps
  one code path.
- **Episode → SeriesDetail (Defect A, GAP):** `vivicast://episode/{provider}/{episodeKey}` carries only an
  episode key — no series/season. A single typed `navDeepLink<SeriesDetail>` **cannot** do the DB round-trip
  (episode→seriesId→series). Keep the current App-side resolve (`getEpisodeByStableKeys`→series) and then
  `navigate(SeriesDetail(...))` with the resolved keys. (Confirms the manual bridge above; the episode host is
  the reason typed deep links can't be universal.)
- **`onNewIntent`** must forward the new intent into the flow (today `deepLinkData = intent.data`); ensure a
  second launch re-runs the bridge (no `launchMode` is set — the single-Activity + one NavController must
  handle re-delivery, not spawn a stacked Activity).
- **`explicitSystemTargetSeen` ordering (review Risk D):** a deep-link launch must **suppress** the
  regular-start (Home / resume-last-channel) effect. Preserve this guard in the new start flow — a deep link
  must not race an unwanted Home/resume navigate.
- **`SystemTargetUnavailableDialog` (review Risk E):** stays App-hoisted; on an unresolved id the resolver
  (App-side bridge, or a destination VM via a **VM-state → App-observes** wire) sets the App dialog state — a
  VM cannot show a dialog itself (no Compose/Context). Wire it explicitly.

### 3.7 PIN gate — route gate + play-time adult gate
- One **`navigateProtected(route, area)`** funnel for route-level protection (`protectionAreaForRoute`:
  Settings/Movies/Series). `unlockedProtectionAreas` stays App session state; deep links pass the funnel
  (§3.6).
- **Play-time per-item adult gate (review Flow 2):** `protectionAreaForMovie/Episode/Series` fold in the
  per-item **adult** flag and today gate at **play time** (you can view an adult item's detail without a PIN).
  In the rebuild play = `navigate(Player(...))`, so the adult gate fires on the **Player navigation** (via
  `navigateProtected` carrying `protectionAreaForMovie/Episode`), **preserving today's timing** (prompt at
  play, not at detail-open). Dedupe against `unlockedProtectionAreas` so detail→player doesn't double-prompt.
  (`ProtectionGateTest` asserts the string-keyed `protectionAreaForRoute` API + `canBeProtected`/
  `protectionTitle` — those helpers move with the funnel; the test updates.)

### 3.8 Language recreate / backup-restore / double-back-exit / resume-on-start (validated — critical fixes)
- **`recreate()` PRESERVES the NavController back stack (review Flow 3, critical).** `rememberNavController`
  registers with `SavedStateRegistry`, so `recreate()`'s `onSaveInstanceState` saves+restores the stack —
  unlike today's plain-`remember` `selectedRoute` that resets to Home (which is *why* the
  `EXTRA_REOPEN_LANGUAGE_SETTINGS` dance exists). So the re-entry machinery must be **reconciled with
  restoration, not kept verbatim**: on the language path, branch on the `EXTRA_REOPEN_LANGUAGE_SETTINGS`
  marker to **intentionally reset/`popUpTo` to Settings** (or accept the restored Settings stack and drop the
  redundant re-navigate); on every other launch let restoration proceed. `recreate()` and process-death share
  the **same** `savedInstanceState` mechanism — the marker is what distinguishes "reset to Settings" from
  "restore".
- **Backup-restore recreate (review Flow 4):** restore's language-change `recreate()` sets **no** marker →
  the restored stack (Settings›Backup) collides with the `regularStart` effect re-firing `Home`/resume. Fix:
  set the same marker (or a restore marker) before `recreate()`, and suppress `regularStart` when restoring.
- **Process-death Player (review Flow 6):** the App-hoisted controller dies; a restored `Player` destination
  would be blank. Because Player args are **decomposed primitives** (§3.2), the Player entry **re-resolves +
  re-plays from its args** (or `popUpTo`s itself off a restored stack if re-play isn't wanted). Content
  destinations are **improved** vs today (stable-key routes survive process death; today's Room-id targets in
  non-saveable `remember` reset to Home).
- **Double-back-exit:** the tab-root `BackHandler` (§3.5) implements it; NavHost owns BACK within a tab.
- **Resume-last-channel / regular start:** via the navigate funnel, gated by the `explicitSystemTargetSeen`
  suppression (§3.6) and the language/restore markers above.
- **Refresh loop + `onStop` save must stay App-scoped (review Flow 5):** keep them off any
  `composable<T>{}` — inside a destination `LocalLifecycleOwner` resolves to the `NavBackStackEntry`, which
  goes STOPPED on navigation-away (not app-background), silently corrupting the foreground/background refresh
  split. "App-hoisted stays App-hoisted" (§0) — flag explicitly for the loop + `onStop`.

---

## 4. Nav layer vs focus layer — separate; focus rebuilt state-of-the-art (review-corrected)

- **Nav layer:** NavController + typed routes/graphs + tab pattern + tab-root back handler + deep-link bridge
  + `navigateProtected`.
- **Focus layer (clean rebuild):**
  - **Top nav bar:** `focusRestorer()` on the nav `Row` + `focusProperties { onEnter = { activeTabRequester
    .requestFocus() } }` — **receiver form (1.8+): call `requestFocus()` inside, do NOT return a requester**
    (`onEnter = { requester }` is a compile error; review). Per-route `FocusRequester` map. Selection-follows-
    focus stays (TV convention); stray focus can't switch tabs → Home-bounce designed out. **Deletes**
    `focusTopNavPending` + `focusRoute` bounce scaffolding.
  - **Per destination:** area **initial** focus (§2.6-8) via `FocusRequester` in a `LaunchedEffect` wrapped in
    `runCatching` (standardized `requestFocusSafely`). `focusRestorer()` for within-screen rails/rows only.
  - **Live-TV focus rebuilt** within the §2.6-6 envelope with deterministic directional targets → replaces the
    signal-counter + spatial-traversal escalation and **designs out the RIGHT-from-low-category bug** (Phase
    C2). Give the **preview-return focus** (`focusChannelOnReturnSignal`) an explicit home (§3.4-7).
  - **Settings focus rebuilt:** real inner-NavHost back-stack → `onParkFocusBeforeEditor` /
    `collapseSubViewSignal` / `detailFocused` deleted; BUT **`pendingOverviewFocus`'s specific-item return**
    (new card after save / neighbour after delete) must be preserved as an explicit per-item focus target — a
    generic `focusRestorer` won't reproduce it (review Risk C).
  - **tv-material3** for TV surfaces; never mix mobile `material3` focus.

### 4.1 What gets DELETED (net win — validated, with corrections)
`selectedRoute` + `destinations` + `selectRoute`/`focusRoute` + `focusTopNavPending`; the shared-state targets
(`*SearchTarget`) + `onTargetConsumed`; Settings `show*` back-stack + `collapseSubViewSignal` + focus-park;
Movies/Series detail `BackHandler` toggles. **Corrections:** (a) `playerVisible`/`keepPlayingOnClose` are
**re-derived from nav state**, not simply deleted — the attach+visibility+z-order effect is rehomed (§3.4-3).
(b) Movies/Series "detail" is **VM state**, so deletion = **VM surgery** (§3.2/2.3), not removing a local var.
(c) `focusChannelOnReturnSignal` + `pendingOverviewFocus` are **re-homed, not deleted** (§3.4-7 / §4).

---

## 5. Phasing (review-restructured: A1→A2→B→C1→C2→D→E; each compiles, gates green, emulator+logcat verified)

The nav-spine swap is **atomic** (every `selectedRoute` reader flips together), so Phase A is split to isolate
the focus rebuild from the routing swap.

- **A1 — nav spine.** ✅ **DONE** (`4fc5246`). Catalog deps (nav 2.9.8, serialization plugin 2.2.21, json 1.x) + route ownership in
  `app`. Outer `NavHost` + `Scaffold` chrome (`hasRoute<Player>()` suppression) + `ShellGraph` with
  `MoviesGraph`/`SeriesGraph` **nesting present now** (List-only child, VMs **graph-scoped from the start**,
  review 2c). Tab pattern + `saveState`/`restoreState` + the **tab-root BACK shadow handler** (§3.5, review
  1a) + `navigateProtected` funnel + language/restore markers (§3.8) + `explicitSystemTargetSeen` suppression.
  **Re-key the Live-TV preview surface effects on `currentDestination`** (review 1b — a slice of the surface
  work is forced into A1; player still an overlay). **Temporary navigate bridges** for the `selectRoute`
  callers whose typed forms aren't ready (Search, deep-link resolve) — keep `*SearchTarget` alive as the
  payload for now (review 2a). Keep `focusTopNavPending` as a shim. **Gate:** VM survives tab switch (init-
  count log) AND re-entry lands on area-initial focus (focus logcat).
- **A2 — top-nav focus rebuild.** ⏸ **DEFERRED** (A1's selection-follows-focus works cleanly, no bounce to fix; revisit only if one appears). Delete `focusTopNavPending`/`focusRoute` bounce; add `focusRestorer` +
  `onEnter=active` (receiver form). Isolated so a focus regression isn't confused with a routing regression.
- **B — Movies + Series detail as destinations.** ✅ **DONE** (`a13600f` movies, `6934286` series). `MovieDetail`/`SeriesDetail` typed **stable-key** args;
  **VM surgery** (remove `detail*Flow`/`onOpenDetail`/staged latch; match by **stableKey**, rewrite the
  `toSeriesTarget`/`toEpisodeTarget` mappers — review Gap A/B); graph-scoped shared VM; detail focus
  initial+restorer. **Delete `movieSearchTarget`/`seriesSearchTarget` here** and rewire Search + the movie/
  series deep-link branch to typed navigation in the same phase (review 2b). Rewrite the broken tests (§8).
- **C1 — Player destination + one-connection handoff.** ✅ **DONE 2026-07-22 (not yet committed), TV-verified on
  SHIELD `.12`.** Player = top-level chrome-suppressed destination with **decomposed primitive args**;
  `playerVisible = currentDestination.hasRoute<Player>()`; back-stack-aware stop; adopt-not-replay. **TWO deviations
  (see the top status block):** (1) SurfaceViews were **NOT** hoisted to App level — the **overlay render is KEPT**
  (App-level always-composed surface broke video on hardware); (2) `navigateTab` runs **without** `saveState`/
  `restoreState` (the top-level Player entangled the tab registry → bounce + no-op tab switches). Also prefixed by
  **B(player)** — the PlayerViewModel orchestration extraction (`plans/player-viewmodel-extraction.md`).
- **C2 — Live-TV return-flow + nav/focus** ✅ **CORE DONE 2026-07-22 (not yet committed), TV-verified.** Fixed the
  bounce loop, wrong-channel-on-return (async-load race), return→Home / can't-leave-Live-TV, and a Compose self-nav
  crash; added the top-nav enter-focus (UP → active tab). **Deviated** from the plan: `liveTvSearchTarget` **KEPT**
  (+ `activate` flag), NOT deleted; `focusChannelOnReturnSignal`/`pendingLiveTvChannelFocus` deleted. **STILL OPEN:**
  full L/R column audit (incl. the live-tv-states RIGHT-from-low-category 2-press bug — unverified) + cold-load
  transient. `live-tv-states.md` to be reconciled after the L/R audit (committed→PlayerViewModel; RIGHT-bug status).
- **D — Settings inner NavHost.** ✅ **DONE** (`adeb84c` D1, `8686931` D2, `7c054fc` D3, + this session's focus
  rework `2d0255f`/`e76c97a`/`f90053b`/`b31cdeb` + cleanup `8043ad9`). Section panels + editors + About sub-pages
  as destinations (Back-to-parent); **`pendingOverviewFocus` specific-item return preserved** (review Risk C).
  Depends on the A1 tab-root back shadow (section-root BACK → gear, not Home). **Deviations from the plan:**
  (a) only **Playlists** sub-views became inner-nav destinations; **EPG/About kept their local overlays** +
  `collapseSubViewSignal`/`onParkFocusBeforeEditor` (deliberate scope cut — "optional later promotion" still
  open). (b) **Beyond the plan:** rail RIGHT now always re-enters on the first row, snapping a scrollable
  destination to the top via a shared `SettingsDetailList` wrapper (guarded so Return/Cancel still lands on the
  origin card); off-screen/sub-view focus-recovery hardened; added a debug-logging module `:core:logging`/`vcLog`.
  (c) **Deep-link polish (`927cbeb`):** a generic `SettingsEntryAction` (section + optional sub-view, cross-panel)
  + a **fresh per-entry inner `NavController`** (non-saveable) so a deep-link always starts on its target section
  — fixed the add-editor's Cancel/Save landing on General instead of the Playlists overview, and a stale section
  rendering under the deep-linked title for a frame ("General flashes before Playlists"); return focus preserved
  (Cancel→Add row, Save→new card). Retired the old approach → `plans/archive/settings-navigation-deeplinks.md`;
  residual add-editor **open latency** → `plans/settings-add-editor-open-latency.md`.
- **E — cleanup.** ⬜ **OPEN.** Manifest/typed deep-link finalization for system/WatchNext (unprotected) + the episode
  resolver bridge; dead-code sweep (incl. the ~900 copy-paste unused imports in `:feature:settings` — needs a
  proper optimize-imports/ktlint pass, NOT text matching: `getValue`/`setValue`/operators are used implicitly by
  `by`-delegates/operators); final detekt-baseline regen; full regression + the §10 doc-sync pass prep.

Riskiest single steps (C1 player handoff; D Settings focus) each land alone on a green, hardware-verified base;
no phase builds a bridge a later phase discards (targets deleted per-area, not batched in E).

---

## 6. Open decisions (validation-surfaced; defaulted to "keep the app as it is" unless you say otherwise)

All three default to preserving current behavior (your "keep as is"); flagged so you can override:
- **D-a — Tab-root BACK** = keep today's *focus-top-nav + double-back-exit* (shadow NavHost's pop-to-start),
  NOT the pattern's default *pop-to-Home*. (Default: keep.)
- **D-b — Preview↔fullscreen zero-flash** = keep both SurfaceViews alive above the NavHost + toggle visibility
  (no black flash), matching today. (Default: keep.)
- **D-c — Adult PIN timing** = prompt at **play** (Player navigation), not at detail-open, matching today
  (you can currently browse an adult item's detail without a PIN). (Default: keep.)
- **RIGHT-from-low-category bug** = designed out by the C2 focus rebuild (you already OK'd this).

If any of D-a/b/c should change, say so; otherwise they stay as "keep as is".

---

## 7. Risks & TV pitfalls
- **Player-as-destination + one-connection handoff** = hardest piece; §3.4 items 1–6 are design decisions to
  settle before C1; hardware verification required (emulator can't decode).
- **Graph-scoped VM lifetime** must match today's surviving-state (init-count log).
- **Focus vacuum on pop/push** — per-destination initial focus + nav-bar `onEnter=active`; watch that
  `saveState` doesn't pin stale focus; logcat every focus path.
- **`recreate()`/process-death back-stack restoration** vs the language/restore/regular-start flows (§3.8) —
  the marker-branch is mandatory; test the language change + restore + cold deep link paths explicitly.
- **`navDeepLink` fail-open on protected targets** — use the manual `await pinSecurityLoaded → navigateProtected`
  bridge (§3.6), don't attach typed deep links to protected destinations.
- **Do NOT** port reference-app smells (§9) or silently change visible layout (§0/§2.6). Scope is large — the
  A1→…→E phasing + per-area target deletion is the risk control.

## 8. Test / verification plan (review-expanded)
- Gates per phase: `.\gradlew.bat detekt` · `assembleDebug` · `test`. Structural bits on **both** API floor
  (26/28) and ceiling (36). **`adb logcat` on EVERY on-device test** (CLAUDE.md); temp `Log.d` at nav + focus
  + surface attach/detach handlers.
- **Tests the rebuild invalidates — rewrite/update in the owning phase (review Gap F):**
  `LiveTvRouteFocusTest` (targets+focus+columns → C2), `SeriesRouteDetailTest` + `SeriesViewModelTest` (staged
  target/detail → B), `MoviesViewModelTest` (onTarget/detail → B), `SettingsRouteInitialSectionTest`
  (initialSection → inner-NavHost start → D), `VivicastTopNavigationFocusTest` (top-nav focus → A2),
  `ProtectionGateTest` (string-route protection API → A1/§3.7). Keep green: `M3uPlaybackSmokeTest`,
  `ProtectionGateTest` (post-update), `WatchNextIntegrationTest`.
- Flow checklist per phase (§2.5 + §2.6): tab switch (data preserved, **initial focus** on re-entry), BACK per
  destination (detail→grid, editor→section, **section-root→gear not Home**, double-back-exit), PIN (route +
  **play-time adult**, no double-prompt, no Home fallback), deep links `vivicast://…` (+WatchNext +system
  search; **episode→SeriesDetail**; **protected fail-closed**), **language change + backup-restore +
  cold-deep-link** (no double-nav), resume-on-start, Live-TV commit-preview + **fullscreen handoff (hardware)**
  + close-to-preview channel focus + zap + catch-up + Auto-Next `Zurück` + `onChooseAnotherChannel`, Search →
  each target type, **process-death restore** (content re-resolves; Player re-resolves/pops).
- VM init-count log (scoping parity). `SystemTargetUnavailableDialog` VM-state→App wire tested.

## 9. Reference-app appendix (hints + verified smells)
**StreamVault:** `onEnter=active-route-requester` + per-route `FocusRequester` map (`AppShell.kt` 720-725);
`focusRestorer()` on plain `LazyRow`; `requestFocusSafely`; three-flag tab reselect. **Smells (avoid):**
Serializable-in-`savedStateHandle`, 26 resumed-guards, `returnRoute` string-threading.
**AerioTV:** graph-scoped shared VM off the graph entry; `popUpTo(graph){inclusive=false}` preserves the VM;
persistent Exo window at NavHost root. **Anti-patterns (AVOID):** selection-follows-focus 400ms heuristic,
`requestFocus` retry loops, declared-but-unused tv-material, 1px-alpha chrome hack.
**JetStream:** focus-driven `Tab(onFocus=select)`; player = outer-graph destination; `focusRestorer()` on the
tab `Row`; multi-stage BACK. String routes + pre-1.8 focus form → take the UX/structure, take the API from
android.com.

---

## 10. Doc-sync backlog (record deviations here; update `vivicast-docs` AFTER the rebuild — do NOT edit docs during it)
`<doc path> — <what changed> — <why (nav/focus)>`. Filled as the rebuild proceeds.
- (none yet)

---

## 11. Validation coverage (5-agent adversarial review, 2026-07-20 — where each finding landed)
- **API-correctness** → §3.1 (json runtime 1.x; per-module/route ownership), §3.2 (Player NavType→primitives;
  `hasRoute<T>` chrome; nullable-`"null"`), §3.3 (getBackStackEntry throw), §4 (focus 1.8/1.11; `onEnter`
  receiver form).
- **Blast-radius** → §2.3 (Movies/Series detail = VM state), §3.2/§4.1 (VM surgery; Room-id→stableKey), §3.6
  (explicitSystemTargetSeen; SystemTargetUnavailable VM→App wire), §2.2/§3.4-7 (focusChannelOnReturnSignal;
  onChooseAnotherChannel 3rd exit), §4 (pendingOverviewFocus specific-item), §8 (6 tests).
- **Phasing** → §5 (A1/A2/B/C1/C2/D/E), §3.5 (tab-root BACK 1a), §3.4 (surface re-key in A1), §5 (per-area
  target deletion; graph-scoped-from-A1; split C).
- **Cross-cutting** → §3.6 (episode GAP + navDeepLink fail-open + onNewIntent), §3.7 (play-time adult gate),
  §3.8 (recreate restoration; backup-restore marker; process-death Player; refresh LocalLifecycleOwner).
- **Player-handoff** → §3.4 (8 must-resolves; attach/detach API), §4.1 (playerVisible half-delete).
