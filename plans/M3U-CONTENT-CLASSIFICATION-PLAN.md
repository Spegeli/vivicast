# M3U Content Classification — Scope Audit & Implementation Plan

> Analyse-/Scoping-Lauf. **Kein Code geändert.** Basis: bestehende M3U-/Xtream-/Catalog-Import-Pipeline.
> Nutzerentscheide (Session): Zielumfang = **Import + Katalog + Playback**; M3U-Import-Flags werden
> **ignoriert** (jeder erkannte Typ importiert, Fallback = Live).

## Context

M3U importiert heute **pauschal als Live-TV** (`RoomCatalogImportRepository.importM3uLiveChannels` mappt
jeden Eintrag auf `ChannelEntity`; Parser generisch). Xtream trennt bereits Live/Movie/Series/Season/
Episode. Ziel (späterer Schritt): M3U-Einträge automatisch klassifizieren und in die **bestehenden**
Movie/Series/Season/Episode-Strukturen importieren + abspielbar machen. **Keine Room-Schemaänderung nötig.**

---

## 1. Aktueller M3U-Importfluss

- **Parser** (`:iptv:m3u`) — `iptv/m3u/.../M3uContracts.kt`: `M3uParser` (Z.5), `DefaultM3uParser`
  (Z.30–172), DTO `M3uChannel` (Z.14–28). **Generisch, keine Klassifizierung.**
  Felder: remoteId, name, streamUrl, categoryName(group-title), logoUrl(tvg-logo),
  channelNumber(tvg-chno/channel-id), tvgId, tvgName, isCatchupAvailable, catchupDays, catchupMode,
  catchupSource, rawAttributes. **NICHT** captured: `CUID` (liegt in `rawAttributes`).
- **Fetch/Read** (`:worker`) — `RefreshExecution.kt` `refreshM3uProvider` (Z.143–159): URL via
  `OkHttpTextFetcher.fetch` (Z.313–325); Datei via `credentials.inlineContent` (`TransientM3uSourceStore`,
  `data/provider/.../ProviderConfigurationModels.kt` Z.66–79). SAF-Picker app-hoisted.
- **Chain:** parse (Z.150) → `catalogImportRepository.importM3uLiveChannels` (Z.154).
- **Channel-Mapping** — `RoomCatalogImportRepository.kt` `importM3uLiveChannels` (Z.35–91),
  `M3uChannel.toEntity` (Z.329–348), Kategorie immer `CATEGORY_TYPE_LIVE`; Stream-URLs →
  `m3uStreamReferenceStore.replaceProviderReferences` (Z.80–89), key `(providerId, remoteId)`.
- **Stable-ID Channel** (`M3uContracts.kt` Z.144–158): tvg-id → name|group|number → name|group|hash(url);
  SHA-256/16B; URL-Secrets weggehasht.

## 2. Aktueller Xtream-Importfluss

- Orchestrierung `refreshXtreamProvider` (`RefreshExecution.kt` Z.161–207) → `importXtreamCatalog`
  (`RoomCatalogImportRepository.kt` Z.93–149).
- **Getrennte Import-Funktionen bestehen:** `upsertChannels`(197), `upsertMovies`(219),
  `upsertSeries`(236), `upsertSeasons`(253), `upsertEpisodes`(274), `buildCategories`(151),
  `buildSeasons`(298), `buildEpisodes`(316).
- **Wiederverwendbare Mapper:** `XtreamVodItem.toEntity→MovieEntity`(366),
  `XtreamSeriesItem.toEntity→SeriesEntity`(391), `XtreamSeason?.toEntity→SeasonEntity`(413),
  `XtreamEpisode.toEntity→EpisodeEntity`(433). DTOs: `iptv/xtream/.../XtreamModels.kt`.
- **Playback-URL** wird aus Server+Creds **gebaut** (`PlaybackStreamResolver.kt` Z.156–165). Movie/Episode-
  Entities haben **keine** URL-Spalte.

## 3. Aktuelle Datenbank-/Entity-Struktur

`core/database/.../VivicastEntities.kt`; DAO `CatalogDao.kt` (`@Upsert` je Typ).

| Inhalt | Entity/Tabelle | Pflichtfelder (Rest nullable) | für M3U nutzbar? | Schemaänderung nötig? |
|---|---|---|---|---|
| Channels | ChannelEntity/channels | id, providerId, categoryId, stableKey, remoteId, name, timestamps | ✅ heute genutzt | ❌ |
| Movies | MovieEntity/movies | id, providerId, stableKey, remoteId, name, timestamps | ✅ | ❌ |
| Series | SeriesEntity/series | id, providerId, stableKey, remoteId, name, timestamps | ✅ | ❌ |
| Seasons | SeasonEntity/seasons | id, providerId, seriesId, stableKey, seasonNumber, name, timestamps | ✅ | ❌ |
| Episodes | EpisodeEntity/episodes | id, providerId, seriesId, seasonId, stableKey, remoteId, episodeNumber, seasonNumber, name, timestamps | ✅ | ❌ |
| Categories | CategoryEntity/categories | id, providerId, stableKey, type, remoteId, name, sortOrder, timestamps | ✅ (LIVE/MOVIE/SERIES) | ❌ |
| Provider | ProviderEntity/providers | inkl. includeLiveTv/Movies/Series | ✅ | ❌ |

**KEINE Schemaänderung nötig** — Metadaten-Spalten nullable; M3U liefert Minimum (remoteId+name).

## 4. Aktuelle Stable-ID-/Key-Logik

`RoomCatalogImportRepository.kt` Z.518–543: `channelId/movieId/seriesId/episodeId =
"$providerId:$type:${stableHash(remoteId)}"`, `seasonId = ":season:stableHash("$seriesRemoteId:$season")"`,
`stableHash` = SHA-256 erste 16B. remoteId trägt Typ-Prefix → keine Cross-Type-Kollision; providerId-Scope
→ keine Cross-Provider-Kollision. (Channel-remoteId aus Parser, s. §1.)

## 5. Empfohlener Einbaupunkt

- **Classifier → `:iptv:m3u`** (neben Parser): reine String-Heuristik, **JVM-unit-testbar ohne Emulator**
  (M3U-Tests liegen dort). `M3uChannel` (Input) lebt hier.
- **Import-Routing → `:data:media` / `RoomCatalogImportRepository`**: alle `upsert*`/Mapper/Reference-Store
  dort; `:data:media` hängt bereits von `:iptv:m3u` ab.
- **Playback → `:data:playback` / `resolveM3u`** (heute Channel-only).
- **Nicht woanders:** Parser bleibt generisch; keine Business-Logik in AppContainer/Composables.

## 6. Empfohlene Zielstruktur

`:iptv:m3u` (rein): `M3uContentClassifier`(+Default), `sealed M3uContentClassification`
(Live / Movie(remoteId,title,year?,ext?) / SeriesEpisode(seriesRemoteId,seriesTitle,info,episodeRemoteId,ext?)),
`M3uSeriesEpisodeInfo(seasonNumber, episodeNumber, episodeTitle?)`.
`:data:media`: neue `importM3uCatalog(providerId, playlist)`, Classifier injiziert; wiederverwendet
`buildCategories`/`upsert*`/Mapper; Stream-Refs für Channel+Movie+Episode in **einem**
`replaceProviderReferences`.
`:data:playback`: `resolveM3u` +Movie/Episode.

## 7. Empfohlene Klassifizierungsregeln

Vorverarbeitung: Pfad extrahieren, **Query-String vor Extension-Check strippen** (`.mkv?token=x`→`.mkv`);
Name/Group lowercased.

**Precedence (erste Regel gewinnt):**
1. **Live-Guard:** `.ts`/`.m3u8` **oder** 24/7 (`24/7`,`24-7`,`24x7`) **oder** `cinema`/`live` ⇒ **Live**.
2. **Series:** `S01E01`/`S01 E01`/`S01.E01`/`S01-E01`/`1x01` ⇒ **SeriesEpisode** (Titel = Name ohne Muster).
3. **Movie:** `.mkv/.mp4/.avi/.m4v/.mov/.webm` ⇒ **Movie**.
4. **Schwache Hinweise (nur Tie-Breaker):** Pfad `/movie/`,`/series/`,`/live/`; `group-title` — nur wenn
   1–3 nichts ergaben und VOD-Container vorliegt. **Nie allein hinreichend.**
5. **Fallback:** **Live**.

**Stable-IDs M3U:** bevorzugt `tvg-id`, sonst normalisiert; `movie:`/`series:`/`episode:`-Prefix + hash;
Season via bestehendes `seasonId`. Kein Kollisionsrisiko (Prefix + providerId).

## 8. Integration in bestehende Import-Pipeline

- **Wiederverwenden (unverändert):** `upsert*`, `buildCategories/Seasons/Episodes`, `toEntity`-Mapper,
  Side-Effect-Cleanup, `replaceProviderReferences`, ID-Helfer.
- **Minimal erweitern:** `importM3uCatalog` (klassifizieren+bucketen+routen); `resolveM3u` (+Movie/Episode);
  `refreshM3uProvider` ruft `importM3uCatalog`; Refs um Movie/Episode-remoteIds.
- **Nicht ändern:** Parser generisch, Room-Schema, Provider-Save/Update/Delete, SAF/Picker,
  SettingsViewModel/Provider-Dialog, Xtream-Import, `TestProviderConnectionUseCase`, AppContainer-Logik.

## 9. Testplan

| Testfall | Modul | Erwartung |
|---|---|---|
| Live `x.ts` | :iptv:m3u (JVM) | Live |
| Movie `film.mkv` | :iptv:m3u | Movie, ext=mkv |
| Movie `film.mp4` ohne `/movie/` | :iptv:m3u | Movie |
| `Show S01 E01 .mkv` | :iptv:m3u | SeriesEpisode s1e1, Titel „Show“ |
| `Show S01E01` | :iptv:m3u | SeriesEpisode s1e1 |
| Series ohne `/series/` | :iptv:m3u | SeriesEpisode |
| `24/7 … .ts` | :iptv:m3u | Live (Guard) |
| `Cinema … .ts` | :iptv:m3u | Live |
| `.mkv?token=abc` | :iptv:m3u | Movie (Query gestrippt) |
| unbekannt/kein Signal | :iptv:m3u | Live (Fallback) |
| gemischte Playlist → DB | :data:media (instrumented) | Channels/Movies/Series/Seasons/Episodes + Refs korrekt |
| M3U-Movie Playback | :data:playback (JVM) | `resolveM3u` liefert direkte URL (Movie) |
| M3U-Episode Playback | :data:playback (JVM) | dito (Episode) |
| Fixtures | alle | nur `example.test`/`example.com`, keine echten URLs/Credentials |

Konventionen: JUnit4, Inline-Fixtures, `trimIndent()`, sanitisierte Domains.

## 10. Risiken / Blocker

- **Schemaänderung:** NEIN.
- **Playback-Resolver-Blocker (eingeplant):** `resolveM3u` akzeptiert heute nur `MediaType.Channel`;
  Movie/Episode brauchen Resolver-Erweiterung **und** gespeicherte direkte URLs. Ohne beide: Katalog
  sichtbar, Abspielen scheitert.
- **ID-Kompatibilität:** Prefix+providerId verhindern Kollision; Channel-IDs unverändert.
- **Bestehende Live-Imports:** Fallback=Live + Live-Guard schützen; gewollte Umklassifizierung via Neuimport.
- **Xtream-Regression:** ausgeschlossen (getrennte Pfade).
- **Performance:** O(n) Regex; Regex vorkompilieren; 10k<10s (bestehender Smoke).
- **False Positives:** 24/7/Cinema/Live-Guard + Fallback.

## 11. Empfohlener erster Implementierungsschritt

**Stufe A (risikolos):** nur `M3uContentClassifier` + Result-Typen in `:iptv:m3u` + JVM-Unit-Tests
(alle Fälle §9 Z.1–10). Kein Import/Playback berührt.
→ **Stufe B:** `importM3uCatalog` in `:data:media` + Worker-Umstellung + instrumented Test.
→ **Stufe C:** `resolveM3u` +Movie/Episode + `:data:playback`-Tests.

## 12. Validierung (späterer Schritt)

```powershell
.\gradlew.bat :iptv:m3u:testDebugUnitTest         # Stufe A
.\gradlew.bat :data:playback:testDebugUnitTest    # Stufe C
.\gradlew.bat :data:media:connectedDebugAndroidTest  # Stufe B (Emulator)
.\gradlew.bat detekt
.\gradlew.bat assembleDebug
```
Smoke bei Emulator: `M3uPlaybackSmokeTest`.

## 13. Git

`git status --short` (dieser Lauf, keine Code-Änderung — die 3 `M`-Einträge stammen aus der vorherigen
UI-Aufgabe; diese Datei ist neu/untracked):
```
 M core/designsystem/src/main/res/values-en/strings.xml
 M core/designsystem/src/main/res/values/strings.xml
 M feature/settings/src/main/java/com/vivicast/tv/feature/settings/ProviderAddFlow.kt
?? plans/M3U-CONTENT-CLASSIFICATION-PLAN.md
```
