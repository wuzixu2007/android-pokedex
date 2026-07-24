/* Global UI language and compact translation helpers. / 全局界面语言与轻量翻译辅助。 */
package com.example.pokedex.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage { Chinese, English }

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Chinese }

@Composable
fun appText(chinese: String, english: String): String =
    localizedText(LocalAppLanguage.current, chinese, english)

fun localizedText(language: AppLanguage, chinese: String, english: String): String =
    if (language == AppLanguage.Chinese) chinese else english

fun PokemonInfo.localizedDisplayName(language: AppLanguage): String =
    if (language == AppLanguage.Chinese) nameZh else nameEn

fun PokemonInfo.localizedAttributeLabel(language: AppLanguage): String {
    if (language == AppLanguage.Chinese) return attributeLabel
    val labels = types.map { type ->
        when (type.removeSuffix("属性")) {
            "一般" -> "Normal"
            "火" -> "Fire"
            "水" -> "Water"
            "电" -> "Electric"
            "草" -> "Grass"
            "冰" -> "Ice"
            "格斗" -> "Fighting"
            "毒" -> "Poison"
            "地面" -> "Ground"
            "飞行" -> "Flying"
            "超能力" -> "Psychic"
            "虫" -> "Bug"
            "岩石" -> "Rock"
            "幽灵" -> "Ghost"
            "龙" -> "Dragon"
            "恶" -> "Dark"
            "钢" -> "Steel"
            "妖精" -> "Fairy"
            else -> type
        }
    }
    return "${labels.joinToString(" / ")}-type Pokémon"
}
