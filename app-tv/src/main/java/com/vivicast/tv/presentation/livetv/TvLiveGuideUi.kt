package com.vivicast.tv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.vivicast.core.domain.EpgNowNext
import com.vivicast.core.model.Channel
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgProgram
import com.vivicast.core.model.Playlist
import kotlinx.coroutines.delay
@Composable
fun BrandNavigationPanel(
    selectedSection: TvSection,
    focusRestoreKey: Int,
    onSelectSection: (TvSection) -> Unit
) {
    Column(
        modifier = Modifier
            .width(170.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BrandHeader()

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NavigationItem(
                    text = "Search",
                    selected = selectedSection == TvSection.Search,
                    focusRestoreKey = focusRestoreKey,
                    onClick = { onSelectSection(TvSection.Search) }
                )
                NavigationItem(
                    text = "Live TV",
                    selected = selectedSection == TvSection.LiveTv,
                    focusRestoreKey = focusRestoreKey,
                    onClick = { onSelectSection(TvSection.LiveTv) }
                )
                NavigationItem(
                    text = "Movies",
                    selected = selectedSection == TvSection.Movies,
                    focusRestoreKey = focusRestoreKey,
                    onClick = { onSelectSection(TvSection.Movies) }
                )
                NavigationItem(
                    text = "Series",
                    selected = selectedSection == TvSection.Series,
                    focusRestoreKey = focusRestoreKey,
                    onClick = { onSelectSection(TvSection.Series) }
                )
                NavigationItem(
                    text = "Settings",
                    selected = selectedSection == TvSection.Settings,
                    focusRestoreKey = focusRestoreKey,
                    onClick = { onSelectSection(TvSection.Settings) }
                )
            }
        }
    }
}

@Composable
fun BrandHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ViviCastColors.Surface)
                .border(2.dp, ViviCastColors.Accent.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "V",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column {
            Row {
                Text("ViVi", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Cast", color = ViviCastColors.Accent, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Text(
                text = "Android TV MVP",
                color = ViviCastColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun NavigationItem(
    text: String,
    selected: Boolean,
    focusRestoreKey: Int,
    onClick: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(selected, focusRestoreKey) {
        if (selected && focusRestoreKey > 0) {
            runCatching { focusRequester.requestFocus() }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> ViviCastColors.FocusFill
                    selected -> ViviCastColors.Selected
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) ViviCastColors.Focus else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = if (selected || focused) Color.White else ViviCastColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun ChannelBrowserPanel(
    section: TvSection,
    navigationVisible: Boolean,
    filtersVisible: Boolean,
    preferProviderFiltersBeforeNavigation: Boolean,
    autoCollapseProviderFilters: Boolean,
    channels: List<Channel>,
    allChannelCount: Int,
    playlists: List<Playlist>,
    categories: List<ChannelCategory>,
    selectedPlaylistId: String?,
    selectedCategoryId: String?,
    selectedPlaylistName: String?,
    selectedCategoryName: String?,
    selectedChannelId: String?,
    previewChannelId: String?,
    showChannelNumbers: Boolean,
    showChannelMetadata: Boolean,
    compactChannelRows: Boolean,
    onFocusChannel: (Channel) -> Unit,
    onSelectPlaylist: (String?) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onShowNavigation: () -> Unit,
    onFiltersVisibleChange: (Boolean) -> Unit,
    onPlayChannel: (Channel) -> Unit
) {
    val firstFilterFocusRequester = remember { FocusRequester() }
    val firstChannelFocusRequester = remember { FocusRequester() }
    var pendingFocusTarget by remember { mutableStateOf<LiveTvFocusTarget?>(null) }
    val panelWidth by animateDpAsState(
        targetValue = when {
            filtersVisible -> 540.dp
            else -> 500.dp
        },
        label = "ChannelBrowserPanelWidth"
    )

    LaunchedEffect(filtersVisible, pendingFocusTarget, channels.size) {
        when (pendingFocusTarget) {
            LiveTvFocusTarget.Filters -> {
                if (filtersVisible) {
                    delay(50)
                    runCatching { firstFilterFocusRequester.requestFocus() }
                    pendingFocusTarget = null
                }
            }

            LiveTvFocusTarget.Channels -> {
                if (!filtersVisible && channels.isNotEmpty()) {
                    delay(50)
                    runCatching { firstChannelFocusRequester.requestFocus() }
                    pendingFocusTarget = null
                }
            }

            null -> Unit
        }
    }

    Row(
        modifier = Modifier
            .width(panelWidth)
            .fillMaxHeight()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        when {
                            !filtersVisible && !preferProviderFiltersBeforeNavigation && !navigationVisible -> {
                                onShowNavigation()
                                true
                            }
                            !filtersVisible -> {
                                pendingFocusTarget = LiveTvFocusTarget.Filters
                                onFiltersVisibleChange(true)
                                true
                            }

                            !navigationVisible -> {
                                onShowNavigation()
                                true
                            }

                            else -> false
                        }
                    }

                    Key.DirectionRight -> {
                        if (filtersVisible) {
                            pendingFocusTarget = LiveTvFocusTarget.Channels
                            onFiltersVisibleChange(false)
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (filtersVisible) {
            LiveTvFilterPanel(
                modifier = Modifier.width(176.dp),
                channels = channels,
                allChannelCount = allChannelCount,
                playlists = playlists,
                categories = categories,
                selectedPlaylistId = selectedPlaylistId,
                selectedCategoryId = selectedCategoryId,
                onSelectPlaylist = onSelectPlaylist,
                onSelectCategory = onSelectCategory,
                firstFocusRequester = firstFilterFocusRequester,
                onMoveToChannels = {
                    pendingFocusTarget = LiveTvFocusTarget.Channels
                    onFiltersVisibleChange(false)
                }
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = when (section) {
                            TvSection.Favorites -> "Favorites"
                            TvSection.Recent -> "Recently watched"
                            else -> "Channels"
                        },
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = selectedCategoryName ?: selectedPlaylistName ?: "All playlists",
                        color = ViviCastColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${channels.size}",
                    color = ViviCastColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (channels.isEmpty()) {
                EmptyChannelBrowserState(
                    section = section,
                    hasProviders = playlists.isNotEmpty(),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                        ChannelRow(
                            position = index + 1,
                            channel = channel,
                            selected = channel.id == selectedChannelId,
                            previewed = channel.id == previewChannelId,
                            showChannelNumbers = showChannelNumbers,
                            showChannelMetadata = showChannelMetadata,
                            compact = compactChannelRows,
                            focusRequester = if (index == 0) firstChannelFocusRequester else null,
                            onFocus = {
                                if (autoCollapseProviderFilters) {
                                    onFiltersVisibleChange(false)
                                }
                                onFocusChannel(channel)
                            },
                            onMoveLeft = {
                                if (preferProviderFiltersBeforeNavigation) {
                                    pendingFocusTarget = LiveTvFocusTarget.Filters
                                    onFiltersVisibleChange(true)
                                } else {
                                    onShowNavigation()
                                }
                                true
                            },
                            onClick = { onPlayChannel(channel) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyChannelBrowserState(
    section: TvSection,
    hasProviders: Boolean,
    modifier: Modifier = Modifier
) {
    val title = when (section) {
        TvSection.Favorites -> "No favorites yet"
        TvSection.Recent -> "No recent channels yet"
        else -> if (hasProviders) "No channels available" else "No provider configured"
    }
    val body = when (section) {
        TvSection.Favorites -> "Mark a channel as favorite in the preview panel to keep it here."
        TvSection.Recent -> "Start playback on any channel. Recently watched entries will appear here."
        else -> if (hasProviders) {
            "Adjust provider or group filters to show channels."
        } else {
            "Open Settings and add an M3U or Xtream provider."
        }
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ViviCastColors.Surface)
            .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            color = ViviCastColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun LiveTvFilterPanel(
    modifier: Modifier,
    channels: List<Channel>,
    allChannelCount: Int,
    playlists: List<Playlist>,
    categories: List<ChannelCategory>,
    selectedPlaylistId: String?,
    selectedCategoryId: String?,
    onSelectPlaylist: (String?) -> Unit,
    onSelectCategory: (String?) -> Unit,
    firstFocusRequester: FocusRequester,
    onMoveToChannels: () -> Unit
) {
    val channelCountByPlaylist = remember(channels, playlists) {
        playlists.associate { playlist -> playlist.id to channels.count { it.playlistId == playlist.id } }
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ViviCastColors.Surface)
                .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "Live TV",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "$allChannelCount channels total",
                color = ViviCastColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Providers",
                color = ViviCastColors.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            FilterListRow(
                text = "All providers",
                subText = "$allChannelCount channels",
                selected = selectedPlaylistId == null,
                focusRequester = firstFocusRequester,
                onMoveRight = onMoveToChannels,
                onClick = { onSelectPlaylist(null) }
            )
            playlists.forEach { playlist ->
                FilterListRow(
                    text = playlist.displayName(),
                    subText = "${channelCountByPlaylist[playlist.id] ?: 0} channels",
                    selected = playlist.id == selectedPlaylistId,
                    onMoveRight = onMoveToChannels,
                    onClick = { onSelectPlaylist(playlist.id) }
                )
            }
        }

        Text(
            text = "Groups",
            color = ViviCastColors.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                FilterListRow(
                    text = "All groups",
                    subText = "${channels.size} visible",
                    selected = selectedCategoryId == null,
                    onMoveRight = onMoveToChannels,
                    onClick = { onSelectCategory(null) }
                )
            }
            items(categories, key = { it.id }) { category ->
                FilterListRow(
                    text = category.name,
                    subText = null,
                    selected = category.id == selectedCategoryId,
                    onMoveRight = onMoveToChannels,
                    onClick = { onSelectCategory(category.id) }
                )
            }
        }
    }
}

@Composable
fun FilterListRow(
    text: String,
    subText: String?,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onMoveRight: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (subText == null) 42.dp else 54.dp)
            .clip(shape)
            .background(
                when {
                    focused -> ViviCastColors.FocusFill
                    selected -> ViviCastColors.Selected
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                color = when {
                    focused -> ViviCastColors.Focus
                    selected -> ViviCastColors.Accent
                    else -> Color.Transparent
                },
                shape = shape
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    event.key == Key.DirectionRight &&
                    onMoveRight != null
                ) {
                    onMoveRight()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            color = if (focused || selected) Color.White else ViviCastColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (focused || selected) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subText != null) {
            Text(
                text = subText,
                color = if (focused || selected) ViviCastColors.TextSecondary else ViviCastColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

