package com.vivicast.tv.iptv.xmltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class XmltvStreamParserTest {

    private class Collector : XmltvStreamHandler {
        val channels = mutableListOf<XmltvChannel>()
        val programs = mutableListOf<XmltvProgram>()
        override fun onChannel(channel: XmltvChannel) { channels += channel }
        override fun onProgram(program: XmltvProgram) { programs += program }
    }

    private val parser = DefaultXmltvParser()

    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="c1"><display-name>One</display-name><icon src="http://i/1.png"/></channel>
          <channel id="c2"><display-name>Two</display-name></channel>
          <programme channel="c1" start="20260101000000 +0000" stop="20260101010000 +0000">
            <title>Show A</title><desc>d</desc>
          </programme>
          <programme channel="c2" start="20260101000000 +0000">
            <title>Open</title>
          </programme>
          <programme channel="c2" start="20260101020000 +0000" stop="20260101030000 +0000">
            <title>Closes previous</title>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun streams_channels_and_programmes() {
        val out = Collector()
        val skipped = parser.parseStreaming(ByteArrayInputStream(sample.toByteArray()), out)

        assertEquals(listOf("c1", "c2"), out.channels.map { it.id })
        assertEquals("One", out.channels[0].displayNames.first())
        assertEquals("http://i/1.png", out.channels[0].iconUrl)
        assertEquals(0, skipped)
        // c1 explicit-stop, c2 open closed by its next programme (00:00→02:00), c2 explicit-stop.
        assertEquals(3, out.programs.size)
        val open = out.programs.first { it.title == "Open" }
        assertEquals(2L * 60 * 60 * 1000, open.endTimeMillis - open.startTimeMillis)
    }

    @Test
    fun decompresses_gzip_body() {
        val gz = ByteArrayOutputStream().also { GZIPOutputStream(it).use { s -> s.write(sample.toByteArray()) } }
        val out = Collector()
        parser.parseStreaming(ByteArrayInputStream(gz.toByteArray()), out)
        assertEquals(2, out.channels.size)
        assertEquals(3, out.programs.size)
    }

    @Test
    fun skips_programme_without_channel_or_start() {
        val xml = """
            <tv>
              <channel id="c1"><display-name>One</display-name></channel>
              <programme start="20260101000000 +0000"><title>NoChannel</title></programme>
              <programme channel="c1"><title>NoStart</title></programme>
            </tv>
        """.trimIndent()
        val out = Collector()
        val skipped = parser.parseStreaming(ByteArrayInputStream(xml.toByteArray()), out)
        assertTrue(out.programs.isEmpty())
        assertEquals(2, skipped)
    }
}
