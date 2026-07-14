# Plan: Spinner + Labelwechsel für Background-Buttons (Save/Delete/Export)

Status: ABGESCHLOSSEN (2026-07-14) – Gates grün: assembleDebug, detekt, test

## Ziel

Buttons, die im Hintergrund arbeiten, zeigen einen Spinner + Labelwechsel („Wird …"),
damit der Nutzer sieht, dass gearbeitet wird. Cancel/Toggle/Navigation bleiben unberührt.

## Scope (vom Nutzer bestätigt)

Rein: **Nr 1–3** aus der Recherche + **Diagnose-Export**. Nr 4–9 bleiben draußen.

Die 7 bereits vorhandenen Spinner-Buttons haben **alle schon** einen Labelwechsel
(inkl. Backup Restore „Wird wiederhergestellt …" / Export „Backup wird erstellt …") →
dort ist nichts zu tun.

## Baustein-Vorarbeit

### A. `VivicastSettingsRow` bekommt optionalen Trailing-Spinner
- Datei: `core/designsystem/.../VivicastInputs.kt`
- Neuer Param `valueLoading: Boolean = false`. Wenn true: kleiner `VivicastSpinner`
  (16.dp) direkt vor dem Wert-Text. Default false = kein Verhalten für alle anderen Zeilen.
- Nur nötig für den Diagnose-Export (Nr 4). Row kann sonst keinen Spinner.

### B. Strings (beide Locales: `values/` + `values-en/`)
- `settings_saving` = „Wird gespeichert…" / „Saving …" (generisch, für EPG-Save;
  `settings_provider_saving` ist provider-benannt → sauberer eigener generischer Key)
- `maintenance_cache_clearing` = „Wird geleert…" / „Clearing …"
- `about_exporting_diagnostics` = „Wird exportiert…" / „Exporting …"
- Verlauf-Delete: **reuse** `settings_deleting` („Wird gelöscht…") — nicht neu anlegen.

## Nr 1 — EPG-Quelle Editor „Speichern" → Spinner

`onSaveEpgSource` ist bereits `suspend … -> Result` und läuft in `scope.launch` in
`EpgSettingsPanel.kt:236`. Kein VM-Umbau nötig, alles im Panel.

- `EpgSettingsPanel.kt`: lokales `var epgSaving by remember { mutableStateOf(false) }`.
  Im `onSave`-Launch: `epgSaving = true` vor dem Aufruf, `finally { epgSaving = false }`.
  Save-Taps ignorieren solange `epgSaving` (wie Playlist-Editor: `saving || testing -> Unit`).
  `epgSaving` in den `EpgSourceEditor` runterreichen.
- `EpgSourceEditor.kt`: Save-`ActionPill` bekommt `loading = saving` + `label` swap
  (`common_save` → `settings_saving`). Cancel `enabled = !saving` (wie Provider-Editor).
  Neuer Param `saving: Boolean = false`.

## Nr 2 — Media-Cache leeren (Confirm) → Spinner

`onClearCache` ist aktuell fire-and-forget `() -> Unit`; Dialog schließt sofort. Für einen
Spinner-bis-Ende muss die Aktion awaitable sein.

- `SettingsViewModel.onClearCache()` → `suspend fun onClearCache()` (clear + Stats-Reload,
  kehrt erst zurück wenn fertig). **Caller prüfen** (Route + bestehender Test
  `onClearCache_clearsThenReloadsStats`; Test läuft in `runBlocking` → nur `suspend`-Aufruf).
- `SettingsRoute.kt`: `onClearCache` als `suspend () -> Unit` durchreichen
  (`viewModel::onClearCache`).
- `MaintenanceSettingsPanel.kt` / `CacheClearConfirmDialog`:
  - Param `onClearCache: suspend () -> Unit`.
  - Lokales `var clearing`. `onConfirm` → `scope.launch { clearing = true; onClearCache();
    schließe Dialog + message }`. Dialog **offen halten** bis fertig.
  - `VivicastDialogActions`: `primaryLoading = clearing`, `primaryEnabled = !clearing`,
    Label swap `maintenance_cache_clear_label` → `maintenance_cache_clearing`.

## Nr 3 — Verlauf löschen (Confirm) → Spinner

`onClearHistory: (Set) -> Unit` ist App-gehostet (bleibt App-hoisted lt. CLAUDE.md), fire-and-forget.

- App-Lambda (`MainActivity`) + `SettingsRoute` Signatur → `suspend (Set) -> Unit`
  (App awaitet die echte Löschung).
- `MaintenanceSettingsPanel.kt` / `HistoryClearDialog`:
  - Lokales `var deleting`. `onConfirm(selection)` → `scope.launch { deleting = true;
    onClearHistory(selection); schließe Dialog + message }`. Dialog offen halten.
  - `VivicastDialogActions`: `primaryLoading = deleting`,
    `primaryEnabled = selected.isNotEmpty() && !deleting`,
    Label swap `common_delete` → `settings_deleting` (reuse).

## Nr 4 — Diagnose exportieren → Spinner in der Zeile (Option B)

Läuft komplett App-seitig: Zeile klicken → Ordner-Picker → nach Wahl schließt Picker →
Export in `scope.launch` (`MainActivity.kt:1035`). Daher **App-gehosteter** `exporting`-State
runter in die Zeile.

- `MainActivity.kt`: `var diagnosticsExporting by remember { mutableStateOf(false) }`.
  Im `onPick`-Launch (`:1035`): `true` am Anfang, `finally { false }`. Runterreichen in die
  Settings-Composable.
- `SettingsRoute.kt`: neuer Param `diagnosticsExporting: Boolean = false` → an `AboutSettingsPanel`.
- `AboutSettingsPanel.kt`: Export-Zeile:
  `value = if (exporting) about_exporting_diagnostics else common_export`,
  `valueLoading = exporting`, `enabled = !exporting` (Doppelklick sperren).

## Tests (nur einfache, kein Emulator-Nav)

- `SettingsViewModelTest.onClearCache_clearsThenReloadsStats`: an `suspend` anpassen (bleibt grün).
- Optional 1 kleiner Test: EPG-Save setzt/räumt `epgSaving` (falls günstig als State-Test).
  Sonst weglassen (UI-State, per Hand im Emulator prüfbar).

## Gates

- `.\gradlew.bat detekt` — Achtung Cyclomatic auf `MaintenanceSettingsPanel` /
  `EpgSettingsPanel` (Dialog-Launch-Blöcke). Ggf. Confirm-Handler in kleine Composables ziehen.
- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat test`

## Offene Detail-Checks bei Umsetzung

- Alle Caller von `SettingsViewModel.onClearCache` (suspend-Umstellung).
- `MaintenanceSettingsPanel` braucht `rememberCoroutineScope` (falls noch keiner da).
- Diagnose: `diagnosticsExporting` durch die bestehende Settings-Composable-Kette
  (Signatur-Erweiterung an genau einer Stelle).
