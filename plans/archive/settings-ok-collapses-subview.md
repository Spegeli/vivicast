# Plan: OK auf markierte Rail-Sektion klappt offenes Sub-Panel ein

Status: ABGESCHLOSSEN (2026-07-14) – assembleDebug/detekt/test grün, im Emulator vom Nutzer verifiziert (Collapse funktioniert je Sub-View, Fokus bleibt Rail). Kein Commit (offen).

## Ziel

Bist du in einem rechten Sub-Panel/Editor und gehst LINKS in die Rail (landest auf der Sektion),
klappt **OK auf dieser bereits markierten Sektion** das Sub-Panel ein und zeigt rechts wieder die
Overview der Sektion. RIGHT bleibt „reingehen" (letzter Fix), OK wird „einklappen".

## Bestätigte Entscheidungen

1. **Editoren mit Entwurf (Playlist / EPG-Quelle):** OK-auf-Sektion verwirft den Entwurf **still**,
   wie Cancel/BACK heute. Kein Speichern-Popup.
2. **Fokus nach dem Einklappen:** bleibt **links auf der Rail-Sektion**; rechts zeigt die Overview.
   (Der Nutzer ist beim OK-Druck auf der Rail fokussiert — das Einklappen des rechten Panels rührt den
   Rail-Fokus nicht an. Daher kein `pendingOverviewFocus` im Einklapp-Pfad.)

## Aktueller Stand (Recherche)

- Sub-View-Zustand liegt **intern** in den Panels: `showEditor` (ProviderSettingsPanel),
  `showEditor`/`showManualMapping`/`showGlobalSettings` (EpgSettingsPanel), `legalPage`
  (AboutSettingsPanel).
- **BACK klappt heute schon still ein** (`BackHandler` je Sub-View → `dismiss…`), Editoren verwerfen
  dabei ohne Popup. OK-auf-Sektion spiegelt also nur das bestehende BACK-Verhalten auf die OK-Taste.
- Rail-`onClick` heute ([SettingsRoute.kt:280-284 / 314-317]):
  `selectSection(section); pendingDetailFocus = true` — erkennt „gleiche Sektion" nicht und schließt
  kein Sub-Panel.

Betroffene Sub-Views (alle 5): Playlist-Editor, EPG-Quellen-Editor, Manuelle EPG-Zuordnung,
Globale EPG-Einstellungen, About-Rechtstexte (Privacy/AGB).

## Umsetzung

### 1. SettingsRoute — OK auf gleiche Sektion = Einklapp-Signal
- Neuer State `var collapseSubViewSignal by remember { mutableStateOf(0) }`.
- Rail-`onClick` (beide Stellen: Hauptsektionen + About):
  ```
  onClick = {
      if (section == selectedSection) {
          collapseSubViewSignal++      // gleiche Sektion: offenes Sub-Panel einklappen; Fokus bleibt Rail
      } else {
          selectSection(section)
          pendingDetailFocus = true    // andere Sektion: wie bisher in Detail springen
      }
  }
  ```
- `collapseSubViewSignal` an die drei Panels mit Sub-Views durchreichen (Provider, EPG, About). Die
  simplen Panels bekommen es nicht → OK auf deren bereits markierte Sektion ist ein No-Op (Fokus bleibt
  Rail).

### 2. Panels — Signal reagiert mit „still einklappen"
Je Panel `LaunchedEffect(collapseSubViewSignal) { … }`, das offene Sub-Views schließt — **ohne**
`pendingOverviewFocus` (Fokus bleibt Rail):
- **ProviderSettingsPanel:** `showEditor = false` (+ `editor = ProviderEditorState.newProvider(...)`,
  `message = null`, Verbindungstest-Reset wie in `dismissEditor`, aber ohne Park/Overview-Fokus).
- **EpgSettingsPanel:** `showEditor = false; showManualMapping = false; showGlobalSettings = false`
  (+ Editor-Reset/`message = null`).
- **AboutSettingsPanel:** `legalPage = null`.

Initial-Fire des `LaunchedEffect` (erste Komposition) ist harmlos: nichts offen → No-Op. Kein Guard nötig.

## Konsequenz (bewusst)

OK auf eine **bereits markierte simple Sektion ohne Sub-Panel** (z. B. Allgemein) springt nicht mehr in
den Detailbereich, sondern bleibt auf der Rail (Fokus rein geht weiter per RIGHT). Konsistente Regel:
**OK = einklappen (oder nichts), RIGHT = reingehen.** Kleiner Verhaltensunterschied zu heute; gewollt
laut Fokus-Entscheidung.

## Kein Doku-Impact

`design/interaction/focus.md` + `nav.md` regeln das Zwei-Spalten-OK-Verhalten nicht normativ. Reine
Fokus-/Navigations-Feinheit, keine sichtbare-UI-/Label-/Datenmodell-Änderung → keine vivicast-docs-Änderung.

## Gates

- `.\gradlew.bat detekt` (evtl. Baseline-Resync bei Signaturänderung der 3 Panels — prüfen ob nur Signatur).
- `.\gradlew.bat assembleDebug`
- Fokus/Navigation manuell im Emulator (Nutzer): je Sub-View LINKS→OK klappt ein, Fokus bleibt Rail,
  RIGHT geht wieder rein; BACK weiter wie gehabt.

## Offene Detail-Checks bei Umsetzung

- Editor-Reset im Einklapp-Pfad exakt an `dismissEditor` angleichen (gleiche Felder), nur ohne
  `onParkFocusBeforeEditor()`/`pendingOverviewFocus`.
- Prüfen, dass der `LaunchedEffect`-Initial-Fire beim Sektionswechsel wirklich No-Op ist (kein
  ungewolltes Schließen eines gerade frisch geöffneten Sub-Panels — Sektionswechsel komponiert das
  Panel neu mit `showX = false`, also unkritisch).
