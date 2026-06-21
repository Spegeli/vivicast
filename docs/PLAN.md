# ViviCast Plan

## Current Direction

ViviCast is being restarted from a clean Android project baseline.

The previous app implementation, architecture, roadmap, and UI concept are no longer active project direction. New product concept, information architecture, UI direction, and technical structure will be provided before new app development begins.

## Current Phase

Phase 0 - Repository Reset

## Current Status

Done:

- Removed the old active Android app modules from the Gradle project.
- Removed old UI concept, design, architecture, refactoring, screenshot, debug, and generated build artifacts from the active project tree.
- Archived the old app/code modules locally under `archive/pre-restart-2026-06-21/` for temporary reference only.
- Reduced active documentation to this plan, the README, setup notes, scripts, and repository instructions.

Missing:

- Define the new ViviCast product concept.
- Decide the new module structure.
- Scaffold the new Android TV application baseline.
- Add the first clean build checkpoint for the new baseline.

## Preserved Project Infrastructure

- `.agents/`
- `AGENTS.md`
- `docs/PLAN.md`
- `docs/setup/windows-android-setup.md`
- `scripts/`
- Gradle wrapper and version catalog
- Root Gradle settings/build files
- Local Android setup files

## Working Rules

- Treat this plan as the only active project memory.
- Do not use archived app code, removed UI concepts, or old roadmap assumptions as product direction.
- Use old archived code only when the user explicitly asks for implementation reference.
- Android TV remains the first likely development target unless the next concept says otherwise.
- Start emulator testing through `scripts\start-tv-emulator.ps1`.
- Do not install APKs on the physical Android TV unless explicitly requested.

## Next Steps

1. Capture the new product concept and MVP scope.
2. Choose the initial Android module structure.
3. Scaffold a minimal Android TV app.
4. Establish the first compile/install validation loop.

## Last Updated

2026-06-21
