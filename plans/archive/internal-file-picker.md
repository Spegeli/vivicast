# Plan: Interner Datei-Picker (SAF-Ersatz für Import + Export-Ordnerwahl)

Status: DONE — vom Owner auf physischem Xiaomi Mi TV 4S (Android 9) end-to-end bestätigt
(2026-07-13). assembleDebug + detekt grün.
Betroffene Flows: M3U-Import, Backup-Import (Restore), Backup-Export, Diagnostik-Export

## Finaler Stand & Abweichungen vom Ursprungsplan

Während der Umsetzung (iterativ mit Owner-Feedback) ergänzt/geändert:

- **Kein separater „Grant"-Button.** Statt einer eigenen Grant-Zeile fragt der Picker die Berechtigung
  **lazy** an: Klick auf einen geschützten Root (Internal/USB) ohne Zugriff löst den System-Dialog aus
  und springt nach dem Gewähren automatisch in den Ordner.
- **Dynamische Root-Anzeige.** Wenn voller Zugriff erreichbar ist (Runtime-Dialog ≤29 / All-Files-
  Grant-Screen 30+ / schon gewährt) → nur Internal/USB, App-Ordner ausgeblendet. Nur wo kein Zugriff
  erreichbar ist (API 30+ TV ohne Grant-Screen) → nur App-Ordner. Grant-Screen-Erkennung via
  `<queries>` (Settings-Actions) + `resolveActivity` gegen Android-11-Package-Visibility.
- **Schreibrecht-Fix (wichtig):** Auf API ≤29 wird `WRITE_EXTERNAL_STORAGE` angefragt (nicht READ) —
  WRITE gibt auf Legacy-Storage rw und deckt Import-Lesen **und** Export-Schreiben. Vorher schlug das
  Direktschreiben in den gewählten Ordner fehl → stiller Fallback in den App-Ordner (am TV sichtbar,
  am Emulator nicht, weil `-g`-Install WRITE auto-gewährt hatte).
- **Export-Reihenfolge:** erst Zielordner-Picker, dann Passphrase-Dialog, dann „Backup erstellen".
  Passphrase-Dialog von Feature nach app-seitig verschoben (`BackupExportPassphraseDialog` in
  AppDialogs).
- **Passwort-Auge** (`allowReveal`) in Export- **und** Import-Passphrase-Dialog.
- **Button-Spinner + Status-Label** im Export („Backup wird erstellt …") und Restore („Wird
  wiederhergestellt …"); Dialog bleibt offen bis fertig. Dafür `primaryLoading` in
  `VivicastDialogActions` ergänzt.
- **Dialog-Button zentriert:** `FileSavedDialog` + `ParentalReactivationHintDialog` „Verstanden" in
  `VivicastButtonRow` gewrappt.
- **Emulator-Infra:** `scripts/start-tv-emulator.ps1` parametrisch (`-Api 28|36`); AVD
  `ViviCast_AndroidTV_API28` angelegt (Test-Floor Android 9).

Neue Dateien: `app/StorageAccess.kt`, `app/FilePickerDialog.kt`. SAF-Erkennung
(`hasRealDocumentPicker`), `FileManagerMissingDialog`, `openFileManagerSearch`, `queryDisplayName`
und die zugehörigen Play-Store-Strings entfernt.

## Problem

SAF (`ACTION_OPEN_DOCUMENT`) ist auf Android TV doppelt unzuverlässig:

1. **Erkennung falsch:** `hasRealDocumentPicker()` (AppDialogs.kt) filtert nur den Paketnamen
   `com.android.tv.frameworkpackagestubs` raus. OEM-Builds (Xiaomi Mi TV 4S, Android 9) haben einen
   anders benannten Stub → rutscht durch → SAF wird gestartet → System zeigt „Aktion von keiner App
   ausführbar", eigenes `FileManagerMissingDialog` erscheint nie.
2. **Verfügbarkeit fehlt:** TV-Dateimanager (z. B. FX) registrieren nur `ACTION_GET_CONTENT`, nicht
   `ACTION_OPEN_DOCUMENT`. Sie sind für `OpenDocument` unsichtbar.

Export nutzt heute Fixordner (`writeToDownloads`): API 29+ MediaStore `Downloads/Vivicast`, API ≤28
App-Ordner. Owner-Wunsch: beim Export den Zielordner wählbar machen.

## Recherche-Verdikt (Web + Referenz-Apps)

OwnTV (`StorageBrowser.kt`/`StorageAccess.kt`) ist brauchbare Vorlage, aber **nicht** blind übernehmen —
drei belegte Schwächen:

1. Fragt `READ_EXTERNAL_STORAGE` nie zur Laufzeit an → liest auf API ≤29 nur auf Auto-Grant-Geräten
   (betrifft das Android-9-Testgerät direkt). → Wir fragen zur Laufzeit an.
2. Kein Fallback, wenn der All-Files-Grant-Screen fehlt — dokumentiertes Android-TV-Limit
   (`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` kann fehlen). → `resolveActivity`-Guard + Fallback.
3. `listFiles()` gibt auf API 30+ **ohne** All-Files-Zugriff `null` → File-Browse dort unmöglich ohne
   Permission. → permission-freier `Android/media/<pkg>`-Kanal als garantierter Fallback (TiviMate-Muster).

## Entscheidungen (vom Owner bestätigt)

- **Voller Speicherzugriff, gestaffelt** (nicht nur All-Files).
- **SAF komplett ersetzen** (interner Picker überall; SAF-Erkennung + `FileManagerMissingDialog` +
  Play-Store-Suche raus).
- **Export-Ordnerwahl** in denselben Picker (FOLDER-Modus).

## Speicher-Strategie (gestaffelt nach API)

| Android | Import (lesen) | Export (schreiben) |
|---|---|---|
| ≤ 29 (Testgerät API 28) | `READ_EXTERNAL_STORAGE` **Runtime** → voller File-Browse | `WRITE_EXTERNAL_STORAGE` (maxSdk 29) → beliebiger Ordner |
| 30+, All-Files gewährt | `MANAGE_EXTERNAL_STORAGE` → voller Browse | File-Write beliebig |
| 30+, Grant-Screen fehlt | **Fallback:** nur `getExternalMediaDirs()[0]`-Root (permission-frei) | Fixordner MediaStore `Downloads/Vivicast` + `Android/media` |

`getExternalMediaDirs()[0]` = `/storage/emulated/0/Android/media/<pkg>` — anders als `Android/data`
nicht vor Dateimanagern/PC verborgen; permission-freier Ablage-/Auslese-Kanal. Immer als Root anbieten.

## Umfang / Nicht-Ziele

- Ziel: robuster Import **und** wählbarer Export-Ordner über eigenen Picker.
- Kein Backup-Format-, Passphrase-, PIN-, Playback-Change.
- Kein externer Dateimanager, kein `ACTION_GET_CONTENT`-Weg.
- Kein neues Modul, keine DI-/Repository-Änderung, keine Repository-Calls in Composables.
- Alles app-hoisted (Context/Environment/Settings/Permission) — konform zu CLAUDE.md.

## Änderungen

### 1. `AndroidManifest.xml` (app)
- `+ READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`)
- `+ WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="29"`)
- `+ MANAGE_EXTERNAL_STORAGE` (`tools:ignore="ScopedStorage"`)
- `application android:requestLegacyExternalStorage="true"` (API 29)
- `<queries>` OPEN_DOCUMENT **entfernen** (nur für alte SAF-Erkennung nötig gewesen).

### 2. `StorageAccess.kt` (NEU, app/) — aus OwnTV adaptiert + gehärtet
- `hasAllFilesAccess()` — `SDK_INT < R || isExternalStorageManager()`
- `canRequestAllFilesAccess(context)` — `resolveActivity`-Guard **vor** dem Grant-Intent (TV-Screen
  kann fehlen); nur dann die Grant-Zeile zeigen.
- `requestAllFilesAccess(context)` — `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`, Fallback
  `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`, beide `runCatching`.
- `needsRuntimeReadPermission(context)` — API ≤29 && `READ_EXTERNAL_STORAGE` nicht gewährt.
- `storageRoots(context)` — Internal + Removable (`getExternalFilesDirs`) + **`getExternalMediaDirs`**
  (permission-freier Fallback) + App-Ordner.
- `mediaDir(context)` — `getExternalMediaDirs()[0]/Vivicast` für Export-Fallback.

### 3. `FilePickerDialog.kt` (NEU, app/) — beide Modi
- Aus OwnTV `StorageBrowser` adaptiert, `BrowseMode.FILE` (Import) + `BrowseMode.FOLDER` (Export).
- Vivicast-Designsystem wiederverwenden (`VivicastDialog`, `InfoPanel`, `ActionPill`,
  `VivicastButtonRow`, fokussierbare Zeilen) statt OwnTV-Rohkomponenten.
- FILE: Extension-Filter (`Set<String>?`), Datei-Zeile ruft `onPick(File)`.
- FOLDER: navigieren + „Diesen Ordner verwenden" + optional „Neuer Ordner".
- Permission-Gate im Dialog:
  - API ≤29 && `needsRuntimeReadPermission`: `ActivityResultContracts.RequestPermission`
    (`READ_EXTERNAL_STORAGE`) vor dem Browsen; bei Deny → nur `Android/media`-Root nutzbar.
  - API 30+ && `!hasAllFilesAccess` && `canRequestAllFilesAccess`: Zeile „Vollen Speicherzugriff
    erlauben" → `requestAllFilesAccess`. Wenn Grant unmöglich/verweigert → nur `Android/media`-Root.
- Fokus nach jeder Navigation neu greifen (OwnTV: `delay(120)` + `requestFocus`).
- BackHandler: eine Ebene hoch, an Wurzel `onDismiss`.

### 4. `MainActivity.kt` — Wiring
- **Import (2 Stellen):** `hasRealDocumentPicker`-Check + `OpenDocument`-Launcher →
  `showFilePicker`-State + `FilePickerDialog(FILE)`. Pick liefert `File` → `file.inputStream()` /
  `file.readText(UTF_8)`.
  Validierung **1:1** wiederverwenden: M3U (leer / `> MAX_M3U_INLINE_SOURCE_CHARS` / kein `#EXTINF`);
  Backup (leer / `!isEncryptedBackupContainer` → dann `pendingImportContainerBytes` →
  `BackupImportPassphraseDialog`).
- **Export (2 Stellen: Backup + Diagnostik):** vor dem Schreiben `FilePickerDialog(FOLDER)` zeigen;
  gewählten `File`-Ordner an die Schreibfunktion geben.
  - `writeToDownloads` erweitern/umbauen: mit gewähltem Ordner → `File(ordner, name)` schreiben;
    ohne Wahl / kein Zugriff → bisheriger Fixordner-Pfad (MediaStore bzw. App-Ordner).
  - Erfolgs-`savedFileInfo`-Dialog mit tatsächlichem Zielpfad (bereits vorhanden).
- `m3uFileImportLauncher`, `encryptedFullBackupImportLauncher`, `showFileManagerPrompt` entfernen.

### 5. Aufräumen
- `AppDialogs.kt`: `hasRealDocumentPicker`, `openFileManagerSearch`, `FileManagerMissingDialog` raus;
  `queryDisplayName` prüfen (nur noch nötig, falls Anzeige-Name gebraucht — sonst raus).
- Ungenutzte Strings `settings_provider_file_no_manager_*`, `_install_manager`, `_no_store` in
  `values/` + `values-en/` entfernen. Neue Strings für Picker-Titel, Grant-Zeile, „Ordner verwenden",
  „Neuer Ordner", Deny-Hinweis (DE default + EN).

### 6. Export-Ordner merken (Owner bestätigt) — `UserPreferencesStore`
- **Getrennt** je Flow: zwei nullable String-Prefs `lastBackupExportDir`, `lastDiagnosticsExportDir`
  in `UserPreferences` + Setter im `UserPreferencesStore` (bestehendes Muster, keine neue Persistenz-
  Infra).
- FOLDER-Picker öffnet vorselektiert im gemerkten Ordner des jeweiligen Flows; existiert er nicht
  (mehr) oder erster Export → Fix-Root (`Downloads/Vivicast` bzw. `Android/media`).
- Nach erfolgreichem Export den gewählten Ordnerpfad in die passende Pref schreiben (app-hoisted
  Effekt, kein Repository-Call im Composable — MainActivity liest `userPreferencesStore.values`
  bereits).

## SDK / Kompatibilität (Android 9 → aktuell)
- **Keine Gradle-Änderung nötig.** `minSdk=23` deckt Android 9 (API 28) bereits ab; `targetSdk=36` /
  `compileSdk=36` bleiben. Die Versionsspanne wird zur Laufzeit über `Build.VERSION.SDK_INT`-Zweige +
  `maxSdkVersion` an den Permissions geregelt (siehe Speicher-Strategie).
- `minSdk` NICHT auf 28 heben (würde Android 6/7/8 grundlos fallen lassen; fürs Feature kein Vorteil).
- **Emulator-Test Boden+Decke (ERLEDIGT):** `scripts/start-tv-emulator.ps1` ist parametrisch
  (`-Api 28|36`, Default 36, mit AVD-Guard). AVD `ViviCast_AndroidTV_API28` (Android 9 Pie,
  tv_1080p) + Image `system-images;android-28;android-tv;x86` sind angelegt. Start:
  `.\scripts\start-tv-emulator.ps1 -Api 28`. Noch nicht voll gebootet — bei Bedarf verifizieren.

## Test
- Emulator (API 30+): All-Files-Grant-Flow; Import aus gewähltem Ordner; Export in gewählten Ordner;
  Grant-verweigert → `Android/media`-Root funktioniert.
- Physisches Xiaomi Mi TV 4S (Android 9, API 28): `READ_EXTERNAL_STORAGE`-Runtime-Dialog per D-Pad;
  Import von USB/Download; Export in beliebigen Ordner; von Vivicast exportiertes Backup wieder
  importieren.
- Gates: `.\gradlew.bat detekt`, `assembleDebug`. Kein APK-Install aufs TV ohne Owner-OK.

## Referenzen
- OwnTV (Vorlage, nicht importieren):
  `../IPTV-APPS/OwnTV-main/app/src/main/java/tv/own/owntv/core/storage/StorageAccess.kt`,
  `.../ui/components/StorageBrowser.kt`, `.../app/src/main/AndroidManifest.xml`
- Web: developer.android.com „Manage all files on a storage device", „Storage updates in Android 11",
  „data-storage/use-cases"; TiviMate-Restore via `Android/media`; TV-Grant-Screen-Limit (CommonsWare,
  MS Q&A). `listFiles()`=null auf API 30+ ohne All-Files bestätigt.
