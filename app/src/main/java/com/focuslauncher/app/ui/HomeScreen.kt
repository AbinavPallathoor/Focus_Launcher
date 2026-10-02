package com.focuslauncher.app.ui

import android.graphics.Rect
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.focuslauncher.app.AppInfo
import com.focuslauncher.app.DeviceUsage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val SWIPE_UP_OPEN_THRESHOLD_PX = -60f
private const val SWIPE_HORIZONTAL_THRESHOLD_PX = 90f
private const val SCREEN_TIME_POLL_MS = 60_000L
// The upper dial sweeps a 180° arc, so a downward-pointing node sits a full radius
// *below* its own anchor — this needs to clear the bottom dial's anchor/reveal area
// below it, or the two visually collide and the upper dial reads as "cut off".
private val UPPER_DIAL_BOTTOM_PADDING = 230.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    bottomDialApps: List<AppInfo>,
    upperDialApps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
) {
    val context = LocalContext.current
    val time = rememberCurrentTime()
    val dateText = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date()) }
    var accumulatedDx by remember { mutableStateOf(0f) }
    var accumulatedDy by remember { mutableStateOf(0f) }
    var gestureResolved by remember { mutableStateOf(false) }
    val view = LocalView.current

    var hasUsageAccess by remember { mutableStateOf(DeviceUsage.hasUsageAccess(context)) }
    var screenTimeMillis by remember { mutableStateOf(DeviceUsage.todayScreenTimeMillis(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            hasUsageAccess = DeviceUsage.hasUsageAccess(context)
            if (hasUsageAccess) screenTimeMillis = DeviceUsage.todayScreenTimeMillis(context)
            delay(SCREEN_TIME_POLL_MS)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .onGloballyPositioned { coordinates ->
                // Claim the bottom strip from the system's edge gesture nav so a swipe
                // starting near the bottom still reaches our own drag detector below.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val bounds = coordinates.boundsInWindow()
                    val exclusionHeightPx = 48 * view.resources.displayMetrics.density
                    view.systemGestureExclusionRects = listOf(
                        Rect(
                            bounds.left.toInt(),
                            (bounds.bottom - exclusionHeightPx).toInt(),
                            bounds.right.toInt(),
                            bounds.bottom.toInt()
                        )
                    )
                }
            }
            // Swipe up anywhere on the home screen opens search; swipe left/right trigger
            // their own quick actions. A plain tap is unaffected since no drag occurs.
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        accumulatedDx = 0f
                        accumulatedDy = 0f
                        gestureResolved = false
                    },
                    onDrag = dragHandler@{ change, amount ->
                        if (gestureResolved) return@dragHandler
                        accumulatedDx += amount.x
                        accumulatedDy += amount.y
                        val absDx = abs(accumulatedDx)
                        val absDy = abs(accumulatedDy)
                        if (accumulatedDy < SWIPE_UP_OPEN_THRESHOLD_PX && absDy > absDx) {
                            change.consume()
                            gestureResolved = true
                            onOpenSearch()
                        } else if (absDx > SWIPE_HORIZONTAL_THRESHOLD_PX && absDx > absDy) {
                            change.consume()
                            gestureResolved = true
                            if (accumulatedDx < 0) onSwipeLeft() else onSwipeRight()
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 72.dp)
                    .width(IntrinsicSize.Max)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = onOpenSettings,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DotMatrixClock(time = time)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Box(modifier = Modifier.weight(1f))
                    Text(
                        text = if (hasUsageAccess) {
                            DeviceUsage.formatDuration(screenTimeMillis)
                        } else {
                            "Enable screen time"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable(enabled = !hasUsageAccess) {
                            DeviceUsage.openUsageAccessSettings(context)
                        }
                    )
                }
            }
        }

        // Visual affordance only — the swipe-up-to-search gesture is handled by the
        // root Box above so it works from anywhere on the home screen.
        SwipeUpIndicator(modifier = Modifier.align(Alignment.BottomCenter))

        RadialAppMenu(
            apps = upperDialApps,
            onLaunch = onLaunch,
            modifier = Modifier.align(Alignment.BottomEnd),
            bottomPadding = UPPER_DIAL_BOTTOM_PADDING,
            startAngleDeg = -90f,
            sweepDeg = 180f,
        )
        RadialAppMenu(
            apps = bottomDialApps,
            onLaunch = onLaunch,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun SwipeUpIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .background(SubtextGrey.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )
    }
}
