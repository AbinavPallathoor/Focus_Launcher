package com.focuslauncher.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Runs off the SmsReceivedReceiver (a new SMS just arrived) and also enqueued manually after
 * toggling the tracker on. Survives process death/doze unlike a plain coroutine would, which
 * matters here since classification can involve real multi-second LLM inference.
 */
class ClassifyTransactionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ExpenseRepository.syncSms(applicationContext)
        ExpenseRepository.classifyUnknownTransactions(applicationContext)
        return Result.success()
    }
}
