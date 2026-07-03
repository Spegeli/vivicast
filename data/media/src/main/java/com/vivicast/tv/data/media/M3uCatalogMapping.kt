package com.vivicast.tv.data.media

import com.vivicast.tv.core.cache.M3uStreamReference
import com.vivicast.tv.iptv.m3u.M3uChannel
import com.vivicast.tv.iptv.m3u.M3uContentClassification
import com.vivicast.tv.iptv.m3u.M3uContentClassifier
import com.vivicast.tv.iptv.m3u.M3uPlaylist
import com.vivicast.tv.iptv.xtream.XtreamEpisode
import com.vivicast.tv.iptv.xtream.XtreamSeriesInfo
import com.vivicast.tv.iptv.xtream.XtreamSeriesItem
import com.vivicast.tv.iptv.xtream.XtreamVodItem
import java.security.MessageDigest

/**
 * Buckets a parsed [M3uPlaylist] into Live / Movie / Series content using the [M3uContentClassifier],
 * and adapts the Movie/Series entries into the same Xtream-shaped DTOs the catalog importer already
 * knows how to persist — so [RoomCatalogImportRepository] can reuse its existing upsert/mapping logic
 * unchanged. Pure data mapping: no Room, no network. Stream URLs are carried out-of-band as
 * type-prefixed [M3uStreamReference]s (never embedded raw into ids/stableKeys) so a later playback
 * step can resolve Movie/Episode direct URLs the same way it already resolves Channel URLs.
 */
internal data class M3uClassifiedCatalog(
    val liveChannels: List<M3uChannel>,
    val vodItems: List<XtreamVodItem>,
    val seriesItems: List<XtreamSeriesItem>,
    val seriesInfos: List<XtreamSeriesInfo>,
    val streamReferences: Map<String, M3uStreamReference>,
)

internal fun classifyM3uPlaylist(
    playlist: M3uPlaylist,
    classifier: M3uContentClassifier,
): M3uClassifiedCatalog {
    val uniqueChannels = playlist.channels.associateBy { it.remoteId }.values
    val liveChannels = mutableListOf<M3uChannel>()
    val vodItems = mutableListOf<XtreamVodItem>()
    val streamReferences = linkedMapOf<String, M3uStreamReference>()
    val seriesAccumulators = linkedMapOf<String, M3uSeriesAccumulator>()

    uniqueChannels.forEach { channel ->
        when (val classification = classifier.classify(channel)) {
            M3uContentClassification.LiveChannel -> {
                liveChannels += channel
                // Channel refs keep the existing key (channel.remoteId) so live playback stays identical.
                streamReferences[channel.remoteId] = M3uStreamReference(
                    streamUrl = channel.streamUrl,
                    catchupMode = channel.catchupMode,
                    catchupSource = channel.catchupSource,
                )
            }

            M3uContentClassification.Movie -> {
                val remoteId = MOVIE_PREFIX + stableHash(channel.remoteId)
                vodItems += channel.toVodItem(remoteId)
                streamReferences[remoteId] = M3uStreamReference(streamUrl = channel.streamUrl)
            }

            is M3uContentClassification.SeriesEpisode -> {
                val info = classification.info
                val seriesTitle = info.seriesTitleRaw.ifBlank { channel.name }
                val seriesRemoteId = SERIES_PREFIX +
                    stableHash("${seriesTitle.normalizedKey()}|${channel.categoryName.normalizedKey()}")
                val episodeRemoteId = EPISODE_PREFIX + stableHash(channel.remoteId)
                val accumulator = seriesAccumulators.getOrPut(seriesRemoteId) {
                    M3uSeriesAccumulator(seriesRemoteId, seriesTitle, channel.categoryName, channel.logoUrl)
                }
                accumulator.episodes += XtreamEpisode(
                    remoteId = episodeRemoteId,
                    episodeNumber = info.episodeNumber,
                    seasonNumber = info.seasonNumber,
                    name = info.episodeTitle ?: channel.name,
                    plot = null,
                    thumbnailUrl = channel.logoUrl,
                    containerExtension = channel.streamUrl.streamFileExtension(),
                    durationSeconds = null,
                    airDate = null,
                )
                streamReferences[episodeRemoteId] = M3uStreamReference(streamUrl = channel.streamUrl)
            }
        }
    }

    return M3uClassifiedCatalog(
        liveChannels = liveChannels,
        vodItems = vodItems,
        seriesItems = seriesAccumulators.values.map { it.toSeriesItem() },
        seriesInfos = seriesAccumulators.values.map { it.toSeriesInfo() },
        streamReferences = streamReferences,
    )
}

private class M3uSeriesAccumulator(
    val remoteId: String,
    val name: String,
    val categoryName: String?,
    val posterUrl: String?,
    val episodes: MutableList<XtreamEpisode> = mutableListOf(),
) {
    fun toSeriesItem(): XtreamSeriesItem =
        XtreamSeriesItem(
            remoteId = remoteId,
            name = name,
            categoryRemoteId = categoryName,
            posterUrl = posterUrl,
            backdropUrl = null,
            rating = null,
            year = null,
            genre = null,
            director = null,
            cast = null,
            plot = null,
            addedAtSeconds = null,
        )

    // Seasons are left empty; buildSeasons derives them from the episodes' season numbers.
    fun toSeriesInfo(): XtreamSeriesInfo =
        XtreamSeriesInfo(seriesRemoteId = remoteId, seasons = emptyList(), episodes = episodes)
}

private fun M3uChannel.toVodItem(remoteId: String): XtreamVodItem =
    XtreamVodItem(
        remoteId = remoteId,
        name = tvgName ?: name,
        categoryRemoteId = categoryName,
        containerExtension = streamUrl.streamFileExtension(),
        posterUrl = logoUrl,
        backdropUrl = null,
        rating = null,
        year = null,
        genre = null,
        durationSeconds = null,
        director = null,
        cast = null,
        plot = null,
        trailerUrl = null,
        addedAtSeconds = null,
    )

private const val MOVIE_PREFIX = "movie:"
private const val SERIES_PREFIX = "series:"
private const val EPISODE_PREFIX = "episode:"

private fun String?.normalizedKey(): String =
    this?.trim()?.lowercase()?.replace(Regex("""\s+"""), " ")?.takeIf { it.isNotBlank() } ?: ""

private fun String.streamFileExtension(): String? {
    val path = trim().substringBefore('#').substringBefore('?')
    val fileName = path.substringAfterLast('/')
    if (!fileName.contains('.')) return null
    return fileName.substringAfterLast('.', "").lowercase().takeIf { it.isNotBlank() }
}

private fun stableHash(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.take(16).joinToString(separator = "") { byte -> "%02x".format(byte) }
}
