# Audit Remediation — Phase 2: Medium Findings

Source: `CODE_REVIEW.md` (2026-07-18). Scope = the **17 Medium** findings. Full evidence per finding in
`CODE_REVIEW.md` (`CR #n`). **No code changes until explicit GO.** ⚠ = decision needed before implementing.

Grouped by area, ordered so data-integrity / EPG issues (can corrupt or mislead) come before UX polish.
Current Room schema is **v19** — items that touch the schema note the migration cost.

## Global validation
```
.\gradlew.bat detekt
.\gradlew.bat assembleDebug
.\gradlew.bat test
```
Schema-touching items additionally need a Room migration + `VivicastDatabaseSchemaTest` update and an
upgrade smoke (install old build, upgrade, verify no data loss). EPG/playlist items need the multi-playlist
multi-EPG smoke (add 2 playlists, link 2 EPG sources, refresh, verify guide/logos).

## Decision index (discuss before GO)
- ⚠ `#3` — encrypt File-mode M3U vs record a signed-off cleartext exception.
- ⚠ `#4` — trim the support export vs gate personal data behind an explicit opt-in.
- ⚠ `#10` — ExoPlayer lifecycle: process-scoped + ProcessLifecycle release, vs Activity-scoped, vs delete the dead `release()` path.
- ⚠ `#9` — favorites `sortOrder`: incrementing-Int (no schema change) vs `Long` end-to-end (schema change).
- ⚠ `#31` — shared tvg-id: one-to-many mapping (schema/key change) vs document the limitation.
- ⚠ `#14` — animation-speed: wire it up vs remove the row + persisted/backup fields.

---

## Data & DB correctness

### `#11` (Med) — Source-switch race resurrects old catalog
**Files:** `data/media/src/main/java/com/vivicast/tv/data/media/RoomCatalogImportRepository.kt` (L89/121/160); `RoomProviderRepository.updateProvider`.
**Fix:** stamp providers with a **source epoch** bumped by `create/updateProvider` on any source change; capture at refresh start, re-check inside the merge transaction, skip if changed. Also cancel the in-flight playlist refresh in `updateProvider` (as `deleteProvider` does) and use REPLACE for the corrective refresh.
**Schema:** +1 column on providers → **Room v20 + migration**.
**Validate:** switch a provider's source mid-refresh → old channels/URLs must not reappear.
**Ref:** CR #11.

### `#9` (Med) — favorites `sortOrder` is a dead constant ⚠
**Files:** `data/favorites/src/main/java/com/vivicast/tv/data/favorites/RoomFavoritesRepository.kt:54`.
**Fix (⚠ pick):** simplest = assign `max(sortOrder)+1` per `(providerId, mediaType)` (no schema change); or make `sortOrder` a `Long` end-to-end (schema change + migration); or drop the column and order by `createdAt DESC`.
**Validate:** favorites keep intended insertion order; `FavoritesDao.observeFavorites` ordering test.
**Ref:** CR #9.

### `#8` (Med) — Live-channel history rewritten every 1s
**Files:** `data/playback/src/main/java/com/vivicast/tv/data/playback/PlaybackProgressRecorder.kt` (L30-45).
**Fix:** apply the existing 10s throttle to the Channel branch (skip when `clock()-lastSaved < INTERVAL` unless `forceSave`/status transition; update the map after writing), or write channel history only on open + final forced save.
**Validate:** recorder unit test — Channel branch respects throttle (extend `PlaybackProgressRecorderTest`).
**Ref:** CR #8.

### `#10` (Med) — ExoPlayer never released ⚠
**Files:** `app/src/main/java/com/vivicast/tv/MainActivity.kt` (L204-214); `core/player/.../VivicastPlayerController.kt` (L844-853).
**Fix (⚠ pick):** process-scoped singleton + release on true app exit via a `ProcessLifecycleOwner` observer in `VivicastApplication` (make controller re-creatable); OR Activity-scope it and `release()` in `onDestroy`; OR if process-life retention is intended, delete the unused `release()` path and document it.
**Caution:** a naive `onDestroy` release breaks locale-change `recreate()` (reuses the singleton).
**Validate:** playback still works across back/stop/locale-change; the chosen teardown actually runs.
**Ref:** CR #10.

---

## EPG / Playlist cross-scenarios

### `#31` (Med) — Shared tvg-id → blank guide for the duplicate ⚠
**Files:** `data/epg/src/main/java/com/vivicast/tv/data/epg/RoomEpgRepository.kt` (L234-236).
**Fix (⚠ pick):** make the relation one-to-many — build `Map<String, List<String>>` (all local channels per `epgChannelId`), emit one staged programme per mapped local channel, and include `localChannel.id` in `epgProgramId`/stable key so per-channel rows stop colliding; OR document the shared-owner limitation.
**Schema:** `epgProgramId` key change → existing `epg_programs` rows rebuild on next refresh (or a migration to clear them). Note interaction with the v16 providerId-in-UNIQUE fix.
**Validate:** two channels sharing one tvg-id (HD/SD) both show programmes.
**Ref:** CR #31.

### `#32` (Med) — EPG-link priority from stale UI snapshot drops a link
**Files:** `feature/settings/src/main/java/com/vivicast/tv/feature/settings/SettingsRoute.kt:484`.
**Fix:** compute next priority **inside the repository/DAO transaction** (`MAX(priority)+1` atomically), mirroring `AutoXtreamEpgSourceUseCase.nextPriority`, instead of passing a UI-computed priority. Also surface the swallowed error (SettingsViewModel `runCatching`) instead of logging a false `source_linked`.
**Validate:** two fast link presses on two sources → both links persist; no silent drop.
**Ref:** CR #32.

### `#33` (Med) — Channel-logo subquery ignores priority + isActive
**Files:** `core/database/src/main/java/com/vivicast/tv/core/database/dao/CatalogDao.kt` (L704-708, `EPG_ICON_SUBQUERY`).
**Fix:** mirror the winner subquery (`EpgDao.kt:60-88`): `JOIN epg_sources s ON s.id=m.epgSourceId AND s.isActive=1` and `JOIN provider_epg_sources pes ON pes.providerId=c.providerId AND pes.epgSourceId=m.epgSourceId`, order by `m.isManual DESC, pes.priority ASC`.
**Validate:** logo comes from the same (active, highest-priority) source as the guide; deactivated source's logo disappears.
**Ref:** CR #33.

### `#13` (Med) — Cancelled refresh stuck at REFRESHING
**Files:** `worker/src/main/java/com/vivicast/tv/worker/RefreshExecution.kt` (L195/212/216-218, `DefaultPlaylistRefresher`).
**Fix:** reset the transient status on cancellation — in the `CancellationException` branch restore prior/Active status before rethrowing, or move a status reset into `finally` (mirror the EPG refresher).
**Validate:** cancel a playlist refresh (WorkManager stop) → provider returns to Active, "Gruppen verwalten" not locked.
**Ref:** CR #13.

---

## Security (on-device data-at-rest)

### `#3` (Med) — File-mode M3U body stored in cleartext ⚠
**Files:** `data/provider/src/main/java/com/vivicast/tv/data/provider/DiskM3uFileSourceStore.kt:26`.
**Fix (⚠ pick):** wrap File-mode content in an AES/GCM envelope from the existing AndroidKeyStore key (mirror `AndroidKeystoreSecureValueStore`), or route through `SecureValueStore`; OR record a signed-off exception (the archived plan's cleartext rationale is stale vs current code — every sibling secret is encrypted).
**Validate:** stored file is ciphertext; import/playback still resolve stream URLs.
**Ref:** CR #3.

### `#4` (Med) — Support export bundles history/favorites/search ⚠
**Files:** `app/src/main/java/com/vivicast/tv/backup/StandardBackupExporter.kt` (L81-88); consumed at `MainActivity.kt:1118→1122`, `DiagnosticsStore.kt:266`.
**Fix (⚠ pick):** reduce the support export to non-personal fields (counts/settings); drop/aggregate `playbackProgress`/`channelHistory`/`favorites`, omit raw `searchHistory` + provider names; OR gate their inclusion behind an explicit opt-in in the export dialog. (Violates PRD-11 "Ausgeschlossene Daten" / 08-android-tv-security §9.7 / ADR-014.)
**Validate:** exported `diagnostics-metadata.json` contains no search queries / history / provider names.
**Ref:** CR #4.

### `#5` (Med) — Provider host/IP leaks past diagnostics redaction
**Files:** `app/src/main/java/com/vivicast/tv/diagnostics/DiagnosticsSanitizer.kt:11`; source `worker/src/main/java/com/vivicast/tv/worker/RefreshExecution.kt:151`.
**Fix:** at the log source, for `IOException` subtypes log `it::class.java.simpleName` not `it.message`; and/or add bare `host:port` / IP patterns to the sanitizer, and/or add `"error"` to `SENSITIVE_KEY`.
**Validate:** test — `Unable to resolve host "x.y.z"` is redacted in the persisted log.
**Ref:** CR #5.

---

## Localization / Settings

### `#12` (Med) — Hardcoded badge/placeholder strings bypass locale
**Files:** `core/designsystem/src/main/java/com/vivicast/tv/core/designsystem/VivicastCards.kt` (L171/237/294/338-340).
**Fix:** replace literals with `stringResource` — reuse `livetv_live_badge`/`livetv_badge_catchup`; add resources for the favorite badge, "Kein Poster", and the rating prefix in **both** `values/` and `values-en/` (strings live only in `:core:designsystem`).
**Validate:** switch app language → badges/placeholders follow the locale.
**Ref:** CR #12.

### `#15` (Med) — Language setting doesn't round-trip through restore
**Files:** `app/src/main/java/com/vivicast/tv/backup/StandardBackupRestorer.kt` (L121-124).
**Fix:** in the restore success path (has a Context) call `LocaleHelper.save(context, restored.appearance.language…)` then `activity.recreate()`, so the applied locale matches the restored preference.
**Validate:** back up in one language, restore in another → whole UI switches, not just the Settings label.
**Ref:** CR #15.

### `#14` (Med) — "Animationen" setting is a no-op ⚠
**Files:** `feature/settings/src/main/java/com/vivicast/tv/feature/settings/AppearanceSettingsPanel.kt` (L206-215).
**Fix (⚠ pick):** wire it — provide `LocalAnimationSpeed` from `appearance.animationSpeed` at the composition root and scale transition durations / disable when Off (mirror how `fontScale` is wired into 6 designsystem files); OR remove the row + its persisted/backup fields.
**Validate:** Off actually removes motion; or the row is gone and backup no longer carries the field.
**Ref:** CR #14.

---

## Architecture

### `#22` (Med) — AppContainer holds business logic
**Files:** `app/src/main/java/com/vivicast/tv/di/AppContainer.kt` (L500-528 `ensureSeriesDetail`, L535-573 `autoDetectXtreamEpg`).
**Fix:** extract into use-cases in `:data` (e.g. `EnsureSeriesDetailUseCase` in `:data:media`; fold auto-EPG steps into `AutoXtreamEpgSourceUseCase` in `:data:epg`), injecting the Xtream client/parser + repos; leave AppContainer to instantiate + delegate. Follows the existing `TestProviderConnectionUseCase` pattern.
**Validate:** `detekt`; behavior unchanged (on-demand series detail still loads; Xtream save still auto-creates EPG).
**Ref:** CR #22.

---

## Android TV / UX

### `#26` (Med) — AsyncImage has no error/fallback
**Files:** `core/designsystem/.../VivicastCards.kt` (L146/264/371), `VivicastPanels.kt:122`; shared `ImageLoader` at `AppContainer.kt:282-293`.
**Fix:** give each `AsyncImage` an `error`/`fallback` rendering the same initials/"?" placeholder as the null-model branch (Coil3 `error=`/`fallback=`, or `SubcomposeAsyncImage`); optionally a global error painter on the `ImageLoader`.
**Validate:** a 404 logo/poster shows the initials placeholder, not an empty box.
**Ref:** CR #26.

### `#27` (Med) — Reorder list yanks D-pad focus to the top on every drop
**Files:** `core/designsystem/src/main/java/com/vivicast/tv/core/designsystem/VivicastReorder.kt:61`.
**Fix:** focus the first row only on initial open (latch a `remember { false }` flag instead of firing on every `items` change); after an external update re-request focus on the last-dropped id, not `items.first()`.
**Note:** existing plan `reorder-dpad-list.md` (kept in `plans/`) covers this area — reconcile with it before implementing.
**Validate:** move + drop a row → focus stays on the moved row.
**Ref:** CR #27.
