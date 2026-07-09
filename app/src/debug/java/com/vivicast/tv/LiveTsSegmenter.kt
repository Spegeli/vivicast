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
 * no `#EXT-X-ENDLIST`) so ExoPlayer can play it as a live stream with a native DVR window — the no-gap Fall-B
 * design (capture never stops; ExoPlayer always plays the local rolling buffer).
 *
 * Segments are cut at **video keyframes** (`random_access_indicator` in the adaptation field of a
 * `payload_unit_start_indicator` packet on the video PID), and each segment is prefixed with the most recent
 * **PAT + PMT** so it is self-decodable. This is what stops the mid-GOP macroblock artifacts the first naive
 * wall-clock cut produced on seek. The video PID is discovered by parsing PAT (PID 0) → PMT.
 *
 * Not production code — a throwaway de-risk that validates the segmenter before it is promoted to
 * `core/player/timeshift/`.
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

    // PSI state, discovered live from the stream.
    private var pmtPid = -1
    private var videoPid = -1

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

    /**
     * Reads 188-byte TS packets and starts a new segment at each video keyframe (once the current one is at
     * least [MIN_SEGMENT_MS]), keeping the original byte stream intact (no injected packets) so consecutive
     * segments still play back gapless. Cutting at keyframes means a seek lands on a decodable segment start;
     * the stream's own repeated PAT/PMT let ExoPlayer parse each segment on its own.
     */
    private fun segmentStream(input: InputStream) {
        val packet = ByteArray(TS_PACKET)
        var out: BufferedOutputStream? = null
        var startedAtMs = 0L
        try {
            while (scope.isActive) {
                if (!readAlignedPacket(input, packet)) break
                trackPsi(packet)
                val keyframe = isVideoKeyframe(packet)
                val now = System.currentTimeMillis()
                if (out == null) {
                    // Wait for the first keyframe (video PID known) so segment 0 also starts clean.
                    if (keyframe) {
                        android.util.Log.d(TAG, "first keyframe on videoPid=$videoPid, starting segments")
                        out = BufferedOutputStream(currentSegmentFile().outputStream())
                        out.write(packet)
                        startedAtMs = now
                    }
                    continue
                }
                if (keyframe && now - startedAtMs >= MIN_SEGMENT_MS) {
                    out.flush(); out.close()
                    finalizeSegment(now - startedAtMs)
                    out = BufferedOutputStream(currentSegmentFile().outputStream())
                    startedAtMs = now
                }
                out.write(packet)
            }
        } finally {
            out?.let { it.flush(); it.close() }
        }
    }

    /** Learns the PMT PID (from PAT) and the video PID (from PMT) so keyframes can be detected. */
    private fun trackPsi(packet: ByteArray) {
        if (!isPayloadStart(packet)) return
        when (pidOf(packet)) {
            PAT_PID -> parsePmtPid(packet)?.let {
                if (pmtPid != it) { pmtPid = it; android.util.Log.d(TAG, "pmtPid=$it") }
            }
            pmtPid -> if (pmtPid >= 0) parseVideoPid(packet)?.let {
                if (videoPid != it) { videoPid = it; android.util.Log.d(TAG, "videoPid=$it") }
            }
        }
    }

    private fun isVideoKeyframe(packet: ByteArray): Boolean =
        videoPid >= 0 && pidOf(packet) == videoPid && hasRandomAccessIndicator(packet)

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
        val targetSeconds = segments.maxOf { it.durationMs }.div(1000.0).toInt() + 1
        val body = buildString {
            append("#EXTM3U\n")
            append("#EXT-X-VERSION:3\n")
            append("#EXT-X-TARGETDURATION:$targetSeconds\n")
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

    // --- TS packet field helpers ---

    private fun pidOf(packet: ByteArray): Int =
        ((packet[1].toInt() and 0x1F) shl 8) or (packet[2].toInt() and 0xFF)

    private fun isPayloadStart(packet: ByteArray): Boolean = (packet[1].toInt() and 0x40) != 0

    private fun adaptationFieldControl(packet: ByteArray): Int = (packet[3].toInt() shr 4) and 0x03

    /** true when this packet's adaptation field flags the random_access_indicator (a keyframe/IDR entry). */
    private fun hasRandomAccessIndicator(packet: ByteArray): Boolean {
        val afc = adaptationFieldControl(packet)
        if (afc != 0x2 && afc != 0x3) return false
        val afLength = packet[4].toInt() and 0xFF
        if (afLength == 0) return false
        return (packet[5].toInt() and 0x40) != 0
    }

    /** Offset of the payload within the packet, or -1 if there is no payload. */
    private fun payloadOffset(packet: ByteArray): Int = when (adaptationFieldControl(packet)) {
        0x1 -> 4
        0x3 -> 5 + (packet[4].toInt() and 0xFF)
        else -> -1
    }

    /** Start of the PSI section body (past the pointer_field), or -1. */
    private fun sectionStart(packet: ByteArray): Int {
        val payload = payloadOffset(packet)
        if (payload < 0 || payload >= TS_PACKET) return -1
        val pointer = packet[payload].toInt() and 0xFF
        val start = payload + 1 + pointer
        return if (start < TS_PACKET) start else -1
    }

    /** Parses a PAT packet → the first program's PMT PID. */
    private fun parsePmtPid(packet: ByteArray): Int? {
        val start = sectionStart(packet)
        if (start < 0 || (packet[start].toInt() and 0xFF) != TABLE_ID_PAT) return null
        val sectionLength = ((packet[start + 1].toInt() and 0x0F) shl 8) or (packet[start + 2].toInt() and 0xFF)
        val end = minOf(start + 3 + sectionLength - CRC_LEN, TS_PACKET)
        var p = start + PAT_HEADER_LEN
        while (p + 4 <= end) {
            val programNumber = ((packet[p].toInt() and 0xFF) shl 8) or (packet[p + 1].toInt() and 0xFF)
            val pid = ((packet[p + 2].toInt() and 0x1F) shl 8) or (packet[p + 3].toInt() and 0xFF)
            if (programNumber != 0) return pid
            p += 4
        }
        return null
    }

    /** Parses a PMT packet → the first video elementary PID. */
    private fun parseVideoPid(packet: ByteArray): Int? {
        val start = sectionStart(packet)
        if (start < 0 || (packet[start].toInt() and 0xFF) != TABLE_ID_PMT) return null
        val sectionLength = ((packet[start + 1].toInt() and 0x0F) shl 8) or (packet[start + 2].toInt() and 0xFF)
        val end = minOf(start + 3 + sectionLength - CRC_LEN, TS_PACKET)
        val programInfoLength = ((packet[start + 10].toInt() and 0x0F) shl 8) or (packet[start + 11].toInt() and 0xFF)
        var p = start + PMT_HEADER_LEN + programInfoLength
        while (p + 5 <= end) {
            val streamType = packet[p].toInt() and 0xFF
            val elementaryPid = ((packet[p + 1].toInt() and 0x1F) shl 8) or (packet[p + 2].toInt() and 0xFF)
            val esInfoLength = ((packet[p + 3].toInt() and 0x0F) shl 8) or (packet[p + 4].toInt() and 0xFF)
            if (streamType in VIDEO_STREAM_TYPES) return elementaryPid
            p += 5 + esInfoLength
        }
        return null
    }

    private companion object {
        const val TS_PACKET = 188
        const val TS_SYNC = 0x47
        const val PAT_PID = 0x0000
        const val TABLE_ID_PAT = 0x00
        const val TABLE_ID_PMT = 0x02
        const val PAT_HEADER_LEN = 8 // table_id..last_section_number before the program loop
        const val PMT_HEADER_LEN = 12 // ..program_info_length before the ES loop
        const val CRC_LEN = 4
        const val MIN_SEGMENT_MS = 4_000L
        const val MAX_SEGMENTS = 90 // ~6 min rolling window (enough to spike a -2 min seek + edge follow)
        const val READ_BUFFER = 1 shl 16
        const val MAX_BACKOFF_MS = 10_000L
        const val TAG = "LiveTsSegmenter"
        // MPEG-1/2 video, H.264, HEVC.
        val VIDEO_STREAM_TYPES = setOf(0x01, 0x02, 0x1B, 0x24)
    }
}
