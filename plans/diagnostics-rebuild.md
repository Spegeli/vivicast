# Plan: Diagnose-Bereich neu aufbauen

Status: **Umgesetzt** (Code + Doc-Anpassungen). Gates grün; App startet sauber; DiagnosticsStoreTest 2/2.

## Ausgangslage (Befund)

- `DiagnosticsStore` ist echt (Sessions/Segmente unter `filesDir/vivicast-diagnostics/`, Sanitizing,
  Retention-Prune, ZIP-Export), aber **`record()` wird nur vom Toggle aufgerufen** → das Log enthält
  faktisch nur `event=diagnostics_enabled/disabled`. Sonst nichts.
- Echte Refresh-Events (Playlist/EPG erfolg/fehler, Logo, Cache) landen in `InMemoryRefreshDiagnostics`
  (Worker) — **nur RAM, nie persistiert, nie exportiert, nirgends angezeigt**. Zwei getrennte Systeme.
- Diagnose-Export + Einstellungen-Export nutzen `CreateDocument` (SAF) → **auf TV kaputt** (Stub-Toast,
  wie Backup-Export vorher). Nur "Support-Informationen kopieren" (Clipboard) läuft auf TV.
- **PRD-11 spezifiziert das eigentlich vollständig** (§148: App-Start/Crashes, Import/EPG mit
  Ergebnis+Dauer+Anzahl, Player-Fehler/Retries/Decoder/Timeshift, Netzwerkfehler ohne URL,
  Backup/Cache/DB-Fehler, bereinigte Stacktraces). Toggle default Aus. Retention 1–7. ZIP-Export.
  **Export-only:** Loginhalt darf **nie** in der App angezeigt/kopiert werden — nur Status. Zentrale
  Bereinigung 2× (vor Schreiben + vor Export).

Fazit: Die Funktion ist eine Attrappe gegenüber ihrer eigenen PRD. "Neu aufbauen" = PRD-11 tatsächlich
umsetzen + wenige TV/UX-Abweichungen, die Doc-Änderungen brauchen.

## Zielbild

Toggle **Aus** = nichts wird protokolliert (Privacy-konform, PRD-konform, dein Wunsch). Der Nutzer
schaltet bei Problemen ein → ab dann werden reale technische Ereignisse **bereinigt** protokolliert.
Ergebnis: **eine Datei**, die der Nutzer dir schickt, damit du Crashes/Fehler debuggen kannst. Für den
Nutzer selbst ist der Inhalt irrelevant (wird ihm auch nicht angezeigt — PRD Export-only).

## Was RAUS

UI (Über die App):
- **"Support-Informationen kopieren"** (Clipboard) — entfernen. Auf TV nutzlos (Text in Datei bekommen ist
  zu umständlich). ⚠️ PRD-11 Zeile 37 nennt "Versionsinformationen kopieren" → braucht PRD-Änderung.
- **"Einstellungen exportieren"** als separate Zeile — entfernen; die nicht-geheimen Settings wandern in den
  Diagnose-Export (in `diagnostics-metadata.json`).

Backend:
- `copySupportInformation` / `buildSupportInformation` (Clipboard).
- `supportSettingsExportLauncher` (SAF `CreateDocument`).
- `diagnosticsExportLauncher` (SAF `CreateDocument`) — durch Download-Ordner-Write ersetzen.
- `InMemoryRefreshDiagnostics` als Sackgasse — durch Einspeisung in den `DiagnosticsStore` ersetzen.

## Was REIN / bleibt

UI (Über die App > Diagnose und Support):
- **Diagnoseprotokollierung** (Toggle) — bleibt, default **Aus**.
- **Aufbewahrungsdauer** (1–7 Tage) — bleibt; bei Aus sichtbar aber nicht änderbar (PRD-11 §82).
- **Diagnose exportieren** (EIN Button) → schreibt das ZIP nach `Download/Vivicast/`, zeigt nur den Pfad
  (Status), **keinen Loginhalt** (PRD Export-only).

Backend:
- **Zentraler `DiagnosticsLogger`** (Interface, z. B. in `core/common`; Impl in App über `DiagnosticsStore`),
  der bei **jedem** Log prüft: ist Logging aktiv? → sonst no-op. Sanitizing bleibt in der zentralen Schicht
  (`DiagnosticsStore.sanitize`) + zweite Prüfung beim Export (bereits vorhanden).
- **Reale Ereignisse an den Logger hängen** (PRD-11 §148 + Referenz-Apps):
  - **Crash-Handler** (`Thread.setDefaultUncaughtExceptionHandler`) → bereinigter Stacktrace **+ logcat-Snapshot
    des eigenen Prozesses** (`logcat -d --pid=<pid>`, bereinigt) — der Vorlauf zum Crash (wie AerioTV). Crash
    immer, unabhängig vom Toggle.
  - **Player-Fehler — reichhaltig** (`VivicastPlayerController.handlePlaybackError`): ExoPlayer-Fehlercode/-Typ
    + Format (Codec/Auflösung/Decoder) + zugehöriger **MediaCodec/Decoder-Fehler aus dem logcat** + Klartext-
    Ursache (Code→Ursache-Mapping wie OwnTV: HW-Decoder überlastet, Format nicht unterstützt, ENOMEM, kaputter
    Stream, Audio-Fehler …). Plus Retries/Reconnects/Timeshift.
  - **Playback-Hänger/Rebuffering** (neu): Buffering start/ende, Stall, „kein-Daten"-Hänger — Live-Streams
    hängen oft ohne Exception (wie OwnTV `LiveDiagnosticsLog`).
  - **Verbindungstest-Fehler** (`ProviderConnectionTestResult.errorMessage`, EPG-Test).
  - **Import/Refresh** (RefreshOrchestrator-Events → Store; Import-Repos: Beginn/Ende/Ergebnis/Anzahl/Dauer).
  - **Netzwerkfehler** (Fehlerart, HTTP-Status, Dauer — **ohne URL**).
  - **Backup/Restore/Cache/DB**-Aktionen + Fehler.
  - **Langsame DB / Freeze** (optional gewählt): langsame Room-Queries / lange Main-Thread-Operationen —
    relevant wegen des früheren DB-Starvation-Freezes (wie StreamVault Slow-Query-Logging).
  - **Speicherdruck** (optional gewählt): `onTrimMemory` / Low-Memory-Ereignisse — korreliert OOM/Langsamkeit.
  - **App-Lifecycle-Breadcrumbs** (optional gewählt): Vordergrund/Hintergrund, App-Start/Stop — Kontext vor Crash.
- **Export** → EIN ZIP `vivicast-diagnostics-YYYYMMDD-HHmmss.zip` (`vivicast-diagnostics.log` +
  `diagnostics-metadata.json` inkl. Settings-Sektion) nach `Download/Vivicast/` (MediaStore, wie Backup;
  App-Ordner-Fallback pre-Q), Pfad-Dialog.

## Was wird geloggt / nicht

Geloggt (nur bei aktivem Toggle, außer Crashes): Crashes/Stacktraces + logcat-Snapshot, reichhaltige
Player-Fehler (Code/Format/Decoder/Codec-logcat + Klartext-Ursache), Playback-Hänger/Rebuffering,
Verbindungstest-Fehler, Import-/EPG-/Refresh-Ergebnisse+Fehler, Netzwerkfehler (ohne URL),
Backup/Cache/DB-Aktionen, langsame DB/Freezes, Speicherdruck (`onTrimMemory`), Lifecycle-Breadcrumbs.
**Nie:** URLs, Tokens, Zugangsdaten, Playlist-/Sender-/Film-/Suchinhalte, Passphrase — zentrale Bereinigung
redigiert diese vor dem Schreiben und nochmal vor dem Export; **auch der logcat-Snapshot** läuft durch die
Bereinigung (logcat kann URLs/Secrets von Libs enthalten). PRD-11 zwingend.

## Wann geloggt (ENTSCHIEDEN)

Hybrid — deckt beide Fälle ab:
- **Crashes: IMMER** erfasst, unabhängig vom Toggle. Jeder unbehandelte Absturz → eigene bereinigte
  Crash-Datei. So geht der erste Crash nie verloren, auch wenn der Nutzer das Diagnoseprotokoll nie
  eingeschaltet hat.
- **Alle anderen Events** (Player, Import, EPG, Refresh, Netzwerk, Backup/DB): **nur bei aktivem Toggle**.
  Default Aus → nichts außer evtl. Crashes.

(Crash-Stacktrace ist bereinigt — keine URLs/Zugangsdaten. PRD-11 wird entsprechend angepasst: Crashes
immer in separates Crash-Log, übrige Ereignisse nur bei aktivem Logging.)

## Ablage, Namensgebung & Größenlimits (ENTSCHIEDEN — flaches Modell)

**Kein Session/Segment-Modell mehr.** Stattdessen flache, fortlaufende Log-Dateien — passt für den Fall,
dass der Nutzer die Diagnose dauerhaft anlässt. (Ersetzt PRD-11 §207–213; Doc wird angepasst.)

Ablage — zwei Orte, je nach Erreichbarkeitsbedarf:
- **Diagnose-Logs (nur bei aktivem Toggle) → app-privat** `filesDir/vivicast-diagnostics/logs/` mit Dateien
  `vivicast-YYYYMMDD-HHmmss.log`. Dateiname = **Erstellungszeit**. Neue Datei bei Rotation oder App-Start.
  Fortlaufend, keine Segmente/Sessions. Abruf über den **Export-Button** (funktioniert, solange die App läuft).
- **Crash-Logs (immer, unabhängig vom Toggle) → öffentlich** `Download/Vivicast/Crashes/vivicast-crash-YYYYMMDD-HHmmss.log`,
  **eine Datei pro Crash**. So per Filemanager erreichbar, **auch wenn die App nicht mehr startet** (der
  Crash-Grund liegt dann direkt vor). Bereinigter Stacktrace + Kopf. Geschrieben via MediaStore (wie Backup),
  App-Ordner-Fallback pre-Q.

Warum getrennt: `filesDir` ist App-privat (Filemanager kommt ohne Root nicht ran; `getExternalFilesDir`
`Android/data/…` ist auf API 30+ für Filemanager versteckt). Crashes = kritisches „App-tot"-Artefakt →
müssen öffentlich liegen. Rolling-Logs sind append-lastig → app-seitig (schnell) + Export.

Export (nach `Download/Vivicast/Diagnostics/`):
- **ZIP:** `vivicast-diagnostics-YYYYMMDD-HHmmss.zip` (Erstellungszeit des Archivs). Enthält `logs/` (alle
  app-seitigen Log-Dateien), die vorhandenen Crash-Logs und `diagnostics-metadata.json`.

Ordnerstruktur `Download/Vivicast/` (konsistent, je Typ ein Unterordner):
`Backups/` (`.vcbak`) · `Crashes/` (Auto-Crash-Logs) · `Diagnostics/` (Export-ZIPs).

Größenlimits (verhindern volles Laufen der Platte, auch bei dauerhaft aktivem Toggle):
- **Rotation: 5 MB pro Log-Datei** → dann neue Datei mit neuer Erstellungszeit.
- **Gesamt-Cap 20 MB** über alle app-seitigen Log-Dateien → bei Überschreitung älteste Datei(en) löschen.
- **Retention 1–7 Tage** für die Diagnose-Logs (Prune).
- Crashes (öffentlich): **max 10 Dateien** (älteste raus); je nur wenige KB. Nicht der Retention unterworfen
  (sie sollen erreichbar bleiben, bis der Nutzer sie löscht oder das 10er-Limit greift).
- `diagnostics-metadata.json` kennzeichnet Abschneidungen (`contentTruncated` + Anzahl gelöschter Dateien/
  Ereignisse).

Metadaten (`diagnostics-metadata.json`) — angepasst ans flache Modell: App-/Geräte-/DB-Version, Sprache,
Zeitzone, Exportzeitpunkt, Retention, Größenlimits, abgedeckter Zeitraum (erste/letzte Zeile), `contentTruncated`
+ Verwurf-Zähler, **Liste der enthaltenen Log-Dateien** (Name, erste/letzte Zeit, Bytes, Ereignis-Anzahl),
Liste der Crash-Dateien, **Settings-Sektion** (ersetzt separaten Einstellungs-Export).

## ⚠️ Nötige Doc-Änderungen (dein Go)

- **PRD-11:** "Versionsinformationen/Support kopieren" (Clipboard) entfernen.
- **PRD-11:** Export-Ziel "Android-Systemauswahl (SAF)" → fester `Download/Vivicast/`-Ordner (TV; identisch
  zur Backup-Entscheidung in ADR-004/PRD-10, weil TV keinen Schreib-Picker hat).
- **PRD-11:** Settings-Sektion in `diagnostics-metadata.json` (das "enthält mindestens" erlaubt Erweiterung;
  ersetzt den separaten Einstellungs-Export).
- **PRD-11:** Crash-Regel — Crashes werden **immer** in ein separates Crash-Log geschrieben (unabhängig vom
  Toggle); übrige Ereignisse nur bei aktivem Logging. Crash-Logs werden mitexportiert.
- **PRD-11 §207–213:** Session/Segment-Modell → **flaches Log-Datei-Modell** (Ordner `logs/` mit
  zeitgestempelten, rotierenden Dateien). Metadaten-Spec entsprechend von Session/Segment-Feldern auf
  Log-Datei-Liste umstellen.
- **PRD-11 §209:** "ausschliesslich privater App-Speicher" → **Crash-Logs liegen öffentlich** in
  `Download/Vivicast/Crashes/` (Filemanager-erreichbar, wenn die App nicht mehr startet). Diagnose-Logs
  bleiben app-privat; nur Crashes sind öffentlich. (Bereinigt, keine Secrets.)
- **PRD-10 / ADR-004 (Backup):** Backup-Export-Pfad `Download/Vivicast/` → `Download/Vivicast/Backups/`
  (Unterordner-Struktur). Betrifft `saveBackupToDownloads` + `BackupSavedDialog`-Pfadanzeige.
- Ggf. **ADR-014/PRD-08** (Diagnose-Redaction) — bleibt gültig, nur bestätigen.

## Berührte Module

`app` (MainActivity: Crash-Handler + logcat-Snapshot, Lifecycle-/Memory-Callbacks, `diagnostics/`,
SettingsPreferenceMappers, AboutSettingsPanel, `backup/` bzw. `saveBackupToDownloads` für den Unterordner-
Pfad), `core/player` (reiche Player-Fehler + Rebuffering/Stalls), `core/network` (Netzwerkfehler:
Typ/HTTP-Status/Dauer ohne URL), `core/database` (Slow-Query-Hook), `data/provider`, `data/media`, `data/epg`,
`worker` (Refresh-Events → Store), evtl. `core/common` (Logger-Interface). `core/datastore` (Prefs
`diagnosticsLoggingEnabled`/`retentionDays` existieren bereits).

## Umfang (ein Durchgang, keine Stufen mehr)

Alles zusammen umsetzen:
- Zentraler Logger + Enabled-Gate.
- **Crash-Handler immer aktiv** → per-Crash-Datei (bereinigt, max 10).
- Event-Hooks (nur bei aktivem Toggle): Player-Fehler, Verbindungstest, Import/EPG/Refresh (RefreshOrchestrator
  → Store), Netzwerkfehler (ohne URL), Backup/Cache/DB-Aktionen.
- **Voll-Metadaten** (flaches Modell): App-/Geräte-/DB-Version, Sprache, Zeitzone, Exportzeitpunkt, Retention,
  Größenlimits, abgedeckter Zeitraum, `contentTruncated` + Verwurf-Zähler, Liste der Log-Dateien
  (Name/erste/letzte Zeit/Bytes/Ereignis-Anzahl), Liste der Crash-Dateien, Settings-Sektion.
- Export → EIN ZIP nach `Download/Vivicast/` (TV-safe, wie Backup); Clipboard-Kopieren + separate
  Settings-Export-Zeile raus.
- Doc-Änderungen (PRD-11) mit umsetzen.

## Entscheidungen (alle ENTSCHIEDEN)

1. Crash: **immer** erfasst (separates Crash-Log), übrige Events nur bei aktivem Toggle. ✓
2. Metadaten: **Vollausbau** direkt mit. ✓
3. Doc-Änderungen: PRD-11 anpassen (Kopieren raus, Export-Ziel Download-Ordner, Crash-Log-Regel,
   Metadaten-Settings). ✓
4. In einem Durchgang bauen. ✓

Verbleibende Detail-Bestätigung (klein): flaches `logs/`-Modell (zeitgestempelte Dateien), Rotation
**5 MB/Datei**, Gesamt-Cap **20 MB**, Retention **1–7 Tage**, Crash-Datei **pro Crash** (max 10) — Zahlen ok?

## Gates

`detekt` · `assembleDebug` · `test`; Emulator-Smoke: Toggle ein → Fehler provozieren (z. B.
Verbindungstest gegen toten Host) → exportieren → ZIP liegt in `Download/Vivicast/`, Log enthält das
bereinigte Event, **keine** URL/Zugangsdaten im ZIP.
