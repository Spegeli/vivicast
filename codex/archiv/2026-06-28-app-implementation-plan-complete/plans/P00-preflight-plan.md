# Package 0 - Repository Preflight and Source Check

## Status

- State: done
- Completed: Repository preflight, source alignment check, first visible re-alignment, and validation.
- Last validated state: `.\gradlew.bat :app:compileDebugKotlin` passed; `.\gradlew.bat :feature:settings:compileDebugAndroidTestKotlin :feature:search:compileDebugAndroidTestKotlin` passed.
- Follow-up: Continue with Package 3 planning for stable identities, pending restore references, and protected secret storage.

## Docs Sources Read

- `../vivicast-docs/codex/README.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/README.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/codex/tv-compose-reference-guide.md`
- `../vivicast-docs/prd/PRD-v1/09-implementation-and-dod.md`
- `../vivicast-docs/prd/PRD-v1/13-test-strategy.md`
- `../vivicast-docs/prd/PRD-v1/04-search-settings-player-requirements.md`
- `../vivicast-docs/prd/PRD-v1/06-data-model.md`
- `../vivicast-docs/architecture/decisions/ADR-010-stable-identities-and-restore-keys.md`

## Affected Masterplan Package

Package 0 is complete. The first write step after preflight was the app-repo working plan plus small structural re-alignment so later implementation does not continue from known stale assumptions.

## Concrete Implementation Scope

- Create `codex/plans/APP-IMPLEMENTATION-PLAN.md`.
- Create this Package 0 technical plan.
- Mark the local 2026-06-21 ID/secret decision as superseded by final docs.
- Add `:feature:home` and wire `Home` as the default main route.
- Remove the most direct visible UI conflicts from preflight.

## Non-Scope

- Full data model replacement.
- Room migrations.
- Backup/restore implementation.
- Secret-store refactor.
- Full UI redesign.

## Affected App Modules and Files

- `settings.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/java/com/vivicast/tv/MainActivity.kt`
- `feature/home/`
- `feature/search/`
- `feature/settings/`
- `feature/player/`
- `feature/movies/`
- `feature/series/`
- `docs/decisions/2026-06-21-local-id-and-secret-storage.md`

## Technical Approach

Keep the existing repository. Change only the parts that are known to contradict final docs and can be corrected without schema migration. Defer data/security changes to the next planned package step, because those require coordinated Room migrations, repository contracts, parser output changes, and tests.

## Risks and Assumptions

- The current Room schema is still pre-final and must not be used as a backup/restore foundation.
- Demo route fallbacks still exist and must be removed before feature completion is claimed.
- Settings remains incomplete against final PRD until the full DataStore/settings package is implemented.

## Relevant Tests and Checks

- Passed: `.\gradlew.bat :app:compileDebugKotlin`
- Passed: `.\gradlew.bat :feature:settings:compileDebugAndroidTestKotlin :feature:search:compileDebugAndroidTestKotlin`
- Later Package 2/7 work must be checked on Android TV emulator for D-Pad focus and start navigation.

## Open Owner Questions

None.
