package com.vivicast.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vivicast.core.database.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAtEpochMillis DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name, updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :playlistId")
    suspend fun updatePlaylistName(
        playlistId: String,
        name: String,
        updatedAtEpochMillis: Long
    )

    @Query(
        """
        UPDATE playlists
        SET sourceUri = :sourceUri,
            sourceUsername = :sourceUsername,
            sourcePassword = :sourcePassword,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :playlistId
        """
    )
    suspend fun updatePlaylistConnection(
        playlistId: String,
        sourceUri: String?,
        sourceUsername: String?,
        sourcePassword: String?,
        updatedAtEpochMillis: Long
    )

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)
}
