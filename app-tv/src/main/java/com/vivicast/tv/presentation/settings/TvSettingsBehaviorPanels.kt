package com.vivicast.tv

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.vivicast.core.model.Channel
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.FavoriteChannel
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.RecentChannel
import java.util.Locale
@Composable
fun AppearanceSettingsPanel(
    settings: AppSettings,
    onSetAutoHidePrimaryNavigation: (Boolean) -> Unit,
    onSetAutoCollapseProviderFilters: (Boolean) -> Unit,
    onSetShowChannelNumbers: (Boolean) -> Unit,
    onSetShowChannelMetadata: (Boolean) -> Unit,
    onSetShowSourceLabels: (Boolean) -> Unit,
    onSetCompactChannelRows: (Boolean) -> Unit,
    onSetCompactProviderRows: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsPanelSection(title = "Navigation") {
                SettingsToggleRow(
                    label = "Auto-hide main navigation in Live TV",
                    value = settings.autoHidePrimaryNavigation,
                    description = "Keeps more space for provider, channel, and preview.",
                    onClick = { onSetAutoHidePrimaryNavigation(!settings.autoHidePrimaryNavigation) }
                )
                SettingsToggleRow(
                    label = "Auto-collapse provider filters while browsing",
                    value = settings.autoCollapseProviderFilters,
                    description = "Shows provider and group lists only when Left returns.",
                    onClick = { onSetAutoCollapseProviderFilters(!settings.autoCollapseProviderFilters) }
                )
            }
        }
        item {
            SettingsPanelSection(title = "Channel list") {
                SettingsToggleRow(
                    label = "Show channel numbers",
                    value = settings.showChannelNumbers,
                    description = "Shows channel positions in lists and playback.",
                    onClick = { onSetShowChannelNumbers(!settings.showChannelNumbers) }
                )
                SettingsToggleRow(
                    label = "Show channel metadata row",
                    value = settings.showChannelMetadata,
                    description = "Shows the secondary metadata line in the channel list.",
                    onClick = { onSetShowChannelMetadata(!settings.showChannelMetadata) }
                )
                SettingsToggleRow(
                    label = "Show source labels",
                    value = settings.showSourceLabels,
                    description = "Shows provider labels in lists and playback.",
                    onClick = { onSetShowSourceLabels(!settings.showSourceLabels) }
                )
                SettingsToggleRow(
                    label = "Use compact channel rows",
                    value = settings.compactChannelRows,
                    description = "Fits more channels into the browsing list.",
                    onClick = { onSetCompactChannelRows(!settings.compactChannelRows) }
                )
            }
        }
        item {
            SettingsPanelSection(title = "Settings lists") {
                SettingsToggleRow(
                    label = "Use compact provider rows",
                    value = settings.compactProviderRows,
                    description = "Fits more providers into the settings list.",
                    onClick = { onSetCompactProviderRows(!settings.compactProviderRows) }
                )
            }
        }
    }
}

@Composable
fun PlaybackSettingsPanel(
    settings: AppSettings,
    onSetKeepScreenAwakeDuringPlayback: (Boolean) -> Unit,
    onSetShowPlaybackStatusBadge: (Boolean) -> Unit,
    onSetShowPlaybackClock: (Boolean) -> Unit,
    onSetShowPlaybackRecents: (Boolean) -> Unit,
    onSetShowPlaybackGuideAction: (Boolean) -> Unit,
    onSetShowPlaybackProgressBar: (Boolean) -> Unit,
    onSetShowPlaybackTrackActions: (Boolean) -> Unit,
    onSetShowPlaybackFavoriteAction: (Boolean) -> Unit,
    onSetShowPlaybackProgrammeDescription: (Boolean) -> Unit,
    onSetShowPlaybackNowNextPanel: (Boolean) -> Unit,
    onSetShowFavoriteFeedbackBanner: (Boolean) -> Unit,
    onCycleRecoverablePlaybackRetryDelay: () -> Unit,
    onSetRetryRecoverablePlaybackErrorsOnce: (Boolean) -> Unit,
    onCycleLeaveLiveTvPlaybackBehavior: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsPanelSection(title = "Player behavior") {
                SettingsToggleRow(
                    label = "Keep screen awake during playback",
                    value = settings.keepScreenAwakeDuringPlayback,
                    description = "Keeps the TV awake while video is active.",
                    onClick = { onSetKeepScreenAwakeDuringPlayback(!settings.keepScreenAwakeDuringPlayback) }
                )
                SettingsValueRow(
                    label = "Leave Live TV behavior",
                    value = settings.leaveLiveTvPlaybackBehavior.label,
                    description = "Cycle keep playing, pause, or stop.",
                    onClick = onCycleLeaveLiveTvPlaybackBehavior
                )
                SettingsValueRow(
                    label = "Recoverable retry delay",
                    value = settings.recoverablePlaybackRetryDelay.label,
                    description = "Used before automatic retry after a recoverable error.",
                    onClick = onCycleRecoverablePlaybackRetryDelay
                )
                SettingsToggleRow(
                    label = "Retry recoverable playback errors once",
                    value = settings.retryRecoverablePlaybackErrorsOnce,
                    description = "Retries the current channel once after a recoverable error.",
                    onClick = { onSetRetryRecoverablePlaybackErrorsOnce(!settings.retryRecoverablePlaybackErrorsOnce) }
                )
            }
        }
        item {
            SettingsPanelSection(title = "Overlay content") {
                SettingsToggleRow(
                    label = "Show playback status badge",
                    value = settings.showPlaybackStatusBadge,
                    description = "Shows Playing, Buffering, or Error.",
                    onClick = { onSetShowPlaybackStatusBadge(!settings.showPlaybackStatusBadge) }
                )
                SettingsToggleRow(
                    label = "Show playback clock",
                    value = settings.showPlaybackClock,
                    description = "Shows current time.",
                    onClick = { onSetShowPlaybackClock(!settings.showPlaybackClock) }
                )
                SettingsToggleRow(
                    label = "Show playback progress bar",
                    value = settings.showPlaybackProgressBar,
                    description = "Shows programme progress when timing data is available.",
                    onClick = { onSetShowPlaybackProgressBar(!settings.showPlaybackProgressBar) }
                )
                SettingsToggleRow(
                    label = "Show programme descriptions",
                    value = settings.showPlaybackProgrammeDescription,
                    description = "Shows longer now/next text.",
                    onClick = { onSetShowPlaybackProgrammeDescription(!settings.showPlaybackProgrammeDescription) }
                )
                SettingsToggleRow(
                    label = "Show now/next panel below player",
                    value = settings.showPlaybackNowNextPanel,
                    description = "Shows lower programme panel.",
                    onClick = { onSetShowPlaybackNowNextPanel(!settings.showPlaybackNowNextPanel) }
                )
            }
        }
        item {
            SettingsPanelSection(title = "Quick actions") {
                SettingsToggleRow(
                    label = "Show recent channel shortcuts",
                    value = settings.showPlaybackRecents,
                    description = "Shows recents in the overlay row.",
                    onClick = { onSetShowPlaybackRecents(!settings.showPlaybackRecents) }
                )
                SettingsToggleRow(
                    label = "Show guide shortcut",
                    value = settings.showPlaybackGuideAction,
                    description = "Shows EPG action chip.",
                    onClick = { onSetShowPlaybackGuideAction(!settings.showPlaybackGuideAction) }
                )
                SettingsToggleRow(
                    label = "Show audio and subtitle track actions",
                    value = settings.showPlaybackTrackActions,
                    description = "Only shows track chips when available.",
                    onClick = { onSetShowPlaybackTrackActions(!settings.showPlaybackTrackActions) }
                )
                SettingsToggleRow(
                    label = "Show favorite shortcut and badge",
                    value = settings.showPlaybackFavoriteAction,
                    description = "Shows favorite action and state badge.",
                    onClick = { onSetShowPlaybackFavoriteAction(!settings.showPlaybackFavoriteAction) }
                )
                SettingsToggleRow(
                    label = "Show favorite feedback banner",
                    value = settings.showFavoriteFeedbackBanner,
                    description = "Shows the short banner after favorite changes.",
                    onClick = { onSetShowFavoriteFeedbackBanner(!settings.showFavoriteFeedbackBanner) }
                )
            }
        }
        item {
            SettingsPanelSection(title = "Technical") {
                SettingsValueRow(
                    label = "Render mode",
                    value = "TextureView"
                )
            }
        }
    }
}

@Composable
fun RemoteControlSettingsPanel(
    settings: AppSettings,
    onSetOpenProviderFiltersWhenEnteringLiveTv: (Boolean) -> Unit,
    onSetBackClearsLiveTvFilters: (Boolean) -> Unit,
    onSetBackHidesLiveTvFiltersFirst: (Boolean) -> Unit,
    onSetGuideResetsToNowOnOpen: (Boolean) -> Unit,
    onSetGuideStartsWithCurrentChannel: (Boolean) -> Unit,
    onSetGuideUsesPlayingChannelProviderOnOpen: (Boolean) -> Unit,
    onSetGuideClearsCategoryOnOpen: (Boolean) -> Unit,
    onSetBackClearsProviderBeforeCategory: (Boolean) -> Unit,
    onSetBackClosesNavigationFirst: (Boolean) -> Unit,
    onSetBackClearsGuideWindowFirst: (Boolean) -> Unit,
    onSetBackClearsGuideProgrammeFirst: (Boolean) -> Unit,
    onSetBackLeavesSettingsToLiveTv: (Boolean) -> Unit,
    onSetBackClearsGuideFilters: (Boolean) -> Unit,
    onSetPreferProviderFiltersBeforeNavigation: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsPanelSection(title = "Live TV navigation") {
                SettingsToggleRow(
                    label = "Open provider filters when entering Live TV",
                    value = settings.openProviderFiltersWhenEnteringLiveTv,
                    description = "Otherwise opens directly to channels.",
                    onClick = {
                        onSetOpenProviderFiltersWhenEnteringLiveTv(!settings.openProviderFiltersWhenEnteringLiveTv)
                    }
                )
                SettingsToggleRow(
                    label = "Left opens provider filters first",
                    value = settings.preferProviderFiltersBeforeNavigation,
                    description = "Otherwise Left opens main navigation first.",
                    onClick = {
                        onSetPreferProviderFiltersBeforeNavigation(!settings.preferProviderFiltersBeforeNavigation)
                    }
                )
            }
        }
        item {
            SettingsPanelSection(title = "Back behavior in Live TV") {
                SettingsToggleRow(
                    label = "Back clears active Live TV filters",
                    value = settings.backClearsLiveTvFilters,
                    description = "Clears group, then provider, before leaving Live TV.",
                    onClick = {
                        onSetBackClearsLiveTvFilters(!settings.backClearsLiveTvFilters)
                    }
                )
                SettingsToggleRow(
                    label = "Back hides Live TV filters panel first",
                    value = settings.backHidesLiveTvFiltersFirst,
                    description = "Closes filter column before clearing filters.",
                    onClick = {
                        onSetBackHidesLiveTvFiltersFirst(!settings.backHidesLiveTvFiltersFirst)
                    }
                )
                SettingsToggleRow(
                    label = "Back clears provider before group",
                    value = settings.backClearsProviderBeforeCategory,
                    description = "Applies to Live TV and Guide filter clearing order.",
                    onClick = {
                        onSetBackClearsProviderBeforeCategory(!settings.backClearsProviderBeforeCategory)
                    }
                )
            }
        }
        item {
            SettingsPanelSection(title = "Guide behavior") {
                SettingsToggleRow(
                    label = "Guide resets to current time when opened",
                    value = settings.guideResetsToNowOnOpen,
                    description = "Otherwise the last shifted Guide window stays active.",
                    onClick = {
                        onSetGuideResetsToNowOnOpen(!settings.guideResetsToNowOnOpen)
                    }
                )
                SettingsToggleRow(
                    label = "Guide starts with current channel first",
                    value = settings.guideStartsWithCurrentChannel,
                    description = "Moves the playing channel to the top when visible.",
                    onClick = {
                        onSetGuideStartsWithCurrentChannel(!settings.guideStartsWithCurrentChannel)
                    }
                )
                SettingsToggleRow(
                    label = "Guide uses playing channel provider on open",
                    value = settings.guideUsesPlayingChannelProviderOnOpen,
                    description = "Starts Guide in playing provider when possible.",
                    onClick = {
                        onSetGuideUsesPlayingChannelProviderOnOpen(!settings.guideUsesPlayingChannelProviderOnOpen)
                    }
                )
                SettingsToggleRow(
                    label = "Guide clears group filter on open",
                    value = settings.guideClearsCategoryOnOpen,
                    description = "Drops the current group filter before the Guide opens.",
                    onClick = {
                        onSetGuideClearsCategoryOnOpen(!settings.guideClearsCategoryOnOpen)
                    }
                )
            }
        }
        item {
            SettingsPanelSection(title = "Back behavior in Guide and Settings") {
                SettingsToggleRow(
                    label = "Back resets shifted Guide window first",
                    value = settings.backClearsGuideWindowFirst,
                    description = "Resets Guide to Now before clearing filters or leaving.",
                    onClick = {
                        onSetBackClearsGuideWindowFirst(!settings.backClearsGuideWindowFirst)
                    }
                )
                SettingsToggleRow(
                    label = "Back clears focused guide details first",
                    value = settings.backClearsGuideProgrammeFirst,
                    description = "Closes selected programme details first.",
                    onClick = {
                        onSetBackClearsGuideProgrammeFirst(!settings.backClearsGuideProgrammeFirst)
                    }
                )
                SettingsToggleRow(
                    label = "Back clears guide filters before leaving Guide",
                    value = settings.backClearsGuideFilters,
                    description = "Clears group, then provider, before leaving Guide.",
                    onClick = {
                        onSetBackClearsGuideFilters(!settings.backClearsGuideFilters)
                    }
                )
                SettingsToggleRow(
                    label = "Back leaves Settings to Live TV",
                    value = settings.backLeavesSettingsToLiveTv,
                    description = "Otherwise Back at the Settings root exits the app.",
                    onClick = {
                        onSetBackLeavesSettingsToLiveTv(!settings.backLeavesSettingsToLiveTv)
                    }
                )
                SettingsToggleRow(
                    label = "Back closes navigation before other exits",
                    value = settings.backClosesNavigationFirst,
                    description = "Closes shell before filter or app exits.",
                    onClick = {
                        onSetBackClosesNavigationFirst(!settings.backClosesNavigationFirst)
                    }
                )
            }
        }
        item {
            SettingsPanelSection(title = "Technical") {
                SettingsValueRow(
                    label = "Primary input model",
                    value = "D-pad / OK / Back"
                )
            }
        }
    }
}

