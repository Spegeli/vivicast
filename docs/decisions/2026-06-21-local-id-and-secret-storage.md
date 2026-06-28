# 2026-06-21 - Local ID and Secret Storage Strategy

Status: superseded for implementation by `../vivicast-docs/prd/PRD-v1/06-data-model.md` and `../vivicast-docs/architecture/decisions/ADR-010-stable-identities-and-restore-keys.md`.

## Context

PRD v1 requires local Room storage, provider isolation, local search, provider deletion cleanup, and encrypted storage for credentials and secret URLs.

## Superseding Rule

This local decision predates the final docs repo alignment and must not be used as an active source of truth when it differs from the final docs.

Implementation must follow:

- Local Room `id` values are only local database IDs.
- Provider identity must use `ProviderEntity.stableKey` as `providerStableKey` for backup, restore, favorites, history, and playback progress references.
- Imported provider content must carry final `stableKey` values.
- User-owned cross-cutting state must use stable references such as `providerStableKey + mediaType + mediaStableKey` and must support pending restore references.
- `providerId + remoteId` and `providerId + mediaType + mediaId` are not sufficient backup/restore identities.
- Secret or private values must be referenced through protected secret storage, not persisted as raw Room values or plaintext app-private files.

## Consequences

- Existing code using only local IDs or `remoteId` needs migration before backup/restore, favorites, history, or playback progress can be considered final-doc compliant.
- Current plaintext stream reference storage must be rechecked against final secret-store rules before production use.
