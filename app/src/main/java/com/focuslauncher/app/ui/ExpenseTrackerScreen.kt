package com.focuslauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslauncher.app.ExpenseCategory
import com.focuslauncher.app.ExpenseRepository
import com.focuslauncher.app.ResolvedBy
import com.focuslauncher.app.TransactionKind
import com.focuslauncher.app.transactionTag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private fun formatAmount(amount: Double): String = "₹${"%.0f".format(amount)}"

/** Shared category→icon mapping, reused by the home screen's own monthly summary. */
internal fun categoryIcon(category: ExpenseCategory): ImageVector = when (category) {
    ExpenseCategory.FOOD -> Icons.Filled.Restaurant
    ExpenseCategory.TRANSPORT -> Icons.Filled.DirectionsCar
    ExpenseCategory.ESSENTIALS -> Icons.Filled.ShoppingCart
    ExpenseCategory.EXTRAS -> Icons.Filled.Star
    ExpenseCategory.SUBSCRIPTION -> Icons.Filled.Subscriptions
}

/**
 * SMS-derived spend dashboard: today's total, a per-category breakdown, a line graph of the
 * last two weeks, who owes who, and the recent transaction log. Every transaction is tagged
 * automatically by the on-device classifier; tapping one here opens an inline prompt to retag it
 * directly, the same correction path the home screen's terminal feed uses.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpenseTrackerScreen(
    todayTotal: Double,
    categoryTotals: Map<ExpenseCategory, Double>,
    dailyTotals: List<ExpenseRepository.DayTotal>,
    recentTransactions: List<ExpenseRepository.Transaction>,
    debtLedger: List<ExpenseRepository.PersonBalance>,
    isProcessingCommand: Boolean,
    onCorrectTransaction: (transactionId: Long, note: String) -> Unit,
    onConfirmTransaction: (transactionId: Long) -> Unit,
    onCommand: (String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(horizontal = 24.dp, vertical = 48.dp)
    ) {
        item {
            Text(text = "Expense Tracker", style = MaterialTheme.typography.bodyLarge, color = PureWhite)
        }

        item {
            DashboardCommandBar(
                isProcessing = isProcessingCommand,
                onCommand = onCommand,
                modifier = Modifier.padding(top = 20.dp),
            )
        }

        item {
            Column(modifier = Modifier.padding(top = 28.dp)) {
                Text(text = "TODAY", style = MaterialTheme.typography.bodySmall, color = SubtextGrey, letterSpacing = 1.sp)
                // New data syncing in rolls the total up/down like an odometer rather than
                // snapping, so a background re-sync finding fresh spend reads as "live".
                AnimatedContent(
                    targetState = todayTotal,
                    transitionSpec = {
                        (slideInVertically(tween(260)) { height -> height } + fadeIn(tween(260))) togetherWith
                            (slideOutVertically(tween(260)) { height -> -height } + fadeOut(tween(260)))
                    },
                    label = "todayTotal",
                    modifier = Modifier.padding(top = 4.dp),
                ) { total ->
                    Text(
                        text = formatAmount(total),
                        style = MaterialTheme.typography.headlineLarge,
                        color = PureWhite,
                    )
                }
            }
        }

        if (debtLedger.isNotEmpty()) {
            item { SectionHeader("Owing") }
            items(debtLedger, key = { it.name }) { person ->
                DebtRow(person, modifier = Modifier.animateItemPlacement(tween(220)))
            }
        }

        item { SectionHeader("By category") }
        item {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                val maxCategoryTotal = max(categoryTotals.values.maxOrNull() ?: 0.0, 1.0)
                ExpenseCategory.entries.forEach { category ->
                    CategoryRow(
                        category = category,
                        amount = categoryTotals[category] ?: 0.0,
                        fraction = ((categoryTotals[category] ?: 0.0) / maxCategoryTotal).toFloat(),
                    )
                }
            }
        }

        item { SectionHeader("Last 14 days") }
        item {
            SpendingLineGraph(
                dailyTotals = dailyTotals,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(top = 12.dp, bottom = 4.dp),
            )
        }

        if (recentTransactions.isNotEmpty()) {
            item { SectionHeader("Recent") }
            items(recentTransactions, key = { it.id }) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    onCorrect = { note -> onCorrectTransaction(transaction.id, note) },
                    onConfirm = { onConfirmTransaction(transaction.id) },
                    modifier = Modifier.animateItemPlacement(tween(220)),
                )
            }
        }
    }
}

@Composable
private fun DebtRow(person: ExpenseRepository.PersonBalance, modifier: Modifier = Modifier) {
    val owesUser = person.netAmount > 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = person.name,
            style = MaterialTheme.typography.bodyMedium,
            color = PureWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (owesUser) "owes you ${formatAmount(person.netAmount)}" else "you owe ${formatAmount(-person.netAmount)}",
            style = MaterialTheme.typography.bodyMedium,
            color = SubtextGrey,
        )
    }
}

/**
 * One free-text instruction to the on-device model, which can add a transaction the SMS
 * pipeline never saw (cash, say), delete one, or retag one — interpreted against the dashboard's
 * own recent list. Clears immediately on submit; "thinking…" stands in for the tag/total values
 * briefly changing underneath it rather than blocking the input itself.
 */
@Composable
private fun DashboardCommandBar(
    isProcessing: Boolean,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, SubtextGrey.copy(alpha = 0.4f)))
            .padding(12.dp),
    ) {
        Box {
            if (input.isEmpty()) {
                Text(
                    text = "> add, remove, or retag something…",
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
                    val command = input.trim()
                    if (command.isNotEmpty()) {
                        onCommand(command)
                        input = ""
                    }
                }),
                decorationBox = { inner ->
                    Row {
                        Text(text = "> ", style = MaterialTheme.typography.bodySmall, color = SubtextGrey)
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isProcessing) {
            Text(
                text = "thinking…",
                style = MaterialTheme.typography.bodySmall,
                color = SubtextGrey,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

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

@Composable
private fun CategoryRow(category: ExpenseCategory, amount: Double, fraction: Float) {
    // Bars ease to their new width instead of jumping, so a re-sync that shifts the
    // breakdown (or a freshly-tagged merchant) reads as a smooth update, not a reset.
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "categoryFraction",
    )
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = null,
                tint = PureWhite,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = category.label, style = MaterialTheme.typography.bodyMedium, color = PureWhite)
            Box(modifier = Modifier.weight(1f))
            Text(text = formatAmount(amount), style = MaterialTheme.typography.bodyMedium, color = SubtextGrey)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(3.dp)
                .background(SubtextGrey.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(3.dp)
                    .background(PureWhite)
            )
        }
    }
}

@Composable
private fun SpendingLineGraph(dailyTotals: List<ExpenseRepository.DayTotal>, modifier: Modifier = Modifier) {
    val maxValue = max(dailyTotals.maxOfOrNull { it.total } ?: 0.0, 1.0)

    // The line grows up from the baseline whenever the underlying data changes (first load,
    // or a re-sync finding new spend) rather than just appearing fully drawn.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(dailyTotals) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
    }

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            drawLine(
                color = SubtextGrey.copy(alpha = 0.3f),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            if (dailyTotals.size < 2) return@Canvas
            val stepX = size.width / (dailyTotals.size - 1)
            val points = dailyTotals.mapIndexed { index, day ->
                val targetY = size.height - (day.total / maxValue).toFloat() * size.height
                val y = size.height - (size.height - targetY) * reveal.value
                Offset(index * stepX, y)
            }
            for (i in 0 until points.size - 1) {
                drawLine(PureWhite, points[i], points[i + 1], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            points.forEach { point -> drawCircle(PureWhite, radius = 2.5.dp.toPx(), center = point) }
        }
        if (dailyTotals.isNotEmpty()) {
            val labelFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                    text = labelFormat.format(Date(dailyTotals.first().dayStartMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtextGrey,
                )
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = labelFormat.format(Date(dailyTotals.last().dayStartMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtextGrey,
                )
            }
        }
    }
}

/**
 * Tapping a row opens an inline "what was this actually?" prompt right below it — the same
 * free-text retagging the home screen's terminal feed offers, available here too for anything
 * that needs fixing after the fact rather than the moment it shows up.
 */
@Composable
private fun TransactionRow(
    transaction: ExpenseRepository.Transaction,
    onCorrect: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    var expanded by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.counterpartName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val tag = transactionTag(transaction.kind, transaction.category)
                Text(
                    text = "$tag · ${dateFormat.format(Date(transaction.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtextGrey,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = formatAmount(transaction.amount), style = MaterialTheme.typography.bodyMedium, color = PureWhite)
            // Nothing to confirm yet if the classifier hasn't produced a guess at all — same
            // guard as the home screen's terminal feed.
            if (transaction.kind != TransactionKind.UNKNOWN) {
                ConfirmButton(
                    confirmed = transaction.resolvedBy == ResolvedBy.USER,
                    onConfirm = onConfirm,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        if (expanded) {
            Box(modifier = Modifier.padding(top = 8.dp)) {
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
                        if (note.isNotEmpty()) {
                            onCorrect(note)
                            input = ""
                        }
                        expanded = false
                    }),
                    decorationBox = { inner ->
                        Row {
                            Text(text = "> ", style = MaterialTheme.typography.bodySmall, color = SubtextGrey)
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
