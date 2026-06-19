package com.vivicast.tv

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.vivicast.core.model.Channel
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.FavoriteChannel
import com.vivicast.core.model.PlaybackStatus
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.RecentChannel
import java.util.Locale
@Composable
fun SettingsInfoCard(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ViviCastColors.Surface)
            .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(text = body, color = ViviCastColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
fun SettingsToggleRow(
    label: String,
    value: Boolean,
    description: String? = null,
    onClick: () -> Unit
) {
    ProviderInstantSettingRow(
        label = label,
        value = if (value) "Enabled" else "Disabled",
        description = description,
        toggleValue = value,
        onClick = onClick
    )
}

@Composable
fun SettingsValueRow(
    label: String,
    value: String,
    description: String? = null,
    onClick: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) ViviCastColors.FocusFill else ViviCastColors.Surface)
            .border(if (focused) 2.dp else 1.dp, if (focused) ViviCastColors.Focus else ViviCastColors.Line, shape)
            .onFocusChanged { focused = it.isFocused }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick).activateOnCenter(onClick).focusable() else Modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = if (focused) ViviCastColors.Background else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = value,
                    color = if (focused) ViviCastColors.Background else ViviCastColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    color = if (focused) ViviCastColors.Background.copy(alpha = 0.76f) else ViviCastColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun SettingsActionChip(
    text: String,
    selected: Boolean? = null,
    requestInitialFocus: Boolean = false,
    onMoveLeft: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(8.dp)
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            runCatching { focusRequester.requestFocus() }
        }
    }
    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(shape)
            .background(
                when {
                    focused -> ViviCastColors.FocusFill
                    selected == true -> ViviCastColors.Selected
                    selected == false -> ViviCastColors.SurfaceRaised
                    else -> ViviCastColors.Selected
                }
            )
            .border(
                if (focused) 3.dp else if (selected == true) 2.dp else 1.dp,
                when {
                    focused -> ViviCastColors.Focus
                    selected == true -> ViviCastColors.Accent
                    selected == false -> ViviCastColors.Line
                    else -> ViviCastColors.Line
                },
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    event.key == Key.DirectionLeft &&
                    onMoveLeft != null
                ) {
                    onMoveLeft()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusRequester(focusRequester)
            .focusable()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

