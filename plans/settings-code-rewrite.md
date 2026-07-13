# Settings Code Rewrite — code→docs / build / cleanup

Status: **planning** — nothing executed. Source of truth for the *decisions*: `docs/SETTINGS-DOCS-CODE-AUDIT.md`
(Round 1 = D1–D40, Round 2 = P-/A-/O-/W-/K-/E-/U-/S-/B-*). The docs (docs→code) are already rewritten;
this plan covers only the **code-side** work where the decision was *change code* (code→docs), *build*, or
*cleanup*.

## Scope decided with owner (2026-07-13)
- **IN this pass:** Group 1 (labels), Group 2 (K-values), Group 3 (A-Shell-1), Group 4 (About rows),
  Group 5 (D22 + D35), Group 7 (cleanup), and **B-3** (surface IO restore errors — build now).
- **DEFERRED to a separate feature round:** Group 6 — D10, D15, D17 (with their own plan).
- Confirmed knobs: **D35 fixed retention = 7 days** · **K-3/4/5 with-PIN protection value = "Ein/Aus"** ·
  **E-G4 = new string `settings_epg_global_interval`, don't rename the shared one.**

Legend: risk **L** low (label/string), **M** medium (logic/state), **H** high (new feature).

---

## Group 1 — Label / string changes (code→docs) · risk L

Pure `strings.xml` edits (de + en), no logic:

| ID | Change | Where |
|----|--------|-------|
| A-Allg-1 | `settings_resume_last_channel` "…gesehenen **Sender**…" → "…**Kanal**…" | `core/designsystem/.../values/strings.xml` (+ `values-en`) |
| O-3 | theme "Dunkel Kontrast" → "Dunkel kontrastreich" | `strings.xml` (~:209) |
| S-1 | `settings_cache_info` "Cache Informationen" → "Medien-Cache" | `strings.xml` (~:94) |
| A-Allg-3 | UA hint `settings_ua_default_hint` — reword: empty is coerced to `Vivicast/1.0`, not "uses app default" | `strings.xml` + GeneralSettingsPanel.kt:241 |

**E-G4 (needs care):** EPG-global interval label → "Globales Aktualisierungsintervall". The string
`settings_provider_update_interval` ("Update Intervall") is **shared** by EpgGlobalSettingsPanel.kt:109,
ProviderEditor.kt:728 and the interval dialog (:829). Do **not** rename the shared string — add a new
`settings_epg_global_interval` and use it only in `EpgGlobalSettingsPanel`. The per-playlist row keeps
"Update Intervall".

## Group 2 — Kindersicherung status values (K-3/4/5) · risk M

Switch the parental rows from *action* words to *status* shortforms (strings + small value logic in
`ParentalControlPanel.kt`):
- PIN row value: "Setzen"/"Ändern" → **"Nicht gesetzt"/"Gesetzt"** (`settings_pin_set_value`/`_change_value`).
- Protection rows, no PIN: "PIN erforderlich" → **"Deaktiviert"** (`settings_pin_required`); with PIN keep Ein/Aus.
- Disable row: "Deaktivieren" → **"Öffnen"** (`settings_pin_disable_value`).

Open Q: exact wording of the with-PIN protection value — keep "Ein/Aus" or use "Aktiviert/Deaktiviert"?

## Group 3 — Behavior: always open Allgemein (A-Shell-1) · risk M

Remove the persisted last-viewed-section restore; Settings always opens on Allgemein. Touches:
- `SettingsViewModel.kt:265` `onSelectedSectionChanged` (remove) — and its call in SettingsRoute.
- `MainActivity.kt:907` `initialSelectedSection` (drop; default to first section).
- `DataStoreUserPreferencesStore.kt` :34/:109/:218 — drop `LastSettingsSection` key + read/write.
- `UserPreferencesStore.kt:41` `lastSettingsSection` field.
- `StandardBackup.kt:242` — remove the now-obsolete exclusion line.
- `SettingsViewModelTest.kt:158` — remove/adjust the assertion.

Resolves A-Shell-2 (undocumented key) + the Round-2 `last_settings_section` cleanup. `focusLanguageRowOnEnter`
(A-Shell-3) is a **separate** flow — keep it.

## Group 4 — Über die App: add info rows (U-1..U-5) · risk L–M

Add two rows to `AboutSettingsPanel`: **Build-Nummer** (from `BuildConfig`) and **Player-Engine**
("Media3/ExoPlayer"). Needs `AboutAppState` fields (`SettingsModels.kt`) + app-side population
(MainActivity/AppContainer). Drop App-Name / UI-Technologie / Build-Typ (docs already dropped — no code).

## Group 5 — Fix / remove · risk M

- **D22** remove the dead "Sitzungsfreigabe sperren" row (`ParentalControlPanel.kt:185-192`) + its strings
  `settings_lock_session`/`_value`. (`state.lockedUntilMillis` stays — used app-side.)
- **D35** diagnostics retention → single fixed value; remove the configurable `DiagnosticsRetentionDays`
  DataStore key + `retentionDays` plumbing. (As built: `DiagnosticsStore` holds `private val retentionDays = 7`
  — a fixed field, not a named top-level constant.)
  Touches `DiagnosticsStore.kt`, `MainActivity.kt` (:279/:976), `DataStoreUserPreferencesStore.kt:85`,
  the diagnostics test. Open Q: confirm the fixed value = **7 days**.

## Group 6 — Build new features · risk H (biggest — discuss scope/timing)

- **D10** per-playlist channel-group management (Anzeigen/Ausblenden/Sortieren) — provider editor UI +
  data layer for group state. Documented as a target in the wireframe; not yet built.
- **D15** local logos folder — Optik "Logos-Ordner" picker (persisted folder URI) + local-logo resolution
  + the 3rd logo-priority option "Lokaler Ordner" (P-5). Ties into the image pipeline.
- **D17** multiple predefined accent colors + contrast guard — expand `SettingsAccentColor` enum, add color
  values + the Color-Select rendering (O-2/O-4 pair).

These are real features, not alignments. Recommend deciding whether to build now, sequence them, or defer.

## Group 7 — Dead-state cleanup · risk L–M

Remove unused state/strings (no UI, no behavior change):
- `settings_provider_step_*` strings — 0 code refs (dead after single-form). **Verified dead.**
- **D23** `HistoryClearTarget.All` + `settings_history_clear_all_value`/`maintenance_all_*`.
- **D24/D25/D26 + B-6/B-7** backup residue: `BackupTargetPreference.Smb/GoogleDrive`, `BackupSettingsState`
  target plumbing, `backup.targetType` serialize/restore, `lastBackupAtMillis`, dead `settings_backup_target`
  / `_last` / `_full_*` / `_never` strings.
- **D36** `HistoryPreferences.maxRecentChannels` / `watchedThresholdPercent` dead fields.
- **D37** `TransparencyLevel.Percent75`, `DecoderPreference.Automatic` unused enum values.
- **D38-40** `AboutAppState.languageTag`/`timeZoneId` (unused), unused `ParentalControlPreferences` keys.
- Round-2: unused row-`help` strings (rows never render help after A-Allg-4) — low priority.

## Flagged (not a decided change) — B-3 robustness gap

Storage/IO restore errors (Ziel nicht erreichbar / kein Speicherplatz / …) are **not surfaced** by the
restore code — only validation/passphrase errors are. Docs now mark these "nicht implementiert". Decide
separately whether to add IO-error surfacing.

---

## Open discussion points (decide with owner before building)

1. **Order / batching** — do the cheap Groups 1–5 + 7 first (mostly strings/state, low risk), builds (Group 6) after? Or a different cut?
2. **Group 6 scope** — build D10/D15/D17 now, sequence them, or defer? These are the only real features.
3. **E-G4** — confirm the new-string approach (don't rename the shared interval string).
4. **K-3/4/5** — confirm the exact with-PIN protection wording (Ein/Aus vs Aktiviert/Deaktiviert).
5. **D35** — confirm fixed retention = 7 days.
6. **B-3** — address the IO-error gap now, or leave flagged?
7. **Gates** — each structural batch: run `detekt` + `assembleDebug` + `test`; watch the detekt baseline
   (signature changes) and update tests (SettingsViewModelTest, DiagnosticsStoreTest).

Nothing is committed; no code touched yet.

## Execution status (2026-07-13)

**Done + compiling green** (`:app:assembleDebug` passed after each batch):
- **G1** — `settings_resume_last_channel` "Sender"→"Kanal", theme "Dunkel kontrastreich", `settings_cache_info`
  "Medien-Cache", UA hint reworded; new `settings_epg_global_interval` used only by EpgGlobalSettingsPanel
  (shared string left alone). de + en.
- **G2** — K-3/4/5 via strings: `settings_pin_set_value`/`_change_value` → "Nicht gesetzt"/"Gesetzt",
  `settings_pin_required` → "Deaktiviert"; disable-row value → `about_open_value` ("Öffnen") so the PIN-dialog
  button keeps "Deaktivieren". K-2/K-6 needed no code change (already position-2 / transient).
- **G3** — A-Shell-1: removed the persisted last-section restore + `last_settings_section` key (VM fn,
  SettingsRoute call, MainActivity arg, DataStore read/write/key, `GeneralPreferences` field, StandardBackup
  line, ViewModel test). `initialSelectedSection` param kept as a pure input (androidTest still valid).
- **G4** — About: `buildNumber` + `playerEngine` added to `AboutAppState`, populated via `PackageInfoCompat`
  + "Media3/ExoPlayer"; two new rows + strings (de+en).
- **G5** — D22 dead session-lock row + its 3 strings removed. D35 diagnostics retention fixed at 7 in
  DiagnosticsStore (var→const, `setConfig` param dropped, DataStore key/field/read/write removed, constant +
  2 call sites cleaned, tests updated).
- **G7 (safe subset)** — 27 dead strings removed (de+en): `settings_provider_step_*`, dead backup-target /
  two-type residue, `settings_history_clear_all_value` + `maintenance_all_*`. `HistoryClearTarget.All` enum
  value + its orchestration branch removed.
- **B-3** — `runStandardRestore` now wraps the restore in try/catch (rethrows `CancellationException`),
  surfacing a new `main_backup_restore_failed` message on storage/DB/keystore IO instead of crashing.

### G7 remainder — DONE (2026-07-13, gate green)
After investigation the deferred items were reconciled + removed (owner-approved). Full gate
(`:app:assembleDebug` + `:feature:settings:testDebugUnitTest` + app & feature:settings androidTest compile
+ `detekt`) green after each cluster.
- **Cluster A (safe):** `about_copy_version`/`about_copied`/`about_app_info` dead strings removed.
  **D37** `TransparencyLevel.Percent75` + `DecoderPreference.Automatic` removed — turned out SAFE, not a
  crash risk: `enumValue()` (`runCatching{enumValueOf}.getOrDefault`) falls stale reads back to the default.
  Removed the enum values + the app/feature mapper `when`-branches.
- **Cluster B (backup, D24/D25/D36):** removed the whole backup-target subsystem (`BackupTargetPreference`,
  `BackupTargetMode`, `BackupSettingsState`, `target` pref, both target mappers, `SettingsRoute`/`MainActivity`
  wiring, unused imports in AppDialogs/PlaybackOrchestration), plus `lastBackupAtMillis` and
  `HistoryPreferences.maxRecentChannels`/`watchedThresholdPercent`. Backup JSON drops the `backup`/`targetType`
  section + the two history keys; restore stays backward-compatible (`optX` defaults). `StandardBackupTest`
  updated (now asserts the `backup` section is absent). `lastExportDir` + `HistoryPreferences.enabled` kept.
  Owner: SMB/Drive to be built fresh later; placeholders gave no head-start.
- **Cluster C (D40):** removed the dead parallel `ParentalControlPreferences` DataStore store
  (data class + `UserPreferences.parentalControl` + `updateParentalControl` interface fn + DataStore
  read/write/keys + 3 test fakes). Real parental state stays in `PinSecurity` (Keystore); restore reset is
  `pinSecurityStateStore.clear()`, verified by the existing `assertFalse(pinStore.read().hasPin)` test.

**Intentionally kept:** `AboutAppState.languageTag`/`timeZoneId` (audit D39) — NOT dead; they feed the
diagnostics-export metadata via `SettingsPreferenceMappers`. Left in place.

**Still open:** G6 features — **D10** per-playlist channel-group management and **D17** predefined accent
colors + contrast guard. (**D15** logos folder + P-5 — DONE 2026-07-13, committed in f65fc8f.)
