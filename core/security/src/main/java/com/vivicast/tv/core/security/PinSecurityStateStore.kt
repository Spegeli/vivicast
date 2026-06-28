package com.vivicast.tv.core.security

interface PinSecurityStateStore {
    suspend fun read(): PinSecurityState
    suspend fun write(state: PinSecurityState)
    suspend fun clear()
}

class SecureValuePinSecurityStateStore(
    private val secureValueStore: SecureValueStore,
) : PinSecurityStateStore {
    override suspend fun read(): PinSecurityState =
        secureValueStore.read(PIN_SECURITY_STATE_KEY)?.decodePinSecurityState() ?: PinSecurityState()

    override suspend fun write(state: PinSecurityState) {
        secureValueStore.write(PIN_SECURITY_STATE_KEY, state.encodeForStorage())
    }

    override suspend fun clear() {
        secureValueStore.delete(PIN_SECURITY_STATE_KEY)
    }
}

private val PIN_SECURITY_STATE_KEY = SecureKey("security:pin-state")
private const val PIN_STATE_VERSION = "v1"
private const val PIN_STATE_NO_CHECK_VALUE = "-"

internal fun PinSecurityState.encodeForStorage(): String {
    val checkValue = checkValue
    val checkValueFields = if (checkValue == null) {
        listOf(PIN_STATE_NO_CHECK_VALUE, PIN_STATE_NO_CHECK_VALUE, PIN_STATE_NO_CHECK_VALUE)
    } else {
        listOf(checkValue.saltHex, checkValue.hashHex, checkValue.iterations.toString())
    }
    return (listOf(PIN_STATE_VERSION) + checkValueFields + listOf(
        failedAttempts.toString(),
        lockoutCount.toString(),
        lockedUntilMillis.toString(),
        protectSettings.toString(),
        protectMovies.toString(),
        protectSeries.toString(),
        protectAdultContent.toString(),
    )).joinToString("|")
}

internal fun String.decodePinSecurityState(): PinSecurityState? =
    runCatching {
        val parts = split("|")
        if (parts[0] != PIN_STATE_VERSION || parts.size !in setOf(7, 11)) return null
        val checkValue = if (parts[1] == PIN_STATE_NO_CHECK_VALUE) {
            null
        } else {
            PinCheckValue(
                saltHex = parts[1],
                hashHex = parts[2],
                iterations = parts[3].toInt(),
            )
        }
        PinSecurityState(
            checkValue = checkValue,
            failedAttempts = parts[4].toInt().coerceAtLeast(0),
            lockoutCount = parts[5].toInt().coerceAtLeast(0),
            lockedUntilMillis = parts[6].toLong().coerceAtLeast(0L),
            protectSettings = parts.getOrNull(7) == true.toString(),
            protectMovies = parts.getOrNull(8) == true.toString(),
            protectSeries = parts.getOrNull(9) == true.toString(),
            protectAdultContent = parts.getOrNull(10) == true.toString(),
        )
    }.getOrNull()
