package com.focuslauncher.app

import android.content.Context

/**
 * Shared "is the model ready, load it if not" check used by every LLM-backed feature
 * (TransactionClassifier, DashboardCommandInterpreter) — the model itself loads once and is
 * grammar-agnostic; each caller supplies its own grammar asset per [LlamaEngine.generate] call.
 */
object LlamaModelLoader {
    fun ensureLoaded(context: Context): Boolean {
        if (LlamaEngine.isLoaded()) return true
        if (!ModelDownloader.isModelReady(context)) return false
        return LlamaEngine.load(
            ModelDownloader.modelFile(context).absolutePath,
            ClassifierPerformanceStore.threadCount(context),
        )
    }

    fun readGrammar(context: Context, assetName: String): String =
        context.assets.open(assetName).bufferedReader().use { it.readText() }
}
