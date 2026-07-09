# Plan: Timeshift-Redesign — natives DVR-Fenster primär, Capture-Engine als Backup

Status: geplant (2026-07-09). **Spike-Ergebnis (Phase 0) hat den Ansatz gedreht** — siehe unten.

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

### Phase 1 — Fall A sauber + Rückbau (jetzt, klein, hoher Nutzen)
- **Rückbau** des `setBackBuffer` (RAM-OOM-Quelle) + der `SimpleCache`-Timeshift-Disk-Nutzung + der virtuellen
  `timeshiftWindowMillis`/`liveEdgeOffset`-Maschinerie, soweit sie das native Fenster ersetzt.
- **Controller/Engine:** Live-Channel mit `seekable=true`; Seek/Live-Edge auf das **native** Timeline-Window
  abbilden (`seekBy`, `seekToDefaultPosition` für Live). `timeshiftProgressState` an die reale Timeline hängen.
- **Xtream-Ausgabeformat-Option** (mirror des vorhandenen `logoPriority`-per-Provider-Settings, komplette Kette):
  Feld `xtreamOutputFormat` (Enum MPEG-TS/HLS, Default HLS) in `ProviderCreateRequest`/`ProviderUpdateRequest`
  + Provider-Domain; **Room-Spalte + DB-Migration**; **Picker-Zeile im `ProviderEditor` nur bei Xtream sichtbar**
  (`SettingsChoiceDialog`, wie Logo-Priorität); `PlaybackStreamResolver.resolveXtream` baut `.m3u8`/`.ts` je
  Option. Catch-up-`.ts`-Pfad bleibt.
- **Auto-Detect**: nach Start Fenster prüfen; nicht-seekbar → „begrenztes/kein Timeshift"-Hinweis
  (`player_timeshift_unavailable`).

### Phase 2 — UI-Vereinfachung
- `PlaybackSettingsPanel.kt`: **Timeshift-Toggle, Max-Dauer, Timeshift-Speicher entfernen** (das Fenster kommt
  vom Server/Fall-B, keine dieser Knöpfe mehr sinnvoll). `PlaybackSettingsState`-Felder + Mapper +
  `PlaybackPreferences`-Felder zurückbauen (tot lassen ok). Strings raus. Timeshift ist dort verfügbar wo der
  Stream es hergibt.

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
