/* Scanner result and Pokemon details UI. */
/* Compose scanner screens, dialogs, and Android UI integration. / Compose 扫描页面、弹窗与 Android UI 集成。 */
package com.example.pokedex.presentation.scanner

import com.example.pokedex.data.scanner.*
import com.example.pokedex.domain.scanner.*

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pokedex.R
import com.example.pokedex.ui.theme.PokedexTheme
import com.example.pokedex.ui.theme.ThemeCatalog
import com.example.pokedex.ui.theme.ThemePalette
import com.example.pokedex.ui.theme.ScannerCanvas
import com.example.pokedex.ui.theme.ScannerBorder
import com.example.pokedex.ui.theme.ScannerGraphite
import com.example.pokedex.ui.theme.ScannerGraphiteLight
import com.example.pokedex.ui.theme.ScannerLensBlue
import com.example.pokedex.ui.theme.ScannerOutline
import com.example.pokedex.ui.theme.ScannerPanel
import com.example.pokedex.ui.theme.ScannerRed
import com.example.pokedex.ui.theme.ScannerRedDark
import com.example.pokedex.ui.theme.ScannerRedLight
import com.example.pokedex.ui.theme.ScannerSignalGreen
import com.example.pokedex.ui.theme.ScannerSuccess
import com.example.pokedex.ui.theme.ScannerWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

@Composable
internal fun PokemonResult(
    pokemon: PokemonRecord,
    catalog: PokemonCatalog,
    capturedImageJpeg: ByteArray?,
    collected: Boolean,
    detailsRepository: PokemonDetailsRepository,
    pageIndex: Int,
    onOpenPokemonDetail: (Int) -> Unit,
    onOpenGallery: () -> Unit = {},
) {
    PokemonPagedDetails(
        pokemon = pokemon,
        catalog = catalog,
        collected = collected,
        detailsRepository = detailsRepository,
        pageIndex = pageIndex,
        capturedImageJpeg = capturedImageJpeg,
        onOpenPokemonDetail = onOpenPokemonDetail,
        onOpenGallery = onOpenGallery,
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerPanel)
            .padding(10.dp)
            .testTag("scanner_result"),
    )
}

@Composable
internal fun CapturedPhotoPanel(
    imageJpeg: ByteArray?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .testTag("captured_photo_panel"),
        contentAlignment = Alignment.TopCenter,
    ) {
        val bitmap = remember(imageJpeg) {
            imageJpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        val framePadding = 8.dp
        val imageRatio = bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1f
        val innerMaxWidth = (maxWidth - framePadding).coerceAtLeast(0.dp)
        val innerMaxHeight = (maxHeight - framePadding).coerceAtLeast(0.dp)
        val imageWidth = minOf(innerMaxWidth, innerMaxHeight * imageRatio)
        val imageHeight = imageWidth / imageRatio
        val frameWidth = imageWidth + framePadding
        val frameHeight = imageHeight + framePadding
        Box(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(ScannerCanvas)
                .border(4.dp, Color.Black, RoundedCornerShape(8.dp))
                .padding(4.dp)
                .testTag("captured_photo"),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured Pokemon",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else {
                Text(
                    text = "NO IMAGE",
                    color = ScannerGraphiteLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

internal enum class PokemonDetailsPage(val label: String) {
    Attributes("属性"), Basic("基础"), Battle("伤害"), Ecology("生态"),
    Evolution("进化"), Pokedex("图鉴"), Names("名称"), Moves("招式"),
}

@Composable
internal fun CircleToggle(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(24.dp)
                .testTag(tag)
                .clip(CircleShape)
                .border(2.dp, if (enabled) ScannerOutline else ScannerGraphiteLight, CircleShape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.fillMaxSize().background(selectedColor, CircleShape))
        }
        Text(label, color = if (enabled) ScannerOutline else ScannerGraphiteLight, fontSize = 10.sp)
    }
}

internal const val DETAILS_PAGE_COUNT = 8

@Composable
internal fun PokemonPagedDetails(
    pokemon: PokemonRecord,
    catalog: PokemonCatalog,
    collected: Boolean,
    detailsRepository: PokemonDetailsRepository,
    pageIndex: Int,
    modifier: Modifier = Modifier,
    capturedImageJpeg: ByteArray? = null,
    onOpenPokemonDetail: (Int) -> Unit = {},
    onOpenGallery: () -> Unit = {},
) {
    var selectedAppearanceIndex by rememberSaveable(pokemon.key) { mutableStateOf<Int?>(null) }
    var selectedShiny by rememberSaveable(pokemon.key) { mutableStateOf(false) }
    var selectedFemale by rememberSaveable(pokemon.key) { mutableStateOf(false) }
    var appearanceMenu by remember { mutableStateOf(false) }
    var moveQuery by rememberSaveable(pokemon.key) { mutableStateOf("") }
    val appearances = pokemon.appearances.ifEmpty { listOf(PokemonAppearance("默认", pokemon.imageAsset, pokemon.shinyImageAsset)) }
    val appearance = selectedAppearanceIndex?.let(appearances::getOrNull) ?: appearances.first()
    val isHatPikachu = pokemon.nameZh == "戴着帽子的皮卡丘"
    val hasShiny = appearance.shinyImageAsset != null
    LaunchedEffect(selectedAppearanceIndex, hasShiny) { if (!hasShiny) selectedShiny = false }
    val details by produceState<PokemonDetails?>(initialValue = null, pokemon.key, detailsRepository) {
        value = withContext(Dispatchers.IO) { runCatching { detailsRepository.load(pokemon) }.getOrNull() }
    }
    val page = PokemonDetailsPage.entries[pageIndex.mod(DETAILS_PAGE_COUNT)]
    val maleRatio = details?.basic?.maleRatio ?: 0.0
    val femaleRatio = details?.basic?.femaleRatio ?: 0.0
    val isGenderless = details != null && maleRatio == 0.0 && femaleRatio == 0.0
    val hasBothGenders = maleRatio > 0.0 && femaleRatio > 0.0
    val gender = when {
        isGenderless -> PokemonGender.Neutral
        femaleRatio > 0.0 && (maleRatio == 0.0 || selectedFemale) -> PokemonGender.Female
        else -> PokemonGender.Male
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(page.label, color = ScannerOutline, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.testTag("details_page_title"))
            Spacer(Modifier.weight(1f))
            Text("${pageIndex.mod(DETAILS_PAGE_COUNT) + 1}/$DETAILS_PAGE_COUNT", color = ScannerRedDark, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.testTag("details_page_indicator"))
        }
        Row(Modifier.fillMaxSize().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailsPageContent(pokemon, catalog, details, page, gender, moveQuery, { moveQuery = it }, onOpenPokemonDetail, Modifier.weight(if (page == PokemonDetailsPage.Attributes) 0.6f else 1f).fillMaxHeight())
            if (page == PokemonDetailsPage.Attributes) {
            Column(Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (capturedImageJpeg != null) CapturedPhotoPanel(capturedImageJpeg, Modifier.fillMaxWidth().weight(0.5f))
                PokemonThumbnail(
                    pokemon = pokemon, appearanceIndex = selectedAppearanceIndex ?: 0, shiny = selectedShiny, collected = collected,
                    modifier = Modifier.fillMaxWidth().weight(if (capturedImageJpeg != null) 0.5f else 1f).testTag("result_pokemon_thumbnail"),
                )
            }
            }
        }
        if (page == PokemonDetailsPage.Attributes) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isHatPikachu) Box(Modifier.weight(1f)) {
                Button(onClick = { appearanceMenu = true }, modifier = Modifier.fillMaxWidth().testTag(if (isHatPikachu) "hat_dress_up_button" else "appearance_selector"), shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(containerColor = ScannerGraphite)) {
                    Text(selectedAppearanceIndex?.let { appearances.getOrNull(it)?.label } ?: if (isHatPikachu) "换装" else "选择外观", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp)
                }
                DropdownMenu(expanded = appearanceMenu, onDismissRequest = { appearanceMenu = false }) {
                    appearances.forEachIndexed { index, item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { selectedAppearanceIndex = index; selectedShiny = false; appearanceMenu = false }) }
                }
            } else Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenGallery, modifier = Modifier.testTag("pokemon_gallery_button")) {
                Image(painterResource(R.drawable.ic_pokemon_gallery), contentDescription = "图库", modifier = Modifier.size(22.dp))
            }
            if (hasBothGenders) {
                CircleToggle("雌性", selectedFemale, Color(0xFFF28BB2), true, "gender_toggle") {
                    selectedFemale = !selectedFemale
                    val token = if (selectedFemale) "雌" else "雄"
                    appearances.indexOfFirst { token in it.label }.takeIf { it >= 0 }?.let { selectedAppearanceIndex = it }
                }
            }
            CircleToggle("异色", selectedShiny, Color(0xFFFFC928), hasShiny, "shiny_toggle") { selectedShiny = !selectedShiny }
        }
        if (!hasShiny) Text("暂无异色素材", color = ScannerGraphiteLight, fontSize = 9.sp)
        }
    }
}

@Composable
internal fun DetailsPageContent(
    pokemon: PokemonRecord,
    catalog: PokemonCatalog,
    details: PokemonDetails?,
    page: PokemonDetailsPage,
    gender: PokemonGender,
    moveQuery: String,
    onMoveQueryChange: (String) -> Unit,
    onOpenPokemonDetail: (Int) -> Unit,
    modifier: Modifier,
) {
    if (details == null && page != PokemonDetailsPage.Attributes) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = ScannerRedDark) }
        return
    }
    when (page) {
        PokemonDetailsPage.Attributes -> Column(modifier) {
            PokemonInfoPanel(pokemon, gender, compact = false, modifier = Modifier.fillMaxWidth().weight(0.55f))
            PokemonStatsRadarChart(
                pokemon = pokemon,
                compact = false,
                modifier = Modifier.fillMaxWidth(0.86f).weight(0.45f),
            )
        }
        PokemonDetailsPage.Basic -> BasicInfoCards(details!!.basic, modifier)
        PokemonDetailsPage.Battle -> DamagePage(pokemon.types, modifier)
        PokemonDetailsPage.Ecology -> FieldList(modifier, listOf(
            "图鉴说明" to details?.ecology?.description, "习性" to details?.ecology?.profile,
            "原型" to details?.ecology?.prototype, "补充资料" to details?.ecology?.detail,
        ))
        PokemonDetailsPage.Evolution -> EvolutionPage(details!!.evolutions, details.battle.specialEvolutions, catalog, onOpenPokemonDetail, modifier)
        PokemonDetailsPage.Pokedex -> DexPage(details!!.dexEntries, modifier)
        PokemonDetailsPage.Names -> NamesPage(details!!.localizedNames, modifier)
        PokemonDetailsPage.Moves -> MovesPage(details!!.moves, pokemon.types.firstOrNull().orEmpty(), moveQuery, onMoveQueryChange, modifier)
    }
}

internal val InfoCardBorder get() = ScannerBorder
internal val InfoCardHeader = Color(0xFFC9D9BE)
internal val InfoCardBody = Color(0xFFFFF8E8)
internal val InfoCardTitle = Color(0xFF285B3A)

@Composable
internal fun BasicInfoCards(details: PokemonBasicDetails, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
        item {
            RetroInfoCard("基本信息", listOf(
                "捕获率" to details.catchRate,
                "蛋群" to details.eggGroups.joinToString(" · ").takeIf(String::isNotBlank),
                "性别比例" to details.genderRatio,
            ))
        }
        item {
            RetroInfoCard("成长信息", listOf(
                "孵化周期" to details.eggCycles,
                "满级经验" to details.level100Experience,
                "基础经验" to details.baseExperience,
                "对战经验" to details.battleExperience,
            ))
        }
        item {
            RetroInfoCard("外观信息", listOf(
                "体型" to details.shape,
                "足迹" to details.footprint,
            ))
        }
    }
}

@Composable
internal fun RetroInfoCard(title: String, fields: List<Pair<String, String?>>) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(InfoCardBody)
            .border(1.dp, InfoCardBorder, RoundedCornerShape(6.dp))
            .testTag("basic_info_card"),
    ) {
        Box(Modifier.fillMaxWidth().background(InfoCardHeader).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(title, color = InfoCardTitle, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.forEach { (label, value) ->
                Text("$label：${value?.takeIf(String::isNotBlank) ?: "暂无数据"}", color = Color.Black, fontSize = 10.sp)
            }
        }
    }
}

@Composable
internal fun FieldList(modifier: Modifier, fields: List<Pair<String, String?>>, cardColor: Color? = null) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(fields) { _, field -> DetailField(field.first, field.second, cardColor) }
    }
}

@Composable
internal fun DamagePage(defendingTypes: List<String>, modifier: Modifier) {
    val rates = remember(defendingTypes) { PokemonTypeChart.incoming(defendingTypes) }
    val order = listOf(4f, 2f, 1f, 0.5f, 0.25f, 0f)
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        order.forEach { multiplier ->
            val values = rates.filter { it.multiplier == multiplier }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("受到伤害 ${formatMultiplier(multiplier)}", color = ScannerOutline, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    values.chunked(3).forEach { rowValues ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowValues.forEach { value -> DamageTypeCard(value.attackType, Modifier.weight(1f)) }
                            repeat(3 - rowValues.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DamageTypeCard(type: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(30.dp).background(typeColor(type), RoundedCornerShape(4.dp)).testTag("damage_type_card"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            type,
            color = typeTextColor(type),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun formatMultiplier(value: Float): String = when (value) {
    4f -> "×4"; 2f -> "×2"; 1f -> "×1"; 0.5f -> "×0.5"; 0.25f -> "×0.25"; else -> "×0"
}

@Composable
internal fun EvolutionPage(
    values: List<PokemonEvolutionEntry>,
    special: List<PokemonSpecialEvolution>,
    catalog: PokemonCatalog,
    onOpenPokemonDetail: (Int) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (values.isEmpty()) item { DetailField("进化链", null) }
        itemsIndexed(values) { _, value ->
            val record = catalog.findExact(value.formName?.takeIf(String::isNotBlank) ?: value.name) ?: catalog.findExact(value.name)
            val recordIndex = record?.let { target -> catalog.records.indexOfFirst { it.key == target.key }.takeIf { it >= 0 } }
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = recordIndex != null) { recordIndex?.let(onOpenPokemonDetail) }
                    .padding(vertical = 5.dp)
                    .testTag("evolution_link"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (value.from != null) Text("→", color = ScannerRedDark, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 5.dp))
                if (value.imageAsset != null) AssetThumbnail(value.imageAsset, Modifier.size(46.dp), value.name)
                else if (record != null) PokemonThumbnail(record, Modifier.size(46.dp), collected = true)
                else Box(Modifier.size(46.dp).border(1.dp, ScannerGraphiteLight, CircleShape), contentAlignment = Alignment.Center) { Text("?", color = ScannerGraphiteLight) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(value.name, color = ScannerOutline, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text(listOfNotNull(value.stage, value.formName, value.condition).joinToString(" · ").ifBlank { "暂无进化条件" }, color = ScannerGraphite, fontSize = 9.sp)
                }
                value.itemAsset?.let { AssetThumbnail(it, Modifier.size(32.dp).testTag("evolution_item"), value.condition ?: "进化道具") }
            }
        }
        itemsIndexed(special) { _, value ->
            val record = catalog.findExact(value.formName) ?: catalog.findExact(value.name)
            val recordIndex = record?.let { target -> catalog.records.indexOfFirst { it.key == target.key }.takeIf { it >= 0 } }
            Row(
                Modifier.fillMaxWidth()
                    .background(InfoCardBody, RoundedCornerShape(6.dp))
                    .border(1.dp, InfoCardBorder, RoundedCornerShape(6.dp))
                    .clickable(enabled = recordIndex != null) { recordIndex?.let(onOpenPokemonDetail) }
                    .padding(8.dp)
                    .testTag("special_evolution_link"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                value.imageAsset?.let { AssetThumbnail(it, Modifier.size(52.dp), value.formName) }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(value.kind, color = InfoCardTitle, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text(listOf(value.name, value.formName).filter(String::isNotBlank).joinToString(" · "), color = Color.Black, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
internal fun DexPage(values: List<PokemonDexEntry>, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (values.isEmpty()) item { DetailField("历代图鉴", null) }
        itemsIndexed(values) { _, value -> DetailField("${value.generation}-${value.version}", listOfNotNull(value.group, value.text).joinToString("：")) }
    }
}

@Composable
internal fun NamesPage(values: List<PokemonLocalizedName>, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (values.isEmpty()) item { DetailField("多语言名称", null) }
        itemsIndexed(values) { _, value -> DetailField(value.language, listOfNotNull(value.name, value.origin).joinToString("：")) }
    }
}

@Composable
internal fun MovesPage(values: List<PokemonMove>, fallbackType: String, query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    Column(modifier) {
        OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth().testTag("move_search"), label = { Text("搜索招式") }, singleLine = true)
        Spacer(Modifier.height(5.dp))
        val filtered = values.filter { query.isBlank() || it.name.contains(query, true) || it.type.orEmpty().contains(query, true) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
            if (filtered.isEmpty()) item { DetailField("招式", null) }
            filtered.groupBy { it.group }.forEach { (group, moves) ->
                item { Text(group, color = InfoCardTitle, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) }
                itemsIndexed(moves, key = { index, move -> "$group-${move.name}-$index" }) { _, move ->
                    MoveRow(move, fallbackType)
                }
            }
        }
    }
}

@Composable
internal fun MoveRow(move: PokemonMove, fallbackType: String) {
    val moveType = move.type ?: fallbackType
    Row(
        Modifier.fillMaxWidth()
            .background(InfoCardBody, RoundedCornerShape(5.dp))
            .border(1.dp, typeColor(moveType).copy(alpha = 0.72f), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .testTag("move_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            moveType,
            color = typeTextColor(moveType),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(48.dp).background(typeColor(moveType), RoundedCornerShape(3.dp)).padding(vertical = 4.dp),
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Column(Modifier.weight(1f)) {
            Text(move.name, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            move.level?.takeIf(String::isNotBlank)?.let { Text("Lv. $it", color = ScannerGraphiteLight, fontSize = 8.sp) }
        }
        Text("PP ${move.pp?.takeIf(String::isNotBlank) ?: "—"}", color = Color.Black, fontSize = 9.sp, modifier = Modifier.width(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Text("命中 ${move.accuracy?.takeIf(String::isNotBlank) ?: "—"}", color = Color.Black, fontSize = 9.sp, modifier = Modifier.width(62.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
internal fun DetailField(label: String, value: String?, cardColor: Color? = null) {
    Column(
        Modifier.fillMaxWidth()
            .background(cardColor?.copy(alpha = 0.28f) ?: Color.Transparent, RoundedCornerShape(4.dp))
            .border(if (cardColor != null) 1.dp else 0.dp, cardColor?.copy(alpha = 0.7f) ?: Color.Transparent, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .testTag("detail_field"),
    ) {
        Text("$label：${value?.takeIf(String::isNotBlank) ?: "暂无数据"}", color = ScannerGraphite, fontSize = 10.sp)
    }
}

internal enum class PokemonGender(val symbol: String, val color: Color) {
    Male("♂", Color(0xFF318CE7)),
    Female("♀", Color(0xFFF28BB2)),
    Neutral("●", Color(0xFFE0B928)),
}

@Composable
internal fun PokemonInfoPanel(
    pokemon: PokemonRecord,
    gender: PokemonGender,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current
    Column(
        modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState()).testTag("pokemon_info_panel"),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
    ) {
        Text(
            text = "#${pokemon.id} ${pokemon.nameEn}",
            color = ScannerGraphiteLight,
            fontSize = if (compact) 8.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = if (compact) 8.sp else 10.sp, stepSize = 0.5.sp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = pokemon.localizedDisplayName(language),
                color = ScannerOutline,
                fontSize = if (compact) 18.sp else 22.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = if (compact) 18.sp else 22.sp, stepSize = 0.5.sp),
                modifier = Modifier.weight(1f),
            )
            Text(gender.symbol, color = gender.color, fontSize = if (compact) 17.sp else 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.testTag("pokemon_gender_symbol"))
        }
        Text(
            text = pokemon.nameJa.ifBlank { "--" },
            color = ScannerGraphite,
            fontSize = if (compact) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = if (compact) 9.sp else 11.sp, stepSize = 0.5.sp),
        )
        Text(
            text = if (language == AppLanguage.Chinese) categoryLabel(pokemon.category) else pokemon.category,
            color = ScannerOutline,
            fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = appText(
                "身高 ${pokemon.height}    体重 ${pokemon.weight}",
                "Height ${pokemon.height}    Weight ${pokemon.weight}",
            ),
            color = ScannerGraphiteLight,
            fontSize = if (compact) 8.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PokemonTypeSection(pokemon.types, compact)
        PokemonAbilitySection(pokemon.abilities, compact)
    }
}

@Composable
internal fun PokemonTypeSection(types: List<String>, compact: Boolean) {
    Row(
        modifier = Modifier.testTag("pokemon_types"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(appText("属性：", "Type: "), color = ScannerOutline, fontSize = if (compact) 9.sp else 11.sp, fontWeight = FontWeight.Black)
        types.forEach { type ->
            Text(
                text = type,
                color = typeTextColor(type),
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .background(typeColor(type), RoundedCornerShape(4.dp))
                    .border(1.dp, ScannerBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

internal fun categoryLabel(category: String): String =
    if (category.endsWith("宝可梦")) category else "${category}宝可梦"

@Composable
internal fun PokemonAbilitySection(abilities: PokemonAbilities, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.testTag("pokemon_abilities")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(appText("特性：", "Abilities: "), color = ScannerOutline, fontSize = if (compact) 9.sp else 11.sp, fontWeight = FontWeight.Black)
            Text(
                text = abilities.normal.ifEmpty { listOf("--") }.joinToString(" · "),
                color = ScannerGraphite,
                fontSize = if (compact) 9.sp else 10.sp,
            )
        }
        if (abilities.hidden.isNotEmpty()) {
            Text(
                text = appText("隐藏特性：", "Hidden: ") + abilities.hidden.joinToString(" · "),
                color = ScannerRedDark,
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun PokemonStatsRadarChart(pokemon: PokemonRecord, compact: Boolean, modifier: Modifier = Modifier) {
    val values = listOf(
        pokemon.stats.hp,
        pokemon.stats.attack,
        pokemon.stats.defense,
        pokemon.stats.specialAttack,
        pokemon.stats.specialDefense,
        pokemon.stats.speed,
    )
    val labels = listOf("HP", appText("攻击", "ATK"), appText("防御", "DEF"), appText("特攻", "SpA"), appText("特防", "SpD"), appText("速度", "SPE"))
    val progress = remember(pokemon.key) { Animatable(0f) }
    LaunchedEffect(pokemon.key) {
        progress.snapTo(0f)
        progress.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 210f))
    }
    Box(modifier.testTag("pokemon_stats_radar")) {
    Canvas(Modifier.fillMaxSize()) {
        val center = androidx.compose.ui.geometry.Offset(size.width * 0.46f, size.height / 2f)
        val radius = minOf(size.width, size.height) * 0.24f
        fun point(index: Int, distance: Float): androidx.compose.ui.geometry.Offset {
            val angle = -PI / 2 + index * PI / 3
            return androidx.compose.ui.geometry.Offset(
                center.x + cos(angle).toFloat() * distance,
                center.y + sin(angle).toFloat() * distance,
            )
        }
        repeat(5) { ring ->
            val ringPath = Path().apply {
                repeat(6) { index ->
                    val p = point(index, radius * (ring + 1) / 5f)
                    if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(ringPath, ScannerGraphiteLight.copy(alpha = 0.34f), style = Stroke(if (ring == 4) 2.dp.toPx() else 1.dp.toPx()))
        }
        repeat(6) { index -> drawLine(ScannerGraphiteLight.copy(alpha = 0.36f), center, point(index, radius), 1.dp.toPx()) }

        val dataPath = Path().apply {
            values.forEachIndexed { index, value ->
                val staggered = ((progress.value * 1.45f) - index * 0.09f).coerceIn(0f, 1.08f)
                val distance = radius * (value.coerceIn(0, 180) / 180f) * staggered
                val p = point(index, distance)
                if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(dataPath, ScannerRed.copy(alpha = 0.30f))
        drawPath(dataPath, ScannerRedDark, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        values.forEachIndexed { index, value ->
            val staggered = ((progress.value * 1.45f) - index * 0.09f).coerceIn(0f, 1.08f)
            drawCircle(ScannerOutline, 3.dp.toPx(), point(index, radius * (value.coerceIn(0, 180) / 180f) * staggered))
        }
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(27, 34, 38)
            textSize = if (compact) 8.sp.toPx() else 9.sp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
        }
        labels.forEachIndexed { index, label ->
            val p = point(index, radius + if (compact) 12.dp.toPx() else 15.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("$label ${values[index]}", p.x, p.y + paint.textSize / 3f, paint)
        }
    }
    }
}

internal fun typeColor(type: String): Color = when (type) {
    "火" -> Color(0xFFEF6C57); "水" -> Color(0xFF4B91D1); "草" -> Color(0xFF65A84A)
    "电" -> Color(0xFFF4D34D); "冰" -> Color(0xFF82D6D1); "格斗" -> Color(0xFFBD4B45)
    "毒" -> Color(0xFFA45AA5); "地面" -> Color(0xFFD9B45B); "飞行" -> Color(0xFF91A7DB)
    "超能力" -> Color(0xFFE9688B); "虫" -> Color(0xFFA5B33B); "岩石" -> Color(0xFFB39A4B)
    "幽灵" -> Color(0xFF6E6496); "龙" -> Color(0xFF6D59C8); "恶" -> Color(0xFF71635D)
    "钢" -> Color(0xFF9DA8B2); "妖精" -> Color(0xFFE99BB2); else -> Color(0xFF9A9A8F)
}

internal fun typeTextColor(type: String): Color = if (type in setOf("电", "冰", "地面", "飞行", "虫", "岩石", "钢", "妖精", "一般")) ScannerOutline else Color.White

@Composable
internal fun PokemonThumbnail(
    pokemon: PokemonRecord,
    modifier: Modifier = Modifier,
    appearanceIndex: Int = 0,
    shiny: Boolean = false,
    collected: Boolean = true,
) {
    val context = LocalContext.current
    val appearance = pokemon.appearances.getOrNull(appearanceIndex)
    val normalAsset = appearance?.imageAsset ?: pokemon.imageAsset
    val imageAsset = if (shiny) appearance?.shinyImageAsset ?: pokemon.shinyImageAsset ?: normalAsset else normalAsset
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, imageAsset) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                ResourceBundleRepository(context).openAsset(imageAsset).use { input -> BitmapFactory.decodeStream(input) }
            }
        }.getOrNull()
    }
    val lockedFilter = remember(collected) {
        if (collected) null else ColorFilter.colorMatrix(
            ColorMatrix().apply { setToSaturation(0f) },
        )
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = pokemon.nameZh,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                colorFilter = lockedFilter,
                alpha = if (collected) 1f else 0.42f,
            )
        }
    }
}

@Composable
internal fun AssetThumbnail(asset: String, modifier: Modifier = Modifier, description: String? = null) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, asset) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                ResourceBundleRepository(context).openAsset(asset).use { input -> BitmapFactory.decodeStream(input) }
            }
        }.getOrNull()
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
    }
}

@Composable
internal fun SpecimenGlyph(
    pokemon: PokemonRecord,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, pokemon.imageAsset) {
        value = runCatching {
            ResourceBundleRepository(context).openAsset(pokemon.imageAsset).use { input -> BitmapFactory.decodeStream(input) }
        }.getOrNull()
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().aspectRatio(1f)) {
            drawCircle(color = ScannerLensBlue.copy(alpha = 0.12f), radius = size.minDimension * 0.46f)
            drawCircle(
                color = ScannerLensBlue.copy(alpha = 0.4f),
                radius = size.minDimension * 0.37f,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawLine(
                color = ScannerLensBlue.copy(alpha = 0.32f),
                start = androidx.compose.ui.geometry.Offset(center.x, size.height * 0.03f),
                end = androidx.compose.ui.geometry.Offset(center.x, size.height * 0.97f),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = ScannerLensBlue.copy(alpha = 0.32f),
                start = androidx.compose.ui.geometry.Offset(size.width * 0.03f, center.y),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.97f, center.y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = pokemon.nameZh,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
        Text(
            text = pokemon.id,
            color = ScannerOutline,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
internal fun TypeBadge(type: String) {
    val color = when (type) {
        "\u8349" -> ScannerSignalGreen
        "\u6bd2" -> Color(0xFF8A5BA7)
        "\u7535" -> ScannerWarning
        "\u706b" -> Color(0xFFD65245)
        "\u6c34" -> ScannerLensBlue
        "\u51b0" -> Color(0xFF6AAFC1)
        "\u683c\u6597" -> Color(0xFFA64B3C)
        "\u8d85\u80fd\u529b" -> Color(0xFFC95A83)
        "\u5e7d\u7075" -> Color(0xFF665B8A)
        "\u6076" -> ScannerGraphite
        "\u94a2" -> Color(0xFF66717A)
        "\u5996\u7cbe" -> Color(0xFFC66F9C)
        else -> ScannerGraphiteLight
    }
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Text(
            text = type,
            color = if (type == "\u7535") ScannerOutline else Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ResultMetric(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = ScannerGraphiteLight,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Text(
            text = value,
            color = ScannerOutline,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
internal fun ScannerPreview() {
    val previewCatalog = remember {
        PokemonCatalog.fromRecords(
            listOf(
                PokemonRecord(
                    key = "p0025_v00",
                    id = "0025",
                    nameZh = "\u76ae\u5361\u4e18",
                    nameEn = "Pikachu",
                    types = listOf("\u7535"),
                    attributeLabel = "电属性宝可梦",
                    category = "\u9f20\u5b9d\u53ef\u68a6",
                    height = "0.4m",
                    weight = "6.0kg",
                    description = "\u4f1a\u628a\u7535\u6c14\u50a8\u5b58\u5728\u8138\u988a\u7684\u7535\u56ca\u91cc\u3002",
                    profile = "皮卡丘会把电储存在脸颊两侧的电囊里。",
                    imageAsset = "pokemon/images/p0025_v00.png",
                ),
            ),
        )
    }
    PokedexTheme {
        ScannerScreenContent(
            state = ScannerUiState(
                mode = ScannerMode.Preview,
            ),
            catalog = previewCatalog,
            surfaceRequest = null,
            useFakeCamera = true,
            onPrimaryAction = {},
            onOpenCatalog = {},
            onBackToCamera = {},
            onSaveSettings = {},
            onTestSettings = {},
            onPreviewNarration = {},
            onRequestPermission = {},
            onRetry = {},
        )
    }
}

