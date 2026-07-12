package com.vivicast.tv.core.database

import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vivicast.tv.core.database.model.ProviderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Regression guard for the startup-refresh UI freeze: with the InvalidationTracker warmed at startup
 * (as [warmInvalidationTracker] does in the app), a new Flow subscription must emit immediately even
 * while a long import transaction holds the writer — instead of blocking on Room's first-observer
 * `syncTriggers` write until the import commits (which showed empty lists in the UI).
 *
 * WAL lets the underlying read see the last committed snapshot; the warm-up removes the trigger-sync
 * write from the subscription path. Without the warm-up this read would time out.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseReadDuringWriteTest {
    @Test
    fun warmedTrackerFlowReadIsNotBlockedByLongWrite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        val database = Room.databaseBuilder(context, VivicastDatabase::class.java, TEST_DB)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        try {
            // Production warm-up: install the invalidation triggers eagerly.
            database.warmInvalidationTracker()

            // Confirm the triggers are live (fire on a write) before opening the long transaction.
            val triggersLive = CountDownLatch(1)
            database.invalidationTracker.addObserver(
                object : InvalidationTracker.Observer("providers") {
                    override fun onInvalidated(tables: Set<String>) = triggersLive.countDown()
                },
            )
            database.providerDao().upsertProvider(sampleProvider())
            check(triggersLive.await(5, TimeUnit.SECONDS)) { "invalidation triggers were not installed" }

            // Hold a long EXCLUSIVE Room transaction open, like a catalog import.
            val transactionStarted = CountDownLatch(1)
            val writer = launch(Dispatchers.IO) {
                database.withTransaction {
                    transactionStarted.countDown()
                    Thread.sleep(WRITE_HOLD_MILLIS)
                }
            }
            transactionStarted.await()

            // Must return the committed provider well before the 2s transaction commits.
            val providers = withTimeout(READ_TIMEOUT_MILLIS) {
                database.providerDao().observeProviders().first()
            }
            assertEquals(listOf("Fixture"), providers.map { it.name })

            writer.join()
        } finally {
            database.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    private fun sampleProvider(): ProviderEntity =
        ProviderEntity(
            id = "provider-1",
            name = "Fixture",
            type = "M3U",
            sourceConfigKey = "secure:1",
            isActive = true,
            status = "ACTIVE",
            includeLiveTv = true,
            includeMovies = true,
            includeSeries = true,
            refreshIntervalHours = 24,
            logoPriority = "playlist",
            createdAt = 1L,
            updatedAt = 2L,
        )

    private companion object {
        const val TEST_DB = "read-during-write-test.db"
        const val WRITE_HOLD_MILLIS = 2_000L
        const val READ_TIMEOUT_MILLIS = 1_000L
    }
}
