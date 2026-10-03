package com.focuslauncher.app

import android.content.Context

private const val PREFS_NAME = "focus_launcher_prefs"
private const val KEY_HIDDEN_APPS = "hidden_apps"

/** Apps hidden from the search/app list — still resolvable for dial/gesture assignments. */
object HiddenAppsStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHiddenKeys(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_HIDDEN_APPS, emptySet()) ?: emptySet()

    fun setHidden(context: Context, appKey: String, hidden: Boolean) {
        val updated = getHiddenKeys(context).toMutableSet()
        if (hidden) updated.add(appKey) else updated.remove(appKey)
        prefs(context).edit().putStringSet(KEY_HIDDEN_APPS, updated).apply()
    }
}
