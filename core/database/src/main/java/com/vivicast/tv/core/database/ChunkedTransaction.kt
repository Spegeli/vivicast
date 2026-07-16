package com.vivicast.tv.core.database

import androidx.room.withTransaction
import kotlinx.coroutines.yield

/**
 * Commits each chunk in its own transaction, yielding between chunks so the single SQLite writer is
 * released and interactive writes (+ cooperative cancellation) can interleave. Used to stage bulk-import
 * rows without holding the writer for the whole import. See plans/nonblocking-db-imports.md.
 */
suspend fun <T> List<T>.forEachChunkedTransaction(
    db: VivicastDatabase,
    size: Int,
    block: suspend (List<T>) -> Unit,
) {
    require(size > 0) { "chunk size must be positive" }
    for (chunk in chunked(size)) {
        db.withTransaction { block(chunk) }
        yield()
    }
}
