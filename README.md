# ViviCast

ViviCast is an Android TV IPTV client for package `com.vivicast.tv`.

Status: The accepted Android TV foundation and Phase 2/2C local UI demo exist. The app has a TV-oriented Compose UI, reusable design-system components, and local demo catalog data. Real provider integrations, parser implementations, persistent PRD data flows, and Media3 playback are not complete yet.

## Source of Truth

- Short startup pointer: [docs/PLAN.md](docs/PLAN.md)
- Long-form implementation roadmap: [docs/roadmap.md](docs/roadmap.md)
- Phase details: `docs/phase-XX-*.md`
- Local Windows/Android setup: [docs/setup/windows-android-setup.md](docs/setup/windows-android-setup.md)

The local read-only documentation clone lives in `external-docs/` and is ignored by Git.

Binding documentation sources:

- `external-docs/prd/PRD-v1/`
- `external-docs/architecture/decisions/`
- `external-docs/architecture/diagrams/`
- `external-docs/design/`

Ignore `external-docs/codex/` and every link pointing to `codex/`.

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

Active Gradle modules are defined in `settings.gradle.kts` and follow the PRD v1 multi-module Android TV structure.

Implemented or partially implemented:

- Android TV Leanback entry point and package/application ID `com.vivicast.tv`
- Compose for TV screens for Live-TV, Movies, Series, Search, Settings, and Player Overlay
- Reusable `:core:designsystem` TV components and tokens
- Local demo catalog data and bundled demo assets in `:data:media`
- Emulator-oriented scripts for environment checks, Android Studio, and Android TV startup

Still stubbed or missing:

- Dependency injection wiring
- Room entities, DAOs, migrations, and real data repositories
- DataStore implementation
- Android Keystore-backed secure value storage
- M3U, Xtream, and XMLTV implementations
- WorkManager refresh jobs
- Real Media3 playback and timeshift
