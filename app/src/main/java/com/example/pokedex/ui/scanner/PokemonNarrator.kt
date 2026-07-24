/* Offline system-TTS narration boundary and speech script. / 离线系统 TTS 播报边界与朗读脚本。 */
package com.example.pokedex.ui.scanner

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.io.Closeable
import java.util.Locale

enum class VoicePreset(
    val speechRate: Float,
    val speechPitch: Float,
) {
    DeepMale(speechRate = 0.90f, speechPitch = 0.45f),
    StandardMale(speechRate = 1.30f, speechPitch = 0.68f),
    FastMale(speechRate = 1.80f, speechPitch = 0.58f),
    Custom(speechRate = 1.30f, speechPitch = 0.68f),
}

data class NarratorVoiceOption(
    val id: String,
    val label: String,
    val likelyMale: Boolean,
)

data class NarrationSettings(
    val preset: VoicePreset = VoicePreset.FastMale,
    val speechRate: Float = VoicePreset.FastMale.speechRate,
    val speechPitch: Float = VoicePreset.FastMale.speechPitch,
    val volume: Float = 1f,
    val voiceName: String? = null,
) {
    fun sanitized(): NarrationSettings = copy(
        speechRate = speechRate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE),
        speechPitch = speechPitch.coerceIn(MIN_SPEECH_PITCH, MAX_SPEECH_PITCH),
        volume = volume.coerceIn(0f, 1f),
        voiceName = voiceName?.trim()?.takeIf(String::isNotEmpty),
    )

    companion object {
        const val MIN_SPEECH_RATE = 0.50f
        const val MAX_SPEECH_RATE = 2.50f
        const val MIN_SPEECH_PITCH = 0.35f
        const val MAX_SPEECH_PITCH = 1.75f

        fun forPreset(
            preset: VoicePreset,
            volume: Float = 1f,
            voiceName: String? = null,
        ): NarrationSettings = NarrationSettings(
            preset = preset,
            speechRate = preset.speechRate,
            speechPitch = preset.speechPitch,
            volume = volume,
            voiceName = voiceName,
        ).sanitized()
    }
}

interface PokemonNarrator : Closeable {
    fun updateSettings(settings: NarrationSettings)
    fun updateLanguage(language: AppLanguage)
    fun speak(pokemon: PokemonInfo, language: AppLanguage = AppLanguage.Chinese)
    fun preview(settings: NarrationSettings)
    fun stop()
}

object PokemonSpeechScript {
    fun build(pokemon: PokemonInfo, language: AppLanguage = AppLanguage.Chinese): String {
        if (language == AppLanguage.English) {
            return "This is ${pokemon.nameEn}. It is ${pokemon.localizedAttributeLabel(language)}."
        }
        val profile = pokemon.profile
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return "这是${pokemon.nameZh}。它是${pokemon.attributeLabel}。$profile"
    }
}

class AndroidPokemonNarrator(
    context: Context,
    private val onUnavailable: (String) -> Unit,
    private val onVoicesAvailable: (List<NarratorVoiceOption>) -> Unit = {},
) : PokemonNarrator {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var currentSettings = NarrationSettings()
    private var currentLanguage = AppLanguage.Chinese
    private var pendingSpeech: PendingSpeech? = null
    private var failureReported = false

    init {
        engine = TextToSpeech(context.applicationContext, ::handleInitialized)
    }

    override fun updateSettings(settings: NarrationSettings) {
        currentSettings = settings.sanitized()
        if (ready) applyVoiceSettings(currentSettings)
    }

    override fun updateLanguage(language: AppLanguage) {
        if (currentLanguage == language) return
        currentLanguage = language
        if (ready) configureLanguageAndVoices()
    }

    override fun speak(pokemon: PokemonInfo, language: AppLanguage) {
        currentLanguage = language
        if (ready) configureLanguageAndVoices()
        val text = PokemonSpeechScript.build(pokemon, language)
        if (ready) {
            speakNow(text, currentSettings, language)
        } else if (!closed) {
            pendingSpeech = PendingSpeech(text, currentSettings, language)
        }
    }

    override fun preview(settings: NarrationSettings) {
        val speech = PendingSpeech(previewText(currentLanguage), settings.sanitized(), currentLanguage)
        if (ready) {
            speakNow(speech.text, speech.settings, speech.language)
        } else if (!closed) {
            pendingSpeech = speech
        }
    }

    override fun stop() {
        pendingSpeech = null
        engine?.stop()
    }

    override fun close() {
        if (closed) return
        closed = true
        stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun handleInitialized(status: Int) {
        if (closed) return
        val activeEngine = engine ?: return reportUnavailable("系统语音服务不可用")
        if (status != TextToSpeech.SUCCESS) {
            reportUnavailable("系统语音服务初始化失败")
            return
        }

        val languageResult = activeEngine.setLanguage(currentLanguage.locale())
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            reportUnavailable(localizedText(currentLanguage, "手机未安装可用的中文语音", "No compatible English voice is installed"))
            return
        }

        activeEngine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        ready = true
        configureLanguageAndVoices()
        applyVoiceSettings(currentSettings)
        pendingSpeech?.also {
            pendingSpeech = null
            speakNow(it.text, it.settings, it.language)
        }
    }

    private fun speakNow(text: String, settings: NarrationSettings, language: AppLanguage) {
        currentLanguage = language
        configureLanguageAndVoices()
        applyVoiceSettings(settings)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume)
        }
        val result = engine?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
        if (result == TextToSpeech.ERROR) reportUnavailable("无法播放宝可梦语音")
    }

    private fun applyVoiceSettings(settings: NarrationSettings) {
        val activeEngine = engine ?: return
        val voices = activeEngine.voices
            ?.filter { voice ->
                voice.locale.language == currentLanguage.locale().language &&
                    !voice.isNetworkConnectionRequired
            }
            .orEmpty()
        selectVoice(activeEngine, settings, voices)
        activeEngine.setSpeechRate(settings.speechRate)
        activeEngine.setPitch(settings.speechPitch)
    }

    private fun configureLanguageAndVoices() {
        val activeEngine = engine ?: return
        val locale = currentLanguage.locale()
        val result = activeEngine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return
        val voices = activeEngine.voices
            ?.filter { voice -> voice.locale.language == locale.language && !voice.isNetworkConnectionRequired }
            ?.sortedWith(
                compareByDescending<Voice> { it.malePreferenceScore() }
                    .thenByDescending { it.locale.country == locale.country }
                    .thenBy { it.name },
            )
            .orEmpty()
        onVoicesAvailable(
            voices.mapIndexed { index, voice ->
                val likelyMale = voice.malePreferenceScore() > 0
                NarratorVoiceOption(
                    id = voice.name,
                    label = localizedText(
                        currentLanguage,
                        "${if (likelyMale) "中文男声" else "中文音色"} ${index + 1}",
                        "${if (likelyMale) "English male" else "English voice"} ${index + 1}",
                    ),
                    likelyMale = likelyMale,
                )
            },
        )
        selectVoice(activeEngine, currentSettings, voices)
    }

    private fun selectVoice(
        activeEngine: TextToSpeech,
        settings: NarrationSettings,
        voices: List<Voice>,
    ) {
        val selected = settings.voiceName
            ?.let { name -> voices.firstOrNull { it.name == name } }
            ?: voices.sortedWith(
                compareByDescending<Voice> { it.malePreferenceScore() }
                    .thenByDescending { it.locale.country == Locale.SIMPLIFIED_CHINESE.country }
                    .thenBy { it.name },
            ).firstOrNull()
        if (selected != null && activeEngine.voice?.name != selected.name) {
            activeEngine.voice = selected
        }
    }

    private fun reportUnavailable(message: String) {
        ready = false
        pendingSpeech = null
        if (!failureReported) {
            failureReported = true
            onUnavailable(message)
        }
    }

    private fun Voice.malePreferenceScore(): Int {
        val descriptor = buildString {
            append(name)
            append(' ')
            append(features.joinToString(" "))
        }.lowercase(Locale.ROOT)
        return when {
            MALE_VOICE_MARKERS.any(descriptor::contains) -> 2
            MALE_VOICE_TOKEN.containsMatchIn(descriptor) -> 1
            else -> 0
        }
    }

    private companion object {
        const val UTTERANCE_ID = "pokedex_identification"
        val MALE_VOICE_MARKERS = listOf("male", "masculine", "男声", "男聲")
        val MALE_VOICE_TOKEN = Regex("(^|[-_ ])m(?:ale)?[0-9]*(?:$|[-_ ])")
    }

    private data class PendingSpeech(
        val text: String,
        val settings: NarrationSettings,
        val language: AppLanguage,
    )

    private fun previewText(language: AppLanguage): String = localizedText(
        language,
        "这是皮卡丘。它是电属性宝可梦。",
        "This is Pikachu. It is an Electric-type Pokémon.",
    )

    private fun AppLanguage.locale(): Locale =
        if (this == AppLanguage.Chinese) Locale.SIMPLIFIED_CHINESE else Locale.US
}
