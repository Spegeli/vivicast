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
@Preview(widthDp = 1280, heightDp = 720)
@Composable
fun MoviesLibraryPanelPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(ViviCastColors.Background)) {
                VodLibraryPanel(
                    title = "Movies",
                    accentLabel = "Imported library",
                    itemCount = 3,
                    sections = listOf(
                        VodLibrarySectionUi(
                            playlistId = "backup",
                            providerName = "Backup",
                            items = listOf(
                                VodLibraryItemUi("m1", "backup", "Interstellar", "Backup", "169 min / 2014", "A science-fiction drama.", "MOVIE", null),
                                VodLibraryItemUi("m2", "backup", "Dune", "Backup", "155 min / 2021", "A desert world epic.", "MOVIE", null)
                            )
                        ),
                        VodLibrarySectionUi(
                            playlistId = "demo",
                            providerName = "Demo",
                            items = listOf(
                                VodLibraryItemUi("m3", "demo", "Arrival", "Demo", "116 min / 2016", "A first-contact mystery.", "MOVIE", null)
                            )
                        )
                    ),
                    selectedItemId = "m2",
                    emptyTitle = "No movies imported yet",
                    emptyBody = "Refresh a provider to import movies.",
                    onFocusItem = {},
                    onShowNavigation = {},
                    detailContent = { selectedItem ->
                        Text(text = "Details", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        selectedItem?.let {
                            Text(text = it.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                            Text(text = it.secondary, color = ViviCastColors.TextSecondary, fontSize = 14.sp)
                            Text(text = it.description ?: "", color = ViviCastColors.TextMuted, fontSize = 14.sp)
                        }
                    }
                )
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
fun SeriesLibraryEmptyPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(ViviCastColors.Background)) {
                VodLibraryPanel(
                    title = "Series",
                    accentLabel = "Imported library",
                    itemCount = 0,
                    sections = emptyList(),
                    selectedItemId = null,
                    emptyTitle = "No series imported yet",
                    emptyBody = "Refresh a provider to import series.",
                    onFocusItem = {},
                    onShowNavigation = {},
                    detailContent = {
                        Text(text = "Details", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Move into the list to preview imported series entries.", color = ViviCastColors.TextMuted, fontSize = 14.sp)
                    }
                )
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
fun MoviesLibraryLoadingPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(ViviCastColors.Background)) {
                VodLibraryPanel(
                    title = "Movies",
                    accentLabel = "Syncing library",
                    itemCount = 0,
                    sections = emptyList(),
                    selectedItemId = null,
                    emptyTitle = "Syncing movies...",
                    emptyBody = "Provider import is running. Movies will appear here when sync completes.",
                    onFocusItem = {},
                    onShowNavigation = {},
                    detailContent = {
                        Text(text = "Details", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Movie details appear here after import.", color = ViviCastColors.TextMuted, fontSize = 14.sp)
                    }
                )
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
fun SeriesLibraryErrorPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(ViviCastColors.Background)) {
                VodLibraryPanel(
                    title = "Series",
                    accentLabel = "Imported library",
                    itemCount = 0,
                    sections = emptyList(),
                    selectedItemId = null,
                    emptyTitle = "Series import failed",
                    emptyBody = "Last import error: Provider returned invalid metadata.",
                    onFocusItem = {},
                    onShowNavigation = {},
                    detailContent = {
                        Text(text = "Details", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Move into the list to preview imported series entries.", color = ViviCastColors.TextMuted, fontSize = 14.sp)
                    }
                )
            }
        }
    }
}

