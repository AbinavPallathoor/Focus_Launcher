package com.focuslauncher.app

/**
 * Thin JNI bridge to a vendored llama.cpp (see `src/main/cpp/`). Deliberately tiny: load a
 * GGUF model once, run grammar-constrained generation against it, nothing else. All the
 * "is this output trustworthy" logic lives in Kotlin (TransactionClassifier), not here.
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

    /** Loads the model and compiles [grammarText] once. Safe to call repeatedly; a no-op if already loaded. */
    @Synchronized
    fun load(modelPath: String, grammarText: String): Boolean {
        if (loaded) return true
        if (!ensureNativeLib()) return false
        loaded = try {
            nativeLoad(modelPath, grammarText)
        } catch (e: Throwable) {
            false
        }
        return loaded
    }

    /** Runs the prompt through the model with the loaded grammar applied; null on any failure. */
    @Synchronized
    fun generate(prompt: String, maxTokens: Int): String? {
        if (!loaded) return null
        return try {
            nativeGenerate(prompt, maxTokens)
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

    private external fun nativeLoad(modelPath: String, grammarText: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?
    private external fun nativeUnload()
}
