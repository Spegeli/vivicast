# Playback Orchestration Migration Plan (P1-03a – Scope Audit)

> Reiner Analyse-/Planungslauf. Keine Code-/Gradle-/Teständerung. Basis: HEAD `1264d296`
> (nach P1-04 + P1-05, Arbeitsbaum clean).

## 1. Kurzfazit

`app/PlaybackOrchestration.kt` (469 Zeilen, davon ~90 ungenutzte Boilerplate-Imports) enthält
**14 `AppContainer`-Extension-Funktionen**. Der fachliche Kern (Stream-Auflösung → `PlaybackRequest`
bauen, Progress/History schreiben) ist bereits sauber von der UI getrennt und **gut testbar**, hängt
aber am `AppContainer` und lebt im App-Modul, daher aktuell nur über langsame androidTests abdeckbar.

**Empfehlung:** Nach **`:data:playback`** verschieben (nicht `:domain`). Dort liegen bereits
`PlaybackStreamResolver`, `PlaybackRepository`, `PlaybackProgressRules` + deren Unit-Tests. Der einzige
neue Bedarf ist eine Modulabhängigkeit **`:data:playback` → `:core:player`** (für `PlaybackRequest`
etc.). `:core:player` hat **keine** Projekt-Abhängigkeiten (nur media3) → **kein Zyklus**. Kein neues
Modul, keine Domain-Repository-Interfaces, kein Clean-Architecture-Zwang.

## 2. Aktueller Funktionsbestand

| Funktion | Verantwortung | Abhängigkeiten (`AppContainer`) | Zielort | Risiko | Testbedarf |
|---------|---------------|--------------------------------|---------|--------|------------|
| `resolveChannelLogoModel` | Cache-File für Kanal-Logo | `mediaCacheStore` (core:cache) | optional `:data:media` / App | niedrig | klein |
| `resolveMovieImageModel` | Cache-File Movie Poster/Backdrop | `mediaCacheStore` | optional `:data:media` / App | niedrig | klein |
| `resolveSeriesImageModel` | Cache-File Series Poster/Backdrop | `mediaCacheStore` | optional `:data:media` / App | niedrig | klein |
| `resolveEpisodeImageModel` | Cache-File Episode-Thumb | `mediaCacheStore` | optional `:data:media` / App | niedrig | klein |
| `openChannelPlayback` | Stream auflösen → Request → `play()` + `onStarted` | `playbackStreamResolver`, `playerController`, timeshift-Mapper | App-Host (ruft Factory + play) | mittel | via Factory |
| `openMoviePlayback` | delegiert an `createMoviePlaybackRequest` + `play()` | `playerController` | App-Host | niedrig | via Factory |
| `createMoviePlaybackRequest` | **reiner** Movie-Request-Builder (+ resume-Progress) | `playbackStreamResolver`, `playbackRepository` | **`:data:playback`** | mittel | **ja** |
| `openEpisodePlayback` | delegiert an `createEpisodePlaybackRequest` + `play()` | `playerController` | App-Host | niedrig | via Factory |
| `createEpisodePlaybackRequest` | **reiner** Episode-Request-Builder (+ resume-Progress) | `playbackStreamResolver`, `playbackRepository` | **`:data:playback`** | mittel | **ja** |
| `openCatchUpPlayback` | Catch-up-Guard + Request → `play()` | `playbackStreamResolver`, `playerController` | App-Host (Guard+Builder → data) | **hoch** | **ja** |
| `canStartCatchUp` (private) | Catch-up-Zeit-/Verfügbarkeitslogik | reine Logik + `MILLIS_PER_DAY` | **`:data:playback`** | mittel | **ja** |
| `savePlaybackProgress` | Channel-History / PlaybackProgress schreiben (Throttle) | `playbackRepository`, Progress-Rules | **`:data:playback`** | **hoch** | **ja** |
| `clearHistory` | Dispatch über `HistoryClearTarget` | `playbackRepository`, `mediaRepository` | **App bleibt** | niedrig | trivial |
| `resolvedStreamOrNull` (private) | `PlaybackStreamResult` → Stream? | rein | **`:data:playback`** | niedrig | mit Factory |
| `playbackId` (private) | ID mit `currentTimeMillis()` | Clock | **`:data:playback`** (Clock injizieren) | mittel | ja |
| `playbackProgressId` / `channelHistoryId` (private) | deterministische IDs | rein | **`:data:playback`** | **hoch** (ID-Kompat!) | ja |
| `toDomainProgressMediaType` (private) | `PlaybackMediaType` → `MediaType?` | rein | **`:data:playback`** | niedrig | ja |

Beteiligte Typen: `PlaybackRequest`/`PlaybackMediaType`/`PlaybackOrigin`/`PlaybackReturnTarget`/
`PlaybackStatus`/`VivicastPlayerState`/`PlaybackTimeshiftConfig` (**core:player**);
`PlaybackStreamRequest`/`PlaybackStreamResult`/`shouldSaveAutomaticPlaybackProgress`/
`automaticPlaybackProgressPercent`/`PLAYBACK_COMPLETION_THRESHOLD_PERCENT` (**data:playback**);
Domain-Models (**domain**). `timeshiftConfig()`-Mapper liegt in App
(`SettingsPreferenceMappers.kt:230`), `MILLIS_PER_DAY` in `MainActivity.kt:1323`.

## 3. Aktueller Datenfluss

- **Live-TV Start:** Route → App-Callback → `openChannelPlayback` → `resolver.resolve(Channel)` →
  `timeshiftConfig()` → `PlaybackRequest` → `playerController.play` → `onStarted`.
- **Movie Start:** `openMoviePlayback` → `createMoviePlaybackRequest` (resolve + `getProgress` resume) →
  `play`.
- **Episode Start:** `openEpisodePlayback` → `createEpisodePlaybackRequest` (resolve + resume) → `play`.
- **Catch-up Start:** `openCatchUpPlayback` → `canStartCatchUp` (Zeitfenster) → `resolve` mit
  `catchupStart/End` → `play`.
- **Progress speichern:** Player-State-Loop (`MainActivity:168/713/1129`) → `savePlaybackProgress` →
  Channel→`saveChannelHistory`, Movie/Episode→Regeln `shouldSaveAutomaticPlaybackProgress` →
  `saveProgress`; Throttle via `automaticProgressSaveTimes`-Map (App-`remember`).
- **History löschen:** Settings → `onClearHistory` → `clearHistory(target)` → Repos.
- **Bild-/Logo-Auflösung:** Routes → `resolveXxxImageModel` → `mediaCacheStore.getEntry().file`.
- **WatchNext/Progress-Sync:** separat in `app/system/` (`WatchNextSynchronizer`), getriggert aus dem
  Player-State-Loop; **nicht** Teil von `PlaybackOrchestration.kt`.

## 4. Soll-Datenfluss

```
Route/App-Callback
  → App mappt Prefs→PlaybackTimeshiftConfig (bleibt App)
  → PlaybackRequestFactory (data:playback): resolve Stream + resume-Progress → PlaybackRequest?
  → App: request?.let { playerController.play(it); onStarted() }

Player-State-Loop (App)
  → PlaybackProgressRecorder (data:playback): Regeln + saveChannelHistory/saveProgress

Settings → clearHistory (bleibt App: mischt playbackRepository + mediaRepository + feature-Enum)
```

`playerController.play(...)` bleibt **im App-Host** — kein `core:player`-Aufruf aus `data:playback`.
UseCases bauen **nur Requests / schreiben Persistenz**, treiben keine Navigation.

## 5. Zielort-Entscheidung

- **`:data:playback`** für Request-Builder, Catch-up-Guard, Progress-Recorder, ID-/Mapper-Helfer.
  Begründung: Resolver + Repository + Rules liegen schon dort; konsistent mit der pragmatischen
  Architektur (Repository-Interfaces bewusst **nicht** dogmatisch nach `:domain`).
- **Neue Dependency:** `:data:playback` → `implementation(project(":core:player"))` (acyclic, verifiziert).
- **`:domain`:** unverändert (nur Models). Keine neuen Domain-UseCases/Repo-Interfaces.
- **App bleibt Host** für: `playerController.play`, `timeshiftConfig()`-Mapping, `clearHistory`,
  `automaticProgressSaveTimes`-Throttle-State, WatchNext-Sync, Navigation/`onStarted`.
- **Kein neues Modul.**

## 6. Empfohlene Umsetzungsschritte

| Schritt | Umfang | Dateien | Risiko | Tests | Nicht Teil |
|--------|--------|---------|--------|-------|-----------|
| **P1-03b** | `PlaybackRequestFactory` in `:data:playback` mit `movieRequest`/`episodeRequest` (reine Builder + resume-Progress) + `playbackId`(Clock)/`resolvedStreamOrNull`. App `openMovie/EpisodePlayback` delegieren. `+core:player`-Dep. | `data/playback/PlaybackRequestFactory.kt` (neu), `data/playback/build.gradle.kts` (+core:player), `app/PlaybackOrchestration.kt` | mittel | neuer `PlaybackRequestFactoryTest` (Fake resolver+repo): Movie/Episode Request, resume-Progress, fehlender Stream=null | Channel/Catch-up, Progress-Save, Image-Resolver |
| **P1-03c** | Channel + Catch-up in dieselbe Factory: `channelRequest(timeshift)`, `catchUpRequest(now)`, `canStartCatchUp` + `MILLIS_PER_DAY` mitnehmen. App `openChannel/CatchUpPlayback` delegieren; App mappt weiter Timeshift + reicht `now`. | `data/playback/PlaybackRequestFactory.kt`, `app/PlaybackOrchestration.kt`, `MainActivity.kt` (MILLIS_PER_DAY-Quelle) | **hoch** (Catch-up-Zeitfenster) | Factory-Tests: Channel-Request (+timeshift seekable), Catch-up erlaubt/verweigert (Zeitgrenzen), fehlender Stream | Progress-Save, Image-Resolver |
| **P1-03d** | `PlaybackProgressRecorder` in `:data:playback`: `savePlaybackProgress` + `toDomainProgressMediaType` + `playbackProgressId`/`channelHistoryId` (Clock injizieren). App-Loop delegiert; Throttle-Map bleibt App-übergeben. | `data/playback/PlaybackProgressRecorder.kt` (neu), `app/PlaybackOrchestration.kt`, `MainActivity.kt` | **hoch** (ID-Kompat, Throttle) | `PlaybackProgressRecorderTest`: Channel-History, Movie/Episode save/skip via Regeln, mediaEnded→completed, ID-Stabilität | Request-Builder, clearHistory |
| **P1-03e** | App-Wiring aufräumen; `PlaybackOrchestration.kt` auf verbleibenden Rest reduzieren (nur noch dünne `play()`-Aufrufe + `clearHistory` + Image-Resolver). Smoke-Tests. | `app/PlaybackOrchestration.kt`, ggf. `MainActivity.kt` | mittel | androidTest-Smoke: `M3uPlaybackSmokeTest`, `ProtectionGateTest`, `WatchNextIntegrationTest` | Image-Resolver-Migration |
| **P1-03f** *(optional)* | Image-Resolver → `:data:media` `MediaImageResolver`. Kein Playback-Bezug. | `data/media/...`, `app/PlaybackOrchestration.kt` | niedrig | kleiner Resolver-Test | — |

## 6a. Erster empfohlener Schritt

**P1-03b** — kleinster risikoarmer Schnitt: die zwei **reinen** Request-Builder (Movie/Episode) haben
klaren Input/Output, keine Zeitlogik, keine Player-/Navigations-Kopplung. Sie etablieren
`PlaybackRequestFactory` + die `core:player`-Dependency + das Testmuster, bevor die riskanten
Catch-up-/Progress-Teile folgen.

## 7. Tests und Validierung

Neue Unit-Tests (Fake `PlaybackStreamResolver` + Fake `PlaybackRepository`, kein Room/Netzwerk):
- Movie-Request (Felder, resume-Progress `takeUnless isCompleted`, `startPositionMillis`).
- Episode-Request (analog, immer resume).
- Channel-Request (timeshift→`seekable`, `returnTarget=LiveTv`).
- Catch-up-Request: erlaubt (innerhalb `catchupDays`) vs. verweigert (Zukunft, `start>=end`,
  `catchupDays<=0`, fremder Channel).
- Progress-Save: Channel-History; Movie/Episode save vs. skip (Throttle/Regeln); `mediaEnded→isCompleted`;
  bestehende `id`/`createdAt` erhalten.
- Fehlender Stream (`PlaybackStreamResult` != Resolved) → `null`, kein `play`.
- ID-Format-Stabilität (`playbackProgressId`, `channelHistoryId`).

Bestehende Tests wiederverwenden: `DefaultPlaybackStreamResolverTest`, `PlaybackProgressRulesTest`
(Regeln bleiben unverändert). androidTest-Smoke nach P1-03e: `M3uPlaybackSmokeTest`,
`ProtectionGateTest`, `WatchNextIntegrationTest` (bekannt flaky — nicht fixen, nur dokumentieren).

## 8. Risiken

- **Progress-/History-ID-Kompatibilität:** `playbackProgressId`/`channelHistoryId`-Format **exakt**
  erhalten, sonst verwaiste DB-Einträge/Resume-Verlust. → Test auf Stringformat.
- **Catch-up-Zeitlogik:** `canStartCatchUp` + `catchupStart/EndMillis` sensibel; Clock injizieren,
  Grenzfälle testen.
- **`playbackId`-Clock:** nutzt `currentTimeMillis()`; deterministisch testbar nur mit injizierter Clock.
- **WatchNext:** hängt am Player-State-Loop, nicht an diesem File — nicht anfassen; nach P1-03e via
  `WatchNextIntegrationTest` verifizieren.
- **PlaybackRequest-Felder:** vollständige Feldübernahme (stableKeys, origin, returnTarget,
  `epgProgramStableKey`) — ein fehlendes Feld ändert Player-/Return-Verhalten.
- **Timeshift/ExternalPlayer:** `timeshiftConfig()` bleibt App-Mapper; Factory nimmt fertigen
  `PlaybackTimeshiftConfig?`. ExternalPlayer-Verhalten steckt im `playerController` (core:player),
  unverändert.
- **Modulabhängigkeit:** `data:playback → core:player` neu; verifiziert acyclic (core:player ohne
  Projekt-Deps). `clearHistory` **nicht** migrieren (bräuchte `data:media` + `feature:settings`-Enum →
  falsche Kopplung).
- **AppContainer:** Extension-Funktionen werden zu Klassen mit Konstruktor-Deps; AppContainer
  instanziiert sie lazy — minimal, keine AppContainer-Refactorings darüber hinaus.

## 9. Bewusst NICHT ändern

- `core:player` / `VivicastPlayerController` (Player bleibt Host-gesteuert).
- `PlayerRoute` / Player-UI, ViewModels, Settings.
- `data:playback`-Repositories/Resolver/Rules (nur **nutzen**, nicht ändern).
- Domain-Models (ausreichend).
- WatchNext-Sync (`app/system/`).
- `clearHistory` (bleibt App-Dispatch).
- `timeshiftConfig()`-Mapper (bleibt App).

## 10. Git-Status

`git status --short`: **clean** (HEAD `1264d296`). Dieser Lauf erzeugt nur diese eine Doku-Datei.
