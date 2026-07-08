package com.vivicast.tv.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.GlassPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastBorders
import com.vivicast.tv.core.designsystem.VivicastCardSizes
import com.vivicast.tv.core.designsystem.VivicastButtonRow
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastDialog
import com.vivicast.tv.core.designsystem.VivicastDialogActions
import com.vivicast.tv.core.designsystem.VivicastDialogError
import com.vivicast.tv.core.designsystem.VivicastDialogWidth
import com.vivicast.tv.core.designsystem.VivicastTextField
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSettingsRow
import com.vivicast.tv.core.designsystem.VivicastShapes
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTypography
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.data.provider.DEFAULT_REFRESH_INTERVAL_HOURS
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.data.provider.isAutomaticallyRefreshable
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderEpgSource
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.domain.model.EpgSource
import androidx.compose.ui.res.stringResource
import com.vivicast.tv.core.designsystem.R
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun PlaybackSettingsPanel(
    state: PlaybackSettingsState = PlaybackSettingsState(),
    onPlaybackPreferencesChanged: (PlaybackSettingsState) -> Unit = {},
    firstFocusModifier: Modifier = Modifier,
) {
    val cycleBufferSize = {
        onPlaybackPreferencesChanged(state.copy(bufferSize = state.bufferSize.nextBufferSize()))
    }
    val cycleAudioDecoder = {
        onPlaybackPreferencesChanged(state.copy(audioDecoder = state.audioDecoder.nextDecoderMode()))
    }
    val cycleVideoDecoder = {
        onPlaybackPreferencesChanged(state.copy(videoDecoder = state.videoDecoder.nextDecoderMode()))
    }
    val toggleAfr = {
        onPlaybackPreferencesChanged(state.copy(afrEnabled = !state.afrEnabled))
    }
    val cycleAudioLanguage = {
        onPlaybackPreferencesChanged(state.copy(preferredAudioLanguage = state.preferredAudioLanguage.nextAudioLanguage()))
    }
    val cycleSubtitleLanguage = {
        onPlaybackPreferencesChanged(
            state.copy(preferredSubtitleLanguage = state.preferredSubtitleLanguage.nextSubtitleLanguage()),
        )
    }
    val toggleAudioPassthrough = {
        onPlaybackPreferencesChanged(state.copy(audioPassthroughEnabled = !state.audioPassthroughEnabled))
    }
    val cycleExternalPlayer = {
        onPlaybackPreferencesChanged(state.copy(externalPlayer = state.externalPlayer.nextExternalPlayerPreference()))
    }
    val toggleTimeshift = {
        onPlaybackPreferencesChanged(state.copy(timeshiftEnabled = !state.timeshiftEnabled))
    }
    val cycleTimeshiftMinutes = {
        if (state.timeshiftEnabled) {
            onPlaybackPreferencesChanged(state.copy(timeshiftMinutes = state.timeshiftMinutes.nextTimeshiftMinutes()))
        }
    }
    val cycleTimeshiftStorage = {
        if (state.timeshiftEnabled) {
            onPlaybackPreferencesChanged(state.copy(timeshiftStorage = state.timeshiftStorage.nextTimeshiftStorage()))
        }
    }
    val toggleAutoNext = {
        onPlaybackPreferencesChanged(state.copy(autoNextEnabled = !state.autoNextEnabled))
    }
    val cycleCountdown = {
        if (state.autoNextEnabled) {
            onPlaybackPreferencesChanged(
                state.copy(autoNextCountdownSeconds = state.autoNextCountdownSeconds.nextAutoNextCountdown()),
            )
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {


        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_buffer_size),
                help = stringResource(R.string.settings_help_next_stream),
                value = state.bufferSize.label(),
                modifier = firstFocusModifier,
                icon = { SettingsRowIcon("buffer") },
                onClick = cycleBufferSize,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_audio_decoder),
                help = stringResource(R.string.settings_help_next_stream),
                value = state.audioDecoder.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("speaker") },
                onClick = cycleAudioDecoder,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_video_decoder),
                help = stringResource(R.string.settings_help_next_stream),
                value = state.videoDecoder.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("film") },
                onClick = cycleVideoDecoder,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_afr),
                help = stringResource(R.string.settings_help_afr),
                value = if (state.afrEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("display") },
                onClick = toggleAfr,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_external_player),
                help = stringResource(R.string.settings_help_external_player),
                value = state.externalPlayer.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("external") },
                onClick = cycleExternalPlayer,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_timeshift),
                help = stringResource(R.string.settings_help_timeshift),
                value = if (state.timeshiftEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("timeshift") },
                onClick = toggleTimeshift,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_timeshift_max_duration),
                help = if (state.timeshiftEnabled) stringResource(R.string.settings_help_timeshift_buffer) else stringResource(R.string.settings_help_disabled_note),
                value = stringResource(R.string.common_minutes, state.timeshiftMinutes.validTimeshiftMinutes()),
                modifier = if (state.timeshiftEnabled) Modifier else Modifier,
                enabled = state.timeshiftEnabled,
                icon = { SettingsRowIcon("clock") },
                onClick = cycleTimeshiftMinutes,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_timeshift_storage),
                help = if (state.timeshiftEnabled) stringResource(R.string.settings_help_timeshift_storage) else stringResource(R.string.settings_help_disabled_note),
                value = state.timeshiftStorage.label(),
                modifier = if (state.timeshiftEnabled) Modifier else Modifier,
                enabled = state.timeshiftEnabled,
                icon = { SettingsRowIcon("storage") },
                onClick = cycleTimeshiftStorage,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_audio_language),
                help = stringResource(R.string.settings_help_audio_lang),
                value = state.preferredAudioLanguage.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("microphone") },
                onClick = cycleAudioLanguage,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_subtitle_language),
                help = stringResource(R.string.settings_help_subtitle_lang),
                value = state.preferredSubtitleLanguage.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("subtitles") },
                onClick = cycleSubtitleLanguage,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_auto_next),
                help = stringResource(R.string.settings_help_auto_next),
                value = if (state.autoNextEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("skip_forward") },
                onClick = toggleAutoNext,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_auto_next_countdown),
                help = if (state.autoNextEnabled) stringResource(R.string.settings_help_auto_next_countdown) else stringResource(R.string.settings_help_autonext_note),
                value = stringResource(R.string.common_seconds, state.autoNextCountdownSeconds.validAutoNextCountdown()),
                modifier = if (state.autoNextEnabled) Modifier else Modifier,
                enabled = state.autoNextEnabled,
                icon = { SettingsRowIcon("timer") },
                onClick = cycleCountdown,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_audio_passthrough),
                help = stringResource(R.string.settings_help_passthrough),
                value = if (state.audioPassthroughEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("passthrough") },
                onClick = toggleAudioPassthrough,
            )
        }
    }
}

private fun Int.validAutoNextCountdown(): Int =
    when (this) {
        5, 10, 15, 30 -> this
        else -> 10
    }

private fun Int.nextAutoNextCountdown(): Int =
    when (validAutoNextCountdown()) {
        5 -> 10
        10 -> 15
        15 -> 30
        else -> 5
    }

private fun Int.validTimeshiftMinutes(): Int =
    when (this) {
        15, 30, 60, 120 -> this
        else -> 30
    }

private fun Int.nextTimeshiftMinutes(): Int =
    when (validTimeshiftMinutes()) {
        15 -> 30
        30 -> 60
        60 -> 120
        else -> 15
    }

@Composable
private fun PlaybackBufferSizeMode.label(): String = when (this) {
    PlaybackBufferSizeMode.Off -> stringResource(R.string.value_off)
    PlaybackBufferSizeMode.Small -> stringResource(R.string.size_small)
    PlaybackBufferSizeMode.Medium -> stringResource(R.string.size_medium)
    PlaybackBufferSizeMode.Large -> stringResource(R.string.size_large)
    PlaybackBufferSizeMode.ExtraLarge -> stringResource(R.string.size_very_large)
}

private fun PlaybackBufferSizeMode.nextBufferSize(): PlaybackBufferSizeMode =
    when (this) {
        PlaybackBufferSizeMode.Off -> PlaybackBufferSizeMode.Small
        PlaybackBufferSizeMode.Small -> PlaybackBufferSizeMode.Medium
        PlaybackBufferSizeMode.Medium -> PlaybackBufferSizeMode.Large
        PlaybackBufferSizeMode.Large -> PlaybackBufferSizeMode.ExtraLarge
        PlaybackBufferSizeMode.ExtraLarge -> PlaybackBufferSizeMode.Off
    }

@Composable
private fun PlaybackDecoderMode.label(): String = when (this) {
    PlaybackDecoderMode.Hardware -> stringResource(R.string.decoder_hardware)
    PlaybackDecoderMode.Software -> stringResource(R.string.decoder_software)
}

private fun PlaybackDecoderMode.nextDecoderMode(): PlaybackDecoderMode =
    when (this) {
        PlaybackDecoderMode.Hardware -> PlaybackDecoderMode.Software
        PlaybackDecoderMode.Software -> PlaybackDecoderMode.Hardware
    }

@Composable
private fun PlaybackAudioLanguage.label(): String = when (this) {
    PlaybackAudioLanguage.SystemDefault -> stringResource(R.string.language_system)
    PlaybackAudioLanguage.German -> stringResource(R.string.language_german)
    PlaybackAudioLanguage.English -> stringResource(R.string.language_english)
    PlaybackAudioLanguage.Original -> stringResource(R.string.audio_original)
}

private fun PlaybackAudioLanguage.nextAudioLanguage(): PlaybackAudioLanguage =
    when (this) {
        PlaybackAudioLanguage.SystemDefault -> PlaybackAudioLanguage.German
        PlaybackAudioLanguage.German -> PlaybackAudioLanguage.English
        PlaybackAudioLanguage.English -> PlaybackAudioLanguage.Original
        PlaybackAudioLanguage.Original -> PlaybackAudioLanguage.SystemDefault
    }

@Composable
private fun PlaybackSubtitleLanguage.label(): String = when (this) {
    PlaybackSubtitleLanguage.Off -> stringResource(R.string.value_off)
    PlaybackSubtitleLanguage.SystemDefault -> stringResource(R.string.language_system)
    PlaybackSubtitleLanguage.German -> stringResource(R.string.language_german)
    PlaybackSubtitleLanguage.English -> stringResource(R.string.language_english)
}

private fun PlaybackSubtitleLanguage.nextSubtitleLanguage(): PlaybackSubtitleLanguage =
    when (this) {
        PlaybackSubtitleLanguage.Off -> PlaybackSubtitleLanguage.SystemDefault
        PlaybackSubtitleLanguage.SystemDefault -> PlaybackSubtitleLanguage.German
        PlaybackSubtitleLanguage.German -> PlaybackSubtitleLanguage.English
        PlaybackSubtitleLanguage.English -> PlaybackSubtitleLanguage.Off
    }

@Composable
private fun PlaybackExternalPlayerMode.label(): String = when (this) {
    PlaybackExternalPlayerMode.Internal -> stringResource(R.string.player_internal)
    PlaybackExternalPlayerMode.External -> stringResource(R.string.player_external)
    PlaybackExternalPlayerMode.AskEveryTime -> stringResource(R.string.player_ask)
}

private fun PlaybackExternalPlayerMode.nextExternalPlayerPreference(): PlaybackExternalPlayerMode =
    when (this) {
        PlaybackExternalPlayerMode.Internal -> PlaybackExternalPlayerMode.External
        PlaybackExternalPlayerMode.External -> PlaybackExternalPlayerMode.AskEveryTime
        PlaybackExternalPlayerMode.AskEveryTime -> PlaybackExternalPlayerMode.Internal
    }

@Composable
private fun PlaybackTimeshiftStorageMode.label(): String = when (this) {
    PlaybackTimeshiftStorageMode.Automatic -> stringResource(R.string.storage_auto)
    PlaybackTimeshiftStorageMode.Ram -> "RAM"
    PlaybackTimeshiftStorageMode.InternalStorage -> stringResource(R.string.storage_internal)
}

private fun PlaybackTimeshiftStorageMode.nextTimeshiftStorage(): PlaybackTimeshiftStorageMode =
    when (this) {
        PlaybackTimeshiftStorageMode.Automatic -> PlaybackTimeshiftStorageMode.Ram
        PlaybackTimeshiftStorageMode.Ram -> PlaybackTimeshiftStorageMode.InternalStorage
        PlaybackTimeshiftStorageMode.InternalStorage -> PlaybackTimeshiftStorageMode.Automatic
    }

