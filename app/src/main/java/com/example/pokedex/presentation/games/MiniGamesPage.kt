package com.example.pokedex.presentation.games

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pokedex.data.scanner.AppSoundEffect
import com.example.pokedex.data.scanner.PokemonCatalog
import com.example.pokedex.presentation.scanner.GameIndex
import com.example.pokedex.presentation.scanner.GameRegionIndex
import com.example.pokedex.presentation.scanner.GameSettingsRepository
import com.example.pokedex.presentation.scanner.SharedPreferencesScoreRepository
import com.example.pokedex.data.scanner.ResourceBundleRepository
import kotlinx.coroutines.delay

@Composable
internal fun MiniGamesPage(
    catalog: PokemonCatalog,
    onPlaySound: (AppSoundEffect) -> Unit = {},
    onSpeakText: (String) -> Unit = {},
    onScoreChanged: (Int) -> Unit = {},
    registry: MiniGameRegistry = defaultMiniGameRegistry,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val index = remember(catalog) { GameIndex(context, catalog) }
    val regionIndex = remember { GameRegionIndex(context) }
    val settingsStore = remember { GameSettingsRepository(context) }
    val scoreStore = remember { SharedPreferencesScoreRepository(context) }
    var settings by remember { mutableStateOf(settingsStore.load(regionIndex.allIds)) }
    var selectedGame by remember { mutableStateOf<MiniGameId?>(null) }
    var loadingGame by remember { mutableStateOf<MiniGameId?>(null) }
    val selectedDefinition = selectedGame?.let(registry::enabledGame)

    LaunchedEffect(loadingGame) {
        val destination = loadingGame ?: return@LaunchedEffect
        delay(220)
        if (registry.enabledGame(destination) != null) selectedGame = destination
        loadingGame = null
    }

    // A disabled or removed game is never composed, including after a registry change.
    LaunchedEffect(selectedGame, selectedDefinition) {
        if (selectedGame != null && selectedDefinition == null) selectedGame = null
    }

    val host = MiniGameHost(
        catalog = catalog,
        index = index,
        regionIndex = regionIndex,
        settings = settings,
        onSettingsChanged = { updated ->
            settings = updated
            settingsStore.save(updated)
        },
        scoreStore = scoreStore,
        onScoreChanged = onScoreChanged,
        onPlaySound = onPlaySound,
        onSpeakText = onSpeakText,
        onBackToMenu = { selectedGame = null },
    )

    Box(modifier = modifier.fillMaxSize().padding(8.dp)) {
        selectedDefinition?.let { game ->
            game.content(host)
        } ?: loadingGame?.let {
            MiniGameLoading()
        } ?: MiniGameMenu(registry.enabledGames) { game ->
            onPlaySound(AppSoundEffect.Interaction)
            loadingGame = game.id
        }
    }
}

@Composable
private fun MiniGameLoading() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("mini_game_loading"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("正在准备游戏", fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 28.dp))
    }
}

@Composable
private fun MiniGameMenu(games: List<MiniGameDefinition>, onOpen: (MiniGameDefinition) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("pokemon_games_menu"),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(games, key = { it.id }) { game ->
            MiniGameMenuCard(game, onClick = { onOpen(game) })
        }
    }
}

@Composable
private fun MiniGameMenuCard(game: MiniGameDefinition, onClick: () -> Unit) {
    val iconSize = 56.dp * game.menuIconScale.coerceIn(0.75f, 1.2f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxOf(89.dp, iconSize + 24.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE9E7EF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(game.menuTestTag)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MiniGameMenuArt(game.imageAsset, game.title, Modifier.size(iconSize))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(game.title, color = Color(0xFF17151C), fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Text(game.description, color = Color(0xFF55515B), fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MiniGameMenuArt(asset: String?, description: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, asset) {
        value = asset?.let { path -> runCatching { ResourceBundleRepository(context).openAsset(path).use(BitmapFactory::decodeStream) }.getOrNull() }
    }
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF3F1F7)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
