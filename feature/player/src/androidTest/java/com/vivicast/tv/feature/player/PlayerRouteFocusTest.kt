package com.vivicast.tv.feature.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.vivicast.tv.core.designsystem.playerTimelineTag
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class PlayerRouteFocusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overlayStartsWithTimelineFocusBackHidesAndOkRestores() {
        var closed = false

        compose.setContent {
            PlayerRoute(onClose = { closed = true })
        }

        compose.onNodeWithTag(playerOverlayTag()).assertIsDisplayed()
        compose.onNodeWithTag(playerTimelineTag()).assertIsFocused()

        pressBack()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerHiddenOverlayTag()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag(playerOverlayTag()).assertCountEquals(0)
        compose.onNodeWithTag(playerHiddenOverlayActionTag()).assertIsFocused()
        assertFalse(closed)

        compose.onNodeWithTag(playerHiddenOverlayActionTag()).performKeyInput {
            pressKey(Key.Enter)
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerOverlayTag()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(playerTimelineTag()).assertIsFocused()
    }

    @Test
    fun backClosesPlayerAfterOverlayIsHidden() {
        var closed = false

        compose.setContent {
            PlayerRoute(onClose = { closed = true })
        }

        pressBack()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(playerHiddenOverlayTag()).fetchSemanticsNodes().isNotEmpty()
        }

        pressBack()

        compose.waitUntil(timeoutMillis = 5_000) { closed }
    }

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }
}
