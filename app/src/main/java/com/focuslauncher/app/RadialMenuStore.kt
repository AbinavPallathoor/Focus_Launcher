package com.focuslauncher.app

import android.content.Context

private const val PREFS_NAME = "focus_launcher_prefs"
private const val SLOT_SEPARATOR = "|"

const val SLOTS_PER_RADIAL_GROUP = 6
const val RADIAL_GROUP_BOTTOM = 0
const val RADIAL_GROUP_UPPER = 1
val RADIAL_GROUPS = listOf(RADIAL_GROUP_BOTTOM, RADIAL_GROUP_UPPER)

/** Persists the app assigned to each slot of each of the two radial dial menus. */
object RadialMenuStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(group: Int) = "radial_group_${group}_slots"

    /** Ordered list of slot keys ("packageName/activityName"), empty slots as null, fixed length [SLOTS_PER_RADIAL_GROUP]. */
    fun getSlotKeys(context: Context, group: Int): List<String?> {
        val raw = prefs(context).getString(key(group), null)
        val stored = raw?.split(SLOT_SEPARATOR)?.map { it.ifEmpty { null } } ?: emptyList()
        return List(SLOTS_PER_RADIAL_GROUP) { index -> stored.getOrNull(index) }
    }

    fun setSlot(context: Context, group: Int, index: Int, appKey: String?) {
        if (index !in 0 until SLOTS_PER_RADIAL_GROUP) return
        val slots = getSlotKeys(context, group).toMutableList()
        slots[index] = appKey
        val serialized = slots.joinToString(SLOT_SEPARATOR) { it ?: "" }
        prefs(context).edit().putString(key(group), serialized).apply()
    }
}
