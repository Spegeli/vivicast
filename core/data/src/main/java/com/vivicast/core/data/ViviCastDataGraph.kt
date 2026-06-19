package com.vivicast.core.data

import android.content.Context
import com.vivicast.core.database.ViviCastDatabase
import com.vivicast.core.database.ViviCastDatabaseFactory
import com.vivicast.core.network.HttpRequest
import com.vivicast.core.network.NetworkResult
import com.vivicast.core.network.UrlConnectionPlaylistHttpClient
import com.vivicast.core.network.UrlConnectionXtreamClient
import com.vivicast.core.network.XtreamLiveStream
import com.vivicast.core.model.XtreamCredentials

class ViviCastDataGraph(context: Context) {
    private val database: ViviCastDatabase = ViviCastDatabaseFactory.create(context.applicationContext)
    private val playlistHttpClient = UrlConnectionPlaylistHttpClient()
    private val xtreamClient = UrlConnectionXtreamClient()

    val playlistImportUseCase = PlaylistImportUseCase(database)
    val vodLibraryUseCase = VodLibraryUseCase(database)
    val xtreamVodImportUseCase = XtreamVodImportUseCase(database, xtreamClient)

    suspend fun downloadText(url: String, headers: Map<String, String> = emptyMap()): Result<String> {
        return when (val response = playlistHttpClient.getText(HttpRequest(url, headers))) {
            is NetworkResult.Success -> Result.success(response.value)
            is NetworkResult.Failure -> Result.failure(
                response.cause ?: IllegalStateException(response.message)
            )
        }
    }

    suspend fun <T> readTextStream(
        url: String,
        headers: Map<String, String> = emptyMap(),
        block: suspend (java.io.Reader) -> T
    ): Result<T> {
        return when (val response = playlistHttpClient.readTextStream(HttpRequest(url, headers), block)) {
            is NetworkResult.Success -> Result.success(response.value)
            is NetworkResult.Failure -> Result.failure(
                response.cause ?: IllegalStateException(response.message)
            )
        }
    }

    suspend fun importXtreamLive(
        playlistName: String,
        playlistId: String? = null,
        credentials: XtreamCredentials
    ): Result<com.vivicast.core.domain.M3uImportResult> {
        return when (val account = xtreamClient.authenticate(credentials)) {
            is NetworkResult.Failure -> Result.failure(
                account.cause ?: IllegalStateException(account.message)
            )
            is NetworkResult.Success -> {
                val categoriesById = when (val categories = xtreamClient.getLiveCategories(credentials)) {
                    is NetworkResult.Success -> categories.value.associate { it.id to it.name }
                    is NetworkResult.Failure -> emptyMap()
                }
                when (val streams = xtreamClient.getLiveStreams(credentials)) {
                    is NetworkResult.Failure -> Result.failure(
                        streams.cause ?: IllegalStateException(streams.message)
                    )
                    is NetworkResult.Success -> runCatching {
                        playlistImportUseCase.importM3u(
                            playlistId = playlistId,
                            playlistName = playlistName.ifBlank { "Xtream ${account.value.username}" },
                            sourceUri = credentials.baseUrl,
                            sourceType = com.vivicast.core.model.PlaylistSourceType.XTREAM_CODES,
                            sourceUsername = credentials.username,
                            sourcePassword = credentials.password,
                            content = streams.value.toM3u(categoriesById)
                        )
                    }
                }
            }
        }
    }

    fun close() {
        database.close()
    }
}

private fun List<XtreamLiveStream>.toM3u(categoriesById: Map<String, String>): String {
    return buildString {
        appendLine("#EXTM3U")
        this@toM3u.forEach { stream ->
            val category = stream.categoryId?.let { categoriesById[it] } ?: "Live TV"
            val attributes = buildList {
                stream.epgChannelId?.let { add("tvg-id=\"${it.escapeM3uAttribute()}\"") }
                add("tvg-name=\"${stream.name.escapeM3uAttribute()}\"")
                stream.logoUrl?.let { add("tvg-logo=\"${it.escapeM3uAttribute()}\"") }
                add("group-title=\"${category.escapeM3uAttribute()}\"")
                if (stream.catchupSupported) {
                    add("catchup=\"default\"")
                }
                stream.archiveDurationHours
                    ?.takeIf { it > 0 }
                    ?.let { hours -> add("catchup-days=\"${hours.toArchiveWindowDaysFromHours()}\"") }
            }.joinToString(" ")
            appendLine("#EXTINF:-1 $attributes,${stream.name}")
            appendLine(stream.streamUrl)
        }
    }
}

private fun Int.toArchiveWindowDaysFromHours(): Int {
    return kotlin.math.ceil(this.toDouble() / 24.0).toInt().coerceAtLeast(1)
}

private fun String.escapeM3uAttribute(): String {
    return replace("\"", "'")
}
