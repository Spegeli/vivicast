package com.vivicast.tv.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SyncFingerprintTest {
    @Test
    fun sameContentPartsProduceSameHash() {
        assertEquals(
            syncFingerprint("channel", 42L, true),
            syncFingerprint("channel", 42L, true),
        )
    }

    @Test
    fun differentContentPartsProduceDifferentHash() {
        assertNotEquals(
            syncFingerprint("channel", 42L, true),
            syncFingerprint("channel", 43L, true),
        )
    }

    @Test
    fun nullPartDiffersFromEmptyStringPart() {
        // null must not collapse to "" — an absent field has to fingerprint differently from an empty one.
        assertNotEquals(
            syncFingerprint(null),
            syncFingerprint(""),
        )
    }

    @Test
    fun partSeparatorPreventsCollisionBetweenJoinedValues() {
        // "a" + "b" must not fingerprint the same as the single value "ab".
        assertNotEquals(
            syncFingerprint("a", "b"),
            syncFingerprint("ab"),
        )
    }
}
