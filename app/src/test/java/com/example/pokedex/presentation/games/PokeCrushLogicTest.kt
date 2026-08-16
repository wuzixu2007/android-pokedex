package com.example.pokedex.presentation.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PokeCrushLogicTest {
    @Test
    fun newBoard_hasNoImmediateMatchesAndHasAMove() {
        repeat(20) {
            val board = newCrushBoard(Random(it))
            assertTrue(findCrushMatches(board).isEmpty())
            assertTrue(hasCrushMove(board))
        }
    }

    @Test
    fun findsAndCollapsesVerticalMatch() {
        val board = MutableList(64) { it % 7 }
        board[0] = 6
        board[8] = 6
        board[16] = 6

        val matches = findCrushMatches(board)
        assertTrue(setOf(0, 8, 16).all(matches::contains))

        val collapsed = collapseCrushBoard(board, matches, Random(4))
        assertEquals(board[24], collapsed[24])
        assertEquals(board[32], collapsed[32])
    }
}
