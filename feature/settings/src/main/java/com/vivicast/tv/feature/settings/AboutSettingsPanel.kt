package com.vivicast.tv.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.SectionTitle
import com.vivicast.tv.core.designsystem.VivicastSettingsRow
import com.vivicast.tv.core.designsystem.VivicastShapes
import com.vivicast.tv.core.designsystem.VivicastSpacing
import androidx.compose.ui.res.stringResource
import com.vivicast.tv.core.designsystem.R
import kotlinx.coroutines.android.awaitFrame

// ---------------------------------------------------------------------------------------------------------
// About sub-views as self-contained inner-nav destination screens (mirrors the Playlists / EPG split). Split
// out of the former single AboutSettingsPanel: the old Box-stack + alpha-0 overlay scaffold (which kept the
// About list composed so a destroyed-focused-row couldn't jump to Home) + closeLegal/closeTechnical/
// closeDiagnostics refocus-opener machinery + the shared collapseSubViewSignal are replaced by real
// destinations with proper BACK and a nav-result focus-return (ABOUT_FOCUS_KEY, see SettingsRoute's
// AboutGraph wiring).
// ---------------------------------------------------------------------------------------------------------

/** Legal page keys — the [AboutLegal] route arg (String, no enum-NavType risk). Also the overview
 *  focus-return token so the correct Privacy-vs-Terms row is refocused on BACK. */
internal const val ABOUT_LEGAL_PRIVACY = "privacy"
internal const val ABOUT_LEGAL_TERMS = "terms"

/** Nav-result sentinels: which overview row to focus after a sub-view pops (legal uses its page key). */
internal const val ABOUT_FOCUS_TECHNICAL = "__about_technical__"
internal const val ABOUT_FOCUS_DIAGNOSTICS = "__about_diagnostics__"

@Composable
internal fun AboutOverviewScreen(
    state: AboutAppState,
    onOpenTechnical: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLegal: (pageKey: String) -> Unit,
    // Focus-return target when popping back from a sub-view (null on a fresh section entry). A sentinel or a
    // legal page key => the matching row.
    pendingFocusToken: String?,
    onFocusHandled: () -> Unit,
    firstFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val technicalRowFocus = remember { FocusRequester() }
    val diagnosticsRowFocus = remember { FocusRequester() }
    val legalRowFocus = remember { FocusRequester() }
    val legalTermsRowFocus = remember { FocusRequester() }
    // Return focus onto the row that opened the sub-view, else it escapes to the top nav (which navigates on
    // focus → Home). All five rows are always composed (no scroll), so the first attempt lands; the retry is
    // frame-timing safety, mirroring the other overviews.
    LaunchedEffect(pendingFocusToken) {
        val token = pendingFocusToken ?: return@LaunchedEffect
        val requester = when (token) {
            ABOUT_FOCUS_TECHNICAL -> technicalRowFocus
            ABOUT_FOCUS_DIAGNOSTICS -> diagnosticsRowFocus
            ABOUT_LEGAL_TERMS -> legalTermsRowFocus
            ABOUT_LEGAL_PRIVACY -> legalRowFocus
            else -> null
        }
        repeat(30) {
            awaitFrame()
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                onFocusHandled()
                return@LaunchedEffect
            }
        }
        runCatching { technicalRowFocus.requestFocus() }
        onFocusHandled()
    }
    SettingsDetailList(modifier = modifier) {
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
                onClick = onOpenTechnical,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.about_diagnostics_section),
                help = stringResource(R.string.about_help_diagnostics_section),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier.focusRequester(diagnosticsRowFocus),
                onClick = onOpenDiagnostics,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_legal_privacy_title),
                help = stringResource(R.string.settings_help_legal_privacy),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier.focusRequester(legalRowFocus),
                onClick = { onOpenLegal(ABOUT_LEGAL_PRIVACY) },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_legal_terms_title),
                help = stringResource(R.string.settings_help_legal_terms),
                value = stringResource(R.string.about_open_value),
                modifier = Modifier.focusRequester(legalTermsRowFocus),
                onClick = { onOpenLegal(ABOUT_LEGAL_TERMS) },
            )
        }
    }
}

// Full-panel legal document. Paragraphs are focusable so the D-pad scrolls through the full text; the first
// carries the entry requester (rail RIGHT re-entry + nav-in focus) and blocks Up so focus can't escape to
// the top nav (which navigates on focus → Home). A scrollable Column (not a LazyColumn) so the first
// paragraph stays composed even scrolled off.
@Composable
internal fun AboutLegalScreen(
    pageKey: String,
    entryFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val page = if (pageKey == ABOUT_LEGAL_TERMS) AboutLegalPage.Terms else AboutLegalPage.Privacy
    val title = stringResource(page.titleRes)
    val paragraphs = stringResource(page.bodyRes).split("\n\n")
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(pageKey) {
        awaitFrame()
        runCatching { firstFocus.requestFocus() }
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        SectionTitle(text = title)
        paragraphs.forEachIndexed { index, paragraph ->
            FocusableLegalParagraph(
                text = paragraph,
                modifier = if (index == 0) {
                    entryFocusModifier
                        .focusRequester(firstFocus)
                        .focusProperties { up = FocusRequester.Cancel }
                } else {
                    Modifier
                },
            )
        }
    }
}

// Technical-details sub-page: read-only info rows. A scrollable Column (not LazyColumn) so the first row —
// which holds the entry requester — stays composed even scrolled off; it blocks Up so focus can't escape to
// the top nav (which navigates on focus → Home).
@Composable
internal fun AboutTechnicalScreen(
    state: AboutAppState,
    entryFocusModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { firstFocus.requestFocus() }
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
    ) {
        SectionTitle(text = stringResource(R.string.about_technical_details))
        VivicastSettingsRow(
            title = stringResource(R.string.about_package_name),
            help = stringResource(R.string.about_help_package),
            value = state.packageName,
            modifier = entryFocusModifier
                .focusRequester(firstFocus)
                .focusProperties { up = FocusRequester.Cancel },
        )
        VivicastSettingsRow(title = stringResource(R.string.about_build_type), help = stringResource(R.string.about_help_build_type), value = state.buildType)
        VivicastSettingsRow(title = stringResource(R.string.about_player_engine), help = stringResource(R.string.about_help_player_engine), value = state.playerEngine)
        VivicastSettingsRow(title = stringResource(R.string.about_db_version), help = stringResource(R.string.about_help_db), value = state.databaseVersion.toString())
        VivicastSettingsRow(title = stringResource(R.string.about_android_version), help = stringResource(R.string.about_help_android), value = state.androidVersion)
        // CPU architecture folded into the device model in parens (e.g. "Xiaomi TV 4 (arm64-v8a)") — one
        // fewer row. cpuAbi stays a separate field for the diagnostics export.
        VivicastSettingsRow(title = stringResource(R.string.about_device_model), help = stringResource(R.string.about_help_device), value = stringResource(R.string.about_model_abi_format, state.deviceModel, state.cpuAbi))
    }
}

internal enum class AboutLegalPage(
    @get:StringRes val titleRes: Int,
    @get:StringRes val bodyRes: Int,
) {
    Privacy(R.string.settings_legal_privacy_title, R.string.settings_legal_privacy_body),
    Terms(R.string.settings_legal_terms_title, R.string.settings_legal_terms_body),
}

// Inline legal document paragraph: focusable so the D-pad scrolls the Column through the full text.
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
