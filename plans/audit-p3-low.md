# Audit Remediation — Phase 3: Low Findings

> **STATUS: ✅ COMPLETED + shipped 2026-07-20.** All 11 Low findings resolved (10 fixed; `#20` deliberately
> left as cosmetic) plus `#35` (surfaced by the P2 review) and a `docs/logos` minSdk follow-up. Cluster-wise
> commits, gate (`detekt` + `assembleDebug` + `test`, plus androidTest compile for `#21`) green per cluster,
> **merged to `main` and pushed**; branch `audit/p3-low` deleted. **No schema change** (Room stays v21).
> Post-implementation: diagnostics/Protokoll logging needed **no** changes; docs — CLAUDE.md (#30 emulator
> floor API 26) + `docs/logos/README.md` (minSdk) updated, and `#21` removal now aligns with PRD-06
> ("Verlauf besitzt in v1 keine eigene DataStore-Einstellung"). Emulator-verified: the minSdk-26 build runs
> on API 36, data preserved, EPG re-import (#34) all SUCCESS, no crash. Decisions below are historical.

Source: `CODE_REVIEW.md` (2026-07-18). Scope = the **11 Low** findings — cleanup, consistency, data
hygiene, minor robustness. Full evidence in `CODE_REVIEW.md` (`CR #n`). **No code changes until GO.**
⚠ = decision needed.

## Global validation
```
.\gradlew.bat detekt
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

## Decision index
- ⚠ `#30` — raise `minSdk` to 28 vs document/test 23-27 as supported.
- ⚠ `#21` — wire a `history.enabled` toggle vs remove the unused preference.

## Decisions + validation (2026-07-20) — all re-validated against current code (bug real + fix viable)

| # | Decision |
|---|---|
| #16 | **Delete** `createRetrofit` + `implementation(libs.retrofit)` + the `retrofit` catalog alias (0 callers verified; app uses OkHttp directly). |
| #24 | Mark `SearchViewModel` / `SearchUiState` / `SearchViewModelFactory` **`internal`** (constructed only in `SearchRoute`, same module). |
| #25 | **Remove** the dead `EpgSourceRepository` / `ProviderRepository` imports — verified **100% import-only in 11 panels** (nonimport count = 0 each). |
| #23 | Add injectable `nowProvider: () -> Long = { System.currentTimeMillis() }` to Movies/Series VMs + their factories (mirror `LiveTvViewModel`). |
| #30 | Raise `minSdk` **23 → 26** (Android 8). Also update `scripts/start-tv-emulator.ps1` (`[ValidateSet(28,36)]` → `(26,28,36)` + the "floor API 28" comment/error) and CLAUDE.md's emulator line (add API 26 = Android 8 floor); document 26–27 as supported. Floor-smoke on an API-26 emulator. Check `scripts/check-environment.ps1` too. |
| #18 | In `importXtreamSeriesDetail`, inside the txn diff old (`getEpisodeIdsForSeries`) vs new episode ids and `deleteProgressForMediaIds(EPISODE, removedIds)` before/around `deleteEpisodesForSeries` (mirrors the provider-wide path). Reuses existing DAO methods. |
| #19 | Add `epgDao.deleteEpgChannelsForSource(sourceId)` to `SecureEpgSourceRepository.deleteSource`'s transaction (DAO already exists). One line. |
| #34 | **Lightweight, non-blocking:** keep the heavy auto-mapping OUTSIDE the txn; add a cheap in-txn purge of staged programmes whose local channel vanished (new DAO: `DELETE FROM epg_programs_stage WHERE providerId=? AND epgSourceId=? AND channelId NOT IN (SELECT id FROM channels WHERE providerId=?)`), called in the merge txn before the stage-insert steps. |
| #17 | Loop-read the 2-byte gzip signature until 2-or-EOF (or `DataInputStream.readFully` / okio `require(2)`), then unread + decide. No perf impact. Verify `PushbackInputStream` buffer ≥ 2. |
| #20 | **Leave** — cosmetic transient UiState; single-threaded + StateFlow dedups + self-heals. |
| #35 | Exclude **408, 425, 423** from `isTerminalHttpStatus` (transient → WorkManager backoff-retry); 429 stays transient. |
| #21 | **Remove** `history.enabled` — it only redundantly gates resume at `MainActivity:654` (`resumeLastChannelOnStart && history.enabled`, always true, no UI). Drop the `HistoryPreferences.enabled` field + DataStore key + backup export/restore field; simplify `MainActivity:654` to `resumeLastChannelOnStart`. No behaviour change. Compiler decides whether `HistoryPreferences`/`updateHistory` plumbing becomes fully empty (then remove) or stays a holder. |

## Implementation (after GO)
New branch `audit/p3-low`. Cluster-wise, gate (`detekt` + `assembleDebug` + `test`) + commit per cluster. #30 additionally a floor-smoke on an **API-26** emulator. Merge to `main` + push only on the user's separate approval.
Cluster order: (1) dead-code #16/#24/#25 · (2) data-hygiene #18/#19/#34 · (3) worker #35 · (4) robustness #17 · (5) consistency #23 · (6) config #30 (build.gradle + scripts + CLAUDE.md) · (7) #21.

## Post-implementation (after all fixes + green) — separate passes, NOT part of the fixes
1. **Diagnostics/Protokoll logging review:** walk every `diagnosticsStore.log(...)` + refresh diagnostics event and reconcile against the P3 rebuilds — does any log need info added / removed / changed? Known touch-points: `#35` (a 408/425 refresh now retries instead of a terminal `Failure` → the recorded refresh outcome shifts), `#34` (a purged-orphan-programmes count could warrant a metadata field), `#19`/`#18` (deleted-row counts). Add/adjust only where a real behaviour changed.
2. **Docs review:** `CLAUDE.md`, `README.md`, the 3 active architecture docs under `docs/`, **and** `vivicast-docs` — update for P3 changes (minSdk 26 + emulator floor API 26, removed Retrofit, removed `history.enabled`). Propose before editing.

---

## Dead code / dependencies

### `#16` (Low) — Retrofit is a shipped-but-unused dependency
**Files:** `core/network/build.gradle.kts:23`, `NetworkClientFactory.kt:71` (`createRetrofit`, zero callers).
**Fix:** delete `createRetrofit` + `implementation(libs.retrofit)`; drop the catalog alias if unused elsewhere (grep confirms none). **Ref:** CR #16.

### `#24` (Low) — Search presentation types are public; siblings are internal
**Files:** `feature/search/.../SearchViewModel.kt:28`, `SearchUiState.kt:9`, `SearchViewModelFactory.kt:11`.
**Fix:** mark all three `internal` (constructed only in `SearchRoute`, same module). **Ref:** CR #24.

### `#25` (Low) — Dead repository imports across ~10 settings panels
**Files:** `feature/settings/.../GeneralSettingsPanel.kt` (L74/83) + 10 sibling panels.
**Fix:** remove the unused `EpgSourceRepository`/`ProviderRepository` imports (panels drive data via the ViewModel). Note: detekt `style` ruleset is off, so the gate won't catch these. **Ref:** CR #25.

---

## Consistency

### `#23` (Low) — Movies/Series VMs hardcode `System.currentTimeMillis()`
**Files:** `feature/movies/.../MoviesViewModel.kt:183`, `feature/series/.../SeriesViewModel.kt:290`.
**Fix:** add `nowProvider: () -> Long = { System.currentTimeMillis() }` (mirror `LiveTvViewModel.kt:42`), thread through the factories, replace direct calls. Makes mark-seen deterministic in tests. **Ref:** CR #23.

### `#30` (Low) — minSdk=23 below the documented API-28 test floor ⚠
**Files:** `app/build.gradle.kts:13`.
**Fix (⚠ pick):** raise `minSdk` to 28 to match the tested floor; OR add an API-23 smoke pass to the release checklist and document 23-27 as supported. Align `build.gradle.kts` with CLAUDE.md either way. **Ref:** CR #30.

---

## Data hygiene (orphan / leaked rows)

### `#18` (Low) — On-demand series detail orphans `playback_progress`
**Files:** `data/media/.../RoomCatalogImportRepository.kt` (L240-241, `importXtreamSeriesDetail`).
**Fix:** before `deleteEpisodesForSeries`, diff `getEpisodeIdsForSeries` vs the fresh episode ids and `playbackDao.deleteProgressForMediaIds(providerId, "EPISODE", removedIds)` (mirror `mergeEpisodes`). **Ref:** CR #18.

### `#19` (Low) — Deleting an EPG source leaks `epg_channels` rows
**Files:** `data/epg/.../SecureEpgSourceRepository.kt` (L71-82, `deleteSource`).
**Fix:** add `database.epgDao().deleteEpgChannelsForSource(sourceId)` inside the delete transaction (DAO method already exists), next to the sibling deletes. **Ref:** CR #19.

### `#34` (Low) — EPG import maps from a non-transactional snapshot
**Files:** `data/epg/.../RoomEpgRepository.kt:197` (`importXmltv`).
**Fix:** read the provider's channels **inside** the EPG merge transaction (or re-validate each mapped channelId against the live catalog inside it), so a concurrent playlist refresh can't orphan programmes/mappings. Related to `#11`/`#31` — may be fixed together. **Ref:** CR #34.

---

## Robustness

### `#17` (Low) — gzip magic-byte sniff assumes a 2-byte read
**Files:** `iptv/xmltv/.../XmltvStreamParser.kt:21` (`maybeGunzip`).
**Fix:** fill the 2-byte signature in a loop until 2 bytes or EOF (or okio `BufferedSource.require(2)` / `DataInputStream.readFully`), unread exactly what was read, then decide. **Ref:** CR #17.

### `#20` (Low) — Multi-collector + full `rebuild()` can emit transient UiState
**Files:** `feature/movies/.../MoviesViewModel.kt` (L97/229) (+ identical LiveTv/Series).
**Fix (optional):** consolidate source flows into one `combine(...)` → single UiState, or conflate `rebuild()`. Acceptable to leave as-is (single-threaded, StateFlow dedups, self-heals). Lowest priority. **Ref:** CR #20.

### `#35` (Low, from the P2-review regression pass — not in CR) — `isTerminalRefreshError` treats transient HTTP 408/425 as terminal
**Files:** `worker/.../RefreshExecution.kt` (`isTerminalHttpStatus`, `this in 400..499 && this != 429`).
**Fix:** exclude **408** (Request Timeout) and optionally **425** (Too Early) / **423** (Locked) from the terminal set, so a provider returning 408 under load gets WorkManager backoff-retry instead of an immediate `Failure` for that cycle. Bounded impact (the next *scheduled* refresh still runs), hence Low. Optionally reconsider whether the broad `IllegalArgumentException` terminal catch is too wide. Surfaced by the P1/P2 post-implementation adversarial regression review (2026-07-19).

---

## Unused preference

### `#21` (Low) — `history.enabled` has no UI control ⚠
**Files:** `core/datastore/.../DataStoreUserPreferencesStore.kt:136`.
**Fix (⚠ pick):** add a toggle (`VivicastSettingsRow` + `SettingsViewModel.onHistoryChanged` → `updateHistory`); OR remove the unused preference + its backup field. (Gates resume-last-channel, not recording — no data-loss risk either way.) **Ref:** CR #21.
