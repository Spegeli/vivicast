package com.vivicast.tv.di

import android.content.Context
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.VivicastDatabaseFactory
import com.vivicast.tv.core.datastore.DataStoreUserPreferencesStore
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.core.security.AndroidKeystoreSecureValueStore
import com.vivicast.tv.core.security.SecureValueStore
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.RoomProviderRepository

class AppContainer(
    context: Context,
) {
    val database: VivicastDatabase by lazy {
        VivicastDatabaseFactory.create(context)
    }

    val userPreferencesStore: UserPreferencesStore by lazy {
        DataStoreUserPreferencesStore(context)
    }

    val secureValueStore: SecureValueStore by lazy {
        AndroidKeystoreSecureValueStore(context)
    }

    val providerRepository: ProviderRepository by lazy {
        RoomProviderRepository(
            database = database,
            secureValueStore = secureValueStore,
        )
    }
}
