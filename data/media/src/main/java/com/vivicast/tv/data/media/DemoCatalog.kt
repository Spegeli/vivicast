package com.vivicast.tv.data.media

object DemoCatalog {
    val providers = listOf(
        DemoProvider("Provider A", ProviderStatus.Active, "Aktiv"),
        DemoProvider("Provider B", ProviderStatus.Error, "Anmeldung fehlgeschlagen"),
        DemoProvider("Provider C", ProviderStatus.Disabled, "Deaktiviert"),
    )

    val categories = listOf("Favoriten", "News", "Sport", "Filme", "Serien", "Kinder", "Unterhaltung", "Leer")

    val channels = listOf(
        DemoChannel(
            name = "ARD HD",
            categories = listOf("Favoriten", "News"),
            program = "Tagesschau",
            logoState = AssetState.Available,
            logoResId = R.drawable.demo_logo_ard,
            hasEpg = true,
            catchUp = false,
            favorite = true,
            description = "Nachrichten kompakt mit Wetter und Sport.",
            epg = listOf(
                DemoEpgItem("19:45", "20:00", "Wetter vor acht", false),
                DemoEpgItem("20:00", "20:15", "Tagesschau", true),
                DemoEpgItem("20:15", "20:45", "Tagesthemen", false),
                DemoEpgItem("20:45", "21:45", "Reportage", false),
            ),
        ),
        DemoChannel(
            name = "ZDF HD",
            categories = listOf("Favoriten", "News"),
            program = "heute journal",
            logoState = AssetState.Available,
            logoResId = R.drawable.demo_logo_zdf,
            hasEpg = true,
            catchUp = true,
            favorite = true,
            description = "Nachrichtenmagazin mit Analyse und Einordnung.",
            epg = listOf(
                DemoEpgItem("19:25", "20:15", "Magazin", false),
                DemoEpgItem("20:15", "21:00", "heute journal", true, catchUp = true),
                DemoEpgItem("21:00", "22:00", "Dokumentation", false, catchUp = true),
            ),
        ),
        DemoChannel(
            name = "Kultur Eins",
            categories = listOf("Filme"),
            program = "Keine EPG-Daten",
            logoState = AssetState.Missing,
            logoResId = R.drawable.demo_logo_one,
            hasEpg = false,
            catchUp = false,
            description = "Fallback-Sender ohne Logo und EPG.",
            epg = emptyList(),
        ),
        DemoChannel(
            name = "RTL HD",
            categories = listOf("Favoriten", "Unterhaltung"),
            program = "Wer wird Millionär?",
            logoState = AssetState.Available,
            logoResId = R.drawable.demo_logo_rtl,
            hasEpg = true,
            catchUp = false,
            favorite = true,
            description = "Quizshow im Abendprogramm.",
            epg = listOf(
                DemoEpgItem("19:05", "20:15", "Daily", false),
                DemoEpgItem("20:15", "22:15", "Wer wird Millionär?", true),
                DemoEpgItem("22:15", "23:00", "Nachrichten", false),
            ),
        ),
        DemoChannel(
            name = "Sport Live 1",
            categories = listOf("Sport"),
            program = "Bundesliga Live",
            logoState = AssetState.Available,
            logoResId = R.drawable.demo_logo_sport1,
            hasEpg = true,
            catchUp = true,
            description = "Live-Sport mit Timeshift.",
            epg = listOf(
                DemoEpgItem("19:00", "20:00", "Vorberichte", false, catchUp = true),
                DemoEpgItem("20:00", "22:00", "Bundesliga Live", true, catchUp = true),
                DemoEpgItem("22:00", "22:30", "Highlights", false, catchUp = true),
            ),
        ),
        DemoChannel(
            name = "Phoenix HD",
            categories = listOf("News"),
            program = "Dokumentation",
            logoState = AssetState.Available,
            logoResId = R.drawable.demo_logo_phoenix,
            hasEpg = true,
            catchUp = false,
            description = "Dokumentationen und politische Einordnung.",
            epg = listOf(
                DemoEpgItem("19:30", "20:15", "Gespräch", false),
                DemoEpgItem("20:15", "21:00", "Dokumentation", true),
                DemoEpgItem("21:00", "21:45", "Analyse", false),
            ),
        ),
    )

    val movies = listOf(
        DemoVodItem(
            title = "Dune: Part Two",
            rating = "8.6",
            year = "2024",
            runtime = "166 min",
            posterState = AssetState.Available,
            backdropState = AssetState.Available,
            posterResId = R.drawable.demo_poster_dune,
            backdropResId = R.drawable.demo_backdrop_dune,
            progressPercent = 52,
            favorite = true,
            seen = false,
            description = "Paul Atreides sucht seinen Platz zwischen Rache, Macht und Prophezeiung.",
        ),
        DemoVodItem(
            title = "Interstellar",
            rating = "8.7",
            year = "2014",
            runtime = "169 min",
            posterState = AssetState.Available,
            backdropState = AssetState.Available,
            posterResId = R.drawable.demo_poster_interstellar,
            backdropResId = R.drawable.demo_backdrop_interstellar,
            progressPercent = 40,
            favorite = false,
            seen = false,
            description = "Eine Reise durch Raum, Zeit und Verantwortung.",
        ),
        DemoVodItem(
            title = "Kein Poster Film",
            rating = "6.8",
            year = "2022",
            runtime = "101 min",
            posterState = AssetState.Missing,
            backdropState = AssetState.Missing,
            posterResId = null,
            backdropResId = null,
            progressPercent = 0,
            favorite = false,
            seen = true,
            description = "",
        ),
        DemoVodItem(
            title = "Seen Signal",
            rating = "7.1",
            year = "2020",
            runtime = "94 min",
            posterState = AssetState.Available,
            backdropState = AssetState.Missing,
            posterResId = R.drawable.demo_poster_signal,
            backdropResId = R.drawable.demo_backdrop_signal,
            progressPercent = 100,
            favorite = true,
            seen = true,
            description = "Gesehen- und Favoritenstatus auf einer Karte.",
        ),
    )

    val series = listOf(
        DemoSeriesItem(
            title = "Dune: Prophecy",
            rating = "7.4",
            seasons = 1,
            episodes = 6,
            posterState = AssetState.Available,
            posterResId = R.drawable.demo_poster_prophecy,
            backdropResId = R.drawable.demo_backdrop_prophecy,
            progressLabel = "Nächste Episode",
            description = "Politische Intrigen in der Welt der Bene Gesserit.",
        ),
        DemoSeriesItem(
            title = "The Expanse",
            rating = "8.5",
            seasons = 6,
            episodes = 62,
            posterState = AssetState.Available,
            posterResId = R.drawable.demo_poster_expanse,
            backdropResId = R.drawable.demo_backdrop_expanse,
            progressLabel = "S2 E4 fortsetzen",
            description = "Staffeln, Episoden und teilweise gesehene Episode.",
        ),
        DemoSeriesItem(
            title = "Kein Poster Serie",
            rating = "6.9",
            seasons = 2,
            episodes = 12,
            posterState = AssetState.Missing,
            posterResId = null,
            backdropResId = null,
            progressLabel = "Episode gesehen",
            description = "Fallback für fehlendes Serienposter.",
        ),
    )

    val searchResults = DemoSearchResults(
        channels = listOf("Dune TV", "Sci-Fi Channel"),
        movies = listOf(
            RatedResult("Dune", "8.1"),
            RatedResult("Dune: Part Two", "8.6"),
        ),
        series = listOf(
            RatedResult("Dune: Prophecy", "7.4"),
            RatedResult("Dune: The Sisterhood", "7.2"),
        ),
        epg = listOf("Dune Special, heute 22:15 auf Movie Channel"),
    )

    val settingsSections = listOf("Allgemein", "Wiedergabelisten", "EPG", "Optik", "Wiedergabe", "Status")

    val settings = listOf(
        DemoSetting("Hintergrundfarbe", "Dunkel", "Ruhiger Premium-TV-Hintergrund."),
        DemoSetting("Akzentfarbe", "Blau", "Cyan-Fokus und klare Auswahlzustände."),
        DemoSetting("Transparenz", "25 Prozent", "Bottom-Overlays bleiben lesbar."),
        DemoSetting("Schriftgröße", "Mittel", "Optimiert für 1080p TV-Abstand."),
        DemoSetting("Animationen", "Normal", "Ruhige Fokusübergänge."),
        DemoSetting("Preview-Startverhalten", "Direkt starten", "OK auf Sender startet die Vorschau sofort."),
    )

    val playerStates = listOf(
        DemoPlayerState("Live-TV ohne Timeshift", listOf("Full HD", "50 FPS", "Stereo"), false),
        DemoPlayerState("Live-TV mit Timeshift", listOf("HD", "25 FPS", "Mono"), true),
        DemoPlayerState("VOD Wiedergabe", listOf("4K", "60 FPS", "5.1"), true),
    )
}

data class DemoProvider(val name: String, val status: ProviderStatus, val statusText: String)

enum class ProviderStatus { Active, Error, Disabled }

enum class AssetState { Available, Missing }

data class DemoChannel(
    val name: String,
    val categories: List<String>,
    val program: String,
    val logoState: AssetState,
    val logoResId: Int? = null,
    val hasEpg: Boolean,
    val catchUp: Boolean,
    val favorite: Boolean = false,
    val description: String,
    val epg: List<DemoEpgItem>,
)

data class DemoEpgItem(
    val start: String,
    val end: String,
    val title: String,
    val current: Boolean,
    val catchUp: Boolean = false,
)

data class DemoVodItem(
    val title: String,
    val rating: String,
    val year: String,
    val runtime: String,
    val posterState: AssetState,
    val backdropState: AssetState,
    val posterResId: Int? = null,
    val backdropResId: Int? = null,
    val progressPercent: Int,
    val favorite: Boolean,
    val seen: Boolean,
    val description: String,
)

data class DemoSeriesItem(
    val title: String,
    val rating: String,
    val seasons: Int,
    val episodes: Int,
    val posterState: AssetState,
    val posterResId: Int? = null,
    val backdropResId: Int? = null,
    val progressLabel: String,
    val description: String,
)

data class DemoSearchResults(
    val channels: List<String>,
    val movies: List<RatedResult>,
    val series: List<RatedResult>,
    val epg: List<String>,
)

data class RatedResult(val title: String, val rating: String)

data class DemoSetting(val title: String, val value: String, val help: String)

data class DemoPlayerState(val title: String, val badges: List<String>, val seekable: Boolean)
