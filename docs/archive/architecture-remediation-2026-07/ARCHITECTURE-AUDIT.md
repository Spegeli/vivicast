# Architecture Audit – ViviCast

> Analyse-Lauf vom 2026-07-01. **Reiner Lese-/Analyse-Lauf.** Es wurden keine Source-, Gradle-,
> Manifest-, Ressourcen-, Test- oder bestehenden Dokumentationsdateien geändert. Es wurden ausschließlich
> drei neue Dateien unter `docs/` erstellt.
>
> Verwandte Dokumente: [ARCHITECTURE-REFACTORING-PLAN.md](ARCHITECTURE-REFACTORING-PLAN.md),
> [ARCHITECTURE-FILE-SPLIT-PLAN.md](ARCHITECTURE-FILE-SPLIT-PLAN.md)

---

## Executive Summary

**Architektur-Gesundheit: MITTEL** (gute Fundamente, punktuell hohe technische Schuld).

ViviCast hat ein **überraschend starkes strukturelles Fundament**: sauberes Multi-Module-Setup (30 Module),
korrekte Abhängigkeitsrichtung, echte Modelltrennung (DTO → Entity → Domain → UI), eine sauber gekapselte
Player-Schicht und **umfangreiche Tests** (39 Testdateien: Parser, Repositories, Player-Controller,
Playback-Regeln, Worker, Fokus-Tests). Das ist deutlich besser als der Durchschnitt einer App in diesem Stadium.

Die technische Schuld konzentriert sich auf **zwei Stellen**, nicht auf die Gesamtstruktur:

1. **Es gibt keinen Presentation-/State-Holder-Layer.** Kein einziges `ViewModel` existiert. Feature-Screens
   sind `*Route`-Composables, die Repositories direkt als Parameter erhalten, direkt `collectAsState` auf
   Repository-Flows aufrufen und UI-State per `remember`/`mutableStateOf` in der Composition halten. Damit ist
   die Screen-Logik weder lebenszyklus-sicher noch unit-testbar (nur via `androidTest`).
2. **Zwei God-Files** binden App-übergreifende Logik: `MainActivity.kt` (2.299 Zeilen) und
   `SettingsRoute.kt` (4.879 Zeilen). Beide vermischen mehrere Verantwortlichkeiten und Features.

Der Rest der Codebase (Data-Layer, Player, IPTV-Parser, Worker, Datenbank, DI) ist pragmatisch sauber und
größtenteils **nicht** refactoring-bedürftig.

### Wichtigste 5 Erkenntnisse

1. **Kein Presentation-Layer / keine ViewModels.** `grep -rl "ViewModel"` über den gesamten Produktivcode
   liefert 0 Treffer. State + Screen-Logik + Datenbeobachtung leben in den Composables.
2. **`MainActivity.kt` (2.299 Z.) ist der De-facto-App-ViewModel + Navigations-Host + Mapper-Schicht +
   Playback-UseCase-Schicht** – alles als `remember`-State und `AppContainer`-Extension-Functions.
3. **`SettingsRoute.kt` (4.879 Z.) bündelt ~9 eigenständige Bereiche** (Allgemein, Darstellung, Wiedergabe,
   EPG, Anbieter inkl. Add-Wizard, Kindersicherung, Backup, Über die App, Wartung) in einer Datei.
4. **Player-Schicht ist vorbildlich gekapselt** (`VivicastPlayerController`-Interface + Media3-Engine +
   `StateFlow`-State). UI steuert ExoPlayer **nicht** direkt. Das ist eine gute Entscheidung – nicht anfassen.
5. **Kein statisches Analyse-/CI-Gate** (kein detekt, ktlint, `.editorconfig`, kein `.github/`). Die
   Codegröße-Ausreißer sind nirgends automatisiert sichtbar.

### Wichtigste 5 empfohlene Maßnahmen

1. **Presentation-State-Holder pro Feature einführen** (`ViewModel` oder – modul-minimal – plain State-Holder),
   damit Repositories nicht mehr direkt in Composables landen. Höchster Architekturnutzen.
2. **`SettingsRoute.kt` in Panel-Dateien pro Bereich aufteilen** (P0-Split-Kandidat).
3. **`MainActivity.kt` entflechten**: Mapper und Playback-Orchestrierung herausziehen, `VivicastApp` als
   eigener Navigations-Host isolieren.
4. **Playback-Orchestrierung (`AppContainer`-Extensions in MainActivity) in echte UseCases/Repository-Methoden
   ziehen** – dort liegt derzeit untestbare Business-Logik im UI-Modul.
5. **detekt + Zeilenlimit-Regel als Gate** einführen, um Wiederentstehen von God-Files zu verhindern.

### Empfohlener erster Refactoring-Schritt

**`SettingsRoute.kt` datei-splitten (reine Extraktion, kein Verhaltensänderung).** Grund: höchster
Schmerz/Risiko-Hebel, rein mechanisch (Composables in Panel-Dateien im selben Package verschieben), sofort
durch bestehende Settings-`androidTest`s abgesichert, und blockiert nichts anderes. Siehe
[ARCHITECTURE-REFACTORING-PLAN.md](ARCHITECTURE-REFACTORING-PLAN.md) Schritt P0-01.

---

## Quellen & Bewertungsgrundlage

Recherchiert am 2026-07-01. Offizielle Google-Quellen wurden höher gewichtet als Blogs; Ivy Wallet nur als
pragmatische Referenz.

| Quelle | Einordnung | Relevanz für ViviCast |
|---|---|---|
| [developer.android.com/topic/architecture/recommendations](https://developer.android.com/topic/architecture/recommendations) | Normativ (Google) | „Strongly recommended": Datenlayer als Single Source of Truth, Repositories immer, **UI/ViewModels greifen nicht direkt auf Datenquellen zu**, UDF, `collectAsStateWithLifecycle`, ViewModel lifecycle-unabhängig. → Genau die Punkte, an denen ViviCast abweicht. |
| [developer.android.com/topic/architecture/ui-layer](https://developer.android.com/topic/architecture/ui-layer) | Normativ (Google) | ViewModel als Screen-State-Holder, immutable `UiState`-Datenklasse, State runter/Events hoch, `collectAsStateWithLifecycle`. UI-Logik mit `Context`/`Resources` gehört in die UI-Schicht (nicht ins ViewModel). |
| [developer.android.com/topic/modularization](https://developer.android.com/topic/modularization) | Normativ (Google) | Warnung vor **Über-Modularisierung**; Feature-Module sollen nicht voneinander abhängen; low coupling / high cohesion. ViviCasts Modulschnitt ist bereits konform → kein Modul-Umbau nötig. |
| [github.com/android/nowinandroid](https://github.com/android/nowinandroid) | Referenz-Sample (Google) | Modultypen `app` / `feature:*` / `core:*` / `data`; `feature`-Module mit ViewModel + `UiState`. ViviCasts Modulnamen entsprechen diesem Muster – nur der ViewModel/UiState-Teil pro Feature fehlt. |
| [Ivy Wallet – Architecture.md](https://github.com/Ivy-Apps/ivy-wallet/blob/main/docs/guidelines/Architecture.md) | Pragmatische Referenz (kein harter Standard) | Optionale Layer sind erlaubt („a data source isn't always needed if it'll do nothing useful"). ViewModel als Übersetzer UI↔Domain. Main-Safety via `withContext(Dispatchers.IO)`. Bestärkt: **kein dogmatischer Domain-Layer für ViviCast erzwingen**. |

**Widerspruch & Schlussfolgerung für ViviCast:** Google empfiehlt einen Domain-Layer nur für große Apps mit
über mehrere ViewModels geteilter Logik; Ivy erklärt Layer explizit für optional. ViviCast hat bereits ein
`domain`-Modul, das aber nur Modelle enthält (keine UseCases). **Empfehlung:** Keinen flächendeckenden
UseCase-Layer erzwingen. UseCases nur dort, wo Logik heute mehrfach oder im falschen Layer liegt (v.a.
Playback-Orchestrierung, EPG-Mapping). Der wichtigste fehlende Baustein ist **nicht** der Domain-Layer,
sondern der **Presentation-State-Holder**.

**Bewusst NICHT übernommen:** `Either`/funktionale Fehlerbehandlung (Ivy) wird nicht empfohlen – ViviCast nutzt
bereits ein `AppResult`/Exceptions-Muster im `core/common`; ein Umbau wäre reiner Stil-Churn ohne Nutzen.

---

## IST-Zustand

### Aktuelle Module (30, Multi-Module Gradle)

```
:app
:core:common   :core:cache      :core:designsystem  :core:database
:core:datastore :core:network   :core:player        :core:security
:data:provider :data:epg        :data:media         :data:favorites  :data:playback
:domain
:feature:home  :feature:live-tv :feature:movies :feature:series :feature:search
:feature:settings :feature:player
:iptv:m3u      :iptv:xtream     :iptv:xmltv
:worker
```

Namespace `com.vivicast.tv`, `compileSdk 36`, `minSdk 23`, AGP 8.13.2, Kotlin 2.2.21, Compose BOM 2026.05,
Media3 1.9.4, Room 2.8.2, tv-material 1.0.0. **Manuelle DI** (kein Hilt/Koin).

### Aktuelle Package-/Layer-Struktur

- **Abhängigkeitsrichtung ist korrekt**: `:app` → `:feature:*` + `:data:*` + `:core:*` + `:domain` + `:worker`.
  `:feature:*` → `:data:*` + `:domain` + `:core:*`. Kein Feature→Feature-Zyklus gefunden.
- **Layering vorhanden, aber unvollständig:**
  - *Data-Layer* – vorhanden und sauber: Repository-Interface + Room-Implementierung je `:data:*`-Modul
    (z.B. `ProviderRepository` + `RoomProviderRepository` in `:data:provider`).
  - *Domain-Layer* – nur Modelle (`domain/model/VivicastModels.kt`, 228 Z., 20 saubere Datenklassen/Enums)
    plus ein 5-Zeilen-`UseCase.kt`-Marker. **Keine UseCases.**
  - *Presentation-Layer* – **fehlt.** Kein ViewModel, kein `UiState`-Holder. Screens = `*Route`-Composables.

### Aktuelle Datenflüsse

**Katalog/Playlist → UI (heute):**
```
IPTV-Parser (M3uChannel/XtreamVodItem = DTO)
  → RoomCatalogImportRepository (DTO→Entity-Mapper, Room-Upsert)
  → Room (ChannelEntity/MovieEntity …)
  → RoomMediaRepository.observe*() (Entity→Domain via .toDomain())  ← Single Source of Truth ✔
  → Feature-Route-Composable ruft repo.observe*().collectAsState()  ← direkt in der UI �’
  → Composable rendert Domain-Modelle
```
Der Bruch liegt am **letzten Pfeil**: Das Composable ist gleichzeitig State-Holder und Datenkonsument.

**Settings/DataStore → UI:**
```
DataStoreUserPreferencesStore (:core:datastore) → UserPreferences (Flow)
  → MainActivity: ~35 Mapper-Funktionen (Preference ↔ Settings-UI-Modell)  ← Mapping im app-UI-Modul
  → SettingsRoute(...) Composable hält lokalen State + ruft onChange-Callbacks
  → MainActivity schreibt via userPreferencesStore.update{}
```

**Playback:**
```
Feature-Route onOpenPlayer(...) → MainActivity: AppContainer.openChannelPlayback()/openMoviePlayback()/…
  (Extension-Functions: Stream auflösen, PlaybackRequest bauen, Fortschritt lesen)
  → playbackStreamResolver + playbackRepository + mediaRepository
  → playerController.play(PlaybackRequest)
  → PlayerRoute: playerController.state.collectAsState() rendert Overlay  ← Player sauber via Controller ✔
```

**Refresh/Import (Background):**
```
WorkManager → RefreshWorkerRegistry → DefaultRefreshWorkerRunner → GlobalRefreshOrchestrator
  → DefaultPlaylistRefresher / DefaultEpgRefresher / DefaultLogoRefresher / DefaultCacheCleaner
  → Fetcher (OkHttp) + Parser + Import-Repositories + Room-Transaktionen
```
Sauber aus der UI ausgelagert. **Gute Entscheidung.**

### Aktuelle DI-Struktur

Ein einziger `AppContainer` (`app/di/AppContainer.kt`, 371 Z.), `by lazy`-Konstruktion aller Singletons,
konstruktorbasierte Injektion in Repos/Refresher. **Pragmatisch angemessen** für die Modulzahl. Zwei
Schönheitsfehler: (1) der Container enthält Business-Logik (`testProviderConnection`, `testM3uConnection`,
`testXtreamConnection`, `createStandardRestoreSafetyBackup`); (2) er ist als einzelne Klasse eine wachsende
zentrale Naht.

### Aktuelle Teststruktur

39 Testdateien. Stark: `:iptv:*`-Parser (unit), `:core:player` Controller (unit), `:data:playback`
Stream-Resolver + Progress-Rules (unit), `:worker` Refresh-Ausführung (unit), Room-Repos + DB-Migration
(`androidTest`), Feature-Fokus-Tests (`androidTest`), Backup/WatchNext-Integration. **Lücke:** Keine
ViewModel-Unit-Tests möglich (keine ViewModels); Screen-Logik nur via langsame `androidTest`; die
Playback-/Mapper-Logik in `MainActivity` ist an `Context`/`AppContainer` gebunden und daher nicht unit-getestet.

### Aktuelle Risiken (Kurzfassung)

- Screen-Logik nicht unit-testbar → Regressionen nur über Emulator-`androidTest` erkennbar.
- `collectAsState` (nicht `collectAsStateWithLifecycle`) → Flows sammeln auch, wenn Screen nicht sichtbar.
- Zwei God-Files → hohes Merge-/Regressionsrisiko, schwer zu navigieren.
- Kein Lint-/detekt-Gate → Größenausreißer wachsen unbemerkt weiter.

---

## SOLL-Zustand

### Empfohlenes Architekturmodell

**Multi-Module beibehalten** (es ist bereits gut geschnitten) **+ pro Feature einen Presentation-State-Holder
ergänzen.** Google „recommended architecture" light, **ohne** flächendeckenden UseCase-Zwang.

- **UI-Layer:** `*Route`-Composables bleiben, werden aber „dumm": sie erhalten ein `UiState` + Event-Lambdas
  und rufen **keine** Repositories mehr direkt auf.
- **Presentation-State-Holder:** je Feature ein `ViewModel` (empfohlen, da WorkManager + mehrere Screens
  ohnehin vorhanden) **oder**, wenn die Modul-Compose-Abhängigkeit minimal bleiben soll, ein plain
  `StateHolder` mit eigenem `CoroutineScope`. Hält `StateFlow<XxxUiState>`, konsumiert Repositories,
  mappt Domain→UiState.
- **Domain-Layer:** bleibt schlank. UseCases **nur** für heute falsch platzierte/mehrfach genutzte Logik
  (Playback-Orchestrierung, EPG-Mapping, Verbindungstest). Kein UseCase pro trivialem CRUD.
- **Data-Layer:** unverändert (ist bereits Single Source of Truth mit sauberem Mapping).
- **Player-Layer:** unverändert (Referenzqualität).

### Empfohlene Dependency-Richtung (unverändert, bereits erfüllt)

```
app → feature:* → (data:* | domain | core:*)
app → worker → (data:* | iptv:* | core:*)
data:* → (domain | core:database | core:*)     iptv:* → (domain | core:network)
domain → (nichts App-spezifisches)             core:* → (nichts feature-spezifisches)
```

### Empfohlener Datenfluss (SOLL)

```
Repository (SSOT, Domain-Flow)
  → FeatureViewModel/StateHolder: mappt zu StateFlow<UiState>, hält Events
  → Route-Composable: uiState by vm.state.collectAsStateWithLifecycle(); rendert; ruft vm.onEvent(...)
```

### Empfohlene State-/Event-Struktur

Pro Feature: immutable `data class XxxUiState`, exponiert als `StateFlow`; Nutzeraktionen als
`vm.onXxx()`-Methoden oder ein `sealed interface XxxEvent`. Einmalige Effekte (Navigation, Dialoge) als
`Channel`/`SharedFlow` oder als konsumierbares State-Feld.

### Empfohlene Fehlerbehandlung

Bestehendes `AppResult` (`:core:common`) beibehalten und konsequent bis in den `UiState` durchreichen
(`isLoading` / `error`). **Kein** `Either`-Umbau.

### Empfohlene Teststrategie

Nach Einführung der State-Holder: Unit-Tests je Feature-State-Holder mit Fake-Repositories (die Interfaces
existieren bereits → Fakes trivial). Bestehende Parser-/Repo-/Player-/Worker-Tests unverändert lassen.
`androidTest`-Fokus-Tests bleiben als UI-Regressionsnetz.

---

## Gap-Analyse

| Bereich | IST | SOLL | Risiko | Priorität | Betroffene Dateien/Packages |
|---|---|---|---|---|---|
| Presentation-Layer | Kein ViewModel; State in Composables | Pro Feature State-Holder (`StateFlow<UiState>`) | Screen-Logik untestbar, nicht lifecycle-safe | **P0** | alle `feature/*/…/*Route.kt` |
| God-File Settings | `SettingsRoute.kt` 4.879 Z., ~9 Bereiche | Panel-Dateien je Bereich | Merge-/Regressionsrisiko, unlesbar | **P0** | `feature/settings/SettingsRoute.kt` |
| God-File App | `MainActivity.kt` 2.299 Z., 5+ Rollen | Navigations-Host + ausgelagerte Mapper/UseCases | zentrale Fehlerquelle | **P0** | `app/MainActivity.kt` |
| Playback-Logik im UI-Modul | `AppContainer`-Extensions in `MainActivity` | Playback-UseCases in `domain`/`data:playback` | untestbare Business-Logik | **P1** | `app/MainActivity.kt` (Z. 1786–2091) |
| Lifecycle-aware Collection | `collectAsState` | `collectAsStateWithLifecycle` | unnötige Sammlung im Hintergrund | **P1** | alle Feature-Routes + `MainActivity:399` |
| Settings-Mapper-Ort | ~35 Mapper in `MainActivity` | Mapper in `:feature:settings` oder `:core:datastore` | Aufblähung app-Modul | **P1** | `app/MainActivity.kt` (Z. 1464–2293) |
| DI enthält Logik | `testProvider*` in `AppContainer` | Verbindungstest-UseCase in `:data:provider` | DI-Klasse wächst, untestbar | **P2** | `app/di/AppContainer.kt` (Z. 291–336) |
| Designsystem-Datei | `VivicastComponents.kt` 1.704 Z. (~40 Composables) | nach Kategorie splitten | nur Größe (kohäsiv) | **P2** | `core/designsystem/VivicastComponents.kt` |
| Statik-Gate | kein detekt/ktlint/CI | detekt + Zeilenlimit-Regel | God-Files wachsen unbemerkt | **P2** | Build-Setup |
| Dispatcher-Injektion | `Dispatchers.IO` hart in `:worker`/`:iptv` | Dispatcher injizieren (wie Player) | geringe Testbarkeitseinbuße | **P3** | `worker/RefreshExecution.kt`, `iptv/xtream/XtreamContracts.kt` |

---

## Architektur-Smells (mit konkretem Codebezug)

- **`MainActivity.kt` – 5 Rollen in einer Datei.** (a) `MainActivity`-Activity (Z. 145); (b) `VivicastApp`
  Navigations- und App-State-Composable ~Z. 188–1372 (**~1.180 Zeilen**, hält per `remember` faktisch den
  gesamten App-State = De-facto-App-ViewModel); (c) ~35 Preference↔UI-Mapper (Z. 1464–2293); (d)
  Playback-Orchestrierung als `AppContainer`-Extensions (`openChannelPlayback` Z. 1838, `openMoviePlayback`
  Z. 1873, `openEpisodePlayback` Z. 1921, `openCatchUpPlayback` Z. 1963, `savePlaybackProgress` Z. 2009,
  `clearHistory` Z. 2076, Bild-Resolver Z. 1786–1837); (e) Dialoge (Protection, ExternalPlayer, Restore).
- **`SettingsRoute.kt` – ~9 Bereiche + Modelle + Wizard in einer Datei.** UI-Modelle Z. 101–271; Panels
  General (1101), Appearance (3690), Playback (543), EPG (1494) inkl. globaler Einstellungen + manuellem
  EPG-Mapping (1827–2168) + EPG-Quellen-CRUD (2171–2464), Provider (2466) inkl. **Add-Wizard**
  `ProviderAddFlow` (2961–3247) + `ProviderEditor` (3347–3625), Parental/PIN (3779–4021), Backup (4024–4193),
  About (4196–4367), Maintenance/Diagnose (4369–4563).
- **State + Datenzugriff im Composable.** `LiveTvRoute` (`feature/livetv/LiveTvRoute.kt`) nimmt
  `providerRepository`, `mediaRepository`, `epgRepository`, `favoritesRepository` direkt entgegen und ruft
  `providerRepository.observeProviders().collectAsState(...)` (Z. 137 ff.) in der Composition. Gleiches Muster
  in `MoviesRoute`, `SeriesRoute`, `SearchRoute`, `HomeRoute`.
- **`collectAsState` statt `collectAsStateWithLifecycle`** durchgängig (z.B. `HomeRoute:58`,
  `LiveTvRoute:137`, `SettingsRoute:1502`, `MainActivity:399`), obwohl `androidx-lifecycle-runtime-compose`
  im Version-Catalog vorhanden ist.
- **DI-Container mit Business-Logik.** `AppContainer.testProviderConnection/testM3uConnection/
  testXtreamConnection` (Z. 291–336) parsen Playlists und werten HTTP-Status aus – das gehört in ein
  Provider-UseCase, nicht in die DI-Naht.
- **Große Repository-/Import-Datei.** `RoomCatalogImportRepository.kt` (550 Z.) mischt Import-Steuerung,
  Kategorie-Diff, Upserts **und** alle DTO→Entity-Mapper (`toEntity`) + ID-Generierung. Grenzwertig, aber
  kohäsiv (eine Verantwortung: Katalog-Import). Split optional (P2), nicht dringend.

---

## Kritische Architektur-Abhängigkeiten

- **Layer-Leak (Aufwärtsrichtung, moderat):** Business-Logik (Playback-Orchestrierung, Preference-Mapping,
  Verbindungstest) liegt im **UI-/app-Modul** (`MainActivity`, `AppContainer`) statt in `data`/`domain`.
  Keine falsche *Compile*-Abhängigkeit, aber die falsche *Logik*-Schicht.
- **Kein Room-Entity-Leak in die UI:** ✔ geprüft – `RoomMediaRepository.observe*()` mappt `Entity.toDomain()`,
  Composables sehen nur Domain-Modelle. Gut.
- **Kein DTO-Leak in Domain:** ✔ – IPTV-DTOs (`M3uChannel`, `XtreamVodItem`) enden im Import-Repository.
- **Kein Feature→Feature-Zyklus:** ✔ – Cross-Feature-Navigation läuft über `MainActivity`-Callbacks.
- **Hart codierte Dispatcher:** `Dispatchers.IO` direkt in `worker/RefreshExecution.kt` (Z. 312, 334) und
  `iptv/xtream/XtreamContracts.kt` (Z. 103). Der Player macht es besser (injizierter Dispatcher,
  `VivicastPlayerController.kt:132/133`). Geringes Risiko, P3.
- **Globaler Singleton mit Zustand:** `RefreshWorkerRegistry.install(...)` (statische Registry für den
  Worker). Pragmatisch nötig wegen WorkManager-Instanziierung; akzeptabel, aber dokumentieren.
- **Kein Context-Leak in ViewModels:** trivially ✔, weil es keine ViewModels gibt – bei deren Einführung
  ist die Google-Regel „kein `Context`/`Activity`/`Resources` im ViewModel" aktiv zu beachten.

---

## Gute bestehende Entscheidungen (NICHT anfassen)

| Bereich | Warum ausreichend / gut |
|---|---|
| **Modulschnitt** (`:core`/`:data`/`:feature`/`:iptv`/`:domain`/`:worker`) | Entspricht Now-in-Android-Konventionen, korrekte Abhängigkeitsrichtung, kein Zyklus. Kein Umbau nötig. |
| **Player-Schicht** (`:core:player`) | `VivicastPlayerController`-Interface, `DefaultVivicastPlayerController` + `Media3PlaybackEngine`, State als `StateFlow<VivicastPlayerState>`, injizierter Dispatcher, `release()` vorhanden, UI konsumiert nur State. Referenzqualität, eigener Unit-Test. **Nicht anfassen.** |
| **Modelltrennung** DTO→Entity→Domain→UI | Vier saubere Ebenen, Domain-Modelle frei von Room/DTO. Mapping in Repositories. Musterhaft. |
| **Data-Layer** (`:data:*`) | Interface + Room-Impl je Modul, SSOT über Room-Flows, Repos main-safe (Room). Konform zur Google-Empfehlung. |
| **Background/Refresh** (`:worker`) | Import/EPG/Logo/Cache sauber aus UI/ViewModel ausgelagert, WorkManager korrekt genutzt, testbar (Unit-Tests vorhanden). |
| **IPTV-Parser** (`:iptv:m3u/xtream/xmltv`) | Als eigene Module gekapselt, DTO-Contracts getrennt, reine Parser mit Unit-Tests. |
| **Testabdeckung** insgesamt | 39 Testdateien inkl. DB-Migration, Backup, Fokus. Deutlich überdurchschnittlich. |
| **Security** (`:core:security`) | Keystore-basierter `SecureValueStore`, PIN separat, `allowBackup=false`. Konform zu den Projektvorgaben. |
| **Manuelle DI** | Für die aktuelle Größe angemessen; ein Hilt-Umbau ist **nicht** dringend (siehe Entscheidungspunkte). |

---

## Entscheidungspunkte (durch Projektinhaber zu klären)

1. **Presentation-Holder: `ViewModel` oder plain State-Holder?** Empfehlung: `ViewModel` (WorkManager + mehrere
   Screens sprechen dafür; `lifecycle-viewmodel-compose` ist im Catalog). Plain-Holder nur, wenn Feature-Module
   bewusst compose-lifecycle-frei bleiben sollen.
2. **Hilt einführen oder `AppContainer` behalten?** Empfehlung: **vorerst behalten.** Erst nach Einführung der
   ViewModels neu bewerten – dann bringt Hilt (`hiltViewModel()`) echten Nutzen.
3. **UseCases: flächendeckend oder selektiv?** Empfehlung: **selektiv** – nur Playback-Orchestrierung,
   EPG-Mapping, Provider-Verbindungstest.
4. **Domain-Repository-Interfaces nach `:domain` verschieben?** Empfehlung: **nein** – Interface + Impl im
   `:data:*`-Modul (Google-pragmatisch) ist ausreichend; Verschiebung wäre reiner Churn.
5. **Settings-Preference-Mapper: nach `:feature:settings` oder `:core:datastore`?** Empfehlung:
   `:feature:settings`, da es UI-Modelle sind.
6. **Fehlerbehandlung:** `AppResult` behalten (kein `Either`). Zur Bestätigung freigeben.
7. **detekt/ktlint einführen?** Empfehlung: **ja**, detekt mit Zeilenlimit-Regel als Gate gegen neue God-Files.

---

## Gesamtempfehlung

**„Layering vervollständigen + zwei God-Files splitten" – Multi-Module beibehalten, nicht neu modularisieren.**

Konkret, in dieser Reihenfolge: (1) `SettingsRoute.kt` und `MainActivity.kt` **mechanisch datei-splitten**
(risikoarm, sofort testabgesichert); (2) **pro Feature einen State-Holder** einführen und Repositories aus den
Composables ziehen; (3) **Playback-Orchestrierung** aus `MainActivity` in UseCases/Repository ziehen; (4)
`collectAsStateWithLifecycle` + detekt-Gate. Der Data-, Player-, Worker-, Parser- und Modul-Layer bleibt
unangetastet – dort hätte Refactoring **mehr Risiko als Nutzen**.

Details und Reihenfolge: [ARCHITECTURE-REFACTORING-PLAN.md](ARCHITECTURE-REFACTORING-PLAN.md).
Datei-Splits: [ARCHITECTURE-FILE-SPLIT-PLAN.md](ARCHITECTURE-FILE-SPLIT-PLAN.md).
