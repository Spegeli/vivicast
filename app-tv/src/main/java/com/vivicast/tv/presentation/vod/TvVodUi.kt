package com.vivicast.tv

import android.view.LayoutInflater
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.vivicast.core.model.Episode
import com.vivicast.core.model.EpisodePlaybackProgress
import com.vivicast.core.model.Movie
import com.vivicast.core.model.MoviePlaybackProgress
import com.vivicast.core.model.PlaybackState
import com.vivicast.core.model.PlaybackContentType
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.Season
import com.vivicast.core.model.Series
data class VodLibraryItemUi(
    val id: String,
    val playlistId: String,
    val title: String,
    val secondary: String,
    val tertiary: String,
    val description: String?,
    val badge: String,
    val posterUrl: String?
)

data class VodLibrarySectionUi(
    val playlistId: String,
    val providerName: String,
    val items: List<VodLibraryItemUi>
)

fun <T> buildVodLibrarySections(
    items: List<T>,
    orderedPlaylistIds: List<String>,
    playlistNameById: Map<String, String>,
    itemMapper: (T) -> VodLibraryItemUi
): List<VodLibrarySectionUi> {
    val mappedItems = items.map(itemMapper)
    val groupedItems = mappedItems.groupBy { it.playlistId }
    val orderedSections = orderedPlaylistIds.mapNotNull { playlistId ->
        groupedItems[playlistId]?.takeIf { it.isNotEmpty() }?.let { sectionItems ->
            VodLibrarySectionUi(
                playlistId = playlistId,
                providerName = playlistNameById[playlistId] ?: "Provider",
                items = sectionItems
            )
        }
    }
    val knownPlaylistIds = orderedSections.map { it.playlistId }.toSet()
    val remainingSections = groupedItems
        .filterKeys { it !in knownPlaylistIds }
        .toSortedMap()
        .map { (playlistId, sectionItems) ->
            VodLibrarySectionUi(
                playlistId = playlistId,
                providerName = playlistNameById[playlistId] ?: "Provider",
                items = sectionItems
            )
        }
    return orderedSections + remainingSections
}

@Composable
fun LibraryPlaceholderPanel(
    title: String,
    accentLabel: String,
    summary: String,
    detail: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(ViviCastColors.Surface)
            .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = accentLabel,
            color = ViviCastColors.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = summary,
            color = ViviCastColors.TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = detail,
            color = ViviCastColors.TextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun VodLibraryPanel(
    title: String,
    accentLabel: String,
    itemCount: Int,
    sections: List<VodLibrarySectionUi>,
    selectedItemId: String?,
    emptyTitle: String,
    emptyBody: String,
    onFocusItem: (String) -> Unit,
    onShowNavigation: () -> Unit,
    detailContent: @Composable ColumnScope.(VodLibraryItemUi?) -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    val allItems = remember(sections) { sections.flatMap { it.items } }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        text = title,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = accentLabel,
                        color = ViviCastColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "$itemCount",
                    color = ViviCastColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (allItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ViviCastColors.Surface)
                        .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = emptyTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(text = emptyBody, color = ViviCastColors.TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sections.forEachIndexed { sectionIndex, section ->
                        item(key = "provider-${section.playlistId}") {
                            VodProviderSectionHeader(
                                providerName = section.providerName,
                                itemCount = section.items.size
                            )
                        }
                        itemsIndexed(section.items, key = { _, item -> item.id }) { itemIndex, item ->
                            VodLibraryRow(
                                item = item,
                                selected = item.id == selectedItemId,
                                focusRequester = if (sectionIndex == 0 && itemIndex == 0) firstItemFocusRequester else null,
                                onFocus = { onFocusItem(item.id) },
                                onMoveLeft = {
                                    onShowNavigation()
                                    true
                                }
                            )
                        }
                    }
                }
            }
        }

        val selectedItem = allItems.firstOrNull { it.id == selectedItemId } ?: allItems.firstOrNull()
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(ViviCastColors.Surface)
                .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            detailContent(selectedItem)
        }
    }
}

@Composable
fun VodProviderSectionHeader(
    providerName: String,
    itemCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = providerName,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$itemCount",
            color = ViviCastColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ColumnScope.MovieDetailPanel(
    controller: ViviCastTvController,
    selectedItem: VodLibraryItemUi?,
    movie: Movie?,
    progress: MoviePlaybackProgress?,
    playbackState: com.vivicast.core.model.PlaybackState,
    providerName: String?,
    onPlay: (Movie, Long) -> Unit
) {
    Text(text = "Details", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    if (selectedItem == null || movie == null) {
        Text(
            text = "Move into the list to preview imported movie entries.",
            color = ViviCastColors.TextMuted,
            fontSize = 14.sp
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = selectedItem.badge, color = ViviCastColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = providerName ?: selectedItem.secondary, color = ViviCastColors.TextMuted, fontSize = 12.sp)
    }
    Text(text = movie.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
    Text(
        text = buildString {
            movie.durationMinutes?.let { append("$it min") }
            movie.releaseDate?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" / ")
                append(it)
            }
        }.ifBlank { selectedItem.tertiary },
        color = ViviCastColors.TextSecondary,
        fontSize = 14.sp
    )
    progress?.let {
        Text(
            text = if (it.completed) "Completed" else "Resume at ${it.positionMs.asPlaybackTimeLabel()}",
            color = if (it.completed) ViviCastColors.Success else ViviCastColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsActionChip(
            text = if ((progress?.positionMs ?: 0L) > 0L && progress?.completed != true) "Resume" else "Play",
            onClick = {
                val startPositionMs = if (progress?.completed == true) 0L else progress?.positionMs ?: 0L
                onPlay(movie, startPositionMs)
            }
        )
        if (progress != null && progress.completed != true && progress.positionMs > 0L) {
            SettingsActionChip(
                text = "Restart",
                onClick = { onPlay(movie, 0L) }
            )
        }
    }
    if (playbackState.contentType == PlaybackContentType.MOVIE && playbackState.channelId == movie.id) {
        EmbeddedVodPlayer(controller = controller, title = movie.title, playbackState = playbackState)
    }
    Text(
        text = movie.plot?.takeIf { it.isNotBlank() } ?: "No description imported yet.",
        color = ViviCastColors.TextMuted,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
}

@Composable
fun ColumnScope.SeriesDetailPanel(
    controller: ViviCastTvController,
    selectedItem: VodLibraryItemUi?,
    series: Series?,
    seasons: List<Season>,
    selectedSeasonId: String?,
    onSelectSeason: (String) -> Unit,
    episodes: List<Episode>,
    selectedEpisodeId: String?,
    onSelectEpisode: (String) -> Unit,
    selectedEpisode: Episode?,
    episodeProgress: EpisodePlaybackProgress?,
    playbackState: com.vivicast.core.model.PlaybackState,
    providerName: String?,
    onPlayEpisode: (Episode, String, Long) -> Unit
) {
    Text(text = "Details", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    if (selectedItem == null || series == null) {
        Text(
            text = "Move into the list to preview imported series entries.",
            color = ViviCastColors.TextMuted,
            fontSize = 14.sp
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = selectedItem.badge, color = ViviCastColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = providerName ?: selectedItem.secondary, color = ViviCastColors.TextMuted, fontSize = 12.sp)
    }
    Text(text = series.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
    Text(
        text = buildString {
            series.episodeRunTimeMinutes?.let { append("$it min episodes") }
            series.releaseDate?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" / ")
                append(it)
            }
        }.ifBlank { selectedItem.tertiary },
        color = ViviCastColors.TextSecondary,
        fontSize = 14.sp
    )
    Text(
        text = series.plot?.takeIf { it.isNotBlank() } ?: "No description imported yet.",
        color = ViviCastColors.TextMuted,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    if (seasons.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons, key = { it.id }) { season ->
                SettingsActionChip(
                    text = season.title,
                    selected = season.id == selectedSeasonId,
                    onClick = { onSelectSeason(season.id) }
                )
            }
        }
    }
    if (selectedEpisode != null) {
        Text(
            text = "Episode ${selectedEpisode.episodeNumber}: ${selectedEpisode.title}",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        episodeProgress?.let {
            Text(
                text = if (it.completed) "Completed" else "Resume at ${it.positionMs.asPlaybackTimeLabel()}",
                color = if (it.completed) ViviCastColors.Success else ViviCastColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionChip(
                text = if ((episodeProgress?.positionMs ?: 0L) > 0L && episodeProgress?.completed != true) "Resume" else "Play",
                onClick = {
                    val startPositionMs = if (episodeProgress?.completed == true) 0L else episodeProgress?.positionMs ?: 0L
                    onPlayEpisode(selectedEpisode, "${series.title} - ${selectedEpisode.title}", startPositionMs)
                }
            )
            if (episodeProgress != null && episodeProgress.completed != true && episodeProgress.positionMs > 0L) {
                SettingsActionChip(
                    text = "Restart",
                    onClick = { onPlayEpisode(selectedEpisode, "${series.title} - ${selectedEpisode.title}", 0L) }
                )
            }
        }
        if (playbackState.contentType == PlaybackContentType.EPISODE && playbackState.channelId == selectedEpisode.id) {
            EmbeddedVodPlayer(
                controller = controller,
                title = "${series.title} - ${selectedEpisode.title}",
                playbackState = playbackState
            )
        }
    }
    if (episodes.isNotEmpty()) {
        Text(text = "Episodes", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            episodes.forEach { episode ->
                VodEpisodeRow(
                    episode = episode,
                    selected = episode.id == selectedEpisodeId,
                    onFocus = { onSelectEpisode(episode.id) },
                    onPlay = {
                        val startPositionMs = if (episode.id == selectedEpisode?.id && episodeProgress?.completed != true) {
                            episodeProgress?.positionMs ?: 0L
                        } else {
                            0L
                        }
                        onPlayEpisode(episode, "${series.title} - ${episode.title}", startPositionMs)
                    }
                )
            }
        }
    }
}

@Composable
fun EmbeddedVodPlayer(
    controller: ViviCastTvController,
    title: String,
    playbackState: com.vivicast.core.model.PlaybackState
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    (LayoutInflater.from(context)
                        .inflate(R.layout.vivicast_player_view, null, false) as PlayerView).apply {
                        player = controller.media3Player
                    }
                },
                update = { playerView ->
                    playerView.player = controller.media3Player
                }
            )
        }
        Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            text = buildString {
                append(playbackState.status.asText())
                playbackState.durationMs?.let { duration ->
                    append(" / ")
                    append(playbackState.positionMs.asPlaybackTimeLabel())
                    append(" / ")
                    append(duration.asPlaybackTimeLabel())
                }
            },
            color = ViviCastColors.TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
fun VodEpisodeRow(
    episode: Episode,
    selected: Boolean,
    onFocus: () -> Unit,
    onPlay: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    focused -> ViviCastColors.FocusFill
                    selected -> ViviCastColors.Selected
                    else -> ViviCastColors.SurfaceRaised
                }
            )
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = when {
                    focused -> ViviCastColors.Focus
                    selected -> ViviCastColors.Accent
                    else -> ViviCastColors.Line
                },
                shape = shape
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onPlay)
            .activateOnCenter(onPlay)
            .focusable()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "E${episode.episodeNumber} - ${episode.title}",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = episode.durationMinutes?.let { "$it min" } ?: "Episode",
                color = ViviCastColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun VodLibraryRow(
    item: VodLibraryItemUi,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onFocus: () -> Unit,
    onMoveLeft: () -> Boolean
) {
    var hasFocus by remember { mutableStateOf(false) }
    val borderColor = if (hasFocus) ViviCastColors.Focus else ViviCastColors.Line
    val backgroundColor = when {
        hasFocus -> ViviCastColors.FocusFill
        selected -> ViviCastColors.Selected
        else -> ViviCastColors.Surface
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused) onFocus()
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onMoveLeft()
                } else {
                    false
                }
            }
            .focusable()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VodPosterThumbnail(
                title = item.title,
                posterUrl = item.posterUrl
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = item.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = item.tertiary, color = ViviCastColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(text = item.badge, color = ViviCastColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VodPosterThumbnail(
    title: String,
    posterUrl: String?
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(84.dp)
            .clip(shape)
            .background(ViviCastColors.SurfaceRaised)
            .border(1.dp, ViviCastColors.Line, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.firstOrNull()?.uppercase() ?: "?",
            color = ViviCastColors.TextSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        posterUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

