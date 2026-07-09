package com.vivicast.tv.feature.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsGeneralPanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun generalPanelShowsDocumentedRowsWithoutPreparedText() {
        compose.setContent {
            GeneralSettingsPanel(
                state = GeneralSettingsState(),
                onLaunchOnBootChanged = {},
                onDoubleBackToExitChanged = {},
                onBackgroundRefreshChanged = {},
                onResumeLastChannelChanged = {},
                onLanguageChanged = {},
                onGlobalUserAgentChanged = {},
            )
        }

        compose.onAllNodesWithText("App beim TV-Start starten").assertCountEquals(1)
        compose.onAllNodesWithText("Doppelte Zurück-Taste zum Beenden").assertCountEquals(1)
        compose.onAllNodesWithText("Sprache").assertCountEquals(1)
        compose.onAllNodesWithText("Hintergrundaktualisierung erlauben").assertCountEquals(1)
        compose.onAllNodesWithText("Zuletzt gesehenen Sender beim Start fortsetzen").assertCountEquals(1)
        compose.onAllNodesWithText("User-Agent").assertCountEquals(1)
        compose.onAllNodesWithText("Jetzt aktualisieren").assertCountEquals(0)
        compose.onAllNodesWithText("Vorbereitet").assertCountEquals(0)
    }

    @Test
    fun userAgentDialogSubmitsTrimmedValue() {
        var submitted: String? = null

        compose.setContent {
            GeneralSettingsPanel(
                state = GeneralSettingsState(globalUserAgent = "Vivicast/1.0"),
                onLaunchOnBootChanged = {},
                onDoubleBackToExitChanged = {},
                onBackgroundRefreshChanged = {},
                onResumeLastChannelChanged = {},
                onLanguageChanged = {},
                onGlobalUserAgentChanged = { submitted = it },
            )
        }

        compose.onNodeWithText("User-Agent").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(userAgentDialogTag()).assertIsDisplayed()
        compose.onNodeWithTag(userAgentFieldTag()).performTextClearance()
        compose.onNodeWithTag(userAgentFieldTag()).performTextInput(" Custom/2.0 ")
        compose.onNodeWithTag(userAgentSaveTag()).performSemanticsAction(SemanticsActions.OnClick)

        compose.waitUntil(timeoutMillis = 5_000) { submitted != null }
        assertEquals("Custom/2.0", submitted)
    }

    @Test
    fun appearancePanelShowsStoredRowsWithoutDeferredRows() {
        compose.setContent {
            AppearanceSettingsPanel(
                state = AppearanceSettingsState(),
                onAppearanceSettingsChanged = {},
            )
        }

        compose.onAllNodesWithText("Hintergrundthema").assertCountEquals(1)
        compose.onAllNodesWithText("Akzentfarbe").assertCountEquals(1)
        compose.onAllNodesWithText("Transparenz").assertCountEquals(1)
        compose.onAllNodesWithText("Schriftgr\u00f6\u00dfe").assertCountEquals(1)
        compose.onAllNodesWithText("Animationen").assertCountEquals(1)
        compose.onAllNodesWithText("Globale Logo-Standardreihenfolge").assertCountEquals(0)
        compose.onAllNodesWithText("Logos-Ordner").assertCountEquals(0)
        compose.onAllNodesWithText("EPG-Darstellung").assertCountEquals(0)
        compose.onAllNodesWithText("Vorbereitet").assertCountEquals(0)
    }

    @Test
    fun playbackPanelShowsDocumentedRowsAndSubmitsChanges() {
        var submitted = PlaybackSettingsState()

        compose.setContent {
            PlaybackSettingsPanel(
                state = submitted,
                onPlaybackPreferencesChanged = { submitted = it },
            )
        }

        compose.onNodeWithText("Puffergröße").assertIsDisplayed()
        compose.onNodeWithText("Audio-Decoder").assertIsDisplayed()
        compose.onNodeWithText("Video-Decoder").assertIsDisplayed()
        compose.onNodeWithText("Automatische Bildwiederholrate").assertIsDisplayed()

        // AFR stays a toggle (API 31+ emulator supports it).
        compose.onNodeWithText("Automatische Bildwiederholrate").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(true, submitted.afrEnabled)

        // Enum rows now open a single-choice popup instead of cycling: pick "Groß" -> Large.
        compose.onNodeWithText("Puffergröße").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Groß").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(PlaybackBufferSizeMode.Large, submitted.bufferSize)

        // Audio decoder popup: pick "Software".
        compose.onNodeWithText("Audio-Decoder").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Software").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(PlaybackDecoderMode.Software, submitted.audioDecoder)
    }
}
