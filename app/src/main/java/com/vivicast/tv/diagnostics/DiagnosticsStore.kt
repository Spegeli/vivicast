package com.vivicast.tv.diagnostics

import android.content.Context
import android.os.Build
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

private const val ROOT_DIR = "vivicast-diagnostics"
private const val LOGS_DIR = "logs"
private const val CRASHES_DIR = "crashes"
private const val LOG_FILE_PREFIX = "vivicast-"
private const val LOG_FILE_EXT = "log"

// Flat rolling-file model (replaces the old session/segment model): one growing file per rotation,
// named by its creation time, in a flat `logs/` folder. Bounds keep the disk in check even if the
// user leaves diagnostics on forever.
private const val ROTATE_BYTES = 5L * 1024 * 1024 // new file after 5 MB
private const val TOTAL_CAP_BYTES = 20L * 1024 * 1024 // keep at most ~20 MB of logs

/**
 * Central diagnostics sink. Feature code never writes here directly — everything goes through the
 * app-layer logger and this [log], which applies the central sanitization before writing. The toggle
 * gates ordinary events; crash logs are written elsewhere (public folder) and are always kept.
 */
class DiagnosticsStore(
    context: Context,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val root = File(context.filesDir, ROOT_DIR)
    private val logsDir = File(root, LOGS_DIR)

    /** Private crash copies bundled into the export (the reachable copies live in public Downloads). */
    val crashDir: File = File(root, CRASHES_DIR)

    @Volatile
    private var enabled = false

    @Volatile
    private var retentionDays = 1

    @Volatile
    private var activeFile: File? = null

    /** Fast check so callers can skip expensive enrichment (e.g. a logcat read) when disabled. */
    val isLoggingEnabled: Boolean
        get() = enabled

    fun setConfig(enabled: Boolean, retentionDays: Int) {
        this.enabled = enabled
        this.retentionDays = retentionDays.coerceIn(1, 7)
        prune()
    }

    /** Writes one sanitized event line. No-op when logging is disabled. Thread-safe. */
    @Synchronized
    fun log(category: String, message: String, details: Map<String, String> = emptyMap()) {
        if (!enabled) return
        logsDir.mkdirs()
        val now = clock()
        val detailText = details.entries
            .sortedBy { it.key }
            .joinToString(" ") { "${DiagnosticsSanitizer.token(it.key)}=${DiagnosticsSanitizer.detail(it.key, it.value)}" }
        val line = buildString {
            append(timestamp(now))
            append(' ')
            append(DiagnosticsSanitizer.token(category))
            append(' ')
            append(DiagnosticsSanitizer.line(message))
            if (detailText.isNotBlank()) {
                append(' ')
                append(detailText)
            }
            append('\n')
        }
        currentFile(now).appendText(line, Charsets.UTF_8)
        prune()
    }

    /** Exports one ZIP: all `logs/`, all private crash copies, and `diagnostics-metadata.json`. */
    fun exportZip(
        output: OutputStream,
        about: DiagnosticsAbout,
        settings: JSONObject? = null,
    ) {
        prune()
        val logFiles = logFiles()
        val crashFiles = crashFiles()
        ZipOutputStream(output).use { zip ->
            logFiles.forEach { file ->
                zip.putNextEntry(ZipEntry("$LOGS_DIR/${file.name}"))
                file.forEachLine(Charsets.UTF_8) { line ->
                    zip.write(DiagnosticsSanitizer.line(line).toByteArray(Charsets.UTF_8))
                    zip.write('\n'.code)
                }
                zip.closeEntry()
            }
            crashFiles.forEach { file ->
                zip.putNextEntry(ZipEntry("$CRASHES_DIR/${file.name}"))
                zip.write(DiagnosticsSanitizer.redact(file.readText(Charsets.UTF_8)).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("diagnostics-metadata.json"))
            zip.write(metadata(about, logFiles, crashFiles, settings).toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun currentFile(now: Long): File {
        val current = activeFile
        if (current != null && current.exists() && current.length() < ROTATE_BYTES) return current
        val fresh = File(logsDir, "$LOG_FILE_PREFIX${fileStamp(now)}.$LOG_FILE_EXT")
        activeFile = fresh
        return fresh
    }

    private fun logFiles(): List<File> =
        logsDir.listFiles { file -> file.isFile && file.name.endsWith(".$LOG_FILE_EXT") }
            ?.sortedBy { it.name }
            .orEmpty()

    private fun crashFiles(): List<File> =
        crashDir.listFiles { file -> file.isFile && file.name.endsWith(".$LOG_FILE_EXT") }
            ?.sortedBy { it.name }
            .orEmpty()

    private fun prune() {
        // 1) retention by age
        val cutoff = clock() - retentionDays.coerceIn(1, 7) * 24L * 60L * 60L * 1000L
        logFiles().filter { it.lastModified() < cutoff && it != activeFile }.forEach { it.delete() }
        // 2) total size cap — drop oldest until under the cap
        var files = logFiles()
        while (files.sumOf { it.length() } > TOTAL_CAP_BYTES) {
            val oldest = files.firstOrNull { it != activeFile } ?: break
            oldest.delete()
            files = logFiles()
        }
    }

    private fun metadata(
        about: DiagnosticsAbout,
        logFiles: List<File>,
        crashFiles: List<File>,
        settings: JSONObject?,
    ): JSONObject {
        val fileEntries = logFiles.map { it to it.readMetrics() }
        val coveredFrom = fileEntries.mapNotNull { it.second.firstAt }.minOrNull()
        val coveredTo = fileEntries.mapNotNull { it.second.lastAt }.maxOrNull()
        return JSONObject()
            .put("appVersion", about.appVersion)
            .put("packageName", about.packageName)
            .put("androidVersion", about.androidVersion)
            .put("deviceModel", about.deviceModel)
            .put("databaseVersion", about.databaseVersion)
            .put("language", about.languageTag)
            .put("timeZone", about.timeZoneId)
            .put("exportedAt", timestamp(clock()))
            .put("retentionDays", retentionDays.coerceIn(1, 7))
            .put("limits", JSONObject().put("rotateBytes", ROTATE_BYTES).put("totalCapBytes", TOTAL_CAP_BYTES))
            .put("coveredFrom", coveredFrom ?: JSONObject.NULL)
            .put("coveredTo", coveredTo ?: JSONObject.NULL)
            .put("contentTruncated", logFiles.sumOf { it.length() } >= TOTAL_CAP_BYTES)
            .put("logFiles", JSONArray().also { arr ->
                fileEntries.forEach { (file, m) ->
                    arr.put(
                        JSONObject()
                            .put("name", file.name)
                            .put("firstAt", m.firstAt ?: JSONObject.NULL)
                            .put("lastAt", m.lastAt ?: JSONObject.NULL)
                            .put("bytes", file.length())
                            .put("eventCount", m.eventCount),
                    )
                }
            })
            .put("crashFiles", JSONArray().also { arr ->
                crashFiles.forEach { file ->
                    arr.put(JSONObject().put("name", file.name).put("bytes", file.length()))
                }
            })
            .put("settings", settings ?: JSONObject())
    }

    private data class FileMetrics(val firstAt: String?, val lastAt: String?, val eventCount: Int)

    private fun File.readMetrics(): FileMetrics {
        var first: String? = null
        var last: String? = null
        var count = 0
        forEachLine(Charsets.UTF_8) { line ->
            if (line.isNotBlank()) {
                count += 1
                val stamp = line.substringBefore(' ').takeIf { it.isNotBlank() }
                if (first == null) first = stamp
                last = stamp
            }
        }
        return FileMetrics(first, last, count)
    }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))

    private fun fileStamp(millis: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(millis))
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
