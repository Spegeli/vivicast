# Vivicast — Pre-Release Code Review

_2026-07-18 · `com.vivicast.tv` v0.1.0 (versionCode 1), minSdk 23 / target 36 — produced by a multi-agent audit with per-finding adversarial verification; only confirmed/adjusted findings are included._

## Summary

Vivicast's post-remediation architecture largely holds: the six feature areas follow the ViewModel + immutable UiState pattern, most provider secrets are Keystore-backed, and the staged delta-merge import pipeline is soundly built. The review nonetheless surfaced one Critical and five High issues that block a release. The Critical is structural — `app/build.gradle.kts` declares no release build type, so `assembleRelease` emits an unsigned, unminified APK and there is no distributable artifact today. The High set compounds it: `MANAGE_EXTERNAL_STORAGE` will get a Google Play submission rejected; an exported search ContentProvider lets any installed app enumerate the user's catalog; permanent auth failures (401/403) are misclassified as retryable and drive an unbounded WorkManager refresh loop; a malicious XMLTV feed can OOM the process via unbounded internal entity expansion; and the player overlay renders a fabricated clock and duration on every VOD playback. The remaining 17 Medium and 11 Low findings are correctness papercuts, dead code/settings, on-device data-at-rest gaps, and duplicate-tvg-id / stale-snapshot EPG edge cases — worth scheduling, but none is individually release-blocking.

**Release-ready: NO** — One Critical (no release/signing/R8 configuration → no installable, obfuscated artifact) plus five blocking High findings (Play-policy permission, world-readable ContentProvider, infinite auth-retry loop, XMLTV DoS, fabricated player timeline) remain open. Ship only after the Top-priorities list is cleared and re-verified against a signed, minified `assembleRelease`.

## Severity overview

| Severity | Count |
|---|---|
| Critical | 1 |
| High | 5 |
| Medium | 17 |
| Low | 11 |
| **Total** | **34** |

## Security

### 1. Exported search ContentProvider has no readPermission — any installed app can dump the catalog
**Severity:** High
**Location:** `app/src/main/AndroidManifest.xml:81-84`, `app/src/main/java/com/vivicast/tv/search/AndroidTvSearchSuggestionProvider.kt:19-48`
**Description:** `AndroidTvSearchSuggestionProvider` (authority `com.vivicast.tv.search`) is `android:exported="true"` with no `android:permission`/`readPermission`/path-permission (unchanged in the merged release manifest). `query()` returns channel/movie/series titles, subtitles, poster URLs and `vivicast://` deep links via `mediaRepository.searchAndroidTvSuggestions()`. Any zero-permission app can `contentResolver.query("content://com.vivicast.tv.search/search_suggest_query/…")`; an empty `selectionArg` falls through to `query=""`, which the repo's own test proves returns every catalog row. PIN protection defaults to `false` when no PIN is set, so by default the whole library is enumerable (trivially past the 50-row cap via prefix bucketing).
**Fix:** Add `android:readPermission="android.permission.GLOBAL_SEARCH"` (and `android:permission=…`) to the `<provider>`. The system TV search app holds `GLOBAL_SEARCH`, so global search keeps working while third-party apps are denied; keep `exported="true"`.

### 2. XMLTV streaming SAX parser permits DOCTYPE — internal entity expansion (billion-laughs) is unbounded
**Severity:** High
**Location:** `iptv/xmltv/src/main/java/com/vivicast/tv/iptv/xmltv/XmltvStreamParser.kt:42-54`
**Description:** The production streaming SAX factory (used by the EPG refresh worker and the add-time connection test) disables external DTD/entities and sets `FEATURE_SECURE_PROCESSING` but deliberately allows the DOCTYPE declaration and sets no entity-expansion bound. On Android's Expat/Harmony SAX, secure-processing does not enforce JAXP expansion limits, and `setFeatureIfSupported` swallows unsupported features via `runCatching` (fails open). The sibling DOM parser blocks the hazard outright (`disallow-doctype-decl=true`, `XmltvContracts.kt:155`); the streaming path does not. The 200 MB `CappedInputStream` bounds bytes read, not in-memory expansion, so a small crafted feed with nested internal general entities can expand to gigabytes and OOM the process — triggerable at add time from an untrusted, user-supplied EPG URL before anything is persisted.
**Fix:** Mirror the DOM path: set `disallow-doctype-decl=true` on the streaming factory (if DTD-less feeds are acceptable), or enforce `jdk.xml.entityExpansionLimit`/`totalEntitySizeLimit` (verified on-device) or reject internal entity declarations via a `DeclHandler`. Stop swallowing failures for the security-critical features so an un-hardenable parser fails closed.

### 3. File-mode M3U playlist body is stored in cleartext while every other secret is Keystore-encrypted
**Severity:** Medium
**Location:** `data/provider/src/main/java/com/vivicast/tv/data/provider/DiskM3uFileSourceStore.kt:26`
**Description:** `write()` persists the raw M3U text via `File.writeText()` under `filesDir/m3u_sources`. A File-mode M3U body is the full channel list whose stream URLs commonly embed the account (`…/user/pass/1234.ts`). Every sibling secret — URL-mode M3U URL, Xtream server/user/pass (`RoomProviderRepository.kt:302-304,362`), per-stream references (`SecureM3uStreamReferenceStore.kt:28`) — goes through the AES/GCM Keystore-backed `SecureValueStore`, so the single most credential-dense artifact is the only one left in cleartext, contradicting the "Secrets: Android Keystore-backed" baseline. Bounded by app sandbox + `allowBackup=false` (extraction needs root/physical access), hence Medium; the same content is encrypted inside the `.vcbak` export.
**Fix:** Wrap File-mode content in an AES/GCM envelope keyed from the existing AndroidKeyStore key (mirror `AndroidKeystoreSecureValueStore`), or route it through `SecureValueStore`. Alternatively record a signed-off exception (the archived plan's cleartext rationale is stale against current code).

### 4. Diagnostics/support export bundles full watch history, favorites and raw search queries
**Severity:** Medium
**Location:** `app/src/main/java/com/vivicast/tv/backup/StandardBackupExporter.kt:81-88`
**Description:** `exportSupportSettingsJson()` nulls only provider `source` and epgSource `url`, retaining full `playbackProgress`, `channelHistory`, `favorites`, categories, provider names and raw `searchHistory` query strings. This JSON is embedded verbatim into `diagnostics-metadata.json` (`MainActivity.kt:1118→1122`, `DiagnosticsStore.kt:266`) and — unlike log lines — never passes through `DiagnosticsSanitizer`. Credentials/URLs are correctly excluded, but this directly violates PRD-11 §"Ausgeschlossene Daten" and 08-android-tv-security §9.7 (export must not contain search history or provider/channel/title names) and ADR-014's "Sensibel" classification of history/favorites/search/progress.
**Fix:** Reduce the support export to non-personal fields (counts/settings), drop or aggregate `playbackProgress`/`channelHistory`/`favorites`, omit raw `searchHistory` and provider names — or gate their inclusion behind an explicit opt-in in the export dialog.

### 5. Provider hostname/IP from network-exception messages survives diagnostics redaction
**Severity:** Medium
**Location:** `app/src/main/java/com/vivicast/tv/diagnostics/DiagnosticsSanitizer.kt:11`, `worker/src/main/java/com/vivicast/tv/worker/RefreshExecution.kt:151`
**Description:** On refresh failure `recordRefresh()` logs the raw `error.message` under key `"error"`. That key does not match `SENSITIVE_KEY`, so the value goes to `redact()`, which only strips `scheme://…` URLs and `key=value` secrets. Common OkHttp `IOException` messages carry a bare host/IP with no scheme — `Unable to resolve host "myprovider.example.com"`, `Failed to connect to myprovider.example.com/1.2.3.4:8080` — matching neither regex, so the provider's private host and resolved IP are written verbatim into the persisted, user-exportable diagnostics log, contradicting the sanitizer's own PRD-11 contract. Bounded (logging off by default, export user-initiated), hence Medium.
**Fix:** At the log source, for `IOException` subtypes log `it::class.java.simpleName` instead of `it.message`; and/or add bare host:port / IP patterns to the sanitizer, or add `"error"` to `SENSITIVE_KEY`. Add a test asserting `Unable to resolve host "x.y.z"` is redacted.

## Correctness

### 6. Permanent auth failure (401/403) is classified as retryable → infinite WorkManager refresh loop
**Severity:** High
**Location:** `worker/src/main/java/com/vivicast/tv/worker/RefreshExecution.kt:47-48,104,132`
**Description:** `isDeterministicRefreshError()` treats only `SQLiteConstraintException` as terminal; both refresh failure branches map everything else to `Retry` → `Result.retry()`. A permanent auth failure surfaces as `XtreamHttpException`/`RefreshHttpException` 401/403 — `toProviderStatus()` even maps these to `InvalidCredentials` — but neither is a `SQLiteConstraintException`, so both retry. There is no `runAttemptCount` cap anywhere, and the same worker classes back the periodic requests, so an expired/invalid subscription re-fetches the whole catalog/EPG forever on exponential backoff (10s…~5h), re-authenticating with bad credentials — battery/network waste and provider-side rate-limit / IP-ban risk. `RefreshAuthenticationException` and `IllegalArgument`→`InvalidCredentials` retry forever too.
**Fix:** Broaden the terminal-error test: also treat `RefreshAuthenticationException`, `RefreshImportException`, `IllegalArgumentException`, and non-transient 4xx (401/403/404) `RefreshHttpException`/`XtreamHttpException` as `Result.failure()`. Keep `IOException`, 5xx and 429 as `Retry`.

### 7. Player timeline renders fabricated elapsed/total time
**Severity:** High
**Location:** `core/designsystem/src/main/java/com/vivicast/tv/core/designsystem/VivicastPlayer.kt:118-119`
**Description:** `VivicastPlayerTimeline` receives only `progress: Int` — a 0-100 percentage (`PlayerRoute.kt:141` passes `progressPercent()`). Line 118 formats that percentage as `"00:$progress"`, so 47% renders as `"00:47"` masquerading as mm:ss; line 119 hardcodes the total as the literal `"01:40"` for every seekable stream regardless of real duration. The fill bar is proportionally correct, but the numeric readout is fake on every movie/episode/catch-up. `VivicastPlayerOverlay` always embeds this timeline, so it shows on the real player for all seekable content.
**Fix:** Thread real `positionMs`/`durationMs` (or pre-formatted mm:ss / h:mm:ss strings) into `VivicastPlayerTimeline`/`Overlay` — `PlayerRoute` already has the data behind `progressPercent()` — and remove the `"00:$progress"` and `"01:40"` literals.

### 8. Live-channel history is rewritten every 1s during playback (10s throttle bypassed)
**Severity:** Medium
**Location:** `data/playback/src/main/java/com/vivicast/tv/data/playback/PlaybackProgressRecorder.kt:30-45`
**Description:** In `record()` the Channel branch calls `saveChannelHistory()` unconditionally and returns before the `automaticProgressSaveTimes` throttle that gates the movie/episode path (`AUTOMATIC_PROGRESS_SAVE_INTERVAL_MILLIS = 10_000`). `record()` runs from the player state loop, which emits every `PROGRESS_POLL_INTERVAL_MILLIS = 1_000` while Playing/Paused, so `channel_history` is upserted once per second for the whole live session — a stable-id row (no growth) but every write dirties the table and re-emits `observeRecentChannels`/`observeAllRecentChannels`. `watchedAt` only needs setting on open/close for recent-channel ordering.
**Fix:** Apply the same throttle to the channel branch (skip when `clock() - lastSaved < INTERVAL` unless `forceSave`/status transition, update the map after writing), or write channel history only on open and the final forced save.

### 9. toggleFavorite stores a constant sortOrder (epoch millis clamped to Int.MAX_VALUE)
**Severity:** Medium
**Location:** `data/favorites/src/main/java/com/vivicast/tv/data/favorites/RoomFavoritesRepository.kt:54`
**Description:** `sortOrder = now.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()` with `now = System.currentTimeMillis()`. In 2026 that is ~1.75e12, always > `Int.MAX_VALUE`, so every favorite created via `toggleFavorite` (the sole production path — LiveTv/Movies/Series VMs) gets `sortOrder = 2147483647`. `FavoritesDao.observeFavorites` orders by `sortOrder, createdAt DESC`, so ordering silently collapses onto the `createdAt DESC` fallback (newest-first — the inverse of the intended insertion-ascending order) and the `sortOrder` column carries no information (also round-tripped meaninglessly through backup).
**Fix:** Stop clamping epoch millis into an Int. Simplest: assign an incrementing `max(sortOrder)+1` per `(providerId, mediaType)` group; or make `sortOrder` a `Long` end-to-end; or drop it and rely on `createdAt DESC`.

### 10. ExoPlayer is never released in production
**Severity:** Medium
**Location:** `app/src/main/java/com/vivicast/tv/MainActivity.kt:204-214`, `core/player/src/main/java/com/vivicast/tv/core/player/VivicastPlayerController.kt:844-853`
**Description:** Every exit path calls `stop()` (disables renderers) but never `release()`. `onStop()` calls `stop()`, PlayerRoute's `onDispose` calls `stop()`, and there is no `onDestroy` override; the only `release()` caller is an instrumentation test. The controller/engine/ExoPlayer is a process-scoped `by lazy` singleton on `applicationContext`, so this is not an Activity leak and not accumulating — but the fully-implemented teardown (`release()` → `player.release()`, job cancellation) is never reached, so the ExoPlayer and its internal playback `HandlerThread` live for the whole process lifetime, violating the Media3 lifecycle contract.
**Fix:** Give the singleton a real teardown: release on true app exit via a `ProcessLifecycleOwner` observer in `VivicastApplication` and make the controller re-creatable, or scope it to the Activity and `release()` in `onDestroy` while rebuilding per Activity. If process-life retention is intended, delete the unused `release()` path and document it. (A naive `onDestroy` release would break locale-change `recreate()`, which reuses the same singleton.)

### 11. Import merge guard checks provider existence, not source identity — a source switch racing an in-flight refresh resurrects the old catalog
**Severity:** Medium
**Location:** `data/media/src/main/java/com/vivicast/tv/data/media/RoomCatalogImportRepository.kt:89,121,160`
**Description:** The only mid-import guard is `if (providerDao.getProvider(providerId) == null) return null`. On a source switch `updateProvider` keeps the provider row, clears the catalog and deletes the M3U stream references, but never cancels an already in-flight refresh (which captured the OLD credentials at start). If the old-source refresh's merge commits after the clear, the existence guard passes and it re-inserts the OLD source's channels/movies/series, and its post-merge `replaceProviderReferences` rewrites stream URLs to the OLD source. The corrective post-save refresh is coalesced/dropped by the process-wide `RefreshRunGuard`, so the provider can display and play stale content until the next refresh. Editing is reachable mid-refresh (only "Gruppen verwalten", not Edit, is gated by `isRefreshing`).
**Fix:** Stamp providers with a source epoch that `create/updateProvider` bump on any source change; capture it at refresh start and re-check inside the merge transaction, skipping if it changed. Additionally cancel the in-flight playlist refresh in `updateProvider` (as `deleteProvider` does) and use REPLACE for the corrective refresh.

### 12. Hardcoded badge/placeholder strings bypass the de/en locale switch
**Severity:** Medium
**Location:** `core/designsystem/src/main/java/com/vivicast/tv/core/designsystem/VivicastCards.kt:171,237,294,338-340`
**Description:** Several visible strings are Kotlin literals instead of `stringResource`: the `"Live"`/`"Favorit"`/`"Catch-Up"` channel badges (338-340), `"Kein Poster"` (171), and the English `"Rating $rating"` prefix in search cards (237, 294). They render through the locale-agnostic `StatusBadge`/`Text`, so they never switch language against the app's real de/en runtime `LocaleHelper` switch — English locale shows German "Favorit"/"Kein Poster", German locale shows English "Rating". This violates the CLAUDE.md "strings live ONLY in `:core:designsystem`" rule; resources `livetv_live_badge` and `livetv_badge_catchup` already exist.
**Fix:** Replace each literal with `stringResource(...)` (reuse `livetv_live_badge`/`livetv_badge_catchup`; add resources for the favorite badge, "Kein Poster", and the rating prefix in both `values/` and `values-en/`), or pass resolved strings in from callers.

### 13. Cancelled playlist refresh leaves the provider stuck at status REFRESHING
**Severity:** Medium
**Location:** `worker/src/main/java/com/vivicast/tv/worker/RefreshExecution.kt:195,212,216-218`
**Description:** `DefaultPlaylistRefresher.refresh` sets status REFRESHING, then the `CancellationException` branch rethrows without resetting it and the `finally` only exits the run-guard. So any cancelled refresh (WorkManager's ~10-min limit, OS stop, low-memory kill) leaves the provider persistently REFRESHING — asymmetric with the EPG refresher, which clears its flag in `finally`. This shows a false "Aktualisierung…" badge and silently locks "Gruppen verwalten" (`ProviderActionsPanel.kt:122,129` gates on `status == Refreshing`). Recovery is only via the startup-only `clearStuckRefreshingStatus()` or a later non-cancelled refresh; with the refresh interval OFF by default, the lockout persists the whole session.
**Fix:** Reset the transient status on cancellation — in the `CancellationException` branch restore the prior/Active status before rethrowing, or move a status reset into `finally` (mirroring the EPG refresher).

### 14. "Animationen" (animation-speed) setting is a no-op
**Severity:** Medium
**Location:** `feature/settings/src/main/java/com/vivicast/tv/feature/settings/AppearanceSettingsPanel.kt:206-215`
**Description:** The Appearance panel exposes an Off/Slow/Normal/Fast "Animationen" row that persists, maps both ways, and is included in backup — but nothing consumes it. A repo-wide search for `animationSpeed` finds only persistence/VM-mapping/UI/backup; there is no `animationSpec`/`tween`/motion-scale consumer, and `MainActivity`'s `CompositionLocalProvider` supplies `fontScale`/opacity but not animation speed. Selecting Off changes nothing, including for motion-sensitive users or weak boxes. (Sibling `fontScale`/transparency settings are genuinely wired into 6 designsystem files.)
**Fix:** Wire it up (provide a `LocalAnimationSpeed` from `appearance.animationSpeed` at the composition root and scale transition durations / disable animations when Off, like `fontScale`), or remove the row and its persisted+backup fields.

### 15. Language setting does not round-trip through backup restore
**Severity:** Medium
**Location:** `app/src/main/java/com/vivicast/tv/backup/StandardBackupRestorer.kt:121-124`
**Description:** Language lives in two stores: DataStore `appearance.language` drives the Settings display, while the applied locale is read by `attachBaseContext` from `LocaleHelper`'s separate `locale_pref` SharedPreferences. The live-change path syncs both; restore writes only DataStore (`updateAppearance`) and never calls `LocaleHelper.save` (its sole caller is `MainActivity.kt:1092`). So restoring a backup made in a different language leaves Settings showing e.g. "English" while the whole UI stays in the old locale until the user manually re-picks the language.
**Fix:** In the restore success path (which has a Context) call `LocaleHelper.save(context, restored.appearance.language…)` then `activity.recreate()`, so the applied locale matches the restored preference.

### 16. Retrofit is a shipped dependency whose only consumer is never called
**Severity:** Low
**Location:** `core/network/build.gradle.kts:23`, `core/network/src/main/java/com/vivicast/tv/core/network/NetworkClientFactory.kt:71`
**Description:** `core/network` declares `implementation(libs.retrofit)`; its only Retrofit use is `createRetrofit()`, which has zero callers (all networking goes through `createOkHttpClient()`). With no R8/shrink (see finding 28), Retrofit and its transitive classes ship in the release APK entirely unused.
**Fix:** Delete `createRetrofit` and remove `implementation(libs.retrofit)` (and the catalog alias if unused elsewhere — grep confirms none do).

### 17. gzip magic-byte sniff assumes a 2-byte read
**Severity:** Low
**Location:** `iptv/xmltv/src/main/java/com/vivicast/tv/iptv/xmltv/XmltvStreamParser.kt:21`
**Description:** `maybeGunzip` decides gzip via `pushback.read(signature, 0, 2)` and `read == 2 && …`. `InputStream.read` may return fewer bytes than requested; the production OkHttp/Okio-backed chain (`OkHttpEpgStreamSource → CappedInputStream → BufferedInputStream → PushbackInputStream`) can deliver 1 byte on the first read under TCP fragmentation, making `read != 2` misclassify a genuinely gzipped feed as plain and hand raw gzip bytes to SAX, which throws. Surfaces as a spurious refresh Retry or a false add-time "format not usable"; intermittent and self-correcting, hence Low.
**Fix:** Fill the 2-byte signature in a loop until 2 bytes or EOF (or `DataInputStream.readFully` / okio `BufferedSource.require(2)`), unread exactly what was read, then decide.

### 18. On-demand series detail import orphans playback_progress for dropped episodes
**Severity:** Low
**Location:** `data/media/src/main/java/com/vivicast/tv/data/media/RoomCatalogImportRepository.kt:240-241`
**Description:** `importXtreamSeriesDetail()` deletes a series' seasons+episodes and re-inserts current ones but never deletes `playback_progress` for episodes the provider dropped, unlike `mergeEpisodes`/`mergeSeries` which clean progress for their removed ids. Today's on-demand rewrite (commit `948c277`) replaced the provider-wide `mergeEpisodes` (which cleaned progress) with a bare delete-then-insert; there is no FK cascade. Impact is bounded — both consumers resolve via `getEpisode` and drop nulls — but orphan rows accumulate in a user-data table and reach backup export.
**Fix:** Before `deleteEpisodesForSeries`, diff `getEpisodeIdsForSeries` against the freshly built episode ids and call `playbackDao.deleteProgressForMediaIds(providerId, "EPISODE", removedIds)`, mirroring `mergeEpisodes`.

### 19. Deleting an EPG source leaks its epg_channels icon rows
**Severity:** Low
**Location:** `data/epg/src/main/java/com/vivicast/tv/data/epg/SecureEpgSourceRepository.kt:71-82`
**Description:** `deleteSource()` removes `provider_epg_sources`, mappings, programs and stage rows but never calls `epgDao.deleteEpgChannelsForSource(sourceId)` (which exists and is the counterpart to the sibling deletes). The `epg_channels` rows written per source during import therefore persist permanently. Functionally harmless — the effective-logo join reaches `epg_channels` only through a mapping row (deleted here) and source ids are UUIDs so no id reuse — but permanent dead rows no path reclaims.
**Fix:** Add `database.epgDao().deleteEpgChannelsForSource(sourceId)` inside the `deleteSource` transaction, next to `deleteMappingsForSource`/`deleteProgramsForSource`.

### 20. Multi-collector shared-var + full rebuild() can emit transient intermediate UiState
**Severity:** Low
**Location:** `feature/movies/src/main/java/com/vivicast/tv/feature/movies/MoviesViewModel.kt:97,229`
**Description:** Movies (and identically LiveTv/Series) run several init collectors that each mutate plain `var` backing fields then call `rebuild()`, which reassigns `_uiState.value` from all fields. Some collectors perform suspend repository calls (`getMovie`, a real dispatcher switch) before `rebuild()`, so another collector can run and emit a UiState assembled from a mix of updated and not-yet-updated fields. Single-threaded (`Main.immediate`), StateFlow dedups, and the final `rebuild()` self-heals, so the effect is occasional transient emissions plus O(N) redundant recomputations during initial load.
**Fix:** Optional — consolidate the source flows into a single `combine(...)` mapping to one UiState, or conflate `rebuild()` so state emits once per settled input set. Acceptable to keep as-is.

### 21. history.enabled preference has no UI control
**Severity:** Low
**Location:** `core/datastore/src/main/java/com/vivicast/tv/core/datastore/DataStoreUserPreferencesStore.kt:136`
**Description:** `HistoryPreferences.enabled` is modeled, persisted, backed up, and gates resume-last-channel (`MainActivity.kt:647`), but `updateHistory` has no production caller except `StandardBackupRestorer.kt:126`, and no settings panel renders a toggle. The preference is frozen at its default (`true`) and can only change via a restore. (It gates resume, not recording, so no data-loss risk.)
**Fix:** Add a history-enable toggle (`VivicastSettingsRow` + `SettingsViewModel.onHistoryChanged` → `updateHistory`), or remove the unused preference and its backup field.

## Architecture

### 22. AppContainer contains business logic (ensureSeriesDetail / autoDetectXtreamEpg)
**Severity:** Medium
**Location:** `app/src/main/java/com/vivicast/tv/di/AppContainer.kt:500-528,535-573`
**Description:** CLAUDE.md requires "AppContainer contains no business logic — only wiring/delegation." `ensureSeriesDetail()` constructs a `DefaultXtreamClient`+transport and `DefaultXtreamParser`, fetches and parses series info, branches on null/failure, calls `importXtreamSeriesDetail()` and emits two diagnostics events — a full on-demand use-case, not delegation. `autoDetectXtreamEpg()` similarly orchestrates credential read, URL build, feed validation, a concurrency `Mutex`, create-or-reuse+link, and logging. Both are only reachable/testable through the whole AppContainer graph; the correct-home pattern already exists (`TestProviderConnectionUseCase` in `:data:provider`, `AutoXtreamEpgSourceUseCase` in `:data:epg`).
**Fix:** Extract into use-cases in `:data` (e.g. `EnsureSeriesDetailUseCase` in `:data:media`; fold the auto-EPG steps into `AutoXtreamEpgSourceUseCase`), injecting the Xtream client/parser and repositories, and leave AppContainer to instantiate and delegate.

### 23. Movies & Series ViewModels hardcode System.currentTimeMillis() instead of an injectable clock
**Severity:** Low
**Location:** `feature/movies/src/main/java/com/vivicast/tv/feature/movies/MoviesViewModel.kt:183`, `feature/series/src/main/java/com/vivicast/tv/feature/series/SeriesViewModel.kt:290`
**Description:** `onMarkSeen`/`onMarkEpisodeSeen` build completed-progress timestamps from a direct `System.currentTimeMillis()`, whereas `LiveTvViewModel.kt:42` already uses an injectable `nowProvider` (test injects it). CLAUDE.md requires an injectable clock. Not a runtime bug, but the mark-seen paths are non-deterministic/unassertable in tests and inconsistent across three otherwise-parallel VMs.
**Fix:** Add `nowProvider: () -> Long = { System.currentTimeMillis() }` to both VMs (mirroring LiveTvViewModel), thread it through their factories, and replace the direct calls.

### 24. Search feature exposes ViewModel/UiState/Factory as public while all other features are internal
**Severity:** Low
**Location:** `feature/search/src/main/java/com/vivicast/tv/feature/search/SearchViewModel.kt:28`, `SearchUiState.kt:9`, `SearchViewModelFactory.kt:11`
**Description:** These three Search types are public; the equivalent Movies/Home/Series/LiveTv/Settings types are all `internal`. They are constructed only inside `SearchRoute` (same module), so nothing crosses a module boundary — over-exposing module-internal presentation types as public API against the convention the other five features follow.
**Fix:** Mark all three `internal`.

### 25. Dead EpgSourceRepository/ProviderRepository imports across ~10 settings panels
**Severity:** Low
**Location:** `feature/settings/src/main/java/com/vivicast/tv/feature/settings/GeneralSettingsPanel.kt:74,83` (+ 10 sibling panels)
**Description:** `GeneralSettingsPanel` imports `EpgSourceRepository` and `ProviderRepository` but references neither; the same two unused imports are duplicated across AppearanceSettingsPanel, PlaybackSettingsPanel, MaintenanceSettingsPanel, EpgGlobalSettingsPanel, ParentalControlPanel, AboutSettingsPanel, BackupSettingsPanel, EpgSettingsPanel, ManualEpgMappingPanel and ProviderSettingsPanel (panels correctly drive all data through the ViewModel). `SETTINGS-APP-HOISTED-DECISIONS.md` blesses only leftover `collectAsState` imports; these Repository imports are a separate, un-blessed set. (Note: the configured detekt gate has the `style` ruleset off, so it would not currently flag them — only the compiler's IDE inspection would.)
**Fix:** Remove the two unused import lines from `GeneralSettingsPanel.kt` and the other panels that never reference them.

## Android TV

### 26. AsyncImage calls have no error/fallback — failed loads show empty boxes instead of the initials placeholder
**Severity:** Medium
**Location:** `core/designsystem/src/main/java/com/vivicast/tv/core/designsystem/VivicastCards.kt:146,264,371`, `core/designsystem/src/main/java/com/vivicast/tv/core/designsystem/VivicastPanels.kt:122`, `app/src/main/java/com/vivicast/tv/di/AppContainer.kt:282-293`
**Description:** The `AsyncImage` calls in `MiniLogo`/`PosterArtwork`/`SearchPosterThumb`/`HeroPanel` pass only model/contentScale/modifier — no `error`/`fallback` — and the shared singleton `ImageLoader` sets no global error/placeholder. Each composable draws an initials/"?" placeholder only in its null-model `else` branch. When the model is a non-null but unreachable URL (extremely common for IPTV logos/posters that 404), Coil fails silently and nothing is drawn, leaving an empty gradient box on Home, Live-TV, Movies, Series and Search.
**Fix:** Give each `AsyncImage` an `error`/`fallback` rendering the same initials/"?" placeholder as the null case (Coil3 `error=`/`fallback=`, or `SubcomposeAsyncImage` with an error slot). Optionally add a global error painter on the `ImageLoader`.

### 27. Reorder list yanks D-pad focus back to the first row after every drop
**Severity:** Medium
**Location:** `core/designsystem/src/main/java/com/vivicast/tv/core/designsystem/VivicastReorder.kt:61`
**Description:** `VivicastReorderList` keys all local state on `items` (`remember(items)`) and its `LaunchedEffect(items)` unconditionally focuses `items.first()`. On drop, `onReorder` persists the new order; the backing list re-emits reordered (the dialog stays open across drops — `ProviderGroupsPanel.kt:147-148`). The re-emitted list is structurally different, so `remember(items)` resets and `LaunchedEffect(items)` re-fires, focusing the first row. Net: after moving and dropping a row, D-pad focus jumps from the just-placed row back to the top on every drop, losing the user's position.
**Fix:** Focus the first row only on initial open (latch via a `remember { false }` flag rather than firing on every `items` change), and after an external update re-request focus on the last-dropped id instead of `items.first()`; or skip the reset when the incoming ids already match the committed order.

## Release-Readiness

### 28. No release signing, R8/minify, or resource-shrink configuration — release build is unsigned and unobfuscated
**Severity:** Critical
**Location:** `app/build.gradle.kts:7-24`, `core/network/src/main/java/com/vivicast/tv/core/network/NetworkClientFactory.kt:80-95`
**Description:** The `android {}` block declares no `buildTypes { release { … } }`, no `signingConfig`, no `isMinifyEnabled`/`proguardFiles`/`shrinkResources`; a repo-wide search finds none of these anywhere and no `proguard-rules.pro` exists. AGP's implicit release type therefore defaults `signingConfig` to null and `isMinifyEnabled` to false. Consequences: (1) `assembleRelease` yields `app-release-unsigned.apk` — not installable or Play-uploadable, a hard distribution blocker; (2) no R8/obfuscation/code+resource shrink, so every class ships in the clear and larger than necessary — including the debug-only `NetworkClientFactory.applyInsecureTrustManager` trust-all TLS branch (correctly `BuildConfig.DEBUG`-gated and unreachable at runtime, so not exploitable, but its bytecode is still emitted for anyone instrumenting the binary) and the unused Retrofit classes.
**Fix:** Add a `signingConfigs` block (release keystore fed from CI/env, never committed) and `buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"); signingConfig = signingConfigs.getByName("release") } }`, plus `app/proguard-rules.pro` with keep rules for Room/Compose/Media3/reflection entry points. Verify `assembleRelease` produces a signed, minified APK; R8 will also constant-fold `BuildConfig.DEBUG` and strip the dead TLS path.

### 29. MANAGE_EXTERNAL_STORAGE ("All files access") is a Google Play distribution blocker
**Severity:** High
**Location:** `app/src/main/AndroidManifest.xml:15-17`, `app/src/main/AndroidManifest.xml:45`
**Description:** The manifest requests `MANAGE_EXTERNAL_STORAGE` (carried unstripped into the merged release manifest) plus `requestLegacyExternalStorage="true"` to power the TV-safe in-app File picker (SAF is unreliable on TV) for M3U import + backup/diagnostics export. Google Play restricts All-files-access to a narrow allowlist (file managers, backup, antivirus, document management); a media/IPTV player is not eligible, so a Play submission triggers the restricted-permission declaration flow and is very likely rejected — and Play Console listing assets are already being prepared (`docs/logos/README.md`), so Play distribution is the intended channel. The in-app picker is a legitimate design choice; the distribution consequence was never evaluated in any doc. Note `requestLegacyExternalStorage="true"` is inert at targetSdk 36 (honored only ≤29) — dead config.
**Fix:** Decide the channel explicitly. For Play: drop `MANAGE_EXTERNAL_STORAGE` and rely on the existing permission-free MediaStore/Downloads (`writeToDownloads`) + `getExternalFilesDir` fallback for writes and `ACTION_OPEN_DOCUMENT`/app-scoped dirs for import, accepting no arbitrary-folder browse on API 30+ without a grant; or prepare a Play Console declaration and accept the review risk. Drop the inert `requestLegacyExternalStorage` either way, and anchor the decision in a release-checklist doc.

### 30. minSdk=23 ships to Android 6.0–8.1, five API levels below the documented API-28 test floor
**Severity:** Low
**Location:** `app/build.gradle.kts:13`
**Description:** `minSdk = 23` (consistent across all 25 modules) installs on API 23-27, but CLAUDE.md documents the physical/emulator test floor as API 28 and structural/storage/permission changes are validated only on 28 and 36 — leaving four OS versions shipped-to but never exercised, with no telemetry for field signal. The storage-permission ladder is coherent for that range (READ maxSdk 32 / WRITE maxSdk 29 / MANAGE 30+), so this is a test-coverage/consistency gap rather than a proven break, but the declared and tested floors disagree by five API levels and no ADR/PRD records 23-27 as an intentional supported range.
**Fix:** Either raise `minSdk` to 28 to match the tested floor, or add an explicit API-23 smoke pass to the release checklist and document 23-27 as supported-and-tested. Align `app/build.gradle.kts` with CLAUDE.md.

## Playlist/EPG Cross-Scenarios

### 31. Channels sharing one tvg-id get an EPG mapping but no programmes (duplicate HD/SD variants show a blank guide)
**Severity:** Medium
**Location:** `data/epg/src/main/java/com/vivicast/tv/data/epg/RoomEpgRepository.kt:234-236`
**Description:** `importXmltv` builds `externalChannelToLocal = (manual+auto).associateBy { it.epgChannelId }.mapValues { it.channelId }`. `associateBy` keeps only the LAST entry per key, so when two local channels map to the same XMLTV id (common: "Sky Sport HD" and "Sky Sport" sharing a tvg-id) only one survives; the staging loop assigns every programme to that single channel while the other keeps an upserted mapping but zero programmes. Reinforced by `epgProgramId` being keyed by the XMLTV channelId (not `localChannel.id`), so per-channel rows would collide anyway. At read time the winner query resolves the source for the starved channel but `WHERE p.channelId=<that channel>` returns nothing — a channel the app explicitly matched shows an empty guide.
**Fix:** Make the relationship one-to-many: build `Map<String, List<String>>` (all local channels per `epgChannelId`) and emit one staged programme per mapped local channel, and include `localChannel.id` in `epgProgramId`/stable key so per-channel rows no longer collide. Or document the shared-owner limitation explicitly.

### 32. EPG-link priority is computed from a stale UI snapshot — two rapid assignments collide on UNIQUE(providerId, priority) and one link is silently dropped
**Severity:** Medium
**Location:** `feature/settings/src/main/java/com/vivicast/tv/feature/settings/SettingsRoute.kt:484`
**Description:** Linking an EPG source computes `priority = (providerEpgLinks.maxOfOrNull { it.priority } ?: 0) + 1` from the StateFlow snapshot, then upserts a `ProviderEpgSourceEntity`; the table has `UNIQUE(providerId, priority)`. The snapshot only updates after the DB write commits and the Flow re-emits, so two OK presses on two sources before that round-trip compute the SAME max+1. The second INSERT violates the unique index; Room's `@Upsert` catches it and falls back to UPDATE-by-primary-key, but the new (provider, source) pair has no existing row — 0 rows updated, link silently lost — yet `.onSuccess` still logs "source_linked" and `SettingsViewModel`'s `runCatching` swallows even a thrown error. `AutoXtreamEpgSourceUseCase.nextPriority` avoids this by reading `MAX(priority)` fresh from the DB.
**Fix:** Compute the next priority inside the repository/DAO within the same transaction as the insert (read `MAX(priority)` and insert `MAX+1` atomically), mirroring `AutoXtreamEpgSourceUseCase.nextPriority`, instead of passing a UI-computed priority.

### 33. Channel-logo EPG-icon subquery ignores source priority and isActive
**Severity:** Medium
**Location:** `core/database/src/main/java/com/vivicast/tv/core/database/dao/CatalogDao.kt:704-708`
**Description:** `EPG_ICON_SUBQUERY` resolves a channel's icon by joining `epg_channel_mappings → epg_channels` ordering only by `m.isManual DESC LIMIT 1` — unlike the program-winner (`EpgDao.kt:60-88`) and search-winner (`SearchDao.kt:96-106`) subqueries the code keeps in sync, it does NOT join `provider_epg_sources` for the priority tiebreak nor filter `epg_sources.isActive = 1`. Result: (1) a channel non-manually mapped to two linked sources gets an arbitrary-tiebreak icon, so the logo can come from the lower-priority source while the guide comes from the higher; (2) deactivating a source keeps its mappings/`epg_channels`, so a channel still shows the deactivated source's icon even though its programmes are correctly hidden. Cosmetic (logo only) but a provable divergence from the winner resolution.
**Fix:** Mirror the winner subquery: add `JOIN epg_sources s ON s.id = m.epgSourceId AND s.isActive = 1` and `JOIN provider_epg_sources pes ON pes.providerId = c.providerId AND pes.epgSourceId = m.epgSourceId`, and order by `m.isManual DESC, pes.priority ASC`.

### 34. EPG import builds channel mappings from a non-transactional snapshot — a concurrent playlist refresh can orphan epg_programs/mappings
**Severity:** Low
**Location:** `data/epg/src/main/java/com/vivicast/tv/data/epg/RoomEpgRepository.kt:197`
**Description:** `importXmltv` reads the provider's channels outside any transaction, builds mappings/staged rows against that snapshot, then merges in a short transaction guarded only by provider+source existence. The playlist and EPG refreshers hold SEPARATE `RefreshRunGuard`s (keyed by providerId vs sourceId) and separate WorkManager unique-work names, so a catalog refresh and an EPG refresh for the same provider run concurrently. If `mergeChannels` deletes a channel and cascades its EPG mappings/programs after the snapshot read but before the EPG merge, the merge re-inserts programmes/mappings for the now-deleted channel. No FK schema, so no crash — orphan rows persist until a later EPG refresh drops the programmes as stale (orphan mappings have no stale-delete step).
**Fix:** Read the provider's channels inside the EPG merge transaction (or re-validate each mapped channelId against the live catalog inside it) so the mapping/programme set reflects the channel snapshot committed at merge time.

## Top priorities before release

1. **Add release build configuration (signing + R8/minify/shrink)** — finding 28. Without a `signingConfigs`/`buildTypes.release` block there is no installable, obfuscated artifact at all; nothing else ships until this exists.
2. **Lock down the exported search ContentProvider** — finding 1. Add `android:readPermission="android.permission.GLOBAL_SEARCH"`; a one-attribute change that closes a world-readable leak of the user's entire catalog to any installed app.
3. **Resolve the MANAGE_EXTERNAL_STORAGE Play-policy blocker** — finding 29. Either drop the permission for the existing MediaStore/scoped fallback or commit to a non-Play channel; a Play submission is otherwise rejected, and Play distribution is the stated intent.
4. **Make permanent auth failures terminal** — finding 6. 401/403/missing-credentials currently retry forever on exponential backoff, risking provider rate-limits / IP-bans and battery/network drain for a very common state.
5. **Harden the XMLTV streaming parser** — finding 2. Block DOCTYPE or bound entity expansion so a malicious/compromised EPG feed cannot OOM the process at add or refresh time.
6. **Fix the fabricated player timeline** — finding 7. The overlay shows a fake `00:NN` clock and a hardcoded `01:40` total on every movie/episode/catch-up; thread real position/duration through.