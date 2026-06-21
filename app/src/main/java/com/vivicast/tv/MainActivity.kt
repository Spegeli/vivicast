package com.vivicast.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastScreenBackground
import com.vivicast.tv.core.designsystem.VivicastTheme
import com.vivicast.tv.core.designsystem.VivicastTopNavItem
import com.vivicast.tv.feature.livetv.LiveTvRoute
import com.vivicast.tv.feature.movies.MoviesRoute
import com.vivicast.tv.feature.player.PlayerRoute
import com.vivicast.tv.feature.search.SearchRoute
import com.vivicast.tv.feature.series.SeriesRoute
import com.vivicast.tv.feature.settings.SettingsRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VivicastTheme {
                VivicastApp()
            }
        }
    }
}

@Composable
private fun VivicastApp() {
    var playerVisible by remember { mutableStateOf(false) }
    var selectedRoute by remember { mutableStateOf("live-tv") }
    val destinations = remember {
        listOf(
            AppDestination("Live-TV", "live-tv") { LiveTvRoute(onOpenPlayer = { playerVisible = true }) },
            AppDestination("Filme", "movies") { MoviesRoute(onOpenPlayer = { playerVisible = true }) },
            AppDestination("Serien", "series") { SeriesRoute(onOpenPlayer = { playerVisible = true }) },
            AppDestination("Suche", "search") { SearchRoute() },
            AppDestination("Einstellungen", "settings") { SettingsRoute() },
        )
    }
    val selectedDestination = destinations.first { it.route == selectedRoute }

    VivicastScreenBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF0EA5E9), Color(0xFF2563EB)),
                                ),
                            ),
                    )
                    BasicText(
                        text = "VIVICAST",
                        style = TextStyle(
                            color = VivicastColors.TextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    destinations.forEach { destination ->
                        VivicastTopNavItem(
                            label = destination.label,
                            selected = destination.route == selectedRoute,
                            onSelected = { selectedRoute = destination.route },
                            onFocused = { selectedRoute = destination.route },
                            minWidth = if (destination.label == "Einstellungen") 144.dp else 96.dp,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }

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
