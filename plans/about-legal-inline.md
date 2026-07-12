# Plan: About/Legal – zwei Inline-Dokumente

Status: **Umgesetzt & auf Emulator verifiziert** (beide Dokumente inline, scrollbar, Zurück ohne Home-Sprung).

## Fokus-Falle (gelöst)
Die Top-Nav navigiert bei Fokus (`onFocused` → `focusRoute` → `selectedRoute`). Ein Inline-Swap, der die
fokussierte Zeile entfernt, resettet den Fokus auf Root → erste fokussierbare = Home-Tab → springt Home.
Lösung: Legal als **Overlay** über der weiterhin composed About-Liste (fokussierte Zeile bleibt); beim
Schließen zuerst Fokus zurück auf die (composed) Legal-Zeile, dann Overlay entfernen. UP von der Zurück-Pill
via `focusProperties { up = FocusRequester.Cancel }` gesperrt.

## Ziel
About-Bereich: die drei Rechts-Zeilen (Lizenzhinweise / Datenschutzinfo / Drittanbieter-Lizenzen)
ersetzen durch **zwei**: **Datenschutzbestimmungen** und **Nutzungsbedingungen**. Anzeige nicht mehr
als Popup, sondern **inline** im rechten Panel (voller, scrollbarer Text).

## Entscheidungen (vom Nutzer bestätigt)
- Lizenz: **GPL-3.0** (LICENSE-Datei ins Repo, Text von gnu.org).
- Anwendbares Recht: **Deutschland**.
- Kontakt: **GitHub-Issues** (https://github.com/Spegeli/vivicast/issues).
- Inhalt: an TiviMate-Terms angelehnt, aber auf Vivicast zugeschnitten (kein Backend/Account/
  Telemetrie/Werbung/Verkauf; alle Daten lokal; App verbindet nur zu nutzer-konfigurierten Quellen).

## Umsetzung
1. `LICENSE` (GPL-3.0 Volltext) im Repo-Root.
2. Strings de + en: `settings_legal_privacy_title/body`, `settings_legal_terms_title/body`,
   `common_back` ("Zurück"). Body = ein String mit `\n\n`-Absätzen.
3. `AboutSettingsPanel.kt`:
   - 3 Legal-Zeilen → 2 (Datenschutz, Nutzungsbedingungen), `onClick` setzt lokalen `legalPage`.
   - Popup (`AboutLegalDialog`) ersetzen durch Inline-Ansicht: bei `legalPage != null` LazyColumn mit
     Zurück-Pill (Initial-Fokus) + Titel + fokussierbare Absatz-Items (`body.split("\n\n")`), damit
     D-Pad scrollt. `BackHandler` → zurück zur Liste.
   - Enum auf zwei Werte (Privacy, Terms) reduzieren.
4. Ungenutzte Alt-Strings entfernen (`about_legal*`, `about_privacy`, `about_third_party`,
   zugehörige Hilfe-/Body-Strings) de + en.

## Gates
`assembleDebug` · `detekt` · Emulator-Render-Smoke. Danach Commit/Push nach Freigabe.
