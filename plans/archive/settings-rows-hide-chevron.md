# Hide the "›" chevron on pure display-only settings rows

> Status: **COMPLETED** — shipped + verified on emulator, user-approved. One-file change in
> `VivicastInputs.kt` (`onClick` nullable + chevron gated on it); no call-site edits needed.

## Problem

`VivicastSettingsRow` draws the `›` chevron on every non-toggle, enabled row
(`VivicastInputs.kt:377-384`) regardless of whether the row does anything. On pure read-only info rows
(no action) the chevron misreads as "this is a button / leads somewhere". Most visible on the About
screen's version + technical-details rows.

## Fix (one file, root cause)

Make the click handler optional and gate the chevron + interactivity on it:

- `VivicastSettingsRow`: `onClick: () -> Unit = {}` → `onClick: (() -> Unit)? = null`.
- Chevron condition: `if (enabled && onClick != null)`.
- The focus surface already tolerates a null handler (`VivicastFocusSurface` does `onClick ?: {}`), so a
  display row stays **focusable** (D-pad can still scroll through it) but shows no chevron.

**Why one file is enough:** verified there is NO `VivicastSettingsRow` call passing an explicit
`onClick = {}` (grep across the repo — the only empty-lambda click is an `ActionPill` in PlayerRoute).
So every one of the 64 call sites auto-classifies correctly:

- Rows that omit `onClick` (currently defaulting to `{}`) → become `null` → **no chevron** (display-only).
- Rows that pass a real `onClick` (dialogs, toggles, sub-pages, actions) → **keep the chevron**.

No per-call-site edits needed. New default is also the safer one: a row with no action no longer looks
actionable.

## Affected (lose the chevron)

All `onClick`-less rows. Concretely visible: About screen — the merged App-version row and the six
Technical-details sub-page rows (Package name, Build type, Player engine, DB version, Android version,
Device model). Any other pure-info row elsewhere is auto-corrected too. Actionable/toggle rows unchanged.

## Scope notes

- Toggle rows (value "An"/"Aus") never drew the chevron anyway — unaffected.
- Focus highlight (value turns accent on focus) stays — it marks the focused row, not "actionable"; only
  the chevron was the misleading signal.
- A display row remains technically clickable (no-op, tiny press feedback). Making it fully inert (no
  press feedback) would be a larger change — **not doing** unless requested.

## Gates

- `detekt` + `assembleDebug` + `test` green. Watch the detekt baseline: the `VivicastSettingsRow`
  signature changes (`onClick` type), which may need a baseline signature sync if that function is
  baselined.
- Visual check on the emulator: About info rows show no chevron; actionable rows still do.
