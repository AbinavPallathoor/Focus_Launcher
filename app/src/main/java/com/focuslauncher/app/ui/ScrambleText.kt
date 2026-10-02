package com.focuslauncher.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val SCRAMBLE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%&*<>/\\?"
private const val SCRAMBLE_STEPS = 10
private const val SCRAMBLE_STEP_DELAY_MS = 28L

/**
 * Hacker-style "decrypting" text reveal: on each change of [text], characters flicker
 * through a random charset and resolve left-to-right into the target string.
 */
@Composable
fun ScrambleText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color = PureWhite,
) {
    var displayed by remember { mutableStateOf(text) }

    LaunchedEffect(text) {
        val target = text
        if (target.isEmpty()) {
            displayed = ""
            return@LaunchedEffect
        }
        for (step in 0..SCRAMBLE_STEPS) {
            val revealCount = target.length * step / SCRAMBLE_STEPS
            displayed = buildString {
                for (i in target.indices) {
                    append(if (i < revealCount) target[i] else SCRAMBLE_CHARS[Random.nextInt(SCRAMBLE_CHARS.length)])
                }
            }
            delay(SCRAMBLE_STEP_DELAY_MS)
        }
        displayed = target
    }

    Text(text = displayed, style = style, color = color, modifier = modifier)
}
