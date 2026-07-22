package com.vivicast.tv.player

import com.vivicast.tv.domain.model.Channel

/**
 * Immutable presentation state for the App-hoisted player orchestration ([PlayerViewModel]).
 *
 * Deliberately tiny: the fullscreen [com.vivicast.tv.feature.player.PlayerRoute] reads the live playback state
 * (position, tracks, timeshift, buffering, …) straight off `playerController.state`, and the App keeps navigation,
 * the PIN gate, the external-player ActivityResult and the preview SurfaceView. Only two orchestration-derived
 * values need to flow through the VM into the App/Live-TV UI:
 *
 * - [committedChannel] — the channel whose stream is committed to the shared player (drives the Live-TV embedded
 *   preview + surface owner). Null until the first OK / a channel is adopted on player-close.
 * - [nextEpisodeTitle] — the auto-next target's title (non-null == a next episode exists); fed to PlayerRoute.
 */
internal data class PlayerUiState(
    val committedChannel: Channel? = null,
    val nextEpisodeTitle: String? = null,
)
