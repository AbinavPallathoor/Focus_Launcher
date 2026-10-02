package com.focuslauncher.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 5 rows x 3 cols bit patterns for digits, and a 1-col pattern for the colon. '1' = lit, '0' = unlit. */
private val DIGIT_PATTERNS: Map<Char, List<String>> = mapOf(
    '0' to listOf("111", "101", "101", "101", "111"),
    '1' to listOf("010", "110", "010", "010", "111"),
    '2' to listOf("111", "001", "111", "100", "111"),
    '3' to listOf("111", "001", "111", "001", "111"),
    '4' to listOf("101", "101", "111", "001", "001"),
    '5' to listOf("111", "100", "111", "001", "111"),
    '6' to listOf("111", "100", "111", "101", "111"),
    '7' to listOf("111", "001", "001", "001", "001"),
    '8' to listOf("111", "101", "111", "101", "111"),
    '9' to listOf("111", "101", "111", "001", "111"),
    ':' to listOf("0", "1", "0", "1", "0"),
)

/** Renders [time] (e.g. "14:07") as a segmented dot-matrix block display. */
@Composable
fun DotMatrixClock(
    time: String,
    modifier: Modifier = Modifier,
    cellSize: Dp = 13.dp,
    cellGap: Dp = 4.dp,
    charGap: Dp = 14.dp,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(charGap)) {
        for (ch in time) {
            val pattern = DIGIT_PATTERNS[ch] ?: continue
            Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                for (row in pattern) {
                    Row(horizontalArrangement = Arrangement.spacedBy(cellGap)) {
                        for (bit in row) {
                            val lit = bit == '1'
                            val cellColor by animateColorAsState(
                                targetValue = if (lit) PureWhite else Color.Transparent,
                                animationSpec = tween(durationMillis = 350),
                                label = "clockCell"
                            )
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .background(cellColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ticking 24-hour HH:mm clock, re-rendered on each minute boundary. */
@Composable
fun rememberCurrentTime(): String {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var time by remember { mutableStateOf(formatter.format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            time = formatter.format(Date())
            val now = Calendar.getInstance()
            val millisToNextMinute = 60_000L - (now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND))
            delay(millisToNextMinute.coerceAtLeast(1_000L))
        }
    }
    return time
}
