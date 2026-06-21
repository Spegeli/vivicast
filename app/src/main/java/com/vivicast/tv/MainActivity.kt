package com.vivicast.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vivicast.tv.core.cache.MediaCacheStats
import com.vivicast.tv.core.datastore.UserPreferences
import com.vivicast.tv.core.designsystem.VivicastScreenBackground
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTheme
import com.vivicast.tv.core.designsystem.VivicastTopNavigation
import com.vivicast.tv.feature.settings.CacheSettingsState
import com.vivicast.tv.feature.settings.GeneralSettingsState
import com.vivicast.tv.feature.livetv.LiveTvRoute
import com.vivicast.tv.feature.movies.MoviesRoute
import com.vivicast.tv.feature.player.PlayerRoute
import com.vivicast.tv.feature.search.SearchRoute
import com.vivicast.tv.feature.series.SeriesRoute
import com.vivicast.tv.feature.settings.SettingsRoute
import com.vivicast.tv.di.AppContainer
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as VivicastApplication).appContainer
        setContent {
            VivicastTheme {
                VivicastApp(appContainer = appContainer)
            }
        }
    }
}

@Composable
private fun VivicastApp(appContainer: AppContainer) {
    var playerVisible by remember { mutableStateOf(false) }
    var selectedRoute by remember { mutableStateOf("live-tv") }
    var cacheStats by remember { mutableStateOf(MediaCacheStats(totalSizeBytes = 0L, fileCount = 0)) }
    val preferences by appContainer.userPreferencesStore.values.collectAsState(initial = UserPreferences())
    val scope = rememberCoroutineScope()

    LaunchedEffect(preferences.general.backgroundRefreshEnabled) {
        appContainer.refreshWorkScheduler.setBackgroundRefreshEnabled(
            enabled = preferences.general.backgroundRefreshEnabled,
        )
    }

    LaunchedEffect(selectedRoute) {
        if (selectedRoute == "settings") {
            cacheStats = appContainer.mediaCacheStore.stats()
        }
    }

    val destinations = listOf(
        AppDestination("Live-TV", "live-tv") { LiveTvRoute(onOpenPlayer = { playerVisible = true }) },
        AppDestination("Filme", "movies") { MoviesRoute(onOpenPlayer = { playerVisible = true }) },
        AppDestination("Serien", "series") { SeriesRoute(onOpenPlayer = { playerVisible = true }) },
        AppDestination("Suche", "search") { SearchRoute() },
        AppDestination("Einstellungen", "settings") {
            SettingsRoute(
                providerRepository = appContainer.providerRepository,
                epgSourceRepository = appContainer.epgSourceRepository,
                generalSettingsState = GeneralSettingsState(
                    launchOnBoot = preferences.general.launchOnBoot,
                    backgroundRefreshEnabled = preferences.general.backgroundRefreshEnabled,
                    rememberSorting = preferences.general.rememberSorting,
                ),
                cacheSettingsState = CacheSettingsState(
                    maxCacheSizeMb = preferences.cache.maxCacheSizeMb,
                    totalSizeBytes = cacheStats.totalSizeBytes,
                    fileCount = cacheStats.fileCount,
                ),
                onBackgroundRefreshChanged = { enabled ->
                    scope.launch {
                        appContainer.userPreferencesStore.updateGeneral(
                            preferences.general.copy(backgroundRefreshEnabled = enabled),
                        )
                        appContainer.refreshWorkScheduler.setBackgroundRefreshEnabled(enabled)
                    }
                },
                onRememberSortingChanged = { enabled ->
                    scope.launch {
                        appContainer.userPreferencesStore.updateGeneral(
                            preferences.general.copy(rememberSorting = enabled),
                        )
                    }
                },
                onRunGlobalRefresh = {
                    appContainer.refreshWorkScheduler.enqueueGlobalRefresh()
                },
                onCacheSizeChanged = { maxSizeMb ->
                    scope.launch {
                        appContainer.userPreferencesStore.updateCache(
                            preferences.cache.copy(maxCacheSizeMb = maxSizeMb),
                        )
                    }
                },
                onRunLogoRefresh = {
                    appContainer.refreshWorkScheduler.enqueueLogoRefresh()
                },
                onRunCacheCleanup = {
                    appContainer.refreshWorkScheduler.enqueueCacheCleanup()
                },
                onClearCache = {
                    scope.launch {
                        appContainer.mediaCacheStore.clear()
                        cacheStats = appContainer.mediaCacheStore.stats()
                    }
                },
                onReloadCacheStats = {
                    scope.launch {
                        cacheStats = appContainer.mediaCacheStore.stats()
                    }
                },
            )
        },
    )
    val selectedDestination = destinations.first { it.route == selectedRoute }
    val selectedIndex = destinations.indexOf(selectedDestination)

    VivicastScreenBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = VivicastSpacing.ScreenHorizontal, vertical = VivicastSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space5),
        ) {
            VivicastTopNavigation(
                brand = "VIVICAST",
                items = destinations.map { it.label },
                selectedIndex = selectedIndex,
                onSelected = { index -> selectedRoute = destinations[index].route },
                onFocused = { index -> selectedRoute = destinations[index].route },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                selectedDestination.content()
            }
        }
    }

    if (playerVisible) {
        PlayerRoute(onClose = { playerVisible = false })
    }
}

@Immutable
private data class AppDestination(
    val label: String,
    val route: String,
    val content: @Composable () -> Unit,
)
