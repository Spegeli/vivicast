# Settings ViewModel Migration Plan (P1-04f0 – Scope Audit)

> Reiner Planungs-/Analyse-Lauf. Keine Code-, Gradle- oder Teständerung. Basis: bestehender Stand
> nach P0-01/P0-01b/P0-02 und P1-04a–e (alle committed, Arbeitsbaum clean).

## 1. Aktueller Settings-Datenfluss

`SettingsRoute` (`feature/settings`) ist **teilweise schon entkoppelt**, aber uneinheitlich:

- **DataStore-getriebene Bereiche werden App-hoisted:** `MainActivity`/`VivicastApp` sammelt
  `appContainer.userPreferencesStore.values` (`loadedPreferences`, `MainActivity.kt:364-368`) und baut daraus
  **immutable State-Objekte** (`GeneralSettingsState`, `AppearanceSettingsState`, `PlaybackSettingsState`,
  `EpgSettingsState`, `BackupSettingsState`, `DiagnosticsSettingsState`) via die Mapper in
  `SettingsPreferenceMappers.kt`. Diese States + passende `onXChanged`-Callbacks werden als Parameter an
  `SettingsRoute` übergeben (`MainActivity.kt:844-1200`). Die Callbacks schreiben via
  `userPreferencesStore.updateGeneral/Appearance/Playback/Epg/Backup/Diagnostics(...)` zurück.
- **Repository-getriebene Bereiche sammeln direkt im Panel:** `EpgSettingsPanel`
  (`epgSourceRepository.observeEpgSources()`, `providerRepository.observeProviders()`,
  `observeProviderEpgSources(...)`), `ManualEpgMappingPanel` (`observeChannelsForProvider`,
  `observeMappingsForChannel`) und `ProviderSettingsPanel` (`observeProviders`) rufen Repository-Flows
  **direkt in der Composable** auf (Repos werden als Parameter gereicht). → Verstößt gegen das P1-04-Ziel.
- **Nicht aus DataStore:**
  - `parentalControlSettingsState` ← `pinSecurityState` (App-`remember`-State; Quelle: Keystore
    `pinSecurityStateStore` + `PinSecurity`-Objekt). Schreibt via `pinSecurityStateStore.write/clear` +
    `syncWatchNext()` + App-State `unlockedProtectionAreas` + Protection-Unlock-Dialog.
  - `cacheSettingsState` ← `cacheStats` (App-`remember`, `MainActivity.kt:195`; Quelle
    `appContainer.mediaCacheStore.stats()`).
  - `aboutAppState` ← `context.aboutAppState()` (Context: PackageManager/Build).
- **Update-Callbacks mit Nebenwirkungen außerhalb DataStore:**
  `onBackgroundRefreshChanged` (+`refreshWorkScheduler.setBackgroundRefreshEnabled`), `onLanguageChanged`
  (`LocaleHelper.save` + `activity.recreate()`), `onDiagnosticsSettingsChanged`
  (+`diagnosticsStore.setLoggingEnabled`), `onProviderSaved` (`refreshWorkScheduler.enqueuePlaylistRefresh`),
  `onRunGlobalRefresh` (`enqueueGlobalRefresh`), `onClearCache`/`onReloadCacheStats` (`mediaCacheStore`),
  `onClearHistory` (`AppContainer.clearHistory`).
- **Rein App/Activity/SAF/System:** `onPickM3uFile` (SAF DocumentPicker + `hasRealDocumentPicker`),
  `onExport/ImportStandardBackup`, `onExport/ImportEncryptedFullBackup` (SAF `ActivityResultLauncher` +
  Passphrase-`CharArray` + Protection-Unlock), `onExportDiagnostics` (SAF), `onSetPin/onChangePin/
  onDisablePin/onProtectionChanged` (PinSecurity + `context.getString` + `syncWatchNext`),
  `onTestProviderConnection` (`AppContainer.testProviderConnection`, Netzwerk),
  `onCopySupportInformation` (Clipboard/Context).

`UserPreferencesStore` bietet: `values: Flow<UserPreferences>` + `updateGeneral/Appearance/Playback/Epg/
Backup/Diagnostics/Parental/History/...`.

## 2. SettingsRoute-Signatur: was kann weg, was muss bleiben

`SettingsRoute` hat **~45 Parameter**. Kategorien:

- **State-Parameter (kandidieren für Wegfall via VM):** `generalSettingsState`, `appearanceSettingsState`,
  `epgSettingsState`, `playbackSettingsState`, `backupSettingsState`, `diagnosticsSettingsState`,
  `cacheSettingsState` (Cache via `mediaCacheStore` → VM möglich), `initialSelectedSection`.
  Nicht ganz trivial: `parentalControlSettingsState` (Keystore/PIN), `aboutAppState` (Context).
- **Reine DataStore-Update-Callbacks (Wegfall via VM):** `onLaunchOnBootChanged`, `onDoubleBackToExitChanged`,
  `onRememberSortingChanged`, `onGlobalUserAgentChanged`, `onAppearanceSettingsChanged`,
  `onEpgPreferencesChanged`, `onPlaybackPreferencesChanged`, `onBackupSettingsChanged`,
  `onSelectedSectionChanged`, `onDiagnosticsSettingsChanged` (DataStore-Teil).
- **Callbacks mit App-Nebenwirkung (bleiben zunächst, ggf. VM+kleiner App-Callback):**
  `onBackgroundRefreshChanged`, `onLanguageChanged`, `onRunGlobalRefresh`, `onClearCache`,
  `onReloadCacheStats`, `onClearHistory`, `onProviderSaved`, `onDiagnosticsSettingsChanged` (System-Teil).
- **Muss App-hoisted bleiben:** `onPickM3uFile`, `onTestProviderConnection`, `onProviderSaved`,
  `onExport/ImportStandardBackup`, `onExport/ImportEncryptedFullBackup`, `onExportDiagnostics`,
  `onCopySupportInformation`, `onSetPin/onChangePin/onDisablePin/onProtectionChanged`.

## 3. Panel-Bewertung

| Bereich | Datenquellen | Schwierigkeit | In VM migrieren? | Begründung | Risiken |
|--------|--------------|---------------|------------------|------------|---------|
| General | DataStore (`general`,`appearance.language`) | **Einfach** | Ja (f1) | Reines Preference↔State-Mapping; nur `backgroundRefresh`/`language` haben App-Seiteneffekt (kleiner App-Callback bleibt) | gering |
| Appearance | DataStore (`appearance`) | **Einfach** | Ja (f1) | Reines Mapping | gering |
| Playback | DataStore (`playback`) | **Einfach** | Ja (f1) | Reines Mapping (13 Felder) | gering (viele Felder) |
| EPG (global) | DataStore (`epg`) | **Einfach** | Ja (f3) | Reines Mapping | gering |
| EPG (Quellen/Editor + Manual-Mapping) | `EpgSourceRepository`, `ProviderRepository` (direkt im Panel) | **Schwer** | Später (f3) | Flow-Collection + CRUD (`setManualChannelMapping`, EPG-Source CRUD); Provider-Auswahl-State; Fokus/Dialog | mittel–hoch |
| Provider (Overview/List) | `ProviderRepository.observeProviders` (direkt) | **Mittel** | Später (f4) | Flow-Collection; Provider-Auswahl | mittel |
| Provider (AddFlow/Editor/Dialogs) | `ProviderRepository` CRUD, `onTestProviderConnection`, `onPickM3uFile` (SAF) | **Schwer** | Teilweise (f4) | Wizard-State (lokal), Connection-Test (Netzwerk), SAF-Filepick | hoch |
| Parental/PIN | Keystore `pinSecurityStateStore`, `PinSecurity`, App-State `unlockedProtectionAreas`, `syncWatchNext`, Protection-Dialog | **Schwer** | Später/ggf. gar nicht (f5) | Security-Zustand + App-Nav-Dialoge; PIN-Callbacks liefern lokalisierte Strings | hoch |
| Backup | SAF-Launcher, `StandardBackupExporter/Restorer`, Protection-Unlock, Passphrase-`CharArray` | **Schwer** | Nein (App-hoisted) | ActivityResult + SAF + Security | hoch |
| About | `context.aboutAppState()` | **Mittel** | Nein/Teil | Context-gebunden; State kann App-hoisted bleiben oder via nicht-Context-Provider gebaut werden | gering |
| Maintenance | `mediaCacheStore` (Cache), `refreshWorkScheduler`, `AppContainer.clearHistory`, Diagnostics | **Mittel** | Teilweise (f2) | Cache-Stats/Clear VM-fähig; Refresh/History bleiben Callback; Diagnostics-Export SAF bleibt App | mittel |

## 4. Empfohlene Zielstruktur

- **Ein `SettingsViewModel`** (nicht mehrere) mit **einem `SettingsUiState`**, das die bestehenden
  immutable Teilstates komponiert. Grund: Settings ist EIN Screen mit Sektions-Routing; mehrere ViewModels
  würden geteilten `UserPreferences`-Flow mehrfach sammeln und die Route verkomplizieren.
- **Bestehende `GeneralSettingsState`/`AppearanceSettingsState`/… weiterverwenden** (liegen bereits als
  immutable data classes in `SettingsModels.kt`). Kein neuer UI-Model-Wildwuchs.
- Dateien (feature/settings):
  - `SettingsUiState.kt` — `data class SettingsUiState(general, appearance, playback, epg, backup,
    diagnostics, cache, about?, parental?, selectedSection?)` (schrittweise gefüllt).
  - `SettingsViewModel.kt` — sammelt `UserPreferencesStore.values`, mappt zu Teilstates, `onXChanged`-Events
    schreiben via `update*`. Nutzt `viewModelScope` (Test-Scope injizierbar, analog f1–e).
  - `SettingsViewModelFactory.kt` — manuelle Factory mit den benötigten Stores/Repos.
  - `SettingsPreferenceMappers.kt` (feature/settings) — **nur die reinen DataStore-Mapper**
    (`toSettings*`/`toDataStore*`, `timeshiftConfig`/`toPlayerStorage` nur falls von Settings gebraucht).
    **Context-Mapper (`aboutAppState`, `diagnosticsAbout`, `copySupportInformation`, `buildSupportInformation`)
    bleiben im App-Modul.**
- **Kein `SettingsEffect`/one-shot nötig** in f1 (DataStore-Writes sind fire-and-forget; Dialoge sind lokaler
  Composable-`remember`-State und bleiben im Panel). Falls später Provider/EPG-CRUD mit
  Erfolg-/Fehlermeldung dazukommt, dann ggf. ein schmales Effect/Message-Feld im UiState.
- **Dialog-State** (UserAgent, PIN, ProviderAddFlow, EpgSourceEditor, Backup-Passphrase, Maintenance-Confirm)
  bleibt **lokaler Panel-`remember`-State** — rein visuell, kein VM.
- **Gradle:** `feature:settings` braucht `:core:datastore` (aktuell nicht eingebunden) für den Zugriff auf
  `UserPreferencesStore` + DataStore-Preference-Typen. Bestehendes Modul, kein neuer External-Dep.

## 5. Empfohlene Umsetzungsschritte

| Schritt | Umfang | Dateien | Risiko | Tests | Akzeptanzkriterien |
|--------|--------|---------|--------|-------|--------------------|
| **P1-04f1** | DataStore-only VM: General + Appearance + Playback + Selected-Section. VM sammelt `UserPreferences`, exposed Teilstates; `onXChanged` schreiben via `updateGeneral/Appearance/Playback`. Mapper → feature/settings. App-Seiteneffekte (`backgroundRefresh`→scheduler, `language`→recreate) bleiben als schmale App-Callbacks an der Route. | `SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsViewModelFactory.kt`, `SettingsPreferenceMappers.kt` (feature/settings), `SettingsRoute.kt` (Signatur), `feature/settings/build.gradle.kts` (+`:core:datastore`), `MainActivity.kt` (Wiring reduzieren) | mittel | `SettingsViewModelTest` (Fake `UserPreferencesStore`) | Route sammelt für diese 3 Bereiche keine App-hoisted States mehr; DataStore-Writes über VM; General/Appearance/Playback-`androidTest`s grün |
| **P1-04f2** | Maintenance-Cache-Stats/Clear (`mediaCacheStore`) + EPG-global-Prefs + Diagnostics-DataStore-Teil ins VM. Refresh/History/Export bleiben App-Callbacks. | VM + UiState + Route + MainActivity | mittel | VM-Test (Fake CacheStore) | Cache-Stats aus VM; EPG-global über VM |
| **P1-04f3** | EPG-Quellen + Provider-EPG-Links + Manual-Mapping ins VM (Flow-Collection + CRUD). | VM + EpgSettingsPanel/EpgSourceEditor/ManualEpgMappingPanel + Route | hoch | VM-Test (Fake EpgSourceRepository/EpgRepository) | Keine direkte Repo-Collection mehr in EPG-Panels |
| **P1-04f4** | Provider-Liste + AddFlow/Editor-Daten ins VM (Provider CRUD). Connection-Test/SAF bleiben App-Callback. | VM + ProviderSettingsPanel/ProviderAddFlow/ProviderEditor + Route | hoch | VM-Test (Fake ProviderRepository) | Keine direkte Repo-Collection mehr in Provider-Panels |
| **P1-04f5** | Prüfen: Parental/PIN + Backup + About. Voraussichtlich **App-hoisted belassen** (SAF/Keystore/Context) — nur dokumentieren, ggf. minimalen State ins VM. | ggf. VM + Route | hoch | — | Entscheidung dokumentiert |

Jeder Schritt ist einzeln buildbar (`:feature:settings:compileDebugKotlin` + `:feature:settings:testDebugUnitTest`
+ `:app:assembleDebug`) und ändert wenige Dateien.

## 6. Erster empfohlener Umsetzungsschritt

**P1-04f1** wie oben: der kleinste sinnvolle Schnitt sind die **reinen DataStore-Bereiche General +
Appearance + Playback + Selected-Section**. Sie sind pure Preference↔State-Mappings ohne Repo/Context/SAF,
haben bereits Panel-`androidTest`s (`SettingsGeneralPanelTest`, `SettingsPlaybackPanelTest`), und entfernen
~15 Parameter aus `SettingsRoute`. Die zwei App-Seiteneffekte (`setBackgroundRefreshEnabled`, `recreate()`)
bleiben als schmale Callbacks an der Route — der DataStore-Write wandert ins VM, der System-Effekt bleibt App.

## 7. Was vorerst App-hoisted bleiben sollte

- SAF/ActivityResult: `onPickM3uFile`, alle Backup-Export/Import, `onExportDiagnostics`.
- Security: PIN-Set/Change/Disable, `onProtectionChanged`, `pinSecurityStateStore`, `unlockedProtectionAreas`,
  Protection-Unlock-Dialog (App-Nav-State), `syncWatchNext`.
- Context: `aboutAppState`, `copySupportInformation`, `context.getString`-basierte PIN-Fehlermeldungen.
- Activity: `LocaleHelper` + `activity.recreate()`.
- Netzwerk-UseCase: `onTestProviderConnection` (Kandidat für separates P2-07 UseCase, nicht Teil von P1-04f).

## 8. Offene Entscheidungspunkte

1. `feature:settings` → `:core:datastore` als neue Modulabhängigkeit hinzufügen? (Empfehlung: **ja**, nötig
   und sauber; bestehendes Modul.)
2. `AboutSettingsState`: App-hoisted lassen oder via nicht-Context-Provider ins VM? (Empfehlung: **App-hoisted
   lassen** — Context-gebunden, geringer Nutzen.)
3. Parental/PIN: überhaupt ins VM oder dauerhaft App-hoisted? (Empfehlung: **App-hoisted** bis Security-/
   Dialog-Konzept separat betrachtet wird.)
4. Provider-Connection-Test als UseCase (P2-07) vor oder nach f4? (Empfehlung: unabhängig; Callback vorerst
   behalten.)
5. Ein UiState-Objekt vs. Teil-`StateFlow`s pro Bereich? (Empfehlung: **ein `SettingsUiState`**, inkrementell.)

## 9. Git-Status

`git status --short`: **clean** (keine offenen Änderungen; P0-01…P1-04e committed). Dieser Lauf erzeugt nur
diese eine neue Datei unter `docs/`.
