# Plan: Backup-Bereich fertigstellen (ein verschlüsseltes Backup)

Status: **Umgesetzt & verifiziert (Export/Import auf Emulator bestätigt). Docs angeglichen.**

## Nachtrag — zwei Abweichungen während der Umsetzung (Endstand)

- **Container ist binär, nicht JSON.** `.vcbak` = opake Bytes: nur ein fester Kopf
  `[MAGIC "VVCB"][Version][Iterationen][salt][nonce]` ist Klartext (technisch nötig zum Entschlüsseln),
  Rest ist AES-GCM-`ciphertext`. Kein JSON-Umschlag mit lesbaren Metadaten mehr (siehe `BackupCrypto.kt`).
- **Export ohne Picker → fester Ordner.** Android-TV-Dateimanager bieten nur Datei-Auswahl (`OPEN_DOCUMENT`),
  keinen Schreib-Picker (`CREATE_DOCUMENT`/`OPEN_DOCUMENT_TREE`). Export schreibt daher ohne Picker nach
  `Download/Vivicast/` (MediaStore ab API 29, sonst App-Ordner-Fallback) und zeigt den Pfad. Import nutzt
  weiter den `OPEN_DOCUMENT`-Picker (Datei zuerst wählen, dann Passphrase). Letzte-Sicherung-Zeile entfernt.

Reviewentscheidungen: Safety-Backup bleibt best-effort (Snapshot + Gate, kein User-Rollback; Restore ist
atomar). Export-Dialog mit **Passphrase-Bestätigungsfeld** (doppelt eingeben). Selbst erledigt: `.vcbak`
MIME `application/octet-stream`; Support-Export ist nicht wiederherstellbar (Restore lehnt ihn klar ab);
Migrations-Naht auf Container- UND Payload-Versions-Check.

## Modell (final, per aktualisierter ADR-004 / PRD-10 v9)

- **EIN Backup** — Passphrase Pflicht, gesamte Nutzlast verschlüsselt (Whole-Blob AES-GCM/PBKDF2, wie
  bestehendes `EncryptedFullBackup`). Enthält alles inkl. Provider-URLs + Zugangsdaten. Keine URL/kein Feld
  je im Klartext. Endung **`.vcbak`**. Kein unverschlüsseltes wiederherstellbares Backup mehr.
- **Restore** — immer Passphrase; falsch/fehlt → Abbruch vor Datenänderung; Ersetzen (kein Merge); Safety-
  Backup; PIN nicht wiederhergestellt; Post-Restore-Hinweis wenn Kindersicherung im Backup aktiv war.
- **Support-/Einstellungsexport (neu, im Diagnose-/Support-Bereich)** — lesbare `.json`, Settings + nicht-
  geheime Metadaten (Provider-Namen/Typ/Flags, Logo-Prio, Gruppen, EPG-Zuordnungen), **keine URLs, keine
  Zugangsdaten**. **Nicht wiederherstellbar.** Für Zusenden an den Entwickler.

## Umsetzung

Backend großteils Wiederverwendung: `exportEncryptedFullJson` + `restoreEncryptedFull` existieren schon.

1. **Backup-Panel** ([BackupSettingsPanel.kt](../feature/settings/src/main/java/com/vivicast/tv/feature/settings/BackupSettingsPanel.kt)):
   3 Zeilen — „Backup erstellen" (Passphrase-Dialog Pflicht → Export), „Backup wiederherstellen"
   (Datei wählen → Passphrase → Restore), „Letztes Backup". Weg: Backup-Ziel + die 4 alten Zeilen
   (Standard-/Full-Export/Import). Full/Standard verschmelzen zu je einer Aktion.
2. **Export-Wiring** ([MainActivity.kt](../app/src/main/java/com/vivicast/tv/MainActivity.kt)):
   immer `exportEncryptedFullJson`; SAF-Name `Vivicast_backup_<YYYYMMDD>_<HHmmss>_v<Version>.vcbak`.
3. **Import-Wiring:** Datei wählen → immer verschlüsselt → Passphrase-Dialog → `restoreEncryptedFull`.
   Standard-Restore-UI-Pfad entfällt (`restore()`/`validateStandardBackupForRestore` bleiben als Code oder
   werden für den Debug-Export/Support genutzt).
4. **Support-Export** (Diagnose-/Support-Bereich): neue Funktion = `buildDocument()` **ohne `source`**
   (keine URLs) → lesbare `.json`, nicht wiederherstellbar. Redaction-Regeln aus ADR-014.
5. **Post-Restore-PIN-Hinweis:** bei `preview.parentalProtectionWasActive` nach erfolgreichem Restore
   Hinweis-Dialog (→ Einstellungen > Kindersicherung). Neue Strings (de+en).
6. **userAgent + refreshOnAppStartEnabled** in den Backup-Payload (`StandardBackupProvider` + toJson +
   Restorer, mit Defaults).
7. **Migrations-Naht:** Validator akzeptiert `schemaVersion <= aktuell` (Migrate-Hook, v1 no-op),
   lehnt `> aktuell` ab.
8. **Passphrase-Mindestlänge** (z.B. 8), da jetzt immer Pflicht. Warnhinweis „ohne Passwort ist das Backup
   unwiederbringlich" im Export-Dialog.
9. **Funktionen umbenennen** (ein Backup, kein „standard/encryptedFull" mehr):
   - `exportEncryptedFullJson` → `exportBackup`; `restoreEncryptedFull`/`restoreFullPayload` → `restoreBackup`;
     `encryptFullBackupPayload`/`decryptFullBackupPayload` → `encryptBackupPayload`/`decryptBackupPayload`;
     `EncryptedFullBackup.kt` → `BackupCrypto.kt` (o.ä.).
   - Der bisherige `exportJson` (Standard) wird zum **Support-Export ohne URLs**: `exportSupportSettingsJson`.
   - `StandardBackup*`-Namen entschlacken wo sinnvoll (Daten-/Doc-Klassen bleiben, nur „Standard"-Präfix
     überdenken). Klein halten, keine API-Kosmetik-Orgie.
   - **Nebenpunkt (bei Umsetzung entscheiden):** das interne Safety-Backup nutzt heute den plaintext-Standard-
     Export (ohne Secrets). Da das nutzerseitige „Standard-Backup" entfällt, klären: Safety-Backup als
     internen (app-privaten) Snapshot behalten — ggf. Keystore-verschlüsselt inkl. Credentials, damit ein
     Rollback den vollen Vorzustand herstellt.
10. **Auto-Refresh nach Restore** (echte Lücke): `runStandardRestore` triggert aktuell **keinen** Refresh →
    nach Restore leere Kanäle/EPG bis zum nächsten App-Start. Fix: im Erfolgspfad die wiederhergestellten
    aktiven Provider + aktiven EPG-Quellen per `scheduler.enqueuePlaylistRefresh`/`enqueueEpgRefresh` anstoßen
    (gleiche Logik wie Startup). Katalog/EPG bauen sich auf, pending Favoriten/Verlauf binden.

## Versionierung (dokumentiert in PRD-10)

- **`database-version` im Backup = nur Info**, blockiert Restore nicht. Restore fügt logische JSON-Daten ins
  aktuelle Schema ein, fehlende neue Felder → Defaults. → altes Backup auf neuerer App/DB funktioniert.
- **`backup-schema-version`** ist das Tor: `<= aktuell` lesen/migrieren, `> aktuell` ablehnen. Nur bei
  **inkompatibler Format-Änderung** hochzählen — nicht für additive default-sichere Felder (wie userAgent jetzt).

## Hinfällig geworden

- „Standard-Backup fehlt Metadaten" — das verschlüsselte Backup hat appVersion/packageName/dbVersion/
  dataSections bereits. Gap löst sich auf.
- Backup-Ziel-Zeile — entfällt komplett.

## Nicht im Scope (per ADR/PRD v1)

Unverschlüsseltes wiederherstellbares Backup, selektiv/Merge/Kopie-Import, PIN-Restore, Field-Level-Krypto,
Cloud/Drive/SMB, Auto-Backup, Checksum.

## Tests & Gates

`StandardBackupTest`: verschlüsselter Roundtrip inkl. userAgent/refreshOnAppStart + Migrations-/Versions-
Fälle + Passphrase-Min-Länge; Support-Export enthält keine URLs/Secrets. Panel/Focus-Tests (neue Zeilen).
`detekt` / `assembleDebug` / `test` + relevante connectedAndroidTests. Emulator-Smoke: Backup mit Passphrase
→ `.vcbak` prüfen; Restore → Daten + Credentials zurück; parental-aktives Backup → Post-Restore-Hinweis;
Support-Export → keine URLs.

## Nicht-normative Docs zum Nachziehen (separat)

`architecture/diagrams/06-backup-restore-flow.md` + Settings-Design/Mockup, falls sie die alten zwei
Backup-Typen zeigen.
