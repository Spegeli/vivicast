package com.vivicast.tv.core.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import java.io.File
import java.util.Locale

interface VivicastPlayerController {
    val state: StateFlow<VivicastPlayerState>

    fun play(request: PlaybackRequest)

    fun pause()

    fun resume()

    fun seekBy(deltaMillis: Long)

    fun seekToLiveEdge()

    fun selectAudio(option: PlaybackAudioOption) = Unit

    fun selectSubtitle(option: PlaybackSubtitleOption) = Unit

    fun selectAspectRatio(mode: PlaybackAspectRatioMode) = Unit

    fun stop()

    fun release()
}

data class PlaybackRequest(
    val playbackId: String,
    val providerId: String,
    val mediaId: String,
    val mediaType: PlaybackMediaType,
    val providerStableKey: String = providerId,
    val mediaStableKey: String = mediaId,
    val origin: PlaybackOrigin = PlaybackOrigin.Unknown,
    val returnTarget: PlaybackReturnTarget = PlaybackReturnTarget.Unknown,
    val title: String,
    val streamUrl: String,
    val seekable: Boolean,
    val startPositionMillis: Long = 0L,
    val epgProgramStableKey: String? = null,
    val timeshift: PlaybackTimeshiftConfig? = null,
    // Per-provider User-Agent for the stream; null/blank falls back to the global User-Agent.
    val userAgent: String? = null,
)

enum class PlaybackMediaType { Channel, Movie, Episode, CatchUp }

enum class PlaybackOrigin { Home, LiveTv, MovieDetail, SeriesDetail, Search, AndroidTv, Unknown }

enum class PlaybackReturnTarget { Home, LiveTv, MovieDetail, SeriesDetail, Unknown }

data class PlaybackTimeshiftConfig(
    val storage: PlaybackTimeshiftStorage,
    val windowMillis: Long,
) {
    val enabled: Boolean
        get() = windowMillis > 0L
}

enum class PlaybackTimeshiftStorage { Automatic, Ram, InternalStorage }

enum class PlaybackAudioOption { SystemDefault, German, English, Original }

enum class PlaybackSubtitleOption { Off, SystemDefault, German, English }

enum class PlaybackAspectRatioMode { Fit, Fill, Zoom }

private const val LIVE_EDGE_TOLERANCE_MILLIS = 2_000L

data class VivicastPlayerState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val request: PlaybackRequest? = null,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val liveEdgeOffsetMillis: Long = 0L,
    val timeshiftWindowMillis: Long = 0L,
    val timeshiftStorage: PlaybackTimeshiftStorage? = null,
    val audioOption: PlaybackAudioOption = PlaybackAudioOption.SystemDefault,
    val subtitleOption: PlaybackSubtitleOption = PlaybackSubtitleOption.Off,
    val aspectRatioMode: PlaybackAspectRatioMode = PlaybackAspectRatioMode.Fit,
    val isReconnecting: Boolean = false,
    val error: PlaybackError? = null,
) {
    val isTimeshiftEnabled: Boolean
        get() = timeshiftWindowMillis > 0L

    val isAtLiveEdge: Boolean
        get() = !isTimeshiftEnabled || liveEdgeOffsetMillis <= LIVE_EDGE_TOLERANCE_MILLIS
}

enum class PlaybackStatus { Idle, Starting, Playing, Paused, Ended, Error, Released }

data class PlaybackError(
    val playbackId: String,
    val retryCount: Int,
    val message: String,
)

class DefaultVivicastPlayerController(
    private val engine: PlaybackEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val maxStartAttempts: Int = DEFAULT_MAX_START_ATTEMPTS,
    private val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
    private val retryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MILLIS,
) : VivicastPlayerController {
    private val mutableState = MutableStateFlow(VivicastPlayerState())
    private var startJob: Job? = null
    private var progressJob: Job? = null
    private var reconnectJob: Job? = null
    private var engineErrorJob: Job? = null
    private var engineEndedJob: Job? = null
    private var liveEdgeOffsetMillis = 0L
    private var released = false

    override val state: StateFlow<VivicastPlayerState> = mutableState.asStateFlow()

    init {
        engineErrorJob = scope.launch(dispatcher) {
            engine.playbackErrors.collect { error ->
                handlePlaybackError(error)
            }
        }
        engineEndedJob = scope.launch(dispatcher) {
            engine.playbackEnded.collect { playbackId ->
                handlePlaybackEnded(playbackId)
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

    override fun selectAudio(option: PlaybackAudioOption) {
        if (released) return
        engine.selectAudio(option)
        mutableState.value = mutableState.value.copy(audioOption = option)
    }

    override fun selectSubtitle(option: PlaybackSubtitleOption) {
        if (released) return
        engine.selectSubtitle(option)
        mutableState.value = mutableState.value.copy(subtitleOption = option)
    }

    override fun selectAspectRatio(mode: PlaybackAspectRatioMode) {
        if (released) return
        engine.selectAspectRatio(mode)
        mutableState.value = mutableState.value.copy(aspectRatioMode = mode)
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
        engineEndedJob?.cancel()
        engineEndedJob = null
        stopProgressPolling()
        engine.release()
        liveEdgeOffsetMillis = 0L
        mutableState.value = VivicastPlayerState(status = PlaybackStatus.Released)
    }

    private suspend fun startWithRetries(request: PlaybackRequest) {
        var attempt = 1
        var activeRequest = request
        while (attempt <= maxStartAttempts) {
            try {
                engine.start(activeRequest)
                mutableState.value = playingState(activeRequest)
                startProgressPolling()
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                activeRequest.timeshiftFallbackRequest()?.let { fallbackRequest ->
                    activeRequest = fallbackRequest
                    continue
                }
                if (attempt >= maxStartAttempts) {
                    stopProgressPolling()
                    mutableState.value = VivicastPlayerState(
                        status = PlaybackStatus.Error,
                        request = activeRequest,
                        error = PlaybackError(
                            playbackId = activeRequest.playbackId,
                            retryCount = attempt,
                            message = error.message ?: "Playback start failed.",
                        ),
                    )
                    return
                }
                val retryDelayMillis = retryDelaysMillis.retryDelayAfterFailedAttempt(attempt)
                if (retryDelayMillis > 0L) {
                    delay(retryDelayMillis)
                }
                attempt += 1
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

    private fun handlePlaybackEnded(playbackId: String) {
        if (released) return
        val current = mutableState.value
        val request = current.request ?: return
        if (request.playbackId != playbackId) return
        if (current.status != PlaybackStatus.Playing && current.status != PlaybackStatus.Paused) return

        stopProgressPolling()
        val durationMillis = maxOf(current.durationMillis, engine.durationMillis).coerceAtLeast(0L)
        val positionMillis = maxOf(current.positionMillis, engine.currentPositionMillis, durationMillis)
            .coerceAtLeast(0L)
        mutableState.value = current.copy(
            status = PlaybackStatus.Ended,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
        )
    }

    private suspend fun reconnectWithRetries(
        request: PlaybackRequest,
        lastError: Throwable,
        lastPositionMillis: Long,
    ) {
        stopProgressPolling()
        var attempt = 1
        while (attempt <= maxReconnectAttempts) {
            mutableState.value = VivicastPlayerState(
                status = PlaybackStatus.Starting,
                request = request,
                positionMillis = lastPositionMillis,
                durationMillis = mutableState.value.durationMillis,
                isReconnecting = true,
            )
            val retryDelayMillis = retryDelaysMillis.retryDelayAfterFailedAttempt(attempt - 1)
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
            attempt += 1
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

    private fun PlaybackRequest.timeshiftFallbackRequest(): PlaybackRequest? =
        takeIf { it.isLiveTimeshift() }?.copy(seekable = false, timeshift = null)

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
        const val DEFAULT_MAX_START_ATTEMPTS = 5
        const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 5
        val DEFAULT_RETRY_DELAYS_MILLIS = listOf(500L, 1_000L, 2_000L, 4_000L)
        private const val PROGRESS_POLL_INTERVAL_MILLIS = 1_000L
    }
}

internal fun List<Long>.retryDelayAfterFailedAttempt(failedAttempt: Int): Long =
    getOrNull(failedAttempt - 1)?.coerceAtLeast(0L) ?: 0L

interface PlaybackEngine {
    val playbackErrors: Flow<Throwable>
        get() = emptyFlow()

    val playbackEnded: Flow<String>
        get() = emptyFlow()

    val currentPositionMillis: Long

    val durationMillis: Long

    suspend fun start(request: PlaybackRequest)

    fun pause()

    fun resume()

    fun seekBy(deltaMillis: Long)

    fun selectAudio(option: PlaybackAudioOption) = Unit

    fun selectSubtitle(option: PlaybackSubtitleOption) = Unit

    fun selectAspectRatio(mode: PlaybackAspectRatioMode) = Unit

    fun stop()

    fun release()
}

class Media3PlaybackEngine(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val userAgentProvider: () -> String = { DEFAULT_USER_AGENT },
) : PlaybackEngine {
    private val appContext = context.applicationContext
    private val timeshiftCache = SimpleCache(
        File(appContext.cacheDir, TIMESHIFT_CACHE_DIR_NAME),
        LeastRecentlyUsedCacheEvictor(TIMESHIFT_CACHE_MAX_BYTES),
        StandaloneDatabaseProvider(appContext),
    )
    private val player = ExoPlayer.Builder(appContext)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBackBuffer(TIMESHIFT_BACK_BUFFER_MILLIS, true)
                .build(),
        )
        .build()
    private val playbackErrorEvents = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)
    private val playbackEndedEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private var activePlaybackId: String? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playbackErrorEvents.tryEmit(error)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        activePlaybackId?.let { playbackEndedEvents.tryEmit(it) }
                    }
                }
            },
        )
    }

    override val playbackErrors: Flow<Throwable> = playbackErrorEvents
    override val playbackEnded: Flow<String> = playbackEndedEvents

    override val currentPositionMillis: Long
        get() = player.currentPosition.coerceAtLeast(0L)

    override val durationMillis: Long
        get() = player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L

    override suspend fun start(request: PlaybackRequest) {
        withContext(dispatcher) {
            activePlaybackId = request.playbackId
            val mediaItem = MediaItem.fromUri(request.streamUrl)
            val defaultDataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory(request.userAgent))
            val mediaSourceFactory = if (request.timeshift?.storage == PlaybackTimeshiftStorage.InternalStorage) {
                DefaultMediaSourceFactory(
                    CacheDataSource.Factory()
                        .setCache(timeshiftCache)
                        .setUpstreamDataSourceFactory(defaultDataSourceFactory)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
                )
            } else {
                DefaultMediaSourceFactory(defaultDataSourceFactory)
            }
            player.setMediaSource(
                mediaSourceFactory.createMediaSource(mediaItem),
                request.startPositionMillis,
            )
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

    override fun selectAudio(option: PlaybackAudioOption) {
        val languages = option.preferredLanguageCodes()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguages(*languages.toTypedArray())
            .build()
    }

    override fun selectSubtitle(option: PlaybackSubtitleOption) {
        val languages = option.preferredLanguageCodes()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, option == PlaybackSubtitleOption.Off)
            .setPreferredTextLanguages(*languages.toTypedArray())
            .build()
    }

    override fun selectAspectRatio(mode: PlaybackAspectRatioMode) {
        player.videoScalingMode = when (mode) {
            PlaybackAspectRatioMode.Fit -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            PlaybackAspectRatioMode.Fill,
            PlaybackAspectRatioMode.Zoom -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        }
    }

    override fun stop() {
        activePlaybackId = null
        player.stop()
        player.clearMediaItems()
    }

    override fun release() {
        activePlaybackId = null
        player.release()
        timeshiftCache.release()
    }

    private companion object {
        const val TIMESHIFT_CACHE_DIR_NAME = "playback-timeshift"
        const val TIMESHIFT_BACK_BUFFER_MILLIS = 120 * 60 * 1_000
        const val TIMESHIFT_CACHE_MAX_BYTES = 512L * 1024L * 1024L
        const val DEFAULT_USER_AGENT = "Vivicast/1.0"
    }

    private fun httpDataSourceFactory(userAgent: String? = null): DefaultHttpDataSource.Factory {
        val resolved = userAgent?.trim()?.takeIf { it.isNotEmpty() } ?: userAgentProvider()
        return DefaultHttpDataSource.Factory().setUserAgent(resolved.normalizedUserAgent())
    }
}

private fun String.normalizedUserAgent(): String =
    trim().takeIf { it.isNotBlank() } ?: "Vivicast/1.0"

private fun PlaybackAudioOption.preferredLanguageCodes(): List<String> =
    when (this) {
        PlaybackAudioOption.SystemDefault -> systemLanguageCodes()
        PlaybackAudioOption.German -> listOf("de", "deu", "ger")
        PlaybackAudioOption.English -> listOf("en", "eng")
        PlaybackAudioOption.Original -> emptyList()
    }

private fun PlaybackSubtitleOption.preferredLanguageCodes(): List<String> =
    when (this) {
        PlaybackSubtitleOption.Off -> emptyList()
        PlaybackSubtitleOption.SystemDefault -> systemLanguageCodes()
        PlaybackSubtitleOption.German -> listOf("de", "deu", "ger")
        PlaybackSubtitleOption.English -> listOf("en", "eng")
    }

private fun systemLanguageCodes(): List<String> =
    Locale.getDefault().language.takeIf { it.isNotBlank() }?.let(::listOf) ?: emptyList()
