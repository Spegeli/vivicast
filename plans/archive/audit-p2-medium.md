# Audit Remediation — Phase 2: Medium Findings

> **STATUS: ✅ COMPLETED + shipped 2026-07-19.** All 17 Medium findings implemented, gated per cluster
> (detekt + assembleDebug + test green), **merged to `main` and pushed** (up to `fe8fcda`); feature branch
> `audit/p2-medium` deleted (local + origin). Room **v20→v21** (#11 `sourceEpoch` + `Migration20To21`).
> Two additions beyond the 17, from the post-implementation diagnostics review:
> - `#13` follow-up — `PlaylistRefreshCancelled` / `EpgRefreshCancelled` diagnostics events (a cancelled
>   refresh previously vanished from the Protokoll).
> - Host-redaction fix — the bare provider host leaked into the shareable diagnostics export via the
>   network-event logger; added `host` to `DiagnosticsSanitizer` (key-blank + line SECRET pattern).
>
> Emulator-verified (API 36, debug): v21 migration clean + data preserved, #10 background-release
> (`ExoPlayerImpl: Release`), #3 M3U file encrypted on disk, #31 HD/SD 7/7, #32 6 links, #4 export trim.
> User confirmed #14/#12/#15 manually. `#22` extracted `EnsureSeriesDetailUseCase` (:data:media) + moved the
> auto-EPG dedup mutex into `AutoXtreamEpgSourceUseCase`, but did **not** fold autoDetectXtreamEpg's core into
> :data:epg (that module lacks :iptv:xtream + the validation is App-hoisted). Decisions below are historical.

Source: `CODE_REVIEW.md` (2026-07-18). Scope = the **17 Medium** findings. Full evidence per finding in
`CODE_REVIEW.md` (`CR #n`). **No code changes until explicit GO.** ⚠ = decision needed before implementing.

Grouped by area, ordered so data-integrity / EPG issues (can corrupt or mislead) come before UX polish.
Current Room schema is **v20** — items that touch the schema note the migration cost.

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

## Implementation sequencing & overlaps (validated 2026-07-18)

Implement by **shared-file cluster**, not by finding number — so a later finding never has to re-edit what an
earlier one changed. Run `detekt` + `assembleDebug` + `test` after each cluster; re-read the plan for the next
cluster before starting it. Recommended order:

1. **`#11` first** — the only schema change (Room **v21** + migration + `VivicastDatabaseSchemaTest`). Land it green before other data edits build on the new schema.
2. **Isolated data:** `#9` (favorites), `#8` (recorder), `#3` (M3u file-store encryption). Independent files.
3. **EPG + its use-case:** `#33` (CatalogDao subquery), `#31` (RoomEpgRepository.importXmltv), then **`#32` + `#22` together** — both touch the `:data:epg` link/use-case area (`AutoXtreamEpgSourceUseCase`, `linkEpgSourceToProvider`).
4. **Worker:** `#5` + `#13` together — both edit `RefreshExecution.kt` (different functions; P1's #6 already lives there).
5. **Player/app lifecycle:** `#10` — ProcessLifecycle release + re-creatable controller; coordinate with the AppContainer wiring touched by #22.
6. **Backup:** `#4` + `#14` + `#15` together — all in the backup export/restore area.
7. **Designsystem/UI:** `#12` + `#26` together (both edit `VivicastCards.kt`, overlapping regions); `#27` (VivicastReorder).

**Overlap clusters (edit together):** `#12`+`#26` (VivicastCards.kt) · `#5`+`#13` (RefreshExecution.kt) · `#31`+`#32` (RoomEpgRepository.kt) · `#32`+`#22` (:data:epg use-case) · `#4`+`#14`+`#15` (backup) · `#22`+`#10` (AppContainer wiring).

## Post-implementation (after all 17 done + green) — separate pass

**Diagnostics/logging (`Protokoll`) review:** once the 17 are implemented and tested, walk every
`diagnosticsStore.log(...)` event and reconcile against the rebuild — remove events for deleted paths, update
metadata whose shape changed, add events for genuinely new behaviors. Known touch-points: `#22` (the 4
EPG/series events move to a result-driven App wrapper — keep them firing), `#32` (false `source_linked` →
real outcome), `#13` (a cancelled-refresh status-reset may warrant an event). **Explicitly not part of the 17.**

---

## Data & DB correctness

### `#11` (Med) — Source-switch race resurrects old catalog — ✅ decided (airtight)
**Decision (2026-07-18):** airtight guard (Option 1) — reject an in-flight OLD-source refresh in the merge when the provider's source changed since refresh start.
**Files:** `data/media/.../RoomCatalogImportRepository.kt` (merge guard L89/160); `RoomProviderRepository.updateProvider` (+ `clearProviderCatalog`).
**Fix:**
- Add a dedicated `sourceEpoch` Int on the provider, bumped **only** on a source switch in `updateProvider` (0 on `createProvider`). Capture it at refresh start; re-check inside the merge transaction; skip the merge if it changed. → Room **v21 + migration** (small additive column, follows the existing v19→v20 pattern) + `VivicastDatabaseSchemaTest` update.
  - `updatedAt` is deliberately **not** reused: by the app's design it reflects a *successful refresh* (UI "Updated"), not a plain save, and it would also false-skip on a name-only edit or a concurrent refresh's completion.
- Defense-in-depth in `updateProvider`: clear the `*_stage` rows (mirror `deleteProvider`'s stage hygiene) and neutralize the provider's in-flight refresh; use REPLACE for the corrective refresh.
**Schema:** +1 column (`sourceEpoch`) → **Room v21 + migration**.
**Validate:** switch a provider's source mid-refresh → old channels/URLs must not reappear (multi-refresh smoke).
**Ref:** CR #11.

### `#9` (Med) — favorites `sortOrder` is a dead constant — ✅ decided
**Decision (2026-07-18):** repair `sortOrder` (Option 1) — insertion order (oldest-first), reorder-ready, **no schema change**.
**Files:** `data/favorites/.../RoomFavoritesRepository.kt:54` (+ a `FavoritesDao` max-sortOrder query).
**Fix:** replace the `now.coerceAtMost(Int.MAX_VALUE…)` clamp with `favoritesDao.maxSortOrder(providerId, mediaType) + 1` (0 when the group is empty). Keep `ORDER BY sortOrder, createdAt DESC`.
**Validate:** favorites keep insertion order; extend the favorites repo/DAO ordering test.
**Ref:** CR #9.

### `#8` (Med) — Live-channel history rewritten every 1s — ✅ decided
**Decision (2026-07-18):** 10s throttle (consistent with the movie/episode path, crash-safe).
**Files:** `data/playback/.../PlaybackProgressRecorder.kt` (L30-45).
**Fix:** in the Channel branch reuse `automaticProgressSaveTimes[playbackId]`: skip when `clock()-lastSaved < AUTOMATIC_PROGRESS_SAVE_INTERVAL_MILLIS` unless `forceSave` or a Playing↔Paused transition; otherwise write + update the map.
**Validate:** recorder unit test — Channel branch respects the throttle + forces on close/pause (extend `PlaybackProgressRecorderTest`).
**Ref:** CR #8.

### `#10` (Med) — ExoPlayer never released — ✅ decided (background-release)
**Decision (2026-07-18):** release the ExoPlayer when the app goes to **background** (frees resources on weak TV boxes); keep it **warm in the foreground** so channel zapping stays smooth. Re-create lazily on the next play.
**Files:** `VivicastApplication` (ProcessLifecycleOwner observer); `app/.../MainActivity.kt` (L204-214); `core/player/.../VivicastPlayerController.kt` (`release()` L850-853); AppContainer player wiring (make the controller re-creatable).
**Fix:**
- Add a `ProcessLifecycleOwner` observer (needs `androidx.lifecycle:lifecycle-process`) that calls `playerController.release()` on `ON_STOP` (whole-app background). Foreground channel switches never hit `ON_STOP` → player stays warm.
- Make the controller/ExoPlayer **re-creatable**: rebuild the engine lazily on the next `play()` after a release, and wire the currently-dead `release()` path in.
- Locale-change `recreate()` keeps the process foregrounded and ProcessLifecycleOwner debounces the Activity restart (~700 ms) → no release across recreate (existing caution satisfied).
**Validate:** foreground zap = warm/smooth; background the app → player released (logcat / no lingering HandlerThread); return + play rebuilds cleanly; locale change still plays.
**Ref:** CR #10.

---

## EPG / Playlist cross-scenarios

### `#31` (Med) — Shared tvg-id → blank guide for the duplicate — ✅ decided (fix 1:n)
**Decision (2026-07-18):** fix — one-to-many mapping so HD/SD (shared-tvg-id) duplicates each get a guide.
**Files:** `data/epg/.../RoomEpgRepository.kt` (L234-256, `externalChannelToLocal` + staging loop; `EpgProgram.toEntity`/stableKey).
**Fix:** build `externalChannelToLocal` as `Map<String, List<String>>` (all local channels per `epgChannelId`); in the staging loop emit **one** staged programme per mapped local channel; include `localChannel.id` in the `epg_programs.stableKey` so per-channel rows don't collide on the UNIQUE index. **No schema-column change** (stableKey formula only) — `epg_programs` are regenerable → rebuild on the next EPG refresh; optionally a trivial "clear epg_programs" on upgrade to avoid stale rows lingering until retention.
**Validate:** two channels sharing one tvg-id (HD/SD) both show programmes (multi-channel EPG smoke).
**Ref:** CR #31.

### `#32` (Med) — EPG-link priority from stale UI snapshot drops a link — ✅ decided
**Decision (2026-07-18):** compute priority atomically in the repo; log the real outcome (no false `source_linked`, no swallowed error). **No** user-facing warning (root-cause fix removes the collision).
**Files:** `feature/settings/.../SettingsRoute.kt:484` (drop UI priority calc); `SettingsViewModel.linkEpgSourceToProvider` (:322); `data/epg/.../RoomEpgRepository.kt:169` + `EpgImportRepository.kt:9` (drop the `priority` param); pattern from `AutoXtreamEpgSourceUseCase.nextPriority`.
**Fix:** `linkEpgSourceToProvider(providerId, sourceId)` computes `MAX(priority)+1` inside its own transaction; Route stops computing/passing priority; log the actual result instead of an unconditional `source_linked`; stop the silent `runCatching` swallow.
**Validate:** two fast link presses on two sources → both links persist (no silent drop); a forced failure logs the real outcome.
**Ref:** CR #32.

### `#33` (Med) — Channel-logo subquery ignores priority + isActive — ✅ decided (fix)
**Decision (2026-07-18):** align logo resolution with the guide-winner (active + priority).
**Files:** `core/database/.../CatalogDao.kt` (L704-708, `EPG_ICON_SUBQUERY`).
**Fix:** mirror the winner subquery (`EpgDao.kt:60-88`): add `JOIN epg_sources s ON s.id=m.epgSourceId AND s.isActive=1` and `JOIN provider_epg_sources pes ON pes.providerId=c.providerId AND pes.epgSourceId=m.epgSourceId`; order by `m.isManual DESC, pes.priority ASC LIMIT 1` (`c.providerId` is already available in the enclosing query).
**Validate:** logo comes from the same (active, highest-priority) source as the guide; a deactivated source's logo disappears.
**Ref:** CR #33.

### `#13` (Med) — Cancelled refresh stuck at REFRESHING — ✅ decided
**Decision (2026-07-18):** on cancellation restore the **prior** status (captured at refresh start), preserving a prior error state (e.g. InvalidCredentials).
**Files:** `worker/.../RefreshExecution.kt` (L195/212/216-218, `DefaultPlaylistRefresher`).
**Fix:** capture `provider.status` before setting `Refreshing`; in the `CancellationException` branch restore it before rethrowing (don't blindly set Active). Brings the playlist path in line with the EPG refresher (which clears its flag in `finally`).
**Validate:** cancel a playlist refresh (WorkManager stop) → provider returns to its prior status, "Gruppen verwalten" not locked.
**Ref:** CR #13.

---

## Security (on-device data-at-rest)

### `#3` (Med) — File-mode M3U body stored in cleartext — ✅ decided (encrypt)
**Decision (2026-07-18):** encrypt the File-mode M3U file — AES/GCM envelope with the existing AndroidKeyStore key (mirror `AndroidKeystoreSecureValueStore`), injected into `DiskM3uFileSourceStore`.
**Files:** `data/provider/.../DiskM3uFileSourceStore.kt` (`write`/`read`); reuse the Keystore cipher from `core/security`.
**Fix:** `write` encrypts (nonce + tag) before writing; `read` decrypts. Inject the cipher/keystore helper into the store. **Encryption stays INSIDE the store → transparent to every caller.**
- **Legacy/compat:** existing on-disk files are cleartext — `read` must fall back to plaintext when a file isn't AES/GCM-framed and re-encrypt it on the next `write`, so upgrading users don't lose File-mode playlists.

**Access points (traced 2026-07-18 — all go through the store, so nothing else changes):**
- write: `RoomProviderRepository:370` (save File-mode).
- read: `getProviderM3uInlineContent → store.read()` (`RoomProviderRepository:66`) — callers: refresh (`RefreshExecution:250`), editor connection-test (`SettingsViewModel:259`), and the **backup export** directly (`StandardBackupExporter:164`).
- restore: `StandardBackupRestorer:159` `store.write(...)`.
- delete: `RoomProviderRepository` (196/279/306/365) + `StandardBackupRestorer:145`.

**Backup safety (verified):** the backup does **not** copy the raw file — the exporter reads plaintext via `store.read()` and embeds it as `m3uInlineContent`, then the whole `.vcbak` is passphrase-encrypted; restore extracts the plaintext and writes it back via `store.write()`. So with encryption inside the store the backup still carries **plaintext** (device-independent) and restore **re-encrypts with the target device's Keystore** → **portable across devices, no backup-code change**. (If encryption were done at call sites / on raw bytes instead, a cross-device restore would break — hence it MUST be inside the store.)

**Validate (user-flagged):** the **edit → test-with-existing-local-file → re-save** flow keeps working end-to-end (read decrypts for the connection test; save re-encrypts); File-mode import + playback still resolve stream URLs; **backup export→restore round-trips File-mode content on a fresh install** (cross-device: plaintext survives, target re-encrypts); legacy cleartext file still reads. Existing `RoomProviderRepositoryTest` + `StandardBackupTest` (androidTest) already exercise read/write + the round-trip through the store — keep them green.
**Ref:** CR #3.

### `#4` (Med) — Support export bundles history/favorites/search — ✅ decided (trim, PRD-compliant)
**Decision (2026-07-18):** trim the support export to non-personal fields only (PRD-11 compliant). No opt-in.
**Files:** `app/.../backup/StandardBackupExporter.kt` (`exportSupportSettingsJson` L81-88) — NOT `exportInternalSnapshotJson` (the internal restore snapshot stays full).
**Fix:** assemble a dedicated support projection from non-personal fields only: settings/appearance/playback, aggregate counts (# providers/channels/movies/series/favorites), provider **types** (not names), security summary (hasPin). Drop raw `searchHistory`, `playbackProgress`, `channelHistory`, `favorites`, and category/provider/channel/title **names** (don't just `copy(...=null)` the full document).
**Validate:** exported `diagnostics-metadata.json` contains no search queries / history / favorites / provider-channel-title names; still carries useful counts + settings.
**Ref:** CR #4.

### `#5` (Med) — Provider host/IP leaks past diagnostics redaction — ✅ decided (source-level)
**Decision (2026-07-18):** fix at the source — for `IOException` subtypes log the class name (diagnostic, no host); other errors keep their message. No sanitizer host/IP regex (avoids regex fragility).
**Files:** `worker/.../RefreshExecution.kt:151` (`recordRefresh`).
**Fix:** in `recordRefresh`, when `error is IOException` put `error::class.java.simpleName` (`UnknownHostException`/`ConnectException`/`SocketTimeoutException`…) under `"error"`; otherwise keep `error.message ?: simpleName` (HTTP-status / import messages carry no host).
**Validate:** unit test — an `UnknownHostException("Unable to resolve host \"x.y.z\"")` logs `UnknownHostException`, not the host. (No `DiagnosticsSanitizer` change.)
**Ref:** CR #5.

---

## Localization / Settings

### `#12` (Med) — Hardcoded badge/placeholder strings bypass locale — ✅ decided
**Decision (2026-07-18):** localize all literals; render rating as a locale-agnostic star `★ {rating}` (no word to translate).
**Files:** `core/designsystem/.../VivicastCards.kt` (L171/237/294/338-340); `:core:designsystem` `res/values/strings.xml` + `res/values-en/strings.xml`.
**Fix:** L338 → `stringResource(R.string.livetv_live_badge)`, L340 → `livetv_badge_catchup` (both exist). Add `favorite_badge` (DE `Favorit` / EN `Favorite`) and `card_no_poster` (DE `Kein Poster` / EN `No poster`) to both locale files. Replace `"Rating $rating"` with `"★ $rating"` (no new string).
**Validate:** switch app language → Favorit/Kein Poster follow the locale; rating shows `★ {x}` in both.
**Ref:** CR #12.

### `#15` (Med) — Language setting doesn't round-trip through restore
**Files:** `app/src/main/java/com/vivicast/tv/backup/StandardBackupRestorer.kt` (L121-124).
**Fix:** in the restore success path (has a Context) call `LocaleHelper.save(context, restored.appearance.language…)` then `activity.recreate()`, so the applied locale matches the restored preference.
**Validate:** back up in one language, restore in another → whole UI switches, not just the Settings label.
**Ref:** CR #15.

### `#14` (Med) — "Animationen" setting is a no-op — ✅ decided (remove)
**Decision (2026-07-18):** remove the dead control — the row + its persisted + backup fields.
**Files (traced 2026-07-18 — ~10 sites):** `feature/settings/AppearanceSettingsPanel.kt` (row L210, `label()` L143-147, option picker L289-292); `feature/settings/SettingsModels.kt` (`SettingsAnimationSpeed` enum L50 + UiState field L17); `feature/settings/SettingsPreferenceMappers.kt` (L70-83 both mappers); `feature/settings/SettingsViewModel.kt` (L352/421); `core/datastore/UserPreferencesStore.kt` (field L50 + `AnimationSpeedPreference` enum L115); `core/datastore/DataStoreUserPreferencesStore.kt` (read L42 / write L117 / key L209); `app/backup/StandardBackup.kt` (export L206 + restore L255); designsystem strings `anim_fast`/`anim_normal`/`anim_slow` (now unused → remove; keep `value_off`). Plus **dead-import cleanup** where the removed types were imported but unused: `MainActivity.kt`, `AppDialogs.kt`, `PlaybackOrchestration.kt`, `app/SettingsPreferenceMappers.kt` (compiler-guided).
**Fix:** delete the row + the `AnimationSpeedPreference`/`SettingsAnimationSpeed` enums, field, mappers, VM state, DataStore key, backup export line + restore read, unused `anim_*` strings, and the now-dangling imports.
- **Compat:** old backups still carry `animationSpeed`; removing the restore read (`StandardBackup.kt:255`) means the key is simply ignored (JSON tolerates extra keys) — must not fail. Any orphaned DataStore key is harmless.
**Validate:** the row is gone; add/edit/backup/restore still work; restoring an OLD backup (with `animationSpeed`) doesn't error.
**Ref:** CR #14.

---

## Architecture

### `#22` (Med) — AppContainer holds business logic — ✅ decided (extract)
**Decision (2026-07-18):** extract to use-cases in `:data` (behavior-neutral); AppContainer becomes wiring only.
**Files:** `app/.../di/AppContainer.kt` (L500-528 `ensureSeriesDetail`, L535-573 `autoDetectXtreamEpg`); new `EnsureSeriesDetailUseCase` in `:data:media`; fold auto-EPG steps into `AutoXtreamEpgSourceUseCase` in `:data:epg`.
**Fix:** move the **core** orchestration into use-cases in `:data` (inject Xtream client/parser + repos), each returning a **structured result**. AppContainer becomes a thin wrapper.
- **⚠ Diagnostics stays App-side (validated 2026-07-18):** `:data` must NOT touch `DiagnosticsStore` (App-hoisted; the data layer never depends on it — see the `onImportSkipped` callback pattern). So the `xtream_source_none` / `xtream_source_linked` / `detail_fetched` / `detail_fetch_failed` events (AppContainer.kt:511/516/554/562) **stay in AppContainer**, driven by the use-case's returned result — do NOT move them into `:data`.
- The dedup `Mutex` (`xtreamEpgDetectionMutex`) moves with `autoDetectXtreamEpg`'s core into the EPG use-case; `ensureSeriesDetail` injects the Xtream client/parser instead of `new`-ing them inline.
**Validate:** `detekt`; behavior unchanged (on-demand series detail still loads on series open; a changed-source Xtream save still auto-creates+links the EPG; the dedup Mutex still serialises; all 4 diagnostics events still fire from the App layer).
**Ref:** CR #22.

---

## Android TV / UX

### `#26` (Med) — AsyncImage has no error/fallback — ✅ decided (contextual per-call)
**Decision (2026-07-18):** contextual per-call fallback — each AsyncImage falls back to its own initials/"?" placeholder on error, reusing the null-model placeholder.
**Files:** `core/designsystem/.../VivicastCards.kt` (L146/264/371 `MiniLogo`/`PosterArtwork`/`SearchPosterThumb`), `VivicastPanels.kt:122` `HeroPanel`.
**Fix:** route the error/empty load state to the same placeholder the null-model branch already draws — prefer rendering the initials/"?" placeholder as a background Box *behind* the `AsyncImage` (a failed image draws nothing → placeholder shows through; no subcomposition cost in TV grids), or `SubcomposeAsyncImage` with an error slot where a background isn't clean. No global ImageLoader painter (not contextual).
**Validate:** a 404 logo/poster shows the initials placeholder, not an empty box (Home/Live-TV/Movies/Series/Search).
**Ref:** CR #26.

### `#27` (Med) — Reorder list yanks D-pad focus to the top on every drop — ✅ decided (fix in P2)
**Decision (2026-07-18):** fix now in P2 (in `VivicastReorder.kt`); note it in `reorder-dpad-list.md` (that plan owns the component, pending emulator verification).
**Files:** `core/designsystem/.../VivicastReorder.kt:61`; cross-ref `plans/reorder-dpad-list.md`.
**Fix:** latch the initial-open focus with a `remember { false }` flag (don't re-fire `LaunchedEffect(items)`'s `focus(items.first())` on every `items` re-emit). After an external reorder update, re-request focus on the last-dropped id.
- **⚠ Mechanism (validated 2026-07-18):** the current `pickedId`/`order`/`moveTick` are all `remember(items)` (VivicastReorder.kt:53-56), so they **reset** when `items` re-emits post-drop — the just-dropped id is lost. Hold it in a **separate `remember { mutableStateOf<String?>(null) }` NOT keyed on `items`** (set it in `onReorder`), and on the next `items` re-emit re-request that id's focus instead of `items.first()`.
**Validate:** move + drop a row in the ProviderGroups reorder dialog → focus stays on the moved row (emulator; folds into the reorder-dpad-list.md emulator verification).
**Ref:** CR #27.
