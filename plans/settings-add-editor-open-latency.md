# Settings Add-Editor Open Latency — Overview lingers before the editor

> Status: **OPEN — reference only, not started.** Documented after the deep-link focus/flash fixes landed
> (fresh inner NavController + generic `SettingsEntryAction`). This file captures the *residual* cosmetic
> issue and the options, so we can pick it up later. No code to change from this file yet.

## Symptom

Home → **"Add playlist"** (no-playlist empty state) → deep-links into Settings/Playlists and opens the
add-provider editor. For a moment the **Playlists overview** (Add-playlist row + provider list) is visible
**before** the editor appears. Correct section (not a wrong-section glitch like the old General flash), but
it looks like a content swap. On the user's physical TV it lasts **longer** than on the emulator (slower CPU)
and is noticeably distracting.

Same class of delay applies when opening the editor **from the overview** (Add row / edit provider) and to
other heavy Settings sub-views — it's just most visible on the Home deep-link because it's unexpected there.

## Root cause (measured, not guessed)

Instrumented each destination's draw with `Modifier.drawWithContent { drawContent(); vcLog("frame"){…} }`
on `ProviderOverviewScreen` + `ProviderEditorScreen`, plus the existing `EditorScreen shown` log. Emulator,
debug build:

```
draw overview            ← Playlists overview (the inner-nav base) draws once
navigate -> editor       ← ~7 ms later (deep-link effect pushes PlaylistEditor)
draw editor              ← ~336 ms AFTER the navigate
```

So the overview is on screen ~**340 ms** = the time **`ProviderEditorScreen` takes to compose + first-draw**.
The overview is the inner-nav **base** beneath the editor and stays visible until the editor is ready to draw.

**It is NOT the transition/fade.** Re-measured with the inner `NavHost` transitions set to
`EnterTransition.None` / `ExitTransition.None` → gap unchanged (~343 ms). Fade reverted; it doesn't help here.

Why the editor is heavy to compose: `ProviderEditorScreen` builds the whole form eagerly — name, source
picker, URL/file/Xtream inputs, import checkboxes, **EPG-source link list**, user-agent row, plus it wires
`onGetProviderCredentials` / `onGetProviderM3uContent` (suspend). ~340 ms is exaggerated by the debug build
(no R8) + emulator; still slower on the user's TV.

## What is already fixed (context — do not redo)

- Deep-link **return focus** (Cancel/Save land on the Playlists overview with Add / new card focused).
- The **General flash** (retained inner-nav base rendered General under the Playlists title): fixed by making
  the inner `NavController` **fresh per Settings entry** (non-saveable `remember { NavHostController(...) }`)
  so it always starts on `startDestination` = the deep-linked section. See `feature/settings/.../SettingsRoute.kt`.
- Micro-opt kept: the deep-link add pushes `PlaylistEditor` **before** `awaitFrame` so the heavy editor
  starts composing 1–2 frames sooner. Small; does not fix the ~340 ms (compose-bound).

## Options (with honest trade-offs)

| # | Approach | Effort | Result |
|---|---|---|---|
| A | **Accept** (current) | — | Overview (correct section) briefly visible; time = editor compose time. |
| B | **Editor as inner-NavHost startDestination** for the add deep-link | medium + risk | Overview never drawn → shows rail + **empty** detail pane during the ~340 ms instead. Does **not** make the editor appear sooner (still compose-bound). Needs Cancel/Save to **navigate** to the overview instead of `popBackStack` (no `SecPlaylists` beneath) + detect "editor is the deep-link base" vs "opened from overview". Not clearly nicer (blank vs overview). |
| C | **Speed up `ProviderEditorScreen` composition** (the real lever) | larger, separate | Editor appears faster → less time seeing *anything* before it. Fixes the from-overview and edit-provider cases too. |
| D | **Progressive editor / skeleton** (a shape of C) | medium | Compose a cheap first frame instantly (name + source + URL), defer the rest (EPG link list, advanced rows, suspend loads) to after first draw. User sees the editor immediately; heavy parts fill in. |

### Recommended path when we tackle it

1. **Profile first** — don't guess which part is slow. Use the `android-performance` + `perfetto-trace-analysis`
   / `perfetto-sql` skills and Compose recomposition tooling (`compose-recomposition-performance`,
   `compose-stability-diagnostics`). Capture a trace of opening the add-editor; find the dominant cost
   (EPG-link list? eager suspend loads? unstable params causing extra work?).
2. **Then C/D** — most likely: defer the EPG-source link section + any credential/M3U loading off the first
   frame (load after `LaunchedEffect`, show a light placeholder), and/or split the form so the first
   composition is cheap. Target: editor first-draw within ~1–2 frames of the navigate.
3. Only if profiling says the compose cost is irreducible, consider **B** (editor-as-base) purely to swap the
   visible "overview → editor" for "blank → editor" — but treat it as cosmetic, not a real fix, and weigh the
   Cancel/Save-rework risk.

## How to re-measure (for whoever picks this up)

Temporarily add draw logs and read the timeline:

```kotlin
// on ProviderOverviewScreen(...) and ProviderEditorScreen(...) modifiers:
modifier = Modifier.fillMaxSize().drawWithContent { drawContent(); vcLog("frame") { "draw overview" } }
```
Then: fresh launch (0 providers) → Home → "Add playlist" → OK, and
`adb -s <dev> logcat -d -v time -s VCd`. The gap between `draw overview` and `draw editor` is the latency.
Remove the temp logs afterwards. (0 providers → Home shows the "Add playlist" button; get there with
`adb shell pm clear com.vivicast.tv`.)

## Constraints

- No new module, no DI migration. App-hoisted nav stays in `MainActivity`.
- New/renamed user-facing strings go to `:core:designsystem` (`values` + `values-en`) only.
- Keep the deep-link focus-return behaviour (Cancel → overview + Add focused; Save → overview + new card).
- Keep the `vcLog` tracing style for any measurement; strip temporary `drawWithContent`/frame logs before done.
