package com.vivicast.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vivicast.tv.core.designsystem.BodyText
import com.vivicast.tv.core.designsystem.LocalVivicastColors
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.StatusBadge
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastSettingsRow
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTypography
import com.vivicast.tv.data.provider.ContentSummary
import com.vivicast.tv.data.provider.M3uSourceMode
import com.vivicast.tv.domain.model.Provider
import com.vivicast.tv.domain.model.ProviderStatus
import kotlinx.coroutines.android.awaitFrame

/**
 * Intermediate "Playlist-Aktionen" view between the overview list and the edit form (design screen 08 —
 * per-playlist actions). Read-only detail header + action rows. Editing the source is a separate action
 * (the editor), so this menu — and "Gruppen verwalten" reached from it — never operates on an unsaved
 * source draft. "Gruppen verwalten" is gated while a refresh is in flight so it opens on fresh groups.
 * See plans/playlist-actions-menu.md.
 */
@Composable
internal fun ProviderActionsPanel(
    provider: Provider,
    sourceMode: M3uSourceMode,
    isRefreshing: Boolean,
    // The refresh-in-flight label, distinguishing playlist vs EPG refresh ("Playlist/EPG wird aktualisiert…").
    refreshingLabel: String,
    testStatus: ConnectionTestStatus,
    testSummary: ContentSummary?,
    testError: String?,
    onEdit: () -> Unit,
    onTestConnection: () -> Unit,
    onRefresh: () -> Unit,
    onManageGroups: () -> Unit,
    onDelete: () -> Unit,
    // Rail RIGHT target (from SettingsRoute) — attach to the first row so the section rail re-enters here.
    entryFocusModifier: Modifier = Modifier,
    // Re-entered via Back from group management → focus the "Gruppen verwalten" row (its origin), not the
    // first "Bearbeiten" row.
    focusGroupsRowOnEntry: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val firstRowRequester = remember { FocusRequester() }
    val groupsRowRequester = remember { FocusRequester() }
    // Focus an action whenever this view enters composition (overview→actions, editor→actions,
    // groups→actions), so focus never escapes to the top nav bar when the previous surface is removed. Back
    // from group management lands on its originating row; every other entry lands on the first action.
    LaunchedEffect(provider.id) {
        awaitFrame()
        runCatching { (if (focusGroupsRowOnEntry) groupsRowRequester else firstRowRequester).requestFocus() }
    }
    // Test result rendered inside the "Verbindung prüfen" row: "Connection OK (X Channels, Y Movies,
    // Z Series)" / "Connection Failed (reason)" / a spinner while testing.
    val testValue = when (testStatus) {
        ConnectionTestStatus.Testing -> stringResource(R.string.settings_provider_msg_checking)
        ConnectionTestStatus.Passed -> testSummary?.let {
            stringResource(R.string.settings_provider_test_ok_format, it.channels, it.movies, it.series)
        }.orEmpty()
        ConnectionTestStatus.Failed -> testError?.let {
            stringResource(R.string.settings_provider_test_failed_format, it)
        }.orEmpty()
        ConnectionTestStatus.Idle -> ""
    }
    SettingsDetailList(
        modifier = modifier,
    ) {
        item { ProviderActionsHeader(provider, sourceMode) }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_provider_action_edit),
                help = "",
                value = "",
                modifier = Modifier.focusRequester(firstRowRequester).then(entryFocusModifier),
                onClick = onEdit,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_provider_action_test),
                help = "",
                value = testValue,
                valueLoading = testStatus == ConnectionTestStatus.Testing,
                onClick = onTestConnection,
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_provider_action_refresh),
                help = "",
                value = if (isRefreshing) refreshingLabel else stringResource(R.string.settings_provider_action_refresh_value),
                valueLoading = isRefreshing,
                // Stays enabled/focusable while refreshing (disabling the focused row would drop focus to
                // the top nav = jump to Home); re-pressing just restarts the refresh.
                onClick = onRefresh,
            )
        }
        item {
            // "Gruppen verwalten" is locked ONLY while THIS playlist's own playlist-refresh runs, so groups
            // open on fresh data. It is NOT gated by an EPG refresh (groups stay editable regardless) or by
            // other playlists' refreshes — provider.status == Refreshing is set solely by this playlist's
            // playlist refresh. The row stays focusable (no enabled=false, which would drop focus to the top
            // nav = jump to Home); while locked the click is a no-op and the value shows "wird aktualisiert…".
            val playlistRefreshing = provider.status == ProviderStatus.Refreshing
            VivicastSettingsRow(
                title = stringResource(R.string.settings_provider_action_groups),
                help = "",
                value = if (playlistRefreshing) stringResource(R.string.settings_provider_action_refreshing_playlist) else "",
                valueLoading = playlistRefreshing,
                modifier = Modifier.focusRequester(groupsRowRequester),
                onClick = { if (!playlistRefreshing) onManageGroups() },
            )
        }
        item {
            VivicastSettingsRow(
                title = stringResource(R.string.settings_provider_action_delete),
                help = "",
                value = "",
                onClick = onDelete,
            )
        }
    }
}

// Mirrors ProviderSourceCard's content layout (design screen 08): name + status/source/type badges on the
// left, "Updated" (+ Xtream expiry / max connections) End-aligned on the right.
@Composable
private fun ProviderActionsHeader(provider: Provider, sourceMode: M3uSourceMode) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = provider.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = VivicastTypography.LabelLarge,
                )
                StatusBadge(provider.status.localizedLabel(), tone = provider.status.tone)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
                StatusBadge(providerSourceLabel(provider.type, sourceMode))
                StatusBadge(stringResource(R.string.nav_live_tv), tone = if (provider.includeLiveTv) LocalVivicastColors.current.accent else VivicastColors.SurfaceHigh)
                StatusBadge(stringResource(R.string.nav_movies_label), tone = if (provider.includeMovies) LocalVivicastColors.current.accent else VivicastColors.SurfaceHigh)
                StatusBadge(stringResource(R.string.nav_series_label), tone = if (provider.includeSeries) LocalVivicastColors.current.accent else VivicastColors.SurfaceHigh)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1)) {
            BodyText(stringResource(R.string.settings_provider_updated_format, provider.updatedAt.toBackupTimestamp()), maxLines = 1)
            provider.xtreamExpiresAtMillis?.let { expiresAt ->
                BodyText(stringResource(R.string.settings_provider_expiry_format, expiresAt.toBackupTimestamp()), maxLines = 1)
            }
            provider.xtreamMaxConnections?.let { maxConnections ->
                BodyText(stringResource(R.string.settings_provider_max_connections_format, maxConnections), maxLines = 1)
            }
        }
    }
}
