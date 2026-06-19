package com.vivicast.core.player.media3

import com.vivicast.core.model.Channel
import com.vivicast.core.model.PlaybackState
import com.vivicast.core.model.StreamTrack
import kotlinx.coroutines.flow.StateFlow

interface IptvPlayerController {
    val playbackState: StateFlow<PlaybackState>

    fun play(channel: Channel)
    fun pause()
    fun stop()
    fun selectTrack(track: StreamTrack)
    fun release()
}
