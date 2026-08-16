package com.example.pokedex.presentation.scanner

import com.example.pokedex.data.scanner.*
import com.example.pokedex.domain.scanner.*

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

sealed interface ScannerImageSource {
    data class CameraFile(val file: File) : ScannerImageSource
    data class GalleryUri(val uri: Uri) : ScannerImageSource
}

sealed interface ScannerEvent {
    data class Notice(val message: String) : ScannerEvent
    data class SpeakPokemon(val pokemonIndex: Int) : ScannerEvent
    data class PlaySound(val effect: AppSoundEffect) : ScannerEvent
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    val catalog = PokemonCatalog.load(application)
    val detailsRepository = PokemonDetailsRepository(application)

    private val feedbackStore = FeedbackStore(application)
    private val collectionStore = CollectionStore(application)
    private val soundEffectStore = SoundEffectStore(application)
    private val pokemonPhotoStore = PokemonPhotoStore(application)
    private val aiResponseHistoryStore = AiResponseHistoryStore(application)
    private val preferences = application.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
    private var authorShareModeStartedAt = readValidAuthorShareStart()
    private var currentSettings = readSettings()
    private val recognitionRepository: RecognitionRepository = CloudRecognitionRepository(
        settings = { currentSettings },
        client = VolcArkRecognitionClient(catalog),
        history = aiResponseHistoryStore,
    )
    private val _uiState = MutableStateFlow(
        ScannerUiState(
            mode = ScannerMode.PermissionRequired,
            scannerSettings = currentSettings,
            feedback = FeedbackUiState(feedbackStore.sampleCount.value),
            soundAssets = soundEffectStore.statuses(),
            collectedKeys = collectionStore.collectedKeys.value,
            authorShareModeStartedAt = authorShareModeStartedAt,
        ),
        )
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<ScannerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ScannerEvent> = _events.asSharedFlow()

    private var cameraPermissionGranted = false
    private var inferenceJob: Job? = null
    private var settingsTestJob: Job? = null

    init {
        viewModelScope.launch {
            feedbackStore.sampleCount.collectLatest { count ->
                _uiState.value = _uiState.value.copy(feedback = _uiState.value.feedback.copy(sampleCount = count))
            }
        }
        viewModelScope.launch {
            collectionStore.collectedKeys.collectLatest { keys -> _uiState.value = _uiState.value.copy(collectedKeys = keys) }
        }
        viewModelScope.launch {
            aiResponseHistoryStore.entries.collectLatest { entries -> _uiState.value = _uiState.value.copy(aiResponseHistory = entries) }
        }
    }

    fun onCameraPermissionChanged(granted: Boolean) {
        cameraPermissionGranted = granted
        if (_uiState.value.mode !is ScannerMode.Result && _uiState.value.mode != ScannerMode.Capturing) {
            _uiState.value = reduceScannerState(_uiState.value, ScannerAction.PermissionChanged(granted))
        }
    }

    fun openCatalog() { _uiState.value = reduceScannerState(_uiState.value, ScannerAction.OpenCatalog) }

    fun openGames() { _uiState.value = reduceScannerState(_uiState.value, ScannerAction.OpenGames) }

    fun openPage(page: ScannerPage) { _uiState.value = reduceScannerState(_uiState.value, ScannerAction.OpenPage(page)) }

    fun openPokemonDetail(pokemonIndex: Int) {
        if (pokemonIndex !in catalog.records.indices) return
        _uiState.value = reduceScannerState(_uiState.value, ScannerAction.OpenCatalogDetail(pokemonIndex))
        _events.tryEmit(ScannerEvent.SpeakPokemon(pokemonIndex))
    }

    fun openPokemonGallery(pokemonIndex: Int) {
        if (pokemonIndex in catalog.records.indices) _uiState.value = reduceScannerState(_uiState.value, ScannerAction.OpenPokemonGallery(pokemonIndex))
    }

    fun photoStore(): PokemonPhotoStore = pokemonPhotoStore

    fun navigateBack() {
        _uiState.value = if (_uiState.value.page == ScannerPage.CatalogDetail) {
            reduceScannerState(_uiState.value, ScannerAction.BackToCatalog)
        } else {
            reduceScannerState(_uiState.value, ScannerAction.BackToCamera)
        }
    }

    fun backToCamera() {
        cancelActiveRequests()
        _uiState.value = reduceScannerState(_uiState.value, ScannerAction.BackToCamera)
    }

    fun saveScannerSettings(settings: ScannerSettings) {
        val safe = settings.sanitized()
        currentSettings = safe
        persistSettings(safe)
        _uiState.value = _uiState.value.copy(scannerSettings = safe)
        viewModelScope.launch { aiResponseHistoryStore.trim(safe.aiHistoryLimit) }
    }

    fun activateAuthorShareMode(cdk: String): Boolean {
        if (cdk != AUTHOR_SHARE_CDK) return false
        val startedAt = System.currentTimeMillis()
        val shared = currentSettings.withProvider(CloudAiProvider.VolcArk).copy(
            userAi = currentSettings.withProvider(CloudAiProvider.VolcArk).userAi.copy(
                model = AUTHOR_SHARE_MODEL,
            ),
        ).sanitized()
        authorShareModeStartedAt = startedAt
        currentSettings = shared
        persistSettings(shared)
        preferences.edit().putBoolean(AUTHOR_SHARE_ENABLED, true).putLong(AUTHOR_SHARE_STARTED_AT, startedAt).apply()
        _uiState.value = _uiState.value.copy(scannerSettings = shared, authorShareModeStartedAt = startedAt)
        return true
    }

    fun exitAuthorShareMode() {
        val restored = currentSettings.copy(
            userAi = currentSettings.userAi.copy(apiKey = "", model = DEFAULT_ARK_MODEL),
        ).sanitized()
        authorShareModeStartedAt = null
        currentSettings = restored
        persistSettings(restored)
        preferences.edit().putBoolean(AUTHOR_SHARE_ENABLED, false).remove(AUTHOR_SHARE_STARTED_AT).apply()
        _uiState.value = _uiState.value.copy(scannerSettings = restored, authorShareModeStartedAt = null)
    }

    fun refreshAuthorShareMode() {
        val startedAt = authorShareModeStartedAt ?: return
        val now = System.currentTimeMillis()
        if (now < startedAt || now - startedAt >= AUTHOR_SHARE_DURATION_MILLIS) exitAuthorShareMode()
    }

    fun removeAiResponseHistory(id: String) { viewModelScope.launch { aiResponseHistoryStore.remove(id) } }
    fun clearAiResponseHistory() { viewModelScope.launch { aiResponseHistoryStore.clear() } }
    fun clearTransientCache() {
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            _events.emit(ScannerEvent.Notice("缓存已清除"))
        }
    }

    fun saveSettingsAndReload(settings: ScannerSettings) = saveScannerSettings(settings)

    fun recognize(source: ScannerImageSource) {
        if (_uiState.value.mode != ScannerMode.Preview) return
        val settings = currentSettings.sanitized()
        inferenceJob?.cancel()
        settingsTestJob?.cancel()
        _uiState.value = reduceScannerState(_uiState.value, ScannerAction.Capture)
        _events.tryEmit(ScannerEvent.PlaySound(AppSoundEffect.ScanStarted))
        inferenceJob = viewModelScope.launch {
            try {
                val normalized = ImagePreprocessor.preprocess(getApplication(), source, settings.imageProcessing)
                _uiState.value = reduceScannerState(_uiState.value, ScannerAction.CapturePrepared(normalized.feedbackJpeg))
                val started = SystemClock.elapsedRealtime()
                val result = withTimeout(settings.userAi.timeoutSeconds * 1_000L) {
                    recognitionRepository.recognize(normalized)
                }
                _uiState.value = _uiState.value.copy(lastInferenceMillis = SystemClock.elapsedRealtime() - started)
                when (result) {
                    is RecognitionResult.Success -> {
                        rankRecognitionCandidate(catalog, result.candidate)?.let { ranked ->
                            runCatching { pokemonPhotoStore.save(catalog.recordAt(ranked.pokemonIndex).key, normalized.feedbackJpeg) }
                        }
                        completeRecognition(result.candidate, source is ScannerImageSource.CameraFile)
                    }
                    is RecognitionResult.Failure -> failRecognition(result.message)
                }
            } catch (_: TimeoutCancellationException) {
                recognitionRepository.cancel()
                failRecognition("云端 AI 请求超时，请检查网络或调高超时时间")
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Throwable) {
                failRecognition(error.message ?: "云端 AI 识别失败")
            } finally {
                if (source is ScannerImageSource.CameraFile) source.file.delete()
            }
        }
    }

    private fun completeRecognition(candidate: RecognitionCandidate, collectOnSuccess: Boolean) {
        val ranked = rankRecognitionCandidate(catalog, candidate) ?: return failRecognition("AI 返回的名称不在本地图鉴中")
        _uiState.value = reduceScannerState(_uiState.value, ScannerAction.CaptureCompleted(ranked))
        if (collectOnSuccess) collectionStore.collect(catalog.recordAt(ranked.pokemonIndex).key)
        _events.tryEmit(ScannerEvent.PlaySound(AppSoundEffect.RecognitionSuccess))
        _events.tryEmit(ScannerEvent.SpeakPokemon(ranked.pokemonIndex))
    }

    private fun failRecognition(message: String) {
        _events.tryEmit(ScannerEvent.PlaySound(AppSoundEffect.RecognitionFailure))
        _uiState.value = reduceScannerState(_uiState.value, ScannerAction.Failed(message))
    }

    fun testScannerSettings(settings: ScannerSettings) {
        if (settingsTestJob?.isActive == true) return
        val safe = settings.sanitized()
        safe.developerAi.validate()?.let {
            _uiState.value = _uiState.value.copy(settingsTest = SettingsTestUiState(message = it))
            return
        }
        saveScannerSettings(safe)
        inferenceJob?.cancel()
        _uiState.value = _uiState.value.copy(settingsTest = SettingsTestUiState(true, message = "正在使用内置图片测试${safe.userAi.provider.displayName}"))
        settingsTestJob = viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    ResourceBundleRepository(getApplication<Application>()).openAsset(CONNECTION_TEST_IMAGE).use { it.readBytes() }
                }
                val image = ImagePreprocessor.preprocessJpeg(bytes, safe.imageProcessing)
                val result = recognitionRepository.testConnection(image)
                val elapsed = (result as? ConnectionTestResult.Success)?.elapsedMillis
                val message = when (result) {
                    is ConnectionTestResult.Success -> buildString {
                        append("连接成功：${result.candidate.standardName}，${result.elapsedMillis}ms")
                        result.requestId?.let { append("，请求 ID：$it") }
                    }
                    is ConnectionTestResult.Failure -> buildString {
                        append(result.message)
                        result.requestId?.let { append("，请求 ID：$it") }
                    }
                }
                _uiState.value = _uiState.value.copy(lastInferenceMillis = elapsed, settingsTest = SettingsTestUiState(elapsedMillis = elapsed, message = message))
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(settingsTest = SettingsTestUiState(message = error.message ?: "连接测试失败"))
            }
        }
    }

    fun retry() {
        cancelActiveRequests()
        _uiState.value = reduceScannerState(_uiState.value, ScannerAction.Retry)
    }

    fun reset() {
        cancelActiveRequests()
        _uiState.value = reduceScannerState(_uiState.value, ScannerAction.Reset)
    }

    fun importSoundEffect(effect: AppSoundEffect, uri: Uri) {
        viewModelScope.launch {
            runCatching { soundEffectStore.import(effect, uri) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(soundAssets = soundEffectStore.statuses())
                    _events.tryEmit(ScannerEvent.Notice("${effect.chineseName}音效已替换"))
                }
                .onFailure { _events.tryEmit(ScannerEvent.Notice(it.message ?: "无法导入音效")) }
        }
    }

    fun resetSoundEffect(effect: AppSoundEffect) {
        viewModelScope.launch {
            soundEffectStore.reset(effect)
            _uiState.value = _uiState.value.copy(soundAssets = soundEffectStore.statuses())
            _events.tryEmit(ScannerEvent.Notice("${effect.chineseName}已恢复默认"))
        }
    }

    fun reportError(message: String) { failRecognition(message) }

    private fun cancelActiveRequests() {
        inferenceJob?.cancel()
        settingsTestJob?.cancel()
        recognitionRepository.cancel()
    }

    private fun readSettings(): ScannerSettings {
        val defaults = ScannerSettings()
        val isLegacyCloudConfig = preferences.getInt("settings_schema", 0) < SETTINGS_SCHEMA_VERSION
        fun protocol() = runCatching { CloudApiProtocol.valueOf(preferences.getString("cloud_protocol", defaults.developerAi.protocol.name)!!) }.getOrDefault(defaults.developerAi.protocol)
        fun provider() = runCatching { CloudAiProvider.valueOf(preferences.getString("cloud_provider", CloudAiProvider.VolcArk.name)!!) }.getOrDefault(CloudAiProvider.VolcArk)
        fun thinking() = runCatching { ThinkingMode.valueOf(preferences.getString("cloud_thinking", defaults.developerAi.thinking.name)!!) }.getOrDefault(defaults.developerAi.thinking)
        val selectedVoicePack = VoicePackId.fromWire(
            preferences.getString("selected_voice_pack", VoicePackId.Original.wireValue).orEmpty(),
        ) ?: VoicePackId.Original
        return ScannerSettings(
            userAi = UserAiSettings(
                preferences.getString("cloud_api_key", "") ?: "",
                if (isLegacyCloudConfig) DEFAULT_ARK_MODEL else preferences.getString("cloud_model", DEFAULT_ARK_MODEL) ?: DEFAULT_ARK_MODEL,
                preferences.getInt("cloud_timeout", defaults.userAi.timeoutSeconds),
                provider(),
            ),
            developerAi = DeveloperAiSettings(
                if (isLegacyCloudConfig) CloudApiProtocol.Responses else protocol(),
                if (isLegacyCloudConfig) DEFAULT_ARK_RESPONSES_URL else preferences.getString("cloud_url", DEFAULT_ARK_RESPONSES_URL) ?: DEFAULT_ARK_RESPONSES_URL,
                preferences.getString("cloud_auth_header", "Authorization") ?: "Authorization",
                preferences.getString("cloud_auth_scheme", "Bearer") ?: "Bearer",
                preferences.getFloat("cloud_temperature", defaults.developerAi.temperature),
                preferences.getInt("cloud_max_tokens", defaults.developerAi.maxTokens),
                thinking(),
                preferences.getString("cloud_prompt", DEFAULT_RECOGNITION_PROMPT) ?: DEFAULT_RECOGNITION_PROMPT,
                preferences.getString("cloud_extra_headers", "{}") ?: "{}",
                preferences.getString("cloud_extra_body", "{}") ?: "{}",
            ),
            imageProcessing = ImageProcessingSettings(
                preferences.getInt("image_max_edge", defaults.imageProcessing.maxEdge),
                preferences.getInt("jpeg_quality", defaults.imageProcessing.jpegQuality),
            ),
            narrationSettings = NarrationSettings(
                selectedVoicePackId = selectedVoicePack,
                volume = preferences.getFloat("speech_volume", 1f),
            ),
            soundEffectSettings = SoundEffectSettings(
                preferences.getBoolean("sound_enabled", true),
                preferences.getFloat("sound_volume", SoundEffectSettings.DEFAULT_VOLUME),
            ),
            aiHistoryLimit = preferences.getInt("ai_history_limit", 5),
        ).sanitized()
    }

    private fun readValidAuthorShareStart(): Long? {
        if (!preferences.getBoolean(AUTHOR_SHARE_ENABLED, false)) return null
        val startedAt = preferences.getLong(AUTHOR_SHARE_STARTED_AT, 0L)
        val now = System.currentTimeMillis()
        if (startedAt > 0L && now >= startedAt && now - startedAt < AUTHOR_SHARE_DURATION_MILLIS) return startedAt
        preferences.edit()
            .putBoolean(AUTHOR_SHARE_ENABLED, false)
            .remove(AUTHOR_SHARE_STARTED_AT)
            .putString("cloud_api_key", "")
            .putString("cloud_model", DEFAULT_ARK_MODEL)
            .apply()
        return null
    }

    private fun persistSettings(settings: ScannerSettings) {
        val s = settings.sanitized()
        preferences.edit()
            .putInt("settings_schema", SETTINGS_SCHEMA_VERSION)
            .putString("cloud_api_key", s.userAi.apiKey)
            .putString("cloud_model", s.userAi.model)
            .putInt("cloud_timeout", s.userAi.timeoutSeconds)
            .putString("cloud_provider", s.userAi.provider.name)
            .putString("cloud_protocol", s.developerAi.protocol.name)
            .putString("cloud_url", s.developerAi.apiUrl)
            .putString("cloud_auth_header", s.developerAi.authHeader)
            .putString("cloud_auth_scheme", s.developerAi.authScheme)
            .putFloat("cloud_temperature", s.developerAi.temperature)
            .putInt("cloud_max_tokens", s.developerAi.maxTokens)
            .putString("cloud_thinking", s.developerAi.thinking.name)
            .putString("cloud_prompt", s.developerAi.systemPrompt)
            .putString("cloud_extra_headers", s.developerAi.extraHeadersJson)
            .putString("cloud_extra_body", s.developerAi.extraBodyJson)
            .putInt("image_max_edge", s.imageProcessing.maxEdge)
            .putInt("jpeg_quality", s.imageProcessing.jpegQuality)
            .putString("selected_voice_pack", s.narrationSettings.selectedVoicePackId.wireValue)
            .putFloat("speech_volume", s.narrationSettings.volume)
            .putBoolean("sound_enabled", s.soundEffectSettings.enabled)
            .putFloat("sound_volume", s.soundEffectSettings.volume)
            .putInt("ai_history_limit", s.aiHistoryLimit)
            .apply()
    }

    override fun onCleared() {
        cancelActiveRequests()
        recognitionRepository.close()
        super.onCleared()
    }

    companion object {
        private const val SETTINGS_FILE = "scanner_settings"
        private const val SETTINGS_SCHEMA_VERSION = 8
        private const val CONNECTION_TEST_IMAGE = "pokemon/images/p0025_v00.png"
        private const val AUTHOR_SHARE_ENABLED = "author_share_enabled"
        private const val AUTHOR_SHARE_STARTED_AT = "author_share_started_at"
        private const val AUTHOR_SHARE_CDK = "作者伍子胥免费分享"
        private const val AUTHOR_SHARE_MODEL = "ep-m-20260725175846-xqm6w"
        private const val AUTHOR_SHARE_DURATION_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}

internal fun rankRecognitionCandidate(catalog: PokemonCatalog, candidate: RecognitionCandidate): RankedPokemonCandidate? =
    catalog.indexOf(candidate.standardName)?.let {
        RankedPokemonCandidate(it, candidate.probability?.takeIf(Float::isFinite)?.coerceIn(0f, 1f), candidate.isShiny)
    }

private object ImagePreprocessor {
    suspend fun preprocess(application: Application, source: ScannerImageSource, settings: ImageProcessingSettings): NormalizedImage = withContext(Dispatchers.IO) {
        val decoderSource = when (source) {
            is ScannerImageSource.CameraFile -> ImageDecoder.createSource(source.file)
            is ScannerImageSource.GalleryUri -> ImageDecoder.createSource(application.contentResolver, source.uri)
        }
        decode(decoderSource, settings)
    }

    suspend fun preprocessJpeg(bytes: ByteArray, settings: ImageProcessingSettings): NormalizedImage = withContext(Dispatchers.IO) {
        decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), settings)
    }

    private fun decode(source: ImageDecoder.Source, settings: ImageProcessingSettings): NormalizedImage {
        val safe = settings.sanitized()
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_FEEDBACK_EDGE && info.size.width > 0 && info.size.height > 0) {
                val scale = MAX_FEEDBACK_EDGE.toFloat() / longest
                val width = (info.size.width * scale).toInt().coerceIn(1, MAX_FEEDBACK_EDGE)
                val height = (info.size.height * scale).toInt().coerceIn(1, MAX_FEEDBACK_EDGE)
                decoder.setTargetSize(width, height)
            }
        }
        val feedback = bitmap.toJpeg(safe.jpegQuality)
        val inference = if (maxOf(bitmap.width, bitmap.height) > safe.maxEdge) {
            val scale = safe.maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
            val width = (bitmap.width * scale).toInt().coerceIn(1, safe.maxEdge)
            val height = (bitmap.height * scale).toInt().coerceIn(1, safe.maxEdge)
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else bitmap
        val jpeg = if (inference === bitmap) feedback else inference.toJpeg(safe.jpegQuality)
        if (inference !== bitmap) inference.recycle()
        bitmap.recycle()
        return NormalizedImage(jpeg, feedback)
    }

    private fun Bitmap.toJpeg(quality: Int) = ByteArrayOutputStream().use { output ->
        check(compress(Bitmap.CompressFormat.JPEG, quality, output)) { "无法处理所选图片" }
        output.toByteArray()
    }

    // Keep both feedback and inference bitmaps bounded on high-resolution OEM cameras.
    private const val MAX_FEEDBACK_EDGE = 1280
}
