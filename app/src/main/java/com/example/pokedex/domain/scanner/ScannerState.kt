package com.example.pokedex.domain.scanner

import com.example.pokedex.data.scanner.*

sealed interface ScannerMode {
    data object PermissionRequired : ScannerMode
    data object Preview : ScannerMode
    data object Capturing : ScannerMode
    data class Result(val candidate: RankedPokemonCandidate) : ScannerMode
    data class Error(val message: String) : ScannerMode
}

enum class ScannerPage { Scanner, Games, Catalog, CatalogDetail, PokemonGallery }
data class RankedPokemonCandidate(
    val pokemonIndex: Int,
    val probability: Float?,
    val isShiny: Boolean = false,
)

data class ScannerUiState(
    val mode: ScannerMode,
    val cameraPermissionGranted: Boolean = false,
    val page: ScannerPage = ScannerPage.Scanner,
    val capturedImageJpeg: ByteArray? = null,
    val scannerSettings: ScannerSettings = ScannerSettings(),
    val lastInferenceMillis: Long? = null,
    val settingsTest: SettingsTestUiState = SettingsTestUiState(),
    val feedback: FeedbackUiState = FeedbackUiState(),
    val soundAssets: List<SoundAssetStatus> = emptyList(),
    val catalogPokemonIndex: Int? = null,
    val galleryPokemonIndex: Int? = null,
    val collectedKeys: Set<String> = emptySet(),
    val aiResponseHistory: List<AiResponseHistoryEntry> = emptyList(),
    val authorShareModeStartedAt: Long? = null,
) {
    val appLanguage: AppLanguage get() = AppLanguage.Chinese
    val narrationSettings: NarrationSettings get() = scannerSettings.narrationSettings
    val soundEffectSettings: SoundEffectSettings get() = scannerSettings.soundEffectSettings
    val authorShareModeEnabled: Boolean get() = authorShareModeStartedAt != null
}

fun ScannerUiState.canNarratePokemon(pokemonIndex: Int): Boolean = when {
    page == ScannerPage.CatalogDetail -> catalogPokemonIndex == pokemonIndex
    page == ScannerPage.Scanner && mode is ScannerMode.Result -> mode.candidate.pokemonIndex == pokemonIndex
    else -> false
}

data class SettingsTestUiState(val running: Boolean = false, val elapsedMillis: Long? = null, val message: String? = null)
data class FeedbackUiState(
    val sampleCount: Int = 0,
    val saving: Boolean = false,
    val savedSampleId: String? = null,
    val message: String? = null,
)

sealed interface ScannerAction {
    data class PermissionChanged(val granted: Boolean) : ScannerAction
    data object Capture : ScannerAction
    data class CapturePrepared(val imageJpeg: ByteArray) : ScannerAction
    data class CaptureCompleted(val candidate: RankedPokemonCandidate) : ScannerAction
    data object Retry : ScannerAction
    data object Reset : ScannerAction
    data object OpenCatalog : ScannerAction
    data object OpenGames : ScannerAction
    data class OpenPage(val page: ScannerPage) : ScannerAction
    data class OpenCatalogDetail(val pokemonIndex: Int) : ScannerAction
    data class OpenPokemonGallery(val pokemonIndex: Int) : ScannerAction
    data object BackToCatalog : ScannerAction
    data object BackToCamera : ScannerAction
    data class Failed(val message: String) : ScannerAction
}

fun reduceScannerState(state: ScannerUiState, action: ScannerAction): ScannerUiState = when (action) {
    is ScannerAction.PermissionChanged -> state.copy(
        cameraPermissionGranted = action.granted,
        mode = if (action.granted) ScannerMode.Preview else ScannerMode.PermissionRequired,
    )
    ScannerAction.Capture -> if (state.mode == ScannerMode.Preview) {
        state.copy(mode = ScannerMode.Capturing, capturedImageJpeg = null, feedback = state.feedback.forNewCapture())
    } else state
    is ScannerAction.CapturePrepared -> if (state.mode == ScannerMode.Capturing) state.copy(capturedImageJpeg = action.imageJpeg) else state
    is ScannerAction.CaptureCompleted -> if (state.mode == ScannerMode.Capturing) state.copy(mode = ScannerMode.Result(action.candidate)) else state
    ScannerAction.Retry, ScannerAction.Reset -> state.copy(
        page = ScannerPage.Scanner,
        mode = if (state.cameraPermissionGranted) ScannerMode.Preview else ScannerMode.PermissionRequired,
        capturedImageJpeg = null,
        feedback = state.feedback.forNewCapture(),
    )
    ScannerAction.OpenCatalog -> state.copy(page = ScannerPage.Catalog, catalogPokemonIndex = null)
    ScannerAction.OpenGames -> state.copy(page = ScannerPage.Games, catalogPokemonIndex = null)
    is ScannerAction.OpenPage -> when (action.page) {
        ScannerPage.Scanner -> reduceScannerState(state, ScannerAction.BackToCamera)
        ScannerPage.Games -> reduceScannerState(state, ScannerAction.OpenGames)
        ScannerPage.Catalog -> reduceScannerState(state, ScannerAction.OpenCatalog)
        ScannerPage.CatalogDetail,
        ScannerPage.PokemonGallery,
        -> state.copy(page = action.page)
    }
    is ScannerAction.OpenCatalogDetail -> state.copy(page = ScannerPage.CatalogDetail, catalogPokemonIndex = action.pokemonIndex)
    is ScannerAction.OpenPokemonGallery -> state.copy(page = ScannerPage.PokemonGallery, galleryPokemonIndex = action.pokemonIndex)
    ScannerAction.BackToCatalog -> state.copy(page = ScannerPage.Catalog, catalogPokemonIndex = null)
    ScannerAction.BackToCamera -> state.copy(
        page = ScannerPage.Scanner,
        mode = if (state.cameraPermissionGranted) ScannerMode.Preview else ScannerMode.PermissionRequired,
        capturedImageJpeg = null,
        catalogPokemonIndex = null,
        feedback = state.feedback.forNewCapture(),
    )
    is ScannerAction.Failed -> state.copy(mode = ScannerMode.Error(action.message))
}

private fun FeedbackUiState.forNewCapture() = copy(saving = false, savedSampleId = null, message = null)
