package com.vivicast.tv.iptv.xmltv

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory

private const val FALLBACK_TITLE = "Ohne Titel"

/** Peeks the gzip magic bytes (0x1F 0x8B) and transparently gunzips a raw .gz body. */
internal fun maybeGunzip(input: InputStream): InputStream {
    val pushback = PushbackInputStream(BufferedInputStream(input), 2)
    val signature = ByteArray(2)
    val read = pushback.read(signature, 0, 2)
    if (read > 0) pushback.unread(signature, 0, read)
    val gzipped = read == 2 && signature[0] == 0x1F.toByte() && signature[1] == 0x8B.toByte()
    return if (gzipped) GZIPInputStream(pushback) else pushback
}

/**
 * Streams an XMLTV document via SAX (constant memory) and pushes channels + programmes to [handler].
 * Returns the number of skipped programmes (missing channel/start, or an open programme with no
 * derivable end time). Missing `stop` times are derived from the next programme's start on the same
 * channel (per-channel look-behind).
 */
internal fun streamXmltv(input: InputStream, handler: XmltvStreamHandler): Int {
    val parser = saxFactory.newSAXParser()
    val contentHandler = XmltvSaxHandler(handler)
    maybeGunzip(input).use { stream ->
        parser.parse(stream, contentHandler)
    }
    return contentHandler.finish()
}

private val saxFactory: SAXParserFactory = SAXParserFactory.newInstance().apply {
    isNamespaceAware = false
    // Allow the DOCTYPE declaration (many real XMLTV feeds carry one) but never fetch external DTDs or
    // expand external entities — XXE-safe streaming.
    setFeatureIfSupported("http://javax.xml.XMLConstants/feature/secure-processing", true)
    setFeatureIfSupported("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
    setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
}

private fun SAXParserFactory.setFeatureIfSupported(feature: String, value: Boolean) {
    runCatching { setFeature(feature, value) }
}

private class XmltvSaxHandler(private val out: XmltvStreamHandler) : DefaultHandler() {
    private var skipped = 0
    private val text = StringBuilder()
    private var capturingText = false

    // Current <channel> being read.
    private var channelId: String? = null
    private var displayNames = mutableListOf<String>()
    private var channelIcon: String? = null

    // Current <programme> being read.
    private var inProgramme = false
    private var progChannel: String? = null
    private var progStart: Long? = null
    private var progStopExplicit: Long? = null
    private var progTitle: String? = null
    private var progSubtitle: String? = null
    private var progDesc: String? = null
    private var progCategory: String? = null
    private var progIcon: String? = null

    // Per-channel programme with no explicit stop, awaiting the next programme's start to close it.
    private val openByChannel = HashMap<String, PendingProgram>()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        when (qName) {
            "channel" -> {
                channelId = attributes.value("id")
                displayNames = mutableListOf()
                channelIcon = null
            }
            "programme" -> {
                inProgramme = true
                progChannel = attributes.value("channel")
                progStart = attributes.value("start")?.let(::parseXmltvTime)
                val stopRaw = attributes.value("stop")
                // A present-but-unparseable stop means the programme is malformed → skip it later.
                progStopExplicit = stopRaw?.let(::parseXmltvTime)
                progStopUnparseable = stopRaw != null && progStopExplicit == null
                progTitle = null; progSubtitle = null; progDesc = null; progCategory = null; progIcon = null
            }
            "icon" -> {
                val src = attributes.value("src")
                if (inProgramme) progIcon = progIcon ?: src else if (channelId != null) channelIcon = channelIcon ?: src
            }
            "display-name", "title", "sub-title", "desc", "category" -> {
                text.setLength(0)
                capturingText = true
            }
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (capturingText) text.append(ch, start, length)
    }

    private var progStopUnparseable = false

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (qName) {
            "display-name" -> textValue()?.let { displayNames.add(it) }
            "title" -> progTitle = textValue()
            "sub-title" -> progSubtitle = textValue()
            "desc" -> progDesc = textValue()
            "category" -> progCategory = textValue()
            "channel" -> {
                val id = channelId
                if (id != null) {
                    out.onChannel(
                        XmltvChannel(
                            id = id,
                            displayNames = displayNames.toList().ifEmpty { listOf(id) },
                            iconUrl = channelIcon,
                        ),
                    )
                }
                channelId = null
            }
            "programme" -> finishProgramme()
        }
        capturingText = false
    }

    private fun finishProgramme() {
        inProgramme = false
        val channel = progChannel
        val start = progStart
        if (channel == null || start == null || progStopUnparseable) {
            skipped += 1
            return
        }
        // A new programme on this channel closes any open (no-stop) programme at this start.
        closeOpen(channel, start)
        val stop = progStopExplicit
        when {
            stop != null && stop > start -> out.onProgram(buildProgram(channel, start, stop))
            stop != null -> skipped += 1 // stop <= start → invalid duration
            else -> openByChannel[channel] = PendingProgram(start, progTitle, progSubtitle, progDesc, progCategory, progIcon)
        }
    }

    private fun closeOpen(channel: String, nextStart: Long) {
        val open = openByChannel.remove(channel) ?: return
        if (nextStart > open.start) {
            out.onProgram(
                XmltvProgram(
                    channelId = channel,
                    startTimeMillis = open.start,
                    endTimeMillis = nextStart,
                    title = open.title ?: FALLBACK_TITLE,
                    subtitle = open.subtitle,
                    description = open.description,
                    category = open.category,
                    iconUrl = open.iconUrl,
                    isCatchupAvailable = false,
                ),
            )
        } else {
            skipped += 1
        }
    }

    private fun buildProgram(channel: String, start: Long, stop: Long): XmltvProgram =
        XmltvProgram(
            channelId = channel,
            startTimeMillis = start,
            endTimeMillis = stop,
            title = progTitle ?: FALLBACK_TITLE,
            subtitle = progSubtitle,
            description = progDesc,
            category = progCategory,
            iconUrl = progIcon,
            isCatchupAvailable = false,
        )

    /** Any programme still open at end of document has no next start → cannot derive an end time. */
    fun finish(): Int {
        skipped += openByChannel.size
        openByChannel.clear()
        return skipped
    }

    private fun textValue(): String? = text.toString().trim().takeIf { it.isNotBlank() }

    private fun Attributes.value(name: String): String? = getValue(name)?.takeIf { it.isNotBlank() }

    private data class PendingProgram(
        val start: Long,
        val title: String?,
        val subtitle: String?,
        val description: String?,
        val category: String?,
        val iconUrl: String?,
    )
}

private val TIME_FORMATS: List<SimpleDateFormat> = listOf(
    SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
    SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US),
    SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
).onEach { it.isLenient = false }

private fun parseXmltvTime(value: String): Long? {
    val normalized = value.trim()
    return TIME_FORMATS.firstNotNullOfOrNull { format ->
        val position = ParsePosition(0)
        val parsed = synchronized(format) { format.parse(normalized, position) }
        parsed?.takeIf { position.index == normalized.length }?.time
    }
}
