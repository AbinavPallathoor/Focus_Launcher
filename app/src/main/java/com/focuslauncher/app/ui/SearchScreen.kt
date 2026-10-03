package com.focuslauncher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
                Box(modifier = Modifier.fillMaxWidth().animateItemPlacement(tween(220))) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isTopMatch) PureBlack else PureWhite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isTopMatch) PureWhite else Color.Transparent)
                            .combinedClickable(
                                onClick = { select(app) },
                                onLongClick = { contextMenuAppKey = app.key },
                            )
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    )
                    if (contextMenuAppKey == app.key) {
                        Popup(
                            alignment = Alignment.TopStart,
                            onDismissRequest = { contextMenuAppKey = null },
                            properties = PopupProperties(focusable = true),
                        ) {
                            AppActionMenu(
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

// Matches the row's own vertical.padding(10dp)*2 + bodyLarge line height — the elbow
// starts below this so the box never sits over the app name itself.
private val MENU_ROW_HEIGHT = 54.dp
private val MENU_DIAGONAL = 26.dp
private val MENU_HORIZONTAL = 32.dp
private val MENU_START_INSET = 8.dp
private val MENU_BOX_HALF_HEIGHT = 26.dp

/**
 * The long-press action menu — same elbow-leader-line language as the radial dial's
 * callout: a 45° diagonal off the row, then a bend to horizontal, into a box of filled
 * monochrome icons (same pack, same size). Anchored below the row so it never covers
 * the app name above it.
 */
@Composable
private fun AppActionMenu(
    onUninstall: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onCloseApp: () -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.85f),
        exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.85f),
    ) {
        Box(modifier = Modifier.width(300.dp).height(140.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val anchor = Offset(MENU_START_INSET.toPx(), MENU_ROW_HEIGHT.toPx())
                val corner = Offset(anchor.x + MENU_DIAGONAL.toPx(), anchor.y + MENU_DIAGONAL.toPx())
                val boxAnchor = Offset(corner.x + MENU_HORIZONTAL.toPx(), corner.y)
                drawLine(PureWhite, anchor, corner, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(PureWhite, corner, boxAnchor, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
            Row(
                modifier = Modifier
                    .offset(
                        x = MENU_START_INSET + MENU_DIAGONAL + MENU_HORIZONTAL,
                        y = MENU_ROW_HEIGHT + MENU_DIAGONAL - MENU_BOX_HALF_HEIGHT,
                    )
                    .border(BorderStroke(1.dp, PureWhite.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
                    .background(PureBlack, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIcon(Icons.Filled.Delete, "Uninstall", onUninstall)
                ActionIcon(Icons.Filled.Edit, "Rename", onRename)
                ActionIcon(Icons.Filled.VisibilityOff, "Hide", onHide)
                ActionIcon(Icons.Filled.Close, "Close", onCloseApp)
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = PureWhite,
        modifier = Modifier
            .size(24.dp)
            .clickable(onClick = onClick)
    )
}
