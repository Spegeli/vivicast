package com.vivicast.tv.feature.settings

import com.vivicast.tv.core.datastore.AccentColor
import com.vivicast.tv.core.datastore.BufferSizePreference
import com.vivicast.tv.core.datastore.DiagnosticsPreferences
import com.vivicast.tv.core.datastore.DecoderPreference
import com.vivicast.tv.core.datastore.ExternalPlayerPreference
import com.vivicast.tv.core.datastore.FontScalePreference
import com.vivicast.tv.core.datastore.LanguagePreference
import com.vivicast.tv.core.datastore.ThemeColor
import com.vivicast.tv.core.datastore.TransparencyLevel

/**
 * Pure DataStore <-> settings-UI mappers for the General, Appearance and Playback sections
 * (migrated from app/SettingsPreferenceMappers.kt in P1-04f1). No Context/Android dependency.
 * Context-based mappers (aboutAppState, diagnostics, support info), the Locale key and backup
 * mappers remain in the app module.
 */

internal fun DiagnosticsPreferences.toSettingsDiagnosticsState(): DiagnosticsSettingsState =
    DiagnosticsSettingsState(
        diagnosticsLoggingEnabled = diagnosticsLoggingEnabled,
    )

internal fun LanguagePreference.toSettingsLanguage(): SettingsLanguage =
    when (this) {
        LanguagePreference.System -> SettingsLanguage.System
        LanguagePreference.German -> SettingsLanguage.German
        LanguagePreference.English -> SettingsLanguage.English
    }

internal fun SettingsLanguage.toDataStoreLanguagePreference(): LanguagePreference =
    when (this) {
        SettingsLanguage.System -> LanguagePreference.System
        SettingsLanguage.German -> LanguagePreference.German
        SettingsLanguage.English -> LanguagePreference.English
    }

// Background/accent/transparency UI <-> datastore enums share identical case names (Red..Black,
// Percent0..Percent100), so map by name — no per-case when to keep in sync as the palette grows.
internal fun ThemeColor.toSettingsThemeMode(): SettingsThemeMode = SettingsThemeMode.valueOf(name)

internal fun SettingsThemeMode.toDataStoreThemeColor(): ThemeColor = ThemeColor.valueOf(name)

internal fun AccentColor.toSettingsAccentColor(): SettingsAccentColor = SettingsAccentColor.valueOf(name)

internal fun SettingsAccentColor.toDataStoreAccentColor(): AccentColor = AccentColor.valueOf(name)

internal fun TransparencyLevel.toSettingsTransparency(): SettingsTransparency =
    SettingsTransparency.valueOf(name)

internal fun SettingsTransparency.toDataStoreTransparencyLevel(): TransparencyLevel =
    TransparencyLevel.valueOf(name)

internal fun FontScalePreference.toSettingsFontScale(): SettingsFontScale =
    when (this) {
        FontScalePreference.Small -> SettingsFontScale.Small
        FontScalePreference.Medium -> SettingsFontScale.Medium
        FontScalePreference.Large -> SettingsFontScale.Large
    }

internal fun SettingsFontScale.toDataStoreFontScalePreference(): FontScalePreference =
    when (this) {
        SettingsFontScale.Small -> FontScalePreference.Small
        SettingsFontScale.Medium -> FontScalePreference.Medium
        SettingsFontScale.Large -> FontScalePreference.Large
    }

internal fun BufferSizePreference.toSettingsBufferSizeMode(): PlaybackBufferSizeMode =
    when (this) {
        BufferSizePreference.Off -> PlaybackBufferSizeMode.Off
        BufferSizePreference.Small -> PlaybackBufferSizeMode.Small
        BufferSizePreference.Medium -> PlaybackBufferSizeMode.Medium
        BufferSizePreference.Large -> PlaybackBufferSizeMode.Large
    }

internal fun PlaybackBufferSizeMode.toDataStoreBufferSizePreference(): BufferSizePreference =
    when (this) {
        PlaybackBufferSizeMode.Off -> BufferSizePreference.Off
        PlaybackBufferSizeMode.Small -> BufferSizePreference.Small
        PlaybackBufferSizeMode.Medium -> BufferSizePreference.Medium
        PlaybackBufferSizeMode.Large -> BufferSizePreference.Large
    }

internal fun DecoderPreference.toSettingsDecoderMode(): PlaybackDecoderMode =
    when (this) {
        DecoderPreference.Hardware -> PlaybackDecoderMode.Hardware
        DecoderPreference.Software -> PlaybackDecoderMode.Software
    }

internal fun PlaybackDecoderMode.toDataStoreDecoderPreference(): DecoderPreference =
    when (this) {
        PlaybackDecoderMode.Hardware -> DecoderPreference.Hardware
        PlaybackDecoderMode.Software -> DecoderPreference.Software
    }

internal fun String?.toSettingsAudioLanguage(): PlaybackAudioLanguage =
    when (this) {
        "de" -> PlaybackAudioLanguage.German
        "en" -> PlaybackAudioLanguage.English
        "original" -> PlaybackAudioLanguage.Original
        else -> PlaybackAudioLanguage.SystemDefault
    }

internal fun PlaybackAudioLanguage.toDataStoreAudioLanguage(): String? =
    when (this) {
        PlaybackAudioLanguage.SystemDefault -> null
        PlaybackAudioLanguage.German -> "de"
        PlaybackAudioLanguage.English -> "en"
        PlaybackAudioLanguage.Original -> "original"
    }

internal fun String?.toSettingsSubtitleLanguage(): PlaybackSubtitleLanguage =
    when (this) {
        "system" -> PlaybackSubtitleLanguage.SystemDefault
        "de" -> PlaybackSubtitleLanguage.German
        "en" -> PlaybackSubtitleLanguage.English
        else -> PlaybackSubtitleLanguage.Off
    }

internal fun PlaybackSubtitleLanguage.toDataStoreSubtitleLanguage(): String? =
    when (this) {
        PlaybackSubtitleLanguage.Off -> null
        PlaybackSubtitleLanguage.SystemDefault -> "system"
        PlaybackSubtitleLanguage.German -> "de"
        PlaybackSubtitleLanguage.English -> "en"
    }

internal fun ExternalPlayerPreference.toSettingsExternalPlayerMode(): PlaybackExternalPlayerMode =
    when (this) {
        ExternalPlayerPreference.Internal -> PlaybackExternalPlayerMode.Internal
        ExternalPlayerPreference.External -> PlaybackExternalPlayerMode.External
        ExternalPlayerPreference.AskEveryTime -> PlaybackExternalPlayerMode.AskEveryTime
    }

internal fun PlaybackExternalPlayerMode.toDataStoreExternalPlayerPreference(): ExternalPlayerPreference =
    when (this) {
        PlaybackExternalPlayerMode.Internal -> ExternalPlayerPreference.Internal
        PlaybackExternalPlayerMode.External -> ExternalPlayerPreference.External
        PlaybackExternalPlayerMode.AskEveryTime -> ExternalPlayerPreference.AskEveryTime
    }
