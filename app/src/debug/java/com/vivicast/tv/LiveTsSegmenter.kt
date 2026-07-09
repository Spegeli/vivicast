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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * Debug-only spike component (Phase 3 / Fall B, mechanism K1). Captures a progressive MPEG-TS live stream over
 * ONE OkHttp connection and re-emits it as a **local rolling live HLS** playlist (`segment-N.ts` + `index.m3u8`,
 * no `#EXT-X-ENDLIST`) so ExoPlayer can play it as a live stream with a native DVR window — the whole point of
 * the no-gap Fall-B design (capture never stops; ExoPlayer always plays the local rolling buffer).
 *
 * Simplest possible segmenter first: **wall-clock ~[SEGMENT_MS] cuts at TS-packet (188-byte) boundaries, no
 * PSI/keyframe alignment.** The spike checks whether ExoPlayer plays + seeks + follows the edge of this. If the
 * decode glitches at segment starts (mid-GOP), the next iteration cuts at `random_access_indicator` boundaries
 * with prepended PAT/PMT. Not production code — throwaway de-risk.
 */
class LiveTsSegmenter(
    private val url: String,
    private val userAgent: String,
    private val outputDir: File,
    private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var status: String = "idle"
        private set

    @Volatile
    var segmentCount: Int = 0
        private set

    val playlistFile = File(outputDir, "index.m3u8")

    private data class SegmentRef(val seq: Int, val file: File, val durationMs: Long)

    private val window = ArrayDeque<SegmentRef>()
    private var nextSeq = 0

    fun start() {
        outputDir.mkdirs()
        outputDir.listFiles()?.forEach { it.delete() }
        scope.launch { captureLoop() }
    }

    fun stop() {
        scope.cancel()
        outputDir.listFiles()?.forEach { it.delete() }
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
                    segmentStream(BufferedInputStream(body.byteStream(), READ_BUFFER))
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

    /** Reads 188-byte TS packets and slices to a new segment every [SEGMENT_MS] at a packet boundary. */
    private fun segmentStream(input: InputStream) {
        val packet = ByteArray(TS_PACKET)
        var out = BufferedOutputStream(currentSegmentFile().outputStream())
        var startedAtMs = System.currentTimeMillis()
        try {
            while (scope.isActive) {
                if (!readAlignedPacket(input, packet)) break
                out.write(packet)
                val now = System.currentTimeMillis()
                if (now - startedAtMs >= SEGMENT_MS) {
                    out.flush(); out.close()
                    finalizeSegment(now - startedAtMs)
                    out = BufferedOutputStream(currentSegmentFile().outputStream())
                    startedAtMs = now
                }
            }
        } finally {
            out.flush(); out.close()
        }
    }

    private fun currentSegmentFile() = File(outputDir, "segment-$nextSeq.ts")

    private fun finalizeSegment(durationMs: Long) {
        val ref = SegmentRef(nextSeq, currentSegmentFile(), durationMs)
        nextSeq += 1
        window.addLast(ref)
        while (window.size > MAX_SEGMENTS) {
            window.removeFirst().file.delete()
        }
        writePlaylist()
        segmentCount = window.size
    }

    private fun writePlaylist() {
        val segments = window.toList()
        if (segments.isEmpty()) return
        val body = buildString {
            append("#EXTM3U\n")
            append("#EXT-X-VERSION:3\n")
            append("#EXT-X-TARGETDURATION:${SEGMENT_MS / 1000 + 1}\n")
            append("#EXT-X-MEDIA-SEQUENCE:${segments.first().seq}\n")
            for (segment in segments) {
                // Locale.US so the EXTINF decimal is a dot, not a locale comma (would break the playlist).
                append(String.format(Locale.US, "#EXTINF:%.3f,\n", segment.durationMs / 1000.0))
                append("${segment.file.name}\n")
            }
        }
        val tmp = File(outputDir, "index.m3u8.tmp")
        tmp.writeText(body)
        tmp.renameTo(playlistFile)
    }

    /** Reads exactly one 188-byte packet, resyncing to the next 0x47 sync byte if alignment was lost. */
    private fun readAlignedPacket(input: InputStream, packet: ByteArray): Boolean {
        var first = input.read()
        if (first < 0) return false
        while (first != TS_SYNC) {
            first = input.read()
            if (first < 0) return false
        }
        packet[0] = TS_SYNC.toByte()
        var offset = 1
        while (offset < TS_PACKET) {
            val read = input.read(packet, offset, TS_PACKET - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private companion object {
        const val TS_PACKET = 188
        const val TS_SYNC = 0x47
        const val SEGMENT_MS = 4_000L
        const val MAX_SEGMENTS = 90 // ~6 min rolling window (enough to spike a -2 min seek + edge follow)
        const val READ_BUFFER = 1 shl 16
        const val MAX_BACKOFF_MS = 10_000L
    }
}
