# Vivicast – Claude Code Project Instructions

## Repository Layout

```
claude-code\
  vivicast\        ← app code (this repo, working directory)
  vivicast-docs\   ← reference docs (read-only during implementation)
```

From this repo root, docs live at `../vivicast-docs` (Windows: `..\vivicast-docs`).

If `../vivicast-docs` is missing, stop and ask the user before making any implementation changes.

## Product Baseline

- **Product name:** Vivicast
- **Package ID:** `com.vivicast.tv`
- **Platform:** Android TV (primary)
- **UI stack:** Kotlin + Jetpack Compose for TV
- **Playback:** Media3 / ExoPlayer
- **Persistence:** Room + DataStore
- **Secrets:** Android Keystore-backed — never plaintext Room storage
- **Main navigation:** `Home | Live-TV | Filme | Serien | Suche | Einstellungen`
- **Default start area:** Home (unless changed by supported settings)
- **Visible UI language:** German with umlauts. Required terms: `Kanäle`, `Über die App`. Exception: `Home` stays as-is.
- **No** server backend, account system, cloud sync, telemetry, external metadata provider, or automatic provider merging in PRD v1
- **No** provider-specific header/cookie in PRD v1. A **per-provider User-Agent override is allowed**
  (the global User-Agent under Allgemein is the default; a playlist may override it) — see
  `docs/SETTINGS-DOCS-CODE-AUDIT.md` D6

## Current Architecture Status

The P0–P3 architecture remediation is **completed** (see the completion report for the full record):

- 6/6 main feature areas (Home, Live-TV, Movies, Series, Search, Settings) use a ViewModel + immutable UiState.
- No direct Repository Flows/CRUD in feature Composables.
- Normal Routes read state via `collectAsStateWithLifecycle`.
- `PlayerRoute`'s `collectAsState` is a documented realtime-player exception.
- `PlaybackRequestFactory` / `PlaybackProgressRecorder` live in `:data:playback`.
- `TestProviderConnectionUseCase` lives in `:data:provider`.
- The designsystem is split into grouped `Vivicast*.kt` files (`VivicastComponents.kt` no longer exists).
- A detekt size/complexity gate exists.

Post-remediation feature work (2026-07-16, on main): a **playlist actions-menu** (Overview → Actions →
Editor/Groups), **per-playlist channel-GROUP management** (show/hide, sort mode playlist/name/manual,
manual reorder, new-groups policy; Room **v17** — `manualSortOrder` + `provider_category_settings`), and
**non-blocking staged DB imports** (Room **v18** — catalog/EPG imports stage chunked, then a fingerprint
delta-merge, so interactive writes don't block on a background refresh; implements the ADR-012 pipeline).

## Active App Architecture References

Only these three docs are active app-architecture references:

- `docs/ARCHITECTURE-REMEDIATION-COMPLETION-REPORT.md`
- `docs/SETTINGS-APP-HOISTED-DECISIONS.md`
- `docs/DETEKT-GATE.md`

Files under `docs/archive/` (the original audit, refactoring plan, file-split plan, settings-VM plan,
playback-orchestration plan) are **historical context only** — they must not override active docs or
the current code.

## Source Priority (conflicts resolved in this order)

1. `../vivicast-docs/prd/PRD-v1/` — product scope, behavior, data, security, acceptance criteria
2. `../vivicast-docs/architecture/decisions/` — architecture decisions (ADRs)
3. `../vivicast-docs/design/design-system/` — visual foundations
4. `../vivicast-docs/design/screens/` + `../vivicast-docs/design/wireframes/` — layout
5. `../vivicast-docs/design/interaction/` — focus, D-Pad, navigation behavior
6. `../vivicast-docs/design/components/` — reusable UI components
7. `../vivicast-docs/design/mockups/high-fidelity/02-ui-direction-decisions.md` — visual direction
8. `../vivicast-docs/design/mockups/high-fidelity/rendered/` — visual target (not a source for labels/nav/logic)
9. **Active app-architecture references (this repo)** — normative for *how* app code is structured (never override product/design above):
   - `docs/ARCHITECTURE-REMEDIATION-COMPLETION-REPORT.md`
   - `docs/SETTINGS-APP-HOISTED-DECISIONS.md`
   - `docs/DETEKT-GATE.md`
10. `plans/` (this repo) — implementation plans (concretize but never override above)

## Key Docs in vivicast-docs

| Path | Role |
|---|---|
| `prd/PRD-v1/*.md` | normative product requirements |
| `architecture/decisions/ADR-*.md` | normative architecture decisions |
| `design/screens/*.md` | normative UI structure |
| `design/wireframes/*.md` | normative layout |
| `design/interaction/*.md` | normative focus/D-Pad behavior |
| `design/components/*.md` | normative component specs |
| `DOCS-GOVERNANCE.md` | conflict resolution + doc roles |

## Before Starting Implementation Work

1. Check `plans/` for any existing plan for the affected area — read it first if found
2. Read relevant PRD, ADR, design, interaction, component files from `../vivicast-docs`
3. For architecture-sensitive work, read the active app-architecture references first:
   - `docs/ARCHITECTURE-REMEDIATION-COMPLETION-REPORT.md`
   - `docs/SETTINGS-APP-HOISTED-DECISIONS.md`
   - `docs/DETEKT-GATE.md`
4. Inspect existing app code before replacing anything
5. Reuse existing code when it doesn't conflict with `../vivicast-docs`
6. For larger changes: create a plan file under `plans/`

## When to Stop and Ask

Stop and ask the user when:
- Sources contradict each other
- A decision changes visible UI, navigation, labels, data model, persistence, backup/restore, PIN, security, or playback behavior
- A required requirement is missing from docs
- Multiple functionally different solutions are equally valid
- Implementation would require changing `../vivicast-docs`

Don't ask for decisions already clearly covered by active docs.

## Module Structure

```
app/          ← MainActivity + AppContainer wiring, AppDialogs, SettingsPreferenceMappers
                (App/Context-only mappers), PlaybackOrchestration (thin App host: image resolvers +
                open*/save delegation + clearHistory), Backup, Diagnostics, WatchNext, AndroidTV
                Search, in-app file picker (FilePickerDialog + StorageAccess), other app-hoisted
                effects (PIN, scheduler, locale/recreate)
core/
  cache/      ← M3uStreamReferenceStore, MediaCache
  common/     ← AppResult
  database/   ← Room DB (v18), DAOs (Provider, Catalog, CategorySettings, EPG, Favorites, Playback,
                Search); staged delta-merge import infra (ChunkedTransaction, SyncFingerprint, *_stage entities)
  datastore/  ← UserPreferencesStore
  designsystem/ ← VivicastTheme + grouped components: VivicastSurfaces / Layout / Badges / Panels /
                Dialogs / Inputs / Cards / Navigation / Player (no VivicastComponents.kt)
  network/    ← NetworkClientFactory
  player/     ← VivicastPlayerController (PlaybackRequest, VivicastPlayerState)
  security/   ← Keystore SecureValueStore, PinSecurity
data/
  epg/        ← EpgRepository, EpgImportRepository
  favorites/  ← FavoritesRepository
  media/      ← MediaRepository, CatalogImportRepository (staged delta-merge), CategoryGroupRepository
  playback/   ← PlaybackRepository, PlaybackStreamResolver, PlaybackProgressRules,
                PlaybackRequestFactory, PlaybackProgressRecorder
  provider/   ← ProviderRepository, ProviderConfigurationModels, TestProviderConnectionUseCase
domain/       ← Vivicast domain models; keep model-focused unless an approved plan requires otherwise
feature/      ← each feature = Route + ViewModel + UiState (+ ViewModelFactory)
  home/       ← HomeRoute, HomeViewModel, HomeUiState
  live-tv/    ← LiveTvRoute, LiveTvViewModel, LiveTvUiState
  movies/     ← MoviesRoute, MoviesViewModel, MoviesUiState
  player/     ← PlayerRoute (realtime player; reads controller state via collectAsState)
  search/     ← SearchRoute, SearchViewModel, SearchUiState
  series/     ← SeriesRoute, SeriesViewModel, SeriesUiState
  settings/   ← SettingsRoute, SettingsViewModel, SettingsUiState + panels (incl. ProviderActionsPanel,
                ProviderGroupsPanel)
iptv/
  m3u/        ← M3uParser + Contracts
  xmltv/      ← XmltvParser + Contracts
  xtream/     ← XtreamParser, XtreamClient, XtreamTransport (injectable ioDispatcher)
worker/       ← RefreshOrchestrator, RefreshWorker, RefreshScheduler (injectable ioDispatcher)
```

## Mandatory Architecture Rules

Follow these for all new/changed app code (they encode the completed remediation):

- New screens: **ViewModel + immutable UiState + `StateFlow`**.
- Routes read UI state via **`collectAsStateWithLifecycle`**.
- **No** Repository Flows collected directly in Composables.
- **No** Repository CRUD calls directly in Composables.
- Navigation lives outside ViewModels.
- Local UI state may stay in Composables: focus, D-Pad, dialog open/closed, input draft, local
  localized messages, scroll/focus requesters.
- ViewModels contain **no** Compose types, Context, Activity, Resources, localized strings, or navigation.
- Data/business logic belongs in `:data` or matching services/UseCases.
- `AppContainer` contains **no** business logic — only wiring/delegation.
- Time logic uses an injectable clock; dispatcher logic uses an injectable `CoroutineDispatcher`
  (default `Dispatchers.IO`).
- **App-hoisted stays App-hoisted:** ActivityResult (permission requests, external player),
  Keystore/PIN/Security, Context/PackageManager/Clipboard, WorkManager/Scheduler, Navigation,
  Locale/`recreate`, `playerController.play`, player state loop / WatchNext / throttle map, Backup,
  Diagnostics export, Global Refresh, Clear History, in-app file picker (`FilePickerDialog` /
  `StorageAccess` — the TV-safe SAF replacement; M3U + backup import + export folder selection).
- **Settings:** follow `docs/SETTINGS-APP-HOISTED-DECISIONS.md`.
- **Playback:** `PlaybackRequestFactory` / `PlaybackProgressRecorder` in `:data:playback`;
  `playerController.play` / `timeshiftConfig()` / WatchNext / `clearHistory` stay App-hoisted.
- **Provider connection test:** `TestProviderConnectionUseCase` in `:data:provider`; the German UI
  message mapping stays App-side.
- **Designsystem:** `VivicastComponents.kt` no longer exists — components live in `VivicastSurfaces /
  Layout / Badges / Panels / Dialogs / Inputs / Cards / Navigation / Player`.
- **Strings live ONLY in `:core:designsystem`** — `res/values/strings.xml` (German, default) +
  `res/values-en/strings.xml` (English). **Do NOT add a `strings.xml` (or any `<string>`) to `app/` or any
  `feature/*` module.** The application module's resources override library resources at merge time, so an
  app-module string silently shadows the designsystem value (this caused corrected labels to not render —
  see commit `97330fa`). Add every new/renamed user-facing string to both designsystem locale files.
- Run `.\gradlew.bat detekt` before structural changes; don't grow the baseline without justification.

## Android Development

- Use Android TV emulator via `scripts\start-tv-emulator.ps1` as primary test environment
  (`-Api 36` = Android 16 ceiling, default; `-Api 28` = Android 9 floor, mirrors the physical test
  device). Test structural / storage / permission changes on **both** floor and ceiling.
- No APK installs on physical device unless user explicitly requests it
- Run compile checkpoints after structural or behavior changes
- Use Android Studio Compose Preview for visual iteration

### Validation Commands

```powershell
# Check environment
.\scripts\check-environment.ps1

# Architecture / build / test gates (keep green for structural changes)
.\gradlew.bat detekt
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

For playback/protection/WatchNext changes, also run the relevant smoke tests when an emulator is
available: `M3uPlaybackSmokeTest`, `ProtectionGateTest`, `WatchNextIntegrationTest`.

## Git & Security

- Never push to GitHub without explicit user permission for that specific action
- Never create remote branches, PRs, or publish commits without explicit approval
- Never commit provider credentials, tokens, playlist URLs, or private screenshots
- Local commits to preserve validated state are fine; pushing still requires separate approval
