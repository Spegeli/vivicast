package com.vivicast.tv.data.epg

import com.vivicast.tv.iptv.xmltv.DefaultXmltvParser
import com.vivicast.tv.iptv.xmltv.XmltvChannel
import com.vivicast.tv.iptv.xmltv.XmltvParser
import com.vivicast.tv.iptv.xmltv.XmltvProgram
import com.vivicast.tv.iptv.xmltv.XmltvStreamHandler
import java.io.InputStream

/**
 * Opens an EPG-source URL and streams its body to a consumer. Implemented in the app/worker layer with
 * OkHttp; abstracted here so the use case needs no OkHttp/Android dependency and can be faked in tests.
 * The body stream (and underlying response) are closed once [block] returns.
 */
interface EpgStreamSource {
    suspend fun open(url: String, block: (InputStream) -> Unit)
}

/**
 * Verifies that an EPG-source URL resolves to a usable XMLTV document before the source is saved.
 * Streams the document (SAX, constant memory) and only counts channels/programmes — never buffers the
 * whole file. gzip bodies are transparently decompressed by the parser. Network HTTP / size errors from
 * [streamSource] propagate unchanged; an empty/unusable document surfaces as
 * [EpgConnectionResponseException]. The App layer maps those throwables to the localized user message.
 */
class TestEpgSourceConnectionUseCase(
    private val streamSource: EpgStreamSource,
    private val xmltvParser: XmltvParser = DefaultXmltvParser(),
) {
    /** Throws on any failure; on success returns the parsed EPG breakdown so the caller can preview it. */
    suspend fun test(url: String): EpgContentSummary {
        val trimmed = url.trim().takeIf { it.isNotBlank() } ?: throw IllegalArgumentException()
        val counter = CountingHandler()
        streamSource.open(trimmed) { input -> xmltvParser.parseStreaming(input, counter) }
        if (counter.channels == 0) {
            throw EpgConnectionResponseException()
        }
        return EpgContentSummary(channels = counter.channels, programs = counter.programs)
    }

    private class CountingHandler : XmltvStreamHandler {
        var channels = 0
            private set
        var programs = 0
            private set

        override fun onChannel(channel: XmltvChannel) { channels += 1 }
        override fun onProgram(program: XmltvProgram) { programs += 1 }
    }
}

/** Parsed EPG breakdown from a passed connection test. */
data class EpgContentSummary(val channels: Int, val programs: Int)

/** Test outcome handed to the UI: an error message, or a content summary on success. */
data class EpgConnectionTestResult(val errorMessage: String?, val summary: EpgContentSummary?)

/** The source responded but the payload had no channels / was not a usable XMLTV document. */
class EpgConnectionResponseException : RuntimeException()
