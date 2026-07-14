# Plan: Font-Scale — popups follow the setting + drop "Very Large"

**Status:** ✅ COMPLETED. All edits applied; gates green (`assembleDebug`, `detekt`, `test`).
Emulator-verified: Font-size popup lists only Small/Medium/Large (no Very Large), and popup text now
scales with the setting (Small 0.90 vs Large 1.10 visibly different) — dialog stays opaque, hue colours
intact. (Research + doc-conflict resolved: owner chose "remove Very Large + update docs".)

Two coupled changes:
- **A.** Make popups/dialogs follow the font-size setting (currently they don't).
- **B.** Remove the "Very Large / Sehr groß" font size — keep Small / Medium / Large, Medium default,
  and set Large 1.12 → **1.10** (symmetric ±0.10 around Medium). Max factor then 1.10 — even less
  overflow risk. (Owner decision.)
- **C.** Update the design docs that normatively list 4 font sizes (owner approved doc edits).

⚠️ **Do NOT touch the Puffergröße (buffer) `ExtraLarge` — it is a separate enum with its own
`size_very_large` / "sehr gross" spec.** Only the FONT `ExtraLarge` is removed.

---

## A. Popups follow font scale (root cause + fix)

### Mechanism today
Font factor (Small 0.90 · Medium 1.00 · Large 1.10) applied once at composition root
(`MainActivity.kt:182-186`) by overriding `LocalDensity.fontScale`. Every `.sp` text
(`VivicastTypography`) rescales app-wide; `dp` geometry fixed. App-hoisted, correct.

### Root cause popups don't scale
All popups route through the single `VivicastDialog` (`core/designsystem/VivicastDialogs.kt:42`),
which uses `androidx.compose.ui.window.Dialog` (line 25/54) — a **separate window with its own
`AndroidComposeView`**. That view's `ProvideCommonCompositionLocals` **re-provides `LocalDensity`**
from the dialog window's platform density, shadowing the app-root override → inside dialogs
`fontScale` = platform default. Custom locals (`LocalVivicastColors`, `LocalSurfaceOpacity`) are
NOT in `ProvideCommonCompositionLocals`, so they cross the window boundary — which is why dialog
colours + transparency already follow the setting but font size does not.
(`MainActivity.kt:172` comment "incl. ... dialogs, which inherit the local" is the faulty assumption.)

Exactly ONE `window.Dialog` in the codebase; every popup goes through it → one-point fix. No raw
`Popup`/`DropdownMenu`/`AlertDialog`. Player panels (`PlayerOptionDialog`, `AutoNextPanel`,
`PlaybackErrorDialog`) are in-composition `VivicastContentCard`, already scale.
Out of scope (verified, not app chrome): Android `Toast` (OS font scale), TV global search /
WatchNext (system launcher), subtitle captions (video is a bare `SurfaceView` `PlayerRoute.kt:267`;
caption text, if any, is OS/Media3 caption style, not the app font setting).

### Fix (3 files, App-hoisted)
Carry the factor across the window boundary in a custom local (customs propagate; density doesn't).

1. `core/designsystem/VivicastTheme.kt` — next to `LocalSurfaceOpacity`:
   ```kotlin
   /** App font-scale multiplier, provided at the composition root. Custom local so it survives the
    *  Dialog/Popup window boundary (LocalDensity does not). */
   val LocalFontScale = staticCompositionLocalOf { 1f }
   ```
2. `app/MainActivity.kt` — provide it beside the density override:
   ```kotlin
   LocalFontScale provides preferences.appearance.fontScale.toFontScaleFactor(),
   ```
3. `core/designsystem/VivicastDialogs.kt` — inside `Dialog { ... }`, merge into the existing
   `CompositionLocalProvider(LocalSurfaceOpacity provides 1f)`:
   ```kotlin
   val density = LocalDensity.current               // dialog window's platform density
   CompositionLocalProvider(
       LocalDensity provides Density(density.density, density.fontScale * LocalFontScale.current),
       LocalSurfaceOpacity provides 1f,
   ) { VivicastGlassPanel(...) { ... } }
   ```
   New imports: `androidx.compose.ui.platform.LocalDensity`, `androidx.compose.ui.unit.Density`.

Both windows share the display Configuration, so `dialogDensity.fontScale * factor` reproduces the
root scaling exactly — popups match every other screen.

---

## B. Remove "Very Large" font size (code, migration-safe)

Backup + DataStore parse enums via `runCatching { enumValueOf<T>(...) }.getOrDefault/getOrNull`
(`DataStoreUserPreferencesStore.kt:243`, `StandardBackup.kt:286`), so a stale persisted/backed-up
`"ExtraLarge"` (old users who picked it) falls back to Medium. **No migration, no crash.**

Edits (FONT enum only — leave every buffer `ExtraLarge`):
1. `core/datastore/UserPreferencesStore.kt:111` → `enum class FontScalePreference { Small, Medium, Large }`
   (do NOT touch `BufferSizePreference` line 117).
2. `feature/settings/SettingsModels.kt:44-49` → remove `ExtraLarge` from `SettingsFontScale`
   (do NOT touch the buffer enum at line 110).
3. `feature/settings/SettingsPreferenceMappers.kt:61 & 69` → remove the two font `ExtraLarge`
   branches (keep buffer branches 94/103).
4. `app/SettingsPreferenceMappers.kt:147-148` → remove `FontScalePreference.ExtraLarge -> 1.25f`
   AND change `Large -> 1.12f` → `Large -> 1.10f` (keep buffer branch 239).
5. `feature/settings/AppearanceSettingsPanel.kt:140` → remove the `ExtraLarge -> font_very_large`
   label branch. (`options = SettingsFontScale.entries` at line 277 auto-drops it.)
6. Strings — remove `font_very_large` from `core/designsystem/res/values/strings.xml` +
   `values-en/strings.xml` (becomes dead; keep `size_very_large`, used by buffer).
7. `feature/settings/SettingsViewModelTest.kt:158,163` → font `ExtraLarge` → `Large`
   (leave the buffer test at 567/570).

All `when` blocks stay exhaustive after removal.

---

## C. Doc edits (owner approved) — remove FONT "Sehr groß" only

1. `design/design-system/02-design-tokens.md:156-179` — drop "Sehr groß" from the size list, drop
   "Sehr groß 1.25x" from the mapping, change `Groß 1.12x` → `Groß 1.10x`, change Grenze "Layouts
   dürfen bei `Sehr groß` nicht brechen" → "bei `Groß`".
2. `design/screens/07-settings.md:167-176` — remove "- sehr gross" from the **Schriftgröße**
   Auswahlwerte; change "Bei `sehr gross` dürfen Layouts nicht brechen…" → "Bei `gross` …".
   ⚠️ Do NOT edit 218-226 — that is the Puffergröße list ("aus/…/sehr gross", next-streamstart).
3. `design/design-system/04-focus-navigation.md:615` — "9. Schriftgröße Sehr groß prüfen." →
   "… Groß prüfen."
4. `design/design-system/05-screen-patterns.md:828` — "Schriftgröße Sehr groß berücksichtigt" →
   "Schriftgröße Groß berücksichtigt".

PRD unaffected: `06-data-model.md:1057` lists only the field "Schriftgröße" (no value enum).

---

## Validation after GO
- `.\gradlew.bat detekt assembleDebug test` (green).
- Emulator: font size only shows Small/Medium/Large, Medium default; open several popups (interval,
  choice/colour, PIN, passphrase) at each size — text scales and matches the underlying screen;
  dialogs stay opaque (transparency still 1f) and colours still follow the hue.
- Backup restore of an old container that stored `"ExtraLarge"` → loads as Medium, no crash.
