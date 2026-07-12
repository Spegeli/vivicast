package com.vivicast.tv.backup

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vivicast.tv.core.database.VivicastDatabase
import com.vivicast.tv.core.database.model.FavoriteEntity
import com.vivicast.tv.core.database.model.EpgSourceEntity
import com.vivicast.tv.core.database.model.PlaybackProgressEntity
import com.vivicast.tv.core.database.model.ProviderEntity
import com.vivicast.tv.core.database.model.SearchHistoryEntity
import com.vivicast.tv.core.datastore.AppearancePreferences
import com.vivicast.tv.core.datastore.BackupPreferences
import com.vivicast.tv.core.datastore.BackupTargetPreference
import com.vivicast.tv.core.datastore.BufferSizePreference
import com.vivicast.tv.core.datastore.DiagnosticsPreferences
import com.vivicast.tv.core.datastore.EpgPreferences
import com.vivicast.tv.core.datastore.FontScalePreference
import com.vivicast.tv.core.datastore.GeneralPreferences
import com.vivicast.tv.core.datastore.HistoryPreferences
import com.vivicast.tv.core.datastore.ParentalControlPreferences
import com.vivicast.tv.core.datastore.PlaybackPreferences
import com.vivicast.tv.core.datastore.ThemeColor
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.core.security.PinSecurity
import com.vivicast.tv.core.security.PinSecurityState
import com.vivicast.tv.core.security.PinSecurityStateStore
import com.vivicast.tv.core.security.SecureKey
import com.vivicast.tv.core.security.SecureValueStore
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class StandardBackupTest {
    @Test
    fun exportsSafeProviderSourcesOnly() {
        assertEquals(
            "https://example.org/list.m3u",
            standardBackupM3uUrlOrNull(" https://example.org/list.m3u "),
        )
        assertNull(standardBackupM3uUrlOrNull("https://user:pass@example.org/list.m3u"))
        assertNull(standardBackupM3uUrlOrNull("https://example.org/list.m3u?token=secret"))
        assertNull(standardBackupM3uUrlOrNull("https://example.org/token/secret/list.m3u"))

        assertEquals(
            "https://example.org:8443",
            standardBackupXtreamServerUrlOrNull("https://example.org:8443/live"),
        )
        assertNull(standardBackupXtreamServerUrlOrNull("https://example.org/player_api.php?username=u&password=p"))
    }

    @Test
    fun mapsProviderWithoutSourceConfigKeyOrUnsafeCredentials() {
        val safeProvider = provider(ProviderType.M3u)
            .toStandardBackupProvider(ProviderCredentials.M3u("https://example.org/public.m3u"))
        val unsafeProvider = provider(ProviderType.M3u)
            .toStandardBackupProvider(ProviderCredentials.M3u("https://example.org/public.m3u?token=secret"))

        assertEquals("provider-stable", safeProvider.stableKey)
        assertEquals("https://example.org/public.m3u", safeProvider.source?.m3uUrl)
        assertNull(unsafeProvider.source)
    }

    @Test
    fun standardJsonContainsOnlyParentalSummary() {
        val json = JSONObject(
            StandardBackupDocument(
                exportedAtMillis = 123L,
                security = StandardBackupSecuritySummary(parentalProtectionWasActive = true),
            ).toJsonString(indentSpaces = 0),
        )

        assertEquals(STANDARD_BACKUP_SCHEMA_VERSION, json.getInt("schemaVersion"))
        assertEquals("STANDARD", json.getString("exportMode"))
        val security = json.getJSONObject("security")
        assertTrue(security.getBoolean("parentalProtectionWasActive"))
        assertFalse(security.has("pin"))
        assertFalse(security.has("checkValue"))
        assertFalse(security.has("failedAttempts"))
        assertFalse(security.has("lockoutCount"))
        assertFalse(json.getJSONObject("preferences").has("cache"))
        val backup = json.getJSONObject("preferences").getJSONObject("backup")
        assertEquals(BackupTargetPreference.LocalStorage.name, backup.getString("targetType"))
        assertFalse(backup.has("lastBackupAtMillis"))

        val text = json.toString()
        assertFalse(text.contains("protectSettings", ignoreCase = true))
        assertFalse(text.contains("protectMovies", ignoreCase = true))
        assertFalse(text.contains("protectSeries", ignoreCase = true))
        assertFalse(text.contains("protectAdultContent", ignoreCase = true))
        assertFalse(text.contains("sourceConfigKey", ignoreCase = true))
        assertFalse(text.contains("password", ignoreCase = true))
    }

    @Test
    fun exporterBuildsStandardSnapshotWithoutSecrets() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java).build()
        val secureStore = FakeSecureValueStore()
        try {
            database.providerDao().upsertProvider(providerEntity())
            database.favoritesDao().upsertFavorite(favoriteEntity())
            database.playbackDao().upsertProgress(playbackProgressEntity())
            database.searchDao().upsertSearchHistory(searchHistoryEntity())
            secureStore.write(SecureKey("provider:one:credentials:m3u_url"), "https://example.org/public.m3u")

            val document = StandardBackupExporter(
                database = database,
                userPreferencesStore = FakePreferencesStore(UserPreferences()),
                secureValueStore = secureStore,
                pinSecurityStateStore = FakePinSecurityStateStore(PinSecurity.setPin("1234")),
                clock = { 123L },
            ).buildDocument()
            val json = document.toJson()

            assertEquals(123L, json.getLong("exportedAtMillis"))
            assertEquals("provider-stable", json.getJSONArray("providers").getJSONObject(0).getString("stableKey"))
            assertEquals(
                "https://example.org/public.m3u",
                json.getJSONArray("providers").getJSONObject(0).getJSONObject("source").getString("m3uUrl"),
            )
            assertEquals("movie-stable", json.getJSONArray("favorites").getJSONObject(0).getString("mediaStableKey"))
            assertEquals(
                "movie-stable",
                json.getJSONArray("playbackProgress").getJSONObject(0).getString("mediaStableKey"),
            )
            assertEquals("dune", json.getJSONArray("searchHistory").getString(0))
            assertTrue(json.getJSONObject("security").getBoolean("parentalProtectionWasActive"))
            assertFalse(json.toString().contains("sourceConfigKey", ignoreCase = true))
            assertFalse(json.toString().contains("1234", ignoreCase = true))
        } finally {
            database.close()
        }
    }

    @Test
    fun fullBackupPayloadIncludesSourceSecretsButNotPinState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java).build()
        val secureStore = FakeSecureValueStore()
        try {
            database.providerDao().upsertProvider(
                providerEntity().copy(
                    id = "provider-xtream",
                    stableKey = "xtream-stable",
                    type = "XTREAM",
                    sourceConfigKey = "provider:xtream:credentials",
                ),
            )
            database.epgDao().upsertEpgSources(listOf(epgSourceEntity()))
            secureStore.write(
                SecureKey("provider:xtream:credentials:xtream_server_url"),
                "https://xtream.example.org/player_api.php?username=fixture-user&password=fixture-password",
            )
            secureStore.write(SecureKey("provider:xtream:credentials:xtream_username"), "fixture-user")
            secureStore.write(SecureKey("provider:xtream:credentials:xtream_password"), "fixture-password")
            secureStore.write(SecureKey("epg:one:url"), "https://example.org/epg.xml?token=fixture-token")

            val exporter = StandardBackupExporter(
                database = database,
                userPreferencesStore = FakePreferencesStore(UserPreferences()),
                secureValueStore = secureStore,
                pinSecurityStateStore = FakePinSecurityStateStore(PinSecurity.setPin("1234")),
                clock = { 123L },
            )
            val payload = exporter.buildBackupPayloadJson(indentSpaces = 0)
            val json = JSONObject(payload)
            val providerSource = json.getJSONArray("providers").getJSONObject(0).getJSONObject("source")

            assertEquals("FULL", json.getString("exportMode"))
            assertEquals(
                "https://xtream.example.org/player_api.php?username=fixture-user&password=fixture-password",
                providerSource.getString("xtreamServerUrl"),
            )
            assertEquals("fixture-user", providerSource.getString("xtreamUsername"))
            assertEquals("fixture-password", providerSource.getString("xtreamPassword"))
            assertEquals(
                "https://example.org/epg.xml?token=fixture-token",
                json.getJSONArray("epgSources").getJSONObject(0).getString("url"),
            )
            assertFalse(payload.contains("1234"))
            assertFalse(json.getJSONObject("security").has("pin"))

            val encrypted = exporter.exportBackup(passphrase = "full-passphrase".toCharArray())
            assertTrue(isEncryptedBackupContainer(encrypted))
            assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("fixture-password"))
            assertTrue(
                decryptBackupPayload(encrypted, "full-passphrase".toCharArray())
                    ?.contains("fixture-password") == true,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun restoreValidatorAcceptsExporterJsonAndBuildsPreview() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java).build()
        val secureStore = FakeSecureValueStore()
        try {
            database.providerDao().upsertProvider(providerEntity())
            database.favoritesDao().upsertFavorite(favoriteEntity())
            secureStore.write(SecureKey("provider:one:credentials:m3u_url"), "https://example.org/public.m3u")

            val json = StandardBackupExporter(
                database = database,
                userPreferencesStore = FakePreferencesStore(UserPreferences()),
                secureValueStore = secureStore,
                pinSecurityStateStore = FakePinSecurityStateStore(PinSecurity.setPin("1234")),
                clock = { 123L },
            ).exportInternalSnapshotJson(indentSpaces = 0)

            val result = validateStandardBackupForRestore(json)

            assertTrue(result is StandardBackupRestoreValidation.Valid)
            val preview = (result as StandardBackupRestoreValidation.Valid).preview
            assertEquals(1, preview.providerCount)
            assertEquals(1, preview.favoriteCount)
            assertTrue(preview.parentalProtectionWasActive)
        } finally {
            database.close()
        }
    }

    @Test
    fun restoreValidatorRejectsUnsafeStandardBackupInput() {
        val invalidReference = StandardBackupDocument(
            exportedAtMillis = 123L,
            favorites = listOf(
                StandardBackupFavorite(
                    providerStableKey = "missing-provider",
                    mediaType = "MOVIE",
                    mediaStableKey = "movie-stable",
                    sortOrder = 1,
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
            ),
        ).toJsonString(indentSpaces = 0)
        assertTrue(validateStandardBackupForRestore(invalidReference) is StandardBackupRestoreValidation.Invalid)

        val unsafeUrl = StandardBackupDocument(
            exportedAtMillis = 123L,
            providers = listOf(
                StandardBackupProvider(
                    stableKey = "provider-stable",
                    name = "Provider",
                    type = "M3U",
                    isActive = true,
                    status = "ACTIVE",
                    includeLiveTv = true,
                    includeMovies = true,
                    includeSeries = true,
                    refreshIntervalHours = 12,
                    logoPriority = "provider",
                    source = StandardBackupProviderSource(m3uUrl = "https://example.org/list.m3u?token=secret"),
                ),
            ),
        ).toJsonString(indentSpaces = 0)
        assertTrue(validateStandardBackupForRestore(unsafeUrl) is StandardBackupRestoreValidation.Invalid)
    }

    @Test
    fun restoreValidatorIgnoresOldPinAndProtectionFields() {
        val json = StandardBackupDocument(exportedAtMillis = 123L).toJson()
        json.getJSONObject("security")
            .put("pin", "1234")
            .put("protectSettings", true)
            .put("protectMovies", true)

        assertTrue(validateStandardBackupForRestore(json.toString()) is StandardBackupRestoreValidation.Valid)
    }

    @Test
    fun restorerReplacesScopeOnlyAfterValidation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java).build()
        val secureStore = FakeSecureValueStore()
        val pinStore = FakePinSecurityStateStore(PinSecurity.setPin("1234"))
        val preferencesStore = FakePreferencesStore(UserPreferences())
        try {
            database.providerDao().upsertProvider(providerEntity().copy(id = "old-provider", stableKey = "old-stable", sourceConfigKey = "old-key"))
            database.favoritesDao().upsertFavorite(favoriteEntity().copy(providerId = "old-provider"))
            secureStore.write(SecureKey("old-key:m3u_url"), "https://old.example.org/list.m3u")

            val invalid = StandardBackupDocument(
                exportedAtMillis = 123L,
                favorites = listOf(
                    StandardBackupFavorite(
                        providerStableKey = "missing-provider",
                        mediaType = "MOVIE",
                        mediaStableKey = "movie-stable",
                        sortOrder = 1,
                        createdAt = 1L,
                        updatedAt = 2L,
                    ),
                ),
            ).toJsonString(indentSpaces = 0)

            val restorer = StandardBackupRestorer(
                database = database,
                userPreferencesStore = preferencesStore,
                secureValueStore = secureStore,
                pinSecurityStateStore = pinStore,
                clock = { 1_000L },
            )

            assertTrue(restorer.restore(invalid) is StandardBackupRestoreValidation.Invalid)
            assertEquals("old-stable", database.providerDao().getProviders().single().stableKey)
            assertEquals("https://old.example.org/list.m3u", secureStore.read(SecureKey("old-key:m3u_url")))

            val valid = StandardBackupDocument(
                exportedAtMillis = 123L,
                providers = listOf(
                    StandardBackupProvider(
                        stableKey = "new-provider",
                        name = "New Provider",
                        type = "M3U",
                        isActive = true,
                        status = "ACTIVE",
                        includeLiveTv = true,
                        includeMovies = true,
                        includeSeries = true,
                        refreshIntervalHours = 12,
                        logoPriority = "provider",
                        source = StandardBackupProviderSource(m3uUrl = "https://example.org/public.m3u"),
                    ),
                ),
                favorites = listOf(
                    StandardBackupFavorite(
                        providerStableKey = "new-provider",
                        mediaType = "MOVIE",
                        mediaStableKey = "movie-stable",
                        sortOrder = 1,
                        createdAt = 1L,
                        updatedAt = 2L,
                    ),
                ),
                searchHistory = listOf("dune"),
                security = StandardBackupSecuritySummary(parentalProtectionWasActive = true),
            ).toJsonString(indentSpaces = 0)

            assertTrue(restorer.restore(valid) is StandardBackupRestoreValidation.Valid)

            val provider = database.providerDao().getProviders().single()
            assertEquals("new-provider", provider.stableKey)
            assertEquals("ACTIVE", provider.status)
            assertNull(secureStore.read(SecureKey("old-key:m3u_url")))
            assertEquals(
                "https://example.org/public.m3u",
                secureStore.read(SecureKey("provider:new-provider:credentials:m3u_url")),
            )
            val favorite = database.favoritesDao().getFavorites().single()
            assertEquals("new-provider", favorite.providerId)
            assertEquals("movie-stable", favorite.mediaStableKey)
            assertTrue(favorite.isPending)
            assertEquals("dune", database.searchDao().getSearchHistory().single().query)
            assertFalse(pinStore.read().hasPin)
            assertNull(preferencesStore.selectedProviderId)
            assertFalse(preferencesStore.parentalControl.pinEnabled)
        } finally {
            database.close()
        }
    }

    @Test
    fun restorerReappliesBackedUpPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java).build()
        val secureStore = FakeSecureValueStore()
        val preferencesStore = FakePreferencesStore(UserPreferences(selectedProviderId = "current-provider"))
        try {
            val backup = StandardBackupDocument(
                exportedAtMillis = 123L,
                preferences = UserPreferences(
                    selectedProviderId = "should-not-be-restored",
                    general = GeneralPreferences(resumeLastChannelOnStart = true),
                    appearance = AppearancePreferences(
                        backgroundColor = ThemeColor.AmoledDark,
                        fontScale = FontScalePreference.Large,
                    ),
                    playback = PlaybackPreferences(
                        bufferSize = BufferSizePreference.ExtraLarge,
                        afrEnabled = true,
                        audioPassthroughEnabled = true,
                        autoNextCountdownSeconds = 42,
                    ),
                ),
            ).toJsonString(indentSpaces = 0)
            val restorer = StandardBackupRestorer(
                database = database,
                userPreferencesStore = preferencesStore,
                secureValueStore = secureStore,
                pinSecurityStateStore = FakePinSecurityStateStore(PinSecurity.setPin("1234")),
            )

            assertTrue(restorer.restore(backup) is StandardBackupRestoreValidation.Valid)
            // Audit #3: backed-up settings are re-applied on restore...
            assertEquals(ThemeColor.AmoledDark, preferencesStore.appearance.backgroundColor)
            assertEquals(FontScalePreference.Large, preferencesStore.appearance.fontScale)
            assertEquals(BufferSizePreference.ExtraLarge, preferencesStore.playback.bufferSize)
            assertTrue(preferencesStore.playback.afrEnabled)
            assertTrue(preferencesStore.playback.audioPassthroughEnabled)
            assertEquals(42, preferencesStore.playback.autoNextCountdownSeconds)
            assertTrue(preferencesStore.general.resumeLastChannelOnStart)
            // ...but the provider selection stays reset (re-selected by the user).
            assertNull(preferencesStore.selectedProviderId)
        } finally {
            database.close()
        }
    }

    @Test
    fun restorerRestoresEncryptedFullBackupSecretsOnlyAfterCorrectPassphrase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceDatabase = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java).build()
        val targetDatabase = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java).build()
        val sourceSecureStore = FakeSecureValueStore()
        val targetSecureStore = FakeSecureValueStore()
        val targetPinStore = FakePinSecurityStateStore(PinSecurity.setPin("1234"))
        try {
            sourceDatabase.providerDao().upsertProvider(
                providerEntity().copy(
                    id = "provider-xtream",
                    stableKey = "xtream-stable",
                    type = "XTREAM",
                    sourceConfigKey = "provider:xtream:credentials",
                ),
            )
            sourceDatabase.epgDao().upsertEpgSources(listOf(epgSourceEntity()))
            sourceSecureStore.write(
                SecureKey("provider:xtream:credentials:xtream_server_url"),
                "https://xtream.example.org/player_api.php?username=fixture-user&password=fixture-password",
            )
            sourceSecureStore.write(SecureKey("provider:xtream:credentials:xtream_username"), "fixture-user")
            sourceSecureStore.write(SecureKey("provider:xtream:credentials:xtream_password"), "fixture-password")
            sourceSecureStore.write(SecureKey("epg:one:url"), "https://example.org/epg.xml?token=fixture-token")
            val encrypted = StandardBackupExporter(
                database = sourceDatabase,
                userPreferencesStore = FakePreferencesStore(UserPreferences()),
                secureValueStore = sourceSecureStore,
                pinSecurityStateStore = FakePinSecurityStateStore(PinSecurity.setPin("1234")),
                clock = { 123L },
            ).exportBackup(passphrase = "correct-passphrase".toCharArray())

            targetDatabase.providerDao().upsertProvider(
                providerEntity().copy(id = "old-provider", stableKey = "old-stable", sourceConfigKey = "old-key"),
            )
            targetSecureStore.write(SecureKey("old-key:m3u_url"), "https://old.example.org/list.m3u")
            val restorer = StandardBackupRestorer(
                database = targetDatabase,
                userPreferencesStore = FakePreferencesStore(UserPreferences()),
                secureValueStore = targetSecureStore,
                pinSecurityStateStore = targetPinStore,
                clock = { 1_000L },
            )

            assertTrue(restorer.restoreBackup(encrypted, "wrong-passphrase".toCharArray()) is StandardBackupRestoreValidation.Invalid)
            assertEquals("old-stable", targetDatabase.providerDao().getProviders().single().stableKey)
            assertEquals("https://old.example.org/list.m3u", targetSecureStore.read(SecureKey("old-key:m3u_url")))

            assertTrue(restorer.restoreBackup(encrypted, "correct-passphrase".toCharArray()) is StandardBackupRestoreValidation.Valid)
            val restoredProvider = targetDatabase.providerDao().getProviders().single()
            assertEquals("xtream-stable", restoredProvider.stableKey)
            assertEquals("ACTIVE", restoredProvider.status)
            assertNull(targetSecureStore.read(SecureKey("old-key:m3u_url")))
            assertEquals(
                "https://xtream.example.org/player_api.php?username=fixture-user&password=fixture-password",
                targetSecureStore.read(SecureKey("provider:xtream-stable:credentials:xtream_server_url")),
            )
            assertEquals(
                "fixture-user",
                targetSecureStore.read(SecureKey("provider:xtream-stable:credentials:xtream_username")),
            )
            assertEquals(
                "fixture-password",
                targetSecureStore.read(SecureKey("provider:xtream-stable:credentials:xtream_password")),
            )
            assertEquals(
                "https://example.org/epg.xml?token=fixture-token",
                targetSecureStore.read(SecureKey("epg-source:epg-stable:url")),
            )
            assertFalse(targetPinStore.read().hasPin)
        } finally {
            sourceDatabase.close()
            targetDatabase.close()
        }
    }

    @Test
    fun restorerHandlesLargeUserDataFixture() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, VivicastDatabase::class.java).build()
        val secureStore = FakeSecureValueStore()
        val pinStore = FakePinSecurityStateStore(PinSecurity.setPin("1234"))
        val preferencesStore = FakePreferencesStore(UserPreferences())
        try {
            database.providerDao().upsertProvider(
                providerEntity().copy(id = "old-provider", stableKey = "old-stable", sourceConfigKey = "old-key"),
            )
            secureStore.write(SecureKey("old-key:m3u_url"), "https://old.example.org/list.m3u")
            val restorer = StandardBackupRestorer(
                database = database,
                userPreferencesStore = preferencesStore,
                secureValueStore = secureStore,
                pinSecurityStateStore = pinStore,
                clock = { 1_000L },
            )
            val backup = largeUserDataBackupDocument().toJsonString(indentSpaces = 0)

            lateinit var result: StandardBackupRestoreValidation
            val restoreMs = measureTimeMillis {
                result = restorer.restore(backup)
            }

            val favorites = database.favoritesDao().getFavorites()
            val progress = database.playbackDao().getPlaybackProgress()
            val history = database.playbackDao().getChannelHistory()
            Log.i(
                BENCHMARK_LOG_TAG,
                "largeFixtureRestore restoreMs=${restoreMs} providers=${database.providerDao().getProviders().size} " +
                    "favorites=${favorites.size} progress=${progress.size} history=${history.size} " +
                    "pendingFavorites=${favorites.count { it.isPending }} pendingProgress=${progress.count { it.isPending }} " +
                    "pendingHistory=${history.count { it.isPending }}",
            )

            assertTrue(result is StandardBackupRestoreValidation.Valid)
            assertEquals("large-provider", database.providerDao().getProviders().single().stableKey)
            assertNull(secureStore.read(SecureKey("old-key:m3u_url")))
            assertEquals(LARGE_FAVORITE_COUNT, favorites.size)
            assertEquals(LARGE_PROGRESS_COUNT, progress.size)
            assertEquals(LARGE_CHANNEL_HISTORY_COUNT, history.size)
            assertTrue(favorites.count { it.isPending } >= LARGE_PENDING_REFERENCE_COUNT)
            assertFalse(pinStore.read().hasPin)
            assertNull(preferencesStore.selectedProviderId)
        } finally {
            database.close()
        }
    }

    @Test
    fun encryptedFullBackupRoundTripsPayloadAndRejectsWrongPassphrase() {
        val payload = """{"secret":"xtream-secret-token","pin":"must-not-be-restored"}"""
        val encrypted = encryptBackupPayload(
            payloadJson = payload,
            passphrase = "correct horse".toCharArray(),
            iterations = 1_000,
        )

        // Whole file is an opaque binary container: recognisable by magic, no plaintext payload.
        assertTrue(isEncryptedBackupContainer(encrypted))
        val asText = String(encrypted, Charsets.ISO_8859_1)
        assertFalse(asText.contains("xtream-secret-token"))
        assertFalse(asText.contains("must-not-be-restored"))
        assertFalse(asText.contains("secret"))

        assertEquals(payload, decryptBackupPayload(encrypted, "correct horse".toCharArray()))
        assertNull(decryptBackupPayload(encrypted, "wrong horse".toCharArray()))
        // A truncated / non-container blob is rejected.
        assertFalse(isEncryptedBackupContainer(byteArrayOf(1, 2, 3)))
        assertNull(decryptBackupPayload(byteArrayOf(1, 2, 3), "correct horse".toCharArray()))
    }
}

private fun provider(type: ProviderType): Provider =
    Provider(
        id = "provider-local",
        stableKey = "provider-stable",
        name = "Provider",
        type = type,
        sourceConfigKey = "provider-local:credentials",
        isActive = true,
        status = ProviderStatus.Active,
        includeLiveTv = true,
        includeMovies = true,
        includeSeries = true,
        refreshIntervalHours = 12,
        logoPriority = "provider",
        createdAt = 1L,
        updatedAt = 2L,
    )

private fun providerEntity(): ProviderEntity =
    ProviderEntity(
        id = "provider-one",
        stableKey = "provider-stable",
        name = "Provider",
        type = "M3U",
        sourceConfigKey = "provider:one:credentials",
        isActive = true,
        status = "ACTIVE",
        includeLiveTv = true,
        includeMovies = true,
        includeSeries = true,
        refreshIntervalHours = 12,
        logoPriority = "provider",
        createdAt = 1L,
        updatedAt = 2L,
    )

private fun favoriteEntity(): FavoriteEntity =
    FavoriteEntity(
        id = "favorite-one",
        providerId = "provider-one",
        mediaType = "MOVIE",
        mediaId = "movie-one",
        mediaStableKey = "movie-stable",
        sortOrder = 1,
        createdAt = 10L,
        updatedAt = 11L,
    )

private fun epgSourceEntity(): EpgSourceEntity =
    EpgSourceEntity(
        id = "epg-one",
        stableKey = "epg-stable",
        name = "EPG",
        sourceConfigKey = "epg:one:url",
        timeShiftMinutes = 0,
        isActive = true,
        createdAt = 1L,
        updatedAt = 2L,
    )

private fun playbackProgressEntity(): PlaybackProgressEntity =
    PlaybackProgressEntity(
        id = "progress-one",
        providerId = "provider-one",
        mediaType = "MOVIE",
        mediaId = "movie-one",
        mediaStableKey = "movie-stable",
        positionMillis = 1_000L,
        durationMillis = 10_000L,
        progressPercent = 10,
        isCompleted = false,
        lastWatchedAt = 20L,
        createdAt = 20L,
        updatedAt = 21L,
    )

private fun searchHistoryEntity(): SearchHistoryEntity =
    SearchHistoryEntity(
        id = "search-one",
        query = "dune",
        normalizedQuery = "dune",
        lastUsedAt = 30L,
        createdAt = 30L,
        updatedAt = 31L,
    )

private fun largeUserDataBackupDocument(): StandardBackupDocument =
    StandardBackupDocument(
        exportedAtMillis = 123L,
        providers = listOf(
            StandardBackupProvider(
                stableKey = "large-provider",
                name = "Large Provider",
                type = "M3U",
                isActive = true,
                status = "ACTIVE",
                includeLiveTv = true,
                includeMovies = true,
                includeSeries = true,
                refreshIntervalHours = 12,
                logoPriority = "provider",
                source = StandardBackupProviderSource(m3uUrl = "https://example.org/public.m3u"),
            ),
        ),
        favorites = (0 until LARGE_FAVORITE_COUNT).map { index ->
            StandardBackupFavorite(
                providerStableKey = "large-provider",
                mediaType = if (index % 2 == 0) "MOVIE" else "SERIES",
                mediaStableKey = "favorite-media-$index",
                sortOrder = index,
                createdAt = index.toLong(),
                updatedAt = index.toLong(),
            )
        },
        playbackProgress = (0 until LARGE_PROGRESS_COUNT).map { index ->
            StandardBackupPlaybackProgress(
                providerStableKey = "large-provider",
                mediaType = if (index % 2 == 0) "MOVIE" else "EPISODE",
                mediaStableKey = "progress-media-$index",
                positionMillis = 10_000L + index,
                durationMillis = 1_000_000L,
                progressPercent = (index % 94) + 1,
                isCompleted = false,
                lastWatchedAt = 2_000L + index,
                updatedAt = 2_000L + index,
            )
        },
        channelHistory = (0 until LARGE_CHANNEL_HISTORY_COUNT).map { index ->
            StandardBackupChannelHistory(
                providerStableKey = "large-provider",
                channelStableKey = "history-channel-$index",
                watchedAt = 3_000L + index,
                durationWatchedMillis = 60_000L + index,
                updatedAt = 3_000L + index,
            )
        },
    )

private const val BENCHMARK_LOG_TAG = "VivicastBenchmark"
private const val LARGE_FAVORITE_COUNT = 1_000
private const val LARGE_PROGRESS_COUNT = 2_000
private const val LARGE_CHANNEL_HISTORY_COUNT = 2_000
private const val LARGE_PENDING_REFERENCE_COUNT = 500

private class FakeSecureValueStore : SecureValueStore {
    private val values = mutableMapOf<SecureKey, String>()

    override suspend fun read(key: SecureKey): String? = values[key]

    override suspend fun write(key: SecureKey, value: String) {
        values[key] = value
    }

    override suspend fun delete(key: SecureKey) {
        values.remove(key)
    }
}

private class FakePinSecurityStateStore(
    private var state: PinSecurityState,
) : PinSecurityStateStore {
    override suspend fun read(): PinSecurityState = state

    override suspend fun write(state: PinSecurityState) {
        this.state = state
    }

    override suspend fun clear() {
        state = PinSecurityState()
    }
}

private class FakePreferencesStore(
    private val preferences: UserPreferences,
) : UserPreferencesStore {
    var selectedProviderId: String? = preferences.selectedProviderId
        private set
    var parentalControl: ParentalControlPreferences = preferences.parentalControl
        private set
    var general: GeneralPreferences = preferences.general
        private set
    var appearance: AppearancePreferences = preferences.appearance
        private set
    var playback: PlaybackPreferences = preferences.playback
        private set

    override val values: Flow<UserPreferences> = flowOf(preferences)

    override suspend fun updateSelectedProviderId(providerId: String?) {
        selectedProviderId = providerId
    }

    override suspend fun updateGeneral(general: GeneralPreferences) { this.general = general }
    override suspend fun updateAppearance(appearance: AppearancePreferences) { this.appearance = appearance }
    override suspend fun updatePlayback(playback: PlaybackPreferences) { this.playback = playback }
    override suspend fun updateHistory(history: HistoryPreferences) = Unit
    override suspend fun updateSearchHistory(searchHistory: List<String>) = Unit
    override suspend fun updateExpandedLiveTvProviderIds(providerIds: Set<String>) = Unit
    override suspend fun updateParentalControl(parentalControl: ParentalControlPreferences) {
        this.parentalControl = parentalControl
    }

    override suspend fun updateEpg(epg: EpgPreferences) = Unit
    override suspend fun updateBackup(backup: BackupPreferences) = Unit
    override suspend fun updateDiagnostics(diagnostics: DiagnosticsPreferences) = Unit
}
