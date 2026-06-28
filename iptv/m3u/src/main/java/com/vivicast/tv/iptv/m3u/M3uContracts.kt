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
    val catchupMode: String?,
    val catchupSource: String?,
    val rawAttributes: Map<String, String>,
)

class DefaultM3uParser : M3uParser {
    override fun parse(content: String): M3uPlaylist {
        val channels = mutableListOf<M3uChannel>()
        var skippedEntries = 0
        var pendingInfo: PendingExtInf? = null

        content.lineSequence()
            .map { it.trim().removePrefix(UTF8_BOM).trim() }
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

                    else -> {
                        skippedEntries += 1
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
            val catchupMode = catchup?.lowercase()?.takeIf { it == "default" || it == "append" }
            val catchupSource = attributes.firstValue("catchup-source")
            val isCatchupAvailable = catchupMode != null && catchupSource != null && catchupDays > 0
            val group = attributes.firstValue("group-title")
            val channelNumber = attributes.firstValue("tvg-chno") ?: attributes.firstValue("channel-id")
            val remoteId = stableChannelId(
                tvgId = tvgId,
                name = name,
                group = group,
                channelNumber = channelNumber,
                streamUrl = normalizedUrl,
            )

            return M3uChannel(
                remoteId = remoteId,
                name = name,
                streamUrl = normalizedUrl,
                categoryName = group,
                logoUrl = attributes.firstValue("tvg-logo"),
                channelNumber = channelNumber,
                tvgId = tvgId,
                tvgName = tvgName,
                isCatchupAvailable = isCatchupAvailable,
                catchupDays = catchupDays,
                catchupMode = catchupMode,
                catchupSource = catchupSource,
                rawAttributes = attributes,
            )
        }
    }

    private companion object {
        const val UTF8_BOM = "\uFEFF"
        const val MEDIA_TYPE_CHANNEL = "channel"
        val ATTRIBUTE_REGEX = Regex("""([\w-]+)\s*=\s*"([^"]*)"""")

        fun Map<String, String>.firstValue(key: String): String? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
                ?.value
                ?.takeIf { it.isNotBlank() }

        fun stableChannelId(
            tvgId: String?,
            name: String,
            group: String?,
            channelNumber: String?,
            streamUrl: String,
        ): String {
            tvgId?.takeIf { it.isNotBlank() }?.let { return "$MEDIA_TYPE_CHANNEL:tvg-id:${it.trim()}" }
            val normalizedName = name.normalizedStablePart()
            val normalizedGroup = group.normalizedStablePart()
            channelNumber?.takeIf { it.isNotBlank() }?.let { number ->
                return "$MEDIA_TYPE_CHANNEL:name-group-number:${stableId("$normalizedName|$normalizedGroup|${number.trim()}")}"
            }
            return "$MEDIA_TYPE_CHANNEL:name-group-stream:${stableId("$normalizedName|$normalizedGroup|${stableId(streamUrl)}")}"
        }

        fun String?.normalizedStablePart(): String =
            this?.trim()
                ?.lowercase()
                ?.replace(Regex("""\s+"""), " ")
                ?.takeIf { it.isNotBlank() }
                ?: ""

        fun stableId(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.take(16).joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}
