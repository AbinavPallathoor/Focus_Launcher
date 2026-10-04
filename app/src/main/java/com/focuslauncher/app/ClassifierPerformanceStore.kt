package com.focuslauncher.app

import android.content.Context

/**
 * How many CPU threads llama.cpp uses for inference. The emulator used throughout development
 * is slow regardless of this setting, but a real phone has real cores to spend — this just
 * decides how many of them classification is allowed to claim while it runs. Takes effect the
 * next time the model loads (a session that's already loaded keeps its original thread count
 * until the app restarts or the model is unloaded).
 */
enum class ClassifierPerformanceMode(val label: String) {
    BATTERY_SAVER("Low"),
    BALANCED("Medium"),
    PERFORMANCE("High");

    companion object {
        fun fromStorage(value: String?): ClassifierPerformanceMode =
            entries.firstOrNull { it.name == value } ?: BALANCED
    }
}

object ClassifierPerformanceStore {
    private const val PREFS_NAME = "focus_launcher_prefs"
    private const val KEY_MODE = "classifier_performance_mode"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMode(context: Context): ClassifierPerformanceMode =
        ClassifierPerformanceMode.fromStorage(prefs(context).getString(KEY_MODE, null))

    fun setMode(context: Context, mode: ClassifierPerformanceMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    /** Resolves the mode against this device's actual core count, not a hardcoded number — the
     * same "Performance" setting uses every core on whatever phone it's running on. */
    fun threadCount(context: Context): Int {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val minUseful = 2.coerceAtMost(cores)
        return when (getMode(context)) {
            ClassifierPerformanceMode.BATTERY_SAVER -> minUseful
            ClassifierPerformanceMode.BALANCED -> (cores / 2).coerceIn(minUseful, cores)
            ClassifierPerformanceMode.PERFORMANCE -> cores
        }
    }
}
