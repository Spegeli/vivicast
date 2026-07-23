package com.vivicast.tv.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VivicastTopNavClockTest {
    @Test
    fun clockTextIs24hHhMm() {
        // Any instant formats to two-digit HH:mm (24h), regardless of the machine's timezone.
        val text = topNavClockText(1_609_509_900_000L)
        assertTrue("expected HH:mm, got $text", Regex("""\d{2}:\d{2}""").matches(text))
    }

    @Test
    fun alignsToNextFullMinute() {
        assertEquals(60_000L, millisUntilNextMinute(0L))
        assertEquals(59_000L, millisUntilNextMinute(1_000L))
        assertEquals(1L, millisUntilNextMinute(59_999L))
    }
}
