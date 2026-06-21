package com.vivicast.tv.core.datastore

import kotlinx.coroutines.flow.Flow

interface UserPreferencesStore {
    val values: Flow<UserPreferences>
}

data class UserPreferences(
    val selectedProviderId: String? = null,
)
