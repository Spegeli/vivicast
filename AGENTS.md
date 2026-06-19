# ViviCast Project Instructions

## Startup Routine

At the start of every new session in this repository:

1. Read `docs/PLAN.md` first.
2. Use `docs/PLAN.md` as the source of truth for the current phase, active task, and next steps.
3. Read additional docs only when relevant:
   - `docs/architecture.md` for architecture or module decisions
   - `docs/tv-design-direction.md` for Android TV UX and layout work
   - `docs/brand-direction.md` for branding or visual identity work
   - `docs/setup/windows-android-setup.md` for local environment or device setup

## Plan Maintenance

- Keep `docs/PLAN.md` up to date whenever meaningful progress is made.
- Update it after completed milestones, direction changes, or when the active task changes.
- Do not leave the plan stale after code changes that affect the current phase or next steps.
- Keep status entries concise and practical.
- Prefer detailed `Done` / `Partial` / `Missing` tracking only for the active phase.
- When the active phase changes, convert the new active phase to the same `Done` / `Partial` / `Missing` structure.
- Keep earlier phases brief unless regressions or unfinished carry-over work need to stay visible.

## Documentation Rules

- Treat `docs/PLAN.md` as the living project memory.
- Do not create multiple competing planning files unless explicitly requested.
- Prefer updating the existing plan instead of scattering progress across new markdown files.
- Keep markdown files lean. Do not copy the same project facts, status notes, or structure descriptions into multiple docs without a strong reason.
- Give each core doc one primary ownership area:
  - `README.md`: quick project entry, key links, local quick start
  - `docs/PLAN.md`: current phase, status, next steps, roadmap memory
  - `docs/architecture.md`: modules, data flow, technical decisions
  - `docs/tv-design-direction.md`: TV UX, layout, focus, navigation direction
  - `docs/brand-direction.md`: visual identity and asset direction
  - `docs/setup/windows-android-setup.md`: local environment and workflow
- When updating docs, prefer linking to the source-of-truth file instead of duplicating the same detail.
- Remove stale or duplicated markdown content when it no longer helps current development or project understanding.

## Product Context

- ViviCast is Android-only.
- Android TV is the first MVP target.
- Shared Kotlin core logic is preferred, with device-specific UI layers.
- Provider setup and management belongs in Settings.
- The TV experience should prioritize D-pad usability, quick channel switching, and a dense but readable EPG flow.
- In Settings, every binary option should use a visible toggle control across all sections. Do not use plain action rows for on/off states.
- Across the TV UI, avoid unnecessary descriptive helper text. Prefer clear, high-signal titles and current values over explanatory paragraphs that consume space.

## Android TV Emulator Rule

- Always start the Android TV emulator through `scripts\start-tv-emulator.ps1`.
- The correct default AVD is `ViviCast_AndroidTV_API36`.
- Do not use `ViviCast_TV_1080p_API36` for normal ViviCast development unless the user explicitly asks for it.
- `ViviCast_TV_1080p_API36` is the Google TV/login setup AVD and can block testing with first-run setup requirements.

## Android UI Development Workflow

- Use a hybrid Android UI workflow instead of rebuilding and reinstalling after every visual edit.
- Build new or substantially revised Compose screens as small, state-driven composables that can render without an Activity, Room, Media3, network access, or an attached device.
- Add representative `@Preview` states for normal content, empty/loading/error states, long text, and dense TV data where relevant.
- Use Android Studio Compose Preview for layout, hierarchy, spacing, copy, and visual-state iteration.
- Use Interactive Preview or Live Edit when useful, but do not treat either as proof of TV remote behavior.
- Use the Android TV emulator for D-pad focus, key routing, Back behavior, dialogs, navigation, playback, persistence, database, and integration checks.
- Run compile checkpoints after structural or behavior changes, not after every small styling edit.
- Complete each active-phase UI block with one emulator validation pass after its Preview-driven visual pass.
- Codex may operate Android Studio and the emulator directly. User interaction should only be requested for unavoidable OS permission, license, login, or other blocking dialogs.

## Project-Local Android Skills

- Official Android skills from `android/skills` are installed only for this repository under `.agents/skills`.
- Before Android implementation, migration, testing, profiling, build-tooling, Compose, navigation, or optimization work, check whether an installed Android skill matches the task.
- When a skill matches, read its complete `SKILL.md` before changing code and follow its workflow together with these project instructions.
- Apply skills selectively by task. Do not force unrelated mobile, Wear OS, XR, camera, billing, identity, or Play integrations into the Android TV MVP.
- Treat Android's official repository and Android Developers documentation as the source of truth for skill behavior; DeepWiki is orientation material only.
- Keep project-local skill updates separate from product feature work. Updating an upstream skill must not overwrite local ViviCast code or documentation.

## Physical Android TV

- The physical Android TV is the main real-device test target.
- Its stable ADB address is `192.168.178.40:5555`.
- Device model: `Xiaomi Mi Smart TV 4S` (`MiTV-MSSp3`).
- OS version: Android 9.
- Do not install APKs on the physical Android TV unless the user explicitly asks for it.
- Use the Android TV emulator as the normal Codex test environment.
- Use the physical TV only for user-approved real remote/D-pad acceptance checks.

## Security

- Never paste provider credentials, tokens, or private playlist data into documentation.
- If sensitive values appear in screenshots or chat, avoid repeating them in files.
