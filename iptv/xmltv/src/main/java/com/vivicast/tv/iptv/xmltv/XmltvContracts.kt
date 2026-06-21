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
        val programs = root.children("programme").mapNotNull { element ->
            val channelId = element.attribute("channel")
            val start = element.attribute("start")?.let(::parseXmltvTime)
            val stop = element.attribute("stop")?.let(::parseXmltvTime)
            val title = element.firstChildElement("title")?.textValue()
            if (channelId == null || start == null || stop == null || title == null) {
                skippedPrograms += 1
                null
            } else {
                XmltvProgram(
                    channelId = channelId,
                    startTimeMillis = start,
                    endTimeMillis = stop,
                    title = title,
                    subtitle = element.firstChildElement("sub-title")?.textValue(),
                    description = element.firstChildElement("desc")?.textValue(),
                    category = element.firstChildElement("category")?.textValue(),
                    iconUrl = element.firstChildElement("icon")?.attribute("src"),
                    isCatchupAvailable = false,
                )
            }
        }

        return XmltvDocument(
            channels = channels,
            programs = programs,
            skippedPrograms = skippedPrograms,
        )
    }

    private fun parseXmltvTime(value: String): Long? {
        val normalized = value.trim()
        return TIME_FORMATS.firstNotNullOfOrNull { format ->
            val position = ParsePosition(0)
            val parsed = synchronized(format) { format.parse(normalized, position) }
            parsed?.takeIf { position.index == normalized.length }?.time
        }
    }

    private companion object {
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
