package com.vivicast.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vivicast.tv.core.database.model.ProviderCategorySettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderCategorySettingsDao {
    // The panel observes the settings for the currently managed (provider, type). NULL until a setting is
    // first written (the repository treats NULL as the defaults: PLAYLIST + show new groups).
    @Query("SELECT * FROM provider_category_settings WHERE providerId = :providerId AND type = :type LIMIT 1")
    fun observeSettings(providerId: String, type: String): Flow<ProviderCategorySettingsEntity?>

    // One-shot read for the importer (new-groups hidden policy) and repository writes.
    @Query("SELECT * FROM provider_category_settings WHERE providerId = :providerId AND type = :type LIMIT 1")
    suspend fun getSettings(providerId: String, type: String): ProviderCategorySettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: ProviderCategorySettingsEntity)

    // Cleaned up with the catalog on provider deletion (no FK, matching the other entities).
    @Query("DELETE FROM provider_category_settings WHERE providerId = :providerId")
    suspend fun deleteSettingsForProvider(providerId: String)
}
