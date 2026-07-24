/* Validated recognition, runtime, narration, and sound settings. / 经校验的识别、运行时、语音与音效设置。 */
package com.example.pokedex.ui.scanner

import kotlin.math.abs

enum class RecognitionPreset {
    Speed,
    Balanced,
    Quality,
}

data class ModelRuntimeOptions(
    val contextSize: Int = RecognitionTuning.DEFAULT_CONTEXT_SIZE,
    val batchSize: Int = RecognitionTuning.DEFAULT_BATCH_SIZE,
    val threads: Int = RecognitionTuning.defaultThreads(),
) {
    init {
        require(contextSize in RecognitionTuning.CONTEXT_SIZES) { "Unsupported context size: $contextSize" }
        require(batchSize in RecognitionTuning.BATCH_SIZES) { "Unsupported batch size: $batchSize" }
        require(batchSize <= contextSize) { "Batch size cannot exceed context size" }
        require(threads in RecognitionTuning.MIN_THREADS..RecognitionTuning.maxThreads()) {
            "Unsupported thread count: $threads"
        }
    }
}

data class DecodeOptions(
    val maxTokens: Int,
    val penaltyLastN: Int,
    val repetitionPenalty: Float,
    val frequencyPenalty: Float,
    val presencePenalty: Float,
) {
    init {
        require(maxTokens in RecognitionTuning.MIN_SINGLE_TOKENS..RecognitionTuning.MAX_MULTIPLE_TOKENS)
        require(penaltyLastN in RecognitionTuning.MIN_PENALTY_LAST_N..RecognitionTuning.MAX_PENALTY_LAST_N)
        require(repetitionPenalty.isFinite() && repetitionPenalty in RecognitionTuning.MIN_REPETITION_PENALTY..RecognitionTuning.MAX_REPETITION_PENALTY)
        require(frequencyPenalty.isFinite() && frequencyPenalty in RecognitionTuning.MIN_TOKEN_PENALTY..RecognitionTuning.MAX_TOKEN_PENALTY)
        require(presencePenalty.isFinite() && presencePenalty in RecognitionTuning.MIN_TOKEN_PENALTY..RecognitionTuning.MAX_TOKEN_PENALTY)
    }
}

data class RecognitionTuning(
    val singleMaxTokens: Int = DEFAULT_SINGLE_TOKENS,
    val multipleMaxTokens: Int = DEFAULT_MULTIPLE_TOKENS,
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    val imageMaxEdge: Int = DEFAULT_IMAGE_MAX_EDGE,
    val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
    val contextSize: Int = DEFAULT_CONTEXT_SIZE,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val threads: Int = defaultThreads(),
    val penaltyLastN: Int = DEFAULT_PENALTY_LAST_N,
    val repetitionPenalty: Float = DEFAULT_REPETITION_PENALTY,
    val frequencyPenalty: Float = DEFAULT_FREQUENCY_PENALTY,
    val presencePenalty: Float = DEFAULT_PRESENCE_PENALTY,
) {
    fun sanitized(processorCount: Int = Runtime.getRuntime().availableProcessors()): RecognitionTuning {
        val safeThreadMax = maxThreads(processorCount)
        return copy(
            singleMaxTokens = singleMaxTokens.coerceIn(MIN_SINGLE_TOKENS, MAX_SINGLE_TOKENS),
            multipleMaxTokens = multipleMaxTokens.coerceIn(MIN_MULTIPLE_TOKENS, MAX_MULTIPLE_TOKENS),
            timeoutSeconds = timeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
            imageMaxEdge = imageMaxEdge.nearestValue(IMAGE_EDGE_OPTIONS),
            jpegQuality = jpegQuality.coerceIn(MIN_JPEG_QUALITY, MAX_JPEG_QUALITY),
            contextSize = contextSize.nearestValue(CONTEXT_SIZES),
            batchSize = batchSize.nearestValue(BATCH_SIZES),
            threads = threads.coerceIn(MIN_THREADS, safeThreadMax),
            penaltyLastN = penaltyLastN.coerceIn(MIN_PENALTY_LAST_N, MAX_PENALTY_LAST_N),
            repetitionPenalty = repetitionPenalty.finiteOr(DEFAULT_REPETITION_PENALTY)
                .coerceIn(MIN_REPETITION_PENALTY, MAX_REPETITION_PENALTY),
            frequencyPenalty = frequencyPenalty.finiteOr(DEFAULT_FREQUENCY_PENALTY)
                .coerceIn(MIN_TOKEN_PENALTY, MAX_TOKEN_PENALTY),
            presencePenalty = presencePenalty.finiteOr(DEFAULT_PRESENCE_PENALTY)
                .coerceIn(MIN_TOKEN_PENALTY, MAX_TOKEN_PENALTY),
        )
    }

    fun modelRuntimeOptions(): ModelRuntimeOptions {
        val safe = sanitized()
        return ModelRuntimeOptions(
            contextSize = safe.contextSize,
            batchSize = safe.batchSize,
            threads = safe.threads,
        )
    }

    fun decodeOptions(mode: RecognitionMode): DecodeOptions {
        val safe = sanitized()
        return DecodeOptions(
            maxTokens = when (mode) {
                RecognitionMode.Single -> safe.singleMaxTokens
                RecognitionMode.Multiple -> safe.multipleMaxTokens
            },
            penaltyLastN = safe.penaltyLastN,
            repetitionPenalty = safe.repetitionPenalty,
            frequencyPenalty = safe.frequencyPenalty,
            presencePenalty = safe.presencePenalty,
        )
    }

    fun withRuntimeOptions(options: ModelRuntimeOptions): RecognitionTuning = copy(
        contextSize = options.contextSize,
        batchSize = options.batchSize,
        threads = options.threads,
    ).sanitized()

    companion object {
        const val MIN_SINGLE_TOKENS = 48
        const val MAX_SINGLE_TOKENS = 96
        const val MIN_MULTIPLE_TOKENS = 96
        const val MAX_MULTIPLE_TOKENS = 192
        const val MIN_TIMEOUT_SECONDS = 15
        const val MAX_TIMEOUT_SECONDS = 120
        const val MIN_JPEG_QUALITY = 70
        const val MAX_JPEG_QUALITY = 100
        const val MIN_THREADS = 2
        const val MIN_PENALTY_LAST_N = 64
        const val MAX_PENALTY_LAST_N = 256
        const val MIN_REPETITION_PENALTY = 1.0f
        const val MAX_REPETITION_PENALTY = 2.0f
        const val MIN_TOKEN_PENALTY = 0.0f
        const val MAX_TOKEN_PENALTY = 1.0f

        const val DEFAULT_SINGLE_TOKENS = 48
        const val DEFAULT_MULTIPLE_TOKENS = 128
        const val DEFAULT_TIMEOUT_SECONDS = 60
        const val DEFAULT_IMAGE_MAX_EDGE = 448
        const val DEFAULT_JPEG_QUALITY = 90
        const val DEFAULT_CONTEXT_SIZE = 4096
        const val DEFAULT_BATCH_SIZE = 512
        const val DEFAULT_PENALTY_LAST_N = 128
        const val DEFAULT_REPETITION_PENALTY = 1.35f
        const val DEFAULT_FREQUENCY_PENALTY = 0.40f
        const val DEFAULT_PRESENCE_PENALTY = 0.50f

        val IMAGE_EDGE_OPTIONS = listOf(224, 336, 448, 672, 896, 1024)
        val BATCH_SIZES = listOf(128, 256, 512, 1024)
        val CONTEXT_SIZES = listOf(4096, 6144, 8192)

        fun maxThreads(processorCount: Int = Runtime.getRuntime().availableProcessors()): Int =
            processorCount.coerceAtLeast(MIN_THREADS).coerceAtMost(6)

        fun defaultThreads(processorCount: Int = Runtime.getRuntime().availableProcessors()): Int =
            4.coerceAtMost(maxThreads(processorCount))

        fun forPreset(
            preset: RecognitionPreset,
            processorCount: Int = Runtime.getRuntime().availableProcessors(),
        ): RecognitionTuning {
            val balancedThreads = defaultThreads(processorCount)
            return when (preset) {
                RecognitionPreset.Speed -> RecognitionTuning(
                    multipleMaxTokens = MIN_MULTIPLE_TOKENS,
                    timeoutSeconds = 45,
                    imageMaxEdge = 336,
                    jpegQuality = 85,
                    threads = balancedThreads,
                )
                RecognitionPreset.Balanced -> RecognitionTuning(threads = balancedThreads)
                RecognitionPreset.Quality -> RecognitionTuning(
                    singleMaxTokens = 64,
                    multipleMaxTokens = 160,
                    timeoutSeconds = 90,
                    imageMaxEdge = 672,
                    jpegQuality = 95,
                    contextSize = 6144,
                    threads = 5.coerceAtMost(maxThreads(processorCount)),
                )
            }.sanitized(processorCount)
        }
    }
}

data class ScannerSettings(
    val recognitionMode: RecognitionMode = RecognitionMode.Single,
    val recognitionTuning: RecognitionTuning = RecognitionTuning(),
    val narrationSettings: NarrationSettings = NarrationSettings(),
    val soundEffectSettings: SoundEffectSettings = SoundEffectSettings(),
) {
    fun sanitized(): ScannerSettings = copy(
        recognitionTuning = recognitionTuning.sanitized(),
        narrationSettings = narrationSettings.sanitized(),
        soundEffectSettings = soundEffectSettings.sanitized(),
    )
}

private fun Int.nearestValue(options: List<Int>): Int =
    options.minByOrNull { option -> abs(option - this) } ?: this

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
