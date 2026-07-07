# Plan: EPG „Add source" als Infill + Button-/Refresh-Feinschliff

Status: umgesetzt (Build grün, detekt grün, auf Emulator installiert)
Bereich: `feature/settings` (Einstellungen → EPG)
Referenzmuster: `ProviderSettingsPanel` (Playlists) — 1:1 gespiegelt, kein neues Modul/keine neue Architektur.

## Problem

1. **Editor erscheint nicht.** `EpgSettingsPanel` rendert `EpgGlobalSettings` + Pills + ein
   `Row(Modifier.fillMaxSize())` (Liste ‖ Editor) in einer **nicht-scrollenden** `Column`. Das letzte
   `Row` fordert per `fillMaxSize()` die volle Panelhöhe, sitzt aber unter ~450dp Inhalt →
   wird unter den sichtbaren Rand geschoben. Editor/Liste/Manual-Mapping rendern off-screen.
2. **Button-Stil inkonsistent.** „Add EPG source" ist ein `ActionPill`; „Add playlist" ist ein
   voll­breiter `VivicastSettingsRow`.
3. **Refresh-Button überflüssig ohne Quellen.** „EPG jetzt aktualisieren" (`settings_epg_now`) wird
   immer gezeigt, auch wenn keine EPG-Quelle existiert (nichts zu refreshen).

## Zielverhalten

- Klick auf „EPG Quelle hinzufügen" → voll­breiter Editor **ersetzt** die Übersicht (Infill-Swap),
  wie „Add playlist".
- Add-Button im Zeilen-Stil von „Add playlist" (`VivicastSettingsRow`, Wert „Öffnen ›").
- „EPG jetzt aktualisieren" nur sichtbar wenn ≥1 EPG-Quelle existiert (Muster wie
  „Refresh all playlists", `ProviderSettingsPanel.kt:479`).

## Änderungen

### 1. `EpgSettingsPanel.kt` — Full-Swap statt Nebeneinander
Ersetze die `if (showManualMapping) … else Row{ Liste ‖ (Editor|Info) }`-Struktur durch:
```
when {
  showEditor        -> { BackHandler(dismiss); EpgSourceEditor(Modifier.fillMaxSize()) }
  showManualMapping -> ManualEpgMappingPanel(Modifier.fillMaxSize())
  else              -> Overview(LazyColumn: EpgGlobalSettings, Add-Zeile, Manual-Zeile, EpgSourceList)
}
```
- Editor/Manual/Übersicht schließen sich gegenseitig aus → jeweils volle Breite, kein Overflow.
- Übersicht als scrollbare `LazyColumn` (mehrere Items), damit nichts off-screen fällt.

### 2. Add-Button → Zeile
- „EPG Quelle hinzufügen" von `ActionPill` auf `VivicastSettingsRow` (title `settings_epg_add_source`,
  value `about_open_value` = „Öffnen", help = neuer String `settings_epg_help_add_source`).
- „Manuelle Zuordnung" zur Konsistenz ebenfalls als `VivicastSettingsRow` in dieselbe Liste
  (title `settings_epg_manual_mapping`, help `settings_epg_manual_mapping_body`).
  *(Nicht explizit gefordert — nur damit Übersicht nicht Zeile+Pill mischt. Bei Veto: Pill lassen.)*

### 3. „EPG jetzt aktualisieren" ausblenden ohne Quellen
- Diese Zeile lebt in `EpgGlobalSettings`. Neuer Param `canRefreshNow: Boolean` (= `sources.isNotEmpty()`),
  Zeile nur rendern wenn `true`. Spiegelt das bestehende Playlists-Muster.

### 4. Fokus-Handling (wie Playlists)
- `onParkFocusBeforeEditor` (Fokus auf EPG-Section-Button parken vor Swap → kein Flucht-Fokus in
  Top-Nav/Home). Aus `SettingsRoute` durchreichen (analog `sectionPlaylists`).
- `pendingOverviewFocus` (Add-Zeile bzw. bearbeitete Quelle) für Rückkehr-Fokus.
- `BackHandler(dismissEditor)` zum Schließen des Editors.

### 5. Strings
- Neu: `settings_epg_help_add_source` (DE) in `core/designsystem` strings.xml (dort liegen die
  EPG-Panel-Strings, Panels nutzen `core.designsystem.R`).

## Unverändert / außerhalb Scope
- Save/Validierung/Provider-Zuweisung, `SettingsViewModel`-CRUD, `EpgSourceRepository`,
  XMLTV-Import/`RefreshOrchestrator`.
- Keine Repo-Flows/CRUD in Composables (Save läuft über bestehende `onSave…`-Callbacks).
- Keine neuen Module, keine DI-Änderung.

## Nachtrag: Editor-Inhalt + Connection-Test (2. Iteration)

Am `EpgSourceEditor` (Add/Edit-Formular) umgesetzt:
1. Oberes Info-Panel („URL is stored securely…") entfernt.
2. Name-Feld wie Playlist: Placeholder `settings_provider_name_placeholder`, `maxLength=25`,
   Leer→Feld rot (nach Save-Versuch), Duplikat→rot + `settings_provider_name_exists_body`,
   Save blockiert bei leer/Duplikat. Duplikat-Flag aus `EpgSettingsPanel` (`sources`).
3. Connection-Test neben URL-Feld (`ConnectionTestButton`, jetzt `internal`): fetch + XMLTV-parse.
   Erfolg = erreichbar + gültiges XMLTV mit ≥1 Kanal → grün „%d Kanäle · %d Sendungen"
   (`settings_epg_test_summary`); sonst rote Meldung.
   - Neuer `TestEpgSourceConnectionUseCase` in `data:epg` (fetchText-Abstraktion + `XmltvParser`,
     Default `DefaultXmltvParser`). Wirft; App foldet zu `EpgConnectionTestResult` + DE-Meldung
     (`AppContainer.testEpgSourceConnection` / `toEpgConnectionMessage`).
   - Wiring: MainActivity → SettingsRoute (`onTestEpgConnection`) → EpgSettingsPanel (Status/Summary/
     Error-State) → EpgSourceEditor. `OkHttpTextFetcher(okHttpClient)` (globaler User-Agent + Debug-TLS).
   - Unit-Test: `TestEpgSourceConnectionUseCaseTest` (valid/blank/empty).
4. Active + Save unten horizontal zentriert.

## Nachtrag 3: Buttons, Fokus, Test-Text (3. Iteration)

- **Fokus:** Editor öffnet mit Fokus auf dem Namensfeld (`firstFocus`-Requester +
  `LaunchedEffect{ awaitFrame(); requestFocus() }`), wie der Provider-Editor.
- **Buttons:** „Active"-Pill entfernt → Button-Reihe jetzt **Abbrechen + Speichern** (+ Löschen im
  Edit-Modus), zentriert. `onCancel` = `dismissEditor`. Der Aktiv-Schalter ist jetzt eine **Zeile oben,
  nur im Edit-Modus** (`VivicastSettingsRow`, `settings_provider_active_label`), wie beim Provider; neue
  Quelle ist per Default aktiv.
- **Test-Ergebnis-Text:** von „%d Kanäle · %d Sendungen" (grün) geändert zu **„Found EPG for %d channels"**
  / „EPG für %d Kanäle gefunden" (nur Kanäle), in **Default-`BodyText`-Farbe** (wie Playlist-Summary
  „Found in this playlist"), nicht mehr grün.

> **Hinweis:** Der Connection-Test-Fetch/Parse aus Nachtrag 2 (`fetchText` + `OkHttpTextFetcher` +
> DOM `XmltvParser.parse`) wurde später auf **Streaming** umgestellt — siehe
> [`EPG-streaming-import.md`](EPG-streaming-import.md). Aktuell: `EpgStreamSource` +
> `OkHttpEpgStreamSource` + `parseStreaming` (SAX, gzip, 200-MB-Cap); Meldung „zu groß"
> mappt jetzt `EpgSourceTooLargeException`.

## Validierung
- `.\gradlew.bat :app:compileDebugKotlin :feature:settings:compileDebugKotlin`
- `.\gradlew.bat detekt`
- Install auf Emulator, EPG-Bereich prüfen: Add öffnet Editor (voll­breit), Back schließt,
  Refresh-Now weg wenn keine Quelle, erscheint nach dem Anlegen einer Quelle.
