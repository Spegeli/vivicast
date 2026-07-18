package com.vivicast.tv.data.epg

import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.domain.model.EpgProgram
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.ProviderEpgSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoXtreamEpgSourceUseCaseTest {

    private fun url(host: String, user: String, pass: String) =
        "http://$host/xmltv.php?username=$user&password=$pass"

    @Test
    fun `creates and links a source named after the username when none exists`() = runBlocking {
        val repo = FakeRepo()

        val result = AutoXtreamEpgSourceUseCase(repo).ensureFor("p1", "alice", url("host", "alice", "pw"))

        assertEquals(1, repo.sources.size)
        val src = repo.sources.single()
        assertEquals("alice", src.name)
        assertEquals(url("host", "alice", "pw"), repo.urls[src.id])
        assertEquals(src.id, result?.epgSourceId)
        assertEquals(false, result?.reused)
        assertEquals(1, repo.links.single { it.providerId == "p1" }.priority)
    }

    @Test
    fun `reuses same server plus username on rotated password, updates url and keeps user settings`() = runBlocking {
        val repo = FakeRepo()
        repo.seed(id = "s0", name = "renamed by user", url = url("host", "alice", "OLD"), isActive = false, timeShift = 45)

        val result = AutoXtreamEpgSourceUseCase(repo).ensureFor("p1", "alice", url("host", "alice", "NEW"))

        assertEquals("no duplicate", 1, repo.sources.size)
        assertEquals("s0", result?.epgSourceId)
        assertEquals(true, result?.reused)
        val src = repo.sources.single()
        assertEquals("renamed by user", src.name)          // name preserved (user may have renamed)
        assertEquals(false, src.isActive)                  // isActive preserved
        assertEquals(45, src.timeShiftMinutes)             // timeShift preserved
        assertEquals(url("host", "alice", "NEW"), repo.urls["s0"])  // url refreshed to new password
        assertTrue(repo.links.any { it.providerId == "p1" && it.epgSourceId == "s0" })
    }

    @Test
    fun `different server with same username creates a new suffixed source`() = runBlocking {
        val repo = FakeRepo()
        repo.seed(id = "s0", name = "alice", url = url("other-host", "alice", "pw"))

        AutoXtreamEpgSourceUseCase(repo).ensureFor("p1", "alice", url("host", "alice", "pw"))

        assertEquals(2, repo.sources.size)
        assertTrue(repo.sources.any { it.name == "alice #1" })
    }

    @Test
    fun `server change unlinks the old-server source from this provider but keeps the source`() = runBlocking {
        val repo = FakeRepo()
        // Old-server auto-EPG source for the same account, already linked to p1.
        repo.seed(id = "old", name = "alice", url = url("old-host", "alice", "pw"))
        repo.linkSourceToProvider("p1", "old")

        val result = AutoXtreamEpgSourceUseCase(repo).ensureFor("p1", "alice", url("new-host", "alice", "pw"))

        assertEquals(false, result?.reused)
        // Old-server link removed from p1, new source linked instead.
        assertTrue(repo.unlinked.contains("p1" to "old"))
        assertTrue(repo.links.none { it.providerId == "p1" && it.epgSourceId == "old" })
        assertTrue(repo.links.any { it.providerId == "p1" && it.epgSourceId == result?.epgSourceId })
        // The source itself is NOT deleted (may serve other playlists).
        assertTrue(repo.sources.any { it.id == "old" })
    }

    @Test
    fun `blank inputs are a no-op`() = runBlocking {
        val repo = FakeRepo()
        assertNull(AutoXtreamEpgSourceUseCase(repo).ensureFor("", "alice", url("h", "alice", "p")))
        assertNull(AutoXtreamEpgSourceUseCase(repo).ensureFor("p1", "", url("h", "alice", "p")))
        assertNull(AutoXtreamEpgSourceUseCase(repo).ensureFor("p1", "alice", ""))
        assertEquals(0, repo.sources.size)
    }
}

private class FakeRepo : EpgSourceRepository {
    val sources = mutableListOf<EpgSource>()
    val urls = mutableMapOf<String, String>()
    val links = mutableListOf<ProviderEpgSource>()
    val unlinked = mutableListOf<Pair<String, String>>()
    private var seq = 0

    fun seed(id: String, name: String, url: String, isActive: Boolean = true, timeShift: Int = 0) {
        sources.add(source(id, name, isActive, timeShift))
        urls[id] = url
    }

    private fun source(id: String, name: String, isActive: Boolean, timeShift: Int) =
        EpgSource(
            id = id,
            stableKey = id,
            name = name,
            sourceConfigKey = "k:$id",
            timeShiftMinutes = timeShift,
            isActive = isActive,
            lastRefreshAt = null,
            lastProgramCount = 0,
            lastChannelCount = 0,
            isRefreshing = false,
        )

    override suspend fun getEpgSources(): List<EpgSource> = sources.toList()
    override suspend fun getSourceUrl(sourceId: String): String? = urls[sourceId]

    override suspend fun saveSource(request: EpgSourceEditRequest): EpgSource {
        val id = request.sourceId ?: "new${seq++}"
        require(sources.none { it.id != id && it.name.trim().equals(request.name.trim(), ignoreCase = true) }) {
            "EPG source name must be unique."
        }
        request.url?.takeIf { it.isNotBlank() }?.let { urls[id] = it }
        val saved = source(id, request.name, request.isActive, request.timeShiftMinutes)
        sources.removeAll { it.id == id }
        sources.add(saved)
        return saved
    }

    override suspend fun linkSourceToProvider(providerId: String, epgSourceId: String) {
        val priority = links.firstOrNull { it.providerId == providerId && it.epgSourceId == epgSourceId }?.priority
            ?: ((links.filter { it.providerId == providerId }.maxOfOrNull { it.priority } ?: 0) + 1)
        links.removeAll { it.providerId == providerId && it.epgSourceId == epgSourceId }
        links.add(
            ProviderEpgSource(
                id = "$providerId:$epgSourceId",
                providerId = providerId,
                epgSourceId = epgSourceId,
                priority = priority,
            ),
        )
    }

    override fun observeProviderEpgSources(providerId: String): Flow<List<ProviderEpgSource>> =
        flowOf(links.filter { it.providerId == providerId })

    override suspend fun unlinkSourceFromProvider(providerId: String, epgSourceId: String) {
        unlinked.add(providerId to epgSourceId)
        links.removeAll { it.providerId == providerId && it.epgSourceId == epgSourceId }
    }

    // Unused by the use case:
    override fun observeEpgSources(): Flow<List<EpgSource>> = flowOf(sources.toList())
    override suspend fun deleteSource(sourceId: String): Unit = error("unused")
    override suspend fun reorderProviderEpgSources(providerId: String, orderedSourceIds: List<String>): Unit = error("unused")
    override fun observeChannelsForProvider(providerId: String): Flow<List<Channel>> = error("unused")
    override fun observeProgramsForChannel(
        providerId: String,
        channelId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<EpgProgram>> = error("unused")
    override fun observeMappingsForChannel(providerId: String, channelId: String): Flow<List<EpgChannelMapping>> =
        error("unused")
    override suspend fun setManualChannelMapping(request: ManualEpgChannelMappingRequest): EpgChannelMapping =
        error("unused")
    override suspend fun clearManualChannelMapping(providerId: String, channelId: String, epgSourceId: String): Unit =
        error("unused")
}
