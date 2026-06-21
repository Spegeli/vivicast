package com.vivicast.tv.iptv.m3u

import java.security.MessageDigest

interface M3uParser {
    fun parse(content: String): M3uPlaylist
}

data class M3uPlaylist(
    val channels: List<M3uChannel>,
    val skippedEntries: Int,
)

data class M3uChannel(
    val remoteId: String,
    val name: String,
    val streamUrl: String,
    val categoryName: String?,
    val logoUrl: String?,
    val channelNumber: String?,
    val tvgId: String?,
    val tvgName: String?,
    val isCatchupAvailable: Boolean,
    val catchupDays: Int,
    val rawAttributes: Map<String, String>,
)

class DefaultM3uParser : M3uParser {
    override fun parse(content: String): M3uPlaylist {
        val channels = mutableListOf<M3uChannel>()
        var skippedEntries = 0
        var pendingInfo: PendingExtInf? = null

        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        if (pendingInfo != null) {
                            skippedEntries += 1
                        }
                        pendingInfo = parseExtInf(line)
                    }

                    line.startsWith("#") -> Unit

                    pendingInfo != null -> {
                        val info = requireNotNull(pendingInfo)
                        val channel = info.toChannel(streamUrl = line)
                        if (channel == null) {
                            skippedEntries += 1
                        } else {
                            channels += channel
                        }
                        pendingInfo = null
                    }
                }
            }

        if (pendingInfo != null) {
            skippedEntries += 1
        }

        return M3uPlaylist(channels = channels, skippedEntries = skippedEntries)
    }

    private fun parseExtInf(line: String): PendingExtInf {
        val commaIndex = line.indexOf(',')
        val header = if (commaIndex >= 0) line.substring(0, commaIndex) else line
        val fallbackName = if (commaIndex >= 0) line.substring(commaIndex + 1).trim().takeIf { it.isNotBlank() } else null
        return PendingExtInf(attributes = parseAttributes(header), fallbackName = fallbackName)
    }

    private fun parseAttributes(header: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ATTRIBUTE_REGEX.findAll(header).forEach { match ->
            val key = match.groupValues[1].trim()
            val value = match.groupValues[2].trim()
            if (key.isNotBlank()) result[key] = value
        }
        return result
    }

    private data class PendingExtInf(
        val attributes: Map<String, String>,
        val fallbackName: String?,
    ) {
        fun toChannel(streamUrl: String): M3uChannel? {
            val normalizedUrl = streamUrl.trim()
            if (normalizedUrl.isBlank()) return null
            val tvgId = attributes.firstValue("tvg-id")
            val tvgName = attributes.firstValue("tvg-name")
            val name = tvgName ?: fallbackName ?: attributes.firstValue("name") ?: return null
            val catchup = attributes.firstValue("catchup")
            val catchupDays = attributes.firstValue("catchup-days")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val isCatchupAvailable = catchup != null && !catchup.equals("false", ignoreCase = true) || catchupDays > 0
            val remoteId = tvgId?.takeIf { it.isNotBlank() } ?: stableId(normalizedUrl)

            return M3uChannel(
                remoteId = remoteId,
                name = name,
                streamUrl = normalizedUrl,
                categoryName = attributes.firstValue("group-title"),
                logoUrl = attributes.firstValue("tvg-logo"),
                channelNumber = attributes.firstValue("tvg-chno") ?: attributes.firstValue("channel-id"),
                tvgId = tvgId,
                tvgName = tvgName,
                isCatchupAvailable = isCatchupAvailable,
                catchupDays = catchupDays,
                rawAttributes = attributes,
            )
        }
    }

    private companion object {
        val ATTRIBUTE_REGEX = Regex("""([\w-]+)\s*=\s*"([^"]*)"""")

        fun Map<String, String>.firstValue(key: String): String? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
                ?.value
                ?.takeIf { it.isNotBlank() }

        fun stableId(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.take(16).joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}
