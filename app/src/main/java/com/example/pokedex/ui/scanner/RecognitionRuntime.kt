/* Replaceable recognition repositories, strict protocol, and JNI runtime. / 可替换识别仓库、严格协议与 JNI 运行时。 */
package com.example.pokedex.ui.scanner

import android.util.Log
import com.example.pokedex.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.Closeable
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class NormalizedImage(
    val jpeg: ByteArray,
    val feedbackJpeg: ByteArray = jpeg,
)

data class RecognitionCandidate(
    val standardName: String,
    val probability: Float?,
)

data class RecognitionOptions(
    val mode: RecognitionMode,
    val decodeOptions: DecodeOptions,
) {
    val maxTokens: Int get() = decodeOptions.maxTokens

    companion object {
        fun forMode(
            mode: RecognitionMode,
            tuning: RecognitionTuning = RecognitionTuning(),
        ): RecognitionOptions = RecognitionOptions(
            mode = mode,
            decodeOptions = tuning.decodeOptions(mode),
        )
    }
}

sealed interface RecognitionResult {
    data class Success(val candidates: List<RecognitionCandidate>) : RecognitionResult
    data class Failure(val message: String) : RecognitionResult
}

sealed interface RecognitionModelFiles {
    data class LegacyVisionLanguage(
        val languageModelPath: String,
        val visionModelPath: String,
    ) : RecognitionModelFiles

    data class Classifier(val modelPath: String) : RecognitionModelFiles
}

interface VisionLanguageRuntime : Closeable {
    suspend fun loadModels(
        languageModelPath: String,
        visionModelPath: String,
        options: ModelRuntimeOptions,
    )

    suspend fun recognize(
        image: NormalizedImage,
        prompt: String,
        grammar: String,
        options: DecodeOptions,
    ): String

    fun cancel()
}

interface RecognitionRepository : Closeable {
    suspend fun loadModels(
        modelFiles: RecognitionModelFiles,
        options: ModelRuntimeOptions = ModelRuntimeOptions(),
    )
    suspend fun recognize(image: NormalizedImage, options: RecognitionOptions): RecognitionResult
    fun cancel()
}

class LegacyVlmRecognitionRepository(
    private val catalog: PokemonCatalog,
    private val runtime: VisionLanguageRuntime,
) : RecognitionRepository {
    private val inferenceMutex = Mutex()

    override suspend fun loadModels(modelFiles: RecognitionModelFiles, options: ModelRuntimeOptions) {
        require(modelFiles is RecognitionModelFiles.LegacyVisionLanguage) {
            "旧版 VLM 运行时需要语言模型和视觉投影模型"
        }
        runtime.loadModels(modelFiles.languageModelPath, modelFiles.visionModelPath, options)
    }

    override suspend fun recognize(
        image: NormalizedImage,
        options: RecognitionOptions,
    ): RecognitionResult = inferenceMutex.withLock {
        val rawOutput = runtime.recognize(
            image = image,
            prompt = recognitionPrompt(options.mode),
            grammar = catalog.candidateGrammar(options.mode.candidateCount),
            options = options.decodeOptions,
        )
        if (BuildConfig.DEBUG) runCatching { Log.d(DEBUG_TAG, "raw_candidate_output=$rawOutput") }
        runCatching {
            PokemonCandidateProtocol.parse(
                raw = rawOutput,
                catalog = catalog,
                expectedCount = options.mode.candidateCount,
            )
        }
            .onFailure { error ->
                if (BuildConfig.DEBUG) runCatching { Log.e(DEBUG_TAG, "candidate_protocol_error", error) }
            }
            .getOrElse { return@withLock RecognitionResult.Failure(OUTPUT_PROTOCOL_ERROR) }
            .let { candidates -> RecognitionResult.Success(candidates) }
    }

    override fun cancel() = runtime.cancel()

    override fun close() = runtime.close()

    companion object {
        const val SINGLE_RECOGNITION_PROMPT =
            "你是宝可梦图像分类器。识别图片中占主体的宝可梦。" +
                "只能输出一个只包含一项的 JSON 数组。该项只能包含 name 和 probability 两个字段。" +
                "name 必须是候选集合中的简体中文标准名称。" +
                "probability 必须是 0 到 100 之间的一位小数或整数，表示置信度百分比。" +
                "禁止输出解释、Markdown、代码块、额外字段或任何 JSON 之外的字符。"
        const val MULTIPLE_RECOGNITION_PROMPT =
            "你是宝可梦图像分类器。识别图片中占主体的宝可梦。" +
                "只能输出一个 JSON 数组，数组必须包含五个不同的候选宝可梦。" +
                "每项只能包含 name 和 probability 两个字段。" +
                "name 必须是候选集合中的简体中文标准名称。" +
                "这不是重复确认任务。第一项选择最可能的宝可梦；第二项必须排除第一项后选择次可能的名称；" +
                "第三项必须排除前两项；第四项必须排除前三项；第五项必须排除前四项。任何名称都不得重复。" +
                "probability 必须是 0 到 100 之间的一位小数或整数，表示模型对该候选的置信度百分比。" +
                "例如百分之八十必须输出 80.0，禁止输出 0.8。" +
                "候选必须按照 probability 从高到低排列。" +
                "禁止输出解释、标点说明、Markdown、代码块、额外字段或任何 JSON 之外的字符。"
        const val OUTPUT_PROTOCOL_ERROR = "AI 输出不符合候选 JSON 协议，请重试"
        private const val DEBUG_TAG = "PokedexRecognition"

        fun recognitionPrompt(mode: RecognitionMode): String = when (mode) {
            RecognitionMode.Single -> SINGLE_RECOGNITION_PROMPT
            RecognitionMode.Multiple -> MULTIPLE_RECOGNITION_PROMPT
        }
    }
}

object PokemonCandidateProtocol {
    private val requiredKeys = setOf("name", "probability")

    fun parse(
        raw: String,
        catalog: PokemonCatalog,
        expectedCount: Int = MAX_RESULT_CANDIDATES,
    ): List<RecognitionCandidate> {
        require(expectedCount > 0) { "候选数量必须大于零" }
        require(raw.isNotEmpty() && raw == raw.trim()) { "输出前后包含额外字符" }
        val array = JSONArray(raw)
        require(array.length() == expectedCount) { "候选数量不符合当前识别模式" }
        val names = HashSet<String>(expectedCount)
        return buildList(expectedCount) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val keys = item.keys().asSequence().toSet()
                require(keys == requiredKeys) { "第 ${index + 1} 项字段不符合协议" }
                val name = item.getString("name")
                require(catalog.findExact(name) != null) { "未知标准名称：$name" }
                require(names.add(name)) { "候选名称重复：$name" }
                val probability = item.getDouble("probability")
                require(probability.isFinite() && probability in 0.0..100.0) { "概率超出范围：$probability" }
                add(
                    RecognitionCandidate(
                        standardName = name,
                        probability = (probability / 100.0).toFloat(),
                    ),
                )
            }
        }.sortedByDescending { it.probability ?: 0f }
    }
}

data class ClassifierPrediction(
    val standardName: String,
    val probability: Float,
)

/** Runtime boundary for the trained on-device image classifier. */
interface PokemonClassifierRuntime : Closeable {
    suspend fun loadModel(modelPath: String)
    suspend fun classify(image: NormalizedImage): List<ClassifierPrediction>
    fun cancel()
}

class ClassifierRecognitionRepository(
    private val catalog: PokemonCatalog,
    private val runtime: PokemonClassifierRuntime,
) : RecognitionRepository {
    private val inferenceMutex = Mutex()

    override suspend fun loadModels(modelFiles: RecognitionModelFiles, options: ModelRuntimeOptions) {
        require(modelFiles is RecognitionModelFiles.Classifier) {
            "分类器运行时需要单个分类模型文件"
        }
        runtime.loadModel(modelFiles.modelPath)
    }

    override suspend fun recognize(
        image: NormalizedImage,
        options: RecognitionOptions,
    ): RecognitionResult = inferenceMutex.withLock {
        val candidates = runtime.classify(image)
            .asSequence()
            .filter { prediction ->
                prediction.probability.isFinite() &&
                    prediction.probability in 0f..1f &&
                    catalog.findExact(prediction.standardName) != null
            }
            .sortedByDescending(ClassifierPrediction::probability)
            .distinctBy(ClassifierPrediction::standardName)
            .take(options.mode.candidateCount)
            .map { prediction ->
                RecognitionCandidate(
                    standardName = prediction.standardName,
                    probability = prediction.probability,
                )
            }
            .toList()

        if (candidates.size != options.mode.candidateCount) {
            RecognitionResult.Failure("分类模型没有返回当前模式所需的有效候选")
        } else {
            RecognitionResult.Success(candidates)
        }
    }

    override fun cancel() = runtime.cancel()

    override fun close() = runtime.close()
}

class NativeVisionLanguageRuntime : VisionLanguageRuntime {
    private val handleLock = Any()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pokedex-vlm").apply { priority = Thread.NORM_PRIORITY }
    }
    private var handle: Long = 0L
    private var closed = false

    override suspend fun loadModels(
        languageModelPath: String,
        visionModelPath: String,
        options: ModelRuntimeOptions,
    ) {
        suspendCancellableCoroutine { continuation ->
            executor.execute {
                try {
                    val previous = synchronized(handleLock) {
                        check(!closed) { "本地 AI 运行时已关闭" }
                        handle.also { handle = 0L }
                    }
                    if (previous != 0L) {
                        NativeBindings.cancel(previous)
                        NativeBindings.destroy(previous)
                    }
                    val created = NativeBindings.create(
                        languageModelPath = languageModelPath,
                        visionModelPath = visionModelPath,
                        contextSize = options.contextSize,
                        batchSize = options.batchSize,
                        threads = options.threads,
                    )
                    check(created != 0L) { "本地 AI 模型加载失败" }
                    if (continuation.isActive) {
                        synchronized(handleLock) { handle = created }
                        continuation.resume(Unit)
                    } else {
                        NativeBindings.destroy(created)
                    }
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }
    }

    override suspend fun recognize(
        image: NormalizedImage,
        prompt: String,
        grammar: String,
        options: DecodeOptions,
    ): String = suspendCancellableCoroutine { continuation ->
        val activeHandle = synchronized(handleLock) {
            check(!closed) { "本地 AI 运行时已关闭" }
            handle.also { check(it != 0L) { "本地 AI 模型尚未加载" } }
        }
        executor.execute {
            try {
                val result = NativeBindings.recognize(
                    handle = activeHandle,
                    imageJpeg = image.jpeg,
                    prompt = prompt,
                    grammar = grammar,
                    maxTokens = options.maxTokens,
                    penaltyLastN = options.penaltyLastN,
                    repetitionPenalty = options.repetitionPenalty,
                    frequencyPenalty = options.frequencyPenalty,
                    presencePenalty = options.presencePenalty,
                )
                if (continuation.isActive) continuation.resume(result)
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
        continuation.invokeOnCancellation { NativeBindings.cancel(activeHandle) }
    }

    override fun cancel() {
        synchronized(handleLock) { handle }.takeIf { it != 0L }?.let(NativeBindings::cancel)
    }

    override fun close() {
        val activeHandle = synchronized(handleLock) {
            if (closed) return
            closed = true
            handle.also { handle = 0L }
        }
        if (activeHandle != 0L) {
            NativeBindings.cancel(activeHandle)
            executor.execute { NativeBindings.destroy(activeHandle) }
        }
        executor.shutdown()
    }

}

/** Stable class name used by JNI RegisterNatives. */
object NativeBindings {
    init {
        System.loadLibrary("pokedex_inference")
    }

    external fun create(
        languageModelPath: String,
        visionModelPath: String,
        contextSize: Int,
        batchSize: Int,
        threads: Int,
    ): Long

    external fun recognize(
        handle: Long,
        imageJpeg: ByteArray,
        prompt: String,
        grammar: String,
        maxTokens: Int,
        penaltyLastN: Int,
        repetitionPenalty: Float,
        frequencyPenalty: Float,
        presencePenalty: Float,
    ): String

    external fun cancel(handle: Long)
    external fun destroy(handle: Long)
}
