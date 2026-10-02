package com.focuslauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.focuslauncher.app.AppInfo
import kotlinx.coroutines.delay

private const val AUTO_LAUNCH_DEBOUNCE_MS = 250L

private fun filterApps(apps: List<AppInfo>, query: String): List<AppInfo> {
    if (query.isBlank()) return apps
    val q = query.trim().lowercase()
    return apps
        .filter { it.label.lowercase().contains(q) }
        .sortedWith(
            compareBy(
                { !it.label.lowercase().startsWith(q) },
                { it.label.lowercase() }
            )
        )
}

/**
 * Full-screen search overlay. In normal mode, tapping a result (or narrowing to a single
 * match, or pressing the keyboard's search action) launches it via [onLaunch]. In pick mode
 * ([onPick] non-null, used when assigning a home-screen favorite slot), tapping a result calls
 * [onPick] instead and nothing auto-launches.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onClose: () -> Unit,
    onPick: ((AppInfo) -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) { filterApps(apps, query) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun select(app: AppInfo) {
        if (onPick != null) onPick(app) else onLaunch(app)
        onClose()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Auto-launch once typing narrows to exactly one match (debounced so fast typists
    // aren't interrupted mid-word). Only applies in normal launch mode.
    LaunchedEffect(query) {
        if (onPick == null && query.isNotBlank()) {
            delay(AUTO_LAUNCH_DEBOUNCE_MS)
            val matches = filterApps(apps, query)
            if (matches.size == 1) {
                select(matches[0])
            }
        }
    }

    BackHandler(onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(horizontal = 24.dp, vertical = 48.dp)
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text(if (onPick != null) "Pick an app" else "Search apps", style = MaterialTheme.typography.bodyLarge, color = SubtextGrey) },
            // An explicit color on textStyle would override colors.focusedTextColor below,
            // so leave it Unspecified and let the TextFieldColors win.
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Unspecified),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    filtered.firstOrNull()?.let { select(it) }
                }
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PureBlack,
                unfocusedContainerColor = PureBlack,
                focusedIndicatorColor = SubtextGrey,
                unfocusedIndicatorColor = SubtextGrey.copy(alpha = 0.4f),
                // The typed characters themselves stay invisible — only the narrowing
                // result list below is shown — but a faint cursor still confirms focus.
                focusedTextColor = Color.Transparent,
                unfocusedTextColor = Color.Transparent,
                cursorColor = SubtextGrey,
            )
        )

        LazyColumn(modifier = Modifier.padding(top = 24.dp)) {
            itemsIndexed(filtered, key = { _, app -> app.key }) { index, app ->
                val isTopMatch = index == 0
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isTopMatch) PureBlack else PureWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemPlacement(tween(220))
                        .background(if (isTopMatch) PureWhite else Color.Transparent)
                        .clickable { select(app) }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                )
            }
        }
    }
}
