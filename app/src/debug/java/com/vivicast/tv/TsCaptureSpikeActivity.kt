package com.vivicast.tv

import android.os.Bundle
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vivicast.tv.core.network.NetworkClientFactory
import com.vivicast.tv.core.player.DefaultVivicastPlayerController
import com.vivicast.tv.core.player.Media3PlaybackEngine
import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.VivicastPlayerController
import com.vivicast.tv.core.player.timeshift.MultiFileTailingDataSource
import kotlinx.coroutines.delay
import java.io.File

/**
 * Debug-only spike (Phase 3 / Fall B, mechanism **K2** — multi-file variant). [SegmentedTsRecorder] captures a
 * live progressive-TS stream verbatim into fixed-size `seg-N.ts` files (rolling window, front-trimmed); the real
 * [VivicastPlayerController] + [Media3PlaybackEngine] play that directory via [MultiFileTailingDataSource].
 * Validates: seamless live across segment rollovers (no glitch), clean seek-back within the retained window,
 * front-trim of old segments — all on one connection, byte-identical to the source (no re-mux).
 *
 * Launch (no URL in the repo — pass a progressive-TS live URL, e.g. a Tvheadend `?profile=pass`, at runtime):
 *   adb shell am start -n com.vivicast.tv/.TsCaptureSpikeActivity --es url "<progressive-ts-url>"
 */
class TsCaptureSpikeActivity : ComponentActivity() {

    private lateinit var controller: VivicastPlayerController
    private lateinit var recorder: SegmentedTsRecorder
    private lateinit var bufferDir: File
    private var playRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url").orEmpty()
        val client = NetworkClientFactory().createOkHttpClient(
            userAgentProvider = { USER_AGENT },
            trustAllCertificates = BuildConfig.DEBUG,
        )
        bufferDir = File(cacheDir, "ts-spike")
        controller = DefaultVivicastPlayerController(Media3PlaybackEngine(applicationContext))
        recorder = SegmentedTsRecorder(
            url, USER_AGENT, bufferDir, client,
            segmentBytes = MultiFileTailingDataSource.DEFAULT_SEGMENT_BYTES,
            maxSegments = MAX_SEGMENTS,
        )
        if (url.isNotBlank()) recorder.start()

        setContent {
            val state by controller.state.collectAsState()
            val label = TextStyle(color = Color.White, fontSize = 15.sp)
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { ctx -> SurfaceView(ctx).also { controller.attachVideoSurface(it) } },
                    modifier = Modifier.fillMaxSize(),
                )
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(
                        "url set: ${url.isNotBlank()}  capture=${recorder.status}  seg=${recorder.segmentCount}  captured=${recorder.capturedBytes / 1_000_000}MB  started=$playRequested",
                        style = label,
                    )
                    BasicText(
                        "status=${state.status}  pos=${state.positionMillis / 1000}s  window=${state.timeshiftWindowMillis / 1000}s  " +
                            "behindLive=${state.liveEdgeOffsetMillis / 1000}s  seekable=${state.request?.seekable}",
                        style = label,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpikeButton("-10s") { controller.seekBy(-10_000) }
                        SpikeButton("-30s") { controller.seekBy(-30_000) }
                        SpikeButton("+10s") { controller.seekBy(10_000) }
                        SpikeButton("Live") { controller.seekToLiveEdge() }
                    }
                }
            }

            // Wait for a few segments, then play the segmented buffer dir as a channel (multi-file tailing).
            LaunchedEffect(Unit) {
                while (!playRequested) {
                    if (recorder.segmentCount >= START_SEGMENTS) {
                        playRequested = true
                        controller.play(
                            PlaybackRequest(
                                playbackId = "ts-spike",
                                providerId = "spike",
                                mediaId = "spike",
                                mediaType = PlaybackMediaType.Channel,
                                title = "TS Multi-File Spike",
                                streamUrl = bufferDir.toURI().toString(),
                                seekable = true,
                                tailing = true,
                            ),
                        )
                    }
                    delay(500)
                }
            }
        }
    }

    override fun onDestroy() {
        recorder.stop()
        controller.release()
        super.onDestroy()
    }

    private companion object {
        const val USER_AGENT = "Vivicast/1.0"
        const val START_SEGMENTS = 3 // play once a few segments exist; tailing follows the growing edge
        // Large so front-trim does not catch the play head during the test (playback starts near offset 0 and
        // trim races forward — starting at the live edge is a separate production fix). Lets us validate seeks.
        const val MAX_SEGMENTS = 999
    }
}

@androidx.compose.runtime.Composable
private fun SpikeButton(text: String, onClick: () -> Unit) {
    BasicText(
        text = text,
        style = TextStyle(color = Color.White, fontSize = 18.sp),
        modifier = Modifier
            .background(Color(0xFF1E3A5F))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
