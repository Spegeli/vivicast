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
private fun SettingsThemeMode.label(): String = when (this) {
    SettingsThemeMode.StandardDark -> stringResource(R.string.theme_dark)
    SettingsThemeMode.HighContrastDark -> stringResource(R.string.theme_dark_contrast)
    SettingsThemeMode.AmoledDark -> stringResource(R.string.theme_amoled)
}

@Composable
private fun SettingsAccentColor.label(): String = when (this) {
    SettingsAccentColor.Blue -> stringResource(R.string.accent_blue)
}

@Composable
private fun SettingsTransparency.label(): String = when (this) {
    SettingsTransparency.Percent0 -> "0 %"
    SettingsTransparency.Percent25 -> "25 %"
    SettingsTransparency.Percent50 -> "50 %"
}

@Composable
private fun SettingsFontScale.label(): String = when (this) {
    SettingsFontScale.Small -> stringResource(R.string.font_small)
    SettingsFontScale.Medium -> stringResource(R.string.font_medium)
    SettingsFontScale.Large -> stringResource(R.string.font_large)
    SettingsFontScale.ExtraLarge -> stringResource(R.string.font_very_large)
}

@Composable
private fun SettingsAnimationSpeed.label(): String = when (this) {
    SettingsAnimationSpeed.Off -> stringResource(R.string.value_off)
    SettingsAnimationSpeed.Fast -> stringResource(R.string.anim_fast)
    SettingsAnimationSpeed.Normal -> stringResource(R.string.anim_normal)
    SettingsAnimationSpeed.Slow -> stringResource(R.string.anim_slow)
}

private enum class AppearancePicker { Theme, Accent, Transparency, Font, Animation }

@Composable
internal fun AppearanceSettingsPanel(
    state: AppearanceSettingsState = AppearanceSettingsState(),
    onAppearanceSettingsChanged: (AppearanceSettingsState) -> Unit = {},
    firstFocusModifier: Modifier = Modifier,
) {
    var openPicker by remember { mutableStateOf<AppearancePicker?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_theme),
                help = stringResource(R.string.settings_help_theme),
                value = state.themeMode.label(),
                modifier = firstFocusModifier,
                icon = { SettingsRowIcon("moon") },
                onClick = { openPicker = AppearancePicker.Theme },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_accent_color),
                help = stringResource(R.string.settings_help_accent),
                value = state.accentColor.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("palette") },
                onClick = { openPicker = AppearancePicker.Accent },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_transparency),
                help = stringResource(R.string.settings_help_transparency),
                value = state.transparency.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("transparency") },
                onClick = { openPicker = AppearancePicker.Transparency },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_font_size),
                help = stringResource(R.string.settings_help_font_scale),
                value = state.fontScale.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("font") },
                onClick = { openPicker = AppearancePicker.Font },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_animations),
                help = stringResource(R.string.settings_help_animation),
                value = state.animationSpeed.label(),
                modifier = Modifier,
                icon = { SettingsRowIcon("animation") },
                onClick = { openPicker = AppearancePicker.Animation },
            )
        }
    }

    val dismiss = { openPicker = null }
    when (openPicker) {
        AppearancePicker.Theme -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = SettingsThemeMode.entries,
            selected = state.themeMode,
            label = { it.label() },
            onSelect = { onAppearanceSettingsChanged(state.copy(themeMode = it)); dismiss() },
            onDismiss = dismiss,
        )
        AppearancePicker.Accent -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_accent_color),
            options = SettingsAccentColor.entries,
            selected = state.accentColor,
            label = { it.label() },
            onSelect = { onAppearanceSettingsChanged(state.copy(accentColor = it)); dismiss() },
            onDismiss = dismiss,
        )
        AppearancePicker.Transparency -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_transparency),
            options = SettingsTransparency.entries,
            selected = state.transparency,
            label = { it.label() },
            onSelect = { onAppearanceSettingsChanged(state.copy(transparency = it)); dismiss() },
            onDismiss = dismiss,
        )
        AppearancePicker.Font -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_font_size),
            options = SettingsFontScale.entries,
            selected = state.fontScale,
            label = { it.label() },
            onSelect = { onAppearanceSettingsChanged(state.copy(fontScale = it)); dismiss() },
            onDismiss = dismiss,
        )
        AppearancePicker.Animation -> SettingsChoiceDialog(
            title = stringResource(R.string.settings_animations),
            options = SettingsAnimationSpeed.entries,
            selected = state.animationSpeed,
            label = { it.label() },
            onSelect = { onAppearanceSettingsChanged(state.copy(animationSpeed = it)); dismiss() },
            onDismiss = dismiss,
        )
        null -> Unit
    }
}

@Composable
private fun PlaceholderSettingsPanel(
    title: String,
    body: String,
    firstFocusModifier: Modifier = Modifier,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            InfoPanel(title = title, body = body, modifier = firstFocusModifier.fillMaxWidth())
        }
    }
}

