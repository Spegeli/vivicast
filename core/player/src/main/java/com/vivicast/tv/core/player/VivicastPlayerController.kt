package com.vivicast.tv.core.player

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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

    /** Selects a concrete stream track (from [VivicastPlayerState.audioTracks]/[subtitleTracks]). */
    fun selectAudioTrack(track: PlaybackTrack) = Unit

    fun selectTextTrack(track: PlaybackTrack) = Unit

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
    // Per-provider User-Agent for the stream; null/blank falls back to the global User-Agent.
    val userAgent: String? = null,
)

enum class PlaybackMediaType { Channel, Movie, Episode, CatchUp }

enum class PlaybackOrigin { Home, LiveTv, MovieDetail, SeriesDetail, Search, AndroidTv, Unknown }

enum class PlaybackReturnTarget { Home, LiveTv, MovieDetail, SeriesDetail, Unknown }

enum class PlaybackAudioOption { SystemDefault, German, English, Original }

enum class PlaybackSubtitleOption { Off, SystemDefault, German, English }

enum class PlaybackAspectRatioMode { Fit, Fill, Zoom }

enum class PlaybackTrackType { Audio, Text }

/**
 * A selectable audio/text track detected in the current stream. [groupIndex]/[trackIndex] locate it within the
 * player's `Tracks` so a later override can re-resolve the media group. [label] is the raw stream label (may be
 * null); the UI derives the display name from [label]/[language].
 */
data class PlaybackTrack(
    val type: PlaybackTrackType,
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
    // Audio channel count (-1 = unknown); distinguishes e.g. a stereo vs 5.1 track of the same language.
    val channelCount: Int = -1,
    val isSelected: Boolean,
)

// Generous vs the old exact-virtual 2s: native live offset jitters as ExoPlayer speed-adjusts toward the edge.
private const val LIVE_EDGE_TOLERANCE_MILLIS = 5_000L

data class VivicastPlayerState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val request: PlaybackRequest? = null,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val liveEdgeOffsetMillis: Long = 0L,
    val timeshiftWindowMillis: Long = 0L,
    val audioOption: PlaybackAudioOption = PlaybackAudioOption.SystemDefault,
    val subtitleOption: PlaybackSubtitleOption = PlaybackSubtitleOption.Off,
    val aspectRatioMode: PlaybackAspectRatioMode = PlaybackAspectRatioMode.Fit,
    // Audio/text tracks actually present in the current stream, for the in-player language picker.
    val audioTracks: List<PlaybackTrack> = emptyList(),
    val subtitleTracks: List<PlaybackTrack> = emptyList(),
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
        engine.seekBy(deltaMillis)
        mutableState.value = current.withNativeTimeline(request)
    }

    override fun seekToLiveEdge() {
        if (released) return
        val current = mutableState.value
        val request = current.request ?: return
        if (!current.isTimeshiftEnabled || current.isAtLiveEdge) return
        engine.seekToLiveEdge()
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
        mutableState.value = mutableState.value.copy(subtitleOption = option).withEngineTracks()
    }

    override fun selectAudioTrack(track: PlaybackTrack) {
        if (released) return
        engine.selectAudioTrack(track)
        mutableState.value = mutableState.value.withEngineTracks()
    }

    override fun selectTextTrack(track: PlaybackTrack) {
        if (released) return
        engine.selectTextTrack(track)
        mutableState.value = mutableState.value.withEngineTracks()
    }

    // Optimistically refresh the track lists after a selection; the 1s progress poll corrects any async lag.
    private fun VivicastPlayerState.withEngineTracks(): VivicastPlayerState =
        copy(audioTracks = engine.audioTracks, subtitleTracks = engine.textTracks)

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
                // Frame rate + available tracks become known shortly after tracks load; picked up each poll.
                mutableState.value = current.withNativeTimeline(request)
                    .copy(
                        videoFrameRate = engine.videoFrameRate,
                        audioTracks = engine.audioTracks,
                        subtitleTracks = engine.textTracks,
                    )
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

    /** Audio/text tracks present in the current stream, for the in-player picker. */
    val audioTracks: List<PlaybackTrack>
        get() = emptyList()

    val textTracks: List<PlaybackTrack>
        get() = emptyList()

    suspend fun start(request: PlaybackRequest)

    fun pause()

    fun resume()

    fun seekBy(deltaMillis: Long)

    /** Seek to the native live edge (default position) of the current media item. */
    fun seekToLiveEdge() = Unit

    fun selectAudio(option: PlaybackAudioOption) = Unit

    fun selectSubtitle(option: PlaybackSubtitleOption) = Unit

    fun selectAudioTrack(track: PlaybackTrack) = Unit

    fun selectTextTrack(track: PlaybackTrack) = Unit

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
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                // Paused/rewound longer than the DVR window, so the position fell out of it. Recover in place at
                // the EARLIEST still-available position (seekTo(0) = window start) instead of jumping to the live
                // edge — the user keeps the oldest retained content rather than losing everything since the pause.
                // Handled here (not propagated) so no reconnect UI flashes. ponytail: the controller's running-max
                // offset briefly lags after the re-prepare and self-corrects on the next progress poll.
                player.seekTo(0L)
                player.prepare()
                return
            }
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
        // Build the new player BEFORE releasing the old one: if buildExoPlayer throws (e.g. an invalid
        // LoadControl config), the old player + builtTuning stay intact so the retry keeps a live player
        // instead of wedging on a released instance.
        val next = buildExoPlayer(appContext, tuning).also { it.addListener(newPlayerListener()) }
        player.release()
        player = next
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

    override val audioTracks: List<PlaybackTrack>
        get() = readTracks(C.TRACK_TYPE_AUDIO, PlaybackTrackType.Audio)

    override val textTracks: List<PlaybackTrack>
        get() = readTracks(C.TRACK_TYPE_TEXT, PlaybackTrackType.Text)

    // One entry per track GROUP (a group's tracks are usually bitrate variants of the same language/version —
    // ExoPlayer adapts within it), then collapse groups sharing language+label (some streams declare one group
    // per video variant, e.g. ZDF's 2 audio groups → 3 unique versions). ponytail: matched by group index at
    // selection time; a mid-watch manifest reshuffle is a rare, self-correcting edge.
    private fun readTracks(trackType: Int, mapped: PlaybackTrackType): List<PlaybackTrack> {
        val perGroup = mutableListOf<PlaybackTrack>()
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != trackType) return@forEachIndexed
            val representative = (0 until group.length).firstOrNull { group.isTrackSupported(it) }
                ?: return@forEachIndexed
            val format = group.getTrackFormat(representative)
            perGroup += PlaybackTrack(
                type = mapped,
                groupIndex = groupIndex,
                trackIndex = representative,
                language = format.language,
                label = format.label,
                channelCount = format.channelCount,
                isSelected = (0 until group.length).any { group.isTrackSelected(it) },
            )
        }
        // Key on channelCount too, so a genuine stereo vs 5.1 pair (same language+label) is NOT collapsed —
        // only true redundancy (e.g. a stream's A/B origin copies) is.
        return perGroup
            .groupBy { Triple(it.language, it.label, it.channelCount) }
            .map { (_, duplicates) -> duplicates.firstOrNull { it.isSelected } ?: duplicates.first() }
    }

    override fun selectAudioTrack(track: PlaybackTrack) = applyTrackOverride(C.TRACK_TYPE_AUDIO, track)

    override fun selectTextTrack(track: PlaybackTrack) = applyTrackOverride(C.TRACK_TYPE_TEXT, track)

    /** Pins the track's whole group via a TrackSelectionOverride (adaptive across its bitrate variants). */
    private fun applyTrackOverride(trackType: Int, track: PlaybackTrack) {
        val mediaGroup = player.currentTracks.groups.getOrNull(track.groupIndex)?.mediaTrackGroup ?: return
        val indices = (0 until mediaGroup.length).toList()
        if (indices.isEmpty()) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(mediaGroup, indices))
            .setTrackTypeDisabled(trackType, false)
            .build()
    }

    override suspend fun start(request: PlaybackRequest) {
        val gate = startGate.arm()
        val tuning = tuningProvider()
        withContext(dispatcher) {
            rebuildPlayerIfTuningChanged(tuning)
            activePlaybackId = request.playbackId
            val defaultDataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory(request.userAgent))
            player.setMediaSource(
                DefaultMediaSourceFactory(defaultDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(request.streamUrl)),
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
    }

    private companion object {
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
