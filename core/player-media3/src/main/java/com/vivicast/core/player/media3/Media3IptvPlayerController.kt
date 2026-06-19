package com.vivicast.core.player.media3

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.vivicast.core.model.Channel
import com.vivicast.core.model.PlaybackContentType
import com.vivicast.core.model.PlaybackState
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.StreamTrack
import com.vivicast.core.model.TrackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Media3IptvPlayerController(
    context: Context
) : IptvPlayerController {
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("ViviCast/1.0 AndroidTV")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)

    private val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory))
        .build()
    val media3Player: Player
        get() = player
    private val mutablePlaybackState = MutableStateFlow(
        PlaybackState(
            channelId = null,
            contentType = null,
            contentTitle = null,
            positionMs = 0L,
            durationMs = null,
            status = PlaybackStatus.Idle,
            audioTracks = emptyList(),
            subtitleTracks = emptyList()
        )
    )

    override val playbackState: StateFlow<PlaybackState> = mutablePlaybackState

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val status = when (playbackState) {
                        Player.STATE_BUFFERING -> PlaybackStatus.Buffering
                        Player.STATE_READY -> if (player.playWhenReady) PlaybackStatus.Playing else PlaybackStatus.Paused
                        Player.STATE_ENDED, Player.STATE_IDLE -> PlaybackStatus.Idle
                        else -> PlaybackStatus.Idle
                    }
                    mutablePlaybackState.value = mutablePlaybackState.value.copy(
                        status = status,
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.takeIf { it > 0 }
                    )
                    publishTracks()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val detail = error.cause?.message
                        ?.takeIf { it.isNotBlank() }
                        ?: error.message
                        ?: "Playback failed"
                    mutablePlaybackState.value = mutablePlaybackState.value.copy(
                        status = PlaybackStatus.Error(
                            message = "${error.errorCodeName}: $detail",
                            recoverable = true
                        ),
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.takeIf { it > 0 }
                    )
                    publishTracks()
                }

                override fun onTracksChanged(tracks: Tracks) {
                    publishTracks()
                }
            }
        )
    }

    override fun play(channel: Channel) {
        playStream(
            contentId = channel.id,
            title = channel.name,
            streamUrl = channel.streamUrl,
            contentType = PlaybackContentType.LIVE_TV
        )
    }

    fun playStream(
        contentId: String,
        title: String,
        streamUrl: String,
        contentType: PlaybackContentType,
        startPositionMs: Long = 0L
    ) {
        mutablePlaybackState.value = mutablePlaybackState.value.copy(
            channelId = contentId,
            contentType = contentType,
            contentTitle = title,
            positionMs = startPositionMs.coerceAtLeast(0L),
            durationMs = null,
            status = PlaybackStatus.Buffering,
            audioTracks = emptyList(),
            subtitleTracks = emptyList()
        )
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        player.playWhenReady = true
    }

    override fun pause() {
        player.pause()
        mutablePlaybackState.value = mutablePlaybackState.value.copy(
            status = PlaybackStatus.Paused,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0 }
        )
    }

    override fun stop() {
        player.stop()
        mutablePlaybackState.value = mutablePlaybackState.value.copy(
            channelId = null,
            contentType = null,
            contentTitle = null,
            positionMs = 0L,
            durationMs = null,
            status = PlaybackStatus.Idle,
            audioTracks = emptyList(),
            subtitleTracks = emptyList()
        )
    }

    fun currentPositionMs(): Long = player.currentPosition.coerceAtLeast(0L)

    fun currentDurationMs(): Long? = player.duration.takeIf { it > 0 }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        mutablePlaybackState.value = mutablePlaybackState.value.copy(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0 }
        )
    }

    override fun selectTrack(track: StreamTrack) {
        val currentTracks = player.currentTracks
        val parametersBuilder = player.trackSelectionParameters
            .buildUpon()

        when (track.type) {
            TrackType.AUDIO -> {
                val match = parseTrackSelectionId(track.id)
                    ?.takeIf { it.type == TrackType.AUDIO }
                    ?: return
                val group = currentTracks.groups.getOrNull(match.groupIndex) ?: return
                parametersBuilder
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(match.trackIndex)))
            }

            TrackType.SUBTITLE -> {
                if (track.id == SUBTITLE_OFF_ID) {
                    parametersBuilder
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    val match = parseTrackSelectionId(track.id)
                        ?.takeIf { it.type == TrackType.SUBTITLE }
                        ?: return
                    val group = currentTracks.groups.getOrNull(match.groupIndex) ?: return
                    parametersBuilder
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(match.trackIndex)))
                }
            }
        }

        player.trackSelectionParameters = parametersBuilder.build()
        publishTracks()
    }

    override fun release() {
        player.release()
    }

    private fun publishTracks() {
        val audioTracks = mutableListOf<StreamTrack>()
        val subtitleTracks = mutableListOf<StreamTrack>()
        val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            val type = when (group.type) {
                C.TRACK_TYPE_AUDIO -> TrackType.AUDIO
                C.TRACK_TYPE_TEXT -> TrackType.SUBTITLE
                else -> null
            } ?: return@forEachIndexed

            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.mediaTrackGroup.getFormat(trackIndex)
                val track = StreamTrack(
                    id = buildTrackSelectionId(type, groupIndex, trackIndex),
                    type = type,
                    label = format.label
                        ?.takeIf { it.isNotBlank() }
                        ?: format.language
                        ?.takeIf { it.isNotBlank() }
                        ?.uppercase()
                        ?: when (type) {
                            TrackType.AUDIO -> "Audio ${audioTracks.size + 1}"
                            TrackType.SUBTITLE -> "Subtitle ${subtitleTracks.size + 1}"
                        },
                    language = format.language?.takeIf { it.isNotBlank() },
                    selected = group.isTrackSelected(trackIndex)
                )
                when (type) {
                    TrackType.AUDIO -> audioTracks += track
                    TrackType.SUBTITLE -> subtitleTracks += track
                }
            }
        }

        val subtitleTracksWithOff = buildList {
            add(
                StreamTrack(
                    id = SUBTITLE_OFF_ID,
                    type = TrackType.SUBTITLE,
                    label = "Off",
                    language = null,
                    selected = textDisabled || subtitleTracks.none { it.selected }
                )
            )
            addAll(subtitleTracks.map { track ->
                if (textDisabled) {
                    track.copy(selected = false)
                } else {
                    track
                }
            })
        }

        mutablePlaybackState.value = mutablePlaybackState.value.copy(
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracksWithOff,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0 }
        )
    }

    private fun buildTrackSelectionId(type: TrackType, groupIndex: Int, trackIndex: Int): String {
        val prefix = when (type) {
            TrackType.AUDIO -> "audio"
            TrackType.SUBTITLE -> "subtitle"
        }
        return "$prefix:$groupIndex:$trackIndex"
    }

    private fun parseTrackSelectionId(id: String): ParsedTrackSelectionId? {
        val parts = id.split(":")
        if (parts.size != 3) return null
        val type = when (parts[0]) {
            "audio" -> TrackType.AUDIO
            "subtitle" -> TrackType.SUBTITLE
            else -> return null
        }
        val groupIndex = parts[1].toIntOrNull() ?: return null
        val trackIndex = parts[2].toIntOrNull() ?: return null
        return ParsedTrackSelectionId(type, groupIndex, trackIndex)
    }

    private data class ParsedTrackSelectionId(
        val type: TrackType,
        val groupIndex: Int,
        val trackIndex: Int
    )

    private companion object {
        private const val SUBTITLE_OFF_ID = "subtitle:off"
    }
}
