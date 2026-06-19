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
fun M3uUrlImportDialog(
    importing: Boolean,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val inputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }

    fun submit() {
        val normalizedUrl = url.trim()
        val validationError = normalizedUrl.validatePlaylistUrl()
        if (validationError != null) {
            localError = validationError
        } else {
            localError = null
            onImport(normalizedUrl)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 220.dp, vertical = 42.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ViviCastColors.Surface)
                .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Add M3U URL",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Paste or type the playlist URL from your IPTV provider.",
                    color = ViviCastColors.TextSecondary,
                    fontSize = 14.sp
                )
            }

            M3uUrlInput(
                value = url,
                enabled = !importing,
                error = localError,
                focusRequester = inputFocusRequester,
                onValueChange = {
                    url = it
                    localError = null
                },
                onSubmit = ::submit
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogActionButton(
                    text = if (importing) "Importing..." else "Import",
                    enabled = !importing,
                    primary = true,
                    onClick = ::submit
                )
                DialogActionButton(
                    text = "Cancel",
                    enabled = !importing,
                    primary = false,
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
fun XtreamLoginDialog(
    importing: Boolean,
    onDismiss: () -> Unit,
    onImport: (XtreamCredentials) -> Unit
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val serverFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        serverFocusRequester.requestFocus()
    }

    fun submit() {
        val normalizedServerUrl = serverUrl.trim().trimEnd('/')
        val validationError = when {
            normalizedServerUrl.validatePlaylistUrl() != null -> "Enter a valid server URL."
            username.isBlank() -> "Enter your Xtream username."
            password.isBlank() -> "Enter your Xtream password."
            else -> null
        }

        if (validationError != null) {
            localError = validationError
        } else {
            localError = null
            onImport(
                XtreamCredentials(
                    baseUrl = normalizedServerUrl,
                    username = username.trim(),
                    password = password
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 220.dp, vertical = 82.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ViviCastColors.Surface)
                .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Xtream Login",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Enter the server URL and credentials from your IPTV provider.",
                    color = ViviCastColors.TextSecondary,
                    fontSize = 14.sp
                )
            }

            TvTextInput(
                value = serverUrl,
                enabled = !importing,
                placeholder = "http://provider.example:8080",
                error = localError,
                focusRequester = serverFocusRequester,
                onValueChange = {
                    serverUrl = it
                    localError = null
                },
                onSubmit = ::submit
            )
            TvTextInput(
                value = username,
                enabled = !importing,
                placeholder = "Username",
                error = null,
                onValueChange = {
                    username = it
                    localError = null
                },
                onSubmit = ::submit
            )
            TvTextInput(
                value = password,
                enabled = !importing,
                placeholder = "Password",
                error = null,
                visualTransformation = PasswordVisualTransformation(),
                onValueChange = {
                    password = it
                    localError = null
                },
                onSubmit = ::submit
            )

            Text(
                text = localError ?: "Only live streams are imported in the current MVP.",
                color = if (localError == null) ViviCastColors.TextSecondary else ViviCastColors.Error,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogActionButton(
                    text = if (importing) "Importing..." else "Import",
                    enabled = !importing,
                    primary = true,
                    onClick = ::submit
                )
                DialogActionButton(
                    text = "Cancel",
                    enabled = !importing,
                    primary = false,
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
fun EpgSourceDialog(
    title: String,
    importing: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var sourceName by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val inputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }

    fun submit() {
        val trimmedUrl = sourceUrl.trim()
        val validationError = when {
            trimmedUrl.validatePlaylistUrl() != null -> "Enter a valid XMLTV URL."
            sourceName.trim().isBlank() -> "Enter a source name."
            else -> null
        }
        if (validationError != null) {
            localError = validationError
        } else {
            localError = null
            onSubmit(sourceName.trim(), trimmedUrl)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 220.dp, vertical = 82.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ViviCastColors.Surface)
                .border(1.dp, ViviCastColors.Line, RoundedCornerShape(8.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Reusable XMLTV source for one or more providers.",
                    color = ViviCastColors.TextSecondary,
                    fontSize = 14.sp
                )
            }

            TvTextInput(
                value = sourceName,
                enabled = !importing,
                placeholder = "EPG source name",
                error = localError,
                focusRequester = inputFocusRequester,
                onValueChange = {
                    sourceName = it
                    localError = null
                },
                onSubmit = ::submit
            )
            TvTextInput(
                value = sourceUrl,
                enabled = !importing,
                placeholder = "https://provider.example/xmltv.xml",
                error = null,
                onValueChange = {
                    sourceUrl = it
                    localError = null
                },
                onSubmit = ::submit
            )

            Text(
                text = localError ?: "This source can later be assigned to multiple providers.",
                color = if (localError == null) ViviCastColors.TextSecondary else ViviCastColors.Error,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogActionButton(
                    text = if (importing) "Saving..." else "Save",
                    enabled = !importing,
                    primary = true,
                    onClick = ::submit
                )
                DialogActionButton(
                    text = "Cancel",
                    enabled = !importing,
                    primary = false,
                    onClick = onDismiss
                )
            }
        }
    }
}

