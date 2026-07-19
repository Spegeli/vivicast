# Settings Navigation & Deep-Links — Problem Notes + Plan (reference only)

> Status: **NOT started — reference/scoping only.** Captured after the Home rework, where a Home button
> was meant to deep-link straight into the Settings *add-playlist form*. Section-landing works; opening a
> sub-view (the add-provider editor) does not, because it fights the Settings focus/remount flow. To be
> tackled in a dedicated pass. No code until then.

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
