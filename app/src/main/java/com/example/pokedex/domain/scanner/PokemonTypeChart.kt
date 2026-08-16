package com.example.pokedex.domain.scanner

data class PokemonDamageRate(val attackType: String, val multiplier: Float)

object PokemonTypeChart {
    val types = listOf("一般", "火", "水", "电", "草", "冰", "格斗", "毒", "地面", "飞行", "超能力", "虫", "岩石", "幽灵", "龙", "恶", "钢", "妖精")

    private data class Matchup(val strong: Set<String> = emptySet(), val weak: Set<String> = emptySet(), val immune: Set<String> = emptySet())
    private val chart = mapOf(
        "一般" to Matchup(weak = setOf("岩石", "钢"), immune = setOf("幽灵")),
        "火" to Matchup(setOf("草", "冰", "虫", "钢"), setOf("火", "水", "岩石", "龙")),
        "水" to Matchup(setOf("火", "地面", "岩石"), setOf("水", "草", "龙")),
        "电" to Matchup(setOf("水", "飞行"), setOf("电", "草", "龙"), setOf("地面")),
        "草" to Matchup(setOf("水", "地面", "岩石"), setOf("火", "草", "毒", "飞行", "虫", "龙", "钢")),
        "冰" to Matchup(setOf("草", "地面", "飞行", "龙"), setOf("火", "水", "冰", "钢")),
        "格斗" to Matchup(setOf("一般", "冰", "岩石", "恶", "钢"), setOf("毒", "飞行", "超能力", "虫", "妖精"), setOf("幽灵")),
        "毒" to Matchup(setOf("草", "妖精"), setOf("毒", "地面", "岩石", "幽灵"), setOf("钢")),
        "地面" to Matchup(setOf("火", "电", "毒", "岩石", "钢"), setOf("草", "虫"), setOf("飞行")),
        "飞行" to Matchup(setOf("草", "格斗", "虫"), setOf("电", "岩石", "钢")),
        "超能力" to Matchup(setOf("格斗", "毒"), setOf("超能力", "钢"), setOf("恶")),
        "虫" to Matchup(setOf("草", "超能力", "恶"), setOf("火", "格斗", "毒", "飞行", "幽灵", "钢", "妖精")),
        "岩石" to Matchup(setOf("火", "冰", "飞行", "虫"), setOf("格斗", "地面", "钢")),
        "幽灵" to Matchup(setOf("超能力", "幽灵"), setOf("恶"), setOf("一般")),
        "龙" to Matchup(setOf("龙"), setOf("钢"), setOf("妖精")),
        "恶" to Matchup(setOf("超能力", "幽灵"), setOf("格斗", "恶", "妖精")),
        "钢" to Matchup(setOf("冰", "岩石", "妖精"), setOf("火", "水", "电", "钢")),
        "妖精" to Matchup(setOf("格斗", "龙", "恶"), setOf("火", "毒", "钢")),
    )

    fun incoming(defendingTypes: List<String>): List<PokemonDamageRate> = types.map { attackType ->
        PokemonDamageRate(attackType, defendingTypes.distinct().fold(1f) { rate, defendingType -> rate * rate(attackType, defendingType) })
    }

    fun rate(attackType: String, defendingType: String): Float {
        val matchup = chart[attackType] ?: return 1f
        return when (defendingType) {
            in matchup.immune -> 0f
            in matchup.strong -> 2f
            in matchup.weak -> 0.5f
            else -> 1f
        }
    }
}
