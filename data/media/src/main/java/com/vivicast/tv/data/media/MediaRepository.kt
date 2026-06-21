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

interface MediaRepository {
    fun observeCategories(providerId: String, type: CategoryType): Flow<List<Category>>

    fun observeChannels(providerId: String, categoryId: String?): Flow<List<Channel>>

    fun observeMovies(providerId: String, categoryId: String?): Flow<List<Movie>>

    fun observeSeries(providerId: String, categoryId: String?): Flow<List<Series>>

    fun observeSeasons(providerId: String, seriesId: String): Flow<List<Season>>

    fun observeEpisodes(providerId: String, seasonId: String): Flow<List<Episode>>

    suspend fun search(query: String, limitPerType: Int): SearchResults
}
