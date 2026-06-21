package com.vivicast.tv.iptv.xmltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DefaultXmltvParserTest {
    private val parser = DefaultXmltvParser()

    @Test
    fun parseChannelsAndPrograms() {
        val document = parser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="ard.de">
                <display-name>Das Erste HD</display-name>
                <display-name>ARD</display-name>
                <icon src="https://logos.example/ard.png" />
              </channel>
              <programme channel="ard.de" start="20260621180000 +0200" stop="20260621183000 +0200">
                <title>Tagesschau</title>
                <sub-title>Nachrichten</sub-title>
                <desc>Aktuelle Nachrichten.</desc>
                <category>News</category>
                <icon src="https://images.example/tagesschau.png" />
              </programme>
            </tv>
            """.trimIndent(),
        )

        assertEquals(1, document.channels.size)
        assertEquals("ard.de", document.channels.single().id)
        assertEquals(listOf("Das Erste HD", "ARD"), document.channels.single().displayNames)
        assertEquals("https://logos.example/ard.png", document.channels.single().iconUrl)

        assertEquals(1, document.programs.size)
        val program = document.programs.single()
        assertEquals("ard.de", program.channelId)
        assertEquals("Tagesschau", program.title)
        assertEquals("Nachrichten", program.subtitle)
        assertEquals("News", program.category)
        assertEquals(time("20260621180000 +0200"), program.startTimeMillis)
        assertEquals(time("20260621183000 +0200"), program.endTimeMillis)
        assertFalse(program.isCatchupAvailable)
    }

    @Test
    fun skipsInvalidPrograms() {
        val document = parser.parse(
            """
            <tv>
              <channel id="ard.de"><display-name>ARD</display-name></channel>
              <programme channel="ard.de" start="bad" stop="20260621183000 +0200">
                <title>Broken</title>
              </programme>
              <programme channel="ard.de" start="20260621190000 +0200" stop="20260621193000 +0200" />
            </tv>
            """.trimIndent(),
        )

        assertEquals(1, document.channels.size)
        assertEquals(0, document.programs.size)
        assertEquals(2, document.skippedPrograms)
    }

    private fun time(value: String): Long {
        val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return requireNotNull(format.parse(value)).time
    }
}
