package com.vivicast.tv.iptv.xtream

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class XtreamXmltvUrlTest {
    @Test
    fun `builds xmltv_php endpoint with credentials`() {
        val url = xtreamXmltvUrl("https://xtream.example", "user", "pass").toHttpUrl()

        assertEquals("https", url.scheme)
        assertEquals("xtream.example", url.host)
        assertEquals("/xmltv.php", url.encodedPath)
        assertEquals("user", url.queryParameter("username"))
        assertEquals("pass", url.queryParameter("password"))
    }

    @Test
    fun `mirrors apiUrl normalisation - trailing slash and path prefix and port`() {
        val url = xtreamXmltvUrl("http://host:8080/xtream/", "u", "p").toHttpUrl()

        assertEquals(8080, url.port)
        assertEquals("/xtream/xmltv.php", url.encodedPath)
    }

    @Test
    fun `url-encodes special characters and round-trips`() {
        val url = xtreamXmltvUrl("https://h", "user name", "p@ss+wörd").toHttpUrl()

        // Decoded values must survive the round-trip so dedup can match server+username reliably.
        assertEquals("user name", url.queryParameter("username"))
        assertEquals("p@ss+wörd", url.queryParameter("password"))
    }

    @Test
    fun `rejects blank inputs`() {
        assertThrows(IllegalArgumentException::class.java) { xtreamXmltvUrl(" ", "u", "p") }
        assertThrows(IllegalArgumentException::class.java) { xtreamXmltvUrl("https://h", "", "p") }
        assertThrows(IllegalArgumentException::class.java) { xtreamXmltvUrl("https://h", "u", "") }
    }
}
