package com.vivicast.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.vivicast.core.model.ChannelCategory
import com.vivicast.core.model.EpgSource
import com.vivicast.core.model.Playlist
import com.vivicast.core.model.XtreamCredentials
import java.io.File
@Composable
fun ProviderInstantSettingRow(
    label: String,
    value: String,
    description: String? = null,
    toggleValue: Boolean? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) ViviCastColors.FocusFill else ViviCastColors.SurfaceRaised)
            .border(1.dp, if (focused) ViviCastColors.Focus else ViviCastColors.Line, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (toggleValue != null) {
                TvToggle(
                    checked = toggleValue,
                    focused = focused
                )
            } else {
                Text(text = value, color = ViviCastColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
        description?.let {
            Text(
                text = it,
                color = ViviCastColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun TvToggle(
    checked: Boolean,
    focused: Boolean
) {
    Box(
        modifier = Modifier
            .width(50.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    checked && focused -> ViviCastColors.Focus
                    checked -> ViviCastColors.Accent
                    focused -> ViviCastColors.FocusFill
                    else -> ViviCastColors.Line
                }
            )
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun ProviderGroupToggleRow(
    name: String,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(shape)
            .background(if (focused) ViviCastColors.FocusFill else ViviCastColors.Surface)
            .border(1.dp, if (focused) ViviCastColors.Focus else ViviCastColors.Line, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .activateOnCenter(onClick)
            .focusable()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderRowActionButton(
                text = "Up",
                enabled = canMoveUp,
                onClick = onMoveUp
            )
            ProviderRowActionButton(
                text = "Down",
                enabled = canMoveDown,
                onClick = onMoveDown
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        TvToggle(
            checked = visible,
            focused = focused
        )
    }
}

@Composable
fun ProviderRowActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 28.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> ViviCastColors.SurfaceRaised
                    focused -> ViviCastColors.FocusFill
                    else -> ViviCastColors.SurfaceRaised
                }
            )
            .border(
                1.dp,
                when {
                    !enabled -> ViviCastColors.Line
                    focused -> ViviCastColors.Focus
                    else -> ViviCastColors.Line
                },
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .activateOnCenter { if (enabled) onClick() }
            .focusable(enabled = enabled)
            .padding(bottom = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun ProviderDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ViviCastColors.SurfaceRaised)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = ViviCastColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun M3uUrlInput(
    value: String,
    enabled: Boolean,
    error: String?,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ViviCastColors.SurfaceRaised)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = when {
                        error != null -> ViviCastColors.Error
                        focused -> ViviCastColors.Focus
                        else -> ViviCastColors.Line
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isBlank()) {
                Text(
                    text = "https://example.com/playlist.m3u",
                    color = ViviCastColors.TextMuted,
                    fontSize = 16.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(ViviCastColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .activateOnCenter(onSubmit)
            )
        }

        Text(
            text = error ?: "Supported formats: M3U and M3U8 playlist URLs.",
            color = if (error == null) ViviCastColors.TextSecondary else ViviCastColors.Error,
            fontSize = 12.sp
        )
    }
}

@Composable
fun TvTextInput(
    value: String,
    enabled: Boolean,
    placeholder: String,
    error: String?,
    focusRequester: FocusRequester? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ViviCastColors.SurfaceRaised)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    error != null -> ViviCastColors.Error
                    focused -> ViviCastColors.Focus
                    else -> ViviCastColors.Line
                },
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isBlank()) {
            Text(
                text = placeholder,
                color = ViviCastColors.TextMuted,
                fontSize = 15.sp
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            ),
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(ViviCastColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .activateOnCenter(onSubmit)
        )
    }
}

@Composable
fun RowScope.DialogActionButton(
    text: String,
    enabled: Boolean,
    primary: Boolean,
    requestInitialFocus: Boolean = false,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(46.dp)
            .focusRequester(focusRequester)
            .clip(shape)
            .background(
                when {
                    !enabled -> ViviCastColors.SurfaceRaised.copy(alpha = 0.55f)
                    focused -> ViviCastColors.FocusFill
                    primary -> ViviCastColors.Selected
                    else -> ViviCastColors.SurfaceRaised
                }
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> ViviCastColors.Focus
                    primary -> ViviCastColors.Accent
                    else -> ViviCastColors.Line
                },
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .activateOnCenter { if (enabled) onClick() }
            .focusable(enabled)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else ViviCastColors.TextMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

