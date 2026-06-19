# ViviCast Brand Direction

This document captures the initial ViviCast brand direction based on the provided concept logo.

## Brand Feel

- Modern IPTV / streaming utility.
- Dark, premium Android TV atmosphere.
- Fast, focused, and technical without feeling complicated.
- Built around a clear "V" mark, play shape, and wireless/cast signal motif.

## Logo Direction

The current concept uses:

- A stylized white "V" as the main identity anchor.
- Cyan/turquoise play and cast-wave elements.
- A dark circular mark with soft teal glow.
- Wordmark split visually between white `ViVi` and cyan `Cast`.

This direction fits the Android TV MVP well because the mark reads clearly on dark surfaces and gives us a strong focus/accent color for the interface.

## Color Tokens

Initial UI palette direction:

- App background: near-black / blue-black.
- Primary surface: dark charcoal.
- Elevated surface: slightly lighter blue-charcoal.
- Primary text: white.
- Secondary text: cool grey.
- Accent: cyan/turquoise inspired by the logo.
- Focus: bright cyan outline or fill with high contrast.
- Error: restrained red, used only for playback/import failures.

The UI should avoid becoming a one-color cyan theme. Cyan is the accent, not the entire app.

## Usage Guidance

- Use the logo mark on first-run setup, empty states, and app branding surfaces.
- In dense TV screens, prefer a compact wordmark or icon-only treatment so browsing and playback remain dominant.
- Use the cyan accent for selected navigation, focused rows/cells, active channel indicators, and primary actions.
- Avoid heavy glow effects in regular UI. The logo can glow; controls should stay crisp.
- Favor crisp high-contrast focus fills and borders over decorative gradients or neon-heavy effects.
- Launcher icons and TV banner assets should be exported separately from the source concept, not cropped casually from the wide artwork.

## Needed Assets

Before release-quality packaging, create or export:

- Android launcher foreground icon.
- Android launcher round icon.
- Android TV banner.
- Transparent logo mark.
- Transparent horizontal wordmark.
- Optional splash-screen mark.

Recommended source format:

- SVG or high-resolution transparent PNG for the mark and wordmark.
- Separate wide hero/background artwork only for optional branding screens.

## Implementation Order

1. Define ViviCast theme colors from the concept.
2. Add logo assets to the Android resource structure.
3. Use the compact mark in the TV app first-run/setup screen.
4. Generate proper launcher and Android TV banner assets.
5. Revisit mobile/tablet branding later with touch-specific layouts.
