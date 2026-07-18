# Provider lifecycle fixes — UA-on-add · slow delete · Xtream 429 (Research + Plan)

> Status: **COMPLETED — implemented, static gates green, migration validated on the real emulator DB, and
> all flows (playlist add/edit/delete, EPG, groups) verified bug-free by the user's manual test. Committed.**
> Built: Task 1 (UA row un-gated for add, via a shared `providerUserAgentItem` helper); Task 2
> (structural rowid-tied FTS + `MIGRATION_18_19`, in-merge provider/source existence guards, targeted
> `android_tv_search_entries` delete, `cancelPlaylistRefresh`/`cancelEpgRefresh` APIs + provider-delete
> cancel wiring); Task 3 (429/rate-limit backoff in `withXtreamRetry`, on-demand `importXtreamSeriesDetail`
> + `AppContainer.ensureSeriesDetail` wired through SeriesRoute/VM, eager series-details enqueue removed).
> Validated: `assembleDebug`, `testDebugUnitTest`, `detekt`, `assembleDebugAndroidTest` all green (detekt
> baseline re-keyed for SettingsRoute/SeriesRoute signatures + `RoomCatalogImportRepository` TooManyFunctions
> entry dropped as the eager import method was deleted); the v18→v19 migration ran on the real 74 MB DB
> (user_version=19, all FTS docid==base rowid), and a real-data provider delete dropped from ~4 min to
> **movies 0.20 s / epg 0.64 s** (O(1) triggers).
> **Follow-up done (2nd pass):** EPG-source-delete now cancels its refresh+periodic (`onEpgSourceDeleted`
> wired MainActivity→SettingsRoute) — the source-existence guard already covered correctness; and the eager
> series-details path (`DefaultSeriesDetailsRefresher`, `SeriesDetailsRefreshWorker`,
> `enqueueSeriesDetailsRefresh`, global `importXtreamSeriesDetails`, `needsSeriesDetailsRefresh`, the
> `SeriesDetailsRefresh*` diagnostics + work contracts) was fully deleted (not left dormant). Still deferred:
> stage-hygiene-on-delete (defense-in-depth; restart's `recoverStuckRefreshState` clears stage anyway).
> Remaining: user's manual UI test + commit (needs GO).
> Three independent bug fixes around the playlist (provider) add/delete lifecycle. Researched against
> HEAD `55d313f`, with live evidence from the emulator DB (`vivicast.db`, one Xtream "Test" provider)
> and empirical delete timings on a copy. Reference apps in `../IPTV-APPS` consulted for Task 3 only.

---

## Task 1 — User-Agent field missing when ADDING a playlist

### Root cause (verified)
The whole UA plumbing already carries a per-provider User-Agent on ADD — the **only** missing piece is
the UI row that sets it, which is inside an edit-only gate:

- The UA settings row `item(key = "user-agent")` lives in `providerEditControlItems(...)`
  (`ProviderEditor.kt:718-725`), and that whole helper is called only inside
  `if (editor.isEditing) { … }` at `ProviderEditor.kt:255`. `isEditing = providerId != null`
  (`ProviderEditorState.kt:48`). So in add mode the UA row is never composed → `editor.userAgent`
  stays `""` → `null` flows everywhere → global default UA is always used on add.
- Everything downstream is already UA-aware: state field `ProviderEditorState.userAgent` (`:35`),
  the input dialog `ProviderUserAgentDialog` (`ProviderEditor.kt:864-907`) rendered **unconditionally**
  via `ProviderEditorDialogs` (`:302`), `ProviderCreateRequest.userAgent` (`ProviderConfigurationModels
  .kt:21`), `toConnectionTestRequest()` (`ProviderEditorState.kt:160`), `toCreateRequest()` (`:179`),
  and `RoomProviderRepository.createProvider` persisting it (`:104`).

### Fix (minimal — one place)
Render the UA row in add mode too. Lift **only** the `item(key = "user-agent")` block out of the
`if (editor.isEditing)` gate so it composes in both modes (its `onClick` flips `dialogs.showUserAgent`,
and the dialog already renders in both modes). Leave interval / app-start / EPG / logo / xtream-output
inside the edit-only gate (those are legitimately edit-only — e.g. EPG assignment needs a saved id).

- No model / request / repository change (all already carry UA).
- No new strings; default `""` = "use global default" is already the correct add seed.
- The connection test on add will then carry the entered UA (`toConnectionTestRequest()` already
  includes it), so a provider that only answers a custom UA can be added at all.

Change surface: `feature/settings/.../ProviderEditor.kt` only (un-gate one lazy-list item). Trivial.

---

## Task 2 — Deleting a playlist takes 1.5–4 minutes

### Evidence
Emulator DB, the "Test" Xtream provider (`id 32454f54-…`): **601** channels, **8900** movies,
**1483** series, **21887** epg_programs, 261 epg_channel_mappings, 31 categories. DB file 74 MB.

Empirical delete timing on a VACUUM'd copy (fast dev machine; the emulator is several× slower):

| Table (rows) | base `DELETE WHERE providerId` (trigger fires) | with the fix (bulk-delete FTS first) |
|---|---|---|
| movies (8900) | **10.8 s** | **0.28 s** (≈38×) |
| epg_programs (21887) | O(N²) — extrapolates to ~1–2 min | **0.92 s** |
| channels (601) | 0.09 s | 0.008 s |

Scaled to the slower emulator, the summed base-delete is the observed ~4 min (Xtream) / ~1.5 min (even
a small M3U — see below).

### Root cause (verified) — per-row FTS4 delete triggers scanning a `notindexed` column
The search tables are **standalone `@Fts4`** entities (no `contentEntity`) — `search_channels_fts`,
`search_movies_fts`, `search_series_fts`, `search_epg_fts` (`SearchFtsEntities.kt:8-77`), kept in sync
by hand-written triggers created in `VivicastMigrations.createSearchFtsTriggers()` (`:616-740`),
installed via `VivicastDatabaseCallbacks.SearchFtsCallback`.

The killer — `search_epg_fts_delete` (`VivicastMigrations.kt:731-739`):
```sql
CREATE TRIGGER search_epg_fts_delete AFTER DELETE ON epg_programs
BEGIN DELETE FROM search_epg_fts WHERE programId = old.id; END
```
`programId` is a **`notindexed`** FTS4 column (`SearchFtsEntities.kt:63`). FTS4 can only resolve a lookup
by `rowid` or a `MATCH` on an *indexed* text column — a `WHERE programId = ?` equality on a notindexed
column has **no index**, so SQLite **linearly scans the entire `search_epg_fts` content for every trigger
invocation**. Deleting N base rows fires the trigger N times → **N full scans of M FTS rows = O(N×M)**.
For epg_programs (N≈M≈2·10⁴ here, ≈10⁵⁺ on real feeds) that is the multi-minute cost. Same anti-pattern
on `search_channels_fts_delete` / `search_movies_fts_delete` / `search_series_fts_delete`
(`VivicastMigrations.kt:640/671/702`, `mediaId` notindexed).

Every deleted base table **already has a `providerId` index** (VivicastEntities.kt) — the base DELETE is
fine; the cost is entirely the triggers. You **cannot** fix it by adding an index (can't index a
notindexed FTS4 column).

**Why even a small M3U takes ~1.5 min:** an M3U playlist with a linked XMLTV EPG source stores its EPG
under the *provider's* id — `RoomEpgRepository.importXmltv` writes `EpgProgramEntity(providerId = …)`
per active linked provider. So a ~200-channel M3U with a guide still has tens of thousands of
`epg_programs`; deleting them hits the same O(N×M) trigger scan. The channel count is a red herring —
the hidden `epg_programs` count is what gets squared.

### Delete flow (verified)
`RoomProviderRepository.deleteProvider` (`RoomProviderRepository.kt:256-275`), all in one
`database.withTransaction`:
1. `catalogDao.deleteCatalogForProvider` (`:259`) → `@Transaction` of bulk deletes episodes/seasons/
   series/movies/channels/categories (`CatalogDao.kt:622-675`) — fires channels/movies/series FTS triggers.
2. `epgDao.deleteProgramsForProvider` (`:261`) — **the dominant O(N×M) cost**.
3. mappings / provider_epg_sources / favorites / progress / channel_history / providers (`:262-267`) — cheap.
4. `androidTvSearchDao.rebuildEntries()` (`:268`) — **secondary waste** (see below).

Runs on Room's transaction executor (not the main thread → no ANR), but the confirm dialog stays open
the whole time (`ProviderSettingsPanel.kt:640-651` clears `pendingDelete` only on success) → a long
visible hang.

**Secondary cost:** `AndroidTvSearchDao.rebuildEntries()` (`AndroidTvSearchDao.kt:153-159`) clears
**all** `android_tv_search_entries` and re-inserts **every remaining active provider's** entire catalog
on every delete — set-based (not O(n²)) but wasteful; only the deleted provider changed.

### Chosen fix (user decision: structural best-solution, with migration)
Tie each FTS row's `docid`/`rowid` to its **base row's integer `rowid`**, so the per-row delete trigger
becomes an **O(1)** `DELETE FROM search_<x>_fts WHERE docid = old.rowid` instead of a full-content scan.
This is the standard FTS4 external-content shape; it kills the O(N²) class for **every** delete path
(bulk provider delete *and* single-row edits/re-imports), needs no runtime DDL, and has no
multi-provider caveat. `deleteProvider` then needs no FTS knowledge — the base deletes are fast because
the triggers are O(1).

Feasibility: the base tables (channels/movies/series/epg_programs) use a TEXT `id` PK but are normal
(rowid) tables, so `new.rowid` in an AFTER-INSERT trigger is a valid stable integer key; the FTS is
maintained **only** by triggers (SearchDao only `MATCH`-queries — no direct Room inserts), so realigning
docid↔rowid is self-contained.

Implementation:
1. **Rewrite the 12 sync triggers** in `VivicastMigrations.createSearchFtsTriggers()` (and the fresh-install
   `SearchFtsCallback.onCreate` path) to be rowid-based:
   - INSERT: `INSERT INTO search_<x>_fts(docid, …cols…) VALUES(new.rowid, …)`
   - UPDATE: `DELETE FROM search_<x>_fts WHERE docid = old.rowid; INSERT …(docid,…) VALUES(new.rowid,…)`
   - DELETE: `DELETE FROM search_<x>_fts WHERE docid = old.rowid`
   for all four tables (channels/movies/series/epg — note epg keys on `epg_programs.rowid`).
2. **Room migration v18→v19** (`VIVICAST_DATABASE_VERSION` 18→19, new `MIGRATION_18_19`): drop the old
   triggers, rebuild each FTS table's content aligned to base rowid
   (`DELETE FROM search_<x>_fts; INSERT INTO search_<x>_fts(docid,…) SELECT rowid, …cols… FROM <base>;`),
   then create the new rowid-based triggers. Room's entity schema is unchanged (the FTS `@Entity`/`@Fts4`
   definitions in `SearchFtsEntities.kt` stay identical — Room doesn't track the hand-written triggers),
   so `19.json` ≈ `18.json`; the migration is a data/trigger migration and validation still passes.
3. **Migration test** in `VivicastDatabaseMigrationTest` (18→19: seed base+FTS at v18, migrate, assert FTS
   docids now equal base rowids and search still matches).
4. **Independent, same task:** replace `androidTvSearchDao.rebuildEntries()` in `deleteProvider`
   (`RoomProviderRepository.kt:268`) with a targeted `DELETE FROM android_tv_search_entries WHERE
   providerStableKey = ?` (new `AndroidTvSearchDao` query) — the deleted provider is the only change, so
   the full all-providers rebuild is wasteful.

Change surface: `core/database` — `VivicastMigrations.kt` (triggers + MIGRATION_18_19), `VivicastDatabase
.kt` (version + register migration), `AndroidTvSearchDao.kt` (targeted delete), schema `19.json`,
`VivicastDatabaseMigrationTest`; `data/provider/RoomProviderRepository.deleteProvider` (swap rebuild →
targeted delete). No new module, no DI, no feature/UI change — same tables, same search, correct trigger
keying. Room version bumps 18→19 (persistence change, user-approved).

Rejected alternatives: (a) "bulk-delete FTS by providerId first, triggers intact" — proven fast for
1–few providers (movies 10.8 s → 0.28 s, epg 0.92 s) but degrades to O(N × M_others) with several large
playlists; (b) "drop/recreate triggers around the bulk delete" — robust O(N+M) but runtime DDL in the
delete path and only fixes the bulk path. The structural fix supersedes both.

---

## Task 3 — Rapid add/delete of an Xtream playlist → HTTP 429

### What the catalog fetch already does right
The import fetches each type in **bulk, all-categories, one request** — not per-category, not per-item:
`DefaultPlaylistRefresher.refreshXtreamProvider` (`RefreshExecution.kt:270-338`) calls
`getLiveStreams`/`getVodStreams`/`getSeries` with **no** `categoryId`. No category loops in production.

### Root cause (verified) — series-info storm × stacked, uncancelled workers
1. **`getSeriesInfo` is fetched per series (N requests), eager, unpaced.**
   `DefaultSeriesDetailsRefresher.refresh` (`RefreshExecution.kt:349-381`): `getSeries` again +
   `getSeriesInfo(seriesId)` for **every** series (`:365-369`), sequential, no delay. Auto-enqueued
   after every successful playlist refresh when `includeSeries` (`:72-74` → `enqueueSeriesDetailsRefresh`).
2. **`deleteProvider` never cancels WorkManager jobs** (`RoomProviderRepository.kt:256-275` — no
   `cancelUniqueWork`). A running series-details worker keeps firing its remaining `getSeriesInfo` calls
   after the playlist is deleted (`:368` loop has no provider-existence/cancel check).
3. **Each add uses a fresh `UUID.randomUUID()` provider id** (`:70`), so the unique work name differs per
   cycle → the `REPLACE` policy only supersedes work with the *same* id → workers from **multiple**
   add/delete cycles **stack and run concurrently** → several overlapping `getSeriesInfo × N` storms → 429.
4. **429 is not retried/paced.** `withXtreamRetry` (`XtreamContracts.kt:156-169`) retries only IOException
   + HTTP 5xx; any 4xx (incl. 429) throws immediately, no `Retry-After`/backoff.

### Redundant requests in one add cycle (~13 + N_series)
- Connection test `TestProviderConnectionUseCase.testXtream` (`:50-70`): **4** — `getUserInfo`,
  `getLiveStreams`, `getVodStreams`, `getSeries` — fetching full live/vod/series payloads **just to
  `.size` them**, ignoring the include flags (a live-only provider still downloads VOD+series).
- Playlist import: **7**.
- Series-details worker: **1 + N_series**.
- EPG auto-detect (new): **1** xmltv probe (streams the guide once to validate — 1 request, heavy body).
- `get_series` fetched **3×** (test / import / series-details); `get_userInfo` **2×**; live/vod streams **2×**
  (test result discarded, re-fetched by import seconds later).

### Reference apps (comparison only)
OwnTV: bulk all-categories, per-category only as an error fallback **paced 150 ms** to avoid 429, and
**series `get_series_info` deferred to on-demand** (not at sync). StreamVault: per-category but wrapped in
retry+backoff+adaptive throttle. Both **avoid eager per-series `get_series_info` at import** — the exact
gap in Vivicast.

### Chosen fixes (user decision: baseline hardening + on-demand series fetch; test kept as-is)

**1. Defer `getSeriesInfo` to ON-DEMAND — removes the `× N` storm entirely (the main fix).**
Series-flow map (verified):
- Eager writer `DefaultSeriesDetailsRefresher` (`RefreshExecution.kt:343-382`) = `getSeries` (redundant —
  the catalog refresh already has the list) + `getSeriesInfo` per series (`:368-370`) → global reconcile
  `importXtreamSeriesDetails` (`RoomCatalogImportRepository.kt:201-219`). **Hazard:** that method
  reconciles per *provider* — it clears the whole provider's episode stage and drops seasons not in the
  passed set (`:363,:431,:441`, doc `CatalogImportRepository.kt:26-28` "pass ALL series per run"). So it
  **cannot** be reused per single series.
- Read side (`feature/series`) reads seasons/episodes **purely from DB Flows** (`SeriesViewModel.kt:181-213`
  `observeSeasons`/`observeEpisodes`); there is **no fetch path** — it assumes the worker populated them.
  Empty DB → "no episodes" panel (`SeriesRoute.kt:411,425-431`).
- Nothing needs eager population of *all* series: search has no episode field (`RoomMediaRepository
  .search`); Home/WatchNext/Continue only touch episodes that already have playback progress — which only
  exists after the user opened+played that series (exactly when on-demand would have fetched it).

Implementation:
- **New per-series write method** `importXtreamSeriesDetail(providerId, XtreamSeriesInfo)` in
  `CatalogImportRepository`/`RoomCatalogImportRepository`: upsert one series' seasons+episodes and
  reconcile **within that one series** — reuse the existing per-info `buildSeasons`/`buildEpisodes`
  (`:455-486`) + the existing per-series scoped deletes `deleteSeasonsForSeries` / `deleteEpisodesForSeries`
  (`CatalogDao.kt:653,662`) for the delete-stale step, instead of the provider-wide stage merge. Focused
  unit test: single-series upsert must not touch a sibling series' rows.
- **On-open fetch trigger.** A small App-hoisted helper (collaborators = `providerRepository +
  xtreamClient + xtreamParser + catalogImportRepository`, the exact set already wired at `AppContainer.kt
  :377-382`) does: resolve `ProviderCredentials.Xtream` → `xtreamClient.getSeriesInfo(creds, series
  .remoteId)` → `xtreamParser.parseSeriesInfo(...)` → the new write method. Exposed to `feature/series` as
  an injected `suspend (Series) -> Unit` (e.g. `ensureSeriesDetail`), the same suspend-lambda pattern as
  `resolveSeriesPosterModel` (`SeriesRoute.kt:57`, wired `MainActivity.kt:880-881`) — so **no new module
  dep** (`feature/series` doesn't depend on `:iptv:xtream`; the helper lives App-side / `:data:media`).
  The ViewModel calls it from `onOpenSeriesDetail` (`SeriesViewModel.kt:243-246`), **guarded to fetch only
  when `observeSeasons` is empty** (avoid refetch on every open; fetch on open, not focus, to avoid
  scroll-storms). Seasons/episode Flows re-emit automatically once rows land.
- **Remove the eager path** (it IS the 429 storm): delete the `enqueueSeriesDetailsRefresh` call +
  `needsSeriesDetailsRefresh` flag (`RefreshExecution.kt:72-74,224`); the now-dead
  `DefaultSeriesDetailsRefresher`, `SeriesDetailsRefreshWorker` + its work contract, and the global
  `importXtreamSeriesDetails` reconcile can be removed too (deletion over dead code). This also cuts the
  redundant 3rd `get_series` fetch.

**2. Cancel the provider's workers on delete.** `deleteProvider` calls `RefreshWorkScheduler` to cancel
the provider's playlist-refresh unique work, so a delete mid-import stops the in-flight catalog requests
(and, combined with fix 1, there is no longer a series-details worker to outlive the delete). Goes
through the App-hoisted scheduler boundary. This is also the fix for the "stacked workers across
add/delete cycles" driver.

**3. Add 429 / `Retry-After` backoff** to `withXtreamRetry` (`XtreamContracts.kt:156-169`): treat HTTP 429
as retriable with a bounded backoff (honour `Retry-After` when present) instead of the current hard 4xx
failure. Protects every Xtream call (incl. rapid add/delete cycles and the on-demand `getSeriesInfo`).

**Kept as-is (user decision):** the connection test still checks all types (`testXtream` `:50-70`). It
calls `getSeries` once = a *series count*, never `getSeriesInfo`, so it downloads no episodes — nothing
wasteful. The remaining minor duplicates (`get_userInfo` 2×, live/vod streams test-vs-import) are left.

Change surface: `data/media` (`CatalogImportRepository`/`RoomCatalogImportRepository` new per-series
method + test), App-side helper + `SeriesRoute`/`SeriesViewModel` wiring (on-demand lambda),
`iptv/xtream/XtreamContracts.kt` (429 backoff), `worker/RefreshExecution.kt` + `RefreshWorkScheduler` +
`WorkerContracts` (remove eager series-details path; add cancel API if missing),
`RoomProviderRepository.deleteProvider` (cancel workers). No new module, no DI framework.

---

## Decisions (resolved with user)

1. **Task 2 delete fix → STRUCTURAL (best solution), with Room migration v18→v19.** Rowid-tied FTS so
   every delete is O(1) per row (see Task 2 "Chosen fix"). The two workaround variants are rejected.
2. **Task 3 series-info → baseline + defer `getSeriesInfo` to ON-DEMAND.** Ship the no-behavior-change
   baseline (cancel-on-delete, pace loop, 429 backoff, trim redundant fetches) **and** move series
   season/episode fetch to when a series is opened (episode detail then loads on first open). On-demand
   design pending the series-flow research (this plan's Task 3 gets that detail before implementation).
3. **Task 3 test fetch → KEEP AS-IS.** The connection test stays checking all types. Confirmed: it calls
   `getSeries` once = a *series count* (1 request), and never calls `getSeriesInfo` — so it does **not**
   fetch episodes. Nothing unnecessary there.

## Concurrency & multi-entity scenarios (adversarial pass 2)

### The concurrency model (verified)
- **Room = a single writer.** Every `withTransaction` serialises through one SQLite writer; WAL
  (`VivicastDatabaseFactory.kt:26`) lets reads run concurrently with the writer.
- **WorkManager = the default executor** (`AppContainer.kt:254`, no custom `Configuration`) → **workers
  for different providers/sources run in parallel** (their network+parse phases overlap).
- **Staged imports use chunked transactions** (`ChunkedTransaction.forEachChunkedTransaction` — one tx per
  chunk + `yield()`, `:17-20`) → the writer is **released between chunks**, so imports interleave with
  each other and with a delete (the ADR-012 non-blocking design).
- **The final delta-merge is ONE atomic transaction** (`RoomCatalogImportRepository.kt:82-107 / 145-196`,
  `RoomEpgRepository.importXmltv:264-275`). Live tables are written **only** by the merge; staging writes
  only `*_stage`.
- **`deleteProvider` is ONE transaction** (`RoomProviderRepository.kt:258-269`) → holds the writer for its
  whole duration. **Today (O(N²)) it holds the single writer for ~4 min → every other provider's
  merge/EPG-import stalls behind it app-wide.** Task 2 (fast delete) removes this cross-provider stall —
  a second, independent reason Task 2 matters for multi-playlist use.

### The load-bearing gap → existence guard (verified, must fix)
The merge does **no provider-existence re-check** — `DefaultPlaylistRefresher.refresh` checks only at the
top (`RefreshExecution.kt:202-206`), then fetches/stages/merges with no further check; the merge INSERTs
key on `:providerId` with no existence predicate and **there are no foreign keys** in the schema. Since
delete and merge serialise through the one writer, if delete commits first the merge re-inserts
channels/movies/series/episodes/categories/seasons (and EPG programs/mappings) for the **deleted
providerId → orphan live rows**. Cancellation is best-effort (async) and cannot prevent this ordering.
**Fix = re-check provider existence as the FIRST read *inside* each merge transaction** (catalog M3U +
Xtream, EPG `importXmltv`, and the new on-demand series write) and short-circuit to an empty result if the
provider is gone. Because it is inside the same serialised writer, it is decisive: delete-first → guard
sees null → skip; merge-first → delete removes the just-merged rows afterwards. `providerDao()` is already
on `VivicastDatabase`. The EPG guard also closes the narrower TOCTOU between the `activeProviderIds`
snapshot (`RefreshExecution.kt:453-455`) and each `importXmltv`.

### Scenario matrix (A/B = playlists, S1/S2 = EPG sources)
| # | Scenario | Behaviour (with the planned fixes) |
|---|----------|-----------------------------------|
| C1 | Delete A **while A's playlist refresh runs** | Delete cancels A's playlist worker (new cancel API, best-effort); the **in-merge existence guard** guarantees no orphan A rows even if cancel loses the race. A cancelled refresh never reaches `onSuccess`, so it triggers **no** EPG re-enqueue. |
| C2 | Delete A **while B's playlist refresh runs** | Different workers → **parallel** network/parse. DB writes **serialise** (single writer); B's import is chunked so it interleaves with A's (now fast) delete. B's guard sees B alive → B imports normally. No cross-cancel (per-provider work names). *Pre-Task-2 this stalled B for minutes.* |
| C3 | Delete A **while A's EPG source S1 refresh runs** | Delete does **not** cancel S1 (a source is independent, may serve others). S1's `importXmltv` for A is skipped by the existence guard; S1's own refresh + counts complete; S1 survives (unlinked from A). |
| C4 | Delete A, **S1 linked A+B**, S1 refreshing | S1 imports for A (skipped by guard) and B (normal). S1 keeps its B link and survives; only A↔S1 link is removed. |
| C5 | **S1 linked only to A**, delete A | A↔S1 link removed, A's `epg_programs` deleted, **S1 survives with 0 links** (by design — user deletes the source manually). A future S1 refresh imports for 0 providers (no-op writes, still stamps counts). |
| C6 | **A linked to S1+S2**, delete A | Both links removed, **both sources survive** (may serve others); A's programs from both are deleted. |
| C7 | A's playlist refresh **succeeds**, enqueues EPG for S1, **then** user deletes A | S1 refresh runs (KEEP-coalesced), imports for A (skipped by guard) + any other linked provider. Wasteful S1 run, but safe. |
| C8 | A refresh **cancelled by delete** | `onSuccess` not reached → **no EPG refresh enqueued** (directly answers the user's question). |
| C9 | Two playlists A+B, **both linked to S1**, both refresh | Both enqueue EPG-for-S1 on success → **KEEP coalesces to one S1 refresh**; S1 imports for A and B. |
| C10 | A deleting (slow, **pre-Task-2**) + B/C refreshing + S1 importing | All of B/C/S1's writes **stall behind A's 4-min write transaction**. Post-Task-2: A's delete <1s → negligible. |
| C11 | Two big imports A+B concurrently | Chunked → **interleave** (writer released between chunks) → both progress, no deadlock; the two atomic merges serialise briefly. |
| C12 | Add A (test+import+auto-EPG) while B refreshes | Serialised small writes interleave; auto-EPG `Mutex` serialises only auto-EPG detection. Fine. |
| C13 | Add A, **immediately delete A** before auto-EPG finishes | `autoDetectXtreamEpg` → `getCredentials` null → no-op (existing guard). If the source was already created+linked, it survives unlinked (C5). |
| C14 | Rapid add/delete across A **and** B within ~5 min | Task 3 (on-demand series + cancel-on-delete + 429 backoff) removes the request storm → no 429. |
| C15 | Delete A while A's **periodic** playlist refresh is scheduled | Delete also `cancelPlaylistPeriodic(A)` so it can't fire later for a dead provider. A's EPG source periodic is **not** cancelled (source independent). |
| C16 | **Process death** mid-delete | The single delete transaction rolls back atomically → no half-deleted provider; consistent on restart. Mid-refresh death → `recoverStuckRefreshState` clears all `*_stage` + resets Refreshing status at next start. |
| C17 | **Edit A with a source switch** (M3U↔Xtream or M3U url↔file), A idle | `updateProvider` runs `clearProviderCatalog` (`RoomProviderRepository.kt:169`) = the SAME bulk catalog+epg delete as `deleteProvider` → **today it hits the O(N²) FTS triggers → edit-with-source-switch is also slow.** Task 2's structural FTS fix fixes this path too (one transaction; no separate work). |
| C18 | **Edit A (source switch) while A's refresh runs** | Save fires `onRefreshProvider` = REPLACE → cancels the in-flight refresh and restarts with the new source/creds; `clearProviderCatalog` runs in the update tx. The old in-flight refresh (old creds) is cancelled; its merge is stopped by the same in-merge existence/settings state. Type→Xtream also fires auto-EPG (C12/C13 apply). |
| C19 | **Edit A (source switch) while B refreshes** | A's `clearProviderCatalog` is one write tx (fast after Task 2) → briefly serialises with B's chunked import; no lasting block. *Pre-Task-2 it stalls B like a slow delete.* |
| C20 | **Delete EPG source S1 while S1 is refreshing** | `deleteSource` deletes links/mappings/programs/source-row (`SecureEpgSourceRepository.kt:68-77`). Two gaps mirror playlist delete: (a) `deleteProgramsForSource` hits the O(N²) FTS triggers → **source delete is also slow** (Task 2 fixes it); (b) the running EPG refresh races the delete → needs the **source-existence guard in the `importXmltv` merge** + a **`cancelEpgRefresh(sourceId)`** on delete (see fixes). |
| C21 | **Delete EPG source S1 linked to A+B** | S1's programs for both A and B are removed; both A↔S1 and B↔S1 links go; the playlists survive with no EPG until re-linked. If a playlist refresh had just enqueued S1, that EPG work finds `getActiveSource(S1)=null` → clean no-op. |
| C22 | **Same playlist A refreshed twice at once** (interval fires during a manual/save refresh) | `RefreshRunGuard.tryEnter(A)` returns false for the 2nd → skipped (`RefreshExecution.kt:207-209`); WorkManager KEEP also coalesces. No double import. Same for EPG (`refreshRunGuard` keyed on `source.id`, `:485`). |
| C23 | **Unlink A from S1 while S1 refreshes** | The refresh's `activeProviderIds` snapshot (`:453-455`) may still include A → it imports `epg_programs` for A once more even though just unlinked (A still exists). Minor/eventually-consistent; the next refresh won't include A. Not a crash/orphan (A exists). Noted, not blocking. |
| C24 | **Playback of A's channel active while A is deleted/refreshed** | The player holds an already-resolved stream URL → current playback continues; the channel list/next-prev reads from DB and updates (empty after delete). On a TV you can't delete from Settings while the player is foreground, so this is a background-refresh edge; no crash (reads just re-emit). |

### Additional fixes folded into Tasks 2/3 (from this pass)
1. **In-merge existence guards** (load-bearing) — as the first read inside each merge transaction, skip if
   the target is gone:
   - **provider**-existence in the catalog M3U + Xtream merges and the new on-demand series write;
   - **provider**-existence in EPG `importXmltv` (per-provider loop) **and source**-existence (the `epg_sources`
     row still exists) — the latter covers deleting an EPG source mid-refresh (C20).
2. **New one-time cancel APIs** on `RefreshWorkScheduler` (today only `cancel*Periodic` exists):
   - `cancelPlaylistRefresh(providerId)` = `cancelUniqueWork(uniquePlaylistRefreshWork(providerId))`, called
     from `deleteProvider` (+ `cancelPlaylistPeriodic`). **Do NOT** cancel EPG workers on a *playlist* delete
     (a source is independent, may serve other playlists).
   - `cancelEpgRefresh(epgSourceId)` = `cancelUniqueWork(uniqueEpgRefreshWork(epgSourceId))`, called from
     `deleteSource` (+ the existing `cancelEpgPeriodic`) — deleting a source cancels *its own* refresh.
3. **Stage hygiene (optional, defense-in-depth):** `deleteProvider` clears the provider's
   `channels_stage/movies_stage/series_stage/episodes_stage`; `deleteSource` clears `epg_programs_stage`
   for the source (new DAO query) — so a refresh cancelled mid-stage doesn't orphan stage rows until the
   next app start.

Ordering in `deleteProvider` / `deleteSource`: cancel the relevant worker (best-effort) → the existing
delete transaction (now protected by the in-merge guard) → clear stage. Correctness rests on the in-merge
guard, not on cancel timing.

### Task 2's structural FTS fix also speeds up two more paths (no extra work)
The O(N²) FTS-trigger cost is shared by every bulk catalog/EPG delete, so the rowid-tied fix (Task 2)
**also** fixes: **editing a playlist's source/type** (`updateProvider` → `clearProviderCatalog`, C17) and
**deleting an EPG source** (`deleteSource` → `deleteProgramsForSource`, C20). One structural fix, four
fast paths (playlist delete, playlist source-switch edit, EPG-source delete, single-row edits).

## Before / during / after the rebuild (extra considerations)

**Before**
- **Dry-run the v18→v19 migration on the pulled real DB** (`scratchpad/vivicast.db`, 74 MB, 8900 movies /
  21887 epg_programs) with a throwaway sqlite script *before* wiring it into Room: apply the trigger
  rewrite + FTS rebuild, then assert (a) each `search_<x>_fts` docid == the base row's rowid, (b) a sample
  `MATCH` still returns the same rows, (c) row counts unchanged, (d) it finishes fast (it's O(N) bulk
  `INSERT … SELECT rowid …`, expect seconds even on this DB). This de-risks the migration cheaply.
- Migrations run **at first launch after update**, before the DB opens → a one-time startup cost. The
  O(N) rebuild is fine, but measure it on the big DB so we know the worst case.

**During**
- Implement in order **Task 1 → Task 2 → Task 3**, each with its own gate run + emulator check on **both**
  API 28 and 36 (Task 2 is a storage/migration change → test both floor and ceiling), separate commits on
  approval. The concurrency fixes (in-merge guards, cancel APIs, stage hygiene) ship with Tasks 2/3.
- The in-merge guard must be the **first read inside** the merge `withTransaction` (using
  `database.providerDao()` / the `epg_sources` read), else the serialise-ordering argument doesn't hold.
- **rowid stability check:** the staged delta-merge deletes-then-inserts *changed* rows, giving them new
  rowids; the rewritten triggers keep the FTS in sync (delete old rowid, insert new). Confirm the merge
  path never bypasses the triggers (it doesn't — all writes go through the base tables).
- **Detekt baseline:** new methods on already-baselined large files (`RoomProviderRepository`,
  `RefreshExecution`, `RoomCatalogImportRepository`) may re-key entries; regenerate + diff-verify like the
  auto-EPG change (only the touched signatures should move; no rules loosened).

**After**
- **Orphan sweep** on a freshly pulled DB after the concurrency tests: `SELECT COUNT(*)` grouped by
  `providerId` on channels/movies/series/episodes/categories/epg_programs/epg_channel_mappings must have no
  rows for a deleted provider id; same for `epgSourceId` after a source delete.
- **Search correctness** after the FTS migration: channels/movies/series/EPG search still returns results
  (the rebuilt FTS is intact).
- **On-demand series graceful failure:** opening a series while offline / with an expired account →
  `getSeriesInfo` fails → the screen shows the existing "no episodes" state, no crash, and a retry on the
  next open works. (No new error UI needed; just don't crash on the failed fetch.)
- Re-confirm WatchNext / continue-watching / home rails behave (they key off playback progress, unaffected).

## Validation (per fix, before "done")
- Static: `./gradlew.bat detekt assembleDebug test`.
- Task 1: emulator — add a playlist, confirm the User-Agent row shows and the connection test uses it.
- Task 2: emulator — delete the "Test" Xtream provider, confirm it completes in seconds (was ~4 min);
  re-add + delete an M3U-with-EPG, confirm fast; search still works after delete (FTS intact).
- Task 3: emulator — add+delete the same Xtream account 3–4× within ~5 min, confirm no 429; confirm a
  delete mid-import stops the series-info requests (logcat / diagnostics).
- Concurrency (two playlists A+B, an EPG source): (1) start A's refresh, delete A mid-import → A gone,
  **no orphan rows** for A (query counts by A's providerId = 0 across channels/movies/series/episodes/
  categories/epg_programs/mappings), search still works; (2) start B's refresh, delete A at the same time
  → B imports fully, delete is fast, neither hangs the other; (3) EPG source shared A+B, delete A →
  source survives, B's EPG intact; (4) delete A while A's playlist refresh runs → no EPG refresh gets
  enqueued for A. Verify orphan checks on the pulled DB with a sqlite count query.
