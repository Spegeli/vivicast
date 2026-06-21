# Phase 03 - Provider Security and Configuration

## Goal

Implement local provider management and encrypted credential storage for M3U and Xtream providers, without importing real catalogs yet.

This phase prepares secure configuration. It does not parse playlists, call provider APIs for catalogs, or start playback streams.

## Affected Modules

- `:core:security`
- `:core:datastore`
- `:core:database`
- `:core:network`
- `:data:provider`
- `:feature:settings`
- `:app`

## Concrete Tasks

- Implement `SecureValueStore` using Android Keystore-backed encryption.
- Store secret values outside Room:
  - M3U URLs
  - Xtream server URLs
  - Xtream usernames
  - Xtream passwords
  - EPG URLs when treated as protected URLs
- Store only stable credential key references in Room.
- Add provider create/edit flows in Settings:
  - M3U provider name and URL
  - Xtream provider name, server, username, password
  - include Live-TV, Movies, Series flags
  - refresh interval
- Add provider status model:
  - active
  - refresh running
  - connection error
  - invalid credentials
  - expired
  - disabled
- Add duplicate-provider warning while still allowing creation.
- Implement provider rename without changing IDs or related data.
- Implement provider disable by changing status and keeping all data.
- Implement provider delete behavior with a safe confirmation dialog:
  - delete provider-owned catalog data
  - delete provider-owned favorites, playback progress, history, and EPG mappings
  - keep independent EPG sources
- Ensure destructive dialogs default focus to the safe action.
- Add tests for credential storage, provider lifecycle operations, and deletion side effects.

## Definition of Done

- Provider configuration can be created, edited, disabled, renamed, and deleted locally.
- Secret values are encrypted and not stored in Room, logs, screenshots, or docs.
- Provider deletion follows ADR-009.
- Provider isolation is preserved for every persisted object.
- Settings remains fully D-Pad navigable.
- The app builds with `.\gradlew.bat assembleDebug`.
- No real catalog import, parser implementation, provider API catalog call, or stream playback is included.

## Status

Complete as of 2026-06-21.

Implemented:

- Android Keystore-backed `SecureValueStore`.
- Room-backed provider repository with stable credential key references in Room.
- Local Settings provider management for M3U and Xtream configuration.
- Provider status, enable/disable, rename/update, duplicate-name warning, and safe delete flow.
- ADR-009 provider-owned deletion side effects for catalog, favorites, playback progress/history, and provider EPG mappings while keeping independent EPG sources.
- Instrumentation tests for provider lifecycle, credential storage boundaries, and deletion side effects.

Validated:

- `.\gradlew.bat :data:provider:connectedDebugAndroidTest`
- `.\gradlew.bat :core:security:testDebugUnitTest :core:database:testDebugUnitTest assembleDebug`
- `.\gradlew.bat :app:installDebug`
- Android TV emulator launch and D-Pad Settings/provider smoke test.

Not included:

- Playlist parsing.
- Xtream catalog client calls.
- XMLTV import.
- WorkManager refresh scheduling.
- Real stream playback.

## References

- `external-docs/prd/PRD-v1/05-iptv-epg-favorites.md`
- `external-docs/prd/PRD-v1/06-data-model.md`
- `external-docs/prd/PRD-v1/08-android-tv-security.md`
- `external-docs/architecture/decisions/ADR-001-provider-isolation.md`
- `external-docs/architecture/decisions/ADR-004-backup-strategy.md`
- `external-docs/architecture/decisions/ADR-009-provider-deletion-and-favorites.md`
- `external-docs/design/design-system/05-screen-patterns.md`
