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
 * Debug-only spike component (Phase 3 / Fall B, mechanism **K2** — concat, no re-mux). Captures a progressive
 * MPEG-TS live stream over ONE OkHttp connection and appends the bytes **verbatim** to a single [bufferFile].
 * Because the file is byte-identical to the original stream, it is a conformant MPEG-TS that ExoPlayer's
 * TsExtractor can seek and any decoder (HW or SW) can decode — the point K1 (hand re-segmenting) failed on real
 * hardware. This spike checks exactly that on the TV: play the concat file, is it clean + seekable?
 *
 * No chunking / eviction here — the spike just proves the concat is decodable + seekable. The rolling-window
 * trim and the growing-edge (tailing) playback for the no-gap production build come after this proof.
 */
class RawTsRecorder(
    private val url: String,
    private val userAgent: String,
    private val bufferFile: File,
    private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var status: String = "idle"
        private set

    @Volatile
    var capturedBytes: Long = 0L
        private set

    fun start() {
        bufferFile.parentFile?.mkdirs()
        bufferFile.delete()
        scope.launch { captureLoop() }
    }

    fun stop() {
        scope.cancel()
        bufferFile.delete()
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
                    BufferedOutputStream(bufferFile.outputStream()).use { out ->
                        val buffer = ByteArray(READ_BUFFER)
                        while (scope.isActive) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            out.flush()
                            capturedBytes += read
                        }
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

    private companion object {
        const val READ_BUFFER = 1 shl 16
        const val MAX_BACKOFF_MS = 10_000L
    }
}
