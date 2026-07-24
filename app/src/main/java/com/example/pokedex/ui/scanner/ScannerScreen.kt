/* Compose scanner screens, dialogs, and Android UI integration. / Compose 扫描页面、弹窗与 Android UI 集成。 */
package com.example.pokedex.ui.scanner

import android.Manifest
import android.content.Intent
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pokedex.R
import com.example.pokedex.ui.theme.PokedexTheme
import com.example.pokedex.ui.theme.ScannerCanvas
import com.example.pokedex.ui.theme.ScannerError
import com.example.pokedex.ui.theme.ScannerGraphite
import com.example.pokedex.ui.theme.ScannerGraphiteLight
import com.example.pokedex.ui.theme.ScannerLensBlue
import com.example.pokedex.ui.theme.ScannerOutline
import com.example.pokedex.ui.theme.ScannerPanel
import com.example.pokedex.ui.theme.ScannerRed
import com.example.pokedex.ui.theme.ScannerRedDark
import com.example.pokedex.ui.theme.ScannerSignalGreen
import com.example.pokedex.ui.theme.ScannerSuccess
import com.example.pokedex.ui.theme.ScannerWarning
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PokedexScannerScreen(
    modifier: Modifier = Modifier,
    scannerViewModel: ScannerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by scannerViewModel.uiState.collectAsState()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var availableNarrationVoices by remember { mutableStateOf(emptyList<NarratorVoiceOption>()) }
    var pendingSoundImport by remember { mutableStateOf<AppSoundEffect?>(null) }
    val narrator = remember(context.applicationContext) {
        AndroidPokemonNarrator(
            context = context.applicationContext,
            onUnavailable = { message ->
                mainHandler.post {
                    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
                }
            },
            onVoicesAvailable = { voices ->
                mainHandler.post { availableNarrationVoices = voices }
            },
        )
    }
    val soundPlayer = remember(context.applicationContext) {
        AndroidPokemonSoundPlayer(
            context = context.applicationContext,
            store = SoundEffectStore(context.applicationContext),
            onUnavailable = { message ->
                mainHandler.post {
                    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
                }
            },
        )
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        scannerViewModel.onCameraPermissionChanged(granted)
    }
    val languageModelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scannerViewModel.importModel(ModelRole.LANGUAGE, uri)
    }
    val visionModelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scannerViewModel.importModel(ModelRole.VISION, uri)
    }
    val soundEffectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val effect = pendingSoundImport
        pendingSoundImport = null
        if (effect != null && uri != null) scannerViewModel.importSoundEffect(effect, uri)
    }

    val cameraSession = rememberCameraSession(
        enabled = hasCameraPermission && state.mode == ScannerMode.Preview,
        onError = scannerViewModel::reportError,
    )

    fun capture() {
        if (state.mode != ScannerMode.Preview) return
        cameraSession.capture(
            onCaptured = { scannerViewModel.recognize(ScannerImageSource.CameraFile(it)) },
            onError = scannerViewModel::reportError,
        )
    }

    fun stopAllAudio() {
        narrator.stop()
        soundPlayer.stop()
    }

    LaunchedEffect(hasCameraPermission) {
        scannerViewModel.onCameraPermissionChanged(hasCameraPermission)
    }
    DisposableEffect(narrator, soundPlayer) {
        onDispose {
            narrator.close()
            soundPlayer.close()
        }
    }
    LaunchedEffect(state.narrationSettings, narrator) {
        narrator.updateSettings(state.narrationSettings)
    }
    LaunchedEffect(state.appLanguage, narrator) {
        narrator.updateLanguage(state.appLanguage)
    }
    LaunchedEffect(state.soundEffectSettings, soundPlayer) {
        soundPlayer.updateSettings(state.soundEffectSettings)
    }
    LaunchedEffect(state.page, state.mode) {
        val canNarrate = state.page == ScannerPage.CatalogDetail ||
            (state.page == ScannerPage.Scanner && state.mode is ScannerMode.Result)
        if (!canNarrate) {
            stopAllAudio()
        }
    }
    LaunchedEffect(scannerViewModel, context, narrator) {
        scannerViewModel.events.collect { event ->
            when (event) {
                is ScannerEvent.Notice -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                is ScannerEvent.SpeakPokemon -> {
                    val currentState = scannerViewModel.uiState.value
                    if (currentState.canNarratePokemon(event.pokemonIndex)) {
                        narrator.speak(
                            scannerViewModel.catalog.recordAt(event.pokemonIndex),
                            currentState.appLanguage,
                        )
                    }
                }
                is ScannerEvent.PlaySound -> {
                    soundPlayer.updateSettings(scannerViewModel.uiState.value.soundEffectSettings)
                    soundPlayer.play(event.effect)
                }
                is ScannerEvent.ShareFeedback -> {
                    runCatching {
                        val file = File(event.filePath)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, localizedText(state.appLanguage, "导出宝可梦标注数据", "Export Pokémon feedback")))
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            error.message ?: localizedText(state.appLanguage, "无法分享标注数据", "Unable to share feedback data"),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    ScannerScreenContent(
        state = state,
        catalog = scannerViewModel.catalog,
        surfaceRequest = cameraSession.surfaceRequest,
        useFakeCamera = false,
        availableNarrationVoices = availableNarrationVoices,
        soundAssets = state.soundAssets,
        onPrimaryAction = {
            stopAllAudio()
            if (state.page != ScannerPage.Scanner) {
                scannerViewModel.backToCamera()
            } else {
                when (state.mode) {
                    ScannerMode.Preview -> capture()
                    is ScannerMode.Result -> scannerViewModel.reset()
                    is ScannerMode.Error -> scannerViewModel.retry()
                    else -> Unit
                }
            }
        },
        onOpenCatalog = {
            stopAllAudio()
            scannerViewModel.openCatalog()
        },
        onOpenPokemonDetail = scannerViewModel::openPokemonDetail,
        onBackToCamera = {
            stopAllAudio()
            scannerViewModel.navigateBack()
        },
        onMoveResult = scannerViewModel::moveResult,
        onSaveSettings = scannerViewModel::saveScannerSettings,
        onSaveSettingsAndReload = scannerViewModel::saveSettingsAndReload,
        onTestSettings = scannerViewModel::testScannerSettings,
        onPreviewNarration = narrator::preview,
        onPreviewSound = { effect, settings ->
            soundPlayer.updateSettings(settings)
            soundPlayer.play(effect)
        },
        onImportSound = { effect ->
            pendingSoundImport = effect
            soundEffectLauncher.launch(arrayOf("audio/*"))
        },
        onResetSound = scannerViewModel::resetSoundEffect,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onRetry = scannerViewModel::retry,
        onImportLanguage = { languageModelLauncher.launch(arrayOf("*/*")) },
        onImportVision = { visionModelLauncher.launch(arrayOf("*/*")) },
        onConfirmFeedback = scannerViewModel::confirmSelectedCandidate,
        onCorrectFeedback = scannerViewModel::correctRecognition,
        onCorrectNotPokemon = scannerViewModel::correctAsNotPokemon,
        onUndoFeedback = scannerViewModel::undoFeedback,
        onExportFeedback = scannerViewModel::exportFeedback,
        modifier = modifier,
    )
}

@Composable
internal fun ScannerScreenContent(
    state: ScannerUiState,
    catalog: PokemonCatalog,
    surfaceRequest: SurfaceRequest?,
    useFakeCamera: Boolean,
    availableNarrationVoices: List<NarratorVoiceOption> = emptyList(),
    soundAssets: List<SoundAssetStatus> = emptyList(),
    onPrimaryAction: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenPokemonDetail: (Int) -> Unit = {},
    onBackToCamera: () -> Unit,
    onMoveResult: (Int) -> Unit,
    onSaveSettings: (ScannerSettings) -> Unit,
    onSaveSettingsAndReload: (ScannerSettings) -> Unit,
    onTestSettings: (ScannerSettings) -> Unit,
    onPreviewNarration: (NarrationSettings) -> Unit,
    onPreviewSound: (AppSoundEffect, SoundEffectSettings) -> Unit = { _, _ -> },
    onImportSound: (AppSoundEffect) -> Unit = {},
    onResetSound: (AppSoundEffect) -> Unit = {},
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onImportLanguage: () -> Unit,
    onImportVision: () -> Unit,
    onConfirmFeedback: () -> Unit,
    onCorrectFeedback: (Int) -> Unit,
    onCorrectNotPokemon: () -> Unit,
    onUndoFeedback: () -> Unit,
    onExportFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalAppLanguage provides state.appLanguage) {
    var correctionDialogVisible by remember { mutableStateOf(false) }
    var settingsDialogVisible by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScannerCanvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        ScannerShell(
            mode = state.mode,
            recognitionMode = state.recognitionMode,
            canMoveResult = state.candidates.size > 1,
            feedbackCount = state.feedback.sampleCount,
            onPrimaryAction = onPrimaryAction,
            page = state.page,
            onOpenCatalog = onOpenCatalog,
            onBackToCamera = onBackToCamera,
            onMoveResult = onMoveResult,
            onOpenSettings = { settingsDialogVisible = true },
            onExportFeedback = onExportFeedback,
            modifier = Modifier
                .fillMaxSize(),
        ) {
            when (state.page) {
                ScannerPage.Catalog -> CatalogPage(
                    catalog = catalog,
                    onPokemonClick = { pokemon ->
                        catalog.records.indexOfFirst { it.key == pokemon.key }
                            .takeIf { it >= 0 }
                            ?.let(onOpenPokemonDetail)
                    },
                )
                ScannerPage.CatalogDetail -> state.catalogPokemonIndex?.let { pokemonIndex ->
                    CatalogDetailPage(pokemon = catalog.recordAt(pokemonIndex))
                }
                ScannerPage.Scanner -> {
                ScannerViewport(
                    state = state,
                    surfaceRequest = surfaceRequest,
                    useFakeCamera = useFakeCamera,
                    catalog = catalog,
                    modelStatus = state.modelStatus,
                    onRequestPermission = onRequestPermission,
                    onRetry = onRetry,
                    onImportLanguage = onImportLanguage,
                    onImportVision = onImportVision,
                    onConfirmFeedback = onConfirmFeedback,
                    onRequestCorrection = { correctionDialogVisible = true },
                    onUndoFeedback = onUndoFeedback,
                    onExportFeedback = onExportFeedback,
                )
                }
            }
        }
    }
    if (correctionDialogVisible) {
        CorrectionDialog(
            catalog = catalog,
            onDismiss = { correctionDialogVisible = false },
            onSelect = { pokemonIndex ->
                correctionDialogVisible = false
                onCorrectFeedback(pokemonIndex)
            },
            onSelectNotPokemon = {
                correctionDialogVisible = false
                onCorrectNotPokemon()
            },
        )
    }
    if (settingsDialogVisible) {
        RecognitionSettingsDialog(
            scannerSettings = state.scannerSettings,
            activeRuntimeOptions = state.activeRuntimeOptions,
            lastInferenceMillis = state.lastInferenceMillis,
            settingsTest = state.settingsTest,
            hasCapturedImage = state.capturedImageJpeg != null,
            canReloadModels = state.modelStatus.allReady,
            availableNarrationVoices = availableNarrationVoices,
            soundAssets = soundAssets,
            onPreviewNarration = onPreviewNarration,
            onPreviewSound = onPreviewSound,
            onImportSound = onImportSound,
            onResetSound = onResetSound,
            onSave = { newSettings ->
                onSaveSettings(newSettings)
                settingsDialogVisible = false
            },
            onSaveAndReload = { newSettings ->
                onSaveSettingsAndReload(newSettings)
                settingsDialogVisible = false
            },
            onTest = onTestSettings,
            onDismiss = { settingsDialogVisible = false },
        )
    }
    }
}

@Composable
private fun ScannerViewport(
    state: ScannerUiState,
    surfaceRequest: SurfaceRequest?,
    useFakeCamera: Boolean,
    catalog: PokemonCatalog,
    modelStatus: ModelSetStatus,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onImportLanguage: () -> Unit,
    onImportVision: () -> Unit,
    onConfirmFeedback: () -> Unit,
    onRequestCorrection: () -> Unit,
    onUndoFeedback: () -> Unit,
    onExportFeedback: () -> Unit,
) {
    val mode = state.mode
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerPanel, RoundedCornerShape(10.dp))
            .border(4.dp, ScannerOutline, RoundedCornerShape(10.dp))
            .padding(10.dp)
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
                ScannerMode.ModelSetup -> ModelSetupPanel(
                    status = modelStatus,
                    onImportLanguage = onImportLanguage,
                    onImportVision = onImportVision,
                )
                ScannerMode.LoadingModels -> LoadingModelPanel()
                ScannerMode.PermissionRequired -> PermissionPanel(onRequestPermission)
                ScannerMode.Preview,
                ScannerMode.Capturing,
                -> CameraViewport(
                    useFakeCamera = useFakeCamera,
                    surfaceRequest = surfaceRequest,
                    mode = targetMode,
                    capturedImageJpeg = state.capturedImageJpeg,
                )

                is ScannerMode.Result -> {
                    val selected = state.candidates.getOrNull(targetMode.candidateIndex)
                    if (selected == null) {
                        ErrorPanel(message = appText("候选结果不可用", "Candidate result unavailable"), onRetry = onRetry)
                    } else {
                        PokemonResult(
                            pokemon = catalog.recordAt(selected.pokemonIndex),
                            capturedImageJpeg = state.capturedImageJpeg,
                            candidates = state.candidates,
                            selectedCandidateIndex = targetMode.candidateIndex,
                            catalog = catalog,
                            feedback = state.feedback,
                            onConfirmFeedback = onConfirmFeedback,
                            onRequestCorrection = onRequestCorrection,
                            onUndoFeedback = onUndoFeedback,
                            onExportFeedback = onExportFeedback,
                        )
                    }
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
private fun CameraViewport(
    useFakeCamera: Boolean,
    surfaceRequest: SurfaceRequest?,
    mode: ScannerMode,
    capturedImageJpeg: ByteArray?,
) {
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
                modifier = Modifier.fillMaxSize(),
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
private fun CapturedPhoto(imageJpeg: ByteArray) {
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
private fun CameraStartingPanel() {
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
private fun FakeCameraPreview() {
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
private fun ScanHud(mode: ScannerMode) {
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
            HudLabel(if (mode == ScannerMode.Capturing) "LOCKED" else "SEARCHING")
            HudLabel("CAM 01")
        }
    }
}

@Composable
private fun HudLabel(text: String) {
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
private fun PermissionPanel(onRequestPermission: () -> Unit) {
    MessagePanel(
        title = stringResource(R.string.camera_permission_title),
        message = stringResource(R.string.camera_permission_body),
        action = stringResource(R.string.camera_permission_action),
        actionTag = "permission_button",
        onAction = onRequestPermission,
    )
}

@Composable
private fun LoadingModelPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerGraphite)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = ScannerLensBlue, strokeWidth = 4.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.model_loading),
            color = ScannerPanel,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "MiniCPM-V 4.6 // CPU",
            color = ScannerPanel.copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun ModelSetupPanel(
    status: ModelSetStatus,
    onImportLanguage: () -> Unit,
    onImportVision: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerGraphite)
            .padding(18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.model_setup_title),
            color = ScannerPanel,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.model_setup_body),
            color = ScannerPanel.copy(alpha = 0.74f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(16.dp))
        ModelImportButton(
            label = stringResource(R.string.import_language_model),
            ready = status.languageReady,
            progress = if (status.importingRole == ModelRole.LANGUAGE) status.importProgress else null,
            onClick = onImportLanguage,
            tag = "import_language_model_button",
        )
        Spacer(Modifier.height(8.dp))
        ModelImportButton(
            label = stringResource(R.string.import_vision_model),
            ready = status.visionReady,
            progress = if (status.importingRole == ModelRole.VISION) status.importProgress else null,
            onClick = onImportVision,
            tag = "import_vision_model_button",
        )
        status.error?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = ScannerError,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ModelImportButton(
    label: String,
    ready: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    tag: String,
) {
    Column {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().testTag(tag),
            enabled = progress == null,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (ready) ScannerSignalGreen else ScannerRed,
                contentColor = ScannerPanel,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = if (ready) "$label  // ${stringResource(R.string.model_ready)}" else label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        progress?.let {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(ScannerOutline, RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(it)
                        .height(3.dp)
                        .background(ScannerWarning, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun ErrorPanel(
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
private fun MessagePanel(
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
private fun CameraGlyph(modifier: Modifier = Modifier) {
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

@Composable
private fun CatalogPage(
    catalog: PokemonCatalog,
    onPokemonClick: (PokemonRecord) -> Unit,
) {
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerPanel, RoundedCornerShape(10.dp))
            .border(4.dp, ScannerOutline, RoundedCornerShape(10.dp))
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
                    PokemonThumbnail(pokemon = pokemon, modifier = Modifier.size(42.dp))
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
private fun CatalogDetailPage(pokemon: PokemonRecord) {
    val language = LocalAppLanguage.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerPanel, RoundedCornerShape(8.dp))
            .border(4.dp, ScannerOutline, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag("catalog_detail_page"),
    ) {
        val expanded = maxWidth >= 560.dp
        val detailContent: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    appText("完整图鉴资料", "COMPLETE POKÉDEX RECORD"),
                    color = ScannerGraphiteLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "#${pokemon.id}  ${pokemon.localizedDisplayName(language)}",
                    color = ScannerOutline,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.testTag("catalog_detail_name"),
                )
                Text(
                    "${pokemon.nameEn} · ${pokemon.nameZh} · ${pokemon.nameJa.ifBlank { "--" }}",
                    color = ScannerGraphiteLight,
                    fontSize = 10.sp,
                )
                Text(
                    pokemon.localizedAttributeLabel(language),
                    color = ScannerRedDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    appText(
                        "分类：${categoryLabel(pokemon.category)}",
                        "Category: ${pokemon.category}",
                    ),
                    color = ScannerGraphite,
                    fontSize = 11.sp,
                )
                Text(
                    appText(
                        "身高：${pokemon.height}    体重：${pokemon.weight}",
                        "Height: ${pokemon.height}    Weight: ${pokemon.weight}",
                    ),
                    color = ScannerGraphite,
                    fontSize = 11.sp,
                )
                Text(
                    appText(
                        "特性：${pokemon.ability.ifBlank { "--" }}",
                        "Ability: ${pokemon.ability.ifBlank { "--" }}",
                    ),
                    color = ScannerGraphite,
                    fontSize = 11.sp,
                )
                PokemonStatsList(pokemon = pokemon, compact = false)
                Text(appText("图鉴说明", "POKÉDEX DESCRIPTION"), color = ScannerOutline, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(pokemon.description, color = ScannerGraphite, fontSize = 10.sp)
                Text(appText("习性资料", "BEHAVIOR PROFILE"), color = ScannerOutline, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(
                    pokemon.profile,
                    color = ScannerGraphite,
                    fontSize = 10.sp,
                    modifier = Modifier.testTag("catalog_detail_profile"),
                )
            }
        }
        if (expanded) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PokemonThumbnail(pokemon, Modifier.weight(0.42f).fillMaxHeight())
                Box(Modifier.weight(0.58f)) { detailContent() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PokemonThumbnail(
                    pokemon,
                    Modifier
                        .fillMaxWidth()
                        .weight(0.34f),
                )
                Box(Modifier.weight(0.66f)) { detailContent() }
            }
        }
    }
}

private enum class SettingsConsoleTab(val chinese: String, val english: String) {
    Basic("基础", "Basic"),
    Performance("性能", "Performance"),
    Decoding("解码", "Decoding"),
    Voice("语音", "Voice"),
    Sound("音效", "Sound"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecognitionSettingsDialog(
    scannerSettings: ScannerSettings,
    activeRuntimeOptions: ModelRuntimeOptions?,
    lastInferenceMillis: Long?,
    settingsTest: SettingsTestUiState,
    hasCapturedImage: Boolean,
    canReloadModels: Boolean,
    availableNarrationVoices: List<NarratorVoiceOption>,
    soundAssets: List<SoundAssetStatus>,
    onPreviewNarration: (NarrationSettings) -> Unit,
    onPreviewSound: (AppSoundEffect, SoundEffectSettings) -> Unit,
    onImportSound: (AppSoundEffect) -> Unit,
    onResetSound: (AppSoundEffect) -> Unit,
    onSave: (ScannerSettings) -> Unit,
    onSaveAndReload: (ScannerSettings) -> Unit,
    onTest: (ScannerSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTabName by rememberSaveable { mutableStateOf(SettingsConsoleTab.Basic.name) }
    val selectedTab = SettingsConsoleTab.valueOf(selectedTabName)
    var draft by remember(scannerSettings) { mutableStateOf(scannerSettings.sanitized()) }
    val draftRuntime = draft.recognitionTuning.modelRuntimeOptions()
    val runtimePending = activeRuntimeOptions != null && activeRuntimeOptions != draftRuntime
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .heightIn(max = 720.dp)
                .background(ScannerPanel, RoundedCornerShape(6.dp))
                .border(4.dp, ScannerOutline, RoundedCornerShape(6.dp))
                .padding(16.dp)
                .testTag("recognition_settings_dialog"),
        ) {
            Text(
                text = appText("识别参数控制台", "RECOGNITION CONSOLE"),
                color = ScannerOutline,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SettingsConsoleTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTabName = tab.name },
                        shape = SegmentedButtonDefaults.itemShape(index, SettingsConsoleTab.entries.size),
                        modifier = Modifier.testTag("settings_tab_${tab.name}"),
                    ) {
                        Text(appText(tab.chinese, tab.english), fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (selectedTab) {
                    SettingsConsoleTab.Basic -> BasicSettingsPanel(
                        settings = draft,
                        onChange = { draft = it },
                    )
                    SettingsConsoleTab.Performance -> PerformanceSettingsPanel(
                        tuning = draft.recognitionTuning,
                        activeRuntimeOptions = activeRuntimeOptions,
                        lastInferenceMillis = lastInferenceMillis,
                        runtimePending = runtimePending,
                        onChange = { tuning -> draft = draft.copy(recognitionTuning = tuning) },
                    )
                    SettingsConsoleTab.Decoding -> DecodingSettingsPanel(
                        tuning = draft.recognitionTuning,
                        onChange = { tuning -> draft = draft.copy(recognitionTuning = tuning) },
                    )
                    SettingsConsoleTab.Voice -> VoiceSettingsPanel(
                        narration = draft.narrationSettings,
                        availableVoices = availableNarrationVoices,
                        onChange = { narration -> draft = draft.copy(narrationSettings = narration) },
                        onPreview = onPreviewNarration,
                    )
                    SettingsConsoleTab.Sound -> SoundSettingsPanel(
                        settings = draft.soundEffectSettings,
                        assets = soundAssets,
                        onChange = { settings -> draft = draft.copy(soundEffectSettings = settings) },
                        onPreview = onPreviewSound,
                        onImport = onImportSound,
                        onReset = onResetSound,
                    )
                }
                if (settingsTest.message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append(settingsTest.message)
                            settingsTest.elapsedMillis?.let { append(" · ${it}ms") }
                        },
                        color = if (settingsTest.running) ScannerWarning else ScannerGraphite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("settings_test_status"),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { draft = resetSettingsTab(draft, selectedTab) },
                    modifier = Modifier.testTag("settings_reset_tab"),
                ) { Text(appText("恢复本页", "Reset tab"), color = ScannerGraphite, fontSize = 11.sp) }
                TextButton(
                    onClick = { draft = ScannerSettings() },
                    modifier = Modifier.testTag("settings_reset_all"),
                ) { Text(appText("全部默认", "Reset all"), color = ScannerGraphite, fontSize = 11.sp) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onTest(draft.sanitized()) },
                    enabled = hasCapturedImage && !runtimePending && !settingsTest.running,
                    modifier = Modifier.testTag("settings_test"),
                ) {
                    Text(if (settingsTest.running) appText("测试中", "Testing") else appText("测试配置", "Test"), fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(appText("取消", "Cancel"), color = ScannerRedDark, fontWeight = FontWeight.Black)
                }
                Button(
                    onClick = { onSave(draft.sanitized()) },
                    modifier = Modifier.testTag("settings_save"),
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerRedDark),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(appText("保存", "Save"), fontWeight = FontWeight.Black)
                }
            }
            if (runtimePending) {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { onSaveAndReload(draft.sanitized()) },
                    enabled = canReloadModels,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_save_reload"),
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerGraphite),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(appText("保存并重载模型", "Save & reload"), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun BasicSettingsPanel(
    settings: ScannerSettings,
    onChange: (ScannerSettings) -> Unit,
) {
    val tuning = settings.recognitionTuning
    Text(appText("识别预设", "Recognition preset"), color = ScannerOutline, fontWeight = FontWeight.Black, fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        RecognitionPreset.entries.forEachIndexed { index, preset ->
            val presetTuning = RecognitionTuning.forPreset(preset)
            SegmentedButton(
                selected = tuning == presetTuning,
                onClick = { onChange(settings.copy(recognitionTuning = presetTuning)) },
                shape = SegmentedButtonDefaults.itemShape(index, RecognitionPreset.entries.size),
                modifier = Modifier.testTag("recognition_preset_${preset.name}"),
            ) {
                Text(
                    when (preset) {
                        RecognitionPreset.Speed -> appText("速度", "Speed")
                        RecognitionPreset.Balanced -> appText("平衡", "Balanced")
                        RecognitionPreset.Quality -> appText("质量", "Quality")
                    },
                    fontSize = 10.sp,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    ParameterHeader(appText("候选数量", "Candidates"), appText("单个模式生成一项，多候选模式严格生成五项。", "Single returns one candidate; multiple returns exactly five."))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        RecognitionMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = settings.recognitionMode == mode,
                onClick = { onChange(settings.copy(recognitionMode = mode)) },
                shape = SegmentedButtonDefaults.itemShape(index, RecognitionMode.entries.size),
                modifier = Modifier.testTag(
                    if (mode == RecognitionMode.Single) "recognition_mode_single" else "recognition_mode_multiple",
                ),
            ) { Text(if (mode == RecognitionMode.Single) appText("单个", "Single") else appText("多个", "Multiple")) }
        }
    }
    IntSliderSetting(
        label = appText("单候选 Token", "Single max tokens"),
        help = appText("限制单个模式最多生成的文本长度。", "Maximum generated length in single mode."),
        value = tuning.singleMaxTokens,
        range = RecognitionTuning.MIN_SINGLE_TOKENS..RecognitionTuning.MAX_SINGLE_TOKENS,
        step = 8,
        tag = "single_tokens_slider",
        onChange = { onChange(settings.copy(recognitionTuning = tuning.copy(singleMaxTokens = it))) },
    )
    IntSliderSetting(
        label = appText("多候选 Token", "Multiple max tokens"),
        help = appText("过低可能截断五候选 JSON。", "A low value may truncate the five-candidate JSON."),
        value = tuning.multipleMaxTokens,
        range = RecognitionTuning.MIN_MULTIPLE_TOKENS..RecognitionTuning.MAX_MULTIPLE_TOKENS,
        step = 8,
        tag = "multiple_tokens_slider",
        onChange = { onChange(settings.copy(recognitionTuning = tuning.copy(multipleMaxTokens = it))) },
    )
    IntSliderSetting(
        label = appText("识别超时", "Timeout"),
        help = appText("达到时间后取消本次本地推理。", "Cancel local inference when the limit is reached."),
        value = tuning.timeoutSeconds,
        range = RecognitionTuning.MIN_TIMEOUT_SECONDS..RecognitionTuning.MAX_TIMEOUT_SECONDS,
        step = 5,
        suffix = "s",
        tag = "timeout_slider",
        onChange = { onChange(settings.copy(recognitionTuning = tuning.copy(timeoutSeconds = it))) },
    )
    DiscreteSliderSetting(
        label = appText("图片边长", "Image edge"),
        help = appText("更大尺寸保留更多细节，但视觉编码明显变慢。", "Larger images preserve detail but slow visual encoding."),
        value = tuning.imageMaxEdge,
        options = RecognitionTuning.IMAGE_EDGE_OPTIONS,
        suffix = "px",
        tag = "image_edge_slider",
        onChange = { onChange(settings.copy(recognitionTuning = tuning.copy(imageMaxEdge = it))) },
    )
    IntSliderSetting(
        label = appText("JPEG 质量", "JPEG quality"),
        help = appText("控制送入模型和冻结照片的 JPEG 压缩质量。", "Compression quality for inference and the frozen photo."),
        value = tuning.jpegQuality,
        range = RecognitionTuning.MIN_JPEG_QUALITY..RecognitionTuning.MAX_JPEG_QUALITY,
        step = 5,
        suffix = "%",
        tag = "jpeg_quality_slider",
        onChange = { onChange(settings.copy(recognitionTuning = tuning.copy(jpegQuality = it))) },
    )
}

@Composable
private fun PerformanceSettingsPanel(
    tuning: RecognitionTuning,
    activeRuntimeOptions: ModelRuntimeOptions?,
    lastInferenceMillis: Long?,
    runtimePending: Boolean,
    onChange: (RecognitionTuning) -> Unit,
) {
    Text(appText("模型运行参数", "Model runtime"), color = ScannerOutline, fontWeight = FontWeight.Black, fontSize = 14.sp)
    Text(
        text = activeRuntimeOptions?.let {
            appText("当前生效：${it.contextSize} ctx · ${it.batchSize} batch · ${it.threads} 线程", "Active: ${it.contextSize} ctx · ${it.batchSize} batch · ${it.threads} threads")
        } ?: appText("模型尚未加载", "Model not loaded"),
        color = ScannerGraphite,
        fontSize = 10.sp,
        modifier = Modifier.testTag("active_runtime_options"),
    )
    if (runtimePending) {
        Text(
            appText("修改待重载，保存不会立即中断当前模型", "Changes require reload; saving will not interrupt the active model."),
            color = ScannerWarning,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("runtime_pending"),
        )
    }
    lastInferenceMillis?.let {
        Text(appText("上次识别：${it}ms", "Last inference: ${it}ms"), color = ScannerGraphite, fontSize = 10.sp)
    }
    IntSliderSetting(
        label = appText("CPU 线程", "CPU threads"),
        help = appText("线程更多不一定更快，也会增加发热。修改后需重载模型。", "More threads may increase heat without improving speed. Reload required."),
        value = tuning.threads,
        range = RecognitionTuning.MIN_THREADS..RecognitionTuning.maxThreads(),
        step = 1,
        tag = "threads_slider",
        onChange = { onChange(tuning.copy(threads = it)) },
    )
    DiscreteSliderSetting(
        label = "Batch Size",
        help = appText("较大批次可能更快，但会占用更多内存。修改后需重载模型。", "A larger batch may be faster but uses more memory. Reload required."),
        value = tuning.batchSize,
        options = RecognitionTuning.BATCH_SIZES,
        tag = "batch_size_slider",
        onChange = { onChange(tuning.copy(batchSize = it)) },
    )
    DiscreteSliderSetting(
        label = "Context Size",
        help = appText("上下文越大内存占用越高。修改后需重载模型。", "A larger context uses more memory. Reload required."),
        value = tuning.contextSize,
        options = RecognitionTuning.CONTEXT_SIZES,
        tag = "context_size_slider",
        onChange = { onChange(tuning.copy(contextSize = it)) },
    )
}

@Composable
private fun DecodingSettingsPanel(
    tuning: RecognitionTuning,
    onChange: (RecognitionTuning) -> Unit,
) {
    Text(appText("候选解码", "Candidate decoding"), color = ScannerOutline, fontWeight = FontWeight.Black, fontSize = 14.sp)
    IntSliderSetting(
        label = appText("重复窗口", "Penalty window"),
        help = appText("在最近多少个 Token 内抑制重复。", "Suppress repetition within the recent token window."),
        value = tuning.penaltyLastN,
        range = RecognitionTuning.MIN_PENALTY_LAST_N..RecognitionTuning.MAX_PENALTY_LAST_N,
        step = 16,
        tag = "penalty_last_n_slider",
        onChange = { onChange(tuning.copy(penaltyLastN = it)) },
    )
    FloatSliderSetting(
        label = appText("重复惩罚", "Repetition penalty"),
        help = appText("提高后可减少多候选名称重复。", "Higher values reduce duplicate candidate names."),
        value = tuning.repetitionPenalty,
        range = RecognitionTuning.MIN_REPETITION_PENALTY..RecognitionTuning.MAX_REPETITION_PENALTY,
        tag = "repetition_penalty_slider",
        onChange = { onChange(tuning.copy(repetitionPenalty = it)) },
    )
    FloatSliderSetting(
        label = appText("频率惩罚", "Frequency penalty"),
        help = appText("根据 Token 出现次数降低其再次出现概率。", "Penalize tokens according to prior frequency."),
        value = tuning.frequencyPenalty,
        range = RecognitionTuning.MIN_TOKEN_PENALTY..RecognitionTuning.MAX_TOKEN_PENALTY,
        tag = "frequency_penalty_slider",
        onChange = { onChange(tuning.copy(frequencyPenalty = it)) },
    )
    FloatSliderSetting(
        label = appText("存在惩罚", "Presence penalty"),
        help = appText("已出现 Token 会受到固定惩罚。", "Apply a fixed penalty to tokens already present."),
        value = tuning.presencePenalty,
        range = RecognitionTuning.MIN_TOKEN_PENALTY..RecognitionTuning.MAX_TOKEN_PENALTY,
        tag = "presence_penalty_slider",
        onChange = { onChange(tuning.copy(presencePenalty = it)) },
    )
    Spacer(Modifier.height(8.dp))
    Text(appText("已锁定：贪心解码", "Locked: greedy decoding"), color = ScannerSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Text(appText("已锁定：严格 JSON 语法与名称白名单", "Locked: strict JSON grammar and name whitelist"), color = ScannerSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun VoiceSettingsPanel(
    narration: NarrationSettings,
    availableVoices: List<NarratorVoiceOption>,
    onChange: (NarrationSettings) -> Unit,
    onPreview: (NarrationSettings) -> Unit,
) {
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    Text(appText("男性播报音色", "Narration voice"), color = ScannerOutline, fontSize = 14.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(8.dp))
    ParameterHeader(
        appText("系统中文音色", "System voice"),
        appText("切换手机已经安装的离线中文语音。实际音色数量由系统语音服务决定。", "Select an installed offline voice for the active app language."),
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { voiceMenuExpanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tts_voice_selector"),
            colors = ButtonDefaults.buttonColors(containerColor = ScannerGraphite),
            shape = RoundedCornerShape(4.dp),
        ) {
            val selectedLabel = availableVoices.firstOrNull { it.id == narration.voiceName }?.label
            Text(selectedLabel ?: appText("自动选择男声", "Auto-select male voice"), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(
            expanded = voiceMenuExpanded,
            onDismissRequest = { voiceMenuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(appText("自动选择男声", "Auto-select male voice")) },
                onClick = {
                    voiceMenuExpanded = false
                    onChange(narration.copy(voiceName = null))
                },
            )
            availableVoices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice.label) },
                    onClick = {
                        voiceMenuExpanded = false
                        onChange(narration.copy(voiceName = voice.id))
                    },
                    modifier = Modifier.testTag("tts_voice_${voice.id}"),
                )
            }
        }
    }
    if (availableVoices.isEmpty()) {
        Text(appText("系统当前未提供可切换的离线中文音色", "No selectable offline voice is installed"), color = ScannerGraphiteLight, fontSize = 9.sp)
    }
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        VoicePreset.entries.forEachIndexed { index, preset ->
            SegmentedButton(
                selected = narration.preset == preset,
                onClick = {
                    onChange(
                        if (preset == VoicePreset.Custom) narration.copy(preset = VoicePreset.Custom)
                        else NarrationSettings.forPreset(
                            preset = preset,
                            volume = narration.volume,
                            voiceName = narration.voiceName,
                        ),
                    )
                },
                shape = SegmentedButtonDefaults.itemShape(index, VoicePreset.entries.size),
                modifier = Modifier.testTag("voice_preset_${preset.name}"),
            ) {
                Text(
                    when (preset) {
                        VoicePreset.DeepMale -> appText("低沉", "Deep")
                        VoicePreset.StandardMale -> appText("标准", "Standard")
                        VoicePreset.FastMale -> appText("快速", "Fast")
                        VoicePreset.Custom -> appText("自定义", "Custom")
                    },
                    fontSize = 10.sp,
                )
            }
        }
    }
    NarrationSlider(
        label = appText("语速", "Rate"),
        value = narration.speechRate,
        valueRange = NarrationSettings.MIN_SPEECH_RATE..NarrationSettings.MAX_SPEECH_RATE,
        valueText = String.format(Locale.ROOT, "%.2fx", narration.speechRate),
        tag = "speech_rate_slider",
        onValueChange = { onChange(narration.copy(preset = VoicePreset.Custom, speechRate = it)) },
    )
    NarrationSlider(
        label = appText("音调", "Pitch"),
        value = narration.speechPitch,
        valueRange = NarrationSettings.MIN_SPEECH_PITCH..NarrationSettings.MAX_SPEECH_PITCH,
        valueText = String.format(Locale.ROOT, "%.2fx", narration.speechPitch),
        tag = "speech_pitch_slider",
        onValueChange = { onChange(narration.copy(preset = VoicePreset.Custom, speechPitch = it)) },
    )
    NarrationSlider(
        label = appText("音量", "Volume"),
        value = narration.volume,
        valueRange = 0f..1f,
        valueText = "${(narration.volume * 100).toInt()}%",
        tag = "speech_volume_slider",
        onValueChange = { onChange(narration.copy(volume = it)) },
    )
    TextButton(
        onClick = { onPreview(narration.sanitized()) },
        modifier = Modifier.testTag("narration_preview"),
    ) {
        Text(appText("试听", "Preview"), color = ScannerGraphite, fontWeight = FontWeight.Black)
    }
}

/**
 * Manages packaged defaults and user-imported replacements for each sound slot.
 * 管理每个音效槽位的内置默认资源与用户导入替换文件。
 */
@Composable
private fun SoundSettingsPanel(
    settings: SoundEffectSettings,
    assets: List<SoundAssetStatus>,
    onChange: (SoundEffectSettings) -> Unit,
    onPreview: (AppSoundEffect, SoundEffectSettings) -> Unit,
    onImport: (AppSoundEffect) -> Unit,
    onReset: (AppSoundEffect) -> Unit,
) {
    val safe = settings.sanitized()
    Text(appText("应用音效", "Application sounds"), color = ScannerOutline, fontSize = 14.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(8.dp))
    ParameterHeader(appText("播放状态", "Playback"), appText("关闭后保留音频文件和配置，但识别流程不会播放提示音。", "Disabling playback keeps imported files and settings."))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(true to appText("开启", "On"), false to appText("关闭", "Off")).forEachIndexed { index, option ->
            SegmentedButton(
                selected = safe.enabled == option.first,
                onClick = { onChange(safe.copy(enabled = option.first)) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                modifier = Modifier.testTag("sound_enabled_${option.first}"),
            ) {
                Text(option.second, fontSize = 10.sp)
            }
        }
    }
    NarrationSlider(
        label = appText("音量", "Volume"),
        value = safe.volume,
        valueRange = 0f..1f,
        valueText = "${(safe.volume * 100).roundToInt()}%",
        tag = "sound_volume_slider",
        onValueChange = { onChange(safe.copy(volume = it)) },
    )
    Spacer(Modifier.height(6.dp))
    AppSoundEffect.entries.forEach { effect ->
        val status = assets.firstOrNull { it.effect == effect }
            ?: SoundAssetStatus(effect, SoundAssetSource.BuiltIn, "内置默认音效")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Text(effect.localizedName(LocalAppLanguage.current), color = ScannerGraphite, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(
                text = if (status.source == SoundAssetSource.Custom) {
                    appText("自定义 · ${status.fileName}", "Custom · ${status.fileName}")
                } else {
                    appText("内置默认音效", "Built-in default")
                },
                color = ScannerGraphiteLight,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onPreview(effect, safe.copy(enabled = true)) },
                    modifier = Modifier.testTag("sound_preview_${effect.name}"),
                ) { Text(appText("试听", "Preview"), fontSize = 10.sp) }
                TextButton(
                    onClick = { onImport(effect) },
                    modifier = Modifier.testTag("sound_import_${effect.name}"),
                ) { Text(appText("上传替换", "Replace"), fontSize = 10.sp) }
                TextButton(
                    onClick = { onReset(effect) },
                    enabled = status.source == SoundAssetSource.Custom,
                    modifier = Modifier.testTag("sound_reset_${effect.name}"),
                ) { Text(appText("恢复默认", "Restore"), fontSize = 10.sp) }
            }
        }
    }
    Text(
        appText("支持 MP3、WAV、OGG、M4A、AAC；单个文件不超过 15 MB，时长 0.05–30 秒。", "MP3, WAV, OGG, M4A, AAC; max 15 MB and 0.05–30 seconds."),
        color = ScannerGraphiteLight,
        fontSize = 9.sp,
    )
}

@Composable
private fun ParameterHeader(label: String, help: String) {
    var expanded by remember(label) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ScannerGraphite, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(1.dp, ScannerGraphiteLight, CircleShape)
                .clickable { expanded = !expanded },
            contentAlignment = Alignment.Center,
        ) {
            Text("i", color = ScannerGraphite, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
    if (expanded) Text(help, color = ScannerGraphiteLight, fontSize = 9.sp)
}

@Composable
private fun IntSliderSetting(
    label: String,
    help: String,
    value: Int,
    range: IntRange,
    step: Int,
    tag: String,
    suffix: String = "",
    onChange: (Int) -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    ParameterHeader(label, help)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                val snapped = range.first + (((raw - range.first) / step).roundToInt() * step)
                onChange(snapped.coerceIn(range))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            modifier = Modifier.weight(1f).testTag(tag),
        )
        Text("$value$suffix", modifier = Modifier.width(58.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DiscreteSliderSetting(
    label: String,
    help: String,
    value: Int,
    options: List<Int>,
    tag: String,
    suffix: String = "",
    onChange: (Int) -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    ParameterHeader(label, help)
    val selectedIndex = options.indexOf(value).coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { onChange(options[it.roundToInt().coerceIn(options.indices)]) },
            valueRange = 0f..options.lastIndex.toFloat(),
            steps = (options.size - 2).coerceAtLeast(0),
            modifier = Modifier.weight(1f).testTag(tag),
        )
        Text("$value$suffix", modifier = Modifier.width(66.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun FloatSliderSetting(
    label: String,
    help: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tag: String,
    onChange: (Float) -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    ParameterHeader(label, help)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = { raw -> onChange((raw * 20f).roundToInt() / 20f) },
            valueRange = range,
            steps = 19,
            modifier = Modifier.weight(1f).testTag(tag),
        )
        Text(String.format(Locale.ROOT, "%.2f", value), modifier = Modifier.width(58.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

private fun resetSettingsTab(
    current: ScannerSettings,
    tab: SettingsConsoleTab,
): ScannerSettings {
    val defaults = ScannerSettings()
    val currentTuning = current.recognitionTuning
    val defaultTuning = defaults.recognitionTuning
    return when (tab) {
        SettingsConsoleTab.Basic -> current.copy(
            recognitionMode = defaults.recognitionMode,
            recognitionTuning = currentTuning.copy(
                singleMaxTokens = defaultTuning.singleMaxTokens,
                multipleMaxTokens = defaultTuning.multipleMaxTokens,
                timeoutSeconds = defaultTuning.timeoutSeconds,
                imageMaxEdge = defaultTuning.imageMaxEdge,
                jpegQuality = defaultTuning.jpegQuality,
            ),
        )
        SettingsConsoleTab.Performance -> current.copy(
            recognitionTuning = currentTuning.copy(
                contextSize = defaultTuning.contextSize,
                batchSize = defaultTuning.batchSize,
                threads = defaultTuning.threads,
            ),
        )
        SettingsConsoleTab.Decoding -> current.copy(
            recognitionTuning = currentTuning.copy(
                penaltyLastN = defaultTuning.penaltyLastN,
                repetitionPenalty = defaultTuning.repetitionPenalty,
                frequencyPenalty = defaultTuning.frequencyPenalty,
                presencePenalty = defaultTuning.presencePenalty,
            ),
        )
        SettingsConsoleTab.Voice -> current.copy(narrationSettings = defaults.narrationSettings)
        SettingsConsoleTab.Sound -> current.copy(soundEffectSettings = defaults.soundEffectSettings)
    }.sanitized()
}

@Composable
private fun NarrationSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    tag: String,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(42.dp),
            color = ScannerGraphite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .weight(1f)
                .testTag(tag),
        )
        Text(
            text = valueText,
            modifier = Modifier.width(52.dp),
            color = ScannerOutline,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun CorrectionDialog(
    catalog: PokemonCatalog,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onSelectNotPokemon: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query, catalog) {
        val normalized = query.trim()
        catalog.records.withIndex()
            .asSequence()
            .filter { indexed ->
                normalized.isBlank() ||
                    indexed.value.nameZh.contains(normalized, ignoreCase = true) ||
                    indexed.value.nameEn.contains(normalized, ignoreCase = true) ||
                    indexed.value.id.contains(normalized)
            }
            .take(120)
            .toList()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(min = 420.dp, max = 680.dp)
                .background(ScannerPanel, RoundedCornerShape(6.dp))
                .border(4.dp, ScannerOutline, RoundedCornerShape(6.dp))
                .padding(16.dp)
                .testTag("correction_dialog"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
            text = appText("选择正确名称", "Select the correct Pokémon"),
                        color = ScannerOutline,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.sp,
                    )
                    Text(
            text = appText("只能选择本地图鉴标准名称", "Only local standard Pokédex names are accepted"),
                        color = ScannerGraphiteLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("correction_cancel")) {
            Text(appText("取消", "Cancel"), color = ScannerRedDark, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("correction_search"),
            label = { Text(appText("名称、英文名或编号", "Name or Pokédex number")) },
                singleLine = true,
                shape = RoundedCornerShape(4.dp),
            )
            Spacer(Modifier.height(8.dp))
            CorrectionOption(
                    title = appText("非宝可梦", "Not a Pokémon"),
                    subtitle = appText("图片中没有需要识别的宝可梦", "No Pokémon is present in this image"),
                tag = "correction_not_pokemon",
                accent = ScannerRed,
                onClick = onSelectNotPokemon,
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("correction_results"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    items = matches,
                    key = { _, indexed -> indexed.value.key },
                ) { _, indexed ->
                    CorrectionOption(
                        title = indexed.value.nameZh,
                        subtitle = "#${indexed.value.id} // ${indexed.value.nameEn}",
                        tag = "correction_option_${indexed.index}",
                        accent = ScannerLensBlue,
                        onClick = { onSelect(indexed.index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CorrectionOption(
    title: String,
    subtitle: String,
    tag: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(ScannerCanvas.copy(alpha = 0.06f), RoundedCornerShape(3.dp))
            .border(1.dp, ScannerGraphiteLight.copy(alpha = 0.24f), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(32.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ScannerOutline,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                color = ScannerGraphiteLight,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PokemonResult(
    pokemon: PokemonRecord,
    capturedImageJpeg: ByteArray?,
    candidates: List<RankedPokemonCandidate>,
    selectedCandidateIndex: Int,
    catalog: PokemonCatalog,
    feedback: FeedbackUiState,
    onConfirmFeedback: () -> Unit,
    onRequestCorrection: () -> Unit,
    onUndoFeedback: () -> Unit,
    onExportFeedback: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerPanel)
            .testTag("scanner_result"),
    ) {
        val compact = maxHeight < 460.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 8.dp else 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top,
            ) {
                CapturedPhotoPanel(
                    imageJpeg = capturedImageJpeg,
                    pokemon = pokemon,
                    compact = compact,
                    modifier = Modifier.weight(0.48f),
                )
                PokemonInfoPanel(
                    pokemon = pokemon,
                    compact = compact,
                    modifier = Modifier
                        .weight(0.52f)
                        .padding(start = 8.dp),
                )
            }
            CandidateStatusBar(
                candidate = candidates.getOrNull(selectedCandidateIndex),
                candidateIndex = selectedCandidateIndex,
                candidateCount = candidates.size,
                catalog = catalog,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(6.dp))
            FeedbackActions(
                feedback = feedback,
                onConfirm = onConfirmFeedback,
                onCorrect = onRequestCorrection,
                onUndo = onUndoFeedback,
                onExport = onExportFeedback,
            )
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(ScannerSignalGreen),
            )
        }
    }
}

@Composable
private fun CapturedPhotoPanel(
    imageJpeg: ByteArray?,
    pokemon: PokemonRecord,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .testTag("captured_photo_panel"),
        contentAlignment = Alignment.TopCenter,
    ) {
        val edge = minOf(maxWidth, maxHeight)
        Box(
            modifier = Modifier
                .size(edge)
                .background(ScannerCanvas)
                .border(1.dp, ScannerPanel)
                .testTag("captured_photo"),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = remember(imageJpeg) {
                imageJpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
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
        val preferredThumbnailSize = if (compact) 104.dp else 144.dp
        val thumbnailSize = minOf(preferredThumbnailSize, maxWidth * 0.62f)
        val lowerAreaHeight = (maxHeight - edge).coerceAtLeast(0.dp)
        if (lowerAreaHeight >= thumbnailSize) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(edge)
                    .height(lowerAreaHeight),
                contentAlignment = Alignment.Center,
            ) {
                PokemonThumbnail(
                    pokemon = pokemon,
                    modifier = Modifier
                        .size(thumbnailSize)
                        .testTag("result_pokemon_thumbnail"),
                )
            }
        } else {
            PokemonThumbnail(
                pokemon = pokemon,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(thumbnailSize)
                    .testTag("result_pokemon_thumbnail"),
            )
        }
    }
}

@Composable
private fun PokemonInfoPanel(
    pokemon: PokemonRecord,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .testTag("pokemon_info_panel"),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
    ) {
        Text(
            text = "#${pokemon.id} ${pokemon.nameEn}",
            color = ScannerGraphiteLight,
            fontSize = if (compact) 8.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = pokemon.localizedDisplayName(language),
            color = ScannerOutline,
            fontSize = if (compact) 18.sp else 22.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = pokemon.nameJa.ifBlank { "--" },
            color = ScannerGraphite,
            fontSize = if (compact) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (language == AppLanguage.Chinese) categoryLabel(pokemon.category) else pokemon.localizedAttributeLabel(language),
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
        Text(
            text = appText(
                "特性：${pokemon.ability.ifBlank { "--" }}",
                "Ability: ${pokemon.ability.ifBlank { "--" }}",
            ),
            color = ScannerGraphite,
            fontSize = if (compact) 9.sp else 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        PokemonStatsList(pokemon = pokemon, compact = compact)
    }
}

@Composable
private fun PokemonStatsList(pokemon: PokemonRecord, compact: Boolean) {
    PokemonStatBar("HP", pokemon.stats.hp, ScannerRed, compact)
    PokemonStatBar(appText("攻击", "ATK"), pokemon.stats.attack, Color(0xFFFFA726), compact)
    PokemonStatBar(appText("防御", "DEF"), pokemon.stats.defense, Color(0xFFFFE082), compact)
    PokemonStatBar(appText("特攻", "SpA"), pokemon.stats.specialAttack, Color(0xFF4FC3F7), compact)
    PokemonStatBar(appText("特防", "SpD"), pokemon.stats.specialDefense, Color(0xFF8BCF7B), compact)
    PokemonStatBar(appText("速度", "SPE"), pokemon.stats.speed, Color(0xFF9FA8DA), compact)
}

private fun categoryLabel(category: String): String =
    if (category.endsWith("宝可梦")) category else "${category}宝可梦"

@Composable
private fun PokemonStatBar(
    label: String,
    value: Int,
    color: Color,
    compact: Boolean,
) {
    val safeValue = value.coerceIn(0, MAX_STAT_VALUE)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 17.dp else 21.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(if (compact) 27.dp else 34.dp),
            color = ScannerGraphite,
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.Black,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(if (compact) 5.dp else 7.dp)
                .background(ScannerGraphiteLight.copy(alpha = 0.18f), RoundedCornerShape(4.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safeValue / MAX_STAT_VALUE.toFloat())
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(4.dp)),
            )
        }
        Text(
            text = safeValue.toString(),
            modifier = Modifier.width(if (compact) 25.dp else 30.dp),
            color = ScannerOutline,
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.Black,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

private const val MAX_STAT_VALUE = 255

@Composable
private fun CandidateStatusBar(
    candidate: RankedPokemonCandidate?,
    candidateIndex: Int,
    candidateCount: Int,
    catalog: PokemonCatalog,
    modifier: Modifier = Modifier,
) {
    val pokemon = candidate?.let { catalog.recordAt(it.pokemonIndex) }
    val probability = candidate?.probability?.let { String.format(Locale.ROOT, "%.1f%%", it * 100f) } ?: "--"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(ScannerGraphite, RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp)
            .testTag("candidate_status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = appText("候选 ${candidateIndex + 1}/${candidateCount.coerceAtLeast(1)}", "Candidate ${candidateIndex + 1}/${candidateCount.coerceAtLeast(1)}"),
            color = ScannerPanel,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = pokemon?.nameZh ?: "--",
            color = ScannerPanel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
        Text(
            text = probability,
            color = ScannerSuccess,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun PokemonThumbnail(
    pokemon: PokemonRecord,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, pokemon.imageAsset) {
        value = runCatching {
            context.assets.open(pokemon.imageAsset).use { input -> BitmapFactory.decodeStream(input) }
        }.getOrNull()
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = pokemon.nameZh,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun FeedbackActions(
    feedback: FeedbackUiState,
    onConfirm: () -> Unit,
    onCorrect: () -> Unit,
    onUndo: () -> Unit,
    onExport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (feedback.savedSampleId == null) {
                FeedbackActionButton(
            text = if (feedback.saving) appText("保存中", "Saving") else appText("正确", "Correct"),
                    enabled = !feedback.saving,
                    color = ScannerSuccess,
                    tag = "feedback_confirm",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
                FeedbackActionButton(
            text = appText("纠正", "Correct it"),
                    enabled = !feedback.saving,
                    color = ScannerRed,
                    tag = "feedback_correct",
                    onClick = onCorrect,
                    modifier = Modifier.weight(1f),
                )
            } else {
                FeedbackActionButton(
            text = appText("已保存", "Saved"),
                    enabled = false,
                    color = ScannerSuccess,
                    tag = "feedback_saved",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                FeedbackActionButton(
            text = if (feedback.saving) appText("处理中", "Working") else appText("撤销", "Undo"),
                    enabled = !feedback.saving,
                    color = ScannerRedDark,
                    tag = "feedback_undo",
                    onClick = onUndo,
                    modifier = Modifier.weight(1f),
                )
            }
            FeedbackActionButton(
            text = appText("导出 ${feedback.sampleCount}", "Export ${feedback.sampleCount}"),
                enabled = feedback.sampleCount > 0 && !feedback.saving,
                color = ScannerGraphite,
                tag = "feedback_export",
                onClick = onExport,
                modifier = Modifier.weight(1.15f),
            )
        }
        feedback.message?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp)
                    .testTag("feedback_message"),
                color = if (feedback.savedSampleId == null) ScannerRedDark else ScannerGraphite,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeedbackActionButton(
    text: String,
    enabled: Boolean,
    color: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxHeight()
            .testTag(tag),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (color == ScannerSuccess) ScannerOutline else ScannerPanel,
            disabledContainerColor = color.copy(alpha = 0.5f),
            disabledContentColor = ScannerPanel.copy(alpha = 0.78f),
        ),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun CandidateList(
    candidates: List<RankedPokemonCandidate>,
    selectedCandidateIndex: Int,
    catalog: PokemonCatalog,
    compact: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("candidate_list"),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp),
    ) {
        repeat(MAX_RESULT_CANDIDATES) { index ->
            val candidate = candidates.getOrNull(index)
            val selected = index == selectedCandidateIndex && candidate != null
            val candidateName = candidate?.let { catalog.recordAt(it.pokemonIndex).nameZh } ?: "--"
            val probability = candidate?.probability?.let { value ->
                String.format(Locale.ROOT, "%.1f%%", value.coerceIn(0f, 1f) * 100f)
            } ?: "--"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 21.dp else 25.dp)
                    .background(
                        color = if (selected) ScannerLensBlue.copy(alpha = 0.2f) else ScannerCanvas.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(3.dp),
                    )
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) ScannerLensBlue else ScannerGraphiteLight.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(3.dp),
                    )
                    .padding(horizontal = 6.dp)
                    .testTag("candidate_$index"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${index + 1}. $candidateName",
                    modifier = Modifier.weight(1f),
                    color = if (candidate == null) ScannerGraphiteLight else ScannerOutline,
                    fontSize = if (compact) 9.sp else 10.sp,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = probability,
                    color = if (selected) ScannerRedDark else ScannerGraphite,
                    fontSize = if (compact) 9.sp else 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SpecimenGlyph(
    pokemon: PokemonRecord,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, pokemon.imageAsset) {
        value = runCatching {
            context.assets.open(pokemon.imageAsset).use { input -> BitmapFactory.decodeStream(input) }
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
private fun TypeBadge(type: String) {
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
private fun ResultMetric(
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
private fun ScannerPreview() {
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
                modelStatus = ModelSetStatus(true, true),
            ),
            catalog = previewCatalog,
            surfaceRequest = null,
            useFakeCamera = true,
            onPrimaryAction = {},
            onOpenCatalog = {},
            onBackToCamera = {},
            onMoveResult = {},
            onSaveSettings = {},
            onSaveSettingsAndReload = {},
            onTestSettings = {},
            onPreviewNarration = {},
            onRequestPermission = {},
            onRetry = {},
            onImportLanguage = {},
            onImportVision = {},
            onConfirmFeedback = {},
            onCorrectFeedback = {},
            onCorrectNotPokemon = {},
            onUndoFeedback = {},
            onExportFeedback = {},
        )
    }
}
