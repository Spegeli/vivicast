# Phase 01 - Docs and Foundation Alignment

## Goal

Create a local planning baseline that matches the PRD, ADRs, and design sources while preserving the existing Android TV foundation and Phase 2C UI demo work.

This phase does not add app features.

## Affected Modules and Files

- `.gitignore`
- `external-docs/`
- `docs/roadmap.md`
- `docs/phase-*.md`
- `docs/PLAN.md`
- `settings.gradle.kts`
- Existing Gradle files and module entry points, read-only unless a mismatch is discovered

## Concrete Tasks

- Clone `Spegeli/vivicast-docs` into `external-docs/`.
- Add `external-docs/` to `.gitignore`.
- Treat `external-docs/` as read-only context and never edit it from the app repo.
- Read only these source areas:
  - `prd/PRD-v1/`
  - `architecture/decisions/`
  - `architecture/diagrams/`
  - `design/`
- Ignore the complete `codex/` folder and all links pointing into it.
- Audit the current local project state:
  - module list
  - package and application ID
  - Gradle setup
  - DI status
  - Room status
  - DataStore status
  - Keystore/security status
  - parser/provider/player/worker status
  - existing demo UI and demo data status
- Create the roadmap index and one phase file per implementation phase.
- Update `docs/PLAN.md` so it points to `docs/roadmap.md` as the active long-form project plan.
- Do not update `AGENTS.md` in this phase unless the user approves a shown diff first.

## Definition of Done

- `external-docs/` exists locally and is ignored by Git.
- Roadmap and phase files exist under `docs/`.
- The roadmap reflects the existing modules and does not replan completed foundation or UI demo work from zero.
- Each phase file includes goal, affected modules, concrete tasks, Definition of Done, and source references.
- No files under `external-docs/` are modified.
- No `codex/` source is used as binding input.
- No commits or pushes are made without explicit approval.

## References

- `external-docs/prd/PRD-v1/01-product-overview.md`
- `external-docs/prd/PRD-v1/09-implementation-and-dod.md`
- `external-docs/architecture/decisions/ADR-001-provider-isolation.md`
- `external-docs/architecture/decisions/ADR-008-android-tv-integration.md`
- `external-docs/design/design-system/README.md`
- `external-docs/design/mockups/high-fidelity/02-ui-direction-decisions.md`

