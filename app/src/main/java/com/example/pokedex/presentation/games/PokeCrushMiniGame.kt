package com.example.pokedex.presentation.games

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import com.example.pokedex.data.scanner.ResourceBundleRepository
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pokedex.data.scanner.AppSoundEffect
import com.example.pokedex.presentation.scanner.GamePokemon
import com.example.pokedex.presentation.scanner.PokemonRegionSelector
import com.example.pokedex.presentation.scanner.regionalCandidates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

private const val CRUSH_WIDTH = 8
private const val CRUSH_CELLS = CRUSH_WIDTH * CRUSH_WIDTH
private const val CRUSH_KINDS = 7

internal object PokeCrushMiniGame {
    val definition = MiniGameDefinition(
        id = MiniGameId.PokeCrush,
        enabled = true,
        title = "宝可梦消消乐",
        description = "交换相邻宝可梦，连成三个或更多即可消除",
        imageAsset = "game/poke_crush_icon.jpg",
        menuTestTag = "game_menu_poke_crush",
    ) { host -> PokeCrushGame(host) }
}

@Composable
private fun PokeCrushGame(host: MiniGameHost) {
    val candidates = remember(host.settings) { regionalCandidates(host.index, host.regionIndex, host.settings) }
    var pieces by remember { mutableStateOf<List<GamePokemon>?>(null) }
    if (pieces == null) {
        PokemonRegionSelector(
            regionIndex = host.regionIndex,
            settings = host.settings,
            candidateCount = candidates.size,
            onSettingsChanged = host.onSettingsChanged,
            onBack = host.onBackToMenu,
            onStart = { pieces = candidates.shuffled().take(CRUSH_KINDS) },
            onPlaySound = host.onPlaySound,
            minimumCandidates = CRUSH_KINDS,
        )
        return
    }
    PokeCrushBoard(host, pieces!!)
}

@Composable
private fun PokeCrushBoard(host: MiniGameHost, pieces: List<GamePokemon>) {
    val context = LocalContext.current
    val artwork = remember(context, pieces) { loadCrushArtwork(context, pieces) }
    val scope = rememberCoroutineScope()
    var board by remember(pieces) { mutableStateOf(newCrushBoard()) }
    var score by remember(pieces) { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var resolving by remember { mutableStateOf(false) }

    fun resolveSwap(first: Int, second: Int) {
        if (resolving || !areAdjacent(first, second)) return
        scope.launch {
            val before = board
            val swapped = before.swap(first, second)
            board = swapped
            selected = null
            delay(110)
            if (findCrushMatches(swapped).isEmpty()) {
                board = before
                return@launch
            }
            resolving = true
            var next = swapped
            while (true) {
                val matches = findCrushMatches(next)
                if (matches.isEmpty()) break
                score += matches.size
                next = collapseCrushBoard(next, matches)
                board = next
                delay(170)
            }
            if (!hasCrushMove(next)) board = newCrushBoard()
            resolving = false
        }
    }

    fun selectCell(index: Int) {
        if (resolving) return
        val previous = selected
        when {
            previous == null -> selected = index
            previous == index -> selected = null
            areAdjacent(previous, index) -> resolveSwap(previous, index)
            else -> selected = index
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("poke_crush_game")) {
        PokeCrushBackground(wide = maxWidth > maxHeight)
        val wide = maxWidth > maxHeight
        val side = if (wide) minOf(maxHeight - 28.dp, maxWidth * 0.65f, 520.dp)
        else minOf(maxWidth - 24.dp, maxHeight - 176.dp, 520.dp)
        val boardContent: @Composable () -> Unit = {
            CrushBoard(board, artwork, selected, !resolving, side.coerceAtLeast(150.dp), ::selectCell, ::resolveSwap)
        }
        val controls: @Composable () -> Unit = {
            CrushControls(
                score = score,
                onRestart = {
                    if (!resolving) {
                        board = newCrushBoard()
                        score = 0
                        selected = null
                    }
                },
                onExit = host.onBackToMenu,
                modifier = if (wide) Modifier.widthIn(min = 132.dp, max = 220.dp) else Modifier.fillMaxWidth(),
            )
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                boardContent(); controls()
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                controls(); boardContent()
            }
        }
    }
}

@Composable
private fun PokeCrushBackground(wide: Boolean) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color(0xFFB7DDED)),
        factory = {
            ImageView(it).apply {
                setImageDrawable(runCatching {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets, "game/poke_crush/fondo.gif"))
                }.getOrNull())
                (drawable as? AnimatedImageDrawable)?.start()
            }
        },
        update = { view ->
            view.scaleType = if (wide) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            (view.drawable as? AnimatedImageDrawable)?.start()
        },
    )
}

@Composable
private fun CrushControls(score: Int, onRestart: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("宝可梦消消乐", color = Color(0xFF1F355E), fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text("本局分数 $score", color = Color(0xFFCD5B12), fontSize = 17.sp, fontWeight = FontWeight.Black)
        Button(onClick = onRestart, modifier = Modifier.testTag("poke_crush_restart")) { Text("重新开始") }
        Button(onClick = onExit, modifier = Modifier.testTag("poke_crush_exit")) { Text("返回菜单") }
    }
}

@Composable
private fun CrushBoard(board: List<Int>, artwork: Map<Int, androidx.compose.ui.graphics.ImageBitmap>, selected: Int?, enabled: Boolean, size: Dp, onTap: (Int) -> Unit, onSwipe: (Int, Int) -> Unit) {
    Column(Modifier.size(size).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(Color(0xFFE8F7FF)).border(2.dp, Color(0xFF3E87C7), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(3.dp)) {
        repeat(CRUSH_WIDTH) { row -> Row(Modifier.fillMaxWidth().weight(1f)) {
            repeat(CRUSH_WIDTH) { column ->
                val index = row * CRUSH_WIDTH + column
                CrushTile(artwork.getValue(board[index]), selected == index, enabled, Modifier.weight(1f).fillMaxSize(), { onTap(index) }) { direction ->
                    neighborFor(index, direction)?.let { onSwipe(index, it) }
                }
            }
        } }
    }
}

private enum class CrushDirection { Left, Up, Right, Down }

@Composable
private fun CrushTile(image: androidx.compose.ui.graphics.ImageBitmap, selected: Boolean, enabled: Boolean, modifier: Modifier, onTap: () -> Unit, onSwipe: (CrushDirection) -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, tween(120), label = "crush selection")
    var dx = 0f; var dy = 0f
    Box(modifier.padding(1.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)).background(if (selected) Color(0xFFFFF1A1) else Color.White).border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFFF9F1C) else Color(0xFFAFCBE1), androidx.compose.foundation.shape.RoundedCornerShape(5.dp)).graphicsLayer(scaleX = scale, scaleY = scale).clickable(enabled = enabled, onClick = onTap).pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectDragGestures(onDragStart = { dx = 0f; dy = 0f }, onDrag = { change, amount -> change.consume(); dx += amount.x; dy += amount.y }, onDragEnd = {
            if (maxOf(abs(dx), abs(dy)) >= 18f) onSwipe(if (abs(dx) > abs(dy)) if (dx < 0f) CrushDirection.Left else CrushDirection.Right else if (dy < 0f) CrushDirection.Up else CrushDirection.Down)
        })
    }, contentAlignment = Alignment.Center) {
        Image(image, null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
    }
}

private fun loadCrushArtwork(context: Context, pieces: List<GamePokemon>): Map<Int, androidx.compose.ui.graphics.ImageBitmap> =
    pieces.mapIndexed { kind, pokemon -> kind to ResourceBundleRepository(context).openAsset(pokemon.record.imageAsset).use(BitmapFactory::decodeStream).asImageBitmap() }.toMap()

internal fun newCrushBoard(random: Random = Random.Default): List<Int> {
    val board = MutableList(CRUSH_CELLS) { 0 }
    for (index in board.indices) {
        val excluded = buildSet {
            if (index % CRUSH_WIDTH >= 2 && board[index - 1] == board[index - 2]) add(board[index - 1])
            if (index / CRUSH_WIDTH >= 2 && board[index - CRUSH_WIDTH] == board[index - CRUSH_WIDTH * 2]) add(board[index - CRUSH_WIDTH])
        }
        board[index] = (0 until CRUSH_KINDS).filterNot(excluded::contains).random(random)
    }
    return if (hasCrushMove(board)) board else newCrushBoard(random)
}

internal fun findCrushMatches(board: List<Int>): Set<Int> {
    val matches = mutableSetOf<Int>()
    repeat(CRUSH_WIDTH) { row -> var start = 0; while (start < CRUSH_WIDTH) { var end = start + 1; while (end < CRUSH_WIDTH && board[row * CRUSH_WIDTH + end] == board[row * CRUSH_WIDTH + start]) end++; if (end - start >= 3) for (column in start until end) matches += row * CRUSH_WIDTH + column; start = end } }
    repeat(CRUSH_WIDTH) { column -> var start = 0; while (start < CRUSH_WIDTH) { var end = start + 1; while (end < CRUSH_WIDTH && board[end * CRUSH_WIDTH + column] == board[start * CRUSH_WIDTH + column]) end++; if (end - start >= 3) for (row in start until end) matches += row * CRUSH_WIDTH + column; start = end } }
    return matches
}

internal fun collapseCrushBoard(board: List<Int>, removed: Set<Int>, random: Random = Random.Default): List<Int> {
    val result = MutableList(CRUSH_CELLS) { 0 }
    repeat(CRUSH_WIDTH) { column ->
        val remaining = (0 until CRUSH_WIDTH).map { row -> row * CRUSH_WIDTH + column }.filterNot(removed::contains).map(board::get)
        (List(CRUSH_WIDTH - remaining.size) { random.nextInt(CRUSH_KINDS) } + remaining).forEachIndexed { row, kind -> result[row * CRUSH_WIDTH + column] = kind }
    }
    return result
}

internal fun hasCrushMove(board: List<Int>): Boolean = board.indices.any { index -> listOf(index + 1, index + CRUSH_WIDTH).any { target -> target in board.indices && areAdjacent(index, target) && findCrushMatches(board.swap(index, target)).isNotEmpty() } }
private fun areAdjacent(first: Int, second: Int): Boolean = first in 0 until CRUSH_CELLS && second in 0 until CRUSH_CELLS && (abs(first - second) == CRUSH_WIDTH || first / CRUSH_WIDTH == second / CRUSH_WIDTH && abs(first - second) == 1)
private fun neighborFor(index: Int, direction: CrushDirection): Int? = when (direction) { CrushDirection.Left -> index.takeIf { it % CRUSH_WIDTH > 0 }?.minus(1); CrushDirection.Right -> index.takeIf { it % CRUSH_WIDTH < CRUSH_WIDTH - 1 }?.plus(1); CrushDirection.Up -> index.takeIf { it >= CRUSH_WIDTH }?.minus(CRUSH_WIDTH); CrushDirection.Down -> index.takeIf { it < CRUSH_CELLS - CRUSH_WIDTH }?.plus(CRUSH_WIDTH) }
private fun List<Int>.swap(first: Int, second: Int): List<Int> = toMutableList().also { values -> val held = values[first]; values[first] = values[second]; values[second] = held }
