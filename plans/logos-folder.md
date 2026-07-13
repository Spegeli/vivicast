# D15 — Local logos folder (feature, part of G6)

Status: **DONE (2026-07-13)** — built, gate green, and verified working on the API 36 emulator (local-folder
logos + EPG logos both resolve; fuzzy matching, remove-folder, fallback chains, deleted-folder skip all
confirmed by the owner). Uncommitted.

<details><summary>Original build record</summary>

Status: **BUILT (2026-07-13), gate green** — `:app:assembleDebug` + `:feature:settings:testDebugUnitTest` +
`:data:epg:testDebugUnitTest` + app & feature:settings androidTest compile + `detekt` all pass. Not committed;
not yet run on an emulator (androidTest compiles but hasn't executed). Scope: **channels/Live-TV only.**

### What was built
- Shared normalize util `data/epg/ChannelNameNormalize.kt` (`normalizeChannelMatchKey`/`normalizeChannelName`);
  `RoomEpgRepository` now delegates to it (EPG matching unchanged — `data:epg` tests green).
- `epgChannelId` surfaced onto domain `Channel` (append-only default) + `ChannelEntity.toDomain`.
- `LOGO_PRIORITY_LOCAL` const + `normalizeLogoPriority`; editor option shown **only when a folder is set**
  (threaded `localLogosConfigured` through ProviderSettingsPanel → ProviderEditor → dialog); `logoPriorityLabel` branch.
- `UserPreferences.localLogoFolder` (top-level, `local_logo_folder` key, `updateLocalLogoFolder`; not backed up) + 3 test fakes.
- `LocalLogoIndex` (app): recursive scan, `{normalizedKey → File}` × 2 tiers, extension-priority dedup
  (png>jpg>jpeg>webp, path tiebreak), MAX_DEPTH 12 / MAX_FILES 20k guard. `LocalLogoIndexTest` androidTest.
- Resolve hook in `resolveChannelLogoModel`: LOCAL priority + index hit → `File`, else existing URL/cache path.
- Optik UI: "Logos-Ordner" row (folder picker) + conditional "Logos neu einlesen" row; MainActivity wiring
  (pick saves path → LaunchedEffect rebuilds; rescan explicit + toast; rebuild on app-start / folder change).
- Strings (de/en) + flipped the `SettingsGeneralPanelTest` "Logos-Ordner" absent→present assertion.

### Emulator-driven fixes (2026-07-13, on API 36)
- **Matching was too strict for real data.** Rebuilt around a shared `smartMatchKey` (data/epg/ChannelNameNormalize):
  collapse separators, drop `@variant` + bracketed tags + diacritics, map `+`→`plus`, drop resolution noise +
  a leading/trailing country code, keep digits. So `ZDFinfo.de@SD` / `ZDFinfo.de` / `zdf-info-de` /
  `DE - ZDFinfo` / `ZDFinfo (720p) [Geo-blocked]` all → `zdfinfo`; `Sport1`/`Sport1+`/`ZDF2` stay distinct.
- **Unified recognition (owner's original vision):** the SAME `smartMatchKey` now drives BOTH the local-logo
  matcher AND the **EPG channel matcher** (`RoomEpgRepository`) — the EPG matcher previously produced **0
  mappings** for `@variant` tvg-ids + `DE - ` EPG display-names. Programme-title normalization left untouched.
  EPG-matcher sanity-checked (existing `ard.de`/`Das Erste HD` cases preserved); androidTest not yet run on device.
- **Remove-folder action** added ("Logos-Ordner entfernen" → clears the pref; the LOGO_PRIORITY_LOCAL editor
  option disappears again). **Logo-priority labels unified** to "Logos aus … bevorzugen" (Playlist/EPG/lokalem Ordner).
- **Verified on emulator:** local-folder logos work (fuzzy match against the tv-logos `germany` set).
- **Build note:** `:app:assembleDebug` can wrongly report UP-TO-DATE (Gradle incremental bug) → stale APK.
  Always `--rerun-tasks` + verify APK mtime before installing.

### Open / follow-up
- Run on emulator (API 28 + 36) — storage-permission path + a real folder pick not yet exercised.
- Coil same-name-replace cache-bust (lastModified) — noted, not yet done (rare edge; restart/new-name works).


Investigation: 3 Explore agents (2026-07-13); this plan reflects the owner decisions from that walk-through.

## What it does
A device-local folder of logo image files. When a playlist's logo priority is set to **"Lokaler Ordner"**,
each channel's logo is resolved from a matching file in that folder; on no match it falls back to the current
playlist/EPG URL logic.

## Benchmark (owner-requested, 2026-07-13)
Reviewed 5 open-source IPTV apps in `../IPTV-APPS` (AerioTV, BBC, M3UAndroid, OwnTV, StreamVault). **None has a
local-logo-folder feature** — all source channel logos from remote `tvg-logo` URLs (AerioTV adds only a
show/hide toggle; StreamVault writes into the Android-TV launcher; OwnTV has a folder browser but only for
backup/EPG files). No "picon" support anywhere. So there is no reference implementation — the recursion +
duplicate rules below are our own design calls.

## Owner decisions
- **Matching = reuse the EPG channel recognition (2-tier ladder):** 1) normalized `epgChannelId` (tvg-id),
  2) else normalized channel name. Same `normalize()`/`normalizeName()` as `RoomEpgRepository` (diacritics-folded).
- **Extensions (search order):** `png`, `jpg`, `jpeg`, `webp`.
- **Recursive subfolders:** YES — scan the chosen folder AND all subfolders (user may organize by country/etc).
- **Index, not per-lookup scan:** build an in-memory `{normalizedKey → File}` index ONCE (on folder-set +
  app-start), rebuilt when the folder changes; per-channel lookup is O(1). Guard depth/file-count against
  pathological trees (`ponytail:` note).
- **Duplicate resolution (e.g. `zdf.png` + `zdf.jpg`):** deterministic **extension priority** `png > jpg >
  jpeg > webp`; same-extension collisions across subfolders (`de/zdf.png` + `at/zdf.png`) break ties by
  lexicographic path. NOT "first found".
- **Scope:** Live-TV channel logos only. Movies/series posters stay URL-based (separate feature).
- **Option gating:** the `LOGO_PRIORITY_LOCAL` editor option appears **only when a logos folder is configured**.
- **Persistence:** a plain absolute-path `String` (codebase avoids SAF/URIs; mirrors `lastExportDir`). Device-local,
  **not** part of a backup.

## Current state (from investigation)
- Folder picker already exists: `FilePickerMode.FOLDER` in `app/.../FilePickerDialog.kt`; path persistence
  precedent = `BackupPreferences.lastExportDir` (`MainActivity.kt` export flow).
- Logo priority: `LOGO_PRIORITY_PLAYLIST`/`_EPG` + `normalizeLogoPriority` (`data/provider/ProviderConfigurationModels.kt:94-99`);
  effective-logo SQL `CASE` (`core/database/.../CatalogDao.kt:399-409`) — `local` isn't `'epg'` so it already
  falls to the playlist `ELSE` (no SQL change).
- Resolve hook: `AppContainer.resolveChannelLogoModel` (`app/.../PlaybackOrchestration.kt:123-134`) — has
  `mediaCacheStore`/`providerRepository`/`userPreferencesStore` in scope; returns a `File` or URL; Coil + the
  `logoMissing` flag already handle a `File`.
- Recognition to reuse: `RoomEpgRepository.kt:319-329` (`idMatch = xmlById[epgChannelId.normalize()] ?: xmlByName[name.normalizeName()]`),
  `normalize()`/`normalizeName()` at `:419/:428` — currently **private**.
- Gap: `ChannelEntity.epgChannelId` (raw tvg-id, DB column exists) is **dropped** by `ChannelEntity.toDomain()`
  (`RoomMediaRepository.kt:250-262`) — so the domain `Channel` has no tvg-id.

## Build steps
1. **Pref** `localLogoFolder: String? = null` — new DataStore key `local_logo_folder` (read + `setNullable` write,
   mirror `LastBackupExportDir`), surfaced on `UserPreferences` (own one-field group or top-level). Not backed up.
2. **Optik row** "Logos-Ordner" in `AppearanceSettingsPanel` → `onPickLogoFolder` callback → `SettingsRoute` →
   `MainActivity` (mirror `onPickM3uFile`) → `FilePickerRequest(FOLDER, startDir = current)` → store `folder.absolutePath`.
   Row value = path or "Nicht gesetzt". Flip the deferred-row assertion at `SettingsGeneralPanelTest.kt:87`.
3. **3rd priority** `LOGO_PRIORITY_LOCAL = "local"` + `normalizeLogoPriority` keeps it; editor option added
   **conditionally** (only when folder set) in `ProviderEditor.kt:360-372`; `logoPriorityLabel` branch; new strings
   `settings_provider_logo_priority_local` (de/en). SQL unchanged.
4. **Shared normalize util:** extract `normalize`/`normalizeName` from `RoomEpgRepository` to a shared location both
   `:data:epg` and the app/`:data` can use; surface `epgChannelId` onto domain `Channel` + `ChannelWithLogo`
   mapping (no DB migration — the column already exists).
5. **Local-logo index** (new small component, e.g. `LocalLogoIndex` in `:core:cache` or app): recursively
   `walkTopDown` the folder, keep `png/jpg/jpeg/webp`, key each by its normalized basename; on collision keep the
   higher-priority extension, tiebreak by lexicographic path. Cap depth/file count. Build on folder-set +
   app-start; rebuild when the folder pref changes. Expose `lookup(tvgId, name): File?` = index[normalize(tvgId)]
   ?: index[normalizeName(name)].
6. **Resolution hook** in `resolveChannelLogoModel`: if `localLogoFolder != null` **and**
   `provider.logoPriority == LOGO_PRIORITY_LOCAL` → `index.lookup(channel.epgChannelId, channel.name)`; on hit
   return the `File`; on miss fall through to the existing cache/URL logic. Memoize the per-provider
   `logoPriority` lookup (called per channel row) with a `ponytail:` note. One small `assert`/unit check for the
   matcher (tvg-id hit, name fallback, extension-priority dedup, miss → URL fallback).

## Docs
`design/screens/07-settings` + `design/components/settings` (vivicast-docs) already document a "Logos-Ordner"
Optik row + the 3-option logo priority incl. "Lokaler Ordner" (P-5 was coupled to D15). Once built, reconcile
any wording; no new doc decision expected.

## Gates
`detekt` + `assembleDebug` + `:feature:settings:testDebugUnitTest` + relevant androidTest compile after the
structural (data-layer) batch and again at the end. Test on the API 28 floor + 36 ceiling for the storage/permission path.

</details>
