# D17 — Appearance colours: background + accent + transparency (audit O-2 / O-4)

Status: **COMPLETED (2026-07-14).** Owner-reviewed on device across all 17 background hues, accent colours,
transparency (panels + player overlay), swatch pickers, tonal-flat panels, and the backdrop-seam fix. Final
verification pass found no regressions/contradictions; all gates green (`test` 1011 tasks, detekt,
`assembleDebug`, strings-only-in-designsystem). Split out of the former combined
`d10-group-management-d17-accent-colors.md` (D10 stays in its own file). Source of the feature:
`docs/archive/SETTINGS-DOCS-CODE-AUDIT.md` (O-2/O-4).

### Build deviations from the plan (all intentional)
- **Re-hue approach** instead of ~25 scheme fields: each navy surface literal wraps in `scheme.surface(c)`
  (re-hue keeping lightness/alpha); default = identity → byte-identical. Accent literals use
  `scheme.accentize(c)`. Far smaller + maintainable (see `VivicastPalette.kt`).
- **Default transparency = 20 %** (25 % is not on the 0/10/… grid; 20 % ≈ current 0.75 opacity feel).
- **Swatch picker = 3-column grid** (circle + short name) for compactness.
- **Blue swatch/accent = brand cyan** `0xFF00C8FF` so the factory default is unchanged.
- Palette hexes are a **first pass** (Material-ish); explicitly to be fine-tuned on device.
- **Vividness = "Satt" (owner choice, on-device review 2026-07-14).** First flat re-hue made warm hues muddy
  (dark yellow = olive, dark orange = brown). Replaced with **hand-curated per-hue params**
  (`surfaceParamsFor`): true hue angle + saturation + a lightness **band** (`floor`/`span`) that remaps the
  navy literals into a saturated-but-white-text-legible range (warm hues use a lower band to keep contrast).

---

## Current state (the feature effectively does not exist yet)

- **Both background AND accent are dead.** `VivicastTheme` (`core/designsystem/.../VivicastTheme.kt:24`)
  takes **only `content`** — no accent / background / theme parameter. `MainActivity.kt:176-183` applies
  **only** `fontScale` (via `LocalDensity`) + `transparency` (via `LocalSurfaceOpacity`). So
  `appearance.backgroundColor` (`ThemeColor`) and `appearance.accentColor` are stored / mapped / backed-up
  but **never rendered** — the three "Hintergrundthema" options render identically, and accent is the fixed
  constant `VivicastColors.Accent = Color(0xFF00C8FF)`.
- Three-layer enum pattern (per appearance field): datastore `ThemeColor{Dark,HighContrastDark,AmoledDark}`
  / `AccentColor{Blue}` / `TransparencyLevel{Percent0,25,50}` (`UserPreferencesStore.kt:90-94`) ↔ UI
  `SettingsThemeMode` / `SettingsAccentColor` / `SettingsTransparency` (`SettingsModels.kt:26-40`), bridged
  by `when`-mappers (`SettingsPreferenceMappers.kt:40-76`).
- **Persistence = enum `.name` + graceful fallback** (`enumValue`, `DataStoreUserPreferencesStore.kt:241`)
  → **adding enum cases needs NO migration**; unknown stored values fall back to the default.
- `LocalVivicastColors` CompositionLocal is **defined but never provided or consumed**
  (`VivicastTheme.kt:208`) — the seam for dynamic colour already exists, unused.
- Background painted in `VivicastScreenBackground` (`VivicastSurfaces.kt:44`) = a 3-stop vertical gradient;
  only the middle stop reads `VivicastColors.Background`, the other two are literal navy hexes.
- **No contrast/luminance logic anywhere.** `onPrimary` = fixed `TextOnAccent = Color(0xFF031525)`
  (dark-on-cyan) — will not hold for other hues.
- Picker UI = generic **text-list** `SettingsChoiceDialog` (`AppearanceSettingsPanel.kt:232-271`).
- Transparency mechanism: enum → opacity multiplier `toSurfaceOpacity` (`app/SettingsPreferenceMappers.kt:153`,
  normative `0%→1.0, 25%→0.75, 50%→0.5`), applied to panel fills via `LocalSurfaceOpacity`
  (`VivicastPanels.kt:48`, `VivicastSurfaces.kt:216`); dialogs re-provide `1f` to stay opaque
  (`VivicastDialogs.kt:57`).

---

## DECISIONS (locked 2026-07-14, owner)

1. **Background = dark-tinted colours.** "Hintergrundthema" → renamed **"Hintergrundfarbe"**. **17 options**
   (Material-family): Rot, Pink, Lila, Indigo, Blau, Cyan, Blaugrün, Grün, Lime, Gelb, Bernstein, Orange,
   Braun, Grau, Blaugrau, Dunkelgrau, Schwarz. Each rendered as a **very dark shade** of the hue
   (Schwarz = pure AMOLED black). Stays TV-dark + readable. The old dark-variant trio
   (Standard/Kontrast/AMOLED) is dropped — `Dunkel kontrastreich` has no colour equivalent (was dead anyway).
2. **Accent = 16 colours** (same list minus Dunkelgrau). Keeps a blue as default.
3. **Rendering = Variante B (full tonal).** The background hue drives the **whole surface family** — backdrop
   AND every panel/card/row surface — as a **dark tonal ramp** (backdrop darkest → `Surface` →
   `SurfaceHigh`/elevated a step lighter, so panels lift off the backdrop). Panels stay **dark tints** and
   body text stays light → text contrast holds for every hue by construction. `Schwarz`/`Dunkelgrau`
   collapse backdrop≈panel (AMOLED-flat) — expected; focus ring/border still delineates panels.
4. **Accent colours highlight states only:** focus (ring + glow), selected, toggle-on, badges.
   **Focus follows accent** (`FocusRing`/`FocusGlow` take the accent hue) → unified look. Backed by (5).
5. **Contrast = runtime auto-luminance (WCAG).** One pure helper picks the on-accent foreground
   (white/near-black) and **boosts the focus/accent tone so it always stays visible on the chosen dark
   background** (16×17 combos → runtime compute, not baked pairs).
6. **Colour picker = swatch + small name.** Both the Hintergrundfarbe and Akzentfarbe pop-ups show each
   option as a filled **coloured circle** (red for Rot, green for Grün, …) with a **small name caption**,
   kept **compact enough to fit one pop-up** without growing oversized. Black/dark swatches get a hairline
   border so they stay visible on the dark dialog. *(Deviates from the old text-only spec — see Docs.)*
7. **Transparency = 0–100 % in 10 % steps** (11 values), replacing 0/25/50. Default **25 %**. 100 % usable
   (panels fully transparent; dialogs already re-provide opaque).
8. **Re-tint scope = B1** (structural surfaces + accent; decorative poster/hero gradients stay fixed navy).
   See COLOUR INVENTORY below.

**Factory default is unchanged visually:** default background = a dark blue, default accent = blue → the
current cyan-accent + navy look is reproduced exactly at first run.

---

## COMPLETE COLOUR INVENTORY (three buckets) — full audit 2026-07-14

Verified: `colorScheme.*` is read **0 times** in app code (only androidx.tv library components read it for
their own defaults → keep it set, but app surfaces don't depend on it). The full `VivicastColors.*` usage
list is complete (26 named tokens). **Critical:** most *visible* surface colour comes NOT from the named
`Surface*` tokens but from **~40 hardcoded navy hex literals** inside the designsystem painters. Any "panels
follow background" therefore means converting those painter literals, not just the 8 named tokens.

### Bucket A — BACKGROUND-driven (dark tonal ramp from the chosen hue)
- **Named surface tokens (8):** `Background, BackgroundElevated, Surface, SurfaceHigh, SurfaceFocus,
  SurfacePressed, SurfaceSelected, SurfaceDisabled` (read 18× — designsystem-centric).
- **Painter literals (the real panel/backdrop fills):**
  - `VivicastSurfaces.kt` — `VivicastScreenBackground` backdrop stops (`:54/:56`) + overlay (`:66`);
    `VivicastGlassPanel` fill (`:223-225`) + border (`:229`); `FocusSurface` idle border (`:94`);
    settings-item surface gradients (`:239-242`).
  - `VivicastPanels.kt` — `VivicastDetailsPanel` fill+border (`:54/:57`); glass-panel base (`:117` non-media
    stops); scrims stay black.
  - `VivicastLayout.kt` — `VivicastContentCard` fill+border (`:58/:59`).
  - `VivicastNavigation.kt` — selected/focus top-nav container (`:159/:161`), logo inner fill (`:230`).
  - `VivicastInputs.kt` — text-field / stepper / toggle-track surface fills (`:179/:393/:418/:420/:458/:460`)
    + unchecked border (`:185/:422`).
  - `VivicastPlayer.kt` — overlay panel bg (`:60`), scrubber track (`:154`).

### Bucket B — ACCENT-driven (chosen accent hue + auto-luminance guard)
- **Named:** `Accent, AccentSoft, FocusRing, FocusGlow, Focus`, `Info` (= alias of `Accent`).
  `TextOnAccent` → **replaced by computed** `onColorFor(accent)`. (~15 named reads: designsystem
  `VivicastSurfaces/Navigation/Inputs`, feature `SettingsRoute:531`, `ProviderEditor:939/944`,
  `SearchRoute:211`, app `FilePickerDialog:252`.)
- **Accent-tinted literals:** cyan focus/selected borders (`VivicastCards:260/:367`, `VivicastPanels:120`,
  `VivicastPlayer:61`, `VivicastBadges:28`), and the **brand logo** gradient
  (`VivicastNavigation:229` = `0xFF00C8FF/0xFF2563EB`).

### Bucket C — NEITHER (static, stays fixed)
- **Text:** `TextPrimary/Secondary/Tertiary/Disabled` (light greys).
- **Semantic status (meaning-bound — must NOT shift):** `Success`(green), `Warning`(amber), `Error`(red),
  `Live`(red), `Favorite`(yellow), `CatchUp`(blue), `Progress`(blue) + their badge-tone literals
  (`VivicastCards:338-340` Live/Favorit/Catch-Up, `:185` favorite/green).
- **Pure-black scrims / image darkeners** (`0x..000000`, `VivicastCards` poster scrims, `VivicastPanels:150`)
  — hue-neutral, stay.
- **Decorative media gradients** — poster **placeholder** gradients (`VivicastCards:139/141/259/362/364`) and
  the **hero featured** blue→amber gradient (`VivicastPanels:117/143`). Sit behind artwork; see scope below.

### Re-tint scope = B1 (LOCKED 2026-07-14, owner)
- **B1 (chosen):** bucket A structural surfaces + bucket B accent. Bucket-C decorative media gradients
  (poster placeholders, hero featured panel) **stay fixed navy** — they mostly sit behind poster artwork, so
  a hue mismatch shows only transiently while art loads. Bounded, low-risk, ~6 designsystem painters.
- B2 (full decorative re-tint) was rejected: large, fragile (glass/hero hand-tuned per stop), small payoff.
  Individual decorative gradients can be revisited later if a specific hue looks off.

## BUILD PLAN

### Palette + dynamic-colour seam (`:core:designsystem`)
- New `VivicastPalette.kt`: owns the brand hue tables + a `data class VivicastColorScheme` holding the
  **dynamic subset**:
  - accent family: `accent, accentSoft, focusRing, focusGlow, onAccent`
  - **background tonal ramp** (Variante B): `backgroundBase, backgroundElevated, surface, surfaceHigh,
    surfaceFocus, surfacePressed, surfaceSelected, surfaceDisabled` + the 2 backdrop-gradient end stops.
- **Per-hue tables (3 values per hue):** `swatchColorFor(hue)` = a **vivid** representative (≈ Material 500)
  for the picker circle; `darkBaseFor(hue)` = the dark base the ramp is built from; accent value for the
  accent role. The applied dark ramp is **derived by a pure function** `darkRampFor(base)` via lightness
  steps (backdrop = base·darker, surface = base, surfaceHigh = base·lighter, …) → a new hue = one base
  value, not eight hand-picked hexes. `Schwarz` = pure black base; the swatch keeps a border so it's visible.
- Auto-luminance helpers (pure, testable): `onColorFor(bg): Color` (WCAG relative luminance → white/near-
  black) and `ensureVisibleOn(fg, bg): Color` (raise fg lightness until the contrast ratio clears a min
  threshold). **One unit test** asserts boundary cases + that a low-contrast pair gets boosted.
- Retype the unused `LocalVivicastColors` from `object` to `VivicastColorScheme`, and **provide** it in
  `VivicastTheme(scheme: VivicastColorScheme = VivicastColorScheme.Default, content)` (also set
  `colorScheme.primary/secondary/background/border` from the scheme). `object VivicastColors` stays for all
  **static** tokens (text, success/warning/error, live/favorite, spacing, shapes…).
  `VivicastColorScheme.Default` reproduces today's values → zero visual change at default.

### Route reads → `LocalVivicastColors.current` (see COLOUR INVENTORY for the full map)
Two things move from static → scheme (scope B1):
- **Named tokens:** bucket-B accent family (~15 reads) + bucket-A surface tokens (18 reads) →
  `LocalVivicastColors.current.*`.
- **Painter literals (bucket A):** the ~6 designsystem painters that hardcode navy fills
  (`VivicastSurfaces` GlassPanel/backdrop/settings-item, `VivicastPanels` DetailsPanel,
  `VivicastLayout` ContentCard, `VivicastNavigation` containers, `VivicastInputs` field/track surfaces,
  `VivicastPlayer` overlay) switch their literal `Color(0x…)` to the scheme ramp. Bucket-B accent-cyan
  literals (focus borders + logo) switch to the scheme accent. Bucket C stays literal.
- Net: the settings **left rail + right detail panel** (the owner's example) are painted by
  `VivicastGlassPanel`/`FocusPanel` + `VivicastDetailsPanel` — all bucket A → they follow the background hue.

### Swatch picker (`:core:designsystem` + `AppearanceSettingsPanel.kt`)
- New `SettingsColorChoiceDialog` (swatch variant of `SettingsChoiceDialog`): each option = a focusable row/
  tile with a filled **circle** (`swatchColorFor(hue)`, hairline border) + a small name caption; selected
  marked. Vertical list reusing the existing dialog scaffold + D-pad focus (grid is a later upgrade).
- `AppearancePicker.Accent` and `.Theme` (→ Hintergrundfarbe) use the swatch dialog; the other pickers
  (Transparency/Font/Animation) stay text `SettingsChoiceDialog`.
- The settings **row** value for Hintergrundfarbe/Akzentfarbe gains a small leading swatch too (consistency,
  cheap) — optional; keep the text label as the row value.

### Persistence + mapping (migration-free)
- `:core:datastore` `UserPreferencesStore.kt`: extend `ThemeColor`(17 hues) + `AccentColor`(16) +
  `TransparencyLevel`(11 = Percent0…Percent100). **Keep the type names** (churn cut); only cases change.
  Defaults: background = dark blue, accent = Blue, transparency = Percent25. No DataStore-impl change; no
  migration; old stored `Dark`/`Percent25`/… degrade gracefully.
- `:feature:settings` `SettingsModels.kt`: mirror the three enums.
- `SettingsPreferenceMappers.kt` (feature): extend the three `when`-pairs both directions.
- App root `MainActivity.kt:176`: build a `VivicastColorScheme` from `preferences.appearance`
  (enum → designsystem palette) and pass it to `VivicastTheme(scheme = …)`.
- App `SettingsPreferenceMappers.kt`: `toSurfaceOpacity` = `(100 - percent)/100f` across 11 cases.

### UI labels (`AppearanceSettingsPanel.kt`)
- Extend the three `.label()` `when`s (colour name strings). Transparency label stays numeric inline
  (`"$p %"`) — no strings.

### Strings — `:core:designsystem` both locales only (de + en)
- Rename `settings_theme` "Hintergrundthema" → "Hintergrundfarbe" (+ `settings_help_theme`). Drop
  `theme_dark/theme_dark_contrast/theme_amoled` + `accent_blue`. Add 17 background + 16 accent colour-name
  strings (shared names reused where identical). Review `settings_help_accent`/`settings_help_transparency`.

### Docs — owner-approved changes to `../vivicast-docs`
- `design/screens/07-settings.md`:
  - rename **Hintergrundthema → Hintergrundfarbe**; new colour lists (17 bg / 16 accent).
  - soften "Das Theme bleibt immer TV-tauglich und dunkel" → dark **getönt** (still dark, hue-tinted).
  - transparency values 0 %…100 % in 10 % steps (default 25 %).
  - **:131 flip:** "Textauswahl-Dialog … kein Farbfeld" → swatch (coloured-circle) picker.
  - note: focus hue follows accent, visibility guaranteed by the contrast guard.

### Gates
- `.\gradlew.bat detekt` (watch baseline: `VivicastTheme` signature + the enum `when`s — 17 branches < 20,
  should clear `CyclomaticComplexMethod`), `:app:assembleDebug --rerun-tasks`, `test` (+ new luminance test).
- Update `StandardBackupTest.kt:400/421` (`ThemeColor.AmoledDark` → a surviving case).
- Emulator visual check API 36 ceiling: cycle several background + accent combos; confirm focus stays
  visible on the darkest / most-saturated background (the guard's job) and swatches are distinguishable.

---

## Transparency scope — follow-up (decided 2026-07-14, NOT yet built)

Research (5 peer IPTV apps in `../IPTV-APPS`): **none** expose a user transparency/opacity slider —
panel/dialog opacity is always designer-hardcoded; the player controls overlay is a hardcoded gradient
scrim in 4/5 apps (flat scrim in M3U). StreamVault's "transparent" is subtitle-background only. Peer panels
are **mostly flat single-colour**; gradients are reserved for hero banners + poster/player scrims.

Current Vivicast: transparency only fades `GlassPanel` + `InfoPanel` **fill** — invisible on the dark,
wallpaper-less theme, so the slider feels inert.

**Decision A — transparency target = player overlay + panels (owner).** Extend the transparency setting to
also drive the **player controls overlay** fill (`VivicastPlayer.kt`, currently NOT wired to
`LocalSurfaceOpacity`) — that's the visible, valuable target (video shows through the controls), and the
design doc already says *"Transparenz darf … Player-Overlays nicht unlesbar machen"* (so it was always in
scope, just never wired). Keep panels affected (subtle). Keep **dialogs opaque**. Add a **readability floor**
so the overlay never fully vanishes over bright video (doc requirement). Small, focused change; no doc
rewrite (doc already names Player-Overlays). **BUILT 2026-07-14** — `VivicastPlayer.kt` overlay fill now
`.scaledAlpha(LocalSurfaceOpacity.current.coerceAtLeast(0.5f))`; gates green; **verified on emulator**
2026-07-14 — with a live channel (ZDFneo HD) at 50 % transparency the video visibly shows through the
controls-overlay fill while title/timeline/buttons stay fully legible.

**Decision B — panel style = Material-3 tonal-flat (owner, 2026-07-14, after web research).** Web research
(m3.material.io, Android TV design, M3 Expressive 2025, Netflix/Disney/Prime, Apple Liquid Glass) → the
modern Google/Android norm is **tone-based surfaces**: *solid* tinted surfaces whose elevation is shown by a
**tone step** (`surface` → `surfaceContainerLow/High/Highest`), NOT gradients ("prefer tonal elevation, add
shadow only for separation"). Streaming apps use solid cards + gradients only for hero/text-scrims. Apple's
Liquid Glass is the counter-trend but needs blur + content-behind (little payoff on a static TV backdrop).

So: replace the per-panel **vertical gradient** with a **solid tonal fill** (elevation by lightness step:
backdrop < panel < card). Reuses the D17 re-hue band (each surface already maps to a tone of the hue — just
render it solid instead of a 2-3-stop gradient). Keep gradients ONLY for hero/featured panel, poster
placeholder scrims, and the player controls scrim/overlay.

**Concrete plan (awaiting GO):**
- Collapse each panel/card/row painter's gradient to a solid tone (pick the mid stop): `VivicastGlassPanel`
  (`VivicastSurfaces` 223-225), `VivicastDetailsPanel` (`VivicastPanels` 54), `VivicastContentCard`
  (`VivicastLayout` 58), `focusSurfaceBrush` idle/focus/selected (`VivicastSurfaces` 239-242).
- Tune the tone ladder so panel vs backdrop vs card are clearly separated when solid (nudge the surface
  literals / add explicit `surfaceContainer` levels if the current tones overlap too much).
- **Keep gradients (verified full inventory):** `VivicastHeroPanel` featured + scrims (`VivicastPanels`
  120/146/154), poster placeholders + artwork scrims (`VivicastCards` 139/141/155/167/259/273/285),
  `MiniLogo` channel-logo tile (`VivicastCards` 362/364 — media placeholder behind the logo image), brand
  logo (`VivicastNavigation` 231), player overlay (`VivicastPlayer`), and the **backdrop**
  `VivicastScreenBackground` (53/66) — **kept** as the subtle base-layer gradient (it's the wallpaper, not a
  panel; flattening it adds risk for no gain).
- Glassmorphism/Liquid-Glass = possible future opt-in "glass theme", not now.
- Gates + emulator visual check (panels should read as clean solid tonal surfaces, clear elevation).

**BUILT + verified 2026-07-14.** Flattened `VivicastGlassPanel` (`VivicastSurfaces`), `focusSurfaceBrush`
(idle/focus/selected → `SolidColor`), `VivicastDetailsPanel` (`VivicastPanels`), `VivicastContentCard`
(`VivicastLayout`) to solid tonal fills; removed now-unused `Brush` import in `VivicastLayout`. Gates green
(detekt + assembleDebug). Emulator: Settings rail + detail panels render as clean solid tonal surfaces (no
gradient sheen); hero + media/logo + player gradients preserved. Tone step vs backdrop is subtle (border
does most of the delineation) — nudge the panel tone if more elevation contrast is wanted. Uncommitted.

## Backdrop double-paint seam — fix (2026-07-14, pre-existing bug found during Decision B review)

Symptom: on every route, the top of the content area was a **darker band** than the shell backdrop above it
(a colour seam, most visible on saturated backgrounds, converging further down). Root cause: `VivicastScreen`
(used by all 6 routes) internally painted `VivicastScreenBackground`, but `MainActivity` **already** paints
one full-screen `VivicastScreenBackground` behind the top nav + routes. The nested backdrop restarts the
vertical gradient (darkest stop `0xFF02040A`) at the route's own top, so it's darker than the continuous
shell gradient at that y. Fix: `VivicastScreen` is now a plain layout `Box` (no backdrop) — the single shell
backdrop shows through continuously. **Verified on emulator** (Search screen, red bg) — backdrop now uniform,
no seam. Not caused by Decision B; app-wide.

## Not in scope (v1)
- Tinting is derived from **one base per hue**; hand-tuning individual surface tones per hue is a later knob.
- Grid-layout swatch picker (vertical list first).
- Light themes (explicitly out per PRD v1).
