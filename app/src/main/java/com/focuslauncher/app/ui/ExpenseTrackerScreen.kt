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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslauncher.app.ExpenseCategory
import com.focuslauncher.app.ExpenseRepository
import com.focuslauncher.app.TransactionKind
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
 * last two weeks, and a prompt to tag any merchant seen for the first time. Categorizing a
 * merchant here retroactively re-tags all of its past transactions too.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpenseTrackerScreen(
    todayTotal: Double,
    categoryTotals: Map<ExpenseCategory, Double>,
    dailyTotals: List<ExpenseRepository.DayTotal>,
    untaggedMerchants: List<String>,
    recentTransactions: List<ExpenseRepository.Transaction>,
    onTagMerchant: (String, ExpenseCategory) -> Unit,
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

        if (untaggedMerchants.isNotEmpty()) {
            item { SectionHeader("Tag new merchants") }
            items(untaggedMerchants, key = { it }) { merchant ->
                MerchantTagRow(
                    merchant = merchant,
                    onTag = { category -> onTagMerchant(merchant, category) },
                    modifier = Modifier.animateItemPlacement(tween(220)),
                )
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
                TransactionRow(transaction, modifier = Modifier.animateItemPlacement(tween(220)))
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MerchantTagRow(merchant: String, onTag: (ExpenseCategory) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 14.dp)) {
        Text(
            text = merchant,
            style = MaterialTheme.typography.bodyLarge,
            color = PureWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExpenseCategory.entries.forEach { category ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onTag(category) },
                ) {
                    Icon(
                        imageVector = categoryIcon(category),
                        contentDescription = null,
                        tint = SubtextGrey,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = category.label, style = MaterialTheme.typography.bodySmall, color = SubtextGrey)
                }
            }
        }
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

@Composable
private fun TransactionRow(transaction: ExpenseRepository.Transaction, modifier: Modifier = Modifier) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.counterpartName,
                style = MaterialTheme.typography.bodyMedium,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val statusLabel = transaction.category?.label
                ?: transaction.kind.takeIf { it != TransactionKind.UNKNOWN }?.label
                ?: "Unclassified"
            Text(
                text = "$statusLabel · ${dateFormat.format(Date(transaction.timestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = SubtextGrey,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = formatAmount(transaction.amount), style = MaterialTheme.typography.bodyMedium, color = PureWhite)
    }
}
