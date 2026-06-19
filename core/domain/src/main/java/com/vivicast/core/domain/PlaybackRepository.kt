package com.vivicast.core.domain

import com.vivicast.core.model.Channel

interface PlaybackRepository {
    fun play(channel: Channel)
    fun stop()
}
