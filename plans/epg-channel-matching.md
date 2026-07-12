# Plan: EPG↔Channel-Matching verbessern (Fix 1 + 2)

Status: **abgeschlossen** (2026-07-12). Fix 1+2 umgesetzt. Gates grün (detekt/assembleDebug/test);
Migration 15→16 + EPG-Matching connectedAndroidTest grün (15 EPG-Tests + 2 neue); Migration lief sauber
auf der echten v15-DB (App startet, Daten intakt). Live „EPG-Source hinzufügen"-UI-Flow nicht per adb
gefahren (Leanback-IME blockt URL-Eingabe) — Matching ist per Instrumented-Test end-to-end verifiziert.

## Befund (verifiziert)

Auto-Matching `buildAutomaticMappings` (RoomEpgRepository.kt:307): Tier 1 `channel.remoteId` == XMLTV-id,
Tier 2 `channel.name` == display-name. `normalize()` = nur `trim().lowercase()` (Z.414). Kein Fuzzy.

- **Tier 1 tot:** M3u-`remoteId` ist `"channel:tvg-id:<id>"` (Prefix via `stableChannelId`, M3uContracts.kt:151);
  Xtream `epg_channel_id` wird beim Import **verworfen** (ChannelEntity hat kein Feld, RoomCatalogImportRepository.kt:391).
  → verglichen gegen rohe XMLTV-id, nie gleich. Nur Tier 2 (exakter Name) greift real.
- **Normalisierung trivial:** „ZDF HD"≠„ZDF", „SAT.1"≠„Sat 1".
- Der Auto-Mapping-Test (RoomEpgRepositoryTest.kt:57) seedet `remoteId="ard.de"` (roh) — unrealistisch,
  versteckt den Prefix-Bug.

Gut & bleibt: Manual-Mapping (persistiert, überlebt Re-Import), `epg_channel_mappings`-Tabelle,
EPG-Icon als Logo-Quelle (`EFFECTIVE_LOGO_COLUMN`).

Open-Source-Referenz: StreamVault (4-Tier + persistierte Tabelle + Normalizer), OwnTV (tvg-id + Jaro-Winkler
+ aggressiver Normalizer), AerioTV (5-Key exakt), BBC (2-Key exakt, in-memory). Fuzzy (Tier 3) bewusst
zurückgestellt (False-Positive-Risiko; falsches EPG schlimmer als keins).

## Fix 1 — rohe EPG-id nutzbar machen (größter Hebel)

1. `ChannelEntity` (VivicastEntities.kt:78): neue Spalte `@ColumnInfo(defaultValue = "NULL") val epgChannelId: String? = null`.
2. DB: `VIVICAST_DATABASE_VERSION` 15→16; `Migration15To16` = `ALTER TABLE channels ADD COLUMN epgChannelId TEXT DEFAULT NULL`;
   in VivicastDatabaseFactory registrieren. Room generiert `16.json` (exportSchema=true).
3. Import befüllt die Spalte:
   - M3u `M3uChannel.toEntity` (RoomCatalogImportRepository.kt:364): `epgChannelId = tvgId`.
   - Xtream `XtreamLiveStream.toEntity` (Z.385): `epgChannelId = epgChannelId`.
4. Matcher (RoomEpgRepository.kt:323): Tier 1 = `channel.epgChannelId?.takeIf{ it.isNotBlank() }?.let { xmlById[it.normalize()] }`,
   dann Tier 2 (Name). `remoteId` bleibt die Identität (unberührt).

## Fix 2 — starke Namens-Normalisierung (nur Tier 2 + Programm-Titel bleibt wie ist)

5. Neue `normalizeName()` (epg-Modul, testbar): lowercase(ROOT) → Diakritika strippen (NFD + combining marks)
   → Klammer-Gruppen entfernen → Separatoren `[._\-:|/]`→Space → Quality-Tokens als ganze Wörter weg
   (`hd uhd fhd sd 4k 8k hq hevc h264 h265`) → Whitespace kollabieren → trim. Token-Liste = Tuning-Knopf.
6. `buildAutomaticMappings`: `xmlByName` mit `displayName.normalizeName()`; Tier 2 matcht `channel.name.normalizeName()`.
   `normalize()` (trim+lowercase) bleibt für Tier 1 (id) + Programm-Titel.
7. `confidence`: id-Match `1.0f`, name-Match `0.7f` (Label wird aussagekräftig; billig).

## Tests

- `RoomEpgRepositoryTest`: `seedLiveChannel` setzt `epgChannelId`; bestehende Seeds `remoteId="ard.de"` →
  `epgChannelId="ard.de"`. Neue Tests: (a) id-Tier matcht via epgChannelId trotz abweichendem Namen;
  (b) name-Tier matcht „ZDF HD"↔„ZDF" via `normalizeName`.
- Unit-Test für `normalizeName` (reine Funktion).
- `VivicastDatabaseMigrationTest`: 15→16 ergänzen.

## Gates & Verifikation

`detekt` + `assembleDebug` + `test`; androidTest (Migration + EPG) am Emulator. Emulator: josxha-Playlist +
eine EPG-Source hinzufügen, prüfen dass mehr Channels EPG/Logo bekommen.

## Nicht im Scope

Fuzzy/Tier 3 (Phase 2), Domain-Model-Änderung (Matcher nutzt ChannelEntity direkt), Manual-Mapping/Persistenz/
EPG-Icon (schon gut). Kein neues Modul.
