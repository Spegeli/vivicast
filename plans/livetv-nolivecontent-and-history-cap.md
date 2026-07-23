# Live-TV „kein Live-Import"-Zustand + Channel-History DB-Cap — Plan

**Status:** ✅ ABGESCHLOSSEN + VERIFIZIERT (2026-07-23). DB-Cap = 12. Gates grün (compile / detekt /
Unit-Tests). **Emulator-verifiziert:** prune-androidTest PASS; Live-TV-Browse rendert + Leerzustand-Gate
(kein False-Trigger bei vorhandenem Live-Content) am Emulator geprüft.

Ergänzungen zu bereits umgesetzten Aufgaben (Task 2-Basis + Task 3-Anzeigelimit sind schon drin & grün).

---

## Task 2 — Ergänzung: dritter Leer-Zustand `NoLiveContent`

### Kontext
Home hat drei Zustände; der Live-TV-relevante fehlt noch: **Wiedergabelisten vorhanden + aktiv, aber keine
importiert Live-TV** → auf Home wird die „Zuletzt gesehen"-Zeile einfach ausgeblendet (`hasLive=false`). Im
Live-TV-Bereich soll stattdessen eine **Vollmeldung** erscheinen (wie NoPlaylist / AllDisabled).

### Erkennung (verifiziert)
`mediaRepository.observeHasLiveContent()` == `catalogDao.observeHasActiveChannels()` → **active-provider-aware**
(zählt nur Kanäle unter **aktiven** Providern). Exakt das Signal, das Home nutzt. `hasLiveContent == false` bei
aktiven Providern = „kein Live-Import".

### Änderungen
1. **`LiveTvEmptyReason`** (LiveTvUiState.kt) um `NoLiveContent` erweitern.
2. **LiveTvViewModel**:
   - Neues Feld `hasLiveContent: Boolean = false` + Collector:
     ```kotlin
     coroutineScope.launch {
         mediaRepository.observeHasLiveContent().collect { hasLiveContent = it; rebuild() }
     }
     ```
   - `computeEmptyReason()` erweitern (Reihenfolge wichtig):
     ```kotlin
     when {
         !initialProvidersLoaded -> null
         providersRaw.isEmpty() -> NoPlaylist
         providersRaw.none { it.isActive } -> AllDisabled
         !initialLoadComplete -> null            // Kaltstart läuft → noch keine Meldung
         !hasLiveContent -> NoLiveContent        // aktive Listen, aber 0 aktive Kanäle
         else -> null
     }
     ```
     (bleibt eine kleine, extrahierte Funktion → detekt-Gate für `rebuild` bleibt eingehalten.)
3. **LiveTvEmptyState** (LiveTvRoute.kt): `NoLiveContent`-Zweig → Titel + Body + **zwei** Buttons:
   - „Zu den Wiedergabelisten" → `onOpenPlaylists` (landet auf Playlisten-Übersicht in den Einstellungen)
   - „Einstellungen" → `onOpenSettings` (normale Einstellungen)
   - Fokus: erster Button trägt den `firstFocusRequester` (wie die anderen Zweige).
4. **Strings** (neu, **beide** Locales in `:core:designsystem`):
   - `livetv_no_live_title` — de: „Kein Live-TV in den Wiedergabelisten" / en: „No Live TV in your playlists"
   - `livetv_no_live_body` — de: „Es sind Wiedergabelisten aktiv, aber keine importiert Live-TV-Kanäle.
     Öffne die Wiedergabelisten und aktiviere dort den Live-TV-Import." / en entsprechend
   - `livetv_go_playlists` — de: „Zu den Wiedergabelisten" / en: „Go to playlists"
   - Einstellungen-Button: bestehendes `home_settings` („Einstellungen") wiederverwenden.
   *(Wording ist Vorschlag — bei Review gern anpassen.)*
5. **MainActivity**: **keine** Änderung — `onOpenPlaylists` + `onOpenSettings` sind bereits an `LiveTvRoute`
   verdrahtet (aus Task-2-Basis).

### Tests
- **`FakeMediaRepository`** (LiveTvViewModelTest) muss `observeHasLiveContent()` überschreibbar machen
  (Interface-Default ist `flowOf(false)`), z.B. Konstruktor-Param `hasLive: Boolean = false`.
- **Neuer Test** `activePlaylistNoLive_setsNoLiveContentEmptyReason` (aktiver Provider, `hasLive=false`) → `NoLiveContent`.
- **⚠️ Bestehenden Test fixen:** `activeProvider_hasNoEmptyReason` muss `hasLive=true` setzen — sonst liefert
  der Fake `false` und der Test bekäme jetzt `NoLiveContent` statt `null`.

### Randfall
Kurzer `NoLiveContent`-Flash möglich, während ein Live-importierender Provider gerade importiert
(`initialLoadComplete` schon true, `hasLiveContent` noch false) — identisches Verhalten wie Home; durch das
`initialLoadComplete`-Gate minimiert.

---

## Task 3 — Ergänzung: DB-Cap für Channel History

### Ziel
Aktuell **kein** DB-Cap (`@Upsert`, 1 Zeile pro (Provider, Kanal), dedupliziert, kein Pruning) → wächst mit
jedem je gesehenen Kanal. Nur die Home-Anzeige braucht die History. → **DB auf 2× Anzeige begrenzen = 12**
(Anzeige = 6). Ältere Zeilen (nach `watchedAt`) werden gelöscht.

### Änderungen
1. **`PlaybackDao`**: neue Query
   ```kotlin
   @Query(
       "DELETE FROM channel_history WHERE id NOT IN " +
       "(SELECT id FROM channel_history ORDER BY watchedAt DESC LIMIT :cap)",
   )
   suspend fun pruneChannelHistory(cap: Int)
   ```
   Global (nicht pro Provider) — die Anzeige-Query `observeAllRecentChannels` ist ebenfalls global.
2. **`RoomPlaybackRepository.saveChannelHistory`**: nach `upsertChannelHistory` → `pruneChannelHistory(CHANNEL_HISTORY_DB_CAP)`.
3. **Konstante**: `const val CHANNEL_HISTORY_DB_CAP = 12` (in `:data:playback`), Kommentar: „2× Home-Anzeige
   (`RECENT_CHANNELS_LIMIT = 6`)". *(Falls du lieber = 6 hart cappst: eine Zahl ändern.)*
4. **Keine Migration** — reine `DELETE`-Query, kein Schema-Change, kein Room-Versions-Bump.

### Test
- `RoomPlaybackRepositoryTest` (androidTest): 13 Kanäle mit steigendem `watchedAt` schreiben → nach jedem
  `saveChannelHistory` bleiben genau die **12** neuesten; ältester ist weg.
  *(androidTest → braucht Emulator/Gerät zum Ausführen; kompiliert lokal in den Gates.)*

---

## Verifikation (nach GO)
`.\gradlew.bat assembleDebug` + `detekt` + `:feature:live-tv:test` + `:feature:home:test` grün.
Emulator + `adb logcat -s VCd`: NoLiveContent-Meldung + beide Buttons (Fokus/Navigation), History bleibt ≤12.

## Reihenfolge bei Umsetzung
1. Strings (beide Locales). 2. `LiveTvEmptyReason.NoLiveContent` + VM (`hasLiveContent` + `computeEmptyReason`).
3. `LiveTvEmptyState`-Zweig. 4. Tests (Fake + neu + Fix). 5. DAO-Prune + Repository + Konstante + androidTest.
