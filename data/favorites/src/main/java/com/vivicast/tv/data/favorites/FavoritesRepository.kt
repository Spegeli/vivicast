package com.vivicast.tv.data.favorites

import com.vivicast.tv.domain.model.Favorite
import com.vivicast.tv.domain.model.MediaType
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    fun observeFavorites(providerId: String, mediaType: MediaType): Flow<List<Favorite>>

    suspend fun isFavorite(providerId: String, mediaType: MediaType, mediaId: String): Boolean

    suspend fun addFavorite(favorite: Favorite)

    suspend fun removeFavorite(providerId: String, mediaType: MediaType, mediaId: String)
}
