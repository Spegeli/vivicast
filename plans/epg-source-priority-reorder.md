# EPG-Source assignment popup: remove Close + add priority reorder (Research + Plan)

> Status: **COMPLETED 2026-07-18 (committed + pushed).** Impl (I); F1 hardening included; G-edge = linked-only.
> All static gates green: assembleDebug, testDebugUnitTest (35/0, incl. reorder VM test),
> assembleDebugAndroidTest, detekt (baseline re-keyed to 33 — only the ProviderSettingsPanel signature
> entries, no growth). 3 new EPG androidTests PASS on the API-36 emulator: winner-query
> (priority/match/manual-override), F1 unlink-cleanup, reorder. Pending: user manual UI test (popup two
> sections + Priorität reorder dialog + focus) + commit (needs GO). Researched 2026-07-18, HEAD 62b47d5.
>
> **▶ Follow-up queued (NEXT task, right after this plan + its tests are green):** winner-aware EPG search
> — `EpgDao.searchPrograms` currently searches all sources → duplicate hits for a channel matched by ≥2
> sources. Directly related to this priority work; tackle it as the immediate next task (see scenario I).
> Companion to `plans/reorder-dpad-list.md` (this is that plan's wiring site #2: "EPG sources per
> playlist — order by priority, `ProviderEpgSourceEntity.priority` already exists — **Low tier**").
>
> **⚠ Blocking finding (see below): `priority` is stored but NOT honored in EPG resolution.** The popup
> UI (Changes 1+2) would set a value nothing consumes for multi-source playlists. Needs a scope decision
> before build.

## Decisions (user)

- Label: **"Priorität"**. Host: **nested dialog** (my call). Empty-section headers: **only when non-empty**.
- EPG resolution: **include it** (user chose "Fix A mitnehmen"), **match-based** model — each channel's
  EPG = highest-priority linked source that MATCHED it; lower sources fill unmatched channels; manual wins.
  See Change 3.
- Implementation: **(I) read-time winner** — CHOSEN by user. (II) rejected (only saved storage, not worth
  the pipeline rewrite).

## Scope (user request)

The EPG-source assignment popup reached from **playlist edit** (`ProviderEditor` →
`ProviderEpgSourcesDialog`). Two changes:

1. **Remove the "Close" button.** Toggles already link/unlink immediately; the dialog already dismisses
   on Back (`VivicastDialog` Back-KeyUp handler). The button is redundant.
2. **Add a priority sort** for the *linked* EPG sources, using the existing D-Pad grab-and-move component
   (`VivicastReorderList`) — the same idiom already shipped for Logo priority and Manage-Groups (Manual).

## ⚠ Finding — priority is stored but NOT honored in EPG resolution (decision needed)

Traced save / refresh / read. `provider_epg_sources.priority` is **written** (link, auto-link `max+1`,
unlink-renumber, backup) but **never read to resolve which source provides a channel's EPG**:

- **Read** `EpgDao.observeProgramsForChannel` (`EpgDao.kt:54`) selects `epg_programs` for
  `(providerId, channelId)` from **every active source**, `ORDER BY startTime` — no priority, no
  per-source pick.
- **Import** `RoomEpgRepository.importXmltv` runs **per source**; each source auto-maps its own channels
  (`epg_channel_mappings` unique `(providerId, channelId, epgSourceId)` = one row **per source**) and
  delta-merges its own `epg_programs` (scoped by `epgSourceId`). Sources never coordinate.
- **Refresh** `RoomEpgSourceReader.getActiveSourceIdsForProvider` (`RefreshExecution.kt:323`) just
  collects active sources to refresh — priority not used for ordering or resolution.
- **Mapping display** `observeMappingsForChannel` orders `isManual DESC, createdAt DESC` — not priority.

**Consequence:** for a channel mapped in ≥2 linked+active sources, the channel's EPG is a **merge of all
of them** (duplicate / overlapping programmes); priority has **zero visible effect**. A single-source
playlist (the common case) is unaffected. So the reorder UI, by itself, sets a value nothing consumes.

This changes visible EPG behavior and is not algorithm-specified by ADR-002 (it only says
"EPG-Mapping und Provider-EPG-Prioritäten anwenden") → **stop & ask** before implementing.

### Recommended fix (option A — highest-priority source wins, with fallback)

Per provider channel, serve EPG from the **highest-priority linked+active source that actually has
programmes** in the window; lower sources are fallback only when the higher has none. Standard IPTV "EPG
source priority"; also removes the duplicate-timeline bug.

- **Where:** read-time, one choke-point. `observeProgramsForChannel` picks the winning `epgSourceId` for
  `(providerId, channelId)` from `provider_epg_sources.priority`, then returns only that source's
  programmes (join `provider_epg_sources` for priority + pick the min-priority source having in-window
  rows). No schema change, no migration; takes effect immediately on reorder.
- **Blast radius:** the one DAO query — consumed by Live-TV now/next + the EPG day view. Add a DAO
  androidTest (priority winner + fallback when the top source has no programmes). Verify Live-TV/EPG still
  render on the emulator.
- Alternatives: (B) merge-but-prefer-higher-on-overlap — messier, still shows lower-source gaps oddly;
  (C) import-time single-writer per channel — cross-source coordination, much larger. Read-time winner is
  smallest + correct.

### Scope question

Include option A in **this** plan (so priority is actually meaningful), or ship the popup UI (Changes
1+2) now and do resolution as a **separate follow-up** plan? (Recommend: include A — a control that does
nothing is bad UX, and A is a contained single-query change.)

## What already exists (verified in code)

- **Popup** `ProviderEpgSourcesDialog` — `ProviderEditor.kt:921-953`. Compact `VivicastDialog`, one
  `VivicastSettingsRow` toggle per source (`onToggle(source.id, !linked)`), then a `VivicastButtonRow`
  with a single **Close** `ActionPill` (lines 948-951) = the button to remove.
- **Reorder component** `VivicastReorderList(items, onReorder, modifier)` + `VivicastReorderItem(id,label)`
  — `core/designsystem/VivicastReorder.kt`. Stateless from the caller; OK = pick up, ▲/▼ = move, OK =
  drop → `onReorder(orderedIds)`, Back = cancel pickup. Built to be embedded inline OR wrapped in a
  dialog. Precedent: `ProviderGroupsPanel.kt:139-156` wraps it in a `VivicastDialog` (Wide, heightIn 340dp)
  opened by a "Sortieren" `ActionPill` gated on `sortMode == Manual && groups.size > 1`.
- **Priority storage** `ProviderEpgSourceEntity.priority` + unique index `(providerId, priority)`.
  `EpgDao.observeProviderEpgSources` / `getProviderEpgSources` both `ORDER BY priority` → the linked list
  is already delivered in priority order.
- **UiState** `SettingsUiState.providerEpgLinks: List<ProviderEpgSource>` (has `priority`, ordered),
  scoped to `selectedEpgProviderId`. `openEditorFor` sets `onSelectEpgProvider(provider.id)`
  (`ProviderSettingsPanel.kt:322`) → the edited provider IS the scoped provider, so its ordered links are
  available to the editor.
- **Renumber logic** `SecureEpgSourceRepository.rewritePriorities(links)` (`:114`) — temp-offset then
  1..N renumber to dodge the unique index. Currently **private**, called only by `unlinkSourceFromProvider`.
  There is **no public reorder/move method yet** — this is the one backend gap.
- **Wiring idiom to mirror** (`onToggleEpgLink`): `SettingsRoute.kt:478` (`routeScope.launch { viewModel.
  link/unlinkEpgSourceToProvider(...) }`) → `ProviderSettingsPanel` param `:132` + call-site `:624` →
  `ProviderEditor` `EditorActions.onToggleEpgLink :413`. VM methods are `suspend … : Result<Unit>`
  (`SettingsViewModel.kt:322-326`).

## Change 1 — popup restructure: two sections + remove Close

`ProviderEpgSourcesDialog` (`ProviderEditor.kt:921`) becomes two labelled buckets instead of a flat list:

- **"Zugewiesene Quellen"** — the linked sources, in **priority order** (`linkedInOrder`, already
  `ORDER BY priority`). Each row is an ON toggle; toggling off unlinks → the row drops to the lower bucket.
- **"Nicht zugewiesene Quellen"** — `sources` minus `linkedIds`. Each row is an OFF toggle; toggling on
  links (priority = max+1) → the row rises into the assigned bucket, appended last.

The move between buckets is **automatic**: toggle → link/unlink → `observeProviderEpgSources` re-emits →
`providerEpgLinks` / `linkedIds` update → recompose re-buckets the row. No extra logic (it is the same
Room-write + Flow-emit that already flips the ON/OFF value today).

```
┌─ EPG sources ─────────────────┐
│ Zugewiesene Quellen           │   ← header, only if ≥1 assigned
│   Test              [ON ●]    │   priority order (1,2,3…)
│   Test #2           [ON ●]    │
│        [ Priorität ]          │   ← ActionPill, only if ≥2 assigned
│ Nicht zugewiesene Quellen     │   ← header, only if ≥1 unassigned
│   Test #1           [   ○]    │
└───────────────────────────────┘   Back schließt (kein Close-Button)
```

- **Section labels** = `BodyText(...)` sub-headers (same idiom as `ProviderGroupsPanel.kt:172`'s sort
  label). Render a section only when it has ≥1 item; skip an empty section's header. 0 sources total →
  the existing "no EPG sources" `BodyText` (unchanged).
- **Remove Close**: delete the `Spacer` + `VivicastButtonRow { ActionPill(common_close) }` block
  (`:948-951`). Toggles apply immediately; `VivicastDialog` dismisses on Back.
- Focus: first focusable = first assigned row (or first unassigned if none assigned). Empty-total case →
  no focusable, Back still closes. `ponytail:` no fallback focusable added.
- **New strings** (designsystem, both locales): `settings_provider_epg_assigned` = "Zugewiesene Quellen" /
  "Assigned sources"; `settings_provider_epg_unassigned` = "Nicht zugewiesene Quellen" / "Unassigned
  sources".

## Change 2 — priority reorder (the real work)

### UX (recommended): a "Priorität" action → nested reorder dialog

Faithful reuse of the shipped `ProviderGroupsPanel` shape, adapted to a dialog host:

- In the popup, **directly under the "Zugewiesene Quellen" section** (it reorders exactly that bucket),
  add a single `ActionPill("Priorität")` — shown **only when ≥2 assigned sources** (reorder of 0-1 is a
  no-op).
- Click → opens a **second `VivicastDialog`** (Wide, `heightIn(max=340.dp)`) hosting
  `VivicastReorderList` of the **linked** sources in priority order (label = source name).
- Drop (OK) → `onReorderEpg(orderedSourceIds)` persists the new priority; the reorder list keeps editing
  its local copy, each drop commits (same as ProviderGroupsPanel). **Back** closes the reorder dialog →
  returns to the toggle popup underneath. Back semantics are natural because the inner dialog is its own
  window (no custom Back handling needed beyond what `VivicastReorderList` already does for pickup).

Why nested dialog over "swap content in one dialog": the outer `VivicastDialog` dismisses on Back-KeyUp;
a content-swap would need an extra BackHandler so Back returns to the toggle view instead of closing the
whole popup. A nested dialog gets that for free (inner Back → close inner → outer stays). Both are
architecturally fine; nested is the smaller, lower-risk diff and matches precedent.

**Risk to verify on emulator:** dialog-over-dialog focus return on TV (focus should land back in the
toggle popup when the reorder dialog closes). `VivicastReorderList`-in-`VivicastDialog` itself is already
proven by ProviderGroupsPanel; only the stacking is new. Fallback if focus misbehaves: the content-swap
variant (one dialog, `var showReorder`, a BackHandler to return to the toggle view).

### Touch list (mirror the onToggleEpgLink chain exactly)

| Layer | File | Change |
|---|---|---|
| **Repo iface** | `data/epg/EpgSourceRepository.kt` | add `suspend fun reorderProviderEpgSources(providerId: String, orderedSourceIds: List<String>)` |
| **Repo impl** | `data/epg/SecureEpgSourceRepository.kt` | implement in `withTransaction`: read `getProviderEpgSources`, reorder the entities to match `orderedSourceIds` (ignore ids not currently linked; keep any missing linked ones appended in current order — defensive), reuse the temp-offset renumber (extract the body of `rewritePriorities` to take an explicit ordered list). |
| **VM** | `feature/settings/SettingsViewModel.kt` | add `suspend fun reorderEpgSourcesForProvider(providerId, orderedSourceIds): Result<Unit> = runCatching { epgSourceRepository.reorderProviderEpgSources(...) }` (next to link/unlink `:322-326`) |
| **Route** | `feature/settings/SettingsRoute.kt` | at the `ProviderSettingsPanel(...)` call (~`:478`) add `onReorderEpgLink = { providerId, orderedIds -> routeScope.launch { viewModel.reorderEpgSourcesForProvider(providerId, orderedIds) } }` |
| **Panel** | `feature/settings/ProviderSettingsPanel.kt` | new param `onReorderEpgLink: (providerId, orderedIds: List<String>) -> Unit = { _, _ -> }`; in the editor `EditorActions` (`:624` area) add `onReorderEpg = { orderedIds -> selectedProviderId?.let { onReorderEpgLink(it, orderedIds) } }`; extend the `ProviderEpgLinkInfo(...)` built at `:628` with the ordered linked list (below) |
| **Editor state** | `feature/settings/ProviderEditor.kt` | `ProviderEpgLinkInfo` (`:417`) gains `val linkedInOrder: List<EpgSource> = emptyList()` (built in the panel from `providerEpgLinks` — already priority-ordered — mapped to `epgSources` by id; drives the **assigned** section + the reorder dialog). `EditorActions` (`:413`) gains `onReorderEpg: (orderedSourceIds: List<String>) -> Unit = {}`. `ProviderEpgSourcesDialog` (`:921`) gains `linkedInOrder` + `onReorder` params, renders the **two labelled sections** (assigned in priority order / unassigned), drops the Close block, adds the gated "Priorität" action under the assigned section + the nested reorder dialog. |
| **Strings** | `core/designsystem/res/values/strings.xml` + `values-en/strings.xml` | add `settings_provider_epg_priority` = "Priorität" / "Priority" (action + reorder-dialog title), `settings_provider_epg_assigned` = "Zugewiesene Quellen" / "Assigned sources", `settings_provider_epg_unassigned` = "Nicht zugewiesene Quellen" / "Unassigned sources". Reorder hints (`reorder_hint_active/idle`) already exist. **Do NOT** add strings to app/ or feature/ (designsystem-only rule). |

`linkedInOrder` build (panel, `:628`):
```
val linkedOrdered = providerEpgLinks                       // already ORDER BY priority
    .mapNotNull { link -> epgSources.firstOrNull { it.id == link.epgSourceId } }
ProviderEpgLinkInfo(sources = epgSources, linkedIds = …, linkedInOrder = linkedOrdered)
```

### Interaction with existing link/unlink (composes cleanly)

- **Link** a new source → `SettingsRoute:481` assigns `priority = max+1` → appended last in the order. ✅
- **Unlink** → `unlinkSourceFromProvider` already renumbers remaining 1..N (`rewritePriorities`). ✅
- **Reorder** → renumbers linked to the chosen order. After commit, `observeProviderEpgSources` re-emits →
  `providerEpgLinks` updates → `linkedInOrder` + the reorder list reflect it (list re-keys on new items). ✅
- Toggling a source **off** while its row is in the (open) reorder dialog: the reorder dialog is only
  reachable from the toggle popup and shows a snapshot; on unlink the source leaves `linkedInOrder` on the
  next emission. Edge, low risk — reorder dialog is short-lived. Note for emulator check.

## Change 3 — EPG resolution honors priority (user's match-based model)

**User's model (confirmed):** priority decides, per channel, **which source claims it by MATCH**. Sources
in priority order (e.g. a, c, b): source a maps every channel it finds a match for; the channels a did
**not** match fall to c; c's leftovers to b. So each channel's EPG = the **highest-priority linked source
that has a matching EPG channel** for it. Manual mapping wins over all (ADR-002).

Winner per provider channel:
1. **Manual mapping** → its source (regardless of priority).
2. else **highest-priority (lowest `priority` value) linked+active source that has a MAPPING** (= matched
   that channel). Lower sources cover only the channels the higher ones didn't match.

Note this is **match-based**, not programme-count-based: if source a matched channel X, a owns X even if
a currently has no programmes for X (no silent fallback to c) — matches the user's claiming description.

### Two implementations (pick one)

**(I) Read-time winner — RECOMMENDED.** All sources keep importing their own mappings + programmes
(unchanged pipeline). `EpgDao.observeProgramsForChannel` (`EpgDao.kt:54`) returns only the **winner
source's** programmes, winner chosen by a subquery over `epg_channel_mappings` ⨝ `provider_epg_sources`
(priority) ⨝ `epg_sources` (isActive): `ORDER BY isManual DESC, priority ASC LIMIT 1`.
- Produces the **exact** a→c→b result the user described (winner = highest-priority source with a match).
- **Robust** to the current independent/parallel per-source refresh (no cross-source ordering needed).
- **Instant on reorder** — no re-import; the next read recomputes the winner. Deactivating/unlinking a
  source auto-drops it from winner selection.
- One query rewrite, **no schema / no migration / no import change**.
- Tradeoff: losing sources' mappings+programmes stay in storage (invisible, bounded, self-heal on
  refresh). `ponytail:` acceptable vs a cross-source claiming rewrite; the winner subquery scans the
  provider's few linked sources (1–3) — memoize only if it profiles hot.

**(II) Mapping-time claiming.** Enforce one-winner mapping per channel during import: a source skips
channels already claimed by a higher-priority source and evicts a lower source's claim; only the winner's
programmes are ever stored. Literally matches the "a claims, c fills gaps" mechanism, and is what
ADR-003's step 7 ("EPG-Mapping und Provider-EPG-Prioritäten anwenden") envisions as a provider-scoped
pass. **But**: the current pipeline refreshes sources independently/in parallel, so this needs a
priority-ordered provider-scoped resolution pass + cross-source mapping/programme eviction + a re-run on
every priority reorder (else a reorder does nothing until the next refresh). Much larger, touches the core
import/merge, higher regression risk. Same visible result as (I).

**Recommendation: (I).** Identical visible behavior to the user's model, a fraction of the risk, and
priority "greift" the moment EPG is shown after any refresh or reorder. (II) only differs in that storage
holds only the winner's rows — an internal cleanliness gain not worth the pipeline rewrite now.

### Consumers + tests (either implementation)

- **Consumers:** Live-TV now/next + the EPG day view (both call `observeProgramsForChannel`).
- **Tests:** DAO androidTest — (a) two sources both matching a channel, higher priority wins; (b) a
  channel only the lower source matches → lower serves it; (c) manual mapping to the lower-priority source
  → manual wins. Emulator: Live-TV EPG + day view render for single- and multi-source playlists; reorder
  in the popup visibly re-points a shared channel's EPG.

## Adversarial scenarios (multi-playlist / shared source / refresh / reorder)

Verified against code. Data model: `priority` (provider_epg_sources), `epg_channel_mappings`, and
`epg_programs` **all carry `providerId`**; refresh fans out per active linked provider
(`DefaultEpgRefresher`: `activeProviderIds.forEach { importXmltv(providerId, source, doc) }`,
`RefreshExecution.kt:392`). So everything below is **per-provider isolated**.

- **A. Isolation (foundation).** Two playlists sharing source S each hold their own priority order,
  mappings, and program copies. The winner subquery keys on `providerId` → P1/P2 resolve independently; a
  reorder in P1 never touches P2. ✅
- **B. Different order per playlist.** P1 `[S,T]`, P2 `[T,S]`, channel X matched by both → P1 shows S,
  P2 shows T. ✅
- **C. Reorder during an in-flight refresh.** Reorder writes provider_epg_sources; refresh writes
  mappings/programs; both serialize on SQLite's single writer. The winner query reads whatever is
  committed → eventually consistent, no crash. Read-time resolution is inherently robust to partial
  refresh (a not-yet-imported source has no mapping → cannot win yet). ✅ (This is why (I) beats (II).)
- **D. Partial / failed refresh.** P1 `[S,T]`, S imported, T failed → X (S-matched) shows S; Y (only
  T-matched) shows nothing until T imports, then Y→T. No stale winner. ✅
- **E. Unlink the current winner.** Winner subquery requires the source still linked (join
  provider_epg_sources) → an unlinked source drops out immediately; next-priority linked source wins. ✅
  **But see finding F1 (stale rows).**
- **F. Deactivate the winning source** (`isActive=0`). Winner subquery joins `epg_sources.isActive=1` →
  excluded → next active source wins; reactivate → returns. ✅
- **G. Manual vs priority.** Auto-mapping already skips manually-mapped channels per provider
  (`ignoredChannelIds = manualMapped`) → a manual channel has ONLY its manual mapping; winner
  `ORDER BY isManual DESC` → manual wins. No conflict. ✅ ADR-002 preserved. **Edge decision:** a manual
  mapping whose source was later unlinked — default **require the source still linked** (unlink fully
  removes a source from resolution), i.e. the winner subquery only considers linked sources even for
  manual. Confirm.
- **H. Delete global source vs unlink.** The popup toggle-off = **unlink** (per provider); it never
  deletes the global source. `deleteSource` (EPG settings, separate) removes S for everyone incl. a
  sharing P2 — existing global semantics, unchanged. ✅
- **I. EPG search (out of scope, note).** `EpgDao.searchPrograms` searches **all** sources' programs,
  not winner-filtered → a channel matched by ≥2 sources can yield duplicate program hits in the "EPG"
  search group. Pre-existing (merge-all). Change 3 fixes only the channel-**display** path
  (`observeProgramsForChannel`, the sole display consumer = `LiveTvViewModel.kt:143`). Winner-aware
  search is a separate, larger change — **not** done here; flag as a known limitation.
- **J. Backup/restore.** Export includes `provider_epg_sources` incl. `priority`; restore reads
  `priority` (`StandardBackupRestorer.kt:244`, `coerceAtLeast(1)`). Priority round-trips. ✅ No change.
- **K. Auto-EPG (Xtream companion).** Links at `priority = max+1` (append last) — consistent; user can
  reorder after. ✅
- **L. Reorder then unlink a middle source.** `rewritePriorities` renumbers the remaining in order → gap
  closed, order preserved. ✅
- **M. Winner-query cost.** Scalar subquery references only `:providerId`/`:channelId` (not the outer
  row) → evaluated once per query, over the channel's ≤(#linked sources) mapping rows. Sole consumer is
  Live-TV, per visible/focused channel. Negligible. `ponytail:` memoize only if it ever profiles hot.

### Finding F1 — clean (provider, source) rows on unlink (recommended hardening)

`unlinkSourceFromProvider` today deletes only the `provider_epg_sources` row + renumbers; it leaves
`epg_programs` and `epg_channel_mappings` for `(provider, source)` behind. With (I) those are invisible
(the winner subquery excludes unlinked sources), **but**: (a) storage leak, and (b) on **re-link** the
stale old rows resurface and can win immediately, showing stale EPG until S's next refresh for that
provider. Fix in the unlink transaction: `epgDao.deleteProgramsForProviderAndSource(providerId, sourceId)`
(exists) + a **new** `deleteMappingsForProviderAndSource(providerId, epgSourceId)` DAO query (only
`deleteMappingsForProvider` / `…ForSource` / `…ForChannels` exist today). Small, in-transaction, no schema
change. Add a repo androidTest (unlink purges the pair's programs+mappings; re-link starts clean).

## Architecture compliance (CLAUDE.md + SETTINGS-APP-HOISTED)

- Reorder **persistence** = a `:data:epg` repo method → VM `suspend Result` → panel callback. Exactly the
  sanctioned home: `SETTINGS-APP-HOISTED-DECISIONS.md` line 28 lists "link/unlink/**move**" under
  `EpgSourceRepository`. No new module, no DI change, no repo-flow/CRUD in a composable.
- Reorder **interaction** = local composable UI state inside `VivicastReorderList` (focus/D-Pad/dialog
  state — allowed in composables).
- ViewModel stays free of Compose/Context/strings. Strings live in designsystem only.
- App-hoisted boundaries untouched (no scheduler/Keystore/SAF involved).

## Detekt / gates

- `ProviderEditor.kt` composables are already baselined (LongMethod/CyclomaticComplexMethod). The new
  nested reorder dialog is a **new private composable** (not growth of an existing baselined method);
  `ProviderEpgSourcesDialog` gains 2 params + a small block. Watch `detekt`; if `ProviderEpgSourcesDialog`
  trips LongMethod, extract the reorder dialog into its own `@Composable`. Prefer extraction over baseline
  growth.
- Keep green: `.\gradlew.bat detekt`, `assembleDebug`, `test`.

## Tests (required checks)

- **Repo**: androidTest `reorderProviderEpgSources` — link 3 sources (priority 1,2,3), reorder to
  [3,1,2], assert `getProviderEpgSources` priorities are 1,2,3 in that id order; unique-index survives
  (temp-offset). Extend `RoomEpgRepositoryTest` (it already covers link/unlink/renumber).
- **VM**: extend `SettingsViewModelTest` — `reorderEpgSourcesForProvider` returns success + calls repo
  with the ordered ids (fake repo records order).
- No new UI instrumentation test required (reuse the shipped reorder component; its behavior is covered
  by the logo/groups callers). Manual emulator pass covers the popup + nested dialog + focus return.

## Open UX decision (confirm before build)

1. **Action label**: "Priorität" (recommend) vs "Sortieren" (Groups wording). Placement is settled: under
   the "Zugewiesene Quellen" section.
2. **Reorder host**: nested dialog (recommended) vs content-swap in one dialog (fallback). OK to go nested
   and fall back only if emulator focus-return misbehaves?
3. **Empty-section headers**: show a section header only when it has items (recommend, less noise) vs
   always show both headers with a muted "keine" hint (always communicates the two-bucket model).

## Not doing (YAGNI)

- No change to link/unlink, auto-EPG, or the EPG-global settings panel.
- No hold-to-accelerate / ◄►±10 in the reorder component (already deferred there; the linked set is small).
- No reorder for the EPG-settings-panel provider list (out of scope — user asked only for the playlist-edit
  popup).
