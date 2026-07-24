// JNI bridge for llama.cpp/libmtmd multimodal inference. / llama.cpp/libmtmd 多模态推理 JNI 桥接。
#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <chrono>
#include <cmath>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include "common.h"
#include "chat.h"
#include "ggml-backend.h"
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

namespace {

constexpr const char * kLogTag = "PokedexNative";

#ifndef NDEBUG
void debugLog(const char * message, long long elapsed_ms = -1, long long detail = -1) {
    if (elapsed_ms >= 0 && detail >= 0) {
        __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s elapsed_ms=%lld detail=%lld",
                            message, elapsed_ms, detail);
    } else if (elapsed_ms >= 0) {
        __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s elapsed_ms=%lld", message, elapsed_ms);
    } else {
        __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s", message);
    }
}
#else
void debugLog(const char *, long long = -1, long long = -1) {}
#endif

using SteadyClock = std::chrono::steady_clock;

long long elapsedMillis(const SteadyClock::time_point & start) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(SteadyClock::now() - start).count();
}

bool isAllowedContextSize(int value) {
    return value == 4096 || value == 6144 || value == 8192;
}

bool isAllowedBatchSize(int value) {
    return value == 128 || value == 256 || value == 512 || value == 1024;
}

void throwJava(JNIEnv * env, const char * class_name, const std::string & message) {
    jclass klass = env->FindClass(class_name);
    if (klass != nullptr) {
        env->ThrowNew(klass, message.c_str());
        env->DeleteLocalRef(klass);
    }
}

bool isCancelled(void * data) {
    return static_cast<std::atomic_bool *>(data)->load(std::memory_order_relaxed);
}

bool modelLoadProgress(float, void * data) {
    return !isCancelled(data);
}

bool abortDecode(void * data) {
    return isCancelled(data);
}

struct MtmdContextDeleter {
    void operator()(mtmd_context * value) const {
        if (value != nullptr) mtmd_free(value);
    }
};

struct BitmapDeleter {
    void operator()(mtmd_bitmap * value) const {
        if (value != nullptr) mtmd_bitmap_free(value);
    }
};

struct ChunkDeleter {
    void operator()(mtmd_input_chunks * value) const {
        if (value != nullptr) mtmd_input_chunks_free(value);
    }
};

struct BatchDeleter {
    void operator()(mtmd_batch * value) const {
        if (value != nullptr) mtmd_batch_free(value);
    }
};

using MtmdPtr = std::unique_ptr<mtmd_context, MtmdContextDeleter>;
using BitmapPtr = std::unique_ptr<mtmd_bitmap, BitmapDeleter>;
using ChunksPtr = std::unique_ptr<mtmd_input_chunks, ChunkDeleter>;
using BatchPtr = std::unique_ptr<mtmd_batch, BatchDeleter>;

class Runtime {
public:
    Runtime(const std::string & language_path,
            const std::string & vision_path,
            int context_size,
            int batch_size,
            int threads)
        : cancelled_(false),
          context_size_(context_size),
          batch_size_(batch_size) {
        if (!isAllowedContextSize(context_size_) || !isAllowedBatchSize(batch_size_) ||
            batch_size_ > context_size_ || threads < 2 || threads > 6) {
            throw std::invalid_argument("本地 AI 模型运行参数超出安全范围");
        }
        const auto load_start = SteadyClock::now();
        debugLog("model_load_started");
        static std::once_flag backend_once;
        std::call_once(backend_once, [] {
            llama_backend_init();
            common_init();
        });

        common_params params;
        params.model.path = language_path;
        params.n_ctx = context_size_;
        params.n_batch = batch_size_;
        params.n_ubatch = params.n_batch;
        params.n_parallel = 1;
        params.n_gpu_layers = 0;
        params.fit_params = false;
        params.use_mmap = true;
        params.warmup = false;
        params.no_perf = true;
        params.cpuparams.n_threads = threads > 0 ? threads : 2;
        params.cpuparams_batch.n_threads = params.cpuparams.n_threads;
        params.load_progress_callback = modelLoadProgress;
        params.load_progress_callback_user_data = &cancelled_;

        llama_init_ = common_init_from_params(params);
        if (!llama_init_ || llama_init_->model() == nullptr || llama_init_->context() == nullptr) {
            throw std::runtime_error("语言模型加载失败");
        }
        llama_set_abort_callback(llama_init_->context(), abortDecode, &cancelled_);

        templates_ = common_chat_templates_init(llama_init_->model(), "");
        if (!templates_) {
            throw std::runtime_error("模型聊天模板加载失败");
        }

        mtmd_context_params vision_params = mtmd_context_params_default();
        vision_params.use_gpu = false;
        vision_params.print_timings = false;
        vision_params.n_threads = params.cpuparams.n_threads;
        vision_params.warmup = false;
        vision_params.batch_max_tokens = params.n_batch;
        // Observing every graph node serializes vision execution on Android.
        // Cancellation is checked between image chunks; llama_decode keeps its
        // dedicated CPU abort callback.
        vision_params.cb_eval = nullptr;
        vision_params.cb_eval_user_data = nullptr;
        vision_params.progress_callback = modelLoadProgress;
        vision_params.progress_callback_user_data = &cancelled_;
        vision_ = MtmdPtr(mtmd_init_from_file(vision_path.c_str(), llama_init_->model(), vision_params));
        if (!vision_ || !mtmd_support_vision(vision_.get())) {
            throw std::runtime_error("视觉投影模型加载失败");
        }
        debugLog("model_load_completed", elapsedMillis(load_start));
    }

    ~Runtime() {
        cancelled_.store(true, std::memory_order_relaxed);
        std::lock_guard<std::mutex> lock(operation_mutex_);
        // Members are released after this body.  The lock ensures an active
        // recognition call has left the native engine before that happens.
    }

    void cancel() {
        cancelled_.store(true, std::memory_order_relaxed);
    }

    std::string recognize(const unsigned char * jpeg,
                          size_t jpeg_size,
                          const std::string & prompt,
                          const std::string & grammar,
                          int max_tokens,
                          int penalty_last_n,
                          float repetition_penalty,
                          float frequency_penalty,
                          float presence_penalty) {
        std::lock_guard<std::mutex> lock(operation_mutex_);
        const auto recognition_start = SteadyClock::now();
        debugLog("recognition_started");
        cancelled_.store(false, std::memory_order_relaxed);
        if (jpeg == nullptr || jpeg_size == 0) throw std::runtime_error("图像数据为空");
        if (max_tokens < 48 || max_tokens > 192 || penalty_last_n < 64 || penalty_last_n > 256 ||
            !std::isfinite(repetition_penalty) || repetition_penalty < 1.0f || repetition_penalty > 2.0f ||
            !std::isfinite(frequency_penalty) || frequency_penalty < 0.0f || frequency_penalty > 1.0f ||
            !std::isfinite(presence_penalty) || presence_penalty < 0.0f || presence_penalty > 1.0f) {
            throw std::invalid_argument("本地 AI 解码参数超出安全范围");
        }

        llama_memory_clear(llama_get_memory(llama_init_->context()), true);
        llama_set_abort_callback(llama_init_->context(), abortDecode, &cancelled_);

        common_chat_templates_inputs inputs;
        inputs.use_jinja = true;
        inputs.enable_thinking = false;
        inputs.add_generation_prompt = true;
        inputs.messages.push_back({"system", prompt});
        inputs.messages.push_back({
            "user",
            std::string(mtmd_default_marker()) + "\n识别图片中的宝可梦。",
        });
        const common_chat_params chat_params = common_chat_templates_apply(templates_.get(), inputs);
        if (chat_params.prompt.empty()) throw std::runtime_error("聊天模板未生成有效提示词");

        auto image_result = mtmd_helper_bitmap_init_from_buf(
            vision_.get(), jpeg, jpeg_size, false);
        if (image_result.bitmap == nullptr) {
            if (image_result.video_ctx != nullptr) mtmd_helper_video_free(image_result.video_ctx);
            throw std::runtime_error("视觉模型无法解码 JPEG 图像");
        }
        BitmapPtr bitmap(image_result.bitmap);
        if (image_result.video_ctx != nullptr) mtmd_helper_video_free(image_result.video_ctx);
        const mtmd_bitmap * bitmaps[] = { bitmap.get() };

        mtmd_input_text text{};
        text.text = chat_params.prompt.c_str();
        text.text_len = chat_params.prompt.size();
        text.add_special = true;
        text.parse_special = true;
        ChunksPtr chunks(mtmd_input_chunks_init());
        if (!chunks) throw std::runtime_error("无法创建视觉输入块");
        int32_t result = mtmd_tokenize(vision_.get(), chunks.get(), &text, bitmaps, 1);
        if (result != 0) throw std::runtime_error("视觉输入分词失败");

        llama_pos n_past = 0;
        const size_t chunk_count = mtmd_input_chunks_size(chunks.get());
        debugLog("input_tokenized", elapsedMillis(recognition_start), static_cast<long long>(chunk_count));
        for (size_t index = 0; index < chunk_count; ++index) {
            if (cancelled_.load(std::memory_order_relaxed)) throw std::runtime_error("识别已取消");
            const mtmd_input_chunk * chunk = mtmd_input_chunks_get(chunks.get(), index);
            if (mtmd_input_chunk_get_type(chunk) == MTMD_INPUT_CHUNK_TYPE_TEXT) {
                llama_pos next = n_past;
                result = mtmd_helper_eval_chunk_single(
                    vision_.get(), llama_init_->context(), chunk, n_past, 0,
                    batch_size_, index + 1 == chunk_count, &next);
                if (result != 0) throw std::runtime_error("文本输入计算失败");
                n_past = next;
                continue;
            }

            BatchPtr batch(mtmd_batch_init(vision_.get()));
            if (!batch || mtmd_batch_add_chunk(batch.get(), chunk) != 0) {
                throw std::runtime_error("视觉输入批处理失败");
            }
            if (mtmd_batch_encode(batch.get()) != 0) {
                throw std::runtime_error("视觉编码失败");
            }
            debugLog("vision_chunk_encoded", elapsedMillis(recognition_start), static_cast<long long>(index));
            float * embedding = mtmd_batch_get_output_embd(batch.get(), chunk);
            if (embedding == nullptr) throw std::runtime_error("视觉编码没有输出");
            llama_pos next = n_past;
            result = mtmd_helper_decode_image_chunk(
                vision_.get(), llama_init_->context(), chunk, embedding, n_past, 0,
                batch_size_, &next, nullptr, nullptr);
            if (result != 0) throw std::runtime_error("视觉特征注入失败");
            n_past = next;
        }

        if (cancelled_.load(std::memory_order_relaxed)) throw std::runtime_error("识别已取消");
        auto sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>(
            llama_sampler_chain_init(llama_sampler_chain_default_params()), &llama_sampler_free);
        if (!sampler) throw std::runtime_error("无法创建解码器");
        auto grammar_sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>(
            llama_sampler_init_grammar(llama_model_get_vocab(llama_init_->model()), grammar.c_str(), "root"),
            &llama_sampler_free);
        if (!grammar_sampler) throw std::runtime_error("候选五项 JSON 语法初始化失败");
        llama_sampler_chain_add(sampler.get(), grammar_sampler.release());
        // The JSON schema repeats the same candidate shape five times. Penalize
        // recently emitted name tokens so greedy decoding can advance to the
        // next-best class instead of copying rank one into every slot.
        llama_sampler_chain_add(
            sampler.get(),
            llama_sampler_init_penalties(
                penalty_last_n,
                repetition_penalty,
                frequency_penalty,
                presence_penalty));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());

        std::vector<llama_token> generated;
        generated.reserve(max_tokens > 0 ? max_tokens : 64);
        std::string output;
        const int limit = max_tokens > 0 ? max_tokens : 64;
        for (int index = 0; index < limit; ++index) {
            if (cancelled_.load(std::memory_order_relaxed)) throw std::runtime_error("识别已取消");
            llama_token token = llama_sampler_sample(sampler.get(), llama_init_->context(), -1);
            if (llama_vocab_is_eog(llama_model_get_vocab(llama_init_->model()), token)) break;
            generated.push_back(token);
            char piece[256];
            int32_t piece_size = llama_token_to_piece(
                llama_model_get_vocab(llama_init_->model()), token, piece, sizeof(piece), 0, false);
            if (piece_size > 0) output.append(piece, static_cast<size_t>(piece_size));
            llama_batch next = llama_batch_get_one(&token, 1);
            if (llama_decode(llama_init_->context(), next) != 0) {
                throw std::runtime_error("文本生成计算失败");
            }
            ++n_past;
            if (n_past >= static_cast<llama_pos>(context_size_)) break;
        }
        debugLog("recognition_completed", elapsedMillis(recognition_start), static_cast<long long>(generated.size()));
        return output;
    }

private:
    std::atomic_bool cancelled_;
    std::mutex operation_mutex_;
    int context_size_;
    int batch_size_;
    common_init_result_ptr llama_init_;
    common_chat_templates_ptr templates_;
    MtmdPtr vision_;
};

Runtime * fromHandle(jlong handle) {
    return reinterpret_cast<Runtime *>(static_cast<uintptr_t>(handle));
}

jlong nativeCreate(JNIEnv * env, jobject, jstring language, jstring vision,
                    jint context_size, jint batch_size, jint threads) {
    if (language == nullptr || vision == nullptr) {
        throwJava(env, "java/lang/IllegalArgumentException", "模型路径不能为空");
        return 0;
    }
    const char * language_chars = env->GetStringUTFChars(language, nullptr);
    const char * vision_chars = env->GetStringUTFChars(vision, nullptr);
    try {
        auto * runtime = new Runtime(language_chars, vision_chars, context_size, batch_size, threads);
        env->ReleaseStringUTFChars(language, language_chars);
        env->ReleaseStringUTFChars(vision, vision_chars);
        return static_cast<jlong>(reinterpret_cast<uintptr_t>(runtime));
    } catch (const std::bad_alloc &) {
        env->ReleaseStringUTFChars(language, language_chars);
        env->ReleaseStringUTFChars(vision, vision_chars);
        throwJava(env, "java/lang/OutOfMemoryError", "本地 AI 模型内存不足");
    } catch (const std::exception & error) {
        env->ReleaseStringUTFChars(language, language_chars);
        env->ReleaseStringUTFChars(vision, vision_chars);
        throwJava(env, "java/lang/IllegalStateException", error.what());
    }
    return 0;
}

jstring nativeRecognize(JNIEnv * env, jobject, jlong handle, jbyteArray image,
                        jstring prompt, jstring grammar, jint max_tokens,
                        jint penalty_last_n, jfloat repetition_penalty,
                        jfloat frequency_penalty, jfloat presence_penalty) {
    auto * runtime = fromHandle(handle);
    if (runtime == nullptr || image == nullptr || prompt == nullptr || grammar == nullptr) {
        throwJava(env, "java/lang/IllegalArgumentException", "识别参数不能为空");
        return nullptr;
    }
    const jsize size = env->GetArrayLength(image);
    std::vector<unsigned char> jpeg(static_cast<size_t>(size));
    env->GetByteArrayRegion(image, 0, size, reinterpret_cast<jbyte *>(jpeg.data()));
    const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    const char * grammar_chars = env->GetStringUTFChars(grammar, nullptr);
    try {
        const std::string result = runtime->recognize(
            jpeg.data(), jpeg.size(), prompt_chars, grammar_chars, max_tokens,
            penalty_last_n, repetition_penalty, frequency_penalty, presence_penalty);
        env->ReleaseStringUTFChars(prompt, prompt_chars);
        env->ReleaseStringUTFChars(grammar, grammar_chars);
        return env->NewStringUTF(result.c_str());
    } catch (const std::bad_alloc &) {
        env->ReleaseStringUTFChars(prompt, prompt_chars);
        env->ReleaseStringUTFChars(grammar, grammar_chars);
        throwJava(env, "java/lang/OutOfMemoryError", "本地 AI 识别内存不足");
    } catch (const std::exception & error) {
        env->ReleaseStringUTFChars(prompt, prompt_chars);
        env->ReleaseStringUTFChars(grammar, grammar_chars);
        throwJava(env, "java/lang/IllegalStateException", error.what());
    }
    return nullptr;
}

void nativeCancel(JNIEnv *, jobject, jlong handle) {
    if (auto * runtime = fromHandle(handle)) runtime->cancel();
}

void nativeDestroy(JNIEnv *, jobject, jlong handle) {
    delete fromHandle(handle);
}

JNINativeMethod kMethods[] = {
    {"create", "(Ljava/lang/String;Ljava/lang/String;III)J", reinterpret_cast<void *>(nativeCreate)},
    {"recognize", "(J[BLjava/lang/String;Ljava/lang/String;IIFFF)Ljava/lang/String;", reinterpret_cast<void *>(nativeRecognize)},
    {"cancel", "(J)V", reinterpret_cast<void *>(nativeCancel)},
    {"destroy", "(J)V", reinterpret_cast<void *>(nativeDestroy)},
};

} // namespace

JNIEXPORT jint JNI_OnLoad(JavaVM * vm, void *) {
    JNIEnv * env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass bindings = env->FindClass("com/example/pokedex/ui/scanner/NativeBindings");
    if (bindings == nullptr) return JNI_ERR;
    const int count = static_cast<int>(sizeof(kMethods) / sizeof(kMethods[0]));
    const jint result = env->RegisterNatives(bindings, kMethods, count);
    env->DeleteLocalRef(bindings);
    return result == JNI_OK ? JNI_VERSION_1_6 : JNI_ERR;
}
