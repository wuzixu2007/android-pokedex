package com.example.pokedex.data.scanner

import com.example.pokedex.domain.scanner.*

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap

data class PokemonBasicDetails(
    val color: String?, val catchRate: String?, val eggGroups: List<String>, val genderRatio: String?,
    val maleRatio: Double?, val femaleRatio: Double?,
    val eggCycles: String?, val level100Experience: String?, val baseExperience: String?,
    val battleExperience: String?, val shape: String?, val footprint: String?,
)
data class PokemonSpecialEvolution(val kind: String, val name: String, val formName: String, val imageAsset: String?)
data class PokemonBattleDetails(
    val specialEvolutions: List<PokemonSpecialEvolution>,
)
data class PokemonEcologyDetails(val description: String?, val profile: String?, val prototype: String?, val detail: String?)
data class PokemonEvolutionEntry(
    val chain: Int, val name: String, val stage: String?, val condition: String?, val from: String?,
    val formName: String?, val imageAsset: String?, val itemAsset: String?,
)
data class PokemonDexEntry(val generation: String, val version: String, val group: String?, val text: String)
data class PokemonLocalizedName(val language: String, val name: String, val origin: String?)
data class PokemonMove(
    val group: String, val level: String?, val name: String, val type: String?, val category: String?,
    val power: String?, val accuracy: String?, val pp: String?,
)
data class PokemonDetails(
    val basic: PokemonBasicDetails,
    val battle: PokemonBattleDetails,
    val ecology: PokemonEcologyDetails,
    val evolutions: List<PokemonEvolutionEntry>,
    val dexEntries: List<PokemonDexEntry>,
    val localizedNames: List<PokemonLocalizedName>,
    val moves: List<PokemonMove>,
)

class PokemonDetailsRepository(private val context: Context) {
    private val cache = object : LinkedHashMap<String, PokemonDetails>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PokemonDetails>?): Boolean = size > 6
    }

    fun load(record: PokemonRecord): PokemonDetails = synchronized(cache) {
        cache[record.key] ?: parse(record).also { cache[record.key] = it }
    }

    private fun parse(record: PokemonRecord): PokemonDetails {
        if (record.detailsAsset.isBlank()) return emptyDetails(record)
        val root = ResourceBundleRepository(context).openAsset(record.detailsAsset).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        val form = matchingObject(root.optJSONArray("forms"), record.sourceFormName, "name")
        return PokemonDetails(
            basic = PokemonBasicDetails(
                color = form.text("color"), catchRate = form.text("catch_rate"), eggGroups = form.strings("egg_groups"),
                genderRatio = genderRatio(form.optJSONObject("gender_ratio")), eggCycles = form.text("egg_cycles"),
                maleRatio = form.optJSONObject("gender_ratio").number("male"),
                femaleRatio = form.optJSONObject("gender_ratio").number("female"),
                level100Experience = form.text("experience_100"), baseExperience = form.text("base_exp"),
                battleExperience = form.text("battle_exp"), shape = form.text("shape"), footprint = form.text("footprint"),
            ),
            battle = PokemonBattleDetails(
                specialEvolutions = parseSpecialEvolutions(root),
            ),
            ecology = PokemonEcologyDetails(root.text("description"), root.text("profile"), root.text("prototype"), root.text("detail")),
            evolutions = parseEvolutionChains(root.optJSONArray("evolution_chains")),
            dexEntries = parseDexEntries(root.optJSONArray("pokedex_entries")),
            localizedNames = root.optJSONArray("names").objects().map {
                PokemonLocalizedName(it.optString("language"), it.optString("name"), it.text("origin"))
            },
            moves = buildList {
                addAll(parseMoves("升级招式", root.optJSONArray("learnable_moves"), record.sourceFormName))
                addAll(parseMoves("机器招式", root.optJSONArray("machine_moves"), record.sourceFormName))
                addAll(parseMoves("蛋招式", root.optJSONArray("egg_moves"), record.sourceFormName))
            },
        )
    }

    private fun parseSpecialEvolutions(root: JSONObject): List<PokemonSpecialEvolution> = buildList {
        root.optJSONArray("mega_evolution").objects().forEach {
            add(PokemonSpecialEvolution("超级进化", it.optString("name"), it.optString("form_name"), it.text("image_asset")))
        }
        root.optJSONArray("gigantamax_evolution").objects().forEach {
            add(PokemonSpecialEvolution("超极巨化", it.optString("name"), it.optString("form_name"), it.text("image_asset")))
        }
    }

    private fun parseEvolutionChains(chains: JSONArray?): List<PokemonEvolutionEntry> = buildList {
        if (chains == null) return@buildList
        repeat(chains.length()) { chainIndex ->
            chains.optJSONArray(chainIndex).objects().forEach {
                add(PokemonEvolutionEntry(
                    chainIndex + 1, it.optString("name"), it.text("stage"), it.text("text"), it.text("from"),
                    it.text("form_name"), it.text("image_asset"), it.text("item_asset"),
                ))
            }
        }
    }

    private fun parseDexEntries(entries: JSONArray?): List<PokemonDexEntry> = buildList {
        entries.objects().forEach { generation ->
            generation.optJSONArray("versions").objects().forEach { version ->
                val text = version.optString("text")
                if (text.isNotBlank()) add(PokemonDexEntry(generation.optString("name"), version.optString("name"), version.text("group"), text))
            }
        }
    }

    private fun parseMoves(group: String, groups: JSONArray?, formName: String): List<PokemonMove> {
        val values = matchingObject(groups, formName, "form").optJSONArray("data")
        return values.objects().map {
            PokemonMove(group, it.text("level"), it.optString("name"), it.text("type"), it.text("category"),
                it.text("power"), it.text("accuracy"), it.text("pp"))
        }
    }

    private fun matchingObject(values: JSONArray?, target: String, key: String): JSONObject {
        val objects = values.objects()
        return objects.maxByOrNull { matchScore(it.optString(key), target) } ?: JSONObject()
    }

    private fun matchScore(source: String, target: String): Int {
        val sourceKey = source.replace(FORM_SEPARATORS, "")
        val targetKey = target.replace(FORM_SEPARATORS, "")
        if (sourceKey == targetKey) return 100
        if (sourceKey.isNotBlank() && (sourceKey in targetKey || targetKey in sourceKey)) return 50
        val shared = FORM_TOKENS.count { it in sourceKey && it in targetKey }
        return shared * 10 + if (source in setOf("", "一般", "普通")) 1 else 0
    }

    private fun emptyDetails(record: PokemonRecord) = PokemonDetails(
        PokemonBasicDetails(null, null, emptyList(), null, null, null, null, null, null, null, null, null),
        PokemonBattleDetails(emptyList()),
        PokemonEcologyDetails(record.description, record.profile, null, null),
        emptyList(), emptyList(), emptyList(), emptyList(),
    )

    private fun genderRatio(value: JSONObject?): String? {
        if (value == null) return null
        val male = value.opt("male")?.toString()
        val female = value.opt("female")?.toString()
        return listOfNotNull(male?.let { "雄性 $it%" }, female?.let { "雌性 $it%" }).joinToString(" · ").takeIf(String::isNotBlank)
    }

    private fun JSONObject.text(key: String): String? = opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.takeIf(String::isNotBlank)
    private fun JSONObject?.number(key: String): Double? = this?.opt(key)?.toString()?.toDoubleOrNull()
    private fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { array -> List(array.length()) { array.optString(it) }.filter(String::isNotBlank) }.orEmpty()
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { optJSONObject(it) }.filterNotNull()

    companion object {
        private val FORM_SEPARATORS = Regex("[（）()\\s·_/-]")
        private val FORM_TOKENS = listOf("阿罗拉", "伽勒尔", "洗翠", "帕底亚", "超级", "超极巨", "原始", "达摩", "攻击", "防御", "速度")
    }
}
