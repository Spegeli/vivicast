package com.vivicast.tv.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vivicastPreferencesDataStore by preferencesDataStore(name = "vivicast_preferences")

class DataStoreUserPreferencesStore(
    context: Context,
) : UserPreferencesStore {
    private val dataStore = context.applicationContext.vivicastPreferencesDataStore

    override val values: Flow<UserPreferences> =
        dataStore.data.map { preferences ->
            UserPreferences(
                selectedProviderId = preferences[Keys.SelectedProviderId],
                general = GeneralPreferences(
                    launchOnBoot = preferences[Keys.LaunchOnBoot] ?: false,
                    doubleBackToExit = preferences[Keys.DoubleBackToExit] ?: true,
                    backgroundRefreshEnabled = preferences[Keys.BackgroundRefreshEnabled] ?: true,
                    rememberSorting = preferences[Keys.RememberSorting] ?: true,
                ),
                appearance = AppearancePreferences(
                    backgroundColor = preferences.enumValue(Keys.BackgroundColor, ThemeColor.Dark),
                    accentColor = preferences.enumValue(Keys.AccentColor, AccentColor.Blue),
                    transparency = preferences.enumValue(Keys.Transparency, TransparencyLevel.Percent25),
                    fontScale = preferences.enumValue(Keys.FontScale, FontScalePreference.Medium),
                    language = preferences.enumValue(Keys.Language, LanguagePreference.System),
                    animationSpeed = preferences.enumValue(Keys.AnimationSpeed, AnimationSpeedPreference.Normal),
                ),
                playback = PlaybackPreferences(
                    bufferSize = preferences.enumValue(Keys.BufferSize, BufferSizePreference.Medium),
                    audioDecoder = preferences.enumValue(Keys.AudioDecoder, DecoderPreference.Automatic),
                    videoDecoder = preferences.enumValue(Keys.VideoDecoder, DecoderPreference.Automatic),
                    afrEnabled = preferences[Keys.AfrEnabled] ?: false,
                    timeshiftStorage = preferences.enumValue(
                        Keys.TimeshiftStorage,
                        TimeshiftStoragePreference.Automatic,
                    ),
                    timeshiftMinutes = preferences[Keys.TimeshiftMinutes] ?: 30,
                    preferredAudioLanguage = preferences[Keys.PreferredAudioLanguage],
                    preferredSubtitleLanguage = preferences[Keys.PreferredSubtitleLanguage],
                    externalPlayer = preferences.enumValue(Keys.ExternalPlayer, ExternalPlayerPreference.Internal),
                ),
                history = HistoryPreferences(
                    enabled = preferences[Keys.HistoryEnabled] ?: true,
                    maxRecentChannels = preferences[Keys.MaxRecentChannels] ?: 50,
                    watchedThresholdPercent = preferences[Keys.WatchedThresholdPercent] ?: 95,
                ),
                cache = CachePreferences(
                    maxCacheSizeMb = preferences[Keys.MaxCacheSizeMb] ?: 500,
                ),
                parentalControl = ParentalControlPreferences(
                    pinEnabled = preferences[Keys.PinEnabled] ?: false,
                    protectSettings = preferences[Keys.ProtectSettings] ?: false,
                    protectMovies = preferences[Keys.ProtectMovies] ?: false,
                    protectSeries = preferences[Keys.ProtectSeries] ?: false,
                    protectAdultContent = preferences[Keys.ProtectAdultContent] ?: false,
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

    override suspend fun updateGeneral(general: GeneralPreferences) {
        dataStore.edit { preferences ->
            preferences[Keys.LaunchOnBoot] = general.launchOnBoot
            preferences[Keys.DoubleBackToExit] = general.doubleBackToExit
            preferences[Keys.BackgroundRefreshEnabled] = general.backgroundRefreshEnabled
            preferences[Keys.RememberSorting] = general.rememberSorting
        }
    }

    override suspend fun updateAppearance(appearance: AppearancePreferences) {
        dataStore.edit { preferences ->
            preferences[Keys.BackgroundColor] = appearance.backgroundColor.name
            preferences[Keys.AccentColor] = appearance.accentColor.name
            preferences[Keys.Transparency] = appearance.transparency.name
            preferences[Keys.FontScale] = appearance.fontScale.name
            preferences[Keys.Language] = appearance.language.name
            preferences[Keys.AnimationSpeed] = appearance.animationSpeed.name
        }
    }

    override suspend fun updatePlayback(playback: PlaybackPreferences) {
        dataStore.edit { preferences ->
            preferences[Keys.BufferSize] = playback.bufferSize.name
            preferences[Keys.AudioDecoder] = playback.audioDecoder.name
            preferences[Keys.VideoDecoder] = playback.videoDecoder.name
            preferences[Keys.AfrEnabled] = playback.afrEnabled
            preferences[Keys.TimeshiftStorage] = playback.timeshiftStorage.name
            preferences[Keys.TimeshiftMinutes] = playback.timeshiftMinutes
            preferences.setNullable(Keys.PreferredAudioLanguage, playback.preferredAudioLanguage)
            preferences.setNullable(Keys.PreferredSubtitleLanguage, playback.preferredSubtitleLanguage)
            preferences[Keys.ExternalPlayer] = playback.externalPlayer.name
        }
    }

    override suspend fun updateHistory(history: HistoryPreferences) {
        dataStore.edit { preferences ->
            preferences[Keys.HistoryEnabled] = history.enabled
            preferences[Keys.MaxRecentChannels] = history.maxRecentChannels
            preferences[Keys.WatchedThresholdPercent] = history.watchedThresholdPercent
        }
    }

    override suspend fun updateCache(cache: CachePreferences) {
        dataStore.edit { preferences ->
            preferences[Keys.MaxCacheSizeMb] = cache.maxCacheSizeMb
        }
    }

    override suspend fun updateParentalControl(parentalControl: ParentalControlPreferences) {
        dataStore.edit { preferences ->
            preferences[Keys.PinEnabled] = parentalControl.pinEnabled
            preferences[Keys.ProtectSettings] = parentalControl.protectSettings
            preferences[Keys.ProtectMovies] = parentalControl.protectMovies
            preferences[Keys.ProtectSeries] = parentalControl.protectSeries
            preferences[Keys.ProtectAdultContent] = parentalControl.protectAdultContent
        }
    }

    private fun MutablePreferences.setNullable(key: Preferences.Key<String>, value: String?) {
        if (value == null) {
            remove(key)
        } else {
            this[key] = value
        }
    }

    private object Keys {
        val SelectedProviderId = stringPreferencesKey("selected_provider_id")

        val LaunchOnBoot = booleanPreferencesKey("launch_on_boot")
        val DoubleBackToExit = booleanPreferencesKey("double_back_to_exit")
        val BackgroundRefreshEnabled = booleanPreferencesKey("background_refresh_enabled")
        val RememberSorting = booleanPreferencesKey("remember_sorting")

        val BackgroundColor = stringPreferencesKey("background_color")
        val AccentColor = stringPreferencesKey("accent_color")
        val Transparency = stringPreferencesKey("transparency")
        val FontScale = stringPreferencesKey("font_scale")
        val Language = stringPreferencesKey("language")
        val AnimationSpeed = stringPreferencesKey("animation_speed")

        val BufferSize = stringPreferencesKey("buffer_size")
        val AudioDecoder = stringPreferencesKey("audio_decoder")
        val VideoDecoder = stringPreferencesKey("video_decoder")
        val AfrEnabled = booleanPreferencesKey("afr_enabled")
        val TimeshiftStorage = stringPreferencesKey("timeshift_storage")
        val TimeshiftMinutes = intPreferencesKey("timeshift_minutes")
        val PreferredAudioLanguage = stringPreferencesKey("preferred_audio_language")
        val PreferredSubtitleLanguage = stringPreferencesKey("preferred_subtitle_language")
        val ExternalPlayer = stringPreferencesKey("external_player")

        val HistoryEnabled = booleanPreferencesKey("history_enabled")
        val MaxRecentChannels = intPreferencesKey("max_recent_channels")
        val WatchedThresholdPercent = intPreferencesKey("watched_threshold_percent")

        val MaxCacheSizeMb = intPreferencesKey("max_cache_size_mb")

        val PinEnabled = booleanPreferencesKey("pin_enabled")
        val ProtectSettings = booleanPreferencesKey("protect_settings")
        val ProtectMovies = booleanPreferencesKey("protect_movies")
        val ProtectSeries = booleanPreferencesKey("protect_series")
        val ProtectAdultContent = booleanPreferencesKey("protect_adult_content")
    }
}

private typealias MutablePreferences = androidx.datastore.preferences.core.MutablePreferences

private inline fun <reified T : Enum<T>> Preferences.enumValue(key: Preferences.Key<String>, default: T): T {
    val rawValue = this[key] ?: return default
    return runCatching { enumValueOf<T>(rawValue) }.getOrDefault(default)
}
