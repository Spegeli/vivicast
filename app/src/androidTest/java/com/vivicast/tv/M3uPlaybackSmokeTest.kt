package com.vivicast.tv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.vivicast.tv.core.player.PlaybackMediaType
import com.vivicast.tv.core.player.PlaybackRequest
import com.vivicast.tv.core.player.PlaybackStatus
import com.vivicast.tv.data.playback.PlaybackStreamRequest
import com.vivicast.tv.data.playback.PlaybackStreamResult
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.di.AppContainer
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.worker.RefreshWorkerResult
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class M3uPlaybackSmokeTest {
    private lateinit var context: Context
    private lateinit var appContainer: AppContainer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        File(context.filesDir, M3U_STREAM_REFERENCE_DIR).deleteRecursively()
        appContainer = AppContainer(context)
    }

    @After
    fun tearDown() {
        runOnMainSync {
            appContainer.playerController.release()
        }
    }

    @Test
    fun publicM3uChannelResolvesAndStartsWithTimeshift() = runBlocking {
        val provider = appContainer.providerRepository.createProvider(
            ProviderCreateRequest(
                name = "Public M3U smoke",
                type = ProviderType.M3u,
                m3uUrl = PUBLIC_M3U_URL,
                includeLiveTv = true,
                includeMovies = false,
                includeSeries = false,
            ),
        ).provider

        assertEquals(
            RefreshWorkerResult.Success,
            appContainer.refreshWorkerRunner.runPlaylistRefresh(provider.id),
        )

        val channels = withTimeout(60_000) {
            appContainer.mediaRepository.observeChannels(provider.id, categoryId = null)
                .first { it.isNotEmpty() }
                .filter { it.remoteId.isNotBlank() }
                .take(SAMPLE_CHANNEL_COUNT)
        }
        assertTrue(channels.isNotEmpty())

        for (channel in channels) {
            val stream = appContainer.playbackStreamResolver.resolve(
                PlaybackStreamRequest(
                    providerId = channel.providerId,
                    mediaId = channel.id,
                    mediaType = MediaType.Channel,
                    remoteId = channel.remoteId,
                ),
            )
            if (stream !is PlaybackStreamResult.Resolved) {
                continue
            }

            val resolvedStream = stream.stream
            val playbackId = "m3u-smoke:${System.currentTimeMillis()}"
            runOnMainSync {
                appContainer.playerController.play(
                    PlaybackRequest(
                        playbackId = playbackId,
                        providerId = resolvedStream.providerId,
                        mediaId = resolvedStream.mediaId,
                        mediaType = PlaybackMediaType.Channel,
                        title = channel.name,
                        streamUrl = resolvedStream.url,
                        seekable = true,
                    ),
                )
            }

            val started = runCatching {
                withTimeout(PLAYBACK_START_TIMEOUT_MILLIS) {
                    appContainer.playerController.state.first {
                        it.request?.playbackId == playbackId && it.status == PlaybackStatus.Playing
                    }
                }
            }.getOrNull()

            if (started != null) {
                // A seekable public HLS channel exposes a native DVR window; depth is server-defined, not fixed.
                assertTrue(started.isTimeshiftEnabled)

                delay(PLAYBACK_STABILITY_MILLIS)
                if (appContainer.playerController.state.value.status != PlaybackStatus.Error) {
                    return@runBlocking
                }
            }
            runOnMainSync {
                appContainer.playerController.stop()
            }
        }

        fail("No sampled public M3U channel stayed playable for the smoke window.")
    }

    private companion object {
        const val DATABASE_NAME = "vivicast.db"
        const val M3U_STREAM_REFERENCE_DIR = "m3u-streams"
        const val PUBLIC_M3U_URL = "https://raw.githubusercontent.com/josxha/german-tv-m3u/main/german-tv.m3u"
        const val PLAYBACK_START_TIMEOUT_MILLIS = 20_000L
        const val PLAYBACK_STABILITY_MILLIS = 5_000L
        const val SAMPLE_CHANNEL_COUNT = 5

        fun runOnMainSync(action: () -> Unit) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
        }
    }
}
