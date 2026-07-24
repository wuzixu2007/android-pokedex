/* Narration-script normalization tests. / 播报脚本规范化测试。 */
package com.example.pokedex

import com.example.pokedex.ui.scanner.PokemonInfo
import com.example.pokedex.ui.scanner.PokemonSpeechScript
import com.example.pokedex.ui.scanner.NarrationSettings
import com.example.pokedex.ui.scanner.VoicePreset
import org.junit.Assert.assertEquals
import org.junit.Test

class PokemonSpeechScriptTest {
    @Test
    fun scriptIncludesNameAttributeAndCompleteNormalizedProfile() {
        val pokemon = PokemonInfo(
            key = "p0007_v00",
            id = "0007",
            nameZh = "杰尼龟",
            nameEn = "Squirtle",
            types = listOf("水"),
            attributeLabel = "水属性宝可梦",
            category = "小龟宝可梦",
            height = "0.5m",
            weight = "9.0kg",
            description = "",
            profile = "第一段。\n第二段完整保留。",
            imageAsset = "",
        )

        assertEquals(
            "这是杰尼龟。它是水属性宝可梦。第一段。 第二段完整保留。",
            PokemonSpeechScript.build(pokemon),
        )
    }

    @Test
    fun voicePresetsAndCustomValuesUseSafeRanges() {
        val fast = NarrationSettings.forPreset(VoicePreset.FastMale)
        assertEquals(1.80f, fast.speechRate)
        assertEquals(0.58f, fast.speechPitch)

        val sanitized = NarrationSettings(
            preset = VoicePreset.Custom,
            speechRate = 5f,
            speechPitch = 0.1f,
            volume = 2f,
        ).sanitized()
        assertEquals(NarrationSettings.MAX_SPEECH_RATE, sanitized.speechRate)
        assertEquals(NarrationSettings.MIN_SPEECH_PITCH, sanitized.speechPitch)
        assertEquals(1f, sanitized.volume)
    }
}
