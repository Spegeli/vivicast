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
fun providerEnabledKey(playlistId: String): String = "provider_enabled_$playlistId"
fun providerLiveTvEnabledKey(playlistId: String): String = "provider_live_tv_enabled_$playlistId"
fun providerVodEnabledKey(playlistId: String): String = "provider_vod_enabled_$playlistId"
fun providerLogoPriorityKey(playlistId: String): String = "provider_logo_priority_$playlistId"
fun providerHiddenCategoriesKey(playlistId: String): String = "provider_hidden_categories_$playlistId"
fun providerCategoryOrderKey(playlistId: String): String = "provider_category_order_$playlistId"
fun providerUserAgentKey(playlistId: String): String = "provider_user_agent_$playlistId"
fun providerXtreamOutputFormatKey(playlistId: String): String = "provider_xtream_output_format_$playlistId"
fun providerXtreamApiScopeKey(playlistId: String): String = "provider_xtream_api_scope_$playlistId"
fun providerRefreshIntervalKey(playlistId: String): String = "provider_refresh_interval_$playlistId"
fun providerRefreshOnAppStartKey(playlistId: String): String = "provider_refresh_on_app_start_$playlistId"
fun providerRefreshOnPlaylistChangeKey(playlistId: String): String = "provider_refresh_on_playlist_change_$playlistId"
fun providerEpgAssignmentsKey(playlistId: String): String = "provider_epg_assignments_$playlistId"
fun providerLastSyncedAtKey(playlistId: String): String = "provider_last_synced_at_$playlistId"
fun providerLastErrorKey(playlistId: String): String = "provider_last_error_$playlistId"
fun providerLastRefreshAttemptAtKey(playlistId: String): String = "provider_last_refresh_attempt_at_$playlistId"
fun providerLastErrorAtKey(playlistId: String): String = "provider_last_error_at_$playlistId"
fun providerLastRefreshDurationKey(playlistId: String): String = "provider_last_refresh_duration_$playlistId"
fun providerLastRefreshSourceKey(playlistId: String): String = "provider_last_refresh_source_$playlistId"
fun providerLastChannelCountKey(playlistId: String): String = "provider_last_channel_count_$playlistId"
fun providerLastCategoryCountKey(playlistId: String): String = "provider_last_category_count_$playlistId"
fun providerLastIgnoredLineCountKey(playlistId: String): String = "provider_last_ignored_line_count_$playlistId"
fun providerLastCatchupChannelCountKey(playlistId: String): String = "provider_last_catchup_channel_count_$playlistId"
fun providerLastArchiveWindowDaysKey(playlistId: String): String = "provider_last_archive_window_days_$playlistId"
fun providerLastXtreamVodCategoryCountKey(playlistId: String): String = "provider_last_xtream_vod_category_count_$playlistId"
fun providerLastXtreamSeriesCategoryCountKey(playlistId: String): String = "provider_last_xtream_series_category_count_$playlistId"
fun providerLastXtreamMovieCountKey(playlistId: String): String = "provider_last_xtream_movie_count_$playlistId"
fun providerLastXtreamSeriesCountKey(playlistId: String): String = "provider_last_xtream_series_count_$playlistId"
fun providerLastXtreamSeasonCountKey(playlistId: String): String = "provider_last_xtream_season_count_$playlistId"
fun providerLastXtreamEpisodeCountKey(playlistId: String): String = "provider_last_xtream_episode_count_$playlistId"
fun providerLastXtreamSeriesDetailFailureCountKey(playlistId: String): String = "provider_last_xtream_series_detail_failure_count_$playlistId"
fun providerRefreshSuccessCountKey(playlistId: String): String = "provider_refresh_success_count_$playlistId"
fun providerRefreshFailureCountKey(playlistId: String): String = "provider_refresh_failure_count_$playlistId"
fun providerConsecutiveRefreshFailureCountKey(playlistId: String): String = "provider_consecutive_refresh_failure_count_$playlistId"
fun providerLastEpgAttemptAtKey(playlistId: String): String = "provider_last_epg_attempt_at_$playlistId"
fun providerLastEpgImportedAtKey(playlistId: String): String = "provider_last_epg_imported_at_$playlistId"
fun providerLastEpgImportDurationKey(playlistId: String): String = "provider_last_epg_import_duration_$playlistId"
fun providerLastEpgXmltvChannelCountKey(playlistId: String): String = "provider_last_epg_xmltv_channel_count_$playlistId"
fun providerLastEpgImportedProgramCountKey(playlistId: String): String = "provider_last_epg_imported_program_count_$playlistId"
fun providerLastEpgUnmatchedProgramCountKey(playlistId: String): String = "provider_last_epg_unmatched_program_count_$playlistId"
fun providerLastEpgErrorKey(playlistId: String): String = "provider_last_epg_error_$playlistId"
fun providerLastEpgErrorAtKey(playlistId: String): String = "provider_last_epg_error_at_$playlistId"
fun providerEpgSuccessCountKey(playlistId: String): String = "provider_epg_success_count_$playlistId"
fun providerEpgFailureCountKey(playlistId: String): String = "provider_epg_failure_count_$playlistId"
fun providerConsecutiveEpgFailureCountKey(playlistId: String): String = "provider_consecutive_epg_failure_count_$playlistId"
const val startOnBootKey = "app_start_on_boot"
const val resumeLastChannelOnStartKey = "app_resume_last_channel_on_start"
const val confirmExitOnBackKey = "app_confirm_exit_on_back"
const val autoHidePrimaryNavigationKey = "app_auto_hide_primary_navigation"
const val autoCollapseProviderFiltersKey = "app_auto_collapse_provider_filters"
const val showChannelNumbersKey = "app_show_channel_numbers"
const val showChannelMetadataKey = "app_show_channel_metadata"
const val showSourceLabelsKey = "app_show_source_labels"
const val compactChannelRowsKey = "app_compact_channel_rows"
const val compactProviderRowsKey = "app_compact_provider_rows"
const val keepScreenAwakeDuringPlaybackKey = "app_keep_screen_awake_during_playback"
const val showPlaybackStatusBadgeKey = "app_show_playback_status_badge"
const val showPlaybackClockKey = "app_show_playback_clock"
const val showPlaybackRecentsKey = "app_show_playback_recents"
const val showPlaybackGuideActionKey = "app_show_playback_guide_action"
const val showPlaybackProgressBarKey = "app_show_playback_progress_bar"
const val showPlaybackTrackActionsKey = "app_show_playback_track_actions"
const val showPlaybackFavoriteActionKey = "app_show_playback_favorite_action"
const val showPlaybackProgrammeDescriptionKey = "app_show_playback_programme_description"
const val showPlaybackNowNextPanelKey = "app_show_playback_now_next_panel"
const val showFavoriteFeedbackBannerKey = "app_show_favorite_feedback_banner"
const val recoverablePlaybackRetryDelayKey = "app_recoverable_playback_retry_delay"
const val retryRecoverablePlaybackErrorsOnceKey = "app_retry_recoverable_playback_errors_once"
const val leaveLiveTvPlaybackBehaviorKey = "app_leave_live_tv_playback_behavior"
const val stopPlaybackWhenLeavingLiveTvKey = "app_stop_playback_when_leaving_live_tv"
const val openProviderFiltersWhenEnteringLiveTvKey = "app_open_provider_filters_when_entering_live_tv"
const val backClearsLiveTvFiltersKey = "app_back_clears_live_tv_filters"
const val backHidesLiveTvFiltersFirstKey = "app_back_hides_live_tv_filters_first"
const val guideResetsToNowOnOpenKey = "app_guide_resets_to_now_on_open"
const val guideStartsWithCurrentChannelKey = "app_guide_starts_with_current_channel"
const val guideUsesPlayingChannelProviderOnOpenKey = "app_guide_uses_playing_channel_provider_on_open"
const val guideClearsCategoryOnOpenKey = "app_guide_clears_category_on_open"
const val backClearsProviderBeforeCategoryKey = "app_back_clears_provider_before_category"
const val backClosesNavigationFirstKey = "app_back_closes_navigation_first"
const val backClearsGuideWindowFirstKey = "app_back_clears_guide_window_first"
const val backClearsGuideProgrammeFirstKey = "app_back_clears_guide_programme_first"
const val backLeavesSettingsToLiveTvKey = "app_back_leaves_settings_to_live_tv"
const val backClearsGuideFiltersKey = "app_back_clears_guide_filters"
const val preferProviderFiltersBeforeNavigationKey = "app_prefer_provider_filters_before_navigation"
const val epgTimeOffsetKey = "app_epg_time_offset"
const val epgRefreshOnAppStartKey = "app_epg_refresh_on_app_start"
const val epgUpdateIntervalKey = "app_epg_update_interval"
const val epgRefreshDuringSessionKey = "app_epg_refresh_during_session"
const val epgRetentionDaysKey = "app_epg_retention_days"

fun M3uImportResult.asStatusText(): String {
    return "Imported $channelCount channels in $categoryCount categories"
}

fun List<ChannelCategory>.sortedForProvider(categoryOrderIds: List<String>): List<ChannelCategory> {
    if (isEmpty()) return this
    val fallbackOrder = sortedWith(compareBy<ChannelCategory>({ it.sortIndex }, { it.name.lowercase() }))
    if (categoryOrderIds.isEmpty()) return fallbackOrder
    val orderMap = categoryOrderIds.withIndex().associate { (index, id) -> id to index }
    return fallbackOrder.sortedWith(
        compareBy<ChannelCategory> { orderMap[it.id] ?: Int.MAX_VALUE }
            .thenBy { it.sortIndex }
            .thenBy { it.name.lowercase() }
    )
}

fun List<ChannelCategory>.sortedForProviders(
    providerSettings: Map<String, ProviderUiSettings>
): List<ChannelCategory> {
    if (isEmpty()) return this
    return groupBy { it.playlistId }
        .values
        .flatMap { playlistCategories ->
            val order = providerSettings[playlistCategories.first().playlistId]?.categoryOrderIds.orEmpty()
            playlistCategories.sortedForProvider(order)
        }
}

fun List<ChannelCategory>.moveCategory(
    categoryId: String,
    direction: Int
): List<ChannelCategory> {
    val currentIndex = indexOfFirst { it.id == categoryId }
    if (currentIndex == -1) return this
    val targetIndex = (currentIndex + direction).coerceIn(indices)
    if (targetIndex == currentIndex) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(currentIndex)
    mutable.add(targetIndex, item)
    return mutable
}

fun ProviderUiSettings?.isLiveTvActive(): Boolean {
    return this?.isLiveTvActive() != false
}

fun ProviderUiSettings?.isImportActive(): Boolean {
    return this?.isImportActive() != false
}

fun PlaybackStatus.asText(): String {
    return when (this) {
        PlaybackStatus.Idle -> "Idle"
        PlaybackStatus.Buffering -> "Buffering"
        PlaybackStatus.Playing -> "Playing"
        PlaybackStatus.Paused -> "Paused"
        is PlaybackStatus.Error -> "Error: $message"
    }
}

fun PlaybackStatus.statusColor(): Color {
    return when (this) {
        PlaybackStatus.Buffering -> ViviCastColors.Warning
        PlaybackStatus.Playing -> ViviCastColors.Success
        is PlaybackStatus.Error -> ViviCastColors.Error
        else -> ViviCastColors.TextSecondary
    }
}

fun String.validatePlaylistUrl(): String? {
    return when {
        isBlank() -> "Enter a playlist URL."
        !(startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)) ->
            "URL must start with http:// or https://."
        !contains(".", ignoreCase = true) -> "Enter a complete URL."
        else -> null
    }
}

fun String.validatePlaylistFileSource(): String? {
    val normalized = trim()
    return when {
        normalized.isBlank() -> "Enter a file URI or local file path."
        normalized.startsWith("content://", ignoreCase = true) -> null
        normalized.startsWith("file://", ignoreCase = true) -> null
        normalized.contains("/") || normalized.contains("\\") -> null
        else -> "Use content://, file://, or a full local file path."
    }
}

fun String.extractXtreamCredentialsFromPlaylistUrl(): XtreamCredentials? {
    val parsed = runCatching { Uri.parse(trim()) }.getOrNull() ?: return null
    val scheme = parsed.scheme?.takeIf { it.equals("http", ignoreCase = true) || it.equals("https", ignoreCase = true) }
        ?: return null
    val host = parsed.host?.takeIf { it.isNotBlank() } ?: return null
    val lastSegment = parsed.lastPathSegment?.lowercase(Locale.US) ?: return null
    if (lastSegment != "get.php") return null
    val username = parsed.getQueryParameter("username")?.trim().takeIf { !it.isNullOrBlank() } ?: return null
    val password = parsed.getQueryParameter("password")?.trim().takeIf { !it.isNullOrBlank() } ?: return null
    val pathSegments = parsed.pathSegments
        .dropLast(1)
        .filter { it.isNotBlank() }
    val authority = buildString {
        append(host)
        parsed.port.takeIf { it != -1 }?.let {
            append(':')
            append(it)
        }
    }
    val baseUrl = buildString {
        append(scheme)
        append("://")
        append(authority)
        if (pathSegments.isNotEmpty()) {
            append('/')
            append(pathSegments.joinToString("/"))
        }
    }
    return XtreamCredentials(
        baseUrl = baseUrl.trimEnd('/'),
        username = username,
        password = password
    )
}

fun String.playlistNameFromUrl(): String {
    return toDisplayHost()
        ?: "M3U Playlist"
}

fun Playlist.displayName(): String {
    return name
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("?")
        .substringBefore("/")
        .takeIf { it.isNotBlank() }
        ?: sourceUri?.toDisplayHost()
        ?: "Playlist"
}

fun Playlist.safeSourceLabel(): String {
    return sourceUri?.maskedDisplaySource() ?: "local source"
}

fun EpgProgram.asGuideLabel(): String {
    return "${startUtcEpochMillis.asShortTime()} $title"
}

fun EpgProgram.timeRangeLabel(): String {
    return "${startUtcEpochMillis.asShortTime()} - ${endUtcEpochMillis.asShortTime()}"
}

fun EpgProgram.progressAt(nowUtcEpochMillis: Long): Float {
    val duration = (endUtcEpochMillis - startUtcEpochMillis).coerceAtLeast(1L)
    return ((nowUtcEpochMillis - startUtcEpochMillis).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

fun Long.asShortTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))
}

fun Long.asShortDateTime(): String {
    return SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date(this))
}

fun Long.asOverlayClock(): String {
    return SimpleDateFormat("EEE, dd MMM  HH:mm", Locale.getDefault()).format(Date(this))
}

fun Long.asDurationLabel(): String {
    return when {
        this < 1_000L -> "${this}ms"
        this < 60_000L -> String.format(Locale.getDefault(), "%.1fs", this / 1000f)
        else -> {
            val minutes = this / 60_000L
            val seconds = (this % 60_000L) / 1_000L
            "${minutes}m ${seconds}s"
        }
    }
}

fun Long.asPlaybackTimeLabel(): String {
    val totalSeconds = (this / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun Long.roundDownToHalfHour(): Long {
    return this - (this % HalfHourMillis)
}

fun Long.toGuideWeight(): Float {
    return (this.toFloat() / 60_000f).coerceAtLeast(1f)
}

fun String.toDisplayHost(): String? {
    return removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
        .substringBefore("?")
        .takeIf { it.isNotBlank() }
}

fun String.maskedDisplaySource(): String {
    val normalized = trim()
    if (normalized.isBlank()) return normalized
    return when {
        normalized.startsWith("content://", ignoreCase = true) -> {
            val uri = Uri.parse(normalized)
            uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "content source"
        }
        normalized.startsWith("file://", ignoreCase = true) -> {
            Uri.parse(normalized).path?.substringAfterLast('/')?.substringAfterLast('\\')
                ?.takeIf { it.isNotBlank() }
                ?: "local file"
        }
        normalized.startsWith("http://", ignoreCase = true) || normalized.startsWith("https://", ignoreCase = true) -> {
            val scheme = if (normalized.startsWith("https://", ignoreCase = true)) "https://" else "http://"
            val withoutScheme = normalized.removePrefix("https://").removePrefix("http://")
            val host = withoutScheme.substringBefore("/")
            val path = withoutScheme.substringAfter("/", "")
            buildString {
                append(scheme)
                append(host)
                if (path.isNotBlank() || normalized.contains("?")) {
                    append("/...")
                }
            }
        }
        else -> normalized.toDisplayHost()
            ?: normalized.substringAfterLast('/').substringAfterLast('\\').ifBlank { normalized }
    }
}

