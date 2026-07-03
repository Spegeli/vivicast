# Vivicast — Projekt-Audit & Weiterentwicklungsplan (2026-07-03)

> Audit-/Planungslauf. **Keine Produktivcode-/Doc-/Test-Änderungen, kein Commit/Push.** Alle Laufzeit-
> tests strikt sequenziell, private Quellen nach Test gelöscht (max. 1 Verbindung eingehalten). Keine
> Credentials/URLs/Senderdetails in diesem Bericht. Zwei Wegwerf-Instrumentierungstests wurden nur zur
> Laufzeitmessung erzeugt und nach Nutzung wieder gelöscht.

## 0. Vorabprüfung

- Branch `main`, in sync origin (`5f8b4b72`). Arbeitsbaum bis auf **vorbestehende** Owner-Löschung
  `plans/M3U-CONTENT-CLASSIFICATION-PLAN.md` (unstaged `D`) clean.
- `../vivicast-docs` ✅, `../IPTV-Apps` ✅ (AerioTV MIT · BBC Apache-2.0 · OwnTV GPLv3 · StreamVault
  Non-Commercial). Emulator `emulator-5554` (API36 TV) läuft.
- Statischer Audit via 3 Explore-Agents: PRD/14 ADRs/Design (Soll), Referenz-Apps, Code-Restbereiche.

## 1. Build-/Test-/Qualitätsgates

| Gate | Ergebnis | Anmerkung |
|---|---|---|
| `detekt` | ✅ SUCCESS | Baseline 36, keine neuen Verstöße |
| `assembleDebug` | ✅ SUCCESS | — |
| `test` (JVM, alle Module) | ❌ **FAIL** | nur `:worker` — Details F1 |
| `:iptv:m3u/:iptv:xtream/:data:playback/:feature:settings:testDebugUnitTest` | ✅ | grün |
| `test` **ohne** `:worker`-Tests | ✅ SUCCESS | Rest der Suite grün |
| `:data:media:compileDebugAndroidTestKotlin` | ✅ | — |
| `:app:installDebug` + Start | ✅ | App startet, kein Crash |

## 2. Laufzeitbefunde (Emulator, sanitisiert)

**F1 — `:worker` Unit-Tests kompilieren nicht (P1, Regression aus Stufe B).**
`worker/src/test/.../RefreshExecutionTest.kt:418` `FakeCatalogImportRepository` implementiert das in
Stufe B neu eingeführte `CatalogImportRepository.importM3uCatalog(...)` **nicht** → Kompilierfehler in
`:worker:compileDebug/ReleaseUnitTestKotlin`. Zusätzlich ruft `RefreshExecution.refreshM3uProvider`
seit Stufe B `importM3uCatalog` statt `importM3uLiveChannels`; die Test-Fake erfasst `m3uProviderId`/
`m3uPlaylist` aber weiterhin in `importM3uLiveChannels` → auch die Assertions sind fachlich veraltet.
Ursache: Stufe B ergänzte Interface + Worker-Call, aktualisierte die Worker-Test-Fake nicht, und die
Stufe-B-Gates liefen `:data:media`+`assembleDebug`+`detekt`, **nicht** `:worker:test`.
*Repro:* `.\gradlew.bat :worker:testDebugUnitTest` → FAILED. *Schwere:* hoch (Test-Gate rot).
*Fix (später):* `importM3uCatalog`-Override in der Fake ergänzen (in `importM3uLiveChannels`-Capture
delegieren) **und** Assertions auf den neuen Call umstellen. *Owner-Entscheidung:* nein (klarer Bugfix).

**F2 — Öffentliche Live-TV-M3U (A): Import ok (positiv).**
353 Kanäle, 0 Movies/0 Series (reine Live-Liste), Sample-Kanal **RESOLVED** (https). Provider danach
gelöscht. Verbindungstest/Import/Resolver für M3U-Live funktionieren.

**F3 — Private M3U (C): Auto-Klassifizierung + VOD-Playback validiert (positiv, Kernbeleg).**
Ein M3U → **535 Kanäle, 8 814 Filme, 1 115 Serien** korrekt in die jeweiligen Katalogtabellen
klassifiziert. Sample **Channel + Movie + Episode je RESOLVED** (http). Damit sind Stufen A
(Classifier), B (Import-Routing) und C (Movie/Episode-Playback-Resolver) auf **echten** Daten
end-to-end bestätigt. Import (9,2 MB / ~29k Einträge) lief ohne Crash; Provider danach gelöscht.

**F4-Update (2026-07-03, D-04-Diagnose, GELÖST):** Kein Xtream-Fehler. Isolierter Diagnose-Lauf
(sanitisiert): Connection-Test **OK**, **alle** Endpunkte OK (liveCategories/liveStreams/vodCategories/
vodStreams/seriesCategories/series/seriesInfo). Root Cause des früheren `refresh=false`: der damalige
Audit-Harness hatte `withTimeout(180_000)` um `runPlaylistRefresh`, während der volle Xtream-Refresh
**N+1 sequentielle `getSeriesInfo`-Calls** macht (**1469 Serien** × ~0,5–1 s ≈ 15–25 min) → Timeout →
false/0/0/0; zusätzlich lief er direkt nach dem großen Same-Source-M3U-Import (1-Verbindungs-Cooldown).
Der reale WorkManager-Refresh hat kein 180-s-Limit, aber siehe **F11**.

**F11 — Xtream-Serien-Import ist N+1 (P2, Perf/Robustheit, NEU aus D-04).**
`RefreshExecution.refreshXtreamProvider` ruft `getSeriesInfo` **einmal pro Serie** sequentiell
(`seriesItems.map { … }`). Bei 1469 Serien ~15–25 min Refresh; bei großen Providern droht Überschreitung
der WorkManager-Ausführungslimits (~10 min) → Teil-/kein Refresh der Serien. *Schwere:* mittel.
*Owner-Entscheidung (D-09):* z. B. begrenzte Parallelität, Lazy-Laden von Seasons/Episodes erst bei
Serien-Detail, oder Cap/Backoff — ändert Import-/Playback-Verhalten → Owner-Entscheidung.

**F11-Status: ✅ BEHOBEN (2026-07-03, Variante C „Folge-Hintergrundjob", Owner-freigegeben, „voll pro
Zyklus").** Der Haupt-Xtream-Refresh importiert jetzt nur Live+Filme+Serien-Liste **schnell** (kein
`getSeriesInfo` mehr inline) und meldet sofort Erfolg. Season/Episode-Detail läuft in einem **separaten**
`SeriesDetailsRefreshWorker` pro Provider (sequenziell, 1-Verbindungs-sicher), enqueued nach erfolgreichem
Playlist-Refresh (Standalone + Global). Import-Split ohne Datenverlust: `importXtreamCatalog` mit leeren
`seriesInfos` lässt bestehende Seasons/Episoden unangetastet; neues `importXtreamSeriesDetails` reconciled
sie global aus allen Serien. Verifiziert: `test`+`detekt` grün, Worker-Unit-Tests, **instrumentierter
No-Wipe-/Repopulate-Test grün**, App startet ohne Crash. **Bekannte Grenze (dokumentiert, konsistent mit
akzeptierter Skalierung):** der Hintergrund-Job ist nicht chunk-resumabel — wird er (WorkManager-Limit)
gekillt, holt der Retry alle Serien neu; bei sehr großen Katalogen (~20k) kann ein Zyklus lange dauern.
Follow-up-Option: Chunk-Offset-Resumability.

**F4 — Private Xtream (D): Refresh fehlgeschlagen (P2, Follow-up).**
`refresh=false`, 0/0/0 importiert, obwohl dieselbe Source Minuten zuvor als M3U (F3) erfolgreich war.
Ursache **nicht isoliert** (Diagnose-Logging standardmäßig aus, keine Exception im App-Log-Tag; kein
erneuter Verbindungsversuch wegen 1-Verbindungs-Limit). Kandidaten: Xtream-Endpunkt/Account-seitig,
1-Verbindungs-Cooldown nach großem M3U-Import, oder Bug im Xtream-Refresh-Pfad. *Schwere:* mittel.
*Owner-Entscheidung:* ja — dedizierter Diagnose-Lauf (Diagnose-Logging an) vs. als Provider-seitig
einstufen. Nicht als gesicherter App-Bug behauptet.

**F5 — D-Pad: Fokuswechsel Textfeld → Dialog-Buttons unzuverlässig (P2, UX).**
Im „Wiedergabeliste hinzufügen"-Dialog verließ `DPAD_DOWN` aus dem fokussierten Namensfeld nicht
zuverlässig das Feld Richtung „Abbrechen/Weiter" (brauchte Zusatz-Tasten). Auf einer echten Fernbedienung
(nur D-Pad) potenziell blockierend. *Repro:* teilweise (Timing-abhängig). *Ursache (vermutet):*
Fokus-Order/`focusProperties` der Dialog-TextField→Button-Kante. *Datei:* `feature/settings/…/ProviderAddFlow.kt`.
*Owner-Entscheidung:* nein (UX-Bug; nach Bestätigung fixen).

**F6 — Live-TV-Layout entspricht 3-Zonen-Soll (Gap G1 aufgelöst, positiv).**
Runtime zeigt drei Zonen: links Provider/Favoriten-Kategorien · Mitte „Channel list" · rechts
„Preview" mit „OK starts preview" + Tabs Live/Cat./★ + „Details". Deckt sich mit PRD (Preview erst bei
OK, keine Auto-Vorschau). Der ursprüngliche Code-Audit-Eindruck „2 Spalten" war unpräzise. G1 gilt als
weitgehend konform (offen nur: separates Provider-Panel erscheint erst mit vorhandenen Providern).

**F7 — Provider-Add: nur M3U / Xtream, kein Clipboard (Gap G3 bestätigt).**
Quelltyp-Dialog bietet **M3U** und **Xtream Codes**; **kein Clipboard**. PRD nennt M3U per
URL/Datei/**Clipboard**. Owner-Entscheidung nötig (D-03).

**F8 — Lokalisierung & Empty States korrekt (positiv).**
Sprachumschaltung System/Deutsch/Englisch funktioniert (Activity-Recreate sauber, kein Crash).
Deutsche UI vollständig inkl. „Über die App", „Einstellungen", „Wiedergabelisten"; `Home` bleibt
(erlaubte Ausnahme). Alle Empty States lokalisiert und aussagekräftig. Fokus gut sichtbar (Cyan-Glow +
Rahmen) — Designsystem-konform. Suche zeigt „Microphone"-Eintrag → Sprachsuche (manuell) vorhanden
(Gap G4 aufgelöst). Top-Nav-Begriffe: Home · Live-TV · Filme · Serien (+ Such-/Zahnrad-Icon).

**F9 — Kosmetik/Perf (niedrig).** Verbindungstest + Parser lesen die komplette Playlist in den Speicher
(9,2 MB bei C); Referenz-Apps nutzen Line-Streaming. Kein Fehler, aber Speicher-/Zeit-Schuld.

**F10 — Android-TV-System-Search: geschützte Inhalte leaken nach Refresh (P1, Security, NEU im
autonomen Lauf gefunden).**
Der Suggestion-Index wird protection-fähig gebaut (`AndroidTvSearchDao.rebuildEntries(protectMovies,
protectSeries, protectAdultContent)`, Tests vorhanden), **aber** der Import ruft ihn ohne Argumente:
`RoomCatalogImportRepository` (Z. 72 M3U, Z. 139 Xtream) → `androidTvSearchDao.rebuildEntries()` →
Defaults **alle false** → Index enthält **alle** Filme/Serien/Adult. Der protection-fähige Rebuild
(`MediaRepository.rebuildAndroidTvSearchIndex(protect…)`) läuft **nur** in `MainActivity`
(`LaunchedEffect` auf `pinSecurityState.protect*`, Z. 699–708) — also nur bei laufender Activity und
PIN-State-Änderung, **nicht** nach einem Import. Folge: nach jedem Playlist-/Xtream-Refresh —
inkl. **headless Hintergrund-Auto-Refresh** (Worker, App nicht im Vordergrund) — sind geschützte
Filme/Serien/Adult-Inhalte über die Android-TV-Systemsuche auffindbar, bis `MainActivity` den
protection-fähigen Rebuild erneut ausführt. Verstoß gegen ADR-008 („System search … no protected").
*Schwere:* hoch (Privatsphäre/Kindersicherung). *Repro:* deterministisch aus Codepfad (nicht am
Emulator ausgelöst). *Owner-Entscheidung nötig* (Fix koppelt Import/Worker an PIN-State oder erzwingt
protection-fähigen Rebuild nach Refresh — sichtbares Security-/Import-Verhalten → D-08).

**F10-Status: ✅ BEHOBEN (2026-07-03, Variante A/Read-Time-Filter, Owner-freigegeben).** Der Suchindex
ist jetzt ein reiner Content-Spiegel (neue Spalte `isAdult`, DB v6→v7-Migration); Schutz wird zur
**Read-Zeit** in `AndroidTvSearchDao.searchEntries` + `AndroidTvSearchSuggestionProvider` gegen die
aktuellen PIN-Flags erzwungen (**fail-closed**). Import/Refresh können nicht mehr leaken;
MainActivity-Protection-Rebuild entfernt. Gates grün inkl. Migration- + Read-Time-Instrumententests +
realer 6→7-Migration am Emulator ohne Crash. Zusätzlich behoben: Stale-Fenster bei PIN-Umschaltung
(Schutz nun sofort wirksam).

### D-08 — F10 beheben (Systemsuche-Leak nach Refresh)
**Bereich:** PIN/Security + Import/Worker + Architektur. **Problem:** unfilterter Index-Rebuild nach
Refresh. **A:** Import/Refresh liest aktuelle PIN-Protection-Flags (aus `PinSecurityStateStore`) und
ruft den protection-fähigen Rebuild — koppelt `:data:media`/Worker an `:core:security`. **B:** Worker
löst nach erfolgreichem Refresh einen protection-fähigen Rebuild aus (App-hoisted Hook; Worker läuft
headless → braucht Zugriff auf PIN-State). **C:** DAO-Default auf „protektiv" + Read-Time-Filter mit
aktuellen Flags. **Empfehlung:** **A** (Rebuild dort, wo der Index entsteht, mit aktuellen Flags;
kleinste konsistente Lösung), Tests ergänzen. **Frage:** Variante A ok, oder B/C bevorzugt? Sichtbares
Security-/Import-Verhalten → Owner-Entscheidung.

## 3. Vergleichsmatrix

| Bereich | Code | Laufzeit | Docs (Soll) | Referenzen | Bewertung | Entsch.? |
|---|---|---|---|---|---|---|
| Provider hinzufügen | mehrstufig Name→Typ→Quelle | ✅ funktioniert; F5 Fokus | URL/Datei/Clipboard | alle: Multi-step | gut, F5 UX | – |
| M3U-Import | URL/Datei | ✅ F2/F3 | URL/Datei/**Clipboard** | Line-Streaming | konform − Clipboard | **D-03** |
| Xtream-Import | Live/VOD/Serie | ❌ F4 (Testquelle) | Server/User/Pass, Checkboxen | alle | Refresh-Fehler ungeklärt | **D-04** |
| M3U-Auto-Erkennung | Classifier A/B/C | ✅ **F3 real** | nicht spezifiziert | OwnTV type=/SxxExx | Code>Docs, stark | **D-05** (Docs) |
| EPG Import/Zuweisung | Zeitfenster, man./auto | nicht mit Daten getestet | global, Prio, 1–14d | Rolling-Window+Prune | Code konform | – |
| Live-TV-Navigation | 3 Zonen | ✅ F6 | 3 Zonen, Preview@OK | In-Player-Liste | konform | – |
| EPG-Spalte/Preview | vorhanden | ✅ „OK starts preview" | Sender-Modus+EPG | Guide-Overlay | konform | – |
| Player-Overlay | Timeline/Audio/Sub/CH± | nicht mit Stream getestet | Timeline primär | Preview-Reuse | Code konform | – |
| Filme/Serien | Kategorien/Detail/Resume | ✅ Empty States | Hero+Grid, 95 % | shelves | konform | – |
| Suche | FTS 300/20/20 | ✅ „local" + Mic | 4 Gruppen + Sprache | global FTS | konform | – |
| Favoriten/Verlauf | Room, 95 %/10 s | Home-Rows leer (kein Provider) | anbieterübergr., 95 % | per-profile | konform | – |
| Cache | LRU | – | informativ+leeren | – | konform | – |
| Backup | Standard+Full(AES) | nicht getestet | lokal/**SMB/Drive** | JSON/Drive | Ziele SMB/Drive unklar | **D-06** |
| PIN | PBKDF2 120k, Lockout | nicht getestet | 4-stellig, Lockout | per-profile | konform | – |
| Diagnose | Redaction+Limits | Logging default aus (F4) | 1–7d, 20 MiB | – | konform; Default-aus | – |
| Settings-Struktur | 9 Gruppen | ✅ alle sichtbar | 9 Gruppen | – | konform | – |
| Designsystem | Tokens; TransLvl 75 %, nur Accent Blau | Optik nicht geöffnet | Trans ≤50 %, mehrere Accents | – | 2 Abweichungen | **D-07** |
| Focus/D-Pad | sichtbar | ✅ gut; F5 Kante | Farbe+Form | modern | gut, F5 | – |
| Tests | 47 Dateien | ❌ `:worker` (F1) | Teststrategie-Doc | – | Gate rot | **D-01** |
| Architektur | P0–P3, VM/UiState | ✅ | ADR-konform | multi-module | stark | – |
| TLS | Debug-Bypass | aktiv (ermöglicht F2/F3) | ADR-014 strikt | – | bewusste Dev-Abweichung | **D-02** |

## 4. Entscheidungen (D-XX)

**Owner-Beschlüsse (2026-07-03):**

| D | Beschluss | Folge |
|---|---|---|
| D-01 | **Jetzt fixen** | ✅ **erledigt** — `:worker`-Test-Fake auf `importM3uCatalog` umgestellt, `test`+`detekt` grün. (`test` deckt `:worker` bereits ab — Stufe-B-Lehre: bei Cross-Modul-Interface-Änderungen volles `test` statt nur Ziel-Tasks laufen.) |
| D-02 | **Bypass behalten + Checkliste** | Release-Checklisten-/ADR-014-Notiz „Bypass vor Release entfernen/prüfen" |
| D-03 | **Clipboard aus v1 streichen + Docs** | M3U-Clipboard nicht bauen; `../vivicast-docs` angleichen (Doc-Freigabe nötig) |
| D-04 | **Diagnose-Lauf** | ✅ **erledigt** — kein Xtream-Fehler; früheres false = Harness-180-s-Timeout auf N+1 `getSeriesInfo` (1469 Serien) + M3U-Cooldown. Siehe **F11/D-09**. |
| D-05 | **Docs ergänzen** | M3U-VOD-Klassifizierung (A–C) in PRD/ADR dokumentieren (Doc-Freigabe nötig) |
| D-06 | **v1 nur lokal** | SMB/Drive nicht in v1; ungenutzte Enums + Docs bereinigen (Doc-Freigabe nötig) |
| D-07 | **Code an Docs angleichen** | 75 % entfernen + Akzentfarben ergänzen; **zuerst Optik-Panel visuell verifizieren** |



### D-01 — `:worker`-Unit-Tests reparieren (aus F1)
**Bereich:** Tests/Refresh. **Code:** `FakeCatalogImportRepository` ohne `importM3uCatalog`; M3U-Assertions
prüfen den alten Call. **Laufzeit:** `:worker:test` kompiliert nicht. **Docs:** Teststrategie fordert grüne
Gates. **Referenzen:** n/a. **Problem:** Regression aus Stufe B, Gate rot.
**A:** Fake-Override + Assertions aktualisieren (klein). **B:** ignorieren. **Empfehlung:** **A**, zeitnah als
P1-Bugfix (eigener kleiner Schritt, `:worker:test` als Gate). **Frage:** Sofort als nächster Fix?

### D-02 — TLS-Debug-Bypass: Umgang bis Release
**Code:** `BuildConfig.DEBUG`-gated trust-all. **Laufzeit:** ermöglicht Emulator-Tests (F2/F3). **Docs:**
ADR-014 „TLS strikt, kein Bypass". **Problem:** bewusste, release-sichere Dev-Abweichung, aber ADR-Konflikt.
**A:** so lassen (Release faltet weg) + Release-Checklistenpunkt „Bypass entfernen/prüfen". **B:** jetzt
entfernen (Emulator dann teils unbrauchbar). **C:** zusätzlich Laufzeit-Schalter (Debug). **Empfehlung:** **A**
+ Checklisten-/ADR-Notiz. **Frage:** A ok, oder C gewünscht?

### D-03 — M3U-Clipboard-Import (aus F7)
**Code/Laufzeit:** nur URL/Datei. **Docs:** URL/Datei/Clipboard. **Problem:** PRD-Feature fehlt.
**A:** Clipboard-Quelle bauen (App-hoisted Clipboard→ vorhandener M3U-Parse-Pfad). **B:** aus v1-Scope
streichen + Docs aktualisieren. **Empfehlung:** **B** kurzfristig (geringer Nutzen auf TV ohne Tastatur),
später A optional. **Frage:** Scope streichen oder bauen?

### D-04 — Xtream-Refresh-Fehler untersuchen (aus F4)
**Problem:** Refresh der privaten Xtream-Quelle schlug fehl, gleiche Source als M3U ok; Ursache unklar.
**A:** dedizierter Diagnose-Lauf (Diagnose-Logging an, 1 Verbindung) zur Ursachenklärung. **B:** als
Provider-/1-Verbindungs-seitig einstufen, nichts tun. **Empfehlung:** **A** (kurz, keine Codeänderung nötig
zum Messen). **Frage:** Diagnose-Lauf freigeben?

### D-05 — Docs für M3U-VOD-Klassifizierung nachziehen
**Problem:** Klassifizierung Live/Movie/Series (Stufen A–C) ist implementiert + real bestätigt (F3), aber
in `../vivicast-docs` nicht spezifiziert. **A:** PRD/ADR ergänzen (Heuristik, Fallback=Live, Stable-IDs).
**B:** undokumentiert lassen. **Empfehlung:** **A** (nur Doku, außerhalb dieses Laufs). **Frage:** Docs-Update
beauftragen? (Docs-Änderung nur mit deiner Freigabe.)

### D-06 — Backup-Ziele SMB/Google Drive
**Code:** Enums vorhanden, Implementierung unklar; **Docs:** lokal/SMB/Drive. **A:** implementieren.
**B:** v1 auf „lokal" beschränken, Enums/Docs bereinigen. **Empfehlung:** **B** für v1 (SMB/Drive = großer
Aufwand), A als Post-v1. **Frage:** v1-Scope bestätigen?

### D-07 — Designsystem-Abweichungen (Transparenz 75 %, Akzentfarben)
**Code:** `TransparencyLevel` enthält 75 % (Docs ≤50 %); `AccentColor` nur `Blue` (Docs „mehrere
vordefinierte"). Laufzeit (Optik-Panel) nicht verifiziert. **A:** Code an Docs angleichen (75 % entfernen,
Akzentfarben ergänzen). **B:** Docs an Code lockern. **Empfehlung:** 75 % entfernen (A) + Akzentfarben
ergänzen (A), da PRD-normativ. **Frage:** Zustimmung? (zuerst Optik-Panel visuell verifizieren).

## 5. Referenz-App-Ideen (nur Konzepte; Lizenzen beachten — kein Code kopieren)

- **Line-Streaming-Parser** (OwnTV/StreamVault) → adressiert F9-Schuld, wichtig für sehr große Listen.
- **EPG-Rolling-Window (now→+48 h) + Prune** auf vorhandene Kanäle (OwnTV/StreamVault).
- **Kategorie-Customization** hide/rename/reorder, resync-fest (OwnTV) — passt zu Docs „group visibility".
- **Preview→Fullscreen-Stream-Reuse** (StreamVault) — schnellerer Kanalstart.
- **Nummerneingabe** für Direktwahl (OwnTV/StreamVault) — nicht in PRD; optionaler UX-Zusatz.
- **Paging 3** für 50k+-Listen (StreamVault) — bei privater M3U mit ~8,8k Movies relevant.
GPLv3 (OwnTV) und Non-Commercial (StreamVault): nur Muster abstrahieren, eigenen Code schreiben.

## 6. Priorisierte Roadmap (Vorschlag, final nach D-XX)

- **P1 — Stabilität/Security:** D-01 (`:worker`-Tests grün) ✅ · **D-08 (F10 Systemsuche-Leak) ✅ behoben
  (Read-Time-Filter, DB v7)** · D-04 (Xtream-Fehler geklärt ✅ — kein Bug, siehe F11). Hinweis: `test` deckt `:worker` bereits ab.
- **NEU P2 — D-09 (F11):** Xtream-Serien-Import N+1 (`getSeriesInfo` pro Serie) → Refresh-Perf/WorkManager-Limit.
- **P2 — UX-/PRD-Angleichung:** F5 (Dialog-Fokus) · D-07 (Transparenz/Akzent) · D-03-Entscheid ·
  Backup-i18n-Fix (`StandardBackupRestorer.kt:45` dt. hartkodiert) · TV-System-Search PIN-Filter verifizieren.
- **P3 — Schulden/Perf:** F9 Line-Streaming-Parser + leichter Verbindungstest · ungenutzte
  `collectAsState`-Imports (17 Panels) · Diagnose-Default.
- **P4 — Referenz-Features:** EPG-Rolling-Window, Kategorie-Customization, Preview-Reuse, Nummerneingabe,
  Paging 3 — je eigener kleiner Schritt mit Gates.
- **Doku (separat, Freigabe nötig):** D-05 (M3U-VOD), D-02 (ADR-014-Notiz), D-06 (Backup-Ziele).

## 7. Git-Status (Ende des Laufs)

```
 D plans/M3U-CONTENT-CLASSIFICATION-PLAN.md   (vorbestehend, Owner; nicht angefasst)
?? plans/VIVICAST-AUDIT-2026-07.md            (dieser Bericht)
```
Keine weiteren Änderungen. Throwaway-Instrumentierungstests gelöscht. Kein Commit/Push.

**Sicherheit:** Im Chat gepostete Xtream-Zugangsdaten + private URLs nach der Testphase **rotieren/neu
generieren** (Chat kann geloggt/gecacht sein). In Repo/Bericht/Logs wurden keine Credentials/URLs abgelegt.
