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
internal fun AboutSettingsPanel(
    state: AboutAppState,
    diagnosticsSettingsState: DiagnosticsSettingsState,
    onDiagnosticsSettingsChanged: (DiagnosticsSettingsState) -> Unit,
    onExportDiagnostics: () -> Unit,
    onCopySupportInformation: () -> Unit,
    firstFocusModifier: Modifier = Modifier,
) {
    val strDaysSingular = stringResource(R.string.common_days_singular)
    val strDaysPlural = stringResource(R.string.common_days_plural)
    val toggleDiagnostics = {
        onDiagnosticsSettingsChanged(
            diagnosticsSettingsState.copy(
                diagnosticsLoggingEnabled = !diagnosticsSettingsState.diagnosticsLoggingEnabled,
            ),
        )
    }
    val decreaseRetention = {
        if (diagnosticsSettingsState.diagnosticsLoggingEnabled) {
            onDiagnosticsSettingsChanged(
                diagnosticsSettingsState.copy(retentionDays = (diagnosticsSettingsState.retentionDays - 1).coerceAtLeast(1)),
            )
        }
    }
    val increaseRetention = {
        if (diagnosticsSettingsState.diagnosticsLoggingEnabled) {
            onDiagnosticsSettingsChanged(
                diagnosticsSettingsState.copy(retentionDays = (diagnosticsSettingsState.retentionDays + 1).coerceAtMost(7)),
            )
        }
    }
    var legalPage by remember { mutableStateOf<AboutLegalPage?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3)) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_app_version_title),
                help = stringResource(R.string.about_help_version),
                value = state.appVersion,
                modifier = firstFocusModifier,
            )
        }
        item {
            VivicastSettingsRow(title = stringResource(R.string.about_package_name), help = stringResource(R.string.about_help_package), value = state.packageName)
        }
        item {
            VivicastSettingsRow(title = stringResource(R.string.about_db_version), help = stringResource(R.string.about_help_db), value = state.databaseVersion.toString())
        }
        item {
            VivicastSettingsRow(title = stringResource(R.string.about_android_version), help = stringResource(R.string.about_help_android), value = state.androidVersion)
        }
        item {
            VivicastSettingsRow(title = stringResource(R.string.about_device_model), help = stringResource(R.string.about_help_device), value = state.deviceModel)
        }
        item {
            InfoPanel(
                title = stringResource(R.string.about_diagnostics_section),
                body = stringResource(R.string.about_diagnostics_body),
                badge = stringResource(R.string.about_diagnostics_badge),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_diagnostics_logging),
                help = stringResource(R.string.about_help_logging),
                value = if (diagnosticsSettingsState.diagnosticsLoggingEnabled) stringResource(R.string.value_on) else stringResource(R.string.value_off),
                modifier = Modifier,
                onClick = toggleDiagnostics,
            )
        }
        item {
            val days = diagnosticsSettingsState.retentionDays.coerceIn(1, 7)
            AdjustableSettingsRow(
                title = stringResource(R.string.about_retention),
                help = if (diagnosticsSettingsState.diagnosticsLoggingEnabled) {
                    stringResource(R.string.about_help_retention_on)
                } else {
                    stringResource(R.string.about_help_retention_off)
                },
                value = if (days == 1) strDaysSingular.format(days) else strDaysPlural.format(days),
                onDecrease = decreaseRetention,
                onIncrease = increaseRetention,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_export_diagnostics),
                help = stringResource(R.string.about_help_export_diagnostics),
                value = stringResource(R.string.common_export),
                modifier = Modifier,
                onClick = onExportDiagnostics,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_copy_support),
                help = stringResource(R.string.about_help_copy_support),
                value = stringResource(R.string.common_copy),
                modifier = Modifier,
                onClick = onCopySupportInformation,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_legal),
                help = stringResource(R.string.about_help_legal),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier,
                onClick = { legalPage = AboutLegalPage.Licenses },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_privacy),
                help = stringResource(R.string.about_help_privacy),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier,
                onClick = { legalPage = AboutLegalPage.Privacy },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_third_party),
                help = stringResource(R.string.about_help_third_party),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier,
                onClick = { legalPage = AboutLegalPage.ThirdParty },
            )
        }
    }

    legalPage?.let { page ->
        AboutLegalDialog(page = page, onDismiss = { legalPage = null })
    }
}

private enum class AboutLegalPage(
    @get:StringRes val titleRes: Int,
    @get:StringRes val bodyRes: Int,
) {
    Licenses(R.string.about_legal_licenses_title, R.string.about_legal_licenses_body),
    Privacy(R.string.about_legal_privacy_title, R.string.about_legal_privacy_body),
    ThirdParty(R.string.about_legal_third_party_title, R.string.about_legal_third_party_body),
}

@Composable
private fun AboutLegalDialog(
    page: AboutLegalPage,
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onDismiss,
        width = VivicastDialogWidth.Standard,
        initialFocus = closeFocus,
    ) {
        InfoPanel(
            title = stringResource(page.titleRes),
            body = stringResource(page.bodyRes),
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastButtonRow {
            ActionPill(
                label = stringResource(R.string.common_close),
                modifier = Modifier.focusRequester(closeFocus),
                onClick = onDismiss,
            )
        }
    }
}

