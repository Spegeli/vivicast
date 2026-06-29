package com.vivicast.tv.core.datastore

import kotlinx.coroutines.flow.Flow

interface UserPreferencesStore {
    val values: Flow<UserPreferences>

    suspend fun updateSelectedProviderId(providerId: String?)
    suspend fun updateGeneral(general: GeneralPreferences)
    suspend fun updateAppearance(appearance: AppearancePreferences)
    suspend fun updatePlayback(playback: PlaybackPreferences)
    suspend fun updateHistory(history: HistoryPreferences)
    suspend fun updateSearchHistory(searchHistory: List<String>)
    suspend fun updateExpandedLiveTvProviderIds(providerIds: Set<String>)
    suspend fun updateParentalControl(parentalControl: ParentalControlPreferences)
    suspend fun updateEpg(epg: EpgPreferences)
    suspend fun updateBackup(backup: BackupPreferences)
    suspend fun updateDiagnostics(diagnostics: DiagnosticsPreferences)
}

data class UserPreferences(
    val selectedProviderId: String? = null,
    val general: GeneralPreferences = GeneralPreferences(),
    val appearance: AppearancePreferences = AppearancePreferences(),
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val history: HistoryPreferences = HistoryPreferences(),
    val searchHistory: List<String> = emptyList(),
    val expandedLiveTvProviderIds: Set<String> = emptySet(),
    val parentalControl: ParentalControlPreferences = ParentalControlPreferences(),
    val epg: EpgPreferences = EpgPreferences(),
    val backup: BackupPreferences = BackupPreferences(),
    val diagnostics: DiagnosticsPreferences = DiagnosticsPreferences(),
)

data class GeneralPreferences(
    val launchOnBoot: Boolean = false,
    val doubleBackToExit: Boolean = true,
    val backgroundRefreshEnabled: Boolean = true,
    val rememberSorting: Boolean = true,
    val globalUserAgent: String = DEFAULT_GLOBAL_USER_AGENT,
    val lastSettingsSection: String? = null,
)

data class AppearancePreferences(
    val backgroundColor: ThemeColor = ThemeColor.Dark,
    val accentColor: AccentColor = AccentColor.Blue,
    val transparency: TransparencyLevel = TransparencyLevel.Percent25,
    val fontScale: FontScalePreference = FontScalePreference.Medium,
    val language: LanguagePreference = LanguagePreference.System,
    val animationSpeed: AnimationSpeedPreference = AnimationSpeedPreference.Normal,
)

data class PlaybackPreferences(
    val bufferSize: BufferSizePreference = BufferSizePreference.Medium,
    val audioDecoder: DecoderPreference = DecoderPreference.Hardware,
    val videoDecoder: DecoderPreference = DecoderPreference.Hardware,
    val afrEnabled: Boolean = false,
    val audioPassthroughEnabled: Boolean = false,
    val timeshiftEnabled: Boolean = true,
    val timeshiftStorage: TimeshiftStoragePreference = TimeshiftStoragePreference.Automatic,
    val timeshiftMinutes: Int = 30,
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val externalPlayer: ExternalPlayerPreference = ExternalPlayerPreference.Internal,
    val autoNextEnabled: Boolean = false,
    val autoNextCountdownSeconds: Int = 10,
)

data class HistoryPreferences(
    val enabled: Boolean = true,
    val maxRecentChannels: Int = 50,
    val watchedThresholdPercent: Int = 95,
)

data class ParentalControlPreferences(
    val pinEnabled: Boolean = false,
    val protectSettings: Boolean = false,
    val protectMovies: Boolean = false,
    val protectSeries: Boolean = false,
    val protectAdultContent: Boolean = false,
)

data class EpgPreferences(
    val pastRetentionDays: Int = 1,
    val futureRetentionDays: Int = 7,
    val refreshIntervalHours: Int = 24,
    val refreshOnAppStartEnabled: Boolean = true,
    val refreshOnPlaylistChangeEnabled: Boolean = true,
)

data class BackupPreferences(
    val target: BackupTargetPreference = BackupTargetPreference.LocalStorage,
    val lastBackupAtMillis: Long? = null,
)

data class DiagnosticsPreferences(
    val diagnosticsLoggingEnabled: Boolean = false,
    val retentionDays: Int = 1,
    val keepLastSessionSummary: Boolean = true,
)

enum class ThemeColor { Dark, HighContrastDark, AmoledDark }

enum class AccentColor { Blue }

enum class TransparencyLevel { Percent0, Percent25, Percent50, Percent75 }

enum class FontScalePreference { Small, Medium, Large, ExtraLarge }

enum class LanguagePreference { System, German, English }

enum class AnimationSpeedPreference { Off, Slow, Normal, Fast }

enum class BufferSizePreference { Off, Small, Medium, Large, ExtraLarge }

enum class DecoderPreference { Automatic, Hardware, Software }

enum class TimeshiftStoragePreference { Automatic, Ram, InternalStorage }

enum class ExternalPlayerPreference { Internal, External, AskEveryTime }

enum class BackupTargetPreference { LocalStorage, Smb, GoogleDrive }

const val DEFAULT_GLOBAL_USER_AGENT = "Vivicast/1.0"
