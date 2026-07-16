# Playlist actions-menu restructure (intermediate detail/actions view)

Status: **✅ COMPLETED / ABGESCHLOSSEN (2026-07-16)** — implemented, owner-driven on emulator, audited,
committed + pushed to main (`ed791f7`), archived.

### Update 2026-07-16 (post non-blocking-imports)
- Actions menu fully driven: header "Updated" right-aligned, focus-escape fixes, per-provider refresh label
  ("Playlist/EPG wird aktualisiert…"), global refresh-button **cross-lock** (both lock while any refresh runs).
- The "Gruppen verwalten" **freshness gate was REMOVED** (imports are non-blocking now — see
  `plans/nonblocking-db-imports.md`). The refresh-button cross-lock stays (playlist→EPG auto-chain).
- Diagnostics `provider/deleted` (target) added; `playlist_refresh_triggered` + `provider_test_ok` already existed.
- Deferred (documented): optimistic `pendingRefresh` flag (R2) — obsoleted by the gate removal; residual
  cross-lock gap is harmless (WorkManager KEEP coalesces). detekt baseline now 34.

Original status below (historical).

Status: IMPLEMENTED (GO 2026-07-16) — verified static + app launches clean; owner-confirmed core flows.
Grounded by AerioTV's flow + Vivicast flow map + docs. Coupled to `plans/d10-channel-group-management.md`
(this view hosts "Gruppen verwalten" + replaces D10's stale-edit guard).

### Implementation status (2026-07-16)
**Built + verified (compile + detekt + assembleDebug green; app installs, launches, no crash):**
- `ProviderSettingsPanel` 4-view state machine (`showEditor` + `actionsProviderId` + `showGroups` layered
  flags): Overview → **Actions** (`ProviderActionsPanel`, new file) → Editor / Groups. Card click opens
  Actions (not the editor); Save/Back return to Actions; Actions Back → Overview.
- Actions menu: read-only detail header + Bearbeiten · Verbindung prüfen (test with inline counts) ·
  Aktualisieren (per-playlist, REPLACE) · Gruppen verwalten (freshness-gated) · Löschen.
- Delete removed from the editor (R9); `onRefreshProvider` REPLACE wired panel→SettingsRoute→MainActivity;
  save-refresh + menu-refresh use `restart=true` (REPLACE), refresh-all/interval/app-start stay KEEP (R10).
- detekt baseline regenerated (stable 30) for the added params.

**Owner-confirmed working (manual test):** back-from-editor → Actions; connection-test inline counts;
refresh-lock on the group button. **Just fixed (rebuild pending re-test):** header layout (Updated on the
right) + "Gruppen verwalten → Home" focus escape (park on actions↔groups).

**Remaining:** owner is re-testing the group-panel drive; Phase 11 docs alignment (Wireframe 05 / Screen 08
/ PRD 05 / components — edits ../vivicast-docs, needs owner go); optimistic pending-refresh flag deferred
(status==Refreshing only, ENQUEUED-gap window accepted).

## Goal
Insert an intermediate **"Playlist-Aktionen"** view between the playlist list and the edit form. Clicking
a playlist card opens the actions menu (not the editor). "Bearbeiten" becomes one action; Save/Back
returns to the menu. Mirrors AerioTV `PlaylistDetailScreen` (owner's reference), adapted to Vivicast's
multi-provider id model (no "active playlist" dance).

## Doc basis (aligned)
- Screen `08-playlist-epg.md:144-179` — per-playlist actions (Details öffnen, bearbeiten, aktualisieren,
  aktivieren, deaktivieren, löschen); the Detailbereich includes "Gruppen verwalten"; test-before-save on
  source change (`:177`); "Bei erfolgreichem Test wird gespeichert und direkt aktualisiert/importiert" (`:179`).
- PRD `05` — per-provider "jetzt aktualisieren".
- **Stale doc:** Wireframe `05-settings.md` still draws a single edit form and denies per-playlist refresh
  ("nur global"). PRD + screen 08 override it. → update wireframe 05 to match the new flow (doc edit, deferred).

## Current flow (verified — what changes)
- `ProviderSettingsPanel.kt`: one `var showEditor by remember { mutableStateOf(false) }` (`:132`) swaps
  overview↔editor via two `if` blocks (`:208`, `:360`). Card `onClick = { onOpenProvider(provider) }`
  (`:595`) → `onOpenProvider` (`:241-263`) sets `showEditor = true`. **Local state only, no NavController.**
- Save `persistEditor()` (`:297-328`): on success sets `showEditor = false` → returns to **overview**,
  focus on the saved card (`:317-320`), then `onProviderSaved(id)` (`:322`) → `MainActivity.kt:1008-1013`
  `enqueuePlaylistRefresh(id)` — async WorkManager, **`ExistingWorkPolicy.KEEP`** (`RefreshWorkScheduler.kt:62-68`).
- Actions today: Test connection (editor inline + implicit on save, no standalone); Delete (editor action
  row `ProviderEditor.kt:298-300`); enable/disable (editor draft toggle `:419-434`); refresh-all (overview
  row). **No standalone per-playlist refresh; no test outside the editor.**
- Dead code: `ProviderList` (`ProviderSettingsPanel.kt:654-706`) never called — safe to drop when here.

## New flow (3-state, owner-confirmed)
`showEditor: Boolean` → **`providerView: Overview | Actions(providerId) | Editor(providerId) |
Groups(providerId)`** (small sealed type + `selectedProviderId`). `Editor` and `Groups` are peer
sub-views reached from `Actions`. Still pure local state in `ProviderSettingsPanel`; `SettingsRoute`
section routing unchanged. Back chain: Editor/Groups → Actions → Overview.

- Card `onClick` → **Actions(id)** (was Editor).
- **Actions view** (new `ProviderActionsPanel`, scoped to `selectedProviderId`) — owner-confirmed contents:
  - **Detail header (read-only):** name, type badge, status, last refresh, content badges
    (Live-TV/Filme/Serien), Xtream expiry + max connections. Shows **"wird aktualisiert…"** while a refresh
    for this provider is in flight.
  - **Playlist bearbeiten** → Editor(id).
  - **Verbindung prüfen** → `TestProviderConnectionUseCase` (load creds for id first). Result rendered
    **right of the button**: idle → on click a **spinner + "Verbindung wird getestet…"** → on finish
    either **"Fehlgeschlagen: <Grund>"** or **"Verbindung OK (X Kanäle · Y Filme · Z Serien)"**. Reuses
    the same test function as the editor. **Counts already exist** — `TestProviderConnectionUseCase.test()`
    returns `ContentSummary(channels, movies, series)`; the spinner/Passed/Failed states + German counts
    string already exist in the editor (see R1). Must reproduce the async credential load first
    (`getProviderCredentials` + File-mode `getProviderM3uContent`). Test stays mandatory inside the editor
    on source change too (screen 08:177).
  - **Aktualisieren (nur diese Playlist)** → `enqueuePlaylistRefresh(id)`. **NEW** standalone control.
  - **Gruppen verwalten** → the D10 group panel. **Disabled + labelled "wird aktualisiert…" while a
    refresh for this provider is in flight; enabled only when the refresh has completed** → guarantees the
    D10 panel opens on fresh groups. (This REPLACES D10's source-dirty guard.)
  - **Löschen** → existing delete dialog.
  - Enable/disable **not** in the menu — stays the editor draft toggle (owner).
  - **Back** → Overview.
- **Editor Save** (`persistEditor`, ProviderSettingsPanel:297-328): return to **Actions(id)** instead of
  overview (change `showEditor=false`/`pendingOverviewFocus=Card` → set `providerView=Actions(id)` +
  actions-focus target). Refresh must use **`REPLACE`** — but via a **new `onRefreshProvider(id)` callback**
  (→ `enqueuePlaylistRefresh(id, REPLACE)`), **not** by flipping `onProviderSaved`. `onProviderSaved`
  stays `KEEP` because it is ALSO the refresh-all path (`ProviderSettingsPanel:238`) whose KEEP semantics
  are deliberate (a stuck provider must not block the rest — comment `:230-232`). Editor **Cancel/Back**
  (`dismissEditor`) → Actions(id).
- BackHandlers: Editor→Actions, Actions→Overview (mirror AerioTV precedence `MainScaffold.kt:867-876`).

## Refresh-in-flight signal (gates the group button + header status) — RESOLVED (R2)
Use **`Provider.status == ProviderStatus.Refreshing`** — observable via `observeProviders()` and **already
collected** in `SettingsViewModel.kt:91`. No `androidx.work`/WorkInfo in `feature/settings` (it's not on
that classpath). **Gap:** status flips to `Refreshing` only when the worker *executes* (network-constrained),
not at enqueue — so between tap/save and worker start the status is stale. **Fix:** an optimistic local
`pendingRefresh(providerId)` flag set the moment we enqueue (save or the menu's Aktualisieren); the group
button is locked when `pendingRefresh || status == Refreshing`, and unlocks when status leaves `Refreshing`
(clear the flag then). Add a safety timeout so a coalesced/never-run refresh can't lock it forever;
`clearStuckRefreshingStatus()` (`AppContainer.kt:417`) already self-heals stuck `REFRESHING` at startup.
Unique work name = **`"playlist_refresh:$providerId"`**. Exposed to the panel through `SettingsUiState`
(Flow lives in the VM, not collected in the composable).

## Blast radius (no new module, no DI, no NavController)
- `feature/settings/ProviderSettingsPanel.kt` — 3-state `providerView`; card onClick → Actions; save →
  Actions; BackHandlers; refresh-in-flight + detail-header state via VM. Drop dead `ProviderList`.
- `feature/settings/ProviderActionsPanel.kt` (**NEW file**) — the actions menu composable. New file, not
  added into `ProviderSettingsPanel`, to avoid growing the detekt baseline (see Risks R6).
- `feature/settings/ProviderEditor.kt` — **remove the Delete action row** (`:298-300`) + its `onDelete`
  plumbing; delete now lives only in the actions menu. Save/Cancel remain.
- `feature/settings/SettingsViewModel.kt` + `SettingsUiState` — per-provider refresh-in-flight Flow;
  a standalone `refreshProvider(id)` (delegates to the existing enqueue path); detail-header fields;
  connection-test state for the menu. Test-connection use case already wired (`SettingsRoute.kt:408`).
  Watch `TooManyFunctions` (already baselined) — keep additions minimal / delegate.
- `app/MainActivity.kt` — add a `onRefreshProvider(id)` wiring → `enqueuePlaylistRefresh(id, REPLACE)`
  for save + the menu Aktualisieren; **leave `onProviderSaved` (:1008-1013) at KEEP** (refresh-all +
  interval + app-start + restore keep it). `enqueuePlaylistRefresh` gains a `policy` param (default KEEP)
  so only these two sites opt into REPLACE (see R2/R10).
- `core/designsystem` strings (both locales): "Verbindung prüfen", "Verbindung wird getestet…",
  "Verbindung OK", "Fehlgeschlagen", "Kanäle"/"Filme"/"Serien", "Aktualisieren", "wird aktualisiert…",
  actions-menu title/labels, header labels.
- `plans/d10-channel-group-management.md` — updated (entry moves here; guard replaced).

## Docs alignment (final phase, after the rebuild is verified)
Once the rebuild is done and driven on the emulator, align `../vivicast-docs` to the new behaviour
(owner owns the docs — these are the known drift points):
- Wireframe `design/wireframes/05-settings.md` — replace the single-form edit + "no per-playlist refresh"
  with the actions-menu flow (list → Playlist-Aktionen → bearbeiten; per-playlist Aktualisieren exists).
- Screen `design/screens/08-playlist-epg.md` — reconcile "Details öffnen" vs "bearbeiten" into the
  actions-menu model; confirm the connection-test-with-counts wording; "Gruppen verwalten" reached from
  the actions menu.
- `design/components/settings.md` — the "Gruppen verwalten" section now describes the inlay panel +
  actions-menu entry, not an edit-form row.
- PRD `05` — ensure per-provider "jetzt aktualisieren" + connection-test counts match; note the default
  category order is now source order (was A→Z).
- Reconcile the two flagged doc inconsistencies (per-playlist refresh; single-form vs menu).

## Diagnostics / event logging (concrete — from the audit)
`DiagnosticsStore.log(category, message, details)`; gated by the diagnostics toggle. **Two hard rules:**
(1) log at the **app layer** (MainActivity callbacks, mirroring `onProviderSaved`) — feature/data/VM never
touch the store. (2) `DiagnosticsSanitizer` redacts URLs/secrets and blanks a detail **by key-name**, not
by value, and does **not** value-redact the message — so put provider **id under a `target`-style key and
never log the provider name** anywhere; counts/enums under plain keys are safe.
- **Reuse (already logged):** per-provider `refresh`/`PlaylistRefreshSucceeded`|`PlaylistRefreshFailed`
  (worker — `target`+counts+duration); `connection`/`provider_test_failed`.
- **New (app layer):**
  - `refresh`/`playlist_refresh_triggered` — at the enqueue call site (menu Aktualisieren + save). `target`.
  - `connection`/`provider_test_ok` — success + counts (`channels`,`movies`,`series`); beside the failure
    log (`MainActivity.kt:967-972`).
  - `provider`/`deleted` — no delete event exists today; add when delete moves to the menu. `target`.
  - New category `groups`: `group_hidden`/`group_shown` (`target`, group **stableKey** not name),
    `groups_bulk_hidden`/`groups_bulk_shown` (`count`), `group_reordered` (`type`,`count`),
    `sort_mode_changed` (`type`,`mode`), `new_groups_policy_changed` (`type`,`policy`) — via new app-layer
    callbacks (mirror `onProviderSaved`).

## Not doing (YAGNI)
- No NavController migration (local-state, matches current + AerioTV).
- No "Refresh Everything" / EPG-cache-nuke actions (AerioTV has them; out of scope unless asked).
- Enable/disable not hoisted to the menu.

## Risk register (from the 2026-07-16 codebase audit — 4 read-only agents)
| # | Risk | Severity | Resolution |
|---|---|---|---|
| R1 | Connection-test counts for "Verbindung OK (X/Y/Z)" | **Low — resolved** | `TestProviderConnectionUseCase.test() → ContentSummary(channels,movies,series)` already returns counts; spinner/Passed/Failed states + German counts string (`strings.xml:674`) already exist. Work = reproduce the async cred-load (`getProviderCredentials` + File-mode `getProviderM3uContent`) before `onTestProviderConnection`, render inline. Fix stale comment `ProviderConfigurationModels.kt:59`. Perf: Xtream 4 calls / M3U full parse — keep off-main (AppContainer already `Dispatchers.IO`). |
| R2 | Gate group button on "refresh in flight" | **Medium** | `Provider.status==Refreshing` (`observeProviders()`, already in `SettingsViewModel:91`). ENQUEUED gap → optimistic local `pendingRefresh` + status; unlock when status leaves `Refreshing`; safety timeout; `REPLACE` on save + standalone refresh. Work name `"playlist_refresh:$providerId"`. |
| R3 | Source-order category sort | **Low — resolved** | Order intact to `RoomCatalogImportRepository.kt:189`; only `.sortedBy` at `:190` reorders. `manualSortOrder` preserve mirrors `isHidden` (`:205`). `__UNCATEGORIZED__` loses its forced-first pin (D10 micro-default); Xtream = `get_live_categories` order. |
| R4 | Diagnostics events | **Low** | See Diagnostics section. App-layer only; provider **id** under `target`, never name; counts safe. |
| R5 | Focus + Back across the new 4-view stack | **Medium** | Verified in `ProviderSettingsPanel:120-420`. Every `showEditor` read/write (`:132,175,177,208,220,250,268,274,277,290,320,360`) → the `providerView` sealed. `dismissEditor` (:283-293) + `persistEditor` success (:316-322) return to **Actions**, not overview, with an actions-layer focus target (currently `pendingOverviewFocus=Card`). `collapseSubViewSignal` (:273-281) must collapse the **whole** stack (Editor/Groups/Actions → Overview). `LaunchedEffect(providers)` external-delete reset (:170-177) must cover Actions/Groups too. The async cred-load in `onOpenProvider` (:257-262) relocates: **Actions** loads creds for the detail header + "Verbindung prüfen"; the **Editor** load happens when Bearbeiten opens. Every transition needs an explicit FocusRequester (open Actions→first action; Editor/Groups back→originating row; Actions back→provider card) — the code repeatedly warns focus "escapes to the top nav bar → jumps Home" (:216,242-243,284) if a node is removed while focused. |
| R6 | Detekt baseline growth | **Medium** | `ProviderSettingsPanel`/`ProviderEditor`/`SettingsViewModel`/`SettingsRoute` already baselined-large. New composables (`ProviderActionsPanel`, group inlay panel) → **new files**, methods < LongMethod 150. Don't grow `SettingsViewModel` fn count (TooManyFunctions baselined) — delegate. Run `detekt` per batch. |
| R7 | Migration + backup ordering | **Low-Med** | v16→v17 = `manualSortOrder` col + `provider_category_settings` (FK→providers CASCADE). Restore order providers→categories→settings; add `manualSortOrder` to `StandardBackupCategory` + new `StandardBackupCategorySettings`. `17.json` + migration + schema tests. Restored order survives next refresh via the preserve line. |
| R8 | Group data to panel via VM/UiState | **Low-Med** | Panel reads all categories (incl. hidden) + settings via `SettingsViewModel`/`SettingsUiState`, not a repo Flow in the composable. Mirror the EPG per-provider observation (`onSelectEpgProvider`). Watch UiState growth (R6). |
| R9 | Delete/enable relocation | **Low** | Remove editor Delete row + `onDelete` wiring (`ProviderEditor.kt:298-300`); `DeleteProviderDialog` now triggered from the menu. Enable/disable stays the editor draft toggle (not in menu). Refresh-all stays in overview. |
| R10 | `REPLACE` scope — must not regress refresh-all/interval | **Low-Med** | `enqueuePlaylistRefresh` hardcodes `KEEP` (`RefreshWorkScheduler.kt:65`) and is called by save (`onProviderSaved`), refresh-all (via the same `onProviderSaved`, `ProviderSettingsPanel:238`), interval loop (`MainActivity:721`), app-start (`:679`), restore (`:387`). **Do NOT flip the shared method** — add a `policy` param (default `KEEP`) and route only save + the new menu Aktualisieren through a `onRefreshProvider(id)` → `REPLACE`. Refresh-all keeps `KEEP` (its "stuck provider must not block others" comment `:230-232` stays valid); interval must stay `KEEP` (REPLACE every tick would thrash). Precedent for the param value: `enqueueSeriesDetailsRefresh` already `REPLACE`. |

## Gates
`.\gradlew.bat detekt` (baseline 36), `:app:assembleDebug --rerun-tasks`, `test`. Emulator floor+ceiling
for the nav + save→refresh→group-unlock path. D10's migration adds schema/migration tests.

## Owner-confirmed (2026-07-16)
- Restructure to a 3-state actions menu; Save returns to the menu. **Confirmed.**
- Menu = Detail header + Bearbeiten + Verbindung prüfen + Aktualisieren (single) + Gruppen verwalten +
  Löschen; enable/disable stays in editor. **Confirmed.**
- Group button locked with "wird aktualisiert…" during refresh; save-refresh uses REPLACE. **Confirmed.**

## Still NO GO — planning only, more owner requirements may follow.
