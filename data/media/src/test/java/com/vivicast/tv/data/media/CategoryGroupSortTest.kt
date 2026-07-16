package com.vivicast.tv.data.media

import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.domain.model.CategorySortMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the non-trivial group sort logic — especially MANUAL (manual order first, unplaced last). */
class CategoryGroupSortTest {
    private fun cat(name: String, sortOrder: Int, manual: Int? = null) = CategoryEntity(
        id = name,
        providerId = "p",
        type = "LIVE",
        remoteId = name,
        name = name,
        sortOrder = sortOrder,
        isHidden = false,
        manualSortOrder = manual,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun playlist_keepsSourceOrder() {
        val input = listOf(cat("Zeta", 0), cat("Alpha", 1))
        assertEquals(
            listOf("Zeta", "Alpha"),
            input.sortedByGroupMode(CategorySortMode.Playlist).map { it.name },
        )
    }

    @Test
    fun name_sortsAlphabeticallyCaseInsensitive() {
        val input = listOf(cat("beta", 0), cat("Alpha", 1), cat("gamma", 2))
        assertEquals(
            listOf("Alpha", "beta", "gamma"),
            input.sortedByGroupMode(CategorySortMode.Name).map { it.name },
        )
    }

    @Test
    fun manual_placedFirstInManualOrderThenUnplacedBySourceOrder() {
        val input = listOf(
            cat("Sport", sortOrder = 5, manual = 1),
            cat("Music", sortOrder = 3, manual = null),
            cat("News", sortOrder = 4, manual = 0),
            cat("Docs", sortOrder = 2, manual = null),
        )
        assertEquals(
            // manual 0,1 first (News, Sport), then unplaced by source order (Docs=2, Music=3)
            listOf("News", "Sport", "Docs", "Music"),
            input.sortedByGroupMode(CategorySortMode.Manual).map { it.name },
        )
    }
}
