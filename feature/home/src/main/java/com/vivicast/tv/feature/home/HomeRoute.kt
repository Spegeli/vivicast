package com.vivicast.tv.feature.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.VivicastContentRow
import com.vivicast.tv.core.designsystem.VivicastHomeChannelCard
import com.vivicast.tv.core.designsystem.VivicastHomePosterCard
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackRepository
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.Series

@Composable
fun HomeRoute(
    playbackRepository: PlaybackRepository? = null,
    mediaRepository: MediaRepository? = null,
    providerRepository: ProviderRepository? = null,
    resolveChannelLogoModel: suspend (Channel) -> Any? = { null },
    resolveMoviePosterModel: suspend (Movie) -> Any? = { null },
    resolveSeriesPosterModel: suspend (Series) -> Any? = { null },
    onOpenMovie: (Movie) -> Unit = {},
    onOpenEpisode: (Episode) -> Unit = {},
    onOpenChannel: (Channel) -> Unit = {},
    onAddPlaylist: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
    onOpenLiveTv: () -> Unit = {},
    onOpenMovies: () -> Unit = {},
    onOpenSeries: () -> Unit = {},
) {
    if (playbackRepository == null || mediaRepository == null || providerRepository == null) {
        HomeContent(
            state = HomeUiState(loaded = true, emptyReason = HomeEmptyReason.NoPlaylist),
            resolveChannelLogoModel = resolveChannelLogoModel,
            resolveMoviePosterModel = resolveMoviePosterModel,
            resolveSeriesPosterModel = resolveSeriesPosterModel,
            onOpenMovie = onOpenMovie,
            onOpenEpisode = onOpenEpisode,
            onOpenChannel = onOpenChannel,
            onAddPlaylist = onAddPlaylist,
            onOpenSettings = onOpenSettings,
            onOpenPlaylists = onOpenPlaylists,
            onOpenLiveTv = onOpenLiveTv,
            onOpenMovies = onOpenMovies,
            onOpenSeries = onOpenSeries,
        )
        return
    }

    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(playbackRepository, mediaRepository, providerRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = uiState,
        resolveChannelLogoModel = resolveChannelLogoModel,
        resolveMoviePosterModel = resolveMoviePosterModel,
        resolveSeriesPosterModel = resolveSeriesPosterModel,
        onOpenMovie = onOpenMovie,
        onOpenEpisode = onOpenEpisode,
        onOpenChannel = onOpenChannel,
        onAddPlaylist = onAddPlaylist,
        onOpenSettings = onOpenSettings,
        onOpenPlaylists = onOpenPlaylists,
        onOpenLiveTv = onOpenLiveTv,
        onOpenMovies = onOpenMovies,
        onOpenSeries = onOpenSeries,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    resolveChannelLogoModel: suspend (Channel) -> Any?,
    resolveMoviePosterModel: suspend (Movie) -> Any?,
    resolveSeriesPosterModel: suspend (Series) -> Any?,
    onOpenMovie: (Movie) -> Unit,
    onOpenEpisode: (Episode) -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onAddPlaylist: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenLiveTv: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
) {
    // DOWN from the top nav must land on the FIRST element of the first shown row (history channel → else the
    // row's "go browse" CTA), not the card geometrically under the tab. One requester, attached to whichever
    // element is first (the hasLive → hasMovies → hasSeries cascade), redirected to only on a Down nav-entry.
    val firstFocusRequester = remember { FocusRequester() }
    val firstFocusModifier = Modifier.focusRequester(firstFocusRequester)
    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        when {
            // Loading guard: render only the shell backdrop until the first emit, so the empty state never
            // flashes before providers + history have loaded.
            !state.loaded -> Unit

            state.emptyReason != null -> HomeEmptyState(
                reason = state.emptyReason,
                firstFocusRequester = firstFocusRequester,
                onAddPlaylist = onAddPlaylist,
                onOpenSettings = onOpenSettings,
                onOpenPlaylists = onOpenPlaylists,
            )

            else -> HomeRows(
                state = state,
                firstFocusRequester = firstFocusRequester,
                firstFocusModifier = firstFocusModifier,
                resolveChannelLogoModel = resolveChannelLogoModel,
                resolveMoviePosterModel = resolveMoviePosterModel,
                resolveSeriesPosterModel = resolveSeriesPosterModel,
                onOpenMovie = onOpenMovie,
                onOpenEpisode = onOpenEpisode,
                onOpenChannel = onOpenChannel,
                onOpenLiveTv = onOpenLiveTv,
                onOpenMovies = onOpenMovies,
                onOpenSeries = onOpenSeries,
            )
        }
    }
}

/**
 * The content rows (Sender -> Filme -> Serien). Each row is shown only when its type exists in an active
 * playlist; a present-but-unwatched type shows a "go browse" CTA button instead of cards. The first element of
 * the FIRST shown row carries [firstFocusModifier] — the DOWN nav-entry target (so DOWN lands on the first
 * history channel / first CTA, not the card geometrically under the tab).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HomeRows(
    state: HomeUiState,
    firstFocusRequester: FocusRequester,
    firstFocusModifier: Modifier,
    resolveChannelLogoModel: suspend (Channel) -> Any?,
    resolveMoviePosterModel: suspend (Movie) -> Any?,
    resolveSeriesPosterModel: suspend (Series) -> Any?,
    onOpenMovie: (Movie) -> Unit,
    onOpenEpisode: (Episode) -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onOpenLiveTv: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
        modifier = Modifier
            .fillMaxSize()
            .focusProperties {
                enter = { direction ->
                    if (direction == FocusDirection.Down) firstFocusRequester else FocusRequester.Default
                }
            }
            .focusGroup(),
    ) {
        if (state.hasLive) {
            if (state.recentChannels.isNotEmpty()) {
                val firstChannelId = state.recentChannels.first().channel.id
                VivicastContentRow(title = stringResource(R.string.home_recent_channels)) {
                    items(state.recentChannels, key = { it.channel.id }) { item ->
                        val logoModel by produceState<Any?>(null, item.channel.id, item.channel.logoUrl) {
                            value = resolveChannelLogoModel(item.channel)
                        }
                        VivicastHomeChannelCard(
                            channelName = item.channel.name,
                            logoText = item.channel.name.firstOrNull()?.uppercase() ?: "?",
                            logoMissing = item.channel.logoUrl.isNullOrBlank() && logoModel == null,
                            logoModel = logoModel,
                            onClick = { onOpenChannel(item.channel) },
                            modifier = if (item.channel.id == firstChannelId) firstFocusModifier else Modifier,
                        )
                    }
                }
            } else {
                HomeCtaRow(
                    title = stringResource(R.string.home_recent_channels),
                    buttonLabel = stringResource(R.string.home_go_livetv),
                    onClick = onOpenLiveTv,
                    buttonModifier = firstFocusModifier,
                )
            }
        }

        if (state.hasMovies) {
            val firstMod = if (!state.hasLive) firstFocusModifier else Modifier
            if (state.movieItems.isNotEmpty()) {
                val firstMovieId = state.movieItems.first().id
                VivicastContentRow(title = stringResource(R.string.home_continue_movies)) {
                    items(state.movieItems, key = { it.id }) { item ->
                        val imageModel by produceState<Any?>(null, item.id) {
                            value = resolveMoviePosterModel(item.movie)
                        }
                        VivicastHomePosterCard(
                            title = item.title,
                            hasPoster = item.hasImage || imageModel != null,
                            progressPercent = item.progress.progressPercent,
                            imageModel = imageModel,
                            onClick = { onOpenMovie(item.movie) },
                            modifier = if (item.id == firstMovieId) firstMod else Modifier,
                        )
                    }
                }
            } else {
                HomeCtaRow(
                    title = stringResource(R.string.home_continue_movies),
                    buttonLabel = stringResource(R.string.home_go_movies),
                    onClick = onOpenMovies,
                    buttonModifier = firstMod,
                )
            }
        }

        if (state.hasSeries) {
            val firstMod = if (!state.hasLive && !state.hasMovies) firstFocusModifier else Modifier
            if (state.seriesItems.isNotEmpty()) {
                val firstSeriesId = state.seriesItems.first().id
                VivicastContentRow(title = stringResource(R.string.home_continue_series)) {
                    items(state.seriesItems, key = { it.id }) { item ->
                        val imageModel by produceState<Any?>(null, item.id) {
                            value = resolveSeriesPosterModel(item.series)
                        }
                        VivicastHomePosterCard(
                            title = item.title,
                            hasPoster = item.hasImage || imageModel != null,
                            progressPercent = item.progressPercent,
                            imageModel = imageModel,
                            onClick = { onOpenEpisode(item.episode) },
                            modifier = if (item.id == firstSeriesId) firstMod else Modifier,
                        )
                    }
                }
            } else {
                HomeCtaRow(
                    title = stringResource(R.string.home_continue_series),
                    buttonLabel = stringResource(R.string.home_go_series),
                    onClick = onOpenSeries,
                    buttonModifier = firstMod,
                )
            }
        }
    }
}

/** A row for a present-but-unwatched type: the row title + a single "go browse" button. */
@Composable
private fun HomeCtaRow(title: String, buttonLabel: String, onClick: () -> Unit, buttonModifier: Modifier = Modifier) {
    VivicastContentRow(title = title) {
        item { ActionPill(buttonLabel, onClick = onClick, modifier = buttonModifier) }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HomeEmptyState(
    reason: HomeEmptyReason,
    firstFocusRequester: FocusRequester,
    onAddPlaylist: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylists: () -> Unit,
) {
    val title = when (reason) {
        HomeEmptyReason.NoPlaylist -> stringResource(R.string.home_no_playlist_title)
        HomeEmptyReason.EmptyCatalog -> stringResource(R.string.home_empty_title)
        HomeEmptyReason.AllDisabled -> stringResource(R.string.home_disabled_title)
    }
    val body = when (reason) {
        HomeEmptyReason.NoPlaylist -> stringResource(R.string.home_empty_body)
        HomeEmptyReason.AllDisabled -> stringResource(R.string.home_disabled_body)
        HomeEmptyReason.EmptyCatalog -> stringResource(R.string.home_empty_check_body)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(VivicastSpacing.Space6)
            .focusProperties {
                enter = { direction ->
                    if (direction == FocusDirection.Down) firstFocusRequester else FocusRequester.Default
                }
            }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InfoPanel(title = title, body = body, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
            when (reason) {
                HomeEmptyReason.NoPlaylist -> {
                    ActionPill(stringResource(R.string.home_add_playlist), onClick = onAddPlaylist, modifier = Modifier.focusRequester(firstFocusRequester))
                    ActionPill(stringResource(R.string.home_settings), onClick = onOpenSettings)
                }
                HomeEmptyReason.AllDisabled, HomeEmptyReason.EmptyCatalog ->
                    ActionPill(stringResource(R.string.home_open_settings), onClick = onOpenPlaylists, modifier = Modifier.focusRequester(firstFocusRequester))
            }
        }
    }
}
