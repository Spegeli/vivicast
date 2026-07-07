# Plan: EPG-Import auf Streaming umstellen (große Dateien + .gz)

Status: **Abgeschlossen** (Phasen 1–6, gebaut · detekt grün · auf Emulator installiert) ·
optionale Rest-Optimierung (echtes Stream-to-DB) offen

Auslöser: 48-MB-`epg-de.xml` scheiterte (32-MB-Cap + DOM-Parser lud alles in RAM); `.gz` nicht unterstützt.
Recherche: 4 OSS-IPTV-Apps (StreamVault, AerioTV, OwnTV, BBC) — Konsens: **Streaming-Parser** (kein DOM),
**gzip via Magic-Bytes `0x1F 0x8B`**, chunk-weise DB-Inserts, Cap optional (StreamVault 200 MB mid-stream).

Fortschritt:
- ✅ Phase 1 — `iptv:xmltv`: `parseStreaming(InputStream, XmltvStreamHandler)` via **SAX** (JVM+Android),
  gzip Magic-Bytes `0x1F 0x8B`, DOCTYPE erlaubt aber XXE-safe, Look-behind für fehlende `stop`. Tests grün.
- ✅ Phase 2 — `worker`: `OkHttpEpgStreamSource` (Response-Body-Stream, `CappedInputStream` 200 MB
  mid-stream, `EpgSourceTooLargeException`).
- ✅ Phase 5a — Test-Pfad: `TestEpgSourceConnectionUseCase` streamt + zählt (kein Volldokument);
  `EpgStreamSource`-Interface in `data:epg`, App verdrahtet `OkHttpEpgStreamSource`; Meldung „zu groß
  (max. 200 MB)". Connection-Test funktioniert jetzt mit 48-MB-.xml und .gz.
- ✅ Phase 3+4 — `worker`: `DefaultEpgRefresher` nutzt `OkHttpEpgStreamSource.open` + `parseStreaming`,
  sammelt Kanäle+Programme in ein `XmltvDocument` und ruft das **bestehende** `importXmltv` (Mappings,
  Chunk-Insert 20k, Provider-Fanout, atomische Transaktion — alles wiederverwendet). `RefreshExecutionTest`
  auf `FakeEpgStreamSource` umgestellt. App verdrahtet `OkHttpEpgStreamSource`.
  - **Entscheidung:** SAX→`XmltvDocument`→`importXmltv` statt Channel-Bridge-Stream-to-DB. Beseitigt den
    DOM-Blowup (~200 MB → ~70 MB Objekt-Puffer = bestehendes Import-Modell), niedriges Risiko, volle
    Test-Wiederverwendung, Atomarität bleibt.
- ✅ Phase 6 — DOM `parse(String)` bleibt (nur noch von `DefaultXmltvParserTest` genutzt), Referenz/Tests
  intakt. detekt grün.

## Rest-Optimierung (offen, optional)
Der Import puffert die geparsten Programme noch im RAM (~70 MB bei 48 MB Feed; deutlich besser als DOM,
aber nicht null). Echtes Stream-to-DB (Channel-Bridge: SAX-Callbacks → bounded Queue → suspend
Batch-Insert, Programme nie alle gleichzeitig im RAM) wäre für 100 MB+ / description-lastige Feeds nötig.
Nicht-atomar ohne Staging → optional später mit Staging-Tabelle (wie StreamVault).

---
_Abschnitte unten sind historischer Planungskontext (Ist-Zustand vor dem Umbau, Zielarchitektur,
Phasen-Details). Der Umbau weicht bei Phase 3–4 bewusst ab: kein `importXmltvStream`, stattdessen
SAX→`XmltvDocument`→bestehendes `importXmltv` (siehe Fortschritt oben)._

## Ist-Zustand (Vivicast) — vor dem Umbau

- `iptv:xmltv` `DefaultXmltvParser.parse(content: String): XmltvDocument` — **DOM** (`DocumentBuilder`),
  lädt ganzen Baum in RAM. Leitet fehlende `stop`-Zeiten global aus dem nächsten Programm ab.
- `worker` `OkHttpTextFetcher.fetch(url): String` — **32-MB-Cap** (`MAX_M3U_URL_BYTES`, für M3U gedacht),
  gibt String zurück. Kein gzip.
- `worker` `DefaultEpgRefresher.refresh`: `parse(fetch(url))` → importiert **dasselbe Dokument je aktivem
  Provider** (`activeProviderIds.forEach { importXmltv(providerId, sourceId, document) }`).
- `data:epg` `importXmltv(providerId, sourceId, document)`: baut Auto-Mappings aus `document.channels`,
  dann Programme chunk-weise (`EPG_IMPORT_CHUNK_SIZE`) in **einer** `withTransaction`.
- `data:epg` `TestEpgSourceConnectionUseCase`: `parse(fetchText(url))` (mein neuer Test).

## Zielarchitektur

XMLTV liefert immer erst alle `<channel>`, dann alle `<programme>` → Streaming passt:
Kanäle sammeln (klein) → Mappings bauen → Programme einzeln streamen → chunk-weise inserten.

### Design-Entscheidungen
- **Fehlende `stop`-Zeit:** Streaming-Parser nutzt **per-Kanal Look-behind** (offenes Programm ohne
  `stop` wird beim nächsten Programm desselben Kanals auf dessen `start` geschlossen; am Ende noch
  offene → skipped). Leicht abweichend vom DOM (global), aber korrekt für geordnete Feeds (Regelfall)
  und wie die OSS-Apps.
- **Multi-Provider-Fanout:** Eine Quelle kann mehreren Providern dienen. Streaming kann den Stream nicht
  mehrfach lesen → Import bekommt **die Provider-Liste** und fächert pro Programm-Chunk an alle auf.
- **Transaktionsmodell:** Nicht eine Transaktion über den ganzen Netzwerk-Stream (hält DB-Lock während
  I/O). Stattdessen: Mappings + `deleteProgramsForProviderAndSource` in einer Vor-Transaktion, dann
  **pro Chunk eine kurze Transaktion** (`insertPrograms`). Ausfall mittendrin = partielle Daten →
  akzeptabel (nächster Refresh überschreibt); optional später Staging wie StreamVault.
- **Additiv, nicht ersetzend:** DOM `parse(String)` + `importXmltv(document)` bleiben (Tests/Back-compat);
  neuer Streaming-Pfad wird ergänzt und in Refresher + Test-UseCase verdrahtet. DOM-Pfad kann später weg.
- **gzip:** Magic-Bytes `0x1F 0x8B` via `PushbackInputStream`, dann `GZIPInputStream`. Robust
  unabhängig von URL-Endung / Content-Encoding (OkHttp entpackt Content-Encoding zusätzlich transparent).

## Phasen

### Phase 1 — `iptv:xmltv`: Streaming-Parser + gzip
- `XmltvParser.parseStreaming(input: InputStream, handler: XmltvStreamHandler)` mit
  `onChannel(XmltvChannel)` / `onProgram(XmltvProgram)`. Impl: `android.util.Xml.newPullParser()`
  (bzw. `XmlPullParserFactory`; im `iptv:xmltv`-Modul verfügbar prüfen — sonst `org.xmlpull`).
  **Achtung:** `android.util.Xml` ist Android-only; `iptv:xmltv` ist reines Kotlin/JVM-Modul? →
  prüfen. Falls JVM: `XmlPullParserFactory.newInstance()` (kxml2) oder StAX. Fallback-Entscheidung
  in Phase 1.
- gzip-Helper `maybeGunzip(InputStream): InputStream` (Magic-Bytes).
- Look-behind für fehlende `stop`.
- Tests: gültig (Kanäle+Programme via Callback), gzip-Input, fehlende stop.

### Phase 2 — `worker`: EPG-Stream-Fetcher
- Neuer Fetcher, der den Response-Body als `InputStream` liefert (nicht String/ByteArray), mit
  **eigenem EPG-Cap (200 MB) mid-stream** (FilterInputStream-Zähler) statt des 32-MB-M3U-Caps.
  Caller konsumiert + schließt Response.

### Phase 3 — `data:epg`: Streaming-Import
- `importXmltvStream(providerIds: List<String>, epgSourceId, channels: List<XmltvChannel>,
  programs: Sequence/Flow<XmltvProgram>)` **oder** callback-basiert: Kanäle zuerst (Mappings), dann
  Programme chunk-weise pro Provider. Fanout an alle Provider. Kurze Chunk-Transaktionen.

### Phase 4 — `worker`: Refresher umstellen
- `DefaultEpgRefresher`: Stream-Fetch → `parseStreaming` → `importXmltvStream(activeProviderIds, …)`.
  Kanäle puffern, Programme streamen. „keine Programme" weiterhin als Fehler.

### Phase 5 — App + Test-UseCase
- App: EPG-Stream-Fetcher verdrahten. `TestEpgSourceConnectionUseCase` zählt per Streaming
  (Kanäle/Programme) ohne Volldokument; `M3uSourceTooLargeException`-Mapping ersetzt durch EPG-Cap.

### Phase 6 — Aufräumen + Gates
- Ungenutzten DOM-Pfad prüfen (behalten/entfernen). `RefreshExecutionTest` anpassen.
  detekt-Baseline regenerieren. Build + Emulator-Install + Test mit `epg-de.xml` (48 MB) und `.gz`.

## Validierung
- Unit: neue Parser-Streaming-Tests, `data:epg`-Import-Test, `TestEpgSourceConnectionUseCaseTest` (Stream).
- `.\gradlew.bat :iptv:xmltv:test :data:epg:test :worker:test detekt assembleDebug`
- Emulator: `epg-de.xml` (48 MB) Test grün + Import; `.gz`-Variante grün.

## Nicht-Ziele (v1)
- Kein Staging/atomic-swap (später möglich). Kein `.xz`/`.zip`. Kein Retry-Rework (WorkManager reicht),
  optional OwnTV-Muster (3× exp. Backoff) später.
