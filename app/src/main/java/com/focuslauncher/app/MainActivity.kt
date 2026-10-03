package com.focuslauncher.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focuslauncher.app.ui.ExpenseTrackerScreen
import com.focuslauncher.app.ui.FocusLauncherTheme
import com.focuslauncher.app.ui.HomeScreen
import com.focuslauncher.app.ui.SearchScreen
import com.focuslauncher.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusBar()
        setContent {
            FocusLauncherTheme {
                LauncherRoot()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        // Belt-and-suspenders alongside the no-animation window style in themes.xml —
        // returning to the home screen should feel like the OS surfacing again, not like
        // an app opening, even on OEM skins that don't fully honor the theme attribute.
        overridePendingTransition(0, 0)
    }

    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private sealed class Screen {
    data object Home : Screen()
    data object Settings : Screen()
    data class ExpenseTracker(val returnTo: Screen = Home) : Screen()
    data class Search(
        val onPicked: ((AppInfo) -> Unit)? = null,
        val returnTo: Screen = Home,
    ) : Screen()
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun LauncherRoot() {
    val context = LocalContext.current
    // allApps is unfiltered (label overrides applied) so dial/gesture assignments keep
    // resolving even if the app gets hidden later; visibleApps/hiddenAppsList derive from it.
    var allApps by remember { mutableStateOf(AppRepository.getInstalledApps(context)) }
    var hiddenKeys by remember { mutableStateOf(HiddenAppsStore.getHiddenKeys(context)) }
    val visibleApps = remember(allApps, hiddenKeys) { allApps.filter { it.key !in hiddenKeys } }
    val hiddenAppsList = remember(allApps, hiddenKeys) { allApps.filter { it.key in hiddenKeys } }
    var radialGroups by remember {
        mutableStateOf(RADIAL_GROUPS.associateWith { group -> RadialMenuStore.getSlotKeys(context, group) })
    }
    var gestureApps by remember {
        mutableStateOf(
            mapOf(
                SwipeGesture.LEFT to GestureAppStore.getAppKey(context, SwipeGesture.LEFT),
                SwipeGesture.RIGHT to GestureAppStore.getAppKey(context, SwipeGesture.RIGHT),
            )
        )
    }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    var expenseTrackerEnabled by remember { mutableStateOf(ExpenseRepository.isEnabled(context)) }
    var expenseRefreshTick by remember { mutableStateOf(0) }
    // Cheap no-op queries against empty tables when the tracker is off, so it's simpler to
    // always compute these than to thread an enabled check through every call site.
    val todayExpenseTotal = remember(expenseRefreshTick) { ExpenseRepository.getTodayTotal(context) }
    val monthlyExpenseTotal = remember(expenseRefreshTick) { ExpenseRepository.getMonthTotal(context) }
    val categoryTotalsMonth = remember(expenseRefreshTick) { ExpenseRepository.getCategoryTotalsMonth(context) }
    val untaggedExpenseCount = remember(expenseRefreshTick) { ExpenseRepository.getUntaggedCount(context) }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ExpenseRepository.setEnabled(context, true)
            expenseTrackerEnabled = true
            ExpenseRepository.syncSms(context)
            expenseRefreshTick++
        }
    }

    fun openExpenseTracker(returnTo: Screen) {
        ExpenseRepository.syncSms(context)
        expenseRefreshTick++
        screen = Screen.ExpenseTracker(returnTo)
    }

    fun refreshApps() {
        allApps = AppRepository.getInstalledApps(context)
    }

    fun refreshHidden() {
        hiddenKeys = HiddenAppsStore.getHiddenKeys(context)
    }

    fun refreshRadialGroups() {
        radialGroups = RADIAL_GROUPS.associateWith { group -> RadialMenuStore.getSlotKeys(context, group) }
    }

    fun refreshGestureApps() {
        gestureApps = mapOf(
            SwipeGesture.LEFT to GestureAppStore.getAppKey(context, SwipeGesture.LEFT),
            SwipeGesture.RIGHT to GestureAppStore.getAppKey(context, SwipeGesture.RIGHT),
        )
    }

    fun resolveGroupApps(group: Int): List<AppInfo> =
        radialGroups[group].orEmpty().mapNotNull { key -> key?.let { AppRepository.findByKey(allApps, it) } }

    fun resolveGestureApp(gesture: SwipeGesture): AppInfo? =
        gestureApps[gesture]?.let { key -> AppRepository.findByKey(allApps, key) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshApps()
                refreshRadialGroups()
                refreshGestureApps()
                if (expenseTrackerEnabled) {
                    ExpenseRepository.syncSms(context)
                    expenseRefreshTick++
                }
                screen = Screen.Home
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        val receiver = PackageChangeReceiver.register(context) {
            refreshApps()
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    AnimatedContent(
        targetState = screen,
        // A quick crossfade rather than a sliding "sheet" — screens should read as the
        // same persistent OS surface changing state, not as separate apps opening on top.
        transitionSpec = {
            fadeIn(tween(140)) togetherWith fadeOut(tween(140))
        },
        label = "screenTransition"
    ) { currentScreen ->
        when (currentScreen) {
            is Screen.Home -> {
                // A launcher's home screen shouldn't be dismissible with back.
                BackHandler(enabled = true) {}
                HomeScreen(
                    bottomDialApps = resolveGroupApps(RADIAL_GROUP_BOTTOM),
                    upperDialApps = resolveGroupApps(RADIAL_GROUP_UPPER),
                    onLaunch = { AppRepository.launchApp(context, it) },
                    onOpenSearch = { screen = Screen.Search(returnTo = Screen.Home) },
                    onOpenSettings = { screen = Screen.Settings },
                    onSwipeLeft = {
                        val override = resolveGestureApp(SwipeGesture.LEFT)
                        if (override != null) AppRepository.launchApp(context, override) else AppRepository.launchCamera(context)
                    },
                    onSwipeRight = {
                        val override = resolveGestureApp(SwipeGesture.RIGHT)
                        if (override != null) AppRepository.launchApp(context, override) else AppRepository.launchContacts(context)
                    },
                    onSwipeDown = { AppRepository.expandNotifications(context) },
                    expenseTrackerEnabled = expenseTrackerEnabled,
                    todayExpenseTotal = todayExpenseTotal,
                    monthlyExpenseTotal = monthlyExpenseTotal,
                    categoryTotalsMonth = categoryTotalsMonth,
                    untaggedExpenseCount = untaggedExpenseCount,
                    onOpenExpenseTracker = { openExpenseTracker(Screen.Home) },
                )
            }
            is Screen.Settings -> {
                SettingsScreen(
                    resolveSlot = { group, index ->
                        radialGroups[group]?.getOrNull(index)?.let { key -> AppRepository.findByKey(allApps, key) }
                    },
                    onPickSlot = { group, index ->
                        screen = Screen.Search(
                            onPicked = { app ->
                                RadialMenuStore.setSlot(context, group, index, app.key)
                                refreshRadialGroups()
                            },
                            returnTo = Screen.Settings,
                        )
                    },
                    onClearSlot = { group, index ->
                        RadialMenuStore.setSlot(context, group, index, null)
                        refreshRadialGroups()
                    },
                    resolveGesture = { gesture -> resolveGestureApp(gesture) },
                    onPickGesture = { gesture ->
                        screen = Screen.Search(
                            onPicked = { app ->
                                GestureAppStore.setAppKey(context, gesture, app.key)
                                refreshGestureApps()
                            },
                            returnTo = Screen.Settings,
                        )
                    },
                    onClearGesture = { gesture ->
                        GestureAppStore.setAppKey(context, gesture, null)
                        refreshGestureApps()
                    },
                    hiddenApps = hiddenAppsList,
                    onUnhideApp = { app ->
                        HiddenAppsStore.setHidden(context, app.key, false)
                        refreshHidden()
                    },
                    expenseTrackerEnabled = expenseTrackerEnabled,
                    onToggleExpenseTracker = {
                        if (expenseTrackerEnabled) {
                            ExpenseRepository.setEnabled(context, false)
                            expenseTrackerEnabled = false
                        } else if (ExpenseRepository.hasSmsPermission(context)) {
                            ExpenseRepository.setEnabled(context, true)
                            expenseTrackerEnabled = true
                            ExpenseRepository.syncSms(context)
                            expenseRefreshTick++
                        } else {
                            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                        }
                    },
                    onOpenExpenseTracker = { openExpenseTracker(Screen.Settings) },
                    onClose = { screen = Screen.Home },
                )
            }
            is Screen.ExpenseTracker -> {
                val untagged = remember(expenseRefreshTick) { ExpenseRepository.getUntaggedMerchants(context) }
                val todayTotal = remember(expenseRefreshTick) { ExpenseRepository.getTodayTotal(context) }
                val categoryTotals = remember(expenseRefreshTick) { ExpenseRepository.getCategoryTotalsToday(context) }
                val dailyTotals = remember(expenseRefreshTick) { ExpenseRepository.getDailyTotals(context, 14) }
                val recentTransactions = remember(expenseRefreshTick) { ExpenseRepository.getRecentTransactions(context) }
                ExpenseTrackerScreen(
                    todayTotal = todayTotal,
                    categoryTotals = categoryTotals,
                    dailyTotals = dailyTotals,
                    untaggedMerchants = untagged,
                    recentTransactions = recentTransactions,
                    onTagMerchant = { merchant, category ->
                        ExpenseRepository.tagMerchant(context, merchant, category)
                        expenseRefreshTick++
                    },
                    onClose = { screen = currentScreen.returnTo },
                )
            }
            is Screen.Search -> {
                SearchScreen(
                    apps = visibleApps,
                    onLaunch = { AppRepository.launchApp(context, it) },
                    onClose = { screen = currentScreen.returnTo },
                    onPick = currentScreen.onPicked,
                    onUninstallApp = { app -> AppRepository.requestUninstall(context, app) },
                    onRenameApp = { app, newLabel ->
                        AppLabelStore.setCustomLabel(context, app.key, newLabel)
                        refreshApps()
                    },
                    onHideApp = { app ->
                        HiddenAppsStore.setHidden(context, app.key, true)
                        refreshHidden()
                    },
                    onCloseApp = { app -> AppRepository.closeApp(context, app) },
                )
            }
        }
    }
}
