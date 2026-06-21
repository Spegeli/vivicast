package com.vivicast.tv.feature.livetv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.MiniLogo
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
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
        Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionPill(
                    "Kategorie-Modus",
                    selected = mode == LiveColumnMode.Category,
                    onClick = { mode = LiveColumnMode.Category },
                )
                ActionPill(
                    "Sender-Modus",
                    selected = mode == LiveColumnMode.Channel,
                    onClick = { mode = LiveColumnMode.Channel },
                )
                StatusBadge("Preview: Direkt starten")
                StatusBadge("EPG-Button entfernt")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
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
                        modifier = Modifier.weight(0.24f),
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
                    modifier = Modifier.weight(if (mode == LiveColumnMode.Category) 0.30f else 0.28f),
                )

                if (mode == LiveColumnMode.Channel) {
                    EpgColumn(channel = selectedChannel, modifier = Modifier.weight(0.34f))
                }

                PreviewColumn(
                    channel = selectedChannel,
                    previewStarted = previewStarted,
                    providerErrorVisible = mode == LiveColumnMode.Category,
                    onOpenPlayer = onOpenPlayer,
                    onShowCategoryMode = { mode = LiveColumnMode.Category },
                    modifier = Modifier.weight(0.38f),
                )
            }
        }
    }
}

@Composable
private fun ProviderCategoryColumn(
    selectedCategory: String,
    onCategoryFocused: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Provider / Kategorien")
        DemoCatalog.providers.forEach { provider ->
            FocusPanel(
                selected = provider.status == ProviderStatus.Active,
                onClick = {},
                contentPadding = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(
                        text = provider.name,
                        style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    )
                    BodyText(provider.statusText, color = if (provider.status == ProviderStatus.Error) Color(0xFFFFB4A8) else VivicastColors.TextSecondary)
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(DemoCatalog.categories) { category ->
                FocusPanel(
                    selected = category == selectedCategory,
                    onClick = { onCategoryFocused(category) },
                    onFocused = { onCategoryFocused(category) },
                    contentPadding = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BasicText(
                        text = category,
                        style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    )
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Senderliste")
        if (channels.isEmpty() && emptyCategory) {
            InfoPanel("Empty State", "Diese Kategorie enthaelt keine Demo-Sender.", badge = "Leer")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(channels) { channel ->
                FocusPanel(
                    selected = channel == selectedChannel,
                    onFocused = { onChannelFocused(channel) },
                    onClick = { onChannelClick(channel) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        MiniLogo(channel.name, channel.logoState == AssetState.Missing)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = channel.name,
                                style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                            )
                            BodyText(channel.program)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (channel.favorite) StatusBadge("Favorit", tone = Color(0xFF5B3F15))
                                if (channel.catchUp) StatusBadge("Catch-Up", tone = Color(0xFF254A63))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgColumn(channel: DemoChannel, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Sender-EPG")
        if (!channel.hasEpg) {
            InfoPanel("Fehlende EPG-Daten", "${channel.name} zeigt den Fallback fuer Sender ohne EPG.", badge = "Fallback")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(channel.epg) { item ->
                    FocusPanel(selected = item.current, contentPadding = 12.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            BodyText("${item.start} - ${item.end}")
                            BasicText(
                                text = item.title,
                                style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
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

@Composable
private fun PreviewColumn(
    channel: DemoChannel,
    previewStarted: Boolean,
    providerErrorVisible: Boolean,
    onOpenPlayer: () -> Unit,
    onShowCategoryMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Vorschau / Details")
        InfoPanel(
            title = channel.name,
            body = channel.description.ifBlank { "Keine Beschreibung vorhanden." },
            badge = if (previewStarted) "Preview laeuft" else "Details",
        )
        InfoPanel(
            title = "Preview-Flaeche",
            body = if (channel.logoState == AssetState.Missing) "Fallback ohne Senderlogo sichtbar." else "Lokale Demo-Preview.",
            modifier = Modifier.height(132.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionPill("Player Demo", onClick = onOpenPlayer)
            ActionPill("Kategorie-Modus", onClick = onShowCategoryMode)
        }
        if (!channel.hasEpg) {
            InfoPanel("EPG Fallback", "Keine EPG-Daten fuer diesen Sender vorhanden.", badge = "Ohne EPG")
        }
        if (providerErrorVisible) {
            InfoPanel("Provider-Fehler", "Provider B: Anmeldung fehlgeschlagen.", badge = "Error")
        }
    }
}
