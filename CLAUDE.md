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
- **Distribution:** open-source on GitHub (`github.com/Spegeli/vivicast`); **sideload / GitHub-release
  APK only — no Google Play**. So Play-restricted permissions (e.g. `MANAGE_EXTERNAL_STORAGE`) are
  acceptable. Release builds are signed via a gitignored `keystore.properties` (CI: env vars), **R8/minify off**.

## Current Architecture Status

The architecture invariants are the **Mandatory Architecture Rules** below; the Room schema is at **v21**.

## Active App Architecture References

Only these two docs are active app-architecture references:

- `docs/ARCHITECTURE-SETTINGS-HOISTING.md`
- `docs/ARCHITECTURE-DETEKT-GATE.md`

Files under `docs/archive/` are **historical context only** — they must not override active docs or the
current code.

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
   - `docs/ARCHITECTURE-SETTINGS-HOISTING.md`
   - `docs/ARCHITECTURE-DETEKT-GATE.md`
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

**Step 0 — load the matching skill(s) for the task first (see `## Skills` below), proactively, before touching code.** Then:

1. Check `plans/` for any existing plan for the affected area — read it first if found
2. Read relevant PRD, ADR, design, interaction, component files from `../vivicast-docs`
3. For architecture-sensitive work, read the active app-architecture references first:
   - `docs/ARCHITECTURE-SETTINGS-HOISTING.md`
   - `docs/ARCHITECTURE-DETEKT-GATE.md`
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

## Skills (use proactively — no prompting needed)

The available skills (Android / Compose / Kotlin, under `.claude/commands/`) are listed each session with a
"Use when…" description. **Invoke the matching skill via the Skill tool BEFORE writing or reviewing code in its
area — on your own, without being asked.** Treat it as a gate, not a suggestion: load the skill, follow it, then
implement. Load every skill a task touches; if unsure which, scan the skills' "Use when…" lines and pick. Skills
load per-turn — re-invoke in a later turn when the task shifts or is still relevant. Do not wait for the user to
name a skill.

Routing (task area → skill):

| Task area | Skill(s) |
|---|---|
| Navigation, routes, deep links | `android-navigation` (+ `android-navigation-type-safe` for typed routes/args) |
| TV D-pad focus, `FocusRequester`, `focusProperties`, key events, initial focus | `compose-focus-navigation` |
| Composable UI, recomposition, LazyColumn, layout/modifiers, reusable components | `android-compose` (+ `compose-recomposition-performance` / `compose-stability-diagnostics` / `compose-modifier-and-layout-style` / `compose-slot-api-pattern`) |
| Compose side effects (`LaunchedEffect`/`DisposableEffect`/`snapshotFlow`/focus requests) | `compose-side-effects` |
| Where state lives / hoisting / state-holder split | `compose-state-hoisting`, `compose-state-holder-ui-split`, `compose-state-authoring` |
| ViewModel + immutable UiState + `StateFlow`/`SharedFlow`/sealed state | `android-state` (+ `kotlin-flow-state-event-modeling`) |
| Coroutines, scopes, dispatcher injection | `android-concurrency`, `kotlin-coroutines-structured-concurrency` |
| Room / DataStore | `android-persistence` |
| Keystore / PIN / encrypted storage | `android-security` |
| WorkManager / RefreshWorker | `android-background-work` |
| OkHttp / networking | `android-networking` |
| Strings / localization / resources | `android-resources` |
| detekt / ktlint / lint gates | `android-tooling` |
| Startup / jank / perf; trace analysis | `android-performance` (+ `perfetto-trace-analysis` / `perfetto-sql`) |
| Writing / reviewing tests | `android-testing` (+ `compose-ui-testing-patterns` for Compose / focus tests) |
| Architecture, module placement, UDF | `android-architecture` |
| M3 theming / design tokens | `android-design-system` |
| AGP 9 / Gradle DSL | `android-agp-upgrade` |
| Kotlin control flow / value classes | `kotlin-control-flow`, `kotlin-types-value-class` |
| Emulator / adb / AVD / screenshots / UI inspection | `android-cli` |
| Compose Styles API | `styles` |
| Compose animations | `compose-animations` |

**Standing note:** the upcoming Jetpack Navigation Compose rebuild always loads `android-navigation` +
`android-navigation-type-safe`; anything TV-focus-related always loads `compose-focus-navigation`.

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
  database/   ← Room DB (v21), DAOs (Provider, Catalog, CategorySettings, EPG, Favorites, Playback,
                Search); staged delta-merge import infra (ChunkedTransaction, SyncFingerprint, *_stage entities)
  datastore/  ← UserPreferencesStore
  designsystem/ ← VivicastTheme + grouped components: VivicastSurfaces / Layout / Badges / Panels /
                Dialogs / Inputs / Cards / Navigation / Player (no VivicastComponents.kt)
  logging/    ← vcLog() debug tracing — one tag `VCd`, BuildConfig.DEBUG-gated (:core:logging)
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

Follow these for all new/changed app code:

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
  Locale/`recreate`, WatchNext, the ExoPlayer connection singleton (`AppContainer.playerController`)
  + the preview SurfaceView, Backup, Diagnostics export, Global Refresh, Clear History, in-app file
  picker (`FilePickerDialog` / `StorageAccess` — the TV-safe SAF replacement; M3U + backup import +
  export folder selection). Player **orchestration** is the one carve-out — it moved to an
  activity-scoped `PlayerViewModel` (see Playback below).
- **Settings:** follow `docs/ARCHITECTURE-SETTINGS-HOISTING.md`.
- **Playback:** `PlaybackRequestFactory` / `PlaybackProgressRecorder` in `:data:playback`. Player
  **orchestration** (build+`play`, the auto-save + auto-next loops, zap, the committed-preview identity)
  lives in the activity-scoped `PlayerViewModel` (`:app`); the ExoPlayer **connection**
  (`AppContainer.playerController`) stays a singleton, and navigation, the PIN gate, the external-player
  ActivityResult, the preview SurfaceView, WatchNext + `clearHistory` stay App-side. See
  `plans/player-viewmodel-extraction.md`.
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
  (`-Api 36` = Android 16 ceiling, default; `-Api 26` = Android 8 floor = `minSdk` (26–27 supported but
  untested on hardware); `-Api 28` = Android 9, mirrors the physical test device). Test structural /
  storage / permission changes on **both** floor and ceiling.
- **ALWAYS run `adb logcat` during EVERY on-device test — emulator OR physical TV — and read it as part of
  the evaluation. Every single time, no exceptions: visual test or not, big change or a trivial one. Never
  debug focus / key-event / runtime behaviour from screenshots alone.** Start logcat before the interaction
  (e.g. `adb -s <device> logcat -c` then `adb -s <device> logcat` filtered to the app / relevant tags), and
  actually inspect the trace before drawing conclusions. When a focus/event/navigation path is unclear, add a
  `vcLog("<area>") { … }` trace at the handler + state callback and confirm via `adb logcat -s VCd` what fired
  — do not guess. **Prefer `vcLog` (from `:core:logging`) over raw `Log.d`**: one shared `VCd` tag, already
  wired into `app` + every `feature/*` module, and `BuildConfig.DEBUG`-gated so it never reaches release
  logcat (R8/minify is off, so a raw `Log.d` would). Leave useful traces in (debug-only); don't strip them.
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

# Signed release build (uses gitignored keystore.properties; R8/minify off) -> app-release.apk (v1+v2)
.\gradlew.bat assembleRelease
```

For playback/protection/WatchNext changes, also run the relevant smoke tests when an emulator is
available: `M3uPlaybackSmokeTest`, `ProtectionGateTest`, `WatchNextIntegrationTest`.

## Git & Security

- Never push to GitHub without explicit user permission for that specific action
- Never create remote branches, PRs, or publish commits without explicit approval
- Never commit provider credentials, tokens, playlist URLs, or private screenshots
- Local commits to preserve validated state are fine; pushing still requires separate approval
