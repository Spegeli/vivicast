package com.vivicast.core.domain

import com.vivicast.core.model.EpgProgram

data class M3uImportResult(
    val playlistId: String,
    val channelCount: Int,
    val categoryCount: Int,
    val ignoredLineCount: Int,
    val catchupChannelCount: Int,
    val archiveWindowDaysMax: Int?
)

data class M3uVodImportResult(
    val playlistId: String,
    val movieCategoryCount: Int,
    val movieCount: Int,
    val seriesCategoryCount: Int,
    val seriesCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val classifiedMovieStreamCount: Int,
    val classifiedSeriesEpisodeCount: Int
)

data class EpgImportResult(
    val xmltvChannelCount: Int,
    val importedProgramCount: Int,
    val unmatchedProgramCount: Int
)

data class EpgNowNext(
    val now: EpgProgram?,
    val next: EpgProgram?
)

data class XtreamVodImportResult(
    val movieCategoryCount: Int,
    val movieCount: Int,
    val seriesCategoryCount: Int,
    val seriesCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val failedSeriesDetailCount: Int
)
