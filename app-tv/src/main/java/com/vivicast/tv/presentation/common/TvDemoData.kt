package com.vivicast.tv

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.vivicast.core.domain.M3uImportResult
import com.vivicast.core.domain.M3uVodImportResult
import com.vivicast.core.domain.XtreamVodImportResult
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgProgram
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.XtreamCredentials
import com.vivicast.core.model.XtreamOutputFormat as CoreXtreamOutputFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
val demoPlaylist = """
    #EXTM3U
    #EXTINF:-1 tvg-id="mux-demo" tvg-name="Mux Demo" group-title="Demo",Mux Demo
    https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
    #EXTINF:-1 tvg-id="apple-bipbop" tvg-name="Apple BipBop" group-title="Demo",Apple BipBop
    https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8
    #EXTINF:-1 tvg-id="bbb-hls" tvg-name="Big Buck Bunny" group-title="Demo",Big Buck Bunny
    https://test-streams.mux.dev/test_001/stream.m3u8
""".trimIndent()

val demoVodPlaylist = """
    #EXTM3U
    #EXTINF:-1 tvg-name="Big Buck Bunny" group-title="Movies",Big Buck Bunny
    https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4
    #EXTINF:-1 tvg-name="Elephant Dream" group-title="Movies",Elephant Dream
    https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4
    #EXTINF:-1 tvg-name="ViviCast Samples S01E01 Arrival" group-title="Series",ViviCast Samples S01E01 Arrival
    https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4
    #EXTINF:-1 tvg-name="ViviCast Samples S01E02 Signals" group-title="Series",ViviCast Samples S01E02 Signals
    https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4
    #EXTINF:-1 tvg-name="ViviCast Samples S02E01 Return" group-title="Series",ViviCast Samples S02E01 Return
    https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4
""".trimIndent()

const val HalfHourMillis = 30 * 60 * 1000L
const val GuideWindowMillis = 3 * 60 * 60 * 1000L

