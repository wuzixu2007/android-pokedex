package com.example.pokedex

import com.example.pokedex.data.scanner.PokemonCatalog
import com.example.pokedex.data.scanner.PokemonRecord
import com.example.pokedex.domain.scanner.PokemonCandidateProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonCandidateProtocolTest {
    private val catalog = PokemonCatalog.fromRecords(listOf(record("皮卡丘"), record("阿罗拉小拉达")))

    @Test fun validObject_isConvertedToFractionAndKeepsShiny() {
        val result = PokemonCandidateProtocol.parse(
            "{\"name\":\"皮卡丘\",\"probability\":94.8,\"isShiny\":true}", catalog,
        )
        assertEquals("皮卡丘", result.standardName)
        assertEquals(0.948f, result.probability)
        assertTrue(result.isShiny)
    }

    @Test fun markdownFence_isAccepted() {
        val result = PokemonCandidateProtocol.parse(
            "```json\n{\"name\":\"皮卡丘\",\"probability\":80,\"isShiny\":false}\n```", catalog,
        )
        assertFalse(result.isShiny)
    }

    @Test fun malformedObjects_areRejected() {
        listOf(
            "[]",
            "{\"name\":\"皮卡丘\",\"probability\":100}",
            "{\"name\":\"Unknown\",\"probability\":20,\"isShiny\":false}",
            "{\"name\":\"皮卡丘\",\"probability\":-1,\"isShiny\":false}",
            "{\"name\":\"皮卡丘\",\"probability\":1,\"isShiny\":\"false\"}",
            "{\"name\":\"皮卡丘\",\"probability\":1,\"isShiny\":false,\"extra\":true}",
        ).forEach { assertTrue(it, runCatching { PokemonCandidateProtocol.parse(it, catalog) }.isFailure) }
    }

    private fun record(name: String) = PokemonRecord(
        key = name, id = "0001", nameZh = name, nameEn = name, types = listOf("一般"),
        attributeLabel = "一般属性宝可梦", category = "宝可梦", height = "1.0m", weight = "1.0kg",
        description = "", profile = "", imageAsset = "",
    )
}
