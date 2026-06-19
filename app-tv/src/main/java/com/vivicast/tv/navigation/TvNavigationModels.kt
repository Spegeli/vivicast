package com.vivicast.tv

enum class TvSection {
    Search,
    LiveTv,
    Movies,
    Series,
    Favorites,
    Recent,
    Guide,
    Settings
}

enum class SettingsSection(val label: String) {
    General("General"),
    Providers("Providers"),
    Epg("EPG"),
    Appearance("Appearance"),
    Playback("Playback"),
    Remote("Remote control"),
    About("About")
}

fun TvSection.label(): String = when (this) {
    TvSection.Search -> "Search"
    TvSection.LiveTv -> "Live TV"
    TvSection.Movies -> "Movies"
    TvSection.Series -> "Series"
    TvSection.Favorites -> "Favorites"
    TvSection.Recent -> "Recently watched"
    TvSection.Guide -> "Guide"
    TvSection.Settings -> "Settings"
}

enum class LiveTvFocusTarget {
    Filters,
    Channels
}
