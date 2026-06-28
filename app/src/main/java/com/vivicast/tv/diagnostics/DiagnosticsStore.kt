package com.vivicast.tv.diagnostics

import android.content.Context
import android.os.Build
import com.vivicast.tv.core.datastore.DiagnosticsPreferences
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

private const val LOG_ENTRY_NAME = "vivicast-diagnostics.log"
private const val METADATA_ENTRY_NAME = "diagnostics-metadata.json"
private const val MAX_TOTAL_BYTES = 20 * 1024 * 1024L
private const val MAX_SEGMENT_BYTES = 2 * 1024 * 1024L
private const val MAX_SEGMENTS_PER_SESSION = 3
private const val MAX_SESSION_LOG_BYTES = 6 * 1024 * 1024L

class DiagnosticsStore(
    context: Context,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val root = File(context.filesDir, "vivicast-diagnostics")

    fun setLoggingEnabled(enabled: Boolean, retentionDays: Int) {
        prune(retentionDays)
        record(if (enabled) "diagnostics_enabled" else "diagnostics_disabled", retentionDays)
    }

    fun record(event: String, retentionDays: Int, details: Map<String, String> = emptyMap()) {
        if (!root.exists()) root.mkdirs()
        val now = clock()
        val session = currentSessionDirectory(now)
        if (!session.exists()) session.mkdirs()
        val segment = writableSegment(session)
        val detailText = details.entries
            .sortedBy { it.key }
            .joinToString(separator = " ") { "${sanitize(it.key)}=${sanitizeDetail(it.key, it.value)}" }
        val line = buildString {
            append(timestamp(now))
            append(" event=")
            append(sanitize(event))
            if (detailText.isNotBlank()) {
                append(' ')
                append(detailText)
            }
            append('\n')
        }
        segment.appendText(line, Charsets.UTF_8)
        segment.setLastModified(now)
        session.setLastModified(now)
        prune(retentionDays)
    }

    fun exportZip(
        output: OutputStream,
        about: DiagnosticsAbout,
        preferences: DiagnosticsPreferences,
    ) {
        prune(preferences.retentionDays)
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(LOG_ENTRY_NAME))
            allSegments().forEach { segment ->
                segment.forEachLine(Charsets.UTF_8) { line ->
                    zip.write(sanitize(line).toByteArray(Charsets.UTF_8))
                    zip.write('\n'.code)
                }
            }
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(METADATA_ENTRY_NAME))
            zip.write(metadata(about, preferences).toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun currentSessionDirectory(now: Long): File =
        File(root, "session-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))}")

    private fun writableSegment(session: File): File {
        val existing = session.listFiles { file -> file.name.startsWith("segment-") && file.extension == "log" }
            ?.sortedBy { it.name }
            .orEmpty()
        val current = existing.lastOrNull()
        if (current != null && current.length() < MAX_SEGMENT_BYTES) return current
        val nextIndex = existing.size.coerceAtMost(MAX_SEGMENTS_PER_SESSION - 1)
        return File(session, "segment-${nextIndex.toString().padStart(3, '0')}.log")
    }

    private fun allSegments(): List<File> =
        root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("session-") }
            ?.sortedBy { it.name }
            ?.flatMap { session ->
                session.listFiles { file -> file.name.startsWith("segment-") && file.extension == "log" }
                    ?.sortedBy { it.name }
                    .orEmpty()
            }
            .orEmpty()

    private fun prune(retentionDays: Int) {
        if (!root.exists()) return
        val cutoff = clock() - retentionDays.coerceIn(1, 7) * 24L * 60L * 60L * 1000L
        root.listFiles()
            ?.filter { it.isDirectory && it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
        while (root.walkTopDown().filter { it.isFile }.sumOf { it.length() } > MAX_TOTAL_BYTES) {
            val oldest = root.listFiles()
                ?.filter { it.isDirectory }
                ?.minByOrNull { it.lastModified() }
                ?: return
            oldest.deleteRecursively()
        }
    }

    private fun metadata(about: DiagnosticsAbout, preferences: DiagnosticsPreferences): JSONObject =
        JSONObject()
            .put("appVersion", about.appVersion)
            .put("packageName", about.packageName)
            .put("androidVersion", about.androidVersion)
            .put("deviceModel", about.deviceModel)
            .put("databaseVersion", about.databaseVersion)
            .put("language", about.languageTag)
            .put("timeZone", about.timeZoneId)
            .put("exportedAt", timestamp(clock()))
            .put("retentionDays", preferences.retentionDays.coerceIn(1, 7))
            .put("limits", limitsJson())
            .put("sessions", sessionsJson())

    private fun limitsJson(): JSONObject =
        JSONObject()
            .put("diagnosticsMaxTotalBytes", MAX_TOTAL_BYTES)
            .put("diagnosticsMaxSegmentBytes", MAX_SEGMENT_BYTES)
            .put("diagnosticsMaxSegmentsPerSession", MAX_SEGMENTS_PER_SESSION)
            .put("diagnosticsMaxSessionLogBytes", MAX_SESSION_LOG_BYTES)

    private fun sessionsJson(): JSONArray =
        JSONArray().also { sessions ->
            root.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("session-") }
                ?.sortedBy { it.name }
                ?.forEach { session ->
                    val segments = session.listFiles { file -> file.name.startsWith("segment-") && file.extension == "log" }
                        ?.sortedBy { it.name }
                        .orEmpty()
                    sessions.put(
                        JSONObject()
                            .put("sessionId", session.name)
                            .put("segmentCount", segments.size)
                            .put("bytes", segments.sumOf { it.length() }),
                    )
                }
        }

    private fun sanitize(value: String): String =
        value
            .replace(Regex("https?://\\S+"), "[redacted-url]")
            .replace(Regex("(?i)(token|password|passwd|pwd|cookie|authorization|username|user)=\\S+"), "$1=[redacted]")
            .replace(Regex("[\\r\\n]+"), " ")
            .take(2_000)

    private fun sanitizeDetail(key: String, value: String): String =
        if (key.contains(Regex("(?i)(provider|content|title|name|search|url|header|cookie|token|password|username)"))) {
            "[redacted]"
        } else {
            sanitize(value)
        }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))
}

data class DiagnosticsAbout(
    val appVersion: String,
    val packageName: String,
    val databaseVersion: Int,
    val androidVersion: String,
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
    val languageTag: String = Locale.getDefault().toLanguageTag(),
    val timeZoneId: String = TimeZone.getDefault().id,
)
