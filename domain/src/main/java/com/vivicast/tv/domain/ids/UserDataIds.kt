package com.vivicast.tv.domain.ids

import com.vivicast.tv.domain.model.MediaType
import java.security.MessageDigest

/**
 * Deterministic primary-key builders for user-data rows (favorites / playback progress / channel history).
 *
 * Shared by the live write path (`RoomFavoritesRepository`, `PlaybackProgressRecorder`) and the backup
 * post-import reconcile (`RoomCatalogImportRepository.reconcilePendingUserData`) so both produce
 * byte-identical keys. A divergent key would let the next live write insert a DUPLICATE row for the same
 * media (the tables carry a unique index on the media reference, but the PK differs).
 *
 * ID formats are kept byte-for-byte identical to the previous per-module definitions — existing DB /
 * resume keys depend on them, so DO NOT change the string shapes.
 */
object UserDataIds {
    /** `"$providerId:favorite:<segment>:<sha256-16(mediaId)>"` — mediaId is the catalog row id. */
    fun favoriteId(providerId: String, mediaType: MediaType, mediaId: String): String =
        "$providerId:favorite:${mediaType.segment}:${stableHash(mediaId)}"

    /** `"$providerId:progress:<segment>:<mediaId>"` — mediaId is the catalog row id (not hashed). */
    fun playbackProgressId(providerId: String, mediaType: MediaType, mediaId: String): String =
        "$providerId:progress:${mediaType.segment}:$mediaId"

    /** `"$providerId:history:channel:<channelId>"` — channelId is the catalog row id (not hashed). */
    fun channelHistoryId(providerId: String, channelId: String): String =
        "$providerId:history:channel:$channelId"

    /** SHA-256 of [value], first 16 bytes as lowercase hex. Matches the catalog stable-key hashing. */
    fun stableHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(HASH_BYTES).joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    // Segment == storageValue.lowercase() == name.lowercase() for every constant; spelled out so a future
    // MediaType addition forces a deliberate choice here instead of silently deriving a new segment.
    private val MediaType.segment: String
        get() = when (this) {
            MediaType.Channel -> "channel"
            MediaType.Movie -> "movie"
            MediaType.Series -> "series"
            MediaType.Episode -> "episode"
        }

    private const val HASH_BYTES = 16
}
