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
        ).build()
}

