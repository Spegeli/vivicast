# Plan: UI-Freeze bei Startup-Refresh (DB-Read-Starvation)

Status: **abgeschlossen** (2026-07-12). ACHTUNG: der ursprünglich geplante Fix (getrennte Query-/
Transaction-Executor) war **falsch** — per connectedAndroidTest widerlegt. Echte Ursache + Fix unten.

## Befund (verifiziert)

Beim App-Start enqueued `MainActivity` für jeden aktiven Provider mit `refreshOnAppStartEnabled`
einen Playlist-Refresh (+ EPG-Refresh) → bei 2 Playlists + 2 EPG bis zu ~4 parallele Import-Jobs.
Jeder Katalog-Import läuft in **einer großen `database.withTransaction`** (RoomCatalogImportRepository.kt:48),
inkl. `androidTvSearchDao.rebuildEntries()` (voller Suchindex-Neuaufbau) — lange Schreib-Transaktion.

Die DB nutzt Room-Defaults (VivicastDatabaseFactory: nur `.build()`) → **gemeinsamer Thread-Pool für
Reads UND Transaktionen** (2–4 Threads). Die parallelen Import-Transaktionen belegen den Pool; die
reaktiven Read-Flows der UI bekommen keinen Thread und emittieren erst nach Commit. Deshalb zeigt die
UI leere Listen, bis die Refreshes durch sind. `observeProviders` = `SELECT * FROM providers` (kein
Status-Filter) — die Daten sind da, nur der Read läuft nicht.

## Wo tritt es auf?

Symptom betrifft **alle** Room-Flow-Reads während eines Imports/Refresh — nicht nur Settings:

- Settings: `observeProviders`, `observeEpgSources`, `observeProviderEpgSources`, `observeChannelsForProvider`, `observeMappingsForChannel`
- Home: `observeContinueWatching`/`observeAllContinueWatching`, `observeRecentChannels`/`observeAllRecentChannels`, Provider-Liste
- Live-TV: `observeChannels`/`observeChannelsPage`/`observeCategories`
- Movies: `observeMovies` · Series: `observeSeries`/`observeSeasons`/`observeEpisodes`
- Suche: FTS-Reads · Favoriten: `observeFavorites`

**Alle** teilen sich die eine `VivicastDatabase` (AppContainer.kt:110) → **ein** Fix an der DB-Factory
behebt alles. Keine Änderung pro Screen nötig.

## Was NICHT die Ursache war (per Test widerlegt)

Der Executor-Split half nicht. connectedAndroidTest (`DatabaseReadDuringWriteTest`) zeigte:
- Ein **plain SELECT** (`getProviders()`) läuft auch während einer langen exklusiven Room-Transaktion
  **sofort durch** (WAL-Snapshot) — Executor/Exklusivität egal.
- Es blockt **nur die Flow-Erstemission** (`observeProviders().first()`): beim ersten Flow-Observer
  installiert Room via `InvalidationTracker.syncTriggers()` die Trigger — das ist ein **Write**, der
  hinter der laufenden Import-Transaktion serialisiert (SQLite: ein Writer). Deshalb emittiert der Flow
  erst nach Import-Commit → leere Liste bis Refresh fertig.

## Fix (2 Teile)

1. **`VivicastDatabase.warmInvalidationTracker()`** (core/database): fügt beim Start einen permanenten
   No-op-Observer über die UI-Tabellen hinzu → installiert die Trigger **einmal, bevor** ein Import läuft.
   Spätere Flow-Subscriptions brauchen kein `syncTriggers` mehr → emittieren sofort den WAL-Snapshot.
   Aufruf in `VivicastApplication.onCreate` (auf IO, vor `installWorkerRunner`/Refresh-Enqueue) via
   `AppContainer.warmDatabaseObservers()`.
2. **WAL explizit** in `VivicastDatabaseFactory` (`.setJournalMode(WRITE_AHEAD_LOGGING)`) — garantiert
   nebenläufige Reads auf dem Commit-Snapshot auf allen Geräten (Default `AUTOMATIC` kann auf schwacher
   Hardware TRUNCATE wählen und Reads unter Schreiblast blockieren).

Wirkung: gilt app-weit (eine DB) — Settings, Home, Live-TV, Movies, Series, Suche, Favoriten.

## Verifikation (erfolgt)

- connectedAndroidTest `DatabaseReadDuringWriteTest`: mit vorgewärmtem Tracker emittiert
  `observeProviders().first()` in <1s trotz 2s exklusiver Transaktion (ohne Warm-up: Timeout). Grün.
- Migrationstest grün (WAL-Config). App startet sauber; Daten intakt (2 Provider, 419 Kanäle).
- Gates: detekt / assembleDebug / test grün.
- Voller Freeze-Repro visuell schwer zu timen (kleiner lokaler Import); der deterministische Test ist
  der maßgebliche Nachweis.

## Nicht im Scope

Kein Umbau der Import-Logik, keine Änderung an den Feature-Screens, keine Migration.
