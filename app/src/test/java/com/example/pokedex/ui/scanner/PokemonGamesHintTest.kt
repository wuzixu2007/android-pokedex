package com.example.pokedex.presentation.scanner

import com.example.pokedex.data.scanner.*
import com.example.pokedex.domain.scanner.*

import org.junit.Assert.assertEquals
import org.junit.Test

class PokemonGamesHintTest {
    @Test
    fun exactSetIsMatch() {
        assertEquals(HintColor.Match, setHint(listOf("冰"), listOf("冰")))
        assertEquals(HintColor.Match, setHint(listOf("龙", "冰"), listOf("冰", "龙")))
    }

    @Test
    fun overlappingSetIsClose() {
        assertEquals(HintColor.Close, setHint(listOf("龙", "冰"), listOf("冰")))
        assertEquals(HintColor.Close, setHint(listOf("漂浮", "压迫感"), listOf("压迫感", "静电")))
    }

    @Test
    fun disjointSetIsMiss() {
        assertEquals(HintColor.Miss, setHint(listOf("龙"), listOf("冰")))
    }
}
