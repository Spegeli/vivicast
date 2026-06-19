package com.vivicast.core.playlist

data class ParsedM3uChannel(
    val name: String,
    val streamUrl: String,
    val groupTitle: String?,
    val logoUrl: String?,
    val tvgId: String?,
    val tvgName: String?,
    val catchupSupported: Boolean,
    val archiveWindowDays: Int?
)

data class M3uParseReport(
    val channels: List<ParsedM3uChannel>,
    val ignoredLineCount: Int
)
