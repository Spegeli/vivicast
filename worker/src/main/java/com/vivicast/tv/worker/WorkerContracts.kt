package com.vivicast.tv.worker

object WorkerContracts {
    const val GLOBAL_REFRESH_WORK = "global_refresh"
    const val PERIODIC_GLOBAL_REFRESH_WORK = "periodic_global_refresh"
    const val PROVIDER_REFRESH_WORK = "provider_refresh"
    const val PLAYLIST_REFRESH_WORK = "playlist_refresh"
    const val EPG_REFRESH_WORK = "epg_refresh"
    const val SERIES_DETAILS_REFRESH_WORK = "series_details_refresh"
    const val LOGO_REFRESH_WORK = "logo_refresh"
    const val CACHE_CLEANUP_WORK = "cache_cleanup"

    const val INPUT_PROVIDER_ID = "provider_id"
    const val INPUT_EPG_SOURCE_ID = "epg_source_id"

    const val DEFAULT_GLOBAL_REFRESH_INTERVAL_HOURS = 12L
    const val MIN_GLOBAL_REFRESH_INTERVAL_HOURS = 1L

    fun uniquePlaylistRefreshWork(providerId: String): String =
        "$PLAYLIST_REFRESH_WORK:$providerId"

    fun uniqueEpgRefreshWork(epgSourceId: String): String =
        "$EPG_REFRESH_WORK:$epgSourceId"

    fun uniqueSeriesDetailsRefreshWork(providerId: String): String =
        "$SERIES_DETAILS_REFRESH_WORK:$providerId"
}
