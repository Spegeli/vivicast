package com.vivicast.tv.diagnostics

import android.os.Process

/**
 * Turns raw player failures into something debuggable: ExoPlayer's own error message is often generic,
 * while the real cause is a MediaCodec/audio error in logcat with a numeric code. Mirrors OwnTV's
 * approach — read the recent codec/audio error lines and map the code to a human-readable cause.
 */
object PlaybackDiagnostics {
    /** Recent codec/audio error line from the own process, or null. Blocking — call off the main thread. */
    fun recentCodecError(): String? =
        runCatching {
            Runtime.getRuntime()
                .exec(arrayOf("logcat", "-d", "-t", "200", "--pid=${Process.myPid()}", "-s", "MediaCodec:E", "ACodec:E", "AudioTrack:E"))
                .inputStream.bufferedReader().use { reader -> reader.readText() }
                .lineSequence().lastOrNull { it.isNotBlank() }
        }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Maps a known codec/error signature (from logcat or the engine message) to a plain-language cause. */
    fun causeHint(text: String): String? {
        val l = text.lowercase()
        return when {
            "0x80001000" in l -> "Hardware video decoder busy or cannot handle this stream"
            "0x80001001" in l -> "Hardware video decoder transient error — retry"
            "format_unsupported" in l || "0xfffffff3" in l || "0xffffffea" in l || "omx_errorformat" in l ->
                "Device decoder does not support this format/profile (e.g. HEVC 10-bit)"
            "enomem" in l || "out of memory" in l -> "Out of memory for the decoder"
            "drm" in l || "secure" in l -> "DRM / secure-decoder error"
            "unrecognized file format" in l || "invalid data" in l -> "Stream format not recognized (bad or partial stream)"
            "audiotrack" in l || "audiosink" in l || "audioflinger" in l || "audio codec" in l -> "Audio output error"
            else -> null
        }
    }
}
