# Plan: Timeshift-Redesign — natives DVR-Fenster primär, Capture-Engine als Backup

Status: **Phase 1 + 2 DONE** (2026-07-09, Commits 5bd904b/476ccb3/6f4e508). Phase 3 (Fall B) offen/deferred.
**Spike-Ergebnis (Phase 0) hat den Ansatz gedreht** — siehe unten.

## Spike-Erkenntnis (entscheidend)

Debug-Spike (`app/src/debug/.../TimeshiftSpikeActivity.kt`) auf ARD-HLS (`daserste …/master.m3u8`) gezeigt:
- Bild rendert, `status=Playing`.
- ExoPlayer meldet ein **natives server-seitiges DVR-Fenster von 7200s = 2 Stunden**; Seek-zurück (−5 min)
  springt sofort auf andere Szene, läuft flüssig weiter. **Kein eigener Capture nötig.**

→ Für **HLS-Streams mit DVR-Fenster** ist Timeshift praktisch geschenkt. Der bisherige
`setBackBuffer`+`SimpleCache`+virtuelles-Fenster-Ansatz ist dafür unnötig und begrenzt das native Fenster
künstlich.

## Ziel (öffentliche App → breite Stream-Kompatibilität)

Zwei Wege, **automatisch nach Stream-Typ** gewählt:

- **Fall A — natives DVR-Fenster (primär):** M3U/HLS-Broadcaster + Xtream-HLS-Output. ExoPlayer-Seek im
  nativen `seekableWindow`. Deckt die breite Masse (Broadcaster oft Stunden).
- **Fall B — lokale Capture-Engine (Backup, dedizierte spätere Phase):** nur für progressive MPEG-TS ohne
  natives Fenster (Xtream-`.ts`) bzw. HLS-Live-Edge-only. Immer-an, rollendes Fenster, segment-basiert
  (StreamVault-Muster: Disk-first, Front-Trim, 2-GB-LRU, Frei-Speicher-Floor). Bis B existiert: sauberer
  Hinweis „begrenztes/kein Timeshift" für solche Streams.

### Schlüssel-Hebel: Xtream-Ausgabeformat (wie TVMate)
Der Resolver baut Xtream-Live aktuell **hardcoded `.ts`** (`PlaybackStreamResolver`,
`…/live/user/pass/id.ts`) → nie nativ seekbar. **Neue Provider-Option „Ausgabeformat" (MPEG-TS / HLS),
Default HLS** → Resolver baut `…/id.m3u8`, ExoPlayer nutzt das native Fenster wo der Server es liefert.
Ehrliche Nuance: Xtream-HLS-Fenster ist oft klein (Live-Edge, server-abhängig); tiefes Xtream-Rewind bleibt
Fall B oder der Server-Timeshift-Endpoint (den wir für Catch-up schon bauen).

### Auto-Detect zur Laufzeit
Nach `play()` das native Fenster lesen (`player.isCurrentMediaItemSeekable` / Timeline-Window-Dauer):
ausreichend seekbar → Fall A (natives Fenster als Timeline). Sonst → Fall B (falls gebaut) bzw. Hinweis.

## Phasen

### Phase 1 — Fall A sauber + Rückbau ✅ DONE (Commit 476ccb3, 2026-07-09)
- ✅ **Rückbau** `setBackBuffer` (RAM-OOM-Quelle, ~1GB bei langem Live) + `backBufferMinutes`. Virtuelles
  `timeshiftWindowMillis`/`liveEdgeOffset`/`seekTimeshiftBy`/`timeshiftProgressState` + Timeshift-Start-Fallback
  entfernt. `SimpleCache`/`usesDiskCache`-Disk-Pfad bleibt (dormant, Fall-B-Substrat).
- ✅ **Controller/Engine:** Live-Channel `seekable=true`; `seekBy`/`seekToLiveEdge` delegieren nativ
  (`player.seekTo` / `seekToDefaultPosition`). `isNativeLiveTimeshift = Channel && request.seekable &&
  player.isCurrentMediaItemSeekable`. `timeshiftWindowMillis` = native DVR-Tiefe (`player.duration`).
- ⚠️ **Wichtige Spike-Korrektur (Emulator, CMAF-Live):** `player.currentLiveOffset` ist unzuverlässig (manche
  Manifeste deklarieren keine Live-Zeit → UNSET), und `duration − position` taugt NICHT als „hinter-Live"-Maß
  (bei Live ist `position` = Zeit-seit-Join unbegrenzt, `duration` = DVR-Tiefe). **Lösung: Live-Rand = Running-Max
  der nativen Position**; Offset = `liveEdge − position`; position/duration aufs DVR-Fenster remappt (Progress-Bar
  100% am Rand). Auf Emulator validiert: Fenster 599s erkannt, −5min-Seek in State+Video, Zurück-an-Rand.
  Ceiling: kein Wall-Clock-Advance → langer PAUSE am Live-Rand wächst den Offset erst beim Weiterspielen.
- ✅ **Xtream-Ausgabeformat-Option** (komplette Kette + Editor-UI, DB-Migration 14→15). Commit 5bd904b.
- ✅ **Auto-Detect** via `isCurrentMediaItemSeekable`; nicht-seekbar → kein Timeshift-UI (`player_timeshift_unavailable`-Hinweis bleibt).

### Phase 2 — UI-Vereinfachung ✅ DONE (Commit 6f4e508, 2026-07-09)
- ✅ Timeshift-Toggle, Max-Dauer, Timeshift-Speicher aus `PlaybackSettingsPanel` entfernt (+ Picker,
  `PlaybackTimeshiftStorageMode`-Enum, State-Felder, VM/Mapper). Immer-an, Fenster kommt vom Server.
- ✅ `PlaybackRequestFactory.channelRequest`: `timeshift`-Param raus, `seekable=true` (native Auto-Detect).
  `timeshiftConfig()` (app) entfernt.
- ⏳ Offen (harmlos): `PlaybackPreferences.timeshift*` + Backup-Felder als Alt-Felder gelassen (kein
  Migrations-Risiko); tote Timeshift-Strings (spawn-task für Cleanup).

### Phase 3 (dediziert, später) — Fall B Capture-Engine
Nur für MPEG-TS-ohne-Fenster. Immer-an, rollendes 60-min-Fenster, `TimeshiftRecorder` + `TimeshiftDiskManager`
(2-GB-Budget/LRU/Frei-Floor/Waisen-Cleanup, RAM-Fallback ~5 min + Trim-Kill), lokale rollende Live-HLS-Playlist
in-place referenziert, ein ExoPlayer drauf. Erst wenn Bedarf real da ist (viele TS-only-Nutzer). Eigener Spike
für Progressive-TS-Chunk-Playback (die offene Unbekannte) vor dem Bau.

## Betroffene Dateien
- `data/playback/.../PlaybackStreamResolver.kt` (Xtream `.m3u8`/`.ts`)
- `data/provider/.../ProviderConfigurationModels.kt` + Provider-Editor (Ausgabeformat-Option)
- `core/player/.../VivicastPlayerController.kt` (Rückbau setBackBuffer/virtuelles Fenster; natives Fenster nutzen;
  Auto-Detect)
- `feature/settings/.../PlaybackSettingsPanel.kt`, `.../SettingsModels.kt`, `.../SettingsViewModel.kt`, Mapper;
  `app/.../SettingsPreferenceMappers.kt`; `core/datastore/.../UserPreferencesStore.kt`; Strings
- Phase 3: neu `core/player/.../TimeshiftRecorder.kt`, `.../TimeshiftDiskManager.kt`

## Verifikation
- Spike-Activity bleibt (debug-only) für weitere Stream-Tests. Emulator + Test-Playlisten (Memory
  `test-playlists`): Broadcaster-HLS → tiefes Fenster + Seek; nicht-seekbarer Stream → sauberer Hinweis.
- Unit-Tests: Auto-Detect-Schwelle, Xtream-URL-Bau (`.m3u8`/`.ts`), Fall-B-Limit-Helfer (Phase 3).
- Gates je Phase: `.\gradlew.bat detekt test assembleDebug`.

## Offen / Risiken
- Xtream-HLS-Fenster-Tiefe ist server-abhängig (oft klein) — real getestet werden, sobald ein Xtream-Test
  verfügbar ist. Bis dahin deckt Fall A garantiert die Broadcaster-HLS ab.
- Progressive-TS-Chunk-Playback (Fall B) = weiterhin die Kern-Unbekannte → eigener Spike vor Phase 3.
