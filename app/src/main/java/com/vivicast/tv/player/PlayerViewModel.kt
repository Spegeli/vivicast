package com.vivicast.tv.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackOrigin
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.VivicastPlayerController
import com.vivicast.tv.core.player.VivicastPlayerState
import com.vivicast.tv.data.media.MediaRepository
import com.vivicast.tv.data.playback.PlaybackProgressRecorder
import com.vivicast.tv.data.playback.PlaybackRequestFactory
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.Episode
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.Movie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Activity-scoped orchestration holder for the single App-hoisted player. It owns the *pure* playback
 * orchestration that previously lived inline in `MainActivity.VivicastApp`: building a [PlaybackRequest] and
 * calling `play()`, the auto-save progress loop, the auto-next-episode derivation, the zap list, and the
 * committed-preview identity. The single ExoPlayer **connection** stays a process-lifetime singleton in
 * `AppContainer.playerController` (ADR-013 one-active-playback + seamless preview↔fullscreen handoff) — this VM
 * only *drives* it; it never owns/creates it.
 *
 * Stays App-side (NOT here — Context/Activity/View/nav bound): navigation (this VM only *signals* via
 * [navigateToPlayer]), the PIN gate, the external-player ActivityResult + choice dialog, the preview
 * `SurfaceView` + `attachVideoSurface`, WatchNext, diagnostics logging, `onStop` save, and the two
 * settings→controller push effects (`updateGlobalUserAgent` / `updatePlaybackTuning`). See
 * `plans/player-viewmodel-extraction.md`.
 *
 * [scope] lets unit tests inject a controlled scope; production uses [viewModelScope].
 */
internal class PlayerViewModel(
    private val playerController: VivicastPlayerController,
    private val playbackRequestFactory: PlaybackRequestFactory,
    private val playbackProgressRecorder: PlaybackProgressRecorder,
    private val mediaRepository: MediaRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {

    private val coroutineScope: CoroutineScope = scope ?: viewModelScope

    // Zap neighbour list (the currently-playable Live-TV channels). Fed from the Live-TV route.
    private var livePlaybackChannels: List<Channel> = emptyList()
    // The auto-next target resolved from the playing episode; drives [PlayerUiState.nextEpisodeTitle] + play-next.
    private var nextAutoNextEpisode: Episode? = null
    // Per-playback throttle map for the automatic progress recorder (owned here so both the loop and the
    // explicit force-save on close share one map). Not exposed.
    private val automaticProgressSaveTimes = mutableMapOf<String, Long>()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // One-shot "the running player should now be shown fullscreen" signal. The App collects it and runs the
    // actual NavController.navigate(Player(...)) (nav stays App-side). extraBufferCapacity=1 so an emit from a
    // non-suspending caller is never dropped.
    private val _navigateToPlayer = MutableSharedFlow<PlaybackRequest>(extraBufferCapacity = 1)
    val navigateToPlayer: SharedFlow<PlaybackRequest> = _navigateToPlayer.asSharedFlow()

    init {
        // Automatic VOD progress persistence — records on every player-state change (the recorder applies the
        // ADR-013 thresholds + throttle). collectLatest so a burst of states doesn't queue stale saves.
        coroutineScope.launch {
            playerController.state.collectLatest { state ->
                playbackProgressRecorder.record(state, automaticProgressSaveTimes)
            }
        }
        // Auto-next: whenever the playing item changes to an episode, resolve the next episode (title feeds the
        // player's auto-next panel). Keyed on the request identity so it re-runs once per real change, not per tick.
        coroutineScope.launch {
            playerController.state
                .distinctUntilChangedBy { s ->
                    Triple(s.request?.playbackId, s.request?.mediaId, s.request?.mediaType)
                }
                .collect { state ->
                    val request = state.request
                    nextAutoNextEpisode = if (request?.mediaType == PlaybackMediaType.Episode) {
                        mediaRepository.getEpisode(request.providerId, request.mediaId)
                            ?.let { mediaRepository.getNextEpisode(it) }
                    } else {
                        null
                    }
                    _uiState.update { it.copy(nextEpisodeTitle = nextAutoNextEpisode?.name) }
                }
        }
    }

    /** The Live-TV route reports its currently-playable channels; used as the zap (CH+/-) neighbour list. */
    fun setPlayableChannels(channels: List<Channel>) {
        livePlaybackChannels = channels
    }

    /** Build + start a channel and signal fullscreen. One connection: `play()` stops any previous stream first. */
    fun playChannel(channel: Channel, origin: PlaybackOrigin) {
        coroutineScope.launch {
            val request = playbackRequestFactory.channelRequest(channel, origin) ?: return@launch
            playerController.play(request)
            _navigateToPlayer.tryEmit(request)
        }
    }

    /**
     * Start the embedded Live-TV preview: play the channel on the shared player WITHOUT signalling fullscreen.
     * The App's preview surface renders it. One connection (`play()` stops any previous stream first).
     */
    fun commitLivePreview(channel: Channel) {
        _uiState.update { it.copy(committedChannel = channel) }
        coroutineScope.launch {
            val request = playbackRequestFactory.channelRequest(channel, PlaybackOrigin.LiveTv) ?: return@launch
            playerController.play(request)
        }
    }

    /**
     * Set the committed preview channel WITHOUT re-playing (player-close hand-back of an already-running stream).
     * The caller resolves the [Channel] first so the commit + the following nav happen in order.
     */
    fun setCommittedChannel(channel: Channel?) {
        _uiState.update { it.copy(committedChannel = channel) }
    }

    /** Stop the preview + release the connection (leaving Live-TV / clearing the committed channel). */
    fun stopLivePreview() {
        if (_uiState.value.committedChannel != null) {
            _uiState.update { it.copy(committedChannel = null) }
            playerController.stop()
        }
    }

    /**
     * Live-TV "watch": if the channel is already the committed preview, just signal fullscreen (surface handoff,
     * NO reconnect); otherwise start it fresh.
     */
    fun openLivePlayer(channel: Channel) {
        if (_uiState.value.committedChannel?.id == channel.id) {
            playerController.state.value.request?.let { _navigateToPlayer.tryEmit(it) }
        } else {
            playChannel(channel, PlaybackOrigin.LiveTv)
        }
    }

    /** Channel zap (CH+/-) over the playable list; no wrap past a single connection (`play()` swaps in place). */
    fun zap(direction: Int) {
        if (direction == 0 || livePlaybackChannels.isEmpty()) return
        val currentRequest = playerController.state.value.request
        if (currentRequest?.mediaType != PlaybackMediaType.Channel) return
        val currentIndex = livePlaybackChannels.indexOfFirst { it.id == currentRequest.mediaId }
        val nextIndex = if (currentIndex < 0) {
            0
        } else {
            Math.floorMod(currentIndex + direction, livePlaybackChannels.size)
        }
        playChannel(livePlaybackChannels[nextIndex], PlaybackOrigin.LiveTv)
    }

    /** Internal-player movie start (the App keeps the PIN gate + external-player branch around this). */
    fun playMovieInternal(movie: Movie, resumeProgress: Boolean, origin: PlaybackOrigin) {
        coroutineScope.launch {
            val request = playbackRequestFactory.movieRequest(movie, resumeProgress, origin) ?: return@launch
            playerController.play(request)
            _navigateToPlayer.tryEmit(request)
        }
    }

    /** Internal-player episode start. */
    fun playEpisodeInternal(episode: Episode, origin: PlaybackOrigin) {
        coroutineScope.launch {
            val request = playbackRequestFactory.episodeRequest(episode, origin) ?: return@launch
            playerController.play(request)
            _navigateToPlayer.tryEmit(request)
        }
    }

    /** Play the resolved auto-next episode (from the player's "next episode" action). */
    fun playNextAutoNextEpisode() {
        val episode = nextAutoNextEpisode ?: return
        playEpisodeInternal(episode, PlaybackOrigin.SeriesDetail)
    }

    /** Catch-up (EPG-context limited VOD). */
    fun playCatchUp(channel: Channel, program: EpgProgram, origin: PlaybackOrigin) {
        coroutineScope.launch {
            val request = playbackRequestFactory.catchUpRequest(channel, program, origin) ?: return@launch
            playerController.play(request)
            _navigateToPlayer.tryEmit(request)
        }
    }

    /**
     * Build a request for the EXTERNAL player without starting the internal player (the App launches it via
     * ActivityResult). Suspends because stream resolution is just-in-time.
     */
    suspend fun buildMovieRequest(movie: Movie, resumeProgress: Boolean, origin: PlaybackOrigin): PlaybackRequest? =
        playbackRequestFactory.movieRequest(movie, resumeProgress, origin)

    /** Build an episode request for the external player (see [buildMovieRequest]). */
    suspend fun buildEpisodeRequest(episode: Episode, origin: PlaybackOrigin): PlaybackRequest? =
        playbackRequestFactory.episodeRequest(episode, origin)

    /** Force-save progress on player close (the PlayerRoute `onBeforeStop` hook). Shares the throttle map. */
    fun saveProgress(state: VivicastPlayerState) {
        coroutineScope.launch {
            playbackProgressRecorder.record(state, automaticProgressSaveTimes, forceSave = true)
        }
    }
}
