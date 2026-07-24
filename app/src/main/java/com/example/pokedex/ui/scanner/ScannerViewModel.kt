/* Application orchestration for models, camera inputs, inference, settings, and feedback. / 模型、相机输入、推理、设置与反馈的应用编排层。 */
package com.example.pokedex.ui.scanner

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.example.pokedex.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

sealed interface ScannerImageSource {
    data class CameraFile(val file: File) : ScannerImageSource
    data class GalleryUri(val uri: Uri) : ScannerImageSource
}

sealed interface ScannerEvent {
    data class ShareFeedback(val filePath: String) : ScannerEvent
    data class Notice(val message: String) : ScannerEvent
    data class SpeakPokemon(val pokemonIndex: Int) : ScannerEvent
    data class PlaySound(val effect: AppSoundEffect) : ScannerEvent
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    val catalog: PokemonCatalog = PokemonCatalog.load(application)

    private val modelStore = ModelStore(application)
    private val feedbackStore = FeedbackStore(application)
    private val soundEffectStore = SoundEffectStore(application)
    private val settings = application.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
    private val recognitionRepository: RecognitionRepository = LegacyVlmRecognitionRepository(
        catalog = catalog,
        runtime = NativeVisionLanguageRuntime(),
    )
    private val _uiState = MutableStateFlow(
        ScannerUiState(
            mode = if (modelStore.status.value.allReady) {
                ScannerMode.LoadingModels
            } else {
                ScannerMode.ModelSetup
            },
            modelStatus = modelStore.status.value,
            scannerSettings = savedScannerSettings(),
            feedback = FeedbackUiState(sampleCount = feedbackStore.sampleCount.value),
            soundAssets = soundEffectStore.statuses(),
        ),
    )
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<ScannerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ScannerEvent> = _events.asSharedFlow()

    private var cameraPermissionGranted = false
    private var modelsLoaded = false
    private var loadJob: Job? = null
    private var inferenceJob: Job? = null
    private var settingsTestJob: Job? = null

    init {
        viewModelScope.launch {
            feedbackStore.sampleCount.collectLatest { count ->
                _uiState.value = _uiState.value.copy(
                    feedback = _uiState.value.feedback.copy(sampleCount = count),
                )
            }
        }
        viewModelScope.launch {
            modelStore.status.collectLatest { status ->
                _uiState.value = _uiState.value.copy(modelStatus = status)
                if (status.importingRole != null) {
                    inferenceJob?.cancel()
                    recognitionRepository.cancel()
                    modelsLoaded = false
                } else if (status.allReady) {
                    ensureModelsLoaded()
                } else if (!status.allReady) {
                    modelsLoaded = false
                    _uiState.value = _uiState.value.copy(mode = ScannerMode.ModelSetup)
                }
            }
        }
    }

    fun onCameraPermissionChanged(granted: Boolean) {
        cameraPermissionGranted = granted
        if (modelsLoaded && _uiState.value.mode !is ScannerMode.Result && _uiState.value.mode != ScannerMode.Capturing) {
            _uiState.value = _uiState.value.copy(
                mode = if (granted) ScannerMode.Preview else ScannerMode.PermissionRequired,
            )
        }
    }

    fun importModel(role: ModelRole, uri: Uri) {
        viewModelScope.launch { modelStore.importModel(role, uri) }
    }

    /**
     * Imports and atomically replaces one custom effect in app-private storage.
     * 导入一个自定义音效，并在应用私有目录中进行原子替换。
     */
    fun importSoundEffect(effect: AppSoundEffect, uri: Uri) {
        viewModelScope.launch {
            runCatching { soundEffectStore.import(effect, uri) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(soundAssets = soundEffectStore.statuses())
                    _events.tryEmit(ScannerEvent.Notice(localizedText(_uiState.value.appLanguage, "${effect.chineseName}音效已替换", "${effect.englishName} sound replaced")))
                }
                .onFailure { error ->
                    _events.tryEmit(ScannerEvent.Notice(error.message ?: "无法导入音效"))
                }
        }
    }

    /** Restores a sound slot to the packaged default. / 将音效槽恢复为 APK 内置默认资源。 */
    fun resetSoundEffect(effect: AppSoundEffect) {
        viewModelScope.launch {
            soundEffectStore.reset(effect)
            _uiState.value = _uiState.value.copy(soundAssets = soundEffectStore.statuses())
            _events.tryEmit(ScannerEvent.Notice(localizedText(_uiState.value.appLanguage, "${effect.chineseName}已恢复默认", "${effect.englishName} restored to default")))
        }
    }

    fun openModelSetup() {
        inferenceJob?.cancel()
        recognitionRepository.cancel()
        _uiState.value = _uiState.value.copy(
            mode = ScannerMode.ModelSetup,
            capturedImageJpeg = null,
            candidates = emptyList(),
            feedback = _uiState.value.feedback.copy(
                saving = false,
                savedSampleId = null,
                message = null,
            ),
        )
    }

    fun openCatalog() {
        _uiState.value = reduceScannerState(_uiState.value, ScannerAction.OpenCatalog)
    }

    fun openPokemonDetail(pokemonIndex: Int) {
        if (pokemonIndex !in catalog.records.indices) return
        _uiState.value = reduceScannerState(
            _uiState.value,
            ScannerAction.OpenCatalogDetail(pokemonIndex),
        )
        _events.tryEmit(ScannerEvent.SpeakPokemon(pokemonIndex))
    }

    /** Returns one level from detail, otherwise returns to camera. / 从详情返回一级，否则返回相机。 */
    fun navigateBack() {
        if (_uiState.value.page == ScannerPage.CatalogDetail) {
            _uiState.value = reduceScannerState(_uiState.value, ScannerAction.BackToCatalog)
        } else {
            backToCamera()
        }
    }

    fun setRecognitionMode(mode: RecognitionMode) {
        saveScannerSettings(_uiState.value.scannerSettings.copy(recognitionMode = mode))
    }

    fun saveScannerSettings(scannerSettings: ScannerSettings) {
        val sanitized = scannerSettings.sanitized()
        persistScannerSettings(sanitized)
        _uiState.value = _uiState.value.copy(scannerSettings = sanitized)
    }

    fun saveSettingsAndReload(scannerSettings: ScannerSettings) {
        saveScannerSettings(scannerSettings)
        reloadModelsWithRollback()
    }

    fun backToCamera() {
        inferenceJob?.cancel()
        settingsTestJob?.cancel()
        recognitionRepository.cancel()
        val reset = reduceScannerState(_uiState.value, ScannerAction.BackToCamera)
        _uiState.value = reset.copy(
            mode = when {
                !modelsLoaded -> ScannerMode.ModelSetup
                cameraPermissionGranted -> ScannerMode.Preview
                else -> ScannerMode.PermissionRequired
            },
        )
    }

    fun recognize(source: ScannerImageSource) {
        if ((_uiState.value.mode != ScannerMode.Preview && _uiState.value.mode != ScannerMode.PermissionRequired) || !modelsLoaded) return
        val scannerSettings = _uiState.value.scannerSettings.sanitized()
        val tuning = scannerSettings.recognitionTuning
        val recognitionOptions = RecognitionOptions.forMode(scannerSettings.recognitionMode, tuning)
        inferenceJob?.cancel()
        settingsTestJob?.cancel()
        _uiState.value = reduceScannerState(
            _uiState.value,
            ScannerAction.Capture,
        )
        _events.tryEmit(ScannerEvent.PlaySound(AppSoundEffect.ScanStarted))
        inferenceJob = viewModelScope.launch {
            try {
                val normalized = ImagePreprocessor.preprocess(getApplication(), source, tuning)
                _uiState.value = reduceScannerState(
                    _uiState.value,
                    ScannerAction.CapturePrepared(normalized.feedbackJpeg),
                )
                val inferenceStartedAt = SystemClock.elapsedRealtime()
                val result = withTimeout(tuning.timeoutSeconds * 1_000L) {
                    recognitionRepository.recognize(normalized, recognitionOptions)
                }
                val elapsedMillis = SystemClock.elapsedRealtime() - inferenceStartedAt
                _uiState.value = _uiState.value.copy(lastInferenceMillis = elapsedMillis)
                if (BuildConfig.DEBUG) {
                    Log.d(
                        PERFORMANCE_TAG,
                        "mode=${recognitionOptions.mode.name} elapsedMs=$elapsedMillis",
                    )
                }
                when (result) {
                    is RecognitionResult.Success -> {
                        val candidates = rankRecognitionCandidates(
                            catalog = catalog,
                            candidates = result.candidates,
                            limit = recognitionOptions.mode.candidateCount,
                        )
                        check(candidates.isNotEmpty()) { "识别结果不在本地图鉴中" }
                        _uiState.value = reduceScannerState(
                            _uiState.value,
                            ScannerAction.CaptureCompleted(candidates),
                        )
                        _events.tryEmit(ScannerEvent.PlaySound(AppSoundEffect.RecognitionSuccess))
                        _events.tryEmit(ScannerEvent.SpeakPokemon(candidates.first().pokemonIndex))
                    }
                    is RecognitionResult.Failure -> {
                        _events.tryEmit(ScannerEvent.PlaySound(AppSoundEffect.RecognitionFailure))
                        fail(result.message)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                recognitionRepository.cancel()
                _events.tryEmit(ScannerEvent.PlaySound(AppSoundEffect.RecognitionFailure))
                fail("本地 AI 识别超时，请重试")
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Throwable) {
                _events.tryEmit(ScannerEvent.PlaySound(AppSoundEffect.RecognitionFailure))
                fail(error.message ?: "\u672c\u5730 AI \u8bc6\u522b\u5931\u8d25")
            } finally {
                if (source is ScannerImageSource.CameraFile) source.file.delete()
            }
        }
    }

    fun testScannerSettings(scannerSettings: ScannerSettings) {
        if (!modelsLoaded || settingsTestJob?.isActive == true) return
        val imageJpeg = _uiState.value.capturedImageJpeg ?: run {
            _uiState.value = _uiState.value.copy(
                settingsTest = SettingsTestUiState(message = "请先完成一次拍照识别"),
            )
            return
        }
        val sanitized = scannerSettings.sanitized()
        saveScannerSettings(sanitized)
        if (_uiState.value.hasPendingRuntimeSettings) {
            _uiState.value = _uiState.value.copy(
                settingsTest = SettingsTestUiState(message = "性能参数尚未生效，请先重载模型"),
            )
            return
        }

        val tuning = sanitized.recognitionTuning
        val recognitionOptions = RecognitionOptions.forMode(sanitized.recognitionMode, tuning)
        inferenceJob?.cancel()
        _uiState.value = _uiState.value.copy(
            settingsTest = SettingsTestUiState(running = true, message = "正在测试当前配置"),
        )
        settingsTestJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                val normalized = ImagePreprocessor.preprocessJpeg(imageJpeg, tuning)
                val result = withTimeout(tuning.timeoutSeconds * 1_000L) {
                    recognitionRepository.recognize(normalized, recognitionOptions)
                }
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val message = when (result) {
                    is RecognitionResult.Success -> "测试完成，返回 ${result.candidates.size} 个有效候选"
                    is RecognitionResult.Failure -> result.message
                }
                _uiState.value = _uiState.value.copy(
                    lastInferenceMillis = elapsed,
                    settingsTest = SettingsTestUiState(
                        elapsedMillis = elapsed,
                        message = message,
                    ),
                )
            } catch (_: TimeoutCancellationException) {
                recognitionRepository.cancel()
                _uiState.value = _uiState.value.copy(
                    settingsTest = SettingsTestUiState(message = "测试超时，请调整配置后重试"),
                )
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(
                    settingsTest = SettingsTestUiState(message = error.message ?: "当前配置测试失败"),
                )
            }
        }
    }

    fun retry() {
        settingsTestJob?.cancel()
        if (modelsLoaded) {
            _uiState.value = _uiState.value.copy(
                mode = if (cameraPermissionGranted) ScannerMode.Preview else ScannerMode.PermissionRequired,
                capturedImageJpeg = null,
                candidates = emptyList(),
                feedback = _uiState.value.feedback.copy(
                    saving = false,
                    savedSampleId = null,
                    message = null,
                ),
            )
        } else {
            ensureModelsLoaded()
        }
    }

    fun reset() {
        inferenceJob?.cancel()
        settingsTestJob?.cancel()
        recognitionRepository.cancel()
        _uiState.value = _uiState.value.copy(
            mode = when {
                !modelsLoaded -> ScannerMode.ModelSetup
                cameraPermissionGranted -> ScannerMode.Preview
                else -> ScannerMode.PermissionRequired
            },
            capturedImageJpeg = null,
            candidates = emptyList(),
            feedback = _uiState.value.feedback.copy(
                saving = false,
                savedSampleId = null,
                message = null,
            ),
        )
    }

    fun moveResult(delta: Int) {
        _uiState.value = reduceScannerState(
            state = _uiState.value,
            action = ScannerAction.MoveResult(delta),
        )
    }

    fun confirmSelectedCandidate() {
        val state = _uiState.value
        val resultMode = state.mode as? ScannerMode.Result ?: return
        val candidate = state.candidates.getOrNull(resultMode.candidateIndex) ?: return
        saveFeedback(correctPokemonIndex = candidate.pokemonIndex, confirmedCorrect = true)
    }

    fun correctRecognition(pokemonIndex: Int) {
        val pokemon = catalog.records.getOrNull(pokemonIndex) ?: return
        saveFeedback(
            correctLabel = FeedbackLabel(pokemon.key, pokemon.nameZh),
            confirmedCorrect = false,
        )
    }

    fun correctAsNotPokemon() {
        saveFeedback(
            correctLabel = FeedbackLabel(NOT_POKEMON_KEY, NOT_POKEMON_NAME),
            confirmedCorrect = false,
        )
    }

    fun undoFeedback() {
        val sampleId = _uiState.value.feedback.savedSampleId ?: return
        if (_uiState.value.feedback.saving) return
        _uiState.value = _uiState.value.copy(
            feedback = _uiState.value.feedback.copy(saving = true, message = null),
        )
        viewModelScope.launch {
            runCatching { feedbackStore.remove(sampleId) }
                .onSuccess { removed ->
                    _uiState.value = _uiState.value.copy(
                        feedback = _uiState.value.feedback.copy(
                            saving = false,
                            savedSampleId = null,
                            message = if (removed) "已撤销本次标注" else "标注已经不存在",
                        ),
                    )
                }
                .onFailure { error ->
                    updateFeedbackFailure(error.message ?: "无法撤销标注")
                }
        }
    }

    fun exportFeedback() {
        if (_uiState.value.feedback.saving) return
        viewModelScope.launch {
            runCatching { feedbackStore.export() }
                .onSuccess { file -> _events.emit(ScannerEvent.ShareFeedback(file.absolutePath)) }
                .onFailure { error ->
                    _events.emit(ScannerEvent.Notice(error.message ?: "无法导出标注数据"))
                }
        }
    }

    fun clearModelError() = modelStore.clearError()

    fun reportError(message: String) = fail(message)

    private fun saveFeedback(correctPokemonIndex: Int, confirmedCorrect: Boolean) {
        val pokemon = catalog.records.getOrNull(correctPokemonIndex) ?: return
        saveFeedback(
            correctLabel = FeedbackLabel(pokemon.key, pokemon.nameZh),
            confirmedCorrect = confirmedCorrect,
        )
    }

    private fun saveFeedback(correctLabel: FeedbackLabel, confirmedCorrect: Boolean) {
        val state = _uiState.value
        if (state.mode !is ScannerMode.Result || state.feedback.saving || state.feedback.savedSampleId != null) return
        val imageJpeg = state.capturedImageJpeg
        if (imageJpeg == null) {
            _events.tryEmit(ScannerEvent.Notice("本次识别没有可保存的图片"))
            return
        }
        val predictions = state.candidates.map { candidate ->
            val pokemon = catalog.recordAt(candidate.pokemonIndex)
            FeedbackPrediction(
                pokemonKey = pokemon.key,
                standardName = pokemon.nameZh,
                probability = candidate.probability,
            )
        }
        _uiState.value = state.copy(
            feedback = state.feedback.copy(saving = true, message = null),
        )
        viewModelScope.launch {
            runCatching {
                feedbackStore.save(
                    FeedbackSaveRequest(
                        imageJpeg = imageJpeg,
                        correctLabel = correctLabel,
                        predictions = predictions,
                        confirmedCorrect = confirmedCorrect,
                        modelVersion = CURRENT_MODEL_VERSION,
                    ),
                )
            }.onSuccess { result ->
                if (_uiState.value.capturedImageJpeg === imageJpeg) {
                    _uiState.value = _uiState.value.copy(
                        feedback = _uiState.value.feedback.copy(
                            saving = false,
                            savedSampleId = result.sampleId,
                            message = if (confirmedCorrect) "已确认识别结果" else "已保存正确名称：${correctLabel.standardName}",
                        ),
                    )
                }
            }.onFailure { error ->
                updateFeedbackFailure(error.message ?: "无法保存标注")
            }
        }
    }

    private fun updateFeedbackFailure(message: String) {
        _uiState.value = _uiState.value.copy(
            feedback = _uiState.value.feedback.copy(saving = false, message = message),
        )
    }

    private fun ensureModelsLoaded() {
        if (modelsLoaded || loadJob?.isActive == true) return
        val paths = modelStore.pathsOrNull() ?: return
        val desiredOptions = _uiState.value.scannerSettings.recognitionTuning.modelRuntimeOptions()
        _uiState.value = _uiState.value.copy(mode = ScannerMode.LoadingModels)
        loadJob = viewModelScope.launch {
            runCatching {
                recognitionRepository.loadModels(
                    RecognitionModelFiles.LegacyVisionLanguage(
                        languageModelPath = paths.languageModelPath,
                        visionModelPath = paths.visionModelPath,
                    ),
                    desiredOptions,
                )
            }.onSuccess {
                modelsLoaded = true
                _uiState.value = _uiState.value.copy(
                    mode = if (cameraPermissionGranted) ScannerMode.Preview else ScannerMode.PermissionRequired,
                    activeRuntimeOptions = desiredOptions,
                )
            }.onFailure { error ->
                modelsLoaded = false
                fail(error.message ?: "\u672c\u5730 AI \u6a21\u578b\u52a0\u8f7d\u5931\u8d25")
            }
        }
    }

    private fun reloadModelsWithRollback() {
        if (loadJob?.isActive == true) return
        val paths = modelStore.pathsOrNull() ?: return
        val desiredSettings = _uiState.value.scannerSettings.sanitized()
        val desiredOptions = desiredSettings.recognitionTuning.modelRuntimeOptions()
        val previousOptions = _uiState.value.activeRuntimeOptions
        if (modelsLoaded && previousOptions == desiredOptions) return

        inferenceJob?.cancel()
        settingsTestJob?.cancel()
        recognitionRepository.cancel()
        modelsLoaded = false
        _uiState.value = _uiState.value.copy(
            mode = ScannerMode.LoadingModels,
            capturedImageJpeg = null,
            candidates = emptyList(),
            settingsTest = SettingsTestUiState(),
        )
        val modelFiles = RecognitionModelFiles.LegacyVisionLanguage(
            languageModelPath = paths.languageModelPath,
            visionModelPath = paths.visionModelPath,
        )
        loadJob = viewModelScope.launch {
            val desiredResult = runCatching {
                recognitionRepository.loadModels(modelFiles, desiredOptions)
            }
            if (desiredResult.isSuccess) {
                modelsLoaded = true
                _uiState.value = _uiState.value.copy(
                    mode = readyScannerMode(),
                    activeRuntimeOptions = desiredOptions,
                )
                return@launch
            }

            val rollbackResult = previousOptions?.let { options ->
                runCatching { recognitionRepository.loadModels(modelFiles, options) }
            }
            if (previousOptions != null && rollbackResult?.isSuccess == true) {
                val restoredSettings = desiredSettings.copy(
                    recognitionTuning = desiredSettings.recognitionTuning.withRuntimeOptions(previousOptions),
                )
                persistScannerSettings(restoredSettings)
                modelsLoaded = true
                _uiState.value = _uiState.value.copy(
                    mode = readyScannerMode(),
                    scannerSettings = restoredSettings,
                    activeRuntimeOptions = previousOptions,
                    settingsTest = SettingsTestUiState(
                        message = "新性能配置加载失败，已恢复上一组可用参数",
                    ),
                )
                _events.tryEmit(ScannerEvent.Notice("新性能配置加载失败，已恢复上一组可用参数"))
            } else {
                modelsLoaded = false
                val failure = rollbackResult?.exceptionOrNull() ?: desiredResult.exceptionOrNull()
                fail(failure?.message ?: "本地 AI 模型重载失败")
            }
        }
    }

    private fun readyScannerMode(): ScannerMode =
        if (cameraPermissionGranted) ScannerMode.Preview else ScannerMode.PermissionRequired

    private fun fail(message: String) {
        _uiState.value = reduceScannerState(
            _uiState.value,
            ScannerAction.Failed(message),
        )
    }

    private fun savedScannerSettings(): ScannerSettings {
        val storedSchema = settings.getInt(SETTINGS_SCHEMA_KEY, 0)
        val recognitionMode = runCatching {
            RecognitionMode.valueOf(
                settings.getString(RECOGNITION_MODE_KEY, RecognitionMode.Single.name)
                    ?: RecognitionMode.Single.name,
            )
        }.getOrDefault(RecognitionMode.Single)
        val preset = runCatching {
            VoicePreset.valueOf(
                settings.getString(VOICE_PRESET_KEY, VoicePreset.FastMale.name)
                    ?: VoicePreset.FastMale.name,
            )
        }.getOrDefault(VoicePreset.FastMale)
        val volume = settings.getFloat(SPEECH_VOLUME_KEY, 1f)
        val voiceName = settings.getString(TTS_VOICE_NAME_KEY, null)
        val narrationSettings = if (storedSchema < 3 && preset != VoicePreset.Custom) {
            NarrationSettings.forPreset(preset, volume, voiceName)
        } else {
            NarrationSettings(
                preset = preset,
                speechRate = settings.getFloat(SPEECH_RATE_KEY, preset.speechRate),
                speechPitch = settings.getFloat(SPEECH_PITCH_KEY, preset.speechPitch),
                volume = volume,
                voiceName = voiceName,
            ).sanitized()
        }
        val defaults = RecognitionTuning()
        val recognitionTuning = RecognitionTuning(
            singleMaxTokens = settings.getInt(SINGLE_MAX_TOKENS_KEY, defaults.singleMaxTokens),
            multipleMaxTokens = settings.getInt(MULTIPLE_MAX_TOKENS_KEY, defaults.multipleMaxTokens),
            timeoutSeconds = settings.getInt(INFERENCE_TIMEOUT_SECONDS_KEY, defaults.timeoutSeconds),
            imageMaxEdge = settings.getInt(IMAGE_MAX_EDGE_KEY, defaults.imageMaxEdge),
            jpegQuality = settings.getInt(JPEG_QUALITY_KEY, defaults.jpegQuality),
            contextSize = settings.getInt(CONTEXT_SIZE_KEY, defaults.contextSize),
            batchSize = settings.getInt(BATCH_SIZE_KEY, defaults.batchSize),
            threads = settings.getInt(THREADS_KEY, defaults.threads),
            penaltyLastN = settings.getInt(PENALTY_LAST_N_KEY, defaults.penaltyLastN),
            repetitionPenalty = settings.getFloat(REPETITION_PENALTY_KEY, defaults.repetitionPenalty),
            frequencyPenalty = settings.getFloat(FREQUENCY_PENALTY_KEY, defaults.frequencyPenalty),
            presencePenalty = settings.getFloat(PRESENCE_PENALTY_KEY, defaults.presencePenalty),
        ).sanitized()
        return ScannerSettings(
            recognitionMode = recognitionMode,
            recognitionTuning = recognitionTuning,
            narrationSettings = narrationSettings,
            soundEffectSettings = SoundEffectSettings(
                enabled = settings.getBoolean(SOUND_ENABLED_KEY, true),
                volume = settings.getFloat(SOUND_VOLUME_KEY, SoundEffectSettings.DEFAULT_VOLUME),
            ).sanitized(),
        )
    }

    private fun persistScannerSettings(scannerSettings: ScannerSettings) {
        val safe = scannerSettings.sanitized()
        val tuning = safe.recognitionTuning
        val narration = safe.narrationSettings
        val soundEffects = safe.soundEffectSettings
        val editor = settings.edit()
            .putInt(SETTINGS_SCHEMA_KEY, SETTINGS_SCHEMA_VERSION)
            .putString(RECOGNITION_MODE_KEY, safe.recognitionMode.name)
            .putInt(SINGLE_MAX_TOKENS_KEY, tuning.singleMaxTokens)
            .putInt(MULTIPLE_MAX_TOKENS_KEY, tuning.multipleMaxTokens)
            .putInt(INFERENCE_TIMEOUT_SECONDS_KEY, tuning.timeoutSeconds)
            .putInt(IMAGE_MAX_EDGE_KEY, tuning.imageMaxEdge)
            .putInt(JPEG_QUALITY_KEY, tuning.jpegQuality)
            .putInt(CONTEXT_SIZE_KEY, tuning.contextSize)
            .putInt(BATCH_SIZE_KEY, tuning.batchSize)
            .putInt(THREADS_KEY, tuning.threads)
            .putInt(PENALTY_LAST_N_KEY, tuning.penaltyLastN)
            .putFloat(REPETITION_PENALTY_KEY, tuning.repetitionPenalty)
            .putFloat(FREQUENCY_PENALTY_KEY, tuning.frequencyPenalty)
            .putFloat(PRESENCE_PENALTY_KEY, tuning.presencePenalty)
            .putString(VOICE_PRESET_KEY, narration.preset.name)
            .putFloat(SPEECH_RATE_KEY, narration.speechRate)
            .putFloat(SPEECH_PITCH_KEY, narration.speechPitch)
            .putFloat(SPEECH_VOLUME_KEY, narration.volume)
            .putBoolean(SOUND_ENABLED_KEY, soundEffects.enabled)
            .putFloat(SOUND_VOLUME_KEY, soundEffects.volume)
        if (narration.voiceName == null) {
            editor.remove(TTS_VOICE_NAME_KEY)
        } else {
            editor.putString(TTS_VOICE_NAME_KEY, narration.voiceName)
        }
        editor.apply()
    }

    override fun onCleared() {
        settingsTestJob?.cancel()
        recognitionRepository.cancel()
        recognitionRepository.close()
        super.onCleared()
    }

    companion object {
        private const val CURRENT_MODEL_VERSION = "minicpm-v4.6-q4-configurable-v4"
        private const val SETTINGS_FILE = "scanner_settings"
        private const val SETTINGS_SCHEMA_KEY = "settings_schema"
        private const val SETTINGS_SCHEMA_VERSION = 4
        private const val RECOGNITION_MODE_KEY = "recognition_mode"
        private const val SINGLE_MAX_TOKENS_KEY = "single_max_tokens"
        private const val MULTIPLE_MAX_TOKENS_KEY = "multiple_max_tokens"
        private const val INFERENCE_TIMEOUT_SECONDS_KEY = "inference_timeout_seconds"
        private const val IMAGE_MAX_EDGE_KEY = "image_max_edge"
        private const val JPEG_QUALITY_KEY = "jpeg_quality"
        private const val CONTEXT_SIZE_KEY = "context_size"
        private const val BATCH_SIZE_KEY = "batch_size"
        private const val THREADS_KEY = "threads"
        private const val PENALTY_LAST_N_KEY = "penalty_last_n"
        private const val REPETITION_PENALTY_KEY = "repetition_penalty"
        private const val FREQUENCY_PENALTY_KEY = "frequency_penalty"
        private const val PRESENCE_PENALTY_KEY = "presence_penalty"
        private const val VOICE_PRESET_KEY = "voice_preset"
        private const val SPEECH_RATE_KEY = "speech_rate"
        private const val SPEECH_PITCH_KEY = "speech_pitch"
        private const val SPEECH_VOLUME_KEY = "speech_volume"
        private const val TTS_VOICE_NAME_KEY = "tts_voice_name"
        private const val SOUND_ENABLED_KEY = "sound_enabled"
        private const val SOUND_VOLUME_KEY = "sound_volume"
        private const val PERFORMANCE_TAG = "PokedexPerformance"
        private const val NOT_POKEMON_KEY = "__NOT_POKEMON__"
        private const val NOT_POKEMON_NAME = "非宝可梦"
    }
}

internal fun rankRecognitionCandidates(
    catalog: PokemonCatalog,
    candidates: List<RecognitionCandidate>,
    limit: Int = MAX_RESULT_CANDIDATES,
): List<RankedPokemonCandidate> = candidates
    .mapNotNull { candidate ->
        catalog.indexOf(candidate.standardName)?.let { pokemonIndex ->
            RankedPokemonCandidate(
                pokemonIndex = pokemonIndex,
                probability = candidate.probability?.takeIf(Float::isFinite)?.coerceIn(0f, 1f),
            )
        }
    }
    .sortedWith(
        compareByDescending<RankedPokemonCandidate> { it.probability != null }
            .thenByDescending { it.probability ?: 0f },
    )
    .distinctBy(RankedPokemonCandidate::pokemonIndex)
    .take(limit.coerceIn(1, MAX_RESULT_CANDIDATES))

private object ImagePreprocessor {
    suspend fun preprocess(
        application: Application,
        source: ScannerImageSource,
        tuning: RecognitionTuning,
    ): NormalizedImage =
        withContext(Dispatchers.IO) {
            val decoderSource = when (source) {
                is ScannerImageSource.CameraFile -> ImageDecoder.createSource(source.file)
                is ScannerImageSource.GalleryUri -> ImageDecoder.createSource(
                    application.contentResolver,
                    source.uri,
                )
            }
            decode(decoderSource, tuning)
        }

    suspend fun preprocessJpeg(
        jpeg: ByteArray,
        tuning: RecognitionTuning,
    ): NormalizedImage = withContext(Dispatchers.IO) {
        decode(ImageDecoder.createSource(ByteBuffer.wrap(jpeg)), tuning)
    }

    private fun decode(
        source: ImageDecoder.Source,
        tuning: RecognitionTuning,
    ): NormalizedImage {
        val safe = tuning.sanitized()
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val width = info.size.width
            val height = info.size.height
            val longest = maxOf(width, height)
            if (longest > MAX_FEEDBACK_IMAGE_EDGE) {
                val scale = MAX_FEEDBACK_IMAGE_EDGE.toFloat() / longest
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
        val feedbackJpeg = bitmap.toJpeg(safe.jpegQuality)
        val inferenceBitmap = if (maxOf(bitmap.width, bitmap.height) > safe.imageMaxEdge) {
            val scale = safe.imageMaxEdge.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val inferenceJpeg = if (inferenceBitmap === bitmap) {
            feedbackJpeg
        } else {
            inferenceBitmap.toJpeg(safe.jpegQuality)
        }
        if (inferenceBitmap !== bitmap) inferenceBitmap.recycle()
        bitmap.recycle()
        return NormalizedImage(jpeg = inferenceJpeg, feedbackJpeg = feedbackJpeg)
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray = ByteArrayOutputStream().use { output ->
        check(compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            "无法处理所选图片"
        }
        output.toByteArray()
    }

    private const val MAX_FEEDBACK_IMAGE_EDGE = 1024
}
