/* Settings range and reload-contract tests. / 设置范围与重载契约测试。 */
package com.example.pokedex

import com.example.pokedex.ui.scanner.ModelRuntimeOptions
import com.example.pokedex.ui.scanner.RecognitionMode
import com.example.pokedex.ui.scanner.RecognitionPreset
import com.example.pokedex.ui.scanner.RecognitionTuning
import com.example.pokedex.ui.scanner.ScannerMode
import com.example.pokedex.ui.scanner.ScannerSettings
import com.example.pokedex.ui.scanner.ScannerUiState
import com.example.pokedex.ui.scanner.SoundEffectSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerSettingsTest {
    @Test
    fun soundSettings_sanitizeInvalidVolumeWithoutDisablingPreference() {
        val invalid = SoundEffectSettings(enabled = false, volume = Float.NaN).sanitized()
        val tooLoud = SoundEffectSettings(volume = 4f).sanitized()

        assertFalse(invalid.enabled)
        assertEquals(SoundEffectSettings.DEFAULT_VOLUME, invalid.volume)
        assertEquals(1f, tooLoud.volume)
    }

    @Test
    fun tuningSanitization_clampsAndSnapsEveryExposedValue() {
        val sanitized = RecognitionTuning(
            singleMaxTokens = 1,
            multipleMaxTokens = 999,
            timeoutSeconds = 3,
            imageMaxEdge = 500,
            jpegQuality = 120,
            contextSize = 7000,
            batchSize = 900,
            threads = 12,
            penaltyLastN = 500,
            repetitionPenalty = Float.NaN,
            frequencyPenalty = -1f,
            presencePenalty = 2f,
        ).sanitized(processorCount = 6)

        assertEquals(48, sanitized.singleMaxTokens)
        assertEquals(192, sanitized.multipleMaxTokens)
        assertEquals(15, sanitized.timeoutSeconds)
        assertEquals(448, sanitized.imageMaxEdge)
        assertEquals(100, sanitized.jpegQuality)
        assertEquals(6144, sanitized.contextSize)
        assertEquals(1024, sanitized.batchSize)
        assertEquals(6, sanitized.threads)
        assertEquals(256, sanitized.penaltyLastN)
        assertEquals(1.35f, sanitized.repetitionPenalty)
        assertEquals(0f, sanitized.frequencyPenalty)
        assertEquals(1f, sanitized.presencePenalty)
    }

    @Test
    fun presets_keepBalancedDefaultsAndDoNotTouchModeOrNarration() {
        val current = ScannerSettings(recognitionMode = RecognitionMode.Multiple)
        val balanced = RecognitionTuning.forPreset(RecognitionPreset.Balanced, processorCount = 6)
        val speed = RecognitionTuning.forPreset(RecognitionPreset.Speed, processorCount = 6)
        val quality = RecognitionTuning.forPreset(RecognitionPreset.Quality, processorCount = 6)

        assertEquals(RecognitionTuning(threads = 4), balanced)
        assertEquals(336, speed.imageMaxEdge)
        assertEquals(96, speed.multipleMaxTokens)
        assertEquals(672, quality.imageMaxEdge)
        assertEquals(6144, quality.contextSize)
        assertEquals(RecognitionMode.Multiple, current.copy(recognitionTuning = speed).recognitionMode)
    }

    @Test
    fun runtimeAndDecodeOptions_areDerivedFromSanitizedSettings() {
        val tuning = RecognitionTuning(
            singleMaxTokens = 64,
            multipleMaxTokens = 160,
            contextSize = 6144,
            batchSize = 1024,
            threads = 5,
            penaltyLastN = 192,
            repetitionPenalty = 1.5f,
            frequencyPenalty = 0.25f,
            presencePenalty = 0.75f,
        )

        assertEquals(ModelRuntimeOptions(6144, 1024, 5), tuning.modelRuntimeOptions())
        val single = tuning.decodeOptions(RecognitionMode.Single)
        val multiple = tuning.decodeOptions(RecognitionMode.Multiple)
        assertEquals(64, single.maxTokens)
        assertEquals(160, multiple.maxTokens)
        assertEquals(192, multiple.penaltyLastN)
        assertEquals(1.5f, multiple.repetitionPenalty)
        assertEquals(0.25f, multiple.frequencyPenalty)
        assertEquals(0.75f, multiple.presencePenalty)
    }

    @Test
    fun uiState_marksOnlyRuntimeDifferencesAsPendingReload() {
        val active = ModelRuntimeOptions()
        val hotChange = ScannerSettings(
            recognitionTuning = RecognitionTuning(singleMaxTokens = 64),
        )
        val coldChange = ScannerSettings(
            recognitionTuning = RecognitionTuning(contextSize = 6144),
        )

        assertFalse(
            ScannerUiState(
                mode = ScannerMode.Preview,
                scannerSettings = hotChange,
                activeRuntimeOptions = active,
            ).hasPendingRuntimeSettings,
        )
        assertTrue(
            ScannerUiState(
                mode = ScannerMode.Preview,
                scannerSettings = coldChange,
                activeRuntimeOptions = active,
            ).hasPendingRuntimeSettings,
        )
    }
}
