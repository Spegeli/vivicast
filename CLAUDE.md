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
- **No** provider-specific header/cookie/User-Agent in PRD v1 — only global User-Agent under Allgemein

## Source Priority (conflicts resolved in this order)

1. `../vivicast-docs/prd/PRD-v1/` — product scope, behavior, data, security, acceptance criteria
2. `../vivicast-docs/architecture/decisions/` — architecture decisions (ADRs)
3. `../vivicast-docs/design/design-system/` — visual foundations
4. `../vivicast-docs/design/screens/` + `../vivicast-docs/design/wireframes/` — layout
5. `../vivicast-docs/design/interaction/` — focus, D-Pad, navigation behavior
6. `../vivicast-docs/design/components/` — reusable UI components
7. `../vivicast-docs/design/mockups/high-fidelity/02-ui-direction-decisions.md` — visual direction
8. `../vivicast-docs/design/mockups/high-fidelity/rendered/` — visual target (not a source for labels/nav/logic)
9. `plans/` (this repo) — implementation plans (concretize but never override above)

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
3. Inspect existing app code before replacing anything
4. Reuse existing code when it doesn't conflict with `../vivicast-docs`
5. For larger changes: create a plan file under `plans/`

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
app/          ← MainActivity, DI, Backup, Diagnostics, WatchNext, AndroidTV Search
core/
  cache/      ← M3uStreamReferenceStore, MediaCache
  common/     ← AppResult
  database/   ← Room DB, DAOs (Provider, Catalog, EPG, Favorites, Playback, Search)
  datastore/  ← UserPreferencesStore
  designsystem/ ← VivicastTheme, VivicastComponents
  network/    ← NetworkClientFactory
  player/     ← VivicastPlayerController
  security/   ← Keystore SecureValueStore, PinSecurity
data/
  epg/        ← EpgRepository, EpgImportRepository
  favorites/  ← FavoritesRepository
  media/      ← MediaRepository, CatalogImportRepository
  playback/   ← PlaybackRepository, PlaybackStreamResolver, PlaybackProgressRules
  provider/   ← ProviderRepository, ProviderConfigurationModels
domain/       ← VivicastModels, UseCase
feature/
  home/       ← HomeRoute
  live-tv/    ← LiveTvRoute
  movies/     ← MoviesRoute
  player/     ← PlayerRoute
  search/     ← SearchRoute
  series/     ← SeriesRoute
  settings/   ← SettingsRoute
iptv/
  m3u/        ← M3uParser + Contracts
  xmltv/      ← XmltvParser + Contracts
  xtream/     ← XtreamParser, XtreamClient, XtreamTransport
worker/       ← RefreshOrchestrator, RefreshWorker, RefreshScheduler
```

## Android Development

- Use Android TV emulator via `scripts\start-tv-emulator.ps1` as primary test environment
- No APK installs on physical device unless user explicitly requests it
- Run compile checkpoints after structural or behavior changes
- Use Android Studio Compose Preview for visual iteration

```powershell
# Check environment
.\scripts\check-environment.ps1

# Build debug
.\gradlew.bat assembleDebug
```

## Git & Security

- Never push to GitHub without explicit user permission for that specific action
- Never create remote branches, PRs, or publish commits without explicit approval
- Never commit provider credentials, tokens, playlist URLs, or private screenshots
- Local commits to preserve validated state are fine; pushing still requires separate approval
