# Vivicast — Logo & App-Grafiken

Quell-Dateien (SVG) für App-Icon, Banner, Splash und Play-Store-Assets.
Gerenderte Rasterbilder liegen in [`rendered/`](rendered/).

**Status:** ✅ **In die App verdrahtet** (Launcher-Icon, Banner, Splash, Manifest) —
`assembleDebug` grün. Play-Store-Rasterbilder gerendert, Upload durch den Nutzer offen.
Details siehe [Integration erledigt](#8-integration-erledigt).

---

## 1. Konzept

**Wortmarke:** `Vivicast` · **Bildmarke:** V+C-Monogramm + Play-Dreieck.

- **C** (weiß) als äußerer Ring, **Öffnung nach rechts** (echter Kleinbuchstabe „c", nicht spiegelverkehrt, kein geschlossener Ring).
- **V** (cyan) mittig **in** der C-Öffnung — zentriert, darf das C nicht berühren/quetschen.
- **Play-Dreieck** (cyan, Verlauf, runde Ecken) rechts daneben — nur wo Platz ist.

Gewählte Richtung: **„Variante 3" = weit offenes c**. Ausgeschlossen wurden reine
Streaming-Klischees (Play-Ring, Equalizer, Broadcast-Wellen) als zu generisch.

### Zwei Kompositionen (wichtig!)

Es gibt **bewusst zwei** Anordnungen — nicht verwechseln:

| | Icon-Komposition | Banner-/Feature-Komposition |
|---|---|---|
| Einsatz | Launcher-Icon, Splash, 512-Store-Icon | TV-Banner, Feature Graphic |
| C-Radius | **30** (nach links versetzt) | **34** (zentriert) |
| Play | **klein, integriert** rechts im Quadrat | **größer, separat** rechts neben C |
| Wortmarke | nein (nur Bildmarke) | ja („Vivicast" Pflicht im Banner) |
| Grund | Quadrat 1:1, muss bei ~48px lesbar sein | 16:9, viel Breite → Play + Text passen |

Merksatz: **Icon = kompakt (C+V+kleines Play). Banner = breit (C+V + großes Play + „Vivicast").**

---

## 2. Farben (Brand-Tokens)

Aus `vivicast-docs .../vivicast_visual_tokens_v2.json` bzw. `VivicastColors.kt`.

| Rolle | Hex | Verwendung |
|---|---|---|
| Hintergrund (Navy) | `#050914` | Icon-/Banner-Grund, Adaptive-Background-Layer |
| C (weiß) | `#FFFFFF` | C-Ring |
| V / Text-Akzent | `#00C8FF` | V, erster Buchstabe „**V**ivicast" |
| Text primär | `#F8FAFC` | „ivicast" |
| Text tertiär | `#94A3B8` | Feature-Tagline |
| Play-Verlauf hell | `#5FE0FF` | Play oben |
| Play-Verlauf tief | `#00B4F0` | Play unten |

Play-Verlauf (in jeder SVG als `<linearGradient id="pg">`):
```xml
<linearGradient id="pg" x1="0" y1="0" x2="0.35" y2="1">
  <stop offset="0" stop-color="#5FE0FF"/>
  <stop offset="1" stop-color="#00B4F0"/>
</linearGradient>
```

---

## 3. Geometrie (Master-Koordinaten, viewBox `0 0 108 108`)

Alle Pfade sind exakt und wiederverwendbar. Strichbreiten in User-Units.

### Icon-Komposition (C r30 + V + kleines Play) — horizontal zentriert
```
C  (weiß, sw 10, cap round):
   M64.85 78.3 A30 30 0 1 1 64.85 29.7
V  (cyan #00C8FF, sw 11, cap+join round):
   M33.25 44 L47.25 68 L61.25 44
Play (fill+stroke url(#pg), sw 4, join round):
   M75.75 45 L75.75 63 L93.75 54 Z
```

### Banner-Komposition (C r34 + V + großes Play)
```
C  (weiß, sw 11, cap round):
   M74 81.5 A34 34 0 1 1 74 26.5
V  (cyan, sw 12, cap+join round):
   M40 45 L54 70 L68 45
Play (fill+stroke url(#pg), sw 5, join round):   [in 320×180-Banner-Koordinaten]
   M97 77 L97 103 L120 90 Z
Wortmarke: <text x=140 y=103 font-size=32 font-weight=700>
   <tspan fill="#00C8FF">V</tspan>ivicast   (fill #F8FAFC)
```

### Horizontale Zentrierung (wichtig)
Beide Kompositionen sind **auf die Canvas-Mitte zentriert** (Bounding-Box-Mitte = Mitte).
Konkrete Offsets, falls du Elemente verschiebst:
- **Icon:** Roh-Bbox-Mitte lag bei x≈51.25 → alle Pfade **+2.75** (jetzt Mitte = 54). Danach `scale`
  um Pivot 54 (Foreground .78 / Legacy+512 .9 / Splash .8) — bleibt zentriert.
- **Banner:** Block *Marke+Play+Wortmarke* in `translate(20.6 0)` → Ränder links = rechts ≈ 43.
  (Wortmarke „Vivicast" @32 Bold Segoe UI = **116 px** breit — daraus berechnet.)
- **TV-Banner 1280×720:** `scale(4)` über den **zentrierten** Banner-Block.
- **Feature 1024×500:** Block `translate(121.7 -32) scale(2.8)` → Block-Mitte auf 512.
  **Tagline** mittig unter der Wortmarke: `x=676 text-anchor=middle` (676 = Wortmarken-Mitte,
  *nicht* Canvas-Mitte — sonst rutscht sie unter die Bildmarke nach links).

### Warum der C-Bogen so aussieht (Stolperfalle!)
Das „c mit Öffnung rechts" entsteht als **großer Bogen** (`large-arc-flag = 1`)
zwischen zwei Punkten auf der **rechten** Seite. Reihenfolge: **unterer** Startpunkt →
**oberer** Endpunkt, `sweep-flag = 1`. Startet man am oberen Punkt, kippt SVG auf den
anderen Mittelpunkt → **spiegelverkehrtes c (ɔ)**. Das war ein früher Bug. Endpunkt-Y
muss `cy ± r·sin(θ)` sein (θ = halber Öffnungswinkel), nicht frei geschätzt — sonst
verzerrt der Bogen und das V rutscht ins C.

---

## 4. Assets & Dateien

### Im APK (auf dem Gerät)

| Datei | Ziel-Format | Größen / Dichten | Manifest / Ort |
|---|---|---|---|
| `ic_launcher_foreground.svg` | Vektor → `ic_launcher_foreground.xml` | 108×108 dp, Motiv im **72dp-Safe** (scale .78) | `mipmap-anydpi-v26` |
| `ic_launcher_background.svg` | Vektor → `ic_launcher_background.xml` (oder Farbe `#050914`) | 108×108 dp | `mipmap-anydpi-v26` |
| `icon_legacy.svg` | PNG | mdpi 80² · hdpi 120² · xhdpi 160² · xxhdpi 240² · xxxhdpi 320² | `mipmap-*dpi/ic_launcher.png` |
| `splash_icon.svg` | Vektor | Canvas 432dp, Motiv im inneren 288dp | Splash-Theme (`windowSplashScreenAnimatedIcon`) |
| `banner_320x180.svg` | Vektor/PNG | mdpi 160×90 · hdpi 240×135 · **xhdpi 320×180 (min)** · xxhdpi 480×270 · xxxhdpi 640×360 | `drawable/banner`, `android:banner` |

Adaptive-Icon → `android:icon="@mipmap/ic_launcher"` (fehlt aktuell im Manifest!).

### Play Console (Upload, nicht im APK)

| Datei | Größe | Format |
|---|---|---|
| `playstore_icon_512.svg` | 512×512 | 32-bit PNG, ≤1024 kB |
| `tv_banner_1280x720.svg` | 1280×720 | JPEG / 24-bit PNG, **kein Alpha** |
| `feature_graphic_1024x500.svg` | 1024×500 | JPEG / 24-bit PNG, **kein Alpha** |
| TV-Screenshots | 16:9, 320–3840px, ≥1 (max 8) | JPEG / 24-bit PNG — **aus echten App-Screens**, nicht aus dem Logo |

### Extra
| Datei | Zweck |
|---|---|
| `mark-icon.svg` | Reine Bildmarke (C+V+Play), transparent — Master für alle Icons |
| `mark-lockup.svg` | Bildmarke + Wortmarke „Vivicast", transparent — für Docs/Präsentation |

### `rendered/` — gerenderte Rasterbilder
Aus den SVGs per Browser-Canvas gerendert (echte Segoe-UI-Wortmarke). Neu erzeugbar
mit dem Render-Setup (siehe [Abschnitt 5](#5-render-svg--png)).

| Datei | Größe | genutzt als |
|---|---|---|
| `ic_launcher_48/72/96/144/192.png` | 48–192² | Legacy-Launcher → `app/.../mipmap-{m,h,x,xx,xxx}dpi/ic_launcher.png` |
| `banner_320x180/480x270/640x360.png` | 3× 16:9 | Banner → `app/.../drawable-{x,xx,xxx}hdpi/banner.png` |
| `ic_playstore_512.png` | 512² | Play Console Hi-Res-Icon (Upload) |
| `tv_banner_1280x720.jpg` | 1280×720 | Play Console TV-Banner (Upload, kein Alpha) |
| `feature_graphic_1024x500.jpg` | 1024×500 | Play Console Feature Graphic (Upload, kein Alpha) |

---

## 5. Export (SVG → PNG)

Empfohlen: **Inkscape** oder **rsvg-convert** oder **resvg**.

```bash
# Beispiel Legacy-Icon-Dichten
for s in 80 120 160 240 320; do
  rsvg-convert -w $s -h $s icon_legacy.svg -o ic_launcher_$s.png
done
# Play-Store (kein Alpha! -b weiß? NEIN → Navy-Grund ist bereits im SVG,
# also opake Fläche vorhanden; PNG als 24-bit ohne Alpha exportieren)
rsvg-convert -w 512  -h 512 playstore_icon_512.svg      -o ic_playstore_512.png
rsvg-convert -w 1280 -h 720 tv_banner_1280x720.svg      -o tv_banner_1280x720.png
rsvg-convert -w 1024 -h 500 feature_graphic_1024x500.svg -o feature_graphic.png
```

**Wichtig für Play-Banner/Feature/512:** kein Alpha-Kanal. Der Navy-Grund (`#050914`)
ist als opake `<rect>` in den SVGs enthalten → beim 24-bit-PNG-Export entsteht kein
Transparenz-Problem. Trotzdem prüfen (`file`/Bildbetrachter): **keine Transparenz**.

**Schrift:** Banner/Feature nutzen `<text>` mit System-Schrift (system-ui/Segoe/Roboto,
Bold). Für **konsistente** PNGs die Wortmarke vor dem Export **in Pfade wandeln**
(Inkscape: Objekt → Objekt in Pfad umwandeln) oder eine feste Schrift einbetten —
sonst hängt die Darstellung vom Render-Host ab. Für die Vektor-Assets im APK
(`banner`) wird die Wortmarke ohnehin als Pfad benötigt (Android rendert kein
Web-`<text>`), also **vor der APK-Integration Wortmarke → Vektor-Pfade**.

---

## 6. Do / Don't

**Do**
- Motiv **horizontal zentriert** halten (siehe [Zentrierung](#horizontale-zentrierung-wichtig)) — Bbox-Mitte = Canvas-Mitte, Feature-Tagline mittig unter der Wortmarke.
- C-Öffnung **immer nach rechts**.
- V **zentriert** in der C-Öffnung, Luft zum Ring lassen.
- Navy-Grund `#050914` beibehalten.
- Banner **muss die Wortmarke** „Vivicast" enthalten (Google-TV-Pflicht: Text im Banner).
- Play-Dreieck: gefüllt, runde Ecken, `pg`-Verlauf, ~quadratisches Seitenverhältnis (Breite ≈ Höhe).
- Feature-Graphic-Abstände = **exakt skalierter Banner** (×2.8 in `feature_graphic`), damit V↔Play-Abstand identisch bleibt.

**Don't**
- Kein spiegelverkehrtes c (ɔ).
- Play **nicht** ins kleine Launcher-Icon quetschen, wenn dadurch alles winzig wird — Icon-Komposition nutzt bewusst das **kleine** integrierte Play.
- Kein Alpha in Play-Store-Banner/Feature/512.
- Keine Extra-Ränder um das Logo (werden von Launcher-Masken beschnitten).
- Adaptive-Vordergrund nicht über den **72dp-Safe** hinaus (sonst Masken-Clipping) — daher `scale .78`.

---

## 7. Entscheidungs-Log

- Marke = **V+C-Monogramm**, weil reine Streaming-Icons (Play/Equalizer/Broadcast) zu generisch.
- „großes V + kleines c" verworfen (c verschwand im V) → stattdessen **großes C umschließt V**.
- Play zunächst als **Outline** (User-Skizze) → später **gefüllt + rund + Verlauf** („nicht so plump").
- Play im Icon: getestet, klein aber ok → **bleibt drin** (User-Wunsch). Alternative „Icon nur CV, Play nur im Banner" ist dokumentiert, falls man's kleiner mag.
- Zwei-Kompositionen-System (Icon vs Banner), weil C+V+Play im 1:1 sonst quetscht.

---

## 8. Integration erledigt

Umgesetzt im `app`-Modul (`minSdk = 26`), `assembleDebug` grün:

| Was | Wo |
|---|---|
| Adaptive-Icon (API 26+) | `app/.../res/mipmap-anydpi-v26/ic_launcher.xml` → `drawable/ic_launcher_foreground.xml` (C+V+Play, Verlauf, Safe-Zone scale .78) + `drawable/ic_launcher_background.xml` (Navy) |
| Legacy-Icon (< API 26 — nun unter `minSdk`, moot; als harmloser Fallback belassen) | `app/.../res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher.png` (48/72/96/144/192) |
| Banner | `app/.../res/drawable-{x,xx,xxx}hdpi/banner.png` (320×180 / 480×270 / 640×360) — Platzhalter `drawable/banner.xml` entfernt |
| Manifest | `android:icon="@mipmap/ic_launcher"` + `android:roundIcon="@mipmap/ic_launcher_round"` + `android:banner` auch auf der `MainActivity` (alle Referenz-TV-Apps machen das) |
| roundIcon | `mipmap-anydpi-v26/ic_launcher_round.xml` (Adaptive) + `mipmap-{m,h,x,xx,xxx}dpi/ic_launcher_round.png` (Legacy-Fallback) |
| Splash (API 31+) | `app/.../res/values-v31/styles.xml`: `windowSplashScreenBackground=#050914`, `windowSplashScreenAnimatedIcon=@drawable/ic_launcher_foreground` |

**Bewusste Entscheidungen:**
- Icon-VectorDrawable nutzt **Gradient-Stroke/Fill** fürs Play — ab API 24 gerendert, also auf allen
  unterstützten Geräten (`minSdk = 26`). Der Adaptive-Vektor lädt ab API 26; das Legacy-PNG darunter ist
  nun moot (kein Gerät unter `minSdk`).
- **Kein** `androidx.core:core-splashscreen` hinzugefügt (wäre neue Dependency + Theme-Migration).
  Splash läuft rein über die **native** SplashScreen-API (Theme-Attribute, nur API 31+).
  API 26–30: unverändertes Verhalten (Default-Splash auf `windowBackground`).
- Banner als **PNG** (nicht VectorDrawable), weil die Wortmarke sonst als Vektor-Pfad
  vorliegen müsste. PNG bäckt die Segoe-UI-Wortmarke ein — für ein Banner Standard.

### Offen / Todo
- [x] **Emulator Api 36 (Ceiling)** (Google-TV-Launcher): Banner-Kachel erscheint, App startet normal.
- [x] **Emulator Api 28 (Floor)** (klassischer Leanback-Launcher): Banner-Kachel rendert im Apps-Drawer, App startet normal.
- [ ] **Play Console**: `rendered/ic_playstore_512.png`, `tv_banner_1280x720.jpg`,
      `feature_graphic_1024x500.jpg` hochladen (macht der Nutzer). Vorher **kein Alpha** verifizieren.
- [ ] TV-Screenshots aus echten App-Screens erzeugen.
- [ ] Optional: eigene Marken-Schrift statt Segoe UI, dann Banner/Feature neu rendern
      (Wortmarke vor Nicht-System-Font-Wechsel in Pfade wandeln).

### Troubleshooting: Banner-Kachel bleibt leer im Emulator

**Symptom:** App-Kachel im (klassischen) Leanback-Launcher ist leer, obwohl `android:banner`
korrekt gesetzt und die Banner-Ressource vorhanden ist.

**Ursache:** Der Leanback-Launcher / das TV-System **cacht Banner-Bitmaps**. `adb install -r`,
`pm clear com.google.android.leanbacklauncher` und `am force-stop` löschen diesen Cache
**nicht** zuverlässig.

**Fix:** **Emulator komplett neu starten** (`adb reboot` bzw. Cold Boot). Danach rendert der
Banner. → Bei „Banner/Icon zeigt nicht" in einem Emulator **immer zuerst rebooten**, bevor man
Ressourcen/Manifest verdächtigt. (Icon-Verlauf, roundIcon usw. waren hier NICHT die Ursache.)

**Diese Datei ist die Quelle der Wahrheit für das Logo.** Bei Weiterarbeit in einer
neuen Session: zuerst hier lesen, dann die SVGs — nicht neu raten.
