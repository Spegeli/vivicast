package com.vivicast.tv.core.player

import org.junit.Assert.assertEquals
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
            // DefaultLoadControl.build() asserts these two; a violation throws at buildExoPlayer (crashed
            // the player when Buffer=Off shipped as min 1000 < afterRebuffer 1500).
            assertTrue("min>=forPlayback for $tier", d.minMs >= d.forPlaybackMs)
            assertTrue("min>=afterRebuffer for $tier", d.minMs >= d.afterRebufferMs)
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
}
