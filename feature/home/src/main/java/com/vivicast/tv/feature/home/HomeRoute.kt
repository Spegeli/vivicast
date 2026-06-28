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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
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
import com.vivicast.tv.domain.model.ChannelHistory
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.PlaybackProgress
import kotlinx.coroutines.flow.flowOf

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
    val continueFlow = remember(playbackRepository) {
        playbackRepository?.observeAllContinueWatching() ?: flowOf(emptyList())
    }
    val recentChannelsFlow = remember(playbackRepository) {
        playbackRepository?.observeAllRecentChannels(limit = 12) ?: flowOf(emptyList())
    }
    val continueProgress by continueFlow.collectAsState(initial = emptyList())
    val recentChannelHistory by recentChannelsFlow.collectAsState(initial = emptyList())
    val continueItems by produceState(initialValue = emptyList(), mediaRepository, continueProgress) {
        value = mediaRepository?.resolveContinueItems(continueProgress) ?: emptyList()
    }
    val recentChannels by produceState(initialValue = emptyList(), mediaRepository, recentChannelHistory) {
        value = mediaRepository?.resolveRecentChannels(recentChannelHistory) ?: emptyList()
    }

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
                VivicastContentRow(title = "Fortsetzen") {
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
                            meta = item.meta,
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
                VivicastContentRow(title = "Zuletzt gesehene Live-TV-Sender") {
                    items(recentChannels, key = { it.channel.id }) { item ->
                        val logoModel by produceState<Any?>(initialValue = null, item.channel.id, item.channel.logoUrl) {
                            value = resolveChannelLogoModel(item.channel)
                        }
                        VivicastChannelCard(
                            channelName = item.channel.name,
                            program = item.meta,
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
                    title = "Noch keine Inhalte",
                    body = "Lege in den Einstellungen zuerst eine Wiedergabeliste an. Begonnene Filme, Serien und zuletzt gesehene Live-TV-Sender erscheinen danach hier.",
                    badge = "Leer",
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
            body = "Setze die Wiedergabe an der letzten Position fort.",
            meta = continueItem.meta,
            modifier = Modifier.fillMaxWidth(),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    ActionPill("Fortsetzen", onClick = {
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
            body = "Zuletzt gesehener Live-TV-Sender.",
            meta = recentChannel.meta,
            modifier = Modifier.fillMaxWidth(),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    ActionPill("Live-TV abspielen", onClick = { onOpenChannel(recentChannel.channel) })
                }
            },
        )

        else -> HeroPanel(
            title = "Home",
            body = "Begonnene Filme, Serien und zuletzt gesehene Live-TV-Sender erscheinen hier.",
            meta = "Keine Wiedergabeliste",
            modifier = Modifier.fillMaxWidth(),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    ActionPill("Wiedergabeliste hinzufügen", onClick = onAddPlaylist)
                }
            },
        )
    }
}

private suspend fun MediaRepository.resolveContinueItems(progress: List<PlaybackProgress>): List<ContinueHomeItem> =
    progress.mapNotNull { item ->
        when (item.mediaType) {
            MediaType.Movie -> getMovie(item.providerId, item.mediaId)?.let { movie ->
                ContinueHomeItem.MovieItem(progress = item, movie = movie)
            }
            MediaType.Episode -> getEpisode(item.providerId, item.mediaId)?.let { episode ->
                ContinueHomeItem.EpisodeItem(progress = item, episode = episode)
            }
            MediaType.Channel,
            MediaType.Series -> null
        }
    }

private suspend fun MediaRepository.resolveRecentChannels(history: List<ChannelHistory>): List<RecentChannelHomeItem> =
    history.mapNotNull { item ->
        getChannel(item.providerId, item.channelId)?.let { channel ->
            RecentChannelHomeItem(history = item, channel = channel)
        }
    }

private sealed interface ContinueHomeItem {
    val id: String
    val title: String
    val meta: String
    val progress: PlaybackProgress
    val hasImage: Boolean
    val imageSourceKey: String?

    data class MovieItem(
        override val progress: PlaybackProgress,
        val movie: Movie,
    ) : ContinueHomeItem {
        override val id: String = "movie:${movie.providerId}:${movie.id}"
        override val title: String = movie.name
        override val meta: String = "${progress.progressPercent} % | Film"
        override val hasImage: Boolean = !movie.posterUrl.isNullOrBlank()
        override val imageSourceKey: String? = movie.posterUrl
    }

    data class EpisodeItem(
        override val progress: PlaybackProgress,
        val episode: Episode,
    ) : ContinueHomeItem {
        override val id: String = "episode:${episode.providerId}:${episode.id}"
        override val title: String = episode.name
        override val meta: String = "${progress.progressPercent} % | S${episode.seasonNumber}E${episode.episodeNumber}"
        override val hasImage: Boolean = !episode.thumbnailUrl.isNullOrBlank()
        override val imageSourceKey: String? = episode.thumbnailUrl
    }
}

private data class RecentChannelHomeItem(
    val history: ChannelHistory,
    val channel: Channel,
) {
    val meta: String = if (history.durationWatchedMillis > 0L) {
        "${history.durationWatchedMillis / 60_000L} Min zuletzt gesehen"
    } else {
        "Zuletzt gesehen"
    }
}
