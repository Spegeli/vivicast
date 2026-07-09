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
import com.vivicast.tv.core.player.DefaultVivicastPlayerController
import com.vivicast.tv.core.player.Media3PlaybackEngine
import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.VivicastPlayerController

/**
 * Debug-only spike (Phase 0 of the timeshift redesign). Plays a stream URL passed via intent extra and
 * exposes raw seek-back buttons so we can see how far the stream lets us rewind NATIVELY (its server DVR
 * window) — before building any local capture. Isolated from the real player. Debug source set only.
 *
 * Launch (no URL in the repo — pass it at runtime):
 *   adb shell am start -n com.vivicast.tv/.TimeshiftSpikeActivity --es url "<stream-url>"
 */
class TimeshiftSpikeActivity : ComponentActivity() {

    private lateinit var controller: VivicastPlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = DefaultVivicastPlayerController(Media3PlaybackEngine(applicationContext))
        val url = intent.getStringExtra("url").orEmpty()

        setContent {
            val state by controller.state.collectAsState()
            val label = TextStyle(color = Color.White, fontSize = 16.sp)
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { ctx -> SurfaceView(ctx).also { controller.attachVideoSurface(it) } },
                    modifier = Modifier.fillMaxSize(),
                )
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText("url set: ${url.isNotBlank()}", style = label)
                    BasicText(
                        "status=${state.status}  pos=${state.positionMillis / 1000}s  " +
                            "seekableWindow=${state.durationMillis / 1000}s  fps=${state.videoFrameRate}",
                        style = label,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpikeButton("-30s") { controller.seekBy(-30_000) }
                        SpikeButton("-5min") { controller.seekBy(-300_000) }
                        SpikeButton("+30s") { controller.seekBy(30_000) }
                        SpikeButton("Live") { controller.seekBy(600_000) }
                    }
                }
            }
            LaunchedEffect(url) {
                if (url.isNotBlank()) {
                    controller.play(
                        PlaybackRequest(
                            playbackId = "spike",
                            providerId = "spike",
                            mediaId = "spike",
                            mediaType = PlaybackMediaType.Channel,
                            title = "Spike",
                            streamUrl = url,
                            seekable = true,
                        ),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
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
