package com.vivicast.tv.feature.settings

import android.os.Build
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

private enum class PlaybackPicker {
    Buffer, AudioDecoder, VideoDecoder, ExternalPlayer, TimeshiftMinutes, TimeshiftStorage,
    AudioLanguage, SubtitleLanguage, Countdown,
}

@Composable
fun PlaybackSettingsPanel(
    state: PlaybackSettingsState = PlaybackSettingsState(),
    onPlaybackPreferencesChanged: (PlaybackSettingsState) -> Unit = {},
    firstFocusModifier: Modifier = Modifier,
) {
    var openPicker by remember { mutableStateOf<PlaybackPicker?>(null) }
    // AFR needs the seamless setFrameRate overload (API 31+); older systems can't switch → row disabled.
    val afrSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_buffer_size),
                help = stringResource(R.string.settings_help_next_stream),
                value = state.bufferSize.label(),
                modifier = firstFocusModifier,
                // "Off" is a real buffer value; force a text+chevron row so it isn't mistaken for a toggle.
                forceTextValue = true,
                icon = { SettingsRowIcon("buffer") },
                onClick = { openPicker = PlaybackPicker.Buffer },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_audio_decoder),
                help = stringResource(R.string.settings_help_next_stream),
                value = state.audioDecoder.label(),
                icon = { SettingsRowIcon("speaker") },
                onClick = { openPicker = PlaybackPicker.AudioDecoder },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_video_decoder),
                help = stringResource(R.string.settings_help_next_stream),
                value = state.videoDecoder.label(),
                icon = { SettingsRowIcon("film") },
                onClick = { openPicker = PlaybackPicker.VideoDecoder },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_afr),
                help = if (afrSupported) stringResource(R.string.settings_help_afr) else stringResource(R.string.settings_help_afr_unsupported),
                value = if (state.afrEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                enabled = afrSupported,
                icon = { SettingsRowIcon("display") },
                onClick = { onPlaybackPreferencesChanged(state.copy(afrEnabled = !state.afrEnabled)) },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_external_player),
                help = stringResource(R.string.settings_help_external_player),
                value = state.externalPlayer.label(),
                icon = { SettingsRowIcon("external") },
                onClick = { openPicker = PlaybackPicker.ExternalPlayer },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_timeshift),
                help = stringResource(R.string.settings_help_timeshift),
                value = if (state.timeshiftEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                icon = { SettingsRowIcon("timeshift") },
                onClick = { onPlaybackPreferencesChanged(state.copy(timeshiftEnabled = !state.timeshiftEnabled)) },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_timeshift_max_duration),
                help = if (state.timeshiftEnabled) stringResource(R.string.settings_help_timeshift_buffer) else stringResource(R.string.settings_help_disabled_note),
                value = stringResource(R.string.common_minutes, state.timeshiftMinutes.validTimeshiftMinutes()),
                enabled = state.timeshiftEnabled,
                icon = { SettingsRowIcon("clock") },
                onClick = { openPicker = PlaybackPicker.TimeshiftMinutes },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_timeshift_storage),
                help = if (state.timeshiftEnabled) stringResource(R.string.settings_help_timeshift_storage) else stringResource(R.string.settings_help_disabled_note),
                value = state.timeshiftStorage.label(),
                enabled = state.timeshiftEnabled,
                icon = { SettingsRowIcon("storage") },
                onClick = { openPicker = PlaybackPicker.TimeshiftStorage },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_audio_language),
                help = stringResource(R.string.settings_help_audio_lang),
                value = state.preferredAudioLanguage.label(),
                icon = { SettingsRowIcon("microphone") },
                onClick = { openPicker = PlaybackPicker.AudioLanguage },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_subtitle_language),
                help = stringResource(R.string.settings_help_subtitle_lang),
                value = state.preferredSubtitleLanguage.label(),
                // "Off" is a real subtitle value; force text+chevron so it isn't mistaken for a toggle.
                forceTextValue = true,
                icon = { SettingsRowIcon("subtitles") },
                onClick = { openPicker = PlaybackPicker.SubtitleLanguage },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_auto_next),
                help = stringResource(R.string.settings_help_auto_next),
                value = if (state.autoNextEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                icon = { SettingsRowIcon("skip_forward") },
                onClick = { onPlaybackPreferencesChanged(state.copy(autoNextEnabled = !state.autoNextEnabled)) },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_auto_next_countdown),
                help = if (state.autoNextEnabled) stringResource(R.string.settings_help_auto_next_countdown) else stringResource(R.string.settings_help_autonext_note),
                value = stringResource(R.string.common_seconds, state.autoNextCountdownSeconds.validAutoNextCountdown()),
                enabled = state.autoNextEnabled,
                icon = { SettingsRowIcon("timer") },
                onClick = { openPicker = PlaybackPicker.Countdown },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_audio_passthrough),
                help = stringResource(R.string.settings_help_passthrough),
                value = if (state.audioPassthroughEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                icon = { SettingsRowIcon("passthrough") },
                onClick = { onPlaybackPreferencesChanged(state.copy(audioPassthroughEnabled = !state.audioPassthroughEnabled)) },
            )
        }
    }

    PlaybackSettingsDialogs(openPicker, state, onPlaybackPreferencesChanged) { openPicker = null }
}

@Composable
private fun PlaybackSettingsDialogs(
    openPicker: PlaybackPicker?,
    state: PlaybackSettingsState,
    onChange: (PlaybackSettingsState) -> Unit,
    onDismiss: () -> Unit,
) {
    when (openPicker) {
        PlaybackPicker.Buffer -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_buffer_size),
            options = PlaybackBufferSizeMode.entries,
            selected = state.bufferSize,
            label = { it.label() },
            onSelect = { onChange(state.copy(bufferSize = it)); onDismiss() },
            onDismiss = onDismiss,
        )
        PlaybackPicker.AudioDecoder -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_audio_decoder),
            options = PlaybackDecoderMode.entries,
            selected = state.audioDecoder,
            label = { it.label() },
            onSelect = { onChange(state.copy(audioDecoder = it)); onDismiss() },
            onDismiss = onDismiss,
        )
        PlaybackPicker.VideoDecoder -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_video_decoder),
            options = PlaybackDecoderMode.entries,
            selected = state.videoDecoder,
            label = { it.label() },
            onSelect = { onChange(state.copy(videoDecoder = it)); onDismiss() },
            onDismiss = onDismiss,
        )
        PlaybackPicker.ExternalPlayer -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_external_player),
            options = PlaybackExternalPlayerMode.entries,
            selected = state.externalPlayer,
            label = { it.label() },
            onSelect = { onChange(state.copy(externalPlayer = it)); onDismiss() },
            onDismiss = onDismiss,
        )
        PlaybackPicker.TimeshiftMinutes -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_timeshift_max_duration),
            options = listOf(15, 30, 60, 120),
            selected = state.timeshiftMinutes.validTimeshiftMinutes(),
            label = { stringResource(R.string.common_minutes, it) },
            onSelect = { onChange(state.copy(timeshiftMinutes = it)); onDismiss() },
            onDismiss = onDismiss,
        )
        PlaybackPicker.TimeshiftStorage -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_timeshift_storage),
            options = PlaybackTimeshiftStorageMode.entries,
            selected = state.timeshiftStorage,
            label = { it.label() },
            onSelect = { onChange(state.copy(timeshiftStorage = it)); onDismiss() },
            onDismiss = onDismiss,
        )
        PlaybackPicker.AudioLanguage -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_audio_language),
            options = PlaybackAudioLanguage.entries,
            selected = state.preferredAudioLanguage,
            label = { it.label() },
            onSelect = { onChange(state.copy(preferredAudioLanguage = it)); onDismiss() },
            onDismiss = onDismiss,
        )
        PlaybackPicker.SubtitleLanguage -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_subtitle_language),
            options = PlaybackSubtitleLanguage.entries,
            selected = state.preferredSubtitleLanguage,
            label = { it.label() },
            onSelect = { onChange(state.copy(preferredSubtitleLanguage = it)); onDismiss() },
            onDismiss = onDismiss,
        )
        PlaybackPicker.Countdown -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_auto_next_countdown),
            options = listOf(5, 10, 15, 30),
            selected = state.autoNextCountdownSeconds.validAutoNextCountdown(),
            label = { stringResource(R.string.common_seconds, it) },
            onSelect = { onChange(state.copy(autoNextCountdownSeconds = it)); onDismiss() },
            onDismiss = onDismiss,
        )
        null -> Unit
    }
}

private fun Int.validAutoNextCountdown(): Int =
    when (this) {
        5, 10, 15, 30 -> this
        else -> 10
    }

private fun Int.validTimeshiftMinutes(): Int =
    when (this) {
        15, 30, 60, 120 -> this
        else -> 30
    }

@Composable
private fun PlaybackBufferSizeMode.label(): String = when (this) {
    PlaybackBufferSizeMode.Off -> stringResource(R.string.value_off)
    PlaybackBufferSizeMode.Small -> stringResource(R.string.size_small)
    PlaybackBufferSizeMode.Medium -> stringResource(R.string.size_medium)
    PlaybackBufferSizeMode.Large -> stringResource(R.string.size_large)
    PlaybackBufferSizeMode.ExtraLarge -> stringResource(R.string.size_very_large)
}

@Composable
private fun PlaybackDecoderMode.label(): String = when (this) {
    PlaybackDecoderMode.Hardware -> stringResource(R.string.decoder_hardware)
    PlaybackDecoderMode.Software -> stringResource(R.string.decoder_software)
}

@Composable
private fun PlaybackAudioLanguage.label(): String = when (this) {
    PlaybackAudioLanguage.SystemDefault -> stringResource(R.string.language_system)
    PlaybackAudioLanguage.German -> stringResource(R.string.language_german)
    PlaybackAudioLanguage.English -> stringResource(R.string.language_english)
    PlaybackAudioLanguage.Original -> stringResource(R.string.audio_original)
}

@Composable
private fun PlaybackSubtitleLanguage.label(): String = when (this) {
    PlaybackSubtitleLanguage.Off -> stringResource(R.string.value_off)
    PlaybackSubtitleLanguage.SystemDefault -> stringResource(R.string.language_system)
    PlaybackSubtitleLanguage.German -> stringResource(R.string.language_german)
    PlaybackSubtitleLanguage.English -> stringResource(R.string.language_english)
}

@Composable
private fun PlaybackExternalPlayerMode.label(): String = when (this) {
    PlaybackExternalPlayerMode.Internal -> stringResource(R.string.player_internal)
    PlaybackExternalPlayerMode.External -> stringResource(R.string.player_external)
    PlaybackExternalPlayerMode.AskEveryTime -> stringResource(R.string.player_ask)
}

@Composable
private fun PlaybackTimeshiftStorageMode.label(): String = when (this) {
    PlaybackTimeshiftStorageMode.Automatic -> stringResource(R.string.storage_auto)
    PlaybackTimeshiftStorageMode.Ram -> "RAM"
    PlaybackTimeshiftStorageMode.InternalStorage -> stringResource(R.string.storage_internal)
}

