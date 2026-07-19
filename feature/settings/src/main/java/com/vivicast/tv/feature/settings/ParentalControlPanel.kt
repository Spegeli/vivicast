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
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.data.provider.DEFAULT_REFRESH_INTERVAL_HOURS
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderCreateRequest
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
fun ParentalControlSettingsPanel(
    state: ParentalControlSettingsState,
    onSetPin: (String) -> String?,
    onChangePin: (String, String) -> String?,
    onDisablePin: (String) -> String?,
    onProtectionChanged: (ParentalProtectionArea, Boolean) -> String? = { _, _ -> null },
    firstFocusModifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<PinDialogMode?>(null) }
    var inlineError by remember { mutableStateOf<String?>(null) }
    val strProtectSettings = stringResource(R.string.settings_protect_settings)
    val strProtectMovies = stringResource(R.string.settings_protect_movies)
    val strProtectSeries = stringResource(R.string.settings_protect_series)
    val strProtectAdult = stringResource(R.string.settings_protect_adult)
    val strPinFirst = stringResource(R.string.settings_pin_first)
    val strHelpProtectSettings = stringResource(R.string.settings_help_protect_settings)
    val strHelpProtectMovies = stringResource(R.string.settings_help_protect_movies)
    val strHelpProtectSeries = stringResource(R.string.settings_help_protect_series)
    val strHelpProtectAdult = stringResource(R.string.settings_help_protect_adult)

    fun changeProtection(area: ParentalProtectionArea, enabled: Boolean) {
        inlineError = if (state.hasPin) {
            onProtectionChanged(area, enabled)
        } else {
            strPinFirst
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        inlineError?.let { message ->
            item {
                InfoPanel(
                    title = stringResource(R.string.common_note),
                    body = message,
                )
            }
        }
        item {
            VivicastSettingsRow(
                title = if (state.hasPin) stringResource(R.string.settings_pin_change) else stringResource(R.string.settings_pin_set),
                help = stringResource(R.string.settings_help_pin_entry),
                value = if (state.hasPin) stringResource(R.string.settings_pin_change_value) else stringResource(R.string.settings_pin_set_value),
                icon = { SettingsRowIcon("key") },
                onClick = { dialog = if (state.hasPin) PinDialogMode.Change else PinDialogMode.Set },
            )
        }
        if (state.hasPin) {
            item {
                VivicastSettingsRow(
                    title = stringResource(R.string.settings_pin_disable),
                    help = stringResource(R.string.settings_help_pin_change),
                    value = stringResource(R.string.about_open_value),
                    icon = { SettingsRowIcon("lock_off") },
                    onClick = { dialog = PinDialogMode.Disable },
                )
            }
        }
        protectionAreaItem(
            title = strProtectSettings,
            help = strHelpProtectSettings,
            enabled = state.protectSettings,
            onClick = { changeProtection(ParentalProtectionArea.Settings, !state.protectSettings) },
            hasPin = state.hasPin,
        )
        protectionAreaItem(
            title = strProtectMovies,
            help = strHelpProtectMovies,
            enabled = state.protectMovies,
            onClick = { changeProtection(ParentalProtectionArea.Movies, !state.protectMovies) },
            hasPin = state.hasPin,
        )
        protectionAreaItem(
            title = strProtectSeries,
            help = strHelpProtectSeries,
            enabled = state.protectSeries,
            onClick = { changeProtection(ParentalProtectionArea.Series, !state.protectSeries) },
            hasPin = state.hasPin,
        )
        protectionAreaItem(
            title = strProtectAdult,
            help = strHelpProtectAdult,
            enabled = state.protectAdultContent,
            onClick = { changeProtection(ParentalProtectionArea.AdultContent, !state.protectAdultContent) },
            hasPin = state.hasPin,
        )
    }

    dialog?.let { mode ->
        PinDialog(
            mode = mode,
            onCancel = { dialog = null },
            onConfirm = { currentPin, newPin ->
                val error = when (mode) {
                    PinDialogMode.Set -> onSetPin(newPin)
                    PinDialogMode.Change -> onChangePin(currentPin, newPin)
                    PinDialogMode.Disable -> onDisablePin(currentPin)
                }
                if (error == null) {
                    dialog = null
                }
                error
            },
        )
    }
}

private fun LazyListScope.protectionAreaItem(
    title: String,
    help: String,
    enabled: Boolean,
    onClick: () -> Unit,
    hasPin: Boolean,
) {
    item {
        VivicastSettingsRow(
            title = title,
            help = help,
            value = if (!hasPin) {
                stringResource(R.string.settings_pin_required)
            } else if (enabled) {
                stringResource(R.string.value_on)
            } else {
                stringResource(R.string.value_off)
            },
            onClick = onClick,
        )
    }
}

private enum class PinDialogMode { Set, Change, Disable }

@Composable
private fun PinDialog(
    mode: PinDialogMode,
    onCancel: () -> Unit,
    onConfirm: (currentPin: String, newPin: String) -> String?,
) {
    val firstFocusRequester = remember { FocusRequester() }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var repeatPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val strPinCurrentRequired = stringResource(R.string.settings_pin_current_required)
    val strPinFourDigits = stringResource(R.string.settings_pin_four_digits)
    val strPinMismatch = stringResource(R.string.settings_pin_mismatch)
    val title = when (mode) {
        PinDialogMode.Set -> stringResource(R.string.settings_pin_set)
        PinDialogMode.Change -> stringResource(R.string.settings_pin_change)
        PinDialogMode.Disable -> stringResource(R.string.settings_pin_disable)
    }

    val pinKeyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    val pinPlaceholder = stringResource(R.string.settings_pin_placeholder)

    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        title = title,
        initialFocus = firstFocusRequester,
        modifier = Modifier.testTag(pinDialogTag()),
    ) {
        if (mode != PinDialogMode.Set) {
            VivicastTextField(
                value = currentPin,
                onValueChange = { currentPin = it.pinInput() },
                label = stringResource(R.string.settings_pin_label_current),
                placeholder = pinPlaceholder,
                secret = true,
                keyboardOptions = pinKeyboard,
                focusRequester = firstFocusRequester,
                fieldModifier = Modifier.testTag(pinCurrentFieldTag()),
            )
        }
        if (mode != PinDialogMode.Disable) {
            VivicastTextField(
                value = newPin,
                onValueChange = { newPin = it.pinInput() },
                label = stringResource(R.string.settings_pin_label_new),
                placeholder = pinPlaceholder,
                secret = true,
                keyboardOptions = pinKeyboard,
                focusRequester = if (mode == PinDialogMode.Set) firstFocusRequester else null,
                fieldModifier = Modifier.testTag(pinNewFieldTag()),
            )
            VivicastTextField(
                value = repeatPin,
                onValueChange = { repeatPin = it.pinInput() },
                label = stringResource(R.string.settings_pin_label_repeat),
                placeholder = pinPlaceholder,
                secret = true,
                keyboardOptions = pinKeyboard,
                fieldModifier = Modifier.testTag(pinRepeatFieldTag()),
            )
        }
        error?.let { BodyText(it, color = VivicastColors.Error, maxLines = 2) }
        VivicastDialogActions(
            primaryLabel = if (mode == PinDialogMode.Disable) stringResource(R.string.settings_pin_disable_value) else stringResource(R.string.common_save),
            onPrimary = {
                error = validatePinDialog(mode, currentPin, newPin, repeatPin, strPinCurrentRequired, strPinFourDigits, strPinMismatch)
                    ?: onConfirm(currentPin, newPin)
            },
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            primaryTestTag = pinConfirmTag(),
            secondaryTestTag = pinCancelTag(),
        )
    }
}

private fun validatePinDialog(
    mode: PinDialogMode,
    currentPin: String,
    newPin: String,
    repeatPin: String,
    msgCurrentRequired: String,
    msgFourDigits: String,
    msgMismatch: String,
): String? {
    if (mode != PinDialogMode.Set && currentPin.length != PIN_LENGTH) return msgCurrentRequired
    if (mode != PinDialogMode.Disable && newPin.length != PIN_LENGTH) return msgFourDigits
    if (mode != PinDialogMode.Disable && newPin != repeatPin) return msgMismatch
    return null
}

private fun String.pinInput(): String =
    filter(Char::isDigit).take(PIN_LENGTH)

fun pinDialogTag(): String = "pin-dialog"
fun pinCurrentFieldTag(): String = "pin-current"
fun pinNewFieldTag(): String = "pin-new"
fun pinRepeatFieldTag(): String = "pin-repeat"
fun pinCancelTag(): String = "pin-cancel"
fun pinConfirmTag(): String = "pin-confirm"

private const val PIN_LENGTH = 4

