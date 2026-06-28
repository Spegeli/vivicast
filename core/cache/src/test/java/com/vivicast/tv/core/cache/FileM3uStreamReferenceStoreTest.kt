package com.vivicast.tv.core.cache

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileM3uStreamReferenceStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun replaceProviderReferencesReadsLatestUrlsAndRemovesOldOnReplace() = runBlocking {
        val store = FileM3uStreamReferenceStore(temporaryFolder.newFolder("refs"))

        store.replaceProviderReferences(
            providerId = "provider-1",
            references = mapOf(
                "ard.de" to M3uStreamReference(
                    streamUrl = "https://streams.example/ard.m3u8",
                    catchupMode = "default",
                    catchupSource = "https://streams.example/archive?start={start}",
                ),
                "zdf.de" to M3uStreamReference(streamUrl = "https://streams.example/zdf.m3u8"),
            ),
        )
        store.replaceProviderReferences(
            providerId = "provider-1",
            references = mapOf("ard.de" to M3uStreamReference(streamUrl = "https://streams.example/ard-new.m3u8")),
        )

        assertEquals("https://streams.example/ard-new.m3u8", store.getStreamUrl("provider-1", "ard.de"))
        assertEquals("https://streams.example/ard-new.m3u8", store.getReference("provider-1", "ard.de")?.streamUrl)
        assertNull(store.getStreamUrl("provider-1", "zdf.de"))
    }

    @Test
    fun deleteProviderReferencesRemovesProviderFile() = runBlocking {
        val store = FileM3uStreamReferenceStore(temporaryFolder.newFolder("refs"))

        store.replaceProviderReferences(
            "provider-1",
            mapOf("ard.de" to M3uStreamReference(streamUrl = "https://streams.example/ard.m3u8")),
        )
        store.deleteProviderReferences("provider-1")

        assertNull(store.getStreamUrl("provider-1", "ard.de"))
    }
}
