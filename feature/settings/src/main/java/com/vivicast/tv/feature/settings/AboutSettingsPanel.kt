package com.vivicast.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.GlassPanel
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastBorders
import com.vivicast.tv.core.designsystem.VivicastCardSizes
import com.vivicast.tv.core.designsystem.VivicastColors
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
    exporting: Boolean = false,
    firstFocusModifier: Modifier = Modifier,
    // Bumped when OK is pressed on the already-selected rail section: collapse the open legal page back
    // to the About overview. Focus stays on the rail (which holds it), so no row-refocus is needed.
    collapseSubViewSignal: Int = 0,
) {
    val toggleDiagnostics = {
        onDiagnosticsSettingsChanged(
            diagnosticsSettingsState.copy(
                diagnosticsLoggingEnabled = !diagnosticsSettingsState.diagnosticsLoggingEnabled,
            ),
        )
    }
    var legalPage by remember { mutableStateOf<AboutLegalPage?>(null) }
    val activeLegalPage = legalPage
    val legalBackFocus = remember { FocusRequester() }
    val legalRowFocus = remember { FocusRequester() }
    val legalTermsRowFocus = remember { FocusRequester() }
    var showTechnical by remember { mutableStateOf(false) }
    val technicalRowFocus = remember { FocusRequester() }
    val technicalBackFocus = remember { FocusRequester() }
    val legalTitle = activeLegalPage?.let { stringResource(it.titleRes) }.orEmpty()
    val legalParagraphs = activeLegalPage?.let { stringResource(it.bodyRes).split("\n\n") }.orEmpty()
    // Close: move focus back to the row that OPENED the overlay FIRST, then drop the overlay. Removing the
    // overlay while it holds focus would reset focus to the top nav and navigate Home.
    val closeLegal = {
        val target = if (legalPage == AboutLegalPage.Terms) legalTermsRowFocus else legalRowFocus
        runCatching { target.requestFocus() }
        legalPage = null
    }
    // OK on the rail section collapses the open legal page to the overview. Focus is on the rail (not the
    // overlay), so unlike closeLegal this just drops the page without a row-refocus. No-op if none open.
    val closeTechnical = {
        runCatching { technicalRowFocus.requestFocus() }
        showTechnical = false
    }
    LaunchedEffect(collapseSubViewSignal) {
        if (legalPage != null) legalPage = null
        if (showTechnical) showTechnical = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Hidden (but kept composed, so the focused legal row survives) while a legal page is open — the
        // legal overlay renders transparently over it on the host GlassPanel background.
        LazyColumn(
            modifier = Modifier.alpha(if (activeLegalPage != null || showTechnical) 0f else 1f),
            verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
        ) {
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_app_version_title),
                help = stringResource(R.string.about_help_version),
                value = stringResource(R.string.about_version_build_format, state.appVersion, state.buildNumber),
                modifier = firstFocusModifier,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_technical_details),
                help = stringResource(R.string.about_help_technical_details),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier.focusRequester(technicalRowFocus),
                onClick = { showTechnical = true },
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
            VivicastSettingsRow(
                title = stringResource(R.string.about_export_diagnostics),
                help = stringResource(R.string.about_help_export_diagnostics),
                value = stringResource(if (exporting) R.string.about_exporting_diagnostics else R.string.common_export),
                modifier = Modifier,
                valueLoading = exporting,
                // Block a second tap (which would re-open the folder picker) while an export runs.
                onClick = { if (!exporting) onExportDiagnostics() },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_legal_privacy_title),
                help = stringResource(R.string.settings_help_legal_privacy),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier.focusRequester(legalRowFocus),
                onClick = { legalPage = AboutLegalPage.Privacy },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_legal_terms_title),
                help = stringResource(R.string.settings_help_legal_terms),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier.focusRequester(legalTermsRowFocus),
                onClick = { legalPage = AboutLegalPage.Terms },
            )
        }
        }

        // Legal document as a focus-capturing overlay: the About list stays composed underneath so the
        // focused row is not destroyed (destroying it resets focus to the top nav, which navigates on
        // focus and would jump to Home). The overlay covers the list and grabs focus on entry.
        if (activeLegalPage != null) {
            AboutLegalOverlay(
                title = legalTitle,
                paragraphs = legalParagraphs,
                backFocus = legalBackFocus,
                firstFocusModifier = firstFocusModifier,
                onClose = closeLegal,
            )
        }
        if (showTechnical) {
            TechnicalDetailsOverlay(
                state = state,
                backFocus = technicalBackFocus,
                firstFocusModifier = firstFocusModifier,
                onClose = closeTechnical,
            )
        }
    }
}

// Full-panel legal document, drawn over the (still-composed) About list so the focused list row is not
// destroyed. Paragraphs are focusable so the D-pad scrolls the LazyColumn through the full text.
@Composable
private fun AboutLegalOverlay(
    title: String,
    paragraphs: List<String>,
    backFocus: FocusRequester,
    firstFocusModifier: Modifier,
    onClose: () -> Unit,
) {
    // No visible back button: the system Back/Return key closes the overlay (BackHandler).
    BackHandler(onBack = onClose)
    LaunchedEffect(title) { runCatching { backFocus.requestFocus() } }
    // No own background: the About list underneath is hidden via alpha(0) (kept composed so the focused row
    // survives), so the legal text renders directly on the host GlassPanel — same fill + transparency, no
    // colour seam, and the panel's normal padding/border instead of a mismatched inner rectangle + stripes.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        item { SectionTitle(text = title) }
        itemsIndexed(paragraphs) { index, paragraph ->
            FocusableLegalParagraph(
                text = paragraph,
                // First paragraph is the focus entry: it carries the detail requester + entry focus and
                // blocks Up so focus can't escape to the top nav (which navigates on focus).
                modifier = if (index == 0) {
                    firstFocusModifier
                        .focusRequester(backFocus)
                        .focusProperties { up = FocusRequester.Cancel }
                } else {
                    Modifier
                },
            )
        }
    }
}

// Technical-details sub-page: mirrors AboutLegalOverlay (alpha-hides the About list, renders over the host
// GlassPanel, Back closes + refocuses the opening row). Read-only info rows; the first blocks Up so focus
// can't escape to the top nav (which navigates on focus).
@Composable
private fun TechnicalDetailsOverlay(
    state: AboutAppState,
    backFocus: FocusRequester,
    firstFocusModifier: Modifier,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        item { SectionTitle(text = stringResource(R.string.about_technical_details)) }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_package_name),
                help = stringResource(R.string.about_help_package),
                value = state.packageName,
                modifier = firstFocusModifier
                    .focusRequester(backFocus)
                    .focusProperties { up = FocusRequester.Cancel },
            )
        }
        item { VivicastSettingsRow(title = stringResource(R.string.about_build_type), help = stringResource(R.string.about_help_build_type), value = state.buildType) }
        item { VivicastSettingsRow(title = stringResource(R.string.about_player_engine), help = stringResource(R.string.about_help_player_engine), value = state.playerEngine) }
        item { VivicastSettingsRow(title = stringResource(R.string.about_db_version), help = stringResource(R.string.about_help_db), value = state.databaseVersion.toString()) }
        item { VivicastSettingsRow(title = stringResource(R.string.about_android_version), help = stringResource(R.string.about_help_android), value = state.androidVersion) }
        // CPU architecture folded into the device model in parens (e.g. "Xiaomi TV 4 (arm64-v8a)") — one
        // fewer row. cpuAbi stays a separate field for the diagnostics export.
        item { VivicastSettingsRow(title = stringResource(R.string.about_device_model), help = stringResource(R.string.about_help_device), value = stringResource(R.string.about_model_abi_format, state.deviceModel, state.cpuAbi)) }
    }
}

private enum class AboutLegalPage(
    @get:StringRes val titleRes: Int,
    @get:StringRes val bodyRes: Int,
) {
    Privacy(R.string.settings_legal_privacy_title, R.string.settings_legal_privacy_body),
    Terms(R.string.settings_legal_terms_title, R.string.settings_legal_terms_body),
}

// Inline legal document paragraph: focusable so the D-pad scrolls the LazyColumn through the full text.
@Composable
private fun FocusableLegalParagraph(text: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(
                color = if (focused) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                shape = VivicastShapes.CardRadius,
            )
            .padding(horizontal = VivicastSpacing.Space3, vertical = VivicastSpacing.Space2),
    ) {
        BodyText(text = text, maxLines = Int.MAX_VALUE, modifier = Modifier.fillMaxWidth())
    }
}

