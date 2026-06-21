package com.vivicast.tv.worker

object WorkerContracts {
    const val GLOBAL_REFRESH_WORK = "global_refresh"
    const val PROVIDER_REFRESH_WORK = "provider_refresh"
    const val PLAYLIST_REFRESH_WORK = "playlist_refresh"
    const val EPG_REFRESH_WORK = "epg_refresh"
    const val LOGO_REFRESH_WORK = "logo_refresh"
    const val CACHE_CLEANUP_WORK = "cache_cleanup"

    const val INPUT_PROVIDER_ID = "provider_id"
    const val INPUT_EPG_SOURCE_ID = "epg_source_id"
}
