package com.example.pokedex.presentation.games

/**
 * The only place where games are enabled and ordered.
 * Add a game file, then register its definition here to expose it in the app.
 */
internal class MiniGameRegistry(games: List<MiniGameDefinition>) {
    val games: List<MiniGameDefinition> = games.toList()
    val enabledGames: List<MiniGameDefinition> get() = games.filter(MiniGameDefinition::enabled)

    fun enabledGame(id: MiniGameId): MiniGameDefinition? =
        enabledGames.firstOrNull { it.id == id }
}

internal val defaultMiniGameRegistry = MiniGameRegistry(
    listOf(
        WordleMiniGame.definition.copy(enabled = true),
        WhoAmIMiniGame.definition.copy(enabled = false),
        PokeCrushMiniGame.definition.copy(enabled = true),
        WeightMiniGame.definition.copy(enabled = true),
        TypesMiniGame.definition.copy(enabled = true),
    ),
)
