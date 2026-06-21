package com.vivicast.tv.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [],
    version = 1,
    exportSchema = true,
)
abstract class VivicastDatabase : RoomDatabase()
