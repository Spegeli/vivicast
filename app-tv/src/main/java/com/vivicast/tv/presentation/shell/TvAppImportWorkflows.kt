package com.vivicast.tv

import android.content.Context
import android.net.Uri
import com.vivicast.core.domain.EpgImportResult
import com.vivicast.core.domain.M3uImportResult
import com.vivicast.core.data.ViviCastDataGraph
import com.vivicast.core.model.Channel
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.Episode
import com.vivicast.core.model.EpisodePlaybackProgress
import com.vivicast.core.model.Movie
import com.vivicast.core.model.MoviePlaybackProgress
import com.vivicast.core.model.PlaybackContentType
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.StreamTrack
import com.vivicast.core.model.XtreamCredentials
import com.vivicast.core.model.XtreamOutputFormat as CoreXtreamOutputFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

suspend fun ViviCastTvController.importDemoPlaylist(): M3uImportResult {
        return withContext(Dispatchers.IO) {
            val liveImport = importUseCase.importM3u(
                playlistName = "ViviCast Demo",
                sourceUri = "demo://vivicast",
                sourceType = com.vivicast.core.model.PlaylistSourceType.M3U_URL,
                content = demoPlaylist
            )
            val vodImport = importUseCase.importM3uVod(
                playlistId = liveImport.playlistId,
                playlistName = "ViviCast Demo",
                sourceUri = "demo://vivicast",
                sourceType = com.vivicast.core.model.PlaylistSourceType.M3U_URL,
                content = demoVodPlaylist
            ).asProviderSummary()
            settingsRepository.edit()
                .putBoolean(providerVodEnabledKey(liveImport.playlistId), true)
                .commit()
            updateProviderSettings(liveImport.playlistId) { current ->
                current.copy(vodEnabled = true)
            }
            updateProviderSyncState(liveImport.playlistId) { current ->
                current.copy(
                    refreshing = false,
                    lastErrorMessage = null,
                    lastXtreamVodCategoryCount = vodImport.movieCategoryCount,
                    lastXtreamSeriesCategoryCount = vodImport.seriesCategoryCount,
                    lastXtreamMovieCount = vodImport.movieCount,
                    lastXtreamSeriesCount = vodImport.seriesCount,
                    lastXtreamSeasonCount = vodImport.seasonCount,
                    lastXtreamEpisodeCount = vodImport.episodeCount,
                    lastXtreamSeriesDetailFailureCount = vodImport.failedSeriesDetailCount
                )
            }
            liveImport
        }
    }

suspend fun ViviCastTvController.importM3uFromUrl(url: String): Result<M3uImportResult> {
        return withContext(Dispatchers.IO) {
            dataGraph.downloadText(url).mapCatching { content ->
                importUseCase.importM3u(
                    playlistName = url.playlistNameFromUrl(),
                    sourceUri = url,
                    sourceType = com.vivicast.core.model.PlaylistSourceType.M3U_URL,
                    content = content
                )
            }
        }
    }

suspend fun ViviCastTvController.importXtream(credentials: XtreamCredentials): Result<M3uImportResult> {
        return withContext(Dispatchers.IO) {
            dataGraph.importXtreamLive(
                playlistName = "",
                credentials = credentials
            )
        }
    }


suspend fun ViviCastTvController.refreshProvider(playlist: Playlist): Result<ProviderRefreshResult> {
        updateProviderSyncState(playlist.id) { current ->
            current.copy(refreshing = true, lastErrorMessage = null)
        }
        return withContext(Dispatchers.IO) {
            val refreshStartedAtMillis = System.currentTimeMillis()
            val providerSettings = mutableProviderSettings.value[playlist.id] ?: loadProviderSettings(playlist.id)
            if (!providerSettings.enabled) {
                val result = Result.failure<ProviderRefreshResult>(
                    IllegalStateException("Provider is disabled.")
                )
                applyRefreshResult(
                    playlist.id,
                    result,
                    refreshStartedAtMillis,
                    playlist.sourceType.name.replace('_', ' ')
                )
                return@withContext result
            }
            if (!providerSettings.isImportActive()) {
                val result = Result.failure<ProviderRefreshResult>(
                    IllegalStateException("Enable Live TV or Movies / Series for this provider before refreshing.")
                )
                applyRefreshResult(
                    playlist.id,
                    result,
                    refreshStartedAtMillis,
                    playlist.sourceType.name.replace('_', ' ')
                )
                return@withContext result
            }
            val result = when (playlist.sourceType) {
                com.vivicast.core.model.PlaylistSourceType.M3U_URL -> {
                    if (!providerSettings.liveTvEnabled && !providerSettings.vodEnabled) {
                        Result.failure(
                            IllegalStateException("Enable Live TV or Movies / Series for this provider before refreshing.")
                        )
                    } else {
                        val sourceUri = playlist.sourceUri?.trim().orEmpty()
                        if (sourceUri.isBlank()) {
                            Result.failure(IllegalStateException("Provider URL is missing."))
                        } else if (sourceUri.equals("demo://vivicast", ignoreCase = true)) {
                            runCatching {
                                val liveImport = if (providerSettings.liveTvEnabled) {
                                    importUseCase.importM3u(
                                        playlistId = playlist.id,
                                        playlistName = playlist.name,
                                        sourceUri = sourceUri,
                                        sourceType = playlist.sourceType,
                                        sourceUsername = playlist.sourceUsername,
                                        sourcePassword = playlist.sourcePassword,
                                        content = demoPlaylist
                                    )
                                } else {
                                    null
                                }
                                val vodImport = if (providerSettings.vodEnabled) {
                                    importUseCase.importM3uVod(
                                        playlistId = playlist.id,
                                        playlistName = playlist.name,
                                        sourceUri = sourceUri,
                                        sourceType = playlist.sourceType,
                                        sourceUsername = playlist.sourceUsername,
                                        sourcePassword = playlist.sourcePassword,
                                        content = demoVodPlaylist
                                    ).asProviderSummary()
                                } else {
                                    null
                                }
                                ProviderRefreshResult(
                                    liveImport = liveImport,
                                    vodImport = vodImport
                                )
                            }
                        } else {
                            dataGraph.downloadText(
                                sourceUri,
                                headers = providerRequestHeaders(playlist.id)
                            ).mapCatching { content ->
                                val liveImport = if (providerSettings.liveTvEnabled) {
                                    importUseCase.importM3u(
                                        playlistId = playlist.id,
                                        playlistName = playlist.name,
                                        sourceUri = sourceUri,
                                        sourceType = playlist.sourceType,
                                        sourceUsername = playlist.sourceUsername,
                                        sourcePassword = playlist.sourcePassword,
                                        content = content
                                    )
                                } else {
                                    null
                                }
                                val vodImport = if (providerSettings.vodEnabled) {
                                    importUseCase.importM3uVod(
                                        playlistId = playlist.id,
                                        playlistName = playlist.name,
                                        sourceUri = sourceUri,
                                        sourceType = playlist.sourceType,
                                        sourceUsername = playlist.sourceUsername,
                                        sourcePassword = playlist.sourcePassword,
                                        content = content
                                    ).asProviderSummary()
                                } else {
                                    null
                                }
                                ProviderRefreshResult(
                                    liveImport = liveImport,
                                    vodImport = vodImport
                                )
                            }
                        }
                    }
                }

                com.vivicast.core.model.PlaylistSourceType.XTREAM_CODES -> {
                    val baseUrl = playlist.sourceUri?.trim().orEmpty()
                    val username = playlist.sourceUsername?.trim().orEmpty()
                    val password = playlist.sourcePassword?.trim().orEmpty()
                    if (baseUrl.isBlank() || username.isBlank() || password.isBlank()) {
                        Result.failure(
                            IllegalStateException("Xtream connection details are incomplete.")
                        )
                    } else {
                        val credentials = XtreamCredentials(
                            baseUrl = baseUrl,
                            username = username,
                            password = password,
                            userAgent = providerUserAgent(playlist.id),
                            outputFormat = providerXtreamOutputFormat(playlist.id).asCoreModel()
                        )
                        val xtreamApiScope = providerXtreamApiScope(playlist.id)
                        runCatching {
                            val liveImport = if (providerSettings.liveTvEnabled) {
                                dataGraph.importXtreamLive(
                                    playlistId = playlist.id,
                                    playlistName = playlist.name,
                                    credentials = credentials
                                ).getOrThrow()
                            } else {
                                null
                            }
                            val vodImport = if (providerSettings.vodEnabled && xtreamApiScope.includesVodMetadata) {
                                xtreamVodImportUseCase.importPlaylist(
                                    playlistId = playlist.id,
                                    credentials = credentials
                                ).asProviderSummary()
                            } else {
                                null
                            }
                            ProviderRefreshResult(
                                liveImport = liveImport,
                                vodImport = vodImport
                            )
                        }
                    }
                }

                com.vivicast.core.model.PlaylistSourceType.M3U_FILE -> {
                    if (!providerSettings.liveTvEnabled && !providerSettings.vodEnabled) {
                        Result.failure(
                            IllegalStateException("Enable Live TV or Movies / Series for this provider before refreshing.")
                        )
                    } else {
                        val sourceUri = playlist.sourceUri?.trim().orEmpty()
                        if (sourceUri.isBlank()) {
                            Result.failure(IllegalStateException("Provider file source is missing."))
                        } else {
                            readTextFromLocalPlaylistSource(sourceUri).fold(
                                onSuccess = { content ->
                                    runCatching {
                                        val liveImport = if (providerSettings.liveTvEnabled) {
                                            importUseCase.importM3u(
                                                playlistId = playlist.id,
                                                playlistName = playlist.name,
                                                sourceUri = sourceUri,
                                                sourceType = playlist.sourceType,
                                                sourceUsername = playlist.sourceUsername,
                                                sourcePassword = playlist.sourcePassword,
                                                content = content
                                            )
                                        } else {
                                            null
                                        }
                                        val vodImport = if (providerSettings.vodEnabled) {
                                            importUseCase.importM3uVod(
                                                playlistId = playlist.id,
                                                playlistName = playlist.name,
                                                sourceUri = sourceUri,
                                                sourceType = playlist.sourceType,
                                                sourceUsername = playlist.sourceUsername,
                                                sourcePassword = playlist.sourcePassword,
                                                content = content
                                            ).asProviderSummary()
                                        } else {
                                            null
                                        }
                                        ProviderRefreshResult(
                                            liveImport = liveImport,
                                            vodImport = vodImport
                                        )
                                    }
                                },
                                onFailure = { error -> Result.failure(error) }
                            )
                        }
                    }
                }
            }
            applyRefreshResult(
                playlist.id,
                result,
                refreshStartedAtMillis,
                playlist.sourceType.name.replace('_', ' ')
            )
            result
        }
    }

suspend fun ViviCastTvController.importEpg(playlist: Playlist, epgSource: EpgSource): Result<EpgImportResult> {
        return withContext(Dispatchers.IO) {
            val startedAtMillis = System.currentTimeMillis()
            val epgSettings = mutableAppSettings.value
            val result = dataGraph.readTextStream(
                epgSource.sourceUri,
                headers = providerRequestHeaders(playlist.id)
            ) { reader ->
                importUseCase.importXmltv(
                    playlistId = playlist.id,
                    sourceName = epgSource.name,
                    sourceUri = epgSource.sourceUri,
                    timeOffsetMinutes = epgSettings.epgTimeOffset.minutes,
                    retentionDays = epgSettings.epgRetentionDays.days,
                    reader = reader
                )
            }
            val finishedAtMillis = System.currentTimeMillis()
            val durationMillis = (finishedAtMillis - startedAtMillis).coerceAtLeast(0L)
            updateProviderSyncState(playlist.id) { current ->
                result.fold(
                    onSuccess = { importResult ->
                        current.copy(
                            lastEpgAttemptAtEpochMillis = finishedAtMillis,
                            lastEpgImportedAtEpochMillis = finishedAtMillis,
                            lastEpgImportDurationMillis = durationMillis,
                            lastEpgXmltvChannelCount = importResult.xmltvChannelCount,
                            lastEpgImportedProgramCount = importResult.importedProgramCount,
                            lastEpgUnmatchedProgramCount = importResult.unmatchedProgramCount,
                            lastEpgErrorMessage = null,
                            lastEpgErrorAtEpochMillis = null,
                            epgSuccessCount = current.epgSuccessCount + 1,
                            consecutiveEpgFailureCount = 0
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            lastEpgAttemptAtEpochMillis = finishedAtMillis,
                            lastEpgImportDurationMillis = durationMillis,
                            lastEpgErrorMessage = error.message ?: error::class.simpleName,
                            lastEpgErrorAtEpochMillis = finishedAtMillis,
                            epgFailureCount = current.epgFailureCount + 1,
                            consecutiveEpgFailureCount = current.consecutiveEpgFailureCount + 1
                        )
                    }
                )
            }
            result
        }
    }

