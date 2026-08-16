/* Scanner camera and scan-state UI. */
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
internal fun PrimaryContentSlot(
    page: ScannerPage,
    registry: PageRegistry,
    swipeDirection: PageOrbitalDirection,
    swipeProgress: Float,
    host: PokedexPageHost,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val activePage = registry.enabledPage(page)
        val primaryPage = activePage?.primaryNavigation == true
        val incomingPage = if (primaryPage) {
            registry.primaryDestination(page, swipeDirection)?.let(registry::enabledPage)
        } else {
            null
        }
        val progress = if (primaryPage) swipeProgress.coerceIn(0f, 1f) else 0f
        val direction = if (swipeDirection == PageOrbitalDirection.Forward) -1f else 1f
        val widthPx = constraints.maxWidth.toFloat()

        fun layerAlpha(active: Boolean): Float = when {
            !primaryPage -> 0f
            active -> 1f - progress * 0.35f
            else -> progress
        }

        if (!primaryPage) {
            activePage?.let { definition ->
                Box(Modifier.fillMaxSize().testTag(definition.contentSlotTestTag)) {
                    definition.content(host)
                }
            }
            return@BoxWithConstraints
        }

        activePage?.let { definition ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .graphicsLayer {
                        alpha = layerAlpha(true)
                        translationX = direction * widthPx * progress
                    }
                    .testTag(definition.contentSlotTestTag),
            ) {
                definition.content(host)
            }
        }
        incomingPage?.let { definition ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .graphicsLayer {
                        alpha = layerAlpha(false)
                        translationX = -direction * widthPx * (1f - progress)
                    }
                    .testTag(definition.contentSlotTestTag),
            ) {
                definition.content(host)
            }
        }
    }
}

@Composable
internal fun ScannerViewport(
    state: ScannerUiState,
    surfaceRequest: SurfaceRequest?,
    useFakeCamera: Boolean,
    catalog: PokemonCatalog,
    detailsRepository: PokemonDetailsRepository,
    detailsPageIndex: Int,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onOpenPokemonDetail: (Int) -> Unit,
    onOpenPokemonGallery: (Int) -> Unit = {},
    onZoomCamera: (Float) -> Unit,
) {
    val mode = state.mode
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("scanner_viewport"),
    ) {
        AnimatedContent(
            targetState = mode,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(5.dp)),
            contentKey = { targetMode ->
                when (targetMode) {
                    ScannerMode.Preview, ScannerMode.Capturing -> "camera"
                    else -> targetMode
                }
            },
            label = "scanner viewport content",
        ) { targetMode ->
            when (targetMode) {
                ScannerMode.PermissionRequired -> PermissionPanel(onRequestPermission)
                ScannerMode.Preview,
                ScannerMode.Capturing,
                -> CameraViewport(
                    useFakeCamera = useFakeCamera,
                    surfaceRequest = surfaceRequest,
                    mode = targetMode,
                    capturedImageJpeg = state.capturedImageJpeg,
                    onZoomCamera = onZoomCamera,
                )

                is ScannerMode.Result -> {
                    val pokemon = catalog.recordAt(targetMode.candidate.pokemonIndex)
                    PokemonResult(
                        pokemon = pokemon,
                        catalog = catalog,
                        capturedImageJpeg = state.capturedImageJpeg,
                        collected = pokemon.key in state.collectedKeys,
                        detailsRepository = detailsRepository,
                        pageIndex = detailsPageIndex,
                        onOpenPokemonDetail = onOpenPokemonDetail,
                        onOpenGallery = { onOpenPokemonGallery(targetMode.candidate.pokemonIndex) },
                    )
                }

                is ScannerMode.Error -> ErrorPanel(
                    message = targetMode.message,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
internal fun CameraViewport(
    useFakeCamera: Boolean,
    surfaceRequest: SurfaceRequest?,
    mode: ScannerMode,
    capturedImageJpeg: ByteArray?,
    onZoomCamera: (Float) -> Unit,
) {
    val zoomModifier = if (mode == ScannerMode.Preview && !useFakeCamera) {
        Modifier.pointerInput(Unit) {
            detectTransformGestures { _, _, zoom, _ -> onZoomCamera(zoom) }
        }
    } else Modifier
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerCanvas),
    ) {
        when {
            mode == ScannerMode.Capturing && capturedImageJpeg != null -> CapturedPhoto(
                imageJpeg = capturedImageJpeg,
            )
            useFakeCamera -> FakeCameraPreview()
            surfaceRequest != null -> CameraXViewfinder(
                surfaceRequest = surfaceRequest,
                modifier = Modifier.fillMaxSize().then(zoomModifier),
            )
            else -> CameraStartingPanel()
        }

        ScanHud(mode = mode)

        if (mode == ScannerMode.Capturing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ScannerCanvas.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.scanner_analyzing),
                        color = ScannerPanel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .width(176.dp)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("thinking_progress"),
                        color = ScannerWarning,
                        trackColor = ScannerPanel.copy(alpha = 0.24f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CapturedPhoto(imageJpeg: ByteArray) {
    val bitmap = remember(imageJpeg) {
        BitmapFactory.decodeByteArray(imageJpeg, 0, imageJpeg.size)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerCanvas)
            .testTag("captured_photo"),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Captured Pokemon",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
    }
}

@Composable
internal fun CameraStartingPanel() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerCanvas),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = ScannerLensBlue,
            strokeWidth = 3.dp,
        )
    }
}

@Composable
internal fun FakeCameraPreview() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(ScannerCanvas)
        val grid = ScannerPanel.copy(alpha = 0.08f)
        for (i in 1 until 8) {
            val x = size.width * i / 8f
            drawLine(grid, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
        }
        for (i in 1 until 6) {
            val y = size.height * i / 6f
            drawLine(grid, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
        }

        drawCircle(
            color = ScannerLensBlue.copy(alpha = 0.22f),
            radius = size.minDimension * 0.23f,
            center = center,
        )
        drawCircle(
            color = ScannerPanel.copy(alpha = 0.14f),
            radius = size.minDimension * 0.12f,
            center = center.copy(y = center.y - size.minDimension * 0.11f),
        )
        val body = Path().apply {
            moveTo(center.x - size.minDimension * 0.18f, center.y + size.minDimension * 0.2f)
            quadraticTo(center.x, center.y - size.minDimension * 0.02f, center.x + size.minDimension * 0.18f, center.y + size.minDimension * 0.2f)
            close()
        }
        drawPath(body, ScannerPanel.copy(alpha = 0.12f))
    }
}

@Composable
internal fun ScanHud(mode: ScannerMode) {
    val transition = rememberInfiniteTransition(label = "scanner line")
    val scanPosition by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mode == ScannerMode.Capturing) 620 else 1800,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan position",
    )
    val hudColor = if (mode == ScannerMode.Capturing) ScannerWarning else ScannerSuccess

    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerLength = size.minDimension * 0.13f
            val stroke = 2.dp.toPx()

            fun corner(x: Float, y: Float, horizontalDirection: Float, verticalDirection: Float) {
                drawLine(
                    color = hudColor,
                    start = androidx.compose.ui.geometry.Offset(x, y),
                    end = androidx.compose.ui.geometry.Offset(x + cornerLength * horizontalDirection, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Square,
                )
                drawLine(
                    color = hudColor,
                    start = androidx.compose.ui.geometry.Offset(x, y),
                    end = androidx.compose.ui.geometry.Offset(x, y + cornerLength * verticalDirection),
                    strokeWidth = stroke,
                    cap = StrokeCap.Square,
                )
            }

            corner(0f, 0f, 1f, 1f)
            corner(size.width, 0f, -1f, 1f)
            corner(0f, size.height, 1f, -1f)
            corner(size.width, size.height, -1f, -1f)
            drawLine(
                color = hudColor.copy(alpha = 0.58f),
                start = androidx.compose.ui.geometry.Offset(8.dp.toPx(), size.height * scanPosition),
                end = androidx.compose.ui.geometry.Offset(size.width - 8.dp.toPx(), size.height * scanPosition),
                strokeWidth = 2.dp.toPx(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
        }
    }
}

@Composable
internal fun HudLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .background(ScannerOutline.copy(alpha = 0.72f), RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = ScannerPanel,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    )
}

@Composable
internal fun PermissionPanel(onRequestPermission: () -> Unit) {
    MessagePanel(
        title = stringResource(R.string.camera_permission_title),
        message = stringResource(R.string.camera_permission_body),
        action = stringResource(R.string.camera_permission_action),
        actionTag = "permission_button",
        onAction = onRequestPermission,
    )
}

@Composable
internal fun ErrorPanel(
    message: String,
    onRetry: () -> Unit,
) {
    val isCameraError = message.startsWith("相机") || message.startsWith("拍照") || message.startsWith("Camera") || message.startsWith("Capture")
    MessagePanel(
        title = stringResource(
            if (isCameraError) R.string.camera_unavailable else R.string.recognition_unavailable,
        ),
        message = message,
        action = stringResource(R.string.camera_retry),
        actionTag = "retry_button",
        onAction = onRetry,
    )
}

@Composable
internal fun MessagePanel(
    title: String,
    message: String,
    action: String,
    actionTag: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerGraphite)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CameraGlyph(modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            color = ScannerPanel,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            color = ScannerPanel.copy(alpha = 0.76f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            letterSpacing = 0.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAction,
            modifier = Modifier.testTag(actionTag),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ScannerRed,
                contentColor = ScannerPanel,
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = action.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
internal fun CameraGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 3.dp.toPx()
        drawRoundRect(
            color = ScannerPanel,
            topLeft = androidx.compose.ui.geometry.Offset(2.dp.toPx(), size.height * 0.22f),
            size = androidx.compose.ui.geometry.Size(size.width - 4.dp.toPx(), size.height * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
            style = Stroke(width = stroke),
        )
        drawCircle(
            color = ScannerLensBlue,
            radius = size.minDimension * 0.18f,
            center = center.copy(y = center.y + size.height * 0.04f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = ScannerPanel,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.22f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.08f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ScannerPanel,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.08f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.22f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

