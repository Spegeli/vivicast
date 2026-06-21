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
}

data class EpgSourceSaveRequest(
    val sourceId: String? = null,
    val name: String,
    val urlKey: String,
    val timeShiftMinutes: Int,
    val isActive: Boolean,
)

data class EpgImportResult(
    val programsImported: Int,
    val programsSkipped: Int,
    val mappingsAdded: Int,
    val mappingsUpdated: Int,
)
