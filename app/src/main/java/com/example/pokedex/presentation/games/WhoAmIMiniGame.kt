package com.example.pokedex.presentation.games

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import com.example.pokedex.data.scanner.ResourceBundleRepository
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pokedex.R
import com.example.pokedex.data.scanner.AppSoundEffect
import com.example.pokedex.data.scanner.PokemonRecord
import com.example.pokedex.presentation.scanner.GamePokemon
import com.example.pokedex.presentation.scanner.GameStartSettings
import com.example.pokedex.presentation.scanner.PokemonRegionSelector
import com.example.pokedex.presentation.scanner.regionalCandidates
import kotlin.math.ceil

private enum class WhoAmIPhase { Setup, Intro, Playing, Reveal }

internal fun whoAmIScoreForIncorrectCount(incorrectCount: Int): Int = when (incorrectCount) {
    0 -> 30
    1 -> 15
    else -> 5
}

internal object WhoAmIMiniGame {
    val definition = MiniGameDefinition(
        id = MiniGameId.WhoAmI,
        enabled = true,
        title = "猜猜我是谁",
        description = "看剪影，从四个名字中找出正确的宝可梦",
        imageAsset = "game/who_am_i_icon.jpg",
        menuTestTag = "game_menu_who_am_i",
        menuIconScale = 1.2f,
    ) { host ->
        WhoAmIGame(host)
    }
}

@Composable
private fun WhoAmIGame(host: MiniGameHost) {
    var phase by remember { mutableStateOf(WhoAmIPhase.Setup) }
    var target by remember { mutableStateOf<GamePokemon?>(null) }
    var options by remember { mutableStateOf(emptyList<GamePokemon>()) }
    var incorrectKeys by remember { mutableStateOf(emptySet<String>()) }
    var scoreDelta by remember { mutableStateOf<Int?>(null) }
    val candidates = remember(host.settings) { regionalCandidates(host.index, host.regionIndex, host.settings) }

    fun beginRound() {
        val nextTarget = candidates.random()
        target = nextTarget
        options = (candidates.filterNot { it.record.key == nextTarget.record.key }.shuffled().take(3) + nextTarget).shuffled()
        incorrectKeys = emptySet()
        scoreDelta = null
        phase = WhoAmIPhase.Intro
    }

    when (phase) {
        WhoAmIPhase.Setup -> PokemonRegionSelector(
            regionIndex = host.regionIndex,
            settings = host.settings,
            candidateCount = candidates.size,
            onSettingsChanged = host.onSettingsChanged,
            onBack = host.onBackToMenu,
            onStart = ::beginRound,
            onPlaySound = host.onPlaySound,
            minimumCandidates = 4,
        )
        WhoAmIPhase.Intro,
        WhoAmIPhase.Playing,
        WhoAmIPhase.Reveal,
        -> {
            val answer = target ?: return
            WhoAmIPlayArea(
                target = answer,
                options = options,
                incorrectKeys = incorrectKeys,
                opening = phase == WhoAmIPhase.Intro,
                revealing = phase == WhoAmIPhase.Reveal,
                scoreDelta = scoreDelta,
                onSelect = { choice ->
                    if (phase != WhoAmIPhase.Playing || choice.record.key in incorrectKeys) return@WhoAmIPlayArea
                    if (choice.record.key == answer.record.key) {
                        val delta = whoAmIScoreForIncorrectCount(incorrectKeys.size)
                        scoreDelta = delta
                        host.onScoreChanged(host.scoreStore.apply(delta))
                        host.onPlaySound(AppSoundEffect.GameCorrect)
                        phase = WhoAmIPhase.Reveal
                    } else {
                        val updated = incorrectKeys + choice.record.key
                        incorrectKeys = updated
                        host.onPlaySound(AppSoundEffect.GameIncorrect)
                        if (updated.size == 3) {
                            scoreDelta = 0
                            phase = WhoAmIPhase.Reveal
                        }
                    }
                },
                onNext = { phase = WhoAmIPhase.Setup },
                onBack = host.onBackToMenu,
                onOpeningFinished = { phase = WhoAmIPhase.Playing },
            )
        }
    }
}

@Composable
private fun WhoAmISetup(
    settings: GameStartSettings,
    candidateCount: Int,
    onSettingsChanged: (GameStartSettings) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("who_am_i_setup"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("猜猜我是谁", fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("选择本局可出现的宝可梦地区", color = Color(0xFF55515B))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSettingsChanged(settings.copy(regions = emptySet())) }) { Text("全不选") }
                Button(onClick = {
                    val regions = ResourceBundleRepository(context).openAsset("pokemon/game_regions.json").use { input ->
                        org.json.JSONObject(input.bufferedReader().readText()).getJSONArray("regions").let { values ->
                            buildSet { repeat(values.length()) { add(values.getJSONObject(it).getString("id")) } }
                        }
                    }
                    onSettingsChanged(settings.copy(regions = regions))
                }) { Text("全选") }
            }
        }
        items(settings.regions.toList().sorted()) { regionId ->
            Text(regionId)
        }
        item {
            RegionToggleList(settings, onSettingsChanged)
        }
        item {
            Text("可用宝可梦：$candidateCount", fontWeight = FontWeight.Bold)
            if (candidateCount < 4) Text("至少选择一个拥有四只可用宝可梦的地区。", color = Color(0xFFC62828))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onBack) { Text("返回菜单") }
                Button(onClick = onStart, enabled = candidateCount >= 4, modifier = Modifier.testTag("who_am_i_start")) { Text("开始") }
            }
        }
    }
}

@Composable
private fun RegionToggleList(settings: GameStartSettings, onSettingsChanged: (GameStartSettings) -> Unit) {
    val context = LocalContext.current
    val regions = remember {
        ResourceBundleRepository(context).openAsset("pokemon/game_regions.json").use { input ->
            val values = org.json.JSONObject(input.bufferedReader().readText()).getJSONArray("regions")
            List(values.length()) { index -> values.getJSONObject(index).getString("id") to values.getJSONObject(index).getString("label") }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        regions.forEach { (id, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f))
                Switch(
                    checked = id in settings.regions,
                    onCheckedChange = { checked ->
                        onSettingsChanged(settings.copy(regions = if (checked) settings.regions + id else settings.regions - id))
                    },
                    modifier = Modifier.testTag("who_am_i_region_$id"),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("包含超级进化", Modifier.weight(1f))
            Switch(settings.includeMega, { onSettingsChanged(settings.copy(includeMega = it)) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("包含超极巨化", Modifier.weight(1f))
            Switch(settings.includeGigantamax, { onSettingsChanged(settings.copy(includeGigantamax = it)) })
        }
    }
}

@Composable
private fun WhoAmIOpeningAudio(onFinished: () -> Unit) {
    val context = LocalContext.current
    val latestOnFinished by rememberUpdatedState(onFinished)
    DisposableEffect(context) {
        var player: MediaPlayer? = null
        player = runCatching {
            val created = MediaPlayer()
            try {
                created.apply {
                setDataSource(ResourceBundleRepository(context).file("raw/who_am_i_intro_sound.wav").absolutePath)
                setOnPreparedListener { prepared ->
                    runCatching { prepared.start() }.onFailure { latestOnFinished() }
                }
                setOnCompletionListener { latestOnFinished() }
                setOnErrorListener { failed, _, _ ->
                    runCatching { failed.release() }
                    if (player === failed) player = null
                    latestOnFinished()
                    true
                }
                prepareAsync()
                }
                created
            } catch (error: Throwable) {
                created.runCatching { release() }
                throw error
            }
        }.getOrNull()
        if (player == null) latestOnFinished()
        onDispose {
            player?.runCatching { stop() }
            player?.runCatching { release() }
            player = null
        }
    }
}

@Composable
private fun WhoAmIPlayArea(
    target: GamePokemon,
    options: List<GamePokemon>,
    incorrectKeys: Set<String>,
    opening: Boolean,
    revealing: Boolean,
    scoreDelta: Int?,
    onSelect: (GamePokemon) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onOpeningFinished: () -> Unit,
) {
    val blackAlpha = remember { Animatable(1f) }
    val nameProgress = remember { Animatable(0f) }
    var revealComplete by remember { mutableStateOf(false) }
    LaunchedEffect(revealing) {
        if (revealing) {
            blackAlpha.animateTo(0f, tween(500, easing = LinearEasing))
            nameProgress.animateTo(1f, tween(200, easing = LinearEasing))
            revealComplete = true
        } else {
            blackAlpha.snapTo(1f)
            nameProgress.snapTo(0f)
            revealComplete = false
        }
    }
    val visibleName = target.record.nameZh.take(ceil(target.record.nameZh.length * nameProgress.value).toInt())
    BoxWithConstraints(Modifier.fillMaxSize().testTag("who_am_i_play")) {
        if (opening) WhoAmIOpeningAudio(onOpeningFinished)
        val wide = maxWidth > 620.dp
        WhoAmIBoardBackground()
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (wide) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    SilhouetteFrame(target.record, blackAlpha.value, opening, Modifier.size(230.dp))
                    RevealName(visibleName, revealing, Modifier.weight(1f))
                }
            } else {
                SilhouetteFrame(target.record, blackAlpha.value, opening, Modifier.fillMaxWidth().height(215.dp).align(Alignment.CenterHorizontally))
                RevealName(visibleName, revealing, Modifier.fillMaxWidth())
            }
            Text("从四个名字中选择正确答案", color = Color.White, fontWeight = FontWeight.Black)
            options.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { option ->
                        val disabled = opening || revealing || option.record.key in incorrectKeys
                        Button(
                            onClick = { onSelect(option) },
                            enabled = !disabled,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("who_am_i_option_${option.record.key}"),
                        ) { Text(option.record.nameZh, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            scoreDelta?.let { delta ->
                Text(if (delta > 0) "+$delta 积分" else "答案揭晓", color = Color(0xFFFFD54F), fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onBack) { Text("返回菜单") }
                if (revealComplete) Button(onClick = onNext, modifier = Modifier.testTag("who_am_i_next")) { Text("下一题") }
            }
        }
    }
}

@Composable
private fun WhoAmIBoardBackground() {
    val context = LocalContext.current
    val board by produceState<android.graphics.Bitmap?>(null) {
        value = runCatching { ResourceBundleRepository(context).openAsset("game/who_am_i_board.jpg").use(BitmapFactory::decodeStream) }.getOrNull()
    }
    board?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
}

@Composable
private fun SilhouetteFrame(pokemon: PokemonRecord, blackAlpha: Float, opening: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, pokemon.imageAsset) {
        value = runCatching { ResourceBundleRepository(context).openAsset(pokemon.imageAsset).use(BitmapFactory::decodeStream) }.getOrNull()
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF54DD91)).border(4.dp, Color.White, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), pokemon.nameZh, Modifier.fillMaxSize().padding(14.dp), contentScale = ContentScale.Fit)
            if (blackAlpha > 0f) Image(
                it.asImageBitmap(),
                null,
                Modifier.fillMaxSize().padding(14.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = blackAlpha), BlendMode.SrcIn),
            )
            if (opening) WhoAmIIntroVideo()
        }
    }
}

@Composable
private fun WhoAmIIntroVideo() {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(true) }
    if (!visible) return
    AndroidView(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).testTag("who_am_i_intro"),
        factory = {
            VideoView(it).apply {
                setVideoURI(Uri.fromFile(ResourceBundleRepository(context).file("raw/who_am_i_intro_video.mp4")))
                setOnPreparedListener { player -> player.setVolume(0f, 0f); start() }
                setOnCompletionListener { visible = false }
                setOnErrorListener { _, _, _ -> visible = false; true }
            }
        },
    )
}

@Composable
private fun RevealName(name: String, revealing: Boolean, modifier: Modifier) {
    if (!revealing) return
    Text(
        text = name,
        modifier = modifier.widthIn(min = 80.dp),
        color = Color(0xFFFFD54F),
        fontSize = 30.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Start,
        maxLines = 2,
    )
}
