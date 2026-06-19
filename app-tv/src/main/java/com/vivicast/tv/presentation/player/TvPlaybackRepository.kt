package com.vivicast.tv

import android.content.Context
import androidx.media3.common.Player
import com.vivicast.core.model.Channel
import com.vivicast.core.model.PlaybackContentType
import com.vivicast.core.model.PlaybackState
import com.vivicast.core.model.StreamTrack
import com.vivicast.core.player.media3.Media3IptvPlayerController
import kotlinx.coroutines.flow.StateFlow

class TvPlaybackRepository(context: Context) {
    private val playerController = Media3IptvPlayerController(context.applicationContext)

    val playbackState: StateFlow<PlaybackState> = playerController.playbackState
    val media3Player: Player
        get() = playerController.media3Player

    fun play(channel: Channel) {
        playerController.play(channel)
    }

    fun playStream(
        contentId: String,
        title: String,
        streamUrl: String,
        contentType: PlaybackContentType,
        startPositionMs: Long = 0L
    ) {
        playerController.playStream(
            contentId = contentId,
            title = title,
            streamUrl = streamUrl,
            contentType = contentType,
            startPositionMs = startPositionMs
        )
    }

    fun pause() {
        playerController.pause()
    }

    fun stop() {
        playerController.stop()
    }

    fun selectTrack(track: StreamTrack) {
        playerController.selectTrack(track)
    }

    fun currentPositionMs(): Long = playerController.currentPositionMs()

    fun currentDurationMs(): Long? = playerController.currentDurationMs()

    fun release() {
        playerController.release()
    }
}
