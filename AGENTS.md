# ViviCast Project Instructions

## Startup Routine

At the start of every new session in this repository:

1. Read `docs/PLAN.md` first.
2. Use `docs/PLAN.md` as the source of truth for the current phase, active task, and next steps.
3. Read `docs/setup/windows-android-setup.md` only when local environment, Android SDK, emulator, or device setup is relevant.

## Plan Maintenance

- Keep `docs/PLAN.md` up to date whenever meaningful progress is made.
- Update it after completed milestones, direction changes, or when the active task changes.
- Do not leave the plan stale after code changes that affect the current phase or next steps.
- Keep status entries concise and practical.
- Do not create competing planning files unless explicitly requested.

## Documentation Rules

- Treat `docs/PLAN.md` as the living project memory.
- Keep markdown files lean.
- Prefer updating existing docs over scattering progress across new markdown files.
- Remove stale or duplicated markdown content when it no longer helps current development or project understanding.
- Do not reintroduce old UI concept, architecture, roadmap, or branding assumptions unless the user explicitly asks for them.

## Product Context

- ViviCast is restarting from zero.
- The previous app implementation, UI concept, architecture, and roadmap are no longer active direction.
- New concept, structure, and product rules will be provided before new app development begins.
- Android TV remains the first likely development target unless the new concept changes that.

## Android TV Emulator Rule

- Always start the Android TV emulator through `scripts\start-tv-emulator.ps1`.
- The correct default AVD is `ViviCast_AndroidTV_API36`.
- Do not use `ViviCast_TV_1080p_API36` for normal ViviCast development unless the user explicitly asks for it.
- `ViviCast_TV_1080p_API36` is the Google TV/login setup AVD and can block testing with first-run setup requirements.

## Android Development Workflow

- Before Android implementation, migration, testing, profiling, build-tooling, Compose, navigation, or optimization work, check whether an installed Android skill matches the task.
- When a skill matches, read its complete `SKILL.md` before changing code and follow its workflow together with these project instructions.
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
- Local commits are allowed only when they help preserve an approved work state; pushing those commits still requires separate explicit approval.

## Security

- Never paste provider credentials, tokens, or private playlist data into documentation.
- If sensitive values appear in screenshots or chat, avoid repeating them in files.
