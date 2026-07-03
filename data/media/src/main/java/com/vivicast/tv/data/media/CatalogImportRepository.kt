package com.vivicast.tv.data.media

import com.vivicast.tv.iptv.m3u.M3uPlaylist
import com.vivicast.tv.iptv.xtream.XtreamCategory
import com.vivicast.tv.iptv.xtream.XtreamLiveStream
import com.vivicast.tv.iptv.xtream.XtreamSeriesInfo
import com.vivicast.tv.iptv.xtream.XtreamSeriesItem
import com.vivicast.tv.iptv.xtream.XtreamVodItem

interface CatalogImportRepository {
    /**
     * Imports a full M3U playlist, classifying each entry as Live / Movie / Series and persisting it
     * into the matching catalog structures. The returned counts describe the Live/category portion;
     * Movie/Series/Episode results are queried from the catalog directly.
     */
    suspend fun importM3uCatalog(providerId: String, playlist: M3uPlaylist): CatalogImportResult

    /** Retained for API compatibility; delegates to [importM3uCatalog] (now content-aware). */
    suspend fun importM3uLiveChannels(providerId: String, playlist: M3uPlaylist): CatalogImportResult

    suspend fun importXtreamCatalog(providerId: String, catalog: XtreamCatalog): XtreamCatalogImportResult
}

data class CatalogImportResult(
    val categoriesAdded: Int,
    val categoriesUpdated: Int,
    val categoriesRemoved: Int,
    val channelsAdded: Int,
    val channelsUpdated: Int,
    val channelsRemoved: Int,
    val skippedEntries: Int,
)

data class XtreamCatalog(
    val liveCategories: List<XtreamCategory> = emptyList(),
    val liveStreams: List<XtreamLiveStream> = emptyList(),
    val vodCategories: List<XtreamCategory> = emptyList(),
    val vodItems: List<XtreamVodItem> = emptyList(),
    val seriesCategories: List<XtreamCategory> = emptyList(),
    val seriesItems: List<XtreamSeriesItem> = emptyList(),
    val seriesInfos: List<XtreamSeriesInfo> = emptyList(),
)

data class XtreamCatalogImportResult(
    val liveCategories: ImportCount,
    val movieCategories: ImportCount,
    val seriesCategories: ImportCount,
    val channels: ImportCount,
    val movies: ImportCount,
    val series: ImportCount,
    val seasons: ImportCount,
    val episodes: ImportCount,
)

data class ImportCount(
    val added: Int,
    val updated: Int,
    val removed: Int,
)
