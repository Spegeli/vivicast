package com.vivicast.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusEnterExitScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivicast.tv.core.cache.MediaCacheStore
import com.vivicast.tv.core.datastore.UserPreferencesStore
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
import com.vivicast.tv.core.designsystem.LocalVivicastColors
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastFocusSurface
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
import com.vivicast.tv.data.epg.EpgConnectionTestResult
import com.vivicast.tv.data.epg.EpgSourceEditRequest
import com.vivicast.tv.data.epg.EpgSourceRepository
import com.vivicast.tv.data.epg.ManualEpgChannelMappingRequest
import com.vivicast.tv.domain.model.Channel
import com.vivicast.tv.domain.model.EpgChannelMapping
import com.vivicast.tv.data.provider.DEFAULT_REFRESH_INTERVAL_HOURS
import com.vivicast.tv.data.provider.MAX_M3U_INLINE_SOURCE_CHARS
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.data.provider.ProviderCredentials
import com.vivicast.tv.data.provider.ProviderConnectionTestResult
import com.vivicast.tv.data.provider.ProviderCreateRequest
import com.vivicast.tv.data.media.CategoryGroupRepository
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
private fun settingsSectionsList() = listOf(
    stringResource(R.string.settings_section_general),
    stringResource(R.string.settings_section_playlists),
    stringResource(R.string.settings_section_epg),
    stringResource(R.string.settings_section_appearance),
    stringResource(R.string.settings_section_playback),
    stringResource(R.string.settings_section_parental),
    stringResource(R.string.settings_section_cache),
    stringResource(R.string.settings_section_backup),
    stringResource(R.string.settings_section_about),
)

// Rail item OK: re-selecting the current section collapses its open sub-view; a different section
// switches. Kept top-level so the branch doesn't count toward SettingsRoute's complexity gate.
private fun railSectionActivate(
    section: String,
    selectedSection: String,
    onCollapse: () -> Unit,
    onSwitch: (String) -> Unit,
) {
    if (section == selectedSection) onCollapse() else onSwitch(section)
}

// Right detail panel focus bounds: LEFT (at the left edge) returns to the current section; UP/DOWN and
// RIGHT stop at the ends (the detail panel is the rightmost pane, so RIGHT must not escape upward to the
// top-nav gear). Only fires when there's no focus target left inside the group, so internal navigation
// (rows, button groups) is unaffected.
private fun FocusEnterExitScope.exitDetailPanel(section: FocusRequester) {
    when (requestedFocusDirection) {
        FocusDirection.Left -> section.requestFocus()
        FocusDirection.Up, FocusDirection.Down, FocusDirection.Right -> cancelFocusChange()
        else -> {}
    }
}

// Left section rail focus bounds: LEFT stops (leftmost pane); UP from the top section exits to the
// top-nav gear. RIGHT (→ detail) and internal UP/DOWN between sections are unaffected.
private fun FocusEnterExitScope.exitSectionRail(topNav: FocusRequester) {
    when (requestedFocusDirection) {
        FocusDirection.Left -> cancelFocusChange()
        FocusDirection.Up -> topNav.requestFocus()
        else -> {}
    }
}

@Composable
fun SettingsRoute(
    providerRepository: ProviderRepository,
    epgSourceRepository: EpgSourceRepository,
    categoryGroupRepository: CategoryGroupRepository,
    userPreferencesStore: UserPreferencesStore,
    mediaCacheStore: MediaCacheStore,
    parentalControlSettingsState: ParentalControlSettingsState = ParentalControlSettingsState(),
    aboutAppState: AboutAppState,
    localLogoFolder: String? = null,
    onPickLogoFolder: () -> Unit = {},
    onRescanLogos: () -> Unit = {},
    rescanningLogos: Boolean = false,
    onRemoveLogoFolder: () -> Unit = {},
    topNavFocusRequester: FocusRequester,
    initialSelectedSection: String? = null,
    focusLanguageRowOnEnter: Boolean = false,
    onInitialLanguageFocusApplied: () -> Unit = {},
    onTestProviderConnection: suspend (ProviderCreateRequest) -> ProviderConnectionTestResult,
    onTestEpgConnection: suspend (String) -> EpgConnectionTestResult = { EpgConnectionTestResult(null, null) },
    onPickM3uFile: ((String, String) -> Unit) -> Unit = {},
    onProviderSaved: (String) -> Unit,
    onRefreshProvider: (String) -> Unit = {},
    // App-hoisted: after an Xtream provider save with a changed source, auto-detect its xmltv.php EPG.
    onXtreamProviderSaved: (String) -> Unit = {},
    onLogProviderSaved: (descriptor: String, switchedFromType: String?) -> Unit = { _, _ -> },
    // App-layer diagnostics loggers (feature/VM never touch DiagnosticsStore). Provider id / category id go
    // under a "target" key — both are opaque (random-UUID provider id; category id = "<providerId>:...:<hash>"),
    // carrying no name/URL/credential, so they are safe to log raw; type/mode/policy/count are plain enums.
    onLogProviderDeleted: (providerId: String) -> Unit = {},
    onLogGroupEvent: (message: String, details: Map<String, String>) -> Unit = { _, _ -> },
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    onLanguageChanged: (SettingsLanguage) -> Unit = {},
    onSetPin: (String) -> String? = { null },
    onChangePin: (String, String) -> String? = { _, _ -> null },
    onDisablePin: (String) -> String? = { null },
    onProtectionChanged: (ParentalProtectionArea, Boolean) -> String? = { _, _ -> null },
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onDiagnosticsSettingsChanged: (DiagnosticsSettingsState) -> Unit = {},
    onExportDiagnostics: () -> Unit = {},
    onDeleteLogs: suspend (Set<DiagnosticsLogKind>) -> Unit = {},
    onReadLog: suspend (DiagnosticsLogKind) -> String? = { null },
    diagnosticsExporting: Boolean = false,
    onRefreshEpgSource: (sourceId: String) -> Unit,
    onClearHistory: suspend (Set<HistoryClearTarget>) -> Unit,
    imageCacheSizeBytes: suspend () -> Long = { 0L },
    clearImageCache: suspend () -> Unit = {},
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(
            userPreferencesStore,
            mediaCacheStore,
            epgSourceRepository,
            providerRepository,
            categoryGroupRepository,
            imageCacheSizeBytes,
            clearImageCache,
        ),
    )
    val settingsUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val routeScope = rememberCoroutineScope()
    val settingsSections = settingsSectionsList()
    val mainSections = remember(settingsSections) { settingsSections.dropLast(1) }
    val sectionGeneral = stringResource(R.string.settings_section_general)
    val sectionPlaylists = stringResource(R.string.settings_section_playlists)
    val sectionEpg = stringResource(R.string.settings_section_epg)
    val sectionAppearance = stringResource(R.string.settings_section_appearance)
    val sectionPlayback = stringResource(R.string.settings_section_playback)
    val sectionParental = stringResource(R.string.settings_section_parental)
    val sectionCache = stringResource(R.string.settings_section_cache)
    val sectionBackup = stringResource(R.string.settings_section_backup)
    val sectionAbout = stringResource(R.string.settings_section_about)
    var selectedSection by remember(initialSelectedSection) {
        mutableStateOf(initialSelectedSection?.takeIf { it in settingsSections } ?: sectionGeneral)
    }
    val detailFocusRequester = remember { FocusRequester() }
    var pendingDetailFocus by remember { mutableStateOf(false) }
    // Bumped when OK is pressed on the already-selected section: signals the detail panel to collapse
    // any open sub-view (editor / global settings / legal page) back to its overview. Focus stays on
    // the rail (the collapse touches only the right pane). See settings-ok-collapses-subview plan.
    var collapseSubViewSignal by remember { mutableStateOf(0) }
    // True while focus is inside the right detail panel — gates the settings-scoped BACK (detail → rail).
    var detailFocused by remember { mutableStateOf(false) }
    val sectionFocusRequesters = remember(settingsSections) {
        settingsSections.associateWith { FocusRequester() }
    }
    val selectedSectionFocusRequester = sectionFocusRequesters[selectedSection] ?: FocusRequester.Default
    val detailFirstFocusModifier = Modifier
        .focusRequester(detailFocusRequester)
        .focusProperties { left = selectedSectionFocusRequester }
    val selectSection: (String) -> Unit = { section ->
        // Section selection is local UI state only; Settings always reopens on Allgemein.
        selectedSection = section
    }
    // OK on a rail section: re-selecting the current one collapses its open sub-view (focus stays on the
    // rail); a different one switches and moves focus into the detail. Shared by every rail item. The
    // branch lives in the top-level railSectionActivate to keep SettingsRoute under the complexity gate.
    val activateSection: (String) -> Unit = { section ->
        railSectionActivate(
            section = section,
            selectedSection = selectedSection,
            onCollapse = { collapseSubViewSignal++ },
            onSwitch = { selectSection(it); pendingDetailFocus = true },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.onReloadCacheStats()
        awaitFrame()
        if (focusLanguageRowOnEnter && selectedSection == sectionGeneral) {
            // Post language-change entry: land on the language row (detailFirstFocusModifier is moved
            // onto it below) instead of the section rail.
            detailFocusRequester.requestFocus()
            onInitialLanguageFocusApplied()
        } else {
            selectedSectionFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(selectedSection, pendingDetailFocus) {
        if (pendingDetailFocus) {
            detailFocusRequester.requestFocus()
            pendingDetailFocus = false
        }
    }

    // Settings-scoped BACK: from the detail panel, return to the current section in the rail first;
    // only a further BACK from the rail falls through to the global handler (→ top-nav gear). Inline
    // editors/overlays carry their own (more-nested) BackHandler, so they still close first.
    BackHandler(enabled = detailFocused) {
        selectedSectionFocusRequester.requestFocus()
    }

    VivicastScreen(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space4),
            modifier = Modifier.fillMaxSize(),
        ) {
            GlassPanel(
                modifier = Modifier.weight(0.26f).fillMaxSize(),
                contentPadding = VivicastSpacing.Space3,
            ) {
                Column(
                    // Rail focus bounds: LEFT stops (leftmost pane); UP from the top section exits to the
                    // top-nav gear. RIGHT (→ detail) is a spatial search into the detail focusRestorer;
                    // BACK (→ gear) stays global.
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup()
                        .focusProperties {
                            onExit = { exitSectionRail(topNavFocusRequester) }
                        },
                    verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                ) {
                    SettingsPanelTitle(stringResource(R.string.settings_title))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1),
                        contentPadding = PaddingValues(top = VivicastSpacing.Space2),
                    ) {
                        items(mainSections) { section ->
                            FocusPanel(
                                selected = section == selectedSection,
                                onClick = { activateSection(section) },
                                onFocused = { selectSection(section) },
                                // RIGHT → detail: no hard target. A spatial search enters the detail
                                // focusGroup, whose focusRestorer lands on the last-focused (visible) row
                                // — so RIGHT still works when the first row is scrolled off. See below.
                                modifier = Modifier
                                    .focusRequester(sectionFocusRequesters.getValue(section))
                                    .fillMaxWidth(),
                                contentPadding = VivicastSpacing.Space2,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingsSectionIcon(section = section, selected = section == selectedSection)
                                    BasicText(
                                        text = section,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                                    )
                                }
                            }
                        }
                    }
                    FocusPanel(
                        selected = sectionAbout == selectedSection,
                        onClick = { activateSection(sectionAbout) },
                        onFocused = { selectSection(sectionAbout) },
                        modifier = Modifier
                            .focusRequester(sectionFocusRequesters.getValue(sectionAbout))
                            .fillMaxWidth()
                            .wrapContentHeight(unbounded = true),
                        contentPadding = VivicastSpacing.Space2,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingsSectionIcon(section = sectionAbout, selected = sectionAbout == selectedSection)
                            BasicText(
                                text = sectionAbout,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                            )
                        }
                    }
                }
            }

            GlassPanel(
                modifier = Modifier.weight(0.78f).fillMaxSize(),
                contentPadding = VivicastSpacing.Space4,
            ) {
                Column(
                    // Detail focus bounds: LEFT (at the panel's left edge) returns to the current section;
                    // UP/DOWN stop at the ends. Internal navigation (rows, button groups) is unaffected —
                    // an exit target only applies when there's no focus target left inside the group.
                    // focusRestorer: on RIGHT re-entry restore the last-focused child (so it works when the
                    // first row is scrolled out of the LazyColumn); fallback = the first row on first entry.
                    modifier = Modifier
                        .focusRestorer(detailFocusRequester)
                        .focusGroup()
                        .onFocusChanged { detailFocused = it.hasFocus }
                        .focusProperties {
                            onExit = { exitDetailPanel(selectedSectionFocusRequester) }
                        },
                    verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
                ) {
                    SettingsPanelTitle(selectedSection)
                    when (selectedSection) {
                        sectionGeneral -> GeneralSettingsPanel(
                            state = settingsUiState.general,
                            onLaunchOnBootChanged = viewModel::onLaunchOnBootChanged,
                            onDoubleBackToExitChanged = viewModel::onDoubleBackToExitChanged,
                            onBackgroundRefreshChanged = { enabled ->
                                viewModel.onBackgroundRefreshChanged(enabled)
                                onBackgroundRefreshChanged(enabled)
                            },
                            onResumeLastChannelChanged = viewModel::onResumeLastChannelChanged,
                            onLanguageChanged = { language ->
                                viewModel.onLanguageChanged(language)
                                onLanguageChanged(language)
                            },
                            onGlobalUserAgentChanged = viewModel::onGlobalUserAgentChanged,
                            firstFocusModifier = detailFirstFocusModifier,
                            focusLanguageInsteadOfFirst = focusLanguageRowOnEnter,
                        )
                        sectionPlaylists -> ProviderSettingsPanel(
                            providers = settingsUiState.providers,
                            onGetProviderCredentials = viewModel::getProviderCredentials,
                            onGetProviderM3uContent = viewModel::getProviderM3uInlineContent,
                            onCreateProvider = viewModel::createProvider,
                            onUpdateProvider = viewModel::updateProvider,
                            onSetProviderEnabled = viewModel::setProviderEnabled,
                            onDeleteProvider = { id ->
                                viewModel.deleteProvider(id).also { if (it.isSuccess) onLogProviderDeleted(id) }
                            },
                            onTestProviderConnection = onTestProviderConnection,
                            onPickM3uFile = onPickM3uFile,
                            onProviderSaved = onProviderSaved,
                            onRefreshProvider = onRefreshProvider,
                            onXtreamProviderSaved = onXtreamProviderSaved,
                            groupsControls = ProviderGroupsControls(
                                activeType = settingsUiState.manageGroupsType,
                                groups = settingsUiState.manageGroups,
                                settings = settingsUiState.manageGroupSettings,
                                onOpen = viewModel::onManageGroups,
                                onClose = viewModel::onCloseManageGroups,
                                onSelectType = viewModel::onManageGroupsTypeSelected,
                                onToggleHidden = { categoryId, hidden ->
                                    onLogGroupEvent(if (hidden) "group_hidden" else "group_shown", mapOf("target" to categoryId))
                                    routeScope.launch { viewModel.setGroupHidden(categoryId, hidden) }
                                },
                                onSetAllHidden = { providerId, type, hidden ->
                                    onLogGroupEvent(
                                        if (hidden) "groups_bulk_hidden" else "groups_bulk_shown",
                                        mapOf("type" to type.name, "count" to settingsUiState.manageGroups.size.toString()),
                                    )
                                    routeScope.launch { viewModel.setAllGroupsHidden(providerId, type, hidden) }
                                },
                                onReorder = { ids ->
                                    onLogGroupEvent(
                                        "group_reordered",
                                        mapOf("type" to settingsUiState.manageGroupsType.name, "count" to ids.size.toString()),
                                    )
                                    routeScope.launch { viewModel.reorderGroups(ids) }
                                },
                                onResetOrder = { providerId, type ->
                                    onLogGroupEvent("group_order_reset", mapOf("type" to type.name))
                                    routeScope.launch { viewModel.resetGroupOrder(providerId, type) }
                                },
                                onSetSortMode = { providerId, type, mode ->
                                    onLogGroupEvent("sort_mode_changed", mapOf("type" to type.name, "mode" to mode.name))
                                    routeScope.launch { viewModel.setGroupSortMode(providerId, type, mode) }
                                },
                                onSetHideNewGroups = { providerId, type, hidden ->
                                    onLogGroupEvent(
                                        "new_groups_policy_changed",
                                        mapOf("type" to type.name, "policy" to if (hidden) "hidden" else "shown"),
                                    )
                                    routeScope.launch { viewModel.setHideNewGroups(providerId, type, hidden) }
                                },
                            ),
                            onLogProviderSaved = onLogProviderSaved,
                            epgSources = settingsUiState.epgSources,
                            providerEpgLinks = settingsUiState.providerEpgLinks,
                            onSelectEpgProvider = viewModel::onEpgProviderSelected,
                            onToggleEpgLink = { providerId, sourceId, link ->
                                routeScope.launch {
                                    if (link) {
                                        val priority = (settingsUiState.providerEpgLinks.maxOfOrNull { it.priority } ?: 0) + 1
                                        viewModel.linkEpgSourceToProvider(providerId, sourceId, priority)
                                    } else {
                                        viewModel.unlinkEpgSourceFromProvider(providerId, sourceId)
                                    }
                                }
                            },
                            firstFocusModifier = detailFirstFocusModifier,
                            // Park focus on the (always-present) section button before the overview
                            // swaps to the inline editor, so focus can't escape to the top nav bar.
                            onParkFocusBeforeEditor = {
                                runCatching { sectionFocusRequesters.getValue(sectionPlaylists).requestFocus() }
                            },
                            collapseSubViewSignal = collapseSubViewSignal,
                        )
                        sectionEpg -> EpgSettingsPanel(
                            state = settingsUiState.epg,
                            sources = settingsUiState.epgSources,
                            providers = settingsUiState.providers,
                            selectedProviderId = settingsUiState.selectedEpgProviderId,
                            providerLinks = settingsUiState.providerEpgLinks,
                            manualMappingChannels = settingsUiState.manualMappingChannels,
                            manualMappings = settingsUiState.manualMappingsForSelectedChannel,
                            selectedManualMappingChannelId = settingsUiState.selectedManualMappingChannelId,
                            onEpgPreferencesChanged = viewModel::onEpgSettingsChanged,
                            onRefreshEpgSource = onRefreshEpgSource,
                            onSelectProvider = viewModel::onEpgProviderSelected,
                            onSaveEpgSource = viewModel::saveEpgSource,
                            onDeleteEpgSource = viewModel::deleteEpgSource,
                            onSelectManualMappingChannel = viewModel::onManualMappingChannelSelected,
                            onResetManualMappingChannel = viewModel::onManualMappingReset,
                            onSetManualMapping = viewModel::setManualChannelMapping,
                            onClearManualMapping = viewModel::clearManualChannelMapping,
                            onGetEpgSourceUrl = viewModel::getEpgSourceUrl,
                            onTestEpgConnection = onTestEpgConnection,
                            firstFocusModifier = detailFirstFocusModifier,
                            // Park focus on the (always-present) section button before the overview swaps
                            // to the inline editor, so focus can't escape to the top nav bar (jumps Home).
                            onParkFocusBeforeEditor = {
                                runCatching { sectionFocusRequesters.getValue(sectionEpg).requestFocus() }
                            },
                            collapseSubViewSignal = collapseSubViewSignal,
                        )
                        sectionAppearance -> AppearanceSettingsPanel(
                            state = settingsUiState.appearance,
                            onAppearanceSettingsChanged = viewModel::onAppearanceSettingsChanged,
                            localLogoFolder = localLogoFolder,
                            onPickLogoFolder = onPickLogoFolder,
                            onRescanLogos = onRescanLogos,
                            rescanningLogos = rescanningLogos,
                            onRemoveLogoFolder = onRemoveLogoFolder,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionPlayback -> PlaybackSettingsPanel(
                            state = settingsUiState.playback,
                            onPlaybackPreferencesChanged = viewModel::onPlaybackSettingsChanged,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionParental -> ParentalControlSettingsPanel(
                            state = parentalControlSettingsState,
                            onSetPin = onSetPin,
                            onChangePin = onChangePin,
                            onDisablePin = onDisablePin,
                            onProtectionChanged = onProtectionChanged,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionCache -> MaintenanceSettingsPanel(
                            cacheSettingsState = settingsUiState.cache,
                            onClearCache = viewModel::onClearCache,
                            onClearHistory = onClearHistory,
                            onReloadCacheStats = viewModel::onReloadCacheStats,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionBackup -> BackupSettingsPanel(
                            onExportBackup = onExportBackup,
                            onImportBackup = onImportBackup,
                            firstFocusModifier = detailFirstFocusModifier,
                        )
                        sectionAbout -> AboutSettingsPanel(
                            state = aboutAppState,
                            diagnosticsSettingsState = settingsUiState.diagnostics,
                            onDiagnosticsSettingsChanged = { diagnostics ->
                                viewModel.onDiagnosticsSettingsChanged(diagnostics)
                                onDiagnosticsSettingsChanged(diagnostics)
                            },
                            onExportDiagnostics = onExportDiagnostics,
                            onDeleteLogs = onDeleteLogs,
                            onReadLog = onReadLog,
                            exporting = diagnosticsExporting,
                            firstFocusModifier = detailFirstFocusModifier,
                            collapseSubViewSignal = collapseSubViewSignal,
                        )
                        else -> InfoPanel(
                            title = selectedSection,
                            body = stringResource(R.string.settings_no_options),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun SettingsPanelTitle(text: String) {
    BasicText(
        text = text.uppercase(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = VivicastTypography.LabelSmall.copy(color = VivicastColors.TextSecondary),
    )
}

@Composable
private fun SettingsSectionIcon(section: String, selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) LocalVivicastColors.current.accentSoft else VivicastColors.TextSecondary
    val sGeneral = stringResource(R.string.settings_section_general)
    val sPlaylists = stringResource(R.string.settings_section_playlists)
    val sEpg = stringResource(R.string.settings_section_epg)
    val sAppearance = stringResource(R.string.settings_section_appearance)
    val sPlayback = stringResource(R.string.settings_section_playback)
    val sCache = stringResource(R.string.settings_section_cache)
    val sBackup = stringResource(R.string.settings_section_backup)
    val sParental = stringResource(R.string.settings_section_parental)
    val sAbout = stringResource(R.string.settings_section_about)
    Canvas(modifier = modifier.size(16.dp)) {
        val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (section) {
            sGeneral -> {
                // Horizontal sliders icon (3 adjustable controls)
                val xPositions = listOf(0.38f, 0.62f, 0.46f)
                listOf(0.26f, 0.50f, 0.74f).forEachIndexed { i, y ->
                    drawLine(color, Offset(w * 0.12f, h * y), Offset(w * 0.88f, h * y), strokeWidth = 2f, cap = StrokeCap.Round)
                    drawCircle(color, radius = w * 0.08f, center = Offset(w * xPositions[i], h * y), style = stroke)
                }
            }
            sPlaylists -> {
                repeat(3) { i ->
                    val y = h * (0.26f + i * 0.24f)
                    drawLine(color, Offset(w * 0.20f, y), Offset(w * 0.80f, y), strokeWidth = 2f, cap = StrokeCap.Round)
                }
            }
            sEpg -> {
                // Calendar: outer rect, header bar, tick marks, two content lines
                drawRect(color, topLeft = Offset(w * 0.16f, h * 0.22f), size = Size(w * 0.68f, h * 0.58f), style = stroke)
                drawLine(color, Offset(w * 0.16f, h * 0.38f), Offset(w * 0.84f, h * 0.38f), strokeWidth = 2f)
                drawLine(color, Offset(w * 0.32f, h * 0.14f), Offset(w * 0.32f, h * 0.30f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.68f, h * 0.14f), Offset(w * 0.68f, h * 0.30f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.28f, h * 0.52f), Offset(w * 0.54f, h * 0.52f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.28f, h * 0.64f), Offset(w * 0.48f, h * 0.64f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            sAppearance -> {
                // Palette: circle with four color dots
                drawCircle(color, radius = w * 0.30f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.36f, h * 0.34f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.64f, h * 0.34f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.34f, h * 0.62f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.64f, h * 0.62f))
            }
            sPlayback -> {
                // Play triangle
                val p = Path().apply {
                    moveTo(w * 0.28f, h * 0.22f); lineTo(w * 0.76f, h * 0.50f); lineTo(w * 0.28f, h * 0.78f); close()
                }
                drawPath(p, color, style = stroke)
            }
            sCache -> {
                // Clock
                drawCircle(color, radius = w * 0.30f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.26f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.68f, h * 0.58f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            sBackup -> {
                // Cloud with upload arrow
                drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.36f, h * 0.50f), style = stroke)
                drawCircle(color, radius = w * 0.20f, center = Offset(w * 0.56f, h * 0.44f), style = stroke)
                drawLine(color, Offset(w * 0.22f, h * 0.60f), Offset(w * 0.74f, h * 0.60f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.72f), Offset(w * 0.50f, h * 0.86f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.72f), Offset(w * 0.40f, h * 0.82f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.72f), Offset(w * 0.60f, h * 0.82f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            sParental -> {
                // Lock
                drawRect(color, topLeft = Offset(w * 0.24f, h * 0.44f), size = Size(w * 0.52f, h * 0.40f), style = stroke)
                drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.30f, h * 0.18f), size = Size(w * 0.40f, h * 0.32f), style = stroke)
                drawCircle(color, radius = w * 0.05f, center = Offset(w * 0.50f, h * 0.62f))
            }
            sAbout -> {
                // Info circle with "i"
                drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.46f), Offset(w * 0.50f, h * 0.70f), strokeWidth = 2f, cap = StrokeCap.Round)
                drawCircle(color, radius = w * 0.04f, center = Offset(w * 0.50f, h * 0.34f))
            }
            else -> {
                // Generic dot grid fallback
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.36f, h * 0.36f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.64f, h * 0.36f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.36f, h * 0.64f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.64f, h * 0.64f))
            }
        }
    }
}

@Composable
internal fun SettingsRowIcon(key: String, modifier: Modifier = Modifier) {
    val color = VivicastColors.TextSecondary
    Canvas(modifier = modifier.size(18.dp)) {
        val sw = 1.8f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (key) {
            "power" -> {
                drawArc(color, startAngle = -230f, sweepAngle = 280f, useCenter = false,
                    topLeft = Offset(w * 0.18f, h * 0.20f), size = Size(w * 0.64f, h * 0.64f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.08f), Offset(w * 0.50f, h * 0.44f), strokeWidth = sw + 0.5f, cap = StrokeCap.Round)
            }
            "home" -> {
                drawLine(color, Offset(w * 0.14f, h * 0.54f), Offset(w * 0.50f, h * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.86f, h * 0.54f), Offset(w * 0.50f, h * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
                val p = Path().apply {
                    moveTo(w * 0.24f, h * 0.52f); lineTo(w * 0.24f, h * 0.86f)
                    lineTo(w * 0.76f, h * 0.86f); lineTo(w * 0.76f, h * 0.52f)
                }
                drawPath(p, color, style = stroke)
                drawRect(color, topLeft = Offset(w * 0.40f, h * 0.62f), size = Size(w * 0.20f, h * 0.24f), style = stroke)
            }
            "back" -> {
                drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.82f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.38f, h * 0.28f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.38f, h * 0.72f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "language" -> {
                drawCircle(color, radius = w * 0.38f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawOval(color, topLeft = Offset(w * 0.26f, h * 0.12f), size = Size(w * 0.48f, h * 0.76f), style = stroke)
                drawLine(color, Offset(w * 0.12f, h * 0.50f), Offset(w * 0.88f, h * 0.50f), strokeWidth = sw)
            }
            "refresh" -> {
                drawArc(color, startAngle = 40f, sweepAngle = 270f, useCenter = false,
                    topLeft = Offset(w * 0.16f, h * 0.16f), size = Size(w * 0.68f, h * 0.68f), style = stroke)
                val p = Path().apply {
                    moveTo(w * 0.78f, h * 0.20f); lineTo(w * 0.84f, h * 0.08f); lineTo(w * 0.70f, h * 0.14f)
                }
                drawPath(p, color, style = stroke)
            }
            "sort" -> {
                drawLine(color, Offset(w * 0.18f, h * 0.28f), Offset(w * 0.78f, h * 0.28f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.68f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.72f), Offset(w * 0.52f, h * 0.72f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "server" -> {
                // Code / terminal icon: < / >
                drawLine(color, Offset(w * 0.18f, h * 0.38f), Offset(w * 0.36f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.50f), Offset(w * 0.18f, h * 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.82f, h * 0.38f), Offset(w * 0.64f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.64f, h * 0.50f), Offset(w * 0.82f, h * 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.56f, h * 0.24f), Offset(w * 0.44f, h * 0.76f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "buffer" -> {
                drawRect(color, topLeft = Offset(w * 0.12f, h * 0.52f), size = Size(w * 0.16f, h * 0.32f), style = stroke)
                drawRect(color, topLeft = Offset(w * 0.34f, h * 0.36f), size = Size(w * 0.16f, h * 0.48f), style = stroke)
                drawRect(color, topLeft = Offset(w * 0.56f, h * 0.20f), size = Size(w * 0.16f, h * 0.64f), style = stroke)
                drawLine(color, Offset(w * 0.08f, h * 0.88f), Offset(w * 0.80f, h * 0.88f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "speaker" -> {
                val p = Path().apply {
                    moveTo(w * 0.18f, h * 0.36f); lineTo(w * 0.36f, h * 0.36f)
                    lineTo(w * 0.56f, h * 0.18f); lineTo(w * 0.56f, h * 0.82f)
                    lineTo(w * 0.36f, h * 0.64f); lineTo(w * 0.18f, h * 0.64f); close()
                }
                drawPath(p, color, style = stroke)
                drawArc(color, startAngle = -40f, sweepAngle = 80f, useCenter = false,
                    topLeft = Offset(w * 0.60f, h * 0.28f), size = Size(w * 0.26f, h * 0.44f), style = stroke)
            }
            "film" -> {
                drawRect(color, topLeft = Offset(w * 0.14f, h * 0.20f), size = Size(w * 0.72f, h * 0.60f), style = stroke)
                drawLine(color, Offset(w * 0.34f, h * 0.20f), Offset(w * 0.34f, h * 0.80f), strokeWidth = sw)
                drawLine(color, Offset(w * 0.66f, h * 0.20f), Offset(w * 0.66f, h * 0.80f), strokeWidth = sw)
                val p = Path().apply {
                    moveTo(w * 0.42f, h * 0.36f); lineTo(w * 0.42f, h * 0.64f); lineTo(w * 0.60f, h * 0.50f); close()
                }
                drawPath(p, color)
            }
            "display" -> {
                drawRect(color, topLeft = Offset(w * 0.10f, h * 0.18f), size = Size(w * 0.80f, h * 0.52f), style = stroke)
                drawLine(color, Offset(w * 0.38f, h * 0.70f), Offset(w * 0.62f, h * 0.70f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.70f), Offset(w * 0.50f, h * 0.84f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.32f, h * 0.84f), Offset(w * 0.68f, h * 0.84f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "external" -> {
                drawRect(color, topLeft = Offset(w * 0.14f, h * 0.32f), size = Size(w * 0.44f, h * 0.44f), style = stroke)
                drawLine(color, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.82f, h * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.82f, h * 0.18f), Offset(w * 0.82f, h * 0.38f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.44f, h * 0.56f), Offset(w * 0.82f, h * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "timeshift" -> {
                // Clock with counterclockwise arrow overlay
                drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.56f, h * 0.54f), style = stroke)
                drawLine(color, Offset(w * 0.56f, h * 0.54f), Offset(w * 0.56f, h * 0.34f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.56f, h * 0.54f), Offset(w * 0.70f, h * 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
                drawArc(color, startAngle = 110f, sweepAngle = -230f, useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.10f), size = Size(w * 0.42f, h * 0.42f), style = stroke)
                val arr = Path().apply { moveTo(w*0.12f, h*0.10f); lineTo(w*0.24f, h*0.08f); lineTo(w*0.18f, h*0.22f) }
                drawPath(arr, color, style = stroke)
            }
            "clock" -> {
                drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.24f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.68f, h * 0.60f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "storage" -> {
                drawOval(color, topLeft = Offset(w * 0.14f, h * 0.14f), size = Size(w * 0.72f, h * 0.22f), style = stroke)
                drawLine(color, Offset(w * 0.14f, h * 0.25f), Offset(w * 0.14f, h * 0.64f), strokeWidth = sw)
                drawLine(color, Offset(w * 0.86f, h * 0.25f), Offset(w * 0.86f, h * 0.64f), strokeWidth = sw)
                drawArc(color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.14f, h * 0.53f), size = Size(w * 0.72f, h * 0.22f), style = stroke)
            }
            "microphone" -> {
                drawRoundRect(color, topLeft = Offset(w * 0.36f, h * 0.10f), size = Size(w * 0.28f, h * 0.50f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f), style = stroke)
                drawArc(color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.22f, h * 0.30f), size = Size(w * 0.56f, h * 0.40f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.70f), Offset(w * 0.50f, h * 0.86f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.86f), Offset(w * 0.64f, h * 0.86f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "subtitles" -> {
                drawRect(color, topLeft = Offset(w * 0.12f, h * 0.22f), size = Size(w * 0.76f, h * 0.50f), style = stroke)
                drawLine(color, Offset(w * 0.22f, h * 0.44f), Offset(w * 0.52f, h * 0.44f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.22f, h * 0.56f), Offset(w * 0.66f, h * 0.56f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.44f, h * 0.80f), Offset(w * 0.56f, h * 0.72f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.44f, h * 0.80f), Offset(w * 0.30f, h * 0.80f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "skip_forward" -> {
                val p1 = Path().apply { moveTo(w*0.14f,h*0.22f); lineTo(w*0.52f,h*0.50f); lineTo(w*0.14f,h*0.78f); close() }
                val p2 = Path().apply { moveTo(w*0.50f,h*0.22f); lineTo(w*0.78f,h*0.50f); lineTo(w*0.50f,h*0.78f); close() }
                drawPath(p1, color, style = stroke); drawPath(p2, color, style = stroke)
                drawLine(color, Offset(w * 0.82f, h * 0.22f), Offset(w * 0.82f, h * 0.78f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "timer" -> {
                drawCircle(color, radius = w * 0.32f, center = Offset(w * 0.50f, h * 0.58f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.58f), Offset(w * 0.50f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.40f, h * 0.14f), Offset(w * 0.60f, h * 0.14f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "passthrough" -> {
                drawArc(color, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(w * 0.46f, h * 0.22f), size = Size(w * 0.22f, h * 0.56f), style = stroke)
                drawArc(color, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(w * 0.62f, h * 0.14f), size = Size(w * 0.26f, h * 0.72f), style = stroke)
                val p = Path().apply { moveTo(w*0.16f,h*0.36f); lineTo(w*0.38f,h*0.50f); lineTo(w*0.16f,h*0.64f) }
                drawPath(p, color, style = stroke)
                drawLine(color, Offset(w * 0.16f, h * 0.50f), Offset(w * 0.44f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "moon" -> {
                val p = Path().apply {
                    moveTo(w * 0.60f, h * 0.14f)
                    cubicTo(w * 0.14f, h * 0.18f, w * 0.14f, h * 0.82f, w * 0.60f, h * 0.86f)
                    cubicTo(w * 0.30f, h * 0.78f, w * 0.28f, h * 0.22f, w * 0.60f, h * 0.14f)
                }
                drawPath(p, color, style = stroke)
            }
            "palette" -> {
                drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.36f, h * 0.32f))
                drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.64f, h * 0.32f))
                drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.34f, h * 0.62f))
                drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.64f, h * 0.62f))
            }
            "transparency" -> {
                drawRect(color, topLeft = Offset(w * 0.10f, h * 0.10f), size = Size(w * 0.50f, h * 0.50f), style = stroke)
                drawRect(color, topLeft = Offset(w * 0.40f, h * 0.40f), size = Size(w * 0.50f, h * 0.50f), style = stroke)
            }
            "font" -> {
                val p = Path().apply { moveTo(w*0.16f,h*0.82f); lineTo(w*0.50f,h*0.18f); lineTo(w*0.84f,h*0.82f) }
                drawPath(p, color, style = stroke)
                drawLine(color, Offset(w * 0.28f, h * 0.58f), Offset(w * 0.72f, h * 0.58f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "animation" -> {
                for (i in 0..3) {
                    val x = w * (0.14f + i * 0.20f)
                    drawArc(color, startAngle = if (i % 2 == 0) 180f else 0f, sweepAngle = 180f, useCenter = false,
                        topLeft = Offset(x, h * 0.30f), size = Size(w * 0.20f, h * 0.40f), style = stroke)
                }
            }
            "key" -> {
                drawCircle(color, radius = w * 0.22f, center = Offset(w * 0.30f, h * 0.42f), style = stroke)
                drawLine(color, Offset(w * 0.48f, h * 0.58f), Offset(w * 0.86f, h * 0.58f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.74f, h * 0.58f), Offset(w * 0.74f, h * 0.72f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.86f, h * 0.58f), Offset(w * 0.86f, h * 0.70f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "lock" -> {
                drawRect(color, topLeft = Offset(w * 0.22f, h * 0.46f), size = Size(w * 0.56f, h * 0.42f), style = stroke)
                drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.30f, h * 0.16f), size = Size(w * 0.40f, h * 0.36f), style = stroke)
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.50f, h * 0.64f))
            }
            "lock_off" -> {
                drawRect(color, topLeft = Offset(w * 0.22f, h * 0.46f), size = Size(w * 0.56f, h * 0.42f), style = stroke)
                drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.12f), size = Size(w * 0.40f, h * 0.36f), style = stroke)
                drawLine(color, Offset(w * 0.52f, h * 0.30f), Offset(w * 0.78f, h * 0.30f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "shield" -> {
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f); lineTo(w * 0.84f, h * 0.24f)
                    lineTo(w * 0.84f, h * 0.54f); lineTo(w * 0.50f, h * 0.88f)
                    lineTo(w * 0.16f, h * 0.54f); lineTo(w * 0.16f, h * 0.24f); close()
                }
                drawPath(p, color, style = stroke)
            }
            "export" -> {
                drawRect(color, topLeft = Offset(w * 0.18f, h * 0.46f), size = Size(w * 0.64f, h * 0.38f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.50f, h * 0.56f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.34f, h * 0.32f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.66f, h * 0.32f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "import" -> {
                drawRect(color, topLeft = Offset(w * 0.18f, h * 0.46f), size = Size(w * 0.64f, h * 0.38f), style = stroke)
                drawLine(color, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.50f, h * 0.54f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.54f), Offset(w * 0.34f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.54f), Offset(w * 0.66f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "folder" -> {
                val p = Path().apply {
                    moveTo(w * 0.12f, h * 0.36f); lineTo(w * 0.12f, h * 0.78f)
                    lineTo(w * 0.88f, h * 0.78f); lineTo(w * 0.88f, h * 0.36f)
                    lineTo(w * 0.48f, h * 0.36f); lineTo(w * 0.40f, h * 0.24f)
                    lineTo(w * 0.12f, h * 0.24f); close()
                }
                drawPath(p, color, style = stroke)
            }
            "calendar_refresh" -> {
                drawRect(color, topLeft = Offset(w * 0.12f, h * 0.26f), size = Size(w * 0.54f, h * 0.48f), style = stroke)
                drawLine(color, Offset(w * 0.12f, h * 0.42f), Offset(w * 0.66f, h * 0.42f), strokeWidth = sw)
                drawLine(color, Offset(w * 0.30f, h * 0.16f), Offset(w * 0.30f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.36f), strokeWidth = sw, cap = StrokeCap.Round)
                drawArc(color, startAngle = 40f, sweepAngle = 260f, useCenter = false,
                    topLeft = Offset(w * 0.60f, h * 0.48f), size = Size(w * 0.30f, h * 0.30f), style = stroke)
            }
            "list_refresh" -> {
                drawLine(color, Offset(w * 0.12f, h * 0.26f), Offset(w * 0.58f, h * 0.26f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.12f, h * 0.48f), Offset(w * 0.52f, h * 0.48f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.12f, h * 0.70f), Offset(w * 0.44f, h * 0.70f), strokeWidth = sw, cap = StrokeCap.Round)
                drawArc(color, startAngle = 40f, sweepAngle = 260f, useCenter = false,
                    topLeft = Offset(w * 0.52f, h * 0.38f), size = Size(w * 0.36f, h * 0.36f), style = stroke)
            }
            else -> drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
        }
    }
}

@Composable
internal fun AdjustableSettingsRow(
    title: String,
    help: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusPanel(
        modifier = modifier
            .fillMaxWidth()
            .height(VivicastCardSizes.CompactSettingsRowHeight),
        contentPadding = 0.dp,
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = VivicastSpacing.Space4),
        ) {
            // help intentionally not rendered — single-line row, same height as the other settings rows.
            BasicText(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelLarge,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                StepperButton(plus = false, onClick = onDecrease)
                BasicText(value, style = VivicastTypography.LabelLarge)
                StepperButton(plus = true, onClick = onIncrease)
            }
        }
    }
}

/** Compact +/- stepper pill. Draws the glyph as a vector so it's centered regardless of font metrics. */
@Composable
private fun StepperButton(plus: Boolean, onClick: () -> Unit) {
    VivicastFocusSurface(
        onClick = onClick,
        modifier = Modifier.width(56.dp).height(28.dp),
        contentPadding = 0.dp,
        shape = VivicastShapes.PillRadius,
    ) { focused ->
        val color = if (focused) Color.White else VivicastColors.TextSecondary
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val arm = 6.dp.toPx()
            val sw = 2.dp.toPx()
            drawLine(color, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = sw, cap = StrokeCap.Round)
            if (plus) drawLine(color, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

