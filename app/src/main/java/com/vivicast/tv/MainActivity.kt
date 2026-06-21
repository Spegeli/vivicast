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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vivicast.tv.core.designsystem.VivicastScreenBackground
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTheme
import com.vivicast.tv.core.designsystem.VivicastTopNavigation
import com.vivicast.tv.feature.livetv.LiveTvRoute
import com.vivicast.tv.feature.movies.MoviesRoute
import com.vivicast.tv.feature.player.PlayerRoute
import com.vivicast.tv.feature.search.SearchRoute
import com.vivicast.tv.feature.series.SeriesRoute
import com.vivicast.tv.feature.settings.SettingsRoute
import com.vivicast.tv.di.AppContainer

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
    val destinations = remember {
        listOf(
            AppDestination("Live-TV", "live-tv") { LiveTvRoute(onOpenPlayer = { playerVisible = true }) },
            AppDestination("Filme", "movies") { MoviesRoute(onOpenPlayer = { playerVisible = true }) },
            AppDestination("Serien", "series") { SeriesRoute(onOpenPlayer = { playerVisible = true }) },
            AppDestination("Suche", "search") { SearchRoute() },
            AppDestination("Einstellungen", "settings") {
                SettingsRoute(providerRepository = appContainer.providerRepository)
            },
        )
    }
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
