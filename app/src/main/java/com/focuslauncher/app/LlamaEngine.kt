package com.focuslauncher.app

/**
 * Thin JNI bridge to a vendored llama.cpp (see `src/main/cpp/`). Deliberately tiny: load a
 * GGUF model once, run grammar-constrained generation against it, nothing else. The grammar is
 * supplied fresh on every [generate] call rather than fixed at [load] time, since more than one
 * feature now shares this one loaded model against different output schemas (the per-transaction
 * classifier, the dashboard's free-text command bar). All the "is this output trustworthy" logic
 * lives in Kotlin (TransactionClassifier, DashboardCommandInterpreter), not here.
 *
 * Every entry point is wrapped against Throwable (not just the expected UnsatisfiedLinkError):
 * a native crash here must degrade to "couldn't classify, try again later" for the caller, never
 * take down the whole app — especially important while the native lib may not even be built yet.
 */
object LlamaEngine {
    @Volatile private var nativeLibAvailable: Boolean? = null // null = not attempted yet
    @Volatile private var loaded = false

    private fun ensureNativeLib(): Boolean {
        nativeLibAvailable?.let { return it }
        val available = try {
            System.loadLibrary("focuslauncher_llama")
            true
        } catch (e: Throwable) {
            false
        }
        nativeLibAvailable = available
        return available
    }

    fun isLoaded(): Boolean = loaded

    /** Loads the model using [threads] CPU threads for inference. Safe to call repeatedly; a
     * no-op if already loaded (the thread count only takes effect on the load that actually
     * happens). */
    @Synchronized
    fun load(modelPath: String, threads: Int): Boolean {
        if (loaded) return true
        if (!ensureNativeLib()) return false
        loaded = try {
            nativeLoad(modelPath, threads.coerceAtLeast(1))
        } catch (e: Throwable) {
            false
        }
        return loaded
    }

    /** Runs the prompt through the model with [grammarText] constraining the output for this
     * one call; null on any failure. */
    @Synchronized
    fun generate(prompt: String, maxTokens: Int, grammarText: String): String? {
        if (!loaded) return null
        return try {
            nativeGenerate(prompt, maxTokens, grammarText)
        } catch (e: Throwable) {
            null
        }
    }

    @Synchronized
    fun unload() {
        if (loaded) {
            try {
                nativeUnload()
            } catch (e: Throwable) {
                // Already in an unknown state; there's nothing more to clean up from here.
            }
            loaded = false
        }
    }

    private external fun nativeLoad(modelPath: String, threads: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, grammarText: String): String?
    private external fun nativeUnload()
}
