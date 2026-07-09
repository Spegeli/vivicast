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
import kotlinx.coroutines.delay
import java.io.File

/**
 * Debug-only spike (Phase 3 / Fall B, mechanism K1). Proves the no-gap local-capture timeshift end-to-end:
 * [LiveTsSegmenter] captures a progressive MPEG-TS live stream over ONE connection into a rolling local live
 * HLS playlist, and the REAL [VivicastPlayerController] + [Media3PlaybackEngine] play that local `index.m3u8`
 * as a channel. So it also exercises the Phase-1 native-window logic (the local live HLS is seekable → the
 * controller shows a timeshift window). Buttons rewind / return-to-live to check: does ExoPlayer play the
 * self-segmented TS, seek back within the window, and follow the growing edge with no gap, all on one connection?
 *
 * Launch (no URL in the repo — pass a progressive-TS live URL, e.g. a Tvheadend `?profile=pass`, at runtime):
 *   adb shell am start -n com.vivicast.tv/.TsCaptureSpikeActivity --es url "<progressive-ts-url>"
 */
class TsCaptureSpikeActivity : ComponentActivity() {

    private lateinit var controller: VivicastPlayerController
    private lateinit var segmenter: LiveTsSegmenter
    private var playRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url").orEmpty()
        val client = NetworkClientFactory().createOkHttpClient(
            userAgentProvider = { USER_AGENT },
            trustAllCertificates = BuildConfig.DEBUG,
        )
        controller = DefaultVivicastPlayerController(Media3PlaybackEngine(applicationContext))
        segmenter = LiveTsSegmenter(url, USER_AGENT, File(cacheDir, "ts-spike"), client)
        if (url.isNotBlank()) segmenter.start()

        setContent {
            val state by controller.state.collectAsState()
            val label = TextStyle(color = Color.White, fontSize = 15.sp)
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { ctx -> SurfaceView(ctx).also { controller.attachVideoSurface(it) } },
                    modifier = Modifier.fillMaxSize(),
                )
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText("url set: ${url.isNotBlank()}  capture=${segmenter.status}  segments=${segmenter.segmentCount}", style = label)
                    BasicText(
                        "status=${state.status}  pos=${state.positionMillis / 1000}s  window=${state.timeshiftWindowMillis / 1000}s  " +
                            "behindLive=${state.liveEdgeOffsetMillis / 1000}s",
                        style = label,
                    )
                    BasicText(
                        "timeshift=${state.isTimeshiftEnabled}  atLive=${state.isAtLiveEdge}  fps=${state.videoFrameRate}",
                        style = label,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpikeButton("-20s") { controller.seekBy(-20_000) }
                        SpikeButton("-2min") { controller.seekBy(-120_000) }
                        SpikeButton("+30s") { controller.seekBy(30_000) }
                        SpikeButton("Live") { controller.seekToLiveEdge() }
                    }
                }
            }

            // Wait until the segmenter has a few segments, then play the LOCAL rolling live HLS as a channel.
            LaunchedEffect(Unit) {
                while (!playRequested) {
                    if (segmenter.segmentCount >= START_AFTER_SEGMENTS) {
                        playRequested = true
                        controller.play(
                            PlaybackRequest(
                                playbackId = "ts-spike",
                                providerId = "spike",
                                mediaId = "spike",
                                mediaType = PlaybackMediaType.Channel,
                                title = "TS Capture Spike",
                                streamUrl = segmenter.playlistFile.toURI().toString(),
                                seekable = true,
                            ),
                        )
                    }
                    delay(500)
                }
            }
        }
    }

    override fun onDestroy() {
        segmenter.stop()
        controller.release()
        super.onDestroy()
    }

    private companion object {
        const val USER_AGENT = "Vivicast/1.0"
        const val START_AFTER_SEGMENTS = 3
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
