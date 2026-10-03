package com.focuslauncher.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Reads the next upcoming event straight from the device's calendar provider — this already
 * holds whatever's synced from a Google account added on-device, so no separate Google
 * sign-in/OAuth flow is needed here, just the standard runtime calendar permission.
 */
object CalendarRepository {

    data class UpcomingEvent(val title: String, val startMillis: Long, val allDay: Boolean)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun getNextEvent(context: Context): UpcomingEvent? {
        if (!hasPermission(context)) return null

        val now = System.currentTimeMillis()
        val windowEnd = now + TimeUnit.DAYS.toMillis(30)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, now)
            ContentUris.appendId(this, windowEnd)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
        )

        return try {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        UpcomingEvent(
                            title = cursor.getString(0) ?: return null,
                            startMillis = cursor.getLong(1),
                            allDay = cursor.getInt(2) != 0,
                        )
                    } else null
                }
        } catch (e: SecurityException) {
            null
        }
    }

    fun formatEvent(event: UpcomingEvent): String {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_YEAR) to calendar.get(Calendar.YEAR)
        calendar.timeInMillis = event.startMillis
        val eventDay = calendar.get(Calendar.DAY_OF_YEAR) to calendar.get(Calendar.YEAR)

        val whenText = when {
            event.allDay -> "Today"
            eventDay == today -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(event.startMillis)
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(event.startMillis)
        }
        return "${event.title} · $whenText"
    }
}
