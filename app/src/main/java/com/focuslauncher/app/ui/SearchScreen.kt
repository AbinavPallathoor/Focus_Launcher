package com.focuslauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focuslauncher.app.AppInfo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable

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
    onUninstallApp: (AppInfo) -> Unit = {},
    onRenameApp: (AppInfo, String) -> Unit = { _, _ -> },
    onHideApp: (AppInfo) -> Unit = {},
    onCloseApp: (AppInfo) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) { filterApps(apps, query) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var contextMenuAppKey by remember { mutableStateOf<String?>(null) }
    var renamingApp by remember { mutableStateOf<AppInfo?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun select(app: AppInfo) {
        if (onPick != null) onPick(app) else onLaunch(app)
        onClose()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Auto-launch the instant typing narrows to exactly one match. Only applies in
    // normal launch mode — no debounce, so the app opens the moment it's unambiguous.
    LaunchedEffect(query, filtered) {
        contextMenuAppKey = null
        if (onPick == null && query.isNotBlank() && filtered.size == 1) {
            select(filtered[0])
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
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = PureWhite),
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
                focusedTextColor = PureWhite,
                unfocusedTextColor = PureWhite,
                cursorColor = PureWhite,
            )
        )

        LazyColumn(modifier = Modifier.padding(top = 24.dp)) {
            itemsIndexed(filtered, key = { _, app -> app.key }) { index, app ->
                val isTopMatch = index == 0
                AppRow(
                    app = app,
                    isTopMatch = isTopMatch,
                    isMenuOpen = contextMenuAppKey == app.key,
                    modifier = Modifier.animateItemPlacement(tween(220)),
                    onSelect = { select(app) },
                    onLongPress = { contextMenuAppKey = app.key },
                    onDismissMenu = { contextMenuAppKey = null },
                    onUninstall = {
                        contextMenuAppKey = null
                        onUninstallApp(app)
                    },
                    onRename = {
                        contextMenuAppKey = null
                        renameText = app.label
                        renamingApp = app
                    },
                    onHide = {
                        contextMenuAppKey = null
                        onHideApp(app)
                    },
                    onCloseApp = {
                        contextMenuAppKey = null
                        onCloseApp(app)
                    },
                )
            }
        }
    }

    renamingApp?.let { app ->
        AlertDialog(
            onDismissRequest = { renamingApp = null },
            containerColor = PureBlack,
            tonalElevation = 0.dp,
            title = { Text("Rename", color = PureWhite) },
            text = {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Unspecified),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = PureBlack,
                        unfocusedContainerColor = PureBlack,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        cursorColor = PureWhite,
                        focusedIndicatorColor = SubtextGrey,
                        unfocusedIndicatorColor = SubtextGrey.copy(alpha = 0.4f),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameApp(app, renameText)
                    renamingApp = null
                }) {
                    Text("Save", color = PureWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingApp = null }) {
                    Text("Cancel", color = SubtextGrey)
                }
            },
        )
    }
}

private const val HOLD_BORDER_RISE_MS = 500
private const val HOLD_BORDER_FADE_MS = 180
private val ICON_PANEL_START_MARGIN = 22.dp // opaque buffer so text never cuts off right at the line
private val ICON_PANEL_END_PADDING = 12.dp
private val ICON_GAP = 18.dp
private val DIVIDER_GAP = 14.dp

/**
 * One search result row. Selected (top-match) rows are white-on-black inverted; all rows
 * get an animated white/black border (matching contrast) that fills in while held down and
 * fades on release. Long-pressing reveals an icon action panel inline on the right — text
 * behind it is left unclipped and simply covered by the panel's own opaque background, with
 * a margin before the dividing line so nothing is cut off right at the line.
 */
@Composable
private fun AppRow(
    app: AppInfo,
    isTopMatch: Boolean,
    isMenuOpen: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onUninstall: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onCloseApp: () -> Unit,
) {
    val backgroundColor = if (isTopMatch) PureWhite else PureBlack
    val contentColor = if (isTopMatch) PureBlack else PureWhite
    val holdProgress = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .border(BorderStroke(1.5.dp, contentColor.copy(alpha = holdProgress.value)))
            .pointerInput(app.key) {
                detectTapGestures(
                    onPress = {
                        coroutineScope {
                            val riseJob = launch {
                                holdProgress.snapTo(0f)
                                holdProgress.animateTo(1f, tween(HOLD_BORDER_RISE_MS, easing = LinearEasing))
                            }
                            tryAwaitRelease()
                            riseJob.cancel()
                            launch { holdProgress.animateTo(0f, tween(HOLD_BORDER_FADE_MS)) }
                        }
                    },
                    onTap = { if (isMenuOpen) onDismissMenu() else onSelect() },
                    onLongPress = { onLongPress() },
                )
            }
    ) {
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        )

        AnimatedVisibility(
            visible = isMenuOpen,
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(220)) { fullWidth -> fullWidth },
            exit = fadeOut(tween(150)) + slideOutHorizontally(tween(180)) { fullWidth -> fullWidth },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Row(
                modifier = Modifier
                    .background(backgroundColor)
                    .padding(vertical = 10.dp)
                    .padding(end = ICON_PANEL_END_PADDING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.width(ICON_PANEL_START_MARGIN))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(contentColor.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.width(DIVIDER_GAP))
                Row(horizontalArrangement = Arrangement.spacedBy(ICON_GAP), verticalAlignment = Alignment.CenterVertically) {
                    ActionIcon(Icons.Filled.Delete, "Uninstall", contentColor, onUninstall)
                    ActionIcon(Icons.Filled.Edit, "Rename", contentColor, onRename)
                    ActionIcon(Icons.Filled.VisibilityOff, "Hide", contentColor, onHide)
                    ActionIcon(Icons.Filled.Close, "Close", contentColor, onCloseApp)
                }
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, contentDescription: String, tint: Color, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier
            .size(24.dp)
            .clickable(onClick = onClick)
    )
}
