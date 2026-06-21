# ViviCast Plan

## Current Direction

ViviCast is now building the Phase 2 Android TV UI demo on top of the accepted Phase 1 foundation from `Spegeli/vivicast-docs`.

## Current Phase

Phase 2 - UI demo with local demo data

## Current Status

Done:

- Phase 1 foundation accepted and pushed to `Spegeli/vivicast`.
- Added local, reproducible Phase 2 demo catalog data in `:data:media`.
- Added Android TV demo screens for Live-TV, Movies, Series, Search, Settings, and Player Overlay.
- Added D-pad-oriented focus behavior, top-level screen switching, adaptive Live-TV columns, VOD cards, search rows, settings master-detail, and player timeline controls.
- Kept provider data, parsers, real stream URLs, Media3 playback, network demo content, and DB imports out of Phase 2.
- Verified latest Phase 2 build with `.\gradlew.bat assembleDebug`.
- Verified APK install, launch, D-pad navigation, Live-TV sender mode, fullscreen player overlay, timeline OK/seek handling, back/restore overlay behavior, and no crash markers on `ViviCast_AndroidTV_API36`.

Missing:

- Phase 2 review/acceptance by the user.

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

1. User review of the local Phase 2 UI demo.
2. Wait for explicit approval before committing or pushing Phase 2 changes.

## Last Updated

2026-06-21
