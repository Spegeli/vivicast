# Plan: Fall B — lokale TS-Capture-Timeshift (Produktion)

Status: in Arbeit (2026-07-09). Umsetzungs-Plan für die **Produktion** von Fall B. Vorgeschichte (K1 verworfen,
K2 concat + Tailing validiert) in `plans/timeshift-redesign.md`. **Multi-File-Tailing-Spike (Schritt 0) gemacht:
Play/Trim/Seek-Decode ✓, aber Positions-/Seek-STEUERUNG über ExoPlayers unbounded-Timeline unzuverlässig → muss
capture-getrieben werden** (der eine echte Rest-Design-Punkt, siehe Schritt-0-Ergebnis unten). Diese Datei ist
der konkrete Bau-Plan.

## Ziel & Scope

Timeshift (Pause/Rewind bis 60 min, rollend) für Live-Kanäle **ohne** natives Server-DVR-Fenster —
progressive MPEG-TS, v.a. **Xtream mit Ausgabeformat TS** (opt-in; Default ist HLS = Fall A). HLS/DVR-Kanäle
bleiben Fall A (nativ, fertig). Zielgruppe schmal, deshalb: robust + einfach halten, nicht übertreiben.

**Gewählter Ansatz: A (Tailing), Ausbaustufe Multi-File.** Begründung (User-Sicht): flüssiges Live ist das
Wichtigste → Tailing liefert es; Multi-File gibt zusätzlich ein rollendes Fenster **ohne** Rollover-Glitch.
(Snapshot-Variante B verworfen: sie macht Live glitchig.)

## Was schon bewiesen/gebaut ist (nicht neu bauen)
- Concat/byte-identisches TS dekodiert sauber auf HW-Decoder (kein Re-Mux).
- `TailingFileDataSource` (`core/player/.../timeshift/`): Single-File-Tailing — spielt wachsende Datei
  durchgehend (`status=Playing`), seekbar, Seek-back sauber. Auf Mi TV 4S + echtem Xtream-`.ts` validiert.
- `PlaybackRequest.tailing` + Engine-Branch (`ProgressiveMediaSource` über die Tailing-DS).
- Controller-Fenster-Metrik für Tailing: Fenster/Offset aus running-max-Position (`window=0`-Problem gelöst),
  `seekToLiveEdge` = weit-vorwärts-Clamp. Unit-getestet.

## Kern-Constraint: `max_connections: 1`
Xtream-Accounts haben oft nur **eine** Verbindung. Daher fürs ganze Design bindend:
- Der **Recorder besitzt die einzige Verbindung** (captured live → lokaler Puffer). Der Player spielt **nur**
  den lokalen Puffer (Tailing-DS), **nie** die Live-URL direkt.
- **Kanalwechsel:** alte Recorder-Verbindung **voll schließen** (Socket zu), dann neue öffnen — sonst
  „max connections reached" (Server-Grace-Period). Ggf. kurzer Guard, bis `active_cons` frei.
- Kein Probing (Live-URL kurz anspielen zum Seekbarkeits-Check) — würde die eine Verbindung verbrauchen.
  **Fall-A/B-Erkennung muss ohne Probe auskommen** (siehe unten).

## Schritt 0 (ZUERST) — Multi-File-Tailing-Spike (offene Mechanik-Frage)
Single-File-Tailing ist bewiesen; **Multi-File + Front-Trim ist der eine unbewiesene Rest.** Vor dem
Produktions-Recorder auf der TV verifizieren:
- **`MultiFileTailingDataSource`** (Debug-Spike): Puffer = mehrere Segment-Dateien (`seg-N.ts`, je ~[SEG_BYTES]).
  Die DS mappt einen **logischen Byte-Offset** (ExoPlayers kontinuierliche Sicht) auf (Datei, Offset-in-Datei),
  liest **nahtlos über Dateigrenzen**, blockt am Ende der neuesten Datei (Tailing). **Logische Offsets bleiben
  stabil** (gelöschte Front-Dateien behalten ihren reservierten Offset-Bereich → Seek-Map bleibt gültig).
- **Front-Trim:** älteste Segment-Datei löschen, sobald sie > 60 min hinter dem Live-Rand liegt. Der gelöschte
  Bereich ist außerhalb des Fensters → nicht mehr seekbar (UI begrenzt darauf); die DS liefert für solche
  Offsets sauber „nicht verfügbar".
- **Spike beweist:** (1) nahtloses Live über einen Segment-Rollover (kein Glitch); (2) Seek-back innerhalb des
  Fensters sauber, auch über gelöschte-Front hinweg begrenzt; (3) 1 Verbindung.
- Debug-Spike (`SegmentedTsRecorder` → fixe SEG_BYTES-Segmente + Front-Trim; `TsCaptureSpikeActivity` → tailing
  über die Multi-File-DS; Engine dispatcht Dir→Multi-File / Datei→Single-File). Test auf Mi TV 4S mit echtem
  Xtream-`.ts`.

### ✅/⚠️ Spike-Ergebnis (2026-07-09, Mi TV 4S, echtes Xtream-`.ts`)
- ✅ **Nahtloses Live über viele Rollover** (seg 11 → 25, `status=Playing` durchgehend, pixel-scharf, **kein
  Glitch** an Segmentgrenzen).
- ✅ **Front-Trim funktioniert** (seg bei `maxSegments`=25 gecappt, alte gelöscht, Wiedergabe läuft weiter).
- ✅ **Seek serviert sauberes Bild** (−30s → anderer, scharfer, früherer Frame — kein Garbage).
- ❌ **Position/Seek-STEUERUNG über ExoPlayers unbounded-Timeline unzuverlässig** (der „nicht-standard"-Punkt,
  jetzt real): nach Seek **re-based ExoPlayer die Position auf 0** (`pos=0`), das Fenster aus running-max ignoriert
  den Trim (`window=185s` bei nur ~50s retained), und `seekToLiveEdge` (mein `seekBy(+24h)`-Clamp-Trick) **schießt
  über** auf 24h statt Puffer-Ende → Bild eingefroren.

**Fazit:** Der **Decode-/Play-/Trim-Mechanismus ist bewiesen**; **die Timeshift-Positions-/Seek-Steuerung darf
NICHT aus ExoPlayers unbounded-Position kommen.** Sie muss **capture-getrieben** sein:
- Recorder mappt **Bytes ↔ Zeit** (v1: Wall-Clock beim Schreiben je Segment; genauer: PCR/PTS parsen) und meldet
  die **Fenster-Grenzen** (ältestes retained ↔ Live-Rand, in ms).
- Controller zeigt Position/Fenster/behind-live **aus diesem Mapping** (nicht `player.currentPosition`).
- Seek: Ziel-ms → Byte-Offset (via Mapping) → `player.seekTo` auf die zugehörige Media-Zeit; `seekToLiveEdge` →
  Live-Rand-ms (nicht +24h-Overshoot). UI begrenzt Seeks auf `[ältestes-retained, Live-Rand]`.
- Das ist der **eine echte Rest-Design-Punkt** (Increment vor der vollen Engine-Integration).
- **Fallback falls capture-getriebene Steuerung zu fummelig:** „A einfach" (Single-File + Rollover-Re-Prepare;
  saubere ExoPlayer-Position, dafür seltener Live-Glitch beim Rollover).

## Produktions-Komponenten (`core/player/.../timeshift/`)
Nach erfolgreichem Spike:

1. **`MultiFileTailingDataSource`** (aus dem Spike gehärtet) — die Wiedergabe-Quelle.
2. **`LiveTimeshiftRecorder`** — Capture-Session:
   - Eine OkHttp-Verbindung (vom `NetworkClientFactory`, Debug-Trust-All wie sonst), lädt die Live-URL,
     schreibt in rollende Segment-Dateien; Front-Trim (60-min-Fenster).
   - Injizierbar: OkHttp-Client, `clock`, `Dispatchers.IO`, Session-Dir.
   - Eigene Coroutine, Mutex-guarded; **Call-Cancel bei stop/Kanalwechsel/release** (Verbindung voll zu).
   - **Reconnect** bei Verbindungsabbruch (Backoff, ~10 Versuche); solange blockt die Tailing-DS am EOF.
   - Exponiert: `bufferDir`, `capturedWindowMillis`/`isActive` für Controller/DS.
3. **`TimeshiftDiskManager`** — Disk-Budget: **2 GB** global (LRU→80 %), **200 MB** Frei-Floor, Orphan-Cleanup
   (Waisen-Session-Dirs beim ersten Start löschen), per-Session-Dir. `onTrimMemory` ist irrelevant (Disk-only).
4. **`TimeshiftModels`** — Config/State (Fenster-Tiefe, Segment-Größe, Budget-Konstanten).

## Fall-A/B-Erkennung (ohne Probe)
Vor dem Play entscheiden, ob Recorder+Local (Fall B) oder Direkt (Fall A):
- **Xtream:** Provider-`xtreamOutputFormat == TS` → Fall B. `== HLS` (Default) → Fall A.
- **M3U:** Stream-URL endet auf `.ts` (oder bekannt-progressiv) → Fall B; `.m3u8`/sonst → Fall A.
- Ergebnis als Flag am Request: `PlaybackRequestFactory.channelRequest` setzt `captureLocally: Boolean` (neues
  Feld) anhand des aufgelösten Streams/Providers. (Analog zum bestehenden `tailing`-Flag; der Engine-/
  Orchestration-Pfad liest es.)
- **Kein Auto-Detect per `isCurrentSeekable`** hier (das war der Fall-A-Native-Weg) — bei Fall B spielen wir von
  Anfang an lokal, weil die eine Verbindung dem Recorder gehört.

## Engine-Integration (App/Player-Layer)
- **App-Orchestration** (`openChannelPlayback`): bei `captureLocally` →
  1. `LiveTimeshiftRecorder` starten (Live-URL, User-Agent) — besitzt die Verbindung.
  2. Warten bis genug gepuffert (~wenige Segmente), dann `playerController.play(request.copy(streamUrl =
     bufferDir-URI, tailing = true))` → Engine spielt lokal-tailing.
  3. Bei Kanalwechsel/Stop: Recorder stoppen (Verbindung zu), dann nächsten starten.
- **`Media3PlaybackEngine`**: `request.tailing`-Branch **erledigt** — dispatcht Dir→`MultiFileTailingDataSource`,
  Datei→`TailingFileDataSource` (beide gebaut).
- Bleibt **App-hoisted** (Recorder-Lifecycle + Verbindung + Play-Delegation im App-Layer, wie
  `docs/SETTINGS-APP-HOISTED-DECISIONS.md` / die Playback-Orchestrierung). Recorder-Klasse selbst in
  `:core:player` (kein Context nötig außer cacheDir/OkHttp — injizieren).

## Controller/UI — Positions-/Seek-Steuerung (der Rest-Design-Punkt)
- ⚠️ Der erste Ansatz (Fenster/Offset aus `player.currentPosition` running-max) reicht für Multi-File **nicht**
  (Spike-Ergebnis: Position re-based beim Seek, seekToLiveEdge überschießt). **Muss capture-getrieben werden:**
  - Recorder liefert **Bytes↔ms-Mapping** (v1 Wall-Clock je Segment; genauer PCR/PTS) + Fenster-Grenzen
    (`oldestRetainedMs`, `liveEdgeMs`).
  - Controller: `timeshiftWindowMillis = liveEdgeMs - oldestRetainedMs`; `positionMillis`/`behindLive` aus dem
    Mapping (nicht `player.currentPosition`).
  - `seekBy`/`seekToLiveEdge`: Ziel-ms → Byte-Offset → passende Media-Zeit an `player.seekTo`; Live-Rand = `liveEdgeMs`
    (kein +24h-Overshoot). UI klemmt auf `[oldestRetainedMs, liveEdgeMs]`.
- Vor genug Puffer beim Kanalstart: kurzer „Timeshift baut auf"/Buffering-Zustand; danach normal.
- Kein sichtbarer „Aufnahme läuft"-Indikator (immer-an, unsichtbar).

## Offene Entscheidungen / Defaults (bestätigen)
- **D1 Fenster-Tiefe:** Default **60 min** rollend.
- **D2 Disk-Budget:** Default **2 GB** global LRU→80 %, **200 MB** Frei-Floor (StreamVault-Werte).
- **D3 Segment-Größe (Multi-File):** Default ~**6 MB** bzw. ~**10 s** je Segment (Kompromiss Trim-Granularität
  vs. Datei-Anzahl). Im Spike feinjustieren.
- **D4 Zap-Delay:** wieviel vorpuffern bevor Play startet — Default **~2-3 Segmente**; im Spike messen.

## Bau-Increments + Gates
1. **Multi-File-Tailing-Spike** (Schritt 0) — auf TV verifizieren. Gate: nahtloses Live + sauberer Seek.
2. `TimeshiftDiskManager` + `TimeshiftModels` (rein, unit-testbar: LRU→80 %, Floor, Orphan, Front-Trim-Logik).
3. `LiveTimeshiftRecorder` (Capture + Segmentierung + Front-Trim + Reconnect).
4. Fall-A/B-Erkennung: `captureLocally`-Flag in `PlaybackRequestFactory` (+ Xtream-Format/M3U-Endung).
5. Engine-/Orchestration-Integration (Recorder-Lifecycle, lokal-tailing-Play, Kanalwechsel-Verbindung).
   **Dabei aufräumen (offener Rest aus `timeshift-redesign.md`):** den nach Phase 2 toten
   `timeshiftCache`/`usesDiskCache`/CacheDataSource-Pfad in `Media3PlaybackEngine.start()` + `Media3PlayerFactory`
   entfernen (der Tailing-Pfad ersetzt seinen Zweck; `SimpleCache` war ein Read-Through-Cache, kein Recorder).
6. End-to-End auf der echten TV mit echtem Xtream-`.ts` (max_cons=1): Live flüssig, Rewind bis Fenster,
   zurück-zu-Live, Kanalwechsel ohne „max connections", Fenster rollt bei 60 min.
- Gates je Increment: `.\gradlew.bat detekt test assembleDebug` + TV-Smoke. Unit-Tests für DiskManager/Trim/
  DS-Offset-Mapping. Debug-Spike bleibt für weitere Stream-Tests.

## Sicherheit / Regeln
- Provider-URL/Creds nie committen (Spike zeigt nur `url set: true`); Test-URL per adb-Extra.
- Debug-Trust-All-TLS bleibt debug-only.
- TV-Installs nur mit Freigabe (hier für die Spikes erteilt).
