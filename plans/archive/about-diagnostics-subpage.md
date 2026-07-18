# Plan: About → "Diagnose & Protokolle" sub-page (logs + crashes: view, refresh, delete)

Status: **completed** (2026-07-17, committed to main). All decisions locked.
Row order (per user): Diagnostic logging · View current log · Export diagnostics · Delete logs.

## Implementation notes (focus escapes fixed during the build)
Two "remove the focused node → focus escapes to the top nav → Home" traps surfaced and were fixed the same
way the earlier legal-overlay bug was:
- **Rows ↔ viewer:** don't `if/else`-swap them (that destroys the focused row mid-switch). The rows stay
  composed (`alpha(0)` when viewing); the viewer overlays. `detailFocusRequester`/entry focus live on
  whichever mode is visible.
- **Viewer Back:** request focus on the (composed, hidden) View-log row **synchronously before**
  `viewingLog = false`, so the viewer isn't torn down with focus still on it.
- **Viewer pills width:** `ActionPill` fills its 300 dp cap, so two tabs alone pushed Refresh off-screen —
  the tabs/Refresh get explicit widths (200/200/150 dp).
Verified on the emulator (API 36): About restructure, multi-select delete (+ actual delete → "No logs
yet"), the Normal/Crash viewer with real log lines, Refresh (Normal only), and Back walking
viewer → rows → About with focus landing on the View-log row then the Diagnose row.

**Not yet tested on API 28 (floor):** the public crash-copy delete there uses the file path
(`getExternalFilesDir/...`) instead of MediaStore; no crash files existed to exercise it. Simple file
deletion, but worth a floor run before release.

## Goal (from user)

Declutter About: move **Diagnostic logging** + **Export diagnostics** off the top level into a dedicated
sub-page ("Diagnose & Protokolle"), opened by one new About row. In that sub-page:
- Diagnostic-logging toggle (as today)
- Export diagnostics (as today)
- **Logs löschen** — a multi-select popup (like Clear History) with checkboxes to delete **normal event
  logs** and/or **crash logs**.
- **View current log** — an inline viewer with a two-way switch at the top: **Normale Logs** (default,
  active) vs **Crash-Logs**. Shows the newest file of the selected kind. A **Refresh** action re-reads —
  **only for normal logs** (crash logs don't refresh). No auto-polling.

## Current state (researched)

- Event logs: `DiagnosticsStore` (App-owned singleton, `AppContainer.diagnosticsStore`) writes flat
  rolling files to `filesDir/vivicast-diagnostics/logs/`, 5 MB rotation, 20 MB cap, fixed 7-day retention.
  `log()` no-ops when disabled; `activeFile` = current file. Today: `setConfig`, `log`, `exportZip`. No
  read/clear API.
- Crash logs: `CrashLogWriter` writes each uncaught exception to a **private** copy in
  `DiagnosticsStore.crashDir` (bundled into the export) **and** a **public** copy in
  `Downloads/Vivicast/Crashes` (MediaStore on API≥Q, file on <Q; user-reachable via a file manager).
  Always-on, prunes to newest 10. **`CrashLogWriter` is NOT retained** — VivicastApplication just calls
  `CrashLogWriter(...).install()` once. The retained owner of `crashDir` is `DiagnosticsStore`.
- All lines are sanitized on write (`DiagnosticsSanitizer`); crash content is redacted on write.
- Clear History already implements the exact **multi-select checkbox dialog** to mirror:
  `HistoryClearDialog` = `VivicastDialog` + rows of (`VivicastFocusSurface` + `VivicastCheckbox` + label) +
  `VivicastDialogActions` (primary "Delete" enabled when the selection is non-empty). Targets are a
  `HistoryClearTarget` enum + `onClearHistory: suspend (Set<HistoryClearTarget>) -> Unit`.
- About sub-pages use a full-panel **overlay** pattern (`Column`+`verticalScroll`, `BackHandler` closes +
  refocuses the opener, alpha-hide the About list) — reused here.

## Decisions (confirmed with user 2026-07-17)
- Label: de „Diagnose & Protokolle" / en „Diagnostics & logs".
- Delete = a multi-select popup (checkboxes: **Normale Logs** / **Crash-Logs**), mirroring Clear History;
  row is named "Logs löschen".
- View = built now, with a **Normale Logs / Crash-Logs** switch; Refresh only for normal logs.
- No-log: the View row stays visible; the viewer shows a „Keine Logs vorhanden" note per selected kind.

## Crash-delete scope (confirmed)
Deleting crash logs removes **both** the private copies AND the public `Downloads/Vivicast/Crashes` copies
(MediaStore delete on API≥Q, file delete on <Q). Reading the viewer uses the private copy.

## Two UX requirements (from user)
- **Section header:** the sub-page shows a big title header exactly like Technical details does (the
  overlay's first item is `SectionTitle("Diagnose & Protokolle")`, under the always-present "ABOUT"
  panel title). Already the overlay pattern — carried over.
- **Close-refocus:** BACK/Return from the sub-page lands focus back on the new "Diagnose & Protokolle"
  About row (its opener), via the same `openerRowFocus.requestFocus()` + drop-overlay order that
  `closeLegal`/`closeTechnical` already use. A new `diagnosticsRowFocus` requester on that row.

## Proposed structure

About top-level list: App version · Technical details › · **Diagnose & Protokolle ›** (new,
`focusRequester(diagnosticsRowFocus)`) · Privacy Policy · Terms of Use. (Diagnostic-logging + Export move
into the new overlay.)

**Diagnose overlay — ONE overlay with an internal `viewingLog` mode** (no third nested layer; extracted to
its own file `AboutDiagnosticsOverlay.kt` to keep `AboutSettingsPanel` under the detekt gate). Both modes
start with `SectionTitle("Diagnose & Protokolle")`:
- **rows mode** (`Column` + `verticalScroll`): Logging toggle (firstFocusModifier on it) · Export
  diagnostics · Logs löschen (→ multi-select dialog) · View current log › .
- **viewer mode**: a FIXED (non-scrolling) `Row` of two toggle pills **Normale Logs / Crash-Logs**
  (default Normal; firstFocusModifier on the first pill so it is always composed = a stable RIGHT-re-entry
  anchor) + a **Refresh** pill shown only for Normal; below, a `LazyColumn(weight = 1f)` of the log lines
  (each line a focusable row so the D-pad scrolls it). Empty → „Keine Logs vorhanden" /
  „Keine Crash-Logs vorhanden".
- **BackHandler:** viewer mode → back to rows mode (refocus the View-log row); rows mode →
  `closeDiagnostics` (refocus the About Diagnose row). So Back walks viewer → rows → About.

Keeping the log lines in an inner `LazyColumn` while the pills header stays fixed avoids the
LazyColumn-first-item-disposed bug we just fixed for the legal pages: the RIGHT-re-entry anchor (the first
pill / the toggle row) is never a scrolled-off lazy item.

### Architecture placement (respects CLAUDE.md)
- UI lives in `feature/settings` (new overlay file); no new module, no DI, no repository Flow/CRUD in
  composables. Log text is local UI state filled by a suspend callback on button press (existing
  `scope.launch { onXxx() }` pattern).
- File ops are **App-hoisted** (filesystem/MediaStore), passed from `MainActivity` → `SettingsRoute` →
  the panel:
  - `onReadLog: suspend (LogViewTarget) -> String?` — Events → newest event-log tail; Crashes → newest
    crash tail; null if none.
  - `onDeleteLogs: suspend (Set<LogClearTarget>) -> Unit` — Events → `clearLogs()`; Crashes →
    `clearCrashes()`.
- One new enum in `feature/settings` (`SettingsModels.kt`), mirroring `HistoryClearTarget`:
  `DiagnosticsLogKind { Events, Crashes }` — used for BOTH the viewer switch and the delete multi-select.
- New methods on the App-owned `DiagnosticsStore` (additive; retain `context`/`ContentResolver` for the
  public crash delete):
  - `readLatestLog(maxChars): String?` — newest event-log file's tail, re-sanitized per line.
  - `readLatestCrash(maxChars): String?` — newest private crash file's tail (already redacted).
  - `clearLogs()` — delete all event-log files, `activeFile = null`. Self-heals: with logging ON the next
    `log()` sees `activeFile` gone and creates a fresh file (no explicit restart needed).
  - `clearCrashes()` — delete private `crashDir` files (+ public MediaStore copies, per the decision).
    The public crash location constants (`CRASH_SUBDIR`, `CRASH_PREFIX`) move to package-level so both
    `CrashLogWriter.prunePublic()` and this share them (no duplicated MediaStore selection).

### Tail cap
Event log files reach 5 MB — the viewer shows only the **tail** (~last 400 lines / ~64 KB) read off
`Dispatchers.IO`. Crash files are small but capped the same way for safety.

## Strings (both locales, `:core:designsystem` only)
Row/title "Diagnose & Protokolle"; "Logs löschen"; delete-dialog title/body + the two category labels
(Normale Logs / Crash-Logs); "View current log"; the Normale-Logs / Crash-Logs switch labels; "Refresh";
"Keine Logs vorhanden" + "Keine Crash-Logs vorhanden". de + en.

## Completeness re-check (verified against the code)
- **No tests break:** no unit/androidTest asserts the About "Diagnostic logging"/"Export diagnostics"
  rows, so moving them into the sub-page is safe (grep clean).
- **collapseSubViewSignal:** `AboutSettingsPanel`'s `LaunchedEffect(collapseSubViewSignal)` currently
  resets `legalPage`/`showTechnical`; it must also reset `showDiagnostics` (and `viewingLog`) so OK on the
  already-selected About rail collapses the new overlay too.
- **firstFocusModifier stays single-target-per-visible-layer:** because the viewer is a MODE inside the
  one Diagnose overlay (not a nested layer), `detailFocusRequester` is only ever on the About list's first
  row + the current overlay's first element — the same 2-node situation as the (working) legal overlay,
  not a fragile 3-node stack.
- **Focus requesters needed:** `diagnosticsRowFocus` (About row), overlay entry (toggle row), viewer entry
  (first pill), View-log row — with `LaunchedEffect`-driven requests on open / mode-switch / close, exactly
  like `TechnicalDetailsOverlay` + `closeTechnical`.
- **Export/toggle unchanged:** the logging toggle keeps its VM-pref + App-`setConfig` split; Export keeps
  its FilePicker flow — both just relocate into the overlay.
- **Reads sanitized + capped + off-main-thread**; event-log delete self-heals; crash delete covers private
  + public (MediaStore ≥Q / file <Q).

## Risks / watch
- Overlay nesting 3 deep (About list → Diagnose → Log view): reuse the fixed `Column`+`verticalScroll`
  pattern + the alpha-hide/close-refocus logic; extract to a new file to stay under the detekt gate.
- Public crash delete = MediaStore delete on API≥Q / file delete on <Q — test on API 28 (floor) and 36
  (ceiling) per CLAUDE.md, since it touches scoped storage.
- All reads off the main thread, capped tail.

## Gates before done
`.\gradlew.bat detekt assembleDebug test`; `:feature:settings` + `:core:designsystem` + `:app` compile;
emulator smoke of: About restructure, multi-select delete, and the Normal/Crash viewer + refresh. No
commit / no push.
