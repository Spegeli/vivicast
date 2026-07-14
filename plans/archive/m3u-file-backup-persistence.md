# Plan: M3U-Datei-Playlisten dauerhaft persistieren + ins Backup aufnehmen

Status: ABGESCHLOSSEN. Teil A + Teil B + Auto-Refresh-Fix umgesetzt; detekt/assembleDebug/test grün;
Instrumented grün auf API 36 (RoomProviderRepositoryTest 7/7, StandardBackupTest 14/14). Manuelle
Editor-Tests vom User bestätigt (anlegen/bearbeiten/ersetzen/Backup-Roundtrip). Vollaudit ohne Restbefund.
Erstellt: 2026-07-14
Aktualisiert: 2026-07-14 (abgeschlossen)

## Teil C — Auto-Refresh-Fix (umgesetzt)

File-Provider haben keine fetchbare Quelle ⇒ dürfen nicht auto-refreshen. Die Planung
(App-Start/periodisch) prüft aber nur `refreshOnAppStartEnabled` / `refreshIntervalHours > 0`, nicht
`isAutomaticallyRefreshable`, und der Editor blendet diese Felder bei File aus ⇒ sie blieben auf
Default (24/true) und der Scheduler hätte den unveränderten Disk-Inhalt bei jedem Start/Intervall
re-importiert.

Fix in `RoomProviderRepository` (ein Schreibpunkt): `isFileModeM3u(...)`-Helper; create+update setzen bei
File `refreshIntervalHours = REFRESH_INTERVAL_OFF` + `refreshOnAppStartEnabled = false`. Manuelles
Speichern/Ersetzen (`onProviderSaved` → direktes `enqueuePlaylistRefresh`) und Post-Restore-Rebuild
(MainActivity, enqueued alle aktiven Provider direkt) bleiben unberührt. Test:
`fileModeM3uProviderIsNeverScheduledForAutoRefresh`.

## Vollaudit (read-only) — kein Restbefund

Domain-`Provider` trägt keine URL/keinen Source-Mode → Consumer sind katalogbasiert, quell-agnostisch.
Alle `credentials.url`-Leser auditiert + korrekt. Logo/EPG-Quellen/EPG-Mapping/WatchNext/Search/Deep-Links
über providerId/stableKey + Room → für File identisch zu URL/Xtream. „Refresh all" schließt File bewusst
aus (konsistent). Playback über M3uStreamReferenceStore (nicht Playlist-URL). Kein File-spezifischer
NPE/Fetch-Fehler/Fehlbranch gefunden.

## Problem

M3U-Playlisten im **Datei-Modus** (`M3uSourceMode.File`) speichern ihren rohen Inhalt nur in
`TransientM3uSourceStore` (RAM), der nach dem ersten Import geleert wird. Folgen:

1. Nach App-Neustart kann ein Datei-Provider nicht refreshen (Inhalt weg).
2. Backup kann den Inhalt nicht exportieren → nach Restore steht der Provider auf
   `CREDENTIALS_REQUIRED` mit leerem Katalog; Nutzer muss die M3U-Datei manuell neu importieren.

PRD-Konflikt (vom Nutzer freigegeben): Kapitel 10 listete nur `M3U-URL` und schloss lokale M3U-Dateien
explizit aus. PRD ist entsprechend angepasst.

## Ziel

Rohen M3U-Datei-Inhalt dauerhaft (auf Disk) speichern und in Backup/Restore einbeziehen, sodass ein
Datei-Provider nach Restore automatisch wie ein URL-/Xtream-Provider wieder aufgebaut wird — **ohne**
den Inhalt in den Compose-Editor-State zu ziehen und mit sauberer Aufräumung bei Quellwechsel.

## Ansatz

Disk-Datei statt SecureValueStore (SharedPreferences ⇒ 32MB-OOM-Risiko) und statt Room (Migration-Churn).
M3U-Inhalt wird wie ein großes Provider-Secret behandelt. Roh als JSON-String im Payload (kein base64 —
Payload ist bereits AES-GCM-verschlüsselt, M3U ist UTF-8-Text).

At-Rest: Inhalt liegt im App-Sandbox-Klartext; die Stream-URLs mit denselben Tokens liegen heute schon im
Room-Katalog im Klartext → keine neue Expositionsklasse.

---

## Teil A — Kern (umgesetzt, Backup-Roundtrip grün)

### A1. Neuer Store — `:data:provider`
- `DiskM3uFileSourceStore(baseDir, ioDispatcher)`: `write/read/delete(providerId)` →
  `File(baseDir, "$providerId.m3u")`. Konkrete Klasse, kein Interface.

### A2. `StandardBackupExporter`
- `ProviderEntity.toFullBackupSourceJson()` M3U: `m3u_url` vorhanden → `{m3uUrl}`; sonst
  `diskStore.read(id)` → `{m3uInlineContent}`; sonst null.

### A3. `StandardBackupRestorer`
- `hasRestorableSource` M3U: true auch bei `m3uInlineContent`.
- `writeRestoredSecrets` File: `diskStore.write(stableKey, content)` + Secret `m3u_source_mode = "File"`.
- `deleteOldSecrets`: `diskStore.delete(provider.id)` + `m3u_source_mode`-Secret.

### A4. `AppContainer` + `TransientM3uSourceStore` entfernt
- Eine Store-Instanz (`filesDir/m3u_sources`) in Repository + Export + Restore.

### A5. PRD `10-backup-import-requirements.md` — angepasst.

---

## Teil B — Nachträge (OFFEN, Runde-2-Entscheidungen)

### B1. Perf: Inhalt nie in den Editor-State (bessere Lösung)

Grund: `from()` würde bis zu 32MB in den Compose-State laden; `sourceSignature()` verkettet den vollen
Inhalt; Editor-Copy pro Keystroke. Zusätzlich las meine Kern-Änderung `getCredentials(File)` beim
Editor-Öffnen 32MB von Disk (Regression ggü. altem RAM-Verhalten). Ziel: Öffnen + Bearbeiten bleiben
leicht; Inhalt nur lesen, wenn wirklich nötig (Refresh + expliziter Test).

Änderungen:
- `RoomProviderRepository.getCredentials` File-Zweig → `inlineContent = null` (liest nur das
  `m3u_source_mode`-Secret, **keinen** Disk-Inhalt). Öffnen des Editors wird wieder billig.
- Neu: `ProviderRepository.getProviderM3uInlineContent(providerId): String?` → Disk-Read.
- `RefreshExecution.refreshM3uProvider` File-Zweig → `getProviderM3uInlineContent(provider.id)` statt
  `credentials.inlineContent`.
- `inlineContent`-Feld aus `ProviderCredentials.M3u` entfernen, falls danach ungenutzt (sonst belassen).
- `ProviderEditorState.from()` File → `m3uContent = ""`, `m3uHasExistingSource = true`,
  `m3uFileName = "$providerId.m3u"` (siehe B2). Editor hält nie den Inhalt.
- Backup-Export bleibt unberührt (liest weiterhin direkt via `DiskM3uFileSourceStore`, nicht via
  `getCredentials`).

### B2. Anzeige der verknüpften Datei beim Bearbeiten

- Beim Bearbeiten eines File-Providers `<providerId>.m3u` als Dateiname anzeigen (bestehendes
  `settings_provider_file_name` / `ProviderM3uFilePicker`-Label). Wird über `from()` gesetzt (B1).
- „Select" (neue Datei wählen): der Picker setzt `m3uFileName = <neuer Dateiname>` bereits sofort
  ([ProviderEditor.kt](../feature/settings/.../ProviderEditor.kt) onPick) → Anzeige springt sofort auf den
  neuen Namen, **nur Draft, nicht gespeichert**. Kein Zusatzcode nötig, nur verifizieren.
- Original-Dateiname wird NICHT persistiert (bewusst) — beim Bearbeiten immer `<providerId>.m3u`.

### B3. Test-Button testet die verknüpfte Datei (ohne Inhalt im State)

- Editor-Test für einen bestehenden File-Provider ohne frisch gewählte Datei: Inhalt on-demand über
  neuen App-/ViewModel-Zugang `getProviderM3uInlineContent(providerId)` auflösen und in die
  Test-Request kopieren. 32MB nur transient während des Tests, nicht im State.
- Frisch gewählte Datei (Pick) → `m3uContent` gefüllt → Test nutzt direkt den neuen Inhalt (unverändert).
- Speichern bei unveränderter Quelle: `isSourceUnchanged` bleibt true (leerer `m3uContent` vs. leere
  Pristine-Signatur) → Test übersprungen, `toUpdateRequest` schreibt die Quelle nicht neu → Disk-Datei
  unangetastet. Verifizieren.

### B4. Mode-Switch File→URL räumt Disk-Datei auf

- Beim Bearbeiten von File→URL (und Speichern mit URL): Disk-Datei `<providerId>.m3u` + zugehöriges
  `m3u_source_mode`-Secret entfernen, da nur noch die URL genutzt wird.
- Umsetzung in `RoomProviderRepository`: nach dem Schreiben der Credentials sicherstellen, dass die
  Disk-Datei nur für M3U-**File** existiert — bei Ergebnis-Modus URL (oder Nicht-M3U)
  `m3uFileSourceStore.delete(providerId)`.
- (Edit erlaubt nur File↔URL innerhalb M3U; 3-Wege-Typwahl ist Add-only.)

---

## Gates
- `.\gradlew.bat detekt`, `assembleDebug`, `test` grün.
- androidTest auf Emulator API 36 (läuft): `StandardBackupTest` (Backup-Roundtrip), `RoomProviderRepositoryTest`,
  `M3uPlaybackSmokeTest`; nach B1–B4 zusätzlich Editor-Öffnen/Test/Speichern eines File-Providers manuell prüfen.
- Neue user-facing Strings: keine erwartet (`settings_provider_file_name` existiert).

## Nicht in Scope
- Backup großer Medieninhalte / Cache.
- base64/Kompression des Inhalts.
- Original-Dateinamen persistieren.
