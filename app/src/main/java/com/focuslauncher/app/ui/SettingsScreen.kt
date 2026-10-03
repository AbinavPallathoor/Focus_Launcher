package com.focuslauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslauncher.app.AppInfo
import com.focuslauncher.app.RADIAL_GROUP_BOTTOM
import com.focuslauncher.app.RADIAL_GROUP_UPPER
import com.focuslauncher.app.SLOTS_PER_RADIAL_GROUP
import com.focuslauncher.app.SwipeGesture
import kotlin.math.cos
import kotlin.math.sin

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
            item {
                RadialDialPreview(
                    slots = List(SLOTS_PER_RADIAL_GROUP) { index -> resolveSlot(RADIAL_GROUP_BOTTOM, index) },
                    startAngleDeg = 0f,
                    sweepDeg = 90f,
                    anchoredAtCenter = false,
                    boxHeight = 200.dp,
                    onTapSlot = { index -> onPickSlot(RADIAL_GROUP_BOTTOM, index) },
                    onClearSlot = { index -> onClearSlot(RADIAL_GROUP_BOTTOM, index) },
                )
            }
            item { SectionHeader("Upper dial") }
            item {
                RadialDialPreview(
                    slots = List(SLOTS_PER_RADIAL_GROUP) { index -> resolveSlot(RADIAL_GROUP_UPPER, index) },
                    startAngleDeg = -90f,
                    sweepDeg = 180f,
                    anchoredAtCenter = true,
                    boxHeight = 260.dp,
                    onTapSlot = { index -> onPickSlot(RADIAL_GROUP_UPPER, index) },
                    onClearSlot = { index -> onClearSlot(RADIAL_GROUP_UPPER, index) },
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

private val PREVIEW_RADIUS = 96.dp
private val PREVIEW_ANCHOR_INSET = 32.dp
private val PREVIEW_NODE_DIAMETER = 40.dp

/**
 * A small, non-interactive-arc replica of the actual on-screen dial: nodes laid out along
 * the same [startAngleDeg]/[sweepDeg] sector so it's immediately clear which physical pull
 * direction launches which app. Tap a node to assign/replace that slot; long-press a filled
 * one to clear it. [anchoredAtCenter] mirrors the real upper dial's 180° sweep, which needs
 * room both above and below its anchor rather than just above it.
 */
@Composable
private fun RadialDialPreview(
    slots: List<AppInfo?>,
    startAngleDeg: Float,
    sweepDeg: Float,
    anchoredAtCenter: Boolean,
    boxHeight: Dp,
    onTapSlot: (Int) -> Unit,
    onClearSlot: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { PREVIEW_RADIUS.toPx() }
    val insetPx = with(density) { PREVIEW_ANCHOR_INSET.toPx() }

    fun nodeAngleRad(index: Int) =
        Math.toRadians((startAngleDeg + (index + 0.5f) / slots.size * sweepDeg).toDouble())

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(boxHeight)
    ) {
        val anchorDpX = maxWidth - PREVIEW_ANCHOR_INSET
        val anchorDpY = if (anchoredAtCenter) maxHeight / 2 else maxHeight - PREVIEW_ANCHOR_INSET

        Canvas(modifier = Modifier.fillMaxSize()) {
            val anchor = Offset(
                size.width - insetPx,
                if (anchoredAtCenter) size.height / 2f else size.height - insetPx
            )
            drawArc(
                color = SubtextGrey.copy(alpha = 0.3f),
                startAngle = startAngleDeg + 180f,
                sweepAngle = sweepDeg,
                useCenter = false,
                topLeft = Offset(anchor.x - radiusPx, anchor.y - radiusPx),
                size = Size(radiusPx * 2, radiusPx * 2),
                style = Stroke(width = 1.5.dp.toPx())
            )
            for (i in slots.indices) {
                val angleRad = nodeAngleRad(i)
                val nodeOffset = Offset(
                    anchor.x - (cos(angleRad) * radiusPx).toFloat(),
                    anchor.y - (sin(angleRad) * radiusPx).toFloat()
                )
                drawLine(
                    color = SubtextGrey.copy(alpha = 0.25f),
                    start = anchor,
                    end = nodeOffset,
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        for (i in slots.indices) {
            val angleRad = nodeAngleRad(i)
            val nodeX = anchorDpX - PREVIEW_RADIUS * cos(angleRad).toFloat()
            val nodeY = anchorDpY - PREVIEW_RADIUS * sin(angleRad).toFloat()
            PreviewNode(
                app = slots[i],
                modifier = Modifier.offset(x = nodeX - PREVIEW_NODE_DIAMETER / 2, y = nodeY - PREVIEW_NODE_DIAMETER / 2),
                onTap = { onTapSlot(i) },
                onClear = { onClearSlot(i) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewNode(
    app: AppInfo?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(PREVIEW_NODE_DIAMETER)
            .background(if (app != null) PureWhite else PureBlack, CircleShape)
            .border(BorderStroke(1.dp, SubtextGrey.copy(alpha = 0.5f)), CircleShape)
            .combinedClickable(
                onClick = onTap,
                onLongClick = if (app != null) onClear else null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = app?.let { abbreviate(it.label) } ?: "+",
            style = MaterialTheme.typography.bodySmall,
            color = if (app != null) PureBlack else SubtextGrey,
            maxLines = 1,
            overflow = TextOverflow.Clip,
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
