/* Local Pokédex schema, exact indexes, and output grammar generation. / 本地图鉴结构、精确索引与输出语法生成。 */
package com.example.pokedex.ui.scanner

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

data class PokemonInfo(
    val key: String,
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val nameJa: String = "",
    val types: List<String>,
    val attributeLabel: String,
    val category: String,
    val height: String,
    val weight: String,
    val ability: String = "",
    val stats: PokemonStats = PokemonStats(),
    val description: String,
    val profile: String,
    val imageAsset: String,
)

typealias PokemonRecord = PokemonInfo

class PokemonCatalog private constructor(
    val records: List<PokemonRecord>,
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

    private val candidateGrammars = HashMap<Int, String>()

    /** Grammar for an exact candidate count. Numeric range and ordering are validated in Kotlin. */
    fun candidateGrammar(candidateCount: Int): String = synchronized(candidateGrammars) {
        require(candidateCount > 0) { "Candidate count must be positive" }
        candidateGrammars.getOrPut(candidateCount) {
        val alternatives = records.joinToString(" | ") { record ->
            "\"${escapeGrammarLiteral(record.nameZh)}\""
        }
        val candidates = List(candidateCount) { "candidate" }.joinToString(" \",\" ws ")
        """
            root ::= "[" ws $candidates ws "]"
            candidate ::= "{" ws "\"name\"" ws ":" ws "\"" name "\"" ws "," ws "\"probability\"" ws ":" number ws "}"
            name ::= $alternatives
            number ::= digit+ ("." digit+)?
            digit ::= [0-9]
            ws ::= [ \t\n\r]*
        """.trimIndent() + "\n"
        }
    }

    fun findExact(name: String): PokemonRecord? = recordsByName[name]

    fun indexOf(name: String): Int? = indexesByName[name]

    fun recordAt(index: Int): PokemonRecord = records[index.mod(records.size)]

    companion object {
        fun load(context: Context): PokemonCatalog {
            val json = context.assets.open("pokemon/catalog.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return fromJson(json)
        }

        fun fromJson(json: String): PokemonCatalog {
            val entries = JSONObject(json).getJSONArray("records")
            val records = buildList(entries.length()) {
                repeat(entries.length()) { index ->
                    val entry = entries.getJSONObject(index)
                    val typesJson = entry.getJSONArray("types")
                    add(
                        PokemonInfo(
                            key = entry.getString("key"),
                            id = entry.getString("id"),
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
                            ability = entry.optString("ability", ""),
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
                        ),
                    )
                }
            }
            return PokemonCatalog(records)
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
