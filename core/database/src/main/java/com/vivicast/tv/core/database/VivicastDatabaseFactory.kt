package com.vivicast.tv.core.database

import android.content.Context
import androidx.room.Room

object VivicastDatabaseFactory {
    private const val DATABASE_NAME = "vivicast.db"

    fun create(context: Context): VivicastDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            VivicastDatabase::class.java,
            DATABASE_NAME,
        )
            .addCallback(VivicastDatabaseCallbacks.SearchFtsCallback)
            .addMigrations(
                VivicastMigrations.Migration1To2,
                VivicastMigrations.Migration2To3,
                VivicastMigrations.Migration3To4,
                VivicastMigrations.Migration4To5,
                VivicastMigrations.Migration5To6,
            )
            .build()
}
