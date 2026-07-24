/* Compose scanner interaction tests. / Compose 扫描器交互测试。 */
package com.example.pokedex

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pokedex.ui.scanner.ModelSetStatus
import com.example.pokedex.ui.scanner.ModelRuntimeOptions
import com.example.pokedex.ui.scanner.NarratorVoiceOption
import com.example.pokedex.ui.scanner.FeedbackUiState
import com.example.pokedex.ui.scanner.PokemonCatalog
import com.example.pokedex.ui.scanner.PokemonRecord
import com.example.pokedex.ui.scanner.PokemonStats
import com.example.pokedex.ui.scanner.RankedPokemonCandidate
import com.example.pokedex.ui.scanner.RecognitionMode
import com.example.pokedex.ui.scanner.RecognitionTuning
import com.example.pokedex.ui.scanner.ScannerMode
import com.example.pokedex.ui.scanner.ScannerPage
import com.example.pokedex.ui.scanner.ScannerSettings
import com.example.pokedex.ui.scanner.ScannerScreenContent
import com.example.pokedex.ui.scanner.ScannerUiState
import com.example.pokedex.ui.theme.PokedexTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ScannerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val catalog = PokemonCatalog.fromRecords(
        List(5) { index -> record(index, "Pokemon ${index + 1}") },
    )
    private val candidates = List(5) { index ->
        RankedPokemonCandidate(
            pokemonIndex = index,
            probability = listOf(0.964f, 0.018f, 0.011f, 0.005f, 0.002f)[index],
        )
    }

    private fun setFakeContent(
        initialState: ScannerUiState = ScannerUiState(ScannerMode.Preview),
        onConfirmFeedback: () -> Unit = {},
        onCorrectFeedback: (Int) -> Unit = {},
        onCorrectNotPokemon: () -> Unit = {},
        availableNarrationVoices: List<NarratorVoiceOption> = emptyList(),
    ) {
        composeRule.setContent {
            var state by remember { mutableStateOf(initialState) }
            PokedexTheme {
                ScannerScreenContent(
                    state = state.copy(modelStatus = ModelSetStatus(true, true)),
                    catalog = catalog,
                    surfaceRequest = null,
                    useFakeCamera = true,
                    availableNarrationVoices = availableNarrationVoices,
                    onPrimaryAction = {
                        state = when (state.mode) {
                            ScannerMode.Preview -> state.copy(
                                mode = ScannerMode.Result(0),
                                candidates = candidates,
                            )
                            is ScannerMode.Result -> ScannerUiState(ScannerMode.Preview)
                            else -> state
                        }
                    },
                    onOpenCatalog = { state = state.copy(page = ScannerPage.Catalog) },
                    onOpenPokemonDetail = { index ->
                        state = state.copy(page = ScannerPage.CatalogDetail, catalogPokemonIndex = index)
                    },
                    onBackToCamera = { state = ScannerUiState(ScannerMode.Preview) },
                    onMoveResult = { delta ->
                        val current = state.mode as? ScannerMode.Result
                        if (current != null) {
                            state = state.copy(
                                mode = ScannerMode.Result(
                                    (current.candidateIndex + delta).mod(state.candidates.size),
                                ),
                            )
                        }
                    },
                    onSaveSettings = { newSettings -> state = state.copy(scannerSettings = newSettings) },
                    onSaveSettingsAndReload = { newSettings -> state = state.copy(scannerSettings = newSettings) },
                    onTestSettings = {},
                    onPreviewNarration = {},
                    onRequestPermission = {},
                    onRetry = { state = ScannerUiState(ScannerMode.Preview) },
                    onImportLanguage = {},
                    onImportVision = {},
                    onConfirmFeedback = onConfirmFeedback,
                    onCorrectFeedback = onCorrectFeedback,
                    onCorrectNotPokemon = onCorrectNotPokemon,
                    onUndoFeedback = {},
                    onExportFeedback = {},
                )
            }
        }
    }

    @Test
    fun capture_showsFiveRankedCandidates() {
        setFakeContent()
        composeRule.onNodeWithTag("capture_button").performClick()

        composeRule.onNodeWithTag("candidate_status").assertIsDisplayed()
        composeRule.onNodeWithText("96.4%").assertIsDisplayed()
    }

    @Test
    fun dpadHorizontalChangesCandidateAndVerticalIsDisabled() {
        setFakeContent(
            ScannerUiState(
                mode = ScannerMode.Result(0),
                candidates = candidates,
            ),
        )

        composeRule.onNodeWithTag("dpad_right").performClick()
        composeRule.onNodeWithText("候选 2/5").assertIsDisplayed()
        composeRule.onNodeWithText("1.8%").assertIsDisplayed()
        composeRule.onNodeWithTag("dpad_up").assertIsNotEnabled()
        composeRule.onNodeWithTag("dpad_down").assertIsNotEnabled()
    }

    @Test
    fun singleCandidate_disablesDpadAndShowsOneOfOne() {
        setFakeContent(
            ScannerUiState(
                mode = ScannerMode.Result(0),
                candidates = candidates.take(1),
                scannerSettings = ScannerSettings(recognitionMode = RecognitionMode.Single),
            ),
        )

        composeRule.onNodeWithText("候选 1/1").assertIsDisplayed()
        composeRule.onNodeWithTag("dpad_left").assertIsNotEnabled()
        composeRule.onNodeWithTag("dpad_right").assertIsNotEnabled()
    }

    @Test
    fun settingsSelectsMultipleMode() {
        setFakeContent()

        composeRule.onNodeWithTag("recognition_settings_button").performClick()
        composeRule.onNodeWithTag("recognition_settings_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("recognition_mode_multiple").performClick()
        composeRule.onNodeWithTag("settings_save").performClick()

        composeRule.onNodeWithTag("recognition_settings_button").performClick()
        composeRule.onNodeWithTag("recognition_mode_multiple").assertIsDisplayed()
    }

    @Test
    fun catalogRow_opensCompletePokemonDetail() {
        setFakeContent()

        composeRule.onNodeWithTag("catalog_button").performClick()
        composeRule.onNodeWithTag("catalog_item_p0000_v00").performClick()

        composeRule.onNodeWithTag("catalog_detail_page").assertIsDisplayed()
        composeRule.onNodeWithTag("catalog_detail_name").assertIsDisplayed()
        composeRule.onNodeWithTag("catalog_detail_profile").assertIsDisplayed()
    }

    @Test
    fun voicePresetAndCustomSlidersAreAvailable() {
        setFakeContent()

        composeRule.onNodeWithTag("recognition_settings_button").performClick()
        composeRule.onNodeWithTag("settings_tab_Voice").performClick()
        composeRule.onNodeWithTag("voice_preset_DeepMale").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_preset_StandardMale").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_preset_FastMale").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_preset_Custom").assertIsDisplayed()
        composeRule.onNodeWithTag("speech_rate_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("speech_pitch_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("speech_volume_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("tts_voice_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("narration_preview").assertIsDisplayed()
    }

    @Test
    fun installedChineseVoiceCanBeSelected() {
        setFakeContent(
            availableNarrationVoices = listOf(
                NarratorVoiceOption("zh-cn-male", "中文男声 1", likelyMale = true),
            ),
        )

        composeRule.onNodeWithTag("recognition_settings_button").performClick()
        composeRule.onNodeWithTag("settings_tab_Voice").performClick()
        composeRule.onNodeWithTag("tts_voice_selector").performClick()
        composeRule.onNodeWithTag("tts_voice_zh-cn-male").performClick()
        composeRule.onNodeWithText("中文男声 1").assertIsDisplayed()
    }

    @Test
    fun soundConsole_exposesBuiltInReplacementControls() {
        setFakeContent()

        composeRule.onNodeWithTag("recognition_settings_button").performClick()
        composeRule.onNodeWithTag("settings_tab_Sound").performClick()
        composeRule.onNodeWithTag("sound_enabled_true").assertIsDisplayed()
        composeRule.onNodeWithTag("sound_volume_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("sound_preview_ScanStarted").assertIsDisplayed()
        composeRule.onNodeWithTag("sound_import_ScanStarted").assertIsDisplayed()
    }

    @Test
    fun settingsConsole_exposesBasicPerformanceAndDecodingParameters() {
        setFakeContent()

        composeRule.onNodeWithTag("recognition_settings_button").performClick()
        composeRule.onNodeWithTag("single_tokens_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("image_edge_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_tab_Performance").performClick()
        composeRule.onNodeWithTag("threads_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("batch_size_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("context_size_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_tab_Decoding").performClick()
        composeRule.onNodeWithTag("repetition_penalty_slider").assertIsDisplayed()
        composeRule.onNodeWithText("已锁定：严格 JSON 语法与名称白名单").assertIsDisplayed()
    }

    @Test
    fun changedRuntimeParameters_showExplicitReloadAction() {
        setFakeContent(
            ScannerUiState(
                mode = ScannerMode.Preview,
                scannerSettings = ScannerSettings(
                    recognitionTuning = RecognitionTuning(contextSize = 6144),
                ),
                activeRuntimeOptions = ModelRuntimeOptions(),
            ),
        )

        composeRule.onNodeWithTag("recognition_settings_button").performClick()
        composeRule.onNodeWithTag("settings_tab_Performance").performClick()
        composeRule.onNodeWithTag("runtime_pending").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_save_reload").assertIsDisplayed()
    }

    @Test
    fun resultShowsLargePokemonImageInLeftPanel() {
        setFakeContent(
            ScannerUiState(
                mode = ScannerMode.Result(0),
                candidates = candidates,
                scannerSettings = ScannerSettings(recognitionMode = RecognitionMode.Multiple),
            ),
        )

        composeRule.onNodeWithTag("captured_photo_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("result_pokemon_thumbnail").assertIsDisplayed()
        val panelBounds = composeRule.onNodeWithTag("captured_photo_panel").fetchSemanticsNode().boundsInRoot
        val thumbnailBounds = composeRule.onNodeWithTag("result_pokemon_thumbnail").fetchSemanticsNode().boundsInRoot
        assertEquals(panelBounds.center.x, thumbnailBounds.center.x, 1f)
        assertTrue(thumbnailBounds.center.y > panelBounds.center.y)
    }

    @Test
    fun analyzing_showsFrozenCapturedPhoto() {
        setFakeContent(
            ScannerUiState(
                mode = ScannerMode.Capturing,
                capturedImageJpeg = byteArrayOf(1, 2, 3),
            ),
        )

        composeRule.onNodeWithTag("captured_photo").assertIsDisplayed()
        composeRule.onNodeWithTag("thinking_progress").assertIsDisplayed()
    }

    @Test
    fun sideButtonsExposeCatalogAndCameraBackActions() {
        setFakeContent()
        composeRule.onNodeWithTag("catalog_button").assertIsDisplayed()
        composeRule.onNodeWithTag("back_button").assertIsNotEnabled()
    }

    @Test
    fun catalogSearchFiltersByNumberAndBackReturnsToCamera() {
        setFakeContent()

        composeRule.onNodeWithTag("catalog_button").performClick()
        composeRule.onNodeWithTag("catalog_page").assertIsDisplayed()
        composeRule.onNodeWithTag("catalog_search").performTextInput("0003")
        composeRule.onNodeWithText("#0003  Pokemon 4").assertIsDisplayed()
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.onNodeWithTag("scanner_viewport").assertIsDisplayed()
    }

    @Test
    fun permissionState_exposesEnableAction() {
        setFakeContent(ScannerUiState(ScannerMode.PermissionRequired))
        composeRule.onNodeWithTag("permission_button").assertIsDisplayed()
    }

    @Test
    fun resultCanBeConfirmedOrCorrectedWithCatalogName() {
        val confirmed = AtomicInteger(0)
        val correctedIndex = AtomicInteger(-1)
        setFakeContent(
            initialState = ScannerUiState(
                mode = ScannerMode.Result(0),
                capturedImageJpeg = byteArrayOf(1),
                candidates = candidates,
            ),
            onConfirmFeedback = { confirmed.incrementAndGet() },
            onCorrectFeedback = correctedIndex::set,
        )

        composeRule.onNodeWithTag("feedback_confirm").performClick()
        assertEquals(1, confirmed.get())
        composeRule.onNodeWithTag("feedback_correct").performClick()
        composeRule.onNodeWithTag("correction_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("correction_search").performTextInput("Pokemon 4")
        composeRule.onNodeWithTag("correction_option_3").performClick()
        assertEquals(3, correctedIndex.get())
    }

    @Test
    fun savedFeedbackShowsUndoAndExportCount() {
        setFakeContent(
            ScannerUiState(
                mode = ScannerMode.Result(0),
                candidates = candidates,
                feedback = FeedbackUiState(
                    sampleCount = 12,
                    savedSampleId = "sample-id",
                    message = "已确认识别结果",
                ),
            ),
        )

        composeRule.onNodeWithTag("feedback_saved").assertIsDisplayed()
        composeRule.onNodeWithTag("feedback_undo").assertIsDisplayed()
        composeRule.onNodeWithText("导出 12").assertIsDisplayed()
    }

    private fun record(index: Int, name: String) = PokemonRecord(
        key = "p${index.toString().padStart(4, '0')}_v00",
        id = index.toString().padStart(4, '0'),
        nameZh = name,
        nameEn = name,
        nameJa = "Japanese ${index + 1}",
        types = listOf("Normal"),
        attributeLabel = "Normal Pokemon",
        category = "Pokemon",
        height = "1.0m",
        weight = "1.0kg",
        ability = "Ability ${index + 1}",
        stats = PokemonStats(45, 49, 49, 65, 65, 45),
        description = "",
        profile = "Profile ${index + 1}",
        imageAsset = "pokemon/images/missing.png",
    )
}
