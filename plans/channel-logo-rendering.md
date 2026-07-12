# Plan: Channel-Logos direkt laden (+ Coil-Cache-Accounting)

Status: **abgeschlossen** (2026-07-12). Umgesetzt + am Emulator verifiziert (Logos rendern,
Coil-Disk-Cache füllt sich); Gates grün (assembleDebug/detekt/test). Kein Commit/Push ohne Freigabe.

## Problem (verifiziert am Emulator)

Import + Zuordnung der Logos funktionieren (66/66 Channels haben `logoUrl`, effektive Logo
= Playlist-Logo via `EFFECTIVE_LOGO_COLUMN`, URLs erreichbar). Die **Anzeige** bricht:

- `resolve*ImageModel` ([PlaybackOrchestration.kt](../app/src/main/java/com/vivicast/tv/PlaybackOrchestration.kt))
  liefert `mediaCacheStore.getEntry()?.file` **oder null** — nie die URL.
- `mediaCacheStore` wird nur vom globalen Maintenance-Worker (`MaintenanceRefreshOrchestrator`)
  befüllt — nicht beim Import; nur bei aktivem Hintergrund-Refresh-Toggle; Intervall ≥1h.
- Coil hat **keinen** Netzwerk-Fetcher (`coil-network-okhttp` nirgends verdrahtet) → kann URL nicht
  direkt laden.
- Ergebnis: leerer Cache → jedes resolve = null → Platzhalter-Initialen. Kein Live-URL-Fallback.

Alle 4 Referenz-Apps (AerioTV/BBC/OwnTV/StreamVault) laden Logos direkt von der URL über
Coil/Glide mit Netzwerk-Fetcher + Disk-Cache; kein Prefetch-Zwang.

## Fix (freigegeben: Direkt-URL + A+B mit)

1. **Dependency** `:app` ([app/build.gradle.kts](../app/build.gradle.kts)):
   `implementation(libs.coil.compose)` + `implementation(libs.coil.network.okhttp)`
   (beide Aliase existieren in `libs.versions.toml`).

2. **Singleton-ImageLoader** ([VivicastApplication.kt](../app/src/main/java/com/vivicast/tv/VivicastApplication.kt)
   implementiert `SingletonImageLoader.Factory`; Instanz via `AppContainer.imageLoader`):
   - `OkHttpNetworkFetcherFactory(callFactory = { appContainer.okHttpClient })` → globaler User-Agent
     **und** Debug-TLS-Bypass (Emulator erreicht Hosts).
   - `DiskCache`: `cacheDir/image_cache`, maxSize 100 MB.
   - `MemoryCache`: `maxSizePercent(0.15)`.
   - Designsystem-`AsyncImage` nutzt den Singleton automatisch → URLs laden dann live.

3. **URL-Fallback** in `resolve*ImageModel` (Channel/Movie/Series/Episode):
   `getEntry(...)?.file ?: <effektive URL>` — vorgeladenes File wenn da, sonst URL live.
   `mediaCacheStore`-Prefetch bleibt als optionaler Offline-Warmcache (nicht blockierend).

4. **A+B — Coil-Disk-Cache ins Cache-Accounting** (jetzt sinnvoll, da echter Disk-Cache existiert):
   - `SettingsViewModel` + Factory: injizierte Lambdas `imageCacheSizeBytes: suspend () -> Long`,
     `clearImageCache: suspend () -> Unit` (VM bleibt Coil-frei), gewired in
     `SettingsRoute` → `MainActivity` aus `appContainer.imageLoader.diskCache`.
   - `onReloadCacheStats`: Größe = `mediaCacheStore.stats` + Coil `diskCache.size`.
   - `onClearCache`: `mediaCacheStore.clear()` + `diskCache?.clear()` + `memoryCache?.clear()`.
   - Cache-Hilfetexte (de+en) anpassen.

5. **detekt-Baseline**: `SettingsRoute`-Signatur ändert sich durch die 2 neuen Params → die 2
   Baseline-Einträge (LongParameterList/LongMethod) auf neue Signatur anpassen (kein Wachstum).

6. **Tests**: `SettingsViewModelTest` — Fake-Lambdas prüfen Clear+Size-Summe. resolve-Fallback wird
   am Emulator verifiziert (AppContainer-Extension, schwer unit-testbar).

7. **Gates**: `detekt` + `assembleDebug` + `test`. Danach Emulator: Logos erscheinen in Live-TV,
   Cache-Größe spiegelt nach Blättern den Coil-Cache.

## Nicht im Scope

Prefetch-on-Import (komplexer, kein Live-Fallback), fuzzy Channel↔EPG-Matching (tvg-id reicht;
optional später wie OwnTV). Kein neues Modul, keine DI-/Room-Migration.
