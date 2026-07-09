package com.vivicast.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File

/**
 * Debug-only spike component (Phase 3 / Fall B, mechanism K2 — multi-file variant). Captures a live
 * progressive-TS stream over ONE OkHttp connection into **fixed-size segment files** (`seg-<seq>.ts`, each
 * exactly [segmentBytes] except the newest) in [bufferDir], and **front-trims** to keep only the last
 * [maxSegments] (rolling window). The sequence number never resets, so a played [MultiFileTailingDataSource]'s
 * logical offset mapping (`seq = offset / segmentBytes`) stays stable across trims.
 *
 * Validates: seamless live across a segment rollover (no glitch), seek-back within the retained window, and
 * that front-trimmed segments drop out — all on one connection. Bytes are copied verbatim (no re-mux), so the
 * concatenation is byte-identical to the original stream.
 */
class SegmentedTsRecorder(
    private val url: String,
    private val userAgent: String,
    val bufferDir: File,
    private val client: OkHttpClient,
    private val segmentBytes: Long,
    private val maxSegments: Int,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var status: String = "idle"
        private set

    @Volatile
    var capturedBytes: Long = 0L
        private set

    @Volatile
    var segmentCount: Int = 0
        private set

    private var seq = 0
    private var currentLength = 0L
    private var out: BufferedOutputStream? = null

    fun start() {
        bufferDir.mkdirs()
        bufferDir.listFiles()?.forEach { it.delete() }
        scope.launch { captureLoop() }
    }

    fun stop() {
        scope.cancel()
        bufferDir.listFiles()?.forEach { it.delete() }
    }

    private suspend fun captureLoop() {
        var attempt = 0
        while (scope.isActive) {
            try {
                status = "connecting"
                val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("empty body")
                    status = "capturing"
                    attempt = 0
                    val input = body.byteStream()
                    val buffer = ByteArray(READ_BUFFER)
                    while (scope.isActive) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        writeChunk(buffer, read)
                    }
                }
                if (scope.isActive) status = "stream ended, reconnecting"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                status = "retry (${error.message})"
            }
            attempt += 1
            delay(minOf(1000L * attempt, MAX_BACKOFF_MS))
        }
    }

    /** Appends [length] bytes, splitting exactly at [segmentBytes] so completed segments are the same size. */
    private fun writeChunk(data: ByteArray, length: Int) {
        var pos = 0
        while (pos < length) {
            val stream = out ?: BufferedOutputStream(File(bufferDir, "seg-$seq.ts").outputStream()).also {
                out = it
                currentLength = 0L
            }
            val room = (segmentBytes - currentLength).toInt()
            val n = minOf(length - pos, room)
            stream.write(data, pos, n)
            currentLength += n
            capturedBytes += n
            pos += n
            if (currentLength >= segmentBytes) {
                stream.flush()
                stream.close()
                out = null
                seq += 1
                trim()
                segmentCount = bufferDir.list()?.count { it.startsWith("seg-") } ?: 0
            }
        }
        out?.flush()
    }

    /** Keeps only the last [maxSegments] completed segments; deletes those that fell out of the window. */
    private fun trim() {
        val keepFrom = seq - maxSegments
        if (keepFrom <= 0) return
        var s = keepFrom - 1
        while (s >= 0) {
            val file = File(bufferDir, "seg-$s.ts")
            if (!file.exists()) break
            file.delete()
            s -= 1
        }
    }

    private companion object {
        const val READ_BUFFER = 1 shl 16
        const val MAX_BACKOFF_MS = 10_000L
    }
}
