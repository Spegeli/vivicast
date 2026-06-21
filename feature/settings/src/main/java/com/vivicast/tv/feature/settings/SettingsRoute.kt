package com.vivicast.tv.feature.settings

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.data.media.DemoCatalog
import com.vivicast.tv.data.media.DemoSetting

@Composable
fun SettingsRoute() {
    var selectedSection by remember { mutableStateOf("Optik") }
    var showConfirm by remember { mutableStateOf(false) }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(0.28f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Einstellungen")
                DemoCatalog.settingsSections.forEach { section ->
                    FocusPanel(
                        selected = section == selectedSection,
                        onClick = { selectedSection = section },
                        onFocused = { selectedSection = section },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BasicText(
                            text = section,
                            style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(0.72f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(selectedSection)
                    StatusBadge("Preview-Startverhalten: Direkt starten")
                }
                when (selectedSection) {
                    "Optik" -> SettingsOptions(showConfirm = { showConfirm = true })
                    "Demo States" -> DemoStates(showConfirm = { showConfirm = true })
                    else -> InfoPanel(
                        title = "$selectedSection Demo",
                        body = "Phase-2 Optionskarten als Platzhalter ohne echte Systemfunktion.",
                        badge = "Demo",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showConfirm) {
        Dialog(onDismissRequest = { showConfirm = false }) {
            InfoPanel(
                title = "Confirm Dialog",
                body = "Demo-Dialog: Diese Aktion aendert keine echten Einstellungen und speichert keine Daten.",
                badge = "Dialog",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsOptions(showConfirm: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(DemoCatalog.settings) { setting ->
            SettingRow(setting)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionPill("Confirm Dialog", onClick = showConfirm)
                StatusBadge("Fokus auf Row")
                StatusBadge("Preview-Startverhalten vorhanden")
            }
        }
    }
}

@Composable
private fun SettingRow(setting: DemoSetting) {
    FocusPanel(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                BasicText(
                    text = setting.title,
                    style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                )
                BodyText(setting.help)
            }
            BasicText(
                text = setting.value,
                style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun DemoStates(showConfirm: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoPanel("Loading State", "Lokaler Demo-Ladezustand ohne Netzwerkzugriff.", badge = "Loading")
        InfoPanel("Empty State", "Leere Kategorie und keine Suchtreffer sind sichtbar testbar.", badge = "Empty")
        InfoPanel("Error State", "Provider B: Anmeldung fehlgeschlagen.", badge = "Error")
        InfoPanel("Provider-Fehlerzustand", "Der Fehler wird im Live-TV Browser und hier angezeigt.", badge = "Provider B")
        ActionPill("Confirm Dialog oeffnen", modifier = Modifier.height(62.dp), onClick = showConfirm)
    }
}
