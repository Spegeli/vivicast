package com.vivicast.tv

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vivicast.tv.core.designsystem.ActionPill
import com.vivicast.tv.core.designsystem.R
import com.vivicast.tv.core.designsystem.VivicastButtonRow
import com.vivicast.tv.core.designsystem.LocalVivicastColors
import com.vivicast.tv.core.designsystem.VivicastColors
import com.vivicast.tv.core.designsystem.VivicastDialog
import com.vivicast.tv.core.designsystem.VivicastDialogWidth
import com.vivicast.tv.core.designsystem.VivicastFocusSurface
import com.vivicast.tv.core.designsystem.VivicastSpacing
import com.vivicast.tv.core.designsystem.VivicastTypography
import kotlinx.coroutines.delay
import java.io.File

enum class FilePickerMode { FILE, FOLDER }

/**
 * In-app file/folder picker — the TV-safe replacement for SAF. [FilePickerMode.FILE] picks a file on
 * tap (filtered by [fileExtensions]); [FilePickerMode.FOLDER] navigates and confirms via "use this
 * folder". Storage permission is requested lazily: opening a protected root (internal / removable)
 * without access triggers the system prompt (runtime READ on API ≤29, All-files access on 30+) and,
 * once granted, enters that root automatically. The permission-free Android/media root always works.
 */
@Composable
fun FilePickerDialog(
    title: String,
    mode: FilePickerMode,
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
    startDir: File? = null,
    fileExtensions: Set<String>? = null,
) {
    val context = LocalContext.current
    val roots = remember { StorageAccess.storageRoots(context) }
    val rootPaths = remember(roots) { roots.map { it.dir.absolutePath }.toSet() }
    var hasAccess by remember { mutableStateOf(StorageAccess.hasFullFileAccess(context)) }
    var current by remember { mutableStateOf(usableStartDir(startDir, hasAccess)) }
    // The protected root the user tapped while access was still missing; entered once it's granted.
    var pendingRoot by remember { mutableStateOf<File?>(null) }
    val firstFocus = remember { FocusRequester() }

    // Can this device ever reach full-storage access? True if already granted, or a runtime READ dialog
    // (API ≤29) or an All-files settings screen (API 30+) exists. When true we show the real storage
    // roots and hide the permission-free App-folder fallback; when false (API 30+ TV with no grant
    // screen) we show only the App folder, since internal/USB can't be unlocked anyway.
    val canReachFullAccess = remember(hasAccess) { canReachFullAccess(context, hasAccess) }
    val visibleRoots = remember(roots, canReachFullAccess) {
        roots.filter { it.permissionFree != canReachFullAccess }.ifEmpty { roots }
    }

    fun enterPendingIfGranted() {
        val target = pendingRoot
        if (hasAccess && target != null) {
            current = target
            pendingRoot = null
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        hasAccess = StorageAccess.hasFullFileAccess(context)
        enterPendingIfGranted()
    }

    // All-files-access grants happen on a separate Settings screen (no result callback), so re-check
    // on resume when the user comes back — and enter the pending root if it was granted.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = StorageAccess.hasFullFileAccess(context)
                enterPendingIfGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openRoot(root: StorageAccess.Root) {
        when {
            root.permissionFree || hasAccess -> current = root.dir
            StorageAccess.needsRuntimeStoragePermission(context) -> {
                pendingRoot = root.dir
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            StorageAccess.canRequestAllFilesAccess(context) -> {
                pendingRoot = root.dir
                StorageAccess.requestAllFilesAccess(context)
            }
            else -> Toast.makeText(context, context.getString(R.string.file_picker_no_access), Toast.LENGTH_LONG).show()
        }
    }

    fun goUp() {
        val dir = current ?: return onDismiss()
        current = parentInTree(dir, rootPaths)
    }

    // Re-grab focus on open / navigation / access change so the D-pad lands on the list, not behind it.
    LaunchedEffect(current, hasAccess) {
        delay(FOCUS_SETTLE_MILLIS)
        runCatching { firstFocus.requestFocus() }
    }

    // Back navigates up one level (VivicastDialog routes BACK here); at the roots list it dismisses.
    VivicastDialog(
        onDismiss = { goUp() },
        width = VivicastDialogWidth.Standard,
        heightCap = 560.dp,
        title = title,
    ) {
        BasicText(
            text = current?.absolutePath ?: stringResource(R.string.file_picker_pick_location),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = VivicastTypography.LabelSmall.copy(color = VivicastColors.TextTertiary),
            modifier = Modifier.fillMaxWidth(),
        )

        val dir = current
        FilePickerList(
            dir = dir,
            roots = visibleRoots,
            hasAccess = hasAccess,
            mode = mode,
            fileExtensions = fileExtensions,
            firstFocus = firstFocus,
            onOpenRoot = { openRoot(it) },
            onNavigate = { current = it },
            onGoUp = { goUp() },
            onPick = onPick,
        )

        VivicastButtonRow {
            ActionPill(stringResource(R.string.common_cancel), onClick = onDismiss)
            if (mode == FilePickerMode.FOLDER && dir != null) {
                ActionPill(stringResource(R.string.file_picker_use_folder), onClick = { onPick(dir) })
            }
        }
    }
}

@Composable
private fun FilePickerList(
    dir: File?,
    roots: List<StorageAccess.Root>,
    hasAccess: Boolean,
    mode: FilePickerMode,
    fileExtensions: Set<String>?,
    firstFocus: FocusRequester,
    onOpenRoot: (StorageAccess.Root) -> Unit,
    onNavigate: (File) -> Unit,
    onGoUp: () -> Unit,
    onPick: (File) -> Unit,
) {
    val children = remember(dir, hasAccess) {
        runCatching { dir?.listFiles()?.toList() }.getOrNull().orEmpty()
    }
    val folders = children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
    val files = if (mode == FilePickerMode.FILE) {
        children
            .filter { it.isFile && (fileExtensions == null || it.extension.lowercase() in fileExtensions) }
            .sortedBy { it.name.lowercase() }
    } else {
        emptyList()
    }

    LazyColumn(
        modifier = Modifier.heightIn(max = 340.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space1),
    ) {
        if (dir == null) {
            itemsIndexed(roots) { index, root ->
                val focusModifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                PickerRow(stringResource(root.labelRes), focusModifier, trailingChevron = true) {
                    onOpenRoot(root)
                }
            }
        } else {
            item {
                PickerRow("..", Modifier.focusRequester(firstFocus), trailingChevron = true, onClick = onGoUp)
            }
            items(folders, key = { it.absolutePath }) { folder ->
                PickerRow(folder.name, trailingChevron = true) { onNavigate(folder) }
            }
            items(files, key = { it.absolutePath }) { file ->
                PickerRow(file.name) { onPick(file) }
            }
        }
    }
}

@Composable
private fun PickerRow(
    label: String,
    modifier: Modifier = Modifier,
    trailingChevron: Boolean = false,
    onClick: () -> Unit,
) {
    VivicastFocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = 0.dp,
        focusScale = 1.0f,
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = VivicastSpacing.Space3, vertical = VivicastSpacing.Space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = VivicastTypography.LabelMedium.copy(
                    color = if (focused) LocalVivicastColors.current.focusRing else VivicastColors.TextPrimary,
                ),
                modifier = Modifier.weight(1f),
            )
            if (trailingChevron) {
                Spacer(Modifier.width(VivicastSpacing.Space2))
                BasicText(
                    text = "›",
                    style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextSecondary),
                )
            }
        }
    }
}

/** The picker's initial directory: the caller's [startDir] only if it's a usable, accessible folder. */
private fun usableStartDir(startDir: File?, hasAccess: Boolean): File? =
    startDir?.takeIf { hasAccess && it.exists() && it.isDirectory }

/** Whether full-storage access is obtainable here: already granted, or a runtime READ dialog (API ≤29)
 *  or an All-files settings screen (API 30+) exists to request it through. */
private fun canReachFullAccess(context: android.content.Context, hasAccess: Boolean): Boolean =
    hasAccess ||
        StorageAccess.needsRuntimeStoragePermission(context) ||
        StorageAccess.canRequestAllFilesAccess(context)

/** Parent within the browse tree: null (→ back to the roots list) when [dir] is itself a root. */
private fun parentInTree(dir: File, rootPaths: Set<String>): File? =
    if (dir.absolutePath in rootPaths) null else dir.parentFile

private const val FOCUS_SETTLE_MILLIS = 120L
