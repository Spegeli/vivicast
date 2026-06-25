# Vivicast App Repository Instructions

## Repository Role

- `Spegeli/vivicast` is the Android TV app code repository.
- `Spegeli/vivicast-docs` is the reference documentation repository for product, architecture, design, interaction, test strategy, and Codex working rules.
- The docs repository is read-only during app implementation unless the Owner explicitly requests a documentation change.
- App code, app-specific technical plans, tests, implementation status, and local architecture decisions belong in this repository.

## Startup Routine

At the start of every new Codex session in this repository:

1. Read this `AGENTS.md`.
2. Read root `README.md`.
3. Read these files in `Spegeli/vivicast-docs`:
   - `codex/README.md`
   - `DOCS-GOVERNANCE.md`
   - `codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md`
   - `codex/coding-rules.md`
4. For the current task, re-check the affected PRD, ADR, design, interaction, component, and test-strategy files from `Spegeli/vivicast-docs`.
5. Inspect the affected app-repo modules and existing implementation before planning changes.
6. Before larger implementation work, create or update a technical working plan in this repository under `codex/plans/`.

Do not rely on memory, old chat context, or stale app-repo planning files when the active docs can be checked.

## Superseded Local Planning Files

The previous app-repo planning model has been superseded by `Spegeli/vivicast-docs`.

Do not use these paths as active sources of truth:

- `docs/PLAN.md`
- `docs/roadmap.md`
- `docs/phase-XX-*.md`
- `external-docs/`

If any of these paths still exist temporarily, treat them as legacy/pre-final remnants. They must not override `Spegeli/vivicast-docs`, `DOCS-GOVERNANCE.md`, or the implementation masterplan.

Do not recreate an `external-docs/` clone inside this repository. If a local read-only copy of `Spegeli/vivicast-docs` is needed for tooling, keep it outside the app repository or ensure it is ignored and never committed.

## Source Priority

Conflicts are resolved by `Spegeli/vivicast-docs/DOCS-GOVERNANCE.md`.

Short rule:

1. PRD files define product scope, behavior, settings, data requirements, and acceptance criteria.
2. ADRs define architecture decisions.
3. Design system, screen specs, wireframes, interaction specs, component specs, and UI direction decisions define UI, focus, navigation, and visual direction.
4. `prd/PRD-v1/13-test-strategy.md` defines tests, fixtures, performance budgets, and DoD evidence.
5. Codex files define working rules.
6. `codex/plans/IMPLEMENTATION-MASTERPLAN-v1.md` defines implementation order and package boundaries.
7. App-repo technical plans may concretize implementation details, but must not override the docs repository.

## Technical Working Plans

Codex must create and maintain technical implementation plans inside this app repository, for example:

```text
codex/plans/
  APP-IMPLEMENTATION-PLAN.md
  P00-preflight-plan.md
  P01-app-skeleton-plan.md
```

Each technical plan must include:

- docs sources read
- affected masterplan package
- concrete implementation scope
- non-scope
- affected app modules/files
- technical approach
- risks and assumptions
- relevant tests, measurements, or Android TV QA
- open Owner questions

Technical plans are implementation aids only. They do not replace PRD, ADRs, design sources, test strategy, Codex rules, or Governance.

## Plan Status Maintenance

The currently active technical plan file is the app-repo working memory for long-running implementation work.

Codex must keep the active technical plan file current:

- after every completed implementation substep
- after every meaningful validation step
- when a task becomes blocked
- when scope, risks, assumptions, or next steps change
- before context compaction, context handoff, or ending a session

The active technical plan must clearly document:

- completed work
- still-open work
- blocked items
- known risks and assumptions
- last validation command or manual check and its result
- next concrete step
- open Owner questions, if any

After a context reset, context compaction, or new session, Codex must not rely on chat memory. It must re-read `AGENTS.md`, `README.md`, the required `Spegeli/vivicast-docs` startup sources, and the active plan file under `codex/plans/` before continuing.

The active technical plan may summarize current implementation state and next steps, but it must not override PRD, ADRs, design sources, test strategy, Codex rules, or Governance.

## Autonomous Execution

Codex should work autonomously inside the active documentation boundaries.

Codex may independently:

- split masterplan packages into smaller technical tasks
- decide implementation details that do not change product behavior or architecture decisions
- add tests
- analyze and fix technical errors
- update app-repo technical plans
- continue with the next sensible task inside the active package after validation

Codex must stop and ask the Owner when:

- current active sources contradict each other
- an important requirement is missing
- multiple functionally different solutions are possible
- a decision would change visible UI, navigation, labels, settings defaults, data model, persistence, backup/restore, PIN, security, playback, or architecture
- a workaround could violate a product rule
- implementation appears to require changing the docs repository
- archived, legacy, or old app-repo information seems relevant but is not confirmed by current docs

Owner questions must be bundled and include the affected source, affected app component, problem, Codex recommendation, and concrete decision options.

## Package Lifecycle and Done Rules

For each implementation package:

1. Read relevant docs.
2. Create or update the app-repo technical plan.
3. Check blockers and Owner questions.
4. Implement only the defined scope.
5. Validate with build, tests, lint, and relevant functional checks as far as possible.
6. Compare the result against PRD, ADR, design, test strategy, Codex rules, and Governance.
7. Update the app-repo technical plan or package status.
8. Mark done only when all relevant Done criteria are fulfilled.

A package is not done if build, relevant tests, UI/focus checks, security rules, persistence rules, or documented acceptance criteria are still unresolved without a clear limitation note.

## Product Baseline

- Product name: `Vivicast`.
- Android package/application ID: `com.vivicast.tv`.
- Primary platform: Android TV.
- UI stack: Kotlin and Jetpack Compose for TV.
- Playback foundation: Media3 / ExoPlayer.
- Local persistence: Room and DataStore.
- Secrets: Android Keystore-backed secret storage, not plaintext Room storage.
- Main navigation: `Home | Live-TV | Filme | Serien | Suche | Einstellungen`.
- `Home` is a fixed main area and the default start area unless changed by supported settings.
- Visible UI text is German with umlauts where required. Allowed visible exception: `Home`.
- Required visible terms include `Kanäle` and `Über die App`.
- No server backend, account system, cloud sync, telemetry, external metadata provider integration, or automatic provider merging for v1.
- No provider-specific header, cookie, or User-Agent settings for v1. Only the global User-Agent under Allgemein may exist.

## Android Development Workflow

- Before Android implementation, migration, testing, profiling, build-tooling, Compose, navigation, or optimization work, check whether an installed Android skill matches the task.
- When a skill matches, read its complete `SKILL.md` before changing code and follow its workflow together with these project instructions.
- Use Android Studio Compose Preview for visual iteration when Compose UI exists.
- Use the Android TV emulator for D-Pad focus, key routing, Back behavior, dialogs, navigation, playback, persistence, database, and integration checks.
- Run compile checkpoints after structural or behavior changes.
- User interaction should only be requested for unavoidable OS permission, license, login, account, or other blocking dialogs.

## Android TV Emulator Rule

- Start the Android TV emulator through `scripts\start-tv-emulator.ps1` when this script is available.
- Use the Android TV emulator as the normal test environment.
- Do not install APKs on a physical Android TV unless the Owner explicitly asks for it.

## Git and GitHub

- Never push to GitHub without explicit Owner permission for that specific push.
- Never create remote repositories, publish branches, open pull requests, or upload commits unless the Owner clearly approved that exact GitHub action.
- Local commits are allowed when they preserve a validated work state; pushing those commits still requires separate explicit approval.

## Security

- Never place sensitive provider, playlist, network, or private user data into documentation, tests, demo data, screenshots, logs, or chat-visible summaries.
- If sensitive values appear in screenshots or chat, avoid repeating them in files.
- Diagnose, log, backup, restore, PIN, and provider credential work must re-check the relevant PRD, ADR, coding rules, and test strategy before implementation.
