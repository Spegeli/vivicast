# ViviCast Project Instructions

## Startup Routine

At the start of every new session in this repository:

1. Read `docs/PLAN.md` first.
2. Use `docs/PLAN.md` as the short startup pointer for the current phase, active task, and next steps.
3. Read `docs/roadmap.md` for the long-form phase overview.
4. Read the active `docs/phase-XX-*.md` file before implementation work.
5. Read `docs/setup/windows-android-setup.md` only when local environment, Android SDK, emulator, or device setup is relevant.

## Plan Maintenance

- Keep `docs/PLAN.md` up to date whenever meaningful progress is made.
- Update it after completed milestones, direction changes, or when the active task changes.
- Do not leave the plan stale after code changes that affect the current phase or next steps.
- Keep status entries concise and practical.
- Do not create competing planning files unless explicitly requested.

## Documentation Rules

- Treat `docs/PLAN.md` as the living project memory.
- Treat `docs/roadmap.md` as the long-form implementation roadmap.
- Treat the active `docs/phase-XX-*.md` file as the detailed task source for long sessions.
- `AGENTS.md` may be updated directly when it is genuinely useful for reliable long-running work. Keep edits minimal and additive; do not rewrite or remove major guidance without explicit user approval.
- Keep markdown files lean.
- Prefer updating existing docs over scattering progress across new markdown files.
- Remove stale or duplicated markdown content when it no longer helps current development or project understanding.
- Do not reintroduce old UI concept, architecture, roadmap, or branding assumptions unless the user explicitly asks for them.

## Product Context

- ViviCast is an Android TV IPTV client, not a server backend.
- The previous app implementation, UI concept, architecture, and roadmap are no longer active direction.
- Current app direction follows PRD v1 and ADR-001 through ADR-009 from the read-only documentation repository.
- Current app state includes the accepted Phase 1 foundation and the Phase 2/2C local UI demo/design-system work.
- Android TV is the active first development target.

## External Documentation

- `external-docs/` is a local clone of `Spegeli/vivicast-docs` and must be treated as read-only.
- Never edit, stage, or commit files under `external-docs/`.
- Binding sources are only:
  - `external-docs/prd/PRD-v1/`
  - `external-docs/architecture/decisions/`
  - `external-docs/architecture/diagrams/`
  - `external-docs/design/`
- Ignore the complete `external-docs/codex/` folder.
- Ignore every reference or link to `codex/`, even if another documentation file mentions it.

## Autonomous Execution

- Work through `docs/roadmap.md` phase by phase without waiting for repeated user prompts.
- After completing a task, validate it, document the result, and continue with the next task.
- Stop only for real blockers such as missing credentials, OS permissions, external account/login steps, or explicit user direction.
- Validate implementation work before moving on. Use builds, tests, Android TV emulator smoke tests, and screenshots when UI, focus, or visual behavior changes.

## Implementation Decisions

- Codex may choose the DI approach. Prefer the simplest approach that fits the codebase.
- Record an ADR under `docs/decisions/` before introducing a DI framework or changing the app-wide DI strategy.
- Public M3U/XMLTV test URLs may be used for later real ingest checks:
  - M3U: `https://raw.githubusercontent.com/josxha/german-tv-m3u/main/german-tv.m3u`
  - EPG XMLTV: `https://iptv-epg.org/files/epg-de.xml`
- No Xtream Codes test credentials are currently available. Ask only when Xtream-specific real integration testing becomes necessary.

## Android TV Emulator Rule

- Always start the Android TV emulator through `scripts\start-tv-emulator.ps1`.
- The correct default AVD is `ViviCast_AndroidTV_API36`.
- Do not use `ViviCast_TV_1080p_API36` for normal ViviCast development unless the user explicitly asks for it.
- `ViviCast_TV_1080p_API36` is the Google TV/login setup AVD and can block testing with first-run setup requirements.

## Android Development Workflow

- Before Android implementation, migration, testing, profiling, build-tooling, Compose, navigation, or optimization work, check whether an installed Android skill matches the task.
- When a skill matches, read its complete `SKILL.md` before changing code and follow its workflow together with these project instructions.
- Before UI/frontend work, especially Settings changes, check the relevant files under `external-docs/design/wireframes/` and the layout/navigation guidance in `external-docs/prd/PRD-v1/`.
- Keep UI structure, focus paths, D-Pad navigation, Back behavior, and states aligned with those wireframes and PRD notes unless the active phase explicitly documents a narrower placeholder.
- Use Android Studio Compose Preview for visual iteration when Compose UI exists.
- Use the Android TV emulator for D-pad focus, key routing, Back behavior, dialogs, navigation, playback, persistence, database, and integration checks.
- Run compile checkpoints after structural or behavior changes.
- Codex may operate Android Studio and the emulator directly. User interaction should only be requested for unavoidable OS permission, license, login, or other blocking dialogs.

## Physical Android TV

- The physical Android TV is the main real-device test target when explicitly requested.
- Its stable ADB address is `192.168.178.40:5555`.
- Device model: `Xiaomi Mi Smart TV 4S` (`MiTV-MSSp3`).
- OS version: Android 9.
- Do not install APKs on the physical Android TV unless the user explicitly asks for it.
- Use the Android TV emulator as the normal Codex test environment.

## Git and GitHub

- Never push to GitHub without the user's explicit permission for that specific push.
- Never create remote repositories, publish branches, open pull requests, or upload commits unless the user has clearly approved that exact GitHub action.
- Local commits are allowed when they preserve a validated work state; pushing those commits still requires separate explicit approval.

## Security

- Never paste provider credentials, tokens, or private playlist data into documentation.
- If sensitive values appear in screenshots or chat, avoid repeating them in files.
