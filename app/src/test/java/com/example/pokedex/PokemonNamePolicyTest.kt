/* Exact catalog-name policy tests. / 图鉴名称精确匹配策略测试。 */
package com.example.pokedex

import com.example.pokedex.ui.scanner.PokemonCatalog
import com.example.pokedex.ui.scanner.PokemonNamePolicy
import com.example.pokedex.ui.scanner.PokemonRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PokemonNamePolicyTest {
    private val pikachu = PokemonRecord(
        key = "p0025_v00",
        id = "0025",
        nameZh = "皮卡丘",
        nameEn = "Pikachu",
        types = listOf("电"),
        attributeLabel = "电属性宝可梦",
        category = "鼠宝可梦",
        height = "0.4m",
        weight = "6.0kg",
        description = "",
        profile = "皮卡丘会把电储存在脸颊两侧的电囊里。",
        imageAsset = "pokemon/images/p0025_v00.png",
    )
    private val policy = PokemonNamePolicy(PokemonCatalog.fromRecords(listOf(pikachu)))

    @Test
    fun exactCanonicalName_isAccepted() {
        assertEquals(pikachu, policy.validate("皮卡丘"))
    }

    @Test
    fun anyProtocolDecoration_isRejected() {
        listOf(
            " 皮卡丘",
            "皮卡丘 ",
            "皮卡丘\n",
            "\"皮卡丘\"",
            "**皮卡丘**",
            "{\"name\":\"皮卡丘\"}",
            "皮卡丘 99%",
            "识别结果：皮卡丘",
        ).forEach { output -> assertNull(output, policy.validate(output)) }
    }
}
