package com.vivicast.tv.diagnostics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val CRASH_PREFIX = "vivicast-crash"
private const val MAX_CRASH_FILES = 10
private const val LOGCAT_LINES = "400"
private const val CRASH_SUBDIR = "Vivicast/Crashes"

/**
 * Always-on crash capture — independent of the diagnostics toggle (a crash can happen before the user
 * ever enables logging, and is exactly what a "the app won't start" report needs). Each uncaught
 * exception writes its own sanitized file:
 *  - a public copy in Downloads/Vivicast/Crashes (reachable by a file manager even if the app is dead),
 *  - a private copy that gets bundled into the diagnostics export.
 * A logcat snapshot of the own process (the lines leading up to the crash) is appended, also sanitized.
 */
class CrashLogWriter(
    private val context: Context,
    private val privateCrashDir: File,
    private val about: DiagnosticsAbout,
) {
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(thread: Thread, throwable: Throwable) {
        val now = System.currentTimeMillis()
        val version = about.appVersion.trim().takeIf { it.isNotBlank() } ?: "unknown"
        val fileName = "${CRASH_PREFIX}_${fileStamp(now)}_v$version.log"
        val content = buildString {
            append("timestamp=").append(timestamp(now)).append('\n')
            append("appVersion=").append(about.appVersion)
                .append(" package=").append(about.packageName)
                .append(" android=").append(about.androidVersion)
                .append(" device=").append(about.deviceModel)
                .append(" db=").append(about.databaseVersion).append('\n')
            append("thread=").append(thread.name).append('\n')
            append("---- stacktrace ----\n")
            append(DiagnosticsSanitizer.redact(throwable.stackTraceToString())).append('\n')
            append("---- logcat (own process) ----\n")
            append(DiagnosticsSanitizer.redact(logcatSnapshot()))
        }
        // Private copy — bundled into the export.
        runCatching {
            privateCrashDir.mkdirs()
            File(privateCrashDir, fileName).writeText(content, Charsets.UTF_8)
            pruneDir(privateCrashDir)
        }
        // Public copy — reachable via a file manager even if the app never starts again.
        runCatching { writePublic(fileName, content.toByteArray(Charsets.UTF_8)) }
    }

    private fun writePublic(fileName: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$CRASH_SUBDIR")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), CRASH_SUBDIR).apply { mkdirs() }
            File(dir, fileName).writeBytes(bytes)
        }
    }

    private fun logcatSnapshot(): String =
        runCatching {
            Runtime.getRuntime()
                .exec(arrayOf("logcat", "-d", "-v", "time", "-t", LOGCAT_LINES, "--pid=${Process.myPid()}"))
                .inputStream.bufferedReader().use { it.readText() }
        }.getOrDefault("")

    private fun pruneDir(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(CRASH_PREFIX) }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.size > MAX_CRASH_FILES) files.take(files.size - MAX_CRASH_FILES).forEach { it.delete() }
    }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    private fun fileStamp(millis: Long): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(millis))
}
