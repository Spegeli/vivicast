# Plan: Playlist-Editor Source-Switch + Refresh-All + Source-Badges

Status: ENTSCHIEDEN — wartet auf GO zur Umsetzung, kein Code
Erstellt: 2026-07-14

## Entscheidungen (User)
- #1: File in „Refresh all" einbeziehen (alle aktiven mit Credentials).
- #2: Badge-Text „M3U Datei" (deutsch, spiegelt Editor-Buttons).
- #3: Switch-Bestätigung in **beiden** Richtungen (URL→Datei und Datei→URL), Popup-Text
  **richtungsabhängig**.
- #4: Freien Typwechsel (M3U-URL ↔ M3U-Datei ↔ Xtream) beim Bearbeiten **mit umsetzen**.
- #5: Add-Bestätigung „Wird als {Typ} gespeichert" **immer** beim Anlegen.
- #6: Beim Provider-Speichern ein sanitisiertes `provider`-Diagnostics-Event loggen (Ziel-Typ/Mode +
  `switchedFrom` bei Wechsel; keine Secrets).
- #7: Validierung switch/typ-bewusst machen (beide Stellen) — Pflicht-Bestandteil von #3/#4, keine Option.
- #8a: Katalog beim Typwechsel **proaktiv leeren** (Katalog + Stream-Refs), Refresh baut neu.
- #8b: Typwechsel-Popup **warnt** vor Verlust von (1) den **alten Quelldaten** (Zugangsdaten/URL des
  bisherigen Typs — werden gelöscht, danach nicht mehr gespeichert) UND (2) Favoriten/Verlauf/Fortschritt
  dieser Playlist. Text richtungsabhängig, benennt den alten Typ konkret (z.B. „Deine M3U-URL wird
  gelöscht…" bzw. „Deine Xtream-Zugangsdaten werden gelöscht…").
- #8c: **Pre-existing Bug mitfixen** — `deleteProvider` löscht künftig auch die M3U-Stream-Refs
  (M3uStreamReferenceStore in `RoomProviderRepository` injizieren; deckt Switch-Wipe + Delete ab).
- #8 MUSS: alte Quelle beim Typwechsel komplett wipen (Credentials/Disk/Refs) vor Schreiben des neuen Typs;
  Xtream-Account-Info (`xtreamExpiresAtMillis`/`xtreamMaxConnections`) beim Wechsel weg von Xtream auf null.

## Status
ABGESCHLOSSEN. #1–#8 fertig + Verifikations-Durchgang.

Verifikation (Review + eigene Prüfung):
- Reviewer-Fund behoben: Typwechsel schrieb neue Credentials mit „keep-existing"-Semantik → Partial-Wipe
  bei Blank-Switch möglich. Fix: erst neu schreiben (fail-fast `requireSecret`), dann alte Gegen-Typ-Secrets
  löschen; neuer Test `switchingToXtreamWithBlankCredentialsFailsAndKeepsOldSource`.
- Toter Code entfernt (`M3uSourceMode.labelRes` + Imports).
- PRD-Widerspruch gefunden + behoben: PRD ch05 verbot Typwechsel — auf „erlaubt (mit Bestätigung)" geändert.
- Docs-Sweep: PRD 05/06/07/08/09, ADR-003/004/009/012/014, design screens/08 + wireframes/05,07 +
  components/settings an die neuen Verhalten angepasst (M3U-Datei-Persistenz+Backup, kein File-Auto-Refresh,
  Typwechsel destruktiv, Badges, Save-/Switch-Dialoge, Delete-Spinner).

Original-Umsetzung #1–#8. detekt/assembleDebug/test grün; androidTest kompiliert; Emulator API 36:
RoomProviderRepositoryTest 8/8 (inkl. Typwechsel-Wipe + Sicherheits-Assert Xtream-Passwort). Editor-
Navigation (Toggle/Popup/Switch-UX) testet User. Backup-androidTest unverändert grün erwartet.

Umsetzungsnotizen:
- `updateProvider` erkennt Typ-/Mode-Switch; Typwechsel wiped alte Quelle komplett (`deleteCredentials`
  + Disk + Stream-Refs), Xtream-Account-Info auf null, Katalog+Refs proaktiv geleert; `ProviderSaveResult.
  switchedFromType` für Diagnostics.
- Editor: 3-Wege-Quellwahl in Add+Edit; `selectSource` bewahrt Drafts; `originalType/originalSourceMode`
  + `sourceSwitched`; Validierung „leer=behalten" nur ohne Switch.
- Einheitlicher `ProviderSourceConfirmDialog` (Add: „wird als X gespeichert"; Edit-Switch: richtungs-
  abhängige Warnung inkl. Quelldaten- + Favoriten/Verlauf-Verlust). Save-Flow: Validierung → Confirm →
  `proceedSave` (Test → Persist).
- Badge über `providerSourceModes`-Map (aus getCredentials) → „M3U URL/Datei/Xtream".
- Refresh-all: alle aktiven mit Credentials (File inklusive).
- Diagnostics: `provider`/`saved` mit `source` + `switchedFrom` (sanitisiert).
- Bonus: `deleteProvider` räumt Stream-Refs.

## Einheitliches Save-Bestätigungsmodell (aus #3/#4/#5)
Ein Mechanismus, drei Fälle:
- **Add-Modus:** immer Popup „Wird als {Ziel-Typ} gespeichert — OK?" (informativ) → Ja speichert.
- **Edit-Modus mit Typ/Mode-Wechsel:** Popup „von {Original} auf {Ziel} umstellen — sicher?"
  (richtungsabhängig) + Warnung: alte Quelldaten (Zugangsdaten/URL) werden gelöscht **und**
  Favoriten/Verlauf/Fortschritt gehen verloren (siehe #8b) → Ja speichert.
  Strings: pro Ausgangs-Typ ein „was-geht-verloren"-Baustein (M3U-URL / M3U-Datei / Xtream-Zugangsdaten),
  kombiniert mit dem generischen Favoriten/Verlauf-Hinweis.
- **Edit-Modus ohne Wechsel:** kein Popup, Save wie bisher.
Ziel-/Original-Typ-Text = derselbe wie Badge (#2) / Editor-Buttons.

Drei UI-/Verhaltenspunkte rund um M3U-Datei-Provider. Reihenfolge nach Umfang.

---

## #1 „Refresh Playlists now" schließt File aus

**Befund:** Der einzige „alle aktualisieren"-Button (`settings_playlist_refresh_all` → `onRefreshAll`,
[ProviderSettingsPanel.kt:213](../feature/settings/.../ProviderSettingsPanel.kt)) filtert:
```
provider.isActive && when (credentials) {
    is M3u -> credentials.sourceMode.isAutomaticallyRefreshable   // File → false → AUSGESCHLOSSEN
    is Xtream -> true
    null -> false
}
```
Ein Maintenance-Refresh existiert nicht. File-Provider werden also von „Refresh all" nicht erfasst.

**Fix (klein):** File einbeziehen — Filter auf `provider.isActive && credentials != null` reduzieren
(jeder aktive M3U/Xtream mit auflösbaren Credentials). File re-importiert dann den Disk-Inhalt
(funktioniert dank Durable-Persistenz, baut Katalog + EPG-Mapping neu). `enqueuePlaylistRefresh` nutzt
KEEP, coalesct also in-flight.

**Offen:** Reicht „alle aktiven einbeziehen"? (Empfehlung: ja.)

---

## #2 Overview-Badge „M3U" → „M3U URL" / „M3U Datei"

**Befund:** Badge zeigt `provider.type.label` = hartkodiert `"M3U"` / `"Xtream"`
([ProviderSettingsPanel.kt:574,636,658](../feature/settings/.../ProviderSettingsPanel.kt)). Das Domain-
`Provider` kennt den Source-Mode nicht — der liegt nur in `ProviderCredentials` (Secure Store).

**Vorschlag:** Source-Mode pro Provider in die Overview reichen und das Badge aufteilen:
- `M3U` + Url → **„M3U URL"**
- `M3U` + File → **„M3U Datei"**
- Xtream → **„Xtream"** (unverändert)

Formulierung spiegelt die Editor-Source-Buttons (`"M3U " + stringResource(m3u_source_url|file)` =
„URL"/„Datei") → konsistent, **keine neuen Strings**.

**Datenweg:** Die Overview lädt bereits `getCredentials` für alle Provider (Duplicate-URL-Erkennung,
[ProviderSettingsPanel.kt:174](../feature/settings/.../ProviderSettingsPanel.kt)). `getCredentials(File)`
ist seit dem Umbau billig (kein Inhalt). → Dort zusätzlich eine `providerId → M3uSourceMode`-Map bauen und
an `ProviderOverviewPanel` (+ die zweite Badge-Stelle) geben. `type.label` bleibt für Xtream; für M3U eine
neue `@Composable`-Beschriftung, die den Mode einbezieht.

**Offen:** Wording „M3U Datei" ok? (statt „M3U File").

---

## #3 Feld-Leeren beim Mode-Toggle + Switch-Bestätigung

**Befund:** Im Edit-Modus leert der URL/File-Toggle die Felder
([ProviderEditor.kt:497-507](../feature/settings/.../ProviderEditor.kt)):
`m3uUrl=""`, `m3uContent=""`, `m3uFileName=""`, `m3uHasExistingSource=false`. Toggle hin und zurück ⇒
ursprünglicher Wert weg ⇒ Save meckert „URL fehlt" bzw. „M3U Datei fehlt". Verwirrend.

**Ziel (User):**
1. URL-Playlist: URL-Feld bleibt beim Bearbeiten immer befüllt (auch über Toggles).
2. Datei-Playlist: Datei-Feld/Marker bleibt immer befüllt.
3. Beim **Speichern** einen Mode-Switch (Original-Mode ≠ gewählter Mode) erkennen und per Popup
   bestätigen lassen („Achtung: von URL auf Datei umstellen — sicher?"), erst „Ja" speichert.

**Design:**

a) **Original-Mode merken:** neues Feld `originalSourceMode: M3uSourceMode?` im `ProviderEditorState`
   (`from()` = geladener Mode; `newProvider()` = null).

b) **Toggle bewahrt Felder:** im Edit-Toggle NICHT mehr leeren. Nur `m3uSourceMode = mode` setzen und
   `m3uHasExistingSource = (mode == originalSourceMode)`. `m3uUrl` / `m3uContent` / `m3uFileName` bleiben.
   - URL-Provider: `m3uUrl` = Original bleibt über Toggles sichtbar.
   - Datei-Provider: `m3uFileName = "<id>.m3u"` bleibt über Toggles sichtbar; Inhalt wie gehabt on-demand.

c) **Validierung (unverändert nutzbar):** Für den *gewechselten* Mode ist `hasExistingSource=false` ⇒
   leeres Feld = Pflichtfehler (URL leer → „URL fehlt"; keine Datei gewählt → „Datei fehlt"). Der Nutzer
   muss die neue Quelle also erst angeben. Für den *Original*-Mode gilt weiter „leer = Quelle behalten".

d) **Switch-Bestätigung beim Speichern:** in `onSave` vor dem Test/Persist prüfen:
   `isEditing && originalSourceMode != null && m3uSourceMode != originalSourceMode`.
   - true ⇒ neues Bestätigungs-Popup `ProviderSourceSwitchDialog` (Ja/Abbrechen). „Ja" ⇒ bestehender
     Save-Flow (Quelle testen → bei Erfolg persistieren; File→URL-Cleanup läuft schon im Repo). „Abbrechen"
     ⇒ zurück in den Editor.
   - false ⇒ Save wie bisher (kein Popup).
   - Nur bei echtem **Mode-Wechsel** — reines URL-Ändern oder Datei-Ersetzen (gleicher Mode) bleibt ohne
     Extra-Popup (Test-/Fehlerdialog wie gehabt).

e) **Neue Strings** (designsystem, de+en): Dialog-Titel + Body (mit Von/Zu-Richtung) + Buttons.
   Zwei Richtungen: URL→Datei und Datei→URL (Body-Text richtungsabhängig oder generisch).

**Interaktion mit bestehender Logik:** `isSourceUnchanged` (Signatur inkl. `m3uSourceMode`) bleibt korrekt:
Toggle hin und zurück ohne Änderung ⇒ Signatur == Pristine ⇒ kein Switch, kein Test, kein Popup.

**Offen / Entscheidungen:**
- Popup-Text richtungsabhängig („von URL auf Datei" / „von Datei auf URL") oder generisch?
- Popup auch bei File→URL nötig, oder nur bei URL→File? (Empfehlung: beide, symmetrisch.)
- `m3uHasExistingSource` beim Editieren-dann-Zurücktoggeln: Reset auf `(mode==original)` ist minimal
  kosmetisch unsauber (überschriebener Draft gilt dann als „vorhanden"), aber kein Datenverlust. OK?

---

## #4 Typwechsel beim Bearbeiten (M3U-URL ↔ M3U-Datei ↔ Xtream) — größer

**Heute:** Im Edit-Modus ist der Typ fix — die 3-Wege-Quellwahl ist Add-only
([ProviderEditor.kt:446](../feature/settings/.../ProviderEditor.kt) `if (isEditing) return`), Edit zeigt nur
den URL/Datei-Toggle innerhalb M3U. `updateProvider` nimmt `existing.type` (Typ unveränderlich).
`ProviderUpdateRequest` hat **kein** `type`-Feld.

**Ziel:** Beim Bearbeiten frei zwischen allen dreien wechseln; die Save-Bestätigung (#3) schützt vor
Versehen.

**Umbau:**
- `ProviderUpdateRequest` bekommt `type: ProviderType`; `toUpdateRequest()` gibt `editor.type` mit.
- `updateProvider` nutzt `request.type` statt `existing.type`: neue Typ-Credentials schreiben, **alte
  Typ-Secrets löschen** (M3U-URL/Mode + Xtream-Trio) und bei Wechsel weg von M3U-Datei die Disk-Datei
  löschen (Cleanup-Logik ist da, muss auf Typwechsel erweitert werden).
- Editor: 3-Wege-Quellwahl auch im Edit-Modus zeigen (statt nur URL/Datei-Toggle); `editor.type` mutierbar.
- Validierung: bei gewechseltem Typ sind dessen Felder Pflicht (Xtream: Server/User/Pass; M3U-URL: URL;
  M3U-Datei: Inhalt). Gleicher Typ = „leer = behalten".
- Switch-Erkennung (#3) erweitern: `originalType != type || (M3u && originalMode != mode)`.
- Bestätigungs-Popup deckt alle Übergänge ab (richtungsabhängiger Text je Von/Zu-Typ).

**Katalog/Reconcile:** Der Importer räumt provider-weit auf (`existingChannels - importedIds` →
`deleteChannels`, analog Kategorien/Movies/Series inkl. Favoriten/Verlauf/EPG-Mappings der entfernten
Einträge, [RoomCatalogImportRepository.kt:229-240](../data/media/.../RoomCatalogImportRepository.kt)). Ein
Typwechsel + Refresh (via `onProviderSaved`) baut den Katalog also sauber neu; alte Einträge fallen raus.
**Zu verifizieren bei Umsetzung:** dass `existingChannels`/Movies/Series/Kategorien wirklich *alle*
Provider-Einträge laden (nicht typ-gefiltert) und alte `M3uStreamReferenceStore`-Refs beim Wechsel weg von
M3U kein Problem sind (Xtream nutzt sie nicht → höchstens verwaiste, harmlose Einträge; ggf. mit räumen).

**Risiko/Umfang:** mittel-groß — bricht die bisherige Invariante „Provider-Typ unveränderlich". Kann als
eigener Schritt **nach** #1-#3 laufen oder mit rein. Entscheidung offen.

## #5 Add-Modus: Bestätigung „Wird als {Typ} gespeichert"

**Heute:** Beim Anlegen kann man zwischen M3U-URL / M3U-Datei / Xtream tabben; Save nimmt den zuletzt
gewählten Button. Wer eine URL eintippt, dann auf Xtream tappt und Xtream-Daten eingibt, speichert als
Xtream — ohne Hinweis.

**Ziel:** Beim Speichern im Add-Modus kurz bestätigen, als welcher Typ gespeichert wird.

**Vorschlag zwei Varianten:**
- **A (immer):** Jeder Add zeigt „Wird als {Typ} gespeichert — OK?". Konsistent mit #3, aber ein Extra-Klick
  bei jedem Anlegen.
- **B (nur bei Mehrdeutigkeit, Empfehlung):** Popup nur, wenn Felder eines *anderen* als des gewählten Typs
  befüllt sind (z.B. URL eingegeben, aber Xtream gewählt). Deckt genau den beschriebenen Verwechsel-Fall,
  nervt aber den sauberen Normalfall nicht.

Popup nutzt denselben Typ-Text wie das Badge (#2) / die Editor-Buttons — richtungslos, nur Ziel-Typ.

## #6 Diagnostics-Logging (Recherche-Ergebnis)

**Heute geloggt:** Refresh (`PlaylistRefreshSucceeded/Failed`, EPG), Backup, Player, `db`, Lifecycle,
`connection`/`provider_test_failed` (MainActivity:952). **Provider-Speichern/-Bearbeiten/-Löschen/-Typwechsel
wird NICHT geloggt.** Der Umbau bricht nichts an den Diagnostics (Sanitizer redigiert URLs; File hat keine).

**Empfehlung (optional, aber da wir den Save-Pfad eh anfassen):** beim Provider-Speichern ein
sanitisiertes Event loggen — Kategorie `provider`, mit `resultType` (M3U-URL/M3U-Datei/Xtream) und
`switchedFrom` (Original-Typ/Mode, falls Wechsel). **Keine Secrets/URLs** — nur Typ/Mode-Enums. Hilft
Support, späteres Refresh-/Import-Verhalten zu erklären („Provider ist jetzt Xtream").

## #7 Error-Handling im Editor (Recherche-Ergebnis) — MUSS beachtet werden

Validierung läuft **zweistufig**:
1. Client-seitig `firstSaveError(...)` ([ProviderEditor.kt:281](../feature/settings/.../ProviderEditor.kt))
   — reddent Feld + springt Fokus, ruft `onSave` erst bei fehlerfrei.
2. Panel `validationMessageResolved()` → `ProviderEditorState.validationMessage(...)` als Backstop (Meldung).

**Lücken für den Umbau (beide Stellen anpassen):**
- `validationMessage`: Xtream-Zweig ist `if (!isEditing)` → im Edit-Modus werden Xtream-Felder NICHT
  geprüft (Annahme: Credentials liegen). Bei Wechsel M3U→Xtream sind die Xtream-Daten aber NEU und Pflicht.
- M3U-Zweig nutzt `allowExistingSource = isEditing` → leeres Feld gilt als „behalten". Bei Wechsel
  Xtream→M3U (oder Mode-Wechsel) gibt es KEINE gespeicherte Quelle des neuen Typs → leer muss Fehler sein.
- `firstSaveError`: `xtreamRequired = !isEditing` → analog anpassen.

**Neue Regel (beide Stellen):** „leer = behalten" nur wenn `isEditing && type == originalType &&
mode == originalMode` (kein Wechsel). Bei Wechsel sind die Zielfelder Pflicht. Ableitbar aus dem neuen
`originalSourceMode`/`originalType`.

**Reihenfolge im Save-Flow** ([ProviderSettingsPanel.kt:346](../feature/settings/.../ProviderSettingsPanel.kt)):
Validierung (leere/Doppel-Felder) BLOCKT weiterhin zuerst → dann NEU: Bestätigungs-Popup (#3/#4/#5) →
dann Test → dann Persist. Popup sitzt also zwischen Validierung und Test, ändert die bestehende
Fehler-/Fokus-Logik nicht (Name/Doppelname bleiben unverändert, typ-unabhängig).

**Kein Konflikt sonst:** Name-blank/Duplicate-Name sind typ-unabhängig; Duplicate-URL nur für URL-Mode
(File/Xtream ohne) — bleibt korrekt.

## #8 Impact-Analyse Typwechsel — was alles am Provider-Typ/-Source hängt

Ergebnis dreier Read-only-Sweeps (Katalog-Reconcile / Xtream+type-keyed State / Secrets+Disk+Refs).

### MUSS beim Typwechsel behandelt werden (sonst offene Baustelle)

1. **Credentials-Wipe (inkl. Sicherheits-Item).** `writeCredentialsForUpdate` keyt auf `existing.type` und
   räumt die Gegenseite NICHT ([RoomProviderRepository.kt:242-256](../data/provider/.../RoomProviderRepository.kt)).
   - Xtream→M3U lässt `xtream_server_url/username/`**`password`** liegen (echtes Secret!).
   - M3U→Xtream lässt `m3u_url` / `m3u_source_mode` / `m3u_inline_content` liegen.
   - **Fix:** `updateProvider` nutzt `request.type`; bei erkanntem Typwechsel VOR dem Schreiben des neuen
     Typs die ALTE Quelle komplett wipen — vorhandenes `deleteCredentials(sourceConfigKey)` (löscht alle 6
     Felder, [:303-308](../data/provider/.../RoomProviderRepository.kt)) + `m3uFileSourceStore.delete(providerId)`
     + Stream-Refs löschen. Reuse statt Feld-für-Feld.

2. **Xtream-Account-Info zurücksetzen.** `xtreamExpiresAtMillis` + `xtreamMaxConnections` überleben
   `existing.copy(...)` und werden **nach Wert, nicht nach Typ** angezeigt
   ([ProviderSettingsPanel.kt:583-588](../feature/settings/.../ProviderSettingsPanel.kt)) → ein von Xtream
   auf M3U gewechselter Provider zeigt weiter Ablaufdatum/Verbindungen. **Fix:** beim Wechsel weg von Xtream
   beide auf `null`.

3. **Disk-Datei bei M3U-Datei→Xtream.** Update-Xtream-Zweig löscht `<providerId>.m3u` nicht (Create-Zweig
   schon). **Fix:** beim Wechsel weg von File löschen (im Wipe aus #1 enthalten).

4. **M3uStreamReferenceStore-Waisen bei M3U→Xtream.** Refs werden nur beim M3U-Import ersetzt, bei
   Xtream-Import nie angefasst; `deleteProviderReferences` hat **null** Produktions-Aufrufer. **Fix:** beim
   Wechsel weg von M3U Refs löschen (Store in `RoomProviderRepository` injizieren).
   - **Bonus/Pre-existing Bug:** `deleteProvider` löscht die Stream-Refs ebenfalls NICHT → Waisen bleiben
     auch beim normalen Löschen. Sollten wir gleich mit fixen (gleiche Store-Injektion).

### Bestätigt sauber (keine Aktion nötig)
- **Katalog-Reconcile ist provider-weit + typ-übergreifend:** Kanäle, Kategorien (alle Typen), Movies,
  Series, Seasons, Episoden + abhängige Favoriten/Fortschritt/Verlauf/EPG-Mappings/EPG-Programme werden beim
  Refresh des neuen Typs vollständig entfernt (M3U- und Xtream-remoteIds sind disjunkt →
  keine Fehl-„Updates"). Kein DB-Waise.
- **Series-Details-Job:** self-healing (REPLACE-Policy + Laufzeit-Typprüfung).
- **userAgent:** harmlos für File. **xtreamOutputFormat:** harmloser Rest (nur für Xtream gelesen,
  Editor-Zeile typ-gated). **WatchNext / Deep-Links / Home/Movies/Series/Live-TV / Backup-Restore:** keine
  `provider.type`-Abhängigkeit bzw. Rebuild statt Live-Switch.
- **m3u_source_mode:** wird bei M3U-Writes immer neu gesetzt; `readM3uSourceMode` defaultet sicher auf Url.

### Latenter Punkt (nur Notiz, nicht Teil dieses Umbaus)
- `upsertEpisodes` löscht KEINE Episoden-Favoriten beim Entfernen. Inert, weil Episoden aktuell nicht
  favorisierbar sind (`addFavorite` nie mit `MediaType.Episode`). Nur relevant, falls Episoden-Favoriten je
  eingeführt werden.

### Zwei Entscheidungen

**(a) Katalog beim Typwechsel proaktiv leeren, oder auf Refresh-Reconcile verlassen?**
- `updateProvider` leert den Katalog heute nicht; der Post-Save-Refresh reconcilet. Im Fenster
  save→refresh-fertig zeigt der Provider den ALTEN Katalog unter dem NEUEN Typ; ein Wiedergabeversuch
  scheitert dann *graceful* (MissingStreamReference / Netzwerkfehler), kein Crash.
- **Proaktiv leeren** (wie `deleteProvider` es tut) = sofort sauberer/leerer Zustand, kein Falsch-Stream-Fenster;
  bei fehlgeschlagenem Refresh bleibt der Provider leer statt „alt+falsch".
- **Empfehlung:** proaktiv leeren beim Typwechsel (deterministisch, ehrlicher Zustand).

**(b) Bestätigungs-Popup beim Typwechsel warnt vor Verlust von Favoriten/Verlauf/Fortschritt?**
- Ein Typwechsel entfernt garantiert den alten Katalog → dessen Favoriten/Verlauf/Fortschritt gehen
  verloren (bei reinem URL→andere-URL nur, soweit stableKeys nicht überlappen; bei Typwechsel immer).
- **Empfehlung:** Popup-Text beim **Typwechsel** um einen Hinweis ergänzen („Favoriten/Verlauf dieser
  Playlist gehen dabei verloren"). Bei reinem URL↔Datei-Wechsel optional derselbe Hinweis.

## Gates (bei Umsetzung)
- detekt / assembleDebug / test grün. Neue Strings nur in `:core:designsystem` (de+en).
- Einfache Instrumented-/Unit-Checks für #3 (Switch-Erkennung, Feld-Erhalt); Editor-Navigation testet User.

## Reihenfolge-Vorschlag
#1 (trivial) → #2 (klein) → #3 (mittel, State + Dialog).
