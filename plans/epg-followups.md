# EPG follow-ups: winner-aware search · no-retry-on-deterministic · stage hygiene · Xtream server-change

> Status: **COMPLETED 2026-07-18 (committed + pushed, cfa8714).** All 4 fixes + Fix 5
> (deactivated/deleted-source skip, found mid-work) done; also repaired 30 pre-existing red EPG
> androidTests. Gates green (assembleDebug,
> testDebugUnitTest incl. the new AutoXtream server-change test, detekt). Fix 1 winner-search + Fix 4
> unit test pass; RoomEpgRepositoryTest (20) + RoomMediaRepositoryTest (10) pass on the API-36 emulator.
> **Also repaired pre-existing androidTest breakage** (red on main since the winner-query fc2cb03 +
> import provider-existence-guard 948c277): those EPG tests seeded programmes without a provider row /
> provider_epg_sources link / channel mapping that the new queries require — added the missing seed
> (provider rows, source links, channel mappings). Researched 2026-07-18, HEAD 9221175.
> Four independent fixes chosen by the user (from the four plans' out-of-scope lists). #2b (ADR-002 rework)
> and #5 (reorder polish) explicitly dropped.

---

## Fix 1 — winner-aware EPG search (#1)

**Problem:** the EPG search group can show the *same* programme once per source when a channel is matched
by ≥2 linked sources — it never applies the per-channel priority winner (the display path got that in the
priority feature; search did not).

**Real query:** `SearchDao.searchEpg` (`SearchDao.kt:90-100`), FTS4-backed:
```sql
SELECT epg_programs.* FROM search_epg_fts
INNER JOIN epg_programs ON epg_programs.id = search_epg_fts.programId
INNER JOIN channels ON channels.id = epg_programs.channelId
WHERE search_epg_fts MATCH :matchQuery
ORDER BY epg_programs.startTime, channels.name COLLATE NOCASE, epg_programs.title COLLATE NOCASE
LIMIT :limit
```
Called by `RoomMediaRepository.kt:163`. (`EpgDao.searchPrograms` — the old `LIKE` query at `EpgDao.kt:108`
— has **no callers**; dead code, remove it in this fix.)

**Change:** add the same winner condition used by `observeProgramsForChannel` — keep a row only if its
source is the winner for its own `(providerId, channelId)`:
```sql
  AND epg_programs.epgSourceId = (
      SELECT m.epgSourceId FROM epg_channel_mappings m
      INNER JOIN epg_sources s ON s.id = m.epgSourceId
      INNER JOIN provider_epg_sources pes
          ON pes.providerId = m.providerId AND pes.epgSourceId = m.epgSourceId
      WHERE m.providerId = epg_programs.providerId
          AND m.channelId = epg_programs.channelId
          AND s.isActive = 1
      ORDER BY m.isManual DESC, pes.priority ASC
      LIMIT 1
  )
```
Correlated on `epg_programs.providerId/channelId` (unlike the display query, which binds fixed params).
The FTS `MATCH` + `LIMIT` bound the candidate set first, and the subquery scans only a provider's few
linked sources. `ponytail:` note the per-row subquery; memoize/denormalize only if it profiles hot. The
winner SQL now lives in two DAO queries — if the winner rule ever changes, update **both**
(`observeProgramsForChannel` + `searchEpg`).

**Files:** `core/database/dao/SearchDao.kt` (query), `core/database/dao/EpgDao.kt` (delete dead
`searchPrograms`). No schema/migration (query-only).

**Test:** `RoomMediaRepositoryTest` (or a SearchDao androidTest) — a channel matched by two linked sources
(priority 1 & 2) with a programme whose title matches: search returns **one** hit (the priority-1 source);
a manual mapping to the lower source flips the winner; an unmatched-by-higher channel falls to the lower.

---

## Fix 2 — don't retry a deterministic refresh failure (#2a)

**Problem:** `runEpgRefresh` / `runPlaylistRefresh` return `RefreshWorkerResult.Retry` on **any** thrown
error (`RefreshExecution.kt:96-98, 124-126`). A deterministic failure (e.g. a DB constraint) then spins a
WorkManager exponential-backoff loop, re-downloading the feed each attempt (exactly the 5–6 min lag seen
after the v20 fix). Retrying a deterministic error never helps.

**Change:** categorize the throwable in both `onFailure` branches — a DB **constraint** failure is
deterministic → `Failure`; everything else (IO/network/HTTP/timeout, transient DB lock) stays `Retry`:
```kotlin
private fun Throwable.isDeterministicRefreshError(): Boolean =
    generateSequence(this) { it.cause }.any { it is android.database.sqlite.SQLiteConstraintException }
// in each onFailure:
if (error.isDeterministicRefreshError()) RefreshWorkerResult.Failure else RefreshWorkerResult.Retry
```
Scope: the two `onFailure` lambdas in `runEpgRefresh` (`:124`) and `runPlaylistRefresh` (`:96`). Leave the
`else -> Retry` (non-exception `outcome.success == false`, e.g. a source URL momentarily unresolved) as
**Retry** — that is transient. `Failure` still records the `EpgRefreshFailed`/playlist diagnostic (the
`recordRefresh` call stays before the return).

**Files:** `worker/RefreshExecution.kt` (helper + two `onFailure` branches).

**Test:** extend `RefreshExecutionTest` — a fake refresher throwing `SQLiteConstraintException` →
`runEpgRefresh`/`runPlaylistRefresh` return `Failure`; throwing `IOException` → `Retry`.

**Note:** this is defense-in-depth — the v20 index fix removed the *current* constraint trigger. It stops
*future* deterministic import bugs from becoming backoff loops.

---

## Fix 3 — stage hygiene on delete (#3)

**Problem:** `deleteProvider` / `deleteSource` remove live rows but not the staging tables, so a delete
racing a mid-stage refresh leaves orphan `*_stage` rows until `recoverStuckRefreshState` clears them on the
next app start.

**deleteProvider** (`RoomProviderRepository.kt:256-270`) — inside the existing `withTransaction`, add:
```kotlin
catalogDao.clearChannelsStage(providerId)   // all exist (per-provider): :385/:437/:491/:545
catalogDao.clearMoviesStage(providerId)
catalogDao.clearSeriesStage(providerId)
catalogDao.clearEpisodesStage(providerId)
epgDao.clearProgramsStageForProvider(providerId)   // NEW DAO query
```
**deleteSource** (`SecureEpgSourceRepository.kt:68-77`) — inside its `withTransaction`, add:
```kotlin
epgDao.clearProgramsStageForSource(epgSourceId)    // NEW DAO query
```

**New DAO queries** (`EpgDao.kt`, no schema change — `epg_programs_stage` already exists):
```kotlin
@Query("DELETE FROM epg_programs_stage WHERE providerId = :providerId")
suspend fun clearProgramsStageForProvider(providerId: String)

@Query("DELETE FROM epg_programs_stage WHERE epgSourceId = :epgSourceId")
suspend fun clearProgramsStageForSource(epgSourceId: String)
```

**Files:** `core/database/dao/EpgDao.kt` (2 queries), `data/provider/RoomProviderRepository.kt`,
`data/epg/SecureEpgSourceRepository.kt`.

**Test:** repo androidTest — stage a row for (provider, source), then `deleteProvider` / `deleteSource`,
assert the matching `epg_programs_stage` / catalog-stage rows are gone.

---

## Fix 4 — Xtream server-change unlinks the superseded old-server source (#4)

**Problem (S5b):** `AutoXtreamEpgSourceUseCase.ensureFor` dedups by **server+username** (`sameEndpoint`).
If the user edits the provider to a **new server** (same username), `sameEndpoint` doesn't match → a **new**
EPG source is created + linked, but the **old-server** source stays linked to this provider with a now-dead
URL (its refresh 401s harmlessly, and it can win the priority race with stale data).

**User's decision:** on server-change, create+link the new source (already happens) **and** deactivate the
old link **for this playlist** — i.e. **unlink** the old-server source from this provider. Do **not** delete
the source (it may still serve other playlists / be active elsewhere). (There is no "linked-but-inactive"
link state; unlinking is the per-playlist deactivation. Confirm this reading.)

**Change** in `ensureFor`, only in the **no-match branch** (a genuinely new endpoint was created — on a
first-ever auto-EPG this loop finds nothing, so it is a no-op; on a server-change it finds the old source):
```kotlin
if (match == null) {
    // Unlink this provider's OTHER xmltv sources for the SAME username but a DIFFERENT endpoint
    // (superseded old-server auto-EPG). Keep the sources globally; unlink also purges this provider's
    // stale programmes+mappings for them (the F1 cleanup in unlinkSourceFromProvider).
    repository.observeProviderEpgSources(providerId).first()
        .filter { it.epgSourceId != sourceId }
        .filter { link ->
            val url = repository.getSourceUrl(link.epgSourceId)
            url != null && parseEndpoint(url)?.username == username.trim() && !sameEndpoint(url, xmltvUrl)
        }
        .forEach { repository.unlinkSourceFromProvider(providerId, it.epgSourceId) }
}
```
Reuses the existing private `sameEndpoint` / `parseEndpoint` / `username()` helpers (move the unlink loop
into the class so they're in scope, or expose them). Identity = same username + xmltv endpoint + different
host/port/path ⇒ superseded. A legitimately-different account (different username) is not touched.

**Edge / confirm:**
- Heuristic = "same username, different xmltv endpoint." A user who manually linked a *different* provider's
  xmltv with the *same* username would also be unlinked — uncommon; the username+endpoint signal is the best
  available (no "auto-created" flag on sources). Acceptable; note it.
- Unlink runs after the new link is created, so the provider is never left with zero EPG.
- Diagnostics: the unlink fires the new `epg/source_unlinked` event (from the P1/P2 logging) — good, it's
  traceable. (`ensureFor` is App-invoked; the event is emitted by the settings unlink path only, not here —
  so `ensureFor`'s unlink is silent unless we add an App-side log. `ponytail:` optional — add an
  `epg/source_superseded` diagnostic if we want the auto-unlink visible; else leave silent like the rest of
  auto-EPG.)

**Files:** `data/epg/AutoXtreamEpgSourceUseCase.kt`.

**Test:** `AutoXtreamEpgSourceUseCaseTest` — the `FakeRepo` records unlink calls; a first save links the new
source and unlinks nothing; a second `ensureFor` with the **same username, different host** links the new
source **and** unlinks the old one; a same-endpoint reuse unlinks nothing.

---

## Fix 5 — a refresh for a deactivated/deleted source (or deleted provider) skips, doesn't retry-loop

**Problem (found by the user):** deactivating an EPG source while a refresh for it is enqueued/looping →
`DefaultEpgRefresher.refresh` hits `getActiveSource == null` (the `isActive` filter) → returns
`success=false` → `runEpgRefresh` maps success=false (non-skip) to **Retry** → WorkManager backoff loops
forever (getActiveSource stays null), each attempt failing instantly (`durationMs=0`) and spamming
`EpgRefreshFailed` every 1–2 min.

**Fix (chosen: A, skip only — the load-bearing fix):** treat "resource no longer active/resolvable" as a
**skip**, so the worker returns Success and WorkManager does not retry:
- `DefaultEpgRefresher.refresh`: `getActiveSource null → EpgRefreshOutcome(success=false, skipped=true)`
  (was `success=false`). EPG `success=false` is *only* this case (transient errors throw → onFailure →
  Retry), so this is safe. `skipped -> Success` + the `!skipped` guard on the failed-log → no loop, no
  false `EpgRefreshFailed`.
- `DefaultPlaylistRefresher.refresh`: `getProvider null (deleted) → …, skipped=true` for symmetry (a
  merely *deactivated* provider was already handled as `success=true` at the `!provider.isActive` check, so
  the playlist never had the deactivate-loop; only a deleted provider could loop).

The worker-side skip is load-bearing because a cancel is best-effort and can't reliably stop an already
in-flight / mid-backoff worker — the worker must re-check the resource itself. **Hardening (B: cancel the
one-time+periodic refresh immediately on deactivate) was declined** — the skip fully stops the loop + spam,
and the existing C1 model already cancels periodics on the next foreground/background transition.

**Test:** `RefreshExecutionTest.epgRefreshSkipsWhenSourceInactiveOrGone` — a `DefaultEpgRefresher` whose
reader returns null → outcome `success=false, skipped=true`.

## Cross-cutting

- **Order of work:** Fix 3 (DAO queries + delete wiring) → Fix 2 (worker) → Fix 1 (search query) → Fix 4
  (use case). Independent; any order compiles.
- **No schema change / no migration** in any of the four (all query-only or logic-only; `epg_programs_stage`
  already exists).
- **Gates:** `assembleDebug`, `testDebugUnitTest`, `detekt`; androidTests for Fix 1 (search winner) + Fix 3
  (stage clear) on the emulator; `AutoXtreamEpgSourceUseCaseTest` + `RefreshExecutionTest` on the JVM.
- **detekt:** none of these grow a baselined signature (SearchDao/EpgDao queries, a worker helper, a repo
  method, a use-case loop) — watch anyway; extract if a method trips a threshold.

## Not doing (this plan)
- #2b ADR-002 single-copy EPG storage rework (large, separate). #5 reorder polish. Winner-aware search does
  not change the display path (already winner-aware) or the FTS mirror/triggers (only the read query).
