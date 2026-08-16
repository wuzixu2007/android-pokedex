package com.example.pokedex

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pokedex.data.scanner.*
import com.example.pokedex.domain.scanner.*
import com.example.pokedex.presentation.scanner.ScannerScreenContent
import com.example.pokedex.ui.theme.PokedexTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScannerScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private val catalog = PokemonCatalog.fromRecords(List(5) { record(it) })
    private val result = RankedPokemonCandidate(0, 0.964f, isShiny = true)

    private fun setFakeContent(
        initialState: ScannerUiState = ScannerUiState(ScannerMode.Preview),
    ) {
        composeRule.setContent {
            var state by remember { mutableStateOf(initialState) }
            PokedexTheme {
                ScannerScreenContent(
                    state = state,
                    catalog = catalog,
                    surfaceRequest = null,
                    useFakeCamera = true,
                    onPrimaryAction = {
                        state = if (state.mode == ScannerMode.Preview) state.copy(mode = ScannerMode.Result(result))
                        else state.copy(mode = ScannerMode.Preview)
                    },
                    onOpenCatalog = { state = state.copy(page = ScannerPage.Catalog) },
                    onOpenPokemonDetail = { state = state.copy(page = ScannerPage.CatalogDetail, catalogPokemonIndex = it) },
                    onBackToCamera = { state = state.copy(page = ScannerPage.Scanner, mode = ScannerMode.Preview) },
                    onSaveSettings = { state = state.copy(scannerSettings = it) },
                    onTestSettings = {}, onPreviewNarration = {}, onRequestPermission = {},
                    onRetry = { state = state.copy(mode = ScannerMode.Preview) },
                )
            }
        }
    }

    private fun openWordleRegionSetup() {
        composeRule.onNodeWithTag("game_menu_wordle").performClick()
        composeRule.waitUntil(2_000) {
            composeRule.onAllNodesWithTag("wordle_region_back").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun captureShowsSingleResultWithoutCandidateOrFeedbackModules() {
        setFakeContent()
        composeRule.onNodeWithTag("capture_button").performClick()
        composeRule.onNodeWithTag("scanner_result").assertIsDisplayed()
        composeRule.onNodeWithTag("result_pokemon_thumbnail").assertIsDisplayed()
        composeRule.onNodeWithTag("candidate_list").assertDoesNotExist()
        composeRule.onNodeWithTag("feedback_confirm").assertDoesNotExist()
    }

    @Test fun settingsHideCandidateModeAndSeparateDeveloperParameters() {
        setFakeContent()
        composeRule.onNodeWithTag("recognition_settings_button").performClick()
        composeRule.onNodeWithTag("settings_tab_General").performClick()
        composeRule.onNodeWithTag("cloud_provider_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("cloud_provider_selector").performClick()
        composeRule.onNodeWithTag("cloud_provider_Anthropic").performClick()
        composeRule.onNodeWithText("Anthropic Claude").assertIsDisplayed()
        composeRule.onNodeWithTag("cloud_api_key").assertIsDisplayed()
        composeRule.onNodeWithTag("recognition_mode_multiple").assertDoesNotExist()
        composeRule.onNodeWithTag("cloud_api_url").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_advanced_toggle").performClick()
        composeRule.onNodeWithTag("cloud_api_url").assertIsDisplayed()
        composeRule.onNodeWithTag("developer_reset").performScrollTo().assertIsDisplayed()
    }

    @Test fun catalogDetailRestoresAttributePageAndShinyToggle() {
        setFakeContent(ScannerUiState(ScannerMode.Preview, page = ScannerPage.Catalog))
        composeRule.onNodeWithTag("catalog_item_p0000_v00").performClick()
        composeRule.onNodeWithTag("catalog_detail_page").assertIsDisplayed()
        composeRule.onNodeWithText("属性").assertIsDisplayed()
        composeRule.onNodeWithText("1/8").assertIsDisplayed()
        composeRule.onNodeWithTag("pokemon_types").assertIsDisplayed()
        composeRule.onNodeWithTag("pokemon_abilities").assertIsDisplayed()
        composeRule.onNodeWithTag("pokemon_stats_radar").assertIsDisplayed()
        composeRule.onNodeWithTag("shiny_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("normal_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("appearance_selector").assertDoesNotExist()
    }

    @Test fun dpadCyclesEightPagesAndNavigatesCatalogVertically() {
        setFakeContent(ScannerUiState(ScannerMode.Preview, page = ScannerPage.CatalogDetail, catalogPokemonIndex = 0))
        composeRule.onNodeWithTag("dpad_right").performClick()
        composeRule.onNodeWithText("基础").assertIsDisplayed()
        composeRule.onNodeWithText("2/8").assertIsDisplayed()
        repeat(7) { composeRule.onNodeWithTag("dpad_right").performClick() }
        composeRule.onNodeWithText("属性").assertIsDisplayed()
        composeRule.onNodeWithText("1/8").assertIsDisplayed()
        composeRule.onNodeWithTag("dpad_up").assertIsEnabled()
        composeRule.onNodeWithTag("dpad_down").assertIsEnabled()
        composeRule.onNodeWithTag("dpad_down").performClick()
        composeRule.onNodeWithText("1/8").assertIsDisplayed()
    }

    @Test fun basicPageUsesThreeInformationCards() {
        setFakeContent(ScannerUiState(ScannerMode.Preview, page = ScannerPage.CatalogDetail, catalogPokemonIndex = 0))
        composeRule.onNodeWithTag("dpad_right").performClick()
        composeRule.onAllNodesWithTag("basic_info_card").assertCountEquals(3)
    }

    @Test fun movesPageExposesSearchAndFieldsNeverShowRawDataLabel() {
        setFakeContent(ScannerUiState(ScannerMode.Preview, page = ScannerPage.CatalogDetail, catalogPokemonIndex = 0))
        repeat(7) { composeRule.onNodeWithTag("dpad_right").performClick() }
        composeRule.onNodeWithText("招式").assertIsDisplayed()
        composeRule.onNodeWithTag("move_search").assertIsDisplayed()
        composeRule.onNodeWithText("DATA", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("data", substring = true, ignoreCase = false).assertDoesNotExist()
    }

    @Test fun absentShinyMaterialDisablesToggle() {
        setFakeContent(ScannerUiState(ScannerMode.Preview, page = ScannerPage.CatalogDetail, catalogPokemonIndex = 1))
        composeRule.onNodeWithTag("shiny_toggle").assertIsNotEnabled()
        composeRule.onNodeWithText("暂无异色素材").assertIsDisplayed()
    }

    @Test fun laterPagesHideArtworkAndSelectionControls() {
        setFakeContent(ScannerUiState(ScannerMode.Preview, page = ScannerPage.CatalogDetail, catalogPokemonIndex = 0))
        composeRule.onNodeWithTag("dpad_right").performClick()
        composeRule.onNodeWithTag("result_pokemon_thumbnail").assertDoesNotExist()
        composeRule.onNodeWithTag("normal_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("shiny_toggle").assertDoesNotExist()
    }

    @Test fun hatPikachuHasDedicatedDressUpButton() {
        setFakeContent(ScannerUiState(ScannerMode.Preview, page = ScannerPage.CatalogDetail, catalogPokemonIndex = 2))
        composeRule.onNodeWithTag("hat_dress_up_button").assertIsDisplayed()
        composeRule.onNodeWithText("换装").assertIsDisplayed()
    }

    @Test fun permissionState_exposesEnableAction() {
        setFakeContent(ScannerUiState(ScannerMode.PermissionRequired))
        composeRule.onNodeWithTag("permission_button").assertIsDisplayed()
        composeRule.onNodeWithTag("dpad_left").assertIsNotEnabled()
        composeRule.onNodeWithTag("dpad_right").assertIsNotEnabled()
        composeRule.onNodeWithText("DATA", substring = true).assertDoesNotExist()
    }

    @Test fun dpadHasStableDirectionHotspots() {
        setFakeContent(ScannerUiState(ScannerMode.Preview, page = ScannerPage.CatalogDetail, catalogPokemonIndex = 0))
        composeRule.onNodeWithTag("dpad").assertIsDisplayed()
        composeRule.onNodeWithTag("dpad_left").assertIsEnabled()
        composeRule.onNodeWithTag("dpad_right").assertIsEnabled()
        composeRule.onNodeWithTag("dpad_up").assertIsEnabled()
        composeRule.onNodeWithTag("dpad_down").assertIsEnabled()
    }

    @Test fun scorePanelReplacesOpticalStatusText() {
        setFakeContent()
        composeRule.onNodeWithTag("trainer_score").assertIsDisplayed()
        composeRule.onNodeWithText("积分:100").assertIsDisplayed()
        composeRule.onNodeWithText("Optical Link Ready").assertDoesNotExist()
    }

    @Test fun backgroundMusicControlsAreInSoundSettings() {
        setFakeContent()
        composeRule.onNodeWithTag("recognition_settings_button").performClick()
        composeRule.onNodeWithTag("settings_tab_Sound").performClick()
        composeRule.onNodeWithTag("background_music_selector").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("background_music_import").assertIsDisplayed()
    }

    @Test fun wordleRegionSetupUsesThreeColumnCardsAndStickyStartAction() {
        setFakeContent(ScannerUiState(ScannerMode.Preview, page = ScannerPage.Games))
        openWordleRegionSetup()
        composeRule.onNodeWithTag("wordle_region_back").assertIsDisplayed()
        composeRule.onNodeWithTag("wordle_select_all").assertIsDisplayed()
        composeRule.onNodeWithTag("wordle_select_none").assertIsDisplayed()
        composeRule.onNodeWithTag("wordle_include_mega").assertIsDisplayed()
        composeRule.onNodeWithTag("wordle_include_gigantamax").assertIsDisplayed()
        composeRule.onNodeWithTag("wordle_region_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("wordle_region_kanto").assertIsDisplayed()
        composeRule.onNodeWithTag("wordle_region_lumiose").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("wordle_start_button").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun wordleRegionSetupCanClearAndRestoreSelection() {
        setFakeContent(ScannerUiState(ScannerMode.Preview, page = ScannerPage.Games))
        openWordleRegionSetup()
        composeRule.onNodeWithTag("wordle_select_none").performClick()
        composeRule.onNodeWithTag("wordle_start_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("wordle_select_all").performClick()
        composeRule.onNodeWithTag("wordle_start_button").assertIsEnabled()
        composeRule.onNodeWithTag("wordle_region_back").performClick()
        composeRule.onNodeWithTag("pokemon_games_menu").assertIsDisplayed()
    }

    private fun record(index: Int): PokemonRecord {
        val isHatPikachu = index == 2
        val name = if (isHatPikachu) "戴着帽子的皮卡丘" else "Pokemon ${index + 1}"
        val appearances = if (isHatPikachu) listOf(
            PokemonAppearance("世界帽子", "pokemon/images/missing.webp"),
            PokemonAppearance("初始帽子", "pokemon/images/missing.webp"),
        ) else listOf(PokemonAppearance("默认", "pokemon/images/missing.webp", if (index == 0) "pokemon/images/missing-shiny.webp" else null))
        return PokemonRecord(
        key = "p${index.toString().padStart(4, '0')}_v00", id = index.toString().padStart(4, '0'),
        sourceFormName = name, nameZh = name, nameEn = name,
        types = listOf("一般"), attributeLabel = "一般属性宝可梦", category = "宝可梦",
        height = "1.0m", weight = "1.0kg", abilities = PokemonAbilities(listOf("特性一", "特性二"), listOf("隐藏特性")),
        stats = PokemonStats(45, 49, 49, 65, 65, 45), description = "", profile = "Profile",
        imageAsset = "pokemon/images/missing.webp", shinyImageAsset = if (index == 0) "pokemon/images/missing-shiny.webp" else null,
        appearances = appearances,
    )
    }
}
