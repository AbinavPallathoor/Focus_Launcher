package com.focuslauncher.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focuslauncher.app.AppInfo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val RADIUS = 150.dp
private val ANCHOR_INSET = 40.dp
private val NODE_DIAMETER = 44.dp
private val MIN_SELECT_DISTANCE = 28.dp
private val CALLOUT_DIAGONAL = 32.dp
private val CALLOUT_HORIZONTAL = 36.dp
private val CALLOUT_BOX_WIDTH = 150.dp

/** "Google Photos" -> "GP", "Chrome" -> "CH". */
private fun abbreviate(label: String): String {
    val words = label.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> (words[0].take(1) + words[1].take(1)).uppercase()
        words.size == 1 -> words[0].take(2).uppercase()
        else -> ""
    }
}

/**
 * Pull-and-rotate app launcher anchored at the bottom-right corner. Drag from the handle;
 * as the finger sweeps through the arc, the nearest app lights up and its name glides into
 * a callout to the left. Releasing on a lit node launches it. [bottomPadding] stacks
 * multiple instances vertically along the same corner; [startAngleDeg]/[sweepDeg] control
 * the arc, measured from pointing left (0°) increasing clockwise toward up (90°).
 */
@Composable
fun RadialAppMenu(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    startAngleDeg: Float = 0f,
    sweepDeg: Float = 90f,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // A plain tween (no overshoot) keeps the nodes and the live drag line growing in
    // perfect lockstep — a spring here was the source of the visible desync glitch.
    val reveal by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "radialReveal"
    )

    val density = LocalDensity.current
    val radiusPx = with(density) { RADIUS.toPx() }
    val anchorInsetPx = with(density) { ANCHOR_INSET.toPx() }
    val minSelectPx = with(density) { MIN_SELECT_DISTANCE.toPx() }

    fun indexForOffset(offset: Offset): Int? {
        if (apps.isEmpty()) return null
        val distance = sqrt(offset.x * offset.x + offset.y * offset.y)
        if (distance < minSelectPx) return null
        val angleDeg = Math.toDegrees(atan2((-offset.y).toDouble(), (-offset.x).toDouble())).toFloat()
        if (angleDeg < startAngleDeg || angleDeg > startAngleDeg + sweepDeg) return null
        return (((angleDeg - startAngleDeg) / sweepDeg) * apps.size).toInt().coerceIn(0, apps.size - 1)
    }

    fun nodeAngleRad(index: Int) =
        Math.toRadians((startAngleDeg + (index + 0.5f) / apps.size * sweepDeg).toDouble())

    Box(modifier = modifier.fillMaxSize()) {
        if (reveal > 0.01f) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f * reveal))
            ) {
                val anchorDpX = maxWidth - ANCHOR_INSET
                val anchorDpY = maxHeight - ANCHOR_INSET - bottomPadding

                // Remember the last selected node's position so the callout can glide
                // smoothly between apps instead of snapping, and hold still while fading
                // out rather than sliding back to the anchor.
                var lastNodeDpX by remember { mutableStateOf(anchorDpX) }
                var lastNodeDpY by remember { mutableStateOf(anchorDpY) }
                if (selectedIndex != null) {
                    val r = RADIUS * reveal
                    val angleRad = nodeAngleRad(selectedIndex!!)
                    lastNodeDpX = anchorDpX - r * cos(angleRad).toFloat()
                    lastNodeDpY = anchorDpY - r * sin(angleRad).toFloat()
                }
                val animatedNodeX by animateDpAsState(lastNodeDpX, label = "calloutX")
                val animatedNodeY by animateDpAsState(lastNodeDpY, label = "calloutY")

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val anchor = Offset(
                        size.width - anchorInsetPx,
                        size.height - anchorInsetPx - with(density) { bottomPadding.toPx() }
                    )
                    val r = radiusPx * reveal

                    drawArc(
                        color = SubtextGrey.copy(alpha = 0.3f * reveal),
                        startAngle = startAngleDeg + 180f,
                        sweepAngle = sweepDeg,
                        useCenter = false,
                        topLeft = Offset(anchor.x - r, anchor.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    for (i in apps.indices) {
                        val angleRad = nodeAngleRad(i)
                        val nodeOffset = Offset(
                            anchor.x - (cos(angleRad) * r).toFloat(),
                            anchor.y - (sin(angleRad) * r).toFloat()
                        )
                        val selected = selectedIndex == i
                        drawLine(
                            color = if (selected) PureWhite else SubtextGrey.copy(alpha = 0.35f * reveal),
                            start = anchor,
                            end = nodeOffset,
                            strokeWidth = if (selected) 2.5.dp.toPx() else 1.dp.toPx()
                        )
                    }

                    if (isDragging && apps.isNotEmpty()) {
                        // Clamp to the *currently revealed* radius, not the final target
                        // radius, so the live line never outruns the still-growing nodes.
                        val rawAngle = atan2(dragOffset.y, dragOffset.x)
                        val clampedDist = min(sqrt(dragOffset.x * dragOffset.x + dragOffset.y * dragOffset.y), r)
                        val fingerPoint = Offset(anchor.x + cos(rawAngle) * clampedDist, anchor.y + sin(rawAngle) * clampedDist)
                        drawLine(
                            color = PureWhite.copy(alpha = 0.6f),
                            start = anchor,
                            end = fingerPoint,
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Elbow leader line from the (animated) selected node: a 45° diagonal
                    // segment, then a bend to horizontal, pointing at the callout box.
                    if (selectedIndex != null) {
                        val nodePx = Offset(animatedNodeX.toPx(), animatedNodeY.toPx())
                        val cornerPx = Offset(nodePx.x - CALLOUT_DIAGONAL.toPx(), nodePx.y - CALLOUT_DIAGONAL.toPx())
                        val boxAnchorPx = Offset(cornerPx.x - CALLOUT_HORIZONTAL.toPx(), cornerPx.y)
                        drawLine(PureWhite, nodePx, cornerPx, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(PureWhite, cornerPx, boxAnchorPx, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    }
                }

                for (i in apps.indices) {
                    val angleRad = nodeAngleRad(i)
                    val r = RADIUS * reveal
                    val nodeX = anchorDpX - r * cos(angleRad).toFloat()
                    val nodeY = anchorDpY - r * sin(angleRad).toFloat()
                    RadialNode(
                        app = apps[i],
                        selected = selectedIndex == i,
                        modifier = Modifier.offset(x = nodeX - NODE_DIAMETER / 2, y = nodeY - NODE_DIAMETER / 2)
                    )
                }

                val selectedApp = selectedIndex?.let { apps.getOrNull(it) }
                if (selectedApp != null) {
                    val boxAnchorX = animatedNodeX - CALLOUT_DIAGONAL - CALLOUT_HORIZONTAL
                    val boxAnchorY = animatedNodeY - CALLOUT_DIAGONAL
                    Box(
                        modifier = Modifier
                            .offset(x = boxAnchorX - CALLOUT_BOX_WIDTH, y = boxAnchorY - 20.dp)
                            .width(CALLOUT_BOX_WIDTH)
                            .border(BorderStroke(1.dp, PureWhite.copy(alpha = 0.6f)))
                            .background(PureBlack)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        ScrambleText(
                            text = selectedApp.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PureWhite,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomPadding)
                .padding(ANCHOR_INSET - NODE_DIAMETER / 2)
                .size(NODE_DIAMETER)
                .pointerInput(apps) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragOffset = Offset.Zero
                            selectedIndex = null
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount
                            selectedIndex = indexForOffset(dragOffset)
                        },
                        onDragEnd = {
                            selectedIndex?.let { idx -> apps.getOrNull(idx)?.let(onLaunch) }
                            isDragging = false
                            selectedIndex = null
                        },
                        onDragCancel = {
                            isDragging = false
                            selectedIndex = null
                        }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(SubtextGrey.copy(alpha = 0.7f), CircleShape)
            )
        }
    }
}

@Composable
private fun RadialNode(
    app: AppInfo,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.3f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 500f),
        label = "nodeScale"
    )
    val background by animateColorAsState(
        targetValue = if (selected) PureWhite else PureBlack,
        label = "nodeBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) PureBlack else PureWhite,
        label = "nodeContentColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "nodeGlow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowScale"
    )

    Box(
        modifier = modifier.size(NODE_DIAMETER),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(NODE_DIAMETER)
                    .scale(glowScale)
                    .background(PureWhite.copy(alpha = 0.18f), CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(NODE_DIAMETER)
                .scale(scale)
                .background(background, CircleShape)
                .border(BorderStroke(1.dp, SubtextGrey.copy(alpha = 0.5f)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = abbreviate(app.label),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
