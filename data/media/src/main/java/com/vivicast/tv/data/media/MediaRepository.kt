package com.vivicast.tv.data.media

import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryType
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.Movie
import com.vivicast.tv.domain.model.SearchResults
import com.vivicast.tv.domain.model.Season
import com.vivicast.tv.domain.model.Series
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class AndroidTvSearchSuggestion(
    val mediaType: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val deepLink: String,
)

interface MediaRepository {
    fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>>

    fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>>

    fun observeChannelsPage(
        providerId: String,
        categoryId: String?,
        limit: Int,
        offset: Int = 0,
    ): Flow<List<Channel>> =
        observeChannels(providerId, categoryId).map { channels ->
            channels.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))
        }

    fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>>

    fun observeMoviesPage(
        providerId: String,
        categoryId: String?,
        limit: Int,
        offset: Int = 0,
    ): Flow<List<Movie>> =
        observeMovies(providerId, categoryId).map { movies ->
            movies.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))
        }

    fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>>

    fun observeSeriesPage(
        providerId: String,
        categoryId: String?,
        limit: Int,
        offset: Int = 0,
    ): Flow<List<Series>> =
        observeSeries(providerId, categoryId).map { series ->
            series.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))
        }

    fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>>

    fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>>

    suspend fun getChannel(providerId: String, channelId: String): Channel?

    suspend fun getChannelByStableKeys(providerStableKey: String, channelStableKey: String): Channel? = null

    suspend fun getMovie(providerId: String, movieId: String): Movie?

    suspend fun getMovieByStableKeys(providerStableKey: String, movieStableKey: String): Movie? = null

    suspend fun getSeries(providerId: String, seriesId: String): Series? = null

    suspend fun getSeriesByStableKeys(providerStableKey: String, seriesStableKey: String): Series? = null

    suspend fun getEpisode(providerId: String, episodeId: String): Episode?

    suspend fun getEpisodeByStableKeys(providerStableKey: String, episodeStableKey: String): Episode? = null

    suspend fun getNextEpisode(episode: Episode): Episode? = null

    suspend fun search(query: String, limitPerType: Int): SearchResults

    fun observeSearchHistory(limit: Int): Flow<List<String>> = flowOf(emptyList())

    suspend fun addSearchHistory(query: String) = Unit

    suspend fun deleteSearchHistory(query: String) = Unit

    suspend fun clearSearchHistory() = Unit

    suspend fun searchAndroidTvSuggestions(query: String, limit: Int): List<AndroidTvSearchSuggestion> =
        emptyList()

    suspend fun rebuildAndroidTvSearchIndex(
        protectMovies: Boolean,
        protectSeries: Boolean,
        protectAdultContent: Boolean,
    ) = Unit
}
