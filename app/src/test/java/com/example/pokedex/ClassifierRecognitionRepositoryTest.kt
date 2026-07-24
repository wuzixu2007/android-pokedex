/* Replaceable classifier repository tests. / 可替换分类器仓库测试。 */
package com.example.pokedex

import com.example.pokedex.ui.scanner.ClassifierPrediction
import com.example.pokedex.ui.scanner.ClassifierRecognitionRepository
import com.example.pokedex.ui.scanner.NormalizedImage
import com.example.pokedex.ui.scanner.PokemonCatalog
import com.example.pokedex.ui.scanner.PokemonClassifierRuntime
import com.example.pokedex.ui.scanner.PokemonRecord
import com.example.pokedex.ui.scanner.RecognitionModelFiles
import com.example.pokedex.ui.scanner.RecognitionMode
import com.example.pokedex.ui.scanner.RecognitionOptions
import com.example.pokedex.ui.scanner.RecognitionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierRecognitionRepositoryTest {
    @Test
    fun classifierOutput_isValidatedSortedAndLimitedToFive() = runBlocking {
        val runtime = FakeClassifierRuntime(
            predictions = listOf(
                prediction(1, 0.2f),
                prediction(0, 0.8f),
                prediction(2, 0.5f),
                prediction(1, 0.9f),
                prediction(3, 0.4f),
                prediction(4, 0.3f),
                prediction(5, 0.1f),
                ClassifierPrediction("Unknown", 1f),
            ),
        )
        val repository = ClassifierRecognitionRepository(catalog(), runtime)
        repository.loadModels(RecognitionModelFiles.Classifier("classifier.tflite"))

        val result = repository.recognize(
            NormalizedImage(byteArrayOf(1)),
            RecognitionOptions.forMode(RecognitionMode.Multiple),
        ) as RecognitionResult.Success

        assertEquals("classifier.tflite", runtime.loadedPath)
        assertEquals(
            listOf("Pokemon 2", "Pokemon 1", "Pokemon 3", "Pokemon 4", "Pokemon 5"),
            result.candidates.map { it.standardName },
        )
        assertEquals(listOf(0.9f, 0.8f, 0.5f, 0.4f, 0.3f), result.candidates.map { it.probability })
    }

    @Test
    fun classifierOutput_requiresFiveValidCandidates() = runBlocking {
        val repository = ClassifierRecognitionRepository(
            catalog = catalog(),
            runtime = FakeClassifierRuntime(listOf(prediction(0, 0.8f))),
        )

        val result = repository.recognize(
            NormalizedImage(byteArrayOf(1)),
            RecognitionOptions.forMode(RecognitionMode.Multiple),
        )

        assertTrue(result is RecognitionResult.Failure)
    }

    @Test
    fun singleMode_returnsOnlyBestCandidate() = runBlocking {
        val repository = ClassifierRecognitionRepository(
            catalog = catalog(),
            runtime = FakeClassifierRuntime(
                listOf(
                    prediction(0, 0.8f),
                    prediction(1, 0.9f),
                    prediction(2, 0.5f),
                ),
            ),
        )

        val result = repository.recognize(
            NormalizedImage(byteArrayOf(1)),
            RecognitionOptions.forMode(RecognitionMode.Single),
        ) as RecognitionResult.Success

        assertEquals(1, result.candidates.size)
        assertEquals("Pokemon 2", result.candidates.single().standardName)
    }

    private fun catalog() = PokemonCatalog.fromRecords(
        List(6) { index ->
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

    private fun prediction(index: Int, probability: Float) = ClassifierPrediction(
        standardName = "Pokemon ${index + 1}",
        probability = probability,
    )

    private class FakeClassifierRuntime(
        private val predictions: List<ClassifierPrediction>,
    ) : PokemonClassifierRuntime {
        var loadedPath: String? = null

        override suspend fun loadModel(modelPath: String) {
            loadedPath = modelPath
        }

        override suspend fun classify(image: NormalizedImage): List<ClassifierPrediction> = predictions

        override fun cancel() = Unit

        override fun close() = Unit
    }
}
