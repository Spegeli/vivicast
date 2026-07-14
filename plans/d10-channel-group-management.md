# D10 — per-playlist channel-group management: show / hide / sort (audit D10)

Status: **planning — not started.** D17 (appearance colours) was split out to
`plans/d17-appearance-colors.md` on 2026-07-14; this file is D10-only now. No code touched. Source of
decisions: `docs/archive/SETTINGS-DOCS-CODE-AUDIT.md` (D10). Investigation grounded with file:line by
2 read-only Explore agents (2026-07-13). The open questions at the end are the blocking ones.

---

## Current state (half-built at the data layer, no write path, no UI)
- A "group" = `CategoryEntity` (`core/database/.../VivicastEntities.kt:55-66`), scoped **per-provider,
  per-type** (`type` = LIVE | MOVIE | SERIES). Domain mirror `Category` (`domain/.../VivicastModels.kt:45-56`).
- **The schema already has `sortOrder: Int` + `isHidden: Boolean`**, and the Live-TV read query already
  honours them: `CatalogDao.observeVisibleCategories` (`CatalogDao.kt:18-25`) =
  `WHERE ... isHidden = 0 ORDER BY sortOrder, name COLLATE NOCASE`.
- Group identity = source name/id: M3U `remoteId` = the raw `group-title` string; Xtream = `category_id`.
  Category id = `"$providerId:category:$type:${stableHash(remoteId)}"` (`RoomCatalogImportRepository.kt:555`).
- Across refresh: **`isHidden` IS preserved** (`isHidden = existingCategory?.isHidden ?: false`, `:205`);
  **`sortOrder` is NOT** — recomputed alphabetically every import (`sortedBy { name }` → `sortOrder = index`,
  `:189-204`). Categories no longer present are hard-deleted (`deleteRemovedCategories`).
- **No write path exists:** no DAO method mutates `isHidden`/`sortOrder` outside import. Hide is a dormant,
  read-only-honoured capability; sort needs preservation work. No hide/visibility/manual-sort UI anywhere.
- Render path a change must hook: `observeVisibleCategories` → `RoomMediaRepository.observeCategories`
  (`:34-37`) → `LiveTvViewModel.kt:91-99` → `LiveTvUiState.categories` → `LiveTvRoute.kt:421` (rendered in
  order, no client re-sort). **Same `categories` table also drives Movies + Series** (scope question).

## Precedents to reuse (don't invent)
- **Per-provider state with ordering + immediate-write toggle dialog:** EPG-source assignment — join table
  `ProviderEpgSourceEntity(providerId, epgSourceId, priority)` (`VivicastEntities.kt:268`), dialog
  `ProviderEpgSourcesDialog` (`ProviderEditor.kt:918-949`) where each toggle writes immediately (no Save).
  A "Gruppen verwalten" row slots in as a 7th row in `providerEditControlItems` (`ProviderEditor.kt:721-797`)
  and mirrors this exact shape (+ a reorder affordance).
- **Favorites** (`FavoriteEntity`, `RoomFavoritesRepository`) — the other per-provider user-state pattern,
  pruned on import when the media disappears.

## Two persistence options (already modelled in-codebase)
- **A. Reuse `CategoryEntity.isHidden` + `sortOrder`** (smallest diff): add a `CatalogDao` update method,
  stop clobbering `sortOrder` on import (preserve it like `isHidden` already is), + editor UI. Mixes user
  state into the import mirror.
- **B. New side table** `provider_category_settings(providerId, categoryStableKey, isHidden, sortOrder)`
  (mirror `provider_epg_sources`), JOINed in `observeVisibleCategories`. Cleaner separation; survives
  category delete/recreate if keyed on `stableKey`. DB is at version 16 (`VivicastDatabase.kt:71`) → needs
  `Migration16To17` + `17.json` + migration/schema tests.

## Hardest risks (flagged by the investigation)
1. **Group identity across refresh** — user state keyed on `remoteId`/`stableKey` silently resets when a
   group is renamed upstream (M3U especially; Xtream `category_id` is sturdier). No durable surrogate exists.
2. **Ordering storage** — `sortOrder` is overwritten each import, so it can't hold user order without a
   change (preserve-on-import, or a side table). Hide is "free"; sort is the real work.
3. **TV reorder UX** — reordering with a D-pad remote is the hard UX (move-up/down buttons per group vs.
   a dedicated reorder mode). No precedent in the app.
4. **Scope leakage** — the shared `categories` table feeds Live-TV + Movies + Series; a category change is
   app-wide unless scoped by `type = "LIVE"`.

---

## Open questions for the owner (answer before building)
1. **Scope:** Live-TV channel groups only (`type = LIVE`), or also Movies + Series categories?
2. **Persistence:** Option A (reuse `CategoryEntity` columns, smallest diff) or Option B (separate
   `provider_category_settings` side table, cleaner + rename-resilient via `stableKey`)? *(lean: B if we
   want user state to survive refresh cleanly; A if we want the minimal change.)*
3. **Identity across refresh:** accept that a hidden/reordered group resets when the upstream group is
   renamed (key on `stableKey`), or attempt name-based re-matching? *(lean: accept reset — matches how
   `isHidden` already behaves; re-matching is fragile.)*
4. **Reorder UX on a TV remote:** up/down move buttons per group, drag-style reorder mode, or no manual sort
   in v1 (hide-only first, sort later)?
5. **"Ausblenden" reach:** does a hidden group disappear only from the Live-TV rail, or everywhere
   (search / home / favorites source lists) too?
6. **Uncategorized group** (`__UNCATEGORIZED__`): also hideable / sortable, or fixed?

---

## Gates (when we build)
Per structural batch: `.\gradlew.bat detekt`, `:app:assembleDebug` (`--rerun-tasks` — stale-APK bug),
`test`. D10 (Option B) adds DB work → also `VivicastDatabaseMigrationTest` + `VivicastDatabaseSchemaTest` +
a new `17.json`. Emulator check on API 28 floor + 36 ceiling for any storage/DB path. Watch the detekt
baseline on signature changes.
