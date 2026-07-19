# Cold-Start Splash Overlay — Research + Plan

> Status: **REVERTED after on-device test. Abandoned — does not solve the stated problem. No commit.**
> The v1 overlay + Phase-A theme tweaks were built, then fully reverted (working tree back to main).
>
> **On-device finding (Xiaomi Mi Smart TV 4S, Android 9, Amlogic — the physical test target):** a true
> cold start shows ~2.5–5 s of **pure black (RGB 0,0,0)**, then the brand splash for a fraction of a second,
> then Home. The black is the process fork + first-Compose-frame window: no app-drawn surface exists yet, so
> the low-end TV just shows black there. `android:windowBackground = #050914` did **not** tint it navy on
> this device (captures stayed pure black) — the starting-window paint is effectively skipped/black on this
> board. The in-app Compose splash renders correctly (verified) but can only appear **after** the first
> frame, i.e. **after** the black — so it adds a brief navy-logo flash between black and Home and does not
> replace the black. Net: the splash cannot cover the pre-first-frame window that is the whole complaint.
>
> **Real lever (separate task, if wanted):** shorten the cold-start / first-frame time itself (what blocks
> the first frame — `AppContainer` / Room open / DataStore first read on the startup path). That shrinks the
> black directly; a splash cannot. Not pursued here.
>
> Original goal (kept for context): replace the "few seconds black screen" on a true cold start with a
> branded Vivicast image shown while the app loads. Conclusion: not achievable via an in-app splash on this
> hardware.

## Problem — two black phases on cold start

- **Phase A (pre-first-frame):** launcher tap → process fork → `VivicastApplication.onCreate` →
  `MainActivity.onCreate` → first Compose frame. The system shows a "starting window" painted with
  `android:windowBackground`. Ours = **black** (`values/styles.xml`, `values-v31/styles.xml`).
- **Phase B (post-first-frame, data loading):** first frame drawn, but preferences / PIN / catalog not
  ready yet → empty screen until content fills. **This is the long part** ("paar Sekunden") and the one
  the user actually sees.

### Why the system SplashScreen API does NOT solve this on Android TV
Official Android docs, verbatim: *"In Android 12 and higher, custom splash screen animations built using
the SplashScreen platform API are not supported for Android TV apps."* On TV the system draws its own
**expanding filled circle**, colour = `android:colorPrimary` only. Our current
`values-v31/styles.xml windowSplashScreenAnimatedIcon` (the Vivicast mark) is therefore **ignored on TV**
— it would only ever render on phones/tablets, which we don't ship to (`leanback required=true`). And no
system splash, on any platform, covers Phase B. → The reference app (AerioTV) reached the same conclusion:
it uses an **in-app Compose overlay**, not the SplashScreen API.

Sources:
- https://developer.android.com/training/tv/get-started/create (TV splash caution + colorPrimary)
- https://developer.android.com/develop/ui/views/launch/splash-screen (SplashScreen API, keep-on-screen)

## Decision — in-app Compose splash overlay

On TV this is the **only** reliable way to show our own image during the load window (we control the
pixels; no system restriction). Draw a full-bleed branded surface on top of the app content the instant
the first frame renders, hold it until the app shell is ready, then crossfade to the content.

Rejected alternatives:
- `androidx.core:core-splashscreen` — wrong tool: custom branding blocked on TV, doesn't cover Phase B,
  and adds a dependency + theme migration the logos README already deliberately avoided.
- Separate `SplashActivity` — official anti-pattern; we stay single-activity.

## Asset decision — NO new graphic needed

Build the splash **natively in Compose** (mirrors the brand `mark-lockup` composition), which is crisp at
any TV resolution and trivially resizable ("etwas größer" = one `Modifier.size` value):

- **Mark:** reuse the existing in-app vector `app/.../res/drawable/ic_launcher_foreground.xml`
  (C + V + Play, brand colours, already shipped). It is a `VectorDrawable` → scales up sharp; draw it at
  e.g. 160–200dp instead of the launcher's small size.
- **Wordmark "Vivicast":** render as Compose `Text` — cyan `V` (`#00C8FF`) + `ivicast` (`#F8FAFC`),
  bold. Live text = sharp at any size, and sidesteps the "Android VectorDrawable can't render `<text>`"
  limitation the logos README calls out. "Vivicast" is a brand literal (never translated).
- **Background:** full-bleed navy `#050914` (brand background token).
- **Optional tagline** "Live-TV · Filme · Serien" (`#94A3B8`) — only if the user wants it.

**Feature graphic (`feature_graphic_1024x500`, the one the user spotted) — usable but NOT recommended:**
it is a wide 1024×500 **raster** store asset (navy baked in, tagline included). Scaled up to fill a
1080p/4K TV it goes soft/blurry (contradicts "etwas größer"), and 2.05:1 letterboxes on 16:9. The
Compose composition above gives the same look, sharper and resizable.

**Optional (only if we ever want the mark bigger than the launcher's `.78` safe-zone scale):** convert
`docs/logos/splash_icon.svg` → a dedicated `splash_mark.xml` VectorDrawable. This is *derived from an
existing brand SVG*, not a new design — but per the user's instruction, flag before adding. Not required
for v1; reusing `ic_launcher_foreground` + Compose sizing is enough.

→ **Answer to the user: no image file required from you, and no new graphic to design.**

## Ready signal + timing (best practice: tie to real readiness, cap it)

`VivicastApp` already computes the state we need — reuse it, no new plumbing:
- `loadedPreferences != null` (first DataStore emit) **AND** `pinSecurityLoaded` (PIN state read).
  Both true = the app shell can render real content. This is start-branch agnostic (covers normal start,
  resume-last-channel, and deep-link launches alike).
- **Dismiss** the overlay when ready. Best-practice caution: don't wait for *all* content (Home lists,
  catalog) — that feels slow; Home shows its own loading. Shell-ready is the dismiss point.
- **Min visible time** ~400–600 ms so a fast device doesn't flash the splash for one frame.
- **Hard max timeout** ~4 s as a backstop so a stuck load never traps the user on the splash
  (AerioTV uses 6 s; we tie to readiness so we can go shorter).
- **Crossfade** out (`animateFloatAsState` on alpha), then unmount.

## Architecture fit (Mandatory Rules — all satisfied)

- **App-hoisted:** lives at the composition root (`MainActivity.setContent` / `VivicastApp`), alongside
  the other app-hoisted effects. ✔
- **Local UI state only:** a `splashFinished: Boolean` + alpha animation — no ViewModel, no UiState,
  no `StateFlow` needed (same category as "dialog open/closed"). ✔
- **No Repository Flow / CRUD in the composable:** reuses already-hoisted `loadedPreferences` /
  `pinSecurityLoaded`; adds none. ✔
- **Strings in `:core:designsystem` only:** the wordmark "Vivicast" is a brand literal; **if** we add the
  tagline, its string goes in `core/designsystem/res/values/strings.xml` + `values-en/strings.xml`
  (never the app module). ✔
- Drawable reuse (`ic_launcher_foreground`) is in the app module already; no designsystem change unless
  tagline is added. ✔

## Implementation steps (on GO)

1. New composable in the **app** module (app-root overlay, e.g. `app/.../VivicastSplash.kt`):
   `VivicastSplash(modifier)` = navy `Box` + centered `Column` { `Image(ic_launcher_foreground)` at
   ~180dp + `Text("Vivicast")` brand-coloured (+ optional tagline) }. Static, no animation inside.
2. Wrap the existing content in `VivicastApp` with a `Box`: content underneath (so it loads while
   covered), `VivicastSplash` on top gated by `!splashFinished`, per the AerioTV `SplashGate` shape.
3. Drive `splashFinished` from `loadedPreferences != null && pinSecurityLoaded` + min-time + max-timeout;
   crossfade alpha then flip finished.
4. (Optional, only if requested) tagline string in designsystem de/en.

## Optional Phase-A polish (cheap, kills the pre-first-frame black flash — separate GO)

Independent of the overlay; makes the overlay's entrance seamless:
- `values/styles.xml`: `windowBackground` black → navy `#050914` (same token) → API 26–30 Phase A is
  navy, not black. One-line colour change.
- Add `android:colorPrimary = #050914` (or the accent) so the API 31+ TV system circle is brand-tinted
  instead of default blue. (We currently set only `colorAccent`.)

## Gates before done

- `.\gradlew.bat detekt`, `.\gradlew.bat assembleDebug`, `.\gradlew.bat test` green.
- Emulator drive on **floor (Api 28)** and **ceiling (Api 36)**: cold start (force-stop first) shows the
  Vivicast image immediately, then crossfades to Home; no black gap; no trap if data is slow.

## Open questions for the user (before code)

1. **Tagline** under the wordmark — yes ("Live-TV · Filme · Serien") or mark + wordmark only?
2. **Phase-A polish** (navy `windowBackground` + `colorPrimary`) — include it, or overlay only?
3. Mark size / exact layout is tunable live; any preference or leave to a first emulator pass?
