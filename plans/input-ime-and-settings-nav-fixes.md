# Plan: IME-on-OK, M3U-Label, Settings-RIGHT-Navigation

Status: **completed** (2026-07-17, committed to main). Three independent fixes + About legal-overlay follow-up.

## Implementation note (Task 1 diverged from the planned approach)

The planned "display box ↔ edit field swap" caused a focus-escape glitch on TV: removing the focused
display node from composition to mount the edit field let focus escape the surrounding editor (jumped to
Home in the inline editor / to Cancel in a dialog), and the IME never got stable focus. Replaced with a
**single BasicTextField toggled `readOnly ↔ editable`** so focus never leaves the node. Edit mode is
bound to the actual keyboard via `WindowInsets.isImeVisible`: OK (DPAD-center, caught by
`onPreviewKeyEvent`) enters edit mode + shows the IME; dismissing it (Done or BACK) returns to read-only
so the D-pad resumes navigating instead of moving the text cursor. A latch (`imeShownWhileEditing`) makes
the reset fire only after a real IME show — so it never trips on the initial hidden state or in a headless
test with no IME. User-verified on the device: focus opens no keyboard, click opens it, Return closes it,
Up/Down navigation works afterwards.

## Decisions (confirmed with user 2026-07-17)

- **Task 1 approach:** "Feld wie Button" — focus = highlight only; DPAD-OK enters edit mode + opens IME.
- **Task 1 scope:** all fields via `VivicastTextField`. Search stays untouched (it uses its own raw
  `BasicTextField`, not `VivicastTextField`, so it is already excluded — no opt-out param needed).
- **Task 2 label:** de „Lokale .m3u/.m3u8-Datei wählen…" / en „Choose a local .m3u/.m3u8 file…".
- **Task 3 fix:** `focusRestorer` on the detail focus group + drop the hard `right = detailFocusRequester`.

---

## Task 1 — IME opens only after OK

### Root cause
Every dialog/editor field routes through `VivicastTextField`
(`core/designsystem/.../VivicastInputs.kt:134`) → editable `BasicTextField`. On TV a focused editable
field auto-opens the leanback IME. Dialogs also auto-request field focus on open (`LaunchedEffect`,
e.g. `NameEditDialog.kt:90`, `ProviderEditor.kt:153`) → IME pops immediately.

### Change (single point: `VivicastTextField`)
Add an internal `editing` state to `VivicastTextField`:

- **Not editing (default):** the focus target is a focusable display surface with the same
  border/background/placeholder/value visuals as today. It is **not** an editable text field → no IME
  on focus. `focusRequester` (external param) attaches here, so dialog auto-focus + error-focus jumps
  land on it and only highlight.
- **DPAD-OK on the surface:** `editing = true`.
- **Editing:** render the real `BasicTextField`; `LaunchedEffect(editing)` requests its focus and calls
  `LocalSoftwareKeyboardController.current?.show()`. `keyboardActions.onDone`/`onAny` and
  `onFocusChanged { if (!focused) editing = false }` return to the display surface. BACK closes the IME
  → focus loss → `editing = false`.

Preserve all current params/behaviour: `label`, `placeholder`, `secret` + reveal toggle, `singleLine`,
`keyboardOptions`, `height`, `isError`, `maxLength`, `trailingAction`.

`keyboardOptions` gets `imeAction = ImeAction.Done` merged in (keep any caller-provided type) so the IME
shows a Done action to exit.

### Not touched
- `feature/search/.../SearchRoute.kt:207` — own raw `BasicTextField`, immediate IME kept (live search).

### Risk / verification
Focus + IME lifecycle is delicate and (per project memory) the leanback IME can't be driven by
adb-injected keyevents — **manual emulator test required**: open Provider-Editor, EPG-Source-Editor,
NameEditDialog, PIN dialog, Backup-passphrase dialog; confirm focus highlights without IME, OK opens
IME, Done/BACK closes and restores focus, error-focus jump still highlights the bad field.

---

## Task 2 — M3U button label

Rename string `settings_provider_file_pick` in **both** designsystem locale files (strings live only in
`:core:designsystem` per CLAUDE.md):

- `res/values/strings.xml:694` → „Lokale .m3u/.m3u8-Datei wählen…"
- `res/values-en/strings.xml:694` → „Choose a local .m3u/.m3u8 file…"

### Button width (measured estimate)
LabelMedium = 16sp SemiBold. New label ≈ 31 chars → text ≈ ~278dp + 2× Space4 padding ≈ **~310dp**
needed (English similar). Current pill is a fixed `Modifier.width(190.dp)` (`ProviderEditor.kt:604`) →
new label would truncate ~⅓. A bigger fixed number does not help: `ActionPill` internally clamps to
`widthIn(max = 300.dp)` (`VivicastInputs.kt:68`), so ~310dp gets squeezed to 300 and clips the „…".

**Fix (call site only, no shared-component change):** `ActionPill` already has `fillWidth` for exactly
this ("stretch to parent + show the full label instead of the 300dp content cap"). At
`ProviderEditor.kt:602` drop the fixed `.width(190.dp)` and pass `fillWidth = true` +
`Modifier.weight(1f)`. It stays in the Row with the 175dp `ConnectionTestButton`; the pick pill takes
the remaining detail-panel width (~470dp) → full label, one row, no ellipsis. Verify on emulator.

---

## Task 3 — Settings RIGHT navigation dies after scrolling

### Root cause
`detailFocusRequester` is attached to the **first** LazyColumn item via `firstFocusModifier`
(`SettingsRoute.kt:244`). Each rail item hard-targets it with `focusProperties { right =
detailFocusRequester }` (`SettingsRoute.kt:325`, `:355`). Scrolling the panel disposes the first item →
the requester node detaches → RIGHT becomes a no-op. All right panels are LazyColumn (confirmed).

### Change (single point: `SettingsRoute.kt`)
- Add `Modifier.focusRestorer { detailFocusRequester }` to the detail-panel `focusGroup`
  (`SettingsRoute.kt:382`). Restorer remembers the last focused child and restores it on re-entry;
  fallback = first row (still valid for first entry).
- Remove the hard `focusProperties { right = detailFocusRequester }` from the rail items
  (`:325` and the About item `:355`) so RIGHT does a normal focus search into the detail group, which
  the restorer intercepts and lands on the last-focused (visible) row.
- Keep `pendingDetailFocus` → `detailFocusRequester.requestFocus()` for section switches (LazyColumn is
  fresh at top after a switch, so the first item is present — unaffected).
- Verify `left = selectedSectionFocusRequester` on `detailFirstFocusModifier` and the `onExit`
  handlers still return LEFT to the rail correctly.

### Follow-up: About legal / technical sub-pages
The `focusRestorer` fix worked for every normal panel but NOT the About **Privacy Policy / Terms of Use /
Technical details** sub-pages: there RIGHT re-entry after scrolling landed on nothing visible. Root cause:
those overlays were **LazyColumn**s, so the first item — which carries `detailFocusRequester` (the
focusRestorer fallback) — was disposed once scrolled off, leaving the fallback detached (and the
underlying alpha-0 About list could catch the spatial search). Fix: `AboutLegalOverlay` /
`TechnicalDetailsOverlay` now use a **`Column` + `verticalScroll`** (few paragraphs, not perf-sensitive)
so every item stays composed → the first-item focus target never disposes → RIGHT re-entry lands on the
first paragraph (visible, scrolls to top). BACK-close (refocus the opening row) still works.
Emulator-verified on Privacy Policy: scroll → LEFT → RIGHT re-enters visibly; BACK returns to the row.

### Verification
Emulator: in Appearance/About scroll down, press LEFT to rail, press RIGHT → focus must re-enter the
panel on a visible row (not dead). Also test fresh section switch still lands on the first row.
Legal/technical sub-pages verified too (see follow-up above).

---

## Gates before done
`.\gradlew.bat detekt`, `:core:designsystem` + `:feature:settings` compile, `:feature:settings test`.
No commit / no push (per instruction).
