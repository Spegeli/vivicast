# ViviCast TV Redesign Vision

## Zielbild

Dieses Redesign ist eine eigenständige, moderne Design-Vision für eine IPTV- und Medien-App auf Android TV. Es orientiert sich an aktuellen Premium-Streaming-Erlebnissen: wenige sichtbare Ebenen, große Inhalte, ruhige Bewegung, klare Fokusführung und eine immersive Detailfläche, die sich dynamisch an den ausgewählten Inhalt anfühlt.

Die Vorschau in `redesign_preview.html` zeigt eine Drei-Spalten-Struktur aus Sicherheitsabstand/Navigation, Master-Liste und Detail-/Vorschau-Bereich. Sie ist bewusst als visuelle Zielmarke angelegt, nicht als Kopie bestehender lokaler Layouts.

## Offizielle Design-Basis

Die Design-Entscheidungen folgen den offiziellen Android-TV-Grundlagen:

- Android TV stellt Content in den Mittelpunkt und muss aus etwa 3 Metern lesbar bleiben: https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv
- Layouts sollen klare D-pad-Achsen haben. Jede Richtung braucht eine erkennbare Bedeutung: https://developer.android.com/design/ui/tv/guides/styles/layouts
- Overscan-Sicherheit nutzt ca. 5 Prozent Rand. Bei 960 x 540 Designbasis sind das grob 48 dp horizontal und 24-28 dp vertikal.
- Fokus ist der wichtigste Interaktionszustand auf TV. Es darf immer nur ein primäres Element visuell eindeutig fokussiert sein: https://developer.android.com/design/ui/tv/guides/styles/focus-system
- Navigation auf TV nutzt eine sichtbare Rail bzw. einen Navigation Drawer mit priorisierten Zielen: https://developer.android.com/design/ui/tv/guides/components/navigation-drawer
- Das Farbsystem basiert auf Material 3 für TV, bevorzugt dunkle, filmische Flächen und zugängliche Kontraste: https://developer.android.com/design/ui/tv/guides/styles/color-system
- Typografie nutzt große, robuste Rollen für 10-Foot-Lesbarkeit: https://developer.android.com/design/ui/tv/guides/styles/typography
- Buttons, Karten und Listen reagieren auf Fokus mit Skalierung, Rahmen, Flächenwechsel und klarer Hierarchie: https://developer.android.com/design/ui/tv/guides/components/buttons

## Design-Philosophie

ViviCast soll sich wie ein modernes Streaming-Produkt anfühlen, aber die Geschwindigkeit einer Live-TV-App behalten. Nutzer sollen mit wenigen D-pad-Schritten zwischen Sendern, Programmen und Wiedergabe wechseln können. Die Oberfläche darf visuell hochwertig sein, aber nie die Fernbedienung behindern.

Kernprinzipien:

- Content zuerst: Sendername, aktuelles Programm, Zeitfenster und Wiedergabestatus sind wichtiger als erklärender Text.
- Ein Fokusanker: Die fokussierte Listenzeile steuert Detailbereich, Hintergrundstimmung und Primäraktion.
- Drei stabile Zonen: Navigation links, Auswahl in der Mitte, Kontext rechts.
- Immersion ohne Unruhe: Dynamische Farbverläufe und Ambient-Glow reagieren auf Inhalte, bleiben aber hinter Scrims und dunklen Oberflächen.
- TV-Lesbarkeit: Große Titel, kompakte Metadaten, kurze Texte, klare Zeilenhöhen.
- Remote-Logik: Hoch/runter bewegt Listenfokus, rechts öffnet Aktionen/Details, links geht zurück zur Navigation, OK startet oder bestätigt, Back schließt Overlays zuerst.

## Layout-Spezifikation

### Raster und Sicherheitsabstand

- Designbasis: 16:9, skalierbar von 1080p bis 4K.
- Safe Area: 5 Prozent links/rechts und oben/unten für kritische UI.
- Root-Layout: `BoxWithConstraints` mit innerem `PaddingValues(horizontal = 58.dp, vertical = 28.dp)` bei 1080p-orientierter Skalierung.
- Hauptstruktur: Drei Spalten.

Empfohlene Compose-Aufteilung:

- Navigation Rail: 80-96 dp breit.
- Master-Liste: 30-34 Prozent der verfügbaren Breite, Mindestbreite ca. 360 dp.
- Detailbereich: Restbreite, volle Höhe.
- Spaltenabstand: 20-28 dp.

### Navigation

Die Navigation ist eine kompakte, dauerhaft sichtbare Rail. Sie trägt nur Hauptziele mit Icons und optionaler Erweiterung bei Fokus.

Priorisierte Ziele:

- Search
- Live TV
- Movies
- Series
- Settings

Die Rail bleibt visuell sekundär. Der aktive Bereich ist sichtbar markiert, aber der aktuelle Content bleibt Hauptfokus.

### Master-Liste

Die Master-Liste ist die zentrale Live-TV- oder Medienauswahl. Jede Zeile enthält:

- Kanalnummer oder Content-Rang
- Logo/Poster-Miniatur
- Primärtitel
- aktuelles Programm oder Untertitel
- Kategorie/Provider-Chip

Fokussierte Zeile:

- Skalierung: ca. 1.04-1.08
- 3 dp Fokusrahmen oder deutlich sichtbare Außenlinie
- leicht hellere Container-Fläche
- weicher Glow in Akzentfarbe
- höhere Elevation als Nachbarzeilen

### Detail- und Vorschau-Bereich

Der Detailbereich ist keine statische Karte, sondern eine immersive Bühne. Er zeigt:

- dynamischen Hintergrund, abgeleitet aus Senderlogo, Poster oder Kategorie
- dunklen Scrim für Lesbarkeit
- Logo oder Artwork
- Programmtitel oder Filmtitel
- Zeitbereich, Fortschritt, nächstes Programm
- technische Badges wie 4K, HDR, Dolby, Untertitel, Audio
- Primäraktionen: Watch/Resume, Guide, Favorite, Audio, Subtitles

Die Detailfläche soll auch dann hochwertig wirken, wenn keine echten Bilder vorhanden sind. Dann erzeugt das System eine Ambient-Farbfläche aus Provider-/Kategorie-Farben.

## Farbsystem

Basis ist ein dunkles Material-3-TV-Schema:

- Background: fast schwarz, leicht bläulich
- Surface: transparente dunkle Flächen mit Blur/Scrim
- Primary: helles Cyan für Fokus, Fortschritt, aktive Zustände
- Secondary: kühles Blau/Violett für Tiefe
- Tertiary: warmer Akzent nur für ausgewählte Inhalte
- Error: Material-konformes Rot, sparsam verwendet

Kontrastregeln:

- Primärtext mindestens sehr hell auf dunklem Scrim.
- Metadaten maximal 60-75 Prozent Weiß, damit Hierarchie erhalten bleibt.
- Fokusfarbe muss unabhängig vom Inhalt sichtbar bleiben.
- Ambient-Farben dürfen nie direkt hinter kleinem Text ohne Scrim liegen.

## Typografie

Empfohlene Rollen:

- Hero title: Display/Headline, 48-64 sp bei 1080p.
- Section title: Headline/Title, 28-36 sp.
- Listen-Primärtext: Title, 20-24 sp.
- Listen-Sekundärtext: Body, 16-18 sp.
- Chips/Badges: Label, 13-15 sp.

Regeln:

- Keine langen erklärenden Absätze in primären TV-Flows.
- Zeilen im Master maximal zwei Textebenen.
- Detailbeschreibung maximal zwei bis drei Zeilen.
- Keine negative Laufweite.

## Komponenten

### ChannelListItem

Zustände:

- Default
- Focused
- Selected/Playing
- Disabled/Error
- Loading placeholder

Compose-Eigenschaften:

- `Modifier.focusable()`
- `onFocusChanged` aktualisiert Detailmodell
- `animateFloatAsState` für Skalierung
- `BorderStroke` oder `Glow` nur bei Fokus
- stabile Höhe, damit Fokuswechsel kein Layoutspringen erzeugt

### ImmersiveDetailPane

Zustände:

- Live channel
- Movie
- Series episode
- Empty/no selection
- Playback error

Compose-Eigenschaften:

- `Box` mit Hintergrundgradient/Scrim
- animierte Farbübergänge über `animateColorAsState`
- Content innerhalb Safe Area
- Fortschrittsleiste mit eindeutiger aktueller Zeit

### FocusActionRow

Aktionen:

- Watch/Resume
- Guide
- Favorite
- Audio
- Subtitles
- More

Regeln:

- Maximal eine primäre gefüllte Aktion.
- Sekundäraktionen als Outlined/Filled tonal.
- Fokus skaliert Container, nicht Text allein.

### TechnicalBadges

Badges sind kurz und scanbar:

- HD / 4K
- HDR
- Dolby
- 5.1
- CC
- Live

Sie stehen im Detailbereich und optional im Player-Overlay.

## Bewegung und Fokus

Bewegung muss die Orientierung verbessern:

- Fokuswechsel: 120-180 ms.
- Detail-Farbwechsel: 350-500 ms.
- Overlay ein/aus: 180-240 ms.
- Keine endlosen Animationen im Vordergrund.
- Parallax nur subtil und nicht in Listen, die schnell gescrollt werden.

Remote-Verhalten:

- Up/Down: Fokus in Liste.
- Right: Detailaktionen.
- Left: zurück zur Liste oder Rail.
- OK: fokussierte Zeile startet Playback; fokussierte Aktion führt Aktion aus.
- Back: Overlay/Detailmodus schließen, danach vorherige Navigationsebene.

## Compose-for-TV Implementierungs-Fahrplan

### Schritt 1: Design Tokens

Neue Tokens in einem TV-Theme bündeln:

- Farben: `TvColors`
- Typografie: `TvTypography`
- Shape: `TvShapes`
- Spacing: `TvSpacing`
- Focus: `TvFocusDefaults`

Ziel: Keine hart codierten Farben, Abstände oder Fokuswerte in Screen-Composables.

### Schritt 2: State-driven Screens

Screens rendern nur Modelle:

- `TvShellState`
- `NavRailItemState`
- `ContentListState`
- `ContentItemState`
- `DetailPaneState`
- `PlaybackBadgeState`

Previews müssen normale, leere, lange und dichte Zustände zeigen.

### Schritt 3: Drei-Spalten-Shell

Compose-Struktur:

```kotlin
@Composable
fun TvMediaShell(
    state: TvShellState,
    onNavSelected: (Destination) -> Unit,
    onItemFocused: (ContentId) -> Unit,
    onItemSelected: (ContentId) -> Unit,
    onActionSelected: (DetailAction) -> Unit,
) {
    BoxWithConstraints {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 58.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TvNavigationRail(...)
            MasterContentList(...)
            ImmersiveDetailPane(...)
        }
    }
}
```

### Schritt 4: Fokus- und Key-Handling

- `FocusRequester` pro Hauptzone.
- `focusProperties` für klare Links/Rechts-Wege.
- `onPreviewKeyEvent` nur für globale Remote-Verträge.
- Fokus nach Back stabil auf letzte sinnvolle Zeile zurücksetzen.
- Keine unsichtbaren Fokusziele.

### Schritt 5: Player-Overlay ableiten

Dasselbe visuelle System soll später das Fullscreen-Overlay prägen:

- Ambient-Daten aus aktuellem Stream.
- Gleiche Badges.
- Gleiche Aktionstasten.
- OK toggelt Overlay.
- Back schließt Overlay zuerst.
- Up/Down zappt Live-TV.

### Schritt 6: Validierung

Prüfen auf Android-TV-Emulator:

- 1080p und 4K Skalierung.
- Overscan-Sicherheit.
- D-pad-Navigation ohne Fallen.
- Fokus sichtbar auf jedem Inhalt.
- Lesbarkeit aus 3 m Entfernung.
- Lange Sendernamen und Programmtitel.
- Keine Layoutverschiebung durch Fokus-Skalierung.
- Fehler-, Lade- und Empty-Zustände.

## Übergang von HTML-Vorschau zu Produkt-UI

`redesign_preview.html` dient als statischer Interaktions-Prototyp. Für die echte App sollen nicht HTML oder Tailwind übernommen werden, sondern:

- Layout-Proportionen
- Fokusverhalten
- Farbstimmung
- Komponenten-Hierarchie
- Detailpane-Prinzip
- Motion-Timing

Die Compose-Umsetzung sollte danach schrittweise erfolgen: zuerst Theme/Tokens, dann Shell, danach einzelne Listen- und Detailkomponenten, zuletzt Player-Overlay und echte Datenanbindung.
