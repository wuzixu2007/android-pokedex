/* Legacy VLM repository protocol tests. / 旧版 VLM 仓库协议测试。 */
package com.example.pokedex

import com.example.pokedex.ui.scanner.DecodeOptions
import com.example.pokedex.ui.scanner.LegacyVlmRecognitionRepository
import com.example.pokedex.ui.scanner.ModelRuntimeOptions
import com.example.pokedex.ui.scanner.NormalizedImage
import com.example.pokedex.ui.scanner.PokemonCatalog
import com.example.pokedex.ui.scanner.PokemonRecord
import com.example.pokedex.ui.scanner.RecognitionModelFiles
import com.example.pokedex.ui.scanner.RecognitionMode
import com.example.pokedex.ui.scanner.RecognitionOptions
import com.example.pokedex.ui.scanner.RecognitionResult
import com.example.pokedex.ui.scanner.RecognitionTuning
import com.example.pokedex.ui.scanner.VisionLanguageRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyVlmRecognitionRepositoryTest {
    @Test
    fun modelAndDecodeOptions_crossTheRuntimeBoundary() = runBlocking {
        val runtime = FakeVisionRuntime()
        val repository = LegacyVlmRecognitionRepository(catalog(), runtime)
        val modelOptions = ModelRuntimeOptions(contextSize = 6144, batchSize = 1024, threads = 5)
        val tuning = RecognitionTuning(
            multipleMaxTokens = 160,
            penaltyLastN = 192,
            repetitionPenalty = 1.5f,
            frequencyPenalty = 0.25f,
            presencePenalty = 0.75f,
        )

        repository.loadModels(
            RecognitionModelFiles.LegacyVisionLanguage("language.gguf", "vision.gguf"),
            modelOptions,
        )
        val result = repository.recognize(
            NormalizedImage(byteArrayOf(1, 2, 3)),
            RecognitionOptions.forMode(RecognitionMode.Multiple, tuning),
        )

        assertEquals(modelOptions, runtime.loadedOptions)
        assertEquals(tuning.decodeOptions(RecognitionMode.Multiple), runtime.decodeOptions)
        // Local JVM tests use Android's stub JSONArray, but the call still proves
        // that both option groups crossed the repository/runtime boundary.
        assertTrue(result is RecognitionResult.Failure)
    }

    private fun catalog() = PokemonCatalog.fromRecords(
        List(5) { index ->
            PokemonRecord(
                key = "p$index",
                id = index.toString(),
                nameZh = "Pokemon ${index + 1}",
                nameEn = "Pokemon ${index + 1}",
                types = listOf("Normal"),
                attributeLabel = "Normal Pokemon",
                category = "Pokemon",
                height = "1.0m",
                weight = "1.0kg",
                description = "",
                profile = "Profile",
                imageAsset = "",
            )
        },
    )

    private class FakeVisionRuntime : VisionLanguageRuntime {
        var loadedOptions: ModelRuntimeOptions? = null
        var decodeOptions: DecodeOptions? = null

        override suspend fun loadModels(
            languageModelPath: String,
            visionModelPath: String,
            options: ModelRuntimeOptions,
        ) {
            loadedOptions = options
        }

        override suspend fun recognize(
            image: NormalizedImage,
            prompt: String,
            grammar: String,
            options: DecodeOptions,
        ): String {
            decodeOptions = options
            return """[{"name":"Pokemon 1","probability":90.0},{"name":"Pokemon 2","probability":4.0},{"name":"Pokemon 3","probability":3.0},{"name":"Pokemon 4","probability":2.0},{"name":"Pokemon 5","probability":1.0}]"""
        }

        override fun cancel() = Unit

        override fun close() = Unit
    }
}
