package com.vivicast.tv.worker

object WorkerContracts {
    const val GLOBAL_REFRESH_WORK = "global_refresh"
    const val PERIODIC_GLOBAL_REFRESH_WORK = "periodic_global_refresh"
    const val PLAYLIST_REFRESH_WORK = "playlist_refresh"
    const val EPG_REFRESH_WORK = "epg_refresh"

    // Post-restore ordered refresh: all playlists (Phase 1) then all EPG (Phase 2) as one WorkManager
    // continuation, so EPG only maps against the fully-rebuilt catalog. See plans/backup-restore-followups.md.
    const val RESTORE_REFRESH_WORK = "restore_refresh"

    const val INPUT_PROVIDER_ID = "provider_id"
    const val INPUT_EPG_SOURCE_ID = "epg_source_id"

    // Flags a playlist refresh that runs inside the restore continuation: suppress its per-provider EPG
    // re-enqueue (Phase 2 owns EPG) and never fail the chain (terminal/exhausted → success so `.then` proceeds).
    const val INPUT_RESTORE_CHAIN = "restore_chain"

    const val DEFAULT_GLOBAL_REFRESH_INTERVAL_HOURS = 12L
    const val MIN_GLOBAL_REFRESH_INTERVAL_HOURS = 1L

    fun uniquePlaylistRefreshWork(providerId: String): String =
        "$PLAYLIST_REFRESH_WORK:$providerId"

    fun uniquePlaylistPeriodicWork(providerId: String): String =
        "periodic_$PLAYLIST_REFRESH_WORK:$providerId"

    fun uniqueEpgRefreshWork(epgSourceId: String): String =
        "$EPG_REFRESH_WORK:$epgSourceId"

    fun uniqueEpgPeriodicWork(epgSourceId: String): String =
        "periodic_$EPG_REFRESH_WORK:$epgSourceId"
}
