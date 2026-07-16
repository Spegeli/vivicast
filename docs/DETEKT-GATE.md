# Detekt Gate (P2-08)

Static-analysis gate that keeps god-files from re-appearing after the P0/P1 splits. It is **not** a
full style/format linter — it deliberately checks only file/class/method size + complexity so the
signal stays small and meaningful for a Compose/Android multi-module project.

## How to run

```powershell
.\gradlew.bat detekt
```

One root-applied detekt task scans every module's `src/main/java`. It fails on any **new**
(non-baselined) size/complexity violation.

Regenerate the baseline after an intentional, accepted change to a large file:

```powershell
.\gradlew.bat detektBaseline
```

## Setup

- detekt **1.23.8** (stable) via the version catalog (`gradle/libs.versions.toml`:
  `detekt = { id = "io.gitlab.arturbosch.detekt" }`).
- Applied once in the root `build.gradle.kts` (`buildUponDefaultConfig = true`), scanning all
  subproject `src/main/java` folders → a single config + single baseline + single `detekt` task.
- Config: `config/detekt/detekt.yml`. Baseline: `config/detekt/baseline.xml`.

## Rules that matter (thresholds)

| Rule | Threshold | Why |
|---|---|---|
| `LargeClass` | 600 | Catches new god-classes |
| `LongMethod` | 150 | Catches new god-methods/composables |
| `CyclomaticComplexMethod` | 20 | Overly branchy methods |
| `NestedBlockDepth` | 6 | Deeply nested logic |
| `ComplexCondition` | 5 | Tangled boolean conditions |
| `TooManyFunctions` | 40 file / 30 class | God-files by function count; composables ignored |
| `LongParameterList` | 10 | Tolerant — Compose/Route callbacks legitimately take many params; `@Composable` + default params ignored |

All other default rulesets (`style`, `comments`, `naming`, `potential-bugs`, `exceptions`,
`coroutines`, `performance`, `empty-blocks`) are **switched off** — they produce mostly Compose noise
and are not the purpose of this gate. `MagicNumber` and `MaxLineLength` are not enforced (no
formatter in the project).

## Why a baseline exists

`config/detekt/baseline.xml` records the size/complexity violations that already existed when the gate
was introduced (originally **36**; **34** currently, after later feature work — D10 group management +
the non-blocking-import rebuild), so the gate does not block the build on accepted legacy files while
still failing on anything **new**. These are known/accepted and intentionally not refactored here.

### Known large files tolerated via baseline

- `feature/player/.../PlayerRoute.kt` (`PlayerRoute`) — not split yet (Split-Plan: not immediate).
- `app/.../MainActivity.kt` (`VivicastApp`) — top-level app composable.
- Feature route hosts: `RoomLiveTvRoute`, `RoomMoviesRoute`, `RoomSeriesRoute`, `HomeContent`,
  `RoomChannelColumn`, `RoomEpgColumn`.
- Settings composables: `SettingsRoute`, `EpgSettingsPanel`, `EpgSourceEditor`,
  `ManualEpgMappingPanel`, `ProviderSettingsPanel`, `ProviderAddFlow`, `ProviderEditor`,
  `PlaybackSettingsPanel`, `SettingsRowIcon`.
- `feature/settings/.../SettingsViewModel.kt` (`TooManyFunctions` — aggregates all settings sections).
- Room DAOs `CatalogDao`, `EpgDao` (`TooManyFunctions`).
- `data/media/.../RoomCatalogImportRepository.kt` (`TooManyFunctions` — the staged delta-merge import
  helpers added by the non-blocking-import rebuild).
- `feature/settings/.../SettingsRoute.kt` (`LongParameterList`/`LongMethod`/`CyclomaticComplexMethod` — a
  large route host; the diagnostics-log callbacks added a couple more params).
- `StandardBackupRestoreValidator.kt` validation method.

`core/designsystem/VivicastComponents.kt` was split in P2-06 into nine cohesive designsystem files
(VivicastSurfaces / Layout / Badges / Panels / Dialogs / Inputs / Cards / Navigation / Player), so it
no longer exists. `core/database/VivicastMigrations.kt`, `core/player/VivicastPlayerController.kt` and
`data/media/RoomCatalogImportRepository.kt` / `worker/RefreshExecution.kt` are still intentionally
**not** split now; any current hits are covered by the baseline.

## How to handle new violations

1. Prefer to **fix** the code (split the file/method) — the gate exists to nudge that.
2. Only if the size is genuinely justified, regenerate the baseline (`detektBaseline`) and note why in
   the commit.
3. Do **not** loosen the thresholds to hide a real god-file.

## Recommended usage

Run `.\gradlew.bat detekt` before committing structural changes. No CI wiring was added in P2-08;
adding a CI step that runs `detekt` is a trivial future follow-up.
