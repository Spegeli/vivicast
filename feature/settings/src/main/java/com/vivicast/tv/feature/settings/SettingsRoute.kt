package com.vivicast.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusEnterExitScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vivicast.tv.core.cache.MediaCacheStore
import com.vivicast.tv.core.datastore.UserPreferencesStore
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.FocusPanel
import com.vivicast.tv.core.designsystem.GlassPanel
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
import com.vivicast.tv.core.logging.vcLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.reflect.KClass

// Nav-result key: a Playlists sub-view (actions/editor) stashes the provider id (or OVERVIEW_FOCUS_ADD) on
// the entry it pops back to, so the overview re-focuses the originating card. Replaces the old pendingOverviewFocus.
private const val PROVIDER_FOCUS_KEY = "provider_focus"
// Nav-result: BACK from the groups screen asks the actions menu to focus its "Gruppen verwalten" row (its
// origin) instead of the first action. One-shot; the actions destination clears it after re-entry.
private const val FROM_GROUPS_KEY = "from_groups"

// Rail item OK: re-selecting the current section collapses its open sub-view; a different section
// switches. Kept top-level so the branch doesn't count toward SettingsRoute's complexity gate.
private fun railSectionActivate(
    isCurrent: Boolean,
    onCollapse: () -> Unit,
    onSwitch: () -> Unit,
) {
    if (isCurrent) onCollapse() else onSwitch()
}

/**
 * A settings section rail entry: where the inner nav goes ([navTarget]), the rail [label], and the route
 * matched against the current destination's hierarchy ([matchesRoute]) — Playlists matches its whole graph so
 * an open sub-view still highlights the section.
 */
private class SettingsSection(
    val navTarget: Any,
    val label: String,
    val matchesRoute: KClass<*>,
)

private fun SettingsSection.isSame(other: SettingsSection): Boolean = matchesRoute == other.matchesRoute

private fun NavDestination?.isInSection(route: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(route) } == true

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

// Frames to wait for rail RIGHT focus to land before falling back to a spatial move.
private const val FOCUS_SETTLE_FRAMES = 3

// Bumped on rail RIGHT so entering the detail always starts at its first row: a scrollable destination
// snaps its LazyColumn back to the top (ScrollFirstRowIntoView) before that first row is focused.
internal val LocalRevealFirstRowSignal = compositionLocalOf { 0 }

// A scrollable detail destination calls this so rail RIGHT (which bumps LocalRevealFirstRowSignal) snaps
// it to the top — the first row is then on-screen and takes the focus. Instant, no animation. Short
// destinations whose first row is always visible don't need it.
@Composable
internal fun ScrollFirstRowIntoView(listState: LazyListState) {
    val signal = LocalRevealFirstRowSignal.current
    LaunchedEffect(signal) {
        if (signal > 0) listState.scrollToItem(0)
    }
}

// RIGHT from a rail section → enter the detail pane on its FIRST row: bump the reveal signal (a scrollable
// destination snaps to the top), then focus the first row. If it still isn't reachable (a sub-view/overlay
// replaced the overview) fall back to a spatial move so focus is never stranded on the rail. Handled as a
// key event (not focusProperties.right) so ONLY the D-pad RIGHT path is touched — programmatic
// focus-returns (a saved card, the language row, a section switch) still target their own requester.
private fun enterDetailFromRail(
    event: KeyEvent,
    scope: CoroutineScope,
    detailFirstRow: FocusRequester,
    focusManager: FocusManager,
    isDetailFocused: () -> Boolean,
    revealFirstRow: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionRight) return false
    scope.launch {
        revealFirstRow()   // scrollable destinations snap their list to the top before we focus
        awaitFrame()       // let that scroll + recomposition bring the first row on-screen
        runCatching { detailFirstRow.requestFocus() }
        // Give focus a few frames to actually land (onFocusChanged drives isDetailFocused). Only fall back
        // if it genuinely never entered the detail — otherwise a lagging frame would spuriously moveFocus
        // off a first row that DID get focus. After the scroll-to-top the spatial fallback also lands row 0.
        var entered = false
        repeat(FOCUS_SETTLE_FRAMES) {
            awaitFrame()
            if (!entered && isDetailFocused()) entered = true
        }
        if (!entered) focusManager.moveFocus(FocusDirection.Right)
        vcLog("settings-focus") { "rail RIGHT -> detail (firstRow=$entered)" }
    }
    return true
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
    // D3: deep-link straight into the add-provider form (Home "add playlist" CTA) rather than just landing on
    // the Playlists section. Consumed once on entry; the host resets its flag via onAddPlaylistApplied.
    openAddPlaylistOnEnter: Boolean = false,
    onAddPlaylistApplied: () -> Unit = {},
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
    onLogProviderDeleted: (providerId: String, durationMs: Long) -> Unit = { _, _ -> },
    onLogGroupEvent: (message: String, details: Map<String, String>) -> Unit = { _, _ -> },
    // App-layer EPG-assignment diagnostics (opaque provider/source ids + priority/count/order only — no
    // name/url). Covers source_linked / source_unlinked / priority_reordered.
    onLogEpgEvent: (message: String, details: Map<String, String>) -> Unit = { _, _ -> },
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
    // App-hoisted: after an EPG source is deleted, cancel its in-flight/queued refresh (best-effort; the
    // import's in-merge source-existence guard is what prevents orphan epg rows).
    onEpgSourceDeleted: (sourceId: String, durationMs: Long) -> Unit = { _, _ -> },
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
    val sectionGeneral = stringResource(R.string.settings_section_general)
    val sectionPlaylists = stringResource(R.string.settings_section_playlists)
    val sectionEpg = stringResource(R.string.settings_section_epg)
    val sectionAppearance = stringResource(R.string.settings_section_appearance)
    val sectionPlayback = stringResource(R.string.settings_section_playback)
    val sectionParental = stringResource(R.string.settings_section_parental)
    val sectionCache = stringResource(R.string.settings_section_cache)
    val sectionBackup = stringResource(R.string.settings_section_backup)
    val sectionAbout = stringResource(R.string.settings_section_about)
    // Rail model (order = rail order). Playlists is a nested graph so its sub-editors can attach under it;
    // it matches its whole graph in the hierarchy so an open sub-view keeps the section highlighted.
    val sections = remember(
        sectionGeneral, sectionPlaylists, sectionEpg, sectionAppearance, sectionPlayback,
        sectionParental, sectionCache, sectionBackup, sectionAbout,
    ) {
        listOf(
            SettingsSection(SecGeneral, sectionGeneral, SecGeneral::class),
            SettingsSection(PlaylistsGraph, sectionPlaylists, PlaylistsGraph::class),
            SettingsSection(SecEpg, sectionEpg, SecEpg::class),
            SettingsSection(SecAppearance, sectionAppearance, SecAppearance::class),
            SettingsSection(SecPlayback, sectionPlayback, SecPlayback::class),
            SettingsSection(SecParental, sectionParental, SecParental::class),
            SettingsSection(SecCache, sectionCache, SecCache::class),
            SettingsSection(SecBackup, sectionBackup, SecBackup::class),
            SettingsSection(SecAbout, sectionAbout, SecAbout::class),
        )
    }
    val mainSections = remember(sections) { sections.dropLast(1) }
    val aboutSection = sections.last()

    val innerNav = rememberNavController()
    val currentDest = innerNav.currentBackStackEntryAsState().value?.destination
    val currentSection = sections.firstOrNull { currentDest.isInSection(it.matchesRoute) } ?: sections.first()
    // The section to land on this entry (deep-linked section, else General). currentSection lags one frame
    // behind the NavController on entry, so initial focus targets THIS instead of the (still-General) current.
    val startSectionEntry = remember(initialSelectedSection, sections) {
        initialSelectedSection
            ?.let { label -> sections.firstOrNull { it.label == label } }
            ?: sections.first()
    }

    val detailFocusRequester = remember { FocusRequester() }
    // Fallback for rail RIGHT when the detail's first row isn't composed (scrolled off / sub-view open).
    val focusManager = LocalFocusManager.current
    // Bumped on rail RIGHT; a scrollable detail destination reads it (LocalRevealFirstRowSignal) to snap
    // its list back to the top so RIGHT always lands on the first row.
    var revealFirstRowSignal by remember { mutableStateOf(0) }
    var pendingDetailFocus by remember { mutableStateOf(false) }
    // Bumped when OK is pressed on the already-selected section: signals the detail panel to collapse any
    // open sub-view (editor / global settings / legal page) back to its overview. Focus stays on the rail
    // (the collapse touches only the right pane). Playlists sub-views are nav destinations; EPG/About still
    // consume this signal for their (still-local) overlays.
    var collapseSubViewSignal by remember { mutableStateOf(0) }
    // True while focus is inside the right detail panel — gates the settings-scoped BACK (detail → rail).
    var detailFocused by remember { mutableStateOf(false) }
    val sectionFocusRequesters = remember(sections) { sections.associate { it.label to FocusRequester() } }
    val currentSectionFocusRequester = sectionFocusRequesters[currentSection.label] ?: FocusRequester.Default
    val detailFirstFocusModifier = Modifier
        .focusRequester(detailFocusRequester)
        .focusProperties { left = currentSectionFocusRequester }
    // Invisible focus holder living in the detail pane (below the NavHost). Park focus here before an
    // inner-NavHost content swap so it can't orphan up to the top nav (whose selection-follows-focus jumps to
    // Home) during the transition — and, unlike parking on the visible rail, the user sees NO intermediate
    // focus flash. The target destination's own initial-focus effect then reclaims focus. Replaces the
    // per-panel onParkFocusBeforeEditor the D2 split dropped.
    val focusHolder = remember { FocusRequester() }
    val parkRail: () -> Unit = {
        runCatching { focusHolder.requestFocus() }
    }
    // Rail selection-follows-focus: focusing a section navigates the inner nav to it (shallow replace so a
    // section BACK falls through to the rail/gear handlers, not section→section history). Navigates only on
    // an actual change so rapid rail scrolling doesn't thrash.
    val navigateSection: (SettingsSection) -> Unit = { section ->
        if (!currentSection.isSame(section)) {
            innerNav.navigate(section.navTarget) {
                // Clear the whole inner graph so each section is a depth-1 replace — NOT popUpTo the fixed
                // start (that start gets inclusive-popped on the first switch, then later sections accumulate
                // and wrongly enable the inner NavHost's own BACK). Depth-1 sections leave the inner NavHost
                // BACK disabled, so BACK at a section is handled by the rail/gear handlers; only sub-views
                // (which navigate WITHOUT this clear) stack to depth-2 and let the inner NavHost pop them.
                popUpTo(innerNav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    // OK on a rail section: re-selecting the current one collapses its open sub-view (focus stays on the
    // rail); a different one switches and moves focus into the detail. The branch lives in the top-level
    // railSectionActivate to keep SettingsRoute under the complexity gate.
    val activateSection: (SettingsSection) -> Unit = { section ->
        railSectionActivate(
            isCurrent = currentSection.isSame(section),
            onCollapse = {
                collapseSubViewSignal++ // Epg/About: collapse their still-local overlays
                // Playlists: pop its inner-nav sub-views (actions/editor/groups) back to the overview, so OK on
                // the rail section discards the open sub-view. No-op for sections without nav sub-views.
                runCatching { innerNav.popBackStack(SecPlaylists, inclusive = false) }
            },
            onSwitch = { navigateSection(section); pendingDetailFocus = true },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.onReloadCacheStats()
        awaitFrame()
        when {
            openAddPlaylistOnEnter && startSectionEntry.matchesRoute == PlaylistsGraph::class -> {
                // Deep-link straight into the add-provider form; the editor self-focuses its first field.
                vcLog("playlists") { "D3 deeplink -> navigate PlaylistEditor(add)" }
                innerNav.navigate(PlaylistEditor())
                onAddPlaylistApplied()
            }
            focusLanguageRowOnEnter && startSectionEntry.isSame(sections.first()) -> {
                // Post language-change entry: land on the language row (detailFirstFocusModifier is moved
                // onto it below) instead of the section rail.
                detailFocusRequester.requestFocus()
                onInitialLanguageFocusApplied()
            }
            else -> {
                // Target the START section's rail item (currentSection still lags the NavController this frame).
                (sectionFocusRequesters[startSectionEntry.label] ?: currentSectionFocusRequester).requestFocus()
            }
        }
    }
    LaunchedEffect(currentSection.label, pendingDetailFocus) {
        if (pendingDetailFocus) {
            detailFocusRequester.requestFocus()
            pendingDetailFocus = false
        }
    }

    // Settings-scoped BACK: from the detail panel, return to the current section in the rail first;
    // only a further BACK from the rail falls through to the global handler (→ top-nav gear). Inline
    // editors/overlays carry their own (more-nested) BackHandler, so they still close first.
    BackHandler(enabled = detailFocused) {
        currentSectionFocusRequester.requestFocus()
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
                    // top-nav gear. RIGHT (→ detail) targets the current destination's first row explicitly
                    // (detailFocusRequester) — without the removed focusRestorer, a plain spatial search would
                    // land on whichever row happens to be level with the rail item, not the first one.
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
                            val selected = currentSection.isSame(section)
                            FocusPanel(
                                selected = selected,
                                onClick = { activateSection(section) },
                                onFocused = { navigateSection(section) },
                                modifier = Modifier
                                    .focusRequester(sectionFocusRequesters.getValue(section.label))
                                    // RIGHT → detail: prefer the first row, else fall back to a spatial move so
                                    // focus still enters when that row is scrolled off or replaced by a sub-view.
                                    // A key handler (not focusProperties.right) so an uncomposed first row can't
                                    // strand focus on the rail. See enterDetailFromRail.
                                    .onPreviewKeyEvent { enterDetailFromRail(it, routeScope, detailFocusRequester, focusManager, { detailFocused }, { revealFirstRowSignal++ }) }
                                    .fillMaxWidth(),
                                contentPadding = VivicastSpacing.Space2,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingsSectionIcon(section = section.label, selected = selected)
                                    BasicText(
                                        text = section.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                                    )
                                }
                            }
                        }
                    }
                    val aboutSelected = currentSection.isSame(aboutSection)
                    FocusPanel(
                        selected = aboutSelected,
                        onClick = { activateSection(aboutSection) },
                        onFocused = { navigateSection(aboutSection) },
                        modifier = Modifier
                            .focusRequester(sectionFocusRequesters.getValue(aboutSection.label))
                            .onPreviewKeyEvent { enterDetailFromRail(it, routeScope, detailFocusRequester, focusManager, { detailFocused }, { revealFirstRowSignal++ }) }
                            .fillMaxWidth()
                            .wrapContentHeight(unbounded = true),
                        contentPadding = VivicastSpacing.Space2,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingsSectionIcon(section = aboutSection.label, selected = aboutSelected)
                            BasicText(
                                text = aboutSection.label,
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
                    // NO focusRestorer here: its onEnter redirected EVERY programmatic requestFocus (a returning
                    // card, the "Gruppen verwalten" row, the first row) to its fallback, which broke targeted
                    // focus-return. RIGHT-from-rail is handled on the rail side (enterDetailFromRail): first row
                    // when composed, else a spatial move INTO this group as the fallback — so this focusGroup
                    // only needs onExit. Each destination requests its own focus target directly.
                    modifier = Modifier
                        .focusGroup()
                        .focusProperties {
                            onExit = { exitDetailPanel(currentSectionFocusRequester) }
                        },
                    verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
                ) {
                    SettingsPanelTitle(currentSection.label)
                    // Inner Settings NavHost: the detail pane. The section rail drives it; Playlists is a
                    // nested graph so its editor/actions/groups attach as real destinations (BACK-to-overview),
                    // replacing the old local-state sub-view flags + focus-park machinery.
                    // Rail RIGHT bumps revealFirstRowSignal; scrollable destinations read it (via
                    // LocalRevealFirstRowSignal + ScrollFirstRowIntoView) to snap their list back to the top.
                    CompositionLocalProvider(LocalRevealFirstRowSignal provides revealFirstRowSignal) {
                    NavHost(
                        navController = innerNav,
                        startDestination = startSectionEntry.navTarget,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // Track detail focus HERE (on the NavHost node), not the outer Column: the inner
                            // NavHost isolates its content's focus from the Column's onFocusChanged, so the
                            // Column would never see hasFocus=true and the detail→rail BACK would break.
                            .onFocusChanged { detailFocused = it.hasFocus },
                    ) {
                        composable<SecGeneral> { GeneralSettingsPanel(
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
                        ) }
                        navigation<PlaylistsGraph>(startDestination = SecPlaylists) {
                            composable<SecPlaylists> { entry ->
                                val focusProvider by entry.savedStateHandle
                                    .getStateFlow<String?>(PROVIDER_FOCUS_KEY, null)
                                    .collectAsStateWithLifecycle()
                                ProviderOverviewScreen(
                                    providers = settingsUiState.providers,
                                    epgSources = settingsUiState.epgSources,
                                    onGetProviderCredentials = viewModel::getProviderCredentials,
                                    onProviderSaved = onProviderSaved,
                                    onAddProvider = { parkRail(); innerNav.navigate(PlaylistEditor()) },
                                    onOpenProvider = { id -> parkRail(); innerNav.navigate(PlaylistActions(id)) },
                                    pendingFocusProviderId = focusProvider,
                                    onFocusHandled = { entry.savedStateHandle[PROVIDER_FOCUS_KEY] = null },
                                    firstFocusModifier = detailFirstFocusModifier,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            composable<PlaylistActions> { entry ->
                                val providerId = entry.toRoute<PlaylistActions>().providerId
                                // One-shot from-groups flag: read once on (re-)entry via a plain remember (which
                                // re-inits on pop-back, when the destination re-enters composition) so the
                                // actions panel's entry-focus effect sees a stable value, then clear it.
                                val fromGroups = remember { entry.savedStateHandle.get<Boolean>(FROM_GROUPS_KEY) ?: false }
                                LaunchedEffect(Unit) {
                                    vcLog("playlists") { "actions entry fromGroups=$fromGroups" }
                                    entry.savedStateHandle[FROM_GROUPS_KEY] = false
                                }
                                // BACK from actions → overview, re-focusing the originating card (the inner
                                // NavHost's auto-pop can't carry a nav result, so intercept + stash it first).
                                BackHandler {
                                    vcLog("playlists") { "actions BACK -> stash focus $providerId + pop" }
                                    parkRail()
                                    innerNav.previousBackStackEntry?.savedStateHandle?.set(PROVIDER_FOCUS_KEY, providerId)
                                    innerNav.popBackStack()
                                }
                                ProviderActionsScreen(
                                    providerId = providerId,
                                    focusGroupsRowOnEntry = fromGroups,
                                    providers = settingsUiState.providers,
                                    epgSources = settingsUiState.epgSources,
                                    providerEpgLinks = settingsUiState.providerEpgLinks,
                                    onGetProviderCredentials = viewModel::getProviderCredentials,
                                    onGetProviderM3uContent = viewModel::getProviderM3uInlineContent,
                                    onTestProviderConnection = onTestProviderConnection,
                                    onRefreshProvider = onRefreshProvider,
                                    onDeleteProvider = viewModel::deleteProvider,
                                    onLogProviderDeleted = onLogProviderDeleted,
                                    onSelectEpgProvider = viewModel::onEpgProviderSelected,
                                    onEdit = { parkRail(); innerNav.navigate(PlaylistEditor(providerId)) },
                                    onManageGroups = { parkRail(); innerNav.navigate(PlaylistGroups(providerId)) },
                                    onDeleted = { focusId ->
                                        parkRail()
                                        innerNav.previousBackStackEntry?.savedStateHandle?.set(PROVIDER_FOCUS_KEY, focusId)
                                        innerNav.popBackStack()
                                    },
                                    entryFocusModifier = detailFirstFocusModifier,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            composable<PlaylistEditor> { entry ->
                                val editorArgs = entry.toRoute<PlaylistEditor>()
                                ProviderEditorScreen(
                                    providerId = editorArgs.providerId,
                                    providers = settingsUiState.providers,
                                    epgSources = settingsUiState.epgSources,
                                    providerEpgLinks = settingsUiState.providerEpgLinks,
                                    onGetProviderCredentials = viewModel::getProviderCredentials,
                                    onGetProviderM3uContent = viewModel::getProviderM3uInlineContent,
                                    onCreateProvider = viewModel::createProvider,
                                    onUpdateProvider = viewModel::updateProvider,
                                    onSetProviderEnabled = viewModel::setProviderEnabled,
                                    onDeleteProvider = viewModel::deleteProvider,
                                    onTestProviderConnection = onTestProviderConnection,
                                    onPickM3uFile = onPickM3uFile,
                                    onRefreshProvider = onRefreshProvider,
                                    onXtreamProviderSaved = onXtreamProviderSaved,
                                    onLogProviderSaved = onLogProviderSaved,
                                    onLogProviderDeleted = onLogProviderDeleted,
                                    onSelectEpgProvider = viewModel::onEpgProviderSelected,
                                    onToggleEpgLink = { pid, sourceId, link ->
                                        routeScope.launch {
                                            if (link) {
                                                viewModel.linkEpgSourceToProvider(pid, sourceId).onSuccess {
                                                    onLogEpgEvent("source_linked", mapOf("target" to pid, "source" to sourceId))
                                                }
                                            } else {
                                                viewModel.unlinkEpgSourceFromProvider(pid, sourceId).onSuccess {
                                                    onLogEpgEvent("source_unlinked", mapOf("target" to pid, "source" to sourceId))
                                                }
                                            }
                                        }
                                    },
                                    onReorderEpgLink = { pid, orderedIds ->
                                        routeScope.launch {
                                            viewModel.reorderEpgSourcesForProvider(pid, orderedIds).onSuccess {
                                                onLogEpgEvent("priority_reordered", mapOf("target" to pid, "count" to orderedIds.size.toString(), "order" to orderedIds.joinToString(",")))
                                            }
                                        }
                                    },
                                    onSaved = { focusId ->
                                        vcLog("playlists") { "editor onSaved focus=$focusId + pop" }
                                        parkRail()
                                        innerNav.previousBackStackEntry?.savedStateHandle?.set(PROVIDER_FOCUS_KEY, focusId)
                                        innerNav.popBackStack()
                                    },
                                    onDeleted = { focusId ->
                                        vcLog("playlists") { "editor onDeleted focus=$focusId + pop-to-overview" }
                                        parkRail()
                                        innerNav.getBackStackEntry(SecPlaylists).savedStateHandle[PROVIDER_FOCUS_KEY] = focusId
                                        innerNav.popBackStack(SecPlaylists, inclusive = false)
                                    },
                                    onCancel = {
                                        parkRail()
                                        // Add (from overview) → focus the Add row on return; edit (from actions)
                                        // → actions self-focuses on re-entry, so no result needed.
                                        if (editorArgs.providerId == null) {
                                            innerNav.previousBackStackEntry?.savedStateHandle?.set(PROVIDER_FOCUS_KEY, OVERVIEW_FOCUS_ADD)
                                        }
                                        innerNav.popBackStack()
                                    },
                                    entryFocusModifier = detailFirstFocusModifier,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            composable<PlaylistGroups> { entry ->
                                // BACK from groups → actions; park focus first so the inner NavHost's auto-pop
                                // can't orphan it to the top nav (→ Home). The from-groups result makes actions
                                // re-focus its "Gruppen verwalten" origin row instead of the first action.
                                BackHandler {
                                    parkRail()
                                    innerNav.previousBackStackEntry?.savedStateHandle?.set(FROM_GROUPS_KEY, true)
                                    innerNav.popBackStack()
                                }
                                ProviderGroupsScreen(
                                    providerId = entry.toRoute<PlaylistGroups>().providerId,
                                    providers = settingsUiState.providers,
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
                                        onSetAllHidden = { pid, type, hidden ->
                                            onLogGroupEvent(
                                                if (hidden) "groups_bulk_hidden" else "groups_bulk_shown",
                                                mapOf("type" to type.name, "count" to settingsUiState.manageGroups.size.toString()),
                                            )
                                            routeScope.launch { viewModel.setAllGroupsHidden(pid, type, hidden) }
                                        },
                                        onReorder = { ids ->
                                            onLogGroupEvent("group_reordered", mapOf("type" to settingsUiState.manageGroupsType.name, "count" to ids.size.toString()))
                                            routeScope.launch { viewModel.reorderGroups(ids) }
                                        },
                                        onResetOrder = { pid, type ->
                                            onLogGroupEvent("group_order_reset", mapOf("type" to type.name))
                                            routeScope.launch { viewModel.resetGroupOrder(pid, type) }
                                        },
                                        onSetSortMode = { pid, type, mode ->
                                            onLogGroupEvent("sort_mode_changed", mapOf("type" to type.name, "mode" to mode.name))
                                            routeScope.launch { viewModel.setGroupSortMode(pid, type, mode) }
                                        },
                                        onSetHideNewGroups = { pid, type, hidden ->
                                            onLogGroupEvent(
                                                "new_groups_policy_changed",
                                                mapOf("type" to type.name, "policy" to if (hidden) "hidden" else "shown"),
                                            )
                                            routeScope.launch { viewModel.setHideNewGroups(pid, type, hidden) }
                                        },
                                    ),
                                    entryFocusModifier = detailFirstFocusModifier,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        composable<SecEpg> { EpgSettingsPanel(
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
                            onDeleteEpgSource = { id ->
                                val start = System.currentTimeMillis()
                                viewModel.deleteEpgSource(id).also {
                                    if (it.isSuccess) onEpgSourceDeleted(id, System.currentTimeMillis() - start)
                                }
                            },
                            onSelectManualMappingChannel = viewModel::onManualMappingChannelSelected,
                            onResetManualMappingChannel = viewModel::onManualMappingReset,
                            onSetManualMapping = viewModel::setManualChannelMapping,
                            onClearManualMapping = viewModel::clearManualChannelMapping,
                            onGetEpgSourceUrl = viewModel::getEpgSourceUrl,
                            onTestEpgConnection = onTestEpgConnection,
                            firstFocusModifier = detailFirstFocusModifier,
                            // Park focus on the (always-present) section button before the overview swaps
                            // to the inline editor, so focus can't escape to the top nav bar (jumps Home).
                            onParkFocusBeforeEditor = parkRail,
                            collapseSubViewSignal = collapseSubViewSignal,
                        ) }
                        composable<SecAppearance> { AppearanceSettingsPanel(
                            state = settingsUiState.appearance,
                            onAppearanceSettingsChanged = viewModel::onAppearanceSettingsChanged,
                            localLogoFolder = localLogoFolder,
                            onPickLogoFolder = onPickLogoFolder,
                            onRescanLogos = onRescanLogos,
                            rescanningLogos = rescanningLogos,
                            onRemoveLogoFolder = onRemoveLogoFolder,
                            firstFocusModifier = detailFirstFocusModifier,
                        ) }
                        composable<SecPlayback> { PlaybackSettingsPanel(
                            state = settingsUiState.playback,
                            onPlaybackPreferencesChanged = viewModel::onPlaybackSettingsChanged,
                            firstFocusModifier = detailFirstFocusModifier,
                        ) }
                        composable<SecParental> { ParentalControlSettingsPanel(
                            state = parentalControlSettingsState,
                            onSetPin = onSetPin,
                            onChangePin = onChangePin,
                            onDisablePin = onDisablePin,
                            onProtectionChanged = onProtectionChanged,
                            firstFocusModifier = detailFirstFocusModifier,
                        ) }
                        composable<SecCache> { MaintenanceSettingsPanel(
                            cacheSettingsState = settingsUiState.cache,
                            onClearCache = viewModel::onClearCache,
                            onClearHistory = onClearHistory,
                            onReloadCacheStats = viewModel::onReloadCacheStats,
                            firstFocusModifier = detailFirstFocusModifier,
                        ) }
                        composable<SecBackup> { BackupSettingsPanel(
                            onExportBackup = onExportBackup,
                            onImportBackup = onImportBackup,
                            firstFocusModifier = detailFirstFocusModifier,
                        ) }
                        composable<SecAbout> { AboutSettingsPanel(
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
                        ) }
                    }
                    }
                    // Invisible focus holder (see focusHolder) — parked on during inner-nav swaps so focus
                    // never orphans to the top nav, with no visible flash. It sits below the NavHost and the
                    // detail's DOWN-exit is cancelled, so it's reachable only via requestFocus, not spatial nav.
                    Box(modifier = Modifier.size(1.dp).focusRequester(focusHolder).focusable())
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

