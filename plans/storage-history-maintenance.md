# Plan: Storage & History (Einstellungen → Speicher & Verlauf)

Status: **abgeschlossen** (2026-07-12). Umbau C umgesetzt + am TV-Emulator verifiziert;
Gates grün (assembleDebug/detekt/test). A/B und Optional C/D verworfen (Begründung unten).

## Ausgangslage (verifiziert)

Panel: `feature/settings/.../MaintenanceSettingsPanel.kt` (Sektion „Speicher & Verlauf").
Backend aller sichtbaren Aktionen ist vollständig verkabelt:

- Cache-Größe / -leeren: ViewModel → `mediaCacheStore.stats()/clear()` (`FileMediaCacheStore`, `cacheDir/media`, nur Bilder).
- History-Löschen: App-hoisted `AppContainer.clearHistory(target)` →
  - LiveTv → `channel_history` (deleteAll)
  - Movies → `playback_progress WHERE mediaType='MOVIE'`
  - Series → `playback_progress WHERE mediaType IN ('SERIES','EPISODE')`
  - Search → `search_history`

## Verworfene Punkte

- **Fix A/B (Coil-Disk-Cache zählen/leeren/deckeln): gegenstandslos.**
  `coil-network-okhttp` ist in keinem Modul verdrahtet (nur `coil-compose`), und
  `resolve*ImageModel` liefert `?.file` (File aus `mediaCacheStore`) oder `null` — nie eine URL.
  Coil fetcht also nie übers Netz, sein Disk-Cache bleibt leer. `mediaCacheStore` ist der einzige
  Bild-Disk-Cache und wird bereits korrekt gezählt + geleert. Kein Handlungsbedarf.
- **Optional C (History-Retention-Cap): YAGNI.** `channel_history` (Unique `providerId,channelId`)
  und `playback_progress` (Unique `providerId,mediaType,mediaId`) sind per Upsert an die Katalog-
  Größe gekoppelt, wachsen nicht unbegrenzt. Nur `search_history` ist unbegrenzt — und bereits
  via `trimSearchHistory` gedeckelt.
- **Optional D (`android_tv_search_entries` mitlöschen): Kategoriefehler.** Das ist ein Katalog-
  Spiegel für die Android-TV-Global-Suche (Rebuild bei jedem Refresh), kein getippter Suchverlauf.

## Umbau C (abgeschlossen): 5 History-Zeilen → 1 Button + Checkbox-Popup

Reiner UI-Layer. Backend unverändert.

1. `core/designsystem/.../VivicastInputs.kt`: neue `VivicastCheckbox` (Canvas-Häkchen, reine Visual).
2. `core/designsystem/.../VivicastDialogs.kt`: `VivicastDialogActions` erhält `primaryEnabled: Boolean = true`
   (um „Löschen" bei leerer Auswahl zu deaktivieren).
3. `feature/settings/.../MaintenanceSettingsPanel.kt`:
   - 5 History-Zeilen (Alles + Live/Movie/Series/Search) → eine Zeile „Verlauf löschen".
   - Neuer `HistoryClearDialog`: 4 Checkbox-Zeilen (Live-TV/Filme/Serien/Suche), lokaler
     `Set<HistoryClearTarget>`, Warntext, Aktionen Abbrechen | Löschen; „Löschen" disabled wenn leer.
   - Fokus beim Öffnen auf erste Checkbox (Live-TV) via `VivicastDialog(initialFocus=...)`.
   - Fokus-Return auf „Verlauf löschen"-Zeile nach Schließen (`awaitFrame()`/`requestFocus()`).
   - Cache-Confirm bleibt als eigener Dialog.
4. Callback Set-basiert: `onClearHistory: (Set<HistoryClearTarget>) -> Unit` durch
   `MaintenanceSettingsPanel` → `SettingsRoute` → `MainActivity` (`forEach { clearHistory(it) }`).
5. Strings (designsystem values + values-en): neue Kategorie-Labels + Dialog-Body + Row-Value;
   `settings_history_clear_row_help` umformuliert. Alte selektive `maintenance_*`-Strings bleiben
   (ungenutzt, kein Gate-Bruch) — optionale Aufräumung später.

## Gates

`.\gradlew.bat detekt` + `assembleDebug` + `test`.
