# Audit Remediation — Phase 1: Release Blockers

Source: `CODE_REVIEW.md` (2026-07-18 pre-release audit). Scope = the **1 Critical + 5 High** findings
that gate any release. Full evidence per finding is in `CODE_REVIEW.md` (referenced as `CR #n`).

**Rule: no code changes until explicit GO.** Items marked ⚠ need a decision from you before I implement them.

**Status (2026-07-18):** all six implemented on branch `audit/p1-release-blockers`; `detekt` + `assembleDebug`
+ `test` green (incl. new `#6` `RefreshErrorClassificationTest` and `#2` XMLTV guard tests). Signed
`assembleRelease` verified — `app-release.apk` (36.5 MB), **v1+v2** schemes, `apksigner verify` exit 0
(installs from API 23). On-device smoke (API 28 + 36) for `#2`/`#7` and the commit still pending.

## Ordering

1. `#28` release build config — nothing ships without it.
2. `#1` exported provider — one-attribute security fix.
3. `#29` MANAGE_EXTERNAL_STORAGE — ⚠ distribution-channel decision; may cascade into the file picker.
4. `#6` auth-retry loop — runtime/battery/ban risk.
5. `#2` XMLTV DoS — untrusted-input hardening.
6. `#7` player timeline — visible correctness bug.

## Global validation (run after each item; full set before declaring P1 done)

```
.\gradlew.bat detekt
.\gradlew.bat assembleDebug
.\gradlew.bat test
```
Plus, specifically: `#28`/`#29` → `assembleRelease` **and** an on-emulator smoke run (API 28 **and** 36,
per CLAUDE.md) because R8 stripping only shows at runtime; `#7` → visual player check (VOD + live);
`#6` → worker unit test; `#2` → parser test with a billion-laughs payload.

## Decision index (discuss before GO)

- ⚠ `#28` — release keystore source (CI/env vs gitignored `keystore.properties`); minify on/off.
- ⚠ `#29` — target Google Play (drop the permission, rework picker) vs sideload/alt-store (keep it).
- ⚠ `#2` — disallow DOCTYPE outright vs bound entity expansion.
- ⚠ `#7` — how the timeline renders for **live** streams (no real duration).

---

## `#28` (Critical) — No release signing / R8 / shrink config

**Decision (resolved 2026-07-18):** R8 **off** — no minify/shrink/obfuscate (pointless for an open-source,
non-Play app distributed as sideload / GitHub-release APKs). Sign with an own release keystore; Gradle reads
a gitignored `keystore.properties` locally, with an **env-var fallback** for CI (GitHub Actions). Keystore +
passwords live only in GitHub Secrets / the gitignored local file — never committed (repo is public).

**Files:** `app/build.gradle.kts` (add `signingConfigs.release` + `buildTypes.release`); gitignored
`keystore.properties` (user-provided); `.gitignore` entries. **No** `proguard-rules.pro` while R8 is off.

**Fix**
- At the top of `app/build.gradle.kts`, load signing: read `keystore.properties` if present, else env vars
  (`VIVICAST_KEYSTORE_FILE` / `..._STORE_PASSWORD` / `..._KEY_ALIAS` / `..._KEY_PASSWORD`). If **neither** is
  present, skip the release `signingConfig` so a fresh clone / CI-without-secrets still configures + builds debug.
- `signingConfigs { create("release") { … } }` from those values.
- `buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("release") } }`.
- Keystore lives **in the repo dir, gitignored** (`vivicast-release.jks` at repo root); `keystore.properties`
  at repo root with a **relative** `storeFile`. Add `keystore.properties`, `*.jks`, `*.keystore` to `.gitignore`,
  and commit a placeholder `keystore.properties.example` (no secrets) documenting the format for CI/contributors.
- **User action (before a signed release build):** generate the keystore —
  `keytool -genkeypair -v -keystore vivicast-release.jks -alias vivicast -keyalg RSA -keysize 4096 -validity 10000`
  — then create `keystore.properties` from the example with the real passwords (never committed, never shared).
  Alias `vivicast`, RSA 4096, 10000 days.

**Validate:** `assembleRelease` produces a **signed** APK (`apksigner verify`); install on emulator (API 28 + 36)
and smoke every screen + playback + a catalog/EPG refresh.

**Risk:** low with R8 off (no stripping). Main care: the release `signingConfig` must degrade gracefully when
secrets are absent so non-release / CI-without-secrets configuration doesn't break.

**Note:** the later "GitHub Actions builds + signs + attaches the APK to a Release" workflow is a **separate
follow-up task**; this signing wiring is made CI-compatible for it. R8 stays retrofittable if APK size ever matters.

**CI secrets (locked names — repo `github.com/Spegeli/vivicast`):** `KEYSTORE_BASE64` (base64 of the `.jks`),
`KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS` (=`vivicast`), `KEYSTORE_KEY_PASSWORD`. The future workflow
decodes `KEYSTORE_BASE64` to a temp file → sets `VIVICAST_KEYSTORE_FILE` to it and maps the other three onto
`VIVICAST_STORE_PASSWORD` / `VIVICAST_KEY_ALIAS` / `VIVICAST_KEY_PASSWORD` (the env-fallback the Gradle wiring reads).
Secrets set by the user via GitHub UI; never committed.

**Ref:** CR #28.

---

## `#1` (High) — Exported search ContentProvider has no permission

**Decision (resolved 2026-07-18):** keep the TV global-search integration, lock the provider to `GLOBAL_SEARCH`.

**Files:** `app/src/main/AndroidManifest.xml` (`<provider>` ~L81-84).

**Fix:** add `android:readPermission="android.permission.GLOBAL_SEARCH"` and `android:permission="android.permission.GLOBAL_SEARCH"`; keep `exported="true"`.

**Validate:** build; on-emulator Android TV global-search still returns Vivicast suggestions (system search app holds `GLOBAL_SEARCH`). Optionally assert a plain `contentResolver.query` from a test app/shell is now denied.

**Risk:** low; `GLOBAL_SEARCH` is the standard gate for suggestion providers.

**Ref:** CR #1.

---

## `#29` (resolved → cleanup) — MANAGE_EXTERNAL_STORAGE

**Decision (resolved 2026-07-18):** distribution is **sideload / GitHub-release (no Google Play)**, so the
Play restricted-permission blocker does not apply. **Keep** `MANAGE_EXTERNAL_STORAGE` (the TV-safe in-app
picker needs it; sideload permits it); **no** file-picker rework. Only the inert legacy flag is removed.
Severity drops from High to a one-line cleanup.

**Files:** `app/src/main/AndroidManifest.xml` (remove L45 `android:requestLegacyExternalStorage="true"`).

**Fix:** delete the `requestLegacyExternalStorage` attribute (inert at targetSdk 36, honored only ≤29). Leave
the storage permissions as-is.

**Validate:** build; M3U import + backup export/import via the in-app picker still work on emulator (grant
"All files access" once).

**Ref:** CR #29. Cross-ref memory `android-tv-saf-limitation`.

---

## `#6` (High) — Permanent auth failure retries forever

**Decision (resolved 2026-07-18):** targeted classification **plus** a hard `runAttemptCount` backstop for
transient errors. User-visibility: rely on the existing `InvalidCredentials` provider status (no new notification).

**Files:** `worker/src/main/java/com/vivicast/tv/worker/RefreshExecution.kt` (`isDeterministicRefreshError` + both failure branches, ~L47-48/104/132).

**Fix:**
- Broaden the terminal-error test → `Result.failure()` for `RefreshAuthenticationException`, `RefreshImportException`, `IllegalArgumentException`, and non-transient 4xx (401/403/404) `RefreshHttpException`/`XtreamHttpException`.
- Keep `IOException`, 5xx and 429 as `Retry`.
- Backstop: at `runAttemptCount >= MAX_REFRESH_ATTEMPTS` (proposed **10** — exponential backoff already reaches the multi-hour range by then; tunable) → `Result.failure()` even for transient errors, so a never-recovering transient can't loop forever.

**Validate:** worker unit test — 401/403 → failure (no retry); 5xx/429/IO → retry below the cap; at `runAttemptCount == cap` → failure. (Alongside existing `RefreshTimingTest`/`RefreshWorkRequestsTest`.)

**Risk:** don't over-classify — transient errors must stay retryable below the cap or legitimate refreshes stop; keep the cap high enough to ride out real outages.

**Ref:** CR #6.

---

## `#2` (High) — XMLTV streaming parser billion-laughs DoS ⚠

**Decision (resolved 2026-07-18):** keep DOCTYPE **allowed** (the streaming parser accepts it on purpose —
real XMLTV feeds carry `<!DOCTYPE tv SYSTEM "xmltv.dtd">`, see `XmltvStreamParser.kt:44`); block the actual
attack vector — **internal entity declarations** — and **reject** any feed carrying them (fail-closed).
`disallow-doctype-decl` is explicitly **not** used here: it would regress legitimate DOCTYPE-bearing feeds.

**Files:** `iptv/xmltv/src/main/java/com/vivicast/tv/iptv/xmltv/XmltvStreamParser.kt` (~L42-54, `saxFactory` +
`streamXmltv`/`maybeGunzip`). DOM path (`XmltvContracts.kt:155`) already `disallow-doctype-decl=true` — leave it.

**Fix:**
- Reject a DOCTYPE **internal subset that declares entities**: a legit XMLTV DOCTYPE has no internal subset;
  billion-laughs requires `<!ENTITY …>` declarations. Android's Expat SAX does **not** reliably enforce JAXP
  expansion limits / `DeclHandler`, so use a robust cross-version guard — peek the buffered head of the stream
  (after `maybeGunzip`) and reject if the internal subset contains `<!ENTITY` before handing it to SAX.
  (A `DeclHandler.internalEntityDecl` that throws is defense-in-depth where the property *is* supported.)
- **Fail closed:** stop `runCatching`-swallowing the security-critical `setFeature` calls silently — if XXE
  protections can't be set, treat the feed as un-parseably-unsafe instead of parsing it unprotected.

**Validate:** parser test — a nested internal-entity payload is **rejected** (no OOM); a normal feed *with* a
plain `<!DOCTYPE tv SYSTEM "xmltv.dtd">` (no internal entities) still parses. Verify on-device (API 28 + 36 —
Expat behavior differs by version). Extend `XmltvStreamParserTest`. Related: `#17` (gzip head read) touches the
same `maybeGunzip` head — coordinate the peek logic.

**Ref:** CR #2.

---

## `#7` (High) — Player timeline shows fabricated time ⚠

**Decision (resolved 2026-07-18):** VOD (seekable) shows real elapsed / total (mm:ss, h:mm:ss when ≥1h). Live
shows **only** the right-side `LIVE` marker, **no left time value** (drop the fake left clock). Both the
`"00:$progress"` and hardcoded `"01:40"` literals are removed.

**Files:** `core/designsystem/.../VivicastPlayer.kt` (L118-119 `VivicastPlayerTimeline`; `VivicastPlayerOverlay` forwards the new params); `feature/player/.../PlayerRoute.kt:141` (thread position + duration, not just `progressPercent()`).

**Fix:**
- Extend `VivicastPlayerTimeline` to take real `positionMillis` + `durationMillis` (or pre-formatted strings): left = formatted position when `seekable`, empty when live; right = formatted duration when `seekable`, `"LIVE"` otherwise. Keep the proportional fill bar.
- `PlayerRoute` reads `controllerState` (`durationMillis` at `VivicastPlayerController.kt:708`; position behind `progressPercent()`) and passes both down.

**Validate:** emulator — a movie shows real elapsed/total (h:mm:ss past 1h); a live channel shows `LIVE` with no left value; catch-up (seekable window) shows real times.

**Ref:** CR #7.
