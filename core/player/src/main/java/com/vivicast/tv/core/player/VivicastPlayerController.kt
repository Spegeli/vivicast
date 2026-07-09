package com.vivicast.tv.core.player

import android.content.Context
import android.view.SurfaceView
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
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.vivicast.tv.core.player.timeshift.TailingFileDataSource
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeoutOrNull
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

    /** Attaches the video output surface. The realtime player screen owns the [SurfaceView] and hands it in. */
    fun attachVideoSurface(surfaceView: SurfaceView) = Unit

    fun detachVideoSurface() = Unit

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
    // Fall B / K2: streamUrl points at a local file that a live capture is still appending to; play it via
    // TailingFileDataSource so ExoPlayer follows the growing edge and can seek back into the captured window.
    val tailing: Boolean = false,
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

// Generous vs the old exact-virtual 2s: native live offset jitters as ExoPlayer speed-adjusts toward the edge.
private const val LIVE_EDGE_TOLERANCE_MILLIS = 5_000L

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
    val videoFrameRate: Float = 0f,
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
    // Running maximum of the native playback position for the current live channel = the live edge in the
    // position timeline (currentLiveOffset is unreliable — some manifests don't declare live timing). Behind-live
    // offset derives as liveEdge - position, so any native seek is reflected without extra bookkeeping.
    private var liveEdgePositionMillis = 0L
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
        liveEdgePositionMillis = 0L
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
        if (released) return
        val current = mutableState.value
        val request = current.request ?: return
        if (!request.seekable) return
        // Native seek: ExoPlayer clamps into the seekable/DVR window. State reflects the new native position.
        engine.seekBy(deltaMillis)
        mutableState.value = current.withNativeTimeline(request)
    }

    override fun seekToLiveEdge() {
        if (released) return
        val current = mutableState.value
        val request = current.request ?: return
        if (!current.isTimeshiftEnabled || current.isAtLiveEdge) return
        if (request.tailing) {
            // Growing local capture: the live edge is the current end of the file. seekToDefaultPosition would
            // jump to the START of a progressive file, so instead jump far forward — ExoPlayer clamps to the end.
            engine.seekBy(LIVE_EDGE_SEEK_FORWARD_MILLIS)
        } else {
            engine.seekToLiveEdge()
        }
        mutableState.value = current.withNativeTimeline(request)
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

    override fun attachVideoSurface(surfaceView: SurfaceView) {
        if (released) return
        engine.attachVideoSurface(surfaceView)
    }

    override fun detachVideoSurface() {
        if (released) return
        engine.detachVideoSurface()
    }

    override fun stop() {
        if (released) return
        startJob?.cancel()
        startJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        stopProgressPolling()
        engine.stop()
        liveEdgePositionMillis = 0L
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
        liveEdgePositionMillis = 0L
        mutableState.value = VivicastPlayerState(status = PlaybackStatus.Released)
    }

    private suspend fun startWithRetries(request: PlaybackRequest) {
        var attempt = 1
        val activeRequest = request
        while (attempt <= maxStartAttempts) {
            try {
                engine.start(activeRequest)
                mutableState.value = playingState(activeRequest)
                startProgressPolling()
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
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
                        // Live channels resume at the live edge; VOD resumes where it dropped.
                        request.mediaType == PlaybackMediaType.Channel -> 0L
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
            while (true) {
                delay(PROGRESS_POLL_INTERVAL_MILLIS)
                val current = mutableState.value
                if (current.status != PlaybackStatus.Playing && current.status != PlaybackStatus.Paused) {
                    return@launch
                }
                val request = current.request ?: return@launch
                // Frame rate becomes known shortly after tracks load; picked up on the next poll for AFR.
                mutableState.value = current.withNativeTimeline(request)
                    .copy(videoFrameRate = engine.videoFrameRate)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun playingState(request: PlaybackRequest): VivicastPlayerState =
        VivicastPlayerState(status = PlaybackStatus.Playing, request = request).withNativeTimeline(request)

    /** True for a live channel whose stream exposes a native seek window (HLS DVR) — the timeshift case. */
    private fun isNativeLiveTimeshift(request: PlaybackRequest): Boolean =
        request.mediaType == PlaybackMediaType.Channel && request.seekable && engine.isCurrentSeekable

    /**
     * Populates position/duration/timeshift fields from ExoPlayer's native timeline. For a live channel with a
     * DVR window, timeshiftWindowMillis = the native DVR depth, and behind-live offset = liveEdge - nativePosition
     * where liveEdge is the running max of the native position. Position/duration are remapped onto the DVR window
     * so the timeline bar reads 100% at live and shrinks as you rewind. VOD/non-seekable get plain native values.
     */
    private fun VivicastPlayerState.withNativeTimeline(request: PlaybackRequest): VivicastPlayerState {
        val position = engine.currentPositionMillis
        return when {
            // Fall B: a growing local capture (tailing). ExoPlayer has no duration, so the seekable window is
            // what has been captured so far = the running-max position (position is already window-relative:
            // 0 = buffer start, liveEdge = newest captured). Offset = liveEdge - position.
            request.tailing -> {
                liveEdgePositionMillis = maxOf(liveEdgePositionMillis, position)
                val window = liveEdgePositionMillis
                copy(
                    positionMillis = position,
                    durationMillis = window,
                    timeshiftWindowMillis = window,
                    liveEdgeOffsetMillis = (window - position).coerceAtLeast(0L),
                    timeshiftStorage = request.timeshift?.storage,
                )
            }
            isNativeLiveTimeshift(request) -> {
                val window = engine.durationMillis
                liveEdgePositionMillis = maxOf(liveEdgePositionMillis, position)
                // ponytail: no wall-clock advance, so a long PAUSE at live won't grow the offset until playback
                // resumes and catches up — add clock-based advance only if the paused-behind label matters.
                val offset = (liveEdgePositionMillis - position).coerceIn(0L, window)
                copy(
                    positionMillis = (window - offset).coerceAtLeast(0L),
                    durationMillis = window,
                    timeshiftWindowMillis = window,
                    liveEdgeOffsetMillis = offset,
                    timeshiftStorage = request.timeshift?.storage,
                )
            }
            else -> copy(
                positionMillis = position,
                durationMillis = engine.durationMillis,
                timeshiftWindowMillis = 0L,
                liveEdgeOffsetMillis = 0L,
            )
        }
    }

    companion object {
        const val DEFAULT_MAX_START_ATTEMPTS = 5
        const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 5
        val DEFAULT_RETRY_DELAYS_MILLIS = listOf(500L, 1_000L, 2_000L, 4_000L)
        private const val PROGRESS_POLL_INTERVAL_MILLIS = 1_000L
        // Far-forward seek for a tailing (growing) source: ExoPlayer clamps it to the current buffer end = live.
        private const val LIVE_EDGE_SEEK_FORWARD_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

internal fun List<Long>.retryDelayAfterFailedAttempt(failedAttempt: Int): Long =
    getOrNull(failedAttempt - 1)?.coerceAtLeast(0L) ?: 0L

/**
 * Bridges ExoPlayer's asynchronous `prepare()` onto the suspend [PlaybackEngine.start] contract:
 * start() must not report success until playback actually began, and must throw when it fails.
 * Without this, an HTTP 404/500 lets start() return "success" (prepare() never throws) while the real
 * error only arrives later via `onPlayerError` — which made the reconnect logic loop forever on
 * "Reconnecting…" instead of settling into the error dialog.
 */
internal class PlaybackStartGate {
    @Volatile
    private var pending: CompletableDeferred<Unit>? = null

    /** Arms a fresh gate for the start about to be issued. Call BEFORE prepare() so no signal is missed. */
    fun arm(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { pending = it }

    fun onReady() {
        pending?.complete(Unit)
    }

    fun onError(error: Throwable) {
        pending?.completeExceptionally(error)
    }
}

internal class PlaybackStartTimeoutException(timeoutMillis: Long) :
    Exception("Playback did not reach a ready state within ${timeoutMillis}ms.")

/** Suspends until the gate reports ready, rethrows its error, or throws on timeout. */
internal suspend fun CompletableDeferred<Unit>.awaitStartedOrThrow(timeoutMillis: Long) {
    withTimeoutOrNull(timeoutMillis) { await() }
        ?: throw PlaybackStartTimeoutException(timeoutMillis)
}

interface PlaybackEngine {
    val playbackErrors: Flow<Throwable>
        get() = emptyFlow()

    val playbackEnded: Flow<String>
        get() = emptyFlow()

    val currentPositionMillis: Long

    val durationMillis: Long

    /** Source content frame rate (fps), 0 if unknown — used for auto frame-rate matching. */
    val videoFrameRate: Float
        get() = 0f

    /** True when the current media item exposes a native seek window (e.g. HLS live DVR); false for progressive TS. */
    val isCurrentSeekable: Boolean
        get() = false

    suspend fun start(request: PlaybackRequest)

    fun pause()

    fun resume()

    fun seekBy(deltaMillis: Long)

    /** Seek to the native live edge (default position) of the current media item. */
    fun seekToLiveEdge() = Unit

    fun selectAudio(option: PlaybackAudioOption) = Unit

    fun selectSubtitle(option: PlaybackSubtitleOption) = Unit

    fun selectAspectRatio(mode: PlaybackAspectRatioMode) = Unit

    fun attachVideoSurface(surfaceView: SurfaceView) = Unit

    fun detachVideoSurface() = Unit

    fun stop()

    fun release()
}

class Media3PlaybackEngine(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val userAgentProvider: () -> String = { DEFAULT_USER_AGENT },
    private val tuningProvider: () -> PlaybackTuning = { PlaybackTuning() },
) : PlaybackEngine {
    private val appContext = context.applicationContext
    private val timeshiftCache = SimpleCache(
        File(appContext.cacheDir, TIMESHIFT_CACHE_DIR_NAME),
        LeastRecentlyUsedCacheEvictor(TIMESHIFT_CACHE_MAX_BYTES),
        StandaloneDatabaseProvider(appContext),
    )
    // Buffer/decoder/passthrough live on ExoPlayer.Builder and can't change on a live player, so the player is
    // (re)built from a tuning snapshot: once here, then again in start() whenever the builder-subset changes —
    // exactly "applies at next stream start". Eagerly non-null so the forwarding methods need no null-guards.
    private var builtTuning = PlaybackTuning()
    private var player: ExoPlayer = buildExoPlayer(appContext, builtTuning)
    private val playbackErrorEvents = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)
    private val playbackEndedEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val startGate = PlaybackStartGate()
    private var activePlaybackId: String? = null
    // Remembered so it can be re-attached to a freshly rebuilt player.
    private var videoSurfaceView: SurfaceView? = null

    init {
        player.addListener(newPlayerListener())
    }

    private fun newPlayerListener(): Player.Listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            // Fail the in-flight start (so start() throws for the retry logic) AND keep emitting to the
            // flow so a mid-watch death — after start already succeeded — still triggers the reconnect.
            startGate.onError(error)
            playbackErrorEvents.tryEmit(error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> startGate.onReady()
                Player.STATE_ENDED -> activePlaybackId?.let { playbackEndedEvents.tryEmit(it) }
            }
        }
    }

    /** Rebuilds the player only when a build-time setting changed, re-attaching the video surface. */
    private fun rebuildPlayerIfTuningChanged(tuning: PlaybackTuning) {
        if (tuning.builderSubset == builtTuning.builderSubset) return
        player.release()
        player = buildExoPlayer(appContext, tuning).also { it.addListener(newPlayerListener()) }
        builtTuning = tuning
        videoSurfaceView?.let { player.setVideoSurfaceView(it) }
    }

    override val playbackErrors: Flow<Throwable> = playbackErrorEvents
    override val playbackEnded: Flow<String> = playbackEndedEvents

    override fun attachVideoSurface(surfaceView: SurfaceView) {
        videoSurfaceView = surfaceView
        player.setVideoSurfaceView(surfaceView)
    }

    override fun detachVideoSurface() {
        player.clearVideoSurface()
        videoSurfaceView = null
    }

    override val currentPositionMillis: Long
        get() = player.currentPosition.coerceAtLeast(0L)

    override val durationMillis: Long
        get() = player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L

    override val videoFrameRate: Float
        get() = player.videoFormat?.frameRate?.takeIf { it > 0f } ?: 0f

    override val isCurrentSeekable: Boolean
        get() = player.isCurrentMediaItemSeekable

    override suspend fun start(request: PlaybackRequest) {
        val gate = startGate.arm()
        val tuning = tuningProvider()
        withContext(dispatcher) {
            rebuildPlayerIfTuningChanged(tuning)
            activePlaybackId = request.playbackId
            val mediaItem = MediaItem.fromUri(request.streamUrl)
            val defaultDataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory(request.userAgent))
            val useDisk = request.timeshift?.let { usesDiskCache(it.storage, it.windowMillis) } == true
            val mediaSourceFactory: MediaSource.Factory = when {
                // Fall B / K2: a live-captured local file that keeps growing → tail it, stay seekable.
                request.tailing -> ProgressiveMediaSource.Factory(TailingFileDataSource.Factory())
                useDisk -> DefaultMediaSourceFactory(
                    CacheDataSource.Factory()
                        .setCache(timeshiftCache)
                        .setUpstreamDataSourceFactory(defaultDataSourceFactory)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
                )
                else -> DefaultMediaSourceFactory(defaultDataSourceFactory)
            }
            player.setMediaSource(
                mediaSourceFactory.createMediaSource(mediaItem),
                request.startPositionMillis,
            )
            player.prepare()
            player.playWhenReady = true
            // Seed the initial track selection from the settings snapshot (spec: applied at stream start).
            // A later in-player overlay pick overrides this for the current playback only.
            selectAudio(tuning.preferredAudio)
            selectSubtitle(tuning.preferredSubtitle)
        }
        // Honour the PlaybackEngine.start contract: don't report success until ExoPlayer actually
        // reached READY. A 404/500/dead-host surfaces via onPlayerError and makes this throw so the
        // controller's retry/reconnect budget applies. The timeout is only a backstop for a
        // connected-but-silent endpoint — a real HTTP error fires onPlayerError in well under a second.
        // ponytail: fixed 10s ceiling; make it injectable only if a slow-but-alive stream trips it.
        gate.awaitStartedOrThrow(START_READY_TIMEOUT_MILLIS)
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

    override fun seekToLiveEdge() {
        // Default position of a live window is the live edge; ExoPlayer clamps into the DVR window.
        player.seekToDefaultPosition()
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
        const val TIMESHIFT_CACHE_MAX_BYTES = 512L * 1024L * 1024L
        const val DEFAULT_USER_AGENT = "Vivicast/1.0"
        const val START_READY_TIMEOUT_MILLIS = 10_000L
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
