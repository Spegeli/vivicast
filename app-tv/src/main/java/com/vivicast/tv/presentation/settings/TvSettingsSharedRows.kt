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
enum class SettingsStatusTone {
    Success,
    Warning,
    Error,
    Muted
}

@Composable
fun SettingsWorkspaceGroup(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ViviCastColors.Surface.copy(alpha = 0.72f))
            .border(1.dp, ViviCastColors.Line, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsGroupHeading(title = title, summary = summary)
        content()
    }
}

@Composable
fun SettingsGroupHeading(
    title: String,
    summary: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                color = ViviCastColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
fun SettingsPanelSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    SettingsWorkspaceGroup(
        title = title,
        summary = "",
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
fun SettingsInlineStatus(
    text: String,
    tone: SettingsStatusTone
) {
    val color = tone.settingsStatusColor()
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.48f), RoundedCornerShape(7.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsEmptyState(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ViviCastColors.SurfaceRaised)
            .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = body,
            color = ViviCastColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun ProviderSettingsItemRow(
    item: ProviderSettingsItemUi,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (item.compact) 76.dp else 90.dp)
            .clip(shape)
            .background(if (focused) ViviCastColors.FocusFill else ViviCastColors.Surface)
            .border(if (focused) 3.dp else 1.dp, if (focused) ViviCastColors.Focus else ViviCastColors.Line, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ViviCastColors.SurfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.name.firstOrNull()?.uppercase() ?: "P",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = if (item.compact) 17.sp else 19.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                OverlayBadge(
                    text = if (item.enabled) "ACTIVE" else "DISABLED",
                    textColor = if (item.enabled) ViviCastColors.Success else ViviCastColors.TextMuted
                )
            }
            Text(
                text = buildString {
                    append(item.channelCount)
                    append(" channel")
                    if (item.channelCount != 1) append("s")
                },
                color = ViviCastColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.liveTvEnabled) OverlayBadge(text = "LIVE TV")
            if (item.vodEnabled) OverlayBadge(text = "VOD")
            OverlayBadge(
                text = if (item.epgLinked) "EPG" else "NO EPG",
                textColor = if (item.epgLinked) Color.White else ViviCastColors.Warning
            )
        }
        SettingsInlineStatusCompact(
            text = item.syncLabel,
            tone = item.syncTone
        )
    }
}

@Composable
fun SettingsInlineStatusCompact(
    text: String,
    tone: SettingsStatusTone
) {
    val color = tone.settingsStatusColor()
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.11f))
            .padding(horizontal = 9.dp, vertical = 6.dp)
    )
}

@Composable
fun EpgSourceItemRow(
    item: EpgSourceItemUi,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(shape)
            .background(
                when {
                    focused -> ViviCastColors.FocusFill
                    selected -> ViviCastColors.Selected
                    else -> ViviCastColors.Surface
                }
            )
            .border(
                width = if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                color = when {
                    focused -> ViviCastColors.Focus
                    selected -> ViviCastColors.Accent
                    else -> ViviCastColors.Line
                },
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ViviCastColors.SurfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.name.firstOrNull()?.uppercase() ?: "E",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                OverlayBadge(text = item.scopeLabel.uppercase())
            }
            Text(
                text = buildString {
                    append(item.assignedProviderCount)
                    append(" assigned")
                    if (item.lastImportLabel.isNotBlank()) {
                        append(" - ")
                        append(item.lastImportLabel)
                    }
                },
                color = ViviCastColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SettingsSourceSummary(item: EpgSourceItemUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ViviCastColors.SurfaceRaised)
            .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = item.sourceLabel,
            color = ViviCastColors.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Last import: ${item.lastImportLabel}",
            color = ViviCastColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SettingsDangerAction(
    text: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (focused) ViviCastColors.Error.copy(alpha = 0.34f) else Color.Transparent)
            .border(
                1.dp,
                if (focused) ViviCastColors.Error else ViviCastColors.Error.copy(alpha = 0.5f),
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = ViviCastColors.Error,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )
    }
}

fun SettingsStatusTone.settingsStatusColor(): Color = when (this) {
    SettingsStatusTone.Success -> ViviCastColors.Success
    SettingsStatusTone.Warning -> ViviCastColors.Warning
    SettingsStatusTone.Error -> ViviCastColors.Error
    SettingsStatusTone.Muted -> ViviCastColors.TextMuted
}

@Preview(
    name = "Providers - populated",
    widthDp = 1450,
    heightDp = 760,
    showBackground = true,
    backgroundColor = 0xFF081014
)
@Composable
fun ProvidersSettingsContentPreview() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().background(ViviCastColors.Background)) {
            ProvidersSettingsContent(
                providers = listOf(
                    ProviderSettingsItemUi(
                        id = "1",
                        name = "Living room IPTV",
                        sourceType = "XTREAM CODES",
                        sourceLabel = "https://tv.example.com/...",
                        channelCount = 1842,
                        enabled = true,
                        liveTvEnabled = true,
                        vodEnabled = true,
                        epgLinked = true,
                        syncLabel = "Synced",
                        syncTone = SettingsStatusTone.Success,
                        compact = false
                    ),
                    ProviderSettingsItemUi(
                        id = "2",
                        name = "A very long backup playlist name for dense TV layouts",
                        sourceType = "M3U URL",
                        sourceLabel = "https://backup.example.com/...",
                        channelCount = 418,
                        enabled = true,
                        liveTvEnabled = true,
                        vodEnabled = false,
                        epgLinked = false,
                        syncLabel = "Error",
                        syncTone = SettingsStatusTone.Error,
                        compact = false
                    )
                ),
                status = "Provider refresh completed",
                refreshingAllProviders = false,
                onOpenM3uUrl = {},
                onOpenXtream = {},
                onRefreshAllProviders = {},
                onEditProvider = {}
            )
        }
    }
}

@Preview(
    name = "Providers - empty",
    widthDp = 1450,
    heightDp = 760,
    showBackground = true,
    backgroundColor = 0xFF081014
)
@Composable
fun ProvidersSettingsEmptyPreview() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().background(ViviCastColors.Background)) {
            ProvidersSettingsContent(
                providers = emptyList(),
                status = null,
                refreshingAllProviders = false,
                onOpenM3uUrl = {},
                onOpenXtream = {},
                onRefreshAllProviders = {},
                onEditProvider = {}
            )
        }
    }
}

@Preview(
    name = "EPG - populated",
    widthDp = 1450,
    heightDp = 760,
    showBackground = true,
    backgroundColor = 0xFF081014
)
@Composable
fun EpgSettingsContentPreview() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().background(ViviCastColors.Background)) {
            EpgSettingsContent(
                sources = listOf(
                    EpgSourceItemUi(
                        id = "1",
                        name = "Main XMLTV",
                        sourceLabel = "https://epg.example.com/...",
                        scopeLabel = "Global",
                        assignedProviderCount = 2,
                        lastImportLabel = "Today, 08:42"
                    ),
                    EpgSourceItemUi(
                        id = "2",
                        name = "Long regional fallback programme source",
                        sourceLabel = "https://regional.example.com/...",
                        scopeLabel = "Provider",
                        assignedProviderCount = 1,
                        lastImportLabel = "Failed"
                    )
                ),
                selectedSourceId = "1",
                assignments = listOf(
                    EpgAssignmentItemUi("p1", "Living room IPTV", true, true),
                    EpgAssignmentItemUi("p2", "Backup playlist", true, false),
                    EpgAssignmentItemUi("p3", "Sports provider", false, false)
                ),
                timeOffsetLabel = "0h",
                refreshOnAppStart = true,
                updateIntervalLabel = "12 hours",
                refreshDuringSession = true,
                retentionLabel = "7 days",
                status = null,
                refreshingAllEpg = false,
                detailOpen = true,
                onSelectSource = {},
                onCloseDetail = {},
                onCycleEpgTimeOffset = {},
                onSetEpgRefreshOnAppStart = {},
                onCycleEpgUpdateInterval = {},
                onSetEpgRefreshDuringSession = {},
                onCycleEpgRetentionDays = {},
                onOpenGlobalEpg = {},
                onRefreshDueEpg = {},
                onRefreshAllEpg = {},
                onAssignSourceToProvider = { _, _ -> },
                onMoveAssignedSourceToFront = {},
                onDeleteEpgSource = {}
            )
        }
    }
}

@Preview(
    name = "EPG - empty",
    widthDp = 1450,
    heightDp = 760,
    showBackground = true,
    backgroundColor = 0xFF081014
)
@Composable
fun EpgSettingsEmptyPreview() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().background(ViviCastColors.Background)) {
            EpgSettingsContent(
                sources = emptyList(),
                selectedSourceId = null,
                assignments = emptyList(),
                timeOffsetLabel = "0h",
                refreshOnAppStart = true,
                updateIntervalLabel = "12 hours",
                refreshDuringSession = true,
                retentionLabel = "7 days",
                status = null,
                refreshingAllEpg = false,
                detailOpen = false,
                onSelectSource = {},
                onCloseDetail = {},
                onCycleEpgTimeOffset = {},
                onSetEpgRefreshOnAppStart = {},
                onCycleEpgUpdateInterval = {},
                onSetEpgRefreshDuringSession = {},
                onCycleEpgRetentionDays = {},
                onOpenGlobalEpg = {},
                onRefreshDueEpg = {},
                onRefreshAllEpg = {},
                onAssignSourceToProvider = { _, _ -> },
                onMoveAssignedSourceToFront = {},
                onDeleteEpgSource = {}
            )
        }
    }
}

