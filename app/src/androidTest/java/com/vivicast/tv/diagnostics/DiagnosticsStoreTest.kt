package com.vivicast.tv.diagnostics

import androidx.test.core.app.ApplicationProvider
import com.vivicast.tv.core.datastore.DiagnosticsPreferences
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsStoreTest {
    @Test
    fun exportZipContainsRequiredEntriesAndRedactsSensitiveValues() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "vivicast-diagnostics").deleteRecursively()
        val store = DiagnosticsStore(context) { 1_800_000_000_000L }

        store.record(
            event = "playlist_refresh_failed",
            retentionDays = 1,
            details = mapOf(
                "sourceUrl" to "https://example.org/list.m3u?token=secret",
                "providerName" to "Private Provider",
                "status" to "timeout",
            ),
        )

        val output = ByteArrayOutputStream()
        store.exportZip(
            output = output,
            about = DiagnosticsAbout(
                appVersion = "test",
                packageName = "com.vivicast.tv",
                databaseVersion = 1,
                androidVersion = "test",
                deviceModel = "test-device",
                languageTag = "de-DE",
                timeZoneId = "Europe/Berlin",
            ),
            preferences = DiagnosticsPreferences(diagnosticsLoggingEnabled = true, retentionDays = 1),
        )

        val entries = unzip(output.toByteArray())
        assertTrue(entries.containsKey("vivicast-diagnostics.log"))
        assertTrue(entries.containsKey("diagnostics-metadata.json"))
        val log = entries.getValue("vivicast-diagnostics.log")
        assertTrue(log.contains("playlist_refresh_failed"))
        assertTrue(log.contains("status=timeout"))
        assertFalse(log.contains("https://example.org"))
        assertFalse(log.contains("secret"))
        assertFalse(log.contains("Private Provider"))
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
