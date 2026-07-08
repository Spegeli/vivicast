package com.vivicast.tv.feature.settings

data class GeneralSettingsState(
    val launchOnBoot: Boolean = false,
    val doubleBackToExit: Boolean = true,
    val backgroundRefreshEnabled: Boolean = true,
    val appLanguage: SettingsLanguage = SettingsLanguage.System,
    val globalUserAgent: String = "Vivicast/1.0",
)

data class AppearanceSettingsState(
    val themeMode: SettingsThemeMode = SettingsThemeMode.StandardDark,
    val accentColor: SettingsAccentColor = SettingsAccentColor.Blue,
    val transparency: SettingsTransparency = SettingsTransparency.Percent25,
    val fontScale: SettingsFontScale = SettingsFontScale.Medium,
    val animationSpeed: SettingsAnimationSpeed = SettingsAnimationSpeed.Normal,
)

enum class SettingsLanguage {
    System,
    German,
    English,
}

enum class SettingsThemeMode {
    StandardDark,
    HighContrastDark,
    AmoledDark,
}

enum class SettingsAccentColor {
    Blue,
}

enum class SettingsTransparency {
    Percent0,
    Percent25,
    Percent50,
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

data class BackupSettingsState(
    val target: BackupTargetMode = BackupTargetMode.LocalStorage,
    val lastBackupAtMillis: Long? = null,
)

enum class BackupTargetMode {
    LocalStorage,
    Smb,
    GoogleDrive,
}

data class EpgSettingsState(
    val pastRetentionDays: Int = 1,
    val refreshOnAppStartEnabled: Boolean = true,
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
    val timeshiftEnabled: Boolean = true,
    val timeshiftMinutes: Int = 30,
    val timeshiftStorage: PlaybackTimeshiftStorageMode = PlaybackTimeshiftStorageMode.Automatic,
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

enum class PlaybackTimeshiftStorageMode {
    Automatic,
    Ram,
    InternalStorage,
}

data class AboutAppState(
    val appVersion: String = "Unbekannt",
    val packageName: String = "com.vivicast.tv",
    val databaseVersion: Int = 0,
    val androidVersion: String = "Unbekannt",
    val deviceModel: String = "Unbekannt",
    val languageTag: String = "Unbekannt",
    val timeZoneId: String = "Unbekannt",
    val supportInformationText: String = "",
)

data class DiagnosticsSettingsState(
    val diagnosticsLoggingEnabled: Boolean = false,
    val retentionDays: Int = 1,
)

enum class HistoryClearTarget {
    LiveTv,
    Movies,
    Series,
    Search,
    All,
}

