# Package 4 - Provider, Parser, and Import Foundation

## Status

- Package: Package 4 - Provider, Parser, and Import Foundation
- State: done
- Last completed step: Provider status, duplicate save behavior, M3U stable keys, partial-error refresh status, and focused tests implemented.
- Last validated state: Package 4 validation passed; `git diff --check` emitted LF/CRLF warnings only.
- Next concrete step: Start Package 5 technical plan for import/refresh, EPG, and background-job hardening.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `AGENTS.md`
- `README.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/prd/PRD-v1/05-iptv-epg-favorites.md`
- `../vivicast-docs/prd/PRD-v1/07-background-jobs-performance.md`
- `../vivicast-docs/prd/PRD-v1/12-parser-source-contracts.md`
- `../vivicast-docs/architecture/decisions/ADR-011-parser-source-contracts.md`
- `../vivicast-docs/architecture/decisions/ADR-012-atomic-import-refresh.md`
- `../vivicast-docs/prd/PRD-v1/13-test-strategy.md`

## Scope

- Keep existing M3U, Xtream, provider, import, and refresh foundations.
- Add missing final provider statuses for partial errors and missing credentials.
- Prevent duplicate provider names from being saved.
- Align M3U parser identity derivation with the final source contract.
- Mark successful imports with skipped entries as partial-error provider status.
- Add focused parser/provider/refresh tests for the corrected behavior.

## Non-Scope

- No settings UI redesign or final provider add wizard.
- No XMLTV implementation changes.
- No full mockserver suite or benchmark suite.
- No Package 5 background-job hardening beyond status propagation needed by Package 4.
- No docs-repo changes.

## Affected App Modules and Files

- `domain/src/main/java/com/vivicast/tv/domain/model/VivicastModels.kt`
- `data/provider/src/main/java/com/vivicast/tv/data/provider/RoomProviderRepository.kt`
- `data/provider/src/androidTest/java/com/vivicast/tv/data/provider/RoomProviderRepositoryTest.kt`
- `iptv/m3u/src/main/java/com/vivicast/tv/iptv/m3u/M3uContracts.kt`
- `iptv/m3u/src/test/java/com/vivicast/tv/iptv/m3u/DefaultM3uParserTest.kt`
- `worker/src/main/java/com/vivicast/tv/worker/RefreshExecution.kt`
- `worker/src/test/java/com/vivicast/tv/worker/RefreshExecutionTest.kt`
- Provider status labels in affected feature modules, if compile requires exhaustive handling.

## Technical Approach

- Treat parser output as in-memory staging until the Room import transaction commits.
- Keep the existing import repositories and strengthen the result/status contract.
- Use `ProviderStatus.ActiveWithPartialErrors` for successful imports with skipped entries.
- Use `ProviderStatus.CredentialsRequired` when provider credentials are missing.
- Use media-type-prefixed M3U stable IDs and hash only the secret-bearing stream identity fallback.
- Block duplicate provider names before secret writes and Room writes.

## Risks and Assumptions

- Full connection-test UI behavior belongs to Package 6; Package 4 keeps the data/parser foundation aligned.
- Existing Xtream parser returns typed lists without diagnostics; deeper golden-test diagnostics can be expanded in Package 14.
- M3U uses channel media type for v1 because existing M3U import currently maps playlist items into the Live-TV catalog.
- Full connection-test UI and save/import orchestration remains Package 6 scope; this package keeps the data-layer guard against duplicate provider names and the refresh/import status foundation aligned.

## Validation Plan

- `.\gradlew.bat :iptv:m3u:testDebugUnitTest`
- `.\gradlew.bat :worker:testDebugUnitTest`
- `.\gradlew.bat :data:provider:compileDebugAndroidTestKotlin`
- `.\gradlew.bat :feature:settings:compileDebugKotlin :feature:live-tv:compileDebugKotlin :data:playback:testDebugUnitTest`
- `.\gradlew.bat :app:assembleDebug`

## Progress

- Completed: Package 4 source and app-code inspection.
- Completed: Added final provider statuses `ActiveWithPartialErrors` and `CredentialsRequired`.
- Completed: Duplicate provider names are rejected before credential writes and Room writes.
- Completed: M3U parser now counts orphan stream lines, accepts BOM, and derives media-type-prefixed stable IDs without cleartext secret URL parts.
- Completed: Playlist refresh marks successful M3U imports with skipped entries as `ActiveWithPartialErrors`; missing credentials become `CredentialsRequired`; empty M3U playlists fail before import.
- Completed: Provider status labels and playback blocking rules updated for the new statuses.
- Completed: Focused M3U, refresh, and provider duplicate tests added or compiled.
- Open: None for Package 4.

## Validation Results

- Passed: `.\gradlew.bat :iptv:m3u:testDebugUnitTest`
- Passed: `.\gradlew.bat :worker:testDebugUnitTest`
- Passed: `.\gradlew.bat :data:provider:compileDebugAndroidTestKotlin`
- Passed: `.\gradlew.bat :feature:settings:compileDebugKotlin :feature:live-tv:compileDebugKotlin :data:playback:testDebugUnitTest`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: `git diff --check` with LF/CRLF warnings only.
