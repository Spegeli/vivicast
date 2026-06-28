package com.vivicast.tv.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinCheckValue(
    val saltHex: String,
    val hashHex: String,
    val iterations: Int = DEFAULT_PIN_HASH_ITERATIONS,
)

data class PinSecurityState(
    val checkValue: PinCheckValue? = null,
    val failedAttempts: Int = 0,
    val lockoutCount: Int = 0,
    val lockedUntilMillis: Long = 0L,
    val protectSettings: Boolean = false,
    val protectMovies: Boolean = false,
    val protectSeries: Boolean = false,
    val protectAdultContent: Boolean = false,
) {
    val hasPin: Boolean
        get() = checkValue != null
}

sealed interface PinVerificationResult {
    data class Success(val state: PinSecurityState) : PinVerificationResult
    data class Failure(val remainingAttempts: Int, val state: PinSecurityState) : PinVerificationResult
    data class Locked(val lockedUntilMillis: Long, val state: PinSecurityState) : PinVerificationResult
    data class MissingPin(val state: PinSecurityState) : PinVerificationResult
}

object PinSecurity {
    fun createCheckValue(
        pin: String,
        random: SecureRandom = SecureRandom(),
        iterations: Int = DEFAULT_PIN_HASH_ITERATIONS,
    ): PinCheckValue {
        require(pin.isValidPin()) { "PIN must contain exactly four digits." }
        val salt = ByteArray(PIN_SALT_BYTES).also(random::nextBytes)
        val hash = pin.hashPin(salt, iterations)
        return PinCheckValue(
            saltHex = salt.toHex(),
            hashHex = hash.toHex(),
            iterations = iterations,
        )
    }

    fun verify(pin: String, checkValue: PinCheckValue): Boolean {
        if (!pin.isValidPin()) return false
        val salt = checkValue.saltHex.hexToBytes()
        val expected = checkValue.hashHex.hexToBytes()
        val actual = pin.hashPin(salt, checkValue.iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    fun verifyAndUpdate(
        pin: String,
        state: PinSecurityState,
        nowMillis: Long,
    ): PinVerificationResult {
        val checkValue = state.checkValue ?: return PinVerificationResult.MissingPin(state)
        if (state.lockedUntilMillis > nowMillis) {
            return PinVerificationResult.Locked(state.lockedUntilMillis, state)
        }
        if (verify(pin, checkValue)) {
            return PinVerificationResult.Success(
                state.copy(failedAttempts = 0, lockoutCount = 0, lockedUntilMillis = 0L),
            )
        }

        val failedAttempts = state.failedAttempts + 1
        if (failedAttempts < PIN_FAILURES_BEFORE_LOCK) {
            val failedState = state.copy(failedAttempts = failedAttempts, lockedUntilMillis = 0L)
            return PinVerificationResult.Failure(PIN_FAILURES_BEFORE_LOCK - failedAttempts, failedState)
        }

        val lockedUntilMillis = nowMillis + state.lockoutDurationMillis()
        val lockedState = state.copy(
            failedAttempts = 0,
            lockoutCount = state.lockoutCount + 1,
            lockedUntilMillis = lockedUntilMillis,
        )
        return PinVerificationResult.Locked(lockedUntilMillis, lockedState)
    }

    fun setPin(pin: String): PinSecurityState =
        PinSecurityState(checkValue = createCheckValue(pin))

    fun clearPin(): PinSecurityState = PinSecurityState()
}

const val DEFAULT_PIN_HASH_ITERATIONS = 120_000

private const val PIN_LENGTH = 4
private const val PIN_SALT_BYTES = 16
private const val PIN_HASH_BYTES = 32
private const val PIN_FAILURES_BEFORE_LOCK = 5
private val PIN_LOCKOUT_DURATIONS_MILLIS = longArrayOf(30_000L, 60_000L, 300_000L)

private fun String.isValidPin(): Boolean =
    length == PIN_LENGTH && all(Char::isDigit)

private fun String.hashPin(salt: ByteArray, iterations: Int): ByteArray {
    val chars = toCharArray()
    return try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(chars, salt, iterations, PIN_HASH_BYTES * 8))
            .encoded
    } finally {
        chars.fill('\u0000')
    }
}

private fun PinSecurityState.lockoutDurationMillis(): Long =
    PIN_LOCKOUT_DURATIONS_MILLIS[lockoutCount.coerceAtMost(PIN_LOCKOUT_DURATIONS_MILLIS.lastIndex)]

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex value must have an even length." }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
