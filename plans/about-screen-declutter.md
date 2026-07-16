# About screen declutter (Option B + inline "Technical details" sub-page)

> Status: **COMPLETED** — shipped + verified on emulator, user-approved.
>
> Merged version row
> `0.1.0 (Build 1)`; "Technische Details" inline sub-page (Package, DB, Android `16 (API 36)`, Device,
> real Media3 version, Build type, CPU ABI); diagnostics export extended (buildNumber/playerEngine/
> buildType/cpuAbi); standalone build-number row + dead strings removed. Green: assembleDebug, detekt,
> feature:settings tests, app androidTest compile.
> Follow-up: sub-page rows regrouped (app/build first, device last); CPU architecture folded into the
> device-model row in parens ("Model (abi)"), CPU row + labels removed (cpuAbi stays a separate export field).

## Goal

Make "Über die App" tidier so the actions (Diagnostics, Export, Privacy, Terms) are reachable without
scrolling past a block of read-only info rows. Decisions locked with the user:

1. **Merge only** App version + Build number into one row → value **`0.1.0 (Build 1)`**
   (`"$appVersion (Build $buildNumber)"`). No other rows merged.
2. **Option B:** App version stays inline; the technical read-only rows move behind a **"Technische
   Details"** row.
3. **Technische Details = inline sub-page** (NOT a popup) — same mechanic as the existing Privacy/Terms
   legal pages in this panel (`AboutLegalOverlay`): full-panel sub-view, Back returns, focus survives.

## Layout

```
BEFORE (12 flat rows)              AFTER (top level, 6 rows)
App version   0.1.0                App-Version        0.1.0 (Build 1)
Build number  1                    Technische Details        ›   → inline sub-page
Package name  ...                  ─────────────────────────────
Database ver  16                   Diagnose-Logging       An/Aus
Android ver   16                   Export                     ›
Device model  ...                  Datenschutz                ›
Player engine Media3               Nutzungsbedingungen        ›
Diagnose-Logging …
Export …                           Technische-Details sub-page (Back → About):
Datenschutz …                        Package name / DB version / Android version /
Nutzungsbed. …                       Device model / Player engine (+ additions below)
```

## Technical-details sub-page rows

Keep the 5 current read-only values, plus a few genuinely useful ones (support/debug value). Excludes
noise on purpose.

| Row | Value | Source | Keep/Add | Why |
|---|---|---|---|---|
| Package name | `com.vivicast.tv` | existing | keep | — |
| Database version | `16` | existing (`VIVICAST_DATABASE_VERSION`) | keep | migration/debug |
| Android version | `16 (API 36)` | `Build.VERSION.RELEASE` + `SDK_INT` | **augment** | API level = the precise compat number |
| Device model | `Google AOSP TV on x86` | existing (`Build.MODEL`) | keep | — |
| Player engine | `AndroidXMedia3/1.x.x` | `MediaLibraryInfo.VERSION_SLASHY` (Media3) | **upgrade** | real lib version beats hardcoded string — key for playback bug reports |
| Build type | `Debug` / `Release` | `BuildConfig.DEBUG` | **add** | support-critical: debug builds behave differently (e.g. debug-only TLS trust-all) |
| CPU ABI | `x86` / `arm64-v8a` | `Build.SUPPORTED_ABIS.first()` | **add** | codec/arch issues |

Screen-resolution row **dropped** (user). Verify `MediaLibraryInfo.VERSION_SLASHY` at implementation.

**Deliberately excluded (noise):** uptime, install date, free RAM (live), Kotlin/Compose versions,
WebView, manufacturer (model already covers it), locale (already in settings).

## Architecture placement

- Values are computed in `aboutAppState()` (app `SettingsPreferenceMappers.kt` — app-hoisted, reads
  `Build`/`PackageManager`/`BuildConfig`/`MediaLibraryInfo`). New fields added to `AboutAppState`
  (feature model, `SettingsModels.kt`).
- `AboutSettingsPanel` (feature/settings): drop the standalone Build-number row; merge into the version
  row; replace the 5 inline info rows with one "Technische Details" row that opens the sub-page.
- Sub-page = a `TechnicalDetailsOverlay` composable modelled on `AboutLegalOverlay` (alpha-hide the About
  list, render over the host GlassPanel, Back closes, focus-survival). Share the overlay scaffold with
  the legal one if it stays clean; otherwise a parallel overlay (the focus-survival logic is the part
  worth reusing). Sub-page state = a small `showTechnicalDetails` flag (or fold into the existing
  sub-view state).
- Strings: new/renamed labels in **both** designsystem locale files (`about_app_version` value format,
  `about_technical_details` title, `about_build_type`, `about_cpu_abi`, augmented Android/player labels).
  Drop the now-unused `about_build_number` if nothing else uses it.

## Backup / diagnostics

- **Backup: no.** All About values are runtime device/build facts (`Build.*`, PackageManager,
  `BuildConfig`, `MediaLibraryInfo`, app constants), recomputed per device on every launch. They are not
  user data; backing them up would be wrong (a restore on a new device/version must show the CURRENT
  facts). The backup header already carries what it needs for its own purpose — `schemaVersion` +
  `exportMode` (`StandardBackup.kt:165-166`) for restore-compatibility, nothing to add.
- **Diagnostics export: the correct home — CONFIRMED (user).** It already includes `appVersion` +
  `databaseVersion` (`DiagnosticsStore.kt:165-169`). Extend the export's About snapshot with the new
  values (real Media3 version, build type, CPU ABI, API level) — computed anyway for the sub-page, cheap,
  and exactly support context. Add matching fields to `DiagnosticsAbout` / the export JSON.

## Gates / tests

- `detekt` + `assembleDebug` + relevant `test` green. Watch the detekt baseline: changing
  `AboutSettingsPanel`/`AboutAppState` signatures may need a baseline signature sync (as seen this session).
- No persistence/DB/backup impact (pure display info). No migration.

## Decided

- 4 additions confirmed: Android API level, real Media3 version, Build type, CPU ABI.
- Diagnostics-export extension confirmed. Backup: no.
- Screen-resolution row dropped.
- Awaiting GO to implement.
