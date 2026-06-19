# ViviCast

ViviCast is an Android-first IPTV player project. The first MVP target is Android TV with D-pad-first navigation, local provider management, reusable EPG support, and a shared Media3 playback path for live TV, movies, and series.

## Source of Truth

- Project status, current phase, and next steps: [docs/PLAN.md](docs/PLAN.md)
- Technical structure and architecture decisions: [docs/architecture.md](docs/architecture.md)
- Android TV UX and layout direction: [docs/tv-design-direction.md](docs/tv-design-direction.md)
- Visual identity and asset direction: [docs/brand-direction.md](docs/brand-direction.md)
- Local Windows/Android setup and workflow: [docs/setup/windows-android-setup.md](docs/setup/windows-android-setup.md)

## Current Product Direction

- Android TV first
- No Home section
- Current primary navigation: `Search`, `Live TV`, `Movies`, `Series`, `Settings`
- Provider setup and management belongs in Settings
- Debug builds may still auto-import local demo content; production first-run onboarding is a later phase

## Development Quick Start

Environment check:

```powershell
.\scripts\check-environment.ps1
```

If Android SDK variables are missing:

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

Build and install the current TV debug APK:

```powershell
.\gradlew.bat :app-tv:installDebug
```

## Working Model

- Use Compose Preview first for visual iteration
- Use the Android TV emulator for focus, D-pad, Back, scrolling, playback, persistence, and integration checks
- Treat the current Search destination as shell scaffolding until the dedicated global-search phase is implemented
- Keep project status in `docs/PLAN.md`; do not treat this README as a progress log
