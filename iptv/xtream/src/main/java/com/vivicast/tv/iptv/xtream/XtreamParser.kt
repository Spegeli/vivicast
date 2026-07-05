package com.vivicast.tv.iptv.xtream

import org.json.JSONArray
import org.json.JSONObject

interface XtreamParser {
    fun parseUserInfo(json: String): XtreamUserInfo

    fun parseCategories(json: String): List<XtreamCategory>

    fun parseLiveStreams(json: String): List<XtreamLiveStream>

    fun parseVodItems(json: String): List<XtreamVodItem>

    fun parseSeries(json: String): List<XtreamSeriesItem>

    fun parseSeriesInfo(seriesRemoteId: String, json: String): XtreamSeriesInfo
}

class DefaultXtreamParser : XtreamParser {
    override fun parseUserInfo(json: String): XtreamUserInfo {
        val userInfo = JSONObject(json).optJSONObject("user_info")
        return XtreamUserInfo(
            authenticated = userInfo?.int("auth") == 1,
            expiresAtSeconds = userInfo?.long("exp_date"),
            maxConnections = userInfo?.int("max_connections"),
        )
    }

    override fun parseCategories(json: String): List<XtreamCategory> =
        JSONArray(json).objects().mapNotNull { item ->
            val id = item.string("category_id") ?: return@mapNotNull null
            val name = item.string("category_name") ?: return@mapNotNull null
            XtreamCategory(remoteId = id, name = name)
        }

    override fun parseLiveStreams(json: String): List<XtreamLiveStream> =
        JSONArray(json).objects().mapNotNull { item ->
            val id = item.string("stream_id") ?: return@mapNotNull null
            val name = item.string("name") ?: return@mapNotNull null
            val catchupDays = item.int("tv_archive_duration")?.coerceAtLeast(0) ?: 0
            val catchupFlag = item.string("tv_archive") ?: item.string("catchup")
            XtreamLiveStream(
                remoteId = id,
                name = name,
                categoryRemoteId = item.string("category_id"),
                channelNumber = item.string("num"),
                logoUrl = item.string("stream_icon"),
                epgChannelId = item.string("epg_channel_id"),
                isCatchupAvailable = catchupDays > 0 || catchupFlag == "1" || catchupFlag.equals("true", ignoreCase = true),
                catchupDays = catchupDays,
            )
        }

    override fun parseVodItems(json: String): List<XtreamVodItem> =
        JSONArray(json).objects().mapNotNull { item ->
            val id = item.string("stream_id") ?: return@mapNotNull null
            val name = item.string("name") ?: return@mapNotNull null
            XtreamVodItem(
                remoteId = id,
                name = name,
                categoryRemoteId = item.string("category_id"),
                containerExtension = item.string("container_extension"),
                posterUrl = item.string("stream_icon") ?: item.string("cover"),
                backdropUrl = item.firstBackdrop(),
                rating = item.string("rating") ?: item.string("rating_5based"),
                year = item.string("year") ?: item.string("releaseDate")?.take(4),
                genre = item.string("genre"),
                durationSeconds = item.long("duration_secs"),
                director = item.string("director"),
                cast = item.string("cast"),
                plot = item.string("plot"),
                trailerUrl = item.string("youtube_trailer"),
                addedAtSeconds = item.long("added"),
            )
        }

    override fun parseSeries(json: String): List<XtreamSeriesItem> =
        JSONArray(json).objects().mapNotNull { item ->
            val id = item.string("series_id") ?: return@mapNotNull null
            val name = item.string("name") ?: return@mapNotNull null
            XtreamSeriesItem(
                remoteId = id,
                name = name,
                categoryRemoteId = item.string("category_id"),
                posterUrl = item.string("cover"),
                backdropUrl = item.firstBackdrop(),
                rating = item.string("rating") ?: item.string("rating_5based"),
                year = item.string("year") ?: item.string("releaseDate")?.take(4),
                genre = item.string("genre"),
                director = item.string("director"),
                cast = item.string("cast"),
                plot = item.string("plot"),
                addedAtSeconds = item.long("last_modified") ?: item.long("added"),
            )
        }

    override fun parseSeriesInfo(seriesRemoteId: String, json: String): XtreamSeriesInfo {
        val root = JSONObject(json)
        val seasons = root.optJSONArray("seasons")
            ?.objects()
            ?.mapNotNull { item ->
                val seasonNumber = item.int("season_number") ?: item.int("season_num") ?: return@mapNotNull null
                XtreamSeason(
                    seasonNumber = seasonNumber,
                    name = item.string("name") ?: "Season $seasonNumber",
                    posterUrl = item.string("cover"),
                )
            }
            .orEmpty()
        return XtreamSeriesInfo(
            seriesRemoteId = seriesRemoteId,
            seasons = seasons,
            episodes = root.optJSONObject("episodes").toEpisodes(),
        )
    }

    private fun JSONObject?.toEpisodes(): List<XtreamEpisode> {
        if (this == null) return emptyList()
        val result = mutableListOf<XtreamEpisode>()
        keys().forEach { seasonKey ->
            val seasonNumber = seasonKey.toIntOrNull() ?: return@forEach
            optJSONArray(seasonKey)?.objects()?.mapNotNullTo(result) { item ->
                val id = item.string("id") ?: item.string("episode_id") ?: return@mapNotNullTo null
                val info = item.optJSONObject("info")
                XtreamEpisode(
                    remoteId = id,
                    episodeNumber = item.int("episode_num") ?: item.int("episode_number") ?: result.size + 1,
                    seasonNumber = item.int("season") ?: seasonNumber,
                    name = item.string("title") ?: item.string("name") ?: "Episode ${result.size + 1}",
                    plot = info?.string("plot") ?: item.string("plot"),
                    thumbnailUrl = info?.string("movie_image") ?: item.string("thumbnail"),
                    containerExtension = item.string("container_extension"),
                    durationSeconds = info?.long("duration_secs") ?: item.long("duration_secs"),
                    airDate = info?.string("releasedate") ?: item.string("air_date"),
                )
            }
        }
        return result
    }

    private companion object {
        fun JSONArray.objects(): List<JSONObject> =
            buildList {
                for (index in 0 until length()) {
                    optJSONObject(index)?.let(::add)
                }
            }

        fun JSONObject.string(name: String): String? =
            optString(name, "").trim().takeIf { it.isNotBlank() && it != "null" }

        fun JSONObject.int(name: String): Int? =
            string(name)?.toIntOrNull()

        fun JSONObject.long(name: String): Long? =
            string(name)?.toLongOrNull()

        fun JSONObject.firstBackdrop(): String? {
            val backdrop = opt("backdrop_path")
            return when (backdrop) {
                is JSONArray -> (0 until backdrop.length())
                    .firstNotNullOfOrNull { index -> backdrop.optString(index).trim().takeIf { it.isNotBlank() } }
                is String -> backdrop.trim().takeIf { it.isNotBlank() && it != "null" }
                else -> null
            }
        }
    }
}
