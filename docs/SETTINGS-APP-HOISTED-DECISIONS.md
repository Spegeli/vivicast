# Settings App-Hoisted Decisions

> Abschluss-/Entscheidungsdokument für **P1-04f5**. Reiner Prüf-/Doku-Lauf auf Basis von
> HEAD `dbd2bfc` (P1-04f4b). Keine Kotlin-Codeänderung in diesem Schritt.

## Ergebnis

Aus architektonischer Sicht ist **P1-04 Settings abgeschlossen**. Alle datengetriebenen
Settings-Bereiche (DataStore-Preferences, Cache-Stats, EPG-Quellen/-Zuordnung, Manual-Mapping,
Provider-Liste + Provider-CRUD) laufen über einen einzigen `SettingsViewModel` mit einem immutablen
`SettingsUiState`. Kein Settings-Composable sammelt mehr direkt Repository-Flows oder ruft direkt
Repository-CRUD auf. Die verbleibenden App-hoisted-Bereiche sind bewusst außerhalb des ViewModels
(SAF/ActivityResult, Android-Keystore/PIN, Context/PackageManager, WorkManager-Scheduler,
Netzwerk-Connection-Test) und gehören dort auch hin. Es verbleibt nur eine **kosmetische Restliste**
(ungenutzte `collectAsState`-Imports) und optionale spätere Nicht-P1-04-Schritte.

## In das SettingsViewModel migriert

| Bereich | Status | Datenquelle | Bemerkung |
|--------|--------|-------------|-----------|
| General (LaunchOnBoot, DoubleBack, RememberSorting, GlobalUserAgent, BackgroundRefresh-Pref, SelectedSection) | ✅ vollständig | `UserPreferencesStore` | f1; BackgroundRefresh-**Scheduler** bleibt App |
| Appearance (Theme/Accent/Transparency/FontScale/AnimationSpeed, Language-Pref) | ✅ vollständig | `UserPreferencesStore` | f1; Locale/`recreate()` bleibt App |
| Playback (13 Felder) | ✅ vollständig | `UserPreferencesStore` | f1 |
| EPG-global (Retention/Refresh-Intervall/Flags) | ✅ vollständig | `UserPreferencesStore` | f2a |
| Diagnostics-DataStore (LoggingEnabled, RetentionDays) | ✅ vollständig | `UserPreferencesStore` | f2a; `DiagnosticsStore.setLoggingEnabled` (System) bleibt App |
| Maintenance/Cache-Stats + Clear | ✅ vollständig | `MediaCacheStore` | f2b |
| EPG-Quellenliste + CRUD (save/delete) | ✅ vollständig | `EpgSourceRepository` | f3a; CRUD als `suspend Result` |
| Provider↔EPG-Source-Links (observe + link/unlink/move) | ✅ vollständig | `EpgSourceRepository` | f3a |
| Manual EPG Mapping (channels/mappings + set/clear + Re-Entry-Reset) | ✅ vollständig | `EpgSourceRepository` | f3b + f3b-fix |
| Provider-Overview/List (read-only) | ✅ vollständig | `ProviderRepository.observeProviders()` | f4a; Feld `providers` (geteilt mit EPG) |
| Provider-CRUD (getCredentials/create/update/setEnabled/delete) | ✅ vollständig | `ProviderRepository` | f4b; Mutationen als `suspend Result` |

`SettingsViewModel`-Abhängigkeiten: `UserPreferencesStore`, `MediaCacheStore`, `EpgSourceRepository`,
`ProviderRepository` (+ Test-`scope`). Keine Context/Activity/Resources/PackageManager, keine
Compose-Typen, keine Navigation, keine SAF/Keystore/Scheduler/Netzwerk-Logik.

## Bewusst App-hoisted geblieben

| Bereich | Warum App-hoisted | Risiken | Späterer möglicher Schritt |
|--------|-------------------|---------|----------------------------|
| **Backup** (`BackupSettingsPanel`) | SAF `ActivityResultLauncher`, `StandardBackupExporter/Restorer`, Passphrase-`CharArray`, Protection-Unlock. State/Callbacks kommen aus `MainActivity`. | ActivityResult/SAF/Security an Activity-Lifecycle gebunden; VM-Migration brächte kaum Nutzen, hohes Risiko | Nur falls separates Backup-/Security-Konzept kommt; sonst belassen |
| **Parental/PIN/Security** (`ParentalControlSettingsPanel`) | Android-Keystore (`pinSecurityStateStore`, `PinSecurity`), App-State `unlockedProtectionAreas`, Protection-Unlock-Dialog (App-Nav), `syncWatchNext`, `context.getString`-Fehlermeldungen. PIN-Callbacks liefern lokalisierte Strings zurück. | Security-Zustand + App-Nav-Dialoge + Context-Strings; VM-Migration verlagert Security in Presentation-Layer | Separater Security-/Dialog-Plan (nicht P1-04) |
| **About** (`AboutSettingsPanel`) | `state = context.aboutAppState()` (PackageManager/Build). | Context-gebunden | Optional: nicht-Context-`AboutStateProvider` ins VM; geringer Nutzen |
| **Diagnostics-Export / Support-Copy** | `onExportDiagnostics` (SAF), `onCopySupportInformation` (Clipboard/Context, `buildSupportInformation`). | SAF/Context | Belassen; Diagnostics-**DataStore** ist bereits im VM (f2a) |
| **Global Refresh** (`onRunGlobalRefresh`) | `refreshWorkScheduler.enqueueGlobalRefresh()` (WorkManager). | Scheduler-Seiteneffekt | Optional als Refresh-UseCase (nicht P1-04) |
| **Clear History** (`onClearHistory`) | `AppContainer.clearHistory(target)`. | App-Container-Aktion | Optional als UseCase |
| **Provider Connection-Test** (`onTestProviderConnection`) | `AppContainer.testProviderConnection` (Netzwerk). | Netzwerk-I/O; explizit aus P1-04 ausgeklammert | P2-07 Connection-Test-UseCase (empfohlen, nicht P1-04) |
| **M3U-Dateipicker/SAF** (`onPickM3uFile`) | SAF DocumentPicker + `hasRealDocumentPicker`. | ActivityResult/SAF | Belassen |
| **Locale/Recreate** (`onLanguageChanged`) | `LocaleHelper.save` + `activity.recreate()`. Language-**Pref** ist im VM; nur der System-Effekt bleibt App. | Activity-Recreate | Belassen |
| **BackgroundRefresh-Scheduler** (`onBackgroundRefreshChanged`) | `refreshWorkScheduler.setBackgroundRefreshEnabled`. Pref ist im VM; nur Scheduler bleibt App. | Scheduler | Belassen |
| **ProviderSaved/Scheduler** (`onProviderSaved`) | `refreshWorkScheduler.enqueuePlaylistRefresh`. Reiner Seiteneffekt **nach** VM-Save; speichert selbst nicht. | Scheduler | Belassen |

## Noch vorhandene direkte Calls in Settings-Composables

| Datei | Call | Bewertung | Muss geändert werden? |
|------|------|-----------|-----------------------|
| `SettingsRoute.kt:151` | `viewModel.uiState.collectAsStateWithLifecycle()` | Korrekt — einziges echtes State-Collecting, liest VM-`StateFlow` | Nein |
| `SettingsViewModel.kt` (diverse) | `userPreferencesStore.*`, `mediaCacheStore.*`, `epgSourceRepository.*`, `providerRepository.*` | Korrekt — gehören ins ViewModel | Nein |
| `EpgSettingsPanel`, `ManualEpgMappingPanel`, `ProviderSettingsPanel` | `scope.launch { onXxx(...).onSuccess/onFailure }` (VM `suspend Result`) | Akzeptabel — lokaler UI-Scope nur für lokalisierte Success/Error-Message + lokalen Dialog/Editor-Reset | Nein |
| `ProviderAddFlow`, `ProviderEditor`, `ProviderSettingsPanel` | `scope.launch { onTestProviderConnection(...) }` | Akzeptabel — App-hoisted Netzwerk-Callback | Nein |
| 17 Panel-Dateien | `import androidx.compose.runtime.collectAsState` (Zeile 27) | **Ungenutzt** — Boilerplate-Rest aus dem P0-01-Split; keine echte `collectAsState(...)`-Nutzung mehr (verifiziert) | Nein (nur Warning; optionale spätere Aufräumung) |

**Ergebnis Direktzugriff-Suche:** Keine direkten Repository-Flows und keine direkten
Repository-CRUD-Aufrufe mehr in Settings-Composables. Nur ungenutzte `collectAsState`-Imports als
kosmetischer Rest.

## SettingsUiState-Struktur (Bewertung)

Felder: `general`, `appearance`, `playback`, `epg`, `diagnostics`, `cache`, `epgSources`,
`providers`, `selectedEpgProviderId`, `providerEpgLinks`, `manualMappingChannels`,
`selectedManualMappingChannelId`, `manualMappingsForSelectedChannel`.

- Keine ungenutzten Felder; keine lokalisierten Strings; keine Compose-/Android-Typen.
- `providers` wird von EPG-Bereich **und** Provider-Overview geteilt (bewusst neutral benannt, f4a).
- VM-Methoden sind schlank (ein `runCatching`/`launch` pro Aktion). Keine überladene Methode.
- Kein Namens-/Strukturproblem, das jetzt Handlung erfordert.

## Offene spätere Schritte (nicht P1-04)

- **Provider-Connection-Test als UseCase** (P2-07) — Netzwerk-Logik aus `MainActivity`/AppContainer.
- **Backup/Security-Konzept separat** — Backup + Parental/PIN gemeinsam, inkl. SAF/Keystore/Dialog.
- **About-State-Provider optional** — nicht-Context-Provider, falls About je ins VM soll (geringer Nutzen).
- **`collectAsStateWithLifecycle`-Globalcheck** — hier bereits erfüllt; app-weit optional prüfen.
- **Kosmetik:** ungenutzte `collectAsState`-Imports (17 Panel-Dateien) entfernen; ggf. detekt/Zeilenlimit.

## Validierung

Ausgeführt auf HEAD `dbd2bfc`:

- `:feature:settings:compileDebugKotlin` — ✅ SUCCESS
- `:feature:settings:testDebugUnitTest` — ✅ 36/36 (0 failures)
- `:feature:settings:compileDebugAndroidTestKotlin` — ✅ SUCCESS
- `:app:compileDebugKotlin` — ✅ SUCCESS
- `:app:assembleDebug` — ✅ SUCCESS

`connectedDebugAndroidTest` nicht ausgeführt (optional; bekannte TV-Emulator-/IME-Flakiness).
