package com.vivicast.tv.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsPlaybackPanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun watchedThresholdActionsStepAndClamp() {
        var latestThreshold = 95

        compose.setContent {
            var threshold by remember { mutableStateOf(95) }
            PlaybackSettingsPanel(
                state = PlaybackSettingsState(watchedThresholdPercent = threshold),
                onWatchedThresholdChanged = {
                    threshold = it
                    latestThreshold = it
                },
            )
        }

        compose.onNodeWithText("95 %").assertIsDisplayed()

        compose.onNodeWithTag(settingsPlaybackThresholdPlusTag()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(timeoutMillis = 5_000) { latestThreshold == 100 }
        compose.onNodeWithText("100 %").assertIsDisplayed()

        compose.onNodeWithTag(settingsPlaybackThresholdPlusTag()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(100, latestThreshold)

        compose.onNodeWithTag(settingsPlaybackThresholdMinusTag()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(timeoutMillis = 5_000) { latestThreshold == 95 }
        compose.onNodeWithText("95 %").assertIsDisplayed()
    }
}
