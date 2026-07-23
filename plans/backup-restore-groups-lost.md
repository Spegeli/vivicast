# Backup Restore — User-Daten binden nicht an den neu importierten Katalog

> Status: **PHASE 1 IMPLEMENTIERT + GATES GRÜN + ON-DEVICE-END-TO-END VERIFIZIERT (Emulator, echtes
> verschlüsseltes Backup Export→Import mit Passwort). NICHT committet. Phase 2 (EPG manuelle Mappings) +
> WatchNext offen.**
>
> **Kritischer Bug beim manuellen Test gefunden + gefixt (den die ersten androidTests verfehlten):** Nach
> Restore fiel `categories` auf **0** (Original-Symptom „Gruppen weg"). Ursache: `CategoryEntity` hat
> `UNIQUE(providerId, stableKey)`, `upsertCategories` ist `@Upsert` (PK-basiert). Die restaurierte Kategorie
> hat den **richtigen stableKey aber einen anderen PK** (Restore-id aus stableKey, Import-id aus remoteId) →
> `@Upsert` der frischen Kategorie kollidiert am UNIQUE(stableKey), das Update-by-PK trifft nichts → frische
> Zeile still gedroppt; danach löscht `deleteRemovedCategories` die Restore-Zeile → 0. **Fix:** in
> `buildCategories` die `removedIds` **VOR** dem Upsert löschen (Konflikt weg, frische Zeile inkl. State
> insertet sauber). androidTest `restoredCategoryUserStateSurvivesReimportDespiteWrongIdAndRemoteId` ist der
> Guard (ohne Fix `was:<0>`). **Lektion:** der erste („keep-real-id") Test verfehlte den Bug, weil er die
> Restore-Wrong-id nicht nachbildete — der on-device-Test war essenziell.
>
> **On-device-Ergebnis (Emulator, gefixter Build):** 3 Favoriten gebunden + sichtbar, 8 History gebunden
> (inkl. 8 verwaiste Alt-Restore-Zeilen aus einem früheren Bug, per App-Start-Refresh gebunden + der
> Duplikat-Fall zu 1 Zeile gemerged), 43 Kategorien mit erhaltenem State („Lokal" versteckt, „Nachrichten"
> auf Position 1, volle manuelle Reihenfolge) — exakt = Pre-Export-Referenz. 0 FATAL.
>
> **Nebenbefund gefixt:** `RoomCatalogImportRepositoryTest` seedete keinen Provider → seit dem `#11`
> sourceEpoch-Guard (`getProvider==null → merge skip`) lief die ganze androidTest-Klasse auf main **rot**
> (androidTests sind nicht Teil des `test`-Gates, nur mit Emulator) — jetzt Provider-Seed in `setUp`.
>
> Entscheidung: **Strategie B (Post-Import-Reconcile), schema-frei (kein Backup-Format-Bump, maximal
> backward-kompatibel). Scope: ganze Familie** (Kanalgruppen + Favoriten + Filme/Serien-Fortsetzen +
> Kanal-History). WatchNext: **separat/später** (fällt bei B ohnehin fast gratis ab).
>
> **Phase 1 (implementiert):** geteilte id-Builder `:domain/UserDataIds` (favoriteId/playbackProgressId/
> channelHistoryId + stableHash), Live-Aufrufer (`RoomFavoritesRepository`, `PlaybackProgressRecorder`)
> umgestellt; `buildCategories` matcht per stableKey; neuer `PendingUserDataReconciler` (`:data:media`)
> am Ende von `importM3uCatalog`/`importXtreamCatalog`; DAO-Ergänzungen (`find…IdByStableKey` ×4,
> `getPendingFavorites/Progress/ChannelHistory`, `deleteProgressById/deleteChannelHistoryById`); Tests in
> `RoomCatalogImportRepositoryTest` (Reconcile fav/history/progress, unmatched bleibt pending, Kategorie-
> State trotz falschem remoteId, Legacy-Format-Pin).
>
> **Phase 2 (DONE — androidTest-verifiziert, NICHT on-device, s.u.):** EPG **manuelle** Mappings
> (`isManual=true`) überlebten Restore nicht — Restore schreibt `mapping.channelId = channelStableKey` (bare),
> aber `importXmltv` matcht per Row-id (Programme-Mapping `channelById[channelId]` + Auto-Suppression
> `manualMappedChannelIds`). Fix: `RoomEpgRepository.rebindRestoredManualMappings` läuft am Anfang von
> `importXmltv` — restaurierte Manual-Mappings (channelId = stale stableKey) werden per `channelStableKey` →
> Row-id gebunden (id auf Live-`epgMappingId`, alte Zeile per neuem `deleteMappingById` gelöscht), bevor die
> Mapping-Logik läuft. androidTest `restoredManualMappingRebindsToChannelRowIdOnImport` (Diskriminator: mit
> Fix „heute journal"/manual, ohne „Tagesschau"/auto). **On-device NICHT testbar**, solange die
> Manual-Mapping-UI keine Zuweisung erlaubt (kein Weg, ein Restore-Manual-Mapping ohne UI zu erzeugen) — end-
> to-end nachholen, wenn die UI fertig ist.
>
> Backup-Vertrag: `../vivicast-docs/prd/PRD-v1/10-backup-import-requirements.md`.

## Root Cause (bestätigt per Code-Trace)

Restore persistiert User-Daten referenziert per **`stableKey`** (nackter SHA-Hash); die App löst
Katalog-Referenzen überall per **Room-Row-`id`** auf. **Kein Reconcile** gleicht beide nach dem
Katalog-Import ab — obwohl `isPending` + `mediaStableKey` genau dafür existieren (der Schritt fehlt).

| Bereich | Restore schreibt | App löst auf per | Ergebnis |
|---|---|---|---|
| Kanalgruppen | `remoteId=stableKey`, id aus stableKey-Hash | Refresh `buildCategories` matcht per `remoteId` | Restore-Zeilen als „neu" → gelöscht; State weg |
| Favoriten | `mediaId=mediaStableKey` (bare) | `getChannel(providerId, mediaId)` per Row-`id` | unsichtbar |
| Filme fortsetzen | `mediaId=mediaStableKey` | `getMovie(providerId, mediaId)` per Row-`id` | unsichtbar |
| Serien fortsetzen | `mediaId=mediaStableKey` | `getEpisode(providerId, mediaId)` per Row-`id` | unsichtbar |
| Zuletzt geseh. Sender | `channelId=channelStableKey` | `getChannel(providerId, channelId)` per Row-`id` | unsichtbar |
| WatchNext | `isPending=true` | `WatchNextIntegration:42` überspringt pending | fehlt bis Rewatch |

Live-Gegenbeweis: `PlaybackRequestFactory` schreibt `mediaId=movie.id/channel.id/episode.id` (Row-id),
`onToggleFavorite` übergibt `channel.id`. PKs live: `favoriteId=…:favorite:$type:$stableHash(rowId)`,
`playbackProgressId=…:progress:$type:$rowId`, `channelHistoryId=…:history:channel:$rowId`. Restore-PKs
weichen ab (z.B. History-PK **ohne** `:channel:`-Segment) → zusätzlich **Duplikat-Risiko** in „Zuletzt
gesehen", sobald der Sender wieder angesehen wird (anderer PK = zweite Zeile).

**Warum unbemerkt:** `StandardBackupTest` asserted nur Zeilen-**Anzahl** (`favorites.size` …), nicht
Auflösung. In-App-Listen filtern `isPending` nicht (kein DAO tut das) — Sichtbarkeit scheitert an der id.

## Strategie B — Design (konkret)

Zwei Teile, beide **schema-frei** (matchen per stableKey, der auf beiden Seiten identisch ist).

### Teil 1 — Kanalgruppen: `buildCategories` per stableKey matchen
`RoomCatalogImportRepository.buildCategories` (`:271`) keyt `existing` aktuell per `remoteId`. Umstellen auf
`stableKey`: `existing.associateBy { it.stableKey }` + Lookup `existing[categoryStableKey(type, remoteId)]`.
- Kategorie-`stableKey = stableHash("$type:$remoteId")` ist bei Import UND Backup identisch → die
  restaurierte Kategorie (korrekter stableKey, falscher remoteId/id) matcht die frisch importierte.
- Der Refresh baut dann die frische Kategorie (korrekte id/remoteId) und trägt `isHidden`/`manualSortOrder`
  aus der restaurierten Zeile über; die alte (falsche id) fällt in `removedIds` → gelöscht. Netto korrekt.
- **Normale Refreshes unverändert:** stableKey ↔ remoteId sind 1:1 pro Typ, das Verhalten für bestehende
  Kategorien bleibt identisch (nur restaurierte matchen jetzt zusätzlich).

### Teil 2 — Favoriten/Progress/History: Reconcile nach dem Import
Neuer Schritt am Ende von `importM3uCatalog` **und** `importXtreamCatalog`, **nach** der Merge-Transaktion
(Katalog committed/sichtbar), in eigener Transaktion, gefiltert auf `isPending` (No-op bei normalen
Refreshes, da dann nichts pending ist):

```
reconcilePendingUserData(providerId):
  für jede pending Favorite/Progress/History dieses providerId:
    rowId = <lookup Katalog-Row-id per (providerId, mediaType, stableKey)>
    wenn rowId == null: pending lassen (Item evtl. später wieder da) — NICHT löschen
    sonst: baue korrigierte Entität mit
        mediaId/channelId = rowId
        PK id            = <Live-id-Builder>(providerId, mediaType, rowId)
        isPending        = false
        (watch-Daten/sortOrder/createdAt/updatedAt erhalten)
      alte pending Zeile per alter PK löschen, korrigierte upserten
```

Warum PK neu bauen (nicht nur `mediaId` updaten): sonst legt der nächste Live-Save (anderer PK) eine
**zweite** Zeile an → sichtbare Duplikate (v.a. Kanal-History). PK auf Live-Format zwingt Upsert-Dedup.

### Benötigte Ergänzungen
- **CatalogDao** — 4 neue ungated Lookups (die vorhandenen `*ByStableKeys` sind auf `isActive`/
  `includeLiveTv` gegated (deep-link) und daher hier ungeeignet; ein restaurierter Favorit eines gerade
  deaktivierten Providers muss trotzdem binden):
  `findChannelIdByStableKey / findMovieIdByStableKey / findSeriesIdByStableKey / findEpisodeIdByStableKey(providerId, stableKey): String?`
  (`SELECT id FROM <t> WHERE providerId=? AND stableKey=? LIMIT 1`).
- **FavoritesDao / PlaybackDao** — pending-Selektoren:
  `getPendingFavorites(providerId)`, `getPendingProgress(providerId)`, `getPendingChannelHistory(providerId)`
  (`… WHERE providerId=? AND isPending=1`).
- **Geteilte id-Builder** (verhindert Drift live↔reconcile): `favoriteId`, `playbackProgressId`,
  `channelHistoryId` liegen heute private in `RoomFavoritesRepository` bzw. `PlaybackProgressRecorder`. In
  einen geteilten Ort ziehen (z.B. `:domain` oder `:data` ids-util), den Live-Code + der Reconcile nutzen.
  (Alternativ in `RoomCatalogImportRepository` gespiegelt + Test der PK-Gleichheit — Extraktion ist sauberer.)
- Reconcile-Ort: `RoomCatalogImportRepository` hat bereits `favoritesDao`, `playbackDao`, `catalogDao`
  injiziert → der Reconcile lebt dort. `vcLog("backup-reconcile")`-Trace (pending/resolved/dropped je Typ)
  für die manuelle On-Device-Verifikation einbauen.

### Edge Cases (validiert)
- **Provider gone / import skipped** (`result == null`): kein Reconcile (kein Katalog).
- **Unmatched pending** (Item nicht im frischen Katalog): pending **lassen** — heilt sich beim nächsten
  Import, statt User-Daten zu verlieren. (Trade-off: seltene stale pending-Zeilen; akzeptabel.)
- **Fremd-Provider pending**: Reconcile ist pro-Provider → andere warten auf ihren eigenen Import.
- **Reconcile läuft bei JEDEM Refresh**: durch `isPending`-Filter billiger No-op (leere Selektion).
- **Kategorien-`removedIds`**: die restaurierte (falsche-id) Kategorie wird korrekt entfernt und durch die
  frische ersetzt; kein Doppel.

## Tests

### Automatisiert (ich, vor GO-Umsetzung geschrieben; vorher/nachher)
- **RoomCatalogImportRepositoryTest** (neu):
  - Reconcile Favorit/Progress/History: seed pending (Restore-Format-PK, `isPending=true`, stableKey passend
    zu einem Kanal/Film im eingehenden Playlist-Import) → `importM3uCatalog`/`importXtreamCatalog` → assert:
    `isPending=false`, `mediaId==rowId`, auflösbar via `getChannel/getMovie/getEpisode`, PK == Live-Format
    (Duplikat-Schutz), **genau eine** Zeile.
  - Unmatched pending bleibt pending (kein Verlust).
  - Kategorien-State überlebt Refresh: seed restaurierte Kategorie (korrekter stableKey, `isHidden=true` +
    `manualSortOrder`) → Import mit dieser Gruppe → `isHidden`/`manualSortOrder` erhalten.
  - Regression: bestehende „Favoriten/Progress überleben normalen Refresh" + „removed → cascade delete"
    Tests bleiben grün.
- **StandardBackupTest** (erweitern): nicht nur Zeilenzahl — nach Restore + simuliertem Import prüfen, dass
  Favoriten/Continue-Watching/Recent-Channels **auflösen** (die Lücke, die den Bug durchließ).
- **Gates:** `detekt`, `assembleDebug`, `test` grün.

### Manuell (du über UI; ich parallel am Emulator-Logcat)
Grund: Passwort-Eingabe im verschlüsselten Backup-Flow — die Leanback-IME frisst adb-injizierte Keyevents,
ich kann das nicht selbst durchtippen. Ablauf:
1. Ich starte Emulator + `adb logcat -c` + `adb logcat -s VCd` (Reconcile-Trace scharf).
2. Du exportierst über die UI ein Backup mit *bewusst gesetztem* Zustand: ein paar Gruppen versteckt/manuell
   sortiert, ein paar Favoriten, ein angefangener Film/Serie, ein paar zuletzt gesehene Sender. Passwort setzen.
3. Du importierst (Passwort-Eingabe), wartest den Post-Restore-Refresh ab.
4. Wir prüfen gemeinsam: Manage Groups (State da?), Favoriten sichtbar, Home „Filme/Serien fortsetzen"
   + „Zuletzt gesehene Sender" gefüllt; Logcat zeigt pending→resolved-Counts, keine Duplikate.
5. Emulator zuerst (schnell), danach optional physischer TV (langsamer, deckt Timing besser ab).

## Offene Punkte / vor Umsetzung
- **EPG-Channel-Mappings** (`StandardBackupRestorer:91`, `channelId=channelStableKey`): gleiche Familie für
  **manuelle** Overrides (auto-EPG heilt sich per EPG-Refresh). Prüfen, ob der Reconcile die manuellen
  Mappings mitnehmen soll oder ob das ein eigener kleiner Folgeschritt wird. (Nicht Teil des ersten B-Cuts,
  außer gewünscht.)
- **id-Builder-Extraktion**: bestätigen, in welches Modul (`:domain` bevorzugt) die geteilten id-Builder
  ziehen, ohne Zyklen.

## Betroffene Dateien
- `data/media/…/RoomCatalogImportRepository.kt` — `buildCategories` match-key; `reconcilePendingUserData` +
  Aufruf in beiden Import-Methoden.
- `core/database/…/dao/CatalogDao.kt` — 4 `find…IdByStableKey`.
- `core/database/…/dao/FavoritesDao.kt`, `…/dao/PlaybackDao.kt` — 3 pending-Selektoren.
- geteilte id-Builder (neuer util in `:domain`/`:data`) + Aufrufer `RoomFavoritesRepository`,
  `PlaybackProgressRecorder` umgestellt.
- Tests: `data/media/src/androidTest/.../RoomCatalogImportRepositoryTest.kt`,
  `app/src/androidTest/.../StandardBackupTest.kt`.
- **Kein** Change an `StandardBackup.kt` / Exporter / Backup-Format (schema-frei).
