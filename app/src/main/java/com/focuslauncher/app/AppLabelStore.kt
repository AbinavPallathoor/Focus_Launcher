package com.focuslauncher.app

import android.content.Context

private const val PREFS_NAME = "focus_launcher_prefs"
private const val KEY_PREFIX = "label_override_"

/** User-chosen display names that override an app's real label, launcher-side only. */
object AppLabelStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCustomLabel(context: Context, appKey: String): String? =
        prefs(context).getString(KEY_PREFIX + appKey, null)

    fun setCustomLabel(context: Context, appKey: String, label: String?) {
        val editor = prefs(context).edit()
        if (label.isNullOrBlank()) {
            editor.remove(KEY_PREFIX + appKey)
        } else {
            editor.putString(KEY_PREFIX + appKey, label.trim())
        }
        editor.apply()
    }
}
