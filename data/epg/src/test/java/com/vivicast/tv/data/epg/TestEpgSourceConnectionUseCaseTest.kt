package com.vivicast.tv.data.epg

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class TestEpgSourceConnectionUseCaseTest {

    private val validXmltv = """
        <tv>
          <channel id="c1"><display-name>One</display-name></channel>
          <programme channel="c1" start="20260101000000 +0000" stop="20260101010000 +0000">
            <title>Show</title>
          </programme>
        </tv>
    """.trimIndent()

    private fun sourceOf(content: String) = object : EpgStreamSource {
        override suspend fun open(url: String, block: (InputStream) -> Unit) {
            block(ByteArrayInputStream(content.toByteArray()))
        }
    }

    private val neverOpens = object : EpgStreamSource {
        override suspend fun open(url: String, block: (InputStream) -> Unit) = error("must not open")
    }

    @Test
    fun test_validDocument_returnsSummary() = runBlocking {
        val useCase = TestEpgSourceConnectionUseCase(streamSource = sourceOf(validXmltv))
        val summary = useCase.test("https://example.com/epg.xml")
        assertEquals(EpgContentSummary(channels = 1, programs = 1), summary)
    }

    @Test
    fun test_blankUrl_throwsIllegalArgument() {
        val useCase = TestEpgSourceConnectionUseCase(streamSource = neverOpens)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase.test("   ") }
        }
    }

    @Test
    fun test_documentWithoutChannels_throwsResponseException() {
        val useCase = TestEpgSourceConnectionUseCase(streamSource = sourceOf("<tv></tv>"))
        assertThrows(EpgConnectionResponseException::class.java) {
            runBlocking { useCase.test("https://example.com/epg.xml") }
        }
    }
}
