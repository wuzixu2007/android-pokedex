package com.example.pokedex.presentation.games

import com.example.pokedex.presentation.scanner.WeightGame

internal object WeightMiniGame {
    val definition = MiniGameDefinition(
        id = MiniGameId.Weight,
        enabled = true,
        title = "宝可梦猜体重",
        description = "十题内判断两只宝可梦中谁的体重更大",
        imageAsset = "game/weight.jpg",
        menuTestTag = "game_menu_weight",
    ) { host ->
        WeightGame(host.index, host.onBackToMenu, host.onPlaySound)
    }
}
