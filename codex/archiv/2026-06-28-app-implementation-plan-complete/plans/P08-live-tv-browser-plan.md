# Package 8 - Live-TV Browser

## Status

- Package: Package 8 - Live-TV Browser
- State: done
- Last completed step: Global favorites, sender-mode activation, EPG/No-EPG OK behavior, internal Back-chain, route-level CH+ / CH- handling, PRD 6 sort/hidden scope, and the reachable Live-TV demo fallback were re-aligned.
- Last validated state: Live-TV compile, Live-TV android-test compile, 6 connected Live-TV focus tests, app debug build, and diff check passed after removing the reachable Live-TV demo fallback; manual public-list import QA is deferred because the current Settings/UI import path is not stable enough for Package 8 validation.
- Next concrete step: Start Package 9 - VOD Movies and Series with a package-specific plan.
- Open blockers: None.
- Open Owner questions: None.

## Docs Sources Read

- `AGENTS.md`
- `README.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/prd/PRD-v1/02-live-tv-requirements.md`
- `../vivicast-docs/prd/PRD-v1/06-data-model.md`
- `../vivicast-docs/design/screens/02-live-tv.md`
- `../vivicast-docs/design/wireframes/01-live-tv-browser.md`
- `../vivicast-docs/design/interaction/01-live-tv-adaptive-columns.md`
- `../vivicast-docs/design/interaction/focus.md`
- `../vivicast-docs/design/components/list-grid-items.md`
- `../vivicast-docs/prd/PRD-v1/05-iptv-epg-favorites.md`

## Scope

- Keep the existing Live-TV route and design-system components.
- Make Live-TV favorites a global category above providers.
- Keep provider/category focus from starting preview or opening Sender mode.
- Open Sender mode only on OK in the sender list.
- Preserve provider isolation for channel, favorite, and EPG lookups.

## Non-Scope

- No full player retry/reconnect implementation.
- No new adaptive dependency or Navigation 3 migration.
- No full visual polish pass.
- No channel-level `sortOrder` / hidden-state schema migration; PRD 6 defines category visibility/sort and favorite sort order, not channel-level hidden/sort fields.
- No docs-repo changes.

## Affected App Modules and Files

- `codex/plans/APP-IMPLEMENTATION-PLAN.md`
- `codex/plans/P08-live-tv-browser-plan.md`
- `feature/live-tv/`
- `data/favorites/`
- `core/database/`

## Technical Approach

- Reuse the current three-column Live-TV route.
- Add one global favorites observe query instead of a new use-case layer.
- Resolve global favorite channels through existing `MediaRepository.getChannel`.
- Use the selected channel provider for EPG/favorite actions so global favorites remain provider-isolated.

## Risks and Assumptions

- Current route still uses a placeholder preview surface; this package is aligning browser behavior before deeper player retry/reconnect work.
- CH+ / CH- browser handling was added at route level, but emulator key delivery could not be proven with the current Compose/instrumentation test path.
- Public M3U/EPG live validation is deferred to a later UI/integration pass because the current Settings provider-entry flow did not persist/import the public list reliably in emulator QA.
- Hidden/sort scope was checked against PRD 6: persisted `isHidden` / `sortOrder` belong to categories, and favorite ordering belongs to favorites. No channel-level schema work is required for Package 8 from the current docs.
- The old private `DemoLiveTvRoute` block still exists as dead source code after removing the reachable fallback. Delete it in the broader `DemoCatalog` cleanup to avoid fighting old file-encoding churn in this package slice.

## Validation Plan

- `.\gradlew.bat :data:favorites:compileDebugKotlin`
- `.\gradlew.bat :feature:live-tv:compileDebugAndroidTestKotlin`
- `.\gradlew.bat :feature:live-tv:connectedDebugAndroidTest`
- `.\gradlew.bat :app:assembleDebug`
- `git diff --check`

## Owner-Provided Public Live Test Sources

- M3U: `https://iptv-org.github.io/iptv/languages/deu.m3u`
- EPG: `https://iptv-epg.org/files/epg-de.xml.gz`
- Use only for local emulator/live validation. Do not commit downloaded playlist or EPG data.

## Progress

- Completed: Package 8 plan created from final docs.
- Completed: Current Live-TV route, EPG repository, and favorites repository inspected.
- Completed: Live-TV favorites now use a global favorites query and appear above providers.
- Completed: First provider expands on initial load without forcing re-expansion after manual collapse.
- Completed: Sender focus no longer opens Sender mode or starts preview; Sender mode opens on OK.
- Completed: EPG and favorite actions use the selected channel provider to preserve provider isolation for global favorites.
- Completed: Updated affected Live-TV and Movies test doubles for the expanded `FavoritesRepository` contract.
- Completed: Validated with `.\gradlew.bat :data:favorites:compileDebugKotlin`.
- Completed: Validated with `.\gradlew.bat :feature:live-tv:compileDebugAndroidTestKotlin`.
- Completed: Validated with `.\gradlew.bat :app:assembleDebug`.
- Completed: Current EPG program OK starts fullscreen playback instead of catch-up.
- Completed: No-EPG state is focusable with `Keine Programminformationen verfügbar`, `Ohne EPG`, and OK starts fullscreen playback.
- Completed: Added focused Live-TV route tests for current EPG OK and No-EPG fallback behavior.
- Completed: Validated with `.\gradlew.bat :feature:live-tv:connectedDebugAndroidTest`; 5 tests passed on Android TV emulator.
- Completed: Ran `git diff --check`; only existing LF/CRLF warnings were reported.
- Completed: Internal Back-chain now steps from EPG/Preview to Senderliste, then to Provider/Kategorien; after that `MainActivity` handles Top Navigation.
- Completed: Added focused Live-TV route test for Back-chain behavior.
- Completed: Added route-level CH+ / CH- handling for browser channel-list focus without changing player zapping behavior.
- Completed: Validated with `.\gradlew.bat :feature:live-tv:compileDebugAndroidTestKotlin`.
- Completed: Validated with `.\gradlew.bat :feature:live-tv:connectedDebugAndroidTest`; 6 tests passed on Android TV emulator.
- Completed: Validated with `.\gradlew.bat :app:assembleDebug`.
- Completed: Ran `git diff --check`; only existing LF/CRLF warnings were reported.
- Completed: Checked PRD 6 data-model scope for Package 8 hidden/sort acceptance; no channel-level hidden/sort schema change is required because category visibility/sort and favorite sort order are the documented persisted fields.
- Completed: Removed the attempted CH+ / CH- instrumentation test because shell/native key injection did not deliver TV channel keycodes to Compose route focus handling.
- Completed: Re-validated with `.\gradlew.bat :feature:live-tv:connectedDebugAndroidTest`; 6 tests passed on Android TV emulator.
- Completed: Validated with `.\gradlew.bat :app:assembleDebug`.
- Completed: Ran `git diff --check`; only existing LF/CRLF warnings were reported.
- Completed: Removed the reachable `DemoLiveTvRoute` fallback by requiring real repositories in `LiveTvRoute`.
- Completed: Validated with `.\gradlew.bat :feature:live-tv:compileDebugKotlin`.
- Completed: Validated with `.\gradlew.bat :feature:live-tv:compileDebugAndroidTestKotlin`.
- Completed: Validated with `.\gradlew.bat :feature:live-tv:connectedDebugAndroidTest`; 6 tests passed on Android TV emulator.
- Completed: Validated with `.\gradlew.bat :app:assembleDebug`.
- Completed: Ran targeted `git diff --check`; only existing LF/CRLF warnings were reported.
- Deferred: CH+ / CH- public-list emulator validation waits for a stable Settings/UI import path; route-level handling is implemented and Package 8 browser logic is covered by existing focused tests where key delivery is reliable.
- Deferred: Remove the now-dead private `DemoLiveTvRoute` source block together with the remaining `DemoCatalog` cleanup.
- Completed: Package 8 marked done with manual public-list import QA deferred to later UI/integration hardening rather than treated as a Live-TV Browser blocker.
