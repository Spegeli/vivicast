# Settings Navigation & Deep-Links — Problem Notes + Plan (ARCHIVED — resolved)

> Status: **RESOLVED & ARCHIVED.** Everything this file scoped is solved by the Jetpack Navigation Compose
> rebuild (D1–D3) plus the deep-link polish that followed. The "What does NOT work" / "Fix directions"
> sections below describe the OLD `selectedRoute` + `ProviderSettingsPanel`-remount model and no longer
> reflect the code — kept for history only.

## RESOLVED — what we actually built

Deep-linking into any Settings target now works reliably, regardless of prior focus:

- **Inner Settings NavHost** (`feature/settings/.../SettingsRoute.kt`): the rail drives a real
  `androidx.navigation` graph; each section is a typed `@Serializable` destination and Playlists is a nested
  graph (`PlaylistsGraph → SecPlaylists` + `PlaylistEditor` / `PlaylistActions` / `PlaylistGroups`). This
  replaced the old `selectedRoute` + `showEditor` + panel-remount model, so the route-bounce /
  reset-wipes-intent / panel-remount / collapse-effect failure modes below are structurally gone.
- **Section-landing:** `SettingsRoute(initialSelectedSection = <label>)` → the inner NavHost's
  `startDestination` is that section. Driven by MainActivity `pendingSettingsSection` / `openPlaylistSettings()`.
- **Sub-view deep-link** (the thing that "did NOT work" — opening the add-provider form on entry): now via a
  generic **`SettingsEntryAction`** enum (`None | AddPlaylist`, extensible per section). MainActivity forwards
  it as `pendingSettingsEntryAction`; SettingsRoute's entry effect opens the sub-view
  (`innerNav.navigate(PlaylistEditor())`) on top of the section. The host never needs a section's internal
  routes → cross-panel by design (add EPG/Appearance sub-views later with one enum case + one branch).
- **Fresh inner controller per entry** (non-saveable `remember { NavHostController(...) }`): the NavHost
  always starts on the deep-linked section, so a retained prior section can't render under the wrong title
  (killed the "General flashes before Playlists" glitch) and the editor always has the overview beneath it.
- **Return focus:** Cancel/Save from the deep-linked editor pop to the Playlists overview with the Add row /
  the new provider card focused (`PROVIDER_FOCUS_KEY` on `previousBackStackEntry`).

Verified on emulator via `adb logcat -s VCd` (compose/nav/focus traces) + physical TV. Gates green.

**Open residual (its own plan):** the add-editor's *open latency* — the Playlists overview is briefly visible
while the heavy `ProviderEditorScreen` composes → see `plans/settings-add-editor-open-latency.md`.

---

_Everything below is the ORIGINAL pre-rebuild scoping — historical context only, does not reflect the code._

## Goal

From a button anywhere (Home empty states, future entry points), deep-link into **any Settings target** —
not just a section, but ideally a specific **sub-view / action** inside it (e.g. open the *add-provider
form* directly, or a specific EPG editor). Should be reliable regardless of where focus was.

## What already works (keep / build on)

- **Section-landing** via `SettingsRoute(initialSelectedSection = <localized section label>)`. Robust,
  proven (also used by the language-row re-entry flow). Home's "Wiedergabeliste hinzufügen" and the
  disabled/empty "Open Settings" buttons use it today (land on the **Playlists** section).
  - MainActivity: `pendingSettingsSection` state → `SettingsRoute(initialSelectedSection = …)`; reset when
    leaving Settings. `openPlaylistSettings()`.
- **`focusTopNavPending`** (MainActivity): after a Home CTA switches top-level route, focus is moved onto
  the *target* nav tab so Compose focus doesn't fall back to the Home tab (whose focus-follows-selection
  would yank the route back to Home). Works for the Live-TV/Filme/Serien CTAs (verified).

## What does NOT work: opening a Settings sub-view on entry (the add-provider form)

Attempted: a one-shot `openAddOnEnter` flag threaded MainActivity → `SettingsRoute` →
`ProviderSettingsPanel`, whose `LaunchedEffect(openAddOnEnter)` sets `showEditor = true` on mount.
Reverted — it opens for a frame then closes. Root causes found via on-device logcat:

1. **Route bounce.** Navigating from a Home content button into Settings makes Compose focus briefly fall
   back to a nav tab; `VivicastTopNavigation.onFocused` → MainActivity `focusRoute(route)` (focus follows
   selection) flips `selectedRoute` to `home` for a moment. Logcat showed `selectedRoute=home` right after
   `selectRoute("settings")`. `focusTopNavPending` mitigates the *visible* bounce for simple CTAs but the
   transient still fires effects.
2. **Reset wipes the intent.** MainActivity's `LaunchedEffect(selectedRoute)` clears `pendingSettingsSection`
   (and, in the attempt, the add-intent flag) whenever `selectedRoute != "settings"` — so the transient
   bounce to `home` wipes the deep-link intent.
3. **Panel remounts, loses state.** During this churn `ProviderSettingsPanel` **remounts** (all its
   `LaunchedEffect`s re-run, `remember { showEditor }` resets to `false`). So even after the editor is
   opened, the remount closes it.
4. **Collapse effect fights an open-on-mount editor.** `ProviderSettingsPanel`'s
   `LaunchedEffect(collapseSubViewSignal)` runs on first composition and, if `showEditor == true`, collapses
   the editor to the overview. It's *designed* assuming `showEditor` starts `false` ("initial fire is a
   no-op"). So initializing `showEditor = true` (or setting it in a mount effect that runs before it) is
   immediately undone.

Net: opening a sub-view on entry requires the intent to **survive a remount + a transient route bounce**,
and the collapse effect must not eat it.

## Key code touchpoints

- `app/.../MainActivity.kt`: `selectRoute`, `focusRoute` (focus-follows-selection), `focusTopNavPending`,
  `pendingSettingsSection`, `LaunchedEffect(selectedRoute)` reset, `SettingsRoute(...)` wiring.
- `core/designsystem/.../VivicastNavigation.kt` (`VivicastTopNavigation`): `onFocused` → route change.
- `feature/settings/.../SettingsRoute.kt`: `selectedSection by remember(initialSelectedSection)`, section
  rail focus in `LaunchedEffect(Unit)`, the `when (selectedSection)` panel dispatch, `sectionPlaylists`.
- `feature/settings/.../ProviderSettingsPanel.kt`: `showEditor`, `onAddProvider` (the real open-editor
  body), `LaunchedEffect(collapseSubViewSignal)` (collapse), `LaunchedEffect(providers)`.

## Fix directions to evaluate (pick during the dedicated pass)

1. **Kill the root bounce.** Decouple nav-tab *focus* from *route selection*, or suppress `focusRoute`
   while a programmatic navigation is in flight (a "navigation in progress" guard). If content→route
   navigation never bounces, the reset never wrongly fires and the panel doesn't remount. This also
   simplifies `focusTopNavPending`. Likely the cleanest root fix.
2. **Robust deep-link intent model.** A single `SettingsDeepLinkTarget = (section, subView?/action?)`
   consumed **once** on Settings entry, held somewhere that survives remounts + transient route changes
   (process-scoped on `AppContainer`, not transient Compose state that the route-reset wipes). SettingsRoute
   reads+clears it on mount; the panel opens the sub-view from it.
3. **Make `collapseSubViewSignal` skip its initial fire** (guard a `firstRun` flag) so an editor opened on
   mount isn't collapsed. Small, but only helps once (1)/(2) keep the panel from remounting.
4. **Generalize entry points.** Once robust, expose deep-links to every section (General/EPG/Appearance/
   Playback/Parental/Storage/Backup/About) and selected sub-views, reusable from Home and elsewhere.

## Related Settings-navigation improvements (user has more — fill in)

- (user to add) — several Settings navigation/focus points to improve alongside this.
- Candidates noticed in passing: the focus-follows-selection top-nav behavior (bounce source above);
  ensuring BACK/rail/detail focus stays predictable when entering a section programmatically.

## Constraints (unchanged)

- No new module, no DI migration. App-hoisted navigation stays in MainActivity. Strings only in
  `:core:designsystem` (de + en). Keep `detekt` baseline from growing without justification (adding params
  to the already-god `SettingsRoute` / `ProviderSettingsPanel` shifts their baseline signatures — expect a
  `detektBaseline` regen).
