# Plan: Rechts-Reentry in Settings-Detailpanel bei Inline-Editor/Sub-Panel

Status: ABGESCHLOSSEN (2026-07-14) – assembleDebug/detekt/settings-test grün, im Emulator verifiziert. Kein Commit (Task-Vorgabe).

## Nachtrag: RIGHT-Grenze im Detailpanel

Beim Testen fiel auf: RECHTS auf einer Detailzeile (nichts rechts daneben) sprang hoch aufs Top-Nav-Zahnrad.
Ursache: `exitDetailPanel` fing RIGHT nicht ab (`else -> {}`) → Default-Fokussuche verließ das Panel.
Fix: `FocusDirection.Right` in den `cancelFocusChange()`-Zweig aufgenommen (rechtes Panel ist die
äußerste Spalte, RIGHT stoppt jetzt wie HOCH/RUNTER). Interne Button-Reihen unberührt (onExit feuert nur
an der Gruppengrenze).

## Symptom (vom Nutzer gemeldet)

Zwei-Spalten-Settings: links Sektions-Rail, rechts Detailpanel.
- Sektion mit reinem Overview (z. B. **Allgemein**): links → RECHTS landet im Detail, LINKS zurück
  in die Rail, RECHTS wieder rein. Funktioniert.
- Sektion mit Inline-Editor/Sub-Panel (**Playlist bearbeiten**, **EPG > Globale EPG-Einstellungen**):
  Editor/Sub-Panel offen, LINKS zurück in die Rail → **RECHTS kommt nicht mehr ins rechte Panel**.

## Root Cause

Die Rail-Items setzen `focusProperties { right = detailFocusRequester }`
([SettingsRoute.kt:290/323](../feature/settings/src/main/java/com/vivicast/tv/feature/settings/SettingsRoute.kt)).
`detailFocusRequester` wird über `detailFirstFocusModifier`
([SettingsRoute.kt:217](../feature/settings/src/main/java/com/vivicast/tv/feature/settings/SettingsRoute.kt))
an die **erste Zeile des Overviews** gehängt (jedes Panel bekommt `firstFocusModifier`).

Sobald ein Inline-Editor / Sub-Panel das Overview **ersetzt** (nicht überlagert), ist das Overview
nicht mehr komponiert → der Knoten mit `detailFocusRequester` existiert nicht → `right`-Ziel ist
unattached → `requestFocus()` ist ein No-Op → RECHTS bewegt nichts.

Beweisstellen:
- `ProviderSettingsPanel`: `firstFocusModifier` nur im Overview-Zweig
  (`if (!showEditor) … ProviderOverviewPanel(firstFocusModifier = firstFocusModifier)`,
  [:205/:210](../feature/settings/src/main/java/com/vivicast/tv/feature/settings/ProviderSettingsPanel.kt)).
  Der `ProviderEditor`-Zweig bekommt **keinen** `firstFocusModifier`.
- `EpgSettingsPanel`: Overview-Zeile bekommt `firstFocusModifier.focusRequester(globalSettingsRequester)`
  ([:378](../feature/settings/src/main/java/com/vivicast/tv/feature/settings/EpgSettingsPanel.kt)),
  aber das **Globale-Einstellungen-Sub-Panel** ersetzt es durch einen *frischen* Requester:
  `firstFocusModifier = Modifier.focusRequester(globalFirstFocus)` ([:344]) — der echte
  `firstFocusModifier` (mit `detailFocusRequester`) geht verloren. `EpgSourceEditor`- und
  `ManualEpgMappingPanel`-Zweige bekommen ebenfalls keinen `firstFocusModifier`.

Warum Overview-Sektionen funktionieren: deren erste Zeile ist immer komponiert → `detailFocusRequester`
immer attached.

**Bereits korrekt gelöst:** `AboutSettingsPanel` reicht `firstFocusModifier` in seine Rechts-Sub-Seite
(Datenschutz/AGB) durch ([:202/:235]) — genau das Muster, das den Provider/EPG-Sub-Views fehlt.

## Betroffene Sub-Views (gleiche Fehlerklasse)

| Sub-View | Panel | Gemeldet? |
|---|---|---|
| Playlist-Editor (`ProviderEditor`) | ProviderSettingsPanel (`showEditor`) | ja |
| Globale EPG-Einstellungen (`EpgGlobalSettingsPanel`) | EpgSettingsPanel (`showGlobalSettings`) | ja |
| EPG-Quellen-Editor (`EpgSourceEditor`) | EpgSettingsPanel (`showEditor`) | nein (gleiche Klasse) |
| Manuelle EPG-Zuordnung (`ManualEpgMappingPanel`) | EpgSettingsPanel (`showManualMapping`) | nein (gleiche Klasse) |

Empfehlung: alle vier fixen (identischer Fix, sonst bleibt der Bug in den Geschwistern).

## Fix (Approach A — bestehendes About-Muster, empfohlen)

`firstFocusModifier` (trägt `detailFocusRequester`) an die **erste fokussierbare Zeile der jeweils
sichtbaren** Detail-Ansicht hängen — nicht nur ans Overview. Nur eine Ansicht ist gleichzeitig
komponiert, also kein Doppel-Attach.

1. **Globale EPG-Einstellungen** — trivial: [EpgSettingsPanel.kt:344]
   `Modifier.focusRequester(globalFirstFocus)` → `firstFocusModifier.focusRequester(globalFirstFocus)`
   (gleiche Kombi wie die Overview-Zeile :378; `EpgGlobalSettingsPanel` akzeptiert `firstFocusModifier`
   bereits, :103/:113). Ein-Token-Änderung.

2. **ProviderEditor** — neuen Param `entryFocusModifier: Modifier = Modifier`; an die Initial-Fokus-Zeile
   hängen (Aktiv-Toggle-Item `providerActiveToggleItem`, ProviderEditor:202 — dieselbe Zeile, die im
   Edit `toggleFocus` bekommt). `ProviderSettingsPanel` übergibt `entryFocusModifier = firstFocusModifier`
   im `showEditor`-Zweig.

3. **EpgSourceEditor** — analog: `entryFocusModifier`-Param, an die erste Zeile (Toggle,
   EpgSourceEditor:151-Zielknoten) hängen; `EpgSettingsPanel` übergibt `firstFocusModifier` im
   `showEditor`-Zweig.

4. **ManualEpgMappingPanel** — `firstFocusModifier`-Param ergänzen (fehlt aktuell) + an die erste
   fokussierbare Zeile hängen; `EpgSettingsPanel` übergibt `firstFocusModifier` im
   `showManualMapping`-Zweig.

Verhalten danach: RECHTS aus der Rail landet auf der ersten Zeile der aktuell sichtbaren Detail-Ansicht
(Overview **oder** Editor/Sub-Panel) — konsistent mit den Overview-Sektionen. LINKS-Exit
(`exitDetailPanel`, Container-Ebene) bleibt unberührt. Die Editor-eigene Auto-Fokus-Logik beim Öffnen
bleibt unberührt (nichts fordert `detailFocusRequester` beim Öffnen an).

## Verworfene Alternative (Approach B)

Editor/Sub-View in einen fokussierbaren Wrapper-`Box(focusGroup + focusRequester(detailFocusRequester)
+ focusProperties{enter=Default})` packen → nur 2 Panels berührt, aber neue Fokusgruppen-Schicht um die
Editoren (Risiko bei interner Traversierung/Left-Exit) und ein anderes Muster als der Rest. Approach A
folgt dem bereits im Code bewährten About-Muster → weniger Risiko.

## Kein Doku-Impact

`design/interaction/focus.md` + `nav.md` spezifizieren das Zwei-Spalten-Reentry nicht normativ. Reine
Fokus-Korrektur, keine sichtbare-UI-/Label-/Nav-Struktur-Änderung → keine vivicast-docs-Änderung.

## Gates

- `.\gradlew.bat detekt` (Signatur-Änderung an 3 Composables → evtl. Baseline-Resync wie gehabt, prüfen
  ob nur Signatur).
- `.\gradlew.bat assembleDebug`
- Fokus-Verhalten manuell im Emulator (Nutzer): je Sub-View LINKS→RECHTS-Reentry für alle vier.

## Offene Detail-Checks bei Umsetzung

- Zeigt `ProviderEditor` im Add-Modus den Aktiv-Toggle als erste Zeile? Falls nicht, Attach-Punkt =
  erste tatsächlich sichtbare Zeile (Name-Feld).
- `ManualEpgMappingPanel` erste fokussierbare Zeile identifizieren (Provider-/Kanal-Auswahl).
