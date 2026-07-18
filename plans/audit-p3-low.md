# Audit Remediation — Phase 3: Low Findings

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

---

## Unused preference

### `#21` (Low) — `history.enabled` has no UI control ⚠
**Files:** `core/datastore/.../DataStoreUserPreferencesStore.kt:136`.
**Fix (⚠ pick):** add a toggle (`VivicastSettingsRow` + `SettingsViewModel.onHistoryChanged` → `updateHistory`); OR remove the unused preference + its backup field. (Gates resume-last-channel, not recording — no data-loss risk either way.) **Ref:** CR #21.
