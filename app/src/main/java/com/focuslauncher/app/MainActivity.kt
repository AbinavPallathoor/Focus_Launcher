package com.focuslauncher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
    data class Search(
        val onPicked: ((AppInfo) -> Unit)? = null,
        val returnTo: Screen = Home,
    ) : Screen()
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun LauncherRoot() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf(AppRepository.getInstalledApps(context)) }
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
        radialGroups[group].orEmpty().mapNotNull { key -> key?.let { AppRepository.findByKey(apps, it) } }

    fun resolveGestureApp(gesture: SwipeGesture): AppInfo? =
        gestureApps[gesture]?.let { key -> AppRepository.findByKey(apps, key) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                apps = AppRepository.getInstalledApps(context)
                refreshRadialGroups()
                refreshGestureApps()
                screen = Screen.Home
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        val receiver = PackageChangeReceiver.register(context) {
            apps = AppRepository.getInstalledApps(context)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (initialState is Screen.Home && targetState !is Screen.Home) {
                (slideInVertically(tween(320)) { height -> height } + fadeIn(tween(320))) togetherWith
                    fadeOut(tween(200))
            } else {
                fadeIn(tween(220)) togetherWith
                    (slideOutVertically(tween(320)) { height -> height } + fadeOut(tween(260)))
            }.using(SizeTransform(clip = false))
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
                )
            }
            is Screen.Settings -> {
                SettingsScreen(
                    resolveSlot = { group, index ->
                        radialGroups[group]?.getOrNull(index)?.let { key -> AppRepository.findByKey(apps, key) }
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
                    onClose = { screen = Screen.Home },
                )
            }
            is Screen.Search -> {
                SearchScreen(
                    apps = apps,
                    onLaunch = { AppRepository.launchApp(context, it) },
                    onClose = { screen = currentScreen.returnTo },
                    onPick = currentScreen.onPicked,
                )
            }
        }
    }
}
