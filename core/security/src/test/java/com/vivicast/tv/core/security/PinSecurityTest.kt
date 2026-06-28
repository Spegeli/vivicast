package com.vivicast.tv.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSecurityTest {
    @Test
    fun pinCheckStoresOnlySaltedVerifier() {
        val checkValue = PinSecurity.createCheckValue("1234", iterations = TEST_ITERATIONS)

        assertTrue(PinSecurity.verify("1234", checkValue))
        assertFalse(PinSecurity.verify("1235", checkValue))
        assertFalse(checkValue.hashHex.contains("1234"))
        assertFalse(checkValue.saltHex.contains("1234"))
    }

    @Test
    fun invalidPinCannotCreateCheckValue() {
        runCatching { PinSecurity.createCheckValue("12a4", iterations = TEST_ITERATIONS) }
            .onSuccess { throw AssertionError("Invalid PIN was accepted.") }
    }

    @Test
    fun fiveFailuresLockPinForThirtySeconds() {
        var state = PinSecurityState(
            checkValue = PinSecurity.createCheckValue("1234", iterations = TEST_ITERATIONS),
        )

        repeat(4) {
            val result = PinSecurity.verifyAndUpdate("0000", state, nowMillis = 1_000L)
            state = (result as PinVerificationResult.Failure).state
            assertEquals(4 - it, result.remainingAttempts)
        }

        val locked = PinSecurity.verifyAndUpdate("0000", state, nowMillis = 1_000L) as PinVerificationResult.Locked

        assertEquals(31_000L, locked.lockedUntilMillis)
        assertEquals(1, locked.state.lockoutCount)
        assertEquals(0, locked.state.failedAttempts)
    }

    @Test
    fun lockoutPersistsUntilTimeExpiresAndEscalates() {
        val checkValue = PinSecurity.createCheckValue("1234", iterations = TEST_ITERATIONS)
        var state = PinSecurityState(checkValue = checkValue, lockoutCount = 1, lockedUntilMillis = 31_000L)

        val stillLocked = PinSecurity.verifyAndUpdate("1234", state, nowMillis = 30_999L)
        assertEquals(31_000L, (stillLocked as PinVerificationResult.Locked).lockedUntilMillis)

        repeat(5) {
            val result = PinSecurity.verifyAndUpdate("0000", state.copy(lockedUntilMillis = 0L), nowMillis = 32_000L)
            state = when (result) {
                is PinVerificationResult.Failure -> result.state
                is PinVerificationResult.Locked -> result.state
                else -> throw AssertionError("Unexpected result: $result")
            }
        }

        assertEquals(92_000L, state.lockedUntilMillis)
        assertEquals(2, state.lockoutCount)
    }

    @Test
    fun successfulPinResetsFailuresAndLockoutEscalation() {
        val checkValue = PinSecurity.createCheckValue("1234", iterations = TEST_ITERATIONS)
        val state = PinSecurityState(
            checkValue = checkValue,
            failedAttempts = 2,
            lockoutCount = 2,
        )

        val result = PinSecurity.verifyAndUpdate("1234", state, nowMillis = 1_000L) as PinVerificationResult.Success

        assertEquals(0, result.state.failedAttempts)
        assertEquals(0, result.state.lockoutCount)
        assertEquals(0L, result.state.lockedUntilMillis)
    }

    @Test
    fun pinSecurityStateStorageRoundTrips() {
        val state = PinSecurityState(
            checkValue = PinSecurity.createCheckValue("1234", iterations = TEST_ITERATIONS),
            failedAttempts = 2,
            lockoutCount = 1,
            lockedUntilMillis = 31_000L,
            protectMovies = true,
            protectSeries = true,
            protectAdultContent = true,
        )

        assertEquals(state, state.encodeForStorage().decodePinSecurityState())
    }

    @Test
    fun oldPinSecurityStateStorageDefaultsProtectionAreasOff() {
        val state = "v1|-|-|-|0|0|0".decodePinSecurityState()

        assertEquals(PinSecurityState(), state)
    }

    @Test
    fun damagedPinSecurityStateIsIgnored() {
        assertEquals(null, "not-a-pin-state".decodePinSecurityState())
    }

    private companion object {
        const val TEST_ITERATIONS = 1_000
    }
}
