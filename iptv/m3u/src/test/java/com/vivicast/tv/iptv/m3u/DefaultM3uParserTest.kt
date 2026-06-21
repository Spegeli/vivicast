package com.vivicast.tv.iptv.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultM3uParserTest {
    private val parser = DefaultM3uParser()

    @Test
    fun parseChannelsWithProviderAttributes() {
        val playlist = parser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="ard.de" tvg-name="Das Erste HD" tvg-logo="https://logos.example/ard.png" group-title="Öffentlich" tvg-chno="1" catchup="default" catchup-days="7",ARD HD
            https://stream.example/ard/index.m3u8
            #EXTINF:-1 tvg-name="ZDF HD" group-title="Öffentlich",Fallback Ignored
            https://stream.example/zdf/index.m3u8
            """.trimIndent(),
        )

        assertEquals(2, playlist.channels.size)
        assertEquals(0, playlist.skippedEntries)

        val ard = playlist.channels[0]
        assertEquals("ard.de", ard.remoteId)
        assertEquals("Das Erste HD", ard.name)
        assertEquals("Öffentlich", ard.categoryName)
        assertEquals("https://logos.example/ard.png", ard.logoUrl)
        assertEquals("1", ard.channelNumber)
        assertTrue(ard.isCatchupAvailable)
        assertEquals(7, ard.catchupDays)

        val zdf = playlist.channels[1]
        assertEquals("ZDF HD", zdf.name)
        assertEquals("https://stream.example/zdf/index.m3u8", zdf.streamUrl)
        assertNotEquals(zdf.streamUrl, zdf.remoteId)
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
}
