package com.vivicast.tv

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.vivicast.core.domain.M3uImportResult
import com.vivicast.core.domain.M3uVodImportResult
import com.vivicast.core.domain.XtreamVodImportResult
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgProgram
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.XtreamCredentials
import com.vivicast.core.model.XtreamOutputFormat as CoreXtreamOutputFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
data class AppSettings(
    val startOnBoot: Boolean = false,
    val resumeLastChannelOnStart: Boolean = true,
    val confirmExitOnBack: Boolean = true,
    val autoHidePrimaryNavigation: Boolean = true,
    val autoCollapseProviderFilters: Boolean = true,
    val showChannelNumbers: Boolean = true,
    val showChannelMetadata: Boolean = true,
    val showSourceLabels: Boolean = true,
    val compactChannelRows: Boolean = false,
    val compactProviderRows: Boolean = false,
    val keepScreenAwakeDuringPlayback: Boolean = true,
    val showPlaybackStatusBadge: Boolean = true,
    val showPlaybackClock: Boolean = true,
    val showPlaybackRecents: Boolean = true,
    val showPlaybackGuideAction: Boolean = true,
    val showPlaybackProgressBar: Boolean = true,
    val showPlaybackTrackActions: Boolean = true,
    val showPlaybackFavoriteAction: Boolean = true,
    val showPlaybackProgrammeDescription: Boolean = true,
    val showPlaybackNowNextPanel: Boolean = true,
    val showFavoriteFeedbackBanner: Boolean = true,
    val recoverablePlaybackRetryDelay: PlaybackRetryDelay = PlaybackRetryDelay.Millis900,
    val retryRecoverablePlaybackErrorsOnce: Boolean = true,
    val leaveLiveTvPlaybackBehavior: LeaveLiveTvPlaybackBehavior = LeaveLiveTvPlaybackBehavior.KeepPlaying,
    val openProviderFiltersWhenEnteringLiveTv: Boolean = true,
    val backClearsLiveTvFilters: Boolean = true,
    val backHidesLiveTvFiltersFirst: Boolean = false,
    val guideResetsToNowOnOpen: Boolean = true,
    val guideStartsWithCurrentChannel: Boolean = true,
    val guideUsesPlayingChannelProviderOnOpen: Boolean = false,
    val guideClearsCategoryOnOpen: Boolean = false,
    val backClearsProviderBeforeCategory: Boolean = false,
    val backClosesNavigationFirst: Boolean = true,
    val backClearsGuideWindowFirst: Boolean = true,
    val backClearsGuideProgrammeFirst: Boolean = true,
    val backLeavesSettingsToLiveTv: Boolean = true,
    val backClearsGuideFilters: Boolean = true,
    val preferProviderFiltersBeforeNavigation: Boolean = true,
    val epgTimeOffset: EpgTimeOffset = EpgTimeOffset.Hours0,
    val epgRefreshOnAppStart: Boolean = true,
    val epgUpdateInterval: EpgUpdateInterval = EpgUpdateInterval.Hours12,
    val epgRefreshDuringSession: Boolean = true,
    val epgRetentionDays: EpgRetentionDays = EpgRetentionDays.Days7
)

enum class PlaybackRetryDelay(val delayMillis: Long, val label: String) {
    Millis900(900L, "0.9s"),
    Seconds2(2_000L, "2s"),
    Seconds5(5_000L, "5s");

    fun next(): PlaybackRetryDelay = when (this) {
        Millis900 -> Seconds2
        Seconds2 -> Seconds5
        Seconds5 -> Millis900
    }
}

enum class LeaveLiveTvPlaybackBehavior(val label: String) {
    KeepPlaying("Keep playing"),
    PausePlayback("Pause playback"),
    StopPlayback("Stop playback");

    fun next(): LeaveLiveTvPlaybackBehavior = when (this) {
        KeepPlaying -> PausePlayback
        PausePlayback -> StopPlayback
        StopPlayback -> KeepPlaying
    }
}

data class ProviderUiSettings(
    val enabled: Boolean = true,
    val liveTvEnabled: Boolean = true,
    val vodEnabled: Boolean = false,
    val logoPriority: LogoPriority = LogoPriority.Playlist,
    val hiddenCategoryIds: Set<String> = emptySet(),
    val categoryOrderIds: List<String> = emptyList(),
    val userAgent: String = "",
    val xtreamOutputFormat: XtreamOutputFormat = XtreamOutputFormat.Ts,
    val xtreamApiScope: XtreamApiScope = XtreamApiScope.LiveAndVodMetadata,
    val refreshIntervalHours: RefreshIntervalHours = RefreshIntervalHours.Hours24,
    val refreshOnAppStart: Boolean = true,
    val refreshOnPlaylistChange: Boolean = true
) {
    fun isLiveTvActive(): Boolean = enabled && liveTvEnabled

    fun isImportActive(): Boolean = enabled && (liveTvEnabled || vodEnabled)

    fun contentSummary(): String {
        val live = if (liveTvEnabled) "Live TV on" else "Live TV off"
        val vod = if (vodEnabled) "Movies/Series on" else "Movies/Series off"
        return "$live / $vod"
    }

    fun importStatusLabel(): String {
        return when {
            !enabled -> "Disabled"
            liveTvEnabled -> "Live TV import active"
            vodEnabled -> "Movies / Series import active"
            else -> "No active import scope"
        }
    }
}

data class ProviderRefreshResult(
    val liveImport: M3uImportResult? = null,
    val vodImport: ProviderVodImportSummary? = null
) {
    fun summary(): String {
        val parts = buildList {
            liveImport?.let { add("${it.channelCount} channels in ${it.categoryCount} groups") }
            vodImport?.let {
                add("${it.movieCount} movies")
                add("${it.seriesCount} series")
                if (it.episodeCount > 0) {
                    add("${it.episodeCount} episodes")
                }
                if (it.failedSeriesDetailCount > 0) {
                    add("${it.failedSeriesDetailCount} series detail failures")
                }
            }
        }
        return parts.joinToString(" / ").ifBlank { "No imported content" }
    }
}

data class ProviderVodImportSummary(
    val movieCategoryCount: Int,
    val movieCount: Int,
    val seriesCategoryCount: Int,
    val seriesCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val failedSeriesDetailCount: Int = 0
)

fun XtreamVodImportResult.asProviderSummary(): ProviderVodImportSummary = ProviderVodImportSummary(
    movieCategoryCount = movieCategoryCount,
    movieCount = movieCount,
    seriesCategoryCount = seriesCategoryCount,
    seriesCount = seriesCount,
    seasonCount = seasonCount,
    episodeCount = episodeCount,
    failedSeriesDetailCount = failedSeriesDetailCount
)

fun M3uVodImportResult.asProviderSummary(): ProviderVodImportSummary = ProviderVodImportSummary(
    movieCategoryCount = movieCategoryCount,
    movieCount = movieCount,
    seriesCategoryCount = seriesCategoryCount,
    seriesCount = seriesCount,
    seasonCount = seasonCount,
    episodeCount = episodeCount,
    failedSeriesDetailCount = 0
)

data class ProviderSyncState(
    val lastSyncedAtEpochMillis: Long? = null,
    val lastErrorMessage: String? = null,
    val lastRefreshAttemptAtEpochMillis: Long? = null,
    val lastErrorAtEpochMillis: Long? = null,
    val lastRefreshDurationMillis: Long? = null,
    val lastRefreshSourceLabel: String? = null,
    val lastImportedChannelCount: Int? = null,
    val lastImportedCategoryCount: Int? = null,
    val lastIgnoredLineCount: Int? = null,
    val lastCatchupChannelCount: Int? = null,
    val lastArchiveWindowDays: Int? = null,
    val lastXtreamVodCategoryCount: Int? = null,
    val lastXtreamSeriesCategoryCount: Int? = null,
    val lastXtreamMovieCount: Int? = null,
    val lastXtreamSeriesCount: Int? = null,
    val lastXtreamSeasonCount: Int? = null,
    val lastXtreamEpisodeCount: Int? = null,
    val lastXtreamSeriesDetailFailureCount: Int? = null,
    val refreshSuccessCount: Int = 0,
    val refreshFailureCount: Int = 0,
    val consecutiveRefreshFailureCount: Int = 0,
    val lastEpgAttemptAtEpochMillis: Long? = null,
    val lastEpgImportedAtEpochMillis: Long? = null,
    val lastEpgImportDurationMillis: Long? = null,
    val lastEpgXmltvChannelCount: Int? = null,
    val lastEpgImportedProgramCount: Int? = null,
    val lastEpgUnmatchedProgramCount: Int? = null,
    val lastEpgErrorMessage: String? = null,
    val lastEpgErrorAtEpochMillis: Long? = null,
    val epgSuccessCount: Int = 0,
    val epgFailureCount: Int = 0,
    val consecutiveEpgFailureCount: Int = 0,
    val refreshing: Boolean = false
) {
    fun summary(): String {
        return when {
            refreshing -> "Refreshing"
            lastErrorMessage != null -> "Error"
            lastSyncedAtEpochMillis != null -> "Synced"
            else -> "Manual refresh pending"
        }
    }

    fun refreshAttemptCount(): Int = refreshSuccessCount + refreshFailureCount

    fun epgAttemptCount(): Int = epgSuccessCount + epgFailureCount

    fun refreshSuccessRate(): Int? {
        val attempts = refreshAttemptCount()
        if (attempts <= 0) return null
        return ((refreshSuccessCount * 100.0) / attempts).toInt()
    }

    fun epgSuccessRate(): Int? {
        val attempts = epgAttemptCount()
        if (attempts <= 0) return null
        return ((epgSuccessCount * 100.0) / attempts).toInt()
    }

    fun refreshHealthSummary(): String {
        val attempts = refreshAttemptCount()
        val rate = refreshSuccessRate()
        return buildString {
            append(rate?.let { "$it% ok" } ?: "No attempts")
            if (attempts > 0) {
                append(" / ")
                append(attempts)
                append(" attempts")
            }
            lastRefreshDurationMillis?.let {
                append(" / ")
                append(it.asDurationLabel())
            }
        }
    }

    fun epgHealthSummary(): String {
        val attempts = epgAttemptCount()
        val rate = epgSuccessRate()
        return buildString {
            append(rate?.let { "$it% ok" } ?: "No attempts")
            if (attempts > 0) {
                append(" / ")
                append(attempts)
                append(" attempts")
            }
            lastEpgImportDurationMillis?.let {
                append(" / ")
                append(it.asDurationLabel())
            }
        }
    }

    fun latestActivityAt(): Long? {
        return listOfNotNull(
            lastSyncedAtEpochMillis,
            lastRefreshAttemptAtEpochMillis,
            lastErrorAtEpochMillis,
            lastEpgImportedAtEpochMillis,
            lastEpgAttemptAtEpochMillis,
            lastEpgErrorAtEpochMillis
        ).maxOrNull()
    }

    fun latestActivityLabel(): String {
        val latest = latestActivityAt() ?: return "idle"
        return when (latest) {
            lastErrorAtEpochMillis -> "refresh error"
            lastEpgErrorAtEpochMillis -> "epg error"
            lastEpgImportedAtEpochMillis -> "epg success"
            lastSyncedAtEpochMillis -> "refresh success"
            lastEpgAttemptAtEpochMillis -> "epg attempt"
            lastRefreshAttemptAtEpochMillis -> "refresh attempt"
            else -> "activity"
        }
    }

    fun latestActivitySummary(): String {
        val latest = latestActivityAt() ?: return "No activity yet"
        return "${latestActivityLabel()} / ${latest.asShortDateTime()}"
    }

    fun capabilitySummary(): String {
        val catchup = lastCatchupChannelCount ?: 0
        val archiveDays = lastArchiveWindowDays
        val vod = lastXtreamVodCategoryCount
        val series = lastXtreamSeriesCategoryCount
        val movies = lastXtreamMovieCount
        val seriesItems = lastXtreamSeriesCount
        val episodes = lastXtreamEpisodeCount
        return buildString {
            append(if (catchup > 0) "$catchup catch-up" else "No catch-up seen")
            archiveDays?.let {
                append(" / ")
                append("$it archive days")
            }
            if (vod != null || series != null) {
                append(" / ")
                append("${vod ?: 0} movie cats / ${series ?: 0} series cats")
            }
            if (movies != null || seriesItems != null || episodes != null) {
                append(" / ")
                append("${movies ?: 0} movies / ${seriesItems ?: 0} series / ${episodes ?: 0} episodes")
            }
        }
    }
}

data class VodLibraryStatus(
    val enabledPlaylistIds: Set<String>,
    val refreshing: Boolean,
    val lastErrorMessage: String?
)

fun computeVodLibraryStatus(
    playlists: List<Playlist>,
    providerSettings: Map<String, ProviderUiSettings>,
    providerSyncStates: Map<String, ProviderSyncState>
): VodLibraryStatus {
    val enabledPlaylistIds = playlists
        .asSequence()
        .filter { playlist ->
            val settings = providerSettings[playlist.id]
            settings?.enabled != false && settings?.vodEnabled == true
        }
        .map { it.id }
        .toSet()
    val relevantStates = enabledPlaylistIds.mapNotNull(providerSyncStates::get)
    val refreshing = relevantStates.any { it.refreshing }
    val lastErrorMessage = relevantStates
        .filter { !it.lastErrorMessage.isNullOrBlank() }
        .maxByOrNull { it.lastErrorAtEpochMillis ?: Long.MIN_VALUE }
        ?.lastErrorMessage
    return VodLibraryStatus(
        enabledPlaylistIds = enabledPlaylistIds,
        refreshing = refreshing,
        lastErrorMessage = lastErrorMessage
    )
}

fun movieLibraryEmptyState(
    hasProviders: Boolean,
    status: VodLibraryStatus
): Pair<String, String> {
    return when {
        !hasProviders -> "No provider configured" to "Add a provider in Settings to import movies."
        status.enabledPlaylistIds.isEmpty() -> "Movies disabled" to "Enable Movies for at least one provider in Settings."
        status.refreshing -> "Syncing movies..." to "Provider import is running. Movies will appear here when sync completes."
        !status.lastErrorMessage.isNullOrBlank() -> "Movies import failed" to "Last import error: ${status.lastErrorMessage}"
        else -> "No movies imported yet" to "Refresh a provider to import movies."
    }
}

fun seriesLibraryEmptyState(
    hasProviders: Boolean,
    status: VodLibraryStatus
): Pair<String, String> {
    return when {
        !hasProviders -> "No provider configured" to "Add a provider in Settings to import series."
        status.enabledPlaylistIds.isEmpty() -> "Series disabled" to "Enable Series for at least one provider in Settings."
        status.refreshing -> "Syncing series..." to "Provider import is running. Series will appear here when sync completes."
        !status.lastErrorMessage.isNullOrBlank() -> "Series import failed" to "Last import error: ${status.lastErrorMessage}"
        else -> "No series imported yet" to "Refresh a provider to import series."
    }
}

enum class XtreamOutputFormat(val label: String) {
    Ts("MPEG-TS (.ts)"),
    Hls("HLS (.m3u8)");

    fun next(): XtreamOutputFormat = when (this) {
        Ts -> Hls
        Hls -> Ts
    }

    fun asCoreModel(): CoreXtreamOutputFormat = when (this) {
        Ts -> CoreXtreamOutputFormat.Ts
        Hls -> CoreXtreamOutputFormat.Hls
    }
}

enum class XtreamApiScope(val label: String, val includesVodMetadata: Boolean) {
    LiveOnly("Live only", false),
    LiveAndVodMetadata("Live + VOD metadata", true);

    fun next(): XtreamApiScope = when (this) {
        LiveOnly -> LiveAndVodMetadata
        LiveAndVodMetadata -> LiveOnly
    }
}

enum class EpgTimeOffset(val minutes: Int, val label: String) {
    HoursMinus3(-180, "-3 hours"),
    HoursMinus2(-120, "-2 hours"),
    HoursMinus1(-60, "-1 hour"),
    Hours0(0, "No offset"),
    HoursPlus1(60, "+1 hour"),
    HoursPlus2(120, "+2 hours"),
    HoursPlus3(180, "+3 hours");

    fun next(): EpgTimeOffset = when (this) {
        HoursMinus3 -> HoursMinus2
        HoursMinus2 -> HoursMinus1
        HoursMinus1 -> Hours0
        Hours0 -> HoursPlus1
        HoursPlus1 -> HoursPlus2
        HoursPlus2 -> HoursPlus3
        HoursPlus3 -> HoursMinus3
    }
}

enum class EpgUpdateInterval(val hours: Int?, val label: String) {
    Manual(null, "Manual only"),
    Hours6(6, "6 hours"),
    Hours12(12, "12 hours"),
    Hours24(24, "24 hours");

    fun next(): EpgUpdateInterval = when (this) {
        Manual -> Hours6
        Hours6 -> Hours12
        Hours12 -> Hours24
        Hours24 -> Manual
    }

    fun shouldRefreshSince(lastImportedAtEpochMillis: Long?): Boolean {
        val refreshHours = hours ?: return false
        val lastImportedAt = lastImportedAtEpochMillis ?: return true
        val elapsedMillis = System.currentTimeMillis() - lastImportedAt
        return elapsedMillis >= refreshHours * 60L * 60L * 1000L
    }

    fun nextDueAt(lastImportedAtEpochMillis: Long?): Long? {
        val refreshHours = hours ?: return null
        val intervalMillis = refreshHours * 60L * 60L * 1000L
        return lastImportedAtEpochMillis?.plus(intervalMillis) ?: System.currentTimeMillis()
    }
}

enum class EpgRetentionDays(val days: Int, val label: String) {
    Days1(1, "1 day"),
    Days3(3, "3 days"),
    Days7(7, "7 days"),
    Days14(14, "14 days"),
    Days30(30, "30 days");

    fun next(): EpgRetentionDays = when (this) {
        Days1 -> Days3
        Days3 -> Days7
        Days7 -> Days14
        Days14 -> Days30
        Days30 -> Days1
    }
}

enum class LogoPriority(val label: String) {
    Playlist("Playlist first"),
    Epg("Prefer EPG logos"),
    Local("Prefer local logos");

    fun next(): LogoPriority = when (this) {
        Playlist -> Epg
        Epg -> Local
        Local -> Playlist
    }
}

enum class RefreshIntervalHours(val hours: Int, val label: String) {
    Hours4(4, "4 hours"),
    Hours8(8, "8 hours"),
    Hours12(12, "12 hours"),
    Hours24(24, "24 hours");

    fun next(): RefreshIntervalHours = when (this) {
        Hours4 -> Hours8
        Hours8 -> Hours12
        Hours12 -> Hours24
        Hours24 -> Hours4
    }
}

