package com.vivicast.tv.data.provider

import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.iptv.m3u.M3uChannel
import com.vivicast.tv.iptv.m3u.M3uParser
import com.vivicast.tv.iptv.m3u.M3uPlaylist
import com.vivicast.tv.iptv.xtream.XtreamClient
import com.vivicast.tv.iptv.xtream.XtreamCredentials
import com.vivicast.tv.iptv.xtream.XtreamParser
import com.vivicast.tv.iptv.xtream.XtreamSeriesInfo
import com.vivicast.tv.iptv.xtream.XtreamUserInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TestProviderConnectionUseCaseTest {

    // --- M3U ---

    @Test
    fun m3u_urlMode_succeedsWithNonEmptyPlaylist() = runBlocking {
        val fetcher = FakeFetcher(result = "#EXTM3U")
        val useCase = useCase(m3u = FakeM3uParser(channels = 3), fetchText = fetcher)

        val summary = useCase.test(m3uRequest(url = "http://list.m3u")) // no throw = success

        assertEquals("http://list.m3u", fetcher.lastUrl)
        // Fake channels carry no movie/series markers, so all classify as live channels.
        assertEquals(3, summary?.channels)
    }

    @Test
    fun m3u_fileMode_usesInlineContentWithoutFetching() = runBlocking {
        val fetcher = FakeFetcher(result = "unused")
        val useCase = useCase(m3u = FakeM3uParser(channels = 1), fetchText = fetcher)

        useCase.test(m3uRequest(mode = M3uSourceMode.File, content = "#EXTM3U\n#EXTINF..."))

        assertEquals(null, fetcher.lastUrl) // file mode never fetches
    }

    @Test
    fun m3u_failsWhenPlaylistEmpty() = runBlocking {
        val useCase = useCase(m3u = FakeM3uParser(channels = 0), fetchText = FakeFetcher(result = "#EXTM3U"))

        assertThrows<ProviderConnectionResponseException> {
            useCase.test(m3uRequest(url = "http://list.m3u"))
        }
    }

    @Test
    fun m3u_failsWhenUrlMissing() = runBlocking {
        val useCase = useCase(m3u = FakeM3uParser(channels = 1), fetchText = FakeFetcher(result = ""))

        assertThrows<IllegalArgumentException> {
            useCase.test(m3uRequest(url = null))
        }
    }

    @Test
    fun m3u_propagatesFetchError() = runBlocking {
        val useCase = useCase(m3u = FakeM3uParser(channels = 1), fetchText = FakeFetcher(error = FakeHttpException()))

        assertThrows<FakeHttpException> {
            useCase.test(m3uRequest(url = "http://list.m3u"))
        }
    }

    // --- Xtream ---

    @Test
    fun xtream_succeedsWhenAuthenticated() = runBlocking {
        val client = FakeXtreamClient(response = "{}")
        val useCase = useCase(xtream = client, xtreamParser = FakeXtreamParser(authenticated = true))

        useCase.test(xtreamRequest()) // no throw = success

        assertEquals("userInfo", client.lastCalled) // canonical player_api.php handshake
    }

    @Test
    fun xtream_failsWhenNotAuthenticated() = runBlocking {
        val useCase = useCase(xtream = FakeXtreamClient(response = "{}"), xtreamParser = FakeXtreamParser(authenticated = false))

        assertThrows<ProviderInvalidCredentialsException> {
            useCase.test(xtreamRequest())
        }
    }

    @Test
    fun xtream_failsWhenCredentialsMissing() = runBlocking {
        val useCase = useCase(xtream = FakeXtreamClient(response = "[]"))

        assertThrows<IllegalArgumentException> {
            useCase.test(xtreamRequest(server = null))
        }
    }

    @Test
    fun xtream_failsWhenNoContentSelected() = runBlocking {
        val useCase = useCase(xtream = FakeXtreamClient(response = "[]"))

        assertThrows<IllegalArgumentException> {
            useCase.test(xtreamRequest(includeLiveTv = false, includeMovies = false, includeSeries = false))
        }
    }

    @Test
    fun xtream_propagatesHttpError() = runBlocking {
        val useCase = useCase(xtream = FakeXtreamClient(error = FakeHttpException()))

        assertThrows<FakeHttpException> {
            useCase.test(xtreamRequest())
        }
    }

    // --- helpers ---

    private fun useCase(
        m3u: M3uParser = FakeM3uParser(channels = 1),
        xtream: XtreamClient = FakeXtreamClient(response = "[]"),
        xtreamParser: XtreamParser = FakeXtreamParser(authenticated = true),
        fetchText: FakeFetcher = FakeFetcher(result = "#EXTM3U"),
    ) = TestProviderConnectionUseCase(m3u, xtream, xtreamParser, fetchText::fetch)

    private fun m3uRequest(
        mode: M3uSourceMode = M3uSourceMode.Url,
        url: String? = "http://list.m3u",
        content: String? = null,
    ) = ProviderCreateRequest(name = "P", type = ProviderType.M3u, m3uSourceMode = mode, m3uUrl = url, m3uContent = content)

    private fun xtreamRequest(
        server: String? = "http://server",
        includeLiveTv: Boolean = true,
        includeMovies: Boolean = true,
        includeSeries: Boolean = true,
    ) = ProviderCreateRequest(
        name = "P", type = ProviderType.Xtream,
        xtreamServerUrl = server, xtreamUsername = "u", xtreamPassword = "p",
        includeLiveTv = includeLiveTv, includeMovies = includeMovies, includeSeries = includeSeries,
    )

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName} to be thrown")
        } catch (t: Throwable) {
            assertTrue("Expected ${T::class.simpleName} but got ${t::class.simpleName}", t is T)
        }
    }
}

private class FakeHttpException : RuntimeException()

private class FakeFetcher(private val result: String = "", private val error: Throwable? = null) {
    var lastUrl: String? = null
        private set

    suspend fun fetch(url: String): String {
        lastUrl = url
        error?.let { throw it }
        return result
    }
}

private class FakeM3uParser(private val channels: Int) : M3uParser {
    override fun parse(content: String): M3uPlaylist =
        M3uPlaylist(channels = List(channels) { index -> channel("c$index") }, skippedEntries = 0)

    private fun channel(id: String) = M3uChannel(
        remoteId = id, name = id, streamUrl = "http://$id", categoryName = null, logoUrl = null,
        channelNumber = null, tvgId = null, tvgName = null, isCatchupAvailable = false,
        catchupDays = 0, catchupMode = null, catchupSource = null, rawAttributes = emptyMap(),
    )
}

private class FakeXtreamParser(private val authenticated: Boolean) : XtreamParser {
    override fun parseUserInfo(json: String) =
        XtreamUserInfo(authenticated = authenticated, expiresAtSeconds = null, maxConnections = null)
    override fun parseCategories(json: String) = emptyList<com.vivicast.tv.iptv.xtream.XtreamCategory>()
    override fun parseLiveStreams(json: String) = emptyList<com.vivicast.tv.iptv.xtream.XtreamLiveStream>()
    override fun parseVodItems(json: String) = emptyList<com.vivicast.tv.iptv.xtream.XtreamVodItem>()
    override fun parseSeries(json: String) = emptyList<com.vivicast.tv.iptv.xtream.XtreamSeriesItem>()
    override fun parseSeriesInfo(seriesRemoteId: String, json: String) =
        XtreamSeriesInfo(seriesRemoteId = seriesRemoteId, seasons = emptyList(), episodes = emptyList())
}

private class FakeXtreamClient(
    private val response: String = "[]",
    private val error: Throwable? = null,
) : XtreamClient {
    var lastCalled: String? = null
        private set

    override suspend fun getUserInfo(credentials: XtreamCredentials): String = respond("userInfo")
    override suspend fun getLiveCategories(credentials: XtreamCredentials): String = respond("live")
    override suspend fun getVodCategories(credentials: XtreamCredentials): String = respond("vod")
    override suspend fun getSeriesCategories(credentials: XtreamCredentials): String = respond("series")
    override suspend fun getLiveStreams(credentials: XtreamCredentials, categoryId: String?): String = respond("liveStreams")
    override suspend fun getVodStreams(credentials: XtreamCredentials, categoryId: String?): String = respond("vodStreams")
    override suspend fun getSeries(credentials: XtreamCredentials, categoryId: String?): String = respond("seriesList")
    override suspend fun getSeriesInfo(credentials: XtreamCredentials, seriesId: String): String = respond("seriesInfo")

    private fun respond(tag: String): String {
        lastCalled = tag
        error?.let { throw it }
        return response
    }
}
