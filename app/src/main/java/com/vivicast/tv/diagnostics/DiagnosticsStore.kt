package com.vivicast.tv.diagnostics

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
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
private const val VIEW_TAIL_BYTES = 64 * 1024 // last ~64 KB shown by the in-app log viewer

/**
 * Central diagnostics sink. Feature code never writes here directly — everything goes through the
 * app-layer logger and this [log], which applies the central sanitization before writing. The toggle
 * gates ordinary events; crash logs are written elsewhere (public folder) and are always kept.
 */
class DiagnosticsStore(
    context: Context,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val appContext = context.applicationContext
    private val root = File(context.filesDir, ROOT_DIR)
    private val logsDir = File(root, LOGS_DIR)

    /** Private crash copies bundled into the export (the reachable copies live in public Downloads). */
    val crashDir: File = File(root, CRASHES_DIR)

    @Volatile
    private var enabled = false

    // Diagnostics logs are kept a fixed 7 days; retention is not user-configurable (D35).
    private val retentionDays = 7

    @Volatile
    private var activeFile: File? = null

    // Count of log files evicted by the size cap (not age). Signals real data loss in the export
    // metadata. In-memory: resets on process restart — good enough for a single diagnose session.
    // ponytail: in-memory counter, persist only if cross-restart truncation reporting is ever needed.
    @Volatile
    private var discardedByCap = 0

    /** Fast check so callers can skip expensive enrichment (e.g. a logcat read) when disabled. */
    val isLoggingEnabled: Boolean
        get() = enabled

    fun setConfig(enabled: Boolean) {
        this.enabled = enabled
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

    /**
     * Deletes every event-log file, including the active one. Safe while logging is ON: the next [log]
     * sees the active file gone (`exists()` is false) and starts a fresh one — no restart needed.
     */
    @Synchronized
    fun clearLogs() {
        logFiles().forEach { it.delete() }
        activeFile = null
    }

    /** Deletes crash logs: the private copies (export source) and the public Downloads/Vivicast/Crashes copies. */
    fun clearCrashes() {
        crashFiles().forEach { it.delete() }
        runCatching { clearPublicCrashes() }
    }

    /** Tail of the newest event-log file (last ~64 KB), sanitized per line. Null when there are none. */
    fun readLatestLog(): String? = readTail(logFiles().lastOrNull())

    /** Tail of the newest private crash file (already redacted on write), re-sanitized. Null when none. */
    fun readLatestCrash(): String? = readTail(crashFiles().lastOrNull())

    private fun readTail(file: File?): String? {
        if (file == null || !file.exists()) return null
        return runCatching {
            val length = file.length()
            val fromByte = (length - VIEW_TAIL_BYTES).coerceAtLeast(0L)
            val bytes = ByteArray((length - fromByte).toInt())
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(fromByte)
                raf.readFully(bytes)
            }
            var text = String(bytes, Charsets.UTF_8)
            // Drop the (likely partial) first line when we started mid-file.
            if (fromByte > 0) text = text.substringAfter('\n')
            text.lineSequence().joinToString("\n") { DiagnosticsSanitizer.line(it) }
        }.getOrNull()
    }

    private fun clearPublicCrashes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%$CRASH_SUBDIR%", "$CRASH_PREFIX%")
            val ids = mutableListOf<Long>()
            resolver.query(collection, arrayOf(MediaStore.Downloads._ID), selection, args, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                while (cursor.moveToNext()) ids.add(cursor.getLong(idColumn))
            }
            ids.forEach { id ->
                runCatching { resolver.delete(ContentUris.withAppendedId(collection, id), null, null) }
            }
        } else {
            File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), CRASH_SUBDIR)
                .listFiles { file -> file.isFile && file.name.startsWith(CRASH_PREFIX) }
                ?.forEach { it.delete() }
        }
    }

    private fun currentFile(now: Long): File {
        val current = activeFile
        if (current != null && current.exists() && current.length() < ROTATE_BYTES) return current
        val fresh = File(logsDir, "$LOG_FILE_PREFIX${fileStamp(now)}.$LOG_FILE_EXT")
        activeFile = fresh
        // Rotation point: bound age/size here instead of on every line (per-line prune would do a
        // directory stat + delete scan for each event, amplifying the very freeze it's meant to detect).
        prune()
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
        val cutoff = clock() - retentionDays * 24L * 60L * 60L * 1000L
        logFiles().filter { it.lastModified() < cutoff && it != activeFile }.forEach { it.delete() }
        // 2) total size cap — drop oldest until under the cap
        var files = logFiles()
        while (files.sumOf { it.length() } > TOTAL_CAP_BYTES) {
            val oldest = files.firstOrNull { it != activeFile } ?: break
            oldest.delete()
            discardedByCap += 1
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
            .put("buildNumber", about.buildNumber)
            .put("packageName", about.packageName)
            .put("androidVersion", about.androidVersion)
            .put("deviceModel", about.deviceModel)
            .put("playerEngine", about.playerEngine)
            .put("buildType", about.buildType)
            .put("cpuAbi", about.cpuAbi)
            .put("databaseVersion", about.databaseVersion)
            .put("language", about.languageTag)
            .put("timeZone", about.timeZoneId)
            .put("exportedAt", timestamp(clock()))
            .put("retentionDays", retentionDays)
            .put("limits", JSONObject().put("rotateBytes", ROTATE_BYTES).put("totalCapBytes", TOTAL_CAP_BYTES))
            .put("coveredFrom", coveredFrom ?: JSONObject.NULL)
            .put("coveredTo", coveredTo ?: JSONObject.NULL)
            .put("contentTruncated", discardedByCap > 0)
            .put("droppedFiles", discardedByCap)
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
    val buildNumber: String = "0",
    val playerEngine: String = "Media3/ExoPlayer",
    val buildType: String = "Unbekannt",
    val cpuAbi: String = "Unbekannt",
    val languageTag: String = Locale.getDefault().toLanguageTag(),
    val timeZoneId: String = TimeZone.getDefault().id,
)
