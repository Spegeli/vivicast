# Xtream Codes → Auto-EPG-Source Detection (Research + Plan)

> Status: **COMPLETED — implemented + verified (user manual test + emulator launch sanity), static gates green, committed.**
> Diagnostics: `AppContainer.autoDetectXtreamEpg` emits opaque `"epg"` events — `xtream_source_linked`
> (target=providerId, source=sourceId, mode=created/reused, channels/programs) and `xtream_source_none`
> (no reachable guide); no URL/username (privacy guarantee holds). No existing handler needed changing —
> provider-save (`provider/saved`) and the worker EPG refresh (`refresh/EpgRefreshSucceeded`, counts-only,
> opaque sourceId) already cover the rest and leak nothing.
> Built: `xtreamXmltvUrl` helper (`:iptv:xtream`) + test; `getEpgSources()` on `EpgRepository`/
> `RoomEpgRepository`; `AutoXtreamEpgSourceUseCase` (`:data:epg`, dedup by server+username, reuse/create
> `#N`, `max+1` priority) + test; `AppContainer.autoDetectXtreamEpg()` + `Mutex` (full validation via the
> existing `testEpgSourceConnection`); `onXtreamProviderSaved` callback threaded MainActivity →
> SettingsRoute → ProviderSettingsPanel, fired on `type == Xtream && !isSourceUnchanged`. Four EPG-repo
> test fakes got the new `getEpgSources()` stub. Detekt baseline re-keyed (5 entries — SettingsRoute /
> ProviderSettingsPanel signatures grew one param; no new/loosened rules). Green: `assembleDebug`,
> `testDebugUnitTest` (incl. both new tests), `detekt`, `:feature:settings`/`:feature:live-tv`
> `compileDebugAndroidTestKotlin`. NOT yet driven on an emulator (§11 smoke test).
> Goal: when a playlist is created via Xtream Codes (or an existing playlist is edited from another
> type to Xtream), automatically validate the provider's `xmltv.php` endpoint and, if it serves EPG
> data, create + link an EPG source for it — without duplicates on re-add.
> Research verified against HEAD `e74f620`. Reference apps in `../IPTV-APPS` (OwnTV, AerioTV,
> StreamVault) consulted for the idiom only; nothing copied.
> **Revision 2** folds in an adversarial scenario pass (§12) + maximal reuse of the existing
> `TestEpgSourceConnectionUseCase` (§6). Two must-fixes (S6, S13) are now baked into the spec.
> **Revision 3** resolves the two surfaced decisions (§13): the trigger is **extended to any Xtream
> credential change** (S5 = extend), and **full XMLTV validation is kept** (S9 = full). One minor
> consequence (S5b, server-change leaves a stale link) is accepted under the manual-cleanup stance.

---

## 1. Goal & scope

Most Xtream Codes providers expose a companion XMLTV guide at
`https://SERVER/xmltv.php?username=USER&password=PASS`. Today the user must add that EPG source by
hand. This feature detects it automatically at provider-save time.

**Trigger (confirmed, Rev3):** run whenever a save results in an Xtream provider **and the Xtream
source actually changed** —
1. Creating a **new** Xtream (`ProviderType.Xtream`) playlist,
2. Editing an existing playlist and **switching its type to Xtream**, and
3. Editing an **already-Xtream** playlist whose **credentials changed** (server/username/password —
   e.g. a password rotation; closes the S5 gap).

The exact condition is `saved.provider.type == ProviderType.Xtream && !editor.isSourceUnchanged`.
`isSourceUnchanged` (`ProviderEditorState.kt:92-94`) compares the Xtream `sourceSignature()` =
`"XT|server|username|password"` (`:87-90`) against the loaded pristine source, so a pure metadata edit
(rename, include-flag toggle) with unchanged credentials does **not** re-run detection, while any
credential change does. All three cases run through `ProviderSettingsPanel.persistEditor()`, so one hook
covers them.

**Non-goals:** no auto-delete of the EPG source when the playlist is deleted or switched away from
Xtream (the user removes it manually — matches existing delete semantics); no new UI screen; no DB
schema change; no new Gradle module; no DI framework.

---

## 2. User decisions (record)

| # | Decision | Choice |
|---|----------|--------|
| Trigger | When to run | Any save resulting in Xtream **where the source changed**: new, type-switch, or credential edit (`type == Xtream && !isSourceUnchanged`) — Rev3 |
| Reachability | Blocking vs background | **Non-blocking**: save completes immediately; detection runs after, silent on failure |
| Split | Inline vs worker | **Detect + create + link inline** (App-hoisted, DB-only, fast); the actual EPG **download/import stays in the existing `EpgRefreshWorker`** |
| Dedup key | Recognise "already exists" | **Server + username** (NOT the EPG-source display name — the user may rename the source later) |
| Naming | New source display name | `username`; if taken by any source, `username #1`, `#2`, … |
| Reuse | On server+username match | Reuse the existing source: **update its URL** (password may have rotated), **keep its current name / isActive / timeShift**, link to the new provider |
| URL-builder home (was §10.1) | Where the `xmltv.php` builder lives | **`:iptv:xtream`** (next to the existing `player_api.php` builder), called App-side |
| Dedup strictness (was §10.2) | Match precision | **host + port + full path + username** (see S11/S12) |
| Probe User-Agent (was §10.3) | UA for the check | Global UA (inherited from the shared `OkHttpClient` fallback) |

---

## 3. Verified current state (file:line)

### 3.1 Provider / Xtream

- Two provider types only: `ProviderType.M3u` / `ProviderType.Xtream` — `domain/.../VivicastModels.kt:32`.
  "Xtream Codes" in the UI **is** `ProviderType.Xtream`.
- Credentials are **not** in Room; they live in `SecureValueStore` under
  `provider:<id>:credentials:<field>` with fields `xtream_server_url` / `xtream_username` /
  `xtream_password` — `RoomProviderRepository.kt:507-509`, key format `:491-492` / `:500-501`.
- Read credentials: `ProviderRepository.getCredentials(providerId)` →
  `ProviderCredentials.Xtream(serverUrl, username, password)` — `RoomProviderRepository.kt:37-63`.
- Create/update: `createProvider :68-112` (id = `UUID.randomUUID()` `:70`, returns `ProviderSaveResult`
  whose `provider.id` is the new id), `updateProvider :114-181` (`switchedFromType` set on type change;
  on a source switch it `clearProviderCatalog` `:169`, which deletes catalog + `epg_programs` +
  `epg_channel_mappings` but **NOT** `provider_epg_sources` links — verified `:201-204`). VM wrappers:
  `SettingsViewModel.createProvider :262`, `updateProvider :265`, `getProviderCredentials :256`.
- Duplicate detection today = **name only** (`hasDuplicateName :277-280`); no server+user+pass check.
- **Xtream base-URL normalisation** in `XtreamContracts.kt`: `apiUrl() :106-109` =
  `serverUrl.trim().trimEnd('/') + "/player_api.php"` (private, OkHttp `HttpUrl`); `requestUrl()`/
  `userInfoUrl()` add `username`/`password` via `HttpUrl.addQueryParameter` (auto-encoded, `.trim()`'d).
  **No `xmltv.php` builder exists** (grep = 0 hits).

### 3.2 Save choreography (the hook seam)

`ProviderSettingsPanel.persistEditor() :359-405`:
- `:362-366` calls `onUpdateProvider(...)` / `onCreateProvider(...)` (VM → `Result<ProviderSaveResult>`).
- `.onSuccess { saved -> … } :368` has `saved.provider` (id, `type` `:369-372`, `isActive`) and
  `saved.switchedFromType`. Already fires App-hoisted `onRefreshProvider(saved.provider.id) :380`
  (REPLACE playlist refresh). **New callback fires here** when `saved.provider.type == Xtream`.
- Credentials are committed before `onSuccess` (create: `writeCredentialsForCreate` before the tx;
  update-switch: `writeCredentialsForUpdate` before delete/tx) ⇒ `getCredentials(id)` is safe in the
  callback.

### 3.3 EPG source model & repo

- `EpgSourceEntity` table `epg_sources` — `VivicastEntities.kt:231-252`. **No URL column**; URL in
  `SecureValueStore` under `epg-source:<sourceId>:url`. Unique indices: `stableKey`, `sourceConfigKey`;
  `name` non-unique. Fields incl. `isActive`, `timeShiftMinutes`.
- Repo `EpgSourceRepository` — `SecureEpgSourceRepository.kt:15-26`:
  - `saveSource(EpgSourceEditRequest(sourceId?, name, url?, timeShiftMinutes=0, isActive=true))` — impl
    `:41-61` writes the url to the secure store for a new id or any non-blank url `:47-50`, then
    delegates. **NOTE the defaults `timeShiftMinutes=0`, `isActive=true`** — the reuse path must pass the
    existing values or it clobbers them (S6).
  - `getSourceUrl(sourceId): String?` `:63-66` — reads the secure url. **This is the dedup read.**
  - `linkSourceToProvider(providerId, epgSourceId, priority)` `:79-81` →
    `RoomEpgRepository.linkEpgSourceToProvider :163-178` (idempotent upsert on `(providerId,
    epgSourceId)`, requires `priority > 0`).
  - `deleteSource :68-77`, `unlinkSourceFromProvider :83-95` (the latter renormalises priorities to a
    contiguous `1..n`).
- **`RoomEpgRepository.saveEpgSource :129-161` THROWS on a case-insensitive name clash** with a
  different id (`:140-142` `"EPG source name must be unique."`) — never reuses by name. ⇒ create must
  pre-resolve a free display name (S3/S4); re-add must route to *reuse* (S1), never a second `saveSource`
  under the same name.
- Listing for the dedup scan: `observeEpgSources(): Flow<List<EpgSource>>` (`EpgRepository.kt:11`); the
  DAO already has a suspend `getEpgSources()` (used by the uniqueness backstop). Add a thin suspend
  passthrough on the repo (§7) rather than collecting a Flow.

### 3.4 EPG link, refresh, delete, validate

- Link table `ProviderEpgSourceEntity` `provider_epg_sources` — `VivicastEntities.kt:282-288`
  (`providerId`, `epgSourceId`, `priority`; unique `(providerId,priority)` & `(providerId,epgSourceId)`).
  **Zero Room foreign keys in the whole schema** ⇒ no cascades; cleanup is hand-rolled.
- **Import runs per active *linked* provider** — `DefaultEpgRefresher.refresh` `RefreshExecution.kt:408-488`;
  `RoomEpgSourceReader.getActiveSource :394-405` and `getActiveSourceIdsForProvider :390-392` both
  **require `isActive`**. ⇒ an unlinked *or inactive* source imports nothing (S6/S13).
- Immediate import trigger already App-hoisted: `enqueueEpgRefresh(sourceId)` `MainActivity.kt:1270-1274`
  → one-time `EpgRefreshWorker`, `ExistingWorkPolicy.KEEP` (`RefreshWorkScheduler.kt:85-91`).
- **Post-playlist-change re-enqueue**: after a successful playlist import, if the pref is on,
  `RefreshExecution.kt:79-81` re-enqueues the provider's assigned EPG source ids (empty when none) —
  a second, pref-gated path that also picks up our new link (S15).
- **Playlist delete keeps the EPG source**: `deleteProvider :256-275` deletes the provider's catalog,
  settings, `epg_programs`, `epg_channel_mappings`, and `provider_epg_sources` links `:263` — never the
  `epg_sources` row or its secure url. Desired.
- **Existing reachability+validity check (reuse this):** `TestEpgSourceConnectionUseCase.test(url):
  EpgContentSummary` — `data/epg/.../TestEpgSourceConnectionUseCase.kt:31-39`. Streams the body (SAX,
  constant memory — **never buffers the whole file**), gzip transparent (`XmltvStreamParser.maybeGunzip`),
  counts channels, and **throws `EpgConnectionResponseException` if 0 channels**. Wired App-side as
  `AppContainer.testEpgSourceConnection(url): EpgConnectionTestResult(errorMessage?, summary?)`
  `:466-472` (runs on `Dispatchers.IO`, folds throwables to a German message via `toEpgConnectionMessage`).
  Already used by the EPG editor's test button (`MainActivity.kt:988`). Underlying `OkHttpEpgStreamSource`
  uses the shared `okHttpClient` (connect/read/write timeouts, debug-TLS, global-UA fallback — no
  `callTimeout`, so a huge feed isn't killed mid-transfer). **This replaces any hand-rolled probe.**
- **Room stays at v18** (`VivicastDatabase.kt:86`). No migration; gzip already handled.

### 3.5 Endpoint check (verified live)

`https://x-api.cc/xmltv.php?username=…&password=…` → `HTTP 200`, `content-type: application/x-gzip`,
gunzips to valid `<tv>` XMLTV with real German channels. Reachable Xtream servers answer `xmltv.php`
with a (usually gzipped) XMLTV body → validation returns `channels > 0`.

---

## 4. Proposed flow

After a successful provider save where `saved.provider.type == ProviderType.Xtream`:

```
ProviderSettingsPanel.persistEditor():
    val sourceChanged = !editor.isSourceUnchanged        // capture BEFORE onSuccess resets the editor
    ...onSuccess:
        onRefreshProvider(saved.provider.id)             // existing
        if (saved.provider.type == Xtream && sourceChanged)
            onXtreamProviderSaved(saved.provider.id)     // NEW App-hoisted, fire-and-forget

MainActivity.onXtreamProviderSaved(providerId):
    lifecycleScope.launch {                              // non-blocking; save UI already closed
        appContainer.autoDetectXtreamEpg(providerId)?.let { enqueueEpgRefresh(it) }
    }

AppContainer.autoDetectXtreamEpg(providerId): String? = xtreamEpgMutex.withLock {   // S7
    val cred = providerRepository.getCredentials(providerId) as? Xtream ?: return null
    val xmltvUrl = xtreamXmltvUrl(cred.serverUrl, cred.username, cred.password)      // :iptv:xtream
    if (testEpgSourceConnection(xmltvUrl).summary == null) return null               // reuse existing check
    autoXtreamEpgSourceUseCase.ensureFor(providerId, cred.serverUrl, cred.username, xmltvUrl)
}

AutoXtreamEpgSourceUseCase.ensureFor(providerId, serverUrl, username, xmltvUrl): String? =
    val existing = repo.getEpgSources()
    val match = existing.firstOrNull { sameServerAndUser(repo.getSourceUrl(it.id), serverUrl, username) }
    val sourceId =
        if (match != null)
            repo.saveSource(EpgSourceEditRequest(
                sourceId = match.id, name = match.name, url = xmltvUrl,
                isActive = match.isActive, timeShiftMinutes = match.timeShiftMinutes))   // S6: preserve
                .id
        else
            repo.saveSource(EpgSourceEditRequest(
                name = uniqueDisplayName(username, existing.map { it.name }), url = xmltvUrl))
                .id
    repo.linkSourceToProvider(providerId, sourceId, nextPriority(providerId))            // S13: max+1
    return sourceId
```

`nextPriority(providerId)` = `(existing links' max priority) + 1`, or `1` when none — **max+1, not
count+1**, so a non-contiguous priority set can't collide with the unique `(providerId, priority)` index
(S13). `linkEpgSourceToProvider` requires `priority > 0`.

Validation (`testEpgSourceConnection`) is the reachability gate: reachable + valid XMLTV with ≥1 channel
⇒ proceed; anything else (unreachable, HTTP error, HTML page, 0 channels) ⇒ `summary == null` ⇒ silent
no-op. No user message (non-blocking/silent).

---

## 5. Dedup & naming — exact spec

Two independent concepts — do not conflate:

**(a) Identity = server + username** (reuse vs create). Password is **not** part of identity.
- Candidate: `cred.serverUrl` + `cred.username`.
- Existing: for each source, `getSourceUrl(id)`, parse with `java.net.URI`; `sameServerAndUser` = host
  equal (case-insensitive) **and** port equal **and** normalised path equal **and** the decoded
  `username` query param equals `cred.username`. Normalise both sides through the **same** parse/normalise
  path (build the candidate host/port/path the same way the stored url was built) so `trimEnd('/')`,
  default-port, or scheme (http/https ignored) differences don't split one identity. Sources that aren't
  Xtream `xmltv.php` urls (no matching path / no `username` query) never match (S4).

**(b) Display name = username, collision-suffixed** (only when *creating*, because `saveEpgSource`
throws on a name clash): `username`, else `username #1`, `#2`, … first free vs existing names
(case-insensitive, matching the repo rule). Covers a manual source literally named like the username
(S4) and a second account with a colliding username on a different server (S3).

**Reuse keeps the existing name, `isActive`, and `timeShiftMinutes`** (the user may have customised
them) and only rewrites the url (fresh password) + adds the link (S1, S6).

---

## 6. Reachability / validity — reuse, don't build

**Use the existing `AppContainer.testEpgSourceConnection(xmltvUrl)`** (§3.4). It already:
streams (constant memory), decompresses gzip, applies the shared client's timeouts + debug-TLS +
global UA, validates that the body is real XMLTV with ≥1 channel, and returns
`EpgConnectionTestResult(errorMessage?, summary?)`. Success = `summary != null`.

No new probe class, no new OkHttp wiring, no gzip/timeout/UA code. This is the single biggest reuse in
the plan.

Cost note (S9): a passing check streams the **whole** XMLTV once to count channels, and the worker then
downloads it again for the import — the create path fetches the guide twice. It's background and
non-blocking, but for a large feed on a TV that is real bandwidth. Ponytail-mark the call site with the
upgrade path: since the Xtream account was already authenticated by the pre-save connection test, this
could be downgraded to a status-only `StreamReachabilityProbe` (`AppContainer.kt:305-322`, Range `0-1`,
header-only) if the double-fetch ever hurts — at the cost of not catching a valid-account/xmltv-disabled
panel. See the open decision in §13.

---

## 7. Files & functions to touch

| Module / file | Change |
|---|---|
| `iptv/xtream/.../XtreamContracts.kt` (or a small new file in `:iptv:xtream`) | **New** pure helper `fun xtreamXmltvUrl(serverUrl, username, password): String` mirroring `apiUrl()` normalisation (`trimEnd('/') + "/xmltv.php"` + `username`/`password` via `HttpUrl.addQueryParameter`). Unit-tested (incl. path-prefix + special chars, S11/S12). |
| `data/epg/.../EpgRepository.kt` + `RoomEpgRepository.kt` + `SecureEpgSourceRepository.kt` | **New** `suspend fun getEpgSources(): List<EpgSource>` (thin passthrough to the existing DAO getter) for the dedup + name scan. |
| `data/epg/.../AutoXtreamEpgSourceUseCase.kt` | **New** use case: `ensureFor(providerId, serverUrl, username, xmltvUrl): String?` — dedup by server+username (parse existing urls via `java.net.URI`), reuse-or-create with the name/isActive/timeShift rules (§5), link at `max+1` priority. Depends only on `EpgSourceRepository`. Pure of OkHttp/Android → unit-testable with a fake repo. |
| `app/.../di/AppContainer.kt` | **New** `suspend fun autoDetectXtreamEpg(providerId): String?` — resolve credentials, build url via `xtreamXmltvUrl`, gate on `testEpgSourceConnection(...).summary != null`, delegate to the use case; guard the whole thing with a private `Mutex` (S7). Lazy-wire the use case. |
| `app/.../MainActivity.kt` | **New** callback `onXtreamProviderSaved = { id -> lifecycleScope.launch { autoDetectXtreamEpg(id)?.let { enqueueEpgRefresh(it) } } }`. |
| `feature/settings/.../SettingsRoute.kt` | Thread new param `onXtreamProviderSaved: (String) -> Unit`. |
| `feature/settings/.../ProviderSettingsPanel.kt` | New param; fire it in `persistEditor().onSuccess` when `saved.provider.type == ProviderType.Xtream`. |
| tests | `:iptv:xtream` — `xtreamXmltvUrl` (normalisation + encoding + path-prefix). `:data:epg` — `AutoXtreamEpgSourceUseCase`: create+link; server+username reuse incl. rotated-password url-update + name/isActive/timeShift preserved (S1/S6); name-collision suffix `#1` (S3/S4); no-match-different-server (S3); encoded-username match (S12). |

**No strings** (silent path) ⇒ nothing in `:core:designsystem` string resources; no `strings.xml` in
`app/`/`feature/*` (module-resource shadowing rule).

---

## 8. Architecture fit (CLAUDE.md guardrails)

- **No repo CRUD / no repo Flows in Composables** — the panel only fires a `(String) -> Unit`
  App-hoisted callback; all repo work is App-layer + `:data:epg` use case.
- **Network stays App-hoisted / out of the ViewModel** — validation via the existing
  `testEpgSourceConnection` (`Dispatchers.IO`), same category as `testProviderConnection`.
- **Scheduler stays App-hoisted** — import via the existing `enqueueEpgRefresh` worker path.
- **No new module, no DI framework, no schema/version change.** New use case is a plain class in
  `:data:epg`, following the `TestProviderConnectionUseCase` / `TestEpgSourceConnectionUseCase` precedent.

---

## 9. Checks to keep in mind (quick list)

- Link is mandatory and the source must be **active** — an unlinked or inactive source imports nothing.
- `saveEpgSource` throws on a name clash — suffix first; never create under a taken name.
- Reuse: update url, **preserve name / isActive / timeShift** (S6).
- `nextPriority = max+1` (S13). Idempotent relink is safe.
- Non-blocking, silent on failure; the playlist save is the primary action and already succeeded.
- Secrets: the `xmltv.php` url embeds credentials — only ever to `SecureValueStore`; never log it, never
  echo it in an exception (S16).

---

## 10. (folded into §2 — decisions resolved)

The three former open micro-decisions are resolved and recorded in §2 (URL-builder in `:iptv:xtream`;
match on host+port+path+username; global UA). §13 tracks the *new* decisions from the scenario pass.

---

## 11. Validation (gates before "done")

- `./gradlew.bat detekt` — no baseline growth.
- `./gradlew.bat assembleDebug` — green.
- `./gradlew.bat test` — incl. new `:data:epg` + `:iptv:xtream` unit tests.
- Emulator smoke (API 28 floor **and** 36 ceiling — secure-store path): add an Xtream playlist with a
  real xmltv.php-serving account → EPG source named after the username appears, linked, populates after
  the refresh worker. Delete the playlist → source survives. Re-add same account → no duplicate,
  relinked. Second account, colliding username, different server → `username #1`. Provider whose account
  is valid but whose xmltv.php is disabled → **no** source created.

---

## 12. Hypothetical scenario pass (adversarial)

Each row: situation → behaviour with the current code → verdict / mitigation. Refs are verified.

| # | Scenario | Behaviour → verdict |
|---|----------|---------------------|
| S1 | Re-add same account after deleting the playlist | Old source survived unlinked (delete removes links only, `:263`). Re-add matches server+username → **reuse + relink + url refreshed**. No duplicate. ✓ designed |
| S2 | Same account kept as a *second* provider | Provider dedup is name-only → allowed if named differently; EPG dedup matches → one source linked to both. Import runs for both. Deleting one provider unlinks only that one. ✓ |
| S3 | Two servers, same username | host differs → no match → new source; name `username` taken → `username #1`. ✓ |
| S4 | Manual source named like the username, unrelated url (the "Test" case) | No server+username match → new source; display name suffixed `#1`. Manual source untouched. ✓ |
| **S5** | **Password rotated via EDIT of an existing Xtream provider** | **Resolved (Rev3): now a trigger** (`type == Xtream && !isSourceUnchanged`). server+username unchanged → dedup **matches** → the existing source's url is updated to the new password + relinked. Gap closed; no duplicate. ✓ |
| S5b | Edit changes the **server** (same username) | `!isSourceUnchanged` → fires; host differs → **no** match → a new source is created (name `username #1`) and linked; the old-server source stays linked with a now-dead url (refresh 401s harmlessly). Accepted under the manual-cleanup stance (consistent with "don't auto-delete/unlink"). Uncommon action, non-breaking. If auto-replace-on-server-change is later wanted, it's a follow-up (unlink the provider's previous stale xmltv.php source before linking the new one). |
| **S6** | **Reuse would clobber user settings** | `EpgSourceEditRequest` defaults `isActive=true, timeShiftMinutes=0`. Passing defaults on reuse would force-enable a disabled source and reset a configured time-shift. **Must-fix (baked into §4/§5):** pass the existing `isActive` + `timeShiftMinutes`. Consequence: a deliberately-disabled reused source stays disabled → auto-EPG yields nothing until re-enabled (accepted, least surprise). |
| **S7** | **Concurrent double-save of the same new account** | Two detection coroutines race the dedup scan → both create → the second `saveSource` throws on name-unique → silent EPG loss for the loser. Low prob on TV. **Mitigation (baked in):** serialise `autoDetectXtreamEpg` with an app-level `Mutex`. |
| S8 | Provider deleted between save and detection | `getCredentials` → null → `return null`. No-op. ✓ safe |
| **S9** | **Validation double-downloads the guide** | **Resolved (Rev3): keep full validation** (`testEpgSourceConnection`) — robust, catches valid-account/xmltv-disabled panels. Cost: the guide is streamed once to validate and again by the worker to import. Background/non-blocking. Ponytail-mark the call site with the status-only `StreamReachabilityProbe` fallback for later if the double-fetch ever hurts. |
| S10 | Valid account but xmltv.php disabled (404 / empty / HTML) | Validation → HTTP error or `channels==0` → `summary == null` → no source created. ✓ no dead source |
| S11 | Server has a path prefix / non-default port (`http://h:8080/xtream/`) | Helper builds `.../xtream/xmltv.php`; dedup compares **full path** + host + port → matches only the same endpoint. ✓ (this is why §2 strengthened to full-path) |
| **S12** | **Special chars in username/password** (`@`, space, `+`) | Helper must URL-encode (`HttpUrl.addQueryParameter` does); dedup must **decode** the stored `username` query before comparing, else a false negative → duplicate. **Must-handle:** decode both sides consistently; unit-test it. |
| **S13** | **M3U→Xtream switch on a provider that already had a linked EPG source** | `clearProviderCatalog` on switch does **not** remove `provider_epg_sources` links (verified `:201-204`) → the old link survives; we add ours. **Must-fix (baked in):** `nextPriority = max(existing)+1` to avoid a unique-(providerId,priority) collision. Both stay linked (user prunes) — consistent with "don't auto-delete". |
| S14 | Xtream→M3U switch (leaving Xtream) | No trigger; link + source remain. Post-refresh of the now-M3U provider re-enqueues the xmltv.php source → it still fetches (url is type-independent) and imports for the M3U provider; channel-id match likely poor but no crash. User cleans up manually. ✓ tolerated |
| S15 | Double enqueue of the refresh | Our explicit `enqueueEpgRefresh` + the pref-gated post-playlist re-enqueue (`:79-81`) both target the source; `KEEP` coalesces → no double work. ✓ belt-and-suspenders |
| S16 | Secrets in logs/exceptions | The `xmltv.php` url embeds credentials → never log it; catch validation/save errors without echoing the url; diagnostics use opaque targets (existing pattern). Invariant to hold in the new code. |
| S17 | Network burst on one create | Sequence per create: pre-save Xtream connection test → save → EPG validation (full XMLTV) → worker playlist refresh (catalog) → worker EPG import (full XMLTV again). Heavy but background/coalesced; part of the S9 cost discussion. |

---

## 13. Decisions from the scenario pass (resolved)

1. **S5 — credential edit of an existing Xtream provider → RESOLVED: extend the trigger.** Detection now
   fires on any save where `type == Xtream && !isSourceUnchanged` (new, type-switch, **or** credential
   change). A password rotation re-runs detection; server+username unchanged → dedup updates the existing
   source's url. Pure metadata edits (unchanged credentials) do not re-run it. Minor consequence S5b
   (server change leaves the old link stale) accepted under the manual-cleanup stance.
2. **S9 — validation depth → RESOLVED: keep full XMLTV validation** via the existing
   `testEpgSourceConnection`. Robust against valid-account/xmltv-disabled panels. The extra background
   download is accepted; the call site carries a ponytail comment naming the status-only fallback.

Everything in §12 is now either designed-correct or a must-fix folded into §4/§5/§7. No schema, module,
or DI change. **Plan is complete — awaiting GO to implement.**
