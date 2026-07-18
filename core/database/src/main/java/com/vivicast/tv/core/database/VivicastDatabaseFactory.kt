package com.vivicast.tv.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.Executor

object VivicastDatabaseFactory {
    private const val DATABASE_NAME = "vivicast.db"

    // [queryCallback] runs on the query's own thread (direct executor), so it can detect a main-thread
    // query (the diagnostics slow-DB / freeze hook).
    fun create(
        context: Context,
        queryCallback: RoomDatabase.QueryCallback? = null,
    ): VivicastDatabase {
        val builder = Room.databaseBuilder(
            context.applicationContext,
            VivicastDatabase::class.java,
            DATABASE_NAME,
        )
            .addCallback(VivicastDatabaseCallbacks.SearchFtsCallback)
            // WAL guarantees readers see the last committed snapshot concurrently with a write, so a
            // long import/refresh transaction doesn't block the UI's read queries (the first-emit block
            // from Room's trigger sync is handled by warming the InvalidationTracker at startup).
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(
                VivicastMigrations.Migration1To2,
                VivicastMigrations.Migration2To3,
                VivicastMigrations.Migration3To4,
                VivicastMigrations.Migration4To5,
                VivicastMigrations.Migration5To6,
                VivicastMigrations.Migration6To7,
                VivicastMigrations.Migration7To8,
                VivicastMigrations.Migration8To9,
                VivicastMigrations.Migration9To10,
                VivicastMigrations.Migration10To11,
                VivicastMigrations.Migration11To12,
                VivicastMigrations.Migration12To13,
                VivicastMigrations.Migration13To14,
                VivicastMigrations.Migration14To15,
                VivicastMigrations.Migration15To16,
                VivicastMigrations.Migration16To17,
                VivicastMigrations.Migration17To18,
                VivicastMigrations.Migration18To19,
            )
        if (queryCallback != null) {
            builder.setQueryCallback(queryCallback, Executor { it.run() })
        }
        return builder.build()
    }
}
