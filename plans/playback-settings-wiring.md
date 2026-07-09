# Plan: Playback-Einstellungen — komplett fertig bauen

Status: **umgesetzt** (2026-07-09), Phasen 0–4 committed. Volle Detail-Planung:
`~/.claude/plans/joyful-finding-oasis.md`.

## Kontext
Playback-Screen sollte vollständig funktionieren. Recherche: **Video renderte gar nicht** (keine
Video-Surface) und 7 Settings waren persisted-only. Nutzerentscheidungen: Video-Surface zuerst; FFmpeg
bündeln; AFR jetzt. FFmpeg via **NextLib** statt NDK-Build (WSL/NDK auf der Maschine nicht bereit).

## Umgesetzt (alle 13 Wiedergabe-Zeilen wirken bzw. sind spec-konform)

- **Phase 0** (`4932994`): Video-Surface — `attach/detachVideoSurface` auf Controller/Engine, SurfaceView
  via `AndroidView` in `PlayerRoute` unter dem Overlay → **Video rendert**.
- **Phase 1** (`eb8863b`): Engine baut ExoPlayer bei `start()` aus `PlaybackTuning`-Snapshot neu (nur bei
  Änderung des Builder-Subsets, Reconnect nutzt lebenden Player). Neue `Media3PlayerFactory.kt`
  (`buildExoPlayer` + LoadControl-Buffer-Tiers + `NextRenderersFactory` FFmpeg + `VivicastMediaCodecSelector`
  HW/SW). `RuntimePlaybackTuningPolicy` (analog UA-Policy) + `MainActivity`-Effect + `toPlaybackTuning()`.
  NextLib `io.github.anilbeesetti:nextlib-media3ext:1.9.3-0.12.0`. Back-Buffer trackt `timeshiftMinutes`.
- **Phase 2** (`469f51c`): Passthrough-`AudioSink` (Aus = PCM erzwingen via no-Context-Builder; Ein =
  Default-Context-Sink); Audio/Untertitel-Sprache seeden `trackSelectionParameters` bei jedem Start;
  Timeshift `usesDiskCache()` (RAM=Memory, Intern=Disk, Auto=Disk nur >30min); External-Player MIME
  (`setDataAndType`).
- **Phase 3** (`163bb5c`): AFR — `videoFrameRate` im State (aus `player.videoFormat`), `PlayerRoute`
  `Surface.setFrameRate(fps, FIXED_SOURCE, ONLY_IF_SEAMLESS)`, API-31+-Gate.
- **Phase 4** (`ea49690`): Panel-Enum-Zeilen → `SettingsChoiceDialog`-Popups (Booleans bleiben Toggles),
  Dialoge in `PlaybackSettingsDialogs` ausgelagert (Detekt); AFR-Zeile <API31 deaktiviert + Hinweis.

Werte (Sign-off): Buffer-Tiers 1000/5000 · 5000/15000 · **15000/30000 (mittel)** · 30000/60000 ·
60000/120000 (min/max ms). Passthrough Aus=PCM. Timeshift-Auto >30min→Disk. AFR-fps=`Format.frameRate`.

## Gates
`.\gradlew.bat assembleDebug testDebugUnitTest detekt` grün; androidTest kompiliert. Unit-Tests:
`Media3PlayerFactoryTest` (Buffer-Tiers, Builder-Subset-Rebuild-Logik, `usesDiskCache`). Baseline für
`PlayerRoute`-`afrEnabled`-Param aktualisiert.

## Offene Kleinigkeiten (bewusst zurückgestellt)
- Passthrough-Capability-Gate (Zeile deaktivieren + Hinweis wenn Gerät kein Passthrough) — braucht
  `AudioManager`; der Forced-PCM-Sink degradiert bereits sauber. Nachrüstbar.
- FFmpeg via NextLib gepinnt an media3 1.9.3-Build (unser media3 1.9.4; Patch-Skew, ABI stabil). Bei
  Laufzeit-Mismatch media3 auf 1.9.3 pinnen oder auf NDK-Build wechseln.
- „Software-Video" nur über Plattform-`c2.android.*` (kein gebündelter SW-Video-Decoder); FFmpeg = SW-Audio.

## Manuelle Verifikation (Emulator, Test-Playlisten in Memory)
Provider mit `deu.m3u`/`german-tv.m3u` anlegen → Sender starten: **Bild rendert**; Puffer/Decoder ändern →
gilt ab nächstem Streamstart; Sprache/Untertitel seeden; Popups statt Cyclen. (Leanback-IME blockiert
adb-Autotest hinter Textfeldern — manuell.)
