package com.vivicast.tv.core.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface VivicastPlayerController {
    val state: StateFlow<VivicastPlayerState>

    fun play(request: PlaybackRequest)

    fun pause()

    fun resume()

    fun seekBy(deltaMillis: Long)

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
)

enum class PlaybackMediaType { Channel, Movie, Episode, CatchUp }

data class VivicastPlayerState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val request: PlaybackRequest? = null,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val error: PlaybackError? = null,
)

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
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
) : VivicastPlayerController {
    private val mutableState = MutableStateFlow(VivicastPlayerState())
    private var startJob: Job? = null
    private var progressJob: Job? = null
    private var released = false

    override val state: StateFlow<VivicastPlayerState> = mutableState.asStateFlow()

    override fun play(request: PlaybackRequest) {
        if (released) return
        startJob?.cancel()
        stopProgressPolling()
        engine.stop()
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
        engine.seekBy(deltaMillis)
    }

    override fun stop() {
        if (released) return
        startJob?.cancel()
        startJob = null
        stopProgressPolling()
        engine.stop()
        mutableState.value = VivicastPlayerState(status = PlaybackStatus.Idle)
    }

    override fun release() {
        if (released) return
        released = true
        startJob?.cancel()
        startJob = null
        stopProgressPolling()
        engine.release()
        mutableState.value = VivicastPlayerState(status = PlaybackStatus.Released)
    }

    private suspend fun startWithRetries(request: PlaybackRequest) {
        var retryCount = 0
        while (true) {
            try {
                engine.start(request)
                mutableState.value = VivicastPlayerState(
                    status = PlaybackStatus.Playing,
                    request = request,
                    positionMillis = engine.currentPositionMillis,
                    durationMillis = engine.durationMillis,
                )
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

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = scope.launch(dispatcher) {
            while (true) {
                delay(PROGRESS_POLL_INTERVAL_MILLIS)
                val current = mutableState.value
                if (current.status != PlaybackStatus.Playing && current.status != PlaybackStatus.Paused) {
                    return@launch
                }
                mutableState.value = current.copy(
                    positionMillis = engine.currentPositionMillis,
                    durationMillis = engine.durationMillis,
                )
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    companion object {
        const val DEFAULT_MAX_START_RETRIES = 5
        const val DEFAULT_RETRY_DELAY_MILLIS = 500L
        private const val PROGRESS_POLL_INTERVAL_MILLIS = 1_000L
    }
}

interface PlaybackEngine {
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
