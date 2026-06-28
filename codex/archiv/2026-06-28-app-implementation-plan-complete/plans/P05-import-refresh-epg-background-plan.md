# Package 5 - Import/Refresh, EPG, and Background Jobs

## Status

- Package: Package 5 - Import/Refresh, EPG, and Background Jobs
- State: done
- Last completed step: XMLTV tolerance, EPG retention cleanup, and process-local duplicate refresh guards implemented.
- Last validated state: Package 5 validation passed: XMLTV unit tests, worker unit tests, EPG androidTest compile, app debug build, and diff check.
- Next concrete step: Start Package 6 - Settings, Provider Management, and Data Maintenance UI.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `AGENTS.md`
- `README.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/prd/PRD-v1/05-iptv-epg-favorites.md`
- `../vivicast-docs/prd/PRD-v1/07-background-jobs-performance.md`
- `../vivicast-docs/prd/PRD-v1/12-parser-source-contracts.md`
- `../vivicast-docs/architecture/decisions/ADR-002-epg-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-003-refresh-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-012-atomic-import-refresh.md`
- `../vivicast-docs/architecture/diagrams/03-import-refresh-flow.md`
- `../vivicast-docs/architecture/diagrams/04-epg-flow.md`
- `../vivicast-docs/prd/PRD-v1/13-test-strategy.md`

## Scope

- Reuse the existing WorkManager, refresh, XMLTV parser, and EPG repository foundation.
- Accept XMLTV programmes without `stop` by deriving the end from the next programme on the same XMLTV channel where possible.
- Accept XMLTV programmes without title by using a neutral local fallback title.
- Add EPG retention cleanup that deletes only EPG programmes outside the configured/default window.
- Add process-local guards so direct refresh execution cannot run productive refreshes in parallel for the same provider or EPG source.
- Add focused tests for those contract points.

## Non-Scope

- No complete EPG schema redesign.
- No full mockserver or benchmark suite.
- No UI changes for EPG retention settings.
- No backup/restore, PIN, Android TV Search, Watch Next, or player work.
- No docs-repo changes.

## Affected App Modules and Files

- `iptv/xmltv/src/main/java/com/vivicast/tv/iptv/xmltv/XmltvContracts.kt`
- `iptv/xmltv/src/test/java/com/vivicast/tv/iptv/xmltv/DefaultXmltvParserTest.kt`
- `data/epg/src/main/java/com/vivicast/tv/data/epg/EpgImportRepository.kt`
- `data/epg/src/main/java/com/vivicast/tv/data/epg/RoomEpgRepository.kt`
- `data/epg/src/androidTest/java/com/vivicast/tv/data/epg/RoomEpgRepositoryTest.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/dao/EpgDao.kt`
- `worker/src/main/java/com/vivicast/tv/worker/RefreshExecution.kt`
- `worker/src/test/java/com/vivicast/tv/worker/RefreshExecutionTest.kt`

## Technical Approach

- Keep parser tolerance in `DefaultXmltvParser`; keep persistence cleanup in `RoomEpgRepository`.
- Use the existing Room transaction boundary for XMLTV imports.
- Run retention cleanup as the cleanup step after successful EPG import.
- Use a tiny process-local refresh guard per refresher instance.

## Risks and Assumptions

- Current `epg_programs` still contains `providerId`, so EPG programmes are not yet fully source-only. This is a known larger re-alignment item for a later schema package.
- Process-local guards prevent duplicate productive refreshes inside the app process; WorkManager unique work names still provide the main scheduling dedupe.
- Retention defaults use PRD values: 1 day past and 7 days future until UI wiring is added.

## Validation Plan

- `.\gradlew.bat :iptv:xmltv:testDebugUnitTest`
- `.\gradlew.bat :worker:testDebugUnitTest`
- `.\gradlew.bat :data:epg:compileDebugAndroidTestKotlin`
- `.\gradlew.bat :app:assembleDebug`
- `git diff --check`

## Progress

- Completed: Package 5 source and app-code inspection.
- Completed: XMLTV parser now accepts missing titles with `Ohne Titel`, derives missing `stop` from the next programme on the same XMLTV channel, and skips programmes where no non-invented end time can be persisted.
- Completed: EPG refresh now fails before import when the parsed XMLTV document has no importable programmes.
- Completed: EPG refresh runs retention cleanup after successful imports with PRD defaults of 1 day past and 7 days future.
- Completed: EPG retention cleanup deletes only `epg_programs` outside the retention window and keeps EPG sources, provider links, and mappings.
- Completed: Process-local refresh guards prevent parallel productive refresh execution for the same provider or EPG source inside one app process.
- Completed: Focused parser, worker, and Room compile tests added or updated.
- Open: Full source-only EPG programme storage remains a documented later schema re-alignment item because the current compatibility schema still stores `providerId` on `epg_programs`.

## Validation Log

- Passed: `.\gradlew.bat :iptv:xmltv:testDebugUnitTest`
- Passed: `.\gradlew.bat :worker:testDebugUnitTest`
- Passed: `.\gradlew.bat :data:epg:compileDebugAndroidTestKotlin`
- Passed: `.\gradlew.bat :app:assembleDebug`
- Passed: `git diff --check` with only LF/CRLF warnings.
