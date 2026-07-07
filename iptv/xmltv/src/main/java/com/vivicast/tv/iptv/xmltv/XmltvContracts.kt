package com.vivicast.tv.iptv.xmltv

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

interface XmltvParser {
    fun parse(content: String): XmltvDocument

    /**
     * Streams an XMLTV document (SAX, constant memory) and pushes each channel/programme to [handler].
     * gzip bodies (magic bytes 0x1F 0x8B) are transparently decompressed. Returns the number of skipped
     * programmes. Use this for large feeds instead of [parse], which builds the whole DOM tree in memory.
     */
    fun parseStreaming(input: java.io.InputStream, handler: XmltvStreamHandler): Int
}

/** Sink for the streaming parser; called once per channel and once per resolved programme. */
interface XmltvStreamHandler {
    fun onChannel(channel: XmltvChannel)
    fun onProgram(program: XmltvProgram)
}

data class XmltvDocument(
    val channels: List<XmltvChannel>,
    val programs: List<XmltvProgram>,
    val skippedPrograms: Int,
)

data class XmltvChannel(
    val id: String,
    val displayNames: List<String>,
    val iconUrl: String?,
)

data class XmltvProgram(
    val channelId: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val category: String?,
    val iconUrl: String?,
    val isCatchupAvailable: Boolean,
)

class DefaultXmltvParser : XmltvParser {
    override fun parse(content: String): XmltvDocument {
        val document = documentBuilderFactory.newDocumentBuilder()
            .parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))
        val root = document.documentElement

        val channels = root.children("channel").mapNotNull { element ->
            val id = element.attribute("id") ?: return@mapNotNull null
            XmltvChannel(
                id = id,
                displayNames = element.children("display-name").mapNotNull { it.textValue() }.ifEmpty { listOf(id) },
                iconUrl = element.firstChildElement("icon")?.attribute("src"),
            )
        }

        var skippedPrograms = 0
        val parsedPrograms = root.children("programme").mapNotNull { element ->
            val channelId = element.attribute("channel")
            val start = element.attribute("start")?.let(::parseXmltvTime)
            val stopAttribute = element.attribute("stop")
            val stop = stopAttribute?.let(::parseXmltvTime)
            val title = element.firstChildElement("title")?.textValue()
            if (channelId == null || start == null || stopAttribute != null && stop == null) {
                skippedPrograms += 1
                null
            } else {
                PendingXmltvProgram(
                    channelId = channelId,
                    startTimeMillis = start,
                    explicitEndTimeMillis = stop,
                    title = title ?: FALLBACK_TITLE,
                    subtitle = element.firstChildElement("sub-title")?.textValue(),
                    description = element.firstChildElement("desc")?.textValue(),
                    category = element.firstChildElement("category")?.textValue(),
                    iconUrl = element.firstChildElement("icon")?.attribute("src"),
                    isCatchupAvailable = false,
                )
            }
        }
        val programs = parsedPrograms.mapNotNull { program ->
            val derivedEndTimeMillis = program.explicitEndTimeMillis
                ?: parsedPrograms
                    .asSequence()
                    .filter { it.channelId == program.channelId && it.startTimeMillis > program.startTimeMillis }
                    .minByOrNull { it.startTimeMillis }
                    ?.startTimeMillis
            if (derivedEndTimeMillis == null) {
                skippedPrograms += 1
                null
            } else {
                program.toProgram(derivedEndTimeMillis)
            }
        }

        return XmltvDocument(
            channels = channels,
            programs = programs,
            skippedPrograms = skippedPrograms,
        )
    }

    override fun parseStreaming(input: java.io.InputStream, handler: XmltvStreamHandler): Int =
        streamXmltv(input, handler)

    private fun parseXmltvTime(value: String): Long? {
        val normalized = value.trim()
        return TIME_FORMATS.firstNotNullOfOrNull { format ->
            val position = ParsePosition(0)
            val parsed = synchronized(format) { format.parse(normalized, position) }
            parsed?.takeIf { position.index == normalized.length }?.time
        }
    }

    private data class PendingXmltvProgram(
        val channelId: String,
        val startTimeMillis: Long,
        val explicitEndTimeMillis: Long?,
        val title: String,
        val subtitle: String?,
        val description: String?,
        val category: String?,
        val iconUrl: String?,
        val isCatchupAvailable: Boolean,
    ) {
        fun toProgram(derivedEndTimeMillis: Long): XmltvProgram =
            XmltvProgram(
                channelId = channelId,
                startTimeMillis = startTimeMillis,
                endTimeMillis = derivedEndTimeMillis,
                title = title,
                subtitle = subtitle,
                description = description,
                category = category,
                iconUrl = iconUrl,
                isCatchupAvailable = isCatchupAvailable,
            )
    }

    private companion object {
        const val FALLBACK_TITLE = "Ohne Titel"
        val documentBuilderFactory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
            isIgnoringComments = true
            isNamespaceAware = false
            setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
            setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
        }

        val TIME_FORMATS: List<SimpleDateFormat> = listOf(
            SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
            SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US),
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        ).onEach { it.isLenient = false }

        fun Element.attribute(name: String): String? =
            getAttribute(name).takeIf { it.isNotBlank() }

        fun Element.children(tagName: String): List<Element> {
            val nodes = childNodes
            val result = ArrayList<Element>(nodes.length)
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                if (node is Element && node.tagName == tagName) {
                    result += node
                }
            }
            return result
        }

        fun Element.firstChildElement(tagName: String): Element? =
            children(tagName).firstOrNull()

        fun Element.textValue(): String? =
            textContent?.trim()?.takeIf { it.isNotBlank() }

        fun DocumentBuilderFactory.setFeatureIfSupported(feature: String, enabled: Boolean) {
            runCatching { setFeature(feature, enabled) }
        }
    }
}
