package com.vivicast.core.network

interface PlaylistHttpClient {
    suspend fun getText(request: HttpRequest): NetworkResult<String>
}
