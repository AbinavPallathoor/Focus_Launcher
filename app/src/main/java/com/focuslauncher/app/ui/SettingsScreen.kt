package com.focuslauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslauncher.app.AppInfo
import com.focuslauncher.app.RADIAL_GROUP_BOTTOM
import com.focuslauncher.app.RADIAL_GROUP_UPPER
import com.focuslauncher.app.SLOTS_PER_RADIAL_GROUP
import com.focuslauncher.app.SwipeGesture

/**
 * Lets the user assign which apps appear on each of the two radial dial menus, override
 * the default swipe-left/swipe-right home screen actions, and unhide apps hidden from search.
 */
@Composable
fun SettingsScreen(
    resolveSlot: (group: Int, index: Int) -> AppInfo?,
    onPickSlot: (group: Int, index: Int) -> Unit,
    onClearSlot: (group: Int, index: Int) -> Unit,
    resolveGesture: (gesture: SwipeGesture) -> AppInfo?,
    onPickGesture: (gesture: SwipeGesture) -> Unit,
    onClearGesture: (gesture: SwipeGesture) -> Unit,
    hiddenApps: List<AppInfo>,
    onUnhideApp: (AppInfo) -> Unit,
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

        LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
            item { SectionHeader("Bottom dial") }
            items(SLOTS_PER_RADIAL_GROUP) { index ->
                SlotRow(
                    app = resolveSlot(RADIAL_GROUP_BOTTOM, index),
                    onTap = { onPickSlot(RADIAL_GROUP_BOTTOM, index) },
                    onClear = { onClearSlot(RADIAL_GROUP_BOTTOM, index) },
                )
            }
            item { SectionHeader("Upper dial") }
            items(SLOTS_PER_RADIAL_GROUP) { index ->
                SlotRow(
                    app = resolveSlot(RADIAL_GROUP_UPPER, index),
                    onTap = { onPickSlot(RADIAL_GROUP_UPPER, index) },
                    onClear = { onClearSlot(RADIAL_GROUP_UPPER, index) },
                )
            }
            item { SectionHeader("Gestures") }
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
            if (hiddenApps.isNotEmpty()) {
                item { SectionHeader("Hidden apps") }
                items(hiddenApps, key = { it.key }) { app ->
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SubtextGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUnhideApp(app) }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }
    }
}

/** A small uppercase label over a thin rule — keeps sections visually separate without cards. */
@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = SubtextGrey,
            letterSpacing = 1.sp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(1.dp)
                .background(SubtextGrey.copy(alpha = 0.25f))
        )
    }
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
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = if (app != null) onClear else null,
            )
            .padding(vertical = 10.dp)
    )
}
