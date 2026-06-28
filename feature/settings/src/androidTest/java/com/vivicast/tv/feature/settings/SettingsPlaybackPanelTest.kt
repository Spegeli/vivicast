package com.vivicast.tv.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SettingsPlaybackPanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playbackPanelDoesNotExposeWatchedThresholdControl() {
        compose.setContent {
            PlaybackSettingsPanel()
        }

        compose.onNodeWithText("Wiedergabe").assertIsDisplayed()
        compose.onNodeWithText("Automatisch n\u00e4chste Folge").assertIsDisplayed()
        compose.onNodeWithText("Timeshift").assertIsDisplayed()
        compose.onNodeWithText("Maximale Timeshift-Dauer").assertIsDisplayed()
        compose.onNodeWithText("30 Minuten").assertIsDisplayed()
        compose.onNodeWithText("Timeshift-Speicher").assertIsDisplayed()
        compose.onNodeWithText("Automatisch").assertIsDisplayed()
        compose.onAllNodesWithText("Vorbereitet").assertCountEquals(0)
        compose.onAllNodesWithText("Gesehen ab").assertCountEquals(0)
        compose.onAllNodesWithText("95 %").assertCountEquals(0)
    }
}
