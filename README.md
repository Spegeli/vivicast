# ViviCast

ViviCast is a clean Android TV app foundation for package `com.vivicast.tv`.

Status: Phase 1 foundation scaffolded. The app currently contains only a minimal Android TV shell and placeholders. There are no demo data, provider integrations, parsers, or real player features.

## Source of Truth

- Current plan and next steps: [docs/PLAN.md](docs/PLAN.md)
- Local Windows/Android setup: [docs/setup/windows-android-setup.md](docs/setup/windows-android-setup.md)

## Development Quick Start

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

Start the default Android TV emulator:

```powershell
.\scripts\start-tv-emulator.ps1
```

Build all debug modules:

```powershell
.\gradlew.bat assembleDebug
```

## Current State

Active Gradle modules are defined in `settings.gradle.kts` and follow the Phase 1 bootstrap structure from the documentation repository. Build, install, launch, and basic D-pad focus have been validated on the Android TV emulator.

Phase 2 must only start after explicit approval.
