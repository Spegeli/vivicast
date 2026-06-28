# Package 1 - App Skeleton and Technical Foundation

## Status

- State: done
- Completed: Existing Android TV skeleton checked against final docs, build/install/start validated on Android TV emulator.
- Last validated state: `.\gradlew.bat :app:assembleDebug` passed; `.\gradlew.bat :app:installDebug` passed on `emulator-5554`; `adb shell am start -n com.vivicast.tv/.MainActivity` started the app; UI tree showed `Home` as focused start area; crash buffer was empty.
- Follow-up: Continue with Package 2 plan for design system and navigation shell.

## Docs Sources Read

- `AGENTS.md`
- `README.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/prd/PRD-v1/01-product-overview.md`
- `../vivicast-docs/prd/PRD-v1/08-android-tv-security.md`
- `../vivicast-docs/prd/PRD-v1/09-implementation-and-dod.md`
- `../vivicast-docs/architecture/decisions/ADR-001-provider-isolation.md`
- `../vivicast-docs/architecture/decisions/ADR-002-epg-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-003-refresh-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-004-backup-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-005-local-search.md`
- `../vivicast-docs/architecture/decisions/ADR-006-timeshift-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-007-trailer-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-008-android-tv-integration.md`
- `../vivicast-docs/architecture/decisions/ADR-009-provider-deletion-and-favorites.md`
- `../vivicast-docs/architecture/decisions/ADR-010-stable-identities-and-restore-keys.md`
- `../vivicast-docs/architecture/decisions/ADR-011-parser-source-contracts.md`
- `../vivicast-docs/architecture/decisions/ADR-012-atomic-import-refresh.md`
- `../vivicast-docs/architecture/decisions/ADR-013-player-playback-progress.md`
- `../vivicast-docs/architecture/decisions/ADR-014-security-data-network-backup.md`
- `../vivicast-docs/codex/coding-rules.md`

## Affected Masterplan Package

Package 1: App Skeleton and Technical Foundation.

## Concrete Implementation Scope

- Check Android/Kotlin/Compose-for-TV baseline.
- Check package/application ID `com.vivicast.tv`.
- Check minimal app startup through `MainActivity` and `VivicastApplication`.
- Check dependency/build foundation and module layout.
- Check basic module structure for UI, domain, data, player, settings, parsers, workers, and tests.
- Validate app build, install, and launch on Android TV emulator.

## Non-Scope

- No feature-complete screen implementation.
- No Package 2 navigation/focus QA beyond startup visibility.
- No Package 3 persistence/security migration.
- No CI creation; no workflow exists yet and Package 1 only says first CI/test foundation if appropriate.

## Affected App Modules and Files

- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/vivicast/tv/MainActivity.kt`
- `app/src/main/java/com/vivicast/tv/VivicastApplication.kt`
- `app/src/main/java/com/vivicast/tv/di/AppContainer.kt`
- `scripts/start-tv-emulator.ps1`

## Technical Approach

Reuse the existing skeleton. The repository already has the Android TV app module, Leanback launcher manifest, Compose setup, root DI container, Room/DataStore/security/player/parser/worker modules, and a debug build that installs and starts on the TV emulator. No code change was required for Package 1.

## Risks and Assumptions

- Package 1 does not make feature modules final. Demo fallbacks and pre-final data/security contracts remain tracked for later packages.
- No GitHub Actions workflow exists. This is not blocking Package 1 because local build/install/start validation is present and CI can be added when release workflow is defined.
- The app starts and shows `Home`; deeper D-Pad/back/focus behavior belongs to Package 2 and later feature packages.

## Relevant Tests and Checks

- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: `.\gradlew.bat :app:installDebug`
- Passed: `adb -s emulator-5554 shell am start -n com.vivicast.tv/.MainActivity`
- Passed: `adb -s emulator-5554 shell pidof -s com.vivicast.tv` returned a process ID.
- Passed: `adb -s emulator-5554 exec-out uiautomator dump /dev/tty` showed `Home` and top navigation in package `com.vivicast.tv`.
- Passed: `adb -s emulator-5554 logcat -b crash -d` returned no crash entries.

## Open Owner Questions

None.

