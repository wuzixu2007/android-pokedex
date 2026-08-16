package com.example.pokedex.presentation.scanner

import com.example.pokedex.data.scanner.*
import com.example.pokedex.domain.scanner.*

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.pokedex.R
import com.example.pokedex.ui.theme.ScannerBorder
import com.example.pokedex.ui.theme.ScannerCanvas
import com.example.pokedex.ui.theme.ScannerPanel
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

internal enum class GameScreen { Menu, Wordle, Weight, Types }
internal enum class HintColor { Match, Close, Miss }
internal enum class SpecialForm { Normal, Mega, Gigantamax }

internal data class GameRegion(val id: String, val label: String, val dexIds: Set<String>)
internal data class GameStartSettings(
    val regions: Set<String>,
    val includeMega: Boolean = false,
    val includeGigantamax: Boolean = false,
)

internal class GameRegionIndex(context: Context) {
    val regions: List<GameRegion> = ResourceBundleRepository(context).openAsset("pokemon/game_regions.json").bufferedReader(Charsets.UTF_8).use { reader ->
        val values = JSONObject(reader.readText()).getJSONArray("regions")
        List(values.length()) { index ->
            val region = values.getJSONObject(index)
            val ids = region.getJSONArray("dexIds")
            GameRegion(region.getString("id"), region.getString("label"), buildSet { repeat(ids.length()) { add(ids.getString(it)) } })
        }
    }
    val allIds = regions.map(GameRegion::id).toSet()
}

internal class GameSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("pokemon_game_settings", Context.MODE_PRIVATE)

    fun load(allRegions: Set<String>): GameStartSettings {
        val stored = prefs.getString("regions", null)
        return GameStartSettings(
            regions = stored?.split(',')?.filter(String::isNotBlank)?.toSet()?.intersect(allRegions) ?: allRegions,
            includeMega = prefs.getBoolean("include_mega", false),
            includeGigantamax = prefs.getBoolean("include_gigantamax", false),
        )
    }

    fun save(settings: GameStartSettings) {
        prefs.edit()
            .putString("regions", settings.regions.joinToString(","))
            .putBoolean("include_mega", settings.includeMega)
            .putBoolean("include_gigantamax", settings.includeGigantamax)
            .apply()
    }
}

internal data class GamePokemon(
    val record: PokemonRecord,
    val generation: Int,
    val totalStats: Int,
    val weightKg: Double,
    val tags: Set<String>,
    val evolutionStage: Int,
    val evolutionKind: String,
    val evolutionLabel: String,
    val specialForm: SpecialForm,
)

internal data class GuessFeedback(
    val pokemon: GamePokemon,
    val type: HintColor,
    val stats: HintColor,
    val statsDirection: String,
    val generation: HintColor,
    val abilities: List<Pair<String, HintColor>>,
    val evolution: HintColor,
    val tags: List<Pair<String, HintColor>>,
)

internal data class ScoreResult(val delta: Int, val message: String)

/** Local score store; the interface boundary permits a future remote replacement. */
internal interface ScoreRepository {
    fun score(): Int
    fun apply(delta: Int): Int
}

internal class SharedPreferencesScoreRepository(context: Context) : ScoreRepository {
    private val prefs = context.getSharedPreferences("pokemon_game_score", Context.MODE_PRIVATE)
    override fun score() = prefs.getInt("score", 100)
    override fun apply(delta: Int): Int {
        val updated = (score() + delta).coerceAtLeast(0)
        prefs.edit().putInt("score", updated).apply()
        return updated
    }
}

internal class GameIndex(private val context: Context, private val catalog: PokemonCatalog) {
    private val details = PokemonDetailsRepository(context)
    private val cache = mutableMapOf<String, GamePokemon>()

    fun get(record: PokemonRecord): GamePokemon = cache.getOrPut(record.key) {
        val detail = details.load(record)
        val evolution = detail.evolutions.firstOrNull { it.name == record.sourceFormName || it.name == record.nameZh }
        val condition = evolution?.condition.orEmpty()
        val hasPreviousEvolution = !evolution?.from.isNullOrBlank()
        val hasNextEvolution = detail.evolutions.any { it.from == record.sourceFormName || it.from == record.nameZh }
        val evolutionStage = when (evolution?.stage) { "幼年" -> 0; "未进化" -> 1; else -> 2 }
        GamePokemon(
            record = record,
            generation = generationFor(record.id.toIntOrNull() ?: 1),
            totalStats = record.stats.hp + record.stats.attack + record.stats.defense + record.stats.specialAttack + record.stats.specialDefense + record.stats.speed,
            weightKg = record.weight.removeSuffix("kg").toDoubleOrNull() ?: 0.0,
            tags = tagsFor(record, condition) + detail.battle.specialEvolutions.mapNotNull {
                when (it.kind) {
                    "超级进化" -> "有超级进化"
                    "超极巨化" -> "有超极巨化"
                    else -> null
                }
            },
            evolutionStage = evolutionStage,
            evolutionKind = evolutionKind(condition),
            evolutionLabel = if (!hasPreviousEvolution && !hasNextEvolution) "不可进化" else "第${evolutionStage + 1}进化形",
            specialForm = when {
                record.sourceFormName.contains("超极巨化") -> SpecialForm.Gigantamax
                record.sourceFormName.contains("超级") -> SpecialForm.Mega
                else -> SpecialForm.Normal
            },
        )
    }

    fun all() = catalog.records.map(::get)

    private fun generationFor(id: Int) = when (id) {
        in 1..151 -> 1; in 152..251 -> 2; in 252..386 -> 3; in 387..493 -> 4; in 494..649 -> 5
        in 650..721 -> 6; in 722..809 -> 7; in 810..905 -> 8; else -> 9
    }

    private fun evolutionKind(condition: String): String = when {
        condition.contains("提升等级") && condition.contains("亲密") -> "亲密度提升等级"
        condition.contains("提升等级") && condition.contains("招式") -> "学会招式提升等级"
        condition.contains("提升等级") -> "普通提升等级"
        condition.contains("使用") || condition.contains("之石") -> "使用道具"
        condition.contains("交换") -> "交换"
        condition.isBlank() -> "不进化/第一阶段"
        else -> "特殊进化"
    }

    private fun tagsFor(record: PokemonRecord, condition: String): Set<String> {
        val id = record.id.toIntOrNull() ?: 0
        val name = record.nameZh
        return buildSet {
            if (name.contains("阿罗拉") || name.contains("伽勒尔") || name.contains("洗翠") || name.contains("帕底亚")) add("有地区形态")
            if (name.contains("超级")) add("有超级进化")
            if (name.contains("超极巨化")) add("有超极巨化")
            if (condition.contains("等级5") || condition.contains("等级6") || condition.contains("等级7") || condition.contains("等级8") || condition.contains("等级9")) add("大器晚成")
            if (id in setOf(1,4,7,25,133,152,155,158,252,255,258,387,390,393,495,498,501,650,653,656,722,725,728,810,813,816,906,909,912)) add("最初的伙伴")
            if (id in 793..806) add("究极异兽")
            if (id in 1005..1020) add("悖论宝可梦")
            if (id in setOf(138,139,140,141,142,345,346,347,348,408,409,410,411,564,565,566,567,696,697,698,699,880,881,882,883)) add("化石宝可梦")
            if (id in setOf(172,173,174,175,236,238,239,240,298,360,406,433,438,439,440,446,447,458,848)) add("婴儿宝可梦")
            if (name.contains("雄") || name.contains("雌")) add("性别差异")
        }
    }
}

private fun scoreResult(attempt: Int, won: Boolean, random: Random = Random.Default): ScoreResult {
    val rule = if (!won) ScoreRule(-20..-10, listOf("皮卡丘，路边待着吧","你这皮卡丘有点路边","皮卡丘，别发电了","路边皮卡丘是吧","你是来当背景板的吗","皮卡丘，你安静点","这波纯纯路边了","皮卡丘，下次再努力","你不是皮卡丘，是路边丘","路边一条，别挣扎了")) else scoreRules[attempt] ?: scoreRules.getValue(10)
    return ScoreResult(random.nextInt(rule.range.first, rule.range.last + 1), rule.messages.random(random))
}

private fun regionalScoreResult(attempt: Int, won: Boolean, random: Random = Random.Default): ScoreResult {
    val normalizedAttempt = attempt.coerceIn(1, 10)
    val range = if (!won) -20..-10 else when (normalizedAttempt) {
        1 -> 99..99; 2 -> 70..90; 3 -> 100..150; 4 -> 50..90; 5 -> 30..60
        6 -> 30..50; 7 -> 10..30; 8 -> 10..20; 9 -> 0..20; else -> 0..10
    }
    val messages = if (won) scoreRules.getValue(normalizedAttempt).messages else scoreResult(10, false, random).message.let { listOf(it) }
    return ScoreResult(random.nextInt(range.first, range.last + 1), messages.random(random))
}

private data class ScoreRule(val range: IntRange, val messages: List<String>)
private val scoreRules = mapOf(
    1 to ScoreRule(69..69, listOf("纯靠狗运","狗运拉满","这都能中？狗运","狗运又来了","运气狗一样好","狗运附体","这波是狗运","狗运选手","没别的，就是狗运","狗运给的太多了")),
    2 to ScoreRule(40..60, listOf("这把运气占比不低","运气确实有点成分","不全是实力，有点运气","运气帮了大忙","这波有运气加持","运气成分有点明显","实力有，运气也有","靠运气补上了","运气没拖后腿","这结果有运气因素")),
    3 to ScoreRule(70..120, listOf("大木博士禁止参赛","空木博士禁止参赛","小田卷博士禁止参赛","山梨博士禁止参赛","红豆杉博士禁止参赛","布拉塔诺博士禁止参赛","库库伊博士禁止参赛","木兰博士禁止参赛","奥琳博士禁止参赛","弗图博士禁止参赛","普罗瑟博士禁止参赛","塞德博士禁止参赛","泽伊博士禁止参赛")),
    4 to ScoreRule(20..60, listOf("你是真新镇的小智吧？","你是伽勒尔的不败王者丹帝吧？","你是关都的龙使者渡吧？","你是卡洛斯冠军卡露妮吧？","你是神奥冠军竹兰吧？","你是合众冠军阿戴克吧？","你是丰缘冠军大吾吧？","你是丰缘冠军米可利吧？","你是阿罗拉初代冠军卡奇吧？","你是世界锦标赛大师级别艾岚吧？")),
    5 to ScoreRule(10..40, listOf("你不会是武藏吧","你不会是小次郎吧","你不会是喵喵吧","你不会是可达鸭吧","你不会是魔尼尼吧","你不会是果然翁吧","你不会是小豪吧","你不会是聒噪鸟吧","你不会是水水獭吧","你不会是迷拟丘吧")),
    6 to ScoreRule(10..30, listOf("还行还行","不错不错","可以啊可以","这波可以","有点东西","还真可以","可以，不丢人","可以，挺稳","可以，没白给","可以，继续保持")),
    7 to ScoreRule(0..20, listOf("水水獭，你干的漂亮","呆呆兽，你干的漂亮","沼王，你干的漂亮","黏黏宝，你干的漂亮","无壳海兔，你干的漂亮","小卡比兽，你干的漂亮","跳跳猪，你干的漂亮","古月鸟，你干的漂亮","睡睡菇，你干的漂亮","椰蛋树，你干的漂亮")),
    8 to ScoreRule(0..10, listOf("有点菜，说实话","这操作有点无语","菜得有点明显","无语了，这也能输","有点菜，但能接受","这水平有点尴尬","属实有点菜","有点无语，下次稳住","菜归菜，还能抢救","有点菜，别放弃")),
    9 to ScoreRule(-10..10, listOf("真帮，继续加油","你是真帮，再练练","真帮，但别灰心","帮中帮，还得是你","真帮，下次争取不帮","你太帮了，再接再厉","真帮，已经很努力了","帮就完了，继续冲","真帮，别在意分数","帮出风采，继续加油")),
    10 to ScoreRule(-5..5, listOf("已经不错了","你已经很努力了","不错，至少中了","已经可以了，别自责","能中就不亏了","你已经很棒了","虽然少，但有进步","不错，继续积累","已经很可以了","别急，你会越来越强")),
)

@Composable
internal fun PokemonGamesPage(catalog: PokemonCatalog, onExitToScanner: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val index = remember(catalog) { GameIndex(context.applicationContext, catalog) }
    val scoreStore = remember { SharedPreferencesScoreRepository(context.applicationContext) }
    var score by remember { mutableIntStateOf(scoreStore.score()) }
    var screen by remember { mutableStateOf(GameScreen.Menu) }
    Box(modifier.fillMaxSize().background(ScannerCanvas).pointerInput(Unit) {
        var travel = 0f
        detectHorizontalDragGestures(
            onDragStart = { travel = 0f },
            onHorizontalDrag = { _, drag -> travel += drag },
            onDragEnd = { if (kotlin.math.abs(travel) > 120f) onExitToScanner() },
        )
    }.padding(14.dp)) {
        Column(Modifier.fillMaxSize().background(ScannerPanel, RoundedCornerShape(10.dp)).border(4.dp, ScannerBorder, RoundedCornerShape(10.dp)).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GameStatusLights()
                Text(
                    "积分 $score",
                    modifier = Modifier.background(Color(0xFFEDE7F6), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF5E3A93),
                )
            }
            Spacer(Modifier.height(12.dp))
            when (screen) {
                GameScreen.Menu -> GameMenu { screen = it }
                GameScreen.Wordle -> WordleGame(index, scoreStore, { score = it }, { screen = GameScreen.Menu })
                GameScreen.Weight -> WeightGame(index, { screen = GameScreen.Menu })
                GameScreen.Types -> TypeGame(index, { screen = GameScreen.Menu })
            }
        }
    }
}

@Composable private fun GameStatusLights() = Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
    Canvas(Modifier.size(34.dp)) { drawCircle(Color(0xFF24232A)); drawCircle(Color(0xFF7E57C2), radius = size.minDimension * .34f) }
    listOf(Color(0xFFE96B9A), Color(0xFFF4C451), Color(0xFF54A76E)).forEach { color -> Canvas(Modifier.size(14.dp)) { drawCircle(Color(0xFF24232A)); drawCircle(color, radius = size.minDimension * .31f) } }
}

@Composable private fun GameMenu(open: (GameScreen) -> Unit) = LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    item { GameChoice("宝可梦猜猜乐", "根据属性、种族值、世代、特性、进化和标签猜出目标。") { open(GameScreen.Wordle) } }
    item { GameChoice("宝可梦猜体重", "十题内判断两只宝可梦中谁更重。") { open(GameScreen.Weight) } }
    item { GameChoice("宝可梦猜属性", "根据完整属性组合，从四只宝可梦中选出正确目标。") { open(GameScreen.Types) } }
}

@Composable private fun GameChoice(title: String, note: String, onClick: () -> Unit) = Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2EEF7)).clickable(onClick = onClick).padding(18.dp)) { Text(title, fontWeight = FontWeight.Black, fontSize = 19.sp); Spacer(Modifier.height(5.dp)); Text(note, color = Color.DarkGray) }

@Composable private fun WordleGame(index: GameIndex, scoreStore: ScoreRepository, onScoreChanged: (Int) -> Unit, onBack: () -> Unit) {
    val all = remember { index.all() }
    var started by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(all.first()) }
    var baseline by remember { mutableStateOf<GuessFeedback?>(null) }
    var query by remember { mutableStateOf("") }
    var guesses by remember { mutableStateOf(emptyList<GuessFeedback>()) }
    var result by remember { mutableStateOf<ScoreResult?>(null) }
    var totalScore by remember { mutableIntStateOf(scoreStore.score()) }
    fun start() {
        target = all.random()
        val freeGuess = all.filter { it.record.key != target.record.key }.random()
        baseline = compareGuess(freeGuess, target)
        guesses = emptyList(); query = ""; result = null; started = true
    }
    fun submit(name: String) {
        val found = all.firstOrNull { it.record.nameZh == name.trim() || it.record.sourceFormName == name.trim() } ?: return
        if (!started || result != null || guesses.any { it.pokemon.record.key == found.record.key }) return
        val feedback = compareGuess(found, target)
        guesses = guesses + feedback; query = ""
        if (found.record.key == target.record.key || guesses.size == 10) {
            val score = scoreResult(guesses.size, found.record.key == target.record.key)
            result = score; onScoreChanged(scoreStore.apply(score.delta))
        }
    }
    if (!started) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button(onClick = ::start, modifier = Modifier.size(132.dp, 56.dp)) { Text("开始", fontSize = 20.sp) } }
        return
    }
    val candidates = remember(query, all) { searchCandidates(all, query) }
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = ::start) { Text("随机开局") }; Button(onClick = ::start) { Text("重新开始") }; Text("${guesses.size} / 10", modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("输入宝可梦名称") }, singleLine = true); Spacer(Modifier.width(8.dp)); Button(onClick = { submit(query) }) { Text("猜") } }
        if (query.isNotBlank() && result == null) Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(6.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))) { candidates.forEach { candidate -> Text(candidate.record.nameZh, Modifier.fillMaxWidth().clickable { submit(candidate.record.nameZh) }.padding(10.dp), fontWeight = FontWeight.Medium) } }
        Text("基准宝可梦（免费首猜）", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        baseline?.let { GuessRow(it) }
        if (result != null) { Text("${if (result!!.delta >= 0) "+" else ""}${result!!.delta} 积分", color = if (result!!.delta >= 0) Color(0xFF18794E) else Color(0xFFC62828), fontWeight = FontWeight.Black); Text(result!!.message, fontWeight = FontWeight.Bold); Text("答案：${target.record.nameZh}"); Button(onClick = ::start) { Text("再来一局") } }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(guesses.size) { GuessRow(guesses[it]) } }
        Button(onClick = onBack) { Text("返回菜单") }
    }
}

private fun searchCandidates(all: List<GamePokemon>, query: String): List<GamePokemon> {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    if (terms.isEmpty()) return emptyList()
    return all.filter { pokemon -> terms.all { term -> pokemon.record.nameZh.contains(term) || pokemon.record.sourceFormName.contains(term) } }.take(10)
}

private fun compareGuess(guess: GamePokemon, target: GamePokemon): GuessFeedback {
    val direction = when { guess.totalStats > target.totalStats -> "↑"; guess.totalStats < target.totalStats -> "↓"; else -> "＝" }
    val guessAbilities = guess.record.abilities.normal.plus(guess.record.abilities.hidden).toSet()
    val targetAbilities = target.record.abilities.normal.plus(target.record.abilities.hidden).toSet()
    val abilities = guessAbilities.ifEmpty { setOf("无") }
    val tags = guess.tags.ifEmpty { setOf("无") }
    return GuessFeedback(guess, setHint(guess.record.types, target.record.types), numberHint(guess.totalStats, target.totalStats), direction, if (guess.generation == target.generation) HintColor.Match else if (kotlin.math.abs(guess.generation-target.generation)==1) HintColor.Close else HintColor.Miss, abilities.map { it to if (it == "无") HintColor.Miss else setHint(guessAbilities, targetAbilities) }, if (guess.evolutionKind == target.evolutionKind) HintColor.Match else if (guess.evolutionKind.substringBefore("提升") == target.evolutionKind.substringBefore("提升")) HintColor.Close else HintColor.Miss, tags.map { it to if (it != "无" && it in target.tags) HintColor.Match else HintColor.Miss })
}
internal fun setHint(guess: Collection<String>, target: Collection<String>): HintColor {
    val guessSet = guess.filter(String::isNotBlank).toSet()
    val targetSet = target.filter(String::isNotBlank).toSet()
    return when {
        guessSet == targetSet -> HintColor.Match
        guessSet.intersect(targetSet).isNotEmpty() -> HintColor.Close
        else -> HintColor.Miss
    }
}
private fun numberHint(a: Int, b: Int) = if (a == b) HintColor.Match else if (kotlin.math.abs(a-b) <= 50) HintColor.Close else HintColor.Miss
private fun hintColor(value: HintColor) = when(value) { HintColor.Match -> Color(0xFFB9F6CA); HintColor.Close -> Color(0xFFFFF3B0); HintColor.Miss -> Color(0xFFE9ECEF) }

@Composable private fun GuessRow(item: GuessFeedback) = Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(6.dp)).border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(6.dp)).padding(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) { GameArt(item.pokemon.record, Modifier.size(56.dp)); Spacer(Modifier.width(6.dp)); Text(item.pokemon.record.nameZh, fontWeight = FontWeight.Black) }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { HintChip("属性：${item.pokemon.record.types.joinToString("/")}", item.type); HintChip("种族值：${item.pokemon.totalStats}${item.statsDirection}", item.stats); HintChip("世代：${item.pokemon.generation}", item.generation); HintChip("进化：${item.pokemon.evolutionKind}", item.evolution) }
    Row { item.abilities.forEach { HintChip("特性：${it.first}", it.second) } }
    Row { item.tags.forEach { HintChip("标签：${it.first}", it.second) } }
}
@Composable private fun HintChip(text: String, hint: HintColor) = Text(text, Modifier.padding(2.dp).background(hintColor(hint), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 3.dp), fontSize = 11.sp)

@Composable internal fun WeightGame(index: GameIndex, onBack: () -> Unit, onPlaySound: (AppSoundEffect) -> Unit = {}) {
    val all = remember { index.all().filter { it.weightKg > 0 } }
    var question by remember { mutableStateOf(newWeightQuestion(all)) }; var answered by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf<GamePokemon?>(null) }; var correct by remember { mutableIntStateOf(0) }; var number by remember { mutableIntStateOf(1) }
    val answer = if (question.first.weightKg > question.second.weightKg) question.first else question.second
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("宝可梦猜体重  $number / 10", fontWeight = FontWeight.Black); Text("谁更重？")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { WeightOption(question.first, !answered, Modifier.weight(1f), selected, answer) { selected = question.first; if (question.first == answer) correct++; onPlaySound(if (question.first == answer) AppSoundEffect.GameCorrect else AppSoundEffect.GameIncorrect); answered = true }; WeightOption(question.second, !answered, Modifier.weight(1f), selected, answer) { selected = question.second; if (question.second == answer) correct++; onPlaySound(if (question.second == answer) AppSoundEffect.GameCorrect else AppSoundEffect.GameIncorrect); answered = true } }
        if (answered) { Text("正确答案：${answer.record.nameZh}，重量 ${answer.weightKg} kg", fontWeight = FontWeight.Bold); Button(onClick = { if(number==10){ number=1;correct=0 } else number++; question=newWeightQuestion(all);selected=null;answered=false }) { Text(if(number==10) "再来一局" else "下一题") } }
        Spacer(Modifier.weight(1f)); Button(onClick=onBack){Text("返回菜单")}
    }
}
private fun newWeightQuestion(all: List<GamePokemon>): Pair<GamePokemon, GamePokemon> { val first=all.random(); return first to all.filter { it.record.key != first.record.key && it.weightKg != first.weightKg }.random() }
@Composable private fun WeightOption(pokemon: GamePokemon, enabled: Boolean, modifier: Modifier, selected: GamePokemon?, answer: GamePokemon, onChoose: () -> Unit) { val color = when { selected == null -> Color(0xFFF2EEF7); pokemon == answer -> Color(0xFFB9F6CA); else -> Color(0xFFFFCDD2) }; Column(modifier.aspectRatio(0.82f).clip(RoundedCornerShape(8.dp)).background(color).clickable(enabled=enabled,onClick=onChoose).padding(8.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.SpaceBetween) { GameArt(pokemon.record, Modifier.size(112.dp)); Text(pokemon.record.nameZh, fontWeight=FontWeight.Bold, textAlign=TextAlign.Center) } }

@Composable internal fun TypeGame(index: GameIndex, onBack: () -> Unit, onPlaySound: (AppSoundEffect) -> Unit = {}) {
    val all=remember { index.all() }; var target by remember { mutableStateOf(all.random()) }; var candidates by remember { mutableStateOf(typeCandidates(target,all)) }; var answered by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf<GamePokemon?>(null) }
    Column(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally) { Text("宝可梦猜属性",fontWeight=FontWeight.Black); Text(target.record.types.joinToString(" / "), fontSize=22.sp,fontWeight=FontWeight.Black,color=Color(0xFF6B3FA0)); Row(Modifier.fillMaxWidth()) { candidates.take(2).forEach { TypeOption(it,!answered, Modifier.weight(1f),selected,target){ selected=it;onPlaySound(if (it == target) AppSoundEffect.GameCorrect else AppSoundEffect.GameIncorrect);answered=true } } }; Row(Modifier.fillMaxWidth()) { candidates.drop(2).forEach { TypeOption(it,!answered, Modifier.weight(1f),selected,target){ selected=it;onPlaySound(if (it == target) AppSoundEffect.GameCorrect else AppSoundEffect.GameIncorrect);answered=true } } }; if(answered) { Text("正确答案：${target.record.nameZh}（${target.record.types.joinToString(" / ")}）",fontWeight=FontWeight.Bold); Button(onClick={onPlaySound(AppSoundEffect.Interaction);target=all.random();candidates=typeCandidates(target,all);selected=null;answered=false}){Text("下一题")} }; Spacer(Modifier.weight(1f));Button(onClick={onPlaySound(AppSoundEffect.Interaction);onBack()}){Text("返回菜单")} }
}
private fun typeCandidates(target: GamePokemon, all: List<GamePokemon>): List<GamePokemon> { val set=target.record.types.toSet(); return (all.filter { it.record.types.toSet()!=set }.shuffled().take(3)+target).shuffled() }
@Composable private fun TypeOption(pokemon: GamePokemon, enabled: Boolean, modifier: Modifier, selected: GamePokemon?, answer: GamePokemon, onChoose: () -> Unit) { val color=when { selected==null -> Color(0xFFF2EEF7); pokemon==answer -> Color(0xFFB9F6CA); else -> Color(0xFFFFCDD2) }; Column(modifier.padding(4.dp).aspectRatio(0.82f).clip(RoundedCornerShape(8.dp)).background(color).clickable(enabled=enabled,onClick=onChoose).padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceBetween){GameArt(pokemon.record, Modifier.size(112.dp));Text(pokemon.record.nameZh,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)} }
@Composable private fun GameArt(pokemon: PokemonRecord, modifier: Modifier = Modifier.size(112.dp)) { val context=LocalContext.current; val bitmap by produceState<android.graphics.Bitmap?>(null,pokemon.imageAsset){ value=runCatching{ResourceBundleRepository(context).openAsset(pokemon.imageAsset).use(BitmapFactory::decodeStream)}.getOrNull() }; if(bitmap!=null) Image(bitmap!!.asImageBitmap(),pokemon.nameZh,modifier,contentScale=ContentScale.Fit) else Canvas(modifier){drawCircle(Color.LightGray)} }

/** Shared scan-frame content. The frame and status lamps deliberately live in ScannerShell. */
@Composable
internal fun PokemonGamesPageV2(
    catalog: PokemonCatalog,
    onExitToScanner: () -> Unit,
    onPlaySound: (AppSoundEffect) -> Unit = {},
    onSpeakText: (String) -> Unit = {},
    onScoreChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val index = remember(catalog) { GameIndex(context.applicationContext, catalog) }
    val regionIndex = remember { GameRegionIndex(context.applicationContext) }
    val settingsStore = remember { GameSettingsRepository(context.applicationContext) }
    val scoreStore = remember { SharedPreferencesScoreRepository(context.applicationContext) }
    var settings by remember { mutableStateOf(settingsStore.load(regionIndex.allIds)) }
    var screen by remember { mutableStateOf(GameScreen.Menu) }
    var loading by remember { mutableStateOf<GameScreen?>(null) }

    LaunchedEffect(loading) {
        val destination = loading ?: return@LaunchedEffect
        delay(220)
        screen = destination
        loading = null
    }

    Box(modifier = modifier.fillMaxSize().padding(8.dp)) {
        if (loading != null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("正在准备游戏", fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 28.dp))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                when (screen) {
                    GameScreen.Menu -> GameMenuV2 {
                        onPlaySound(AppSoundEffect.Interaction)
                        loading = it
                    }
                    GameScreen.Wordle -> RegionalWordleGame(index, regionIndex, settings, { updated ->
                        settings = updated
                        settingsStore.save(updated)
                    }, scoreStore, onScoreChanged, { screen = GameScreen.Menu }, onPlaySound, onSpeakText)
                    GameScreen.Weight -> WeightGame(index, { screen = GameScreen.Menu }, onPlaySound)
                    GameScreen.Types -> TypeGame(index, { screen = GameScreen.Menu }, onPlaySound)
                }
            }
        }
    }
}

@Composable
private fun GameMenuV2(open: (GameScreen) -> Unit) = LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("pokemon_games_menu"),
    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item {
        GameMenuCard(
            imageAsset = "game/guess.jpg",
            title = "宝可梦猜猜乐（积分）",
            description = "根据属性、种族值、世代、特性、进化和标签猜出目标",
            tag = "game_menu_wordle",
        ) { open(GameScreen.Wordle) }
    }
    item {
        GameMenuCard(
            imageAsset = "game/weight.jpg",
            title = "宝可梦猜体重",
            description = "十题内判断两只宝可梦中谁的体重更大",
            tag = "game_menu_weight",
        ) { open(GameScreen.Weight) }
    }
    item {
        GameMenuCard(
            imageAsset = "game/types.jpg",
            title = "宝可梦猜属性",
            description = "根据完整属性组合，从四只宝可梦中选出正确目标",
            tag = "game_menu_types",
        ) { open(GameScreen.Types) }
    }
}

@Composable
private fun GameMenuCard(
    imageAsset: String,
    title: String,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(89.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE9E7EF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GameMenuArt(
            asset = imageAsset,
            description = title,
            modifier = Modifier.size(56.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                color = Color(0xFF17151C),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = description,
                color = Color(0xFF55515B),
                fontSize = 8.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GameMenuArt(asset: String, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, asset) {
        value = runCatching {
            ResourceBundleRepository(context).openAsset(asset).use(BitmapFactory::decodeStream)
        }.getOrNull()
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF3F1F7)),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

internal fun regionalCandidates(index: GameIndex, regionIndex: GameRegionIndex, settings: GameStartSettings): List<GamePokemon> {
    val dexIds = regionIndex.regions.filter { it.id in settings.regions }.flatMapTo(mutableSetOf()) { it.dexIds }
    return index.all().filter { pokemon ->
        val formAllowed = when (pokemon.specialForm) {
            SpecialForm.Normal -> true
            SpecialForm.Mega -> settings.includeMega
            SpecialForm.Gigantamax -> settings.includeGigantamax
        }
        pokemon.record.id.padStart(4, '0') in dexIds && formAllowed
    }.distinctBy { it.record.key }
}

@Composable
internal fun RegionalWordleGame(
    index: GameIndex,
    regionIndex: GameRegionIndex,
    settings: GameStartSettings,
    onSettingsChanged: (GameStartSettings) -> Unit,
    scoreStore: ScoreRepository,
    onScoreChanged: (Int) -> Unit,
    onBack: () -> Unit,
    onPlaySound: (AppSoundEffect) -> Unit,
    onSpeakText: (String) -> Unit,
) {
    var started by remember { mutableStateOf(false) }
    var frozenPool by remember { mutableStateOf(emptyList<GamePokemon>()) }
    var target by remember { mutableStateOf<GamePokemon?>(null) }
    var baseline by remember { mutableStateOf<GuessFeedback?>(null) }
    var query by remember { mutableStateOf("") }
    var guesses by remember { mutableStateOf(emptyList<GuessFeedback>()) }
    var result by remember { mutableStateOf<ScoreResult?>(null) }
    var totalScore by remember { mutableIntStateOf(scoreStore.score()) }
    val currentPool = remember(settings) { regionalCandidates(index, regionIndex, settings) }

    fun start(pool: List<GamePokemon> = frozenPool.ifEmpty { currentPool }) {
        if (pool.size < 2) return
        val nextTarget = pool.random()
        val freeGuess = pool.filterNot { it.record.key == nextTarget.record.key }.random()
        frozenPool = pool
        target = nextTarget
        baseline = compareGuess(freeGuess, nextTarget)
        guesses = emptyList(); query = ""; result = null; started = true
    }
    fun finish(attempt: Int, won: Boolean) {
        if (result != null) return
        val scoreResult = regionalScoreResult(attempt, won)
        result = scoreResult
        totalScore = scoreStore.apply(scoreResult.delta)
        onScoreChanged(totalScore)
        onPlaySound(if (won) AppSoundEffect.GameCorrect else AppSoundEffect.GameIncorrect)
        if (!won) onSpeakText(scoreResult.message)
    }
    fun submit(name: String) {
        val goal = target ?: return
        val found = frozenPool.firstOrNull { it.record.nameZh == name.trim() || it.record.sourceFormName == name.trim() } ?: return
        if (!started || result != null || guesses.any { it.pokemon.record.key == found.record.key }) return
        val feedback = compareGuess(found, goal)
        val updated = guesses + feedback
        guesses = updated; query = ""
        if (found.record.key == goal.record.key || updated.size == 10) finish(updated.size, found.record.key == goal.record.key)
    }

    if (!started) {
        PokemonRegionSelector(
            regionIndex = regionIndex,
            settings = settings,
            onSettingsChanged = onSettingsChanged,
            candidateCount = currentPool.size,
            onBack = onBack,
            onStart = { start(currentPool) },
            onPlaySound = onPlaySound,
            minimumCandidates = 2,
        )
        return
    }
    RegionalWordlePlayArea(
        query = query,
        onQueryChange = { query = it },
        candidates = remember(query, frozenPool) { searchCandidates(frozenPool, query) },
        guesses = guesses,
        baseline = baseline,
        result = result,
        totalScore = totalScore,
        answer = target?.record?.nameZh.orEmpty(),
        onSubmit = ::submit,
        onGiveUp = { finish(10, false) },
        onRestart = { start() },
        onBack = onBack,
        onPlaySound = onPlaySound,
    )
}

@Composable
private fun RegionalWordlePlayArea(
    query: String,
    onQueryChange: (String) -> Unit,
    candidates: List<GamePokemon>,
    guesses: List<GuessFeedback>,
    baseline: GuessFeedback?,
    result: ScoreResult?,
    totalScore: Int,
    answer: String,
    onSubmit: (String) -> Unit,
    onGiveUp: () -> Unit,
    onRestart: () -> Unit,
    onBack: () -> Unit,
    onPlaySound: (AppSoundEffect) -> Unit,
) {
    var rulesVisible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).testTag("wordle_input"),
                label = { Text("输入宝可梦名称") },
                singleLine = true,
                enabled = result == null,
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onPlaySound(AppSoundEffect.Interaction); onSubmit(query) }, enabled = result == null, modifier = Modifier.testTag("wordle_submit")) { Text("提交") }
        }
        if (query.isNotBlank() && result == null) {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(6.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))) {
                candidates.forEach { candidate ->
                    Text(candidate.record.nameZh, Modifier.fillMaxWidth().clickable { onSubmit(candidate.record.nameZh) }.padding(10.dp), fontWeight = FontWeight.Medium)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { onPlaySound(AppSoundEffect.Interaction); onGiveUp() }, enabled = result == null, modifier = Modifier.testTag("wordle_give_up")) { Text("放弃") }
            Button(onClick = { onPlaySound(AppSoundEffect.Interaction); onBack() }, modifier = Modifier.testTag("wordle_back")) { Text("返回") }
            Button(onClick = { onPlaySound(AppSoundEffect.Interaction); rulesVisible = true }, modifier = Modifier.testTag("wordle_rules")) { Text("规则") }
            Text("${guesses.size} / 10", modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Black)
        }
        result?.let { score ->
            Column(Modifier.fillMaxWidth().background(Color(0xFFF4F2F6), RoundedCornerShape(8.dp)).padding(10.dp)) {
                Text("${if (score.delta >= 0) "+" else ""}${score.delta} 积分  ·  当前总积分 $totalScore", color = if (score.delta >= 0) Color(0xFF18794E) else Color(0xFFC62828), fontWeight = FontWeight.Black)
                Text(score.message, fontWeight = FontWeight.Bold)
                Text("答案：$answer")
                Button(onClick = { onPlaySound(AppSoundEffect.Interaction); onRestart() }) { Text("再来一局") }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(guesses.asReversed().size) { index -> RegionalGuessCard(guesses.asReversed()[index], "wordle_guess_$index") }
            baseline?.let { item { RegionalGuessCard(it, "wordle_baseline") } }
        }
    }
    if (rulesVisible) WordleRulesDialog { rulesVisible = false }
}

@Composable
private fun RegionalGuessCard(item: GuessFeedback, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFDDD8E2), RoundedCornerShape(8.dp)).padding(8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameArt(item.pokemon.record, Modifier.size(68.dp))
            Text(item.pokemon.record.nameZh, fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).testTag("wordle_guess_table"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GuessField("属性", item.pokemon.record.types.joinToString("/"), item.type)
            GuessField("种族值", "${item.pokemon.totalStats}${item.statsDirection}", item.stats)
            GuessField("世代", "第${item.pokemon.generation}世代", item.generation)
            GuessField("特性", item.abilities.joinToString("、") { it.first }, item.abilities.minByOrNull { it.second.ordinal }?.second ?: HintColor.Miss)
            GuessField("进化", item.pokemon.evolutionLabel, item.evolution)
            GuessField("标签", item.tags.joinToString("、") { it.first }, item.tags.minByOrNull { it.second.ordinal }?.second ?: HintColor.Miss)
        }
    }
}

@Composable
private fun GuessField(title: String, value: String, hint: HintColor) = Column(Modifier.width(118.dp)) {
    Text(title, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    Text(value, modifier = Modifier.fillMaxWidth().background(hintColor(hint), RoundedCornerShape(4.dp)).padding(5.dp), fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun WordleRulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("知道了") } },
        title = { Text("宝可梦猜猜乐规则") },
        text = { Text(wordleRulesText) },
    )
}

private val wordleRulesText = "宝可梦猜猜乐是一个猜谜游戏，需要根据属性、特性、数值、进化等线索猜测目标。\n\n游戏玩法\n输入宝可梦名称后，每次猜测都会显示与目标的接近程度；请在允许次数内猜出目标。\n\n宝可梦范围\n包含第一至第九世代主系列游戏，可在开局设置中更改范围。\n\n标签颜色说明\n绿色：与目标对应标签相同。\n黄色：标签接近。\n灰色：标签不同。\n\n黄色判定\n种族值相差不超过 50；世代相邻；进化方式相近但不相同。"

@Composable
internal fun PokemonRegionSelector(
    regionIndex: GameRegionIndex,
    settings: GameStartSettings,
    onSettingsChanged: (GameStartSettings) -> Unit,
    candidateCount: Int,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onPlaySound: (AppSoundEffect) -> Unit,
    minimumCandidates: Int,
) = Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    onPlaySound(AppSoundEffect.Interaction)
                    onBack()
                },
                modifier = Modifier.testTag("wordle_region_back"),
            ) {
                Canvas(Modifier.size(24.dp)) {
                    val stroke = 2.5.dp.toPx()
                    drawLine(Color(0xFF24212A), Offset(size.width * 0.72f, size.height * 0.2f), Offset(size.width * 0.28f, size.height * 0.5f), strokeWidth = stroke)
                    drawLine(Color(0xFF24212A), Offset(size.width * 0.28f, size.height * 0.5f), Offset(size.width * 0.72f, size.height * 0.8f), strokeWidth = stroke)
                    drawLine(Color(0xFF24212A), Offset(size.width * 0.3f, size.height * 0.5f), Offset(size.width * 0.94f, size.height * 0.5f), strokeWidth = stroke)
                }
            }
            Text("选择本局地区", modifier = Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.size(48.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StartSettingPill("全选", "wordle_select_all") {
                onSettingsChanged(settings.copy(regions = regionIndex.allIds))
            }
            StartSettingPill("全不选", "wordle_select_none") {
                onSettingsChanged(settings.copy(regions = emptySet()))
            }
            StartSettingPill("超级进化", "wordle_include_mega", settings.includeMega) {
                onSettingsChanged(settings.copy(includeMega = !settings.includeMega))
            }
            StartSettingPill("超级巨化", "wordle_include_gigantamax", settings.includeGigantamax) {
                onSettingsChanged(settings.copy(includeGigantamax = !settings.includeGigantamax))
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("wordle_region_grid"),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(regionIndex.regions, key = { it.id }) { region ->
                RegionSelectionCard(
                    region = region,
                    selected = region.id in settings.regions,
                    onSelectedChange = { selected ->
                        onPlaySound(AppSoundEffect.Interaction)
                        onSettingsChanged(settings.copy(regions = if (selected) settings.regions + region.id else settings.regions - region.id))
                    },
                )
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("已选候选：$candidateCount", fontWeight = FontWeight.Bold)
                    if (candidateCount < 2) Text("请至少选择一个拥有两只可用宝可梦的地区。", color = Color(0xFFC62828), textAlign = TextAlign.Center)
                }
            }
        }
    }

    Button(
        onClick = {
            onPlaySound(AppSoundEffect.Interaction)
            onStart()
        },
        enabled = candidateCount >= minimumCandidates,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).testTag("wordle_start_button"),
    ) { Text("开始") }
}

@Composable
private fun StartSettingPill(label: String, tag: String, selected: Boolean? = null, onClick: () -> Unit) {
    val selectedColor = Color(0xFF267654)
    Text(
        text = if (selected == null) label else "$label ${if (selected) "开" else "关"}",
        color = if (selected == true) Color.White else Color(0xFF29242F),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected == true) selectedColor else Color(0xFFE8E5EC))
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 9.dp, vertical = 8.dp),
    )
}

@Composable
private fun RegionSelectionCard(region: GameRegion, selected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .drawBehind {
                val corner = 14.dp.toPx()
                val shadowOffset = 3.dp.toPx()
                drawRoundRect(
                    color = Color(0x44000000),
                    topLeft = Offset(shadowOffset, shadowOffset),
                    size = Size(size.width - shadowOffset, size.height - shadowOffset),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                    size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                    style = Stroke(1.dp.toPx()),
                )
            }
            .clip(shape)
            .background(if (selected) Color(0xFFF2FAF5) else Color(0xFFF7F5F8))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF267654) else Color(0xFFD4CFD8), shape)
            .clickable { onSelectedChange(!selected) }
            .testTag("wordle_region_${region.id}")
            .padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            painter = painterResource(regionImageResource(region.id)),
            contentDescription = "${region.label}地区",
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(9.dp)),
            contentScale = ContentScale.Crop,
        )
        Text(region.label, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun regionImageResource(regionId: String): Int = when (regionId) {
    "kanto" -> R.drawable.region_kanto
    "johto" -> R.drawable.region_johto
    "hoenn" -> R.drawable.region_hoenn
    "sinnoh" -> R.drawable.region_sinnoh
    "unova" -> R.drawable.region_alola
    "kalos" -> R.drawable.region_kalos
    "alola" -> R.drawable.region_alola
    "hisui" -> R.drawable.region_hisui
    "galar" -> R.drawable.region_galar
    "paldea" -> R.drawable.region_paldea
    "lumiose" -> R.drawable.region_lumiose
    else -> R.drawable.region_kanto
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value
