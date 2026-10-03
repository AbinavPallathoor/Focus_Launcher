package com.focuslauncher.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Fetches the on-device classifier's model straight from Hugging Face using Android's built-in
 * DownloadManager — no custom streaming/progress code needed, and it survives the app being
 * closed, retries on its own, and can be Wi-Fi-restricted for a ~1 GB file. The model (Qwen2.5
 * 1.5B Instruct, Apache-2.0, quantized to GGUF) is hosted in the official Qwen org repo, which
 * is publicly downloadable with no Hugging Face login/token required.
 */
object ModelDownloader {
    private const val MODEL_FILE_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    private const val MODEL_URL =
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"

    private const val PREFS = "model_downloader"
    private const val KEY_DOWNLOAD_ID = "download_id"

    data class DownloadProgress(val status: Int, val bytesDownloaded: Long, val bytesTotal: Long) {
        val fraction: Float get() = if (bytesTotal > 0) (bytesDownloaded.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
    }

    fun modelFile(context: Context): File = File(context.getExternalFilesDir(null), MODEL_FILE_NAME)

    fun isModelReady(context: Context): Boolean = modelFile(context).let { it.exists() && it.length() > 0 }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun downloadManager(context: Context) =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun startDownload(context: Context) {
        modelFile(context).delete() // a half-finished file from a cancelled attempt shouldn't linger
        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Focus Launcher — AI model")
            .setDescription("Qwen2.5-1.5B-Instruct (~1.0 GB)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(modelFile(context)))
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            .setAllowedOverRoaming(false)
        val id = downloadManager(context).enqueue(request)
        prefs(context).edit().putLong(KEY_DOWNLOAD_ID, id).apply()
    }

    fun cancelDownload(context: Context) {
        val id = prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)
        if (id != -1L) downloadManager(context).remove(id)
        prefs(context).edit().remove(KEY_DOWNLOAD_ID).apply()
        modelFile(context).delete()
    }

    /** Null if nothing has ever been enqueued (or it was already cleared after completing). */
    fun currentProgress(context: Context): DownloadProgress? {
        val id = prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)
        if (id == -1L) return null
        downloadManager(context).query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                prefs(context).edit().remove(KEY_DOWNLOAD_ID).apply()
            }
            return DownloadProgress(status, downloaded, total)
        }
    }
}
