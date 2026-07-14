package com.vivicast.tv.feature.settings

data class GeneralSettingsState(
    val launchOnBoot: Boolean = false,
    val doubleBackToExit: Boolean = true,
    val backgroundRefreshEnabled: Boolean = true,
    val resumeLastChannelOnStart: Boolean = false,
    val appLanguage: SettingsLanguage = SettingsLanguage.System,
    val globalUserAgent: String = "Vivicast/1.0",
)

data class AppearanceSettingsState(
    val themeMode: SettingsThemeMode = SettingsThemeMode.Blue,
    val accentColor: SettingsAccentColor = SettingsAccentColor.Blue,
    val transparency: SettingsTransparency = SettingsTransparency.Percent20,
    val fontScale: SettingsFontScale = SettingsFontScale.Medium,
    val animationSpeed: SettingsAnimationSpeed = SettingsAnimationSpeed.Normal,
)

enum class SettingsLanguage {
    System,
    German,
    English,
}

// Background "colour" (dark-tinted). Order = picker display order. Mirrors datastore ThemeColor.
enum class SettingsThemeMode {
    Red, Pink, Purple, Indigo, Blue, Cyan, Teal, Green, Lime, Yellow,
    Amber, Orange, Brown, Grey, BlueGrey, DarkGrey, Black,
}

// Accent = background list minus DarkGrey + Black. Mirrors datastore AccentColor.
enum class SettingsAccentColor {
    Red, Pink, Purple, Indigo, Blue, Cyan, Teal, Green, Lime, Yellow,
    Amber, Orange, Brown, Grey, BlueGrey,
}

// 0..100 % in 10 % steps.
enum class SettingsTransparency {
    Percent0, Percent10, Percent20, Percent30, Percent40, Percent50,
    Percent60, Percent70, Percent80, Percent90, Percent100,
}

enum class SettingsFontScale {
    Small,
    Medium,
    Large,
    ExtraLarge,
}

enum class SettingsAnimationSpeed {
    Off,
    Fast,
    Normal,
    Slow,
}

data class CacheSettingsState(
    val totalSizeBytes: Long = 0L,
    val fileCount: Int = 0,
)

data class EpgSettingsState(
    val refreshIntervalHours: Int = 24,
    val pastRetentionDays: Int = 1,
    val refreshOnAppStartEnabled: Boolean = false,
    val refreshOnPlaylistChangeEnabled: Boolean = true,
)

data class PlaybackSettingsState(
    val bufferSize: PlaybackBufferSizeMode = PlaybackBufferSizeMode.Medium,
    val audioDecoder: PlaybackDecoderMode = PlaybackDecoderMode.Hardware,
    val videoDecoder: PlaybackDecoderMode = PlaybackDecoderMode.Hardware,
    val afrEnabled: Boolean = false,
    val preferredAudioLanguage: PlaybackAudioLanguage = PlaybackAudioLanguage.SystemDefault,
    val preferredSubtitleLanguage: PlaybackSubtitleLanguage = PlaybackSubtitleLanguage.Off,
    val audioPassthroughEnabled: Boolean = false,
    val externalPlayer: PlaybackExternalPlayerMode = PlaybackExternalPlayerMode.Internal,
    val autoNextEnabled: Boolean = false,
    val autoNextCountdownSeconds: Int = 10,
)

data class ParentalControlSettingsState(
    val hasPin: Boolean = false,
    val lockedUntilMillis: Long = 0L,
    val protectSettings: Boolean = false,
    val protectMovies: Boolean = false,
    val protectSeries: Boolean = false,
    val protectAdultContent: Boolean = false,
)

enum class ParentalProtectionArea {
    Settings,
    Movies,
    Series,
    AdultContent,
}

enum class PlaybackExternalPlayerMode {
    Internal,
    External,
    AskEveryTime,
}

enum class PlaybackBufferSizeMode {
    Off,
    Small,
    Medium,
    Large,
    ExtraLarge,
}

enum class PlaybackDecoderMode {
    Hardware,
    Software,
}

enum class PlaybackAudioLanguage {
    SystemDefault,
    German,
    English,
    Original,
}

enum class PlaybackSubtitleLanguage {
    Off,
    SystemDefault,
    German,
    English,
}

data class AboutAppState(
    val appVersion: String = "Unbekannt",
    val buildNumber: String = "0",
    val packageName: String = "com.vivicast.tv",
    val databaseVersion: Int = 0,
    val androidVersion: String = "Unbekannt",
    val deviceModel: String = "Unbekannt",
    val playerEngine: String = "Media3/ExoPlayer",
    val languageTag: String = "Unbekannt",
    val timeZoneId: String = "Unbekannt",
)

data class DiagnosticsSettingsState(
    val diagnosticsLoggingEnabled: Boolean = false,
)

enum class HistoryClearTarget {
    LiveTv,
    Movies,
    Series,
    Search,
}

