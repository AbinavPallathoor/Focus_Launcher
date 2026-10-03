package com.focuslauncher.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Fires the moment a new SMS arrives (not just when the app is next opened/resumed, like the
 * old resume-polling sync). Deliberately does nothing but check the feature is on and hand off
 * to WorkManager — broadcast receivers must return fast, and the actual sync + LLM
 * classification can take real seconds.
 */
class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!ExpenseRepository.isEnabled(context)) return
        val request = OneTimeWorkRequestBuilder<ClassifyTransactionWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
