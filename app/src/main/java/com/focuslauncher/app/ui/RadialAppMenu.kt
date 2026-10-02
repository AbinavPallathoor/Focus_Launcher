package com.focuslauncher.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslauncher.app.AppInfo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val SWEEP_DEGREES = 90f
private val RADIUS = 150.dp
private val ANCHOR_INSET = 40.dp
private val NODE_DIAMETER = 44.dp
private val MIN_SELECT_DISTANCE = 28.dp

/**
 * Bottom-right pull-and-rotate app launcher. Drag from the corner handle; as the finger
 * sweeps through the quarter-circle, the nearest app lights up along an animated radial
 * graph. Releasing on a lit node launches it. [bottomPadding] stacks multiple instances
 * vertically along the same corner.
 */
@Composable
fun RadialAppMenu(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
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
        if (angleDeg < 0f || angleDeg > SWEEP_DEGREES) return null
        return (angleDeg / SWEEP_DEGREES * apps.size).toInt().coerceIn(0, apps.size - 1)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (reveal > 0.01f) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f * reveal))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val anchor = Offset(
                        size.width - anchorInsetPx,
                        size.height - anchorInsetPx - with(density) { bottomPadding.toPx() }
                    )
                    val r = radiusPx * reveal

                    val nodePoints = apps.indices.map { i ->
                        val angleRad = Math.toRadians(((i + 0.5f) / apps.size * SWEEP_DEGREES).toDouble())
                        Offset(
                            anchor.x - (cos(angleRad) * r).toFloat(),
                            anchor.y - (sin(angleRad) * r).toFloat()
                        )
                    }

                    drawArc(
                        color = SubtextGrey.copy(alpha = 0.3f * reveal),
                        startAngle = 180f,
                        sweepAngle = SWEEP_DEGREES,
                        useCenter = false,
                        topLeft = Offset(anchor.x - r, anchor.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // A connecting "graph" curve through every node, revealed as a
                    // trace animation that tracks the same reveal progress.
                    if (nodePoints.size >= 2) {
                        val path = Path().apply {
                            moveTo(nodePoints.first().x, nodePoints.first().y)
                            for (i in 1 until nodePoints.size) {
                                val prev = nodePoints[i - 1]
                                val curr = nodePoints[i]
                                val mid = Offset((prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
                                quadraticBezierTo(prev.x, prev.y, mid.x, mid.y)
                            }
                            lineTo(nodePoints.last().x, nodePoints.last().y)
                        }
                        val measure = PathMeasure().apply { setPath(path, false) }
                        val tracedPath = Path()
                        measure.getSegment(0f, measure.length * reveal, tracedPath, true)
                        drawPath(
                            path = tracedPath,
                            color = PureWhite.copy(alpha = 0.3f * reveal),
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    for (i in apps.indices) {
                        val selected = selectedIndex == i
                        drawLine(
                            color = if (selected) PureWhite else SubtextGrey.copy(alpha = 0.35f * reveal),
                            start = anchor,
                            end = nodePoints[i],
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
                }

                val anchorDpX = maxWidth - ANCHOR_INSET
                val anchorDpY = maxHeight - ANCHOR_INSET - bottomPadding
                for (i in apps.indices) {
                    val angleRad = Math.toRadians(((i + 0.5f) / apps.size * SWEEP_DEGREES).toDouble())
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
                AnimatedVisibility(
                    visible = selectedApp != null,
                    enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f),
                    exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.85f),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    ScrambleText(
                        text = selectedApp?.label ?: "",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 34.sp, letterSpacing = 1.sp),
                        color = HackerGreen,
                    )
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
                text = app.label.take(1).uppercase(),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
        }
    }
}
