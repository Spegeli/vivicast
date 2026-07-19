package com.vivicast.tv.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vivicastPreferencesDataStore by preferencesDataStore(name = "vivicast_preferences")
private const val LIST_SEPARATOR = "\u001F"
private const val MAX_SEARCH_HISTORY = 20

class DataStoreUserPreferencesStore(
    context: Context,
) : UserPreferencesStore {
    private val dataStore = context.applicationContext.vivicastPreferencesDataStore

    override val values: Flow<UserPreferences> =
        dataStore.data.map { preferences ->
            UserPreferences(
                selectedProviderId = preferences[Keys.SelectedProviderId],
                localLogoFolder = preferences[Keys.LocalLogoFolder],
                general = GeneralPreferences(
                    launchOnBoot = preferences[Keys.LaunchOnBoot] ?: false,
                    doubleBackToExit = preferences[Keys.DoubleBackToExit] ?: true,
                    backgroundRefreshEnabled = preferences[Keys.BackgroundRefreshEnabled] ?: true,
                    resumeLastChannelOnStart = preferences[Keys.ResumeLastChannelOnStart] ?: false,
                    globalUserAgent = preferences[Keys.GlobalUserAgent]?.takeIf { it.isNotBlank() }
                        ?: DEFAULT_GLOBAL_USER_AGENT,
                ),
                appearance = AppearancePreferences(
                    backgroundColor = preferences.enumValue(Keys.BackgroundColor, ThemeColor.Blue),
                    accentColor = preferences.enumValue(Keys.AccentColor, AccentColor.Blue),
                    transparency = preferences.enumValue(Keys.Transparency, TransparencyLevel.Percent20),
                    fontScale = preferences.enumValue(Keys.FontScale, FontScalePreference.Medium),
                    language = preferences.enumValue(Keys.Language, LanguagePreference.System),
                ),
                playback = PlaybackPreferences(
                    bufferSize = preferences.enumValue(Keys.BufferSize, BufferSizePreference.Medium),
                    audioDecoder = preferences.enumValue(Keys.AudioDecoder, DecoderPreference.Hardware),
                    videoDecoder = preferences.enumValue(Keys.VideoDecoder, DecoderPreference.Hardware),
                    afrEnabled = preferences[Keys.AfrEnabled] ?: false,
                    audioPassthroughEnabled = preferences[Keys.AudioPassthroughEnabled] ?: false,
                    preferredAudioLanguage = preferences[Keys.PreferredAudioLanguage],
                    preferredSubtitleLanguage = preferences[Keys.PreferredSubtitleLanguage],
                    externalPlayer = preferences.enumValue(Keys.ExternalPlayer, ExternalPlayerPreference.Internal),
                    autoNextEnabled = preferences[Keys.AutoNextEnabled] ?: false,
                    autoNextCountdownSeconds = preferences[Keys.AutoNextCountdownSeconds].validAutoNextCountdown(),
                ),
                searchHistory = preferences[Keys.SearchHistory].toSearchHistory(),
                expandedLiveTvProviderIds = preferences[Keys.ExpandedLiveTvProviderIds].toStoredIdSet(),
                epg = EpgPreferences(
                    refreshIntervalHours = (preferences[Keys.EpgRefreshIntervalHours] ?: 24).coerceIn(0, 168),
                    pastRetentionDays = (preferences[Keys.EpgPastRetentionDays] ?: 1).coerceIn(1, 14),
                    refreshOnAppStartEnabled = preferences[Keys.EpgRefreshOnAppStartEnabled] ?: false,
                    refreshOnPlaylistChangeEnabled = preferences[Keys.EpgRefreshOnPlaylistChangeEnabled] ?: true,
                ),
                backup = BackupPreferences(
                    lastExportDir = preferences[Keys.LastBackupExportDir],
                ),
                diagnostics = DiagnosticsPreferences(
                    diagnosticsLoggingEnabled = preferences[Keys.DiagnosticsLoggingEnabled]
                        ?: preferences[Keys.LegacyDiagnosticsEnabled]
                        ?: false,
                    keepLastSessionSummary = preferences[Keys.KeepLastSessionSummary] ?: true,
                    lastExportDir = preferences[Keys.LastDiagnosticsExportDir],
                ),
            )
        }

    override suspend fun updateSelectedProviderId(providerId: String?) {
        dataStore.edit { preferences ->
            if (providerId == null) {
                preferences.remove(Keys.SelectedProviderId)
            } else {
                preferences[Keys.SelectedProviderId] = providerId
            }
        }
    }

    override suspend fun updateLocalLogoFolder(path: String?) {
        dataStore.edit { preferences ->
            if (path.isNullOrBlank()) {
                preferences.remove(Keys.LocalLogoFolder)
            } else {
                preferences[Keys.LocalLogoFolder] = path
            }
        }
    }

    override suspend fun updateGeneral(general: GeneralPreferences) {
        dataStore.edit { preferences ->
            preferences[Keys.LaunchOnBoot] = general.launchOnBoot
            preferences[Keys.DoubleBackToExit] = general.doubleBackToExit
            preferences[Keys.BackgroundRefreshEnabled] = general.backgroundRefreshEnabled
            preferences[Keys.ResumeLastChannelOnStart] = general.resumeLastChannelOnStart
            preferences[Keys.GlobalUserAgent] = general.globalUserAgent.trim().ifBlank { DEFAULT_GLOBAL_USER_AGENT }
        }
    }

    override suspend fun updateAppearance(appearance: AppearancePreferences) {
        dataStore.edit { preferences ->
            preferences[Keys.BackgroundColor] = appearance.backgroundColor.name
            preferences[Keys.AccentColor] = appearance.accentColor.name
            preferences[Keys.Transparency] = appearance.transparency.name
            preferences[Keys.FontScale] = appearance.fontScale.name
            preferences[Keys.Language] = appearance.language.name
        }
    }

    override suspend fun updatePlayback(playback: PlaybackPreferences) {
        dataStore.edit { preferences ->
            preferences[Keys.BufferSize] = playback.bufferSize.name
            preferences[Keys.AudioDecoder] = playback.audioDecoder.name
            preferences[Keys.VideoDecoder] = playback.videoDecoder.name
            preferences[Keys.AfrEnabled] = playback.afrEnabled
            preferences[Keys.AudioPassthroughEnabled] = playback.audioPassthroughEnabled
            preferences.setNullable(Keys.PreferredAudioLanguage, playback.preferredAudioLanguage)
            preferences.setNullable(Keys.PreferredSubtitleLanguage, playback.preferredSubtitleLanguage)
            preferences[Keys.ExternalPlayer] = playback.externalPlayer.name
            preferences[Keys.AutoNextEnabled] = playback.autoNextEnabled
            preferences[Keys.AutoNextCountdownSeconds] = playback.autoNextCountdownSeconds.validAutoNextCountdown()
        }
    }

    override suspend fun updateSearchHistory(searchHistory: List<String>) {
        dataStore.edit { preferences ->
            preferences[Keys.SearchHistory] = searchHistory.cleanSearchHistory().joinToString(separator = LIST_SEPARATOR)
        }
    }

    override suspend fun updateExpandedLiveTvProviderIds(providerIds: Set<String>) {
        dataStore.edit { preferences ->
            preferences[Keys.ExpandedLiveTvProviderIds] = providerIds.cleanStoredIds().joinToString(separator = LIST_SEPARATOR)
        }
    }

    override suspend fun updateEpg(epg: EpgPreferences) {
        dataStore.edit { preferences ->
            preferences[Keys.EpgRefreshIntervalHours] = epg.refreshIntervalHours.coerceIn(0, 168)
            preferences[Keys.EpgPastRetentionDays] = epg.pastRetentionDays.coerceIn(1, 14)
            preferences[Keys.EpgRefreshOnAppStartEnabled] = epg.refreshOnAppStartEnabled
            preferences[Keys.EpgRefreshOnPlaylistChangeEnabled] = epg.refreshOnPlaylistChangeEnabled
        }
    }

    override suspend fun updateBackup(backup: BackupPreferences) {
        dataStore.edit { preferences ->
            preferences.setNullable(Keys.LastBackupExportDir, backup.lastExportDir)
        }
    }

    override suspend fun updateDiagnostics(diagnostics: DiagnosticsPreferences) {
        dataStore.edit { preferences ->
            preferences[Keys.DiagnosticsLoggingEnabled] = diagnostics.diagnosticsLoggingEnabled
            preferences[Keys.KeepLastSessionSummary] = diagnostics.keepLastSessionSummary
            preferences.setNullable(Keys.LastDiagnosticsExportDir, diagnostics.lastExportDir)
            preferences.remove(Keys.LegacyDiagnosticsEnabled)
        }
    }

    private fun MutablePreferences.setNullable(key: Preferences.Key<String>, value: String?) {
        if (value == null) {
            remove(key)
        } else {
            this[key] = value
        }
    }

    private fun MutablePreferences.setNullable(key: Preferences.Key<Long>, value: Long?) {
        if (value == null) {
            remove(key)
        } else {
            this[key] = value
        }
    }

    private object Keys {
        val SelectedProviderId = stringPreferencesKey("selected_provider_id")
        val LocalLogoFolder = stringPreferencesKey("local_logo_folder")

        val LaunchOnBoot = booleanPreferencesKey("launch_on_boot")
        val DoubleBackToExit = booleanPreferencesKey("double_back_to_exit")
        val BackgroundRefreshEnabled = booleanPreferencesKey("background_refresh_enabled")
        val ResumeLastChannelOnStart = booleanPreferencesKey("resume_last_channel_on_start")
        val GlobalUserAgent = stringPreferencesKey("global_user_agent")

        val BackgroundColor = stringPreferencesKey("background_color")
        val AccentColor = stringPreferencesKey("accent_color")
        val Transparency = stringPreferencesKey("transparency")
        val FontScale = stringPreferencesKey("font_scale")
        val Language = stringPreferencesKey("language")

        val BufferSize = stringPreferencesKey("buffer_size")
        val AudioDecoder = stringPreferencesKey("audio_decoder")
        val VideoDecoder = stringPreferencesKey("video_decoder")
        val AfrEnabled = booleanPreferencesKey("afr_enabled")
        val AudioPassthroughEnabled = booleanPreferencesKey("audio_passthrough_enabled")
        val PreferredAudioLanguage = stringPreferencesKey("preferred_audio_language")
        val PreferredSubtitleLanguage = stringPreferencesKey("preferred_subtitle_language")
        val ExternalPlayer = stringPreferencesKey("external_player")
        val AutoNextEnabled = booleanPreferencesKey("auto_next_enabled")
        val AutoNextCountdownSeconds = intPreferencesKey("auto_next_countdown_seconds")

        val SearchHistory = stringPreferencesKey("search_history")
        val ExpandedLiveTvProviderIds = stringPreferencesKey("expanded_live_tv_provider_ids")


        val EpgRefreshIntervalHours = intPreferencesKey("epg_refresh_interval_hours")
        val EpgPastRetentionDays = intPreferencesKey("epg_past_retention_days")
        val EpgRefreshOnAppStartEnabled = booleanPreferencesKey("epg_refresh_on_app_start_enabled")
        val EpgRefreshOnPlaylistChangeEnabled = booleanPreferencesKey("epg_refresh_on_playlist_change_enabled")
        val LastBackupExportDir = stringPreferencesKey("last_backup_export_dir")
        val LastDiagnosticsExportDir = stringPreferencesKey("last_diagnostics_export_dir")
        val DiagnosticsLoggingEnabled = booleanPreferencesKey("diagnostics_logging_enabled")
        val LegacyDiagnosticsEnabled = booleanPreferencesKey("diagnostics_enabled")
        val KeepLastSessionSummary = booleanPreferencesKey("keep_last_session_summary")
    }
}

private typealias MutablePreferences = androidx.datastore.preferences.core.MutablePreferences

private inline fun <reified T : Enum<T>> Preferences.enumValue(key: Preferences.Key<String>, default: T): T {
    val rawValue = this[key] ?: return default
    return runCatching { enumValueOf<T>(rawValue) }.getOrDefault(default)
}

private fun String?.toSearchHistory(): List<String> =
    this
        ?.split(LIST_SEPARATOR)
        ?.cleanSearchHistory()
        ?: emptyList()

private fun List<String>.cleanSearchHistory(): List<String> =
    asSequence()
        .map { it.trim().replace(LIST_SEPARATOR, " ") }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .take(MAX_SEARCH_HISTORY)
        .toList()

private fun String?.toStoredIdSet(): Set<String> =
    this
        ?.split(LIST_SEPARATOR)
        ?.cleanStoredIds()
        ?.toSet()
        ?: emptySet()

private fun Iterable<String>.cleanStoredIds(): List<String> =
    asSequence()
        .map { it.trim().replace(LIST_SEPARATOR, " ") }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

private fun Int?.validAutoNextCountdown(): Int =
    when (this) {
        5, 10, 15, 30 -> this
        else -> 10
    }
