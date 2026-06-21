# 2026-06-21 - Local ID and Secret Storage Strategy

## Context

PRD v1 requires local Room storage, provider isolation, local search, provider deletion cleanup, and encrypted storage for credentials and secret URLs.

## Decision

- Room entity primary keys use stable local `String` IDs.
- Imported provider content keeps provider isolation by storing `providerId` on every provider-owned entity.
- Remote identity is represented by `providerId + remoteId` for imported catalog items.
- User-owned cross-cutting state uses `providerId + mediaType + mediaId` for favorites and playback progress.
- Room stores secret references only:
  - provider credentials use `credentialsKey`
  - protected EPG URLs use `urlKey`
- Room must not store raw M3U URLs, Xtream server URLs, Xtream usernames, Xtream passwords, or protected EPG URLs.

## Consequences

- Provider rename does not change IDs or break favorites/history/progress.
- Provider deletion can clean all provider-owned rows through `providerId`.
- Duplicate channel/movie/series names from different providers remain distinct.
- Later ingest code must generate deterministic local IDs or preserve previously assigned IDs during delta sync.
- Later backup code must explicitly decide whether encrypted secrets are exported and must never infer them from Room rows.

