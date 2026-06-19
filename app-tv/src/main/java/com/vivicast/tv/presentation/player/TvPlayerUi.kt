package com.vivicast.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.vivicast.core.model.Channel
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.RecentChannel
import com.vivicast.core.model.StreamTrack
@Composable
fun WatchPanel(
    controller: ViviCastTvController,
    playbackUiState: PlaybackUiState,
    onToggleFavorite: () -> Unit,
    onOpenGuide: () -> Unit,
    onPlayRecentChannel: (Channel) -> Unit,
    onSelectTrack: (StreamTrack) -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onStopPlayback: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            if (playbackUiState.playingChannel == null) {
                EmptyPlayerState()
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        (LayoutInflater.from(viewContext)
                            .inflate(R.layout.vivicast_player_view, null, false) as PlayerView
                            ).apply {
                            player = controller.media3Player
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { playerView ->
                        playerView.player = controller.media3Player
                        playerView.setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                )

                PlaybackHeroOverlay(
                    playbackUiState = playbackUiState,
                    onToggleFavorite = onToggleFavorite,
                    onOpenGuide = onOpenGuide,
                    onPlayRecentChannel = onPlayRecentChannel,
                    onSelectTrack = onSelectTrack,
                    onPausePlayback = onPausePlayback,
                    onResumePlayback = onResumePlayback,
                    onStopPlayback = onStopPlayback
                )
            }
        }

        if (playbackUiState.showNowNextPanel) {
            NowNextPanel(
                selectedChannel = playbackUiState.displayChannel,
                nowNext = playbackUiState.nowNext,
                currentTimeMillis = playbackUiState.currentTimeMillis,
                sourceName = playbackUiState.sourceName,
                showSourceLabels = playbackUiState.showSourceLabels,
                showProgrammeDescription = playbackUiState.showProgrammeDescription
            )
        }
    }
}

@Composable
fun PlaybackHeroOverlay(
    playbackUiState: PlaybackUiState,
    onToggleFavorite: () -> Unit,
    onOpenGuide: () -> Unit,
    onPlayRecentChannel: (Channel) -> Unit,
    onSelectTrack: (StreamTrack) -> Unit,
    onPausePlayback: () -> Unit,
    onResumePlayback: () -> Unit,
    onStopPlayback: () -> Unit
) {
    val progress = playbackUiState.nowNext.now?.progressAt(playbackUiState.currentTimeMillis) ?: 0f
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (playbackUiState.showFavoriteFeedbackBanner && playbackUiState.favoriteFeedback != null) {
                    StatusBanner(
                        text = playbackUiState.favoriteFeedback,
                        positive = true,
                        compact = true
                    )
                }

                if (playbackUiState.showStatusBadge) {
                    OverlayBadge(
                        text = playbackUiState.status.asText(),
                        backgroundColor = ViviCastColors.Background.copy(alpha = 0.72f),
                        textColor = playbackUiState.status.statusColor()
                    )
                }
            }

            if (playbackUiState.showClock) {
                Text(
                    text = playbackUiState.currentTimeMillis.asOverlayClock(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ViviCastColors.Background.copy(alpha = 0.7f))
                        .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ViviCastColors.Background.copy(alpha = 0.76f))
                    .border(1.dp, ViviCastColors.Line, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (playbackUiState.showChannelNumbers) {
                        playbackUiState.channelPosition?.let { position ->
                            Text(
                                text = position.toString().padStart(2, '0'),
                                color = ViviCastColors.Accent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Text(
                        text = playbackUiState.displayChannel?.name ?: "Live TV",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (playbackUiState.showFavoriteAction && playbackUiState.isFavorite) {
                        OverlayBadge(text = "FAVORITE")
                    }
                    if (playbackUiState.showSourceLabels && !playbackUiState.sourceName.isNullOrBlank()) {
                        OverlayBadge(text = playbackUiState.sourceName)
                    }
                }

                Text(
                    text = playbackUiState.nowNext.now?.title ?: "Live stream",
                    color = ViviCastColors.Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(playbackUiState.nowNext.now?.timeRangeLabel() ?: "EPG data pending")
                        playbackUiState.nowNext.next?.title?.takeIf { it.isNotBlank() }?.let { nextTitle ->
                            append("  |  Next: ")
                            append(nextTitle)
                        }
                    },
                    color = ViviCastColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playbackUiState.showProgressBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ViviCastColors.Line)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(ViviCastColors.Accent)
                        )
                    }
                }

                if (playbackUiState.showProgrammeDescription) {
                    Text(
                        text = playbackUiState.nowNext.now?.description
                            ?: playbackUiState.nowNext.next?.description
                            ?: "EPG details appear here when programme data is available.",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                OverlayActionRow(
                    recentChannels = if (playbackUiState.showRecentChannels) playbackUiState.recentChannels else emptyList(),
                    showGuideAction = playbackUiState.showGuideAction,
                    audioTracks = playbackUiState.audioTracks,
                    subtitleTracks = playbackUiState.subtitleTracks,
                    onOpenGuide = onOpenGuide,
                    onPlayRecentChannel = onPlayRecentChannel,
                    onSelectTrack = onSelectTrack,
                    playbackStatus = playbackUiState.status,
                    onPausePlayback = onPausePlayback,
                    onResumePlayback = onResumePlayback,
                    onStopPlayback = onStopPlayback,
                    modifier = Modifier.weight(1f)
                )

                if (playbackUiState.showFavoriteAction && playbackUiState.displayChannel != null) {
                    FavoriteActionChip(
                        selected = playbackUiState.isFavorite,
                        onClick = onToggleFavorite
                    )
                }
            }
        }
    }
}

