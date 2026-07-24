/* Immutable scanner state, actions, and pure reducer. / 不可变扫描状态、操作与纯 reducer。 */
package com.example.pokedex.ui.scanner

sealed interface ScannerMode {
    data object ModelSetup : ScannerMode
    data object LoadingModels : ScannerMode
    data object PermissionRequired : ScannerMode
    data object Preview : ScannerMode
    data object Capturing : ScannerMode
    data class Result(val candidateIndex: Int) : ScannerMode
    data class Error(val message: String) : ScannerMode
}

enum class ScannerPage {
    Scanner,
    Catalog,
    CatalogDetail,
}

enum class RecognitionMode(val candidateCount: Int) {
    Single(1),
    Multiple(MAX_RESULT_CANDIDATES),
}

data class RankedPokemonCandidate(
    val pokemonIndex: Int,
    val probability: Float?,
)

data class ScannerUiState(
    val mode: ScannerMode,
    val page: ScannerPage = ScannerPage.Scanner,
    val modelStatus: ModelSetStatus = ModelSetStatus(false, false),
    val capturedImageJpeg: ByteArray? = null,
    val candidates: List<RankedPokemonCandidate> = emptyList(),
    val scannerSettings: ScannerSettings = ScannerSettings(),
    val activeRuntimeOptions: ModelRuntimeOptions? = null,
    val lastInferenceMillis: Long? = null,
    val settingsTest: SettingsTestUiState = SettingsTestUiState(),
    val feedback: FeedbackUiState = FeedbackUiState(),
    val soundAssets: List<SoundAssetStatus> = emptyList(),
    val catalogPokemonIndex: Int? = null,
) {
    val appLanguage: AppLanguage get() = AppLanguage.Chinese
    val recognitionMode: RecognitionMode get() = scannerSettings.recognitionMode
    val narrationSettings: NarrationSettings get() = scannerSettings.narrationSettings
    val soundEffectSettings: SoundEffectSettings get() = scannerSettings.soundEffectSettings
    val hasPendingRuntimeSettings: Boolean
        get() = activeRuntimeOptions != null &&
            activeRuntimeOptions != scannerSettings.recognitionTuning.modelRuntimeOptions()
}

/**
 * Rejects stale narration events after navigation or reset.
 * 页面跳转或重置后拒绝迟到的播报事件，防止 stop() 后再次启动语音。
 */
fun ScannerUiState.canNarratePokemon(pokemonIndex: Int): Boolean = when {
    page == ScannerPage.CatalogDetail -> catalogPokemonIndex == pokemonIndex
    page == ScannerPage.Scanner && mode is ScannerMode.Result ->
        candidates.firstOrNull()?.pokemonIndex == pokemonIndex
    else -> false
}

data class SettingsTestUiState(
    val running: Boolean = false,
    val elapsedMillis: Long? = null,
    val message: String? = null,
)

data class FeedbackUiState(
    val sampleCount: Int = 0,
    val saving: Boolean = false,
    val savedSampleId: String? = null,
    val message: String? = null,
)

sealed interface ScannerAction {
    data class PermissionChanged(val granted: Boolean) : ScannerAction
    data object ModelsRequired : ScannerAction
    data object ModelsLoading : ScannerAction
    data object ModelsLoaded : ScannerAction
    data object Capture : ScannerAction
    data class CapturePrepared(val imageJpeg: ByteArray) : ScannerAction
    data class CaptureCompleted(val candidates: List<RankedPokemonCandidate>) : ScannerAction
    data object Retry : ScannerAction
    data object Reset : ScannerAction
    data object OpenCatalog : ScannerAction
    data class OpenCatalogDetail(val pokemonIndex: Int) : ScannerAction
    data object BackToCatalog : ScannerAction
    data object BackToCamera : ScannerAction
    data class MoveResult(val delta: Int) : ScannerAction
    data class SetRecognitionMode(val mode: RecognitionMode) : ScannerAction
    data class Failed(val message: String) : ScannerAction
}

fun reduceScannerState(
    state: ScannerUiState,
    action: ScannerAction,
): ScannerUiState = when (action) {
    ScannerAction.ModelsRequired -> state.copy(
        page = ScannerPage.Scanner,
        mode = ScannerMode.ModelSetup,
        capturedImageJpeg = null,
        candidates = emptyList(),
        feedback = state.feedback.forNewCapture(),
    )
    ScannerAction.ModelsLoading -> state.copy(page = ScannerPage.Scanner, mode = ScannerMode.LoadingModels)
    ScannerAction.ModelsLoaded -> state.copy(page = ScannerPage.Scanner, mode = ScannerMode.Preview)
    is ScannerAction.PermissionChanged -> when {
        state.mode == ScannerMode.ModelSetup || state.mode == ScannerMode.LoadingModels -> state
        action.granted -> state.copy(mode = ScannerMode.Preview)
        else -> state.copy(mode = ScannerMode.PermissionRequired)
    }
    ScannerAction.Capture -> when (state.mode) {
        ScannerMode.Preview -> state.copy(
            mode = ScannerMode.Capturing,
            capturedImageJpeg = null,
            candidates = emptyList(),
            feedback = state.feedback.forNewCapture(),
        )
        else -> state
    }
    is ScannerAction.CapturePrepared -> when (state.mode) {
        ScannerMode.Capturing -> state.copy(capturedImageJpeg = action.imageJpeg)
        else -> state
    }
    is ScannerAction.CaptureCompleted -> when (state.mode) {
        ScannerMode.Capturing -> if (action.candidates.isEmpty()) {
            state
        } else {
            state.copy(
                mode = ScannerMode.Result(candidateIndex = 0),
                candidates = action.candidates.take(MAX_RESULT_CANDIDATES),
            )
        }
        else -> state
    }
    ScannerAction.Retry,
    ScannerAction.Reset,
    -> state.copy(
        page = ScannerPage.Scanner,
        mode = ScannerMode.Preview,
        capturedImageJpeg = null,
        candidates = emptyList(),
        feedback = state.feedback.forNewCapture(),
    )
    is ScannerAction.MoveResult -> {
        val current = state.mode as? ScannerMode.Result
        if (current == null || state.candidates.isEmpty()) state
        else state.copy(
            mode = ScannerMode.Result(
                candidateIndex = (current.candidateIndex + action.delta).mod(state.candidates.size),
            ),
        )
    }
    is ScannerAction.SetRecognitionMode -> state.copy(
        scannerSettings = state.scannerSettings.copy(recognitionMode = action.mode),
    )
    ScannerAction.OpenCatalog -> state.copy(page = ScannerPage.Catalog, catalogPokemonIndex = null)
    is ScannerAction.OpenCatalogDetail -> state.copy(
        page = ScannerPage.CatalogDetail,
        catalogPokemonIndex = action.pokemonIndex,
    )
    ScannerAction.BackToCatalog -> state.copy(page = ScannerPage.Catalog, catalogPokemonIndex = null)
    ScannerAction.BackToCamera -> state.copy(
        page = ScannerPage.Scanner,
        mode = ScannerMode.Preview,
        capturedImageJpeg = null,
        candidates = emptyList(),
        catalogPokemonIndex = null,
        feedback = state.feedback.forNewCapture(),
    )
    is ScannerAction.Failed -> state.copy(mode = ScannerMode.Error(action.message))
}

const val MAX_RESULT_CANDIDATES = 5

private fun FeedbackUiState.forNewCapture(): FeedbackUiState = copy(
    saving = false,
    savedSampleId = null,
    message = null,
)
