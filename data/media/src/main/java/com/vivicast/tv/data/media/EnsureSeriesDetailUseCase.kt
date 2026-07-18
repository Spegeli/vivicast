package com.vivicast.tv.data.media

import com.vivicast.tv.iptv.xtream.XtreamClient
import com.vivicast.tv.iptv.xtream.XtreamCredentials
import com.vivicast.tv.iptv.xtream.XtreamParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-demand import of ONE Xtream series' seasons/episodes (when the user opens it): fetch its info, parse,
 * and import within that single series only. Pure of Android/diagnostics — the caller resolves the
 * credentials App-side and logs the outcome from the returned [EnsureSeriesDetailResult]. Fails silently
 * ([EnsureSeriesDetailResult.Failed]) offline / on an expired account / on a bad response.
 */
class EnsureSeriesDetailUseCase(
    private val catalogImportRepository: CatalogImportRepository,
    private val xtreamClient: XtreamClient,
    private val xtreamParser: XtreamParser,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun importDetail(
        providerId: String,
        seriesRemoteId: String,
        credentials: XtreamCredentials,
    ): EnsureSeriesDetailResult = withContext(ioDispatcher) {
        val info = runCatching {
            xtreamParser.parseSeriesInfo(seriesRemoteId, xtreamClient.getSeriesInfo(credentials, seriesRemoteId))
        }.getOrNull() ?: return@withContext EnsureSeriesDetailResult.Failed
        val result = catalogImportRepository.importXtreamSeriesDetail(providerId, info)
        EnsureSeriesDetailResult.Imported(seasonsAdded = result.seasons.added, episodesAdded = result.episodes.added)
    }
}

sealed interface EnsureSeriesDetailResult {
    /** Fetch/parse failed (offline / expired account / bad response) — nothing was imported. */
    data object Failed : EnsureSeriesDetailResult
    data class Imported(val seasonsAdded: Int, val episodesAdded: Int) : EnsureSeriesDetailResult
}
