package com.vivicast.tv.core.cache

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileMediaCacheStoreTest {
    private val root = Files.createTempDirectory("vivicast-cache-test").toFile()
    private var now = 1_000L
    private val store = FileMediaCacheStore(rootDirectory = root) { now }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun putStoresBytesAndDoesNotPersistSourceUrl() = kotlinx.coroutines.runBlocking {
        val key = MediaCacheKey(
            type = MediaCacheType.ChannelLogo,
            ownerId = "channel-1",
            sourceUrl = "https://logos.example/ard.png?token=secret",
        )

        val entry = store.put(key, byteArrayOf(1, 2, 3))

        assertTrue(entry.file.isFile)
        assertTrue(store.hasEntry(key))
        assertEquals(MediaCacheStats(totalSizeBytes = 3L, fileCount = 1), store.stats())
        assertFalse(root.readTextRecursively().contains("token=secret"))
        assertFalse(root.readTextRecursively().contains("https://logos.example"))
    }

    @Test
    fun putReplacesOlderEntryForSameOwnerWhenSourceChanges() = kotlinx.coroutines.runBlocking {
        val oldKey = MediaCacheKey(MediaCacheType.ChannelLogo, ownerId = "channel-1", sourceUrl = "https://logos.example/old.png")
        val newKey = MediaCacheKey(MediaCacheType.ChannelLogo, ownerId = "channel-1", sourceUrl = "https://logos.example/new.png")

        store.put(oldKey, byteArrayOf(1, 2, 3))
        store.put(newKey, byteArrayOf(4, 5))

        assertFalse(store.hasEntry(oldKey))
        assertTrue(store.hasEntry(newKey))
        assertEquals(MediaCacheStats(totalSizeBytes = 2L, fileCount = 1), store.stats())
    }

    @Test
    fun cleanupRemovesLeastRecentlyAccessedEntriesUntilUnderLimit() = kotlinx.coroutines.runBlocking {
        val first = MediaCacheKey(MediaCacheType.ChannelLogo, ownerId = "channel-1", sourceUrl = "https://logos.example/one.png")
        val second = MediaCacheKey(MediaCacheType.ChannelLogo, ownerId = "channel-2", sourceUrl = "https://logos.example/two.png")
        now = 1_000L
        store.put(first, byteArrayOf(1, 2, 3))
        now = 2_000L
        store.put(second, byteArrayOf(4, 5, 6))
        now = 3_000L
        store.hasEntry(second)

        val result = store.cleanup(maxSizeBytes = 3L)

        assertEquals(1, result.removedFiles)
        assertEquals(3L, result.removedBytes)
        assertEquals(3L, result.remainingBytes)
        assertFalse(store.hasEntry(first))
        assertTrue(store.hasEntry(second))
    }

    @Test
    fun clearRemovesAllEntries() = kotlinx.coroutines.runBlocking {
        store.put(MediaCacheKey(MediaCacheType.ChannelLogo, "channel-1", "https://logos.example/one.png"), byteArrayOf(1))
        store.put(MediaCacheKey(MediaCacheType.MoviePoster, "movie-1", "https://posters.example/one.jpg"), byteArrayOf(2, 3))

        val result = store.clear()

        assertEquals(2, result.removedFiles)
        assertEquals(3L, result.removedBytes)
        assertEquals(MediaCacheStats(totalSizeBytes = 0L, fileCount = 0), store.stats())
    }
}

private fun File.readTextRecursively(): String =
    walkTopDown()
        .filter { it.isFile }
        .joinToString(separator = "\n") { file -> file.readText() }
