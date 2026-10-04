package com.focuslauncher.app.ui

import android.Manifest
import android.graphics.Rect
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslauncher.app.AppInfo
import com.focuslauncher.app.CalendarRepository
import com.focuslauncher.app.DeviceUsage
import com.focuslauncher.app.ExpenseCategory
import com.focuslauncher.app.ExpenseRepository
import com.focuslauncher.app.ResolvedBy
import com.focuslauncher.app.TransactionKind
import com.focuslauncher.app.transactionTag
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
    recentTransactions: List<ExpenseRepository.Transaction>,
    onCorrectTransaction: (transactionId: Long, note: String) -> Unit,
    onConfirmTransaction: (transactionId: Long) -> Unit,
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

        // The terminal feed is composed before the dials (and the dials before the modal
        // overlay) so z-order comes out right: dials draw on top of the feed — they're the
        // thing you're actively dragging, the feed shouldn't be able to cover them — while the
        // full-screen correction overlay still ends up on top of everything, dials included,
        // since it's modal.
        var focusedTransactionId by remember { mutableStateOf<Long?>(null) }
        var updatingTransactionId by remember { mutableStateOf<Long?>(null) }
        var dismissedIds by remember { mutableStateOf(emptySet<Long>()) }
        if (expenseTrackerEnabled) {
            // Cleared whenever the transaction list is actually recomputed (a new sync or a
            // classification finishing) — not on a timer — so "updating…" never lies about
            // whether the correction has really landed yet.
            LaunchedEffect(recentTransactions) { updatingTransactionId = null }

            TerminalFeed(
                transactions = recentTransactions,
                dismissedIds = dismissedIds,
                updatingTransactionId = updatingTransactionId,
                onRequestFocus = { id -> focusedTransactionId = id },
                onConfirm = { id ->
                    dismissedIds = dismissedIds + id
                    onConfirmTransaction(id)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp),
            )
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

        if (expenseTrackerEnabled) {
            val focusedTransaction = recentTransactions.firstOrNull { it.id == focusedTransactionId }
            // Held onto through the dismiss animation: the instant focusedTransactionId goes
            // null, focusedTransaction would too, and AnimatedVisibility's exit transition needs
            // something non-null to keep rendering while it plays.
            var lastShownTransaction by remember { mutableStateOf<ExpenseRepository.Transaction?>(null) }
            if (focusedTransaction != null) lastShownTransaction = focusedTransaction

            BackHandler(enabled = focusedTransactionId != null) { focusedTransactionId = null }
            FocusedCorrectionOverlay(
                visible = focusedTransactionId != null,
                transaction = lastShownTransaction,
                onSubmit = { transactionId, note ->
                    updatingTransactionId = transactionId
                    dismissedIds = dismissedIds + transactionId
                    onCorrectTransaction(transactionId, note)
                },
                onDismiss = { focusedTransactionId = null },
            )
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

private val TERMINAL_WIDTH = 230.dp
private const val TERMINAL_STACK_SIZE = 3
// Tall enough to clearly show a sliver of the card behind (its border, some real content) —
// at 16dp this read as cramped/overlapping text rather than a deliberate stack of cards.
private val TERMINAL_PEEK_HEIGHT = 34.dp

/**
 * Every transaction is tagged automatically now — this is a stack of individual cards, newest
 * at the front, rather than one box listing all of them at once. Older cards peek out above the
 * front one (shrunk and dimmed a little per step back) just enough to read as "there's more
 * behind this" without competing with it. Only the front card is interactive — the ones behind
 * are mostly covered anyway.
 */
@Composable
private fun TerminalFeed(
    transactions: List<ExpenseRepository.Transaction>,
    dismissedIds: Set<Long>,
    updatingTransactionId: Long?,
    onRequestFocus: (transactionId: Long) -> Unit,
    onConfirm: (transactionId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (transactions.isEmpty()) return
    val nonDismissed = transactions.filterNot { it.id in dismissedIds }
    Box(modifier = modifier.width(TERMINAL_WIDTH), contentAlignment = Alignment.BottomCenter) {
        // Every fetched transaction is composed here, not just the visible stack depth — a
        // just-dismissed one (depth -1, filtered out of nonDismissed) still needs to render
        // while its own exit animation plays. Composed back-to-front (highest depth first) so
        // the front/newest card, composed last, naturally draws on top — no explicit
        // z-ordering needed.
        for (transaction in transactions.sortedByDescending { nonDismissed.indexOf(it) }) {
            val depth = nonDismissed.indexOf(transaction)
            val dismissed = depth == -1
            if (!dismissed && depth >= TERMINAL_STACK_SIZE) continue
            key(transaction.id) {
                // Animated rather than a plain offset/graphicsLayer — when the stack reorders
                // (a new transaction arrives, one gets dismissed and the rest settle forward)
                // each card slides to its new depth instead of snapping there. A dismissed card
                // needs to keep whatever depth it last had (not jump to the front) while its own
                // exit transition plays, since `depth` itself goes to -1 the instant it's
                // dismissed — remembered separately so the offset doesn't snap out from under it.
                var lastKnownDepth by remember(transaction.id) { mutableStateOf(0) }
                if (!dismissed) lastKnownDepth = depth
                val effectiveDepth = if (dismissed) lastKnownDepth else depth
                val peekOffset by animateDpAsState(
                    targetValue = -(TERMINAL_PEEK_HEIGHT * effectiveDepth),
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    label = "cardPeekOffset",
                )
                // Alpha-only depth cue — no scale. Scaling from the composable's center also
                // shifts its apparent bottom edge inward, which made the front card look
                // slightly misaligned against the ones peeking behind it; a flat stack with
                // just a dimmer tint behind reads more cleanly anyway.
                val depthAlpha by animateFloatAsState(1f - 0.2f * effectiveDepth, tween(260), label = "cardDepthAlpha")
                AnimatedVisibility(
                    visible = !dismissed,
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = peekOffset),
                    exit = shrinkVertically(tween(220), shrinkTowards = Alignment.Bottom) + fadeOut(tween(180)),
                ) {
                    TerminalCard(
                        transaction = transaction,
                        isFront = effectiveDepth == 0,
                        isUpdating = transaction.id == updatingTransactionId,
                        onRequestFocus = { onRequestFocus(transaction.id) },
                        onConfirm = { onConfirm(transaction.id) },
                        modifier = Modifier.graphicsLayer { alpha = depthAlpha },
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalCard(
    transaction: ExpenseRepository.Transaction,
    isFront: Boolean,
    isUpdating: Boolean,
    onRequestFocus: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val confirmed = transaction.resolvedBy == ResolvedBy.USER
    // Nothing to confirm yet if the classifier hasn't produced a guess at all (still freshly
    // synced and UNKNOWN) — confirming that would just lock in "UNKNOWN" as correct. The tick
    // only appears once there's an actual guess behind it.
    val hasGuess = transaction.kind != TransactionKind.UNKNOWN
    // Pressing the tick dismisses the whole card immediately (the caller removes it from the
    // stack the same way a text correction does) — there's no separate "button fades, card
    // stays" state to track here anymore, just whether there's still something to confirm.

    // Every card renders the same three rows regardless of depth — a shorter back card would
    // end up entirely hidden behind a taller front one instead of peeking out above it. Only
    // the front card actually responds to taps; the ones behind are inert.
    Column(
        modifier = modifier
            .width(TERMINAL_WIDTH)
            .border(BorderStroke(1.dp, SubtextGrey.copy(alpha = if (isFront) 0.5f else 0.3f)))
            .background(PureBlack)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "> ${transaction.counterpartName}  ${formatExpenseAmount(transaction.amount)}",
                style = MaterialTheme.typography.bodySmall,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!isUpdating && !confirmed && hasGuess) {
                ConfirmButton(
                    confirmed = false,
                    onConfirm = onConfirm,
                    interactive = isFront,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        AnimatedContent(
            targetState = if (isUpdating) "updating…" else "[${transactionTag(transaction.kind, transaction.category)}]",
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            label = "cardTag",
            modifier = Modifier.padding(top = 4.dp),
        ) { label ->
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = SubtextGrey)
        }
        Text(
            text = "> what was this actually?",
            style = MaterialTheme.typography.bodySmall,
            color = SubtextGrey.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clickable(enabled = isFront, onClick = onRequestFocus),
        )
    }
}

/** A bordered icon button matching the dashboard's own ">" box — confirms the current auto-tag
 * is correct, no LLM call needed. Fills in solid once confirmed, dims and stops responding to
 * taps after that (there's nothing left to confirm). Shared with the expense dashboard's own
 * transaction rows — same affordance, same meaning, in both places. */
@Composable
internal fun ConfirmButton(
    confirmed: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    // Full white border + icon even before confirming — the dim-grey version this used to be
    // read as decorative rather than as a button and was easy to miss entirely at a glance.
    // Only the fill flips from black to white on confirm.
    val fill by animateColorAsState(if (confirmed) PureWhite else PureBlack, tween(180), label = "confirmFill")
    val iconTint by animateColorAsState(if (confirmed) PureBlack else PureWhite, tween(180), label = "confirmIconTint")
    Box(
        modifier = modifier
            .border(BorderStroke(1.dp, PureWhite.copy(alpha = 0.7f)))
            .background(fill)
            .clickable(enabled = interactive && !confirmed, onClick = onConfirm)
            .padding(horizontal = 7.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = if (confirmed) "Tag confirmed" else "Confirm tag",
            tint = iconTint,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Tapping a terminal entry's prompt lands here instead of editing in place: the rest of the
 * screen dims (the same treatment the radial dial uses while open) and this card grows up into
 * the center from where the terminal feed sits, auto-focused and keyboard up immediately. Enter
 * submits, closes the keyboard, and the card shrinks back out the same way it came; tapping
 * outside dismisses without submitting. [transaction] is kept non-null by the caller through the
 * exit animation (it still needs to render while fading/scaling out) — [visible] is what
 * actually drives the transition.
 */
@Composable
private fun FocusedCorrectionOverlay(
    visible: Boolean,
    transaction: ExpenseRepository.Transaction?,
    onSubmit: (transactionId: Long, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun closeKeyboardAndDismiss() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { closeKeyboardAndDismiss() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Separate from the scrim's own fade so the card gets its own motion — growing up
            // from roughly where the terminal feed sits rather than just popping into place.
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(initialScale = 0.8f, animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                    slideInVertically(animationSpec = tween(260, easing = FastOutSlowInEasing)) { height -> height / 6 },
                exit = scaleOut(targetScale = 0.8f, animationSpec = tween(200)) +
                    slideOutVertically(animationSpec = tween(200)) { height -> height / 6 },
            ) {
                if (transaction != null) {
                    FocusedCorrectionCard(
                        transaction = transaction,
                        visible = visible,
                        onSubmit = { note -> onSubmit(transaction.id, note) },
                        onClose = ::closeKeyboardAndDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusedCorrectionCard(
    transaction: ExpenseRepository.Transaction,
    visible: Boolean,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
) {
    var input by remember(transaction.id) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .width(TERMINAL_WIDTH)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}, // swallow taps so they don't fall through to the scrim above
            )
            .border(BorderStroke(1.dp, PureWhite.copy(alpha = 0.6f)))
            .background(PureBlack)
            .padding(16.dp),
    ) {
        Text(
            text = "> ${transaction.counterpartName}  ${formatExpenseAmount(transaction.amount)}",
            style = MaterialTheme.typography.bodySmall,
            color = PureWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "[${transactionTag(transaction.kind, transaction.category)}]",
            style = MaterialTheme.typography.bodySmall,
            color = SubtextGrey,
            modifier = Modifier.padding(top = 2.dp),
        )
        Box(modifier = Modifier.padding(top = 10.dp)) {
            if (input.isEmpty()) {
                Text(
                    text = "> what was this actually?",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtextGrey.copy(alpha = 0.5f),
                )
            }
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = PureWhite),
                cursorBrush = SolidColor(PureWhite),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val note = input.trim()
                    onClose()
                    if (note.isNotEmpty()) onSubmit(note)
                }),
                decorationBox = { inner ->
                    Row {
                        Text(text = "> ", style = MaterialTheme.typography.bodySmall, color = SubtextGrey)
                        inner()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
    }

    // Only grabs focus on the way in — the exit animation re-renders this same composable with
    // visible=false while it plays, and re-requesting focus then would reopen the keyboard right
    // as everything is trying to close.
    LaunchedEffect(visible) {
        if (visible) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
}
