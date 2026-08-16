/* Scanner catalog UI. */
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
internal fun CatalogPage(
    catalog: PokemonCatalog,
    collectedKeys: Set<String>,
    onPokemonClick: (PokemonRecord) -> Unit,
) {
    val context = LocalContext.current
    val scrollPreferences = remember(context.applicationContext) {
        context.applicationContext.getSharedPreferences("catalog_scroll", android.content.Context.MODE_PRIVATE)
    }
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val matches = remember(normalizedQuery, catalog.records) {
        if (normalizedQuery.isEmpty()) {
            catalog.records
        } else {
            catalog.records.filter { pokemon ->
                pokemon.id.lowercase(Locale.ROOT).contains(normalizedQuery.removePrefix("#")) ||
                    pokemon.nameZh.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    pokemon.nameEn.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    pokemon.nameJa.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
        }
    }
    val savedKey = remember { scrollPreferences.getString("record_key", null) }
    val savedIndex = remember(savedKey, catalog.records) {
        savedKey?.let { key -> catalog.records.indexOfFirst { it.key == key }.takeIf { it >= 0 } }
            ?: scrollPreferences.getInt("record_index", 0).coerceIn(0, (catalog.records.size - 1).coerceAtLeast(0))
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedIndex,
        initialFirstVisibleItemScrollOffset = scrollPreferences.getInt("record_offset", 0).coerceAtLeast(0),
    )
    LaunchedEffect(listState, normalizedQuery) {
        if (normalizedQuery.isNotEmpty()) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                delay(250)
                val record = catalog.records.getOrNull(index) ?: return@collectLatest
                scrollPreferences.edit()
                    .putString("record_key", record.key)
                    .putInt("record_index", index)
                    .putInt("record_offset", offset)
                    .apply()
            }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerPanel, RoundedCornerShape(10.dp))
            .border(4.dp, ScannerBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("catalog_page"),
    ) {
        Text(
            text = appText("全国图鉴 // ${matches.size} 条记录", "NATIONAL POKÉDEX // ${matches.size} RECORDS"),
            color = ScannerOutline,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("catalog_search"),
            singleLine = true,
            label = { Text(appText("搜索编号、中文、英文或日文名", "Search number or Pokémon name")) },
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(
                items = matches,
                key = { _, pokemon -> pokemon.key },
            ) { _, pokemon ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(ScannerCanvas.copy(alpha = 0.06f), RoundedCornerShape(3.dp))
                        .border(1.dp, ScannerGraphiteLight.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                        .clickable { onPokemonClick(pokemon) }
                        .testTag("catalog_item_${pokemon.key}")
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PokemonThumbnail(
                        pokemon = pokemon,
                        collected = pokemon.key in collectedKeys,
                        modifier = Modifier.size(42.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#${pokemon.id}  ${pokemon.localizedDisplayName(LocalAppLanguage.current)}",
                            color = ScannerOutline,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${pokemon.nameEn}  ${pokemon.nameJa}",
                            color = ScannerGraphiteLight,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Full local record rendered after a catalog row is selected. / 点击图鉴条目后展示完整本地参数。 */
@Composable
internal fun CatalogDetailPage(
    pokemon: PokemonRecord,
    catalog: PokemonCatalog,
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
        onOpenPokemonDetail = onOpenPokemonDetail,
        onOpenGallery = onOpenGallery,
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerPanel, RoundedCornerShape(8.dp))
            .border(4.dp, ScannerBorder, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag("catalog_detail_page"),
    )
}

