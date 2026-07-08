package com.vivicast.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vivicast.tv.core.database.model.ProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY name COLLATE NOCASE")
    fun observeProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :providerId")
    suspend fun getProvider(providerId: String): ProviderEntity?

    @Query("SELECT * FROM providers")
    suspend fun getProviders(): List<ProviderEntity>

    @Upsert
    suspend fun upsertProvider(provider: ProviderEntity)

    @Upsert
    suspend fun upsertProviders(providers: List<ProviderEntity>)

    @Query("UPDATE providers SET status = :status, updatedAt = :updatedAt WHERE id = :providerId")
    suspend fun setProviderStatus(providerId: String, status: String, updatedAt: Long)

    // Refresh success: stamp status + updatedAt ("Updated") + lastRefreshAt (interval clock).
    @Query(
        "UPDATE providers SET status = :status, updatedAt = :refreshedAt, lastRefreshAt = :refreshedAt " +
            "WHERE id = :providerId",
    )
    suspend fun setProviderRefreshed(providerId: String, status: String, refreshedAt: Long)

    @Query("UPDATE providers SET status = :status WHERE id = :providerId")
    suspend fun setProviderStatusOnly(providerId: String, status: String)

    // Recovery for a refresh that was cancelled/killed mid-run and left the transient "REFRESHING"
    // status stuck (its finally can't persist a status during cancellation). Run once at startup.
    @Query("UPDATE providers SET status = 'ACTIVE' WHERE status = 'REFRESHING'")
    suspend fun clearStuckRefreshingStatus()

    @Query("UPDATE providers SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :providerId")
    suspend fun setProviderActive(providerId: String, isActive: Boolean, updatedAt: Long)

    @Query(
        "UPDATE providers SET xtreamExpiresAtMillis = :expiresAtMillis, " +
            "xtreamMaxConnections = :maxConnections WHERE id = :providerId",
    )
    suspend fun updateXtreamAccountInfo(
        providerId: String,
        expiresAtMillis: Long?,
        maxConnections: Int?,
    )

    @Query("DELETE FROM providers WHERE id = :providerId")
    suspend fun deleteProvider(providerId: String)
}
