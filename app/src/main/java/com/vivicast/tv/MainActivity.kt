package com.vivicast.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivicast.tv.core.designsystem.VivicastTheme
import com.vivicast.tv.feature.livetv.LiveTvRoute
import com.vivicast.tv.feature.movies.MoviesRoute
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
    val destinations = remember {
        listOf(
            AppDestination("Live-TV", "live-tv") { LiveTvRoute() },
            AppDestination("Filme", "movies") { MoviesRoute() },
            AppDestination("Serien", "series") { SeriesRoute() },
            AppDestination("Suche", "search") { SearchRoute() },
            AppDestination("Einstellungen", "settings") { SettingsRoute() },
        )
    }
    var selectedRoute by remember { mutableStateOf(destinations.first().route) }
    val selectedDestination = destinations.first { it.route == selectedRoute }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101418))
            .padding(horizontal = 56.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        BasicText(
            text = "Vivicast",
            style = TextStyle(
                color = Color(0xFFF2F5F7),
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            destinations.forEach { destination ->
                TopLevelNavItem(
                    label = destination.label,
                    selected = destination.route == selectedRoute,
                    onSelected = { selectedRoute = destination.route },
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            selectedDestination.content()
        }
    }
}

@Composable
private fun TopLevelNavItem(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        focused -> Color(0xFFE5B84B)
        selected -> Color(0xFF5D7487)
        else -> Color.Transparent
    }
    val backgroundColor = if (selected) Color(0xFF26313A) else Color(0xFF171D23)

    Box(
        modifier = Modifier
            .widthIn(min = 128.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onSelected)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (selected || focused) Color.White else Color(0xFFC7D0D8),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Immutable
private data class AppDestination(
    val label: String,
    val route: String,
    val content: @Composable () -> Unit,
)
