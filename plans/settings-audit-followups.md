# Plan: Audit-Follow-ups — Allgemein / Playlist / EPG

## Umsetzungsstatus (autonomer Lauf)

**✅ PLAN ABGESCHLOSSEN** — alle Phasen umgesetzt und verifiziert (detekt + assembleDebug + Unit/Instrumented-Tests grün, auf `main` gepusht). Der **unused-import-Sweep wird bewusst NICHT** im Rahmen dieses Plans gemacht: detekt `UnusedImports` ist AUS (kein Gate-Effekt), Nutzen gering. Falls überhaupt, dann irgendwann als eigener, separater Plan.

- **Phase 1 — ✅ erledigt** (commit `fix: settings audit phase 1`): R4, R6, E2, S1, S2, E3-Cleanup. Tests grün.
- **Phase 2 — ✅ erledigt** (`phase 2 — unify the refresh interval clock`): `Provider.lastRefreshAt` (Migration v13), R1/R2 (persistierte Uhr + Lazy read), R3 (Periodic-Phase via initialDelay), R5 (Doppel-EPG-Kaltstart). Migration real + androidTest verifiziert.
- **Phase 3 — ✅ erledigt** (`phase 3 — editor save/test policy`): E1 (nur testen wenn Source geändert), E4 (Prefill-Fail blockiert nicht). U1 war bereits ok (Save triggert Refresh). Tests grün.
- **Phase 4 — ✅ Kern erledigt** (`phase 4a` D1, `phase 4b` Dedup+Rename, + M2/PROVIDER_REFRESH_WORK): D1-Entfernung, `SettingsEditorHelpers` (duplicate-name + U2 URL-Normalisierung), `Maintenance*`-Rename, tote runMaintenanceRefresh-Branches. detekt-Baseline regeneriert.
- **Phase 4 — Rest ✅ erledigt** (Cleanup-Runde): `ProviderEditor.kt`-Split (1327→943 Z.: neue `ProviderEditorState.kt` mit State+Validation+Fokus-Helpern) + `ConnectionTestButton` → `SettingsInputFields.kt`; toter `connectionTestPassed`/`requireConnectionTest`-Gate raus (+ `validation_connection_test_required`-String); tote Standalone-Worker `enqueueLogoRefresh`/`enqueueCacheCleanup` + `LogoRefreshWorker`/`CacheCleanupWorker` + `runLogoRefresh`/`runCacheCleanup` + `LOGO_REFRESH_WORK`/`CACHE_CLEANUP_WORK` raus (Maintenance-Orchestrator macht Logos+Cache weiter); tote `collectDuePlaylists`/`collectAllActiveSources` + `PlaylistRefreshSource`/`EpgSourceResolver`/`ActiveProviderPlaylistSource` raus; tote D1-EPG-Strings raus (assignment/priority, DE+EN). detekt + assembleDebug + test grün. (unused-import-Sweep bewusst ausgeklammert — siehe Banner oben.)
- **Phase 5 / Logo-Priorität — ✅ erledigt** (Feature-Runde, ohne Migration): `logoPriority` ist jetzt echt genutzt (vorher tote Spalte). Pro-Playlist-Editor-Row + Popup mit 2 Optionen (Playlist bevorzugen = Default | EPG bevorzugen), Werte `"playlist"`/`"epg"` (Legacy `"provider"` → playlist normalisiert). Die bisher **verwaiste `epg_channels`-Tabelle** wird jetzt beim EPG-Import mit den Kanal-`<icon>`s befüllt (replace pro Quelle) und über `epg_channel_mappings` gejoint. Effektives Logo wird **read-time** im `CatalogDao`-Query berechnet (CASE nach `logoPriority`, Fallback jeweils andere Quelle) → reaktiv, keine Materialisierung, keine Migration. Verifiziert: assembleDebug + detekt + Unit-Suite + Instrumented-Test (`RoomMediaRepositoryTest#effectiveChannelLogoFollowsProviderLogoPriority`) grün auf Emulator. Umfang bewusst minimal (kein Global-Default, kein lokaler Ordner) — weicht damit vom PRD (4 Optionen) ab, per Nutzerentscheidung.
- **Verwaiste EPG-Strings — ✅ entfernt** (DE+EN): `settings_epg_panel_title`, `_program_count`, `_retention_day`, `_retention_days`, `_source_edit_title`, `_source_new_title`, `_source_edit_body`, `_source_new_body`.

Nichts mehr offen in diesem Plan. `epg_channels` ist **kein** Removal-Kandidat mehr (jetzt in Nutzung). Einziger nicht gemachter Punkt — der unused-import-Sweep — ist bewusst ausgeklammert (eigener Plan, falls je nötig).

---

Status: **abgeschlossen** (alle Phasen umgesetzt; die folgenden Planungsnotizen sind historisch)
Bereich: `feature/settings`, `worker`, `app/MainActivity`, `data/provider`, `data/epg`, `data/media`, `core/database`, `core/datastore`, `feature/live-tv`
Grundlage: 4 Read-only-Audits (Refresh-Orchestrierung, Settings-Verdrahtung, Editor-Flows, Code-Struktur).

## Gesperrte Entscheidungen
- **D1:** EPG-Provider-Assignment-Code wird **entfernt** (Zuweisung läuft über Playlist-Editor „EPG Quellen"-Popup, das reicht). `linkSourceToProvider`/`unlinkSourceFromProvider` bleiben (dort genutzt).
- **D2:** EPG-URL-Pflicht-bei-Edit bleibt **asymmetrisch** zu M3U (M3U: leer = behalten). Nicht angleichen. Nur den toten `validationMessage`-Edit-Pfad aufräumen.

## Architektur- & Gate-Regeln (verbindlich für alle Phasen)
Aus `docs/DETEKT-GATE.md`, `docs/SETTINGS-APP-HOISTED-DECISIONS.md`, `docs/ARCHITECTURE-REMEDIATION-COMPLETION-REPORT.md` + CLAUDE.md. Gelten für **jeden** Task hier:

- **detekt-Schwellen** (nicht lockern): `LongMethod` 150, `CyclomaticComplexMethod` 20, `LargeClass` 600, `NestedBlockDepth` 6, `ComplexCondition` 5, `TooManyFunctions` 40/Datei · 30/Klasse, `LongParameterList` 10 (`@Composable` + Default-Params zählen nicht). Neue Verletzung: **erst fixen** (Datei/Methode splitten) statt baselinen; baselinen nur mit Begründung im Commit. → betrifft direkt Phase-1/3-Signaturänderungen an bereits gebaselineten Composables (`EpgSourceEditor`, `ProviderEditor`, `ProviderSettingsPanel`, `EpgSettingsPanel`, `SettingsViewModel` = `TooManyFunctions`) und den Phase-4-Split.
- **Baseline-Hygiene:** nach Phase-4-Splits/Extraktionen Baseline-Einträge, die nicht mehr feuern, **entfernen** (nicht stehen lassen). `detektBaseline` regeneriert die **ganze** Baseline — Diff prüfen, keinen stillen Drift übernehmen; sonst chirurgisch. Nach D1 (Phase 4) prüfen, ob `EpgSourceEditor` unter `LongParameterList` fällt (7 Params weniger) → Eintrag ggf. löschen.
- **ViewModel + immutable UiState + StateFlow** für neue/geänderte Datenlogik; **keine** Repository-Flows/CRUD direkt in Composables. → **S2** gehört in `LiveTvViewModel`/`LiveTvUiState` (nicht in die Composable); **E2/S1** in `:data`/DAO; **E2** Repo-Check in `:data:epg`.
- **App-hoisted bleibt App-hoisted:** WorkManager-Scheduler (`enqueue*`/`setMaintenance*`), Connection-Test (Netzwerk), Navigation, SAF, Keystore/PIN. → Phase-2-Refresh-Orchestrierung bleibt in `MainActivity`; **E1**-Test-Entscheidung bleibt App-seitig; Phase-4-Rename hält den Scheduler App-hoisted. `onProviderSaved`/`onBackgroundRefreshChanged` bleiben reine Scheduler-Seiteneffekte nach VM-Save.
- **Injizierbare Clock + Dispatcher:** Zeitlogik über injizierbare Clock; **kein** hartkodiertes `withContext(Dispatchers.IO)` in `worker`/`data` (nur Default-Param `ioDispatcher = Dispatchers.IO`). → Phase 2: `Provider.lastRefreshAt`-Schreiben in `:data:provider` über die vorhandene `clock()` (wie `RoomEpgRepository`); `isRefreshDue` bleibt **pure** + unit-getestet (Fälligkeit nicht in die App-Composable verlagern).
- **Doc-Sync:** Phase-4-Rename (`Global*`→`Maintenance*`) und D1-Entfernung müssen die aktiven Docs aktuell halten — `SETTINGS-APP-HOISTED-DECISIONS.md` (Z. 45/50: `enqueueGlobalRefresh`/`onBackgroundRefreshChanged`/`onProviderSaved`) und Completion-Report (Z. 76 „Global Refresh…"). Beim Umbenennen prüfen, ob `enqueueGlobalRefresh`/`onRunGlobalRefresh` überhaupt noch existiert (Dead-Code-Check Phase 4).

## Reihenfolge
Phase 1 (klare Bugs, klein) → Phase 2 (Refresh-Herz) → Phase 3 (Editor-Politik) → Phase 4 (Cleanup/Refactor) → Phase 5 (später). Nach jeder Phase: `detekt` + `assembleDebug` + relevante Tests grün, dann Emulator-Check. detekt-Baseline bei Signatur-Änderungen nachziehen (kein Wachstum).

Test-Quellen (für Emulator, nicht im Code): 2 M3U + 3 EPG-URLs liegen im Chat-Verlauf.

---

## Phase 1 — Schnelle klare Fixes (geringes Risiko)

### 1.1 R4 — Guard-Contention: Retry → Skip
- **Problem:** Wenn `RefreshRunGuard` nicht eintreten kann (Item läuft schon in-process), gibt der Refresher `success=false` zurück (`RefreshExecution.kt:154-156`, `:345-347`) → Runner mappt auf **Retry** (`:85` playlist, `:105` epg) → WorkManager lädt kurz danach die ganze Playlist / das ganze XMLTV nochmal.
- **Dateien/Funktionen:** `worker/RefreshExecution.kt` — `DefaultPlaylistRefresher.refresh`, `DefaultEpgRefresher.refresh`, `PlaylistRefreshOutcome`/`EpgRefreshOutcome`, `runPlaylistRefresh`, `runEpgRefresh`.
- **Ansatz:** „bereits am Laufen" (guard busy) von „echt fehlgeschlagen" unterscheiden — z. B. Outcome um `skipped: Boolean` erweitern (oder eigenen Rückgabewert). Runner: `skipped → Success` (kein Retry), `failed → Retry`.
- **Verifikation:** Unit-Test: zwei parallele Refreshes desselben Providers → zweiter gibt Success (kein Retry) zurück, kein zweiter Import.

### 1.2 R6 — Hängender `Refreshing`-Status blockiert manuellen „Refresh all"
- **Problem:** Abbruch (u. a. VG-Eintritt cancelt laufende Periodic) lässt Provider auf `Refreshing` (`RefreshExecution.kt:173-179`, bewusst kein Reset). Manueller „Refresh all"-Button early-returned, wenn **irgendein** Provider `Refreshing` (`ProviderSettingsPanel.kt:217`, EPG analog `EpgSettingsPanel.kt:440`) → Button dauerhaft no-op bis Selbstheilung.
- **Dateien/Funktionen:** `worker/RefreshExecution.kt` (Cancel-Pfad), `feature/settings/ProviderSettingsPanel.kt` (onRefreshAll-Guard), `EpgSettingsPanel.kt` (analog).
- **Ansatz (Entscheidung beim Umsetzen):** entweder (a) Status bei Abbruch/Superseded auf vorherigen zurücksetzen, **oder** (b) manuellen Button nicht hart auf `Refreshing` blockieren (One-Shots sind KEEP → Re-Trigger ist safe/coalesced). Tendenz: (b) + zusätzlich Status-Reset bei Cancel, damit die Badge nicht falsch „Refreshing" zeigt.
- **Verifikation:** Provider künstlich in `Refreshing` lassen → „Refresh all" muss trotzdem auslösen; Badge korrekt.

### 1.3 E2 — EPG-Duplikat-Name: Repo-Backstop
- **Problem:** EPG-Duplikat-Name nur in der Composable geprüft (`EpgSettingsPanel.kt:303-308`); `RoomEpgRepository.saveEpgSource` (`:128`) verlangt nur non-blank. Playlist hat Repo-Check (`RoomProviderRepository.kt:72,110`).
- **Dateien/Funktionen:** `data/epg/RoomEpgRepository.kt` — `saveEpgSource` (bzw. `SecureEpgSourceRepository.saveSource`).
- **Ansatz:** vor Upsert case-insensitive Namens-Uniqueness prüfen (eigene ID ausschließen), sonst `IllegalArgumentException`/Result-Fehler wie Provider.
- **Verifikation:** androidTest `RoomEpgRepositoryTest`: zweite Source mit gleichem Namen → Fehler.

### 1.4 S1 — EPG-Source `isActive` im Anzeige-Pfad respektieren
- **Problem:** `EpgDao.observeProgramsForChannel` (`EpgDao.kt:52-67`) filtert nur provider+channel, nicht `epg_sources.isActive`. Deaktivierte Quelle: Programme bleiben sichtbar bis Retention/Löschen.
- **Dateien/Funktionen:** `core/database/dao/EpgDao.kt` — Programm-Query(s) um `isActive`-Join/Filter erweitern; ggf. `data/epg/RoomEpgRepository` Mapping.
- **Ansatz:** Query joint `epg_sources` und filtert `isActive = 1` (oder Repository filtert Programme aktiver Quellen). Achtung: EPG-Priorität/Auswahl pro Kanal nicht brechen.
- **Verifikation:** androidTest: Source deaktivieren → deren Programme verschwinden aus `observeProgramsForChannel`.

### 1.5 S2 — Provider `isActive` in Live-TV-Liste respektieren
- **Problem:** `LiveTvViewModel.kt:268` liefert ungefiltertes `providersRaw`; `CatalogDao.observeChannels`/`observeChannelsPage` (`:33,:43`) ohne `isActive`/`status`-Gate → deaktivierte Playlist bleibt browsebar. Refresh/WatchNext/Resume respektieren es bereits.
- **Dateien/Funktionen:** `feature/live-tv/LiveTvViewModel.kt` (Provider-Filter), evtl. `core/database/dao/CatalogDao.kt`.
- **Ansatz:** im VM nur aktive/nicht-`Disabled` Provider exponieren (kleinster Eingriff). Prüfen ob Movies/Series-Listen dasselbe Gate brauchen (Konsistenz).
- **Verifikation:** Emulator: Playlist deaktivieren → verschwindet aus Live-TV.

### 1.6 E3-Cleanup — toten `validationMessage`-Edit-Pfad aufräumen
- **Problem:** `EpgSourceEditorState.validationMessage` (`EpgSourceEditor.kt:397`) URL-Regel nur `if (!isEditing)`; der Save-Button-Vorcheck (`:288`, `urlBlank` beide Modi) läuft zuerst → validationMessage-URL-Edit-Pfad ist tot/widersprüchlich.
- **Dateien/Funktionen:** `feature/settings/EpgSourceEditor.kt` — `validationMessage`.
- **Ansatz (D2: asymmetrisch lassen):** `validationMessage` konsistent zum Button machen (URL in **beiden** Modi Pflicht) — reine Aufräumung, kein Verhaltenswechsel.
- **Verifikation:** compile; Verhalten unverändert (Button-Gate bleibt maßgeblich).

---

## Phase 2 — Refresh-Modell härten (gemeinsame Wurzel)

**Wurzel:** zwei nicht-synchronisierte Last-Refresh-Uhren — In-Memory-`foregroundRefreshedAt` (`MainActivity.kt:422`) vs WorkManager-Periodics; `Provider` hat kein persistiertes `lastRefreshAt`.

### 2.1 `Provider.lastRefreshAt` einführen (persistierte Uhr)
- **Dateien:** `domain/VivicastModels.kt` (Provider +`lastRefreshAt: Long?`), `core/database/model/VivicastEntities.kt` (ProviderEntity), **Migration v12→v13** in `VivicastMigrations.kt` + `VivicastDatabase.VERSION` + Schema-JSON `13.json` + `VivicastDatabaseMigrationTest` (Schritt), `data/provider/RoomProviderRepository.kt` (toDomain + Schreiben on refresh), Backup (`StandardBackup*`) round-trip.
- **Schreiben:** beim erfolgreichen Playlist-Refresh `lastRefreshAt = now` setzen (analog EPG `markEpgSourceRefreshed`). Ort: `RefreshExecution.kt` Playlist-Refresher / `RoomProviderRepository`.

### 2.2 R1 + R2 — eine Quelle der Wahrheit, Lazy-Seeding
- **Problem R1:** Vordergrund-Loop erster Tick läuft vor Seeding → interval>0-Items refreshen bei jedem Kaltstart, `refreshOnAppStart=false` ignoriert. **R2:** Loop kennt Hintergrund-Refreshes nicht.
- **Dateien/Funktionen:** `app/MainActivity.kt` — der `LaunchedEffect(lifecycleOwner)`-Loop + Reconciler + `foregroundRefreshedAt`.
- **Ansatz:** Fälligkeit aus **DB-`lastRefreshAt`** (Provider **und** EPG) statt In-Memory-Map, oder Map beim VG-Eintritt aus DB seeden **und** nach jedem Refresh aktualisieren. **Lazy-Seed:** fehlender/kein `lastRefreshAt` beim ersten Tick ⇒ als „gerade geseedet" behandeln (nicht fällig), damit der Loop `refreshOnAppStart` nicht aushebelt. Kaltstart-Refresh bleibt allein Sache der app-start-One-Shots (die `refreshOnAppStart` respektieren).
- **Design-Entscheidung beim Umsetzen:** Verhalten für „nie refreshte" Items (frische Installation) festlegen — app-start deckt das ab; Loop soll nicht zusätzlich sofort feuern.
- **Verifikation:** Unit-Test `isRefreshDue`-Nutzung; Emulator: Item interval=X + refreshOnAppStart=aus → Kaltstart refresht **nicht**; nach X Vordergrundzeit refresht es.

### 2.3 R3 — Periodic-Phase nicht bei jedem VG/HG-Wechsel zurücksetzen
- **Problem:** VG-Eintritt cancelt Periodics, VG-Austritt enqueued frisch (`MainActivity.kt:747-748`, `:777-790`) → erster Lauf immer ~1 Intervall entfernt → häufiges Öffnen ⇒ Hintergrund-Refresh verhungert.
- **Ansatz:** beim Re-Enqueue `setInitialDelay(max(0, interval − (now − lastRefreshAt)))` verwenden, damit die Phase erhalten bleibt (nutzt 2.1). `RefreshWorkScheduler.enqueuePlaylistPeriodic`/`enqueueEpgPeriodic` um optionalen Initial-Delay erweitern; MainActivity berechnet ihn aus `lastRefreshAt`.
- **Verifikation:** Item interval=6h, mehrfach kurz öffnen → Periodic feuert trotzdem nach ~6h realer Zeit.

### 2.4 R5 — Doppelter EPG-Refresh beim Kaltstart entschärfen
- **Problem:** app-start-EPG (`MainActivity.kt:730-732`) **und** playlist-change-EPG (`RefreshExecution.kt:81-83`) refreshen dieselbe Source; KEEP coalesced nicht mehr, weil der erste oft schon fertig ist.
- **Ansatz (Entscheidung beim Umsetzen):** app-start-EPG nur für Quellen, die **nicht** von einem beim Start refreshten Provider abgedeckt werden (verlinkte Quellen überlässt man playlist-change); **oder** playlist-change-EPG im Kaltstart-Fenster unterdrücken. Tendenz: app-start-EPG auf nicht-verlinkte/standalone Quellen beschränken.
- **Verifikation:** Kaltstart mit Default-Config → jede verlinkte Source genau **einmal** refresht.

### 2.5 R7 — Semantik-Notiz (kein Code)
Master-Switch off + sofortiges Backgrounden lässt app-start-One-Shots im Hintergrund laufen (by-design). Nur dokumentieren, kein Fix — außer du willst app-start bei Switch-off auch unterdrücken (dann Entscheidung).

---

## Phase 3 — Editor-Politik

### 3.1 E1 — Provider-Save nur testen, wenn Source geändert
- **Problem:** `ProviderSettingsPanel.kt:362` testet bei **jedem** Save; Fehler-Force-Save setzt `isActive=false` (`:444`) → Offline-Blip bei Metadaten-Edit deaktiviert funktionierende Playlist.
- **Dateien/Funktionen:** `feature/settings/ProviderSettingsPanel.kt` (onSave), `ProviderEditorState` (Source-Dirty-Erkennung).
- **Ansatz:** „Source dirty" tracken (URL/Xtream-Creds/File geändert vs. gespeichert). Test nur wenn dirty; reine Namens-/Intervall-/Toggle-Edits speichern ohne Test.
- **Verifikation:** Emulator (mit echter Playlist): Name ändern offline → speichert ohne Test/Deaktivierung.

### 3.2 E4 — Fehlgeschlagener Prefill blockiert nicht Save
- **Problem:** async URL/Cred-Prefill schlägt fehl → `editor.url=""` → „URL missing" für eine Quelle mit gespeicherter URL (`EpgSettingsPanel.kt:486-491`, `ProviderSettingsPanel.kt:246-249`).
- **Ansatz:** „prefill fehlgeschlagen/lädt" von „user hat geleert" unterscheiden; bei fehlgeschlagenem Prefill leeres Feld nicht als Pflicht-Verletzung werten (bzw. Hinweis „konnte nicht laden").
- **Verifikation:** Prefill-Fehler simulieren → Save nicht fälschlich blockiert.

### 3.3 U1 — `timeShiftMinutes` wirkt sofort
- **Problem:** timeShift wird beim Import in Programmzeiten eingebacken (`RoomEpgRepository.kt:181,323-324`); Ändern ohne Refresh scheinbar wirkungslos.
- **Prüfen zuerst:** EPG-Save triggert bereits `onRefreshEpgSource` (aus früherer Arbeit) → timeShift würde beim Save-Refresh neu importiert. Falls ja: nur bestätigen/dokumentieren. Falls nein: Save bei geändertem timeShift einen Refresh auslösen lassen.
- **Verifikation:** timeShift ändern + speichern → Programmzeiten verschieben sich nach Refresh.

### 3.4 U2 — M3U-Duplikat-URL normalisieren (wie EPG)
- **Problem:** M3U vergleicht exakt (`ProviderSettingsPanel.kt:191`); EPG normalisiert `.gz/.xz` (`EpgSettingsPanel.kt:594-595`).
- **Ansatz:** gemeinsame Normalisierung (Trim/Case/Suffix) — passt gut zum Duplikat-Helper aus Phase 4.
- **Verifikation:** gleiche URL mit `/` bzw. `.gz` → als Duplikat erkannt.

---

## Phase 4 — Cleanup / Refactor (geringes Risiko, viel Aufräumen)

### 4.1 D1 — EPG-Provider-Assignment-Plumbing entfernen
- **Entfernen:** `EpgSourceEditor.kt:99-110` (Params `providers, selectedProviderId, providerLinks, onSelectProvider, onLinkProvider, onUnlinkProvider, onMoveProviderLink`), `EpgSettingsPanel.kt:116-118` Params + `:276-302` launch-Blöcke + zugehörige Strings (`:144-147,152-154`), `SettingsRoute.kt:357-359` Bindings, `SettingsViewModel.moveEpgSourcePriority` (`:243-248`), `SecureEpgSourceRepository.moveSourcePriority` (`:106-130`), `EpgSourcePriorityDirection`, verwaiste `settings_epg_*`-Strings (priority/higher/lower/top/bottom/not_assigned/assignment_*).
- **Behalten:** `linkSourceToProvider`/`unlinkSourceFromProvider` (ProviderEditor „EPG Quellen"-Popup nutzt sie: `ProviderEditor.kt:293,349` → `SettingsRoute.kt:326-334`).
- **Verifikation:** compile + detekt; ProviderEditor-EPG-Popup funktioniert weiter.

### 4.2 Toter Code raus
- `DEFAULT_REFRESH_INTERVAL_HOURS`-Imports (~13 Dateien) — Konstante ggf. behalten (1 Playback-Test), Imports weg.
- `WorkerContracts.PROVIDER_REFRESH_WORK` (`:6`).
- `ProviderTextField` Params `trailingActionLabel`/`onTrailingAction` (`SettingsInputFields.kt:111-112,132-133`). `label` behalten (dokumentiert).
- `connectionTestPassed` + `requireConnectionTest`-Gate (`ProviderEditor.kt:922` + ~10 `connectionTestPassed=false`-Copies + Panel) — nach E1 neu bewerten (Test-Flag evtl. wieder relevant für „nur wenn dirty"?). **Reihenfolge: E1 zuerst, dann entscheiden.**
- `RefreshReport`-Felder (immer 0/leer) + tote Branches in `runGlobalRefresh` (`RefreshExecution.kt:56-64`); `enqueueLogoRefresh`/`enqueueCacheCleanup` (keine Caller); `collectDuePlaylists`/`collectAllActiveSources` (test-only); `firstFocus`/`isError` im Edit-Name (`NameEditDialog.kt:47-55`, Fokus-Bindings `ProviderEditor.kt:149`, `EpgSourceEditor.kt:145`).
- Unused imports überall (`ProviderEditor`, `EpgSourceEditor`, `Provider/EpgSettingsPanel`, `SettingsInputFields`, `EpgGlobalSettingsPanel`, `GeneralSettingsPanel`, `ProviderDialogs`).

### 4.3 Duplikate zusammenfassen → neue `SettingsEditorHelpers.kt`
- Duplikat-Name-Prädikat (4×: `ProviderSettingsPanel.kt:171-174,303-305`, `EpgSettingsPanel.kt:303-308`) → `duplicateNameOf(...)`.
- Duplikat-URL-Normalisierung (M3U+EPG, siehe U2).
- `resetConnectionTest` (EPG hat Helper `EpgSettingsPanel.kt:181-185`; Provider inline ~5× `ProviderSettingsPanel.kt:208-213,240-243,267,315-316`).
- `neighborIdAfterDelete(list, deletedId, id)` (`ProviderSettingsPanel.kt:412-416` == `EpgSettingsPanel.kt:511-514`).
- UA-Dialog: `ProviderUserAgentDialog` (`ProviderEditor.kt:795-838`) == `UserAgentDialog` (`GeneralSettingsPanel.kt:198-246`) → ein `UserAgentEditDialog(hintRes, testTags?)`.
- Delete-Dialog: `DeleteProviderDialog` (`ProviderDialogs.kt:100-130`) == `DeleteEpgSourceDialog` (`EpgSourceEditor.kt:347-377`) → ein `DeleteConfirmDialog(...)` im Dialogs-Host.

### 4.4 Naming an C1-Modell angleichen
- `RefreshWorkScheduler.setBackgroundRefreshEnabled` → `setMaintenancePeriodicEnabled` (steuert nur noch Logos+Cache-Periodic). `GlobalRefreshOrchestrator`/`runGlobalRefresh`/`GlobalRefreshWorker` → `Maintenance*`. `MIN_GLOBAL_REFRESH_INTERVAL_HOURS` → `MIN_PERIODIC_REFRESH_INTERVAL_HOURS`.
- **Wichtig:** nur **Kotlin-Identifier** umbenennen, **nicht** die Unique-Work-**String-Werte** (`WorkerContracts.GLOBAL_REFRESH_WORK` etc.) — sonst verwaisen bereits geplante Works. String-Konstanten-Werte unverändert lassen.
- DataStore-Pref `backgroundRefreshEnabled` bleibt (gated korrekt VG-Loop + Periodics).

### 4.5 File-Organisation
- `ProviderEditor.kt` (1320 Z.) splitten: `ProviderEditorState.kt` (State + Request-Mapper + Validation `:874-1055`), `ProviderEditorFocus.kt` (Fokus/Error-Helper `:1066-1187`), die 3 Dialoge in `ProviderDialogs.kt`.
- `ConnectionTestButton` (`ProviderEditor.kt:1189-1245`, von beiden Editoren genutzt) → `SettingsInputFields.kt`.

---

## Phase 5 — Später / optional
- **`epg_channels`** tote Tabelle/Entity entfernen (`VivicastEntities.kt:235-242`, `VivicastDatabase.kt:44`, Migration `:60-75`) → **DB-Migration + Schema-Test-Update** nötig → eigene kleine Aufgabe (am besten mit 2.1-Migration bündeln).
- **`logoPriority`** (`RoomProviderRepository.kt:88`, Konstante `"provider"`): entweder echt nutzbar machen (Editor-Option + Logo-Auswahl) oder ganz entfernen. Produktentscheidung.
- **M3U-File-Content** nach Kaltstart leer (RAM-only): Doku-Notiz oder persistente Ablage — by-design, niedrige Prio.

---

## Offene Punkte / Notizen
- detekt-Baseline: bei Signatur-Änderungen (Phase 1/3/4) Einträge chirurgisch nachziehen; bei Datei-Splits/Extraktionen können Einträge **wegfallen** (Funktionen unter Schwelle) — dann entfernen, nicht wachsen lassen.
- Backup-Kompatibilität bei 2.1 (`Provider.lastRefreshAt`) + Phase-5-Migration beachten.
- Nach jeder Phase: `assembleDebug` + `detekt` + betroffene Unit/androidTests + Emulator-Smoke.
