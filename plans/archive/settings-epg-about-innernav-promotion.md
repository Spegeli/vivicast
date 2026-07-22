# Settings: promote EPG + About sub-views to inner-nav destinations

> Status: **✅ IMPLEMENTED + VERIFIED — 2026-07-22.** All phases P1–P5 done. EPG + About sub-views are now
> real inner-Navigation-Compose destinations (consistent with Playlists): `EpgOverviewScreen` +
> `EpgSourceEditorScreen`/`EpgManualMappingScreen`/`EpgGlobalSettingsScreen`; `AboutOverviewScreen` +
> `AboutLegalScreen`/`AboutTechnicalScreen`/`AboutDiagnosticsScreen`/`AboutDiagnosticsLogScreen`. Focus-return
> via `EPG_FOCUS_KEY`/`ABOUT_FOCUS_KEY` savedStateHandle tokens (+ `ABOUT_LOG_RETURN_KEY` for the nested log
> viewer). The About alpha-hide Box-stack scaffold + `collapseSubViewSignal` + `onParkFocusBeforeEditor` are
> fully removed (P4 folded into P3). Graphs wired **inline** in `SettingsRoute` like `PlaylistsGraph` (V2's
> ext-func extraction skipped: the baseline keys on signature so inline growth is free, `LongParameterList`
> ignores `@Composable`, and inline stays consistent with Playlists). Emulator-verified on API 36 (full
> focus/nav + focus-return matrix, OK-collapse incl. depth-3, no crashes, no Home-jump); **user-verified
> on-device** the EPG editor delete/add/save-existing/cancel returns. Gates green: detekt (baseline 30→27,
> stale `EpgSettingsPanel` entries removed), assembleDebug, test; androidTest compiles (fixed a pre-existing
> stale `EmptyEpgSourceRepository` fake missing `observeCurrentProgramsForChannels`). No new focus androidTests
> — consistent with the D2 Playlists split, which relied on emulator verification. (User decision 2026-07-22:
> make EPG + About consistent with Playlists — their sub-views become real inner-Navigation-Compose
> destinations, even though not deep-linked yet, so we can build on it later.)
>
> **Scope:** pure UI/nav restructuring inside `:feature:settings`. **No** ViewModel/data/DB change, **no** new
> module, **no** new user-facing behavior — the screens look + behave the same; only the *sub-view hosting*
> moves from local boolean/enum overlays to nav destinations. EPG **and** About in ONE plan (they share
> `collapseSubViewSignal`, which only becomes removable once BOTH are migrated).
>
> **Test env:** emulator (`scripts\start-tv-emulator.ps1`) — the SHIELD is retired for this. This work is
> nav/focus structure → the emulator tests it well. The one gap: the EPG source editor's **text-typing/save**
> flow (leanback IME eats adb keys, [[tv-adb-ime-testing]]) → user-verify, same as the D-phase; open/close +
> focus-return of the editor IS auto-testable.

## Validation pass (2026-07-22 — before GO)

Verified against the code; findings folded in:
- **V1 — `AboutLegal` uses a String `pageKey`, not the enum.** Nav is 2.9.8 (supports `@Serializable` enum
  args), but EVERY existing typed route arg in this app is `String`/`String?` — no enum precedent. Using a
  String (`"privacy"`/`"terms"`, mapped to the existing `AboutLegalPage` enum inside the screen) matches
  precedent and removes all enum-NavType uncertainty. Zero-risk.
- **V2 — extract the graph builders to `NavGraphBuilder` extension functions.** `SettingsRoute` is already a
  baselined god-composable and the Playlists graph is ~180 lines INLINE in it. Adding EPG+About graphs inline
  would bloat it further / drift the detekt baseline. Put them in `fun NavGraphBuilder.epgSettingsGraph(…)` /
  `aboutSettingsGraph(…)` (idiomatic Nav Compose) so the NavHost body stays thin (`epgSettingsGraph(…)` /
  `aboutSettingsGraph(…)` calls). Keeps `SettingsRoute` complexity flat; the panel splits NET-REDUCE complexity.
- **V3 — no test breakage.** Only `EpgSourceEditorStateTest` touches EPG, and it tests the `EpgSourceEditorState`
  *model* (reused unchanged), not the panel structure. The new focus/nav androidTests are additive.
- **V4 — no external callers.** Every sub-view body (`EpgSourceEditor`, `ManualEpgMappingPanel`,
  `EpgGlobalSettings`, `AboutDiagnosticsOverlay`, `AboutLegalOverlay`, `TechnicalDetailsOverlay`,
  `DiagnosticsLogViewer`) is called ONLY inside its own panel → free to wrap in Screen composables.
- **V5 — section highlight + deep-link start already handle nested graphs.** `SettingsSection.matchesRoute`
  uses `hierarchy.any { hasRoute }` (SettingsRoute.kt:166), so `matchesRoute = EpgGraph::class`/`AboutGraph::class`
  keeps the section highlighted while a sub-view is open (proven by `PlaylistsGraph`). `startSectionEntry` maps
  the deep-link label → the section's `navTarget` (= the graph, starting at the overview) — no special-casing.
  The `openAddEditorOnEnter` deep-link (:461) is `SettingsEntryAction.AddPlaylist`-only → EPG/About untouched
  (a future `SettingsEntryAction.AddEpgSource` etc. could mirror it — enabled by these routes, out of scope now).

## Why (and why it's optional-but-wanted)

- **Consistency:** today Playlists sub-views are nav destinations (real BACK, back stack) while EPG + About
  sub-views are local overlays (boolean/enum flags + hand-rolled BackHandlers + a focus-park-before-swap +
  manual refocus-the-opener scaffold). One pattern is better than two.
- **Future deep-link readiness:** once they're typed destinations (like `PlaylistEditor`), we *could* later
  deep-link "add EPG source" or a specific About page — not built here, just enabled.
- **Simpler state:** deletes the About alpha-hide scaffold + the EPG/About local flag machinery + the shared
  `collapseSubViewSignal`; replaces the local `pendingOverviewFocus` retry loop with the nav-result
  `savedStateHandle` focus-return Playlists already uses.
- **Honest sizing:** this is a **medium-large refactor**, not a quick win. EPG is a god-panel (3 sub-views + a
  parent-hoisted delete dialog + connection-test + duplicate-URL logic); About is 4 sub-views incl. a nested
  diagnostics state machine (rows → log viewer → delete dialog). No user-visible payoff today. Worth doing for
  the architecture, but it's real work — sized in §7.

## Reference: how Playlists is already done (mirror this)

`SettingsInnerRoutes.kt` + `SettingsRoute.kt`:
- **Nested graph:** `navigation<PlaylistsGraph>(startDestination = SecPlaylists) { composable<SecPlaylists>{…}; composable<PlaylistActions>{…}; composable<PlaylistEditor>{…}; composable<PlaylistGroups>{…} }` (SettingsRoute.kt:647-826). The rail section matches the **whole graph** (`SettingsSection(PlaylistsGraph, …)`, :361) so an open sub-view keeps the section highlighted, and the nested-graph shape preserves the per-entry-controller flash fix (`927cbeb`; SettingsInnerRoutes.kt:11-17).
- **Navigate in:** `parkRail(); innerNav.navigate(PlaylistEditor(id))` (:657-658, 696-697). `parkRail()` = `focusHolder.requestFocus()` on an invisible 1.dp Box (:422-425, :912) so focus can't orphan to the top nav (which would selection-follow to Home) during the NavHost content swap — no visible flash.
- **Focus-return (the nav-result):** before popping, stash a token on the parent entry —
  `innerNav.previousBackStackEntry?.savedStateHandle?.set(PROVIDER_FOCUS_KEY, providerId)` (:680, 700, 750) or
  `getBackStackEntry(SecPlaylists).savedStateHandle[PROVIDER_FOCUS_KEY] = focusId` for delete-to-overview
  (:756). The overview reads `entry.savedStateHandle.getStateFlow<String?>(PROVIDER_FOCUS_KEY, null).collectAsStateWithLifecycle()` (:649-651) → drives a `repeat(30){ awaitFrame(); scrollToItem+requestFocus }` retry loop (`ProviderOverviewPanel`, ProviderSettingsPanel.kt:586-624), then `onFocusHandled()` clears the token.
- **Self-contained screens:** each destination owns its state — `ProviderEditorScreen` self-loads its draft from `providerId`; `ProviderActionsScreen` **hoists its own `DeleteProviderDialog`** and returns `onDeleted(neighborId ?: OVERVIEW_FOCUS_ADD)` (ProviderSettingsPanel.kt:170-270, 248-269).
- **Rail RIGHT re-entry:** the overview list is a `SettingsDetailList` (SettingsRoute.kt:221-237); rail RIGHT bumps `LocalRevealFirstRowSignal` → `ScrollFirstRowIntoView` snaps to item 0 → `detailFocusRequester` (on the first row via `detailFirstFocusModifier`, :413-415) focuses.
- **OK on the current rail section** collapses an open sub-view: `activateSection.onCollapse` today does BOTH `collapseSubViewSignal++` (EPG/About local overlays) **and** `innerNav.popBackStack(SecPlaylists, inclusive=false)` (Playlists nav) (:448-453).

Nav-result key constants live in `SettingsRoute.kt:137-140` (`PROVIDER_FOCUS_KEY`, `FROM_GROUPS_KEY`) + `ProviderSettingsPanel.kt:108` (`OVERVIEW_FOCUS_ADD` sentinel).

## Current state — the sub-views to promote (from the code inventory)

### EPG (`EpgSettingsPanel.kt`, hosted at `composable<SecEpg>` SettingsRoute.kt:827-857)
Overview is a mutually-exclusive `when{}` full-width infill swap; local flags at :123-137.

| # | Sub-view | Shown by (flag) | Route arg | Notable state to self-load |
|---|---|---|---|---|
| EPG-1 | **Global settings** (auto-refresh interval, retention days, on-start/on-change toggles) | `showGlobalSettings` | **none** | `state: EpgSettingsState` + `onEpgPreferencesChanged` (VM). Nests `ProviderIntervalDialog` (a dialog, stays a dialog). |
| EPG-2 | **Source editor** (add/edit: name, URL, timeshift, enable, test, delete) | `showEditor` + `selectedSourceId` + `editor` draft | **`sourceId: String?`** (null=add) | draft `EpgSourceEditorState`; `sources` list; dup-name over `sources`; **dup-URL `existingEpgUrls` over ALL sources' secure URLs (:169-179)**; connection-test tri-state; `epgSaving`. **`DeleteEpgSourceDialog` is parent-hoisted (`pendingDelete`, :131,506-536) → must move INTO the editor destination.** |
| EPG-3 | **Manual mapping** (3-column provider\|channel\|detail) | `showManualMapping` | **none** (VM-keyed) | VM selection: `selectedEpgProviderId`, `selectedManualMappingChannelId`, `manualMappingChannels` (provider-scoped query), `manualMappingsForSelectedChannel`; opening calls `onResetManualMappingChannel()`. |

Shared EPG focus-return today = `pendingOverviewFocus` + a `repeat(30){awaitFrame; scrollToItem+requestFocus}` loop (:383-412) — the local analog of Playlists' savedStateHandle result.

### About (`AboutSettingsPanel.kt` + `AboutDiagnosticsOverlay.kt`, hosted at `composable<SecAbout>` :893-906)
Overview is a **Box-stack with an alpha-0-but-composed `SettingsDetailList`** + overlays drawn on top (NOT a `when` swap) — the list stays composed so the focused row survives (destroying a focused row → focus escapes to top nav → Home). Flags at :108-118.

| # | Sub-view | Shown by (flag) | Route arg | Notable state |
|---|---|---|---|---|
| ABT-1 | **Technical details** (read-only build/device rows) | `showTechnical` | **none** | `state: AboutAppState`. Scrollable Column (not LazyColumn) so first row survives scroll. |
| ABT-2 | **Diagnostics** (logging toggle, export, delete, view-log) | `showDiagnostics` | **none** | VM `diagnostics.*`, `onToggle/Export/DeleteLogs/ReadLog`. **Nested state machine** — `viewingLog` → `DiagnosticsLogViewer` (Events/Crashes tabs + refresh + custom lazy `focusProperties.exit` Up-redirect, AboutDiagnosticsOverlay.kt:293-297) and `showDeleteDialog` → `DiagnosticsDeleteDialog`. |
| ABT-3/4 | **Legal** (Privacy / Terms) — ONE overlay, enum `AboutLegalPage` | `legalPage: AboutLegalPage?` | **`page: AboutLegalPage`** | title/body from the enum's string resources. `closeLegal` refocuses **different opener rows** (`legalTermsRowFocus` vs `legalRowFocus`) per enum — the nav-result must carry which. |

## Target design

### 1. Routes — `SettingsInnerRoutes.kt` (additions, mirror `PlaylistsGraph`)
```
@Serializable internal object EpgGraph            // rail section target; nested graph
@Serializable internal object SecEpg              // graph start = EPG overview
@Serializable internal data class EpgSourceEditor(val sourceId: String? = null)  // null = add
@Serializable internal object EpgManualMapping
@Serializable internal object EpgGlobalSettings

@Serializable internal object AboutGraph          // rail section target; nested graph
@Serializable internal object SecAbout            // graph start = About overview
@Serializable internal data class AboutLegal(val pageKey: String)  // "privacy" | "terms" — STRING arg (see V1: matches the String-only route-arg precedent; no enum-NavType risk). The screen maps it to the existing AboutLegalPage enum.
@Serializable internal object AboutTechnical
@Serializable internal object AboutDiagnostics
@Serializable internal object AboutDiagnosticsLog  // DECISION D1 = (a): the log viewer is its own destination
```
`SecEpg`/`SecAbout` stay the start destinations; the rail `SettingsSection(...)` switches from `SecEpg`/`SecAbout` (SettingsRoute.kt:362,368) to `EpgGraph`/`AboutGraph`.

### 2. Graph wiring — `SettingsRoute.kt`
Replace `composable<SecEpg>{ EpgSettingsPanel(...) }` (:827) and `composable<SecAbout>{ AboutSettingsPanel(...) }` (:893) with:
```
navigation<EpgGraph>(startDestination = SecEpg) {
    composable<SecEpg>          { EpgOverviewScreen(... navigate via parkRail()+innerNav.navigate(EpgSourceEditor(id)/EpgManualMapping/EpgGlobalSettings), reads EPG_FOCUS_KEY) }
    composable<EpgSourceEditor> { entry -> EpgSourceEditorScreen(sourceId = entry.toRoute<EpgSourceEditor>().sourceId, ... stashes EPG_FOCUS_KEY + pop) }
    composable<EpgManualMapping>{ EpgManualMappingScreen(...) }
    composable<EpgGlobalSettings>{ EpgGlobalSettingsScreen(...) }
}
navigation<AboutGraph>(startDestination = SecAbout) {
    composable<SecAbout>        { AboutOverviewScreen(... navigate to AboutLegal(page)/AboutTechnical/AboutDiagnostics, reads ABOUT_FOCUS_KEY) }
    composable<AboutLegal>      { entry -> AboutLegalScreen(page = entry.toRoute<AboutLegal>().page, ...) }
    composable<AboutTechnical>  { AboutTechnicalScreen(...) }
    composable<AboutDiagnostics>{ AboutDiagnosticsScreen(...) }   // + AboutDiagnosticsLog per D1
}
```
All these composables live in SettingsRoute's scope, so they read the SAME `settingsUiState` + `viewModel` callbacks that the two panels get today — **no state is passed as a route arg except the ids/enum above** (heavy state stays VM/scope-sourced, exactly like the Playlists destinations).

### 3. Panel split (the real work)
- `EpgSettingsPanel` → `EpgOverviewScreen` (source list + prefs + the 3 "open sub-view" rows) + `EpgSourceEditorScreen` (self-loads draft from `sourceId`, owns the connection-test + `epgSaving` + the moved `DeleteEpgSourceDialog`, recomputes dup-name/dup-URL from `sources`+`onGetEpgSourceUrl`) + `EpgManualMappingScreen` (wraps `ManualEpgMappingPanel`; calls `onResetManualMappingChannel()` in an entry effect) + `EpgGlobalSettingsScreen` (wraps `EpgGlobalSettings`). Reuse the existing `EpgSourceEditor`/`ManualEpgMappingPanel`/`EpgGlobalSettings` bodies unchanged inside the new Screen wrappers (like ProviderEditorScreen reuses `ProviderEditor`).
- `AboutSettingsPanel` → `AboutOverviewScreen` (the rows, now a real `SettingsDetailList` overview — no alpha-hide) + `AboutLegalScreen(page)` + `AboutTechnicalScreen` + `AboutDiagnosticsScreen` (+ `AboutDiagnosticsLogScreen` per D1). **Delete the entire Box-stack/alpha-0 scaffold + `closeLegal/closeTechnical/closeDiagnostics` refocus-opener-before-drop** — nav pop + savedStateHandle return replaces it.

### 4. Focus-return (nav-result)
Add `EPG_FOCUS_KEY` + `ABOUT_FOCUS_KEY` constants (like `PROVIDER_FOCUS_KEY`). Each sub-view, before pop, stashes a **token** for the origin row on `previousBackStackEntry.savedStateHandle`; the overview reads it via `getStateFlow(...).collectAsStateWithLifecycle()` → the existing `repeat(30){…}` retry loop → focus + clear. Tokens:
- EPG editor: the `sourceId`, or an `EPG_FOCUS_ADD` sentinel (add-row) / on delete-to-overview a neighbor-source id (mirror `ProviderActions.onDeleted(neighbor ?: add)`).
- EPG manual/global + About technical/diagnostics: a fixed sentinel per opener row.
- About legal: the `AboutLegalPage` enum (or a per-row sentinel) so the correct Privacy-vs-Terms row is refocused.

### 5. Remove (once BOTH migrated)
`collapseSubViewSignal` (SettingsRoute.kt:404-408, params at :856,905; EpgSettingsPanel :206-215; AboutSettingsPanel :140-144) → the `activateSection.onCollapse` branch (:448-453) drops the `collapseSubViewSignal++` line and instead pops the section's sub-views: `runCatching { innerNav.popBackStack(SecEpg, inclusive=false) }` / `popBackStack(SecAbout, inclusive=false)` (mirroring the Playlists `popBackStack(SecPlaylists, …)` already there). Also drop EPG's `onParkFocusBeforeEditor` callback (:855) — the destinations call `parkRail()` at the navigate site instead.

## Decision needed from you

**D1 — The diagnostics log viewer.** `DiagnosticsLogViewer` is a full sub-screen (tabs + refresh + log tail + custom Up-exit focus), not a dialog. Two options:
- **(a) Own destination `AboutDiagnosticsLog`** — most consistent with "sub-screens are destinations" (Playlists keeps dialogs as dialogs but promotes screens). BACK-to-parent for free; but its custom lazy `focusProperties.exit` Up-redirect + first-pill focus must be preserved.
- **(b) Keep it as an in-destination mode inside `AboutDiagnosticsScreen`** (a `viewingLog` state) — simpler, avoids re-plumbing the fiddly viewer focus; still "diagnostics is a destination," just with an internal viewer.
- The `DiagnosticsDeleteDialog` stays a **dialog** either way (like `DeleteProviderDialog`).
- **My recommendation: (a)** for full consistency, unless you'd rather minimize risk on the viewer's custom focus — then (b). *This is the only real design fork; everything else follows the Playlists template.*

## Gotchas + how the plan handles them (from the inventory)

1. **EPG editor is more than an id** — draft + dup-URL-over-all-sources + connection-test are panel-scoped. The editor Screen self-loads them (draft from `sourceId` via `onGetEpgSourceUrl`; dup-URL recomputed from `sources`). Feasible — `ProviderEditorScreen` already self-loads a draft.
2. **EPG delete dialog moves into the editor Screen** (parent no longer hosts it) — mirror `ProviderActionsScreen`'s `DeleteProviderDialog` + neighbor-focus-return.
3. **Manual-mapping VM selection lifecycle** — reset-on-open (`onResetManualMappingChannel()`) moves to the navigate site or an entry `LaunchedEffect`; VM state stays VM-owned (no route arg).
4. **About alpha-hide scaffold is deleted, not ported** — the whole reason it exists (a destroyed focused row → Home jump) goes away with real destinations + savedStateHandle return. **Risk: reproduce the focus-return exactly or the Home-jump returns** — the emulator focus tests below are the guard.
5. **Diagnostics nested state machine** — see D1; the delete dialog stays a dialog.
6. **Legal: 2 pages, 1 enum, distinct focus-return** — route arg = the enum; the return token carries which opener row.
7. **`collapseSubViewSignal` shared by EPG + About** — only removable after BOTH; that's why they're one plan (do EPG first keeping the signal for About, then About, then remove).
8. **Per-destination first-focus differs** (editor→name field/enable toggle; global→first row; manual→first provider; legal→first paragraph; technical→first row; diagnostics→toggle). Each Screen keeps its own initial-focus effect; only the OVERVIEWS need `detailFirstFocusModifier` on their first row for rail-RIGHT.
9. **Preserve the flash fix** — nested `navigation<…>(startDestination = SecEpg/SecAbout)` + rail section = the graph, mirroring `PlaylistsGraph`.

## Phasing (each phase compiles + is emulator-verifiable)

- **P1 — routes + graph skeleton.** Add the routes; wrap `SecEpg`/`SecAbout` in `navigation<EpgGraph>`/`<AboutGraph>` with ONLY the overview composable (still the current panels). Rail section → the graphs. Compiles; sections still work. Gates green.
- **P2 — EPG split.** `EpgSettingsPanel` → overview + 3 destination Screens; navigate + `EPG_FOCUS_KEY` return; move the delete dialog into the editor; keep `collapseSubViewSignal` alive for About. Emulator-verify EPG focus/nav.
- **P3 — About split.** `AboutSettingsPanel` → overview + 3(+1) destination Screens; delete the alpha-hide scaffold; `ABOUT_FOCUS_KEY` return; D1 resolved. Emulator-verify About focus/nav.
- **P4 — remove `collapseSubViewSignal`** (both migrated) → `onCollapse` pops EPG/About sub-views; drop `onParkFocusBeforeEditor`. 
- **P5 — tests + gates.** androidTest focus/nav matrix (below); detekt baseline regen (splitting god-panels should NET-REDUCE complexity, like the D2 split was −2); `assembleDebug` + `test` + androidTest-compile; emulator manual pass.

## Tests

**androidTest (Compose, `:feature:settings`, extend the existing `SettingsDialogFocusTest`/`SettingsRouteInitialSectionTest` style):**
- Rail RIGHT → EPG overview first row focused; → About overview first row focused.
- EPG: open source-editor (add-row) → name field focused; BACK → overview, focus on the add row. Open source-editor (a source card) → enable toggle focused; BACK → focus that card. Open manual-mapping → first provider row; BACK → focus the "Manuelle Zuordnung" row. Open global-settings → first row; BACK → focus the "Globale Einstellungen" row.
- EPG delete a source in the editor → overview, focus a neighbor source (or add-row if none).
- EPG off-screen source card (scrolled list) → open + BACK → focus returns to that card (the retry loop).
- About: open Legal(Privacy) → BACK → focus Privacy row; Legal(Terms) → BACK → focus **Terms** row (distinct); Technical → BACK → Technical row; Diagnostics → BACK → Diagnostics row; (per D1) open log viewer → BACK → view-log row, Up-exit-redirect focus preserved.
- OK on the EPG/About rail section while a sub-view is open → collapses to the overview (sub-view popped).
- Deep-link into EPG/About section still lands with no section-flash (existing `SettingsRouteInitialSectionTest`).

**Emulator manual (primary env):** the full click-through of every sub-view + BACK + focus-return, on API-36 (ceiling) and one lower API. **Prerequisite:** a provider with ≥1 EPG source configured (else test the add-flow + empty state). Watch `adb logcat -s VCd` for the `[settings-focus]`/section traces.

**User-verify (IME-gated, can't auto-test):** the EPG source editor **typing + save** flow (name/URL fields — leanback IME eats adb keys, [[tv-adb-ime-testing]]). Open/close + focus-return of the editor IS auto-testable; only the text entry + save is manual.

**Gates:** `detekt` (baseline regen for the new Screen composables — expect a net reduction or small churn), `assembleDebug`, `test`, androidTest-compile.

## Constraints

- No code before explicit GO. Feature-owned inner nav stays inside `:feature:settings` (the documented exception; SettingsInnerRoutes.kt:11-17). **No** VM/data/DB change (VM state + callbacks unchanged; destinations read the same scope). **No** new Gradle module. Strings stay in `:core:designsystem` (de+en) — but this reuses existing sub-view bodies, so likely **no new strings**. Immutable UiState + StateFlow untouched. detekt green. No commit/push without approval.
- `../vivicast-docs` already describes Settings as a rail master-detail with an inner NavHost (updated 2026-07-22); promoting EPG/About is *more* conformant, so at most a one-line note that EPG/About sub-views are now destinations too — done after implementation, not during.

## Open decisions for you
1. **D1** — diagnostics log viewer: own destination (a, recommended) vs in-destination mode (b)?
2. **Naming** — `EpgSourceEditor`/`EpgManualMapping`/`EpgGlobalSettings` + `AboutLegal`/`AboutTechnical`/`AboutDiagnostics` OK, or prefer another convention?
3. **Effort ack** — this is a medium-large refactor with no user-facing change today. Green-light the full EPG+About in one go, or EPG first (P1-P2) then reassess before About?
