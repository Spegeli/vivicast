# Package 2 - Design System and Navigation Shell Plan

## Status

- Package: Package 2 - Design System and Navigation Shell
- State: done
- Last completed step: Shell Back behavior implemented and validated on Android TV emulator.
- Last validated state: `.\gradlew.bat :app:compileDebugKotlin`, `.\gradlew.bat :app:assembleDebug`, `.\gradlew.bat :app:installDebug`, Activity launch, D-Pad TopNav movement, content Back-to-TopNav, double-Back exit, empty crash log, and `git diff --check` passed.
- Next concrete step: Start Package 3 - Persistence, Data Model, and Security Foundation.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `../vivicast-docs/codex/README.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/design/interaction/nav.md`
- `../vivicast-docs/design/interaction/focus.md`
- `../vivicast-docs/design/design-system/README.md`
- `../vivicast-docs/design/design-system/03-components.md`
- `../vivicast-docs/design/design-system/04-focus-navigation.md`
- `../vivicast-docs/design/design-system/05-screen-patterns.md`
- `../vivicast-docs/design/mockups/high-fidelity/02-ui-direction-decisions.md`
- `../vivicast-docs/prd/PRD-v1/13-test-strategy.md`

## Affected Masterplan Package

- Package 2 - Design System and Navigation Shell

## Concrete Implementation Scope

- Keep the existing centralized `:core:designsystem` theme, tokens, reusable components, top navigation, loading/empty/error patterns, cards, settings rows, dialogs, and player primitives where compatible.
- Add the missing shell Back behavior:
  - Back from main-screen content moves focus to Top Navigation.
  - Back from Top Navigation requires a second Back confirmation to exit.
- Keep main navigation order: `Home | Live-TV | Filme | Serien | Suche | Einstellungen`.
- Keep Player as fullscreen context without visible Top Navigation.

## Non-Scope

- No visual redesign against rendered PNGs.
- No Package 3 persistence/security work.
- No Package 6 Settings implementation expansion.
- No Package 7 Home content implementation.
- No full per-screen focus restoration beyond the shell rule.

## Affected App Modules and Files

- `app/src/main/java/com/vivicast/tv/MainActivity.kt`
- `core/designsystem/src/main/java/com/vivicast/tv/core/designsystem/VivicastComponents.kt`
- `codex/plans/APP-IMPLEMENTATION-PLAN.md`
- `codex/plans/P02-design-navigation-shell-plan.md`

## Technical Approach

- Reuse the existing `VivicastTopNavigation` component.
- Add optional selected-item focus requester and item focus callback to the top navigation component.
- Add a shell-level `BackHandler` in `MainActivity` while the player is not visible.
- Use Android TV focus APIs to request focus on the selected top-nav item.
- Use a short second-Back window and Android toast text `Zum Beenden erneut zurück` for the exit confirmation.

## Risks and Assumptions

- Existing feature screens are still partially demo-backed and do not all expose final initial focus yet; this package handles only the global shell rule.
- The double-Back confirmation is implemented with a toast because docs require confirmation behavior but do not mandate a custom dialog or snackbar.
- Per-screen detailed focus paths remain package-specific work for Live-TV, VOD, Search, Settings, Player, and later polish packages.

## Relevant Tests and QA

- Compile app Kotlin after shell changes.
- Assemble debug APK.
- Install and launch on Android TV emulator.
- Check UI tree contains main navigation and starts on `Home`.
- Check D-Pad can move in Top Navigation.
- Check Back from Top Navigation requires a second Back to leave the app.

## Completion Notes

- Done.
- Existing centralized `:core:designsystem` theme, tokens, focus surfaces, cards, rows, dialogs, top navigation, and player primitives were kept.
- `VivicastTopNavigation` now supports an optional selected-item `FocusRequester` and item focus callback for shell-level Back behavior.
- `MainActivity` now handles Back outside the player according to final navigation docs:
  - Back from main-screen content requests focus on the selected Top Navigation item.
  - Back from Top Navigation requires a second Back within two seconds to exit.
- Validation:
  - Passed: `.\gradlew.bat :app:compileDebugKotlin`
  - Passed: `.\gradlew.bat :app:assembleDebug`
  - Passed: `.\gradlew.bat :app:installDebug`
  - Passed: `adb -s emulator-5554 shell am start -n com.vivicast.tv/.MainActivity`
  - Passed: UI tree showed `Home | Live-TV | Filme | Serien | Suche | Einstellungen` and Home focused on start.
  - Passed: D-Pad Right moved focus/active route from Home to Live-TV.
  - Passed: Back from Live-TV content focused Live-TV in Top Navigation.
  - Passed: double Back from Top Navigation left the app; launcher became resumed.
  - Passed: crash log buffer was empty.
  - Passed: `git diff --check` with only LF/CRLF warnings.
