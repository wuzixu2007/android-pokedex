package com.example.pokedex.data.scanner

import android.content.Context
import com.example.pokedex.domain.scanner.AppLanguage

/** Coordinates prerecorded Pokemon narration with the dynamic-text-only system TTS channel. */
class RoutingPokemonNarrator(
    context: Context,
    onUnavailable: (String) -> Unit,
) : PokemonNarrator {
    private val audio = PokemonAudioNarrator(context, VoicePackRepository(context), onUnavailable)
    private val dynamicText = DynamicTextSpeaker(context, onUnavailable)

    override fun updateSettings(settings: NarrationSettings) {
        audio.updateSettings(settings)
        dynamicText.updateSettings(settings)
    }

    override fun updateLanguage(language: AppLanguage) = dynamicText.updateLanguage(language)

    override fun speak(pokemon: PokemonInfo, language: AppLanguage) {
        dynamicText.stop()
        audio.speak(pokemon)
    }

    override fun speakText(text: String, language: AppLanguage) {
        audio.stop()
        dynamicText.speak(text, language)
    }

    override fun preview(settings: NarrationSettings) {
        dynamicText.stop()
        audio.preview(settings)
    }

    override fun stop() {
        audio.stop()
        dynamicText.stop()
    }

    override fun close() {
        audio.close()
        dynamicText.close()
    }
}
