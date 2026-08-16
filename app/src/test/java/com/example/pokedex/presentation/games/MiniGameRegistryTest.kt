package com.example.pokedex.presentation.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniGameRegistryTest {
    @Test
    fun defaultGames_includeOnlyEnabledEntriesInStableMenuOrder() {
        assertEquals(
            listOf(MiniGameId.Wordle, MiniGameId.PokeCrush, MiniGameId.Weight, MiniGameId.Types),
            defaultMiniGameRegistry.enabledGames.map { it.id },
        )
    }

    @Test
    fun disabledGame_isNotResolvableForRendering() {
        val registry = MiniGameRegistry(
            listOf(WordleMiniGame.definition.copy(enabled = false)),
        )

        assertEquals(emptyList<MiniGameDefinition>(), registry.enabledGames)
        assertNull(registry.enabledGame(MiniGameId.Wordle))
    }

    @Test
    fun whoAmI_scoreDecreasesWithEachIncorrectChoice() {
        assertEquals(30, whoAmIScoreForIncorrectCount(0))
        assertEquals(15, whoAmIScoreForIncorrectCount(1))
        assertEquals(5, whoAmIScoreForIncorrectCount(2))
    }
}
