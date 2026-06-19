package com.vivicast.core.data

import androidx.room.withTransaction
import com.vivicast.core.database.ViviCastDatabase
import com.vivicast.core.database.asModel
import com.vivicast.core.database.entity.CategoryEntity
import com.vivicast.core.database.entity.ChannelEntity
import com.vivicast.core.database.entity.EpgSourceEntity
import com.vivicast.core.database.entity.EpgProgramEntity
import com.vivicast.core.database.entity.FavoriteChannelEntity
import com.vivicast.core.database.entity.MovieCategoryEntity
import com.vivicast.core.database.entity.MovieEntity
import com.vivicast.core.database.entity.PlaylistEntity
import com.vivicast.core.database.entity.RecentChannelEntity
import com.vivicast.core.database.entity.SeasonEntity
import com.vivicast.core.database.entity.SeriesCategoryEntity
import com.vivicast.core.database.entity.SeriesEntity
import com.vivicast.core.domain.EpgImportResult
import com.vivicast.core.domain.EpgNowNext
import com.vivicast.core.domain.EpgRepository
import com.vivicast.core.domain.LiveTvRepository
import com.vivicast.core.domain.M3uImportResult
import com.vivicast.core.domain.M3uVodImportResult
import com.vivicast.core.domain.ProviderRepository
import com.vivicast.core.database.entity.EpisodeEntity
import com.vivicast.core.epg.SimpleXmltvParser
import com.vivicast.core.model.Channel
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.EpgProgram
import com.vivicast.core.model.FavoriteChannel
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.PlaylistSourceType
import com.vivicast.core.model.RecentChannel
import com.vivicast.core.playlist.M3uParseReport
import com.vivicast.core.playlist.M3uPlaylistParser
import com.vivicast.core.playlist.ParsedM3uChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.Reader
import java.security.MessageDigest

class PlaylistImportUseCase(
    private val database: ViviCastDatabase,
    private val parser: M3uPlaylistParser = M3uPlaylistParser(),
    private val xmltvParser: SimpleXmltvParser = SimpleXmltvParser()
) : LiveTvRepository, ProviderRepository, EpgRepository {
    override fun observeChannels(): Flow<List<Channel>> {
        return database.channelDao().observeAllChannels()
            .map { channels -> channels.map { it.asModel() } }
    }

    override fun observeCategories(): Flow<List<ChannelCategory>> {
        return database.channelDao().observeAllCategories()
            .map { categories -> categories.map { it.asModel() } }
    }

    override fun observePlaylists(): Flow<List<Playlist>> {
        return database.playlistDao().observePlaylists()
            .map { playlists -> playlists.map { it.asModel() } }
    }

    override fun observeEpgSources(): Flow<List<EpgSource>> {
        return database.epgDao().observeSources()
            .map { sources -> sources.map { it.asModel() } }
    }

    override fun observeEpgProgramCount(): Flow<Int> {
        return database.epgDao().observeProgramCount()
    }

    override fun observeFavorites(): Flow<List<FavoriteChannel>> {
        return database.channelDao().observeFavorites()
            .map { favorites -> favorites.map { it.asModel() } }
    }

    override fun observeRecents(): Flow<List<RecentChannel>> {
        return database.channelDao().observeRecents()
            .map { recents -> recents.map { it.asModel() } }
    }

    override fun observeNowNext(channelId: String, nowUtcEpochMillis: Long): Flow<EpgNowNext> {
        return combine(
            database.epgDao().observeNowProgram(channelId, nowUtcEpochMillis),
            database.epgDao().observeNextProgram(channelId, nowUtcEpochMillis)
        ) { nowProgram, nextProgram ->
            EpgNowNext(
                now = nowProgram?.asModel(),
                next = nextProgram?.asModel()
            )
        }
    }

    override fun observeGuidePrograms(
        channelIds: List<String>,
        fromUtcEpochMillis: Long,
        toUtcEpochMillis: Long
    ): Flow<List<EpgProgram>> {
        if (channelIds.isEmpty()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        return database.epgDao()
            .observeProgramsForChannels(channelIds, fromUtcEpochMillis, toUtcEpochMillis)
            .map { programs -> programs.map { it.asModel() } }
    }

    fun parseM3u(content: String): M3uParseReport {
        return parser.parse(content.lineSequence())
    }

    override suspend fun renamePlaylist(playlistId: String, name: String) {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Provider name cannot be empty." }
        database.playlistDao().updatePlaylistName(
            playlistId = playlistId,
            name = trimmedName,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
    }

    override suspend fun updatePlaylistConnection(
        playlistId: String,
        sourceUri: String?,
        sourceUsername: String?,
        sourcePassword: String?
    ) {
        database.playlistDao().updatePlaylistConnection(
            playlistId = playlistId,
            sourceUri = sourceUri?.trim()?.ifBlank { null },
            sourceUsername = sourceUsername?.trim()?.ifBlank { null },
            sourcePassword = sourcePassword?.trim()?.ifBlank { null },
            updatedAtEpochMillis = System.currentTimeMillis()
        )
    }

    override suspend fun deletePlaylist(playlistId: String) {
        database.playlistDao().deletePlaylist(playlistId)
    }

    override suspend fun addFavorite(channelId: String) {
        database.channelDao().upsertFavorite(
            FavoriteChannelEntity(
                channelId = channelId,
                addedAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun removeFavorite(channelId: String) {
        database.channelDao().deleteFavorite(channelId)
    }

    override suspend fun markRecent(channelId: String) {
        database.channelDao().upsertRecent(
            RecentChannelEntity(
                channelId = channelId,
                watchedAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun upsertEpgSource(playlistId: String, name: String, sourceUri: String) {
        val trimmedUri = sourceUri.trim()
        require(trimmedUri.isNotBlank()) { "EPG URL cannot be empty." }
        database.epgDao().upsertSource(
            EpgSourceEntity(
                id = stableId("epg-source:$playlistId:$trimmedUri"),
                playlistId = playlistId,
                name = name.trim().takeIf { it.isNotBlank() } ?: "EPG",
                sourceUri = trimmedUri,
                lastImportedAtEpochMillis = null
            )
        )
    }

    override suspend fun upsertGlobalEpgSource(name: String, sourceUri: String) {
        val trimmedUri = sourceUri.trim()
        require(trimmedUri.isNotBlank()) { "EPG URL cannot be empty." }
        database.epgDao().upsertSource(
            EpgSourceEntity(
                id = stableId("epg-source:global:$trimmedUri"),
                playlistId = null,
                name = name.trim().takeIf { it.isNotBlank() } ?: (trimmedUri.substringBefore("?").substringBeforeLast("/").ifBlank { "Global EPG" }),
                sourceUri = trimmedUri,
                lastImportedAtEpochMillis = null
            )
        )
    }

    override suspend fun removeEpgSource(playlistId: String) {
        database.epgDao().deleteSourcesForPlaylist(playlistId)
    }

    override suspend fun removeEpgSourceById(sourceId: String) {
        database.epgDao().deleteSourceById(sourceId)
    }

    suspend fun importXmltv(
        playlistId: String,
        sourceName: String,
        sourceUri: String,
        timeOffsetMinutes: Int = 0,
        retentionDays: Int = 7,
        content: String
    ): EpgImportResult {
        return importXmltv(
            playlistId = playlistId,
            sourceName = sourceName,
            sourceUri = sourceUri,
            timeOffsetMinutes = timeOffsetMinutes,
            retentionDays = retentionDays,
            reader = content.reader()
        )
    }

    override suspend fun importXmltv(
        playlistId: String,
        sourceName: String,
        sourceUri: String,
        timeOffsetMinutes: Int,
        retentionDays: Int,
        reader: Reader
    ): EpgImportResult {
        val channels = database.channelDao().getChannels(playlistId)
        val channelIdByXmltvKey = buildBaseChannelMatchIndex(channels).toMutableMap()
        var importedProgramCount = 0
        var unmatchedProgramCount = 0

        val now = System.currentTimeMillis()
        database.withTransaction {
            database.epgDao().deleteProgramsForChannels(channels.map { it.id })
            database.epgDao().deleteProgramsEndingBefore(now - retentionDays.coerceAtLeast(0) * 24L * 60L * 60L * 1000L)
            database.epgDao().upsertSource(
                EpgSourceEntity(
                    id = stableId("epg-source:$playlistId:$sourceUri"),
                    playlistId = playlistId,
                    name = sourceName,
                    sourceUri = sourceUri,
                    lastImportedAtEpochMillis = now
                )
            )
        }

        val report = xmltvParser.parseStreaming(
            reader = reader,
            programBatchSize = 500,
            onChannel = { xmltvChannel ->
                val displayNameKey = xmltvChannel.displayName.matchKey()
                channelIdByXmltvKey[displayNameKey]?.let { channelId ->
                    channelIdByXmltvKey.putIfAbsent(xmltvChannel.xmltvId.matchKey(), channelId)
                }
            },
            onProgramBatch = { programs ->
                val entities = programs.mapNotNull { program ->
                    val channelId = channelIdByXmltvKey[program.xmltvChannelId.matchKey()]
                    if (channelId == null) {
                        unmatchedProgramCount += 1
                        return@mapNotNull null
                    }
                    importedProgramCount += 1
                    val offsetMillis = timeOffsetMinutes * 60L * 1000L
                    EpgProgramEntity(
                        id = stableId(
                            "epg:$channelId:${program.startUtcEpochMillis + offsetMillis}:${program.endUtcEpochMillis + offsetMillis}:${program.title}"
                        ),
                        channelId = channelId,
                        title = program.title,
                        description = program.description,
                        startUtcEpochMillis = program.startUtcEpochMillis + offsetMillis,
                        endUtcEpochMillis = program.endUtcEpochMillis + offsetMillis,
                        iconUrl = program.iconUrl
                    )
                }
                if (entities.isNotEmpty()) {
                    database.epgDao().upsertPrograms(entities)
                }
            }
        )

        return EpgImportResult(
            xmltvChannelCount = report.channelCount,
            importedProgramCount = importedProgramCount,
            unmatchedProgramCount = unmatchedProgramCount
        )
    }

    suspend fun importM3u(
        playlistId: String? = null,
        playlistName: String,
        sourceUri: String?,
        sourceType: PlaylistSourceType = PlaylistSourceType.M3U_URL,
        sourceUsername: String? = null,
        sourcePassword: String? = null,
        content: String
    ): M3uImportResult {
        val report = parseM3u(content)
        val now = System.currentTimeMillis()
        val resolvedPlaylistId = playlistId ?: stableId("playlist:${sourceUri ?: playlistName}")

        val categoryNames = report.channels
            .map { it.groupTitle?.takeIf { group -> group.isNotBlank() } ?: "Live TV" }
            .distinct()

        val categories = categoryNames.mapIndexed { index, name ->
            CategoryEntity(
                id = stableId("$resolvedPlaylistId:category:$name"),
                playlistId = resolvedPlaylistId,
                name = name,
                sortIndex = index
            )
        }

        val categoryByName = categories.associateBy { it.name }
        val channels = report.channels.mapIndexed { index, channel ->
            val categoryName = channel.groupTitle?.takeIf { it.isNotBlank() } ?: "Live TV"
            ChannelEntity(
                id = stableId("$resolvedPlaylistId:channel:${channel.tvgId}:${channel.name}:${channel.streamUrl}"),
                playlistId = resolvedPlaylistId,
                categoryId = categoryByName[categoryName]?.id,
                name = channel.name,
                streamUrl = channel.streamUrl,
                logoUrl = channel.logoUrl,
                tvgId = channel.tvgId,
                tvgName = channel.tvgName,
                catchupSupported = channel.catchupSupported,
                sortIndex = index
            )
        }

        database.withTransaction {
            database.playlistDao().deletePlaylist(resolvedPlaylistId)
            database.playlistDao().upsertPlaylist(
                PlaylistEntity(
                    id = resolvedPlaylistId,
                    name = playlistName,
                    sourceType = sourceType.name,
                    sourceUri = sourceUri,
                    sourceUsername = sourceUsername,
                    sourcePassword = sourcePassword,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
            )
            database.channelDao().upsertCategories(categories)
            database.channelDao().upsertChannels(channels)
        }

        return M3uImportResult(
            playlistId = resolvedPlaylistId,
            channelCount = channels.size,
            categoryCount = categories.size,
            ignoredLineCount = report.ignoredLineCount,
            catchupChannelCount = report.channels.count { it.catchupSupported },
            archiveWindowDaysMax = report.channels.mapNotNull { it.archiveWindowDays }.maxOrNull()
        )
    }

    suspend fun importM3uVod(
        playlistId: String,
        playlistName: String,
        sourceUri: String?,
        sourceType: PlaylistSourceType,
        sourceUsername: String? = null,
        sourcePassword: String? = null,
        content: String
    ): M3uVodImportResult {
        val report = parseM3u(content)
        val now = System.currentTimeMillis()
        val classification = classifyM3uVodChannels(report.channels)

        val movieCategories = classification.movieGroups.keys.toList().mapIndexed { index, name ->
            MovieCategoryEntity(
                id = stableId("$playlistId:movie-category:$name"),
                playlistId = playlistId,
                name = name,
                sortIndex = index
            )
        }
        val movieCategoryIdByName = movieCategories.associateBy({ it.name }, { it.id })
        val movies = classification.movies.mapIndexed { index, movie ->
            MovieEntity(
                id = stableId("$playlistId:movie:${movie.channel.name}:${movie.channel.streamUrl}"),
                playlistId = playlistId,
                categoryId = movieCategoryIdByName[movie.categoryName],
                title = movie.channel.name,
                streamUrl = movie.channel.streamUrl,
                coverUrl = movie.channel.logoUrl,
                plot = null,
                durationMinutes = null,
                releaseDate = null,
                addedAtEpochSeconds = null,
                sortIndex = index
            )
        }

        val seriesCategories = classification.seriesGroups.keys.toList().mapIndexed { index, name ->
            SeriesCategoryEntity(
                id = stableId("$playlistId:series-category:$name"),
                playlistId = playlistId,
                name = name,
                sortIndex = index
            )
        }
        val seriesCategoryIdByName = seriesCategories.associateBy({ it.name }, { it.id })
        val groupedSeries = classification.episodes
            .groupBy { it.seriesTitle.lowercase() }
            .toList()
            .sortedBy { (_, entries) -> entries.first().seriesTitle.lowercase() }

        val series = mutableListOf<SeriesEntity>()
        val seasons = mutableListOf<SeasonEntity>()
        val episodes = mutableListOf<EpisodeEntity>()

        groupedSeries.forEachIndexed { seriesIndex, (_, entries) ->
            val first = entries.first()
            val seriesId = stableId("$playlistId:series:${first.seriesTitle}")
            series += SeriesEntity(
                id = seriesId,
                playlistId = playlistId,
                categoryId = seriesCategoryIdByName[first.categoryName],
                title = first.seriesTitle,
                coverUrl = first.channel.logoUrl,
                plot = null,
                episodeRunTimeMinutes = null,
                releaseDate = null,
                addedAtEpochSeconds = null,
                sortIndex = seriesIndex
            )
            val groupedSeasons = entries.groupBy { it.seasonNumber }
                .toList()
                .sortedBy { (seasonNumber, _) -> seasonNumber }
            groupedSeasons.forEachIndexed { seasonIndex, (seasonNumber, seasonEntries) ->
                val seasonId = stableId("$seriesId:season:$seasonNumber")
                seasons += SeasonEntity(
                    id = seasonId,
                    playlistId = playlistId,
                    seriesId = seriesId,
                    title = "Season $seasonNumber",
                    seasonNumber = seasonNumber,
                    coverUrl = seasonEntries.first().channel.logoUrl,
                    plot = null,
                    sortIndex = seasonIndex
                )
                seasonEntries
                    .sortedBy { it.episodeNumber }
                    .forEachIndexed { episodeIndex, entry ->
                        episodes += EpisodeEntity(
                            id = stableId(
                                "$seasonId:episode:${entry.episodeNumber}:${entry.episodeTitle}:${entry.channel.streamUrl}"
                            ),
                            playlistId = playlistId,
                            seriesId = seriesId,
                            seasonId = seasonId,
                            title = entry.episodeTitle,
                            streamUrl = entry.channel.streamUrl,
                            episodeNumber = entry.episodeNumber,
                            plot = null,
                            durationMinutes = null,
                            coverUrl = entry.channel.logoUrl,
                            addedAtEpochSeconds = null,
                            sortIndex = episodeIndex
                        )
                    }
            }
        }

        database.withTransaction {
            database.playlistDao().upsertPlaylist(
                PlaylistEntity(
                    id = playlistId,
                    name = playlistName,
                    sourceType = sourceType.name,
                    sourceUri = sourceUri,
                    sourceUsername = sourceUsername,
                    sourcePassword = sourcePassword,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
            )
            database.vodDao().deleteEpisodesForPlaylist(playlistId)
            database.vodDao().deleteSeasonsForPlaylist(playlistId)
            database.vodDao().deleteSeriesForPlaylist(playlistId)
            database.vodDao().deleteSeriesCategoriesForPlaylist(playlistId)
            database.vodDao().deleteMoviesForPlaylist(playlistId)
            database.vodDao().deleteMovieCategoriesForPlaylist(playlistId)
            if (movieCategories.isNotEmpty()) database.vodDao().upsertMovieCategories(movieCategories)
            if (movies.isNotEmpty()) database.vodDao().upsertMovies(movies)
            if (seriesCategories.isNotEmpty()) database.vodDao().upsertSeriesCategories(seriesCategories)
            if (series.isNotEmpty()) database.vodDao().upsertSeries(series)
            if (seasons.isNotEmpty()) database.vodDao().upsertSeasons(seasons)
            if (episodes.isNotEmpty()) database.vodDao().upsertEpisodes(episodes)
        }

        return M3uVodImportResult(
            playlistId = playlistId,
            movieCategoryCount = movieCategories.size,
            movieCount = movies.size,
            seriesCategoryCount = seriesCategories.size,
            seriesCount = series.size,
            seasonCount = seasons.size,
            episodeCount = episodes.size,
            classifiedMovieStreamCount = classification.movies.size,
            classifiedSeriesEpisodeCount = classification.episodes.size
        )
    }

    private fun stableId(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(12).joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun buildBaseChannelMatchIndex(channels: List<ChannelEntity>): Map<String, String> {
        val index = mutableMapOf<String, String>()
        channels.forEach { channel ->
            listOfNotNull(channel.tvgId, channel.tvgName, channel.name).forEach { key ->
                index.putIfAbsent(key.matchKey(), channel.id)
            }
        }
        return index
    }

    private fun String.matchKey(): String = trim().lowercase()
}

private data class ClassifiedM3uVod(
    val movieGroups: Map<String, List<ParsedM3uChannel>>,
    val seriesGroups: Map<String, List<ParsedM3uChannel>>,
    val movies: List<MovieCandidate>,
    val episodes: List<SeriesEpisodeCandidate>
)

internal data class M3uVodClassificationPreview(
    val movieCategoryCount: Int,
    val movieCount: Int,
    val seriesCategoryCount: Int,
    val seriesCount: Int,
    val seasonCount: Int,
    val episodeCount: Int
)

private data class MovieCandidate(
    val channel: ParsedM3uChannel,
    val categoryName: String
)

private data class SeriesEpisodeCandidate(
    val channel: ParsedM3uChannel,
    val categoryName: String,
    val seriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String
)

private data class SeriesEpisodeNameMatch(
    val seriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String
)

private fun classifyM3uVodChannels(channels: List<ParsedM3uChannel>): ClassifiedM3uVod {
    val movieGroups = linkedMapOf<String, MutableList<ParsedM3uChannel>>()
    val seriesGroups = linkedMapOf<String, MutableList<SeriesEpisodeCandidate>>()

    channels.forEach { channel ->
        val categoryName = channel.groupTitle?.takeIf { it.isNotBlank() } ?: "On demand"
        val seriesMatch = channel.name.matchSeriesEpisode()
        if (seriesMatch != null && channel.looksLikeVodStream()) {
            seriesGroups.getOrPut(categoryName) { mutableListOf() } += SeriesEpisodeCandidate(
                channel = channel,
                categoryName = categoryName,
                seriesTitle = seriesMatch.seriesTitle,
                seasonNumber = seriesMatch.seasonNumber,
                episodeNumber = seriesMatch.episodeNumber,
                episodeTitle = seriesMatch.episodeTitle
            )
        } else if (channel.looksLikeMovieStream()) {
            movieGroups.getOrPut(categoryName) { mutableListOf() } += channel
        }
    }

    return ClassifiedM3uVod(
        movieGroups = movieGroups.mapValues { it.value.toList() },
        seriesGroups = seriesGroups.mapValues { entries -> entries.value.map { it.channel } },
        movies = movieGroups.flatMap { (categoryName, entries) ->
            entries.map { MovieCandidate(it, categoryName) }
        },
        episodes = seriesGroups.flatMap { (_, entries) -> entries }
    )
}

internal fun previewM3uVodClassification(channels: List<ParsedM3uChannel>): M3uVodClassificationPreview {
    val classification = classifyM3uVodChannels(channels)
    val seriesByTitle = classification.episodes.groupBy { it.seriesTitle.lowercase() }
    val seasonCount = seriesByTitle.values
        .sumOf { entries -> entries.map { it.seasonNumber }.distinct().size }
    return M3uVodClassificationPreview(
        movieCategoryCount = classification.movieGroups.size,
        movieCount = classification.movies.size,
        seriesCategoryCount = classification.seriesGroups.size,
        seriesCount = seriesByTitle.size,
        seasonCount = seasonCount,
        episodeCount = classification.episodes.size
    )
}

private fun ParsedM3uChannel.looksLikeVodStream(): Boolean {
    val normalizedUrl = streamUrl.lowercase()
    return normalizedUrl.contains("/movie/") ||
        normalizedUrl.contains("/series/") ||
        normalizedUrl.endsWith(".mp4") ||
        normalizedUrl.endsWith(".mkv") ||
        normalizedUrl.endsWith(".avi") ||
        normalizedUrl.endsWith(".mov") ||
        normalizedUrl.endsWith(".m4v") ||
        normalizedUrl.endsWith(".webm")
}

private fun ParsedM3uChannel.looksLikeMovieStream(): Boolean {
    if (!looksLikeVodStream()) return false
    return name.matchSeriesEpisode() == null
}

private fun String.matchSeriesEpisode(): SeriesEpisodeNameMatch? {
    val normalized = trim()
    val firstPattern = Regex("""(?i)^(.*?)[ ._\-]+s(\d{1,2})e(\d{1,2})(?:[ ._\-]+(.*))?$""")
    val secondPattern = Regex("""(?i)^(.*?)[ ._\-]+(\d{1,2})x(\d{1,2})(?:[ ._\-]+(.*))?$""")
    val match = firstPattern.matchEntire(normalized) ?: secondPattern.matchEntire(normalized) ?: return null
    val seriesTitle = match.groupValues[1].trim().trim('-', '.', '_')
    val seasonNumber = match.groupValues[2].toIntOrNull() ?: return null
    val episodeNumber = match.groupValues[3].toIntOrNull() ?: return null
    val suffix = match.groupValues.getOrNull(4).orEmpty().trim().trim('-', '.', '_')
    if (seriesTitle.isBlank()) return null
    return SeriesEpisodeNameMatch(
        seriesTitle = seriesTitle,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = suffix.ifBlank { "Episode $episodeNumber" }
    )
}
