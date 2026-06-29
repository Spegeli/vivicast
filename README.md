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

# Start Android TV emulator
.\scripts\start-tv-emulator.ps1

# Build debug
.\gradlew.bat assembleDebug
```

## App Foundation

Reusable:
- Android TV Leanback launcher entry
- package/application ID `com.vivicast.tv`
- multi-module Gradle structure
- Kotlin + Jetpack Compose for TV
- `:core:designsystem` components and tokens
- Room/DataStore/security/cache/parser/worker foundations

## Repository Rules

- Do not modify `../vivicast-docs` during app implementation unless explicitly requested.
- Do not commit provider credentials, tokens, playlist URLs, or private screenshots.
- Do not push, create remote branches, or open PRs without explicit approval for that exact action.
