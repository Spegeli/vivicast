# D-Pad List Reorder — Reusable Component (Research + Plan)

> Status: **implemented (logo priority) — static gates green, pending emulator verification**. No commit.
> Built: `LogoSource`/CSV model (domain) + `normalizeLogoPriority`; SQL order-agnostic `effectiveLogoUrl`
> + `getLogoCandidates` + worker candidate row (core/database); App resolver order-walk + skip-empty
> (PlaybackOrchestration); worker order-winner prefetch; `VivicastReorderList` grab-and-move component
> (designsystem) + hint strings; logo popup → reorder list, order-summary label, Local always shown,
> `localLogosConfigured` plumbing removed (ProviderEditor/ProviderSettingsPanel/SettingsRoute); parser
> unit test; RoomMediaRepository logo test updated to the new order-agnostic projection.
> Green: `assembleDebug`, `detekt`, unit tests, androidTest compile. NOT yet driven on an emulator.
> Companion to `plans/d10-channel-group-management.md` (this is the reorder-UX piece D10 flagged as
> "No precedent in the app"). Also the missing UI for EPG-source priority ordering.

## Goal

One reusable D-Pad-driven reorder mechanism, used later in several places:
1. **Logo priority per playlist** — order the 3 logo sources (Playlist / EPG / Local folder).
   FIRST target (user-chosen). Replaces the current single-choice radio with a reorderable list,
   hosted **inline in the existing logo-priority popup** (not a separate overlay).
2. EPG sources per playlist — order by priority (`ProviderEpgSourceEntity.priority` already exists).
3. Channel groups per playlist — show/hide + sort (D10, `CategoryEntity`).
4. Channels — manual order (no order field yet).

Design the component once, standalone + **embeddable** (usable inline in a dialog OR wrapped in a
dedicated overlay for long lists). Remaining wiring sites decided later by user.

## Why grab-and-move (decision record)

- **Platform-native**: Android TV home screen itself uses pick-up → D-Pad move → center-drop.
- **Validated by references**: 3 of 5 scanned IPTV apps (OwnTV, AerioTV, StreamVault) use exactly this
  D-Pad idiom. OwnTV `MoveOrderOverlay.kt` = cleanest full-screen overlay + auto-scroll.
- **No library fits**: every Compose reorder lib (Calvin-LL/Reorderable, aclassen/ComposeReorderable)
  is touch/mouse-drag only, useless on a remote. Self-built is *less* code than integrating one.
- **Animation is free**: foundation `LazyColumn` + `Modifier.animateItem()` (Compose standard, replaces
  deprecated `animateItemPlacement`) slides neighbours on index change. We only drive index swaps.
- **Zero new dependency.**

## UX flow (confirmed with user)

1. A list panel gets a **"Sortieren"** action (a `VivicastSettingsRow` next to "Hinzufügen").
2. Click → opens a **dedicated reorder overlay** (`VivicastDialog`, Wide) showing the same list.
3. Focus a row, **OK** → row is "picked up": accent border + `↕`, rest dims. This is reorder mode.
4. **▲ / ▼** move the row one position; list auto-scrolls to follow; neighbours slide (`animateItem`).
   - **Hold ▲/▼** → step accelerates (key-repeat count raises step cadence). Long-list nav.
   - (Deferred, trivial add: **◄/► = ±10** coarse jump. Not built initially.)
5. **OK** → drop + persist new order. **Back** → cancel, no write, order unchanged.

Nobody in the 5 reference apps solves "move item 100 → 50 without 50 presses" — hold-accelerate is our
answer; ◄/► ±10 stays in reserve.

## Component shape (reuse-first)

Stateless composable in `:core:designsystem` (new file, e.g. `VivicastReorder.kt`). Embeddable — the
core is a plain list you can drop **inline** in any dialog (logo priority) OR wrap in a dedicated
overlay (long lists):

```
// core, embed anywhere
VivicastReorderList(
    items: List<ReorderItem>,            // id + label (+ optional trailing/icon)
    onCommit: (orderedIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
)
// optional convenience wrapper for long lists — DEFERRED (YAGNI), built when a long-list site needs it
VivicastReorderOverlay(title, items, onCommit, onCancel)   // = VivicastDialog + VivicastReorderList
```

**First cut = inline `VivicastReorderList` only.** No overlay wrapper yet (no long-list site uses it
today). Core stays stateless/list-based so the wrapper is a later add, not a refactor.

- Owns only **local UI state**: pickup index, move-mode flag, focus, working copy of order.
  (Allowed by architecture rules — focus/D-Pad/dialog state may live in composables.)
- No repo, no ViewModel, no Context, no localized business strings inside the component.
- Reuse existing primitives: `VivicastDialog` / `VivicastDialogWidth` / `VivicastDialogActions`,
  `FocusPanel` / `VivicastFocusSurface`, `animateItem()`.
- Key handling mirrors `LiveTvRoute.moveBrowserChannel` (onPreviewKeyEvent, index±1, signal-counter
  focus idiom) and OwnTV `MoveOrderOverlay` structure.

Each call site supplies `items` from its UiState and wires `onCommit` to a VM persist method.

## Architecture placement

- Reorder **interaction** = local composable UI state (in the designsystem component). ✅ allowed.
- Reorder **persistence** = `:data` repository method → `suspend Result` on the feature ViewModel →
  panel invokes via `scope.launch { … }`, German strings stay panel-side. Matches
  `SETTINGS-APP-HOISTED-DECISIONS.md` (line 28 "link/unlink/**move**" already sanctioned home).
- Persist method mirrors `SecureEpgSourceRepository.rewritePriorities` (renumber in
  `database.withTransaction`, temp-offset to dodge the unique index). One unit/android test for it.
- Strings ("Sortieren", "An Position ablegen", hints) → **both** designsystem locale files only.
- Gates green: `detekt` (baseline 36, don't grow), `assembleDebug`, `test`.

## Data readiness per wiring site (effort tiers)

| Site | Order field | DB migration | Tier |
|---|---|---|---|
| EPG sources / playlist | `ProviderEpgSourceEntity.priority` (+ renumber logic exists) | none | **Low** — natural proof-of-concept |
| Channel groups (D10) | `CategoryEntity.sortOrder`/`isHidden` (clobbered on import) | fix import OR side table (v16→v17) | Medium |
| Channels manual | none | new column + v16→v17 + schema/migration tests | High |

## First target: Logo priority per playlist (user-chosen)

Rework the existing per-playlist logo-priority feature from single-choice to user-ordered.

Current (read from code):
- `logoPriority: String` on the provider = one of `LOGO_PRIORITY_PLAYLIST`/`_EPG`/`_LOCAL`
  (`ProviderConfigurationModels.kt:101-103`, default playlist).
- Dialog `SettingsChoiceDialog` (single-select radio), opened from `ProviderEditor.kt:369`.
  Labels "Logos aus … bevorzugen" (`strings.xml:721-723`).
- The one value implies a FIXED 3-way order in the resolver:
  `resolveChannelLogoModel` (`PlaybackOrchestration.kt:127-150`) handles Local app-side; Playlist-vs-EPG
  is resolved in SQL (`CatalogDao` effectiveLogoUrl). Chains: playlist→[P,E,L], epg→[E,P,L], local→[L,P,E].
- Local only offered when a local logos folder is configured (`localLogosConfigured`,
  `ProviderEditor.kt:375`).

Target:
- Same popup, but the 3 sources become a reorderable list (grab-and-move). Free permutation per playlist.
  Rename labels to plain source names ("Playlist" / "EPG" / "Lokaler Ordner"). Title stays "Logo-Priorität".
- **Local row ALWAYS shown** (drop the `localLogosConfigured` gate at `ProviderEditor.kt:375`) — always
  3 rows, whether or not a local folder is configured.

Two real work items (UI is the small part):
1. **Model** — `logoPriority: String` single value → an **ordered value** (`List<LogoSource>`) on the
   provider. Storage: keep the existing `TEXT` column, store the order as CSV of enum names
   (`"epg,local,playlist"`).
   - **Why a value, not a join table:** the 3 logo sources are a closed **enum** (kinds, not entities —
     no identity, no own settings, never queried/joined independently). An ordered value is the correct
     model; a `provider_logo_source(providerId, source, position)` table would over-normalize an
     enum-permutation into relational rows. Contrast: EPG sources ARE entities → the existing
     `provider_epg_sources(priority)` join table is right *there*, wrong *here*. Match storage to the
     data's nature.
   - **The quality upgrade (the "better", not just "simpler"):** introduce a real `LogoSource` enum to
     replace the loose `LOGO_PRIORITY_*` string constants; `List<LogoSource>` in the config model;
     deterministic serialize; **validate on read** — all sources present, deduped, legacy single token
     expands to its historical fixed order. No DB migration is a side effect of the column staying TEXT,
     not the reason for the choice.
   - Touches `ProviderConfigurationModels.kt` (enum + parse/serialize/validate), `RoomProviderRepository`,
     `ProviderEditorState`, backup restore (`StandardBackupRestorer` normalizes legacy single tokens).
2. **Logo pipeline — 3 coordinated touchpoints** (verified in code). Ordering today lives in three
   places, each with a hardwired literal-token check:
   - **SQL** `EFFECTIVE_LOGO_COLUMN` (`CatalogDao.kt:399`): `CASE WHEN p.logoPriority = 'epg' …` — 2-way
     Playlist-vs-EPG only; Local absent. Feeds `ChannelWithLogo.effectiveLogoUrl` (3 read queries incl.
     `getChannelsWithLogoUrls()`).
   - **App resolver** `resolveChannelLogoModel` (`PlaybackOrchestration.kt:127`): applies Local
     (`== LOGO_PRIORITY_LOCAL`), then reads the SQL-chosen URL + cache.
   - **Refresh worker** `DefaultLogoRefresher.refreshLogos` (`RefreshExecution.kt:587,619`): prefetches
     exactly the SQL-chosen `effectiveLogoUrl` into the `ChannelLogo` cache.

   **Correctness trap:** both comparisons are literal tokens — SQL `= 'epg'`, app `== "local"`. The
   instant the column stores CSV (`"epg,local,playlist"`), `'epg,local,playlist' = 'epg'` is false → every
   epg-first user silently falls back to playlist-first. SQL + worker MUST change in lockstep; not optional.

   **Correctness finding — Local in the middle:** collapsing playlist+epg into one `effectiveLogoUrl`
   (as SQL does today) is unrecoverable for orders where Local sits *between* the remotes, e.g.
   `[Playlist, Local, EPG]`: if playlist present → playlist wins; if playlist empty → Local (if any)
   else EPG. The collapsed URL no longer knows *which* remote it was → wrong Local placement. So an
   earlier "just reorder in SQL" idea is wrong. **Ordering must move fully to Kotlin.**

   **Design (keeps domain model + display untouched):**
   - SQL `effectiveLogoUrl` becomes **order-agnostic** `COALESCE(playlist, epg)` = "any remote logo",
     used only as the display produceState key + `logoMissing` heuristic in Home/LiveTv. No token → the
     CSV trap disappears. Domain `Channel` and the two feature routes stay **unchanged**.
   - New small DAO query `getLogoCandidates(channelId)` → (playlistUrl, epgIconUrl) separately.
   - **App resolver** `resolveChannelLogoModel` walks the provider's order over Playlist/EPG/Local,
     skip-empty. One extra indexed query per channel alongside the existing `getProvider`
     (`ponytail:` note it, memoize per provider if it ever profiles hot).
   - **Worker** exposes both remote URLs + order in its channel query and prefetches the order-winner.

   **Skip-empty semantics:** walk the ordered sources; any source yielding nothing is skipped, continue.
   Local with no folder / no match = empty → skipped, not blocking. `[Playlist, Local, EPG]` with no
   local match → Playlist, then EPG.

   **No persisted per-channel logo assignment** (all read-time SQL + display-time app + prefetch cache) →
   **no channel-row backfill / no migration**; a reorder takes effect on next read, cache self-heals on
   next refresh.

   **Finalized blast radius (~8 modules):**
   - `core/database` — `CatalogDao`: `EFFECTIVE_LOGO_COLUMN` → order-agnostic COALESCE; new
     `getLogoCandidates`; worker channel query exposes both URLs + `p.logoPriority`. No schema/migration
     (all computed columns).
   - `app` — `PlaybackOrchestration.resolveChannelLogoModel` rewrite (order walk + CSV parse).
   - `worker` — `RoomMediaImageRefreshSource` picks the order-winner per channel.
   - `data/provider` — `LogoSource` enum + CSV parse/serialize/validate in `ProviderConfigurationModels`;
     `RoomProviderRepository` passes the string through (column stays TEXT, validated on read).
   - `feature/settings` — `ProviderEditor` logo dialog → inline `VivicastReorderList`; `logoPriorityLabel`
     → order summary ("Playlist › EPG › Lokal"); drop the `localLogosConfigured` gate (always 3 rows);
     `ProviderEditorState.logoPriority` stays `String` (CSV).
   - `core/designsystem` — new `VivicastReorderList` + strings (renamed labels, order-summary joiner).
   - `app/backup` — `StandardBackupRestorer` legacy default `"provider"` + single tokens normalize to a
     valid order; CSV round-trips as a plain string.
   - Tests — model parse/expand/validate; resolver order + skip + **Local-in-the-middle**; worker winner;
     backup round-trip.
   - **Untouched:** domain `Channel` model, `HomeRoute`/`LiveTvRoute` display logic.

Persistence write path = the panel's existing `onEditorChange(editor.copy(logoPriority = …))` /
`updateProvider` flow — no new repo renumber method needed for this site (only 3 items, stored as CSV).

## Decided (user)

- Model: **ordered value on the provider** (`List<LogoSource>`), stored as CSV in the existing `TEXT`
  column, backed by a new `LogoSource` enum + read-validation. Chosen as the *correct* model for a
  closed-enum permutation (not merely to skip a migration); a join table would over-normalize. Legacy
  single tokens expand to their historical fixed order → no DB migration, no behaviour change until the
  user reorders. (Reconsidered on request — confirm before build.)
- Resolver rework **is in scope** for the first cut (free order must actually take effect).
- Build **inline `VivicastReorderList` only**; overlay wrapper deferred (YAGNI).
- Logo picture / interaction approved.
- **Local row always visible** in logo priority, regardless of a configured folder; unmatched/unconfigured
  Local is skipped in the resolver chain (skip-empty semantics), never blocks.

## Open items (later)

- Whether ◄/► ±10 / "move to position N" get added later if hold-accelerate feels slow.
- Remaining wiring sites (EPG priority, channel groups, channels) sequenced after this proof-of-concept.

## Interaction & edge cases

- **Back has two layers:** in pickup mode → Back cancels the pickup (item returns to origin), stays in
  the list. Not in pickup → Back closes the dialog. Don't let Back close the dialog mid-pickup.
- **Commit timing (inline):** the list edits a local working copy; "Fertig" commits the order via the
  panel's existing `onEditorChange(editor.copy(logoPriority = …))` → `updateProvider`. Back discards.
- **Single-item / empty list:** reorder is a no-op (no pickup). Guard in the component.
- **Stable keys:** item key = source id, required for `animateItem()`.

## Tests (the required checks)

- Model: parse/serialize round-trip; legacy single token expands to historical fixed order; validation
  fills missing sources + dedups. (`ProviderConfigurationModelsTest` / `ProviderEditorStateTest`.)
- Resolver: user order is honoured; an empty/unmatched source (incl. unconfigured Local) is skipped and
  the chain continues. (Extend the playback/orchestration logo-resolve test.)
- SQL projection: `effectiveLogoUrl` exposes the right candidates after the CASE rework (guard the CSV
  literal-token trap). Worker: `DefaultLogoRefresher` prefetches the remote URL matching the new order
  (extend `RefreshExecutionTest.logoRefreshCachesMissingMediaImagesAndSkipsUnchangedCachedImages`).
- No renumber method for logo priority (only 3 items, stored as one value) — no renumber test here.

## Not doing (YAGNI)

- No reorder library. No generic drag-drop framework. No inline variant until a site needs it.
- No new order columns until the specific site is chosen.
