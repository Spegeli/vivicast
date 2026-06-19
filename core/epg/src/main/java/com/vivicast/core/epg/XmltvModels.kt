package com.vivicast.core.epg

data class XmltvChannel(
    val xmltvId: String,
    val displayName: String,
    val iconUrl: String?
)

data class XmltvProgram(
    val xmltvChannelId: String,
    val title: String,
    val description: String?,
    val startUtcEpochMillis: Long,
    val endUtcEpochMillis: Long,
    val iconUrl: String?
)

data class XmltvImportBatch(
    val channels: List<XmltvChannel>,
    val programs: List<XmltvProgram>
)

data class XmltvStreamingReport(
    val channelCount: Int,
    val programCount: Int
)
