# Plan: Timeshift-Redesign — natives DVR-Fenster primär, Capture-Engine als Backup

Status: **Phase 1 + 2 DONE** (2026-07-09, Commits 5bd904b/476ccb3/6f4e508). **Phase 3: K1-Spike auf echter
Hardware GESCHEITERT** (2026-07-09, Xiaomi Mi TV 4S): Der No-Gap-*Mechanismus* (1 Verbindung, Edge-Follow,
Seek, Phase-1-Integration) läuft, ABER **hand-segmentiertes TS → lokales HLS ist auf einem echten
Hardware-Decoder nicht dekodierbar** (Chroma-/Makroblock-Garbage + Audio-PTS-Discontinuities; naiv UND
keyframe). Der Emulator-Software-Decoder war zu tolerant und hat das verdeckt. → **K1 verworfen. K2 (Concat, kein Re-Mux)
auf echter Hardware + echtem Xtream-Ziel VALIDIERT** (Mi TV 4S, Xtream progressive `.ts`, ~11.8 Mbps HD,
max_cons=1): `buffer.ts` **pixel-scharf**, `seekable=true`, **Seek-back sauber**, **+ Tailing validiert** (wachsende Datei via
`TailingFileDataSource` spielt durchgehend + seekbar). **Alle Mechanik-Fragen geklärt** — Rest ist Plumbing
(Recorder, rollendes Fenster, Engine-Integration). **→ Produktions-Bau-Plan: `plans/fall-b-timeshift-capture.md`**
(diese Datei bleibt der Strategie-/Entscheidungs-Record). **Spike-Ergebnis (Phase 0) hat den Ansatz gedreht** — siehe unten.

## Offene Punkte + Archivierung (Stand 2026-07-09)
**Noch NICHT fertig** — diese Datei bleibt aktiv, bis Fall B produktiv steht:
- **Fall-B-Produktion** (Recorder, Multi-File-Tailing, Detection, Engine-Integration, DiskManager) →
  eigener Bau-Plan `plans/fall-b-timeshift-capture.md`. Inkl. Multi-File-Tailing-Spike (offene Mechanik-Frage).
- **Toter `timeshiftCache`/`usesDiskCache`/CacheDataSource-Pfad** in Engine/Factory entfernen — wird in
  `fall-b-timeshift-capture.md` (Increment 5) miterledigt.
- Harmlos: tote DataStore/Backup-`timeshift*`-Felder + tote Timeshift-Strings (Cleanup-Task-Chip existiert).

**Archivierung:** wenn `fall-b-timeshift-capture.md` komplett umgesetzt ist → beide Plandateien als erledigt
markieren und nach `plans/archive/` verschieben. Bis dahin bleibt diese Datei die aktive Timeshift-Referenz.

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

→ Für StreamVaults **Concat-Swap-Ansatz** entfällt die TS-Schnitt-Unbekannte (reassemblieren statt
schnitt-dekodieren). **ABER** dieser Ansatz erzwingt den MediaItem-Swap live↔`buffer.ts` und — bei nur 1
Verbindung — eine **Inhalts-Lücke** (siehe No-Gap unten): daher nutzen wir Concat NICHT, und die Unbekannte
kommt für unseren Mechanismus teilweise zurück.

**Vivicast-Design — 1 Verbindung UND keine Lücke (Kern):**
Zwei Anforderungen zugleich: (a) Xtream `max_connections: 1` → nur **eine** Verbindung zum Anbieter zu jedem
Zeitpunkt (StreamVaults 2. Capture-Verbindung → Reject/Playback-Kill bei genau der Fall-B-Zielgruppe); (b)
**keine Lücke** — der Mitschnitt darf nie stoppen, auch nicht während man zurückgespult schaut. (Nutzer-Anforderung:
sonst fehlt beim Zurück-zu-Live der Inhalt der Rewind-Dauer und es gibt einen Zeitsprung → TV-untauglich.)
Beides gemeinsam erzwingt: **der Capturer besitzt die eine Verbindung durchgehend, und ExoPlayer spielt IMMER
den lokalen rollenden Puffer** (nie die Live-URL direkt). Lokale Wiedergabe = 0 Netzwerk → der Capturer läuft
ununterbrochen. „Live" = neuester Puffer-Teil, „Rewind" = Seek zurück im lokalen Fenster, „zu Live" = zum Rand.
Kein Rewind-Swap, keine Lücke, 1 Verbindung. **Preis:** kleine Zap-Verzögerung beim Kanalstart (erst muss etwas
Puffer da sein, ~3–5 s) statt der Lücke.

**Der lokale Wiedergabe-Mechanismus (durch Spike ENTSCHIEDEN = K1):**
ExoPlayer muss einen **kontinuierlich wachsenden** lokalen Puffer spielen: Seek-zurück UND dem wachsenden Rand
folgen. **K1 — lokales Live-HLS** (rollende Segmente + laufend aktualisierte `index.m3u8`, kein `#EXT-X-ENDLIST`):
ExoPlayer spielt es als Live-HLS mit DVR-Fenster — nativer Seek + Rand-Folgen + Rebuffering = **bewährter
Player-Pfad** (wie Fall A, nur lokale Segmente). **Vom Spike bestätigt** (siehe „Spike"-Block unten). K2 (tailing
`buffer.ts`) verworfen — nicht nötig.
- **Bekannte Rest-Arbeit (kein Unbekannt mehr):** Segment-Schnitt an **Keyframe-Grenzen** (`random_access_indicator`
  im Adaptation-Field) mit **PAT/PMT je Segment-Start**, sonst Mid-GOP-Artefakte nach Seek (der Spike zeigte das mit
  naivem Wall-Clock-Schnitt). Das ist der klassische Live-TS→HLS-Segmenter — Aufwand, aber Standard-Technik.

**Architektur (Vivicast), engine-intern:**
- Neues Package `core/player/.../timeshift/`: `LiveTimeshiftRecorder` (durchgehender OkHttp-Download der Live-URL,
  TS→Segmente [K1] bzw. rollende Chunks [K2], Backoff-Retry) + `TimeshiftDiskManager` (2-GB-LRU→80 %,
  200-MB-Frei-Floor, Orphan-Cleanup, per-Session-Dir) + `TimeshiftModels`. Injizierbar: OkHttp-Client, `clock`,
  `Dispatchers.IO`, `cacheDir`. Eigene Coroutine je Session, Mutex-guarded, Call-Cancel bei stop/Kanalwechsel/release.
- **`Media3PlaybackEngine` besitzt den Recorder** und spielt ExoPlayer für einen Fall-B-Kanal auf die **lokale
  rollende Quelle** (K1: `file://index.m3u8`; K2: tailing-`buffer.ts`). `seekBy`/`seekToLiveEdge` sind dann normale
  native Seeks im lokalen Fenster — **keine Abfang-Sonderlogik, kein Rewind-Swap.**
- **Auto-Detect:** ExoPlayer startet auf der Live-URL (schnelles Bild); nach `READY` `isCurrentSeekable` prüfen.
  `Channel && !isCurrentSeekable` → Recorder starten + **einmalig** (beim Kanalstart, nicht bei Rewind) auf die
  lokale Quelle umstellen. Seekbar (Fall A) → kein Capture, direkt Live. (Spike prüft, ob das einmalige Umstellen
  glatt läuft oder ob gleich-lokal-starten mit Zap-Delay besser ist.)

**Integration mit der Phase-1-Fenster-Logik:**
- Sobald ExoPlayer auf der lokalen Quelle spielt, ist der Kanal **seekbar** → die **Phase-1-Running-Max-Offset-
  Logik greift direkt** (Fenster = lokale DVR-Tiefe = mitgeschnittene Sekunden, wächst von 0 bis 60 min). Kein
  Sonderpfad für Offset/Position/Live-Rand.
- Vor der Umstellung (noch Live-URL, `isCurrentSeekable=false`): Controller zeigt „Timeshift baut auf" bzw. den
  bestehenden Hinweis, bis genug Puffer da ist.

**UI:** Fenster wächst von 0 (gerade eingeschaltet) bis 60 min; Badge zeigt die aktuelle Tiefe. Kein sichtbarer
„Aufnahme läuft"-Indikator (immer-an, unsichtbar).

**Spike (DONE 2026-07-09):** `app/src/debug/.../TsCaptureSpikeActivity.kt` + `LiveTsSegmenter.kt`, getestet auf
echtem progressive-TS (Tvheadend `?profile=pass`), Emulator UND Xiaomi Mi TV 4S.
- **Mechanismus bestätigt:** 1-Verbindung-Capture, lokales Live-HLS, Seek, **Edge-Follow ohne Lücke** (Fenster
  wuchs 16 → 116 → 164 s, Zurück-zu-Live am Rand), Phase-1-Fenster-Logik greift direkt (`timeshift=true`).
- **❌ K1 (hand-segmentiertes TS → lokales HLS) auf echter Hardware NICHT dekodierbar:** Chroma-/Makroblock-
  Garbage + `AudioSink UnexpectedDiscontinuityException` (PTS-Sprünge). Betrifft **naiven Wall-Clock-Schnitt
  UND Keyframe-Schnitt (mit/ohne PAT/PMT-Prepend)**. Ursache: ein echter HW-Decoder ist strikt gegenüber
  PES-Framing/PTS-Kontinuität; ein handgeschriebener Re-Segmenter erzeugt kein konformes TS. Der Emulator-
  Software-Decoder war tolerant und hat das verdeckt (der eine „saubere" Emulator-Frame war low-motion direkt
  nach Keyframe).

### Hardware-Ergebnis → Neu-Bewertung Fall B (K1 tot)
Hand-Re-Segmentieren ist raus. Verbleibende Wege:
- **K2 — Concat statt Segmentieren (StreamVault-Original) — ✅ AUF ECHTER HARDWARE + ECHTEM ZIEL VALIDIERT
  (2026-07-09):** rohe Bytes NICHT re-muxen, sondern verbatim in **eine `buffer.ts`** (byte-identisch). Spike
  `RawTsRecorder` + Controller, getestet auf der **Xiaomi Mi TV 4S** mit (a) Tvheadend-`.ts` und (b) einem
  **echten Xtream-Provider** (`get_live_streams` → `.ts` = `video/mp2t`, progressive MPEG-TS, ~11.8 Mbps Echtzeit-
  HD, **`max_connections: 1`** — das exakte Fall-B-Szenario). Ergebnis: `buffer.ts` spielt **pixel-scharf** (kein
  Chroma-/Makroblock-Garbage, keine Audio-PTS-Fehler), **`seekable=true`**, und **Seek-back zeigt ein sauberes
  Bild** (`pos 31s→21s`, `behindLive=9s`) — genau da, wo K1 Farbmüll gab. Bestätigt: kein Re-Mux → konform →
  HW-Decoder ok, HW **und** SW-Decoder egal.
  - **Produktions-Hinweis `max_connections: 1`:** nur EINE Verbindung gleichzeitig; Kanalwechsel muss die alte
    Stream-Verbindung **voll schliessen** bevor die neue aufgeht (server-seitige Grace-Period sonst → „max
    connections reached"). Auch Test-Tooling (API-Query + Stream) kollidiert daran.
  - **✅ Tailing VALIDIERT (2026-07-09, Mi TV 4S, Echtzeit-Xtream):** `TailingFileDataSource`
    (`core/player/.../timeshift/`, `BaseDataSource`, blockt am EOF, meldet `LENGTH_UNSET`) + Engine-Branch
    (`request.tailing` → `ProgressiveMediaSource`). Ergebnis: wachsende `buffer.ts` spielt **durchgehend**
    (`status=Playing`, kein `Ended` — Edge-Follow), pixel-scharf, **Seek-back sauber** (`pos 26s→3s`, anderes
    früheres Bild). **Keine Mechanik-Unbekannte mehr.**
  - **Verbleibend = reines Plumbing (kein Spike mehr):** (a) **Fenster-Metrik** — für die Tailing-Quelle hat
    ExoPlayer keine `duration` (`window=0`); Controller muss das Timeshift-Fenster aus der running-max-Position
    (bzw. der mitgeschnittenen Dauer) ableiten statt aus `engine.durationMillis`; (b) **`LiveTimeshiftRecorder`**
    (Produktions-Capture: 1 Verbindung, rollendes 60-min-Fenster, Front-Trim); (c) **`TimeshiftDiskManager`**
    (2-GB-LRU, 200-MB-Floor, Orphan-Cleanup); (d) **Engine-Integration** — Auto-Detect Fall B (nicht-seekbarer
    Channel) → Recorder besitzt die Verbindung, Player spielt lokal-tailing (wegen max_cons=1 nie beide); (e)
    **HLS-vs-TS-Erkennung** (Recorder nur für progressive TS; HLS = Fall A direkt).
- **FFmpeg-Segmenter:** libavformat HLS-Muxer erzeugt konforme Segmente. Robust, aber grosse Abhängigkeit
  (JNI/Binary) + Build-Aufwand.
- **Fall B ganz zurückstellen (v1):** Fall A (nativ, fertig) deckt HLS-Broadcaster + Xtream-HLS (Default);
  TS-only-Xtream zeigt den „kein Timeshift"-Hinweis. Fall B = grosser Aufwand für schmale Zielgruppe.

**Aufräumen (Teil von Phase 3):** den nach Phase 2 toten `timeshiftCache`/`usesDiskCache`/CacheDataSource-Pfad in
`Media3PlaybackEngine.start()` + `Media3PlayerFactory.kt` entfernen (falsches Mechanismus für Fall B; `SimpleCache`
war ein Read-Through-Cache, kein rewindbarer Recorder).

## Betroffene Dateien (Phase 3)
- neu: `core/player/.../timeshift/LiveTimeshiftRecorder.kt`, `.../TimeshiftDiskManager.kt`, `.../TimeshiftModels.kt`
  (+ bei K1 ein `TsSegmenter` — PAT/PMT+Keyframe-Schnitt + `index.m3u8`-Schreiber; bei K2 ein tailing-`DataSource`)
- `core/player/.../VivicastPlayerController.kt` (Engine besitzt Recorder; **einmalige** Umstellung auf die lokale
  Quelle beim Kanalstart, kein Rewind-Swap; toten Cache-Pfad raus)
- `core/player/.../Media3PlayerFactory.kt` (`usesDiskCache`/`SimpleCache` raus)
- `core/network/.../NetworkClientFactory` (OkHttp-Client für den Recorder bereitstellen, falls nicht schon)
- neu Spike: `app/src/debug/.../TsCaptureSpikeActivity.kt`

## Verifikation
- **Phase 1/2:** erledigt (siehe Commits oben), Emulator-validiert (Broadcaster-CMAF: Fenster 599 s, −5 min-Seek,
  Zurück-an-Rand).
- **Phase 3 Spike:** progressive-TS-Live → durchgehender Mitschnitt (1 Verbindung) → lokale Quelle → seek −10 min,
  dann wieder vor bis Live **ohne Lücke**; Zap-Delay gemessen.
- Unit-Tests: `TimeshiftDiskManager` (LRU→80 %, 200-MB-Floor, Orphan-Cleanup), `LiveTimeshiftRecorder`
  (Rolling/Front-Trim; K1: Segment-PAT/PMT-Grenzen + Playlist-Fenster), Auto-Detect-Schwelle.
- Gates je Phase: `.\gradlew.bat detekt test assembleDebug` + Emulator-Smoke.

## Entscheidungen (gelockt) + Rest-Risiken
- **E1 — Vorgehen: ✅ Spike DONE & erfolgreich.** No-Gap-Mechanismus (K1) end-to-end auf echtem progressive-TS
  validiert. Bau kann starten. Bis Fall B fertig: nicht-seekbare Kanäle zeigen den „kein/begrenztes Timeshift"-Hinweis.
- **E2 — Capture: ENTSCHIEDEN = immer-an, 1 Verbindung, KEINE Lücke.** Der Capturer besitzt die eine Verbindung
  durchgehend; ExoPlayer spielt immer den lokalen rollenden Puffer → kontinuierlicher Mitschnitt auch während man
  zurückgespult schaut → **keine fehlenden Minuten / kein Zeitsprung** beim Zurück-zu-Live. Respektiert Xtream
  `max_connections: 1`. Preis: kleine Zap-Verzögerung beim Kanalstart statt der Lücke.
- **E3 — Backend/Tiefe: Empfehlung** Disk-first, **60 min** rollend, RAM-Fallback 5 min, 2-GB-LRU, 200-MB-Floor
  (StreamVault-Limits). Final beim Bau bestätigen.
- **Rest-Risiko: durch Spike aufgelöst.** No-Gap-K1-Mechanismus validiert (Play + Seek + Edge-Follow + 1 Verbindung
  + Phase-1-Integration). Verbleibende Arbeit ist **bekannt, nicht mehr Unbekannt:** Keyframe-ausgerichteter
  Segmenter (`random_access_indicator` + PAT/PMT je Segment) gegen die Mid-GOP-Seek-Artefakte. Standard-Technik,
  Aufwand kalkulierbar. Zap-Delay beim Kanalstart im Spike gesehen (Puffer-Aufbau) — beim Bau ggf. mit direktem
  Live-Start + einmaliger Umstellung optimieren.
- Xtream-HLS-Fenster-Tiefe server-abhängig (oft klein) — sobald ein Xtream-Test da ist real prüfen; Fall A deckt
  die Broadcaster-HLS garantiert.
