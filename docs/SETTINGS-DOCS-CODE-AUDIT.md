# Settings — Docs vs. Code Audit

Status: audit only (no file changed). Scope: **Einstellungen-Bereich only** (Allgemein, Wiedergabelisten,
EPG, Optik, Wiedergabe, Kindersicherung, Speicher & Verlauf, Backup, Über die App). Home / Live-TV /
Filme / Serien / Player / Suche are out of scope.

Method: two parallel surveys (docs side over all settings-relevant `../vivicast-docs` files; code side over
`feature/settings` + `core/datastore` + app-hoisted settings callbacks), plus a direct read of the master
doc `design/screens/07-settings.md` (verbindlich v24).

**Governance note (matters for every decision below):** per `DOCS-GOVERNANCE.md` + data-model §6.23, the
**PRD data contracts (PRD-v1/10, /11) + the §6.19 DataStore registry are authoritative over the older
design docs** (`07-settings.md`, `wireframes/05-settings.md`, `components/settings.md`). For several items
the code already follows the newer PRD and the design doc is simply stale → the natural fix there is
"update the design docs", not "change the code". Those are flagged **[code matches PRD]**.

Delta types: **[MISSING]** = in docs, not built · **[EXTRA]** = built, not in docs · **[DIFFERS]** =
built differently · **[BUG]** = built but broken/incomplete.

Each decision point has an ID (D#) for the discussion.

---

## 1. Allgemein / General

Docs order: 1 App beim TV-Start, 2 **Startbereich**, 3 Doppel-Zurück, 4 Sprache, 5 Hintergrundaktualisierung,
6 **Sortierung merken**, 7 User-Agent.
Code order: Launch-on-boot, Background-refresh, **Resume last channel**, Double-back, Language, User-Agent.

| ID | Delta | Docs | Code |
|----|-------|------|------|
| D1 | [MISSING] **Startbereich** (Select Home/Live-TV/Filme/Serien, default Home; gilt ab nächstem Start) | option present, also drives auto-start target | not implemented |
| D2 | [MISSING] **Sortierung merken** (Toggle, default Ein) | option present | not implemented |
| D3 | [EXTRA] **Resume last channel on start** (Toggle, default Aus) | not in docs' Allgemein | implemented |
| D4 | [DIFFERS] Option order | fixed doc order | different order |
| D5 | [DIFFERS] User-Agent default | default = empty/`App-Standard` (null) | default value shown = `Vivicast/1.0`; max 200 chars |

Match: Launch-on-boot, Double-back, Background-refresh, Language (correctly under Allgemein; persisted
internally on the appearance store — cosmetic only), User-Agent as last row.

---

## 2. Wiedergabelisten / Playlists (Provider)

| ID | Delta | Docs | Code |
|----|-------|------|------|
| D6 | [EXTRA / CONTRADICTS] **Per-playlist User-Agent** in the provider editor | explicitly **forbidden** in v1 (only global UA; §9.4 / ADR-014 / CLAUDE.md baseline) | implemented (`ProviderEditor.kt:753`) |
| D7 | [DIFFERS] **Import-Auswahl (Live/Filme/Serien) für M3U** | docs: import selection only for Xtream, **not** for M3U | code shows Live/Movies/Series checkboxes for all types incl. M3U |
| D8 | [MISSING] **Zwischenablage** as an M3U input mode | docs: URL / Datei / Zwischenablage | code: URL / Datei only |
| D9 | [EXTRA] **Xtream Output-Format (HLS/TS)** in editor | not in docs | implemented |
| D10 | [MISSING] **Gruppen verwalten** (Anzeigen/Ausblenden/Sortieren) per provider | docs Provider-Edit lists it | not implemented |

Match: add flow (Name→Typ→form→test→save→refresh), M3U URL/File + test, Xtream server/user/pass + test,
active toggle, per-provider refresh interval + refresh-on-app-start, logo priority, EPG-source assignment,
delete, connection-test-gates-save, provider cards (status/type/include badges, expiry, max connections).

---

## 3. EPG

| ID | Delta | Docs | Code |
|----|-------|------|------|
| D11 | [MISSING] **EPG-Zukunft laden/behalten** (Select 1–14 Tage, default 7) | option present | not implemented (no `epgFutureRetentionDays`) |
| D12 | [DIFFERS] **Beim App-Start aktualisieren** default | default **Ein** (true) | default **Aus** (false) |
| D13 | [MISSING] **EPG-Aktualisierungshistorie** (display-only view) | option present | not implemented (source cards show last-refresh only) |

Match: global interval 24h default, past retention 1–14 default 1, refresh-on-playlist-change default Ein,
refresh-now, EPG source add/edit (Name/URL/test/Zeitversatz/active/delete), manual channel mapping,
priorities. Note the docs' `epgFutureRetentionDays` (D11) is itself missing from the §6.19 registry (doc-vs-doc C6).

---

## 4. Optik / Appearance

| ID | Delta | Docs | Code |
|----|-------|------|------|
| D14 | [MISSING] **Globale Logo-Standardreihenfolge** (Playlist/EPG/Lokaler Ordner, default Playlist) | option present | not implemented |
| D15 | [MISSING] **Logos-Ordner** (Ordnerauswahl, default Nicht gesetzt) | option present | not implemented |
| D16 | [MISSING] **EPG-Darstellung** (Detail mit 8 Toggles) | option present, 8 named toggles + defaults | not implemented |
| D17 | [DIFFERS] **Akzentfarbe** | mehrere vordefinierte Akzentfarben | only `Blue` (single-value, effectively a no-op picker) |

Match: Theme (Standard/HighContrast/AMOLED dark), Transparenz (0/25/50), Schriftgröße (S/M/L/XL),
Animationen (Aus/Schnell/Normal/Langsam). Minor: leftover `TransparencyLevel.Percent75` enum with no UI.

---

## 5. Wiedergabe / Playback

| ID | Delta | Docs | Code |
|----|-------|------|------|
| D18 | [MISSING] **Timeshift** (Toggle, default Ein) | option present | not implemented |
| D19 | [MISSING] **Maximale Timeshift-Dauer** (15/30/60/120, default 30) | option present | not implemented |
| D20 | [MISSING] **Timeshift-Speicher** (Auto/RAM/Festplatte, default Auto) | option present | not implemented |
| D21 | [DIFFERS] Option order (externer Player position) | external player last | external player before languages |

Match: Puffergröße, Audio/Video-Decoder (HW/SW), AFR (disabled <API 31), Audio-/Untertitel-Sprache,
Auto-Next + Countdown (5/10/15/30, default 10), Audio-Passthrough, Externer Player (Intern/Extern/Fragen).
Completion threshold correctly fixed at 95 % (no row). Minor: leftover `DecoderPreference.Automatic` enum with no UI.

---

## 6. Kindersicherung / Parental Controls

| ID | Delta | Docs | Code |
|----|-------|------|------|
| D22 | [BUG] **Freigabe für aktuelle Sitzung sperren** | should appear only when ≥1 area released, and reset all releases | row is display-only, **no onClick / does nothing** (`ParentalControlPanel.kt:185`); shown unconditionally |

Match: PIN set/change (4 digits), disable-PIN, protect Settings/Movies/Series/Adult, throttling
(5 attempts → 30s/60s/5min, survives restart), FSK-18 logic, PIN prompt on protected access.
Note: parental state uses app-side `PinSecurity`, not `UserPreferencesStore`; the `ParentalControlPreferences`
DataStore keys exist but are unused by the settings screen (internal — expected).

---

## 7. Speicher & Verlauf / Storage & History

| ID | Delta | Docs | Code |
|----|-------|------|------|
| D23 | [MISSING] **„Gesamter Verlauf"** option in Verlauf-löschen dialog | docs list it (`HistoryClearTarget.All` exists) | not offered as a checkbox |

Match: Medien-Cache info, Cache leeren (confirm), Verlauf löschen (Live-TV/Filme/Serien/Suche checkboxes).

---

## 8. Backup

Authoritative model (PRD-v1/10 + ADR-004/014, newest): **one always-encrypted `.vcbak`, local-only in v1,
mandatory passphrase**. Design docs (07-settings etc.) still show Standard-vs-Verschlüsselt modes + SMB/Drive
targets → **stale (doc-vs-doc C1)**.

| ID | Delta | Docs | Code |
|----|-------|------|------|
| D24 | [MISSING] **Backup-Ziel** row (v1: nur „Lokaler Speicher"; SMB/Drive post-v1) | option present | not surfaced (`target` persisted but ignored; `BackupSettingsState` passed to panel and dropped) |
| D25 | [MISSING] **Letzte Sicherung** display | option present (`Nie` when none) | not surfaced; `lastBackupAtMillis` persisted but **never written on export** |
| D26 | [MISSING] **Vorhandene Backups verwalten** (list/view/delete existing backups) | option present | not implemented |
| D27 | [DIFFERS] **Export target** | PRD-v1/10: write to **fixed** `Download/Vivicast/Backups/` **without picker** | code uses an in-app **folder picker** (from the recent internal-picker work) + per-flow last-folder memory |
| D28 | [code matches PRD] Standard vs. encrypted export modes | design docs show both modes | code = encrypted-only (matches PRD-v1/10) → **update design docs** |

Match (vs PRD-v1/10): encrypted `.vcbak`, passphrase + repeat + reveal, import via picker → passphrase →
validate → replace-only → confirm → parental disabled after restore + hint, inline errors.

---

## 9. Über die App / About (incl. Diagnostics)

Design docs order: 1 App-Info, 2 **Versionsinfos kopieren**, 3 Diagnose & Support, 4 **Lizenzhinweise**,
5 Datenschutz, 6 **Drittanbieter-Lizenzen**. PRD-v1/11 (authoritative) removes the clipboard/copy actions
and the retention selector (doc-vs-doc C2/C3).

| ID | Delta | Docs | Code |
|----|-------|------|------|
| D29 | [MISSING] **Diagnose-&-Support-Infoblock** (Provider-Anzahl, EPG-Anzahl, letzter Import, letzter Fehler, aktive Sprache …) | PRD-11 + design describe it | not shown (About shows only version/package/db/android/device) |
| D30 | [MISSING] **Lizenzhinweise** (OSS-Lizenzen) | option present | not implemented |
| D31 | [MISSING/DROPPED] **Drittanbieter-Lizenzen** | design lists it | intentionally dropped (commit c4e8e13) |
| D32 | [EXTRA] **Nutzungsbedingungen / Terms** overlay | not a named doc row (design has Lizenz/Datenschutz/Drittanbieter) | implemented (inline Terms) |
| D33 | [code matches PRD] **Versionsinfos kopieren** | design lists it; PRD-11 removes it | not implemented → matches PRD-11 → **update design docs** |
| D34 | [code matches PRD] **Aufbewahrungsdauer** selector (1–7) | design + §6.19 list it; PRD-11 removes it | not surfaced (retention forced to 7 into DiagnosticsStore, while persisted `retentionDays` default 1 is unused) → **update design docs**, but see D35 |
| D35 | [BUG] Diagnostics retention plumbing inconsistent | — | UI has no selector; `DiagnosticsPreferences.retentionDays` (default 1, coerced 1..7) is persisted but ignored; app hardcodes `DIAGNOSTICS_RETENTION_DAYS = 7` into `DiagnosticsStore`. Dead/confusing state. |

Match: App-Info (version/package/db/android/device), Diagnostics logging toggle (default Aus),
Export diagnostics (folder picker → ZIP, no content shown), Datenschutz overlay.

---

## Cross-cutting: persisted-but-not-surfaced / leftover state

| ID | Item | Note |
|----|------|------|
| D36 | `HistoryPreferences.maxRecentChannels=50`, `watchedThresholdPercent=95` | persisted but hardcoded on read, no UI, write ignores them. Docs: no UI expected, 95 % fixed → matches intent, but the persisted fields are dead. Cleanup candidate. |
| D37 | `TransparencyLevel.Percent75`, `DecoderPreference.Automatic` | enum values with no UI option; collapse to 50 %/Hardware. Cleanup candidate. |
| D38 | `BackupTargetPreference.Smb/GoogleDrive` | reserved post-v1 per docs; persisted, no UI → consistent with "reserved", but tied to D24. |
| D39 | `AboutAppState.languageTag`, `timeZoneId` | in state, never rendered. |
| D40 | `ParentalControlPreferences` DataStore keys | exist but unused by settings (parental uses `PinSecurity`). Dead keys. |

---

## Doc-vs-doc contradictions (from the docs survey) — resolve when updating docs

- **C1** Backup type/targets: design shows Standard/Encrypted + SMB/Drive; PRD-10/ADR-004 = encrypted-only, local v1. **PRD wins.**
- **C2** Versions-/Support-kopieren: design has it; PRD-11 removes it. **PRD wins.**
- **C3** Diagnostics retention selector + session/segment model: design/§6.19/§7 keep it; PRD-11 removes selector + uses flat rotating logs. **PRD wins.**
- **C4** PRD-10 backup scope mentions "provider-spezifischer User-Agent" while §9.4/ADR-014 forbid per-provider UA — stale wording (ties to D6).
- **C5** Cache-info location: §7.7 says under Backup; everything else says Speicher & Verlauf. **Speicher & Verlauf wins.**
- **C6** `epgFutureRetentionDays` named in §7.5/ADR-002 but missing from §6.19 registry (ties to D11).
- **C7** Diagnostics ZIP filename/entry layout differ design vs PRD-11. **PRD wins.**
- Note: several docs cite `prd/PRD-v1/04-search-settings-player-requirements.md` which **does not exist** in the repo.

---

## Decision buckets (for the walk-through)

- **A — code already follows the newer PRD, design doc stale → update docs:** D28, D33, D34 (+ C1/C2/C3/C5/C7).
- **B — feature in docs, not built → build it or drop from docs:** D1, D2, D8, D10, D11, D13, D14, D15, D16,
  D18, D19, D20, D23, D24, D25, D26, D29, D30.
- **C — built but not in docs / contradicts docs → keep+document or remove:** D3, D6, D7, D9, D27, D31, D32.
- **D — bugs / cleanup:** D5, D12, D21, D22, D35, D36, D37, D39, D40.

---

## Decisions (from the walk-through with the owner)

Legend: **docs→code** = keep the app as-is, update the docs to match reality · **code→docs** = change
the app to match the docs.

### Section 1 — Allgemein
- **D1 Startbereich → docs→code.** No start-area setting is added. App starts on Home; "Resume last
  channel" stays the only start-steering. Remove Startbereich from the docs.
- **D2 Sortierung merken → docs→code.** No such toggle. Remove from the docs.
- **D3 Resume last channel → docs→code (keep).** Feature stays; add it to the docs' Allgemein list.
- **D5 User-Agent default → docs→code.** Keep visible default `Vivicast/1.0`; update docs from
  "empty/App-Standard" to `Vivicast/1.0`.
- (D4 order settles once the final Allgemein option set is fixed.)

### Section 2 — Wiedergabelisten
- **D6 Per-playlist User-Agent → docs→code (keep), with a baseline change.** Feature stays. Requires
  updating the **CLAUDE.md project baseline** ("No provider-specific … User-Agent in PRD v1") **and**
  PRD §9.4 / ADR-014 so per-provider User-Agent becomes a v1 feature. High-impact doc change.
- **D7 M3U import selection → docs→code (keep).** Live/Filme/Serien checkboxes stay for M3U too; update docs.
- **D8 Zwischenablage M3U input → docs→code.** Only URL + Datei; remove clipboard input from docs.
- **D9 Xtream Output-Format (HLS/TS) → docs→code (keep).** Stays; add to docs.
- **D10 Gruppen verwalten → code→docs (BUILD).** Implement per-playlist channel-group management
  (show/hide/sort).

### Section 3 — EPG
- **D11 EPG-Zukunft laden/behalten → docs→code (drop).** No future-retention setting; remove from docs
  (incl. §7.5/ADR-002/C6).
- **D12 EPG app-start refresh default → docs→code.** Keep default Aus; update docs to Aus.
- **D13 EPG-Aktualisierungshistorie → docs→code (drop).** No dedicated history view; remove from docs.

### Section 4 — Optik
- **D14 Globale Logo-Standardreihenfolge → docs→code (drop).** No global logo-priority row; keep only
  the per-playlist logo priority. Remove from docs.
- **D15 Logos-Ordner → code→docs (BUILD).** Implement local-logo folder + local-logo resolution
  (folder picker, SAF persistence). **Dependency note:** since D14 (global priority) is dropped, the
  local-folder logos still need a priority mechanism — the per-playlist logo-priority choice likely has to
  gain a "local folder" option so local logos are reachable. Resolve during implementation.
- **D16 EPG-Darstellung → docs→code (drop).** No configurable EPG appearance; fixed look. Remove the
  8-toggle detail from docs.
- **D17 Akzentfarbe → code→docs (BUILD).** Implement multiple predefined accent colors (with contrast
  guard). Replaces the current single-value "Blue" no-op picker.

### Section 5 — Wiedergabe
- **D18–D20 Timeshift (toggle + max duration + storage) → docs→code (drop).** No timeshift in v1.
  Remove all three settings **and** the timeshift references in the docs (incl. ADR-006 timeshift-strategy,
  ADR-013, §6.19 timeshift keys, Live-TV seek behavior). Live-TV seek is not available.
- **D21 Option order → docs→code.** Keep the code order (external player before language rows); update
  docs to match.

### Section 6 — Kindersicherung
- **D22 Freigabe für aktuelle Sitzung sperren → docs→code (remove).** Remove the non-functional row from
  the app and drop it from the docs (no manual session lock).

### Section 7 — Speicher & Verlauf
- **D23 „Gesamter Verlauf" option → docs→code (drop).** Only per-type selection stays; remove from docs.
  (`HistoryClearTarget.All` becomes a cleanup candidate.)

### Section 8 — Backup
- **D24 Backup-Ziel row → docs→code (drop).** No target row (v1 is local-only anyway); remove from docs;
  clean up the unused `target` plumbing (`BackupSettingsState`, `BackupTargetPreference`).
- **D25 Letzte Sicherung → docs→code (drop).** No last-backup display; remove from docs; clean up the
  never-written `lastBackupAtMillis`.
- **D26 Vorhandene Backups verwalten → docs→code (drop).** No backup-management list; import stays via the
  file picker. Remove from docs.
- **D27 Export target → docs→code (keep picker).** The in-app folder picker stays; update **PRD-v1/10**
  from "fixed folder, no picker" to "user chooses the target folder".
- **D28 Export modes → docs→code (encrypted-only).** Encrypted-only (matches code + PRD-v1/10); remove the
  Standard-mode from the stale design docs.

### Section 9 — Über die App
- **D29 Diagnose-&-Support-Infoblock → docs→code (drop).** About shows only version/package/db/android/
  device; remove the support-info block from docs.
- **D30 Lizenzhinweise → docs→code (drop).** No license-notices page; remove from docs.
- **D31 Drittanbieter-Lizenzen → docs→code (stay dropped).** Remove from design docs.
- **D32 Nutzungsbedingungen/Terms → docs→code (keep).** Terms overlay stays; add it to the docs' About list.
- **D33 Versionsinformationen kopieren → docs→code.** No copy action (matches code + PRD-v1/11); remove
  from the stale design docs.
- **D34 Aufbewahrungsdauer-Selektor → docs→code (drop).** No retention selector (fixed internal, matches
  code + PRD-v1/11); remove from design docs + §6.19.
- **D35 Retention plumbing → fix to fixed 7 days.** Keep the effective 7-day retention; remove the unused
  persisted `DiagnosticsPreferences.retentionDays`.

### Cross-cutting cleanup
- **D36–D40 → yes, clean up.** Remove the dead/obsolete persisted state made redundant by the decisions
  above.

---

## Consolidated action plan (post-discussion; nothing done yet)

### A. App/code work
1. **Build (new features):** per-playlist channel-group management (D10); local logos folder + local-logo
   resolution incl. a "local folder" option in the per-playlist logo priority (D15); multiple predefined
   accent colors with contrast guard (D17).
2. **Fix/remove:** remove the non-functional "Freigabe für aktuelle Sitzung sperren" row (D22); fix
   diagnostics retention to a single fixed 7 days and drop the unused `retentionDays` (D35).
3. **Cleanup dead state (D23–D25, D36–D40):** `HistoryClearTarget.All`; backup `target` plumbing +
   `BackupSettingsState` + `BackupTargetPreference.Smb/GoogleDrive`; unused `lastBackupAtMillis`;
   `HistoryPreferences.maxRecentChannels/watchedThresholdPercent` dead fields; `TransparencyLevel.Percent75`;
   `DecoderPreference.Automatic`; `AboutAppState.languageTag/timeZoneId`; unused `ParentalControlPreferences`
   DataStore keys.

### B. Baseline / authoritative-doc changes (code deliberately diverges from current rules)
- **D6:** allow per-provider User-Agent in v1 → update **CLAUDE.md baseline** + **PRD §9.4** + **ADR-014**
  (also reconciles C4).
- **D27:** backup export uses a user-chosen folder → update **PRD-v1/10** (fixed-folder → picker).

### C. Design-doc rewrite (make docs match reality) — main file `design/screens/07-settings.md`, plus `design/wireframes/05-settings.md`, `design/components/settings.md`, `design/components/about-app.md`, `design/screens/08-playlist-epg.md`, and the PRD contracts / §6.19 registry / ADRs
- **Allgemein:** remove Startbereich (D1) + Sortierung merken (D2); add Resume-last-channel (D3); UA default `Vivicast/1.0` (D5).
- **Wiedergabelisten:** keep M3U import selection (D7); remove Zwischenablage input (D8); add Xtream output format (D9); add per-playlist User-Agent (D6); document group management (D10, once built).
- **EPG:** remove future retention (D11, + §7.5/ADR-002/C6); app-start default Aus (D12); remove history view (D13).
- **Optik:** remove global logo priority (D14) + EPG-Darstellung (D16); document logos folder (D15) + multi-accent (D17).
- **Wiedergabe:** remove Timeshift toggle/duration/storage (D18–D20, + ADR-006/ADR-013/§6.19/Live-TV-seek); fix option order (D21).
- **Kindersicherung:** remove session-lock row (D22).
- **Speicher & Verlauf:** remove "Gesamter Verlauf" (D23); fix cache-info location C5.
- **Backup:** encrypted-only (D28/C1); user-chosen export folder (D27); remove Backup-Ziel (D24) + Letzte Sicherung (D25) + Vorhandene Backups verwalten (D26).
- **Über die App:** remove version-copy (D33/C2), support-infoblock (D29), retention selector (D34/C3), Lizenzhinweise (D30), Drittanbieter-Lizenzen (D31); add Terms (D32).
- **Housekeeping:** resolve C1–C7; fix the dangling reference to the non-existent `prd/PRD-v1/04-search-settings-player-requirements.md`.

### Summary counts
- Build new: **3** (D10, D15, D17). Fix/remove in app: **2** (D22, D35). Dead-state cleanup: **D23–D25, D36–D40**.
- Keep app, change baseline/PRD: **2** (D6, D27). Everything else = **doc updates to match code**.

---

## Docs-rewrite status (2026-07-13)

Owner chose **"Erst Docs-Rewrite"**. Settings-screen docs done, then the wider out-of-settings ripples
propagated corpus-wide (Batch 2, below). **All docs are now aligned; no app/code work done yet** — the
build/fix/cleanup items D10/D15/D17/D22/D35/D36–D40 remain for a later session.

### DONE — settings-screen docs aligned to the decisions
- `CLAUDE.md` baseline (D6: per-provider User-Agent now allowed).
- `design/screens/07-settings.md` (master; all sections).
- `design/wireframes/05-settings.md`, `design/wireframes/08-about-app.md`.
- `design/components/settings.md`, `design/components/about-app.md`.
- `design/mockups/05-settings-search-mockup-spec.md`.
- `design/screens/08-playlist-epg.md`.
- `prd/PRD-v1/06-data-model.md` (§6.19 registry + Allgemein/Wiedergabe/Diagnose/Backup prose),
  `prd/PRD-v1/07-background-jobs-performance.md` (§7.5 EPG future, §7.7 cache location),
  `prd/PRD-v1/08-android-tv-security.md` (§9.4 per-provider UA),
  `prd/PRD-v1/10-backup-import-requirements.md` (folder picker), `prd/PRD-v1/11-about-app-requirements.md`.
- `architecture/decisions/ADR-002/004/006/013/014` (EPG future, backup folder, timeshift native-DVR
  redesign, UA). **ADR-006 fully rewritten**: timeshift = native HLS DVR window, no settings, no app
  buffer (it is *built and player-supported*, just has no Settings options — correction to the earlier
  "deferred/not-in-v1" framing).

### DONE — corpus-wide propagation (Batch 2, 2026-07-13)
Owner approved **"Jetzt umschreiben / umbenennen / aktualisieren / beheben"** — the out-of-settings
ripples are now propagated across the whole `vivicast-docs` corpus (file-partitioned subagents +
direct fixes). Final verification grep is clean (0 `Standard-Backup`, 0 dead `04-…` refs, no
configurable-timeshift framing, no Startbereich-as-setting).

- **Timeshift → native-DVR model** (NOT removed — it is built and player-supported): `03-player`,
  `02-live-tv-requirements`, `interaction/02-player-timeline-controls`, `design-system/03-components`,
  `13-test-strategy`, ADR-006 (rewrite), ADR-013 (Live-TV-Seek section). **`timeShiftMinutes`
  (EPG Zeitversatz) untouched — different feature.**
- **"Standard-Backup" → "Backup"** corpus-wide with the **semantic correction**: the single always-
  encrypted `.vcbak` *includes* provider/EPG credentials + private source URLs (encrypted, passphrase-
  protected, nothing in cleartext); PIN check values / protection flags stay excluded from restore.
  Touched: `prd/PRD-v1/05-iptv-epg-favorites`, `06-data-model`, `07-background-jobs-performance`,
  `08-android-tv-security`, `09-implementation-and-dod`, `11-about-app-requirements`,
  ADR-010, ADR-014, `architecture/diagrams/01-system-context`, `architecture/diagrams/06-backup-restore-flow`.
- **Startbereich → Home-fixed + resume-toggle**: `01-product-overview`, `interaction/nav`,
  `design-system/03-components`.
- **Dead `04-search-settings-player-requirements.md` refs** redirected/removed everywhere
  (incl. `architecture/diagrams/05-player-progress-flow`).
- **`09-dod` mandatory encryption**: "optional verschlüsselte Backups" → "immer verschlüsselte Backups".

### DEFERRED — CODE work only (separate session)
Docs are aligned corpus-wide. What remains is **app/code**, untouched:
- **Build:** D10 (per-playlist group management), D15 (local logos folder), D17 (multiple accent colors).
- **Fix:** D22 (remove dead session-lock button), D35 (diagnostics retention → fixed 7 days + remove
  dead field).
- **Cleanup dead state:** D23–D25, D36–D40.
- Keep-code decisions D6/D27 need no code change (docs/baseline already updated).

All doc edits are **uncommitted** (`vivicast-docs` sibling — not a git repo — + this repo's
`CLAUDE.md`/audit). Not committed yet; awaiting explicit per-action approval.

## Round 2 — deep flow/field re-audit (2026-07-13)

Trigger: owner spotted that the Wiedergabelisten add/edit flow in `wireframes/05-settings.md`
differs completely from the shipped code. Scope re-opened for a **flow / field / label / order**-level
pass (round 1 covered coarse option *presence* only). Method: 6 read-only Explore agents (one per area
group) + direct read of the provider editor; no files changed during research. All ~55 deltas were
walked through with the owner. Directions below.

Legend: **docs→code** = update docs to the shipped code (doc edit). **code→docs** = change code to match
docs. **cleanup** = remove dead code/strings. **coupled** = rides on an already-decided build.

### Wiedergabelisten (provider editor)
Key structural finding: docs (wireframe + `components/settings.md` "Step Dialog") prescribe a
**multi-step add wizard**; code is a **single scrolling form** with a 3-way source-type row
(M3U URL | M3U File | Xtream).
- **P-1** add/edit = single form → **docs→code** (rewrite wireframe add-flow + components "Step Dialog"
  to the single-form editor). Folds P-2 (credentials inline), P-11 (no separate Typ row), P-12
  (`settings_provider_step_*` strings become dead → cleanup).
- **P-3** import checkboxes shown for all source types incl. M3U → **docs→code**.
- **P-4** Xtream output-format only in edit mode → **docs→code**.
- **P-5** logo-priority 3rd option "Lokaler Ordner" → **coupled to D15** (docs keep 3; 3rd ships with the
  logos-folder feature).
- **P-7** per-playlist "Jetzt aktualisieren" → **docs→code** (drop; global "Alle aktualisieren" stays the
  only manual trigger).
- **P-8** EPG-source assignment = link/unlink toggle only (priority internal) → **docs→code**.
- **P-9** last-update / Xtream expiry / max-connections shown on the overview card → **docs→code**.
- **P-10** card shows presence badges, not counts → **docs→code**.

### Allgemein + Shell
- **A-Shell-1** Settings reopens on last-viewed section → **code→docs** (always open on Allgemein; remove
  `last_settings_section` DataStore key → resolves A-Shell-2).
- **A-Allg-1** resume-row label "Sender" (code+§6.19) vs "Kanal" (UI docs) → **code→docs "Kanal"**
  (consistent with required term "Kanäle"; change string + §6.19).
- **A-Allg-3** empty global UA coerced to "Vivicast/1.0" → **docs→code** (align docs+§6.19 to the forced
  default; fix the misleading UA dialog hint string).
- **A-Allg-4** row help/description never rendered → **docs→code** (drop the "kurze Beschreibung"
  requirement; help strings become dead → cleanup).
- **A-Allg-2/6** §6.19 order + logical key names → **docs→code** (reorder + list the real snake_case keys).
- **A-Allg-7** "Systemstandard" vs "[System]", **A-Shell-4** About pinned at rail bottom, **A-Shell-3**
  language-refocus flow → **docs→code** (align / document).

### Optik
- **O-1** Logos-Ordner missing = **D15** (build). **O-2** single accent color = **D17** (build).
- **O-3** theme label → **code→docs "Dunkel kontrastreich"**.
- **O-4** Color Select Row vs generic text dialog → **docs→code** (text dialog).
- **O-5** transparency 0/25/50 undocumented → **docs→code** (document values).

### Wiedergabe
All options present in code; timeshift correctly absent (native-DVR).
- **W-1** external-player labels → **docs→code "Intern/Extern"**.
- **W-2** AFR row title → **docs→code "Automatische Bildwiederholrate"**.

### Kindersicherung
- **K-1** dead session-lock row = **D22** (remove).
- **K-2** "Deaktivieren" at position 2 → **docs→code**.
- **K-3/4/5** row values → **code→docs** (status shortforms: Gesetzt/Nicht gesetzt · Deaktiviert · Öffnen —
  change strings + logic).
- **K-6** "PIN zuerst" hint transient → **docs→code**.

### EPG
- **E-G1** global options behind sub-panel → **docs→code**. **E-G6** retention stepper → **docs→code**.
  **E-G7** interval "Aus" (0h) → **docs→code** (document). **E-E1** editor status = toggle, no header badge
  → **docs→code**. **E-G3 / E-G5 / E-G2 / E-G8 / E-E2 / E-E7** → **docs→code** (align labels/order/rows).
- **E-G4** interval label → **code→docs "Globales Aktualisierungsintervall"**.
- **E-E3** Zeitversatz unit = minutes → **docs→code** (wireframe "Stunden"→Minuten). NOTE: this is the EPG
  offset `timeShiftMinutes`, distinct from player timeshift.
- **E-E4/5/6** editor info rows (Nutzung durch Provider / Letzte Aktualisierung / Programme) → **docs→code**
  (remove from editor spec; card shows last-update + channel count; provider-usage / program-count not
  surfaced).

### Über die App
- **U-6** 4 sub-panels vs flat 9-row list → **docs→code** (flat list; folds U-11).
- **U-1..U-5** missing info rows → **partial**: **code→docs** add Build-Nummer + Player-Engine;
  **docs→code** drop App-Name / UI-Technologie / Build-Typ.
- **U-7** "Datenschutzbestimmungen" (code+PRD) vs "-informationen" (UI docs) → **docs→code
  "Datenschutzbestimmungen"**.
- **U-8** legal close = Back only → **docs→code**. **U-9** stale "Copied" state in screen07 → **docs→code**
  (remove). **U-10** PRD support-info block permit vs other-docs forbid → **docs→code** (align PRD to
  forbid; code has none).

### Speicher & Verlauf + Backup
- **S-1** "Cache Informationen" → **code→docs "Medien-Cache"**.
- **S-2** history category labels → **docs→code** (short labels).
- **S-3** second PIN prompt before clear-cache / clear-history → **docs→code** (settings-entry PIN
  suffices; no per-action prompt).
- **S-4** cache-dialog wording/badge + **S-6** cache-row refresh action → **docs→code** (align / document).
- **B-2** restore preview missing App-Version + Migration flag → **docs→code** (reduce spec to the actual
  preview fields).
- **B-3** restore error strings diverge → **docs→code** (align doc list to emitted strings). FLAG:
  storage/IO restore errors are not emitted by the code — possible robustness gap, separate follow-up.
- **B-4** min-8 passphrase rule + **B-5** stale "Verwaltung vorhandener Backup-Dateien" sentences →
  **docs→code** (add rule to spec; delete stale sentences).
- **B-6** dead backup/cache strings + **B-7** vestigial `backup.targetType` pref → **cleanup** (code).

### Execution buckets (round 2)
- **Doc edits (docs→code):** the bulk — provider single-form rewrite, EPG sub-panel/stepper/minutes/editor
  rows, About flat list, §6.19 keys, plus many label/order aligns across all areas.
- **Code changes (code→docs):** A-Shell-1 (always-Allgemein + remove key), A-Allg-1 ("Kanal"), O-3, E-G4,
  S-1 label strings; K-3/4/5 parental status values (strings+logic); U-1..U-5 add Build-Nummer +
  Player-Engine; fix A-Allg-3 UA hint string.
- **Cleanup (code):** `settings_provider_step_*`, unused row-help strings, `last_settings_section`,
  B-6 dead strings, B-7 `backup.targetType`.
- **Round-1 deferred builds (unchanged):** D10, D15 (+P-5), D17 (O-2), D22 (K-1), D35.

### Round-2 execution status (2026-07-13)
- **Docs-rewrite (docs→code, Group A): DONE.** Applied across the corpus via 6 file-partitioned edits
  (`wireframes/05-settings` done directly; `screens/07-settings`, `components/settings`,
  `wireframes/08-about-app` + `components/about-app`, PRD `06`/`10`/`11`, `screens/08-playlist-epg` +
  `mockups/05` via agents) plus residual sweeps in PRD `05` (single-form add flow) and `09` (DoD add-flow
  line, dropped the non-existent M3U "Zwischenablage" mode). Verified: no multi-step wizard / "Step Dialog"
  left, no "Datenschutzinformationen", no "Immer intern", no "kurze Beschreibung" requirement, EPG offset
  in minutes everywhere, §6.19 uses the real snake_case keys, restore preview trimmed, backup error list
  aligned. Provider add/edit now documented as a single scrolling form.
- **Code changes (code→docs) + cleanup: NOT started** — A-Shell-1 (always-Allgemein + remove key),
  A-Allg-1 ("Kanal"), O-3, E-G4, S-1 label strings; K-3/4/5 parental status values; U-1..U-5 add
  Build-Nummer + Player-Engine; fix A-Allg-3 UA hint string; dead-string/pref cleanup. Awaiting go-ahead.
- **Round-1 deferred builds** (D10/D15/D17/D22/D35) unchanged.
- All doc edits uncommitted (`vivicast-docs` is not a git repo; this repo's `CLAUDE.md`/audit uncommitted).
