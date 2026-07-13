# Vivicast

Android TV IPTV client — package `com.vivicast.tv`.

## Repository Layout

```
claude-code\
  vivicast\        ← app code (this repo)
  vivicast-docs\   ← reference docs (sibling, read-only during implementation)
```

Reference docs at `../vivicast-docs`. See `CLAUDE.md` for full source priority and project instructions.

## Development Quick Start

Detailed Windows/Android setup: `docs/setup/windows-android-setup.md`

```powershell
# Check environment
.\scripts\check-environment.ps1

# Configure Android SDK env vars (if needed)
.\scripts\configure-android-env.ps1

# Open Android Studio
.\scripts\open-android-studio.ps1

# Start Android TV emulator (-Api 36 default / Android 16; -Api 28 = Android 9 floor)
.\scripts\start-tv-emulator.ps1 -Api 28

# Build / test / architecture gates
.\gradlew.bat detekt
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

## App Foundation

Reusable:
- Android TV Leanback launcher entry
- package/application ID `com.vivicast.tv`
- multi-module Gradle structure
- Kotlin + Jetpack Compose for TV
- ViewModel + immutable UiState per main feature, with lifecycle-aware UI state collection
- split `:core:designsystem` (grouped `Vivicast*.kt` components and tokens)
- Playback orchestration in `:data:playback` (`PlaybackRequestFactory` / `PlaybackProgressRecorder`)
- Provider connection testing in `:data:provider` (`TestProviderConnectionUseCase`)
- detekt size/complexity gate
- Room/DataStore/security/cache/parser/worker foundations

## Architecture Status

- P0–P3 architecture remediation is **complete**.
- Active architecture references:
  - `docs/ARCHITECTURE-REMEDIATION-COMPLETION-REPORT.md`
  - `docs/SETTINGS-APP-HOISTED-DECISIONS.md`
  - `docs/DETEKT-GATE.md`
- For detailed mandatory rules, see `CLAUDE.md`. Older remediation plans are archived under
  `docs/archive/` (historical context only).

## Repository Rules

- For structural changes, keep `detekt`, `assembleDebug`, and `test` green.
- Follow the mandatory architecture rules in `CLAUDE.md`.
- Do not modify `../vivicast-docs` during app implementation unless explicitly requested.
- Do not commit provider credentials, tokens, playlist URLs, or private screenshots.
- Do not push, create remote branches, or open PRs without explicit approval for that exact action.
