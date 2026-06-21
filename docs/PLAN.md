# ViviCast Plan

## Current Direction

Vivicast is now aligned to the PRD v1 and ADR roadmap from the read-only docs repository.

Use `docs/roadmap.md` as the detailed implementation roadmap. Use the active `docs/phase-XX-*.md` file for long sessions.

## Current Phase

Phase 04 - Ingest, Refresh, and EPG

## Current Status

Done:

- Phase 1 Android TV foundation exists.
- Phase 2/2C demo UI and reusable TV design-system components exist.
- `Spegeli/vivicast-docs` is cloned locally into `external-docs/`.
- `external-docs/` is ignored by Git and must remain read-only context.
- New PRD/ADR-based roadmap and phase files were created under `docs/`.
- Phase 02 data model and persistence foundation is implemented:
  - Room PRD entities, DAOs, indices, schema export, and database factory
  - typed DataStore preferences implementation
  - domain models and repository contracts
  - manual app container persistence wiring
  - local ID and secret-storage ADR
  - schema/security unit tests
- Phase 03 provider security and configuration is implemented:
  - Android Keystore-backed secure value storage
  - Room-backed provider repository with credentials stored outside Room
  - local Settings provider create/edit/disable/delete flow for M3U and Xtream configuration
  - provider lifecycle and deletion side-effect instrumentation tests
  - Android TV emulator smoke test for D-Pad Settings/provider flow

Still important:

- Existing UI uses demo data.
- Parser implementations, provider clients, WorkManager refresh jobs, real EPG import, and Media3 playback are not complete yet.
- The complete `external-docs/codex/` folder and all links to it are ignored as binding sources.

## Working Rules

- Treat `docs/roadmap.md` as the long-form roadmap.
- Treat this file as the short startup pointer.
- Work phase by phase.
- Operate autonomously through the roadmap: after finishing a task, validate it, document the result, and continue with the next task unless blocked by missing credentials, OS permissions, external account/login steps, or explicit user direction.
- Validate implementation work before moving on. Use builds, tests, emulator smoke tests, and visual screenshot checks when UI or focus behavior changes.
- Local commits are allowed when they preserve a validated work state. Pushes still require explicit user approval.
- The implementation may choose the DI approach. Prefer the simplest approach that fits the codebase; record an ADR if introducing a DI framework or changing the app-wide DI strategy.
- Keep the active phase docs current when meaningful progress is made.
- Record architecture decisions or deviations as ADRs under `docs/decisions/`.
- Treat `external-docs/` as read-only and never commit it.
- Start Android TV emulator testing through `scripts\start-tv-emulator.ps1`.
- Do not install APKs on the physical Android TV unless explicitly requested.
- `AGENTS.md` may be updated directly when it is genuinely useful for reliable long-running work. Keep edits minimal and additive; do not rewrite or remove major guidance without explicit user approval.

## Public Test Sources

These public URLs may be used later for real M3U/XMLTV tests:

- M3U: `https://raw.githubusercontent.com/josxha/german-tv-m3u/main/german-tv.m3u`
- EPG XMLTV: `https://iptv-epg.org/files/epg-de.xml`

No Xtream Codes test credentials are available yet. Ask the user only when Xtream-specific real integration testing becomes necessary.

## Next Steps

1. Start Phase 04: Ingest, Refresh, and EPG.
2. Keep Phase 03 provider security boundaries intact: no plaintext credentials in Room, logs, screenshots, docs, or demo data.

## Last Updated

2026-06-21
