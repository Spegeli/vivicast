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
import com.vivicast.tv.core.designsystem.GlassPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastSettingsRow
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.data.media.DemoCatalog
import com.vivicast.tv.data.media.DemoSetting

@Composable
fun SettingsRoute() {
    var selectedSection by remember { mutableStateOf("Optik") }
    var showConfirm by remember { mutableStateOf(false) }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.fillMaxSize()) {
            GlassPanel(modifier = Modifier.weight(0.30f).fillMaxSize(), contentPadding = 22.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionTitle("Einstellungen")
                DemoCatalog.settingsSections.forEach { section ->
                    FocusPanel(
                        selected = section == selectedSection,
                        onClick = { selectedSection = section },
                        onFocused = { selectedSection = section },
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                    ) {
                        BasicText(
                            text = section,
                            style = TextStyle(color = VivicastColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
            }

            GlassPanel(modifier = Modifier.weight(0.70f).fillMaxSize(), contentPadding = 26.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(selectedSection)
                }
                when (selectedSection) {
                    "Optik" -> SettingsOptions(showConfirm = { showConfirm = true })
                    "Status" -> DemoStates(showConfirm = { showConfirm = true })
                    else -> InfoPanel(
                        title = selectedSection,
                        body = "Bereich ist vorbereitet. Optionen werden später hier gebündelt.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            }
        }
    }

    if (showConfirm) {
        Dialog(onDismissRequest = { showConfirm = false }) {
            InfoPanel(
                title = "Änderung bestätigen",
                body = "Diese lokale UI-Aktion speichert keine echten Einstellungen.",
                badge = "Bestätigen",
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
                ActionPill("Änderung prüfen", onClick = showConfirm)
            }
        }
    }
}

@Composable
private fun SettingRow(setting: DemoSetting) {
    VivicastSettingsRow(title = setting.title, help = setting.help, value = setting.value)
}

@Composable
private fun DemoStates(showConfirm: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoPanel("Ladezustand", "Lokaler Ladezustand ohne Netzwerkzugriff.", badge = "Laden")
        InfoPanel("Leerer Zustand", "Leere Kategorie und keine Suchtreffer sind sichtbar testbar.", badge = "Leer")
        InfoPanel("Fehlerzustand", "Provider B: Anmeldung fehlgeschlagen.", badge = "Fehler")
        InfoPanel("Provider-Hinweis", "Der Fehler bleibt im Live-TV Browser nachvollziehbar.", badge = "Provider B")
        ActionPill("Bestätigen", modifier = Modifier.height(62.dp), onClick = showConfirm)
    }
}
