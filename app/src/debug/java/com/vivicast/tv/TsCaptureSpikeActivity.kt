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
 * Debug-only spike (Phase 3 / Fall B, mechanism **K2** — concat, no re-mux). [RawTsRecorder] appends a live
 * progressive-TS byte stream verbatim to one `buffer.ts`; the real [VivicastPlayerController] +
 * [Media3PlaybackEngine] then play that file. Because it is byte-identical to the original stream it should
 * decode cleanly on a real hardware decoder and be seekable via TsExtractor — the exact thing K1 (hand
 * re-segmenting to local HLS) failed on the TV. Buttons rewind so we can see if the concat file is clean + seekable.
 *
 * Launch (no URL in the repo — pass a progressive-TS live URL, e.g. a Tvheadend `?profile=pass`, at runtime):
 *   adb shell am start -n com.vivicast.tv/.TsCaptureSpikeActivity --es url "<progressive-ts-url>"
 */
class TsCaptureSpikeActivity : ComponentActivity() {

    private lateinit var controller: VivicastPlayerController
    private lateinit var recorder: RawTsRecorder
    private lateinit var bufferFile: File
    private var playRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url").orEmpty()
        val client = NetworkClientFactory().createOkHttpClient(
            userAgentProvider = { USER_AGENT },
            trustAllCertificates = BuildConfig.DEBUG,
        )
        bufferFile = File(cacheDir, "ts-spike/buffer.ts")
        controller = DefaultVivicastPlayerController(Media3PlaybackEngine(applicationContext))
        recorder = RawTsRecorder(url, USER_AGENT, bufferFile, client)
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
                        "url set: ${url.isNotBlank()}  capture=${recorder.status}  captured=${recorder.capturedBytes / 1_000_000}MB  started=$playRequested",
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

            // Wait until enough bytes are captured, then play the concat buffer.ts as a channel.
            LaunchedEffect(Unit) {
                while (!playRequested) {
                    if (recorder.capturedBytes >= START_BYTES) {
                        playRequested = true
                        controller.play(
                            PlaybackRequest(
                                playbackId = "ts-spike",
                                providerId = "spike",
                                mediaId = "spike",
                                mediaType = PlaybackMediaType.Channel,
                                title = "TS Concat Spike",
                                streamUrl = bufferFile.toURI().toString(),
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
        recorder.stop()
        controller.release()
        super.onDestroy()
    }

    private companion object {
        const val USER_AGENT = "Vivicast/1.0"
        const val START_BYTES = 20_000_000L // ~15s of HD before playing, so a seek in the concat has room
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
