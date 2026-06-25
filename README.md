# Vivicast

Vivicast is an Android TV IPTV client for package `com.vivicast.tv`.

This repository contains the app implementation. The product, architecture, design, interaction, test-strategy, and Codex reference documentation lives in the separate repository:

```text
Spegeli/vivicast-docs
```

## Source of Truth

For app implementation, use `Spegeli/vivicast-docs` as the source of truth.

For local Codex work, keep `Spegeli/vivicast-docs` available as a sibling repository at:

```text
../vivicast-docs
```

Codex startup order:

1. `Spegeli/vivicast/AGENTS.md`
2. `Spegeli/vivicast/README.md`
3. `Spegeli/vivicast-docs/codex/README.md`
4. `Spegeli/vivicast-docs/DOCS-GOVERNANCE.md`
5. `Spegeli/vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
6. `Spegeli/vivicast-docs/codex/coding-rules.md`
7. affected PRD, ADR, design, interaction, component, and test-strategy files from `Spegeli/vivicast-docs`

Do not use these old app-repo paths as active planning sources:

```text
docs/PLAN.md
docs/roadmap.md
docs/phase-XX-*.md
external-docs/
```

If those paths still exist locally, treat them as legacy/pre-final remnants. They must not override the final docs repository.

## Development Quick Start

Detailed Windows/Android setup notes are available at:

```text
docs/setup/windows-android-setup.md
```

Use that file only for local environment, Android SDK, Android Studio, emulator, or device setup. It is not a product, architecture, design, or implementation source of truth.

Check the local environment:

```powershell
.\scripts\check-environment.ps1
```

Configure Android SDK environment variables if needed:

```powershell
.\scripts\configure-android-env.ps1
```

Open Android Studio:

```powershell
.\scripts\open-android-studio.ps1
```

Start the Android TV emulator:

```powershell
.\scripts\start-tv-emulator.ps1
```

Build all debug modules:

```powershell
.\gradlew.bat assembleDebug
```

## Current App State

The existing app repository should be treated as a partially implemented foundation, not as a finished product and not as a source of product truth.

Likely reusable foundation:

- Android TV Leanback launcher entry
- package/application ID `com.vivicast.tv`
- multi-module Gradle structure
- Kotlin and Jetpack Compose for TV foundation
- reusable `:core:designsystem` components and tokens
- partial Room/DataStore/security/cache/parser/worker foundations
- demo UI for Live-TV, Filme, Serien, Suche, Einstellungen, and Player Overlay

Known re-alignment areas:

- root app instructions had older planning assumptions and have been replaced by `AGENTS.md`
- old app-local planning files are superseded by `Spegeli/vivicast-docs`
- `Home` must exist as a fixed main area and default start area
- demo-backed UI paths must be replaced by persisted/local app data according to PRD and ADRs
- Search, VOD, Player, Backup/Restore, PIN, diagnostics, and final Android-TV integration must follow the final docs repository

## App-Repo Working Plans

Codex should create app-specific technical plans under:

```text
codex/plans/
```

The main status anchor should be:

```text
codex/plans/APP-IMPLEMENTATION-PLAN.md
```

These plans may describe implementation details for this repository, but they must not override:

- PRD files
- ADRs
- design files
- interaction specs
- component specs
- test strategy
- `DOCS-GOVERNANCE.md`
- Codex working rules in `Spegeli/vivicast-docs`

## Repository Rules

- Do not modify `Spegeli/vivicast-docs` during app implementation unless the Owner explicitly requests a documentation change.
- Do not recreate or depend on `external-docs/` inside this repository.
- Do not commit provider credentials, tokens, private playlist URLs, private screenshots, or other sensitive data.
- Do not push, create remote branches, open pull requests, or publish commits without explicit Owner approval for that exact action.
