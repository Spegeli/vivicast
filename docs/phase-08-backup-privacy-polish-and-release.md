# Phase 08 - Backup, Privacy, Polish, and Release

## Goal

Finish v1 readiness with manual backup/restore, privacy hardening, diagnostics, cache maintenance, performance validation, accessibility checks, release documentation, and final acceptance.

## Affected Modules

- `:app`
- `:core:common`
- `:core:database`
- `:core:datastore`
- `:core:network`
- `:core:player`
- `:core:security`
- `:data:provider`
- `:data:epg`
- `:data:media`
- `:data:favorites`
- `:data:playback`
- `:feature:settings`
- `:worker`
- `docs/`

## Concrete Tasks

- Implement manual backup and restore:
  - local storage target
  - SMB target if supported by the chosen implementation
  - Google Drive target if supported by the chosen implementation
  - full export
  - partial export/import for PRD-defined scopes
  - optional encrypted credential export with backup password
  - abort restore on wrong password
  - migration behavior for unknown and missing fields
- Ensure full restore clearly communicates that current configuration will be replaced.
- Implement backup rotation settings.
- Implement diagnostics mode:
  - disabled by default
  - refresh events
  - errors
  - warnings
  - player errors
  - network errors
  - export with credential/token/secret URL redaction
- Implement maintenance actions:
  - database optimize
  - logo cache rebuild
  - poster cache rebuild
  - cache clear
  - EPG re-download
  - full playlist re-read
- Implement cache size reporting and configurable cache limits.
- Validate performance against PRD scale targets using generated non-private test data:
  - 10,000+ channels
  - 50,000+ movies
  - 20,000+ series
  - large EPG dataset
- Validate lazy rendering for lists and grids.
- Validate accessibility and TV readability:
  - large font setting
  - animations off
  - D-Pad only navigation
  - Back behavior
  - focus restore
  - missing logo/poster/EPG/metadata fallbacks
- Complete release documentation:
  - setup notes
  - test matrix
  - privacy/security notes
  - known limitations
  - release checklist
- Run final acceptance against PRD chapter 11 and DoD chapter 13.

## Definition of Done

- Manual backup and restore work according to ADR-004.
- No automatic backups are implemented for v1.
- Diagnostics exports redact passwords, tokens, usernames, and secret URLs.
- Cache and maintenance functions are available from Settings.
- Performance checks pass at PRD target scale or documented blockers exist.
- Android TV D-Pad, voice search, global search, Watch Next, playback, refresh, backup, restore, favorites, history, and progress pass final acceptance.
- The app builds with `.\gradlew.bat assembleDebug`.
- Emulator smoke test passes on `ViviCast_AndroidTV_API36`.
- Release candidate docs are current.

## References

- `external-docs/prd/PRD-v1/05-iptv-epg-favorites.md`
- `external-docs/prd/PRD-v1/06-data-model.md`
- `external-docs/prd/PRD-v1/07-background-jobs-performance.md`
- `external-docs/prd/PRD-v1/08-android-tv-security.md`
- `external-docs/prd/PRD-v1/09-implementation-and-dod.md`
- `external-docs/architecture/decisions/ADR-004-backup-strategy.md`
- `external-docs/architecture/decisions/ADR-008-android-tv-integration.md`
- `external-docs/design/design-system/04-focus-navigation.md`
- `external-docs/design/design-system/05-screen-patterns.md`

