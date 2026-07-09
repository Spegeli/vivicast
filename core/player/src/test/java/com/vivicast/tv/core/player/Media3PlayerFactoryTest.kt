package com.vivicast.tv.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3PlayerFactoryTest {

    @Test
    fun everyBufferTierMapsToValidAscendingDurations() {
        BufferTier.entries.forEach { tier ->
            val d = tier.toBufferDurations()
            assertTrue("min<=max for $tier", d.minMs <= d.maxMs)
            assertTrue("forPlayback>0 for $tier", d.forPlaybackMs > 0)
            assertTrue("afterRebuffer>0 for $tier", d.afterRebufferMs > 0)
        }
        // Spec-signed-off anchors.
        assertEquals(BufferDurations(15000, 30000, 2500, 5000), BufferTier.Medium.toBufferDurations())
        assertEquals(BufferDurations(60000, 120000, 3000, 6000), BufferTier.ExtraLarge.toBufferDurations())
    }

    @Test
    fun builderSubsetIgnoresLanguagePrefsButReflectsBufferChange() {
        // A language change must NOT trigger a player rebuild.
        assertEquals(
            PlaybackTuning(preferredAudio = PlaybackAudioOption.SystemDefault).builderSubset,
            PlaybackTuning(preferredAudio = PlaybackAudioOption.German).builderSubset,
        )
        // A build-time change (buffer/decoder/back-buffer) must.
        assertNotEquals(
            PlaybackTuning(bufferSize = BufferTier.Small).builderSubset,
            PlaybackTuning(bufferSize = BufferTier.Large).builderSubset,
        )
        assertNotEquals(
            PlaybackTuning(audioDecoder = DecoderMode.Hardware).builderSubset,
            PlaybackTuning(audioDecoder = DecoderMode.Software).builderSubset,
        )
    }

    @Test
    fun timeshiftStorageDiskDecision() {
        val long = 60L * 60_000L
        val short = 15L * 60_000L
        assertTrue(usesDiskCache(PlaybackTimeshiftStorage.InternalStorage, short))
        assertFalse(usesDiskCache(PlaybackTimeshiftStorage.Ram, long))
        // Automatic: disk only for windows longer than 30 min.
        assertTrue(usesDiskCache(PlaybackTimeshiftStorage.Automatic, long))
        assertFalse(usesDiskCache(PlaybackTimeshiftStorage.Automatic, short))
        assertFalse(usesDiskCache(PlaybackTimeshiftStorage.Automatic, 30L * 60_000L))
    }
}
