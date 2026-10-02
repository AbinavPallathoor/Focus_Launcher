package com.focuslauncher.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.sqrt

private const val BURST_DURATION_MS = 420

/**
 * Full-screen dot-grid reveal that radiates outward in a circle from the bottom of the
 * screen — played when swiping up into search. Calls [onComplete] once fully expanded.
 */
@Composable
fun DotGridBurst(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
) {
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(BURST_DURATION_MS, easing = FastOutSlowInEasing))
        onComplete()
    }

    Canvas(modifier = modifier.fillMaxSize().background(PureBlack)) {
        val spacing = with(density) { 28.dp.toPx() }
        val dotRadius = with(density) { 2.5.dp.toPx() }
        val origin = Offset(size.width / 2f, size.height)
        val maxRadius = hypot(size.width, size.height)
        val currentRadius = maxRadius * progress.value
        val edgeBand = spacing * 3f

        var y = 0f
        while (y <= size.height) {
            var x = 0f
            while (x <= size.width) {
                val dx = x - origin.x
                val dy = y - origin.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= currentRadius) {
                    val edgeFade = ((currentRadius - dist) / edgeBand).coerceIn(0f, 1f)
                    drawCircle(
                        color = PureWhite.copy(alpha = edgeFade),
                        radius = dotRadius,
                        center = Offset(x, y)
                    )
                }
                x += spacing
            }
            y += spacing
        }
    }
}
