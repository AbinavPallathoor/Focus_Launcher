package com.focuslauncher.app

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String
) {
    val key: String get() = "$packageName/$activityName"
}

object AppRepository {

    /** All installed, launchable apps (label overrides applied), regardless of hidden state. */
    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val selfPackage = context.packageName

        return resolveInfos
            .asSequence()
            .filter { it.activityInfo.packageName != selfPackage }
            .map { ri ->
                AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName,
                    activityName = ri.activityInfo.name
                )
            }
            .distinctBy { it.key }
            .map { app ->
                AppLabelStore.getCustomLabel(context, app.key)?.let { app.copy(label = it) } ?: app
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun findByKey(apps: List<AppInfo>, key: String): AppInfo? = apps.firstOrNull { it.key == key }

    fun launchApp(context: Context, appInfo: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(appInfo.packageName, appInfo.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            context.startActivity(intent)
        } catch (e: PackageManager.NameNotFoundException) {
            // App was uninstalled between query and launch; ignore.
        } catch (e: ActivityNotFoundException) {
            // Same race as above, different exception shape on some OEMs.
        }
    }

    /** Default swipe-left action: jump straight into the device's default camera app. */
    fun launchCamera(context: Context) {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No camera app available; nothing to do.
        }
    }

    /** Default swipe-right action: open the device's default contacts app. */
    fun launchContacts(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No contacts app available; nothing to do.
        }
    }

    /** Opens the system uninstall confirmation for an app — apps can't silently uninstall each other. */
    fun requestUninstall(context: Context, appInfo: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${appInfo.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No uninstaller available (e.g. a system app); nothing to do.
        }
    }

    /**
     * Best-effort "close app": kills the app's background process. Android doesn't let one
     * app force-stop another's foreground activity, so this only reliably affects apps that
     * are already backgrounded.
     */
    fun closeApp(context: Context, appInfo: AppInfo) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.killBackgroundProcesses(appInfo.packageName)
    }
}
