package com.vivicast.tv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vivicast.tv.core.database.model.ChannelEntity
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.di.AppContainer
import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.worker.RefreshWorkerResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PublicEpgSmokeTest {
    private lateinit var context: Context
    private lateinit var appContainer: AppContainer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        appContainer = AppContainer(context)
    }

    @Test
    fun publicXmltvSourceImportsProgramsForLinkedProviderChannel() = runBlocking {
        val provider = appContainer.providerRepository.createProvider(
            ProviderCreateRequest(
                name = "Public EPG smoke",
                type = ProviderType.M3u,
                m3uUrl = "https://example.invalid/public-smoke.m3u",
                includeLiveTv = true,
                includeMovies = false,
                includeSeries = false,
            ),
        ).provider
        appContainer.database.catalogDao().upsertChannels(
            listOf(
                ChannelEntity(
                    id = CHANNEL_ID,
                    providerId = provider.id,
                    categoryId = null,
                    stableKey = CHANNEL_ID,
                    remoteId = PUBLIC_EPG_CHANNEL_ID,
                    channelNumber = null,
                    name = "ABC1",
                    logoUrl = null,
                    isCatchupAvailable = false,
                    catchupDays = 0,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val source = appContainer.epgSourceRepository.saveSource(
            EpgSourceEditRequest(
                name = "Public XMLTV smoke",
                url = PUBLIC_EPG_URL,
                timeShiftMinutes = 0,
                isActive = true,
            ),
        )
        appContainer.epgSourceRepository.linkSourceToProvider(provider.id, source.id, priority = 1)

        assertEquals(
            RefreshWorkerResult.Success,
            appContainer.refreshWorkerRunner.runEpgRefresh(source.id),
        )

        val programs = withTimeout(60_000) {
            appContainer.epgSourceRepository.observeProgramsForChannel(
                providerId = provider.id,
                channelId = CHANNEL_ID,
                fromMillis = 0L,
                toMillis = Long.MAX_VALUE,
            ).first { it.isNotEmpty() }
        }
        assertTrue(programs.any { it.title.isNotBlank() })
    }

    private companion object {
        const val DATABASE_NAME = "vivicast.db"
        const val CHANNEL_ID = "public-epg-smoke-bbc-news"
        const val PUBLIC_EPG_CHANNEL_ID = "BBCNews.uk"
        const val PUBLIC_EPG_URL = "https://raw.githubusercontent.com/dp247/Freeview-EPG/master/epg.xml"
    }
}
