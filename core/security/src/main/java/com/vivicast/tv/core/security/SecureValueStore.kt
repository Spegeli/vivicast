package com.vivicast.tv.core.security

interface SecureValueStore {
    suspend fun read(key: SecureKey): String?
    suspend fun write(key: SecureKey, value: String)
    suspend fun delete(key: SecureKey)
}

@JvmInline
value class SecureKey(val value: String)
