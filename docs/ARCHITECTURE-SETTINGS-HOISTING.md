# Settings Architecture — App-Hoisting Guardrail

Which Settings concerns live in the `SettingsViewModel` and which stay **App-hoisted** (in `MainActivity` /
the app layer), and why. Follow this when touching Settings so the layering isn't accidentally broken — e.g.
do **not** pull Backup / PIN / scheduler / picker logic into the ViewModel.

## In the SettingsViewModel

All data-driven Settings run through one `SettingsViewModel` with an immutable `SettingsUiState`. No Settings
composable collects Repository flows or calls Repository CRUD directly.

| Area | Data source | Note |
|--------|-------------|------|
| General (LaunchOnBoot, DoubleBack, GlobalUserAgent, BackgroundRefresh-Pref, SelectedSection) | `UserPreferencesStore` | BackgroundRefresh **scheduler** stays App |
| Appearance (Theme/Accent/Transparency/FontScale, Language-Pref) | `UserPreferencesStore` | Locale/`recreate()` stays App |
| Playback (13 fields) | `UserPreferencesStore` | |
| EPG-global (Retention/Refresh-Interval/Flags) | `UserPreferencesStore` | |
| Diagnostics-DataStore (LoggingEnabled, RetentionDays) | `UserPreferencesStore` | `DiagnosticsStore.setLoggingEnabled` (system) stays App |
| Maintenance / Cache-Stats + Clear | `MediaCacheStore` + App-owned Coil image cache (size/clear passed in as `suspend` lambdas) | Coil/Context stay out of the VM |
| EPG sources + CRUD (save/delete) | `EpgSourceRepository` | CRUD as `suspend Result` |
| Provider↔EPG-Source links (observe + link/unlink/move) | `EpgSourceRepository` | |
| Manual EPG Mapping (channels/mappings + set/clear + re-entry reset) | `EpgSourceRepository` | |
| Provider overview / list (read-only) | `ProviderRepository.observeProviders()` | field `providers`, shared with EPG |
| Provider CRUD (getCredentials/create/update/setEnabled/delete, getM3uInlineContent) | `ProviderRepository` | mutations as `suspend Result` |
| Category group management (manage/close, type tab, hide/show, bulk-hide, reorder, order-reset, sort-mode, new-groups policy) | `CategoryGroupRepository` | D10; drives the "Gruppen verwalten" flow |

`SettingsViewModel` deps: `UserPreferencesStore`, `MediaCacheStore`, `EpgSourceRepository`,
`ProviderRepository`, `CategoryGroupRepository` (+ the App-owned Coil image cache as `imageCacheSizeBytes` /
`clearImageCache` `suspend` lambdas, + a test `scope`). **No** Context/Activity/Resources/PackageManager,
**no** Compose types, **no** navigation, **no** SAF/Keystore/Scheduler/network logic, **no** localized strings.

## Deliberately App-hoisted (do NOT move into the ViewModel)

| Area | Why it stays App-hoisted |
|--------|--------------------------|
| **Backup** (`BackupSettingsPanel`) | In-app file picker (`FilePickerDialog`/`StorageAccess`, the TV-safe SAF replacement), `StandardBackupExporter/Restorer`, passphrase `CharArray` dialogs (`AppDialogs`), protection-unlock. The panel only triggers `onExportBackup()`/`onImportBackup()`; state + flow live in `MainActivity`. |
| **Parental / PIN / Security** (`ParentalControlSettingsPanel`) | Android Keystore (`pinSecurityStateStore`, `PinSecurity`), app state `unlockedProtectionAreas`, protection-unlock dialog (app nav), `syncWatchNext`, `context.getString` error messages. Moving it would push security into the presentation layer. |
| **About** (`AboutSettingsPanel`) | `state = context.aboutAppState()` (PackageManager/Build) — Context-bound. |
| **Diagnostics-Export** | `onExportDiagnostics` writes the diagnostics ZIP into a `FilePickerDialog`-chosen folder (last dir remembered in `diagnostics.lastExportDir`; `Download/Vivicast` fallback via `writeToUserFolderOrFallback`). Crash logging (`CrashLogWriter`) is App-hoisted, always on. |
| **Diagnostics-Event-Logging** (`onLogProviderSaved` / `onLogProviderDeleted` / `onLogGroupEvent`) | App layer writes to `DiagnosticsStore` (gated by the logging toggle); feature/VM never touch the store — they only report via these callbacks. Provider/category ids go in as opaque `target` values. |
| **Refresh-all playlists** (`onRefreshAll`, `ProviderSettingsPanel`) | `MainActivity` enqueues `refreshWorkScheduler.enqueuePlaylistRefresh(id)` for each active provider (WorkManager) — there is no single global-enqueue method. |
| **Clear History** (`onClearHistory`) | `AppContainer.clearHistory(target)`. |
| **Provider Connection-Test** (`onTestProviderConnection`) | `AppContainer.testProviderConnection` (network I/O). The logic lives in `TestProviderConnectionUseCase` (`:data:provider`); the German UI-message mapping stays App-side. |
| **M3U file picker** (`onPickM3uFile`) | In-app `FilePickerDialog` + `StorageAccess` (SAF replacement; `File.listFiles()` + runtime/all-files permission). |
| **Locale / Recreate** (`onLanguageChanged`) | `LocaleHelper.save` + `activity.recreate()`. The language **pref** is in the VM; only the system effect stays App. |
| **BackgroundRefresh scheduler** (`onBackgroundRefreshChanged`) | `refreshWorkScheduler.setMaintenancePeriodicEnabled` (logos + cache maintenance periodic; playlist/EPG periodics are gated by the C1 model in `MainActivity`). Pref is in the VM; only the scheduler stays App. |
| **ProviderSaved / Scheduler** (`onProviderSaved`) | `refreshWorkScheduler.enqueuePlaylistRefresh` — a side effect **after** the VM save; does not persist itself. |

## SettingsUiState fields

`general`, `appearance`, `playback`, `epg`, `diagnostics`, `cache`, `epgSources`, `providers`,
`selectedEpgProviderId`, `providerEpgLinks`, `manualMappingChannels`, `selectedManualMappingChannelId`,
`manualMappingsForSelectedChannel`, `manageGroupsProviderId`, `manageGroupsType`, `manageGroups`,
`manageGroupSettings`. `providers` is shared by the EPG area **and** the provider overview
(deliberately neutral name). No unused fields, no localized strings, no Compose/Android types.
