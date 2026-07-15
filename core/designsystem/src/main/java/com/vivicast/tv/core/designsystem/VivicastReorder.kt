package com.vivicast.tv.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** One reorderable row: [id] is stable (used as the list key + returned order); [label] is shown. */
data class VivicastReorderItem(val id: String, val label: String)

/**
 * D-Pad reorderable list (grab-and-move): focus a row, press OK to pick it up (accent + ↕), move it with
 * ▲/▼, press OK to drop, Back to cancel back to where the pickup started. Neighbours slide via
 * `animateItem`. Stateless from the caller's view — it holds only local pickup/focus state and reports the
 * new order to [onReorder] when an item is dropped.
 *
 * Embeddable: drop it inline in a dialog (e.g. logo priority) or inside a dedicated overlay for long lists.
 *
 * ponytail: ±1 per press, no hold-to-accelerate / ◄►±10 yet — pointless for the 3-item logo caller; add
 * when a long-list caller (channels/groups) lands.
 */
@Composable
fun VivicastReorderList(
    items: List<VivicastReorderItem>,
    onReorder: (orderedIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var order by remember(items) { mutableStateOf(items) }
    var pickedId by remember(items) { mutableStateOf<String?>(null) }
    var snapshotAtPickup by remember(items) { mutableStateOf<List<VivicastReorderItem>?>(null) }
    var moveTick by remember(items) { mutableIntStateOf(0) }
    val requesters = remember(items) { items.associate { it.id to FocusRequester() } }

    // Focus the first row on open so the list is immediately interactable (host is a dialog).
    LaunchedEffect(items) {
        withFrameNanos {}
        items.firstOrNull()?.let { requesters[it.id]?.runCatching { requestFocus() } }
    }
    // Keep focus on the picked item after it moves — re-request next frame as a safety net (keyed items
    // usually retain focus across reorder on their own).
    LaunchedEffect(moveTick) {
        val id = pickedId ?: return@LaunchedEffect
        withFrameNanos {}
        requesters[id]?.requestFocus()
    }

    fun move(id: String, delta: Int) {
        val i = order.indexOfFirst { it.id == id }
        val target = i + delta
        if (i >= 0 && target in order.indices) {
            order = order.toMutableList().apply { add(target, removeAt(i)) }
            moveTick++
        }
    }
    fun toggle(id: String) {
        when (pickedId) {
            null -> { snapshotAtPickup = order; pickedId = id }
            id -> { pickedId = null; snapshotAtPickup = null; onReorder(order.map { it.id }) }
            else -> Unit
        }
    }
    fun cancel() {
        order = snapshotAtPickup ?: order
        pickedId = null
        snapshotAtPickup = null
        moveTick++
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VivicastSpacing.Space2)) {
            itemsIndexed(order, key = { _, item -> item.id }) { index, item ->
                ReorderRow(
                    label = item.label,
                    position = index + 1,
                    isPicked = item.id == pickedId,
                    requester = requesters.getValue(item.id),
                    onToggle = { toggle(item.id) },
                    onMove = { delta -> move(item.id, delta) },
                    onCancel = { cancel() },
                    modifier = Modifier.fillMaxWidth().animateItem(),
                )
            }
        }
        BasicText(
            text = stringResource(if (pickedId != null) R.string.reorder_hint_active else R.string.reorder_hint_idle),
            style = VivicastTypography.LabelSmall.copy(color = VivicastColors.TextSecondary),
            modifier = Modifier.padding(top = VivicastSpacing.Space2),
        )
    }
}

@Composable
private fun ReorderRow(
    label: String,
    position: Int,
    isPicked: Boolean,
    requester: FocusRequester,
    onToggle: () -> Unit,
    onMove: (delta: Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A Back that cancels a pickup is a KeyDown; its trailing KeyUp must also be swallowed here, else the
    // host dialog (which dismisses on Back KeyUp) would close on the same press.
    var swallowBackUp by remember { mutableStateOf(false) }
    FocusPanel(
        selected = isPicked,
        onClick = onToggle,
        modifier = modifier
            .focusRequester(requester)
            .onPreviewKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown
                when {
                    event.key == Key.Back && down && isPicked -> { onCancel(); swallowBackUp = true; true }
                    event.key == Key.Back && !down && swallowBackUp -> { swallowBackUp = false; true }
                    isPicked && down && event.key == Key.DirectionUp -> { onMove(-1); true }
                    isPicked && down && event.key == Key.DirectionDown -> { onMove(1); true }
                    else -> false
                }
            },
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VivicastSpacing.Space3),
        ) {
            BasicText(
                text = "$position",
                style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextSecondary),
                modifier = Modifier.width(24.dp),
            )
            BasicText(
                text = label,
                style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary),
                modifier = Modifier.weight(1f),
            )
            if (isPicked) {
                BasicText(text = "↕", style = VivicastTypography.LabelMedium.copy(color = VivicastColors.TextPrimary))
            }
        }
    }
}
