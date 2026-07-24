/* Device feedback persistence tests. / 真机反馈持久化测试。 */
package com.example.pokedex

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokedex.ui.scanner.FeedbackLabel
import com.example.pokedex.ui.scanner.FeedbackPrediction
import com.example.pokedex.ui.scanner.FeedbackSaveRequest
import com.example.pokedex.ui.scanner.FeedbackStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class FeedbackStoreTest {
    @Test
    fun saveDeduplicateExportAndRemove() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testDirectory = File(context.cacheDir, "feedback-store-test-${UUID.randomUUID()}")
        val store = FeedbackStore(
            context = context,
            rootDirectory = File(testDirectory, "data"),
            exportDirectory = File(testDirectory, "exports"),
        )
        try {
            val request = FeedbackSaveRequest(
                imageJpeg = byteArrayOf(1, 2, 3, 4),
                correctLabel = FeedbackLabel("p0007_v00", "杰尼龟"),
                predictions = listOf(
                    FeedbackPrediction("p0006_v00", "喷火龙", 0.72f),
                ),
                confirmedCorrect = false,
                modelVersion = "test-v1",
            )

            val first = store.save(request)
            val duplicate = store.save(request)

            assertTrue(first.created)
            assertFalse(duplicate.created)
            assertEquals(first.sampleId, duplicate.sampleId)
            assertEquals(1, store.sampleCount.value)

            val archive = store.export()
            ZipFile(archive).use { zip ->
                assertTrue(zip.getEntry("images/${first.sampleId}.jpg") != null)
                val annotations = zip.getInputStream(zip.getEntry("annotations.jsonl"))
                    .bufferedReader(Charsets.UTF_8)
                    .readLine()
                assertEquals("杰尼龟", JSONObject(annotations).getString("correctName"))
                val manifest = JSONObject(
                    zip.getInputStream(zip.getEntry("manifest.json"))
                        .bufferedReader(Charsets.UTF_8)
                        .readText(),
                )
                assertEquals(1, manifest.getInt("sampleCount"))
            }

            assertTrue(store.remove(first.sampleId))
            assertEquals(0, store.sampleCount.value)
        } finally {
            testDirectory.deleteRecursively()
        }
    }
}
