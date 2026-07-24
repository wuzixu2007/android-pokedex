/* Android candidate-protocol coverage. / Android 候选协议覆盖测试。 */
package com.example.pokedex

import com.example.pokedex.ui.scanner.PokemonCandidateProtocol
import com.example.pokedex.ui.scanner.PokemonCatalog
import com.example.pokedex.ui.scanner.PokemonRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonCandidateProtocolTest {
    private val catalog = PokemonCatalog.fromRecords(
        List(5) { index ->
            PokemonRecord(
                key = "p$index",
                id = index.toString().padStart(4, '0'),
                nameZh = "Pokemon ${index + 1}",
                nameEn = "Pokemon ${index + 1}",
                types = listOf("一般"),
                attributeLabel = "一般属性宝可梦",
                category = "宝可梦",
                height = "1.0m",
                weight = "1.0kg",
                description = "",
                profile = "Profile",
                imageAsset = "",
            )
        },
    )

    @Test
    fun validJson_isSortedAndConvertedToFraction() {
        val result = PokemonCandidateProtocol.parse(
            """
            [{"name":"Pokemon 2","probability":10.0},{"name":"Pokemon 1","probability":94.8},{"name":"Pokemon 3","probability":1.2},{"name":"Pokemon 4","probability":0.8},{"name":"Pokemon 5","probability":0.2}]
            """.trimIndent(),
            catalog,
        )

        assertEquals(listOf("Pokemon 1", "Pokemon 2", "Pokemon 3", "Pokemon 4", "Pokemon 5"), result.map { it.standardName })
        assertEquals(0.948f, result.first().probability)
    }

    @Test
    fun singleMode_acceptsExactlyOneCandidate() {
        val result = PokemonCandidateProtocol.parse(
            raw = "[{\"name\":\"Pokemon 1\",\"probability\":94.8}]",
            catalog = catalog,
            expectedCount = 1,
        )

        assertEquals(1, result.size)
        assertEquals("Pokemon 1", result.single().standardName)
        assertTrue(
            runCatching {
                PokemonCandidateProtocol.parse(
                    raw = "[{\"name\":\"Pokemon 1\",\"probability\":94.8},{\"name\":\"Pokemon 2\",\"probability\":2.0}]",
                    catalog = catalog,
                    expectedCount = 1,
                )
            }.isFailure,
        )
    }

    @Test
    fun malformedJson_isRejected() {
        val invalid = listOf(
            "[]",
            "[{\"name\":\"Pokemon 1\",\"probability\":100}]",
            "[{\"name\":\"Unknown\",\"probability\":20}]",
            "[{\"name\":\"Pokemon 1\",\"probability\":-1}]",
            "[{\"name\":\"Pokemon 1\",\"probability\":1,\"extra\":true}]",
            "说明：[]",
        )

        invalid.forEach { raw ->
            val rejected = runCatching { PokemonCandidateProtocol.parse(raw, catalog) }.isFailure
            assertTrue(raw, rejected)
        }
    }
}
