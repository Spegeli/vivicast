package com.vivicast.tv.data.playback

import com.vivicast.tv.data.provider.DEFAULT_REFRESH_INTERVAL_HOURS
import com.vivicast.tv.core.cache.M3uStreamReference
import com.vivicast.tv.core.cache.M3uStreamReferenceStore
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderSaveResult
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.domain.model.MediaType
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DefaultPlaybackStreamResolverTest {
    @Test
    fun resolvesXtreamLiveChannelUrlFromCredentialsAndRemoteId() = runBlocking {
        val resolver = DefaultPlaybackStreamResolver(
            providerRepository = FakeProviderRepository(
                provider = provider(type = ProviderType.Xtream),
                credentials = ProviderCredentials.Xtream(
                    serverUrl = "https://provider.example/",
                    username = "demo user",
                    password = "demo/pass",
                ),
            ),
        )

        val result = resolver.resolve(
            PlaybackStreamRequest(
                providerId = PROVIDER_ID,
                mediaId = "channel-local-id",
                mediaType = MediaType.Channel,
                remoteId = "100",
            ),
        )

        val stream = (result as PlaybackStreamResult.Resolved).stream
        assertEquals("https://provider.example/live/demo%20user/demo%2Fpass/100.ts", stream.url)
        assertEquals("channel-local-id", stream.mediaId)
        assertEquals(PROVIDER_STABLE_KEY, stream.providerStableKey)
    }

    @Test
    fun resolvesXtreamMovieUrlWithContainerExtension() = runBlocking {
        val resolver = xtreamResolver()

        val result = resolver.resolve(
            PlaybackStreamRequest(
                providerId = PROVIDER_ID,
                mediaId = "movie-local-id",
                mediaType = MediaType.Movie,
                remoteId = "200",
                containerExtension = ".mkv",
            ),
        )

        val stream = (result as PlaybackStreamResult.Resolved).stream
        assertEquals("https://provider.example/movie/demo/secret/200.mkv", stream.url)
    }

    @Test
    fun resolvesXtreamEpisodeUrlWithContainerExtension() = runBlocking {
        val resolver = xtreamResolver()

        val result = resolver.resolve(
            PlaybackStreamRequest(
                providerId = PROVIDER_ID,
                mediaId = "episode-local-id",
                mediaType = MediaType.Episode,
                remoteId = "episode-1",
                containerExtension = "mp4",
            ),
        )

        val stream = (result as PlaybackStreamResult.Resolved).stream
        assertEquals("https://provider.example/series/demo/secret/episode-1.mp4", stream.url)
    }

    @Test
    fun resolvesXtreamCatchupUrlFromEpgWindow() = runBlocking {
        val resolver = xtreamResolver()

        val result = resolver.resolve(
            PlaybackStreamRequest(
                providerId = PROVIDER_ID,
                mediaId = "channel-local-id",
                mediaType = MediaType.Channel,
                remoteId = "100",
                catchupStartMillis = Instant.parse("2026-01-02T03:04:00Z").toEpochMilli(),
                catchupEndMillis = Instant.parse("2026-01-02T03:34:00Z").toEpochMilli(),
            ),
        )

        val stream = (result as PlaybackStreamResult.Resolved).stream
        assertEquals("https://provider.example/timeshift/demo/secret/30/2026-01-02%3A03-04/100.ts", stream.url)
    }

    @Test
    fun rejectsXtreamCatchupWithoutValidEpgWindow() = runBlocking {
        val resolver = xtreamResolver()

        val result = resolver.resolve(
            PlaybackStreamRequest(
                providerId = PROVIDER_ID,
                mediaId = "channel-local-id",
                mediaType = MediaType.Channel,
                remoteId = "100",
                catchupStartMillis = Instant.parse("2026-01-02T03:34:00Z").toEpochMilli(),
                catchupEndMillis = Instant.parse("2026-01-02T03:04:00Z").toEpochMilli(),
            ),
        )

        assertEquals(
            PlaybackStreamFailureReason.InvalidCatchupWindow,
            (result as PlaybackStreamResult.Failed).reason,
        )
    }

    @Test
    fun resolvesM3uChannelUrlFromExternalStreamReferenceStore() = runBlocking {
        val streamReferenceStore = FakeM3uStreamReferenceStore().apply {
            replaceProviderReferences(
                PROVIDER_ID,
                mapOf("ard.de" to M3uStreamReference(streamUrl = "https://streams.example/ard.m3u8")),
            )
        }
        val resolver = DefaultPlaybackStreamResolver(
            providerRepository = FakeProviderRepository(
                provider = provider(type = ProviderType.M3u),
                credentials = ProviderCredentials.M3u(url = "https://playlist.example/list.m3u"),
            ),
            m3uStreamReferenceStore = streamReferenceStore,
        )

        val result = resolver.resolve(
            PlaybackStreamRequest(
                providerId = PROVIDER_ID,
                mediaId = "channel-local-id",
                mediaType = MediaType.Channel,
                remoteId = "ard.de",
            ),
        )

        val stream = (result as PlaybackStreamResult.Resolved).stream
        assertEquals("https://streams.example/ard.m3u8", stream.url)
        assertEquals("channel-local-id", stream.mediaId)
        assertEquals(PROVIDER_STABLE_KEY, stream.providerStableKey)
    }

    @Test
    fun resolvesM3uCatchupFromTemplate() = runBlocking {
        val streamReferenceStore = FakeM3uStreamReferenceStore().apply {
            replaceProviderReferences(
                PROVIDER_ID,
                mapOf(
                    "ard.de" to M3uStreamReference(
                        streamUrl = "https://streams.example/ard.m3u8",
                        catchupMode = "default",
                        catchupSource = "https://archive.example/ard?start={start}&end={end}&duration={duration}",
                    ),
                ),
            )
        }
        val resolver = DefaultPlaybackStreamResolver(
            providerRepository = FakeProviderRepository(
                provider = provider(type = ProviderType.M3u),
                credentials = ProviderCredentials.M3u(url = "https://playlist.example/list.m3u"),
            ),
            m3uStreamReferenceStore = streamReferenceStore,
        )

        val result = resolver.resolve(
            PlaybackStreamRequest(
                providerId = PROVIDER_ID,
                mediaId = "channel-local-id",
                mediaType = MediaType.Channel,
                remoteId = "ard.de",
                catchupStartMillis = Instant.parse("2026-01-02T03:04:00Z").toEpochMilli(),
                catchupEndMillis = Instant.parse("2026-01-02T03:34:00Z").toEpochMilli(),
            ),
        )

        val stream = (result as PlaybackStreamResult.Resolved).stream
        assertEquals(
            "https://archive.example/ard?start=1767323040&end=1767324840&duration=1800",
            stream.url,
        )
    }

    @Test
    fun resolvesM3uAppendCatchupFromTemplate() = runBlocking {
        val streamReferenceStore = FakeM3uStreamReferenceStore().apply {
            replaceProviderReferences(
                PROVIDER_ID,
                mapOf(
                    "ard.de" to M3uStreamReference(
                        streamUrl = "https://streams.example/ard.m3u8",
                        catchupMode = "append",
                        catchupSource = "?utc=\${start}&lutc=\${timestamp}&dur=\${duration_minutes}",
                    ),
                ),
            )
        }
        val resolver = DefaultPlaybackStreamResolver(
            providerRepository = FakeProviderRepository(
                provider = provider(type = ProviderType.M3u),
                credentials = ProviderCredentials.M3u(url = "https://playlist.example/list.m3u"),
            ),
            m3uStreamReferenceStore = streamReferenceStore,
        )

        val result = resolver.resolve(
            PlaybackStreamRequest(
                providerId = PROVIDER_ID,
                mediaId = "channel-local-id",
                mediaType = MediaType.Channel,
                remoteId = "ard.de",
                catchupStartMillis = Instant.parse("2026-01-02T03:04:00Z").toEpochMilli(),
                catchupEndMillis = Instant.parse("2026-01-02T03:34:00Z").toEpochMilli(),
            ),
        )

        val stream = (result as PlaybackStreamResult.Resolved).stream
        assertEquals(
            "https://streams.example/ard.m3u8?utc=1767323040&lutc=1767324840&dur=30",
            stream.url,
        )
    }

    @Test
    fun rejectsM3uWhenExternalStreamReferenceIsMissing() = runBlocking {
        val resolver = DefaultPlaybackStreamResolver(
            providerRepository = FakeProviderRepository(
                provider = provider(type = ProviderType.M3u),
                credentials = ProviderCredentials.M3u(url = "https://playlist.example/list.m3u"),
            ),
            m3uStreamReferenceStore = FakeM3uStreamReferenceStore(),
        )

        val result = resolver.resolve(
            PlaybackStreamRequest(
                providerId = PROVIDER_ID,
                mediaId = "channel-local-id",
                mediaType = MediaType.Channel,
                remoteId = "ard.de",
            ),
        )

        assertEquals(PlaybackStreamFailureReason.MissingStreamReference, (result as PlaybackStreamResult.Failed).reason)
    }

    @Test
    fun rejectsInactiveProviderBeforeReadingCredentials() = runBlocking {
        val repository = FakeProviderRepository(
            provider = provider(type = ProviderType.Xtream, isActive = false),
            credentials = ProviderCredentials.Xtream(
                serverUrl = "https://provider.example",
                username = "demo",
                password = "secret",
            ),
        )
        val resolver = DefaultPlaybackStreamResolver(providerRepository = repository)

        val result = resolver.resolve(
            PlaybackStreamRequest(
                providerId = PROVIDER_ID,
                mediaId = "channel-local-id",
                mediaType = MediaType.Channel,
                remoteId = "100",
            ),
        )

        assertEquals(PlaybackStreamFailureReason.ProviderInactive, (result as PlaybackStreamResult.Failed).reason)
        assertTrue(repository.credentialsReadCount == 0)
    }

    @Test
    fun rejectsVodWithoutContainerExtension() = runBlocking {
        val resolver = xtreamResolver()

        val result = resolver.resolve(
            PlaybackStreamRequest(
                providerId = PROVIDER_ID,
                mediaId = "movie-local-id",
                mediaType = MediaType.Movie,
                remoteId = "200",
            ),
        )

        assertEquals(
            PlaybackStreamFailureReason.MissingContainerExtension,
            (result as PlaybackStreamResult.Failed).reason,
        )
    }

    private fun xtreamResolver(): DefaultPlaybackStreamResolver =
        DefaultPlaybackStreamResolver(
            providerRepository = FakeProviderRepository(
                provider = provider(type = ProviderType.Xtream),
                credentials = ProviderCredentials.Xtream(
                    serverUrl = "https://provider.example",
                    username = "demo",
                    password = "secret",
                ),
            ),
        )

    private class FakeProviderRepository(
        private val provider: Provider?,
        private val credentials: ProviderCredentials?,
    ) : ProviderRepository {
        var credentialsReadCount = 0

        override fun observeProviders(): Flow<List<Provider>> = emptyFlow()

        override suspend fun getProvider(providerId: String): Provider? = provider?.takeIf { it.id == providerId }

        override suspend fun getCredentials(providerId: String): ProviderCredentials? {
            credentialsReadCount += 1
            return credentials
        }

        override suspend fun createProvider(request: ProviderCreateRequest): ProviderSaveResult =
            error("Not needed.")

        override suspend fun updateProvider(request: ProviderUpdateRequest): ProviderSaveResult =
            error("Not needed.")

        override suspend fun saveProvider(provider: Provider) = Unit

        override suspend fun setProviderStatus(providerId: String, status: ProviderStatus) = Unit

        override suspend fun setProviderActive(providerId: String, isActive: Boolean) = Unit

        override suspend fun setProviderEnabled(providerId: String, isEnabled: Boolean) = Unit

        override suspend fun deleteProvider(providerId: String) = Unit
    }

    private class FakeM3uStreamReferenceStore : M3uStreamReferenceStore {
        private val references = mutableMapOf<String, MutableMap<String, M3uStreamReference>>()

        override suspend fun replaceProviderReferences(providerId: String, references: Map<String, M3uStreamReference>) {
            this.references[providerId] = references.toMutableMap()
        }

        override suspend fun getReference(providerId: String, remoteId: String): M3uStreamReference? =
            references[providerId]?.get(remoteId)

        override suspend fun deleteProviderReferences(providerId: String) {
            references.remove(providerId)
        }
    }

    private companion object {
        const val PROVIDER_ID = "provider-1"
        const val PROVIDER_STABLE_KEY = "provider-stable-1"

        fun provider(
            type: ProviderType,
            isActive: Boolean = true,
            status: ProviderStatus = ProviderStatus.Active,
        ): Provider =
            Provider(
                id = PROVIDER_ID,
                name = "Provider",
                type = type,
                sourceConfigKey = "provider-1-credentials",
                isActive = isActive,
                status = status,
                includeLiveTv = true,
                includeMovies = true,
                includeSeries = true,
                refreshIntervalHours = DEFAULT_REFRESH_INTERVAL_HOURS,
                logoPriority = "provider",
                createdAt = 1L,
                updatedAt = 1L,
                stableKey = PROVIDER_STABLE_KEY,
            )
    }
}
