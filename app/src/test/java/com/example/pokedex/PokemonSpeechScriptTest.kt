/* Narration-script normalization tests. / 播报脚本规范化测试。 */
package com.example.pokedex

import com.example.pokedex.data.scanner.*
import com.example.pokedex.domain.scanner.*
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
    fun narrationSettingsDefaultToOriginalAndClampVolume() {
        val sanitized = NarrationSettings(volume = 2f).sanitized()

        assertEquals(VoicePackId.Original, sanitized.selectedVoicePackId)
        assertEquals(1f, sanitized.volume)
    }
}
