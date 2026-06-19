# ViviCast Android TV Design Direction

This document captures the first visual and interaction direction for ViviCast on Android TV. It is informed by public IPTV player screenshots and Android TV conventions, but ViviCast must use its own layouts, styling, wording, icons, and assets.

## Design Goals

- Feel like a fast TV appliance, not a mobile app stretched onto a television.
- Keep the video experience close to the surface: playback, zapping, channel context, and EPG should never feel buried.
- Make every screen usable with D-pad, OK, Back, and long-press patterns.
- Prefer dense, calm information layouts over decorative cards or marketing-style composition.
- Use high contrast and predictable focus states so the app is readable from a sofa distance.

## Visual Direction

- Background: near-black charcoal surfaces with subtle separation between content zones.
- Text: white primary text, muted grey-blue secondary text, no thin low-contrast labels for critical actions.
- Copy: keep on-screen text minimal. Avoid explanatory filler and long helper descriptions when a strong title, current state, or concise value is enough.
- Accent: one vivid focus accent for active rows, selected EPG cells, and primary actions.
- Density: TV-appropriate spacing, but enough rows visible to support fast browsing.
- Shape: restrained 4-8 dp radii for panels and rows; avoid pill-heavy controls except where Android TV conventions expect them.
- Motion: quick focus scale or glow, short overlay fades, no decorative background animation.

## Core TV Screens

## Reference Patterns From TV Testing

The latest Android TV reference photos confirm the target direction for ViviCast. They should be treated as product inspiration and UX validation, not as a layout to copy exactly.

Key patterns to adopt:

- Use a persistent left navigation rail that is either fully visible or fully hidden based on context; do not rely on an icon-only collapsed intermediate state.
- Long-term main destinations should be `Search`, `Live TV`, `Movies`, `Series`, and `Settings`.
- In Live TV browsing, support a three-zone layout: provider/category list, channel list, preview/details.
- In compact guide mode, keep channel rows visible while showing the selected channel schedule and programme details.
- In full EPG mode, use a dense time grid with channel logos/numbers on the left, programme cells on the right, and selected programme details above.
- Use the active video or preview as background context behind quick overlay screens when playback exists.
- Keep Settings as a dedicated full working surface by default, similar to the current ViviCast screen, with clear sub-sections inside it.
- Reserve right-side drawer panels for optional in-player quick settings or narrow edit flows later.
- Make settings rows large, focusable, and scan-friendly, with toggles for binary options and subtext for current values.
- Apply that toggle rule consistently in every Settings section: any true/false option should render as a visible toggle, not only as a clickable value row.
- Keep settings copy compact. Prefer strong row titles and direct state/value labels over descriptive paragraphs unless the user would otherwise not understand the action.
- Do not postpone obvious Settings information-architecture failures until the later design-system phase. If a Settings area is functionally complete but still reads like an internal tools panel, it should be structurally cleaned up in the active product phase.
- Treat provider configuration as a deep settings area: provider enablement, display name, EPG sources, logo priority, catch-up/archive, user-agent, Xtream parameters, group management, and update options.
- Treat EPG sources as reusable entities: they can exist globally, then be assigned to one or more providers with priority.
- Add group management as a provider-level feature: sort groups, show/hide groups, reorder groups.
- Fullscreen player overlay should show current programme, channel logo, time range, progress, technical badges, next programme, and quick action tiles such as EPG and history.
- The current non-final overlay can already surface first audio/subtitle track actions and first pause/stop/retry transport actions when the active stream exposes them.

ViviCast-specific adaptations:

- Keep ViviCast branding darker, cleaner, and more cyan-accented than the reference.
- Avoid exact row sizes, exact panel positions, and exact wording from any reference app.
- Prefer privacy-safe display of provider data: show host names and masked credentials unless the user is editing a field.
- Start with TV and EPG essentials, then expand settings depth once core playback and guide navigation are stable.

Current implementation note:

- The current app already has a hide/show left rail, a three-zone Live TV browser, a guide screen, and shared live/VOD playback surfaces.
- Provider and EPG Settings now share one list-detail contract with overview-first navigation, hidden rail in deep detail, and restoration through `Back` or left-edge exit.
- Appearance, Playback, and Remote control already use grouped Settings sections instead of long flat lists.
- Provider deep-detail work is already split into `Configuration`, `Groups`, `EPG`, and `Maintenance` instead of a single mixed popup.
- Provider and EPG overview cards are already visually closer: compact summaries, aligned action placement, and clearer non-focus versus focus states.
- Movies and Series now already use the long-term primary rail, provider-grouped list/detail browsing, poster-first rows, season and episode detail handling, and embedded playback/resume panels.
- Playback and remote-control settings now cover the current Phase-2 baseline; richer fullscreen-player and large-guide behavior still belongs to later phases.
- `Favorites`, `Recently watched`, and `Guide` still exist as legacy/internal shell features, but the production-facing primary rail is already `Search`, `Live TV`, `Movies`, `Series`, `Settings`.

### 1. First Run / Source Setup

Purpose: get the user to an imported source with as little friction as possible.

Layout:

- Centered setup panel on dark background.
- Primary action: Add playlist.
- Secondary action: Settings.
- Import choices: M3U URL, Xtream login, file import later.
- Clear legal/product text: ViviCast does not provide channels or playlists.

Interaction:

- Initial focus on Add playlist.
- Back does not exit accidentally during import entry; it moves back one step.
- Remote text entry should support paste/keyboard and Android TV IME.

### 2. Live TV Browser

Purpose: browse and zap channels quickly.

Layout:

- Hide/show navigation rail on the left. It should appear when intentionally entered and disappear again when content needs the space.
- Channel list in the center.
- Preview or current player region on the right when not fullscreen.
- Now/next metadata shown inline when EPG is available.

Interaction:

- Up/down moves channels.
- OK starts playback.
- Left opens or focuses the navigation rail.
- Right moves back into channels or to preview/player actions depending on focus.
- Back returns to previous navigation level.

### 3. Player

Purpose: watch with minimal UI interruption.

Layout:

- Today: preview/watch panel with integrated overlay.
- Target: fullscreen video by default.
- Overlay appears on OK, D-pad, or playback state changes.
- Bottom overlay: channel logo/name, current programme, time range, now/next, progress when available.
- Side or bottom quick actions: audio track, subtitles, favorites, EPG, channel list.

Interaction:

- Up/down zaps channel.
- Left/right opens previous/next EPG or short navigation depending on overlay state.
- OK toggles overlay.
- Back hides overlay first, then exits player.

### 4. EPG Guide

Purpose: the signature TV guide surface.

Layout:

- Vertical channel list.
- Horizontal time grid.
- Top preview/video tile with programme details.
- Current time indicator line.
- Selected programme cell has a strong focus treatment.

Interaction:

- D-pad moves through the grid.
- OK starts the focused programme/channel.
- Back returns to player or live browser.
- Fast scroll support is required before large EPG imports become useful.

## Settings Structure

Initial settings groups:

- General
- Providers
- EPG
- Appearance
- Playback
- Remote control
- Parental controls later
- About

Settings should use TV-native list rows, clear selected states, and short descriptions. The default layout is a full Settings workspace with sub-section navigation, not a mandatory right-edge drawer. Avoid deep nesting until the feature set requires it.
Across all Settings sections, binary options should always use visible toggle controls so the current state is readable at a glance from TV distance.
Descriptions in Settings should be the exception, not the default. If a row or panel works with a clear title and value alone, omit extra explanatory text.
Providers and EPG in particular should prefer progressive disclosure: routine setup first, advanced controls second, destructive or maintenance actions last.
Large provider and EPG editors should be full Settings detail workspaces, not modal-looking popups. Their overview/detail transition, selected sub-page treatment, and exit behavior must remain consistent.

### UI iteration and Preview coverage

- Build revised TV surfaces as state-driven composables with device-independent sample data.
- Use Compose Preview to compare hierarchy, grouping, spacing, copy, empty/loading/error states, long values, and dense lists before installing an APK.
- Provider and EPG Preview fixtures should include at least: populated healthy data, no configured data, active refresh/loading, failure state, long provider/source names, and dense multi-provider data.
- Preview is not acceptance testing for TV input. Validate focus order, D-pad traversal, key routing, Back behavior, dialogs, scrolling, and real data integration in `ViviCast_AndroidTV_API36` after the visual pass.
- Avoid composables that directly fetch from Room/network or require an Activity/player merely to render; keep those dependencies in container wiring.

### Providers

Provider setup and management belongs in Settings, not in the Live TV browser.

The Providers screen should allow the user to:

- Add a provider.
- Choose the connection type: M3U or Xtream.
- Enable or disable a provider without deleting it.
- Set a custom display name.
- Enter or update the M3U URL/file reference or Xtream server credentials.
- Attach or update an EPG URL when needed.
- Assign one or more EPG sources with priority.
- Choose logo priority: playlist first, EPG first, or later local folder.
- Configure Xtream-specific options like output format and TV/VOD inclusion later.
- Configure provider update interval and update-on-start behavior later.
- Manage channel groups and hidden groups.
- Show all groups, hide all groups, and reset custom group order quickly.
- Refresh/import the provider manually.
- See last sync status, channel count, and error state.
- Delete or disable a provider.

The Live TV browser may still show compact provider chips for fast filtering between imported providers. Those chips are source-switching controls only; they should not expose provider setup forms.

Current implementation note:

- Add, rename, delete, enable/disable, stored Live TV and Movies/Series scope flags, logo priority, hidden groups, group reordering, refresh preferences, custom user-agent, Xtream output format, Xtream API scope, editable file-based source references, direct EPG URL handling, global EPG sources, provider-to-EPG assignments, and persisted EPG import timing preferences already exist in persisted form.
- The M3U URL flow can already auto-detect Xtream-style playlist URLs and route them into Xtream provider setup instead of saving them as plain M3U URL providers.
- Editing the original M3U/Xtream connection details and richer local provider sync status now exist, including refresh/EPG timing, counters, streaks, and error telemetry; broader Xtream/provider options are still open.
- Provider and EPG overview surfaces now use previewable display-state models, clearer grouping, stable selection/focus restoration, compact card summaries, and a scrollable provider editor with diagnostics hidden until requested.

## MVP Design Order

1. Stabilize focus styles and typography tokens.
2. Continue refining the implemented three-zone Live TV Browser.
3. Continue refining the implemented hide/show navigation rail.
4. Replace the current temporary import dialogs and demo auto-import with a proper first-run setup flow.
5. Evolve the current watch panel into the final fullscreen player overlay behavior.
6. Continue compacting Provider and EPG deep-detail layouts around the existing list-detail contract.
7. Evolve the current EPG guide toward large-data navigation and full remote efficiency.
8. Keep Settings cleanup inside active product phases when a surface is functionally complete but still visually noisy.
9. Move repeated colors, spacing, and focus rules into reusable theme/design-system tokens during the dedicated branding/system phase.
10. Keep import/onboarding and fullscreen playback as the next major interaction rewrites after the current library/settings baseline.

## Non-Goals

- Do not copy TiviMate layouts, assets, names, or exact styling.
- Do not design mobile/tablet by shrinking the TV UI.
- Do not start with a marketing landing page.
- Do not add a separate Home section.
- Do not add decorative blobs, gradients, or oversized hero sections.
