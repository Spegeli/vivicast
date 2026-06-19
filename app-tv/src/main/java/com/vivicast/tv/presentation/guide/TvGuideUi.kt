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
fun EpgGuideScreen(
    channels: List<Channel>,
    programs: List<EpgProgram>,
    playlists: List<Playlist>,
    categories: List<ChannelCategory>,
    selectedPlaylistId: String?,
    selectedCategoryId: String?,
    selectedPlaylistName: String,
    selectedCategoryName: String?,
    windowStartMillis: Long,
    windowEndMillis: Long,
    currentTimeMillis: Long,
    selectedProgram: EpgProgram?,
    onSelectPlaylist: (String?) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onShiftWindow: (Int) -> Unit,
    onResetWindow: () -> Unit,
    onFocusProgram: (EpgProgram) -> Unit,
    onPlayChannel: (Channel) -> Unit
) {
    val programsByChannel = remember(programs) { programs.groupBy { it.channelId } }
    val selectedChannel = selectedProgram?.let { program -> channels.firstOrNull { it.id == program.channelId } }
    val detailProgram = selectedProgram ?: programs.firstOrNull()
    val detailChannel = selectedChannel ?: detailProgram?.let { program -> channels.firstOrNull { it.id == program.channelId } }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(190.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ViviCastColors.Surface)
                    .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TV guide",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = selectedPlaylistName,
                        color = ViviCastColors.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = selectedCategoryName ?: "${channels.size} channels visible",
                        color = ViviCastColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            GuideProgramDetails(
                modifier = Modifier.weight(1f),
                program = detailProgram,
                channel = detailChannel
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    FilterChip(
                        text = "All",
                        selected = selectedPlaylistId == null,
                        onClick = { onSelectPlaylist(null) }
                    )
                }
                items(playlists, key = { it.id }) { playlist ->
                    FilterChip(
                        text = playlist.displayName(),
                        selected = playlist.id == selectedPlaylistId,
                        onClick = { onSelectPlaylist(playlist.id) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideControlButton(text = "-3h", onClick = { onShiftWindow(-1) })
                GuideControlButton(text = "Now", onClick = onResetWindow)
                GuideControlButton(text = "+3h", onClick = { onShiftWindow(1) })
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    text = "All groups",
                    selected = selectedCategoryId == null,
                    onClick = { onSelectCategory(null) }
                )
            }
            items(categories, key = { it.id }) { category ->
                FilterChip(
                    text = category.name,
                    selected = category.id == selectedCategoryId,
                    onClick = { onSelectCategory(category.id) }
                )
            }
        }

        GuideTimeHeader(
            windowStartMillis = windowStartMillis,
            windowEndMillis = windowEndMillis,
            currentTimeMillis = currentTimeMillis
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                GuideChannelRow(
                    position = index + 1,
                    channel = channel,
                    programs = programsByChannel[channel.id].orEmpty(),
                    windowStartMillis = windowStartMillis,
                    windowEndMillis = windowEndMillis,
                    currentTimeMillis = currentTimeMillis,
                    onFocusProgram = onFocusProgram,
                    onPlayChannel = { onPlayChannel(channel) }
                )
            }
        }
    }
}

@Composable
fun GuideProgramDetails(
    modifier: Modifier,
    program: EpgProgram?,
    channel: Channel?
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(ViviCastColors.Surface)
            .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ViviCastColors.SurfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channel?.name?.firstOrNull()?.uppercase() ?: "EPG",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = program?.title ?: "No programme selected",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    if (program != null) {
                        append(program.startUtcEpochMillis.asShortTime())
                        append(" - ")
                        append(program.endUtcEpochMillis.asShortTime())
                    }
                    channel?.let {
                        if (isNotBlank()) append("  |  ")
                        append(it.name)
                    }
                }.ifBlank { "Move focus to a programme cell." },
                color = ViviCastColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = program?.description ?: "EPG information appears here when available.",
                color = ViviCastColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GuideTimeHeader(
    windowStartMillis: Long,
    windowEndMillis: Long,
    currentTimeMillis: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = windowStartMillis.asShortDateTime(),
            color = ViviCastColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(190.dp)
        )
        Row(modifier = Modifier.weight(1f)) {
            var tick = windowStartMillis
            while (tick <= windowEndMillis) {
                Text(
                    text = tick.asShortTime(),
                    color = if (tick <= currentTimeMillis && tick + HalfHourMillis > currentTimeMillis) {
                        ViviCastColors.Accent
                    } else {
                        ViviCastColors.TextSecondary
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                tick += HalfHourMillis
            }
        }
    }
}

@Composable
fun GuideControlButton(
    text: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (focused) ViviCastColors.FocusFill else ViviCastColors.Surface)
            .border(1.dp, if (focused) ViviCastColors.Focus else ViviCastColors.Line, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) Color.White else ViviCastColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun GuideChannelRow(
    position: Int,
    channel: Channel,
    programs: List<EpgProgram>,
    windowStartMillis: Long,
    windowEndMillis: Long,
    currentTimeMillis: Long,
    onFocusProgram: (EpgProgram) -> Unit,
    onPlayChannel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .width(190.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(ViviCastColors.Surface)
                .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = position.toString().padStart(2, '0'),
                color = ViviCastColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ViviCastColors.SurfaceRaised),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name.firstOrNull()?.uppercase() ?: "TV",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        GuideProgramCells(
            modifier = Modifier.weight(1f),
            channel = channel,
            programs = programs,
            windowStartMillis = windowStartMillis,
            windowEndMillis = windowEndMillis,
            currentTimeMillis = currentTimeMillis,
            onFocusProgram = onFocusProgram,
            onPlayChannel = onPlayChannel
        )
    }
}

@Composable
fun GuideProgramCells(
    modifier: Modifier,
    channel: Channel,
    programs: List<EpgProgram>,
    windowStartMillis: Long,
    windowEndMillis: Long,
    currentTimeMillis: Long,
    onFocusProgram: (EpgProgram) -> Unit,
    onPlayChannel: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(ViviCastColors.Surface.copy(alpha = 0.75f))
    ) {
        var cursor = windowStartMillis
        val visiblePrograms = programs
            .filter { it.endUtcEpochMillis > windowStartMillis && it.startUtcEpochMillis < windowEndMillis }
            .sortedBy { it.startUtcEpochMillis }

        if (visiblePrograms.isEmpty()) {
            GuideEmptyCell(channel = channel, onPlayChannel = onPlayChannel)
        } else {
            visiblePrograms.forEach { program ->
                val start = program.startUtcEpochMillis.coerceAtLeast(windowStartMillis)
                val end = program.endUtcEpochMillis.coerceAtMost(windowEndMillis)
                if (start > cursor) {
                    GuideGapCell(weight = (start - cursor).toGuideWeight())
                }
                GuideProgramCell(
                    program = program,
                    weight = (end - start).toGuideWeight(),
                    currentTimeMillis = currentTimeMillis,
                    onFocusProgram = onFocusProgram,
                    onPlayChannel = onPlayChannel
                )
                cursor = end
            }
            if (cursor < windowEndMillis) {
                GuideGapCell(weight = (windowEndMillis - cursor).toGuideWeight())
            }
        }
    }
}

@Composable
fun RowScope.GuideEmptyCell(
    channel: Channel,
    onPlayChannel: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) ViviCastColors.FocusFill else ViviCastColors.SurfaceRaised.copy(alpha = 0.45f))
            .border(1.dp, if (focused) ViviCastColors.Focus else ViviCastColors.Line, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onPlayChannel)
            .activateOnCenter(onPlayChannel)
            .focusable()
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "No EPG for ${channel.name}",
            color = ViviCastColors.TextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RowScope.GuideGapCell(weight: Float) {
    Spacer(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .background(ViviCastColors.Surface.copy(alpha = 0.35f))
    )
}

@Composable
fun RowScope.GuideProgramCell(
    program: EpgProgram,
    weight: Float,
    currentTimeMillis: Long,
    onFocusProgram: (EpgProgram) -> Unit,
    onPlayChannel: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val isNow = currentTimeMillis >= program.startUtcEpochMillis && currentTimeMillis < program.endUtcEpochMillis
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(7.dp))
            .background(
                when {
                    focused -> Color.White
                    isNow -> ViviCastColors.Selected
                    else -> ViviCastColors.SurfaceRaised
                }
            )
            .border(
                1.dp,
                when {
                    focused -> ViviCastColors.Focus
                    isNow -> ViviCastColors.Accent.copy(alpha = 0.8f)
                    else -> ViviCastColors.Line
                },
                RoundedCornerShape(7.dp)
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocusProgram(program)
                }
            }
            .clickable(onClick = onPlayChannel)
            .activateOnCenter(onPlayChannel)
            .focusable()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = if (isNow) "NOW  ${program.title}" else program.title,
            color = if (focused) ViviCastColors.Background else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )
    }
}

