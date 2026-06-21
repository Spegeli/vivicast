package com.vivicast.tv.feature.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.GlassPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.MiniLogo
import com.vivicast.tv.core.designsystem.ProgressLine
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastChannelCard
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.data.media.AssetState
import com.vivicast.tv.data.media.DemoCatalog
import com.vivicast.tv.data.media.DemoChannel
import com.vivicast.tv.data.media.ProviderStatus

private enum class LiveColumnMode { Category, Channel }

@Composable
fun LiveTvRoute(onOpenPlayer: () -> Unit = {}) {
    var selectedCategory by remember { mutableStateOf("Favoriten") }
    var selectedChannel by remember { mutableStateOf(DemoCatalog.channels.first()) }
    var mode by remember { mutableStateOf(LiveColumnMode.Category) }
    var previewStarted by remember { mutableStateOf(false) }
    val channels = remember(selectedCategory) {
        if (selectedCategory == "Leer") emptyList() else DemoCatalog.channels.filter { selectedCategory in it.categories }
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
            if (mode == LiveColumnMode.Category) {
                ProviderCategoryColumn(
                    selectedCategory = selectedCategory,
                    onCategoryFocused = {
                        mode = LiveColumnMode.Category
                        selectedCategory = it
                        DemoCatalog.channels.firstOrNull { channel -> it in channel.categories }?.let { channel ->
                            selectedChannel = channel
                        }
                    },
                    modifier = Modifier.weight(0.25f),
                )
            }

            ChannelColumn(
                channels = channels,
                selectedChannel = selectedChannel,
                emptyCategory = selectedCategory == "Leer",
                onChannelFocused = {
                    selectedChannel = it
                    mode = LiveColumnMode.Channel
                },
                onChannelClick = {
                    selectedChannel = it
                    mode = LiveColumnMode.Channel
                    previewStarted = true
                },
                modifier = Modifier.weight(if (mode == LiveColumnMode.Category) 0.33f else 0.32f),
            )

            if (mode == LiveColumnMode.Channel) {
                EpgColumn(channel = selectedChannel, modifier = Modifier.weight(0.31f))
            }

            PreviewColumn(
                channel = selectedChannel,
                previewStarted = previewStarted,
                providerErrorVisible = mode == LiveColumnMode.Category,
                onOpenPlayer = onOpenPlayer,
                onShowCategoryMode = { mode = LiveColumnMode.Category },
                modifier = Modifier.weight(0.42f),
            )
        }
    }
}

@Composable
private fun ProviderCategoryColumn(
    selectedCategory: String,
    onCategoryFocused: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Provider")
            DemoCatalog.providers.forEach { provider ->
                FocusPanel(
                    selected = provider.status == ProviderStatus.Active,
                    onClick = {},
                    contentPadding = 10.dp,
                    modifier = Modifier.fillMaxWidth().height(74.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicText(
                            text = provider.name,
                            style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        )
                        BodyText(
                            provider.statusText,
                            color = if (provider.status == ProviderStatus.Error) Color(0xFFFFB4A8) else VivicastColors.TextTertiary,
                        )
                    }
                }
            }

            SectionTitle("Kategorien")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(DemoCatalog.categories) { category ->
                    FocusPanel(
                        selected = category == selectedCategory,
                        onClick = { onCategoryFocused(category) },
                        onFocused = { onCategoryFocused(category) },
                        contentPadding = 14.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BasicText(
                            text = category,
                            style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelColumn(
    channels: List<DemoChannel>,
    selectedChannel: DemoChannel,
    emptyCategory: Boolean,
    onChannelFocused: (DemoChannel) -> Unit,
    onChannelClick: (DemoChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("Senderliste")
            if (channels.isEmpty() && emptyCategory) {
                InfoPanel("Keine Sender", "Diese Kategorie enthält keine Sender.", badge = "Leer")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(channels) { channel ->
                    VivicastChannelCard(
                        channelName = channel.name,
                        program = channel.program,
                        logoText = channel.name,
                        logoMissing = channel.logoState == AssetState.Missing,
                        selected = channel == selectedChannel,
                        onFocused = { onChannelFocused(channel) },
                        onClick = { onChannelClick(channel) },
                        progressPercent = if (channel.hasEpg) 55 else 0,
                        favorite = channel.favorite,
                        catchUp = channel.catchUp,
                        logoResId = channel.logoResId,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgColumn(channel: DemoChannel, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("Sender-EPG")
            if (!channel.hasEpg) {
                InfoPanel("Keine Programminformationen", "${channel.name} zeigt derzeit kein EPG.", badge = "Ohne EPG")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(channel.epg) { item ->
                        FocusPanel(selected = item.current, contentPadding = 14.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BodyText("${item.start} - ${item.end}")
                                BasicText(
                                    text = item.title,
                                    style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                                )
                                if (item.current || item.catchUp) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (item.current) StatusBadge("Aktuell")
                                        if (item.catchUp) StatusBadge("Catch-Up")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewColumn(
    channel: DemoChannel,
    previewStarted: Boolean,
    providerErrorVisible: Boolean,
    onOpenPlayer: () -> Unit,
    onShowCategoryMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxSize(), contentPadding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Vorschau")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF16263A), Color(0xFF0B1320)),
                        ),
                    )
                    .border(1.dp, Color(0x5538BDF8), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = if (previewStarted) "Vorschau läuft" else "OK startet Vorschau",
                    style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            InfoPanel(
                title = channel.name,
                body = channel.description.ifBlank { "Keine Beschreibung vorhanden." },
                badge = if (previewStarted) "Live" else "Details",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionPill("Ansehen", onClick = onOpenPlayer)
                ActionPill("Kategorien", onClick = onShowCategoryMode)
            }
            if (!channel.hasEpg) {
                InfoPanel("Keine EPG-Daten", "Für diesen Sender sind keine Programminformationen vorhanden.", badge = "Ohne EPG")
            }
            if (providerErrorVisible) {
                InfoPanel("Provider-Fehler", "Provider B: Anmeldung fehlgeschlagen.", badge = "Error")
            }
        }
    }
}
