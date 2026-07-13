package com.vivicast.tv

import com.vivicast.tv.data.epg.smartMatchKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Matcher checks for [LocalLogoIndex]: fuzzy tv-logos-style matching (`zdf-info-de.png` ↔ tvg-id
 * `ZDFinfo.de@SD` ↔ name `ZDFinfo (720p)`), extension-priority dedup, recursive subfolders, miss → null.
 */
class LocalLogoIndexTest {

    private fun tempDir(): File =
        File.createTempFile("logos", "").let { it.delete(); it.mkdirs(); it }

    @Test
    fun smartKey_collapsesTvLogosNameTvgIdAndChannelName() {
        assertEquals("zdfinfo", smartMatchKey("zdf-info-de"))          // tv-logos filename
        assertEquals("zdfinfo", smartMatchKey("ZDFinfo.de@SD"))        // tvg-id with variant
        assertEquals("zdfinfo", smartMatchKey("ZDFinfo (720p) [Geo-blocked]")) // channel name
        assertEquals("zdfinfo", smartMatchKey("DE - ZDFinfo"))         // EPG display-name prefix
        // Distinct channels stay distinct (digits + plus preserved).
        assertEquals("sport1", smartMatchKey("Sport1"))
        assertEquals("sport1plus", smartMatchKey("Sport1+"))
        assertEquals("zdf2", smartMatchKey("ZDF2"))
    }

    @Test
    fun matchesTvLogosFiles_withExtensionPriorityAndRecursion() = runBlocking {
        val root = tempDir()
        try {
            File(root, "zdf-info-de.jpg").writeText("x")
            File(root, "zdf-info-de.png").writeText("x")        // same fuzzy key → png must win over jpg
            val country = File(root, "de").apply { mkdirs() }
            File(country, "das-erste-de.jpg").writeText("x")    // in a subfolder (recursion)
            File(root, "notanimage.txt").writeText("x")         // ignored extension

            val index = LocalLogoIndex()
            index.rebuild(root)

            // tvg-id tier, png preferred over jpg for the duplicate.
            assertEquals("zdf-info-de.png", index.lookup("ZDFinfo.de@SD", "ZDFinfo (720p)")?.name)
            // name tier (no tvg-id) after fuzzy normalization, found in a subfolder.
            assertEquals("das-erste-de.jpg", index.lookup(null, "Das Erste HD")?.name)
            assertNull(index.lookup("nope.de", "No Such Channel"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nullFolderClearsIndex() = runBlocking {
        val index = LocalLogoIndex()
        index.rebuild(null)
        assertNull(index.lookup("zdf.de", "ZDF"))
    }
}
