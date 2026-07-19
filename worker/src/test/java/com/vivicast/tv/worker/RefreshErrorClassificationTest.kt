package com.vivicast.tv.worker

import com.vivicast.tv.iptv.xmltv.UnsafeXmltvEntityException
import com.vivicast.tv.iptv.xtream.XtreamHttpException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RefreshErrorClassificationTest {

    @Test
    fun permanent_errors_are_terminal() {
        assertTrue(RefreshAuthenticationException("missing creds").isTerminalRefreshError())
        assertTrue(RefreshImportException("empty feed").isTerminalRefreshError())
        assertTrue(IllegalArgumentException("type mismatch").isTerminalRefreshError())
        assertTrue(RefreshHttpException(400).isTerminalRefreshError())
        assertTrue(RefreshHttpException(401).isTerminalRefreshError())
        assertTrue(RefreshHttpException(403).isTerminalRefreshError())
        assertTrue(RefreshHttpException(404).isTerminalRefreshError())
        assertTrue(XtreamHttpException(401).isTerminalRefreshError())
        assertTrue(UnsafeXmltvEntityException("billion laughs").isTerminalRefreshError())
    }

    @Test
    fun transient_errors_still_retry() {
        assertFalse(IOException("timeout").isTerminalRefreshError())
        assertFalse(RefreshHttpException(429).isTerminalRefreshError())
        assertFalse(RefreshHttpException(408).isTerminalRefreshError())
        assertFalse(RefreshHttpException(425).isTerminalRefreshError())
        assertFalse(RefreshHttpException(423).isTerminalRefreshError())
        assertFalse(RefreshHttpException(500).isTerminalRefreshError())
        assertFalse(RefreshHttpException(503).isTerminalRefreshError())
        assertFalse(XtreamHttpException(500).isTerminalRefreshError())
    }

    @Test
    fun terminal_http_status_excludes_transient() {
        assertTrue(400.isTerminalHttpStatus())
        assertTrue(404.isTerminalHttpStatus())
        assertFalse(408.isTerminalHttpStatus())
        assertFalse(423.isTerminalHttpStatus())
        assertFalse(425.isTerminalHttpStatus())
        assertFalse(429.isTerminalHttpStatus())
        assertFalse(500.isTerminalHttpStatus())
    }

    @Test
    fun terminal_error_is_detected_through_the_cause_chain() {
        assertTrue(RuntimeException(RefreshHttpException(401)).isTerminalRefreshError())
        assertFalse(RuntimeException(IOException("timeout")).isTerminalRefreshError())
    }
}
