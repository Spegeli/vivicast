package com.vivicast.tv.di

import android.content.Context
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.VivicastDatabaseFactory
import com.vivicast.tv.core.datastore.DataStoreUserPreferencesStore
import com.vivicast.tv.core.datastore.UserPreferencesStore

class AppContainer(
    context: Context,
) {
    val database: VivicastDatabase by lazy {
        VivicastDatabaseFactory.create(context)
    }

    val userPreferencesStore: UserPreferencesStore by lazy {
        DataStoreUserPreferencesStore(context)
    }
}
