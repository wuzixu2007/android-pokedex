package com.example.pokedex.presentation.games

import androidx.compose.runtime.Composable
import com.example.pokedex.data.scanner.AppSoundEffect
import com.example.pokedex.data.scanner.PokemonCatalog
import com.example.pokedex.presentation.scanner.GameIndex
import com.example.pokedex.presentation.scanner.GameRegionIndex
import com.example.pokedex.presentation.scanner.GameStartSettings
import com.example.pokedex.presentation.scanner.ScoreRepository

/** Stable key used by the registry instead of routing directly to a composable. */
internal enum class MiniGameId { Wordle, Weight, Types, WhoAmI, PokeCrush }

internal data class MiniGameDefinition(
    val id: MiniGameId,
    val enabled: Boolean,
    val title: String,
    val description: String,
    val imageAsset: String?,
    val menuTestTag: String,
    val menuIconScale: Float = 1f,
    val content: @Composable (MiniGameHost) -> Unit,
)

/** Dependencies supplied by the scanner host to every independently registered game. */
internal data class MiniGameHost(
    val catalog: PokemonCatalog,
    val index: GameIndex,
    val regionIndex: GameRegionIndex,
    val settings: GameStartSettings,
    val onSettingsChanged: (GameStartSettings) -> Unit,
    val scoreStore: ScoreRepository,
    val onScoreChanged: (Int) -> Unit,
    val onPlaySound: (AppSoundEffect) -> Unit,
    val onSpeakText: (String) -> Unit,
    val onBackToMenu: () -> Unit,
)
