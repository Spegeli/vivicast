package com.vivicast.tv.data.provider

import com.vivicast.tv.domain.model.LogoSource.Epg
import com.vivicast.tv.domain.model.LogoSource.Local
import com.vivicast.tv.domain.model.LogoSource.Playlist
import com.vivicast.tv.domain.model.parseLogoPriorityOrder
import com.vivicast.tv.domain.model.serializeLogoPriorityOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class LogoPriorityTest {
    @Test fun blankOrNullYieldsDefaultOrder() {
        assertEquals(listOf(Playlist, Epg, Local), parseLogoPriorityOrder(null))
        assertEquals(listOf(Playlist, Epg, Local), parseLogoPriorityOrder(""))
        assertEquals(listOf(Playlist, Epg, Local), parseLogoPriorityOrder("   "))
    }

    @Test fun legacySingleTokensExpandToTheirHistoricalOrder() {
        assertEquals(listOf(Playlist, Epg, Local), parseLogoPriorityOrder("playlist"))
        assertEquals(listOf(Epg, Playlist, Local), parseLogoPriorityOrder("epg"))
        assertEquals(listOf(Local, Playlist, Epg), parseLogoPriorityOrder("local"))
        // The pre-refactor backup default; unknown single tokens fall through to the default order.
        assertEquals(listOf(Playlist, Epg, Local), parseLogoPriorityOrder("provider"))
        assertEquals(listOf(Playlist, Epg, Local), parseLogoPriorityOrder("bogus"))
    }

    @Test fun csvKeepsOrderIncludingLocalInTheMiddle() {
        assertEquals(listOf(Epg, Local, Playlist), parseLogoPriorityOrder("epg,local,playlist"))
        assertEquals(listOf(Playlist, Local, Epg), parseLogoPriorityOrder("playlist,local,epg"))
    }

    @Test fun csvDedupesAndAppendsMissingSources() {
        assertEquals(listOf(Epg, Playlist, Local), parseLogoPriorityOrder("epg,epg,playlist"))
        assertEquals(listOf(Epg, Playlist, Local), parseLogoPriorityOrder("epg, bogus"))
        assertEquals(listOf(Local, Playlist, Epg), parseLogoPriorityOrder("local"))
    }

    @Test fun serializeRoundTrips() {
        assertEquals("epg,local,playlist", serializeLogoPriorityOrder(parseLogoPriorityOrder("epg,local,playlist")))
        assertEquals("playlist,epg,local", serializeLogoPriorityOrder(parseLogoPriorityOrder(null)))
    }

    @Test fun normalizeProducesCanonicalCsv() {
        assertEquals("epg,playlist,local", normalizeLogoPriority("epg"))
        assertEquals("playlist,epg,local", normalizeLogoPriority(null))
        assertEquals("playlist,local,epg", normalizeLogoPriority("playlist,local,epg"))
    }
}
