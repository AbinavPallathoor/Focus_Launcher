package com.focuslauncher.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private val STAR_CHARS = charArrayOf(' ', ' ', ' ', ' ', '.', '.', '·', '*')
private const val GRID_COLS = 19
private const val GRID_ROWS = 6
private const val TWINKLE_INTERVAL_MS = 450L
private const val TWINKLE_CHANCE = 0.12f

private fun randomGrid() = List(GRID_ROWS) { List(GRID_COLS) { STAR_CHARS[Random.nextInt(STAR_CHARS.size)] } }

/** A small twinkling ASCII starfield — reacts to a tap by immediately re-twinkling. */
@Composable
fun SpaceAsciiArt(modifier: Modifier = Modifier) {
    var grid by remember { mutableStateOf(randomGrid()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(TWINKLE_INTERVAL_MS)
            grid = grid.map { row -> row.map { c -> if (Random.nextFloat() < TWINKLE_CHANCE) STAR_CHARS[Random.nextInt(STAR_CHARS.size)] else c } }
        }
    }

    Column(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { grid = randomGrid() }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        grid.forEach { row ->
            Text(
                text = row.joinToString(""),
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 2.sp),
                color = SubtextGrey.copy(alpha = 0.55f),
            )
        }
    }
}
