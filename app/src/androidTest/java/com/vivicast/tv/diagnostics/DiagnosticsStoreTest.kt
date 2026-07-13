package com.vivicast.tv.diagnostics

import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsStoreTest {
    private fun about() = DiagnosticsAbout(
        appVersion = "test",
        packageName = "com.vivicast.tv",
        databaseVersion = 1,
        androidVersion = "test",
        deviceModel = "test-device",
        languageTag = "de-DE",
        timeZoneId = "Europe/Berlin",
    )

    @Test
    fun exportBundlesLogsAndRedactsSensitiveValues() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "vivicast-diagnostics").deleteRecursively()
        val store = DiagnosticsStore(context) { 1_800_000_000_000L }
        store.setConfig(enabled = true)

        store.log(
            category = "refresh",
            message = "playlist_refresh_failed",
            details = mapOf(
                "sourceUrl" to "https://example.org/list.m3u?token=secret",
                "providerName" to "Private Provider",
                "status" to "timeout",
            ),
        )

        val output = ByteArrayOutputStream()
        store.exportZip(output = output, about = about(), settings = null)

        val entries = unzip(output.toByteArray())
        val logEntry = entries.keys.firstOrNull { it.startsWith("logs/") && it.endsWith(".log") }
        assertNotNull("expected a logs/ entry", logEntry)
        assertTrue(entries.containsKey("diagnostics-metadata.json"))
        val log = entries.getValue(logEntry!!)
        assertTrue(log.contains("playlist_refresh_failed"))
        assertTrue(log.contains("status=timeout"))
        assertFalse(log.contains("https://example.org"))
        assertFalse(log.contains("secret"))
        assertFalse(log.contains("Private Provider"))
    }

    @Test
    fun loggingIsGatedByTheToggle() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "vivicast-diagnostics").deleteRecursively()
        val store = DiagnosticsStore(context) { 1_800_000_000_000L }
        store.setConfig(enabled = false)

        store.log("player", "should_not_be_written")

        val output = ByteArrayOutputStream()
        store.exportZip(output = output, about = about(), settings = null)
        val entries = unzip(output.toByteArray())
        assertTrue(entries.keys.none { it.startsWith("logs/") })
        assertTrue(entries.values.none { it.contains("should_not_be_written") })
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return entries
    }
}
