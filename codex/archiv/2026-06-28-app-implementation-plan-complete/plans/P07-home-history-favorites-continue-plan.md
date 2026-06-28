# Package 7 - Home, History, Favorites, and Continue

## Status

- Package: Package 7 - Home, History, Favorites, and Continue
- State: done
- Last completed step: Live-TV playback now writes channel history used by the Home recent-channel row.
- Last validated state: Home/app/playback plus affected feature AndroidTest compiles passed; diff check reports only LF/CRLF warnings.
- Next concrete step: Open Package 8 - Live-TV Browser.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `AGENTS.md`
- `README.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/prd/PRD-v1/01-product-overview.md`
- `../vivicast-docs/prd/PRD-v1/05-iptv-epg-favorites.md`
- `../vivicast-docs/architecture/decisions/ADR-009-provider-deletion-and-favorites.md`
- `../vivicast-docs/architecture/decisions/ADR-013-player-playback-progress.md`
- `../vivicast-docs/design/screens/01-home.md`
- `../vivicast-docs/design/wireframes/00-home.md`

## Scope

- Make Home use real local data instead of placeholders.
- Show mixed movie/episode Continue in Home.
- Show recently watched Live-TV channels separately from Continue.
- Keep Home as the default start area.
- Preserve provider isolation for favorites, history, and playback progress.

## Non-Scope

- No Android TV Search indexing.
- No Watch Next publishing.
- No full Live-TV browser implementation.
- No visual polish pass beyond existing design-system components.
- No docs-repo changes.

## Affected App Modules and Files

- `codex/plans/APP-IMPLEMENTATION-PLAN.md`
- `codex/plans/P07-home-history-favorites-continue-plan.md`
- `feature/home/`
- `app/src/main/java/com/vivicast/tv/MainActivity.kt`
- `data/playback/`
- `data/favorites/`
- `data/media/`
- `feature/movies/`
- `feature/series/`
- `feature/live-tv/`

## Technical Approach

- Reuse `PlaybackRepository.observeContinueWatching` and `observeRecentChannels` before adding new data paths.
- Reuse existing route/player callbacks from `MainActivity`.
- Keep Home rows minimal: Hero, Fortsetzen, Zuletzt gesehene Live-TV-Sender, Empty State.
- Add repository helpers only where Home cannot resolve display data from existing APIs.

## Risks and Assumptions

- Current Home module may still be placeholder-only.
- Existing continue queries are provider-scoped; Home needs a cross-provider view or app-level aggregation.
- Series Continue may need a later refinement for "next episode after completed episode"; first slice should not overbuild that before reading current series/media APIs.

## Validation Plan

- `.\gradlew.bat :feature:home:compileDebugKotlin`
- `.\gradlew.bat :data:playback:compileDebugAndroidTestKotlin`
- `.\gradlew.bat :app:assembleDebug`
- `git diff --check`

## Progress

- Completed: Package 7 plan created from final docs.
- Completed: Current Home implementation and available repositories inspected.
- Completed: Home placeholder replaced with real local Continue and recently watched Live-TV rows.
- Completed: Playback repository now exposes cross-provider Home queries.
- Completed: Media repository now exposes targeted local lookups for Home items.
- Completed: Live-TV playback now writes one recent-channel history entry per channel.
- Completed: Validated first Package 7 slice with `.\gradlew.bat :feature:home:compileDebugKotlin`.
- Completed: Validated playback repository changes with `.\gradlew.bat :data:playback:compileDebugAndroidTestKotlin`.
- Completed: Ran playback Android tests with `.\gradlew.bat :data:playback:connectedDebugAndroidTest`; 6 tests passed on Android TV emulator.
- Completed: Validated app wiring with `.\gradlew.bat :app:assembleDebug`.
- Completed: Ran `git diff --check`; only existing LF/CRLF warnings were reported.
- Completed: Revalidated after Live-TV history write wiring with `.\gradlew.bat :feature:home:compileDebugKotlin`.
- Completed: Revalidated after Live-TV history write wiring with `.\gradlew.bat :app:assembleDebug`.
- Completed: Re-ran `git diff --check`; only existing LF/CRLF warnings were reported.
- Completed: Existing Live-TV, Movies, and Series favorites surfaces were rechecked; they remain provider-scoped and do not merge providers.
- Completed: Existing search history behavior was rechecked; local history add, delete, and clear behavior is covered by feature tests.
- Completed: Updated affected feature test doubles for the new media/playback repository contracts.
- Completed: Revalidated feature AndroidTest compilation with `.\gradlew.bat :feature:movies:compileDebugAndroidTestKotlin`.
- Completed: Revalidated feature AndroidTest compilation with `.\gradlew.bat :feature:search:compileDebugAndroidTestKotlin`.
- Completed: Revalidated feature AndroidTest compilation with `.\gradlew.bat :feature:live-tv:compileDebugAndroidTestKotlin`.
- Completed: Re-ran `git diff --check`; only existing LF/CRLF warnings were reported.
- Done: Package 7 scope is complete against the current implementation masterplan package boundaries.
