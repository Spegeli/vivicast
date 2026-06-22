package com.vivicast.tv.core.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface VivicastPlayerController {
    val state: StateFlow<VivicastPlayerState>

    fun play(request: PlaybackRequest)

    fun pause()

    fun resume()

    fun seekBy(deltaMillis: Long)

    fun seekToLiveEdge()

    fun stop()

    fun release()
}

data class PlaybackRequest(
    val playbackId: String,
    val providerId: String,
    val mediaId: String,
    val mediaType: PlaybackMediaType,
    val title: String,
    val streamUrl: String,
    val seekable: Boolean,
    val startPositionMillis: Long = 0L,
    val timeshift: PlaybackTimeshiftConfig? = null,
)

enum class PlaybackMediaType { Channel, Movie, Episode, CatchUp }

data class PlaybackTimeshiftConfig(
    val storage: PlaybackTimeshiftStorage,
    val windowMillis: Long,
) {
    val enabled: Boolean
        get() = windowMillis > 0L
}

enum class PlaybackTimeshiftStorage { Automatic, Ram, InternalStorage }

private const val LIVE_EDGE_TOLERANCE_MILLIS = 2_000L

data class VivicastPlayerState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val request: PlaybackRequest? = null,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val liveEdgeOffsetMillis: Long = 0L,
    val timeshiftWindowMillis: Long = 0L,
    val timeshiftStorage: PlaybackTimeshiftStorage? = null,
    val error: PlaybackError? = null,
) {
    val isTimeshiftEnabled: Boolean
        get() = timeshiftWindowMillis > 0L

    val isAtLiveEdge: Boolean
        get() = !isTimeshiftEnabled || liveEdgeOffsetMillis <= LIVE_EDGE_TOLERANCE_MILLIS
}

enum class PlaybackStatus { Idle, Starting, Playing, Paused, Error, Released }

data class PlaybackError(
    val playbackId: String,
    val retryCount: Int,
    val message: String,
)

class DefaultVivicastPlayerController(
    private val engine: PlaybackEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val maxStartRetries: Int = DEFAULT_MAX_START_RETRIES,
    private val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
) : VivicastPlayerController {
    private val mutableState = MutableStateFlow(VivicastPlayerState())
    private var startJob: Job? = null
    private var progressJob: Job? = null
    private var reconnectJob: Job? = null
    private var engineErrorJob: Job? = null
    private var liveEdgeOffsetMillis = 0L
    private var released = false

    override val state: StateFlow<VivicastPlayerState> = mutableState.asStateFlow()

    init {
        engineErrorJob = scope.launch(dispatcher) {
            engine.playbackErrors.collect { error ->
                handlePlaybackError(error)
            }
        }
    }

    override fun play(request: PlaybackRequest) {
        if (released) return
        startJob?.cancel()
        reconnectJob?.cancel()
        stopProgressPolling()
        engine.stop()
        liveEdgeOffsetMillis = 0L
        mutableState.value = VivicastPlayerState(status = PlaybackStatus.Starting, request = request)
        startJob = scope.launch(dispatcher) {
            startWithRetries(request)
        }
    }

    override fun pause() {
        if (released || mutableState.value.status != PlaybackStatus.Playing) return
        engine.pause()
        mutableState.value = mutableState.value.copy(status = PlaybackStatus.Paused)
    }

    override fun resume() {
        if (released || mutableState.value.status != PlaybackStatus.Paused) return
        engine.resume()
        mutableState.value = mutableState.value.copy(status = PlaybackStatus.Playing)
    }

    override fun seekBy(deltaMillis: Long) {
        if (released || mutableState.value.request?.seekable != true) return
        val current = mutableState.value
        if (current.isTimeshiftEnabled) {
            seekTimeshiftBy(deltaMillis, current)
            return
        }
        engine.seekBy(deltaMillis)
    }

    override fun seekToLiveEdge() {
        if (released) return
        val current = mutableState.value
        if (!current.isTimeshiftEnabled || current.isAtLiveEdge) return
        val offset = liveEdgeOffsetMillis
        liveEdgeOffsetMillis = 0L
        engine.seekBy(offset)
        mutableState.value = current.copy(
            positionMillis = current.timeshiftWindowMillis,
            liveEdgeOffsetMillis = 0L,
        )
    }

    override fun stop() {
        if (released) return
        startJob?.cancel()
        startJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        stopProgressPolling()
        engine.stop()
        liveEdgeOffsetMillis = 0L
        mutableState.value = VivicastPlayerState(status = PlaybackStatus.Idle)
    }

    override fun release() {
        if (released) return
        released = true
        startJob?.cancel()
        startJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        engineErrorJob?.cancel()
        engineErrorJob = null
        stopProgressPolling()
        engine.release()
        liveEdgeOffsetMillis = 0L
        mutableState.value = VivicastPlayerState(status = PlaybackStatus.Released)
    }

    private suspend fun startWithRetries(request: PlaybackRequest) {
        var retryCount = 0
        while (true) {
            try {
                engine.start(request)
                mutableState.value = playingState(request)
                startProgressPolling()
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (retryCount >= maxStartRetries) {
                    stopProgressPolling()
                    mutableState.value = VivicastPlayerState(
                        status = PlaybackStatus.Error,
                        request = request,
                        error = PlaybackError(
                            playbackId = request.playbackId,
                            retryCount = retryCount,
                            message = error.message ?: "Playback start failed.",
                        ),
                    )
                    return
                }
                retryCount += 1
                if (retryDelayMillis > 0L) {
                    delay(retryDelayMillis)
                }
            }
        }
    }

    private fun handlePlaybackError(error: Throwable) {
        if (released) return
        val current = mutableState.value
        val request = current.request ?: return
        if (current.status != PlaybackStatus.Playing && current.status != PlaybackStatus.Paused) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch(dispatcher) {
            reconnectWithRetries(request, error, current.positionMillis)
        }
    }

    private suspend fun reconnectWithRetries(
        request: PlaybackRequest,
        lastError: Throwable,
        lastPositionMillis: Long,
    ) {
        stopProgressPolling()
        var retryCount = 0
        while (retryCount < maxReconnectAttempts) {
            retryCount += 1
            mutableState.value = VivicastPlayerState(
                status = PlaybackStatus.Starting,
                request = request,
                positionMillis = lastPositionMillis,
                durationMillis = mutableState.value.durationMillis,
            )
            if (retryDelayMillis > 0L) {
                delay(retryDelayMillis)
            }
            try {
                engine.stop()
                val reconnectRequest = request.copy(
                    startPositionMillis = when {
                        request.isLiveTimeshift() -> 0L
                        request.seekable -> lastPositionMillis.coerceAtLeast(0L)
                        else -> 0L
                    },
                )
                engine.start(reconnectRequest)
                mutableState.value = playingState(reconnectRequest)
                startProgressPolling()
                return
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Try again until the PRD retry budget is exhausted.
            }
        }

        mutableState.value = VivicastPlayerState(
            status = PlaybackStatus.Error,
            request = request,
            positionMillis = lastPositionMillis,
            durationMillis = mutableState.value.durationMillis,
            liveEdgeOffsetMillis = mutableState.value.liveEdgeOffsetMillis,
            timeshiftWindowMillis = mutableState.value.timeshiftWindowMillis,
            timeshiftStorage = mutableState.value.timeshiftStorage,
            error = PlaybackError(
                playbackId = request.playbackId,
                retryCount = maxReconnectAttempts,
                message = lastError.message ?: "Playback connection lost.",
            ),
        )
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = scope.launch(dispatcher) {
            var lastPollMillis = System.currentTimeMillis()
            while (true) {
                delay(PROGRESS_POLL_INTERVAL_MILLIS)
                val nowMillis = System.currentTimeMillis()
                val elapsedMillis = (nowMillis - lastPollMillis).coerceAtLeast(0L)
                lastPollMillis = nowMillis
                val current = mutableState.value
                if (current.status != PlaybackStatus.Playing && current.status != PlaybackStatus.Paused) {
                    return@launch
                }
                mutableState.value = if (current.isTimeshiftEnabled) {
                    timeshiftProgressState(current, elapsedMillis)
                } else {
                    current.copy(
                        positionMillis = engine.currentPositionMillis,
                        durationMillis = engine.durationMillis,
                    )
                }
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun playingState(request: PlaybackRequest): VivicastPlayerState {
        val timeshift = request.timeshift?.takeIf { it.enabled }
        return if (request.isLiveTimeshift() && timeshift != null) {
            liveEdgeOffsetMillis = 0L
            VivicastPlayerState(
                status = PlaybackStatus.Playing,
                request = request,
                positionMillis = timeshift.windowMillis,
                durationMillis = timeshift.windowMillis,
                liveEdgeOffsetMillis = 0L,
                timeshiftWindowMillis = timeshift.windowMillis,
                timeshiftStorage = timeshift.storage,
            )
        } else {
            VivicastPlayerState(
                status = PlaybackStatus.Playing,
                request = request,
                positionMillis = engine.currentPositionMillis,
                durationMillis = engine.durationMillis,
            )
        }
    }

    private fun PlaybackRequest.isLiveTimeshift(): Boolean =
        mediaType == PlaybackMediaType.Channel && timeshift?.enabled == true

    private fun seekTimeshiftBy(deltaMillis: Long, current: VivicastPlayerState) {
        val windowMillis = current.timeshiftWindowMillis
        val targetOffset = (liveEdgeOffsetMillis - deltaMillis).coerceIn(0L, windowMillis)
        val actualDeltaMillis = liveEdgeOffsetMillis - targetOffset
        liveEdgeOffsetMillis = targetOffset
        if (actualDeltaMillis != 0L) {
            engine.seekBy(actualDeltaMillis)
        }
        mutableState.value = current.copy(
            positionMillis = windowMillis - targetOffset,
            liveEdgeOffsetMillis = targetOffset,
        )
    }

    private fun timeshiftProgressState(current: VivicastPlayerState, elapsedMillis: Long): VivicastPlayerState {
        if (current.status == PlaybackStatus.Paused) {
            liveEdgeOffsetMillis = (liveEdgeOffsetMillis + elapsedMillis).coerceAtMost(current.timeshiftWindowMillis)
        }
        return current.copy(
            positionMillis = current.timeshiftWindowMillis - liveEdgeOffsetMillis,
            durationMillis = current.timeshiftWindowMillis,
            liveEdgeOffsetMillis = liveEdgeOffsetMillis,
        )
    }

    companion object {
        const val DEFAULT_MAX_START_RETRIES = 5
        const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 5
        const val DEFAULT_RETRY_DELAY_MILLIS = 500L
        private const val PROGRESS_POLL_INTERVAL_MILLIS = 1_000L
    }
}

interface PlaybackEngine {
    val playbackErrors: Flow<Throwable>
        get() = emptyFlow()

    val currentPositionMillis: Long

    val durationMillis: Long

    suspend fun start(request: PlaybackRequest)

    fun pause()

    fun resume()

    fun seekBy(deltaMillis: Long)

    fun stop()

    fun release()
}

class Media3PlaybackEngine(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : PlaybackEngine {
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private val playbackErrorEvents = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackErrorEvents.tryEmit(error)
                }
            },
        )
    }

    override val playbackErrors: Flow<Throwable> = playbackErrorEvents

    override val currentPositionMillis: Long
        get() = player.currentPosition.coerceAtLeast(0L)

    override val durationMillis: Long
        get() = player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L

    override suspend fun start(request: PlaybackRequest) {
        withContext(dispatcher) {
            player.setMediaItem(MediaItem.fromUri(request.streamUrl), request.startPositionMillis)
            player.prepare()
            player.playWhenReady = true
        }
    }

    override fun pause() {
        player.pause()
    }

    override fun resume() {
        player.play()
    }

    override fun seekBy(deltaMillis: Long) {
        player.seekTo((player.currentPosition + deltaMillis).coerceAtLeast(0L))
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    override fun release() {
        player.release()
    }
}
