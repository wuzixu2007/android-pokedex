package com.example.pokedex.presentation.games

import com.example.pokedex.presentation.scanner.TypeGame

internal object TypesMiniGame {
    val definition = MiniGameDefinition(
        id = MiniGameId.Types,
        enabled = true,
        title = "宝可梦猜属性",
        description = "根据完整属性组合，从四只宝可梦中选出正确目标",
        imageAsset = "game/types.jpg",
        menuTestTag = "game_menu_types",
    ) { host ->
        TypeGame(host.index, host.onBackToMenu, host.onPlaySound)
    }
}
