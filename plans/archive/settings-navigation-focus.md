# Plan: Settings-interne Navigation (Fokus + BACK)

Status: DONE & getestet — vom Owner am physischen Xiaomi Mi TV 4S (Android 9) end-to-end bestätigt
(2026-07-13); zusätzlich am API-36-Emulator alle Fälle per Screenshot verifiziert. assembleDebug +
detekt grün. Umgesetzt mit `focusProperties { onExit = … }` (Boundary-Exit) statt der
im Plan zunächst angenommenen directional-`left/up/down` — letztere wirkten auf der `focusGroup` NICHT
als Boundary-Exit (LINKS ging weiter aufs ausgerichtete Menü-Item). `onExit` feuert nur beim echten
Verlassen der Gruppe → interne Navigation bleibt intakt. Exit-Logik in top-level `FocusEnterExitScope`-
Helfer (`exitDetailPanel`/`exitSectionRail`) ausgelagert (detekt-Komplexität). Verifiziert: BACK
Detail→Sektion→Zahnrad; LINKS mittiges Detail→Sektion; HOCH/RUNTER an Rändern Stopp; Rail LINKS→nichts;
Rail-oben HOCH→Zahnrad; interne Navigation unverändert.
Scope: **nur** die Navigation innerhalb des Settings-Screens (`feature/settings`). Kein Verhalten
außerhalb Settings, keine anderen Screens.

## Probleme (vom Owner gemeldet)

1. **BACK springt direkt aufs Zahnrad.** Aus dem rechten Detail-Panel sollte BACK erst ins linke
   Menü auf die *aktuelle Sektion* zurück; erst ein weiteres BACK dann hoch auf die Top-Nav (Zahnrad).
2. **Nach unten aus dem Panel rausscrollbar.** Am letzten Detail-Element springt RUNTER ins linke Menü
   („Über die App"). Soll: letztes Element = Ende, RUNTER macht nichts.
3. **LINKS springt aufs vertikal ausgerichtete Menü-Item** (z. B. „Speicher & Verlauf"). Soll: LINKS am
   **linken Rand** des Panels → aktuelle Sektion (wie BACK).
4. **Nach oben** (neu, analog): am obersten Element macht HOCH nichts, kein Sprung in ein anderes Panel.

**Harte Randbedingung (nichts kaputt machen):** interne Navigation im Panel muss voll erhalten bleiben.
Beispiel Provider-Editor („Wiedergabeliste erstellen") mit 3 Buttons nebeneinander: LINKS auf dem
mittleren/rechten Button muss den **linken Button** treffen — NUR am linken Rand (nichts mehr links im
Panel) geht LINKS ins Menü.

**Gilt für ALLE Sektionen** (Allgemein, Playlists, EPG, Appearance, Playback, Parental, Cache, Backup,
About) — nicht nur Allgemein. Da alle Detail-Panels über denselben `when(selectedSection)`-Block im
rechten Panel laufen, deckt ein einziger `focusGroup`-Wrap alle ab.

5. **Linkes Hauptmenü selbst:** LINKS auf einer Sektion → nichts (ganz links, nichts weiter links).
   BACK auf einer Sektion → weiterhin hoch aufs Zahnrad (globaler Handler).

## Ursachen (in `feature/settings/.../SettingsRoute.kt`)

- **Kein Settings-eigenes BACK.** Globaler Handler in `MainActivity.kt` (~Z.1169): BACK → wenn nicht auf
  Top-Nav → Fokus auf Top-Nav. Greift von überall → Zahnrad.
- **Detail-Panel ohne Exit-Begrenzung.** Alle Detail-Panels sind `LazyColumn`. Nur das **erste**
  Element bekommt `focusProperties { left = <Sektion> }` (Z.183–185). Alle weiteren Elemente haben
  keine Richtungsregeln → Compose-Default-2D-Suche lässt Fokus per LINKS/UNTEN/OBEN aus dem Panel
  entkommen (aufs ausgerichtete Menü-Item bzw. „Über die App").

## Zielverhalten (präzise)

| Fokus steht auf … | Taste | Verhalten |
|---|---|---|
| Detail-Element, nicht am linken Rand (z. B. mittlerer Button) | LINKS | internes Nachbar-Element links (bleibt im Panel) |
| Detail-Element am **linken Rand** | LINKS | → aktuelle Sektion (linkes Menü) |
| **oberstes** Detail-Element | HOCH | nichts (bleibt stehen) |
| **unterstes** Detail-Element | RUNTER | nichts (bleibt stehen) |
| Detail-Element (Panel fokussiert) | BACK | → aktuelle Sektion; nochmal BACK → Zahnrad |
| Sektion im linken Menü | LINKS | nichts (bleibt stehen) |
| **oberste** Sektion (Allgemein) | HOCH | → Einstellungen-Zahnrad in der Top-Nav |
| Sektion im linken Menü (nicht oberste) | HOCH/RUNTER | vorige/nächste Sektion (unverändert) |
| Sektion im linken Menü | BACK | → Zahnrad (globaler Handler, unverändert) |
| Sektion im linken Menü | RECHTS | → erstes Detail-Element (unverändert) |
| beliebige interne Elemente | Navigation dazwischen | unverändert |
| RECHTS aus dem Panel | RECHTS | Default (unverändert; rechts ist eh nichts) |

Das Detail-Verhalten (Boundary-Exit) gilt in **jeder** Sektion identisch.

Kernprinzip: **Boundary-only Exit** — interne Navigation zuerst; ein Exit-Ziel greift nur, wenn im
Panel in dieser Richtung kein Fokusziel mehr ist.

## Lösungsansatz (ein File: `SettingsRoute.kt`)

### 1. Rechtes Detail-Panel als Fokus-Gruppe mit Boundary-Exit
Das rechte Panel (die `when(selectedSection)`-Content-Ebene) in eine `focusGroup()` fassen und den
**Gruppen-Exit** steuern:
- LINKS-Exit → `selectedSectionFocusRequester` (aktuelle Sektion).
- HOCH-Exit → `FocusRequester.Cancel` (Stopp).
- RUNTER-Exit → `FocusRequester.Cancel` (Stopp).
- RECHTS-Exit → Default.

Umsetzung mit der Fokus-Exit-Steuerung des installierten Compose (BOM 2026.05):
`Modifier.focusGroup().focusProperties { exit = { dir -> when (dir) { Left -> section; Up, Down ->
FocusRequester.Cancel; else -> FocusRequester.Default } } }` — der `exit`-Callback feuert **nur** beim
tatsächlichen Verlassen der Gruppe, daher bleibt interne Navigation (3-Button-Zeile etc.) intakt.
Bei Umsetzung die exakte API-Form prüfen (`exit`/`onExit` je nach Compose-Version); Fallback wäre die
directional-Variante (`left/up/down` an der Gruppe), aber der `exit`-Callback ist das korrekte
Boundary-Werkzeug. **Am Emulator (API 28 + 36) verifizieren**, dass:
  - LINKS mittlerer/rechter Editor-Button → linker Button (nicht Menü),
  - LINKS am linken Rand → Sektion,
  - HOCH/RUNTER an den Rändern = Stopp, interne Bewegung unverändert.

Die bisherige Einzel-`left`-Regel am ersten Element wird damit überflüssig (Gruppe deckt alle ab) —
beim Umbau entfernen oder belassen (redundant, harmlos).

### 2. Settings-eigener BackHandler
- `var detailFocused` per `onFocusChanged { detailFocused = it.hasFocus }` an der Detail-`focusGroup`
  tracken.
- `BackHandler(enabled = detailFocused) { selectedSectionFocusRequester.requestFocus() }`.
  - Fokus im Detail → BACK → Sektion (verbraucht).
  - Fokus auf Sektion → Handler aus → globaler MainActivity-Handler → Zahnrad.

**Kein Konflikt mit Inline-Editoren:** Provider-/EPG-/About-Overlay haben eigene, tiefer verschachtelte
`BackHandler` (`dismissEditor`, `dismissManualMapping`, `dismissGlobalSettings`, `onClose`). Die
gewinnen bei offenem Editor (innerster enabled Handler zuerst) → BACK schließt zuerst den Editor,
danach greift der Settings-BackHandler → Sektion. Bei der Umsetzung gegenprüfen, dass das so bleibt
(kein Editor-Close-Regress).

### 3. Linkes Menü (Sektions-Rail): LINKS = Stopp, HOCH-oben → Zahnrad
- **LINKS-Exit unterbinden** → `FocusRequester.Cancel` (nichts passiert; das Panel ist eh das linkeste).
- **HOCH von der obersten Sektion → Top-Nav-Zahnrad.** RECHTS (→ Detail) und BACK (→ Zahnrad, global)
  bleiben unverändert; HOCH/RUNTER innerhalb der Rail navigieren die Sektionen.
- Umsetzung analog zum Detail-Panel: Rail-Gruppen-Exit `left = Cancel`; `up` der obersten Sektion (bzw.
  HOCH-Exit der Rail) → Top-Nav-Fokus.

**Plumbing:** Der Top-Nav-Fokus (`topNavigationFocusRequester`) lebt in `MainActivity` (Feature kennt ihn
nicht). Dafür bekommt `SettingsRoute` **einen neuen Parameter** — entweder den `FocusRequester` der
Top-Nav oder ein `onNavigateUpToTopNav`-Callback (MainActivity ruft dann
`topNavigationFocusRequester.requestFocus()`, wie schon im globalen BACK). **Folge:** SettingsRoute-
Signatur ändert sich → die detekt-Baseline-Einträge (`LongMethod`/`LongParameterList` für SettingsRoute)
müssen wie zuletzt auf die neue Signatur nachgezogen werden (kein Wachstum, nur Hash-Update).

## Bereits im Code erprobte Muster (Referenz)
- `focusProperties { up = FocusRequester.Cancel }` (`AboutSettingsPanel.kt:228`) — directional Cancel.
- `focusProperties { left = … }` / `{ right = detailFocusRequester }` (SettingsRoute) — Richtungsziele.
- Eigene `BackHandler` in ProviderSettingsPanel/EpgSettingsPanel/AboutSettingsPanel.

## Nicht-Ziele
- Kein Umbau des globalen BACK-Verhaltens außerhalb Settings.
- Keine Änderung an Inline-Editor-Logik, an der Sektions-Auswahl oder am rechten Panelinhalt.
- Kein Fokus-„Wrap" (oben↔unten umbrechen) — Ränder sind harte Stopps.

## Test / Akzeptanz
Emulator API 28 **und** 36, langsame D-Pad-Klicks:
1. Zahnrad → Allgemein fokussiert → RECHTS → „App beim TV starten" → **BACK** → Fokus „Allgemein" →
   **BACK** → Zahnrad.
2. In Allgemein runter bis User-Agent (letztes) → **RUNTER** → nichts (bleibt).
3. Oberstes Element → **HOCH** → nichts (bleibt).
4. Runtergescrollt (z. B. Sprache) → **LINKS** → Fokus „Allgemein" (nicht „Speicher & Verlauf").
5. Provider-Editor mit 3-Button-Zeile: LINKS auf mittlerem Button → linker Button; LINKS auf linkem
   Button → Sektion. Editor offen: **BACK** schließt Editor (kein Sprung ins Menü).
6. Auf einer Sektion im linken Menü: **LINKS** → nichts (bleibt); **BACK** → Zahnrad; auf der obersten
   Sektion **HOCH** → Zahnrad.
7. Stichprobe in mehreren Sektionen (Playlists, EPG, Appearance, Playback, Backup, About), dass
   Boundary-Exit + BACK überall identisch greifen.
Gates: `.\gradlew.bat detekt`, `assembleDebug` grün.
