package com.focuslauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focuslauncher.app.AppInfo
import com.focuslauncher.app.RADIAL_GROUP_BOTTOM
import com.focuslauncher.app.RADIAL_GROUP_UPPER
import com.focuslauncher.app.SLOTS_PER_RADIAL_GROUP
import com.focuslauncher.app.SwipeGesture

/**
 * Lets the user assign which apps appear on each of the two radial dial menus, and
 * override the default swipe-left/swipe-right home screen actions.
 */
@Composable
fun SettingsScreen(
    resolveSlot: (group: Int, index: Int) -> AppInfo?,
    onPickSlot: (group: Int, index: Int) -> Unit,
    onClearSlot: (group: Int, index: Int) -> Unit,
    resolveGesture: (gesture: SwipeGesture) -> AppInfo?,
    onPickGesture: (gesture: SwipeGesture) -> Unit,
    onClearGesture: (gesture: SwipeGesture) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(horizontal = 24.dp, vertical = 48.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.bodyLarge, color = PureWhite)

        LazyColumn(modifier = Modifier.padding(top = 24.dp)) {
            item {
                GroupHeader("Bottom dial")
            }
            items(SLOTS_PER_RADIAL_GROUP) { index ->
                SlotRow(
                    app = resolveSlot(RADIAL_GROUP_BOTTOM, index),
                    onTap = { onPickSlot(RADIAL_GROUP_BOTTOM, index) },
                    onClear = { onClearSlot(RADIAL_GROUP_BOTTOM, index) },
                )
            }
            item {
                GroupHeader("Upper dial")
            }
            items(SLOTS_PER_RADIAL_GROUP) { index ->
                SlotRow(
                    app = resolveSlot(RADIAL_GROUP_UPPER, index),
                    onTap = { onPickSlot(RADIAL_GROUP_UPPER, index) },
                    onClear = { onClearSlot(RADIAL_GROUP_UPPER, index) },
                )
            }
            item {
                GroupHeader("Gestures")
            }
            item {
                SlotRow(
                    app = resolveGesture(SwipeGesture.LEFT),
                    defaultLabel = "Swipe left — Camera",
                    onTap = { onPickGesture(SwipeGesture.LEFT) },
                    onClear = { onClearGesture(SwipeGesture.LEFT) },
                )
            }
            item {
                SlotRow(
                    app = resolveGesture(SwipeGesture.RIGHT),
                    defaultLabel = "Swipe right — Contacts",
                    onTap = { onPickGesture(SwipeGesture.RIGHT) },
                    onClear = { onClearGesture(SwipeGesture.RIGHT) },
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        color = SubtextGrey,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SlotRow(
    app: AppInfo?,
    onTap: () -> Unit,
    onClear: () -> Unit,
    defaultLabel: String = "+ Add app",
) {
    Text(
        text = app?.label ?: defaultLabel,
        style = MaterialTheme.typography.bodyLarge,
        color = if (app != null) PureWhite else SubtextGrey,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = if (app != null) onClear else null,
            )
            .padding(vertical = 10.dp)
    )
}
