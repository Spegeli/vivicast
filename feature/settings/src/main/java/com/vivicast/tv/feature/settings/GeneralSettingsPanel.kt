package com.vivicast.tv.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.GlassPanel
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastBorders
import com.vivicast.tv.core.designsystem.VivicastCardSizes
import com.vivicast.tv.core.designsystem.VivicastButtonRow
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastDialog
import com.vivicast.tv.core.designsystem.VivicastDialogActions
import com.vivicast.tv.core.designsystem.VivicastDialogError
import com.vivicast.tv.core.designsystem.VivicastDialogWidth
import com.vivicast.tv.core.designsystem.VivicastTextField
import com.vivicast.tv.core.designsystem.VivicastScreen
import com.vivicast.tv.core.designsystem.VivicastSettingsRow
import com.vivicast.tv.core.designsystem.VivicastShapes
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTypography
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.EpgSourcePriorityDirection
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.data.provider.DEFAULT_REFRESH_INTERVAL_HOURS
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.provider.ProviderRepository
import com.vivicast.tv.data.provider.ProviderUpdateRequest
import com.vivicast.tv.data.provider.isAutomaticallyRefreshable
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderEpgSource
import com.vivicast.tv.domain.model.ProviderStatus
import com.vivicast.tv.domain.model.ProviderType
import com.vivicast.tv.domain.model.EpgSource
import androidx.compose.ui.res.stringResource
import com.vivicast.tv.core.designsystem.R
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun GeneralSettingsPanel(
    state: GeneralSettingsState,
    onLaunchOnBootChanged: (Boolean) -> Unit,
    onDoubleBackToExitChanged: (Boolean) -> Unit,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    onLanguageChanged: (SettingsLanguage) -> Unit,
    onGlobalUserAgentChanged: (String) -> Unit,
    firstFocusModifier: Modifier = Modifier,
    focusLanguageInsteadOfFirst: Boolean = false,
) {
    var showUserAgentDialog by remember { mutableStateOf(false) }
    val toggleLaunchOnBoot = { onLaunchOnBootChanged(!state.launchOnBoot) }
    val toggleBackgroundRefresh = { onBackgroundRefreshChanged(!state.backgroundRefreshEnabled) }
    val toggleDoubleBack = { onDoubleBackToExitChanged(!state.doubleBackToExit) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_launch_on_boot),
                help = stringResource(R.string.settings_help_launch_on_boot),
                value = if (state.launchOnBoot) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = if (focusLanguageInsteadOfFirst) Modifier else firstFocusModifier,
                icon = { SettingsRowIcon("power") },
                onClick = toggleLaunchOnBoot,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_double_back),
                help = stringResource(R.string.settings_help_double_back),
                value = if (state.doubleBackToExit) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("back") },
                onClick = toggleDoubleBack,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_language),
                help = stringResource(R.string.settings_help_language),
                value = state.appLanguage.label(),
                modifier = if (focusLanguageInsteadOfFirst) firstFocusModifier else Modifier,
                icon = { SettingsRowIcon("language") },
                onClick = { showLanguagePicker = true },
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_background_refresh),
                help = stringResource(R.string.settings_help_background_refresh),
                value = if (state.backgroundRefreshEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                icon = { SettingsRowIcon("refresh") },
                onClick = toggleBackgroundRefresh,
            )
        }

        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_user_agent),
                help = stringResource(R.string.settings_help_user_agent),
                value = state.globalUserAgent.ifBlank { stringResource(R.string.value_app_default) },
                modifier = Modifier,
                icon = { SettingsRowIcon("server") },
                onClick = { showUserAgentDialog = true },
            )
        }

    }

    if (showUserAgentDialog) {
        UserAgentDialog(
            initialValue = state.globalUserAgent,
            onCancel = { showUserAgentDialog = false },
            onSave = { value ->
                onGlobalUserAgentChanged(value)
                showUserAgentDialog = false
            },
        )
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            current = state.appLanguage,
            onSelect = { lang ->
                if (lang != state.appLanguage) onLanguageChanged(lang)
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false },
        )
    }
}

@Composable
private fun UserAgentDialog(
    initialValue: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }
    val strInvalidChars = stringResource(R.string.settings_ua_invalid_chars)
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Compact,
        title = stringResource(R.string.settings_user_agent),
        modifier = Modifier.testTag(userAgentDialogTag()),
    ) {
        VivicastTextField(
            value = value,
            onValueChange = {
                value = it.take(200)
                error = null
            },
            fieldModifier = Modifier.testTag(userAgentFieldTag()),
            focusRequester = fieldFocus,
            isError = error != null,
        )
        BodyText(stringResource(R.string.settings_ua_default_hint), maxLines = 2)
        if (error != null) {
            BodyText(error!!, color = VivicastColors.Error)
        }
        VivicastDialogActions(
            primaryLabel = stringResource(R.string.common_save),
            onPrimary = {
                val trimmed = value.trim()
                if (trimmed.any { it.isISOControl() }) {
                    error = strInvalidChars
                } else {
                    onSave(trimmed)
                }
            },
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            primaryTestTag = userAgentSaveTag(),
            secondaryTestTag = userAgentCancelTag(),
        )
    }
}

fun userAgentDialogTag(): String = "settings-user-agent-dialog"
fun userAgentFieldTag(): String = "settings-user-agent-field"
fun userAgentSaveTag(): String = "settings-user-agent-save"
fun userAgentCancelTag(): String = "settings-user-agent-cancel"

@Composable
private fun SettingsLanguage.label(): String = when (this) {
    SettingsLanguage.System -> stringResource(R.string.language_system)
    SettingsLanguage.German -> stringResource(R.string.language_german)
    SettingsLanguage.English -> stringResource(R.string.language_english)
}

@Composable
private fun LanguagePickerDialog(
    current: SettingsLanguage,
    onSelect: (SettingsLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocusRequester = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Compact,
        title = stringResource(R.string.settings_language),
        initialFocus = selectedFocusRequester,
    ) {
        SettingsLanguage.entries.forEach { lang ->
            FocusPanel(
                selected = lang == current,
                onClick = { onSelect(lang) },
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(unbounded = true)
                    .then(if (lang == current) Modifier.focusRequester(selectedFocusRequester) else Modifier),
            ) {
                BasicText(
                    text = lang.label(),
                    style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                )
            }
        }
    }
}

