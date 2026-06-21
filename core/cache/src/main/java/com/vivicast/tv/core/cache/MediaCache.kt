package com.vivicast.tv.core.cache

import java.io.File
import java.security.MessageDigest
import java.util.Properties

interface MediaCacheStore {
    suspend fun hasEntry(key: MediaCacheKey): Boolean

    suspend fun getEntry(key: MediaCacheKey): MediaCacheEntry?

    suspend fun put(key: MediaCacheKey, bytes: ByteArray): MediaCacheEntry

    suspend fun stats(): MediaCacheStats

    suspend fun cleanup(maxSizeBytes: Long): MediaCacheCleanupResult

    suspend fun clear(): MediaCacheCleanupResult
}

data class MediaCacheKey(
    val type: MediaCacheType,
    val ownerId: String,
    val sourceUrl: String,
) {
    init {
        require(ownerId.isNotBlank()) { "Cache owner ID must not be blank." }
        require(sourceUrl.isNotBlank()) { "Cache source URL must not be blank." }
    }

    val sourceHash: String = stableHash(sourceUrl.trim())
}

enum class MediaCacheType(val directoryName: String) {
    ChannelLogo("channel-logos"),
    MoviePoster("movie-posters"),
    MovieBackdrop("movie-backdrops"),
    SeriesPoster("series-posters"),
    SeriesBackdrop("series-backdrops"),
    SeasonImage("season-images"),
    EpisodeImage("episode-images"),
}

data class MediaCacheEntry(
    val key: MediaCacheKey,
    val file: File,
    val sizeBytes: Long,
    val createdAt: Long,
    val lastAccessedAt: Long,
)

data class MediaCacheStats(
    val totalSizeBytes: Long,
    val fileCount: Int,
    val maxSizeBytes: Long? = null,
)

data class MediaCacheCleanupResult(
    val removedFiles: Int,
    val removedBytes: Long,
    val remainingBytes: Long,
)

class FileMediaCacheStore(
    private val rootDirectory: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : MediaCacheStore {
    override suspend fun hasEntry(key: MediaCacheKey): Boolean =
        getEntry(key) != null

    override suspend fun getEntry(key: MediaCacheKey): MediaCacheEntry? {
        val dataFile = dataFileFor(key)
        val metaFile = metaFileFor(key)
        if (!dataFile.isFile || !metaFile.isFile) return null

        val metadata = metaFile.readMetadata()
        if (metadata?.type != key.type || metadata.ownerId != key.ownerId || metadata.sourceHash != key.sourceHash) {
            return null
        }

        val lastAccessedAt = clock()
        metadata.write(metaFile, lastAccessedAt = lastAccessedAt)
        return MediaCacheEntry(
            key = key,
            file = dataFile,
            sizeBytes = metadata.sizeBytes,
            createdAt = metadata.createdAt,
            lastAccessedAt = lastAccessedAt,
        )
    }

    override suspend fun put(key: MediaCacheKey, bytes: ByteArray): MediaCacheEntry {
        require(bytes.isNotEmpty()) { "Cache entry must not be empty." }

        rootDirectory.mkdirs()
        deleteEntriesForOwnerExcept(key)

        val now = clock()
        val directory = directoryFor(key.type).also { it.mkdirs() }
        val dataFile = dataFileFor(key)
        val temporaryFile = File(directory, "${dataFile.name}.tmp")
        temporaryFile.writeBytes(bytes)
        if (dataFile.exists()) dataFile.delete()
        require(temporaryFile.renameTo(dataFile)) { "Could not move cache entry into place." }

        val metadata = StoredMediaCacheEntry(
            type = key.type,
            ownerId = key.ownerId,
            sourceHash = key.sourceHash,
            sizeBytes = bytes.size.toLong(),
            createdAt = now,
            lastAccessedAt = now,
        )
        metadata.write(metaFileFor(key), lastAccessedAt = now)
        return MediaCacheEntry(
            key = key,
            file = dataFile,
            sizeBytes = bytes.size.toLong(),
            createdAt = now,
            lastAccessedAt = now,
        )
    }

    override suspend fun stats(): MediaCacheStats {
        val entries = readStoredEntries()
        return MediaCacheStats(
            totalSizeBytes = entries.sumOf { it.sizeBytes },
            fileCount = entries.size,
        )
    }

    override suspend fun cleanup(maxSizeBytes: Long): MediaCacheCleanupResult {
        if (maxSizeBytes < 0L) {
            return MediaCacheCleanupResult(
                removedFiles = 0,
                removedBytes = 0L,
                remainingBytes = stats().totalSizeBytes,
            )
        }

        val entries = readStoredEntries().sortedWith(compareBy<StoredMediaCacheEntry> { it.lastAccessedAt }.thenBy { it.createdAt })
        var totalBytes = entries.sumOf { it.sizeBytes }
        var removedFiles = 0
        var removedBytes = 0L

        for (entry in entries) {
            if (totalBytes <= maxSizeBytes) break
            val removed = deleteEntry(entry)
            if (removed > 0L) {
                totalBytes -= removed
                removedFiles += 1
                removedBytes += removed
            }
        }

        return MediaCacheCleanupResult(
            removedFiles = removedFiles,
            removedBytes = removedBytes,
            remainingBytes = totalBytes.coerceAtLeast(0L),
        )
    }

    override suspend fun clear(): MediaCacheCleanupResult {
        val entries = readStoredEntries()
        var removedFiles = 0
        var removedBytes = 0L
        entries.forEach { entry ->
            val removed = deleteEntry(entry)
            if (removed > 0L) {
                removedFiles += 1
                removedBytes += removed
            }
        }
        return MediaCacheCleanupResult(
            removedFiles = removedFiles,
            removedBytes = removedBytes,
            remainingBytes = stats().totalSizeBytes,
        )
    }

    private fun deleteEntriesForOwnerExcept(key: MediaCacheKey) {
        readStoredEntries()
            .filter { it.type == key.type && it.ownerId == key.ownerId && it.sourceHash != key.sourceHash }
            .forEach(::deleteEntry)
    }

    private fun readStoredEntries(): List<StoredMediaCacheEntry> =
        MediaCacheType.entries.flatMap { type ->
            directoryFor(type)
                .listFiles { file -> file.extension == META_EXTENSION }
                ?.mapNotNull { it.readMetadata() }
                .orEmpty()
        }.filter { entry -> entry.dataFile(rootDirectory).isFile }

    private fun deleteEntry(entry: StoredMediaCacheEntry): Long {
        val dataFile = entry.dataFile(rootDirectory)
        val metaFile = entry.metaFile(rootDirectory)
        val size = dataFile.takeIf { it.exists() }?.length() ?: entry.sizeBytes
        val dataDeleted = !dataFile.exists() || dataFile.delete()
        val metaDeleted = !metaFile.exists() || metaFile.delete()
        return if (dataDeleted && metaDeleted) size else 0L
    }

    private fun directoryFor(type: MediaCacheType): File =
        File(rootDirectory, type.directoryName)

    private fun dataFileFor(key: MediaCacheKey): File =
        File(directoryFor(key.type), "${stableHash("${key.ownerId}:${key.sourceHash}")}.$DATA_EXTENSION")

    private fun metaFileFor(key: MediaCacheKey): File =
        File(directoryFor(key.type), "${stableHash("${key.ownerId}:${key.sourceHash}")}.$META_EXTENSION")
}

private data class StoredMediaCacheEntry(
    val type: MediaCacheType,
    val ownerId: String,
    val sourceHash: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val lastAccessedAt: Long,
) {
    fun dataFile(rootDirectory: File): File =
        File(File(rootDirectory, type.directoryName), "${stableHash("$ownerId:$sourceHash")}.$DATA_EXTENSION")

    fun metaFile(rootDirectory: File): File =
        File(File(rootDirectory, type.directoryName), "${stableHash("$ownerId:$sourceHash")}.$META_EXTENSION")

    fun write(file: File, lastAccessedAt: Long) {
        file.parentFile?.mkdirs()
        val properties = Properties().apply {
            setProperty("type", type.name)
            setProperty("ownerId", ownerId)
            setProperty("sourceHash", sourceHash)
            setProperty("sizeBytes", sizeBytes.toString())
            setProperty("createdAt", createdAt.toString())
            setProperty("lastAccessedAt", lastAccessedAt.toString())
        }
        file.outputStream().use { output -> properties.store(output, null) }
    }
}

private fun File.readMetadata(): StoredMediaCacheEntry? =
    runCatching {
        val properties = Properties()
        inputStream().use(properties::load)
        StoredMediaCacheEntry(
            type = MediaCacheType.valueOf(properties.getProperty("type")),
            ownerId = properties.getProperty("ownerId"),
            sourceHash = properties.getProperty("sourceHash"),
            sizeBytes = properties.getProperty("sizeBytes").toLong(),
            createdAt = properties.getProperty("createdAt").toLong(),
            lastAccessedAt = properties.getProperty("lastAccessedAt").toLong(),
        )
    }.getOrNull()

private const val DATA_EXTENSION = "bin"
private const val META_EXTENSION = "properties"

private fun stableHash(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.take(16).joinToString(separator = "") { byte -> "%02x".format(byte) }
}
