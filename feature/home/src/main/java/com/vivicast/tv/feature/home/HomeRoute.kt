package com.vivicast.tv.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.HeroPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.PosterCard
import com.vivicast.tv.core.designsystem.VivicastChannelCard
import com.vivicast.tv.core.designsystem.VivicastContentRow
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Movie

@Composable
fun HomeRoute(
    playbackRepository: PlaybackRepository? = null,
    mediaRepository: MediaRepository? = null,
    resolveChannelLogoModel: suspend (Channel) -> Any? = { null },
    resolveMoviePosterModel: suspend (Movie) -> Any? = { null },
    resolveEpisodeImageModel: suspend (Episode) -> Any? = { null },
    onOpenMovie: (Movie) -> Unit = {},
    onOpenEpisode: (Episode) -> Unit = {},
    onOpenChannel: (Channel) -> Unit = {},
    onAddPlaylist: () -> Unit = {},
) {
    if (playbackRepository == null || mediaRepository == null) {
        HomeContent(
            continueItems = emptyList(),
            recentChannels = emptyList(),
            resolveChannelLogoModel = resolveChannelLogoModel,
            resolveMoviePosterModel = resolveMoviePosterModel,
            resolveEpisodeImageModel = resolveEpisodeImageModel,
            onOpenMovie = onOpenMovie,
            onOpenEpisode = onOpenEpisode,
            onOpenChannel = onOpenChannel,
            onAddPlaylist = onAddPlaylist,
        )
        return
    }

    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(playbackRepository, mediaRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        continueItems = uiState.continueItems,
        recentChannels = uiState.recentChannels,
        resolveChannelLogoModel = resolveChannelLogoModel,
        resolveMoviePosterModel = resolveMoviePosterModel,
        resolveEpisodeImageModel = resolveEpisodeImageModel,
        onOpenMovie = onOpenMovie,
        onOpenEpisode = onOpenEpisode,
        onOpenChannel = onOpenChannel,
        onAddPlaylist = onAddPlaylist,
    )
}

@Composable
private fun HomeContent(
    continueItems: List<ContinueHomeItem>,
    recentChannels: List<RecentChannelHomeItem>,
    resolveChannelLogoModel: suspend (Channel) -> Any?,
    resolveMoviePosterModel: suspend (Movie) -> Any?,
    resolveEpisodeImageModel: suspend (Episode) -> Any?,
    onOpenMovie: (Movie) -> Unit,
    onOpenEpisode: (Episode) -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onAddPlaylist: () -> Unit,
) {
    var selectedContinueId by remember { mutableStateOf<String?>(null) }
    var selectedChannelId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(continueItems) {
        if (selectedContinueId == null || continueItems.none { it.id == selectedContinueId }) {
            selectedContinueId = continueItems.firstOrNull()?.id
        }
    }
    LaunchedEffect(recentChannels) {
        if (selectedChannelId == null || recentChannels.none { it.channel.id == selectedChannelId }) {
            selectedChannelId = recentChannels.firstOrNull()?.channel?.id
        }
    }

    val heroContinue = continueItems.firstOrNull { it.id == selectedContinueId } ?: continueItems.firstOrNull()
    val heroChannel = if (heroContinue == null) {
        recentChannels.firstOrNull { it.channel.id == selectedChannelId } ?: recentChannels.firstOrNull()
    } else {
        null
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
            modifier = Modifier.fillMaxSize(),
        ) {
            HomeHero(
                continueItem = heroContinue,
                recentChannel = heroChannel,
                onOpenMovie = onOpenMovie,
                onOpenEpisode = onOpenEpisode,
                onOpenChannel = onOpenChannel,
                onAddPlaylist = onAddPlaylist,
            )

            if (continueItems.isNotEmpty()) {
                VivicastContentRow(title = stringResource(R.string.home_continue)) {
                    items(continueItems, key = { it.id }) { item ->
                        val imageModel by produceState<Any?>(initialValue = null, item.id, item.imageSourceKey) {
                            value = when (item) {
                                is ContinueHomeItem.MovieItem -> resolveMoviePosterModel(item.movie)
                                is ContinueHomeItem.EpisodeItem -> resolveEpisodeImageModel(item.episode)
                            }
                        }
                        PosterCard(
                            title = item.title,
                            rating = "-",
                            meta = item.localizedMeta(),
                            hasPoster = item.hasImage || imageModel != null,
                            progressPercent = item.progress.progressPercent,
                            favorite = false,
                            seen = false,
                            imageModel = imageModel,
                            onFocused = { selectedContinueId = item.id },
                            onClick = {
                                when (item) {
                                    is ContinueHomeItem.MovieItem -> onOpenMovie(item.movie)
                                    is ContinueHomeItem.EpisodeItem -> onOpenEpisode(item.episode)
                                }
                            },
                        )
                    }
                }
            }

            if (recentChannels.isNotEmpty()) {
                VivicastContentRow(title = stringResource(R.string.home_recent_channels)) {
                    items(recentChannels, key = { it.channel.id }) { item ->
                        val logoModel by produceState<Any?>(initialValue = null, item.channel.id, item.channel.logoUrl) {
                            value = resolveChannelLogoModel(item.channel)
                        }
                        VivicastChannelCard(
                            channelName = item.channel.name,
                            program = if (item.history.durationWatchedMillis > 0L)
                                stringResource(R.string.home_watched_ago, item.history.durationWatchedMillis / 60_000L)
                            else
                                stringResource(R.string.home_recently_watched),
                            logoText = item.channel.name.firstOrNull()?.uppercase() ?: "?",
                            logoMissing = item.channel.logoUrl.isNullOrBlank() && logoModel == null,
                            selected = selectedChannelId == item.channel.id,
                            progressPercent = 0,
                            favorite = false,
                            catchUp = item.channel.isCatchupAvailable,
                            logoModel = logoModel,
                            modifier = Modifier.width(340.dp),
                            onFocused = { selectedChannelId = item.channel.id },
                            onClick = { onOpenChannel(item.channel) },
                        )
                    }
                }
            }

            if (continueItems.isEmpty() && recentChannels.isEmpty()) {
                InfoPanel(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body_long),
                    badge = stringResource(R.string.common_empty_badge),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HomeHero(
    continueItem: ContinueHomeItem?,
    recentChannel: RecentChannelHomeItem?,
    onOpenMovie: (Movie) -> Unit,
    onOpenEpisode: (Episode) -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onAddPlaylist: () -> Unit,
) {
    when {
        continueItem != null -> HeroPanel(
            title = continueItem.title,
            body = stringResource(R.string.home_hero_continue_body),
            meta = continueItem.localizedMeta(),
            modifier = Modifier.fillMaxWidth(),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    ActionPill(stringResource(R.string.home_continue), onClick = {
                        when (continueItem) {
                            is ContinueHomeItem.MovieItem -> onOpenMovie(continueItem.movie)
                            is ContinueHomeItem.EpisodeItem -> onOpenEpisode(continueItem.episode)
                        }
                    })
                }
            },
        )

        recentChannel != null -> HeroPanel(
            title = recentChannel.channel.name,
            body = stringResource(R.string.home_hero_recent_channel_body),
            meta = if (recentChannel.history.durationWatchedMillis > 0L)
                stringResource(R.string.home_watched_ago, recentChannel.history.durationWatchedMillis / 60_000L)
            else
                stringResource(R.string.home_recently_watched),
            modifier = Modifier.fillMaxWidth(),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    ActionPill(stringResource(R.string.home_hero_play_livetv), onClick = { onOpenChannel(recentChannel.channel) })
                }
            },
        )

        else -> HeroPanel(
            title = stringResource(R.string.nav_home),
            body = stringResource(R.string.home_hero_default_body),
            meta = stringResource(R.string.home_hero_no_playlist),
            modifier = Modifier.fillMaxWidth(),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    ActionPill(stringResource(R.string.home_add_playlist), onClick = onAddPlaylist)
                }
            },
        )
    }
}

@Composable
private fun ContinueHomeItem.localizedMeta(): String = when (this) {
    is ContinueHomeItem.MovieItem -> "${progress.progressPercent} % | ${stringResource(R.string.home_continue_movie_label)}"
    is ContinueHomeItem.EpisodeItem -> meta
}
