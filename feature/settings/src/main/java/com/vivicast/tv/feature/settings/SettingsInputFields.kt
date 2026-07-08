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
import com.vivicast.tv.core.designsystem.VivicastSpinner
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
internal fun ProviderTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    allowReveal: Boolean = false,
    singleLine: Boolean = true,
    height: Dp = 58.dp,
    trailingActionLabel: String? = null,
    onTrailingAction: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    isError: Boolean = false,
    maxLength: Int? = null,
) {
    VivicastTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        // ponytail: dialogs stay compact — no label row above the field; the placeholder carries the
        // hint. The `label` param is kept so call sites document each field's purpose in code.
        label = null,
        placeholder = placeholder,
        secret = secret,
        allowReveal = allowReveal,
        singleLine = singleLine,
        height = height,
        focusRequester = focusRequester,
        isError = isError,
        maxLength = maxLength,
        trailingActionLabel = trailingActionLabel,
        onTrailingAction = onTrailingAction,
    )
}

@Composable
internal fun ConnectionTestButton(
    status: ConnectionTestStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor: Color? = when (status) {
        ConnectionTestStatus.Passed -> VivicastColors.Success
        ConnectionTestStatus.Failed -> VivicastColors.Error
        else -> null
    }
    val glyph: String? = when (status) {
        ConnectionTestStatus.Passed -> "✓"
        ConnectionTestStatus.Failed -> "✗"
        else -> null
    }
    val labelRes = when (status) {
        ConnectionTestStatus.Testing -> R.string.settings_provider_msg_checking
        ConnectionTestStatus.Passed -> R.string.settings_provider_test_ok
        ConnectionTestStatus.Failed -> R.string.settings_provider_test_fail
        else -> R.string.settings_provider_test_connection
    }
    FocusPanel(
        onClick = onClick,
        modifier = modifier
            .width(175.dp)
            .then(
                if (statusColor != null) {
                    Modifier.border(VivicastBorders.FocusWidth, statusColor, VivicastShapes.CardRadius)
                } else {
                    Modifier
                },
            ),
    ) { _ ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (status == ConnectionTestStatus.Testing) {
                VivicastSpinner(size = 16.dp, color = VivicastColors.TextPrimary)
            }
            if (glyph != null) {
                BasicText(
                    text = glyph,
                    style = VivicastTypography.LabelLarge.copy(color = statusColor ?: VivicastColors.TextPrimary),
                )
            }
            BasicText(
                text = stringResource(labelRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelMedium.copy(color = statusColor ?: VivicastColors.TextPrimary),
            )
        }
    }
}

