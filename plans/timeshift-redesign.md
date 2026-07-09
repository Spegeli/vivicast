# Plan: Timeshift-Redesign — natives DVR-Fenster primär, Capture-Engine als Backup

Status: **Phase 1 + 2 DONE** (2026-07-09, Commits 5bd904b/476ccb3/6f4e508). **Phase 3 vollständig geplant**
(Fall B, 1-Verbindung-Tee) — nächster konkreter Schritt ist der TS-Capture-Spike, dann Bau-Entscheidung.
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

### Phase 3 — Fall B: lokale TS-Capture (progressive MPEG-TS ohne natives Fenster) — GEPLANT

Nur für Kanäle, deren Stream **kein** natives Seek-Fenster hat (progressive MPEG-TS, v.a. Xtream-`.ts`).
Zielgruppe schmal (Xtream default jetzt HLS, Broadcaster HLS) → **Spike zuerst, dann Bau-Entscheidung.**

**Referenz-Mechanismus (StreamVault, `IPTV-APPS/StreamVault-IPTV-master/player/.../timeshift/`, validiert das Konzept):**
- StreamVault: **zweite, unabhängige OkHttp-Verbindung** lädt die Live-URL parallel (2× Bandbreite).
- Byte-Stream (64-KB-Reads) nach **Wall-Clock in ~2s-`chunk-N.ts`** schneiden — **keine TS-Paket-Analyse,
  kein PAT/PMT-/Keyframe-Schnitt.** Chunks dienen nur dem **rollenden Fenster + Eviction** (älteste löschen).
- Bei Pause/Rewind: **alle behaltenen Chunks zu einer contiguous `buffer.ts` konkatenieren** (= exakt der
  mitgeschnittene Byte-Stream) → als `file://`-MediaItem an ExoPlayer. Dessen **`TsExtractor` baut die
  PTS-Seek-Map** über die progressive Datei und seekt nativ. **Kein Remux, kein FFmpeg, kein m3u8 für TS.**
- Live↔Timeshift = **MediaItem-Swap** (Live-URL ↔ `buffer.ts`). Zurück-zu-Live = Live-URL neu `prepare`.
  Jeder Rewind erzeugt einen neuen Snapshot-Dir; alte werden „retired" + off-lock gelöscht.
- Disk-Backend (default) oder RAM-Backend (≤5 min, `onTrimMemory`-Kill). Rollend 15–60 min (default 30/wir 60).
- **Keine neuen Abhängigkeiten:** OkHttp + Media3-`TsExtractor` + `java.io`.

→ **Die frühere Kern-Unbekannte („TS in dekodierbare Chunks schneiden") entfällt.** Es wird nicht
schnitt-dekodiert, sondern reassembliert; Seek macht ExoPlayers TsExtractor auf der concat-Datei. Rest-Risiko
klein (StreamVault in Produktion) → 1 Spike genügt.

**Vivicast-Abweichung — EINE Verbindung (Pflicht, nicht wie StreamVault):**
StreamVaults 2. Capture-Verbindung würde bei **Xtream-Accounts mit `max_connections: 1`** (häufig, und das ist
genau die Fall-B-Zielgruppe) den Anbieter-Reject/Playback-Kill auslösen. Daher: **`TeeDataSource`** —
ExoPlayer spielt die Live-URL, und der Tee schreibt **jedes gelesene Byte** parallel in die rollenden
2s-Chunk-Dateien (dieselbe eine Verbindung, die ExoPlayer eh nutzt). Kette:
`TeeDataSource(upstream = DefaultDataSource(live-URL, userAgent), sink = RollingChunkSink)`.
- **Kosten von „immer-an" sinken damit auf nur Disk-Schreiben** (keine Extra-Bandbreite) — der 2×-Einwand entfällt.
- **Trade-off:** während der Nutzer **aktiv zurückgespult** ist, liest ExoPlayer die lokale `buffer.ts` → die eine
  Verbindung lädt kein Live → der Puffer wächst in dem Moment nicht mit; Zurück-zu-Live macht einen frischen
  Live-Request am echten Rand. Akzeptabel (man schaut ja gerade die Vergangenheit, nicht den Live-Rand).
- Rest wie oben: Wall-Clock-2s-Chunks, Concat-zu-`buffer.ts`, TsExtractor-Seek, MediaItem-Swap, DiskManager.

**Architektur (Vivicast), engine-intern (StreamVault-Stil):**
- Neues Package `core/player/.../timeshift/`: `LiveTimeshiftRecorder` (Capture-Session: OkHttp-Download,
  2s-Chunk-Slicing, Snapshot-Concat, Backoff-Retry) + `TimeshiftDiskManager` (2-GB-LRU→80 %, 200-MB-Frei-Floor,
  Orphan-Cleanup, per-Session-Dir) + `TimeshiftModels`. Injizierbar: OkHttp-Client, `clock`, `Dispatchers.IO`,
  `cacheDir`. Eigene Coroutine je Session, Mutex-guarded, Call-Cancel bei stop/Kanalwechsel/release.
- **`Media3PlaybackEngine` besitzt den Recorder + macht den Swap.** `seekBy`/`seekToLiveEdge` delegieren schon
  an die Engine → sie fängt sie für eine aktive Fall-B-Session ab (wie StreamVaults `seekBackward`): erst
  Snapshot bauen, MediaItem auf `buffer.ts` swappen, Pending-Seek bei `READY` anwenden.
- **Auto-Detect:** nach `start()`+`READY` `isCurrentSeekable` prüfen. `Channel && !isCurrentSeekable` →
  Recorder-Session starten. Seekbar (Fall A) → kein Capture.

**Integration mit der Phase-1-Fenster-Logik (die eigentliche Design-Frage):**
- Der Controller leitet Timeshift-UI aus `isCurrentSeekable` ab. Bei Fall B läuft aber die Live-URL
  (`isCurrentSeekable=false`), während der Recorder schon ein rewindbares Fenster aufbaut → der Controller muss
  „Timeshift verfügbar, Fenster = bisher mitgeschnittene Sekunden" zeigen.
- **Lösung:** Engine meldet Capture-Zustand (`captureActive`, `capturedWindowMillis`) an den Controller;
  `withNativeTimeline` faltet es ein: Fall-B → `timeshiftWindowMillis = min(capturedMillis, 60 min)`, wächst
  von 0 hoch. Nach dem Swap auf `buffer.ts` wird der Kanal seekbar → die vorhandene **Phase-1-Running-Max-Offset-
  Logik läuft direkt auf der buffer.ts-Timeline weiter** (kein Sonderpfad für Offset/Position).

**UI:** Fenster wächst von 0 (gerade eingeschaltet) bis 60 min; Badge zeigt die aktuelle Tiefe. Vor genug
Mitschnitt ist Seek-back begrenzt. Kein sichtbarer „Aufnahme läuft"-Indikator (immer-an, unsichtbar).

**Spike zuerst (ENTSCHIEDEN — Pflicht, de-risk, eigene Datei):** `app/src/debug/.../TsCaptureSpikeActivity.kt`.
Progressive-TS-Live-URL per adb-Extra; **`TeeDataSource` auf ExoPlayers Live-Download (1 Verbindung)** → 2s-Byte-
Chunks nach cacheDir; nach ~30 s alle zu `buffer.ts` konkatenieren; ExoPlayer draufsetzen; −20 s/−2 min seeken.
**Beweist zwei Dinge:** (1) TsExtractor seekt die live-mitgeschnittene concat-`.ts` sauber (Bild + flüssig,
korrekte Dauer); (2) der Tee auf der Playback-Verbindung liefert die Bytes vollständig (kein 2. Download nötig).
Braucht eine progressive-TS-Live-Test-URL (iptv-org hat rohe `.ts`; sonst Xtream-`.ts`).

**Aufräumen (Teil von Phase 3):** den nach Phase 2 toten `timeshiftCache`/`usesDiskCache`/CacheDataSource-Pfad in
`Media3PlaybackEngine.start()` + `Media3PlayerFactory.kt` entfernen (falsches Mechanismus für Fall B; `SimpleCache`
war ein Read-Through-Cache, kein rewindbarer Recorder).

## Betroffene Dateien (Phase 3)
- neu: `core/player/.../timeshift/LiveTimeshiftRecorder.kt`, `.../TimeshiftDiskManager.kt`, `.../TimeshiftModels.kt`
- `core/player/.../VivicastPlayerController.kt` (Engine besitzt Recorder; Swap in start/seek/seekToLiveEdge;
  Capture-State in `withNativeTimeline`; toten Cache-Pfad raus)
- `core/player/.../Media3PlayerFactory.kt` (`usesDiskCache`/`SimpleCache` raus)
- `core/network/.../NetworkClientFactory` (OkHttp-Client für den Recorder bereitstellen, falls nicht schon)
- neu Spike: `app/src/debug/.../TsCaptureSpikeActivity.kt`

## Verifikation
- **Phase 1/2:** erledigt (siehe Commits oben), Emulator-validiert (Broadcaster-CMAF: Fenster 599 s, −5 min-Seek,
  Zurück-an-Rand).
- **Phase 3 Spike:** progressive-TS-Live → 30 s Capture → concat → seek −2 min läuft flüssig.
- Unit-Tests: `TimeshiftDiskManager` (LRU→80 %, 200-MB-Floor, Orphan-Cleanup), `LiveTimeshiftRecorder`
  (Chunk-Rolling/Front-Trim, Snapshot-Concat-Reihenfolge), Auto-Detect-Schwelle.
- Gates je Phase: `.\gradlew.bat detekt test assembleDebug` + Emulator-Smoke.

## Entscheidungen (gelockt) + Rest-Risiken
- **E1 — Vorgehen: ENTSCHIEDEN = Spike zuerst.** Erst `TsCaptureSpikeActivity` (beweist TsExtractor-Seek auf
  live-mitgeschnittener concat-`.ts` + 1-Verbindung-Tee), dann Bau-Entscheidung. Bis dahin: nicht-seekbare Kanäle
  zeigen den vorhandenen „kein/begrenztes Timeshift"-Hinweis.
- **E2 — Capture: ENTSCHIEDEN = immer-an via 1-Verbindung-`TeeDataSource`.** Nutzers Einwand gelöst: keine 2.
  Verbindung (respektiert Xtream `max_connections: 1`), daher keine Extra-Bandbreite → immer-an nur Disk-Kosten.
  Trade-off: kein Live-Mitschnitt während man aktiv zurückgespult ist (frischer Live-Request bei „zu Live").
- **E3 — Backend/Tiefe: Empfehlung** Disk-first, **60 min** rollend, RAM-Fallback 5 min, 2-GB-LRU, 200-MB-Floor
  (StreamVault-Limits). Final beim Bau bestätigen.
- **Rest-Risiko (Spike klärt):** liefert der `TeeDataSource` auf der Playback-Verbindung die Bytes vollständig
  (ExoPlayer liest bei Live-Progressive kontinuierlich am Rand) und seekt TsExtractor die concat-`.ts` sauber?
  StreamVault beweist das Konzept mit 2 Verbindungen; die 1-Verbindungs-Tee-Variante ist die offene Unbekannte.
- Xtream-HLS-Fenster-Tiefe server-abhängig (oft klein) — sobald ein Xtream-Test da ist real prüfen; Fall A deckt
  die Broadcaster-HLS garantiert.
