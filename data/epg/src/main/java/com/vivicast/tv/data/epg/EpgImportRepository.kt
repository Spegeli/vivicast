package com.vivicast.tv.data.epg

import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.iptv.xmltv.XmltvDocument

interface EpgImportRepository {
    suspend fun saveEpgSource(request: EpgSourceSaveRequest): EpgSource

    suspend fun linkEpgSourceToProvider(providerId: String, epgSourceId: String, priority: Int)

    suspend fun importXmltv(
        providerId: String,
        epgSourceId: String,
        document: XmltvDocument,
    ): EpgImportResult

    suspend fun cleanupProgramsOutsideRetention(
        nowMillis: Long,
        pastDays: Int,
        futureDays: Int,
    ): Int

    /** Feed-level refresh metadata (last-refresh timestamp + feed channel/programme counts). */
    suspend fun markEpgSourceRefreshed(sourceId: String, refreshedAt: Long, channelCount: Int, programCount: Int)

    /** Toggles the in-progress flag so the source overview can show a "Refreshing" badge. */
    suspend fun setEpgSourceRefreshing(sourceId: String, refreshing: Boolean)
}

data class EpgSourceSaveRequest(
    val sourceId: String? = null,
    val name: String,
    val sourceConfigKey: String,
    val timeShiftMinutes: Int,
    val isActive: Boolean,
    val refreshIntervalHours: Int = 0,
)

data class EpgImportResult(
    val programsImported: Int,
    val programsSkipped: Int,
    val mappingsAdded: Int,
    val mappingsUpdated: Int,
)
