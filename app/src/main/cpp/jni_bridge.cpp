// JNI bridge between LlamaEngine.kt and llama.cpp. Deliberately minimal: load a GGUF model
// once, run one grammar-constrained generation per call, nothing else. The grammar is passed
// fresh on every generate() call rather than fixed at load time — different features (the
// per-transaction classifier, the dashboard's free-text command bar) need different output
// schemas against the same loaded model. Every exported function is defensive about failure
// (returns false/null rather than letting an exception escape) — Kotlin-side callers already
// treat any failure here as "couldn't run that right now".

#include <jni.h>
#include <android/log.h>
#include <chrono>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "FocusLauncherLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
llama_model* g_model = nullptr;
llama_context* g_ctx = nullptr;
const llama_vocab* g_vocab = nullptr;

void unloadLocked() {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_focuslauncher_app_LlamaEngine_nativeLoad(JNIEnv* env, jobject /*thiz*/, jstring modelPath, jint threads) {
    const char* modelPathChars = env->GetStringUTFChars(modelPath, nullptr);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU-only: no GPU backend built in for this target

    g_model = llama_model_load_from_file(modelPathChars, model_params);

    env->ReleaseStringUTFChars(modelPath, modelPathChars);

    if (!g_model) {
        LOGE("Failed to load model from file");
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);

    const int32_t n_threads = threads > 0 ? threads : 4;
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_batch = 512;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        unloadLocked();
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_focuslauncher_app_LlamaEngine_nativeGenerate(JNIEnv* env, jobject /*thiz*/, jstring promptJ, jint maxTokens, jstring grammarJ) {
    if (!g_model || !g_ctx || !g_vocab) {
        return nullptr;
    }

    const char* promptChars = env->GetStringUTFChars(promptJ, nullptr);
    std::string prompt(promptChars);
    env->ReleaseStringUTFChars(promptJ, promptChars);

    const char* grammarChars = env->GetStringUTFChars(grammarJ, nullptr);
    std::string grammarText(grammarChars);
    env->ReleaseStringUTFChars(grammarJ, grammarChars);

    // Apply the model's own embedded chat template so an instruct-tuned model like Qwen2.5
    // actually follows the instructions instead of just continuing the raw text.
    std::string formatted_prompt;
    const char* tmpl = llama_model_chat_template(g_model, nullptr);
    if (tmpl) {
        llama_chat_message msg{"user", prompt.c_str()};
        std::vector<char> buf(prompt.size() * 2 + 256);
        int32_t n = llama_chat_apply_template(tmpl, &msg, 1, true, buf.data(), (int32_t) buf.size());
        if (n > (int32_t) buf.size()) {
            buf.resize(n);
            n = llama_chat_apply_template(tmpl, &msg, 1, true, buf.data(), (int32_t) buf.size());
        }
        if (n > 0) {
            formatted_prompt.assign(buf.data(), n);
        } else {
            formatted_prompt = prompt;
        }
    } else {
        formatted_prompt = prompt;
    }

    int32_t n_prompt = -llama_tokenize(
        g_vocab, formatted_prompt.c_str(), (int32_t) formatted_prompt.size(), nullptr, 0, true, true);
    std::vector<llama_token> prompt_tokens(n_prompt);
    if (llama_tokenize(
            g_vocab, formatted_prompt.c_str(), (int32_t) formatted_prompt.size(),
            prompt_tokens.data(), (int32_t) prompt_tokens.size(), true, true) < 0) {
        LOGE("Tokenization failed");
        return nullptr;
    }

    // Each classification call is independent — start from a clean KV cache every time.
    llama_memory_clear(llama_get_memory(g_ctx), true);

    // Grammar carries parse state, so the sampler chain is rebuilt fresh for every call.
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    llama_sampler* grammar = llama_sampler_init_grammar(g_vocab, grammarText.c_str(), "root");
    if (!grammar) {
        LOGE("Failed to parse grammar");
        llama_sampler_free(smpl);
        return nullptr;
    }
    llama_sampler_chain_add(smpl, grammar);
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    LOGI("n_threads=%d n_threads_batch=%d prompt_tokens=%d", llama_n_threads(g_ctx), llama_n_threads_batch(g_ctx), n_prompt);

    std::string result;
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), (int32_t) prompt_tokens.size());

    auto t_start = std::chrono::steady_clock::now();
    bool first = true;
    for (int32_t generated = 0; generated < maxTokens; generated++) {
        auto t0 = std::chrono::steady_clock::now();
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("llama_decode failed at step %d", generated);
            break;
        }
        auto t1 = std::chrono::steady_clock::now();
        llama_token new_token = llama_sampler_sample(smpl, g_ctx, -1);
        auto t2 = std::chrono::steady_clock::now();
        auto decode_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count();
        auto sample_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t2 - t1).count();
        if (first || generated < 5 || generated % 20 == 0) {
            LOGI("step %d: decode=%lldms sample=%lldms", generated, (long long) decode_ms, (long long) sample_ms);
        }
        first = false;
        if (llama_vocab_is_eog(g_vocab, new_token)) {
            LOGI("hit EOG at step %d", generated);
            break;
        }
        char piece_buf[256];
        int32_t piece_len = llama_token_to_piece(g_vocab, new_token, piece_buf, sizeof(piece_buf), 0, true);
        if (piece_len > 0) {
            result.append(piece_buf, piece_len);
        }
        batch = llama_batch_get_one(&new_token, 1);
    }
    auto t_end = std::chrono::steady_clock::now();
    LOGI("generation total: %lldms, result length=%zu", (long long) std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count(), result.size());
    LOGI("result: %s", result.c_str());

    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_focuslauncher_app_LlamaEngine_nativeUnload(JNIEnv* /*env*/, jobject /*thiz*/) {
    unloadLocked();
}
