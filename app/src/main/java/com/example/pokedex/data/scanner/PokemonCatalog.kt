/* Local Pokédex schema, exact indexes, and output grammar generation. / 本地图鉴结构、精确索引与输出语法生成。 */
package com.example.pokedex.data.scanner

import com.example.pokedex.domain.scanner.*

import android.content.Context
import org.json.JSONObject

data class PokemonStats(
    val hp: Int = 0,
    val attack: Int = 0,
    val defense: Int = 0,
    val specialAttack: Int = 0,
    val specialDefense: Int = 0,
    val speed: Int = 0,
)

data class PokemonAbilities(
    val normal: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
)

data class PokemonAppearance(
    val label: String,
    val imageAsset: String,
    val shinyImageAsset: String? = null,
)

data class PokemonInfo(
    val key: String,
    val id: String,
    val sourceFormName: String = "",
    val nameZh: String,
    val nameEn: String,
    val nameJa: String = "",
    val types: List<String>,
    val attributeLabel: String,
    val category: String,
    val height: String,
    val weight: String,
    val abilities: PokemonAbilities = PokemonAbilities(),
    val stats: PokemonStats = PokemonStats(),
    val description: String,
    val profile: String,
    val imageAsset: String,
    val shinyImageAsset: String? = null,
    val appearances: List<PokemonAppearance> = emptyList(),
    val detailsAsset: String = "",
)

typealias PokemonRecord = PokemonInfo

class PokemonCatalog private constructor(
    val records: List<PokemonRecord>,
    private val aliases: Map<String, String> = emptyMap(),
) {
    private val recordsByName = records.associateBy(PokemonInfo::nameZh)
    private val indexesByName = records.mapIndexed { index, record -> record.nameZh to index }.toMap()

    init {
        require(records.size == recordsByName.size) { "Pokemon names must be unique" }
    }

    val grammar: String by lazy(LazyThreadSafetyMode.NONE) {
        val alternatives = records.joinToString(" | ") { record ->
            "\"${escapeGrammarLiteral(record.nameZh)}\""
        }
        "root ::= name\nname ::= $alternatives\n"
    }

    fun findExact(name: String): PokemonRecord? = recordsByName[name] ?: aliases[name]?.let(recordsByName::get)

    fun indexOf(name: String): Int? = indexesByName[name] ?: aliases[name]?.let(indexesByName::get)

    fun canonicalName(name: String): String? = findMatch(name)?.nameZh

    /** Maps natural-language model output back to the catalog's canonical name. */
    fun findMatch(rawName: String): PokemonRecord? {
        val trimmed = rawName.trim()
        if (trimmed.isBlank()) return null
        findExact(trimmed)?.let { return it }

        val normalizedAliases = aliases.entries.associate { normalize(it.key) to it.value }
        normalizedAliases[normalize(trimmed)]?.let { recordsByName[it]?.let { record -> return record } }

        val inputVariants = variants(trimmed)
        records.forEach { record ->
            val labels = listOf(record.nameZh, record.sourceFormName).filter(String::isNotBlank)
            if (labels.any { label -> variants(label).any(inputVariants::contains) }) return record
        }

        return records.withIndex()
            .map { indexed ->
                val labels = listOf(indexed.value.nameZh, indexed.value.sourceFormName).filter(String::isNotBlank)
                val score = labels.flatMap { variants(it) }
                    .maxOf { label -> inputVariants.maxOf { input -> similarity(input, label) } }
                indexed to score
            }
            .maxWithOrNull(compareBy<Pair<IndexedValue<PokemonRecord>, Double>> { it.second }
                .thenByDescending { it.first.value.nameZh == trimmed }
                .thenBy { it.first.index })
            ?.first?.value
    }

    fun recordAt(index: Int): PokemonRecord = records[index.mod(records.size)]

    private fun variants(value: String): Set<String> {
        val normalized = normalize(value)
        val result = linkedSetOf(normalized)
        val region = REGIONS.firstOrNull { normalized.contains(it) }
        if (region != null) {
            val base = normalized.replace(region, "")
            result += region + base
            result += base + region
        }
        val form = FORMS.firstOrNull { normalized.contains(it) }
        if (form != null) {
            val base = normalized.replace(form, "")
            result += base + form
            result += form + base
        }
        return result.filter(String::isNotBlank).toSet()
    }

    private fun normalize(value: String): String = value.trim()
        .lowercase()
        .replace('（', '(')
        .replace('）', ')')
        .replace('Ｘ', 'x')
        .replace('Ｙ', 'y')
        .replace('Ｚ', 'z')
        .replace(Regex("[\\s()\\[\\]{}、，,。:：/\\\\]"), "")
        .replace("地区", "")
        .replace("的样子", "")
        .replace("形态", "")
        .replace("普通模式", "普通")
        .replace("普通形态", "普通")
        .replace("-", "")

    private fun similarity(left: String, right: String): Double {
        if (left == right) return 1.0
        val maxLength = maxOf(left.length, right.length)
        if (maxLength == 0) return 1.0
        return 1.0 - levenshtein(left, right).toDouble() / maxLength
    }

    private fun levenshtein(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (left[i] == right[j]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    companion object {
        private val REGIONS = listOf("阿罗拉", "伽勒尔", "洗翠", "帕底亚")
        private val FORMS = listOf("超极巨化", "超级")

        fun load(context: Context): PokemonCatalog {
            val json = ResourceBundleRepository(context).openAsset("pokemon/catalog.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return fromJson(json)
        }

        fun fromJson(json: String): PokemonCatalog {
            val entries = JSONObject(json).getJSONArray("records")
            val aliasesJson = JSONObject(json).optJSONObject("aliases")
            val aliases = aliasesJson?.keys()?.asSequence()?.associateWith { aliasesJson.getString(it) }.orEmpty()
            val records = buildList(entries.length()) {
                repeat(entries.length()) { index ->
                    val entry = entries.getJSONObject(index)
                    val typesJson = entry.getJSONArray("types")
                    add(
                        PokemonInfo(
                            key = entry.getString("key"),
                            id = entry.getString("id"),
                            sourceFormName = entry.optString("sourceFormName", ""),
                            nameZh = entry.getString("nameZh"),
                            nameEn = entry.getString("nameEn"),
                            nameJa = entry.optString("nameJa", ""),
                            types = buildList(typesJson.length()) {
                                repeat(typesJson.length()) { add(typesJson.getString(it)) }
                            },
                            attributeLabel = entry.getString("attributeLabel"),
                            category = entry.getString("category"),
                            height = entry.getString("height"),
                            weight = entry.getString("weight"),
                            abilities = entry.optJSONObject("abilities")?.let { abilities ->
                                PokemonAbilities(
                                    normal = abilities.optJSONArray("normal")?.let { values ->
                                        buildList(values.length()) { repeat(values.length()) { add(values.getString(it)) } }
                                    }.orEmpty(),
                                    hidden = abilities.optJSONArray("hidden")?.let { values ->
                                        buildList(values.length()) { repeat(values.length()) { add(values.getString(it)) } }
                                    }.orEmpty(),
                                )
                            } ?: PokemonAbilities(
                                normal = listOfNotNull(entry.optString("ability", "").takeIf(String::isNotBlank)),
                            ),
                            stats = entry.optJSONObject("stats")?.let { stats ->
                                PokemonStats(
                                    hp = stats.optInt("hp", 0),
                                    attack = stats.optInt("attack", 0),
                                    defense = stats.optInt("defense", 0),
                                    specialAttack = stats.optInt("specialAttack", 0),
                                    specialDefense = stats.optInt("specialDefense", 0),
                                    speed = stats.optInt("speed", 0),
                                )
                            } ?: PokemonStats(),
                            description = entry.getString("description"),
                            profile = entry.getString("profile"),
                            imageAsset = entry.getString("imageAsset"),
                            shinyImageAsset = entry.optString("shinyImageAsset", "").takeIf(String::isNotBlank),
                            appearances = entry.optJSONArray("appearances")?.let { values ->
                                buildList(values.length()) {
                                    repeat(values.length()) { appearanceIndex ->
                                        val appearance = values.getJSONObject(appearanceIndex)
                                        add(
                                            PokemonAppearance(
                                                label = appearance.getString("label"),
                                                imageAsset = appearance.getString("imageAsset"),
                                                shinyImageAsset = appearance.optString("shinyImageAsset", "").takeIf(String::isNotBlank),
                                            ),
                                        )
                                    }
                                }
                            }.orEmpty(),
                            detailsAsset = entry.optString("detailsAsset", ""),
                        ),
                    )
                }
            }
            return PokemonCatalog(records, aliases)
        }

        fun fromRecords(records: List<PokemonRecord>): PokemonCatalog = PokemonCatalog(records)

        private fun escapeGrammarLiteral(value: String): String = buildString(value.length) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }
    }
}

class PokemonNamePolicy(
    private val catalog: PokemonCatalog,
) {
    fun validate(rawOutput: String): PokemonRecord? = catalog.findExact(rawOutput)
}
