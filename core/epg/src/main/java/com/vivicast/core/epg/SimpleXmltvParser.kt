package com.vivicast.core.epg

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.Reader
import java.io.StringReader
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

class SimpleXmltvParser {
    fun parse(content: String): XmltvImportBatch {
        return parse(StringReader(content))
    }

    fun parse(reader: Reader): XmltvImportBatch {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(reader)

        val channels = mutableListOf<XmltvChannel>()
        val programs = mutableListOf<XmltvProgram>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> parseChannel(parser)?.let(channels::add)
                    "programme" -> parseProgram(parser)?.let(programs::add)
                }
            }
            event = parser.next()
        }

        return XmltvImportBatch(channels = channels, programs = programs)
    }

    suspend fun parseStreaming(
        reader: Reader,
        programBatchSize: Int = 500,
        onChannel: (XmltvChannel) -> Unit,
        onProgramBatch: suspend (List<XmltvProgram>) -> Unit
    ): XmltvStreamingReport {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(reader)

        val programBatch = mutableListOf<XmltvProgram>()
        var channelCount = 0
        var programCount = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> parseChannel(parser)?.let { channel ->
                        channelCount += 1
                        onChannel(channel)
                    }

                    "programme" -> parseProgram(parser)?.let { program ->
                        programCount += 1
                        programBatch += program
                        if (programBatch.size >= programBatchSize) {
                            onProgramBatch(programBatch.toList())
                            programBatch.clear()
                        }
                    }
                }
            }
            event = parser.next()
        }

        if (programBatch.isNotEmpty()) {
            onProgramBatch(programBatch.toList())
        }

        return XmltvStreamingReport(
            channelCount = channelCount,
            programCount = programCount
        )
    }

    private fun parseChannel(parser: XmlPullParser): XmltvChannel? {
        val id = parser.getAttributeValue(null, "id")?.takeIf { it.isNotBlank() } ?: return null
        var displayName = id
        var iconUrl: String? = null

        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name == "channel")) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "display-name" -> displayName = parser.nextText().takeIf { it.isNotBlank() } ?: displayName
                    "icon" -> iconUrl = parser.getAttributeValue(null, "src")?.takeIf { it.isNotBlank() }
                }
            }
            event = parser.next()
        }

        return XmltvChannel(xmltvId = id, displayName = displayName, iconUrl = iconUrl)
    }

    private fun parseProgram(parser: XmlPullParser): XmltvProgram? {
        val channelId = parser.getAttributeValue(null, "channel")?.takeIf { it.isNotBlank() } ?: return null
        val start = parser.getAttributeValue(null, "start")?.toXmltvEpochMillis() ?: return null
        val stop = parser.getAttributeValue(null, "stop")?.toXmltvEpochMillis() ?: return null
        var title = "Untitled"
        var description: String? = null
        var iconUrl: String? = null

        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name == "programme")) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> title = parser.nextText().takeIf { it.isNotBlank() } ?: title
                    "desc" -> description = parser.nextText().takeIf { it.isNotBlank() }
                    "icon" -> iconUrl = parser.getAttributeValue(null, "src")?.takeIf { it.isNotBlank() }
                }
            }
            event = parser.next()
        }

        return XmltvProgram(
            xmltvChannelId = channelId,
            title = title,
            description = description,
            startUtcEpochMillis = start,
            endUtcEpochMillis = stop,
            iconUrl = iconUrl
        )
    }

    private fun String.toXmltvEpochMillis(): Long? {
        val normalized = trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")
        return runCatching { OffsetDateTime.parse(normalized, xmltvFormatter).toInstant().toEpochMilli() }
            .getOrNull()
    }

    private companion object {
        val xmltvFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
            .appendPattern("yyyyMMddHHmmss")
            .optionalStart()
            .appendLiteral(' ')
            .appendOffset("+HH:MM", "Z")
            .optionalEnd()
            .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
            .toFormatter()
    }
}
