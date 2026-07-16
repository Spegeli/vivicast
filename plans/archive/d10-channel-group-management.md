# D10 — per-playlist channel-GROUP management: show / hide / sort

Status: **IMPLEMENTED + owner-driven on emulator (2026-07-16).**

### Update 2026-07-16 (post non-blocking-imports)
- Group-panel drive fully done: all-visible chips, focus-escape fixes, reorder as a `VivicastDialog` overlay,
  **Reset-Order** (Manual mode), empty-state reasons, per-provider refresh label.
- **Freshness gate REMOVED**: the "Gruppen verwalten" EPG/playlist-refresh lock is gone — imports are
  non-blocking now (staged delta-merge; see `plans/nonblocking-db-imports.md`), so group edits apply during a
  refresh without queuing. The global refresh-button cross-lock stays (playlist→EPG auto-chain rationale).
- **Diagnostics** (app-layer): `group_hidden`/`group_shown` (target=categoryId), `groups_bulk_hidden`/`_shown`
  (type,count), `group_reordered` (type,count), `group_order_reset` (type), `sort_mode_changed` (type,mode),
  `new_groups_policy_changed` (type,policy). detekt baseline now 34.
- Deferred (documented): optimistic `pendingRefresh` flag — obsoleted by the gate removal.

Original status below (historical).

Status: IMPLEMENTED (GO 2026-07-16) — verified static + on-device DB; group-panel drive pending owner.

### Implementation status (2026-07-16)
**Built + verified (compile + detekt + assembleDebug green):**
- DB **v17**: `CategoryEntity.manualSortOrder` + `provider_category_settings` table + `Migration16To17`
  (+ DAOs). Migration 16→17 + schema **PASSED on emulator** (`:core:database:connectedDebugAndroidTest`).
- Importer: source-appearance order (was A→Z) + preserves `isHidden`/`manualSortOrder` + hide-new policy.
- Domain `CategorySortMode`/`CategoryGroupSettings`; mode-aware `observeCategories`; new
  `CategoryGroupRepository` (observe-all / hide / bulk-hide / reorder / sort-mode / hide-new-policy);
  provider-delete cleans settings; sort-helper **unit test green**.
- Group panel wired end-to-end: `AppContainer` → MainActivity → SettingsRoute → `SettingsViewModelFactory`
  → `SettingsViewModel` (state + 8 methods) → `SettingsUiState` → `ProviderGroupsControls` →
  `ProviderSettingsPanel` (`showGroups` layer) → **`ProviderGroupsPanel`** (type chips · sort-mode chips ·
  new-groups toggle · bulk show/hide · per-group switch list · inline `VivicastReorderList` in MANUAL).
- Backup round-trips `manualSortOrder` + the settings table.
- Reached via the actions-menu "Gruppen verwalten" row (freshness-gated on refresh).

**Owner-confirmed working (manual test):** back-from-editor, connection-test-in-menu (counts inline),
refresh-lock. **Just fixed (rebuild pending owner re-test):** actions-header layout (Updated on the right,
like the overview card) + "Gruppen verwalten → Home" focus escape (park on actions↔groups transitions).

**Not yet done / deferred:** interactive drive of the group panel itself (owner re-testing now);
diagnostics for group hide/sort events (deferred — needs app-layer callbacks, low value); docs alignment
(Phase 11, edits ../vivicast-docs → needs owner go); optimistic refresh-in-flight flag (using
status==Refreshing only). Original planning record preserved below.
Grounded 2026-07-16 by 3 read-only research agents (vivicast-docs, current app code, 5 reference IPTV
apps) + owner decisions over two rounds. Companion: `plans/reorder-dpad-list.md` (reorder component reused).

---

## 1. What this is (from binding docs + owner)

Per-playlist (per-provider) **channel-GROUP / category** management: show / hide, a **sort mode**
(playlist order · name · manual), a **new-groups policy**, and **bulk show/hide**.
- **Group = `CategoryEntity`** (provider `group-title` / Xtream `category_id`). **NOT per-channel**
  (`ChannelEntity` has no such fields; PRD `02` — channel numbers verbatim from provider). Per-channel
  hide/reorder stays out of scope.
- Docs: PRD `05` "Gruppen verwalten" (Anzeigen/Ausblenden/Sortieren) under "Provider bearbeiten";
  `06-data-model.md` §6.3 + "Ausblenden und Sortieren" ("providergebunden"); Screen `08:175`;
  `components/settings.md:323`. Backup `10` requires group visibility + sort in the encrypted backup.

### Locked decisions
| Topic | Decision | Basis |
|---|---|---|
| Level | Group/category only (Live-TV, Filme, Serien) | docs |
| Placement | Reached as a **"Gruppen verwalten" action in the new Playlist-Aktionen menu** (see `plans/playlist-actions-menu.md`), not a row in the edit form | Screen-doc 08 Detailbereich + owner restructure |
| Surface | **Inlay panel** (full-width inline sub-view), NOT a modal popup | owner (R2 Q1) — see §3 wireframes |
| Toggle vs sort | Row toggle = show/hide (all modes); manual reorder only in sort-mode = Manuell | owner (R1 Q2) |
| Sort commit | Immediate on drop (reuse `VivicastReorderList`) | owner (R1 Q3) |
| Sort modes | `PLAYLIST` (source order, default) · `NAME` (A→Z) · `MANUAL` (user order) | owner (R2) |
| Name direction | A→Z ascending | owner (R2 Q4) |
| New-groups policy | Per-(provider,type) toggle: new categories from a refresh default shown or hidden | owner (R2) |
| Bulk | "Alle anzeigen" / "Alle ausblenden" for the active type | owner (R2) |
| Scope of mode + policy | **Per (provider, type)** — each of Live-TV/Filme/Serien has its own | owner (R2 Q2) |
| Persistence | **Room** — new side table + a new `CategoryEntity` column. DB migration **v16→v17** (app pre-live) | owner (R2 Q3) — best-for-scale over DataStore; see §2 |
| Identity across refresh | Accept reset when upstream renames a group (key `(providerId,type,remoteId)`) | docs: verbatim, no merge |
| Freshness gate | Group action **disabled + "wird aktualisiert…" while a refresh for the provider is in flight**; enabled when done → panel opens on fresh groups. **Replaces** the earlier source-dirty guard | owner (restructure) |

---

## 2. Persistence model (Room, best-for-scale — chosen over DataStore)

Rationale (owner asked for the better long-term architecture, not the cheapest): per-(provider,type)
config + manual order is relational, per-provider user-state — the exact shape the app already models in
Room (`ProviderEpgSourceEntity(priority)` + `rewritePriorities`; `FavoriteEntity.sortOrder`). Room gives
FK-cascade integrity on provider deletion (ADR-009), SQL ordering, one backup pipeline, and a consistent
home for future per-category state (rename, lock, channel order). DataStore's only edge was "no migration",
which the owner discounted.

- **`CategoryEntity`** (`core/database/.../VivicastEntities.kt:55-66`) — reuse `isHidden`; **repurpose
  `sortOrder` = source appearance order** (importer writes source order, not the current A→Z); **add
  `manualSortOrder: Int?`** (nullable; user order; only read in MANUAL mode; preserved across import like
  `isHidden`). Source and manual order kept independent → switching modes loses nothing.
- **New table `provider_category_settings`**: `providerId TEXT`, `type TEXT`, `sortMode TEXT NOT NULL
  DEFAULT 'PLAYLIST'`, `hideNewGroups INTEGER NOT NULL DEFAULT 0`, `PRIMARY KEY(providerId, type)`,
  `FOREIGN KEY(providerId) REFERENCES providers(id) ON DELETE CASCADE`, index on `providerId`.
- **Migration v16→v17** (`VivicastDatabase.kt:71` is at 16): `ALTER TABLE categories ADD COLUMN
  manualSortOrder INTEGER` (nullable) + `CREATE TABLE provider_category_settings …`. Add `17.json` +
  `VivicastDatabaseMigrationTest` + `VivicastDatabaseSchemaTest` cases.
- **Read ordering** (`observeVisibleCategories`, currently `CatalogDao.kt:18-25`): keep the `isHidden=0`
  filter; drive `ORDER BY` by mode (mode read from the settings table / passed as param):
  `PLAYLIST → sortOrder` · `NAME → name COLLATE NOCASE` · `MANUAL → manualSortOrder` (null last, fallback
  `sortOrder, name`). Either a `:mode`-parameterised `CASE` ORDER BY, or repo combines a settings Flow with
  the category Flow. Ordering stays in the data layer (not a Composable).

---

## 3. UI — inlay panel (owner-approved wireframes A/B; C = rejected popup)

Entry: a **"Gruppen verwalten" action in the Playlist-Aktionen menu** (see
`plans/playlist-actions-menu.md`), reached by clicking a playlist card. **Freshness gate:** the action is
disabled and labelled **"wird aktualisiert…"** while a refresh for that provider is in flight (async
WorkManager); it enables once the refresh completes → the panel always opens on freshly imported groups.
**No source-dirty guard needed** — editing is a separate menu action, so the group panel can never be
entered mid-edit. Empty case (no groups, no refresh running): panel shows an empty-state hint.

Click → an **inline group-management panel** (not a modal — too cramped with the added controls):
- **Type tabs:** Live-TV | Filme | Serien. Types with 0 categories shown disabled/empty. Each type is its
  own hide/sort/policy scope.
- **Controls row** (per active type): `Sortierung` selector (Wiedergabeliste / Name / Manuell) ·
  `Neue Gruppen` segmented (Anzeigen | Ausblenden) = the new-groups policy · `Alle anzeigen` /
  `Alle ausblenden` bulk buttons.
- **Group list:** every group of the type incl. hidden (hidden = dimmed/strikethrough → this is also the
  un-hide surface). OK on a row = toggle show/hide → **immediate write**.
- **Manual sort:** only when Sortierung = Manuell, a `Sortieren` action swaps the list body to
  `VivicastReorderList` **inline** (OK=pickup, ▲▼ move, commit-on-drop → immediate write). Controls locked
  during pickup. Back layering: pickup → cancel pickup; else sort → exit sort; else close panel. In
  PLAYLIST/NAME modes the manual reorder is hidden/disabled.

Bulk + policy are available in all modes; manual reorder only in MANUAL.

---

## 4. Blast radius (by module — no new module, no DI migration)

- **`core/database`** —
  - `CategoryEntity`: `+ manualSortOrder: Int?`.
  - New `ProviderCategorySettingsEntity` + `ProviderCategorySettingsDao` (get/observe/upsert; default row
    on demand).
  - `CatalogDao`: mode-aware `observeVisibleCategories` (ORDER BY by mode); `observeAllCategories(provider,
    type)` (no isHidden filter, for the management list); `setCategoryHidden(id, hidden, now)`;
    `setCategoriesHiddenForType(provider, type, hidden, now)` (bulk); `updateManualSortOrder(id, order, now)`.
  - `VivicastDatabase`: v17 + `Migration16To17` + `17.json`.
- **`data/media`** — `RoomCatalogImportRepository.buildCategories` (177-221): write `sortOrder` = **source
  appearance order** (was `sortedBy { name }`, 189-204); preserve `isHidden` (already, :205) AND new
  `manualSortOrder`; new categories get `isHidden = hideNewGroups` policy (read from settings) and
  `manualSortOrder = null`. `MediaRepository`/`RoomMediaRepository`: expose settings + the new writes
  (`observeAllCategories`, `setGroupHidden`, `setGroupsHiddenForType`, `reorderGroups(orderedIds)` in a
  `withTransaction`, `observeGroupSettings`, `setSortMode`, `setHideNewGroups`).
- **`feature/settings`** — `ProviderEditor`: "Gruppen verwalten" row + guard; the inlay panel (tabs +
  controls + list + inline manual sort). `SettingsViewModel` + `SettingsUiState`: managed-groups list +
  settings + methods (`runCatching { repo.x() }`, mirror `linkEpgSourceToProvider` `SettingsViewModel.kt:252`).
  `ProviderSettingsPanel`/`SettingsRoute`: pass-through + `scope.launch`. German strings panel-side.
- **`core/designsystem`** — reuse `VivicastReorderList` inline (no overlay). New strings ("Gruppen
  verwalten", "Sortierung", "Wiedergabeliste"/"Name"/"Manuell", "Neue Gruppen", "Anzeigen"/"Ausblenden",
  "Alle anzeigen"/"Alle ausblenden", "Sortieren"/"Fertig", guard + empty hints) → **both** locale files.
- **`app`** — backup extension (below). Not app-hoisted otherwise (category mgmt = feature + data, like EPG).
- **Backup** — `StandardBackupCategory` already round-trips `isHidden`+`sortOrder` (`StandardBackup.kt:79-86`,
  exporter `:201-212`, restorer `:283-284`). **Add `manualSortOrder`** to it, and add a new
  `StandardBackupCategorySettings` (the settings table) to export/restore + `StandardBackupTest`. Docs
  require group visibility+sort in backup.

---

## 5. Build sequence (each batch keeps gates green)

1. **Schema + migration** — `manualSortOrder` column + `provider_category_settings` table +
   `Migration16To17` + `17.json`. Migration + schema tests.
2. **DAO + repo writes** — settings DAO; category writes (hide, bulk-hide, manual reorder in a
   transaction); mode-aware read; `observeAllCategories`. Unit tests: hide flips; bulk flips all of a type;
   reorder writes `manualSortOrder`; mode switches ORDER BY; settings upsert.
3. **Importer** — `buildCategories`: source-order `sortOrder`; preserve `manualSortOrder`; new-category
   `isHidden` = policy. Unit tests: user hide + manual order survive re-import; new group appears at source
   position with the policy's hidden state; verify M3U/Xtream category source order is preserved by the parser.
4. **VM/UiState** — managed-groups list + settings + methods. VM test.
5. **UI** — row + guard + inlay panel (tabs, controls, list, inline manual sort). Strings both locales.
6. **Verify on emulator** — API 28 floor + 36 ceiling (DB write + migration): hide → gone from the
   Live-TV/Filme/Serien rail; each mode orders correctly; manual reorder holds; **refresh → hide + manual
   order survive**; new upstream group honours the policy; bulk works; backup→restore→refresh survives.

## 6. Micro-defaults (owner OK'd round-1; call out any change)
- New upstream groups: appended at source-order position; `manualSortOrder = null` (sink to end in manual
  mode until placed); hidden state = the per-(provider,type) policy.
- Uncategorized group (`__UNCATEGORIZED__`): normal group — hideable + sortable. Note: today the importer
  force-pins it first (the `""` key at `RoomCatalogImportRepository.kt:190`); dropping the name-sort for
  source order means it lands at its natural source position (usually last). Keep it pinned first, or let
  it fall naturally? **Owner-confirmed: natural position** in PLAYLIST mode (drop the forced-first pin).
- Hide reach: category browse rails only (Live-TV/Filme/Serien via `observeVisibleCategories`); Home/Suche
  aren't category-driven → unaffected.
- Manual reorder operates on all groups of the type (hidden included) so order is consistent when un-hidden.
- Default mode = `PLAYLIST`; default policy = show new groups (`hideNewGroups = false`).
- **Behaviour change:** default browse order becomes source order (was A→Z). Intended (Name is now opt-in).
- Long lists (100+): basic ±1 move + auto-scroll for v1; `ponytail:` hold-accelerate / ◄►±10 deferred.

## 7. Gates
Per batch: `.\gradlew.bat detekt` (baseline 36 — don't grow), `:app:assembleDebug --rerun-tasks`, `test`.
**Migration/schema present** → `VivicastDatabaseMigrationTest` + `VivicastDatabaseSchemaTest` + `17.json`.
Emulator floor+ceiling for the DB write + migration path (step 6).

## 8. Not doing (YAGNI)
- No per-channel hide/reorder. No custom-category creation (PRD: post-v1). No `VivicastReorderOverlay`
  wrapper (inline embed suffices). No DataStore for this state (Room chosen for scale). No source-dirty
  guard — the Playlist-Aktionen restructure + refresh-in-flight lock supersede it.

## Owner-confirmed (2026-07-16)
- Persistence model — Room side table + `manualSortOrder` column + v16→v17 migration. **Confirmed.**
- Micro-defaults §6, incl. the default-order-becomes-source-order behaviour change + "show new groups"
  default. **Confirmed.**

## Still NO GO
Owner is adding more requirements before implementation. Planning only — no code until an explicit GO.
