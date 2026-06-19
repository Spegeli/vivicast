# ViviCast Plan

## Roadmap Direction

`docs/PLAN.md` now tracks ViviCast as sequential delivery phases instead of a flat later-list backlog. Work should stay focused inside the active phase, with compile checkpoints only after structural changes and a full emulator validation pass at the end of each phase.

The roadmap below is aligned with:

- `docs/architecture.md`
- `docs/tv-design-direction.md`
- `docs/brand-direction.md`
- the current Android TV codebase

## Current Phase

Phase 4. Fullscreen Player and Overlay

## Current Status

### Phase 1 - Android TV foundation

Status: complete.

Delivered so far:

- Local Windows/Android setup documentation and helper scripts.
- Core Android multi-module project structure.
- Room-backed playlist, channel, EPG, favorites, and recents foundation.
- M3U URL import.
- Xtream live TV import.
- Multiple providers/playlists.
- Provider management moved into Settings.
- XMLTV import and reusable EPG source management.
- First live TV browser with provider/category filtering.
- Preview player and now/next programme details.
- Favorites and recent channels wired through Room.
- First TV guide screen with provider/category filters and time shifting.
- Media3 playback with TextureView compatibility for the Xiaomi Mi Smart TV 4S.
- Dedicated Settings workspace with General, Providers, EPG, Appearance, Playback, Remote control, and About sections.
- Privacy-safe source display with masked URLs/hosts in Settings.
- Hide/show primary navigation and denser TV-focused browsing layout.

### Phase 2 - Complete Settings

Status: complete.

Delivered:

- Persisted General, Providers, EPG, Appearance, Playback, Remote control, and About settings.
- Complete provider add/edit/delete/refresh, content-scope, group, EPG assignment, advanced option, and local telemetry baseline.
- Reusable global/provider EPG sources with assignment priority, timing policy, due checks, manual refresh, and diagnostics.
- State-driven Provider and EPG presentation models with populated and empty Compose Previews.
- Provider overview regrouped into setup and configured-provider areas with readable health/scope badges.
- EPG regrouped into sources plus progressive `Assignments` / `Update policy` workspaces.
- Provider editor made vertically scrollable, diagnostics collapsed by default, and remote-first initial focus moved away from text input.
- URL paths masked in non-editing Settings surfaces.
- Stable EPG selection and eager hydration of persisted provider settings, sync state, and EPG assignments.
- Emulator validation at 1920x1080 for Provider overview, EPG assignments, EPG policy, provider editor scrolling, Back handling, focus restoration, and crash-free startup.
- Providers and EPG now share one full-width list-detail contract: overview rail first, rail hidden in details, explicit selected sub-pages, and overview restoration through `Back` or left-edge navigation.
- Provider details are split into `Configuration`, `Groups`, `EPG`, and `Maintenance` instead of one mixed popup surface.
- General startup behavior is now fixed to Live TV when recent-channel resume is disabled; the user no longer configures a separate start destination.
- Appearance, Playback, and Remote control now use grouped Settings sections instead of long flat lists.
- Provider and EPG overview cards are now visually closer: compact summaries, reduced low-signal counters, stronger focus clarity, and aligned action placement.
- Provider group management now supports `Show all`, `Hide all`, and `Reset order`, and both provider-group and provider-EPG detail areas use the available height instead of fixed shallow panels.

## Active Phase

### Phase 3 - Movies and Series

Goal: import and browse provider-backed Movies and Series with TV-native detail and playback flows.

Status: complete.

Delivered:

- Primary navigation now uses the fixed product order `Search`, `Live TV`, `Movies`, `Series`, `Settings`; Home/Favorites/Recents/Guide are no longer exposed on the main rail.
- Provider-level Movies/Series scope is persisted and visible in Settings.
- Room now stores movie categories, movies, series categories, series, seasons, episodes, movie progress, and episode progress.
- Xtream refresh now imports live TV plus VOD metadata/content into the shared Room VOD library with extended provider sync telemetry.
- Conservative M3U VOD classification is now wired into refresh for URL and file providers without broadly misclassifying ordinary live streams.
- Debug-only demo import now seeds both live and VOD sample content, and the `demo://vivicast` provider can refresh locally without external credentials.
- Movies and Series now use TV-native list/detail surfaces with previewable empty and populated states, provider labels, season selection, episode lists, and embedded playback details.
- Movie playback and episode playback now run through the shared Media3 player path with persisted resume progress.
- VOD library empty/loading/error states are now explicit, and startup refresh now waits for hydrated provider settings so Movies/Series do not stay empty after relaunch because of a settings race.
- Search, Movies, and Series now have an explicit TV back contract: `Back` reveals the primary rail first and then returns to Live TV instead of falling through to an app-exit path.
- `core:network` now has JVM unit coverage for Xtream VOD stream, series list, and series-detail parsing/URL mapping.
- `core:domain` now has JVM coverage for conservative M3U VOD classification so live streams stay out of Movies/Series while movie and episode patterns still import.
- Emulator validation completed for fixed primary navigation, Movies library import, Series detail browsing, left-edge navigation back to the main rail, movie playback/resume, and episode playback.
- Movies and Series library rows now show one poster slot before the title, keep the details pane poster-free, and group entries by provider for clearer library separation.
- Provider setup now auto-detects Xtream-style `get.php?username=...&password=...` playlist URLs from the M3U URL flow and imports them as Xtream accounts instead of storing them as plain M3U URL providers.
- Xtream-backed Movies/Series now align with the visible provider toggle: Xtream providers default to `Live + VOD metadata`, and enabling Movies/Series no longer silently leaves the provider on a live-only Xtream scope.

Completion criteria:

- Xtream and conservatively classified M3U content provide usable Movies and Series destinations.
- Movie and episode playback works with persisted progress.
- Primary navigation matches the fixed product direction and contains no Home, Favorites, Recents, or Guide destination.
- Movies and Series remain fully usable with D-pad, `OK`, `Back`, and left-edge navigation.

## Next Phases

### Phase 4 - Fullscreen Player and Overlay

- Move to a true fullscreen player as the primary playback mode.
- `OK` toggles overlay visibility; `Back` closes overlay first.
- `Up` / `Down` zaps live TV channels.
- Overlay shows channel logo, channel number, programme, time range, progress, next programme, and technical stream badges.
- Add quick actions for EPG, recents, favorites, audio, and subtitles.
- Detect and switch Media3 audio/subtitle tracks.
- Handle buffering, errors, and retry states consistently.
- Preserve TextureView compatibility for the Xiaomi TV.

Groundwork already present in code:

- The current watch panel already uses a TextureView-backed Media3 player path.
- A first in-player hero overlay already shows playback state, clock, channel identity, programme text, progress, favorite action, guide access, and recent-channel shortcuts.
- Movies and Series already use the shared Media3 playback path and persisted resume progress, so fullscreen player work can now target both live TV and VOD together.
- This is not yet the final fullscreen playback model and does not yet cover track switching or the full remote-control contract for the phase.

Completion criteria:

- Live TV and VOD both work in fullscreen with track selection and reliable D-pad control.

### Phase 5 - EPG and Live TV Navigation

- Build a full time-grid guide with a current-time indicator.
- Keep preview and programme detail panels focus-driven.
- Support fast vertical and horizontal movement for large EPG datasets.
- Add jumps to now, previous/next time window, and day.
- Make the guide reachable from both Live TV and the player overlay.
- Move favorites and recents into contextual filters or overlay actions instead of primary navigation.
- Only expose catch-up playback when provider and programme actually support it.

Groundwork already present in code:

- A first guide screen already exists with a time grid, provider/category filters, focus-driven programme details, and `-3h` / `Now` / `+3h` window shifting.
- Guide entry from the current player overlay already exists.
- Guide, favorites, and recents already exist as Room-backed/current-shell features, but they now need to finish their move into contextual Live TV or overlay flows instead of relying on legacy shell paths.

Completion criteria:

- Large EPG datasets remain fluent and fully usable by remote control.

### Phase 6 - Global Search

- Put `Search` first in the main navigation.
- Search across live TV channels, movies, and series in one surface.
- Show result type and provider clearly.
- Jump directly to the correct details surface or playback target.
- Keep search fast on local Room-backed datasets.

Groundwork already present in code:

- `Search` is already the first primary-rail destination.
- The shell route already exists as a placeholder surface, but it does not yet query the shared Room-backed libraries.

Completion criteria:

- One search query can find content across all three content areas.

### Phase 7 - First Run and Source Setup

- Fresh installs open a compact setup flow.
- Offer M3U URL, Xtream login, and file import.
- Show that ViviCast does not provide channels or playlists.
- Support TV keyboard, remote text entry, and paste.
- `Back` moves one step at a time and should not accidentally exit setup.
- Open Live TV after a successful import.
- Skip first-run when providers already exist.

Current mismatch to replace:

- The app currently auto-imports a demo playlist on empty data instead of showing a proper first-run setup flow.
- That demo auto-import is development-only behavior and must not remain the production first-run path.

Current development behavior:

- Debug builds may still auto-import the demo playlist when no providers exist.
- Non-debug behavior should move through the dedicated first-run source setup instead of any automatic demo content import.

Completion criteria:

- A fresh install can be configured with the remote alone.

### Phase 8 - Design System and Branding

- Move colors, typography, spacing, shape, and focus styling out of `MainActivity.kt` into reusable theme tokens.
- Normalize surfaces onto a consistent text and focus system.
- Add the ViviCast logo as transparent app resources.
- Add launcher, round, splash, and Android TV banner assets.
- Keep cyan as the accent, not the whole UI.
- Avoid decorative gradients, excessive glow, and unnecessary card styling.

Current groundwork and gap:

- The TV app already has a consistent dark/cyan visual direction and repeated focus styling.
- Those tokens still live mostly inside `MainActivity.kt` instead of a reusable design-system layer.

Completion criteria:

- App UI, launcher assets, and TV entry surfaces have a consistent ViviCast identity.

### Phase 9 - Android TV MVP Hardening

- Full D-pad, `OK`, `Back`, and long-press validation.
- Remove focus traps and invisible focus targets.
- Cover parser, database, EPG, provider, and player failure paths.
- Validate large M3U, Xtream, and XMLTV imports.
- Verify restart, persistence, and last-channel restoration.
- Review performance for channel lists, VOD, and EPG.
- Use the emulator as the standard validation target.
- Use the Xiaomi Mi Smart TV 4S only for explicitly approved acceptance checks.

Completion criteria:

- Stable sideload-ready Android TV MVP.

### Phase 10 - Mobile and Tablet

- Reuse shared core modules.
- Build dedicated touch-first phone and tablet UI.
- Support portrait, landscape, picture-in-picture, and background playback.
- Add tablet-specific multi-column layouts.
- Do not shrink the TV layout onto touch devices.

## Data Model and Interface Direction

- Providers need separate Live TV and VOD capabilities, sync state, and richer connection options.
- Room now stores movies, series, seasons, episodes, categories, and playback progress alongside live TV data.
- The player abstraction now exposes live/VOD playback identity, position/duration, first audio/subtitle tracks, track selection, first recoverable-error retry behavior, and first session-level playback telemetry hooks, and still needs deeper fullscreen controls later.
- Search needs a shared repository across channels, movies, and series.
- Start behavior should use the persisted recent-channel record and fall back to the Live TV browser when that channel no longer exists.

## Build and Test Strategy

- Use a hybrid UI loop: Compose Preview first for visual structure and state coverage, then emulator validation for TV interaction and integration.
- Keep screen composables state-driven and previewable; isolate Activity, Room, Media3, network, and navigation wiring from presentational UI where practical.
- Add representative Preview states for normal content and relevant empty, loading, error, long-text, and dense-data cases.
- Use Android Studio Interactive Preview or Live Edit for fast visual iteration when useful.
- Do not build or reinstall after every small UI edit.
- Use compile checkpoints after structural UI, database, module-boundary, navigation, or player changes.
- Run one full debug build at the end of each phase.
- Then run one full emulator validation pass against that phase's completion criteria.
- Batch follow-up fixes, then re-run the full phase validation.
- Treat Preview as visual validation only. D-pad focus, key routing, Back behavior, navigation, dialogs, persistence, playback, and database behavior require the Android TV emulator.
- Treat obvious TV-UX failures in active-phase screens as active work, not as automatic deferrals to the later branding/design-system phase.
- Use the physical TV only when explicitly requested.

## Working Rules

- TV-first decisions win unless they block shared-core architecture.
- Settings is the home for provider configuration.
- Start emulator testing through `scripts\start-tv-emulator.ps1`.
- Correct Android TV AVD: `ViviCast_AndroidTV_API36`.
- Avoid `ViviCast_TV_1080p_API36` for normal development because it is the Google TV/login setup AVD.
- Use Android Studio Compose Preview as the default visual iteration surface for previewable UI.
- Install and test APKs in the emulator by default.
- Install APKs on the physical Android TV only when the user explicitly asks for it.
- Keep this plan current when the active phase or completion state changes.

## Product Assumptions

- Default start destination is Live TV.
- "Resume last channel on start" should immediately reopen the most recent valid stream when enabled.
- Without that option enabled, app start should not auto-play a stream.
- There is no separate home screen in the target MVP direction.
- There will never be a separate Home section in the product navigation.
- Main navigation target order is `Search`, `Live TV`, `Movies`, `Series`, `Settings`.
- Guide, favorites, and recents are contextual surfaces, not long-term primary navigation items.
- Movies and Series come immediately after the Settings phase.
- M3U series recognition stays conservative.
- Branding work finishes before final Android TV hardening.

## Last Updated

2026-06-19
