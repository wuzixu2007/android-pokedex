package com.example.pokedex.data.scanner

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.example.pokedex.domain.scanner.AppLanguage
import com.example.pokedex.domain.scanner.localizedAttributeLabel
import java.io.Closeable
import java.util.Locale

data class NarrationSettings(
    val selectedVoicePackId: VoicePackId = VoicePackId.Original,
    val volume: Float = 1f,
) {
    fun sanitized() = copy(
        volume = volume.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 1f,
    )
}

interface PokemonNarrator : Closeable {
    fun updateSettings(settings: NarrationSettings)
    fun updateLanguage(language: AppLanguage)
    fun speak(pokemon: PokemonInfo, language: AppLanguage = AppLanguage.Chinese)
    fun speakText(text: String, language: AppLanguage = AppLanguage.Chinese)
    fun preview(settings: NarrationSettings)
    fun stop()
}

/** Retained for generated-audio validation and legacy tests; runtime Pokemon speech uses AAC files. */
object PokemonSpeechScript {
    fun build(pokemon: PokemonInfo, language: AppLanguage = AppLanguage.Chinese): String {
        if (language == AppLanguage.English) {
            return "This is ${pokemon.nameEn}. It is ${pokemon.localizedAttributeLabel(language)}."
        }
        val profile = pokemon.profile.replace(Regex("[\\r\\n]+"), " ").replace(Regex("\\s+"), " ").trim()
        return "这是${pokemon.nameZh}。它是${pokemon.attributeLabel}。$profile"
    }
}

class PokemonAudioNarrator(
    context: Context,
    private val repository: VoicePackRepository,
    private val onUnavailable: (String) -> Unit,
) : Closeable {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private var settings = NarrationSettings()
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var generation = 0

    fun updateSettings(value: NarrationSettings) {
        settings = value.sanitized()
        player?.setVolume(settings.volume, settings.volume)
    }

    fun speak(pokemon: PokemonInfo) = play(settings.selectedVoicePackId, pokemon.key)

    fun preview(value: NarrationSettings) {
        updateSettings(value)
        play(value.selectedVoicePackId, PREVIEW_KEY)
    }

    private fun play(id: VoicePackId, pokemonKey: String) {
        stop()
        val file = repository.audioFile(id, pokemonKey)
        if (!file.isFile) {
            repository.markRepairRequired(id, "缺少语音文件：$pokemonKey")
            onUnavailable("当前语音包未安装或文件不完整，请在声音设置中下载或修复")
            return
        }
        if (!requestAudioFocus()) return
        val token = ++generation
        runCatching {
            MediaPlayer().also { active ->
                player = active
                active.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                active.setDataSource(file.absolutePath)
                active.setVolume(settings.volume, settings.volume)
                active.setOnPreparedListener { if (token == generation) it.start() else it.release() }
                active.setOnCompletionListener { finishPlayer(it) }
                active.setOnErrorListener { failed, _, _ ->
                    repository.markRepairRequired(id, "无法解码语音文件：$pokemonKey")
                    finishPlayer(failed)
                    onUnavailable("当前语音文件无法播放，请修复语音包")
                    true
                }
                active.prepareAsync()
            }
        }.onFailure {
            repository.markRepairRequired(id, it.message ?: "语音播放失败")
            stop()
            onUnavailable("当前语音文件无法播放，请修复语音包")
        }
    }

    fun stop() {
        generation++
        player?.runCatching { stop() }
        player?.release()
        player = null
        abandonAudioFocus()
    }

    private fun finishPlayer(value: MediaPlayer) {
        if (player === value) player = null
        value.runCatching { release() }
        abandonAudioFocus()
    }

    private fun requestAudioFocus(): Boolean {
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(listener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
        }
        focusRequest = null
    }

    override fun close() = stop()

    private companion object {
        const val PREVIEW_KEY = "p0025_a5e9fe9aa0"
    }
}

/** System TTS is intentionally limited to dynamic mini-game text. */
class DynamicTextSpeaker(
    context: Context,
    private val onUnavailable: (String) -> Unit,
) : Closeable {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var volume = 1f
    private var language = AppLanguage.Chinese
    private var pending: Pair<String, AppLanguage>? = null

    init {
        engine = runCatching { TextToSpeech(context.applicationContext, ::initialized) }
            .onFailure { onUnavailable("系统语音服务不可用") }.getOrNull()
    }

    fun updateSettings(settings: NarrationSettings) { volume = settings.sanitized().volume }
    fun updateLanguage(value: AppLanguage) {
        language = value
        if (ready) applyLanguage(value)
    }

    fun speak(text: String, value: AppLanguage) {
        if (text.isBlank()) return
        language = value
        if (!ready) {
            if (!closed) pending = text to value
            return
        }
        applyLanguage(value)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        if (engine?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "pokedex_dynamic_text") == TextToSpeech.ERROR) {
            onUnavailable("无法播放动态语音")
        }
    }

    fun stop() {
        pending = null
        engine?.stop()
    }

    private fun initialized(status: Int) {
        if (closed) return
        if (status != TextToSpeech.SUCCESS) {
            onUnavailable("系统语音服务初始化失败")
            return
        }
        ready = true
        engine?.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
        )
        applyLanguage(language)
        pending?.also { pending = null; speak(it.first, it.second) }
    }

    private fun applyLanguage(value: AppLanguage) {
        val locale = if (value == AppLanguage.Chinese) Locale.SIMPLIFIED_CHINESE else Locale.US
        val result = engine?.setLanguage(locale) ?: return
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            onUnavailable("系统未安装小游戏动态文本所需的语音")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        stop()
        engine?.shutdown()
        engine = null
        ready = false
    }
}
