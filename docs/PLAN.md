# ViviCast Plan

## Current Direction

ViviCast is restarting as a clean Android TV app using the Phase 1 foundation from `Spegeli/vivicast-docs`.

## Current Phase

Phase 1 - Foundation

## Current Status

Done:

- Scaffolded the Android TV multi-module foundation for package `com.vivicast.tv`.
- Added `:app`, core, data, domain, feature, IPTV, and worker modules from the docs bootstrap prompt.
- Added a minimal Android TV manifest and Compose-based Phase 1 placeholder shell.
- Added design system skeleton primitives and placeholder screen component.
- Added foundation contracts for Room, DataStore, network, security, player, worker, IPTV, and repositories without real provider, parser, or playback logic.
- Verified all modules with `.\gradlew.bat assembleDebug`.
- Verified emulator launch, APK install, Leanback launcher resolution, basic D-pad focus, and placeholder navigation on `ViviCast_AndroidTV_API36`.

Missing:

- Phase 2 UI demo implementation with local demo data after explicit start.

## Active Module Structure

- `:app`
- `:core:common`
- `:core:designsystem`
- `:core:database`
- `:core:datastore`
- `:core:network`
- `:core:player`
- `:core:security`
- `:data:provider`
- `:data:epg`
- `:data:media`
- `:data:favorites`
- `:data:playback`
- `:domain`
- `:feature:live-tv`
- `:feature:movies`
- `:feature:series`
- `:feature:search`
- `:feature:settings`
- `:feature:player`
- `:iptv:m3u`
- `:iptv:xtream`
- `:iptv:xmltv`
- `:worker`

## Working Rules

- Treat this plan as the local active project memory.
- Treat `Spegeli/vivicast-docs` as read-only unless the user explicitly says otherwise.
- Do not use archived app code, removed UI concepts, or old roadmap assumptions as product direction.
- Use old archived code only when the user explicitly asks for implementation reference.
- Android TV is the active first target.
- Start emulator testing through `scripts\start-tv-emulator.ps1`.
- Do not install APKs on the physical Android TV unless explicitly requested.

## Next Steps

1. Start Phase 2 only after explicit instruction using the docs Phase 2 files.
2. Add local demo data only in Phase 2.

## Last Updated

2026-06-21
