package com.vivicast.tv.iptv.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class DefaultM3uParserTest {
    private val parser = DefaultM3uParser()

    @Test
    fun parseChannelsWithProviderAttributes() {
        val playlist = parser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="ard.de" tvg-name="Das Erste HD" tvg-logo="https://logos.example/ard.png" group-title="Öffentlich" tvg-chno="1" catchup="default" catchup-days="7" catchup-source="https://archive.example/ard?start={start}&duration={duration}",ARD HD
            https://stream.example/ard/index.m3u8
            #EXTINF:-1 tvg-name="ZDF HD" group-title="Öffentlich",Fallback Ignored
            https://stream.example/zdf/index.m3u8
            """.trimIndent(),
        )

        assertEquals(2, playlist.channels.size)
        assertEquals(0, playlist.skippedEntries)

        val ard = playlist.channels[0]
        assertEquals("channel:tvg-id:ard.de", ard.remoteId)
        assertEquals("Das Erste HD", ard.name)
        assertEquals("Öffentlich", ard.categoryName)
        assertEquals("https://logos.example/ard.png", ard.logoUrl)
        assertEquals("1", ard.channelNumber)
        assertTrue(ard.isCatchupAvailable)
        assertEquals(7, ard.catchupDays)
        assertEquals("default", ard.catchupMode)
        assertEquals("https://archive.example/ard?start={start}&duration={duration}", ard.catchupSource)

        val zdf = playlist.channels[1]
        assertEquals("ZDF HD", zdf.name)
        assertEquals("https://stream.example/zdf/index.m3u8", zdf.streamUrl)
        assertNotEquals(zdf.streamUrl, zdf.remoteId)
        assertTrue(zdf.remoteId.startsWith("channel:name-group-stream:"))
        assertFalse(zdf.isCatchupAvailable)
    }

    @Test
    fun skipsIncompleteExtinfEntries() {
        val playlist = parser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="broken",Broken Without Url
            #EXTINF:-1 group-title="No Name",
            https://stream.example/noname.m3u8
            """.trimIndent(),
        )

        assertEquals(0, playlist.channels.size)
        assertEquals(2, playlist.skippedEntries)
    }

    @Test
    fun countsStreamLinesWithoutExtinfAndKeepsSecretOutOfStableId() {
        val playlist = parser.parse(
            """
            #EXTM3U
            https://stream.example/orphan.m3u8
            #EXTINF:-1 tvg-name="Private HD" group-title="News",Private HD
            https://user:secret@stream.example/private.m3u8?token=top-secret
            """.trimIndent(),
        )

        assertEquals(1, playlist.channels.size)
        assertEquals(1, playlist.skippedEntries)
        assertTrue(playlist.channels.single().remoteId.startsWith("channel:name-group-stream:"))
        assertFalse(playlist.channels.single().remoteId.contains("secret"))
        assertFalse(playlist.channels.single().remoteId.contains("token"))
    }

    @Test
    fun acceptsUtf8BomAndDerivesChannelNumberStableId() {
        val playlist = parser.parse(
            "\uFEFF#EXTM3U\r\n" +
                "#EXTINF:-1 tvg-name=\"ARD HD\" group-title=\"News\" tvg-chno=\"1\",ARD HD\n" +
                "https://stream.example/ard.m3u8\r\n",
        )

        assertEquals(1, playlist.channels.size)
        assertEquals(0, playlist.skippedEntries)
        assertTrue(playlist.channels.single().remoteId.startsWith("channel:name-group-number:"))
    }

    @Test
    fun parsesTenThousandChannelFixtureWithinSmokeBudget() {
        val content = buildString {
            appendLine("#EXTM3U")
            repeat(10_000) { index ->
                val number = index + 1
                appendLine(
                    "#EXTINF:-1 tvg-id=\"channel-$number\" tvg-name=\"Fixture $number\" " +
                        "group-title=\"News\" tvg-chno=\"$number\",Fixture $number",
                )
                appendLine("https://stream.example/channel-$number/index.m3u8")
            }
        }

        lateinit var playlist: M3uPlaylist
        val elapsedMillis = measureTimeMillis {
            playlist = parser.parse(content)
        }

        assertEquals(10_000, playlist.channels.size)
        assertEquals(0, playlist.skippedEntries)
        assertEquals("Fixture 1", playlist.channels.first().name)
        assertEquals("Fixture 10000", playlist.channels.last().name)
        assertTrue("M3U parser smoke budget exceeded: ${elapsedMillis}ms", elapsedMillis < 10_000)
    }
}
