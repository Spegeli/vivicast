# Plan: Timeshift-Redesign — Immer-an, rollendes 60-min-Fenster (Segment-Engine)

Status: geplant (2026-07-09), noch nicht umgesetzt. Orientiert an StreamVaults Timeshift-Engine
(einzige echte Live-Rewind-App der vier), mit zwei bewussten Verbesserungen (siehe unten).

## Context

Aktueller Timeshift (`setBackBuffer(minutes)` RAM + fixer 512-MB-`SimpleCache`) hat echte Konflikte:
60-min-RAM-Back-Buffer → OOM-Risiko (ExoPlayer bietet keinen Byte-Cap dafür); 512 MB halten bei HD nur
~8–15 min → eingestellte Dauer nicht honoriert; Cache wird bei Sender-Wechsel nicht geleert. Nutzer-Switch
RAM/Disk + frei wählbare Dauer sind Sonderwege (keine Referenz-App hat sie).

## Entschieden (fix, mit Nutzer)

- **Immer-an, rollendes 60-min-Fenster.** Ab Sender-Start läuft die Aufnahme durchgehend; gehalten werden
  stets die **letzten 60 min** (Ältestes vorne wird getrimmt). Man kann **jederzeit** bis 60 min zurück —
  auch ohne vorher Pause (z.B. „Szene vor 5 min nochmal"). Pause läuft im Hintergrund weiter; Resume spielt
  verzögert weiter und man kann innerhalb des rollenden Fensters bleiben.
- **Disk-first automatisch** (cacheDir), **RAM nur Fallback** (~5 min) wenn Disk nicht schreibbar. Kein Switch.
- **60 min hart.** Keine Dauer-Auswahl. **Global ~2 GB Byte-Budget + ~200–500 MB Frei-Speicher-Floor + LRU.**
- **Nur eine Session gleichzeitig** (aktueller Sender). **Sender-Wechsel/Stop → Session löschen.**
  Waisen-Verzeichnisse beim App-/Session-Start aufräumen (Crash-Recovery).

## Architektur (StreamVault-Muster, adaptiert)

**Capture ist von der Wiedergabe entkoppelt** — ein Hintergrund-Downloader schreibt fortlaufend auf Disk,
der Player spielt daraus. Komponenten (neu, in `core/player` bzw. eigenes `:data:timeshift`):

- **`TimeshiftRecorder` + Session** (per Container): Hintergrund-Coroutine (`SupervisorJob + Dispatchers.IO`,
  OkHttp), schreibt Segment-/Chunk-Dateien unter `cacheDir/timeshift/<channelKey>-<ts>/`, hält ein rollendes
  `ArrayDeque` + **Front-Trim** (dauer-basiert, Ältestes löschen solange Fenster > 60 min).
  - **Progressive TS (Xtream `.ts`, unser Hauptfall):** ein langlebiger GET, alle **~2 s** ein neuer
    `chunk-N.ts`; Fenster über Wall-Clock-Dauer. Reconnect mit Backoff.
  - **HLS:** Media-Playlist alle `targetDuration/2` pollen, neue Segmente per `mediaSequence` erkennen +
    downloaden. (DASH optional später.)
- **`TimeshiftDiskManager`:** globales 2-GB-Budget, `usableSpace`-Floor (~300–500 MB), LRU-Eviction alter
  Session-Dirs, Waisen-Cleanup, Per-Write-Throttle (vor jedem Segment/Chunk prüfen).
- **RAM-Fallback:** in-Memory-Byte-Puffer statt Dateien, Tiefe hart ~5 min; `onTrimMemory(CRITICAL)` → Session
  killen.

### Wiedergabe — lokale **Live-Playlist** statt eingefrorenem Snapshot (Verbesserung ggü. StreamVault)

StreamVault erzeugt bei jedem Seek einen statischen `index.m3u8` (`EXT-X-ENDLIST`) und **kopiert** dafür das
ganze Fenster (Disk-Churn), und „an Live aufschließen" ist ein **Rejoin** (kein nahtloses Weiterlaufen hinter
Live). Für unser rollendes Modell besser:

- **Eine** lokale, **rollende Live-HLS-Playlist** `index.m3u8` **ohne** `ENDLIST`, die der Recorder bei jedem
  neuen Segment fortschreibt (neues `#EXTINF` anhängen, getrimmtes entfernen, `#EXT-X-MEDIA-SEQUENCE` hochzählen)
  und die die Segment-Dateien **in place referenziert** (nicht kopiert).
- **Ein** ExoPlayer dauerhaft auf `file://…/index.m3u8`. ExoPlayer behandelt das als **Live-HLS mit
  DVR-Fenster** → nativer Pause/Seek-zurück im 60-min-Fenster, „Live-Rand" = neuestes Segment, Rollen wird
  vom periodischen Live-Reload nativ verfolgt. **Kein** Player-Swap, **keine** Snapshot-Kopie.
- Kosten: wenige Sekunden zusätzliche Latenz ggü. direktem Live (Chunking/Playlist-Reload) — akzeptabel.

**Offene Kern-Unbekannte (Spike klärt):** rohe TS-Chunks in beliebiger 2-s-Schnittweite sind **keine** sauber
einzeln dekodierbaren HLS-Segmente (TS-Schnitt braucht idealerweise Keyframe/PAT/PMT-Grenzen). Zu klären, ob
ExoPlayer eine lokale HLS-Playlist aus solchen TS-Chunks lückenlos spielt, oder ob wir (a) an sinnvollen
Grenzen schneiden, (b) leicht re-muxen, oder (c) auf StreamVaults concat-`buffer.ts`-Snapshot ausweichen
müssen (dann mit periodischem Re-Snapshot fürs Weiterlaufen). **Das entscheidet der Spike.**

## Phasen

### Phase 0 — Spike (zuerst; klärt die eine echte Unbekannte)
Auf Emulator mit den Test-Playlisten (Memory `test-playlists`):
- Progressive Xtream-`.ts`: Downloader schreibt 2-s-`chunk-N.ts` + rollende lokale Live-`index.m3u8` in place;
  spielt ExoPlayer das lückenlos, mit Seek-zurück und Live-Follow? Falls nicht → concat-`buffer.ts`-Variante
  bzw. Schnitt-an-Grenzen testen.
- HLS-Live (`deu.m3u`): Segment-Poll + lokale Playlist — spielt/seekt sauber?
- Ergebnis fixiert die Playback-Mechanik für Phase 2. **Kein Weiterbau ohne validierten Kern.**

### Phase 1 — Recorder + DiskManager (Capture + Rolling + Limits)
Session-Subtypen (Progressive/HLS), Front-Trim, 2-GB-Budget/LRU/Floor/Throttle, RAM-Fallback + Trim-Kill,
Waisen-Cleanup. Reine Fenster-/Cap-/Floor-/Trim-Logik **unit-testbar**. Injizierbarer Dispatcher.

### Phase 2 — Wiedergabe- + Controller-Integration
`Media3PlaybackEngine`: bei Live-`start()` Recorder starten und ExoPlayer auf die lokale rollende Playlist
setzen (per Spike-Ergebnis). `VivicastPlayerController`: Pause/`seekBy`/`seekToLiveEdge` auf das reale
Session-Fenster (0 … verfügbare Länge) umstellen; die vorhandene virtuelle Timeshift-Maschinerie
(`liveEdgeOffsetMillis`, `timeshiftProgressState`, `seekTimeshiftBy`, `timeshiftWindowMillis`) an das echte
Fenster koppeln. `play(neu)`/`stop()`/Sender-Wechsel → Recorder stoppen + Session-Dir löschen.

### Phase 3 — Grenzen/Cleanup scharf schalten
60-min-Cap, Floor, LRU, RAM-Kill, App-Start-Waisen-Sweep, „Sender/Stream unterstützt kein Timeshift"-Fall
(`player_timeshift_unavailable`) sauber. Catch-up-Kontext von Timeshift ausschließen.

### Phase 4 — UI-Vereinfachung (zuletzt, wenn Engine liefert)
`PlaybackSettingsPanel.kt`: **Timeshift-Toggle, Max-Dauer, Timeshift-Speicher entfernen** (+ `PlaybackPicker`
-Einträge + Dialoge). Timeshift immer verfügbar. `SettingsModels.PlaybackSettingsState`: die drei Felder
raus; `SettingsViewModel`/Mapper anpassen. `app/SettingsPreferenceMappers.kt`: `timeshiftConfig()` +
`toPlaybackTuning(backBufferMinutes)` zurückbauen (Back-Buffer entfällt). `core/datastore PlaybackPreferences`:
drei Felder tot → als dead lassen (keine Migration) oder mit-entfernen. Strings der drei Zeilen raus.

## Betroffene Dateien (Kern)
- **neu:** `core/player/.../TimeshiftRecorder.kt` (+ Sessions), `.../TimeshiftDiskManager.kt`, Model/Limit-Helfer
- `core/player/.../VivicastPlayerController.kt` (Engine-Start/Stop + Controller Pause/Seek/Live-Edge/Lifecycle)
- `feature/settings/.../PlaybackSettingsPanel.kt`, `.../SettingsModels.kt`, `.../SettingsViewModel.kt`, `.../SettingsPreferenceMappers.kt`
- `app/.../SettingsPreferenceMappers.kt`, `core/datastore/.../UserPreferencesStore.kt`, `core/designsystem/.../strings.xml`
- `core/player/build.gradle.kts` falls OkHttp/HLS-Parser dort noch fehlt (HLS-Parsing evtl. via media3-exoplayer-hls).

## Risiken / offen
- **Progressive-TS-Chunk-Playback** (lückenlos + seekbar + rollend) = Haupt-Unbekannte → Phase-0-Spike zwingend.
- Referenz-in-place statt Kopie: sicherstellen, dass ein gerade abgespieltes Segment nicht vorzeitig getrimmt/
  gelöscht wird (Trim nur außerhalb der aktuellen Player-Position).
- Latenz durch lokales Chunking (paar Sekunden hinter echtem Live) — akzeptiert.
- Timeshift-Progress-UI vom virtuellen auf reales Fenster umstellen (sonst falsche Timeline).

## Verifikation
- Spike zuerst manuell (Test-Playlisten): Live läuft; jederzeit bis 60 min zurück; Pause 5 min → Resume ohne
  Verlust; Fenster rollt (Ältestes fällt raus); Sender-Wechsel löscht Session-Dir; Frei-Speicher-Floor greift.
- Unit-Tests: Fenster-/Cap-/Floor-/Trim-/Cleanup-Helfer.
- Gates je Phase: `.\gradlew.bat detekt test assembleDebug`; Emulator-Smoke.
