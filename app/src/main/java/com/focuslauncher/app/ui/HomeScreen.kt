package com.focuslauncher.app.ui

import android.Manifest
import android.graphics.Rect
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslauncher.app.AppInfo
import com.focuslauncher.app.CalendarRepository
import com.focuslauncher.app.DeviceUsage
import com.focuslauncher.app.ExpenseCategory
import com.focuslauncher.app.ExpenseRepository
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private const val SWIPE_UP_OPEN_THRESHOLD_PX = -60f
private const val SWIPE_DOWN_OPEN_THRESHOLD_PX = 60f
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
    onSwipeDown: () -> Unit,
    expenseTrackerEnabled: Boolean,
    todayExpenseTotal: Double,
    monthlyExpenseTotal: Double,
    categoryTotalsMonth: Map<ExpenseCategory, Double>,
    untaggedExpenseCount: Int,
    onOpenExpenseTracker: () -> Unit,
    pendingQuestion: ExpenseRepository.PendingQuestion?,
    pendingQuestionCount: Int,
    onAnswerQuestion: (questionId: Long, answer: String) -> Unit,
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

    var hasCalendarPermission by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }
    var nextEvent by remember { mutableStateOf<CalendarRepository.UpcomingEvent?>(null) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCalendarPermission = granted }

    LaunchedEffect(Unit) {
        while (true) {
            hasUsageAccess = DeviceUsage.hasUsageAccess(context)
            if (hasUsageAccess) screenTimeMillis = DeviceUsage.todayScreenTimeMillis(context)
            hasCalendarPermission = CalendarRepository.hasPermission(context)
            if (hasCalendarPermission) nextEvent = CalendarRepository.getNextEvent(context)
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
                        when {
                            accumulatedDy < SWIPE_UP_OPEN_THRESHOLD_PX && absDy > absDx -> {
                                change.consume()
                                gestureResolved = true
                                onOpenSearch()
                            }
                            accumulatedDy > SWIPE_DOWN_OPEN_THRESHOLD_PX && absDy > absDx -> {
                                change.consume()
                                gestureResolved = true
                                onSwipeDown()
                            }
                            absDx > SWIPE_HORIZONTAL_THRESHOLD_PX && absDx > absDy -> {
                                change.consume()
                                gestureResolved = true
                                if (accumulatedDx < 0) onSwipeLeft() else onSwipeRight()
                            }
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Everything below the clock is pinned to the clock's own measured width, so a
            // long calendar title truncates with an ellipsis instead of stretching the whole
            // block wider than the clock itself.
            var clockWidthPx by remember { mutableStateOf(0) }
            val clockWidth = with(LocalDensity.current) { clockWidthPx.toDp() }

            Column(
                modifier = Modifier
                    .padding(top = 72.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = onOpenSettings,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DotMatrixClock(
                    time = time,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        clockWidthPx = coordinates.size.width
                    }
                )
                Row(
                    modifier = Modifier
                        .width(clockWidth)
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = !hasUsageAccess) {
                            DeviceUsage.openUsageAccessSettings(context)
                        }
                    )
                }

                val event = nextEvent
                Column(
                    modifier = Modifier
                        .width(clockWidth)
                        .padding(top = 10.dp)
                        .clickable(enabled = !hasCalendarPermission) {
                            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                ) {
                    when {
                        !hasCalendarPermission -> Text(
                            text = "Enable calendar",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        event != null -> {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = PureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "> ${CalendarRepository.formatDate(event)}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "> ${CalendarRepository.formatTimeRange(event)}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        else -> Text(
                            text = "No upcoming events",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (expenseTrackerEnabled) {
                    ExpenseSummary(
                        todayTotal = todayExpenseTotal,
                        monthTotal = monthlyExpenseTotal,
                        categoryTotals = categoryTotalsMonth,
                        untaggedCount = untaggedExpenseCount,
                        onOpenDashboard = onOpenExpenseTracker,
                        modifier = Modifier
                            .width(clockWidth)
                            .padding(top = 18.dp),
                    )
                }
            }
        }

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

        if (pendingQuestion != null) {
            var dismissed by remember(pendingQuestion.id) { mutableStateOf(false) }
            if (!dismissed) {
                PendingQuestionCard(
                    question = pendingQuestion,
                    queuedCount = pendingQuestionCount,
                    onAnswer = { answer -> onAnswerQuestion(pendingQuestion.id, answer) },
                    onDismiss = { dismissed = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp),
                )
            }
        }
    }
}

private fun formatExpenseAmount(amount: Double): String = "₹${"%.0f".format(amount)}"

private val BAR_GRAPH_HEIGHT = 56.dp
private val DASHBOARD_BADGE_SIZE = 18.dp

/**
 * This month's spend at a glance: total, a bar per category (shortest to tallest, left to
 * right) and a `>`-prefixed breakdown line per category — matching the calendar block's own
 * `>` convention above. Categories with nothing spent yet are omitted to reduce clutter.
 */
@Composable
private fun ExpenseSummary(
    todayTotal: Double,
    monthTotal: Double,
    categoryTotals: Map<ExpenseCategory, Double>,
    untaggedCount: Int,
    onOpenDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nonZero = remember(categoryTotals) { categoryTotals.filter { it.value > 0.0 } }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDashboard),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "TODAY", style = MaterialTheme.typography.bodySmall, color = SubtextGrey, letterSpacing = 1.sp)
                // Rolls like an odometer when a background re-sync finds new spend.
                AnimatedContent(
                    targetState = todayTotal,
                    transitionSpec = {
                        (slideInVertically(tween(260)) { height -> height } + fadeIn(tween(260))) togetherWith
                            (slideOutVertically(tween(260)) { height -> -height } + fadeOut(tween(260)))
                    },
                    label = "todayTotal",
                ) { total ->
                    Text(text = formatExpenseAmount(total), style = MaterialTheme.typography.headlineMedium, color = PureWhite)
                }
                AnimatedContent(
                    targetState = monthTotal,
                    transitionSpec = {
                        (slideInVertically(tween(260)) { height -> height } + fadeIn(tween(260))) togetherWith
                            (slideOutVertically(tween(260)) { height -> -height } + fadeOut(tween(260)))
                    },
                    label = "monthTotal",
                    modifier = Modifier.padding(top = 2.dp),
                ) { total ->
                    Text(
                        text = "This month  ${formatExpenseAmount(total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtextGrey,
                    )
                }
            }
            DashboardButton(untaggedCount = untaggedCount)
        }

        if (nonZero.isNotEmpty()) {
            MonthlyBarGraph(
                categoryTotals = nonZero,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BAR_GRAPH_HEIGHT)
                    .padding(top = 12.dp),
            )
            Column(modifier = Modifier.padding(top = 8.dp)) {
                nonZero.entries.sortedByDescending { it.value }.forEach { (category, amount) ->
                    Text(
                        text = "> ${category.label}  ${formatExpenseAmount(amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Bars sorted shortest to tallest, left to right, each growing up from the baseline. */
@Composable
private fun MonthlyBarGraph(categoryTotals: Map<ExpenseCategory, Double>, modifier: Modifier = Modifier) {
    val ascending = remember(categoryTotals) { categoryTotals.entries.sortedBy { it.value } }
    val maxValue = max(ascending.maxOfOrNull { it.value } ?: 0.0, 1.0)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ascending.forEach { (_, amount) ->
            val targetFraction = (amount / maxValue).toFloat().coerceIn(0.06f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(500, easing = FastOutSlowInEasing),
                label = "barFraction",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(animatedFraction)
                    .background(PureWhite)
            )
        }
    }
}

/** Opens the expense dashboard; a white badge (matching the app's inverted-highlight motif
 * elsewhere) surfaces how many merchants are still waiting to be tagged. */
@Composable
private fun DashboardButton(untaggedCount: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier
                .border(BorderStroke(1.dp, SubtextGrey.copy(alpha = 0.5f)))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text = ">", style = MaterialTheme.typography.bodyLarge, color = PureWhite)
        }
        if (untaggedCount > 0) {
            Box(
                modifier = Modifier
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(DASHBOARD_BADGE_SIZE)
                    .background(PureWhite, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = untaggedCount.toString(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = PureBlack,
                )
            }
        }
    }
}

private val QUESTION_CARD_WIDTH = 230.dp

/**
 * The on-device classifier couldn't resolve a transaction on its own — one short MCQ at a
 * time, in the free space bottom-left (the dials own bottom-right). Entirely optional to
 * answer: dismissing just hides it for this session, the question stays queued and reappears
 * next time the app is reopened. A `>`-prefixed option list matches the calendar block's own
 * convention for this kind of inline list.
 */
@Composable
private fun PendingQuestionCard(
    question: ExpenseRepository.PendingQuestion,
    queuedCount: Int,
    onAnswer: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(QUESTION_CARD_WIDTH)
            .border(BorderStroke(1.dp, SubtextGrey.copy(alpha = 0.5f)))
            .background(PureBlack)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${question.counterpartName} · ${formatExpenseAmount(question.amount)}",
                style = MaterialTheme.typography.bodySmall,
                color = SubtextGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (queuedCount > 1) {
                Text(
                    text = "+${queuedCount - 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtextGrey,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                text = "×",
                style = MaterialTheme.typography.bodyMedium,
                color = SubtextGrey,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clickable(onClick = onDismiss),
            )
        }
        Text(
            text = question.questionText,
            style = MaterialTheme.typography.bodyMedium,
            color = PureWhite,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(modifier = Modifier.padding(top = 8.dp)) {
            question.options.forEach { option ->
                Text(
                    text = "> $option",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtextGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAnswer(option) }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}
