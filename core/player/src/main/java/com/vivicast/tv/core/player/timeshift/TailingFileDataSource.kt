package com.vivicast.tv.core.player.timeshift

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.RandomAccessFile

/**
 * A [DataSource] for a local file that is still being **appended to** by a live capture (Fall B / K2). At the
 * current end-of-file it does not report `END_OF_INPUT`; instead it blocks-and-polls for more bytes for up to
 * [maxWaitMillis], so ExoPlayer keeps consuming the growing file and follows the live edge. Because [open]
 * honours `dataSpec.position` (via `RandomAccessFile.seek`), ExoPlayer can also re-open at an offset to seek
 * back into the captured window — the file behaves like a seekable, growing progressive stream.
 *
 * Reports `LENGTH_UNSET` (unbounded) so the source is treated as live rather than a fixed VOD clip that would
 * end when playback reaches the byte length known at open time.
 */
@UnstableApi
class TailingFileDataSource(
    private val pollMillis: Long = DEFAULT_POLL_MILLIS,
    private val maxWaitMillis: Long = DEFAULT_MAX_WAIT_MILLIS,
) : BaseDataSource(/* isNetwork = */ false) {

    private var file: RandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val path = dataSpec.uri.path ?: throw IllegalArgumentException("TailingFileDataSource requires a file uri")
        val opened = RandomAccessFile(File(path), "r")
        opened.seek(dataSpec.position)
        file = opened
        bytesRemaining = dataSpec.length // LENGTH_UNSET unless a bounded range was requested
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val source = file ?: return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
            minOf(length.toLong(), bytesRemaining).toInt()
        }

        var read = source.read(buffer, offset, toRead)
        var waited = 0L
        // Live tail: the writer may not have produced the next bytes yet — wait for them instead of ending.
        while (read == -1 && waited < maxWaitMillis) {
            Thread.sleep(pollMillis)
            waited += pollMillis
            read = source.read(buffer, offset, toRead)
        }
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            file?.close()
        } finally {
            file = null
            uri = null
            transferEnded()
        }
    }

    /** Builds a fresh [TailingFileDataSource] per playback, as ExoPlayer expects. */
    @UnstableApi
    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = TailingFileDataSource()
    }

    private companion object {
        const val DEFAULT_POLL_MILLIS = 50L
        const val DEFAULT_MAX_WAIT_MILLIS = 15_000L
    }
}
