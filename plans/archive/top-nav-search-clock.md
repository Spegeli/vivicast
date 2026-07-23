# Top-Nav: Suche nach links + 24h-Uhr — Plan

**Status:** ✅ ABGESCHLOSSEN + VERIFIZIERT (2026-07-23). Gates grün (compile / detekt / Unit-Tests inkl.
Uhr-Test). **Emulator-verifiziert:** Suche vor Home (mit Abstand), 24h-Uhr rechts, Home→Live-TV-Nav via
`VCd`-logcat ok. Index-Modell komplett unangetastet — nur Render-Zonen + Uhr.

**Nachtrag (2026-07-23, Commit `7c5f67d`):** Folge-Feinschliff nach diesem Plan — Suche final direkt **vor Home
mit Abstand** (`Space2`, war am Brand); Home-Tab **DE-Label „Startseite"** (via `nav_home_label`; Icon- +
Breiten-Matching lokalisiert; `CLAUDE.md`-Ausnahme angepasst); **Sprachwechsel-Home-Flash gefixt** (`selectedIndex`
während `reopenLanguageSettings` auf Einstellungen gepinnt — Emulator + physischer TV verifiziert).

## Ziel (aus Nutzer-Aufgabe 4)

1. **Suche** (Icon) in der Top-Navigation **vor Home** platzieren (ganz links).
2. An der **frei gewordenen Stelle vor Einstellungen** (wo die Suche vorher war, rechts) eine **Uhr im
   24-Stunden-Format** (`HH:mm`) anzeigen.
3. **Die Navigation darf dadurch nicht brechen** — auch nicht aus Home/Live-TV/… heraus, D-Pad
   LINKS/RECHTS/HOCH, Fokus-Einstieg, Player-Rückkehr, Sprachwechsel-Rekreation.

Kein visueller Test meinerseits nötig (Nutzer testet selbst). Debug-Trace via `vcLog` (Tag `VCd`).

---

## Ist-Zustand (genau analysiert)

### Komponente
`core/designsystem/.../VivicastNavigation.kt` → `VivicastTopNavigation(brand, items: List<String>,
selectedIndex, selectedFocusRequester, onItemFocusChanged, onSelected, onFocused)`.

Rendering in **3 Zonen** (eine `Row`, `CenterVertically`):
- **links:** Brand (`VIVICAST` + Logo)
- **mitte:** `Row(weight 1f)` mit allen Items, deren Label **nicht** in `iconOnlyLabels = {Suche,
  Einstellungen}` liegt → Home, Live-TV, Filme, Serien (Text+Icon).
- **rechts:** `Row` mit den Items, deren Label **in** `iconOnlyLabels` liegt → Suche, Einstellungen
  (nur Icon, 44dp).

`VivicastTopNavItem`: `iconOnly = label == strSearch || label == strSettings` steuert Icon-vs-Text.

### Index-Modell (in `app/MainActivity.kt`) — die kritische Kopplung
Vier Strukturen sind **index-aligned** und müssen konsistent bleiben:

| # | Struktur (MainActivity) | Reihenfolge / Bedeutung |
|---|---|---|
| 1 | `destinations = listOf(AppDestination(...))` | `[Home, Live-TV, Filme, Serien, Suche, Einstellungen]` = Index 0..5 |
| 2 | `items = destinations.map { it.label }` → an `VivicastTopNavigation` | gleiche Reihenfolge; `selectedIndex` highlightet `items[selectedIndex]` |
| 3 | `tabRoutes = listOf(Home, LiveTv, MoviesGraph, SeriesGraph, Search, Settings)` | liefert `selectedIndex` via `hierarchy`-Match — **muss zu (1) passen** |
| 4 | `NavHost { composable<Home> { destinations[0].content() } … composable<Settings> { destinations[5].content() } }` | **hardcodierte Indizes** `destinations[0..5]` |

Zusätzlich: `tabRootRoutes` (BACK-Policy) und `onSelected/onFocused = { index -> …(destinations[index].route) }`.

**Konsequenz:** Würde man die `destinations`/`items`-Liste umsortieren (Suche → Index 0), müssten (1)(2)(3)(4)
+ `onSelected/onFocused` + `NavHost` alle mit-umnummeriert werden. Search ist aktuell **Index 4**, Settings
**Index 5**. Fehler dort = kaputte Navigation (genau das, was vermieden werden soll).

### Fokus-Vertrag (unverändert lassen)
- `selectedFocusRequester = topNavigationFocusRequester`: Einstieg von unten (HOCH aus Content) landet auf dem
  **aktiven** Tab (nicht dem nächstgelegenen); außerdem App-Start-Fokus + Player-Rückkehr-Fokus.
- `focusProperties { enter = { selectedFocusRequester } } + focusGroup()` auf der äußeren Row.
- `onItemFocusChanged` → `topNavigationFocused` (BACK-Exit-Policy).
- D-Pad LINKS/RECHTS = räumliche Fokus-Traversierung in **Kompositions-/Sichtreihenfolge**.

### Test
`core/designsystem/.../VivicastTopNavigationFocusTest.kt`: prüft Fokus per **Tag** (`topNavItemTag(label)`)
und `selectedIndex`-Sync. `TEST_ITEMS = [Home, Live-TV, Filme, Serien, Suche, Einstellungen]`. **Keine**
geometrische Reihenfolge-Assertion.

---

## Gewählter Ansatz: **rein visuelle Umordnung + Anzeige-Uhr** (Strategie B)

**Kernidee:** Das komplette Index-Modell (1–4, `onSelected/onFocused`, `NavHost`, `tabRoutes`) bleibt
**unangetastet**. Nur die **Render-Zonen** in `VivicastTopNavigation` werden umgebaut und die Uhr ergänzt.

Neue Zonen-Aufteilung:
```
[Brand] [Suche-Icon]   [ Home  Live-TV  Filme  Serien  (weight 1f) ]   [ Uhr  Einstellungen-Icon ]
```

- **Suche** wird aus der rechten Zone in eine **neue linke Icon-Zone** (nach Brand, vor der Mitte) gerendert.
- **Uhr** (nicht fokussierbar) kommt in die rechte Zone **vor** Einstellungen.
- `NavItem(index, label)` behält den **echten** Index in `items` → `onSelected/onFocused/selectedFocusRequester`
  bleiben korrekt. **Search bleibt Index 4, Settings Index 5.**

### Warum Strategie B (und nicht Liste umsortieren)
- Search/Settings-Index unverändert → `selectedIndex`, `onSelected`, `onFocused`, `tabRoutes`, `NavHost`,
  BACK-Policy **funktionieren unverändert weiter** → Navigation kann nicht brechen.
- Fokus-Test bleibt grün (Tags + `selectedIndex`-Sync unverändert; keine Geometrie-Assertion).
- Nur die visuelle LINKS/RECHTS-Traversierungsreihenfolge ändert sich — **genau das Gewünschte**.
- **Verworfen — Liste umsortieren (Strategie A):** bricht 4 index-gekoppelte Strukturen + Fokus-Test,
  hohes Risiko, **kein Mehrwert** (Suche würde durch `iconOnlyLabels` trotzdem in der rechten Zone landen,
  Zonen-Umbau wäre ohnehin nötig).

---

## Konkrete Änderungen

### A) `VivicastNavigation.kt` — Zonen umbauen (Kernänderung)
In `VivicastTopNavigation` die Item-Verteilung ändern:
- Label-Referenzen `strSearch` / `strSettings` liegen bereits vor.
- **Links (neu):** `items.forEachIndexed { i, l -> if (l == strSearch) NavItem(i, l) }` direkt nach der Brand-Row,
  mit Abstand (`VivicastSpacing.Space2`).
- **Mitte:** `Row(weight 1f)` rendert Items mit `l != strSearch && l != strSettings` (Home, Live-TV, Filme,
  Serien) — vorher `l !in iconOnlyLabels`.
- **Rechts:** `Row(spacedBy Space2, CenterVertically) { VivicastTopNavClock(); items.forEachIndexed { i, l ->
  if (l == strSettings) NavItem(i, l) } }`.

`iconOnlyLabels`/`topNavItemWidth`/`VivicastTopNavItem` (Icon-Rendering, 44dp): **unverändert** — Suche &
Einstellungen bleiben Icons.

### B) `VivicastNavigation.kt` — Uhr-Composable (neu, privat)
```kotlin
@Composable
private fun VivicastTopNavClock(
    modifier: Modifier = Modifier,
    nowMillis: () -> Long = { System.currentTimeMillis() }, // injizierbar für Tests
) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val time by produceState(formatter.format(Date(nowMillis()))) {
        while (true) {
            val now = nowMillis()
            value = formatter.format(Date(now))
            delay(60_000L - (now % 60_000L)) // auf Minutengrenze ausrichten → HH:mm springt punktgenau
        }
    }
    Text(
        text = time,
        style = VivicastTypography.LabelSmall.copy(color = VivicastColors.TextSecondary),
        modifier = modifier,
    )
}
```
Eigenschaften:
- **24h fix** über Muster `"HH:mm"` (unabhängig von der 12/24h-Geräteeinstellung — genau wie gefordert).
- **Nicht fokussierbar** (reines `Text`, kein `clickable`/`focusable`) → D-Pad überspringt es.
- **Selbstständiger Tick** via `produceState` (= `LaunchedEffect`-Variante) → Rekomposition bleibt auf das
  Uhr-`Text` beschränkt (MainActivity/VivicastApp wird **nicht** jede Minute neu komponiert — wichtig, da
  `VivicastApp` sehr groß ist).
- **Injizierbarer `nowMillis`** (Default = System) → testbar, ohne die MainActivity-Verkabelung.
  Kompromiss zur CLAUDE.md-Regel "injectable clock": reine UI-Chrome, Quelle bleibt injizierbar.
- Neue Imports: `SimpleDateFormat`, `Date`, `Locale`, `produceState`, `delay`, `Text` (tv.material3 —
  schon importiert), `remember`/`getValue` (vorhanden).

### C) `MainActivity.kt`
**Keine Änderung** am Aufruf `VivicastTopNavigation(...)`, an `destinations`, `tabRoutes`, `tabRootRoutes`,
`NavHost`, `onSelected`, `onFocused`. (Uhr ist self-contained in der Komponente.)

### D) Test
- `VivicastTopNavigationFocusTest` bleibt **grün** (Tags + `selectedIndex`-Sync unverändert).
- Optional (klein): Ein Test, der prüft, dass `VivicastTopNavClock` mit festem `nowMillis` `"HH:mm"` rendert
  (Semantik/Text-Assertion) — nur falls gewünscht.

---

## Fokus/Navigation — Verhalten nach Umbau (zu verifizieren)
- D-Pad **HOCH** aus Content → aktiver Tab (via `selectedFocusRequester`/`enter`) — **unverändert**.
- D-Pad **LINKS** von Home → **Suche** (neuer linker Nachbar).
- D-Pad **RECHTS** von Serien → **Einstellungen** (Uhr wird als nicht-fokussierbar übersprungen).
- D-Pad **LINKS** von Einstellungen → Serien; **RECHTS** von Suche → Home.
- `onSelected/onFocused(index)` bilden weiter korrekt auf `destinations[index].route` ab (Search=4, Settings=5).

## Verifikation (bei Umsetzung, nach GO)
1. `.\gradlew.bat assembleDebug` + `detekt` + `:core:designsystem:test` (Fokus-Test) grün.
2. Emulator + **`adb logcat -s VCd`** parallel; Filter `topnav`:
   - Bei Fokus/Auswahl jedes Tabs prüfen, dass `focusRoute <route>` / `selectRoute` den **richtigen** Route
     loggt (Search→live-tv-Suche, Settings→settings usw.). Bei Bedarf temporär `vcLog("topnav")` im
     `onFocused(index)` ergänzen (Index + Label + Route).
   - Nav-Pfade testen: HOCH aus Home/Live-TV/Filme/Serien/Suche/Einstellungen; LINKS/RECHTS durch die Leiste;
     Player-Rückkehr (Live-TV-Tab-Fokus); Sprachwechsel-Rekreation (landet auf Settings).
3. Uhr: zeigt `HH:mm`, springt zur vollen Minute, kein Fokusrahmen, keine Fokusfalle.

## Risiken / offene Punkte
- **Sehr gering** (Index-Modell unangetastet). Einziges reales Verhaltensdelta: LINKS/RECHTS-Traversierung
  (gewollt).
- **Uhr-Position bei schmaler Leiste:** rechts genug Platz? Aktuell nur 2 Icons rechts → mit Uhr + 1 Icon
  unkritisch. Bei Umsetzung Spacing prüfen.
- **Sekundenanzeige?** Plan nutzt `HH:mm` (Minute). Nutzer wollte 24h-Format, keine Sekunden — passt.
- **Uhr-Quelle:** self-contained System-Clock (Default). Falls die adversariellen Reviews die
  "injectable clock"-Regel strikt anlegen: `nowMillis`-Param ist bereits die Injektionsstelle.

## Entscheidungen (beantwortet 2026-07-23)
1. **Uhr-Umsetzung:** ✅ self-contained `VivicastTopNavClock` mit injizierbarem `nowMillis` (Empfehlung).
2. **Uhr-Stil:** ✅ `LabelSmall` / `TextSecondary` (dezent, wie Icons).
3. **Uhr-Test:** ✅ ja — kleiner Test mit festem `nowMillis`, prüft `HH:mm`-Text.
4. **Uhr nicht fokussierbar:** ✅ bestätigt (reine Anzeige, kein `clickable`/`focusable`).
5. **Serien → RECHTS springt direkt auf Einstellungen** (Uhr übersprungen): ✅ so gewollt/korrekt.
