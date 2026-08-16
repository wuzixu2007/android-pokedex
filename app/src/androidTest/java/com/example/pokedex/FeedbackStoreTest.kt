package com.example.pokedex

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokedex.data.scanner.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FeedbackStoreTest {
    @Test
    fun saveDeduplicateAndRemove() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testDirectory = File(context.cacheDir, "feedback-store-test-${UUID.randomUUID()}")
        val store = FeedbackStore(context, File(testDirectory, "data"))
        try {
            val request = FeedbackSaveRequest(
                imageJpeg = byteArrayOf(1, 2, 3, 4),
                correctLabel = FeedbackLabel("p0007_v00", "杰尼龟"),
                predictions = listOf(FeedbackPrediction("p0006_v00", "喷火龙", 0.72f)),
                confirmedCorrect = false,
                modelVersion = "test-v1",
            )

            val first = store.save(request)
            val duplicate = store.save(request)

            assertTrue(first.created)
            assertFalse(duplicate.created)
            assertEquals(first.sampleId, duplicate.sampleId)
            assertEquals(1, store.sampleCount.value)
            assertTrue(store.remove(first.sampleId))
            assertEquals(0, store.sampleCount.value)
        } finally {
            testDirectory.deleteRecursively()
        }
    }
}
