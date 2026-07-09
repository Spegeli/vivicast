# Plan: Appearance-Einstellungen — Popup-Picker + Rendering-Wiring

Status: **abgeschlossen** (2026-07-09). Phasen 1–4 umgesetzt. Phase 5 (Background theme + Accent)
bewusst **nicht** umgesetzt — kommt beim späteren vollständigen UI-Theme-Umbau, wenn die App größtenteils
fertig ist. Grundfunktion (Popups + Font/Transparency wirksam) ist ausreichend.

## Kontext
Die 5 Appearance-Optionen waren persisted-only (kein Rendering-Effekt); Zeilen klickten den Wert durch.
Ziel: Popup-Einzelauswahl je Zeile (wie Sprach-Popup) + echtes Wiring. Normwerte aus
`../vivicast-docs/design/design-system/02-design-tokens.md`. Anwendung im App/Theme-Layer, nie ViewModel;
kein `recreate()` (CompositionLocal rekomponiert live).

## Umgesetzt

### Phase 1 — Popup-Picker für alle 5 + Dedup
- Generischer `SettingsChoiceDialog<T>` — `feature/settings/.../SettingsChoiceDialog.kt`.
- `AppearanceSettingsPanel.kt`: 5 Zeilen von `.next()`-Cycling auf `openPicker`-State + `SettingsChoiceDialog`.
  `.next()`-Helper entfernt, `.label()` behalten.
- Dedup: `LanguagePickerDialog` (GeneralSettingsPanel) und Logo-Prioritäts-Picker (ProviderEditor) nutzen
  jetzt `SettingsChoiceDialog`.

### Phase 2 — Font size (wirksam)
- `LocalDensity`-Override am Composition-Root (`MainActivity.setContent`):
  `Density(fontScale = base.fontScale * factor)`. Skaliert alle `sp` app-weit inkl. Dialoge, ohne
  Typo-Call-Sites. Faktor: `FontScalePreference.toFontScaleFactor()` in `app/SettingsPreferenceMappers.kt`
  (0.90/1.00/1.12/1.25).

### Phase 3 — Transparency (wirksam, Panels)
- `LocalSurfaceOpacity` + `Color.scaledAlpha()` in `VivicastTheme.kt`; bereitgestellt am Root aus
  `TransparencyLevel.toSurfaceOpacity()` (1.0/0.75/0.5, `Percent75`→0.5 geklammert).
- Konsumiert von Panel-Fills: `VivicastGlassPanel`, `VivicastDetailsPanel`. Border/Content bleiben opak.
- `VivicastDialog` stellt `LocalSurfaceOpacity provides 1f` → Dialoge bleiben opak (Spec).
- Bewusst **nicht** angewandt: Cards/Rows (`FocusPanel`/`TvSurface`-Container, Fokus-Klarheit),
  Screen-Background (Root-Backdrop), Player-Overlay (bleibt bei Design-Alpha → trivial ≤50%).

### Phase 4 — Animations (nur Picker)
- Picker + Persistenz. **Kein Wiring** — die App hat aktuell 0 explizite Animationen; „Animations"
  regelt heute nichts. Fokus-Motion (120ms) bewusst ausgenommen.

## Gates
`.\gradlew.bat :app:compileDebugKotlin`, `detekt`, `test`, `assembleDebug` — alle grün. APK auf Emulator
installiert. (Kein JVM-Unit-Test-Set in `:app` → die reinen Enum→Float-Mapper sind erschöpfende, vom
Compiler geprüfte Konstanten-Maps ohne eigenen Test.)

## Phase 5 — nicht umgesetzt (auf späteren UI-Theme-Umbau verschoben)
**Background theme + Accent color** bleiben vorerst persisted-only (Picker vorhanden, kein Rendering-Effekt).
Bewusste Entscheidung: das UI-Theme wird ohnehin neu gebaut, sobald die App weitgehend fertig ist — dann
werden Paletten + Accent zentral gelöst. Für dann relevant:
- `VivicastColors`-`object` → `@Immutable`-Holder via `LocalVivicastColors` (~130 Composable-Refs in 28
  Dateien + Nicht-Composable-Consumer: 11 Typo-Farben, `VivicastFocusDefaults`, `focusSurfaceBrush`).
  Palette in `setContent` über `VivicastTheme(colors=…)`.
- Offene Produktentscheidungen: HighContrast-Dark + AMOLED-Dark Paletten (Hex, nirgends definiert);
  Accent-Liste (nur Blau); Fokus-Ring fix oder Accent-abhängig.
