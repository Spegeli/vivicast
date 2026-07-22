# Live-TV — Ist/Soll Research + Plan

> Status: **PHASE 1 IMPLEMENTED + emulator-verified.** Adaptive 3-state columns
> ([K|S|P]/[S|E|P]/[E|P]) with LEFT-collapse, committed/selected split (Route-local `committedChannel`),
> P2 winner-aware per-channel current-programme batch + ~1min tick, active-favorites gate (A),
> logoConfigSignal (B), TV-Mate channel card (number/thin progress/minimal badges/★), EPG Live badge (R5),
> compact favorites (F1/F2) + No-EPG (F3), preview display-only follows focused EPG (P1/P3/F4/R1-3), column
> titles removed (S8), long-OK favorite (S5), winner-aware logos on all spots. Gates green (assembleDebug,
> testDebugUnitTest, detekt). Fixed a focus-loss bounce (collapsing the focused column on OK jumped to the
> top nav → Home): OK now commits + focuses the EPG, and the collapse to [E|P] happens on `onEpgFocused`
> once focus is safely on the EPG.
> **Phase-1 simplification:** the committed/selected split (Q1-B) is deferred to Phase 2 (it only pays off
> with real video: player on committed, EPG/preview on browsed). Phase 1 uses ONE channel identity — an
> `activated: Boolean` flag; the EPG, preview and fullscreen/BACK target all follow `selectedChannel`. This
> removed a divergence bug (EPG showed one channel while the preview showed another). Phase 2 reintroduces a
> separate committed channel for the video.
> **Emulator-clock caveat:** EPG shows "No program information" everywhere when the emulator clock sits
> outside the loaded programme window (0 current programmes). Not a bug — verified by pulling the DB (38507
> programmes, winner query returns the right "now" title). Enable `auto_time` / set the emulator clock into
> the EPG window to see live data.
> **Round-2 fixes (emulator-verified):** initial focus → first provider (not favorites/category); provider
> disclosure arrow ▸/▾ (was plain "v"/">"); removed the per-provider "Active" status line (inactive
> providers never appear here); preview column ~25% narrower; committed→activated model (bug fix).
> **Round-3 fixes (emulator-verified):** provider disclosure chevron now right-aligned, TV-Mate style (⌄
> collapsed / ⌃ expanded); provider row height wrap-content (was a fixed 52dp); category rows spaced (were
> flush); **preview now follows the focused channel while scrolling the channel list** — root cause was
> `selectedChannelIdFlow` never being collected, so focus changes only reflected on the next 60s tick; added
> a collector + made the auto-select guard fire on an invalid/null selection (not just a list change). The
> browse preview reads the focused channel's current programme from the per-channel batch (instant).
> **Round-4 fixes (emulator-verified):** column layout is now **focus-driven** — RIGHT from the category
> column into the channel list **collapses the categories → `[S|E|P]`** (was staying `[K|S|P]`); removed the
> `deepEpg`/`activated`-gates-layout coupling. **Preview is now non-focusable** (display-only) and the EPG
> column **swallows RIGHT**, so focus can no longer escape sideways to the top nav / Settings. Provider row =
> compact wrap-content + right-aligned ⌄/⌃ chevron; categories spaced; preview follows the focused channel
> instantly (batch). See the corrected focus-driven transitions below.
> **Round-5 fixes (emulator-verified):** LEFT out of the EPG now lands focus on the selected channel in the
> re-shown channel list even when it's scrolled out of view — the channel column keeps a hoisted
> `LazyListState` and `scrollToItem(selected)` before `requestFocus` (else the row isn't laid out and focus
> stayed stuck on the EPG). Also fixed a focus-steal: the channel column's focus effect keyed on
> `selectedChannelId`, so it re-grabbed focus on every auto-select (e.g. focusing a provider clears+re-picks
> the channel) — LEFT S→K bounced back to the top channel. Now it keys ONLY on the explicit
> `requestSelectedFocusSignal`. (Known follow-up: CH+/- zap-focus timing vs the async selectedChannelId — the
> signal may fire a frame before the VM updates; revisit if zap-in-list focus lags.)
> **Round-6 fix (emulator-verified):** EPG programme rows show the **description only in the wide `[E|P]`
> view** (focus in the EPG); the compact 3-column `[S|E|P]` view (focus in the channel list) shows just
> **time + title** to stay dense. Gated by the same `epgFocused` signal as the channel header.
>
> **PHASE 1 = COMPLETE + user-signed-off** (2-column and 3-column both approved). Deferred P1 polish (not
> blocking, tracked): retire now-unused strings (`livetv_epg_on_focus`, `livetv_preview*`, `livetv_cat_button`,
> `livetv_details_button`, `livetv_provider_label`, `livetv_now_label`, old badge strings); update
> `../vivicast-docs` (preview follows focused EPG / no action buttons / channel card + number / favorites via
> long-OK / no column titles / focus-driven collapse); emulator-verify long-OK favorite; CH+/- zap-focus
> timing; a couple of minor nav behaviours the user flagged as "not important yet".
>
> **Round-7 fix (emulator-verified):** UP at the **topmost channel** (`[S|E|P]`) and the **topmost EPG
> programme / sole no-EPG placeholder** (`[E|P]`) is now locked with `focusProperties { up =
> FocusRequester.Cancel }` on the first item only — it stays put instead of escaping to the top nav (which
> read as "jumps to Home"). Intra-list UP still moves between items; **only the Kategorien column top-UP
> escapes to the nav** (intended S7 behaviour, verified: lands on Home tab). DOWN stops were already correct.
>
> **Round-8 change (emulator-verified):** OK on a channel now **starts the preview but stays in `[S|E|P]`**
> (3-column) with focus in the channel list — the user browses channels with the video running. Only a
> deliberate RIGHT into the EPG collapses to `[E|P]` (2-column). Implemented by dropping the forced
> `epgFocusRequest += 1` from `onChannelClick`; RIGHT reaches the EPG via default focus traversal, and
> `EpgProgramRow.onFocused -> onEpgFocused` flips `focusedArea = Epg` (the collapse) exactly as before.
>
> **PHASE 2 (embedded live video, one connection) — DONE (Increments 1–3), emulator-verified.** Preview
> commits video on OK, seamless preview↔fullscreen handoff (no 2nd connection), committed≠selected split
> (Model B: video = committed, EPG/preview-info = focused), BACK fullscreens the streaming channel. See the
> Phase-2 section + the Increment-3 block below. Second of the UI pass (after Home).
>
> **CURRENT STATE (2026-07-19): all planned Live-TV behaviour is implemented + emulator-verified EXCEPT one
> open bug** — RIGHT from a category that sits below the channel rows needs two presses (jumps to the first
> provider first). Full write-up + failed approaches + next step in the "KNOWN OPEN BUG" section near the
> bottom. Four experimental fix attempts were made and all reverted; the code is back to the working baseline.
> Deferred non-blocking polish still open: retire unused strings, update `../vivicast-docs`, the CH+/- hard-zap
> semantic, and the close-fullscreen return-focus choice.
>
> **CURRENT STATE (2026-07-22): the Jetpack-Nav rebuild landed (player-as-destination + PlayerViewModel, commit
> `9660191`), then Live-TV was fully re-audited on the physical SHIELD (.12) with `[ltv]` `vcLog`
> instrumentation. 6 findings; bugs 1/2/3/6 are now FIXED + on-device-verified (pending user sign-off) — see
> "## Nav-Audit 2026-07-22" below. Gates green (detekt/test/assembleDebug). The KNOWN OPEN BUG
> (RIGHT-from-low-category) is RESOLVED: root cause was the content-Row `enter` override over-firing on
> horizontal moves; fix = direction-aware `enter` (Down only) + a deterministic `right =` from the K rows to
> the selected channel. Three minor polish items remain (provider-expand flash, provider-focus-with-favorites,
> Home DOWN focus). (4) and (5) intentionally unchanged.**

## Sources

- Soll: `../vivicast-docs/design/screens/02-live-tv.md` (v6),
  `design/interaction/01-live-tv-adaptive-columns.md` (v5), `design/wireframes/01-live-tv-browser.md` (v3),
  `prd/PRD-v1/02-live-tv-requirements.md` (v5), `design/components/list-grid-items.md`.
- Ist: `feature/live-tv/LiveTvRoute.kt` (RoomLiveTvRoute + RoomProviderCategoryColumn / RoomChannelColumn /
  RoomEpgColumn / RoomPreviewColumn), `LiveTvViewModel.kt`, `LiveTvUiState.kt`.

## Model (Soll = Ist, both modes)

Adaptive 3-column, two modes:
- **Kategorie-Modus:** `[Globale Favoriten / Provider / Kategorien] | [Senderliste] | [Vorschau/Details]`
- **Sender-Modus** (after OK on a channel): `[Senderliste] | [Sender-EPG] | [Vorschau/Details]` (the
  provider/category column is hidden).

Current widths: category mode 25% / 33% / 42%; channel mode 32% / 31% / 42%.

## Ist — what's implemented (and matches the docs)

- **Modes** `LiveColumnMode.Category/Channel`, **focus areas** `Provider/ChannelList/Epg/Preview`.
- **Start:** first provider auto-expanded, focus on its first category, global favorites item above the first
  provider (cross-provider). Only **active** providers are browsable (disabled filtered in the VM).
- **Provider/Category column:** favorites item, provider rows (`v`/`>` expand + status label/colour),
  categories under the expanded provider, "Nicht kategorisiert" fallback, empty/error info panels.
- **Channel list:** `VivicastChannelCard` (informationsreich: logo + name + program line + progress +
  favorite + catch-up badge). Focus a channel → preview column reflects it (no video yet). Load-more pill.
- **OK on a channel** → Sender-Modus: preview starts, EPG column appears, focus jumps to the **current EPG
  program** (or the **No-EPG placeholder** when none). CH+/- move channel focus.
- **EPG column:** past/current/upcoming rows (time, title, description, current + catch-up badges); OK on the
  current program → fullscreen; OK on a catch-up-ready past program → catch-up player. **No-EPG placeholder**
  is a focusable FocusPanel; OK → fullscreen **without** catch-up. (All doc-conform.)
- **Preview/Details column:** preview box ("OK starts preview" / "Preview running"), a **[Live][Cat.][★]**
  action-pill row, an info panel (channel name, provider, now/next), and a provider-error panel.
- **BACK chain:** Epg/Preview → channel list → category mode (provider focus) → (top nav). CH+/- as spec.
- **No-EPG / empty / error / provider-status** states present. EPG is loaded **only for the selected channel**
  (lazy rule honored).

## Deviations & open points (Ist vs Soll / polish)

- **P1 — Preview action pills not in the doc.** The doc's Vorschau/Details lists logo/preview, name,
  program+time, description, next, stream status — **no action buttons** ("ein separater EPG-Button ist nicht
  notwendig"). Ist adds **[Live]** (→ fullscreen), **[Cat.]** (→ back to category mode), **[★]** (→ favorite),
  plus a "Details" badge. Keep / simplify / remove? (The [Live] fullscreen + ★ favorite may be worth keeping;
  [Cat.] duplicates BACK.)
- **P2 — "EPG on focus" text on non-selected channel cards.** Because EPG loads only for the selected channel
  (lazy), every other card's program line shows the literal hint `livetv_epg_on_focus`. Doc's channel card
  wants the current program per channel, but the lazy rule forbids loading all EPG. Options: keep the hint /
  blank it / show program only for channels whose EPG is already cached / load a light "now" per visible
  channel. Likely a visual-clutter complaint.
- **P3 — "OK starts preview" on the preview box.** In category mode, focusing the preview box + OK starts a
  preview separately from the channel-OK flow. Doc starts preview only via OK on the channel. Redundant?
- **P4 — Column widths / preview size.** Preview is 42% (large); provider column 25%. Not doc-specified —
  confirm the balance is what you want.
- **P5 — (to confirm with user) any concrete visual issues** — like the Home channel-card height — that the
  user has noticed and wants fixed. User to enumerate.

## Navigation scenarios (current behavior — to confirm / adjust)

1. **Fresh open, 1 active provider:** provider expanded, focus first category, channel list = that category,
   favorites item above. Preview shows first channel (no video).
2. **Focus another category / global favorites:** channel list updates instantly (favorites = cross-provider).
3. **Multiple providers:** each collapsible; focusing a collapsed provider selects it; OK expands. Only the
   selected+expanded provider shows its categories.
4. **LEFT/RIGHT:** LEFT from channel list → provider/category column; focus a category → list updates;
   (channel mode) RIGHT channel list → EPG → preview, LEFT back.
5. **OK on channel → Sender-Modus:** provider column hides, EPG appears, preview starts, focus = current EPG
   program. Second OK on current program → fullscreen.
6. **No current EPG:** Sender-Modus opens, focus = No-EPG placeholder, OK → fullscreen (no catch-up).
7. **Catch-up:** OK on a past catch-up-ready program → catch-up player.
8. **CH+/- in browser:** move channel focus (preview resets); **in player:** switch channel.
9. **BACK:** steps back EPG/Preview → channel list → categories → top nav.
10. **Edge:** empty category / no providers / disabled provider (hidden) / provider error (status + error
    panel) / no logo (fallback) / no EPG (placeholder).

## Decisions (user) so far

- **P1 — Preview = display-only.** Remove the `[Live] [Cat.] [★]` action-pill row **and** the "Details"
  badge. Preview shows only: preview/logo surface, channel name, current program + time, description, next
  program, stream status (doc-conform).
- **P2 — EPG for all visible channels.** Show the **current program per channel** in the list (not just the
  selected one). Overrides the lazy "EPG only for selected channel" rule. **Needs new data:** a "current
  program for a set/page of channels" query (`EpgRepository` + DAO), fed into the channel list. Drop the
  `livetv_epg_on_focus` hint entirely.
- **P3 — Remove "OK starts preview".** The preview box is display-only; preview starts **only** via OK on a
  channel (the doc flow / Sender-Modus).
- **Favorites toggle = long OK press** on the focused channel (short OK = normal channel-OK / Sender-Modus).
  Replaces the removed `[★]` pill. Implementation: a long-press handler on the channel card / route
  (KEYCODE_DPAD_CENTER long-press, like MainActivity's long-BACK), calling `viewModel.onToggleFavorite()` for
  the focused channel. Short vs long press must not conflict with the existing channel-OK.

## User UI issues (from screenshots)

- **F1 — "Live-TV Favoriten (N)" item too tall.** The global-favorites entry stretches; make it a compact
  fixed height (same class as the Home channel-card stretch fix — bounded height on the panel).
- **F2 — Left column flattened (decided; superseded by S8 re the title).** **No** column title (S8 removes
  all generic column titles). First entry = **"Live-TV Favoriten (N)"** (compact), then per provider its name
  (expandable) → its categories. **Remove** the separate "Favoriten" / "Provider" section sub-headers too.
- **F3 — No-EPG placeholder too tall.** The "Keine Programminformationen verfügbar" FocusPanel fills the whole
  EPG column; make it a compact fixed height (bounded), not full-column.
- **F4 — Preview: drop the "Provider: {name}" line.** The preview info panel should not show the provider
  name (keep name + now/next + stream status).

## Adaptive column visibility (progressive collapse) — decided

Supersedes the fixed "2 modes" layout. Columns: **K** = Kategorien, **S** = Senderliste, **E** = Sender-EPG,
**P** = Preview (always rightmost + visible). The visible left-of-preview columns depend on **focus +
activation** — deeper focus collapses the left context to give the focused list more width (TV-Mate style).

Three layout states:
- **`[K | S | P]` — Browse** (no channel activated). Focus in K or S. Preview shows the focused channel's
  current-program info (no video yet).
- **`[S | E | P]` — Sender-Modus, focus in the channel list.** Channel activated (video + EPG). Channel
  list + its EPG + preview.
- **`[E | P]` — Sender-Modus, focus in the EPG.** The channel list collapses; **E gets the full left width**
  and shows a **channel header** on top (logo + "Nr Name" + provider • category) so the user knows which
  channel's EPG they're scrolling. Preview on the right.

**Activation persists.** Once a channel is activated (OK), the preview keeps running as you navigate columns
(K/S/E) — it stops only on switching channel (zap/OK another) or leaving Live-TV. So `[K|S|P]` can show a
running preview of the previously-activated channel.

**Layout is FOCUS-DRIVEN (corrected — supersedes the earlier "activated-gates-the-columns" text).** The
preview is **non-focusable** (display-only), so focus is only Provider/ChannelList/Epg, and visibility is a
pure function of it: `showK = focus==Provider`, `showS = focus!=Epg`, `showE = focus!=Provider`. So:
- focus **K** (Provider) → `[K|S|P]`.
- focus **S** (ChannelList) → `[S|E|P]` (K collapses, the focused channel's EPG appears — no OK needed).
- focus **E** (Epg) → `[E|P]` (S collapses). `activated` (a channel OK'd) is orthogonal — it only drives
  BACK→fullscreen, not the layout.

Transitions:
- **RIGHT:** `K` → `S` (**collapses K → `[K|S|P]`→`[S|E|P]`**); `S` → `E` (`[S|E|P]`→`[E|P]`); `E` → **swallowed**
  (preview non-focusable — RIGHT must NOT escape to the top nav).
- **LEFT** = column-back: `E` → `S` (`[E|P]`→`[S|E|P]`); `S` → `K` (`[S|E|P]`→`[K|S|P]`); `K` → stop (leftmost).
- **OK on a channel** (focus S) → activate + focus the EPG → **`[E|P]`**, current EPG program (or No-EPG
  placeholder). Phase 2: video starts.
- **CH+/- zap (Sender-Modus):** switch channel → E (header + programs) + preview follow; layout stays
  whatever it is (`[E|P]` or `[S|E|P]`).
- **OK on current EPG program / No-EPG placeholder** → fullscreen. **OK on future program** → no-op.

**BACK / Return (S6, decided):**
- **Preview running (channel activated):** BACK from **any** column → **fullscreen player** of the running
  channel. (BACK does NOT step back columns — LEFT does that.)
- **No preview (browse, not activated):** BACK → **Top Navigation**.
- **Reaching Top Nav while a preview runs:** LEFT to the Kategorien column → UP to the topmost item
  (Favoriten) → **UP** → Top Nav. (The only up-escape; see UP/DOWN below.)

**UP / DOWN — no wrap, stop at ends (S7, decided). Reference: Settings already implements this stop.**
- **Kategorien:** bottom + DOWN → stop; **top + UP → Top Nav** (the up-escape).
- **Senderliste:** top + UP → stop; bottom + DOWN → stop (paged load-more still at the end).
- **Sender-EPG:** top + UP → stop; bottom + DOWN → stop.
- (Preview: focusable panel; UP/DOWN within if it has sub-elements, else inert.)

**Column header titles (S8, decided):**
- **Remove** the generic per-column titles: "Favorites"/"Kategorien", "Channel list"/"Senderliste",
  "Preview". User already knows which column is which; removing them reclaims vertical space. (Today they're
  `SectionTitle`s at the top of each column composable — RoomProviderCategoryColumn, RoomChannelColumn,
  RoomEpgColumn, RoomPreviewColumn.)
- **Keep** the channel header inside `[E|P]` (logo + channel name + provider/group line) — that one is
  informational (tells you which channel the wide EPG belongs to), not a generic column label.
- The Kategorien column's internal "Favoriten" / "Provider" sub-headers are **already removed** per F2 — so
  the left column is just: compact favorites entry, then providers → categories, no labels at all.

**Typography (S9, decided):** keep the current text sizes for channel name / EPG description / preview /
etc. as-is for the rebuild; refine (bigger/smaller per element) in a later polish pass once the layout is in
place.

**Q1 — committed (playing) channel vs focused (browsed) channel (DECIDED: B).** Today the VM has ONE
`selectedChannelId` that drives BOTH the EPG column AND the preview. The activation-persists model splits it:
- **Decided (B):** preview **video** stays on the **committed** channel; the **EPG column (E) + preview
  info** follow the **focused** (browsed) channel; **OK** commits the focused channel (video switches, →
  `[E|P]`); **CH+/-** zaps committed+focused together. Scrolling the list does NOT zap (channel surfing is
  CH+/- only, so playback isn't restarted on every focus move).
  - **Code impact (in scope):** VM gains a `committedChannelId` (drives video / fullscreen / BACK target)
    separate from `selectedChannelId` (drives E + preview info). The full-EPG `observeProgramsForChannel`
    keys off `selectedChannelId` (browsed), so E already follows the focus. `playerController.play` /
    fullscreen / BACK use `committedChannelId`. CH+/- sets both. OK sets `committedChannelId = selected`.
    Leaving Live-TV / switching commit clears the old preview.
- Rejected (A): scrolling re-commits/zaps immediately (restarts playback every row).
- Rejected (C): E + info stay on the committed channel while the highlight moves (highlight ↔ EPG
  disconnected, confusing).

Not in scope now: TV Mate's multi-day **date sidebar** in the EPG (the screenshot shows one) — our EPG stays
a rolling window (past+future). Flag as a separate future feature if wanted.

**Defaults taken (no question needed, stated for the record):**
- Preview surface in Browse `[K|S|P]` with nothing committed (after removing "OK starts preview" text, P3):
  show the channel **logo** centered on a neutral surface (no video). Video appears only once committed.
- `[E|P]` 2-column widths: wide E + preview (~62/38), tuned on-device like the 3-column weights.
- Focusing the top nav via the UP-escape does **not** stop the preview; the preview stops only on an actual
  route change (Home/Movies/…) or switching channel. (Top-nav focus keeps you "on" Live-TV.)
- **CH+/- at list ends:** clamp to the loaded list (no wrap), consistent with S7; at the paged end it may
  trigger load-more rather than stop.
- **Long-OK favorite** applies only when focus is in the **channel list** (S). In `[E|P]` (focus on EPG rows)
  long-OK is inert.

## Reference: TV Mate (visual target, not 1:1) — decided

User shared TV Mate screenshots as the look they want. Adopted:

- **R1 — Preview info follows the FOCUSED EPG program** (not always the current one). The preview **video**
  stays on the current/playing channel; the **info** below (title + time + progress + description) reflects
  whichever EPG row is focused in the Sender-Modus EPG column. Navigating down the EPG updates the info while
  the video keeps playing. (Deviates from the doc's "preview shows current program" → update the doc.)
  - In Kategorie-Modus (no EPG column) the preview info shows the focused channel's **current** program.
  - Needs the Route to track the **focused EPG program id** and feed it to `RoomPreviewColumn`.
- **R2 — Preview shows the program description** (the longer text), under title + time.
- **R3 — Time/progress line:** `HH:MM — HH:MM` + a small progress bar (when the shown program is live) +
  remaining/duration (e.g. "38 Min"). Adopt this format in the preview info.
- **R4 — Channel card simplified toward TV Mate:** **channel number** + logo (left) + name + current program
  + a **thin progress bar**; **minimal badges** — drop the always-on "Live" badge; keep a subtle favorite
  indicator (★ when favorited) and a catch-up icon when available. (Channel number is currently NOT shown —
  add it. Doc: "Sendernummer, falls vorhanden".)

Combined with the earlier decisions, the preview column loses the `[Live][Cat.][★]` pills + "Details" badge
(P1) and the "Provider:" line (F4); preview starts only via channel-OK (P3); favorites toggle = long OK (R4
keeps only a visual indicator, no toggle button).

## Spacing & column widths (decided)

Root of the wasted space: each column is a `GlassPanel` box (rounded border + `Space4`=16dp inner padding),
plus the `Space4`=16dp inter-column gap, plus the cards' own padding → **double padding** (box + card).
Screen-edge safe area is 48dp/side (`ScreenHorizontal`) — keep for TV overscan.

- **Approach A first (trim), B later if still boxy.** Reduce the inter-column gap (16→8) and the per-column
  `GlassPanel` contentPadding (16→8), and avoid double padding (card padding vs box padding). Keep the column
  boxes for now. If it still feels too boxy, switch to **B**: drop the per-column `GlassPanel` borders →
  flat columns with a subtle vertical divider (TV-Mate style, max usable width).
- **Rebalance widths.** Current 25 / 33 / 42 → give the channel list more room, trim the preview a bit
  (e.g. ~22 / ~40 / ~38 — exact values tuned on-device). Category mode vs channel mode weights both revisited.

## Card / EPG refinements (decided)

- **R4a — Channel number in the NAME line** (before the name: "1 3sat HD"), **not** before the logo. Card:
  `[LOGO]  1 3sat HD  ★` / current program / thin progress bar.
- **R5 — Persistent "Live" badge in the EPG column** on the **currently-airing** program (by clock time),
  independent of focus. Scrolling the EPG keeps showing which program is live now. (Focus highlight is a
  separate visual from the Live badge; on entry they coincide on the current program.)

## Scenario walkthrough — behaviours to confirm/decide

Confirmed-OK scenarios (current behaviour matches intent): fresh open (first provider expanded, first
category focused, EPG-for-all shows current program per card); focus category → list updates + auto-select
first channel + preview info = that channel's current; global favorites (cross-provider; empty → "Keine
Sender"); OK on channel → Sender-Modus (video + focus current EPG / No-EPG placeholder); BACK chain
EPG/Preview → channel list → Kategorie-Modus → top nav; deep-link target focuses the target program;
fullscreen player is an overlay so Live-TV state persists on return; disabled providers hidden; provider
error shown in tree + preview error panel.

**Decisions (user):**
- **S1 — OK on a FUTURE EPG program = no-op.** Fullscreen only from the **current** program or the No-EPG
  placeholder. Future rows are info-only (drive the preview info but no action). Catch-up-ready past rows
  still start catch-up.
- **S2/S3 — Zap in Sender-Modus.** CH+/- switches the selected channel from **anywhere** in the browser
  (EPG + preview follow the new channel; the preview keeps running / re-starts for it). Implementation:
  CH+/- re-runs the channel-selection flow (new channel → new EPG, focus its current program, preview
  switches). In Kategorie-Modus CH+/- just moves the channel selection (preview not started there).
- **S4 — Periodic refresh (~1 min).** Re-evaluate each channel's current program + the EPG "Live" badge on a
  ~1-minute tick (from already-loaded EPG — cheap, no extra query unless the window rolls). Keeps the current
  program / Live badge accurate across program boundaries.
- **S5 — Favorite long-OK** (implementation): long-OK on the focused channel toggles favorite, short-OK opens
  Sender-Modus; press-duration guard so they don't collide. Un-favoriting the last item in the favorites
  category → it leaves the list (auto-select next).

## Implementation sketch (on GO — no code before)

**Data (`:data:epg` + `EpgDao` + `LiveTvViewModel` → `LiveTvUiState`):**
- **Current program per visible channel** at `now` (P2/R4) — a batched "current program for these channel
  ids" query; expose as `Map<channelId, EpgProgram?>` so each channel card shows its own current program
  (drops the `epg_on_focus` hint). Loaded for the visible/paged channels, not the whole library.
  - **MUST be winner-aware** (EPG-source priority). The existing single-channel `observeProgramsForChannel`
    (`EpgDao`) already picks ONE source per channel via `AND p.epgSourceId = (SELECT … ORDER BY m.isManual
    DESC, pes.priority ASC LIMIT 1)` (manual mapping wins; else lowest `priority` value among active linked
    sources that matched the channel). The new batched query replicates that subquery but **correlated**
    (`m.channelId = p.channelId`, `m.providerId = p.providerId`) so the winner is picked per channel, then
    `startTime <= now AND endTime > now`, one row per channel. Without this a channel mapped by 2 sources
    shows a wrong/duplicate current program. ~80 channels × a correlated indexed subquery = cheap.
- **Winner already applied elsewhere (no work):** the full-EPG column (E), the current-program highlight, the
  Live badge (R5), and the preview info (R1) all read from `selectedPrograms` = `observeProgramsForChannel`,
  which is already winner-filtered — so a single source's schedule shows, no cross-source duplicates. (Note:
  winner-aware *search* is a separate queued task and does not affect Live-TV display.)
- **Focused-EPG program** (R1) — the Route tracks the focused EPG row id; the preview info renders that
  program (title/time/description/progress). In Kategorie-Modus it falls back to the focused channel's current.

**Left column `RoomProviderCategoryColumn` (F1/F2):**
- Title = **"Kategorien"**. First entry = compact **"Live-TV Favoriten (N)"** (bounded height — no stretch).
  Then per provider: name (expandable) → categories. Remove the "Favoriten"/"Provider" sub-headers.

**Channel card (R4, designsystem):** a Live-TV variant — channel **number** + logo (left) + name + current
program + **thin progress bar**; **no "Live" badge**; keep ★ (favorited) + catch-up icon. Long-OK toggles
favorite (short OK = Sender-Modus). Number is new — read from `Channel.channelNumber`.

**EPG column `RoomEpgColumn` (F3):** the No-EPG placeholder gets a **bounded compact height** (not
full-column). Rows report their focused program id up to the Route (drives the preview info, R1).

**Preview column `RoomPreviewColumn` (P1/P3/F4/R1/R2/R3):** display-only — preview surface (starts only via
channel-OK) + **focused-EPG-program** title + `HH:MM — HH:MM` + progress bar (if live) + remaining/duration
+ **description**. **Remove** the `[Live][Cat.][★]` pills, "Details" badge, "Provider:" line, and the
`onStartPreview` focus path. Keep the provider-error panel.

**Logos MUST use the winner-aware resolver (not `channel.logoUrl`).** `Channel.logoUrl` from the VM flow is
the DB `effectiveLogoUrl` — **order-agnostic** (playlist-if-present, else mapped EPG icon); it does NOT honor
the provider's `logoPriority`. The winner-correct logo comes from the App-side `resolveChannelLogoModel`
(`PlaybackOrchestration`) which walks `parseLogoPriorityOrder(provider.logoPriority)` over [Playlist, Epg,
Local] and takes the first hit. It is already passed into `LiveTvRoute` and used by the channel card
(`produceState { resolveChannelLogoModel(channel) }`, winner-correct today). The **two new logo spots must
route through the same resolver**, keyed like the card:
- `[E|P]` **channel header** logo → `resolveChannelLogoModel(selectedChannel)` (E follows the focused/selected
  channel per Q1-B).
- **Preview surface** logo placeholder in Browse (nothing committed) → `resolveChannelLogoModel(selectedChannel)`.
- Reuse the card's missing-logo fallback pattern (`channel.logoUrl.isNullOrBlank() && logoModel == null` →
  text/monogram). No new query — `getChannelLogoCandidates` + the resolver already exist.

**Favorites:** long-OK on the focused channel → `viewModel.onToggleFavorite()` (KEYCODE_DPAD_CENTER
long-press, guard vs short-OK). Card shows ★ state only.

**Strings:** retire unused (`livetv_epg_on_focus`, `livetv_cat_button`, `livetv_details_button`, preview
`[Live]`/`livetv_provider_label` if unused); add new (channel-number, remaining/duration, etc.) to `:core:
designsystem` (de+en).

**Docs:** update `02-live-tv.md` / `01-live-tv-adaptive-columns.md` / `01-live-tv-browser.md` /
`list-grid-items.md` for: preview info follows focused EPG selection, preview has no action buttons, channel
card simplified (+ number, minimal badges), favorites via long-OK, left column titled "Kategorien".

**Gates:** `detekt` (LiveTvRoute + RoomLiveTvRoute/Column are baselined — regen baseline if signatures
change), `assembleDebug`, `test`. On-device/emulator check: both modes, per-channel current program, preview
follows EPG focus, favorite long-press, compact favorites item + no-EPG placeholder.

## Crossover / multi-link validation (deep pass — providers × EPG sources × logos)

Mapped the full data model (provider↔EPG many-to-many via `provider_epg_sources.priority`; per-channel
mappings auto+manual; logos playlist/EPG/local). Findings that shape the rework:

**Confirmed correct / by-design (no work):**
- **EPG winner is per-(provider, channel)** (`EpgDao.kt:67-78`, winner INNER-JOINs `provider_epg_sources`
  filtered by `m.providerId`). A shared source can win under one provider, lose under another. Never cache a
  global winner. Matters for the multi-provider favorites list.
- **No fallback when the winner has a gap:** if the winning source has no programme in the window, the
  channel shows **empty** — it never drops to the next source (`EpgDao.kt:56-58`). Existing behavior; the
  card's blank/"No program" inline already handles it. P2 inherits this (blank current-program cell is
  correct, not a bug).
- **HD/SD (#31):** several local channels sharing one tvg-id each get identical programme rows by design
  (`RoomEpgRepository.kt:236,458-467`). The list legitimately shows the same "now" on both — do NOT dedupe.
- **Logo-icon pick is also winner-aware** and mirrors the programme winner (`CatalogDao.kt EPG_ICON_SUBQUERY`
  — same `ORDER BY m.isManual DESC, pes.priority ASC, s.isActive=1`). One extra filter (`iconUrl != ''`
  before LIMIT 1) means when the programme-winner source has no `<icon>`, the logo falls through to a
  lower-priority source's icon (intentional, #33 "better a logo than none"). Pre-existing; not our concern.

**Plan refinements (recorded, no user decision):**
- **P2 batch current-program query keys on (providerId, channelId) PAIRS**, not one providerId + a channel
  list — because the global-favorites list spans multiple providers (`FavoritesRepository.observeFavorites`
  is provider-agnostic; VM resolves each via `getChannel`). Replicate the winner subquery **correlated**
  (`m.providerId=p.providerId AND m.channelId=p.channelId`) **and keep `s.isActive=1`** (a merely
  *deactivated* EPG source keeps its programme rows on disk — only the isActive filter hides them; a batch
  query that joins `epg_programs` straight would resurface them).
- **Committed↔Selected split (Q1-B) — exact touch-points to fork from the single `selectedChannelFlow`:**
  - **Selected (browsed):** EPG column (E), preview INFO, inline per-card current program, favorite toggle
    target (`onToggleFavorite`), the `[E|P]` channel header.
  - **Committed (playing):** preview VIDEO, fullscreen/open-player target, BACK-to-fullscreen.
  - `observeProgramsForChannel` keys off **selected** (E already follows focus). `playerController.play` /
    fullscreen / BACK use **committed**. OK sets committed = selected. CH+/- sets both.
  - Note: P2 removes today's "inline current-program only on the selected row" limitation
    (`LiveTvRoute.kt:508-509`) — every card gets its own current program from the batch query.

**Pre-existing, OUT OF SCOPE for this UI rework (flagged, not fixed here):**
- **Android TV system-search index uses the RAW `channels.logoUrl`** (`AndroidTvSearchDao.kt:73`) — ignores
  EPG/local/priority. A separate DAO/surface, not a Live-TV screen. Leave.
- **Logos/EPG-icons load with the GLOBAL User-Agent** (shared Coil loader), never a provider's UA override
  (`AppContainer.kt:284-295`). Only playback/probe/import use per-provider UA. Image-infra concern, not this
  rework.
- **`produceState` logo key `(channel.id, channel.logoUrl)` doesn't include `logoPriority`** — reordering a
  provider's logo priority while the Live-TV list is on screen won't refresh already-drawn logos until
  re-entry (`LiveTvRoute.kt:505`). See open point B below.
- **Favorites count vs star-set** can diverge for a favorite whose channel row vanished on refresh (star set
  keeps the id, list drops it) — pre-existing minor.

**Decided (user):**
- **A — HIDE deactivated-provider favorites (consistent with the tree).** Gate the global-favorites gathering
  by active provider: in `LiveTvViewModel.rebuild()` / the favorites collector, keep only favorites whose
  provider is active — `favoriteChannels = favs.mapNotNull { getChannel(...) }.filter { ch -> providersRaw.any
  { it.id == ch.providerId && it.isActive } }` (or gate before the `getChannel` call). Reactivating brings
  them back (favorites rows are untouched — only display is gated). Filter the star set + count the same way so
  `favoriteChannelCount` stays consistent. Eventual-consistency note: favorites may emit before `providersRaw`
  is populated → a rebuild with an empty active set drops them for one frame, then the providers emission
  rebuilds — harmless (both paths call `rebuild()`).
- **B — FIX the stale-logo-on-reorder now (cheap).** The `produceState` logo key must change when a provider's
  `logoPriority` changes. Add a single `logoConfigSignal: Int` to `LiveTvUiState` (e.g.
  `providersRaw.joinToString { it.id + ":" + it.logoPriority }.hashCode()` — bumps only when the set or any
  logoPriority changes), and include it in every logo `produceState` key: `produceState(channel.id,
  channel.logoUrl, logoConfigSignal) { resolveChannelLogoModel(channel) }` in the channel card, the `[E|P]`
  header, and the preview placeholder. Reordering priority then re-resolves logos immediately, no re-entry.

## Embedded live-video preview (DECIDED: real video, ONE connection) — architecture + phasing

The preview column today is a **stub** (`PreviewBox` = gradient box + text; `previewStarted` is a bool; NO
ExoPlayer). User wants a **real live-video preview** like TV Mate, with a hard constraint: **never open a
second connection** (many providers cap at one). Player-architecture research confirms it's feasible with the
existing single player:
- **One App-hoisted ExoPlayer** (`AppContainer.playerController`, singleton) — never per-route.
- **Video output = detachable `SurfaceView`** via `attachVideoSurface`/`detachVideoSurface`
  (`VivicastPlayerController`), fully decoupled from `setMediaSource`/`prepare`. Re-attaching a surface does
  **not** reconnect/re-buffer (the engine already re-attaches its surface across a player rebuild).
- **Fullscreen is an overlay** (`if (playerVisible) PlayerRoute(...)` in MainActivity) — the Live-TV
  composable stays alive underneath, so an embedded preview survives the fullscreen overlay opening.
- **One stream at a time** already guaranteed: `play()` does `stop()` (clears media) before `start()`.

**Model with video:**
- **Committed channel** = the one the single player streams (set on OK / CH-zap). Preview `SurfaceView`
  renders it. Exactly one connection.
- **Selected/focused channel** = browsed; drives EPG column + preview INFO only — **does NOT stream**
  (respects the one-connection cap; browsing never opens a stream).
- **OK on a channel** → commit → `play()` it → video in the preview + enter `[E|P]`. Re-OK same channel = just
  move focus.
- **BACK (committed)** → re-parent the surface to the fullscreen overlay, **no new `play()`** (same stream);
  BACK from fullscreen → re-parent back to the preview (still playing).
- **CH+/-** → commit+`play()` the new channel (stop-old/start-new = still one connection).
- **Leaving Live-TV (tab/route change, not fullscreen)** → stop the player (release the connection).
- **Audio:** preview plays audio (it's the committed channel you chose — tuner-like, matches TV Mate). Default
  ON. (No mute path exists today; add `setVolume` only if we later want a muted preview.)

**The hard part — release-on-leave rework.** Today the player stops on `PlayerRoute` dispose, `Activity.onStop`,
and ProcessLifecycle background. An embedded preview needs the stream to **survive** preview↔fullscreen but
**stop** when Live-TV is abandoned. Introduce a single **surface-owner** rule (App-level, MainActivity knows
`selectedRoute` + `playerVisible` + committed): owner = fullscreen when `playerVisible`, else the Live-TV
preview when Live-TV is the active route AND a channel is committed, else none → detach + stop. The
preview↔fullscreen transition must NOT route through the existing `onDispose { stop() }`.

**Phasing (each phase compiles + is emulator-testable):**
- **Phase 1 — UI/data rework, preview = logo + focused-program info (placeholder where video will mount):**
  everything below EXCEPT the live surface. Adaptive column collapse, channel card (number/thin progress/
  minimal badges), EPG Live badge (R5), compact favorites (F1/F2) + no-EPG (F3), remove pills/Details/Provider
  (P1/F4), preview-follows-focused-EPG (R1/R2/R3), P2 batch current-program, favorites active-gate (A),
  logoConfigSignal (B), committed/selected **state** split (committed drives the fullscreen target via today's
  `play()`; selected drives EPG/info), remove column titles (S8), long-OK favorite (S5), spacing/width trim,
  strings, docs. This is a complete, shippable UI rework.
- **Phase 2 — embed the shared player video:** mount a `SurfaceView` in the preview, attach the shared
  player's surface to it, move `play()` to commit-time, surface handoff preview↔fullscreen with no reconnect,
  the surface-owner / release-on-leave rework, audio. The risky architecture piece, done in isolation on top
  of a working Phase 1.

### Phase 2 — concrete implementation approach (in progress)

Verified wiring (file:line): one player `appContainer.playerController` (App-hoisted); video via
`attachVideoSurface(SurfaceView)`/`detachVideoSurface()` which do `player.setVideoSurfaceView`/`clearVideoSurface`
— **decoupled from `play()`/`prepare()`, so swapping the surface never reconnects**. `play()` does
`stop()`→`start()` (one connection at a time). Fullscreen = `if (playerVisible) PlayerRoute(...)` overlay
(`MainActivity`), Live-TV stays composed underneath. **Blocker:** `PlayerRoute` stops the stream on
dispose/BACK (`stopPlaybackOnce()` in `onDispose` + BACK) and `MainActivity.onStop`.

**App↔LiveTvRoute interface (feature must NOT touch playerController):**
- `livePreviewSlot: @Composable BoxScope.() -> Unit` — App renders the preview `SurfaceView` here (in the
  preview box) when a channel is committed for preview; else LiveTvRoute shows the Phase-1 logo surface.
- `onCommitPreview: (Channel) -> Unit` — LiveTvRoute calls it on OK (activate) → App `playerController.play`
  + `committedLiveChannel = channel`, `playerVisible` stays false (audio ON — tuner-like).
- `onOpenPlayer(channel)` (existing) → App: if `channel == committedLiveChannel` (already streaming) just
  `playerVisible = true` (surface handoff, NO re-play); else `openChannel(channel)` (play + fullscreen).
- `onLeaveLiveTvPreview` / route-change → stop + clear committed.

**Surface owner (App state):** attach the preview `SurfaceView` when `committedLiveChannel != null && route ==
live-tv && !playerVisible`; fullscreen `PlayerRoute` attaches its own on open (setVideoSurfaceView replaces —
no reconnect); on fullscreen close, re-attach the preview surface. `PlayerRoute` gets `keepPlayingOnClose:
Boolean` — when opened from the live preview it must NOT `stop()` on close/dispose (hand back to preview);
normal (Home/Movies) closes still stop. `MainActivity.onStop` still stops (backgrounding releases the
connection). Zap (CH+/-) = `play(next)` (stop-old/start-new, one connection).

**Increments (each compiles + emulator-tested):** (1) preview SurfaceView slot + play-on-commit +
stop-on-leave-Live-TV (fullscreen may re-play for now). (2) seamless preview→fullscreen handoff
(`keepPlayingOnClose`, no re-play). (3) fullscreen→preview re-attach + zap + edge cases (background, provider
error, channel with no stream).

**P2 INCREMENT 1 + seamless handoff — IMPLEMENTED, plumbing emulator-verified.** Files: `PlayerRoute`
(`keepPlayingOnClose` gates stop + surface-detach on close); `MainActivity` (`committedLiveChannel` +
`livePreviewSurface` [`setZOrderMediaOverlay(true)` so it draws above the panel] + `commitLivePreview` /
`stopLivePreview` / `openLivePlayer` [handoff: if the channel is already the committed preview, just raise
fullscreen — no re-play]; a `DisposableEffect` attaches the preview surface when committed + Live-TV active +
not fullscreen; `LaunchedEffect(selectedRoute)` stops on leaving Live-TV; `keepPlayingOnClose =
committedLiveChannel != null`); `LiveTvRoute` (new `onCommitPreview` / `livePreviewActive` / `livePreviewSlot`
params; OK → `onCommitPreview`; the preview box renders the App's video slot when active, else the logo).
Gates green (assembleDebug, testDebugUnitTest, detekt — detekt baseline regen for the two god-composables'
signature growth). **Verified on emulator:** surface attaches (preview box goes from logo → live surface),
BACK from a committed channel hands off to fullscreen on the SAME player (no second connection),
leaving Live-TV stops the stream. **NOT visually verifiable on this emulator:** actual video frames — the
test streams either fail the HTTP connect (1-2-3 TV) or exceed the goldfish software decoder (3sat 720p50);
both are emulator limits, and the FULLSCREEN path hits the identical error, so it's not a P2 regression.
Needs the physical TV to confirm the picture + audio + the fullscreen↔preview round-trip with a live stream.
**P2 round-2 fixes (user-tested on emulator with a playable channel):**
- **Frozen preview corner in fullscreen (user-confirmed fixed):** the media-overlay preview SurfaceView drew
  ABOVE the fullscreen overlay and froze on its last frame. Fix: `setZOrderMediaOverlay(true)` + toggle the
  surface `visibility` to `INVISIBLE` whenever it's not the owner (fullscreen up / not committed / off Live-TV).
- **Navigation trapped on Home↔Live-TV (regression, fixed):** the initial-provider-focus used a
  `requestFocus()` on load, which STOLE focus from the top nav as focus-follows-selection merely passed
  *through* Live-TV — trapping the nav on the Live-TV tab. Replaced with
  `Modifier.focusProperties { enter = { firstProviderFocusRequester } }` on the content row: focus lands on
  the first provider only when D-pad actually ENTERS the content (DOWN), never on nav pass-through. (Removed
  the `requestFirstProviderFocusSignal` plumbing.)
- **Fullscreen close landed on Home (fixed):** closing the player let focus fall to the Home nav tab
  (focus-follows-selection → Home). Fix: `onClose` for a live-preview channel sets `selectedRoute="live-tv"`,
  requests focus on the Live-TV nav tab synchronously (beats the Home fallback), and bumps a
  `focusChannelOnReturnSignal` → Live-TV focuses the committed channel in the list. **Verified:** close →
  Live-TV, focus on the channel, and the preview RESUMES the same stream (seamless handoff back, no reconnect).

**P2 INCREMENT 3 — committed≠selected split DONE + emulator-verified.** Design decision (user, 2026-07-19):
**Model B (browse-peek)** — the preview-column video follows the COMMITTED channel, but its name + EPG
detail follow the FOCUSED (`selectedChannel`) channel, so you can read a channel's full detail in the big
panel while the current one keeps playing. The video/caption channel mismatch is accepted + deliberate.
- The P-column already rendered Model B (`previewChannel = selectedChannel` for metadata, `livePreviewSlot`
  = committed video) — no change needed there.
- **BACK-targets-committed fix (the real bug):** `LiveTvRoute` now tracks `committedChannel` (set on OK,
  cleared on `channelResetSignal`); the Sender-Modus BACK handler opens `committedChannel ?: selectedChannel`
  instead of `selectedChannel`. Deep-link activation (no committed preview) still falls back to the selected
  channel. **Verified on emulator:** commit 3sat → browse to ARTE (video stays 3sat + "3sat THEMENTAG"
  watermark, EPG/text = ARTE) → BACK fullscreens **3sat** (the streaming one), a seamless handoff — NOT a
  reconnect to ARTE. Close fullscreen → back to Live-TV (not Home), preview resumes 3sat, focus on the browse
  position (ARTE). Gates green (compile + detekt).

**Still TODO / open judgment calls (increment 3):**
- **CH+/- zap re-commit** deferred: `moveChannel` currently only moves focus (browse, consistent with Model B
  for D-pad). A hard-zap that re-`play()`s on CH+/- is a distinct semantic and not testable on this emulator
  (no CH+/- keys); revisit on hardware if the remote has them.
- **Return-focus target after closing fullscreen:** currently the browse position (`selectedChannel` = ARTE);
  the user's literal ask was "focus the channel I last watched" (= committed 3sat). Both are coherent under
  Model B; leaving browse-position until the user weighs in.
- Remaining edge cases (channel with no stream, provider error already shows the InfoPanel; background stop
  via `MainActivity.onStop` already covered; no-EPG channel handled + UP-locked).

## Nav-Audit 2026-07-22 — findings + fix status (IN PROGRESS)

After the Jetpack-Nav rebuild (player-as-destination + PlayerViewModel extraction, commit `9660191`) the whole
Live-TV navigation was re-audited on the physical SHIELD (.12) with full `[ltv]` `vcLog` instrumentation
(focusedArea / activated / vmstate / BACK / TARGET / toggle / click / collapse / zap) — 33 screenshots +
per-step trace timelines. The instrumentation stays in the code (debug-gated).

**Confirmed working (no change):** provider toggle collapse/expand (per-provider category memory + multi-
provider); RIGHT channel→EPG, LEFT EPG→channel; UP from the top content row → active Live-TV tab (C2 fix holds,
no Home jump, no self-nav crash); browse-mode BACK step-back chain (EPG→ChannelList→Provider→tab); preview
commit + seamless fullscreen handoff; return from fullscreen lands on the committed channel with the preview
still playing; cold-load lands on the correct Home channel; DOWN through categories auto-selects each
category's first channel (preview follows).

**Bugs — 1/2/3/6 FIXED + on-device-verified on the SHIELD 2026-07-22 (pending user sign-off).** What was
verified per fix: (1) RIGHT from a low category (Religion) enters the channel list in ONE press, K collapses to
[S|E|P], no Test-jump; (2) LEFT from a channel lands on the ACTIVE category (Religion), not the first provider;
regression — DOWN-from-nav still lands on the first provider, RIGHT provider→channel / channel→EPG and LEFT
EPG→channel all still work; (3) BACK after returning from fullscreen re-fullscreens the still-playing channel
(no re-OK), targets the committed channel even after browsing away, and the return does NOT auto-bounce;
(6) a cold Home→channel→BACK shows "Wird geladen …" instead of "Keine Sender"/"Keine Programminformationen",
and a genuinely-empty category (Favoriten 0) still shows "Keine Sender" instantly (the latch is one-way).
Gates green (detekt/test/assembleDebug); the `[ltv]` audit instrumentation stays in (debug-gated).

1. **RIGHT from a category (or a lower / non-first provider) → 2-press bug** *(major; = the KNOWN OPEN BUG
   below).* First RIGHT jumps focus UP to the first provider ("Test") + scrolls the K column to the top; a
   second RIGHT enters the channel list (selection preserved). Root cause **CONFIRMED via logcat**: RIGHT from
   a K row with no channel card in its rightward beam escalates to the content `Row`, whose `focusProperties {
   enter = { firstProviderFocusRequester } }` (C2, for DOWN-from-nav) redirects to the first provider. **Fix:**
   direction-aware `enter` (`Down` → firstProvider, else no hijack) **plus** a deterministic `focusProperties {
   right = <selected-channel requester> }` from the K rows into the channel list. The combination is new — the
   4 reverted 2026-07-19 attempts each tried only one half while the `enter` kept hijacking. Verify on-device
   per key press via logcat before committing.

2. **LEFT from the channel list → lands on the first provider, not the active category** *(medium).* Same
   `enter`-override root cause (lands on the identical `firstProviderFocusRequester`). The direction-aware
   `enter` fix un-hijacks LEFT, so the existing `selectedCategoryFocusRequest` → `selectedFocusRequester`
   lands focus on the selected category.

3. **After return from fullscreen the preview keeps playing but `activated=false`** *(medium).* BACK then
   steps back through the columns instead of re-fullscreening the visibly-playing channel; you must OK again.
   **Decision (user 2026-07-22): restore Sender-Modus on return** — set `activated=true` + `committedChannel`
   = the returned channel so BACK re-fullscreens. Guard the same BACK press from double-firing into fullscreen
   (the reason C2 chose `activate=false`); verify on-device that the return does NOT auto-bounce.

6. **Cold-load transient — empty placeholders during the ~1s DB load** *(polish, user wants it fixed).* On
   Home→channel→BACK into a COLD Live-TV the screen shows `Keine Sender` / `Keine Programminformationen
   verfügbar` placeholders for ~1.0s (vmstate `provs=0 cats=0 chans=0`) before the data lands and the target
   channel appears. Lands correctly (no wrong-channel flash). **Fix:** add `isInitialLoading` to `LiveTvUiState`
   (true until the first providers emission); render a neutral loading surface instead of the genuine
   empty-state during the cold load.

**Decisions that need NO change:**
- **(4) Return without a preview already steps back like LEFT** (EPG→ChannelList→Provider→tab) — matches
  intent, keep.
- **(5) BACK from the Live-TV tab shows "Zum Beenden erneut zurück" instead of going Home** — **kept on
  purpose** for consistency with Settings (BACK → gear, 2nd BACK → exit). May revisit later.

**Minor items:**
- ~100ms full-provider-list flash (66/80 channels) before the category filter applies on provider expand —
  STILL OPEN (deprioritized; least jarring — shows *more* channels briefly, not empty).
- Focusing a provider row while the global "Favoriten (0)" pseudo-category is active keeps the empty favorites
  list — STILL OPEN (debatable behaviour; not raised again).

**Follow-up fixes — user-raised 2026-07-22, all FIXED + on-device-verified:**
- **A — Favoriten empty-state text.** "Live-TV Favoriten (0)" showed "Keine Sender / Dieser Provider enthält
  keine importierten Live-TV-Sender" — wrong, favorites are CROSS-PROVIDER. Root cause: `emptyChannelMessage`
  detected FAVORITES via the (null-for-a-pseudo-category) `Category` object. Fix: detect via a `favoriteSelected`
  flag → title "Keine Favoriten" + body "Noch keine Live-TV-Favoriten gespeichert." (strings already existed).
- **B — preview panel black background.** The embedded video made the whole preview `GlassPanel` render black
  instead of its green tint. Root cause: the preview `SurfaceView` used `setZOrderMediaOverlay(true)` (draws
  *behind* the window → needs a transparent hole → black around the video). Fix: `setZOrderOnTop(true)` (draws
  *on top* of the panel, only over its 158dp box) — the identical INVISIBLE-while-fullscreen visibility toggle
  keeps handling the fullscreen frozen-corner case (verified: no frozen corner, clean fullscreen + return).
- **C — Home DOWN focus.** DOWN from the top nav landed on the history card geometrically under the tab (the
  rightmost), not the first. Fix: direction-aware `enter` (Down only) on the Home content → a requester on the
  FIRST element of the FIRST shown row, via the hasLive → hasMovies → hasSeries cascade (history channel → the
  row's "go browse" CTA → the empty-state CTA). Rows extracted to `HomeRows` to keep detekt complexity green.

## KNOWN OPEN BUG — RIGHT from a low category needs two presses (RESOLVED 2026-07-22)

> **2026-07-22 — RESOLVED.** Root cause confirmed via `[ltv]` logcat = the content-Row `enter` override
> over-firing on horizontal moves (see bug 1 in "## Nav-Audit 2026-07-22" above). Fix = direction-aware `enter`
> (redirect to the first provider ONLY for `FocusDirection.Down` nav-entry; no hijack on Left/Right) + a
> deterministic `focusProperties { right = <selected-channel requester> }` on the K rows (favorites / provider
> / category), the requester hoisted to `RoomLiveTvRoute` and bound to the selected channel row. The 5th attempt
> succeeded because it combined BOTH halves — the 4 failed 2026-07-19 attempts each tried only one while the
> `enter` kept hijacking. On-device-verified (RIGHT from Religion enters in one press). The historical symptom +
> the 4 failed approaches below stay on record.

**Symptom:** In `[K|S|P]`, focus on a category that sits **below** the (top-anchored) channel rows — e.g.
"Nachrichten" while its channels phoenix/tagesschau render at the very top of the channel column — pressing
RIGHT does **not** enter the channel list. Focus jumps **up to the first provider ("Test #1")**; a **second**
RIGHT (from Test #1, now vertically aligned with the top channel) finally reaches the channel. Categories that
happen to align with a channel row work in one press. DOWN/LEFT/UP are fine.

**Root-cause hypothesis (not yet confirmed with logs):** Compose's geometric 2D focus search for RIGHT finds
no channel in the rightward beam (channels top-anchored, category lower) → the search escalates to the content
`Row`, whose `focusProperties { enter = { firstProviderFocusRequester } }` (added so **DOWN from the top nav**
lands on the first provider, not favorites — line ~308) redirects the entry to the first provider. That
`enter` is over-firing on horizontal column moves.

**Approaches TRIED and FAILED this session (do NOT just retry these — they were all reverted):**
1. `onPreviewKeyEvent` on the Kategorien `GlassPanel` consuming RIGHT → `focusedArea=ChannelList` +
   `selectedChannelFocusRequest++`. → **Home bounce**: flipping `focusedArea` removes the Kategorien column
   (the focus holder) before the channel grabs focus → focus vacuum → nav/Home.
2. Same handler but only `selectedChannelFocusRequest++` (async `LaunchedEffect` `requestFocus`). → focus did
   **not** move to the channel; stayed `[K|S|P]` / went to Test #1.
3. `focusProperties { right = <hoisted single channel requester> }` on each Kategorien item (favorites /
   provider / category), requester bound once to the selected channel row. → **ignored**; geometric fallback →
   Test #1.
4. `onPreviewKeyEvent` with a **direct synchronous** `requester.requestFocus()`. → focus STILL went to
   Test #1, i.e. the RIGHT branch did not consume — **unexplained** (contradicts approach 1, where the same
   handler clearly fired). This contradiction is exactly why the next step must be instrumentation, not
   another guess.

**NEXT STEP — instrument first, don't guess.** Add `Log.d` in the Kategorien `onPreviewKeyEvent` (log key +
whether it fires) and in `onChannelFocused` (log id), run `adb logcat`, press RIGHT once, and read the trace:
(a) does `onPreviewKeyEvent` fire for RIGHT at all? (b) does `requestFocus` land on the channel? (c) what are
`focusedArea` / `selectedChannelId` at that moment? Only then pick a fix. Likely real fix directions: make the
`Row`-level `enter` redirect apply to **nav-entry only** (not horizontal escalation), or give the channel row
a genuinely-bound deterministic RIGHT target. Current code is back to the pre-experiment baseline (RIGHT still
needs two presses); this is the ONE Live-TV item left unfixed.

## Constraints (unchanged)

- No code until explicit GO. No new module, no DI migration. ViewModel + immutable UiState + StateFlow;
  column mode / focus stay in the composable (already the case). Strings only in `:core:designsystem` (de+en).
  `LiveTvRoute` is a baselined god-composable — expect a `detektBaseline` regen if its signature changes.
