package com.vivicast.tv.worker

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshWorkRequestsTest {
    @Test
    fun playlistRefreshUsesProviderInputAndNetworkConstraint() {
        val request = RefreshWorkRequests.playlistRefresh("provider-1")

        assertEquals(PlaylistRefreshWorker::class.java.name, request.workSpec.workerClassName)
        assertEquals("provider-1", request.workSpec.input.getString(WorkerContracts.INPUT_PROVIDER_ID))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun epgRefreshUsesSourceInputAndNetworkConstraint() {
        val request = RefreshWorkRequests.epgRefresh("epg-1")

        assertEquals(EpgRefreshWorker::class.java.name, request.workSpec.workerClassName)
        assertEquals("epg-1", request.workSpec.input.getString(WorkerContracts.INPUT_EPG_SOURCE_ID))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun periodicMaintenanceRefreshClampsTooSmallIntervals() {
        val request = RefreshWorkRequests.periodicMaintenanceRefresh(0)

        assertEquals(MaintenanceRefreshWorker::class.java.name, request.workSpec.workerClassName)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(
            WorkerContracts.MIN_GLOBAL_REFRESH_INTERVAL_HOURS * 60L * 60L * 1000L,
            request.workSpec.intervalDuration,
        )
        assertTrue(request.tags.contains(WorkerContracts.GLOBAL_REFRESH_WORK))
    }

    @Test
    fun uniqueWorkNamesIncludeSourceIds() {
        assertEquals(
            "playlist_refresh:provider-1",
            WorkerContracts.uniquePlaylistRefreshWork("provider-1"),
        )
        assertEquals(
            "epg_refresh:epg-1",
            WorkerContracts.uniqueEpgRefreshWork("epg-1"),
        )
    }
}
