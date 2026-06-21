# ViviCast

ViviCast is restarting from a clean Android project baseline.

The previous application code has been removed from the active Gradle project. A local, ignored reference copy of the old app modules is stored under `archive/pre-restart-2026-06-21/`.

Status: clean restart after concept phase.

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

## Current State

There are currently no active app modules. The next implementation step is to define the new ViviCast concept and scaffold the first Android TV module from scratch.
