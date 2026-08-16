package com.example.pokedex

import com.example.pokedex.domain.scanner.PokemonTypeChart
import org.junit.Assert.assertEquals
import org.junit.Test

class PokemonTypeChartTest {
    @Test fun dualTypeWeaknessesAreMultiplied() {
        val rates = PokemonTypeChart.incoming(listOf("龙", "地面")).associate { it.attackType to it.multiplier }
        assertEquals(4f, rates.getValue("冰"))
        assertEquals(2f, rates.getValue("妖精"))
    }

    @Test fun immunityOverridesOtherTypeMultiplier() {
        val rates = PokemonTypeChart.incoming(listOf("水", "飞行")).associate { it.attackType to it.multiplier }
        assertEquals(4f, rates.getValue("电"))
        assertEquals(0f, rates.getValue("地面"))
    }

    @Test fun quarterResistanceIsCalculated() {
        val rates = PokemonTypeChart.incoming(listOf("火", "飞行")).associate { it.attackType to it.multiplier }
        assertEquals(0.25f, rates.getValue("草"))
    }
}
