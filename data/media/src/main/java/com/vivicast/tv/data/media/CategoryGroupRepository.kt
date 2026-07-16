package com.vivicast.tv.data.media

import androidx.room.withTransaction
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.CategoryEntity
import com.vivicast.tv.core.database.model.ProviderCategorySettingsEntity
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryGroupSettings
import com.vivicast.tv.domain.model.CategorySortMode
import com.vivicast.tv.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * Per-playlist channel-GROUP management (show/hide + sort mode + manual order + new-groups policy). Kept
 * separate from [MediaRepository] (the browse-read side) so neither class grows past the detekt gate and
 * the management-write concern stays distinct from the browse-read concern. See plans/d10-*.
 */
interface CategoryGroupRepository {
    /** All groups of a (provider, type) INCLUDING hidden ones (dimmed in the panel), ordered by the mode. */
    fun observeManagedGroups(providerId: String, type: CategoryType): Flow<List<Category>>

    fun observeGroupSettings(providerId: String, type: CategoryType): Flow<CategoryGroupSettings>

    suspend fun setSortMode(providerId: String, type: CategoryType, mode: CategorySortMode)

    suspend fun setHideNewGroups(providerId: String, type: CategoryType, hidden: Boolean)

    suspend fun setGroupHidden(categoryId: String, hidden: Boolean)

    suspend fun setAllGroupsHidden(providerId: String, type: CategoryType, hidden: Boolean)

    /** Persist a new manual order: writes manualSortOrder = index for each id in one transaction. */
    suspend fun reorderGroups(orderedCategoryIds: List<String>)

    /** Clears the manual order for a (provider, type) → MANUAL mode falls back to source (playlist) order. */
    suspend fun resetManualOrder(providerId: String, type: CategoryType)
}

class RoomCategoryGroupRepository(
    private val database: VivicastDatabase,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : CategoryGroupRepository {
    private val catalogDao = database.catalogDao()
    private val settingsDao = database.providerCategorySettingsDao()

    override fun observeManagedGroups(providerId: String, type: CategoryType): Flow<List<Category>> =
        combine(
            catalogDao.observeAllCategories(providerId, type.storageValue),
            settingsDao.observeSettings(providerId, type.storageValue),
        ) { categories, settings ->
            categories.sortedByGroupMode(settings.groupSortMode()).map { it.toDomain() }
        }

    override fun observeGroupSettings(providerId: String, type: CategoryType): Flow<CategoryGroupSettings> =
        settingsDao.observeSettings(providerId, type.storageValue).map { it.toGroupSettings() }

    override suspend fun setSortMode(providerId: String, type: CategoryType, mode: CategorySortMode) =
        upsertSettings(providerId, type) { it.copy(sortMode = mode.storageValue) }

    override suspend fun setHideNewGroups(providerId: String, type: CategoryType, hidden: Boolean) =
        upsertSettings(providerId, type) { it.copy(hideNewGroups = hidden) }

    override suspend fun setGroupHidden(categoryId: String, hidden: Boolean) =
        catalogDao.setCategoryHidden(categoryId, hidden, nowProvider())

    override suspend fun setAllGroupsHidden(providerId: String, type: CategoryType, hidden: Boolean) =
        catalogDao.setCategoriesHiddenForType(providerId, type.storageValue, hidden, nowProvider())

    override suspend fun reorderGroups(orderedCategoryIds: List<String>) {
        val now = nowProvider()
        database.withTransaction {
            orderedCategoryIds.forEachIndexed { index, id ->
                catalogDao.updateManualSortOrder(id, index, now)
            }
        }
    }

    override suspend fun resetManualOrder(providerId: String, type: CategoryType) =
        catalogDao.resetManualSortOrder(providerId, type.storageValue, nowProvider())

    // Read-modify-upsert the (provider, type) settings row (id is synthetic "$providerId:$type"), seeding
    // defaults when it doesn't exist yet.
    private suspend fun upsertSettings(
        providerId: String,
        type: CategoryType,
        transform: (ProviderCategorySettingsEntity) -> ProviderCategorySettingsEntity,
    ) {
        val now = nowProvider()
        val typeValue = type.storageValue
        val base = settingsDao.getSettings(providerId, typeValue) ?: ProviderCategorySettingsEntity(
            id = "$providerId:$typeValue",
            providerId = providerId,
            type = typeValue,
            createdAt = now,
            updatedAt = now,
        )
        settingsDao.upsertSettings(transform(base).copy(updatedAt = now))
    }
}

// Shared by both repositories (same package): apply the group sort mode to a source-ordered list.
internal fun List<CategoryEntity>.sortedByGroupMode(mode: CategorySortMode): List<CategoryEntity> =
    when (mode) {
        // PLAYLIST: the DAO already ordered by sortOrder (= source appearance order).
        CategorySortMode.Playlist -> this
        CategorySortMode.Name -> sortedBy { it.name.lowercase(Locale.ROOT) }
        // MANUAL: placed groups first in their manual order; unplaced (new) groups fall back to source order.
        CategorySortMode.Manual -> sortedWith(
            compareBy(nullsLast<Int>()) { c: CategoryEntity -> c.manualSortOrder }
                .thenBy { it.sortOrder }
                .thenBy { it.name.lowercase(Locale.ROOT) },
        )
    }

internal fun ProviderCategorySettingsEntity?.groupSortMode(): CategorySortMode =
    when (this?.sortMode) {
        "NAME" -> CategorySortMode.Name
        "MANUAL" -> CategorySortMode.Manual
        else -> CategorySortMode.Playlist
    }

internal fun ProviderCategorySettingsEntity?.toGroupSettings(): CategoryGroupSettings =
    CategoryGroupSettings(
        sortMode = groupSortMode(),
        hideNewGroups = this?.hideNewGroups ?: false,
    )

internal val CategorySortMode.storageValue: String
    get() = when (this) {
        CategorySortMode.Playlist -> "PLAYLIST"
        CategorySortMode.Name -> "NAME"
        CategorySortMode.Manual -> "MANUAL"
    }
