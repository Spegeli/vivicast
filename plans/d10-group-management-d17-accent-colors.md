# G6 remaining builds — D10 (channel-group management) + D17 (accent colors)

Status: **planning — not started.** Deferred by the owner to a later day (2026-07-13: "morgen").
This file captures the research done so far + the decisions I need from the owner **before** building.
No code touched. Source of decisions: `docs/archive/SETTINGS-DOCS-CODE-AUDIT.md` (D10, D15, D17 = the three
G6 builds; D15 done + committed `f65fc8f`). Docs (docs→code) already rewritten to expect both features.

Investigation: 2 read-only Explore agents (2026-07-13) mapped current state; findings below are grounded
with file:line. The design questions at the end are the blocking ones.

---

## D17 — multiple predefined accent colors + contrast guard (audit O-2 / O-4)

### Current state (the feature effectively does not exist yet)
- Two parallel single-value enums: UI `SettingsAccentColor { Blue }`
  (`feature/settings/.../SettingsModels.kt:32`) and DataStore `AccentColor { Blue }`
  (`core/datastore/.../UserPreferencesStore.kt:92`), bridged by a `when` mapper
  (`feature/settings/.../SettingsPreferenceMappers.kt:54-62`).
- **The setting is dead:** `VivicastTheme` (`core/designsystem/.../VivicastTheme.kt:23-42`) takes **no
  accent parameter**; accent is the fixed constant `VivicastColors.Accent = Color(0xFF00C8FF)` (`:60`).
  `MainActivity.kt:176` calls `VivicastTheme { }` and passes fontScale + transparency but **nothing for
  accent**. So `accentColor` is stored/mapped/backed-up but never rendered.
- No palette file (`Color.kt`) — the whole palette is `object VivicastColors` inside `VivicastTheme.kt:44-74`.
- **No contrast logic anywhere** (no luminance/WCAG/on-color). `onPrimary` is the fixed constant
  `TextOnAccent = Color(0xFF031525)` — a dark-on-cyan pairing that will NOT hold for other hues.
- `LocalVivicastColors` CompositionLocal is defined (`:208`) but **never provided or consumed** — every
  component reads the static `VivicastColors.Accent`/`AccentSoft`/`FocusRing` object directly
  (~10 files across `core/designsystem`, `feature/settings`, `feature/search`, `app`).
  `MaterialTheme.colorScheme.primary` is read **0 times**.
- Picker UI = generic text-list `SettingsChoiceDialog` (`AppearanceSettingsPanel.kt:240-247`); audit O-4
  keeps this (no color-swatch row). Persistence stores `.name` with graceful fallback
  (`DataStoreUserPreferencesStore.kt:241`) → **no DB/schema change needed** for new enum values.

### The gap → build sketch (once decisions below are made)
1. Add cases to both enums + extend the two `when` mappers + the label `when`
   (`AppearanceSettingsPanel.kt:106-109`) + string resources (de/en, in `core/designsystem`).
2. **Wire accent into the theme (the missing link):** give `VivicastTheme` an accent input fed from
   `preferences.appearance.accentColor` at `MainActivity.kt:176`, and **actually provide**
   `LocalVivicastColors` with an accent-swapped copy — because consumers read the static object, they must
   be routed through the CompositionLocal (or the theme) for accent to become dynamic. This is the real cost.
3. **Contrast guard is net-new:** per predefined hue, either compute an on-accent foreground via relative
   luminance, or ship pre-validated (accent, on-accent) pairs. The help string already promises
   "Focus and warning contrast remain enforced" (`settings_help_accent`).
4. Predefined hue palette is net-new (add to `VivicastColors` or a new enum→Color map).

### Blast-radius note
Focus colors (`FocusRing`, `FocusGlow`, `Focus`) are in the accent family and are **safety-critical on TV**
(D-pad focus must always be visible). Whether accent recolors focus, or focus stays fixed, is a decision.

---

## D10 — per-playlist channel-group management: show / hide / sort (audit D10)

### Current state (half-built at the data layer, no write path, no UI)
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

### Precedents to reuse (don't invent)
- **Per-provider state with ordering + immediate-write toggle dialog:** EPG-source assignment — join table
  `ProviderEpgSourceEntity(providerId, epgSourceId, priority)` (`VivicastEntities.kt:268`), dialog
  `ProviderEpgSourcesDialog` (`ProviderEditor.kt:918-949`) where each toggle writes immediately (no Save).
  A "Gruppen verwalten" row slots in as a 7th row in `providerEditControlItems` (`ProviderEditor.kt:721-797`)
  and mirrors this exact shape (+ a reorder affordance).
- **Favorites** (`FavoriteEntity`, `RoomFavoritesRepository`) — the other per-provider user-state pattern,
  pruned on import when the media disappears.

### Two persistence options (already modelled in-codebase)
- **A. Reuse `CategoryEntity.isHidden` + `sortOrder`** (smallest diff): add a `CatalogDao` update method,
  stop clobbering `sortOrder` on import (preserve it like `isHidden` already is), + editor UI. Mixes user
  state into the import mirror.
- **B. New side table** `provider_category_settings(providerId, categoryStableKey, isHidden, sortOrder)`
  (mirror `provider_epg_sources`), JOINed in `observeVisibleCategories`. Cleaner separation; survives
  category delete/recreate if keyed on `stableKey`. DB is at version 16 (`VivicastDatabase.kt:71`) → needs
  `Migration16To17` + `17.json` + migration/schema tests.

### Hardest risks (flagged by the investigation)
1. **Group identity across refresh** — user state keyed on `remoteId`/`stableKey` silently resets when a
   group is renamed upstream (M3U especially; Xtream `category_id` is sturdier). No durable surrogate exists.
2. **Ordering storage** — `sortOrder` is overwritten each import, so it can't hold user order without a
   change (preserve-on-import, or a side table). Hide is "free"; sort is the real work.
3. **TV reorder UX** — reordering with a D-pad remote is the hard UX (move-up/down buttons per group vs.
   a dedicated reorder mode). No precedent in the app.
4. **Scope leakage** — the shared `categories` table feeds Live-TV + Movies + Series; a category change is
   app-wide unless scoped by `type = "LIVE"`.

---

## Open questions for the owner (answer before building — asked tomorrow)

### D10
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

### D17
1. **Which predefined accents + how many?** Need the actual set — names + hex values (e.g. Blau / Grün /
   Violett / Orange / …). Keep "Vivicast Blau" as default?
2. **Contrast guard behaviour:** auto-compute the on-accent foreground per hue via luminance, OR ship
   pre-validated (accent, on-accent) pairs? What exactly must "stay enforced" — just text-on-accent, or
   also focus/warning legibility?
3. **Application scope / cost:** accent is currently hardcoded and read directly from `VivicastColors` in
   ~10 files. Approve routing accent through a dynamic theme/`LocalVivicastColors` (bigger, correct), or a
   minimal subset only? This is the main cost driver.
4. **Focus colors:** does the accent recolour `FocusRing`/`FocusGlow` (D-pad focus — safety-critical), or
   does focus stay a fixed colour independent of accent?

### Shared
5. **Order for tomorrow:** build D10 first or D17 first? *(D17 is more self-contained; D10 has the DB +
   TV-reorder-UX weight. A reasonable cut: D17 first as a warm-up, D10 second.)*

---

## Gates (when we build)
Per structural batch: `.\gradlew.bat detekt`, `:app:assembleDebug` (`--rerun-tasks` — stale-APK bug),
`test`. D10 adds DB work → also `VivicastDatabaseMigrationTest` + `VivicastDatabaseSchemaTest` + a new
`17.json`. Emulator check on API 28 floor + 36 ceiling for any storage/DB path. Watch the detekt baseline
on signature changes.
