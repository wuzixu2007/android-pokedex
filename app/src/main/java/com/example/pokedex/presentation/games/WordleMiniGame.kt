package com.example.pokedex.presentation.games

import com.example.pokedex.presentation.scanner.RegionalWordleGame

internal object WordleMiniGame {
    val definition = MiniGameDefinition(
        id = MiniGameId.Wordle,
        enabled = true,
        title = "宝可梦猜猜乐（积分）",
        description = "根据属性、种族值、世代、特性、进化和标签猜出目标",
        imageAsset = "game/guess.jpg",
        menuTestTag = "game_menu_wordle",
    ) { host ->
        RegionalWordleGame(
            index = host.index,
            regionIndex = host.regionIndex,
            settings = host.settings,
            onSettingsChanged = host.onSettingsChanged,
            scoreStore = host.scoreStore,
            onScoreChanged = host.onScoreChanged,
            onBack = host.onBackToMenu,
            onPlaySound = host.onPlaySound,
            onSpeakText = host.onSpeakText,
        )
    }
}
