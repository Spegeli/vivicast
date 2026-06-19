package com.vivicast.core.playlist

class M3uPlaylistParser {
    fun parse(lines: Sequence<String>): M3uParseReport {
        val channels = mutableListOf<ParsedM3uChannel>()
        var ignored = 0
        var pendingInfo: ExtInf? = null

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isBlank() || line == "#EXTM3U" -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingInfo = parseExtInf(line)
                line.startsWith("#") -> ignored++
                pendingInfo != null -> {
                    val info = pendingInfo
                    channels += ParsedM3uChannel(
                        name = info?.name?.takeIf { it.isNotBlank() } ?: line,
                        streamUrl = line,
                        groupTitle = info?.groupTitle,
                        logoUrl = info?.logoUrl,
                        tvgId = info?.tvgId,
                        tvgName = info?.tvgName,
                        catchupSupported = info?.catchupSupported == true,
                        archiveWindowDays = info?.archiveWindowDays
                    )
                    pendingInfo = null
                }
                else -> ignored++
            }
        }

        return M3uParseReport(channels = channels, ignoredLineCount = ignored)
    }

    private fun parseExtInf(line: String): ExtInf {
        val name = line.substringAfter(",", missingDelimiterValue = "").trim()
        return ExtInf(
            name = name,
            groupTitle = attribute(line, "group-title"),
            logoUrl = attribute(line, "tvg-logo"),
            tvgId = attribute(line, "tvg-id"),
            tvgName = attribute(line, "tvg-name"),
            catchupSupported = attribute(line, "catchup").equals("default", ignoreCase = true) ||
                attribute(line, "catchup").equals("append", ignoreCase = true) ||
                !attribute(line, "catchup-source").isNullOrBlank() ||
                ((attribute(line, "catchup-days") ?: attribute(line, "timeshift"))?.toArchiveWindowDays() ?: 0) > 0,
            archiveWindowDays = (attribute(line, "catchup-days") ?: attribute(line, "timeshift"))
                ?.toArchiveWindowDays()
        )
    }

    private fun attribute(line: String, name: String): String? {
        val pattern = Regex("""\b${Regex.escape(name)}="([^"]*)"""", RegexOption.IGNORE_CASE)
        return pattern.find(line)?.groupValues?.getOrNull(1)
    }

    private data class ExtInf(
        val name: String,
        val groupTitle: String?,
        val logoUrl: String?,
        val tvgId: String?,
        val tvgName: String?,
        val catchupSupported: Boolean,
        val archiveWindowDays: Int?
    )

    private fun String.toArchiveWindowDays(): Int? {
        return trim()
            .replace(',', '.')
            .toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?.let { kotlin.math.ceil(it).toInt() }
    }
}
