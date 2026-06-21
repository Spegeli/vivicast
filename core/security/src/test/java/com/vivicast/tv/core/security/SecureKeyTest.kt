package com.vivicast.tv.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SecureKeyTest {
    @Test
    fun secureKeyPreservesStableStorageName() {
        assertEquals("provider:demo:credentials", SecureKey("provider:demo:credentials").value)
    }
}
