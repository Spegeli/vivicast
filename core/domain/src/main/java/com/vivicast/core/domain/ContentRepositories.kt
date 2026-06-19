package com.vivicast.core.domain

import com.vivicast.core.model.Channel
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgProgram
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.Episode
import com.vivicast.core.model.EpisodePlaybackProgress
import com.vivicast.core.model.FavoriteChannel
import com.vivicast.core.model.Movie
import com.vivicast.core.model.MovieCategory
import com.vivicast.core.model.MoviePlaybackProgress
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.RecentChannel
import com.vivicast.core.model.Season
import com.vivicast.core.model.Series
import com.vivicast.core.model.SeriesCategory
import kotlinx.coroutines.flow.Flow
import java.io.Reader

interface LiveTvRepository {
    fun observeChannels(): Flow<List<Channel>>
    fun observeCategories(): Flow<List<ChannelCategory>>
    fun observePlaylists(): Flow<List<Playlist>>
    fun observeFavorites(): Flow<List<FavoriteChannel>>
    fun observeRecents(): Flow<List<RecentChannel>>
    suspend fun addFavorite(channelId: String)
    suspend fun removeFavorite(channelId: String)
    suspend fun markRecent(channelId: String)
}

interface ProviderRepository {
    suspend fun renamePlaylist(playlistId: String, name: String)
    suspend fun updatePlaylistConnection(
        playlistId: String,
        sourceUri: String?,
        sourceUsername: String?,
        sourcePassword: String?
    )
    suspend fun deletePlaylist(playlistId: String)
}

interface EpgRepository {
    fun observeEpgSources(): Flow<List<EpgSource>>
    fun observeEpgProgramCount(): Flow<Int>
    fun observeNowNext(channelId: String, nowUtcEpochMillis: Long): Flow<EpgNowNext>
    fun observeGuidePrograms(
        channelIds: List<String>,
        fromUtcEpochMillis: Long,
        toUtcEpochMillis: Long
    ): Flow<List<EpgProgram>>
    suspend fun upsertEpgSource(playlistId: String, name: String, sourceUri: String)
    suspend fun upsertGlobalEpgSource(name: String, sourceUri: String)
    suspend fun removeEpgSource(playlistId: String)
    suspend fun removeEpgSourceById(sourceId: String)
    suspend fun importXmltv(
        playlistId: String,
        sourceName: String,
        sourceUri: String,
        timeOffsetMinutes: Int = 0,
        retentionDays: Int = 7,
        reader: Reader
    ): EpgImportResult
}

interface VodRepository {
    fun observeMovieCategories(playlistId: String): Flow<List<MovieCategory>>
    fun observeMovies(playlistId: String): Flow<List<Movie>>
    fun observeAllMovies(): Flow<List<Movie>>
    fun observeSeriesCategories(playlistId: String): Flow<List<SeriesCategory>>
    fun observeSeries(playlistId: String): Flow<List<Series>>
    fun observeAllSeries(): Flow<List<Series>>
    fun observeSeasons(seriesId: String): Flow<List<Season>>
    fun observeEpisodes(seasonId: String): Flow<List<Episode>>
    fun observeMovieProgress(movieId: String): Flow<MoviePlaybackProgress?>
    fun observeEpisodeProgress(episodeId: String): Flow<EpisodePlaybackProgress?>
    suspend fun saveMovieProgress(progress: MoviePlaybackProgress)
    suspend fun saveEpisodeProgress(progress: EpisodePlaybackProgress)
}
