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
import com.vivicast.tv.data.epg.EpgSourcePriorityDirection
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
internal fun EpgGlobalSettings(
    preferences: EpgSettingsState,
    onEpgPreferencesChanged: (EpgSettingsState) -> Unit,
    onRunGlobalRefresh: () -> Unit,
    canRefreshNow: Boolean = true,
    firstFocusModifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        AdjustableSettingsRow(
            title = stringResource(R.string.settings_epg_interval),
            help = stringResource(R.string.settings_epg_help_interval),
            value = stringResource(R.string.common_hours, preferences.refreshIntervalHours),
            onDecrease = {
                onEpgPreferencesChanged(
                    preferences.copy(refreshIntervalHours = (preferences.refreshIntervalHours - 1).coerceAtLeast(1)),
                )
            },
            onIncrease = {
                onEpgPreferencesChanged(
                    preferences.copy(refreshIntervalHours = (preferences.refreshIntervalHours + 1).coerceAtMost(168)),
                )
            },
            modifier = firstFocusModifier,
        )
        AdjustableSettingsRow(
            title = stringResource(R.string.settings_epg_past),
            help = stringResource(R.string.settings_epg_help_past),
            value = if (preferences.pastRetentionDays == 1) stringResource(R.string.common_days_singular, preferences.pastRetentionDays) else stringResource(R.string.common_days_plural, preferences.pastRetentionDays),
            onDecrease = {
                onEpgPreferencesChanged(
                    preferences.copy(pastRetentionDays = (preferences.pastRetentionDays - 1).coerceAtLeast(1)),
                )
            },
            onIncrease = {
                onEpgPreferencesChanged(
                    preferences.copy(pastRetentionDays = (preferences.pastRetentionDays + 1).coerceAtMost(14)),
                )
            },
        )
        AdjustableSettingsRow(
            title = stringResource(R.string.settings_epg_future),
            help = stringResource(R.string.settings_epg_help_cleanup_note),
            value = if (preferences.futureRetentionDays == 1) stringResource(R.string.common_days_singular, preferences.futureRetentionDays) else stringResource(R.string.common_days_plural, preferences.futureRetentionDays),
            onDecrease = {
                onEpgPreferencesChanged(
                    preferences.copy(futureRetentionDays = (preferences.futureRetentionDays - 1).coerceAtLeast(1)),
                )
            },
            onIncrease = {
                onEpgPreferencesChanged(
                    preferences.copy(futureRetentionDays = (preferences.futureRetentionDays + 1).coerceAtMost(14)),
                )
            },
        )
        VivicastSettingsRow(
            title = stringResource(R.string.settings_epg_on_start),
            help = stringResource(R.string.settings_epg_help_background),
            value = if (preferences.refreshOnAppStartEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
            icon = { SettingsRowIcon("calendar_refresh") },
            onClick = {
                onEpgPreferencesChanged(
                    preferences.copy(refreshOnAppStartEnabled = !preferences.refreshOnAppStartEnabled),
                )
            },
        )
        VivicastSettingsRow(
            title = stringResource(R.string.settings_epg_on_change),
            help = stringResource(R.string.settings_epg_help_on_playlist_change),
            value = if (preferences.refreshOnPlaylistChangeEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
            icon = { SettingsRowIcon("list_refresh") },
            onClick = {
                onEpgPreferencesChanged(
                    preferences.copy(refreshOnPlaylistChangeEnabled = !preferences.refreshOnPlaylistChangeEnabled),
                )
            },
        )
        // Nothing to refresh without an EPG source, so hide the action until one exists.
        if (canRefreshNow) {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_epg_now),
                help = stringResource(R.string.settings_epg_help_run_now),
                value = stringResource(R.string.settings_epg_now_value),
                icon = { SettingsRowIcon("refresh") },
                onClick = onRunGlobalRefresh,
            )
        }
    }
}

