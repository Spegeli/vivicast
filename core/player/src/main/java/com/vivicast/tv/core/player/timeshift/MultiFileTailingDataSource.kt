package com.vivicast.tv.core.player.timeshift

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * A [DataSource] for a live capture stored as **fixed-size segment files** (`seg-<seq>.ts`, each exactly
 * [segmentBytes] except the newest, which is still growing) in one directory. It presents the segments as a
 * single logical byte stream: logical offset `o` maps deterministically to segment `o / segmentBytes` at
 * in-file offset `o % segmentBytes` — so no shared index/registry is needed and the mapping stays stable even
 * when old segments are front-trimmed (their logical range is simply reserved).
 *
 * This is the Fall B / K2 production playback source (multi-file variant): it follows the growing edge (blocks
 * at the newest segment's current end instead of ending) and stays seekable back into the retained window;
 * front-trimmed offsets are reported as gone. The single-file [TailingFileDataSource] is the degenerate case.
 */
@UnstableApi
class MultiFileTailingDataSource(
    private val segmentBytes: Long,
    private val pollMillis: Long = DEFAULT_POLL_MILLIS,
    private val maxWaitMillis: Long = DEFAULT_MAX_WAIT_MILLIS,
) : BaseDataSource(/* isNetwork = */ false) {

    private var dir: File? = null
    private var uri: Uri? = null
    private var logicalPosition: Long = 0L
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val path = dataSpec.uri.path ?: throw IllegalArgumentException("MultiFileTailingDataSource requires a dir uri")
        dir = File(path)
        logicalPosition = dataSpec.position
        bytesRemaining = dataSpec.length // LENGTH_UNSET unless a bounded range was requested
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val directory = dir ?: return C.RESULT_END_OF_INPUT
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val seq = logicalPosition / segmentBytes
        val within = logicalPosition % segmentBytes
        val segment = File(directory, "seg-$seq.ts")

        // Wait for the current byte to become available (the writer may not have produced it yet).
        var waited = 0L
        while (!hasBytesAt(segment, within, directory, seq) && waited < maxWaitMillis) {
            Thread.sleep(pollMillis)
            waited += pollMillis
        }

        val available = availableAt(segment, within, directory, seq)
        if (available <= 0L) return C.RESULT_END_OF_INPUT // still nothing after waiting → treat as end

        var toRead = minOf(length.toLong(), available)
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) toRead = minOf(toRead, bytesRemaining)

        val read = RandomAccessFile(segment, "r").use { raf ->
            raf.seek(within)
            raf.read(buffer, offset, toRead.toInt())
        }
        if (read <= 0) return C.RESULT_END_OF_INPUT
        logicalPosition += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    /** Bytes readable at [within] in [segment] right now (0 if the segment isn't there / not yet that long). */
    private fun availableAt(segment: File, within: Long, directory: File, seq: Long): Long {
        if (segment.exists()) {
            val end = if (isNewest(directory, seq)) segment.length() else segmentBytes
            return (end - within).coerceAtLeast(0L)
        }
        // Missing: either front-trimmed (older than what we keep) or simply not written yet.
        if (isTrimmed(directory, seq)) {
            throw IOException("segment $seq was trimmed (outside the rolling window)")
        }
        return 0L
    }

    private fun hasBytesAt(segment: File, within: Long, directory: File, seq: Long): Boolean =
        availableAtQuiet(segment, within, directory, seq) > 0L

    private fun availableAtQuiet(segment: File, within: Long, directory: File, seq: Long): Long =
        if (segment.exists()) {
            val end = if (isNewest(directory, seq)) segment.length() else segmentBytes
            (end - within).coerceAtLeast(0L)
        } else {
            0L
        }

    private fun newestSeq(directory: File): Long =
        directory.list()?.mapNotNull { it.removePrefix("seg-").removeSuffix(".ts").toLongOrNull() }?.maxOrNull() ?: -1L

    private fun isNewest(directory: File, seq: Long): Boolean = seq >= newestSeq(directory)

    /** A missing segment older than the newest one has been front-trimmed. */
    private fun isTrimmed(directory: File, seq: Long): Boolean = seq < newestSeq(directory)

    override fun getUri(): Uri? = uri

    override fun close() {
        dir = null
        uri = null
        transferEnded()
    }

    @UnstableApi
    class Factory(private val segmentBytes: Long) : DataSource.Factory {
        override fun createDataSource(): DataSource = MultiFileTailingDataSource(segmentBytes)
    }

    companion object {
        const val DEFAULT_POLL_MILLIS = 50L
        const val DEFAULT_MAX_WAIT_MILLIS = 15_000L
        /** Default segment size: multiple of the 188-byte TS packet, ~3 MB (fast rollover for the rolling window). */
        const val DEFAULT_SEGMENT_BYTES = 188L * 16L * 1024L // 3,080,192 bytes ≈ 2.9 MB
    }
}
