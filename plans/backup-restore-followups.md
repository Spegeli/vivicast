# Backup Restore — Post-Restore-Followups (Audit-Findings)

> Status: **PLAN / Audit-Ergebnis — F1 + F2 zu fixen (Ansatz vor Code besprechen). F3 optional (niedrig).
> Kommentar-Fix DONE. Zwei Punkte = Kontext/Doku, kein Fix nötig.**
>
> Kontext: Voll-Audit der Backup-Export/Import-Funktion (2026-07-23) nach Abschluss von
> `plans/backup-restore-groups-lost.md`. **Vollständigkeit Export/Import = VOLLSTÄNDIG** (alle 10
> Nutzerdaten-Tabellen + alle Secrets + Preferences round-trippen; keine Datenverluste außer bewussten:
> PIN, Diagnostics, gerätelokale Pfade, Such-Timestamps, Provider-Laufzeitdaten). Die Restfunde liegen ALLE
> im **async Post-Restore-Refresh**, nicht in der Export/Import-Datenlogik.

## F1 — Post-Restore-Refresh-Reihenfolge: Restore-Refresh-Orchestrator (mittel-groß, FIX)

**Problem (verschärft durch Multi-Playlist/Multi-EPG):** Post-Restore (`MainActivity.runStandardRestore`
:461-466) enqueued Playlist- UND EPG-Refresh als **unabhängige, ungeordnete** OneTimeWork. `importXmltv`
bindet Programme + Manual-Mappings gegen `catalogDao.getChannels(providerId)`. Eine **geteilte EPG-Quelle** S
(gelinkt an P1,P2,P3 via `provider_epg_sources`) mappt beim Refresh **an jeden gelinkten aktiven Provider
einzeln** (`DefaultEpgRefresher`: `activeProviderIds.forEach { importXmltv(pn, S, doc) }`). Damit S korrekt an
alle mappt, müssen **alle** Kataloge existieren, bevor S läuft. Jeder per-Provider-Ansatz (A/B/C aus der
Erst-Analyse) versagt hier: fährt S nach P1 los, gehen P2/P3 leer aus.

**Korrekte Reihenfolge (User-Modell, bestätigt):** (1) Restore-Transaktion (schon), (2) **alle** Playlist-
Refreshes → voller Katalog + Kategorien + Phase-1-Reconcile je Provider, (3) **Barriere**, (4) **dann alle**
EPG-Refreshes.

**Randbedingungen aus der Worker-Architektur:**
- Per-Item-Worker MÜSSEN bleiben: jeder hat sein eigenes **~10-Min-WorkManager-Budget** (`DelegatingRefreshWorker`);
  ein einzelner Mega-Worker über alle Playlisten+EPG würde das Limit reißen.
- Retry/Backoff (bis `MAX_REFRESH_ATTEMPTS=10`) + Dedup (`RefreshRunGuard`) sind per-Item.

**Design: WorkManager-Continuation als Barriere.**
Neuer Scheduler-Aufruf `enqueueRestoreRefresh(providerIds, epgSourceIds)`:
```
workManager.beginUniqueWork(RESTORE_REFRESH_WORK, REPLACE, playlistRequests)  // Phase 1: alle Playlisten
           .then(epgRequests)                                                  // Phase 2: alle EPG
           .enqueue()
```
- Jeder Node bleibt ein eigener Worker → eigenes 10-Min-Budget; die Kette darf beliebig lange dauern.
- `.then()` startet Phase 2 erst, wenn **alle** Phase-1-Worker `SUCCEEDED` sind (Katalog committed vor
  Worker-Return).
- `MainActivity.runStandardRestore` ersetzt die 2 Enqueue-Schleifen (:461-466) durch den einen Aufruf.

**Zwei Fallen, die gelöst werden müssen (sonst ist die Barriere wertlos):**

1. **Fehlertoleranz.** `.then()` blockiert Phase 2, wenn **eine** Playlist final `FAILED` ist (bad
   Credentials/Netz). Ein kaputter Provider darf EPG für die anderen NICHT blockieren. → Ein
   **`restoreChain=true`**-Input-Flag auf den Playlist-Workern: bei terminalem Fehler `Result.failure()` →
   `Result.success()` mappen (Fehler weiterhin via Diagnostics + Provider-Status protokolliert), damit die
   Kette weiterläuft. (Transiente Fehler retrien normal; die Kette wartet auf sie — s. Tuning unten.)

2. **Der per-Provider-EPG-Trigger untergräbt die Barriere (die scharfe Falle).** `RefreshExecution.kt:111-113`
   feuert bei **jedem** erfolgreichen Playlist-Import `enqueueEpgRefresh` für die Quellen DIESES Providers
   (wenn Pref an). D.h. während Phase 1 würde ein fertiger P1 sofort EPG anstoßen — genau das frühe EPG gegen
   unvollständigen Katalog, das wir vermeiden wollen. → Das `restoreChain`-Flag muss diesen Trigger im
   Playlist-Worker **unterdrücken** (die Kette besitzt EPG in Phase 2).

Das `restoreChain`-Flag macht also zweierlei im Playlist-Worker: (a) terminal→success (Kette läuft weiter),
(b) per-Provider-EPG-Trigger aus.

**Tuning — flaky Provider darf EPG nicht stundenlang blockieren.** Wartet die Kette auf einen transient
retrienden Provider bis `MAX_REFRESH_ATTEMPTS=10` (Backoff → Stunden), verzögert das EPG für ALLE. → Für die
restore-Kette einen **reduzierten Retry-Cap** (z.B. 2-3) verwenden; danach proceed (der flaky Provider wird
vom periodischen Refresh + dessen EPG-Trigger später eingesammelt).

**Empty-Fälle:** keine aktiven Provider → Kette entfällt (kein Katalog → EPG sinnlos). Provider aber keine
EPG-Quellen → nur Phase 1 (`beginUniqueWork(playlists).enqueue()`, kein `.then`).

### Szenario-Matrix (durchgespielt)
| # | Szenario | Verhalten mit dem Orchestrator |
|---|---|---|
| S1 | geteilte EPG-Quelle S→P1,P2,P3 | Barriere: alle Kataloge vor Phase 2 → S mappt an alle. ✓ |
| S2 | ein Provider scheitert (Credentials) | `restoreChain`→success → Kette läuft; EPG mappt an die anderen. ✓ |
| S3 | EPG-Quelle an keinen aktiven Provider gelinkt | refresht, mappt nichts (nur Feed-Stats). Harmlos. |
| S4 | alle Provider deaktiviert | keine Phase 1 → Kette entfällt. ✓ |
| S5 | Provider ohne EPG-Quelle | Phase 1 refresht ihn; Phase 2 enthält nur existierende Quellen. ✓ |
| S6 | App gekillt mitten in der Kette | WorkManager-Continuation überlebt Prozess-Tod, setzt fort. ✓ |
| S7 | Restore 2× (Re-Import) | `REPLACE` auf `RESTORE_REFRESH_WORK` cancelt alte Kette, startet neu. ✓ |
| S8 | normaler Refresh (periodisch/app-start) parallel | `RefreshRunGuard` dedupt gleicher Provider/Source; redundant, sicher. |
| S9 | 10-Min-Limit bei großer Config | jeder Node eigenes Budget; Kette spannt über Zeit. ✓ |
| S10 | per-Provider-EPG-Trigger | via `restoreChain` unterdrückt → kein frühes EPG. ✓ (die Falle) |
| S11 | logoPriority=EPG | EPG-Logos erscheinen nach Phase 2 (read-time-Auflösung + Coil); kein Extra-Schritt. |
| S12 | EPG-Priorität (mehrere Quellen je Kanal) | alle Quellen in Phase 2 → alle Mappings da → read-time-Winner korrekt. ✓ |
| S13 | Provider `CREDENTIALS_REQUIRED` (STANDARD-Backup) | Playlist-Worker wirft terminal → `restoreChain`→success → Kette läuft; leerer Katalog, EPG mappt nichts für ihn. ✓ |
| S14 | Manual-EPG-Mapping (Phase 2) | Phase-2-EPG läuft nach vollem Katalog → mein `rebindRestoredManualMappings` greift zuverlässig. ✓ |

### Betroffene Dateien (F1)
- `worker/.../RefreshWorkScheduler.kt` — `enqueueRestoreRefresh(providerIds, epgSourceIds)` (Continuation);
  `RefreshWorkRequests` (Requests mit `restoreChain`-Flag).
- `worker/.../WorkerContracts.kt` — `RESTORE_REFRESH_WORK`, `INPUT_RESTORE_CHAIN`.
- `worker/.../RefreshWorkers.kt` / `RefreshExecution.kt` — `restoreChain` im Playlist-Pfad: terminal→success +
  EPG-Trigger-Suppression; reduzierter Retry-Cap für die Kette.
- `app/.../MainActivity.kt` — `runStandardRestore` nutzt `enqueueRestoreRefresh` statt der 2 Schleifen.
- Tests (`worker/.../*Test`): Reihenfolge (EPG-Requests hängen an allen Playlist-Requests), `restoreChain`
  terminal→success, Trigger-Suppression, Empty-Fälle.

## F2 — WatchNext-Sync: Flackern + redundanter Leer-Sync (niedrig, FIX)

**Problem:** Mehrere `syncWatchNext` feuern gleichzeitig (jeder `setProviderStatus` je Provider, ohne Mutex —
`SystemIntegrationRepositories.kt`), plus der sofortige `MainActivity.kt:467`-Sync. Jeder Sync macht
**nicht-atomar** delete-all-then-insert (`WatchNextIntegration.kt:156-164`) auf dem System-`WatchNextPrograms`-
Provider → bei Überlappung Flackern/transienter Verlust; der Endzustand ist korrekt (letzter Active-Sync
gewinnt), aber unruhig während des Restore-Refresh. Der sofortige Sync läuft zudem gegen leeren Katalog
(löscht, fügt nichts ein — bis der Refresh neu befüllt).

**Fix:** einen **Mutex** um `syncWatchNext` (in `AppContainer.syncWatchNext` bzw. `WatchNextSynchronizer.sync`)
→ Syncs serialisieren, kein Interleaving von delete/insert. Klein, risikoarm. Der sofortige MainActivity-Sync
bleibt (er räumt stale WatchNext, wenn KEIN Refresh läuft), wird aber vom Mutex serialisiert. Optional:
rapid-fire Syncs coalescen (latest-wins), reicht aber der Mutex.

## F3 — WatchNext × PIN (niedrig, OPTIONAL — zurückgestellt)

Nach `pinSecurityStateStore.clear()` (Restore) synct WatchNext PIN-los → zuvor geschützte „Fortsetzen"-Items
landen kurz ungefiltert in der System-Row. **Heilt sich**: PIN-Neusetzen (`MainActivity.kt:1427`) triggert
einen Sync; und ohne PIN ist ohnehin nichts geschützt. **Zurückgestellt** — die PIN/Parental-Funktion ist noch
nicht ausführlich getestet (User-Entscheidung); zusammen mit dem PIN-Test später angehen.

## Kontext-only (kein Fix nötig)

### K1 — Post-Transaktion außerhalb der DB-Transaktion (theoretisches Crash-Fenster)
Restore = zwei Stufen: (1) `database.withTransaction { }` schreibt ALLE Room-Daten atomar; (2) DANACH, außerhalb
der Transaktion: Secrets (Keystore), `pinSecurityStateStore.clear()`, Preferences (DataStore),
`selectedProviderId`. Stufe 2 KANN nicht in der Room-Transaktion liegen (Keystore/DataStore sind separate
Stores). Crash/Kill zwischen Commit und Stufe 2 → Teilzustand (z.B. Provider ohne geschriebene Credentials,
oder PIN noch nicht gecleart). **Fenster = wenige ms.** Recovery = Backup einfach erneut importieren (Restore
ist idempotent: löscht + schreibt neu). **Empfehlung: dokumentieren, NICHT fixen** (geringer ROI; echte
Atomarität über Room+Keystore+DataStore ist nicht machbar; ein „restore-in-progress"-Flag + Resume wäre
Over-Engineering für ein ms-Fenster).

### K2 — Credentials-Required-Provider → Nutzerdaten bleiben `isPending` (kein Bug)
Ein Provider ohne nutzbare Credentials (STANDARD-Backup ohne Secrets, oder fehlende Quelle) wird als
`CREDENTIALS_REQUIRED` restauriert → Refresh wirft `RefreshAuthenticationException` → nie `Active` → Katalog-
Import läuft nie → mein Reconcile läuft nie → dessen Favoriten/Progress/History bleiben `isPending=true`.
**Das ist korrektes Verhalten:** ohne Katalog gibt es keine Kanäle/Filme, auf die die Favoriten/History
auflösen könnten — sie SOLLEN unsichtbar sein. Sobald der User Credentials nachträgt + der Refresh erfolgreich
läuft → Reconcile bindet alles → sichtbar. Einziger Rest: harmlose `isPending`-Waisenzeilen, falls Credentials
nie nachgetragen werden (unsichtbar, klein). **Empfehlung: keine Aktion** (ggf. 1 Satz Doku).

### K3 — `updateBackup`-Kommentar (DONE)
Der Restorer-Kommentar behauptete, Backup-Prefs würden restauriert — tatsächlich setzt `updateBackup(
restored.backup)` sie auf Default zurück (einziges Feld `lastExportDir` ist gerätelokal). Kommentar
korrigiert (`StandardBackupRestorer.kt:118`). ✅

## Betroffene Dateien (bei Umsetzung F1+F2)
- F1: `app/.../MainActivity.kt` (runStandardRestore EPG-Enqueue) und/oder `worker/.../RefreshExecution.kt` +
  `RefreshWorkScheduler.kt` (REPLACE-Variante / Continuation).
- F2: `app/.../di/AppContainer.kt` (`syncWatchNext` + Mutex) oder `app/.../system/WatchNextIntegration.kt`.
- Tests: WatchNext-Sync-Serialisierung (`WatchNextIntegrationTest`), EPG-nach-Katalog-Reihenfolge
  (Worker-/Integrationstest, soweit ohne Emulator abbildbar).
