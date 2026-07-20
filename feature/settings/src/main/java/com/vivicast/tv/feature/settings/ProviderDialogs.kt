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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
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

@Composable
fun DeleteProviderDialog(
    provider: Provider,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val cancelFocusRequester = remember { FocusRequester() }
    // Deletion runs async and the dialog only closes when it completes — show a spinner meanwhile so
    // the user has feedback that work is happening.
    var deleting by remember { mutableStateOf(false) }

    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        initialFocus = cancelFocusRequester,
        modifier = Modifier.testTag(deleteProviderDialogTag(provider.id)),
    ) {
        InfoPanel(
            title = stringResource(R.string.about_provider_delete_title),
            body = stringResource(R.string.settings_provider_delete_body),
            badge = provider.name,
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastDialogActions(
            primaryLabel = stringResource(if (deleting) R.string.settings_deleting else R.string.settings_delete),
            onPrimary = {
                deleting = true
                onDelete()
            },
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            primaryEnabled = !deleting,
            primaryLoading = deleting,
            primaryTestTag = deleteProviderConfirmTag(provider.id),
            secondaryTestTag = deleteProviderCancelTag(provider.id),
            secondaryFocusRequester = cancelFocusRequester,
        )
    }
}

/**
 * Save-time connection-test failure. Lets the user go back and fix the source, or force-save it —
 * which always deactivates (a broken source is never persisted as active). Only shown on Save, not
 * on the manual test button.
 */
@Composable
fun ProviderConnectionFailedDialog(
    reason: String?,
    isActive: Boolean,
    onCorrect: () -> Unit,
    onSaveAnyway: () -> Unit,
) {
    val correctFocusRequester = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onCorrect,
        width = VivicastDialogWidth.Standard,
        initialFocus = correctFocusRequester,
    ) {
        InfoPanel(
            title = stringResource(R.string.settings_provider_conn_failed_title),
            body = stringResource(R.string.settings_provider_conn_failed_body),
            badge = reason,
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastDialogActions(
            primaryLabel = stringResource(
                if (isActive) R.string.settings_provider_save_and_disable else R.string.settings_provider_save_anyway,
            ),
            onPrimary = onSaveAnyway,
            secondaryLabel = stringResource(R.string.settings_provider_correct),
            onSecondary = onCorrect,
            secondaryFocusRequester = correctFocusRequester,
        )
    }
}

/** Human label for a source type/mode: "M3U URL" / "M3U Datei" / "Xtream". Shared by the overview badge
 * and the save/switch confirmation dialog. */
@Composable
internal fun providerSourceLabel(type: ProviderType, mode: M3uSourceMode): String =
    when (type) {
        ProviderType.M3u ->
            "M3U " + stringResource(if (mode == M3uSourceMode.File) R.string.m3u_source_file else R.string.m3u_source_url)
        ProviderType.Xtream -> "Xtream"
    }

/**
 * Save confirmation. In add mode it's informational ("saved as X"); on an edit that switches the source
 * type/mode it warns that the old source's data (credentials/URL) plus favorites/history/progress are lost.
 */
@Composable
fun ProviderSourceConfirmDialog(
    isSwitch: Boolean,
    targetLabel: String,
    originalLabel: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    VivicastDialog(
        onDismiss = onCancel,
        width = VivicastDialogWidth.Standard,
        initialFocus = focusRequester,
    ) {
        InfoPanel(
            title = stringResource(
                if (isSwitch) R.string.settings_provider_switch_title else R.string.settings_provider_save_confirm_title,
            ),
            body = if (isSwitch) {
                stringResource(R.string.settings_provider_switch_body, originalLabel.orEmpty(), targetLabel)
            } else {
                stringResource(R.string.settings_provider_save_confirm_body, targetLabel)
            },
            // The switch warning is several lines (source deletion + data loss); don't truncate it.
            bodyMaxLines = if (isSwitch) 8 else 3,
            modifier = Modifier.fillMaxWidth(),
        )
        VivicastDialogActions(
            primaryLabel = stringResource(
                if (isSwitch) R.string.settings_provider_switch_confirm else R.string.common_save,
            ),
            onPrimary = onConfirm,
            secondaryLabel = stringResource(R.string.common_cancel),
            onSecondary = onCancel,
            // Destructive switch defaults focus to Cancel; the informational add-confirm to Save.
            primaryFocusRequester = if (isSwitch) null else focusRequester,
            secondaryFocusRequester = if (isSwitch) focusRequester else null,
        )
    }
}

fun deleteProviderDialogTag(providerId: String): String = "settings-delete-provider-dialog-$providerId"
fun deleteProviderCancelTag(providerId: String): String = "settings-delete-provider-cancel-$providerId"
fun deleteProviderConfirmTag(providerId: String): String = "settings-delete-provider-confirm-$providerId"
