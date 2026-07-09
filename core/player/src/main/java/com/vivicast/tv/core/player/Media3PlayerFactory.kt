package com.vivicast.tv.core.player

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * Per-playback tuning snapshot. Because [ExoPlayer.Builder] settings (LoadControl, renderers factory,
 * audio sink) are fixed at build time, the engine rebuilds the player when [builderSubset] changes — this
 * is exactly the spec's "applies at next stream start". The non-builder fields (preferred audio/subtitle)
 * are applied on every start without a rebuild.
 */
data class PlaybackTuning(
    val bufferSize: BufferTier = BufferTier.Medium,
    val audioDecoder: DecoderMode = DecoderMode.Hardware,
    val videoDecoder: DecoderMode = DecoderMode.Hardware,
    val passthroughEnabled: Boolean = false,
    val preferredAudio: PlaybackAudioOption = PlaybackAudioOption.SystemDefault,
    val preferredSubtitle: PlaybackSubtitleOption = PlaybackSubtitleOption.Off,
) {
    /** The build-time subset; a change here forces a player rebuild. Audio/subtitle prefs are excluded. */
    val builderSubset: BuilderSubset
        get() = BuilderSubset(bufferSize, audioDecoder, videoDecoder, passthroughEnabled)

    data class BuilderSubset(
        val bufferSize: BufferTier,
        val audioDecoder: DecoderMode,
        val videoDecoder: DecoderMode,
        val passthroughEnabled: Boolean,
    )
}

/** Core-player-local decoder mode (App maps DataStore's DecoderPreference onto this; keeps layers decoupled). */
enum class DecoderMode { Hardware, Software }

/** Core-player-local buffer tier (App maps DataStore's BufferSizePreference onto this). */
enum class BufferTier { Off, Small, Medium, Large, ExtraLarge }

/** ExoPlayer LoadControl buffer durations in ms. */
data class BufferDurations(val minMs: Int, val maxMs: Int, val forPlaybackMs: Int, val afterRebufferMs: Int)

/** Normative-ish IPTV tiers (values signed off in the plan). forPlayback/afterRebuffer kept low for snappy zap. */
fun BufferTier.toBufferDurations(): BufferDurations = when (this) {
    BufferTier.Off -> BufferDurations(1000, 5000, 1000, 1500)
    BufferTier.Small -> BufferDurations(5000, 15000, 1500, 2500)
    BufferTier.Medium -> BufferDurations(15000, 30000, 2500, 5000)
    BufferTier.Large -> BufferDurations(30000, 60000, 2500, 5000)
    BufferTier.ExtraLarge -> BufferDurations(60000, 120000, 3000, 6000)
}

/**
 * Reorders MediaCodec candidates to put software-only platform codecs first when the tuning requests
 * Software for that track type; otherwise passes ExoPlayer's default ordering through. Combined with the
 * FFmpeg extension renderer (for AC3/E-AC3/DTS/MP2 audio the platform may lack) this implements the HW/SW
 * decoder setting without forcing a specific codec.
 */
class VivicastMediaCodecSelector(private val tuning: PlaybackTuning) : MediaCodecSelector {

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        val preferSoftware = when {
            MimeTypes.isVideo(mimeType) -> tuning.videoDecoder == DecoderMode.Software
            MimeTypes.isAudio(mimeType) -> tuning.audioDecoder == DecoderMode.Software
            else -> false
        }
        // Stable sort: software-only decoders first, keeping ExoPlayer's relative ordering within each group.
        return if (preferSoftware) infos.sortedByDescending { it.softwareOnly } else infos
    }
}

/**
 * Builds a fresh [ExoPlayer] from [tuning]. The engine calls this once at construction and again whenever
 * [PlaybackTuning.builderSubset] changes. Uses [NextRenderersFactory] so the FFmpeg audio decoder is on the
 * classpath; extension mode PREFER when Software audio is requested, otherwise ON (FFmpeg stays a fallback
 * for codecs the platform lacks). ponytail: passthrough AudioSink override lands in Phase 2.
 */
fun buildExoPlayer(context: Context, tuning: PlaybackTuning): ExoPlayer {
    val buffer = tuning.bufferSize.toBufferDurations()
    // No back-buffer: live seek-back rides ExoPlayer's native DVR window (re-fetches segments), so retaining
    // played media in RAM only risked OOM on long-latency live streams. VOD doesn't need it either.
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(buffer.minMs, buffer.maxMs, buffer.forPlaybackMs, buffer.afterRebufferMs)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
    val extensionMode = if (tuning.audioDecoder == DecoderMode.Software) {
        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
    } else {
        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
    }
    val renderersFactory = VivicastRenderersFactory(context, tuning.passthroughEnabled)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(extensionMode)
        .setMediaCodecSelector(VivicastMediaCodecSelector(tuning))
    return ExoPlayer.Builder(context, renderersFactory)
        .setLoadControl(loadControl)
        .build()
}

/**
 * Renderers factory that decides the audio sink from the passthrough setting. NextRenderersFactory adds the
 * FFmpeg audio decoder; this subclass additionally controls passthrough vs forced-PCM.
 */
private class VivicastRenderersFactory(
    context: Context,
    private val passthroughEnabled: Boolean,
) : NextRenderersFactory(context) {
    // OFF (default): force stereo PCM. The no-Context DefaultAudioSink.Builder honours setAudioCapabilities
    // (the Context overload IGNORES it) — this is how PCM decode is forced instead of bitstreaming AC3/DTS to
    // a receiver, avoiding lip-sync/stall on boxes that misreport passthrough. ON: default context sink (HDMI).
    @Suppress("DEPRECATION")
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? =
        if (passthroughEnabled) {
            super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
        } else {
            DefaultAudioSink.Builder()
                .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                .build()
        }
}
