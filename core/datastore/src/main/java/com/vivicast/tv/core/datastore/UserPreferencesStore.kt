package com.vivicast.tv.core.datastore

import kotlinx.coroutines.flow.Flow

interface UserPreferencesStore {
    val values: Flow<UserPreferences>

    suspend fun updateSelectedProviderId(providerId: String?)
    suspend fun updateLocalLogoFolder(path: String?)
    suspend fun updateGeneral(general: GeneralPreferences)
    suspend fun updateAppearance(appearance: AppearancePreferences)
    suspend fun updatePlayback(playback: PlaybackPreferences)
    suspend fun updateSearchHistory(searchHistory: List<String>)
    suspend fun updateExpandedLiveTvProviderIds(providerIds: Set<String>)
    suspend fun updateEpg(epg: EpgPreferences)
    suspend fun updateBackup(backup: BackupPreferences)
    suspend fun updateDiagnostics(diagnostics: DiagnosticsPreferences)
}

data class UserPreferences(
    val selectedProviderId: String? = null,
    // Device-local absolute path to the user's logos folder (for LOGO_PRIORITY_LOCAL). Not part of a backup.
    val localLogoFolder: String? = null,
    val general: GeneralPreferences = GeneralPreferences(),
    val appearance: AppearancePreferences = AppearancePreferences(),
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val searchHistory: List<String> = emptyList(),
    val expandedLiveTvProviderIds: Set<String> = emptySet(),
    val epg: EpgPreferences = EpgPreferences(),
    val backup: BackupPreferences = BackupPreferences(),
    val diagnostics: DiagnosticsPreferences = DiagnosticsPreferences(),
)

data class GeneralPreferences(
    val launchOnBoot: Boolean = false,
    val doubleBackToExit: Boolean = true,
    val backgroundRefreshEnabled: Boolean = true,
    val resumeLastChannelOnStart: Boolean = false,
    val globalUserAgent: String = DEFAULT_GLOBAL_USER_AGENT,
)

data class AppearancePreferences(
    val backgroundColor: ThemeColor = ThemeColor.Blue,
    val accentColor: AccentColor = AccentColor.Blue,
    val transparency: TransparencyLevel = TransparencyLevel.Percent20,
    val fontScale: FontScalePreference = FontScalePreference.Medium,
    val language: LanguagePreference = LanguagePreference.System,
)

data class PlaybackPreferences(
    val bufferSize: BufferSizePreference = BufferSizePreference.Medium,
    val audioDecoder: DecoderPreference = DecoderPreference.Hardware,
    val videoDecoder: DecoderPreference = DecoderPreference.Hardware,
    val afrEnabled: Boolean = false,
    val audioPassthroughEnabled: Boolean = false,
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val externalPlayer: ExternalPlayerPreference = ExternalPlayerPreference.Internal,
    val autoNextEnabled: Boolean = false,
    val autoNextCountdownSeconds: Int = 10,
)

data class EpgPreferences(
    // Global auto-refresh interval for ALL EPG sources; 0 = off. Was per-source, now global again.
    val refreshIntervalHours: Int = 24,
    val pastRetentionDays: Int = 1,
    val refreshOnAppStartEnabled: Boolean = false,
    val refreshOnPlaylistChangeEnabled: Boolean = true,
)

data class BackupPreferences(
    // Device-local last export folder for the in-app picker (never part of a backup container).
    val lastExportDir: String? = null,
)

data class DiagnosticsPreferences(
    val diagnosticsLoggingEnabled: Boolean = false,
    val keepLastSessionSummary: Boolean = true,
    // Device-local last export folder for the in-app picker.
    val lastExportDir: String? = null,
)

// Background "colour" (dark-tinted). Order = picker display order. Persisted by .name with graceful
// fallback, so reordering/adding is migration-free; old values (Dark/HighContrastDark/AmoledDark) fall
// back to the default (Blue).
enum class ThemeColor {
    Red, Pink, Purple, Indigo, Blue, Cyan, Teal, Green, Lime, Yellow,
    Amber, Orange, Brown, Grey, BlueGrey, DarkGrey, Black,
}

// Accent = background list minus DarkGrey + Black (a near-black accent can't be a visible highlight on a
// dark app, and would mislead against its black picker swatch).
enum class AccentColor {
    Red, Pink, Purple, Indigo, Blue, Cyan, Teal, Green, Lime, Yellow,
    Amber, Orange, Brown, Grey, BlueGrey,
}

// 0..100 % in 10 % steps. Opacity = (100 - percent)/100.
enum class TransparencyLevel {
    Percent0, Percent10, Percent20, Percent30, Percent40, Percent50,
    Percent60, Percent70, Percent80, Percent90, Percent100,
}

enum class FontScalePreference { Small, Medium, Large }

enum class LanguagePreference { System, German, English }

enum class BufferSizePreference { Off, Small, Medium, Large }

enum class DecoderPreference { Hardware, Software }

enum class ExternalPlayerPreference { Internal, External, AskEveryTime }


const val DEFAULT_GLOBAL_USER_AGENT = "Vivicast/1.0"
