package com.vivicast.tv.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.vivicast.tv.domain.model.EpgSource
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SettingsDialogFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun deleteProviderDialogFocusesCancelAndBackDismisses() {
        var deleted = false

        compose.setContent {
            var show by remember { mutableStateOf(true) }
            if (show) {
                DeleteProviderDialog(
                    provider = TEST_PROVIDER,
                    onCancel = { show = false },
                    onDelete = { deleted = true },
                )
            }
        }

        compose.onNodeWithTag(deleteProviderDialogTag(TEST_PROVIDER.id)).assertIsDisplayed()
        compose.onNodeWithTag(deleteProviderCancelTag(TEST_PROVIDER.id)).assertIsFocused()

        compose.onNodeWithTag(deleteProviderCancelTag(TEST_PROVIDER.id)).performKeyInput {
            pressKey(Key.Back)
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(deleteProviderDialogTag(TEST_PROVIDER.id)).fetchSemanticsNodes().isEmpty()
        }
        compose.onAllNodesWithTag(deleteProviderDialogTag(TEST_PROVIDER.id)).assertCountEquals(0)
        assertFalse(deleted)
    }

    @Test
    fun deleteProviderConfirmCallsDelete() {
        var deleted = false

        compose.setContent {
            DeleteProviderDialog(
                provider = TEST_PROVIDER,
                onCancel = {},
                onDelete = { deleted = true },
            )
        }

        compose.onNodeWithTag(deleteProviderConfirmTag(TEST_PROVIDER.id)).performSemanticsAction(SemanticsActions.OnClick)

        compose.waitUntil(timeoutMillis = 5_000) { deleted }
    }

    @Test
    fun deleteEpgSourceDialogFocusesCancelAndBackDismisses() {
        var deleted = false

        compose.setContent {
            var show by remember { mutableStateOf(true) }
            if (show) {
                DeleteEpgSourceDialog(
                    source = TEST_EPG_SOURCE,
                    onCancel = { show = false },
                    onDelete = { deleted = true },
                )
            }
        }

        compose.onNodeWithTag(deleteEpgSourceDialogTag(TEST_EPG_SOURCE.id)).assertIsDisplayed()
        compose.onNodeWithTag(deleteEpgSourceCancelTag(TEST_EPG_SOURCE.id)).assertIsFocused()

        compose.onNodeWithTag(deleteEpgSourceCancelTag(TEST_EPG_SOURCE.id)).performKeyInput {
            pressKey(Key.Back)
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag(deleteEpgSourceDialogTag(TEST_EPG_SOURCE.id)).fetchSemanticsNodes().isEmpty()
        }
        compose.onAllNodesWithTag(deleteEpgSourceDialogTag(TEST_EPG_SOURCE.id)).assertCountEquals(0)
        assertFalse(deleted)
    }

    @Test
    fun pinSetDialogSubmitsMatchingPin() {
        var savedPin: String? = null

        compose.setContent {
            ParentalControlSettingsPanel(
                state = ParentalControlSettingsState(hasPin = false),
                onSetPin = {
                    savedPin = it
                    null
                },
                onChangePin = { _, _ -> null },
                onDisablePin = { null },
            )
        }

        compose.onNodeWithText("PIN setzen").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(pinDialogTag()).assertIsDisplayed()
        compose.onNodeWithTag(pinNewFieldTag()).performTextInput("1234")
        compose.onNodeWithTag(pinRepeatFieldTag()).performTextInput("1234")
        compose.onNodeWithTag(pinConfirmTag()).performSemanticsAction(SemanticsActions.OnClick)

        compose.waitUntil(timeoutMillis = 5_000) { savedPin == "1234" }
    }

    @Test
    fun protectionAreaRowSubmitsAreaAndEnabledState() {
        var submitted: Pair<ParentalProtectionArea, Boolean>? = null

        compose.setContent {
            ParentalControlSettingsPanel(
                state = ParentalControlSettingsState(hasPin = true),
                onSetPin = { null },
                onChangePin = { _, _ -> null },
                onDisablePin = { null },
                onProtectionChanged = { area, enabled ->
                    submitted = area to enabled
                    null
                },
            )
        }

        compose.onNodeWithText("Filme schützen").performSemanticsAction(SemanticsActions.OnClick)

        compose.waitUntil(timeoutMillis = 5_000) { submitted != null }
        assertEquals(ParentalProtectionArea.Movies to true, submitted)
    }

    @Test
    fun fullBackupPassphraseDialogSubmitsExportPassphrase() {
        var submitted: String? = null

        compose.setContent {
            BackupSettingsPanel(
                onExportEncryptedFullBackup = { submitted = it },
            )
        }

        compose.onNodeWithText("Vollbackup exportieren").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(fullBackupPassphraseDialogTag()).assertIsDisplayed()
        compose.onNodeWithTag(fullBackupPassphraseFieldTag()).performTextInput("secret-pass")
        compose.onNodeWithTag(fullBackupPassphraseConfirmTag()).performSemanticsAction(SemanticsActions.OnClick)

        compose.waitUntil(timeoutMillis = 5_000) { submitted == "secret-pass" }
    }
}

private val TEST_PROVIDER = Provider(
    id = "provider-a",
    name = "Provider A",
    type = ProviderType.M3u,
    sourceConfigKey = "provider-a-key",
    isActive = true,
    status = ProviderStatus.Active,
    includeLiveTv = true,
    includeMovies = true,
    includeSeries = true,
    refreshIntervalHours = 24,
    logoPriority = "playlist",
    createdAt = 1_000L,
    updatedAt = 1_000L,
)

private val TEST_EPG_SOURCE = EpgSource(
    id = "epg-a",
    name = "EPG A",
    sourceConfigKey = "epg-a-key",
    timeShiftMinutes = 0,
    isActive = true,
)
