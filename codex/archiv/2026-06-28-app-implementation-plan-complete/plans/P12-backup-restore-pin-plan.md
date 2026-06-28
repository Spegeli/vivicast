# Package 12 - Backup, Restore, PIN, and Protection Plan

## Status

- Package: Package 12 - Backup, Restore, PIN, and Protection
- State: done
- Last completed step: Package 12 scope is implemented: Standard-Backup, encrypted Full-Backup, restore-replace validation, PIN/protection state, Settings UI wiring, Android TV Search cleanup, and Watch Next no-op check.
- Last validated state: `:core:security:testDebugUnitTest`, `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, `:feature:settings:compileDebugKotlin`, `:feature:settings:compileDebugAndroidTestKotlin`, focused `StandardBackupTest` with 12 tests, focused `SettingsDialogFocusTest` with 6 tests on the Android TV emulator, and targeted `git diff --check` passed. Manual QA reached the Backup panel and fixed the first D-Pad focus target; the full Android system document-picker roundtrip remains a documented emulator automation limitation.
- Next concrete step: Start Package 13 - Android TV System Integration and Polish.
- Open blockers: None.
- Open Owner questions: None.

## Sources Read

- `AGENTS.md`
- `README.md`
- `codex/plans/APP-IMPLEMENTATION-PLAN.md`
- `../vivicast-docs/codex/README.md`
- `../vivicast-docs/DOCS-GOVERNANCE.md`
- `../vivicast-docs/codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
- `../vivicast-docs/codex/coding-rules.md`
- `../vivicast-docs/prd/PRD-v1/04-search-settings-player-requirements.md`
- `../vivicast-docs/prd/PRD-v1/08-android-tv-security.md`
- `../vivicast-docs/prd/PRD-v1/10-backup-import-requirements.md`
- `../vivicast-docs/prd/PRD-v1/13-test-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-004-backup-strategy.md`
- `../vivicast-docs/architecture/decisions/ADR-014-security-data-network-backup.md`
- `../vivicast-docs/architecture/diagrams/06-backup-restore-flow.md`
- `../vivicast-docs/design/design-system/04-focus-navigation.md`
- `../vivicast-docs/design/design-system/05-screen-patterns.md`

## Scope

- Manual Standard-Backup and encrypted Full-Backup foundations.
- Restore-replace flow foundations with validation before local data changes.
- PIN setup/change/disable and local lockout rules.
- Local protection state for Settings, movies, series, and adult-content gates.
- Backup/Restore/PIN settings UI and TV focus behavior.
- Cleanup hooks for search and Watch Next when restore or protection rules change.

## Non-Scope

- No docs-repo changes.
- No automatic backup schedules.
- No cloud sync or account system.
- No merge restore, conflict dialog, or import-as-copy.
- No new crypto dependency unless PBKDF2/AES-GCM from the platform proves insufficient.

## Affected App Modules and Files

- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `app/src/main/java/com/vivicast/tv/MainActivity.kt`
- `app/src/androidTest/java/com/vivicast/tv/ProtectionGateTest.kt`
- `app/src/main/java/com/vivicast/tv/di/AppContainer.kt`
- `app/src/main/java/com/vivicast/tv/backup/StandardBackup.kt`
- `app/src/main/java/com/vivicast/tv/backup/EncryptedFullBackup.kt`
- `app/src/main/java/com/vivicast/tv/backup/StandardBackupExporter.kt`
- `app/src/main/java/com/vivicast/tv/backup/StandardBackupRestorer.kt`
- `app/src/main/java/com/vivicast/tv/backup/StandardBackupRestoreValidator.kt`
- `app/src/androidTest/java/com/vivicast/tv/backup/StandardBackupTest.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/dao/AndroidTvSearchDao.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/dao/FavoritesDao.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/dao/PlaybackDao.kt`
- `core/database/src/main/java/com/vivicast/tv/core/database/dao/SearchDao.kt`
- `data/media/src/main/java/com/vivicast/tv/data/media/MediaRepository.kt`
- `data/media/src/main/java/com/vivicast/tv/data/media/RoomMediaRepository.kt`
- `data/media/src/androidTest/java/com/vivicast/tv/data/media/RoomMediaRepositoryTest.kt`
- `core/security/src/main/java/com/vivicast/tv/core/security/`
- `core/security/src/test/java/com/vivicast/tv/core/security/`
- `feature/settings/src/main/java/com/vivicast/tv/feature/settings/SettingsRoute.kt`
- `feature/settings/src/androidTest/java/com/vivicast/tv/feature/settings/SettingsDialogFocusTest.kt`
- `core/datastore/src/main/java/com/vivicast/tv/core/datastore/`
- `core/database/src/main/java/com/vivicast/tv/core/database/`
- `data/provider`, `data/epg`, `data/favorites`, `data/playback`, `data/media`
- `feature/settings/src/main/java/com/vivicast/tv/feature/settings/SettingsRoute.kt`

## Technical Approach

- Keep platform backup disabled for app-private data; Vivicast's manual backup is the product backup path.
- Add PIN logic in `:core:security` first: four digits, salted slow verifier, five-failure lockout with 30 s, 60 s, then 5 min.
- Store security-effective local state outside normal DataStore backup/restore targets.
- Add backup schema and validation before destructive restore logic.
- Export Standard-Backup without secrets first; add encrypted Full-Backup with platform PBKDF2/AES-GCM after the standard contract is covered.
- Wire UI last enough to avoid visible flows without working safety logic.

## Risks and Assumptions

- Current Settings UI has placeholder Backup and Kindersicherung sections; these must be replaced rather than treated as working features.
- Current provider secrets are already in `SecureValueStore`, but Android platform backup must not be allowed to treat app-private encrypted blobs as a backup product path.
- Full restore touches many repositories, so restore-replace must stay transactional and small-sliced.

## Relevant Validation

- `.\gradlew.bat :app:processDebugMainManifest`
- `.\gradlew.bat :core:security:testDebugUnitTest`
- `.\gradlew.bat :feature:settings:compileDebugKotlin :feature:settings:compileDebugAndroidTestKotlin`
- Focused Android TV Settings instrumentation tests for PIN and Backup dialogs once UI is wired.
- Backup/Restore repository roundtrip tests before marking Package 12 done.
- Targeted `git diff --check`.

## Completed Work

- Package 12 source refresh completed.
- Current placeholder Backup and Kindersicherung Settings UI inspected.
- Existing Keystore-backed `SecureValueStore` inspected.
- Android platform auto-backup is disabled with `android:allowBackup="false"` so app-private data is not backed up outside the manual Vivicast backup contract.
- `:core:security` now has test-covered PIN check-value generation, constant-time verification, five-failure lockout, lockout escalation, and success reset logic.
- PIN security state is now persisted as a versioned value in the existing Keystore-backed `SecureValueStore`.
- `AppContainer` exposes `PinSecurityStateStore` so app orchestration can load and save PIN state without feature modules depending on storage details.
- Settings `Kindersicherung` now replaces the old placeholder with PIN setup, PIN change, and PIN disable dialogs using numeric password fields and visible confirmation buttons.
- Settings dialog instrumentation now covers the PIN setup dialog and verifies that matching PIN input submits through the callback.
- Protection-area flags for Settings, movies, series, and adult content are now part of `PinSecurityState` and the secure-state codec remains backward-compatible with the earlier PIN-only state format.
- Settings `Kindersicherung` now exposes concrete protection toggles for `Einstellungen schützen`, `Filme schützen`, `Serien schützen`, and `Inhalte ab 18 schützen`; toggles require an existing PIN.
- `MainActivity` now loads, updates, and persists protection-area state through the same Keystore-backed PIN state store.
- Settings dialog instrumentation now covers the protection-area toggle callback for movies.
- `MainActivity` now gates selected protected main routes, Search/deep-link route targets, and direct Movie/Episode playback starts through the PIN verifier.
- Protection unlocks are memory-only Compose state and are cleared when the PIN changes, PIN protection is disabled, or a protection area is disabled.
- Adult-content gates use only the imported `isAdult` flags on Movie/Episode data and do not guess from category names.
- App instrumentation now covers the pure protection-gate decisions.
- Android TV Search index rebuild now accepts protection filters: movie protection removes movie entries, series protection removes series entries, and adult protection removes only `isAdult` movie/series entries.
- `MainActivity` rebuilds the Android TV Search index after loading local protection state and whenever movie, series, or adult-content protection changes.
- Superseded later: Watch Next publication and cleanup/invalidation were implemented in Package 13.
- Standard-Backup schema and JSON output foundation now exists in `app/src/main/java/com/vivicast/tv/backup/StandardBackup.kt`.
- Standard-Backup provider mapping exports provider metadata by stable key and never exports local `credentialsKey`.
- Standard-Backup source redaction exports safe M3U/EPG HTTP(S) URLs only when they have no user info, query, fragment, or secret-like path segment.
- Standard-Backup Xtream source export keeps only the server origin and never exports username or password.
- Standard-Backup security output contains only `parentalProtectionWasActive`; PIN values, lockout state, and protection-area flags are not serialized.
- Standard-Backup snapshot assembly now reads current settings, providers, safe source URLs, EPG sources, EPG priorities/mappings, category visibility/sort, favorites, playback progress, channel history, search history, and parental-protection active summary.
- AppContainer exposes the repository-backed `StandardBackupExporter` for later Settings UI wiring.
- App now declares a direct Room runtime dependency because the exporter intentionally calls database DAO methods from `:app`.
- Standard-Backup restore validation now aborts invalid JSON, unsupported schema version, unsupported export mode, unsafe source URLs, invalid stable-key references, and known credential fields before any local data changes.
- Restore validation ignores old PIN/protection fields and exposes only the non-security `parentalProtectionWasActive` preview flag.
- Standard-Backup restore replacement now reuses validation first and leaves local data untouched for invalid input.
- Standard-Backup restore replacement now transactionally replaces providers, EPG sources, mappings, categories, favorites, playback progress, channel history, and search history with the backup scope.
- Restored favorites, progress, and channel history use stable backup references as pending local references until provider refresh can resolve them.
- Standard-Backup restore writes only safe M3U/EPG URLs back to `SecureValueStore`, marks Xtream restore as credentials-required, removes old known source secrets, clears the selected provider, disables parental control, clears PIN security state, and rebuilds Android TV Search.
- AppContainer exposes the repository-backed `StandardBackupRestorer` for later Settings UI wiring.
- Standard-Backup restore now attempts an internal private safety backup before destructive replacement.
- If the internal safety backup fails, restore returns `SafetyBackupFailed` before local data changes; callers must explicitly retry with `continueAfterSafetyBackupFailure = true` to continue.
- Settings `Backup` now exposes real Standard-Backup export/import actions instead of placeholder rows.
- Standard-Backup export uses Android's document provider through `CreateDocument("application/json")` and writes the repository-backed Standard-Backup JSON.
- Standard-Backup import uses Android's document provider through `OpenDocument`, validates the selected file before restore, shows a restore confirmation preview, and handles the safety-backup-failed continue/abort branch without local data changes before explicit continue.
- Backup export/import actions request a fresh local PIN prompt when Settings protection is active.
- Encrypted Full-Backup container foundation now exists in `app/src/main/java/com/vivicast/tv/backup/EncryptedFullBackup.kt`.
- Full-Backup container metadata includes schema version, export mode, export time, app version, package name, database version, data sections, KDF metadata, cipher metadata, ciphertext, and separated `authTag`.
- Full-Backup payload JSON is encrypted with PBKDF2-HMAC-SHA256 and AES-GCM using a fresh salt and nonce per backup.
- Wrong passphrase, unsupported metadata, or authentication failure returns `null`, so restore callers can abort before any local data changes.
- StandardBackupExporter now builds a repository-backed Full-Backup payload from the same standard snapshot and then switches the encrypted payload to `FULL`.
- Full-Backup payload assembly restores complete provider source secrets for M3U and Xtream, including Xtream username/password, and complete EPG source URLs inside the encrypted payload scope.
- Full-Backup export keeps PIN check values, lockout state, and protection flags out of the payload while still allowing source secrets inside the encrypted section.
- Encrypted Full-Backup restore now decrypts and validates the `FULL` payload before running the existing replace-restore flow.
- Wrong Full-Backup passphrases return an invalid restore result before internal safety backup attempts or local data changes.
- Full-Backup restore writes complete M3U, Xtream, and EPG source secrets back to `SecureValueStore`; Xtream providers with complete restored credentials are marked active.
- Settings `Backup` now exposes encrypted Full-Backup export/import rows with a local passphrase dialog.
- Encrypted Full-Backup export uses Android's document provider through `CreateDocument("application/json")`, encrypts the repository-backed Full-Backup payload, and clears the transient passphrase array after use.
- Encrypted Full-Backup import uses Android's document provider through `OpenDocument`, decrypts and authenticates with the passphrase before restore confirmation, validates the `FULL` payload, clears the transient passphrase array, and reuses the existing restore confirmation and safety-backup branch.
- Settings dialog instrumentation now covers the encrypted Full-Backup passphrase export callback.
- Backup panel D-Pad entry now targets the first actionable row (`Backup exportieren`) instead of the non-focusable informational panel.

## Still Open

- None for Package 12.
- Documented QA limitation: the Android system document-picker export/import roundtrip could not be proven reliably through ADB/UI-dump automation on the current Android TV emulator. The app-side callbacks, passphrase handling, validation, encryption/decryption, restore safety gate, and repository roundtrips are covered by focused tests.
- Superseded later: Watch Next cleanup is no longer a Package 12 open item because Package 13 added Watch Next publication plus invalidation wrappers.

## Owner Questions

- None.

## Validation Log

- Passed: `.\gradlew.bat :app:processDebugMainManifest` after disabling Android platform auto-backup.
- Passed: `.\gradlew.bat :core:security:testDebugUnitTest` after the PIN core slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings for Package 12 touched files.
- Passed: `.\gradlew.bat :core:security:testDebugUnitTest :app:compileDebugKotlin` after the PIN state store slice.
- Passed: `.\gradlew.bat :core:security:testDebugUnitTest :feature:settings:compileDebugKotlin :feature:settings:compileDebugAndroidTestKotlin :app:compileDebugKotlin` after the PIN Settings slice.
- Passed: `.\gradlew.bat :feature:settings:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.settings.SettingsDialogFocusTest"` with 4 tests on the Android TV emulator after the PIN Settings slice.
- Passed after one Kotlin nullability fix: `.\gradlew.bat :core:security:testDebugUnitTest :feature:settings:compileDebugKotlin :feature:settings:compileDebugAndroidTestKotlin :app:compileDebugKotlin` after the protection-area state slice.
- Passed: `.\gradlew.bat :feature:settings:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.settings.SettingsDialogFocusTest"` with 5 tests on the Android TV emulator after the protection-area state slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings after the protection-area state slice.
- Passed: `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` after the protection-gate slice.
- Passed: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.ProtectionGateTest"` with 2 tests on the Android TV emulator after the protection-gate slice.
- Passed: targeted `git diff --check` with only an LF/CRLF warning for `MainActivity.kt` after the protection-gate slice.
- Passed: `.\gradlew.bat :core:database:compileDebugKotlin :data:media:compileDebugKotlin :data:media:compileDebugAndroidTestKotlin :app:compileDebugKotlin` after the Android TV Search protection slice.
- First `:data:media:connectedDebugAndroidTest` attempt could not run because no device was connected; restarted the TV emulator with `scripts\start-tv-emulator.ps1`.
- Passed after one test expectation correction: `.\gradlew.bat :data:media:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.data.media.RoomMediaRepositoryTest"` with 7 tests on the Android TV emulator after the Android TV Search protection slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings after the Android TV Search protection slice.
- Passed: `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` after the Standard-Backup foundation slice.
- Passed: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest"` with 3 tests on the Android TV emulator after the Standard-Backup foundation slice.
- Failed then fixed: `.\gradlew.bat :core:database:compileDebugKotlin :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` initially failed because `:app` accessed `VivicastDatabase` DAO methods without a direct Room runtime dependency; fixed by adding `libs.androidx.room.runtime` to `:app`.
- Passed: `.\gradlew.bat :core:database:compileDebugKotlin :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` after the Standard-Backup snapshot slice.
- Failed then fixed: focused `StandardBackupTest` initially had an overly broad `pin` substring assertion that matched `epgChannelMappings`; fixed to assert forbidden security keys directly.
- Passed: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest"` with 4 tests on the Android TV emulator after the Standard-Backup snapshot slice.
- Passed: `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` after the Standard-Backup restore validation slice.
- Passed: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest"` with 7 tests on the Android TV emulator after the Standard-Backup restore validation slice.
- Passed: `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` after the Standard-Backup replace-flow slice.
- Passed: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest"` with 8 tests on the Android TV emulator after the Standard-Backup replace-flow slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings after the Standard-Backup replace-flow slice.
- Passed: `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` after the Standard-Backup safety-backup gate slice.
- Passed: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest"` with 9 tests on the Android TV emulator after the Standard-Backup safety-backup gate slice.
- Passed: `.\gradlew.bat :feature:settings:compileDebugKotlin :feature:settings:compileDebugAndroidTestKotlin :app:compileDebugKotlin` after the Settings Backup UI wiring slice.
- Passed: `.\gradlew.bat :feature:settings:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.settings.SettingsDialogFocusTest"` with 5 tests on the Android TV emulator after the Settings Backup UI wiring slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings after the Settings Backup UI wiring slice.
- Passed: `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest"` with 10 tests on the Android TV emulator after the encrypted Full-Backup container slice.
- Passed: targeted `git diff --check` after the encrypted Full-Backup container slice.
- Failed then fixed: first Full-Backup payload test compile used a non-existent single-source EPG DAO method; fixed by using `upsertEpgSources(listOf(...))`.
- Passed: `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest"` with 11 tests on the Android TV emulator after the Full-Backup payload assembly slice.
- Passed: `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest"` with 12 tests on the Android TV emulator after the encrypted Full-Backup restore slice.
- Passed: `.\gradlew.bat :feature:settings:compileDebugKotlin :feature:settings:compileDebugAndroidTestKotlin :app:compileDebugKotlin --console=plain` after the encrypted Full-Backup Settings UI slice.
- Passed: `.\gradlew.bat :feature:settings:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.settings.SettingsDialogFocusTest" --console=plain` with 6 tests on the Android TV emulator after the encrypted Full-Backup Settings UI slice.
- Passed: targeted `git diff --check` with only LF/CRLF warnings after the encrypted Full-Backup Settings UI slice.
- Manual Android TV emulator QA reached the Backup panel and confirmed the new Standard/Full-Backup copy is visible; first attempt exposed that D-Pad entry targeted a non-focusable info panel.
- Passed: `.\gradlew.bat :feature:settings:compileDebugKotlin :app:compileDebugKotlin :app:installDebug --console=plain` after moving Backup D-Pad entry focus to the first actionable row.
- Passed: `.\gradlew.bat :feature:settings:compileDebugAndroidTestKotlin :feature:settings:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.settings.SettingsDialogFocusTest" --console=plain` with 6 tests on the Android TV emulator after the Backup focus fix.
- Passed: targeted `git diff --check` with only LF/CRLF warnings after the Backup focus fix.
- Manual Android TV emulator picker-start attempt after the focus fix did not produce a clear DocumentsUI picker in the UI dump; keep the full document-picker export/import roundtrip open.
- Passed: `.\gradlew.bat :core:security:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :feature:settings:compileDebugKotlin :feature:settings:compileDebugAndroidTestKotlin --console=plain` for final Package 12 validation.
- Passed: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.backup.StandardBackupTest" --console=plain` with 12 tests on the Android TV emulator for final Package 12 validation.
- Passed: `.\gradlew.bat :feature:settings:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.vivicast.tv.feature.settings.SettingsDialogFocusTest" --console=plain` with 6 tests on the Android TV emulator for final Package 12 validation.
- Passed: targeted `git diff --check` with only LF/CRLF warnings before marking Package 12 done.
