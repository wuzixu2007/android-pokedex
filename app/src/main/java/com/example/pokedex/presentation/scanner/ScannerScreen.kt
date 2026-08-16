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
fun PokedexScannerScreen(
    modifier: Modifier = Modifier,
    scannerViewModel: ScannerViewModel = viewModel(),
    palette: ThemePalette = ThemeCatalog.byId("default"),
    onThemeSelected: (String) -> Unit = {},
    onRepairResources: () -> Unit = {},
    resourceBackgroundStatus: ResourceBackgroundStatus = ResourceBackgroundStatus.Checking,
    onUpdateResources: () -> Unit = {},
    voicePackStatuses: Map<VoicePackId, VoicePackStatus> = emptyMap(),
    unlockedVoicePackIds: Set<VoicePackId> = emptySet(),
    onDownloadVoicePack: (VoicePackId) -> Unit = {},
    onUpdateVoicePack: (VoicePackId) -> Unit = {},
    onRepairVoicePack: (VoicePackId) -> Unit = {},
    onCancelVoicePack: (VoicePackId) -> Unit = {},
    onRefreshVoicePack: () -> Unit = {},
    onRedeemVoicePack: suspend (String) -> VoicePackRedeemResult = {
        VoicePackRedeemResult.Failure("兑换服务暂不可用")
    },
) {
    val context = LocalContext.current
    val state by scannerViewModel.uiState.collectAsState()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var pendingSoundImport by remember { mutableStateOf<AppSoundEffect?>(null) }
    val uiScope = rememberCoroutineScope()
    val backgroundMusicStore = remember(context.applicationContext) { GameBackgroundMusicStore(context.applicationContext) }
    val backgroundMusicRepository = remember(context.applicationContext) { GameBackgroundMusicSettingsRepository(context.applicationContext) }
    var backgroundMusicSettings by remember { mutableStateOf(backgroundMusicRepository.load()) }
    val backgroundMusicPlayer = remember(context.applicationContext) { GameBackgroundMusicPlayer(context.applicationContext, backgroundMusicStore) }
    val pokemonPhotoStore = remember(context.applicationContext) { scannerViewModel.photoStore() }
    val narrator = remember(context.applicationContext) {
        RoutingPokemonNarrator(
            context = context.applicationContext,
            onUnavailable = { message ->
                mainHandler.post {
                    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
                }
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
    val soundEffectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val effect = pendingSoundImport
        pendingSoundImport = null
        if (effect != null && uri != null) scannerViewModel.importSoundEffect(effect, uri)
    }
    val backgroundMusicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            uiScope.launch {
                runCatching { withContext(Dispatchers.IO) { backgroundMusicStore.import(uri) } }
                    .onSuccess {
                        backgroundMusicSettings = backgroundMusicSettings.copy(track = GameBackgroundTrack.Custom)
                        backgroundMusicRepository.save(backgroundMusicSettings)
                    }
                    .onFailure { error -> Toast.makeText(context, error.message ?: "无法导入背景音乐", Toast.LENGTH_LONG).show() }
            }
        }
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, scannerViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scannerViewModel.refreshAuthorShareMode()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(narrator, soundPlayer, backgroundMusicPlayer) {
        onDispose {
            narrator.close()
            soundPlayer.close()
            backgroundMusicPlayer.close()
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
    // The header control is available on every page, so its enabled state must directly
    // control playback instead of silently waiting until the games page is opened.
    LaunchedEffect(backgroundMusicSettings) {
        backgroundMusicPlayer.update(backgroundMusicSettings)
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
            }
        }
    }

    ScannerScreenContent(
        state = state,
        catalog = scannerViewModel.catalog,
        surfaceRequest = cameraSession.surfaceRequest,
        useFakeCamera = false,
        voicePackStatuses = voicePackStatuses,
        unlockedVoicePackIds = unlockedVoicePackIds,
        soundAssets = state.soundAssets,
        pokemonPhotoStore = pokemonPhotoStore,
        backgroundMusicSettings = backgroundMusicSettings,
        hasCustomBackgroundMusic = backgroundMusicStore.hasCustomTrack(),
        onBackgroundMusicChanged = { settings ->
            backgroundMusicSettings = settings.sanitized()
            backgroundMusicRepository.save(backgroundMusicSettings)
        },
        onToggleBackgroundMusic = {
            backgroundMusicSettings = backgroundMusicSettings.copy(enabled = !backgroundMusicSettings.enabled)
            backgroundMusicRepository.save(backgroundMusicSettings)
        },
        onImportBackgroundMusic = { backgroundMusicLauncher.launch(arrayOf("audio/*")) },
        onResetBackgroundMusic = {
            backgroundMusicStore.resetCustomTrack()
            if (backgroundMusicSettings.track == GameBackgroundTrack.Custom) {
                backgroundMusicSettings = backgroundMusicSettings.copy(track = GameBackgroundTrack.Pallet)
                backgroundMusicRepository.save(backgroundMusicSettings)
            }
        },
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
            soundPlayer.play(AppSoundEffect.Interaction)
            scannerViewModel.openCatalog()
        },
        onOpenGames = {
            soundPlayer.play(AppSoundEffect.Interaction)
            scannerViewModel.openGames()
        },
        onNavigatePrimaryPage = scannerViewModel::openPage,
        onOpenPokemonDetail = scannerViewModel::openPokemonDetail,
        onOpenPokemonGallery = scannerViewModel::openPokemonGallery,
        onBackToCamera = {
            stopAllAudio()
            soundPlayer.play(AppSoundEffect.Interaction)
            scannerViewModel.navigateBack()
        },
        onSaveSettings = scannerViewModel::saveScannerSettings,
        onTestSettings = scannerViewModel::testScannerSettings,
        onActivateAuthorShareMode = scannerViewModel::activateAuthorShareMode,
        onExitAuthorShareMode = scannerViewModel::exitAuthorShareMode,
        onRefreshAuthorShareMode = scannerViewModel::refreshAuthorShareMode,
        onRemoveAiHistory = scannerViewModel::removeAiResponseHistory,
        onClearAiHistory = scannerViewModel::clearAiResponseHistory,
        onClearTransientCache = scannerViewModel::clearTransientCache,
        onRepairResources = onRepairResources,
        resourceBackgroundStatus = resourceBackgroundStatus,
        onUpdateResources = onUpdateResources,
        onDownloadVoicePack = onDownloadVoicePack,
        onUpdateVoicePack = onUpdateVoicePack,
        onRepairVoicePack = { id ->
            narrator.stop()
            onRepairVoicePack(id)
        },
        onCancelVoicePack = onCancelVoicePack,
        onRefreshVoicePack = onRefreshVoicePack,
        onRedeemVoicePack = onRedeemVoicePack,
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
        onPlaySound = { effect ->
            soundPlayer.updateSettings(scannerViewModel.uiState.value.soundEffectSettings)
            soundPlayer.play(effect)
        },
        onSpeakText = { text -> narrator.speakText(text, scannerViewModel.uiState.value.appLanguage) },
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onRetry = scannerViewModel::retry,
        onZoomCamera = cameraSession::zoomBy,
        palette = palette,
        onThemeSelected = onThemeSelected,
        modifier = modifier,
    )
}

@Composable
internal fun ScannerScreenContent(
    state: ScannerUiState,
    catalog: PokemonCatalog,
    surfaceRequest: SurfaceRequest?,
    useFakeCamera: Boolean,
    voicePackStatuses: Map<VoicePackId, VoicePackStatus> = emptyMap(),
    unlockedVoicePackIds: Set<VoicePackId> = emptySet(),
    soundAssets: List<SoundAssetStatus> = emptyList(),
    pokemonPhotoStore: PokemonPhotoStore? = null,
    backgroundMusicSettings: BackgroundMusicSettings = BackgroundMusicSettings(),
    hasCustomBackgroundMusic: Boolean = false,
    onBackgroundMusicChanged: (BackgroundMusicSettings) -> Unit = {},
    onToggleBackgroundMusic: () -> Unit = {},
    onImportBackgroundMusic: () -> Unit = {},
    onResetBackgroundMusic: () -> Unit = {},
    resourceBackgroundStatus: ResourceBackgroundStatus = ResourceBackgroundStatus.Checking,
    onUpdateResources: () -> Unit = {},
    onDownloadVoicePack: (VoicePackId) -> Unit = {},
    onUpdateVoicePack: (VoicePackId) -> Unit = {},
    onRepairVoicePack: (VoicePackId) -> Unit = {},
    onCancelVoicePack: (VoicePackId) -> Unit = {},
    onRefreshVoicePack: () -> Unit = {},
    onRedeemVoicePack: suspend (String) -> VoicePackRedeemResult = {
        VoicePackRedeemResult.Failure("兑换服务暂不可用")
    },
    onPrimaryAction: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenGames: () -> Unit = {},
    onOpenPokemonDetail: (Int) -> Unit = {},
    onOpenPokemonGallery: (Int) -> Unit = {},
    onBackToCamera: () -> Unit,
    onNavigatePrimaryPage: (ScannerPage) -> Unit = { target ->
        if (target == ScannerPage.Games) onOpenGames() else onBackToCamera()
    },
    onSaveSettings: (ScannerSettings) -> Unit,
    onTestSettings: (ScannerSettings) -> Unit,
    onActivateAuthorShareMode: (String) -> Boolean = { false },
    onExitAuthorShareMode: () -> Unit = {},
    onRefreshAuthorShareMode: () -> Unit = {},
    onRemoveAiHistory: (String) -> Unit = {},
    onClearAiHistory: () -> Unit = {},
    onClearTransientCache: () -> Unit = {},
    onRepairResources: () -> Unit = {},
    onPreviewNarration: (NarrationSettings) -> Unit,
    onPreviewSound: (AppSoundEffect, SoundEffectSettings) -> Unit = { _, _ -> },
    onImportSound: (AppSoundEffect) -> Unit = {},
    onResetSound: (AppSoundEffect) -> Unit = {},
    onPlaySound: (AppSoundEffect) -> Unit = {},
    onSpeakText: (String) -> Unit = {},
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onZoomCamera: (Float) -> Unit = {},
    palette: ThemePalette = ThemeCatalog.byId("default"),
    onThemeSelected: (String) -> Unit = {},
    pageRegistry: PageRegistry = defaultPageRegistry,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalAppLanguage provides state.appLanguage) {
    val context = LocalContext.current
    val detailsRepository = remember(context.applicationContext) { PokemonDetailsRepository(context.applicationContext) }
    var trainerScore by remember(context.applicationContext) {
        mutableIntStateOf(SharedPreferencesScoreRepository(context.applicationContext).score())
    }
    var settingsDialogVisible by remember { mutableStateOf(false) }
    var detailsPageIndex by rememberSaveable { mutableStateOf(0) }
    val activePokemonKey = when {
        state.page == ScannerPage.CatalogDetail -> state.catalogPokemonIndex?.let { catalog.recordAt(it).key }
        state.page == ScannerPage.Scanner && state.mode is ScannerMode.Result -> catalog.recordAt(state.mode.candidate.pokemonIndex).key
        else -> null
    }
    LaunchedEffect(activePokemonKey) { detailsPageIndex = 0 }
    val canMoveDetailsPage = activePokemonKey != null
    val canMovePokemon = state.page == ScannerPage.CatalogDetail && state.catalogPokemonIndex != null
    var swipeProgress by remember { mutableFloatStateOf(0f) }
    var swipeDirection by remember { mutableStateOf(PageOrbitalDirection.Forward) }
    val swipeScope = rememberCoroutineScope()
    val pagerModifier = if (pageRegistry.enabledPage(state.page)?.primaryNavigation == true) {
        Modifier.pointerInput(state.page, pageRegistry) {
            var travel = 0f
            var dragWidth = 1f
            detectHorizontalDragGestures(
                onDragStart = {
                    travel = 0f
                    dragWidth = size.width.coerceAtLeast(1).toFloat()
                },
                onHorizontalDrag = { change, amount ->
                    if (change.isConsumed) return@detectHorizontalDragGestures
                    travel += amount
                    swipeDirection = if (travel < 0f) {
                        PageOrbitalDirection.Forward
                    } else {
                        PageOrbitalDirection.Backward
                    }
                    swipeProgress = (kotlin.math.abs(travel) / dragWidth).coerceIn(0f, 1f)
                },
                onDragEnd = {
                    val commit = swipeProgress >= 0.25f
                    val start = swipeProgress
                    swipeScope.launch {
                        Animatable(start).animateTo(
                            targetValue = if (commit) 1f else 0f,
                            animationSpec = tween(160, easing = LinearEasing),
                        ) { swipeProgress = value }
                        if (commit) {
                            pageRegistry.primaryDestination(state.page, swipeDirection)
                                ?.let(onNavigatePrimaryPage)
                        }
                        swipeProgress = 0f
                    }
                },
                onDragCancel = {
                    val start = swipeProgress
                    swipeScope.launch {
                        Animatable(start).animateTo(0f, tween(160, easing = LinearEasing)) { swipeProgress = value }
                    }
                },
            )
        }
    } else Modifier
    Box(
        modifier = modifier.then(pagerModifier)
            .fillMaxSize()
            .background(ScannerCanvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val pageHost = PokedexPageHost(
            state = state,
            catalog = catalog,
            surfaceRequest = surfaceRequest,
            useFakeCamera = useFakeCamera,
            pokemonPhotoStore = pokemonPhotoStore,
            detailsRepository = detailsRepository,
            detailsPageIndex = detailsPageIndex,
            onRequestPermission = onRequestPermission,
            onRetry = onRetry,
            onOpenPokemonDetail = onOpenPokemonDetail,
            onOpenPokemonGallery = onOpenPokemonGallery,
            onZoomCamera = onZoomCamera,
            onPlaySound = onPlaySound,
            onSpeakText = onSpeakText,
            onScoreChanged = { trainerScore = it },
        )
        ScannerShell(
            mode = state.mode,
            onPrimaryAction = onPrimaryAction,
            page = state.page,
            pageRegistry = pageRegistry,
            trainerScore = trainerScore,
            onOpenCatalog = onOpenCatalog,
            onBackToCamera = onBackToCamera,
            onOpenSettings = {
                onPlaySound(AppSoundEffect.Interaction)
                onRefreshAuthorShareMode()
                onRefreshVoicePack()
                settingsDialogVisible = true
            },
            backgroundMusicEnabled = backgroundMusicSettings.enabled,
            onToggleBackgroundMusic = onToggleBackgroundMusic,
            onMoveDetailsPage = { delta ->
                if (canMoveDetailsPage) {
                    onPlaySound(AppSoundEffect.Interaction)
                    detailsPageIndex = (detailsPageIndex + delta).mod(DETAILS_PAGE_COUNT)
                }
            },
            onMovePokemon = movePokemon@{ delta ->
                val current = state.catalogPokemonIndex ?: return@movePokemon
                if (canMovePokemon && catalog.records.isNotEmpty()) {
                    onPlaySound(AppSoundEffect.Interaction)
                    detailsPageIndex = 0
                    onOpenPokemonDetail((current + delta).mod(catalog.records.size))
                }
            },
            canMoveDetailsPage = canMoveDetailsPage,
            canMovePokemon = canMovePokemon,
            orbitalState = PageOrbitalState(swipeDirection, swipeProgress),
            modifier = Modifier
                .fillMaxSize(),
        ) {
            PrimaryContentSlot(
                page = state.page,
                registry = pageRegistry,
                swipeDirection = swipeDirection,
                swipeProgress = swipeProgress,
                host = pageHost,
            )
        }
    }
    if (settingsDialogVisible) {
        RecognitionSettingsDialog(
            scannerSettings = state.scannerSettings,
            lastInferenceMillis = state.lastInferenceMillis,
            settingsTest = state.settingsTest,
            voicePackStatuses = voicePackStatuses,
            unlockedVoicePackIds = unlockedVoicePackIds,
            soundAssets = soundAssets,
            backgroundMusicSettings = backgroundMusicSettings,
            hasCustomBackgroundMusic = hasCustomBackgroundMusic,
            onPreviewNarration = onPreviewNarration,
            onDownloadVoicePack = onDownloadVoicePack,
            onUpdateVoicePack = onUpdateVoicePack,
            onRepairVoicePack = onRepairVoicePack,
            onCancelVoicePack = onCancelVoicePack,
            onRedeemVoicePack = onRedeemVoicePack,
            onPreviewSound = onPreviewSound,
            onImportSound = onImportSound,
            onResetSound = onResetSound,
            onBackgroundMusicChanged = onBackgroundMusicChanged,
            onImportBackgroundMusic = onImportBackgroundMusic,
            onResetBackgroundMusic = onResetBackgroundMusic,
            onSave = { newSettings ->
                onSaveSettings(newSettings)
                settingsDialogVisible = false
            },
            onTest = onTestSettings,
            aiHistory = state.aiResponseHistory,
            onRemoveAiHistory = onRemoveAiHistory,
            onClearAiHistory = onClearAiHistory,
            onClearTransientCache = onClearTransientCache,
            onRepairResources = onRepairResources,
            resourceBackgroundStatus = resourceBackgroundStatus,
            onUpdateResources = {
                settingsDialogVisible = false
                onUpdateResources()
            },
            palette = palette,
            onThemeSelected = onThemeSelected,
            authorShareModeEnabled = state.authorShareModeEnabled,
            onActivateAuthorShareMode = onActivateAuthorShareMode,
            onExitAuthorShareMode = onExitAuthorShareMode,
            onDismiss = { settingsDialogVisible = false },
        )
    }
    }
}

