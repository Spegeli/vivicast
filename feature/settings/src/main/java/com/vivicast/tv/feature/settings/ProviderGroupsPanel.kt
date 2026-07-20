package com.vivicast.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.InfoPanel
import com.vivicast.tv.core.designsystem.VivicastDialog
import com.vivicast.tv.core.designsystem.VivicastDialogWidth
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.VivicastButtonRow
import com.vivicast.tv.core.designsystem.VivicastReorderItem
import com.vivicast.tv.core.designsystem.VivicastReorderList
import com.vivicast.tv.core.designsystem.VivicastSettingsRow
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.domain.model.Category
import com.vivicast.tv.domain.model.CategoryGroupSettings
import com.vivicast.tv.domain.model.CategorySortMode
import com.vivicast.tv.domain.model.CategoryType
import kotlinx.coroutines.android.awaitFrame

/**
 * Group-management inlay (D10): a type switcher (Live-TV/Filme/Serien), the sort mode (Wiedergabeliste /
 * Name / Manuell), a new-groups-hidden policy, bulk show/hide, and a per-group show/hide toggle list. In
 * MANUAL mode a "Sortieren" action swaps the list for the reusable D-Pad reorder list. See plans/d10-*.
 */
@Composable
internal fun ProviderGroupsPanel(
    activeType: CategoryType,
    groups: List<Category>,
    settings: CategoryGroupSettings,
    // Whether the active type was selected for import (provider.includeX): drives the empty-state reason.
    typeIncluded: Boolean,
    onSelectType: (CategoryType) -> Unit,
    onToggleGroupHidden: (categoryId: String, hidden: Boolean) -> Unit,
    onSetAllHidden: (hidden: Boolean) -> Unit,
    onReorder: (orderedIds: List<String>) -> Unit,
    onResetOrder: () -> Unit,
    onSetSortMode: (CategorySortMode) -> Unit,
    onSetHideNewGroups: (Boolean) -> Unit,
    // Rail RIGHT target (from SettingsRoute) — attach to the first focusable so the section rail re-enters.
    entryFocusModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val valueOn = stringResource(R.string.value_on)
    val valueOff = stringResource(R.string.value_off)
    // MANUAL mode's "Reorder" opens a dialog OVERLAY (not a branch swap) so the group panel isn't removed
    // — a branch swap disposes the focused button and focus escapes to the top nav (= Home).
    var showReorderDialog by remember(activeType, settings.sortMode) { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    // Focus the first type chip once on panel entry (NOT on activeType change — that would bounce focus).
    LaunchedEffect(Unit) {
        awaitFrame()
        runCatching { firstFocus.requestFocus() }
    }

    // First focusable = the rail's RIGHT target (entryFocusModifier) AND the on-entry self-focus target.
    // Two focusRequester modifiers on one node both resolve to it.
    val firstChipModifier = Modifier.focusRequester(firstFocus).then(entryFocusModifier)
    val canReorder = settings.sortMode == CategorySortMode.Manual && groups.size > 1
    // One LazyColumn for controls + group rows → linear D-Pad focus (up from the first group reaches the
    // bulk buttons; no Column+LazyColumn focus trapping).
    SettingsDetailList(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
        item { GroupTypeChips(activeType, onSelectType, firstChipModifier) }
        item { GroupSortChips(settings, onSetSortMode) }
        if (canReorder) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                    // Accent-highlighted (selected) so it reads as an action distinct from the sort chips.
                    ActionPill(
                        label = stringResource(R.string.settings_groups_reorder),
                        selected = true,
                        onClick = { showReorderDialog = true },
                    )
                    // Resets the manual order → MANUAL falls back to source (playlist) order.
                    ActionPill(
                        label = stringResource(R.string.settings_groups_reset_order),
                        onClick = onResetOrder,
                    )
                }
            }
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_groups_new_hidden),
                help = "",
                value = if (settings.hideNewGroups) valueOn else valueOff,
                onClick = { onSetHideNewGroups(!settings.hideNewGroups) },
            )
        }
        item {
            VivicastButtonRow {
                ActionPill(label = stringResource(R.string.settings_groups_show_all), onClick = { onSetAllHidden(false) })
                ActionPill(label = stringResource(R.string.settings_groups_hide_all), onClick = { onSetAllHidden(true) })
            }
        }
        if (groups.isEmpty()) {
            item {
                InfoPanel(
                    title = stringResource(R.string.settings_groups_empty),
                    // Explain WHY it's empty: type not selected at import, vs imported but no content.
                    body = stringResource(
                        if (typeIncluded) R.string.settings_groups_empty_no_content else R.string.settings_groups_empty_not_imported,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(groups, key = { it.id }) { group ->
                VivicastSettingsRow(
                    title = group.name,
                    help = "",
                    // Switch reads on = visible; toggling flips the hidden flag.
                    value = if (group.isHidden) valueOff else valueOn,
                    onClick = { onToggleGroupHidden(group.id, !group.isHidden) },
                )
            }
        }
    }

    if (showReorderDialog) {
        // Reorder as a dialog OVERLAY — the group panel stays composed underneath, so switching in/out of
        // reorder never disposes the focused control (no escape to Home). Each drop commits; Back closes.
        VivicastDialog(
            onDismiss = { showReorderDialog = false },
            width = VivicastDialogWidth.Wide,
            title = stringResource(R.string.settings_groups_reorder),
        ) {
            VivicastReorderList(
                items = groups.map { VivicastReorderItem(it.id, it.name) },
                onReorder = onReorder,
                // Bounded height — the reorder list is a LazyColumn; unbounded height in the dialog's
                // Column would crash. Kept short so the dialog doesn't span the whole screen and the
                // component's built-in "OK pick up · ▲▼ move · OK drop · Back cancel" hint stays visible.
                modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
            )
        }
    }
}

// All chips share the row width equally (weight) so every option is visible at once — no scrolling.
@Composable
private fun GroupTypeChips(activeType: CategoryType, onSelectType: (CategoryType) -> Unit, firstChipModifier: Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
        TypeChip(R.string.nav_live_tv, activeType == CategoryType.LiveTv, Modifier.weight(1f).then(firstChipModifier)) { onSelectType(CategoryType.LiveTv) }
        TypeChip(R.string.nav_movies_label, activeType == CategoryType.Movies, Modifier.weight(1f)) { onSelectType(CategoryType.Movies) }
        TypeChip(R.string.nav_series_label, activeType == CategoryType.Series, Modifier.weight(1f)) { onSelectType(CategoryType.Series) }
    }
}

@Composable
private fun GroupSortChips(settings: CategoryGroupSettings, onSetSortMode: (CategorySortMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
        BodyText(stringResource(R.string.settings_groups_sort_label))
        Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.fillMaxWidth()) {
            TypeChip(R.string.settings_groups_sort_playlist, settings.sortMode == CategorySortMode.Playlist, Modifier.weight(1f)) { onSetSortMode(CategorySortMode.Playlist) }
            TypeChip(R.string.settings_groups_sort_name, settings.sortMode == CategorySortMode.Name, Modifier.weight(1f)) { onSetSortMode(CategorySortMode.Name) }
            TypeChip(R.string.settings_groups_sort_manual, settings.sortMode == CategorySortMode.Manual, Modifier.weight(1f)) { onSetSortMode(CategorySortMode.Manual) }
        }
    }
}

// Bundles the group UiState + VM callbacks so ProviderSettingsPanel takes one param, not ten. The
// provider-scoped callbacks take (providerId, type) so ProviderSettingsPanel can bridge the panel's
// provider-agnostic component callbacks using the currently-managed provider.
internal data class ProviderGroupsControls(
    val activeType: CategoryType = CategoryType.LiveTv,
    val groups: List<Category> = emptyList(),
    val settings: CategoryGroupSettings = CategoryGroupSettings(),
    val onOpen: (providerId: String) -> Unit = {},
    val onClose: () -> Unit = {},
    val onSelectType: (CategoryType) -> Unit = {},
    val onToggleHidden: (categoryId: String, hidden: Boolean) -> Unit = { _, _ -> },
    val onSetAllHidden: (providerId: String, type: CategoryType, hidden: Boolean) -> Unit = { _, _, _ -> },
    val onReorder: (orderedIds: List<String>) -> Unit = {},
    val onResetOrder: (providerId: String, type: CategoryType) -> Unit = { _, _ -> },
    val onSetSortMode: (providerId: String, type: CategoryType, mode: CategorySortMode) -> Unit = { _, _, _ -> },
    val onSetHideNewGroups: (providerId: String, type: CategoryType, hidden: Boolean) -> Unit = { _, _, _ -> },
)

// A compact, content-sized selectable chip (ActionPill is capped at 300dp and shows a selected state);
// FocusPanel is a full-width/height card and must NOT be used here.
@Composable
private fun TypeChip(labelRes: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ActionPill(
        label = stringResource(labelRes),
        selected = selected,
        modifier = modifier,
        onClick = onClick,
    )
}
