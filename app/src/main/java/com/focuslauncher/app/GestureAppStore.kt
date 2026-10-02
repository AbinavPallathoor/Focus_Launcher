package com.focuslauncher.app

import android.content.Context

private const val PREFS_NAME = "focus_launcher_prefs"
private const val KEY_SWIPE_LEFT = "gesture_swipe_left_app"
private const val KEY_SWIPE_RIGHT = "gesture_swipe_right_app"

enum class SwipeGesture { LEFT, RIGHT }

/** User overrides for the home screen's horizontal swipe gestures. Null means "use the default". */
object GestureAppStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun keyFor(gesture: SwipeGesture) = when (gesture) {
        SwipeGesture.LEFT -> KEY_SWIPE_LEFT
        SwipeGesture.RIGHT -> KEY_SWIPE_RIGHT
    }

    fun getAppKey(context: Context, gesture: SwipeGesture): String? =
        prefs(context).getString(keyFor(gesture), null)

    fun setAppKey(context: Context, gesture: SwipeGesture, appKey: String?) {
        prefs(context).edit().putString(keyFor(gesture), appKey).apply()
    }
}
