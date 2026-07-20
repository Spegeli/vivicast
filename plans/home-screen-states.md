# Home Screen — Ist/Soll Research + Plan

> Status: **IMPLEMENTED — gates green (assembleDebug, detekt, all unit tests). STILL OPEN (not closed).**
> On-device/emulator verified: no-playlist / all-disabled / empty-catalog empty states (+ 2-button / open-
> settings actions), per-row 3-state (only-live playlist → Sender CTA row, Movies/Series hidden; only-
> movies/series analog), recent-channels row with the compact channel card. Content-row cards for Filme/
> Serien not yet visually verified (the Movies/Series pages are being finished separately).
>
> **Empty-state buttons + deep-links — DONE (all three empty states):**
> - `[Wiedergabeliste hinzufügen]` (no-playlist) now **opens the add-provider form directly** — not just the
>   Playlists section. Cancel/Save return to the Playlists overview with the Add row / the newly-created card
>   focused.
> - `[Einstellungen öffnen]` (disabled / empty-catalog) lands on the **Playlists overview**; `[Einstellungen]`
>   (no-playlist) lands on Settings **General**.
> - Built on the inner Settings NavHost via a generic `SettingsEntryAction` deep-link + a fresh per-entry
>   inner controller (no stale section flashing under the deep-linked title). Full resolution + the retired
>   route-bounce/remount approach: `plans/archive/settings-navigation-deeplinks.md`.
> - _Residual (own plan):_ the add-editor's brief **open latency** (Playlists overview visible while the heavy
>   `ProviderEditorScreen` composes) → `plans/settings-add-editor-open-latency.md`.
>
> **Open follow-ups (tracked, not blocking):**
> - **Channel card aesthetics:** height fixed (was stretching); the logo-tile look is not final — refine later.
> - **Filme/Serien rows:** verify the poster cards + series-centric resume/advancement once those pages are done.
>
> Docs (01-home, 00-home wireframe, list-grid-items in `../vivicast-docs`) updated to the new model.
> Scope: the Home screen's behavior across the three playlist states (none / disabled / active) and the
> deviations from the design docs. First of the UI-fix pass (Home → Live-TV → Filme → Serien).

## Sources

- Soll: `../vivicast-docs/design/screens/01-home.md` (verbindlich v4),
  `../vivicast-docs/design/wireframes/00-home.md` (verbindlich v2).
- Ist (code): `feature/home/HomeRoute.kt`, `HomeViewModel.kt`, `HomeUiState.kt`;
  `core/database/dao/PlaybackDao.kt`, `data/playback/RoomPlaybackRepository.kt`;
  `data/provider/RoomProviderRepository.kt`; `data/media/RoomMediaRepository.kt`;
  `core/database/dao/CatalogDao.kt`.

## How Home gets its content (the root of the issues)

- Home content = **playback history only**: `continueItems` (Movie/Episode with progress, `isCompleted=0`)
  + `recentChannels` (watched live channels). Nothing else.
- `observeAllContinueWatching()` / `observeAllRecentChannels()` read **all** `playback_progress` /
  `channel_history` rows — **no active-provider filter**. `getMovie`/`getChannel`/`getEpisode`
  (`CatalogDao`) also do **not** filter by active.
- **Home has no provider knowledge** — `HomeRoute`/`HomeViewModel` take only `playbackRepository` +
  `mediaRepository`, never `providerRepository`. So Home cannot tell "no playlist" from "playlist exists".
- Contrast (the app-wide norm): Live-TV / Filme / Serien lists use `CatalogDao` queries with
  `INNER JOIN providers ON ... AND providers.isActive = 1` → **disabled providers are hidden** there.
  **Home is the exception.**
- Disable = `setProviderEnabled(id,false)` → `isActive=0` + status `Disabled`; **catalog + history kept**.
  Only `deleteProvider` clears catalog/history/progress. So a disabled provider's history rows persist.

## Ist — behavior in the three states

**HomeRoute render logic (current):** a Hero is *always* shown (continue → recent-channel → default
"Home" hero), the two rows show only when non-empty, and **when both rows are empty an `InfoPanel` is
shown in addition** to the default hero.

1. **No playlist at all** — history empty → **double empty**: default Hero (`"Home"` +
   body `home_hero_default_body` + meta `"Keine Wiedergabeliste"` + `[Wiedergabeliste hinzufügen]`) **plus**
   `InfoPanel("Keine Inhalte" / long body / "Leer")`. (Confirmed on-device: matches the Home screenshot.)
2. **One playlist, disabled** — catalog + history kept, no active filter →
   the disabled provider's continue/recent items **still appear on Home** (leak vs. the app norm). If that
   provider has no history, Home falls to the same **double empty** as (1), wrongly saying
   "Keine Wiedergabeliste" although a playlist exists.
3. **One/more active playlists** —
   - with history: rows + hero populate correctly (doc-conform; multiple active → mixed, doc allows it).
   - **without history yet** (fresh, nothing watched): same **double empty** saying
     "Keine Wiedergabeliste" / "Wiedergabeliste hinzufügen" — **wrong**, a playlist is active.

Current strings (designsystem): `home_empty_title="Keine Inhalte"`, `home_empty_body_long="Lege in den
Einstellungen zuerst eine Wiedergabeliste an. …"`, `home_hero_no_playlist="Keine Wiedergabeliste"`,
`home_hero_default_body="Begonnene Filme, Serien und zuletzt gesehene Live-TV-Sender erscheinen hier."`,
`common_empty_badge="Leer"`, `home_recent_channels="Zuletzt gesehene Sender"`.

## Soll — what the docs say

- Layout zones: Top-Nav, **Hero**, Fortsetzen row, Zuletzt-gesehene-Live-TV row.
- Hero shows the **first Fortsetzen** element; if Fortsetzen empty → **first recent channel**; if **both
  empty → the Empty State** (the hero area *becomes* the empty state — not a persistent hero + panel).
- Empty State = **one** thing: `"Noch keine Inhalte"` + `"Füge eine Wiedergabeliste hinzu, um zu starten."`
  + action `[Wiedergabeliste hinzufügen]`.
- Provider-/Update-status is **not** shown on Home (that's a status *section* ban — Einstellungen only).
- Docs do **not** define the "a playlist exists but nothing has been watched yet" case, nor disabled-
  provider filtering for Home. → user decisions needed.

## Deviations (Ist vs Soll)

- **A. Double empty state.** Ist = default Hero + separate `InfoPanel`. Soll = one Empty State. The two even
  duplicate copy ("Begonnene Filme, Serien …" appears in both).
- **B. Hero never collapses.** Ist keeps a persistent "Home" hero; Soll = hero area *becomes* the Empty
  State when both rows are empty.
- **C. Wrong messaging.** "Keine Wiedergabeliste" / "add playlist" is driven by **empty history**, not real
  playlist state — misleads when a playlist exists (active-no-history, or disabled).
- **D. Disabled content leaks.** Disabled provider's continue/recent still show on Home (inconsistent with
  the `isActive=1` norm used everywhere else).
- **E. Minor label.** Row title string `"Zuletzt gesehene Sender"` vs doc `"Zuletzt gesehene Live-TV-Sender"`.

## Decisions (answered by user)

1. **Single empty state** — collapse the doubled hero + InfoPanel into ONE doc-conform Empty State.
2. **Home knows playlist status** — `HomeViewModel` gets active/total provider info from
   `ProviderRepository` so it can distinguish the causes and show the right copy. (Reading provider
   *existence* to pick empty copy ≠ a provider *status section*, so it stays compatible with
   "kein Provider-Status auf Home".)
3. **Filter disabled providers** out of Home Fortsetzen/Zuletzt-gesehen (match the app-wide `isActive=1`
   norm). A disabled playlist contributes nothing to Home.
4. **Active-but-no-history** → hint + Verweis to Live-TV/Filme/Serien, **no** "Wiedergabeliste hinzufügen".

### Concrete Soll — per-row model + global empty fallback

The per-type "go browse" buttons live **inside each row**, not in a global empty state. `hasType(T)` = at
least one **active** playlist has catalog content of type `T` (live/movies/series) — Room `EXISTS` joined
to `providers.isActive = 1` (actual catalog presence, not just the `includeLiveTv/…` import flags).

**Per-row, for each type (Sender / Filme / Serien):**

| Row condition | Render |
|---|---|
| `!hasType(T)` — no active playlist provides this type | **Row hidden** (recommended; no "gibt es nicht" placeholder) |
| `hasType(T)` and nothing watched/in-progress of `T` | Row title + a single CTA button `[Zu Live-TV / Zu Filme / Zu Serien]` → `selectRoute(...)` |
| `hasType(T)` and items exist | Row with cards |

**Global empty state** — only when **no row is shown at all**:

| Detected case | Empty State |
|---|---|
| 0 providers total | "Noch keine Inhalte" / "Füge eine Wiedergabeliste hinzu, um zu starten." + `[Wiedergabeliste hinzufügen]` (→ Settings, **opens add-playlist form**) + `[Einstellungen]` (→ Settings **general**) |
| Providers exist, 0 active | "Wiedergabeliste deaktiviert" / "Aktiviere sie in den Einstellungen." + `[Einstellungen öffnen]` |
| ≥1 active, but **no** type has any content (empty/fresh import) | "Noch keine Inhalte" / "Prüfe deine Wiedergabelisten in den Einstellungen." + `[Einstellungen öffnen]` |

**Navigation targets** (implemented). `[Wiedergabeliste hinzufügen]` (no-playlist):
`openPlaylistSettings(addNew=true)` → `initialSelectedSection="Wiedergabelisten"` +
`entryAction=SettingsEntryAction.AddPlaylist` → the inner Settings NavHost opens `PlaylistEditor` on the
Playlists overview (the old `ProviderSettingsPanel` add-flow was replaced by the inner-NavHost rebuild).
`[Einstellungen öffnen]` (disabled / empty-catalog): `openPlaylistSettings()` → Playlists overview.
`[Einstellungen]` (no-playlist): `onOpenSettings` → Settings General.
`[Zu Live-TV/Filme/Serien]`: `selectRoute("live-tv"/"movies"/"series")`.

## Content structure (rows + hero) — Soll vs. user's open questions

The empty-state work above is settled. The **non-empty** Home layout is a separate open design point the
user raised (hero + which "recently watched" rows). **Current code already matches the docs here** — the
bugs are only in the empty/disabled handling, not the content layout.

**Docs Soll (01-home v4 / wireframe v2):**
- Rows: **"Fortsetzen"** (Filme + Serien **gemischt**, only *in-progress* resume targets; completed items
  don't appear; a series may advance to the next episode) + **"Zuletzt gesehene Live-TV-Sender"** (separate).
- **Hero** = first Fortsetzen element (movie/episode); if Fortsetzen empty → first recent channel; if both
  empty → the Empty State.
- No separate "recently watched movies/series" rows in the docs; movies/series surface only via *Fortsetzen*.

**Ist:** exactly the above — `HomeViewModel` exposes `continueItems` (in-progress movies/episodes) +
`recentChannels`; `HomeContent` renders Hero + the two rows. So the content structure is doc-conform and
correctly bound.

**Resolved (user) — NEW Home content layout (deviates from docs → docs get updated):**
1. **No Hero.** Home is only rows. The `HomeHero`/`HeroPanel` usage is removed.
2. **Three per-type rows**, order **Sender → Filme → Serien**, titles confirmed:
   - **"Zuletzt gesehene Sender"** — recent live channels (`recentChannels`).
   - **"Filme fortsetzen"** — in-progress movies (`continueItems` filtered to `MovieItem`).
   - **"Serien fortsetzen"** — one card **per series** (series poster + series name), resolved to the resume
     target episode (in-progress episode, or the next episode when the last relevant one is completed — V1/V2).
   Each row follows the per-row 3-state model above (hidden if the type isn't in any active playlist / CTA
   button if present-but-unwatched / cards if items exist).
3. **Semantics:** Movies = in-progress only (from `continueItems` / `MovieItem`). Channels = recent
   (`recentChannels`). **Series = series-centric with advancement** → a new **series-continue resolver**
   (group episode history per series across active providers → pick the resume/next episode), not just the
   raw in-progress episode list. Plus `hasType(T)` (catalog presence over active providers) for hide-vs-CTA.

## Card content on Home (user-decided — deviates from list-grid-items.md v8 → docs get updated)

- **Channel Card (Home):** Logo/Fallback with the **Sendername below** (vertical, not the current
  logo-left/text-right row). Nothing else — **no** current-program line, **no** watch-recency, no progress,
  no favorite. Fallback: initial letter when no logo.
- **Poster Card (Home):** Poster/Fallback + **Titel** + a **thin progress bar** that fills left→right by the
  resume fraction. Bar **colour = accent** (user-customisable appearance token), **no percent number**.
  **No** year, **no** rating, **no** favorite badge, no seen-state (completed items don't appear in a
  "fortsetzen" row anyway). Fallback: gradient + title.
- Doc deviation: `list-grid-items.md` lists more fields for both Home cards (current program / year / rating
  / favorite) — the Home variants are trimmed to the above; update the doc accordingly.
- Code impact: `VivicastChannelCard` currently renders logo-left + name + program (Row); Home needs a
  logo-top / name-below variant with no program. `PosterCard` currently shows meta text + rating "-";
  Home needs title + bar-only (no % text, no rating/favorite). Add compact Home variants or params to
  hide those fields — decide during implementation (reuse first).

## Implementation plan (on GO — no code before)

1. **`HomeViewModel`** — inject `ProviderRepository`. `combine` `observeProviders()` with the continue/recent
   flows:
   - active-provider-id set → **filter** movies (`continueItems`/`MovieItem`) and `recentChannels` to active
     providers only;
   - **series** come from the new series-continue resolver (step 2b), also active-filtered;
   - `hasType` flags (`hasLive` / `hasMovies` / `hasSeries`) = catalog presence over active providers →
     drive per-row hide-vs-CTA;
   - `emptyReason` (`NoPlaylist` / `AllDisabled` / `EmptyCatalog`), non-null **only** when no row shows
     (no type present at all): `NoPlaylist` = 0 providers, `AllDisabled` = providers but 0 active,
     `EmptyCatalog` = ≥1 active but every `hasType` false.
2. **Per-type existence** — `CatalogDao` `EXISTS`/`COUNT` queries joined to `providers.isActive = 1` (mirror
   the existing catalog queries), surfaced via `MediaRepository`; consumed by the VM for the `hasType` flags.
2b. **Series-continue resolver** (`:data:playback`) — per series (across active providers) take the latest
   episode playback (completed **or** in-progress); if in-progress → that episode; else if completed and a
   next episode exists (`getNextEpisode`) → the next episode at progress 0; else drop the series. Returns one
   resume target per series (series + episode + progress), most-recent-series first. Needs episode history
   incl. completed (the current `observeAllContinueWatching` is `isCompleted=0` only — add a query/path).
3. **`HomeUiState`** — `movieItems`, `seriesItems` (series + resume episode + progress), `recentChannels`,
   `hasLive`/`hasMovies`/`hasSeries`, `emptyReason: HomeEmptyReason?`. (VM stays string/Compose-free.)
4. **`HomeRoute` / `HomeContent`** — **remove** `HomeHero` + the old default hero + separate `InfoPanel`.
   Render the three rows (order Sender → Filme → Serien) each via the per-row 3-state: hidden when
   `!hasType`; title + CTA button when `hasType` and items empty; cards when items exist. When **all** rows
   are hidden → ONE global Empty State keyed on `emptyReason` (App/DS side): `NoPlaylist` = 2 buttons;
   `AllDisabled` / `EmptyCatalog` = 1 button.
5. **Callbacks / `MainActivity`** — `HomeRoute` gains `onOpenLiveTv/onOpenMovies/onOpenSeries`,
   `onOpenSettings` (general), and `onAddPlaylist` (opens Settings **with the add-playlist form**).
   `HomeViewModelFactory` gains `ProviderRepository`; MainActivity passes `appContainer.providerRepository`
   and wires the callbacks (`selectRoute(...)`, settings-general, settings-add-form).
6. **Settings add-form auto-open — DONE (later, via the inner-NavHost rebuild, not this step's approach).**
   Shipped as a generic `SettingsEntryAction` deep-link: `[Wiedergabeliste hinzufügen]` sends
   `initialSelectedSection="Wiedergabelisten"` + `entryAction=AddPlaylist`; SettingsRoute's inner NavHost opens
   `PlaylistEditor` on the Playlists overview. Cancel/Save return there with the Add row / new card focused.
   (The originally-scoped `ProviderSettingsPanel` signal was superseded — see
   `plans/archive/settings-navigation-deeplinks.md`; residual open-latency in
   `plans/settings-add-editor-open-latency.md`.)
7. **Strings** — `:core:designsystem` (`values` + `values-en`): row titles, the three empty-state variants,
   the buttons. Retire now-unused hero strings (`home_hero_*`) if nothing else uses them.
8. **Docs** — update `../vivicast-docs/design/screens/01-home.md` + `wireframes/00-home.md` to the new model
   (no hero, per-type rows, three empty variants + button rules, disabled-provider filtering). Local doc
   edit; confirm per CLAUDE.md.
9. **Tests** — `HomeViewModelTest`: active-provider filtering, movie/series/channel split, each `emptyReason`,
   `hasType` → hide-vs-CTA, no-flash-before-load. Series-continue resolver: in-progress episode, advance to
   next after completion, drop when no next, one entry per series (dedupe).
10. **Gates** — `detekt`, `assembleDebug`, `test`; then on-device visual pass of every state (physical TV).

## Validation — extra scenarios found (some need a decision)

**Decided (user):**
- **V1 — Serien row = series-centric (doc).** One card **per series** (series poster + series name) showing
  the resume episode's progress; no duplicate series. Replaces the current episode-centric mapping.
- **V2 — Next-episode advancement = implement now.** After the latest relevant episode is completed and a
  next episode exists, the series stays on Home pointing at that next episode (progress 0). Needs a new
  series-continue resolver (episode history per series incl. completed → resume/next), not just the raw
  in-progress list.

**Will handle without a decision (calling out so they're not forgotten):**
- **V3 — Loading flash guard.** Home flows (providers + history) resolve async; without a guard Home would
  briefly flash a global Empty State before data loads. Add an "undetermined/loading" state (skeleton rows
  per doc, or blank) so an Empty State only renders **after** first load. (Same class of issue as the splash
  flash — treat carefully.)
- **V4 — Initial focus + D-pad traversal** for the new layout (no hero; rows may be cards, a single CTA
  button, or the global empty state). Rule: focus the first visible interactive element (first row's first
  card → its CTA if a CTA row → the primary button in the empty state). UP/DOWN across mixed card/CTA rows
  must work. Redefine in the doc.
- **V5 — Order + cap.** Rows ordered by recency (most-recent first); confirm `observeAllContinueWatching`
  orders by `lastWatchedAt DESC` across providers. Optional per-row cap (recent channels already cap at 12);
  default the movie/series rows to a similar cap.
- **V6 — Active-but-problem status.** Filtering keys on `isActive` only (not `status`), so an active provider
  that is Refreshing / ConnectionError / Expired still contributes its (stale) catalog to Home — consistent
  with the rest of the app (which also filters `isActive=1`, not status). No change; noted as intended.

## Notes / constraints

- No code until explicit GO. No new module, no DI migration.
- If C/D are approved: `HomeViewModel` gains an active-provider count/flow via `ProviderRepository`
  (constructor-injected through the existing factory); rendering logic in `HomeRoute` picks the empty
  variant. New/changed strings go to `:core:designsystem` (de + en) only.
- Any doc gap we agree to fill (the "active-no-history" case, disabled filtering) means updating
  `../vivicast-docs/design/screens/01-home.md` + `wireframes/00-home.md` — doc edits are local-only and
  need user confirmation per CLAUDE.md.
