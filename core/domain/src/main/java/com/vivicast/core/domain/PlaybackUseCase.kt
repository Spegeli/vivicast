package com.vivicast.core.domain

import com.vivicast.core.model.Channel

class PlaybackUseCase(
    private val playbackRepository: PlaybackRepository
) {
    fun playChannel(channel: Channel) {
        playbackRepository.play(channel)
    }

    fun stop() {
        playbackRepository.stop()
    }
}
