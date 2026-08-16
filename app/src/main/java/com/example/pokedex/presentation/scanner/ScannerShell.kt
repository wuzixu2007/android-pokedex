/* Responsive Canvas-based mechanical shell and controls. / 基于 Canvas 的响应式机械外壳与控制组件。 */
package com.example.pokedex.presentation.scanner

import com.example.pokedex.data.scanner.*
import com.example.pokedex.domain.scanner.*

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pokedex.R
import com.example.pokedex.ui.theme.ScannerGraphite
import com.example.pokedex.ui.theme.ScannerGraphiteLight
import com.example.pokedex.ui.theme.ScannerBorder
import com.example.pokedex.ui.theme.ScannerLensBlue
import com.example.pokedex.ui.theme.ScannerLensHighlight
import com.example.pokedex.ui.theme.ScannerOutline
import com.example.pokedex.ui.theme.ScannerPanel
import com.example.pokedex.ui.theme.ScannerRed
import com.example.pokedex.ui.theme.ScannerRedDark
import com.example.pokedex.ui.theme.ScannerRedLight
import com.example.pokedex.ui.theme.ScannerSignalGreen
import com.example.pokedex.ui.theme.ScannerSuccess
import com.example.pokedex.ui.theme.ScannerWarning
import com.example.pokedex.ui.theme.activeThemePalette
import com.example.pokedex.ui.theme.controlForeground
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal data class PageOrbitalState(
    val direction: PageOrbitalDirection = PageOrbitalDirection.Forward,
    val progress: Float = 0f,
)

@Composable
internal fun ScannerShell(
    mode: ScannerMode,
    page: ScannerPage,
    pageRegistry: PageRegistry = defaultPageRegistry,
    trainerScore: Int = 100,
    onPrimaryAction: () -> Unit,
    onOpenCatalog: () -> Unit,
    onBackToCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    backgroundMusicEnabled: Boolean = false,
    onToggleBackgroundMusic: () -> Unit = {},
    onMoveDetailsPage: (Int) -> Unit,
    onMovePokemon: (Int) -> Unit,
    canMoveDetailsPage: Boolean,
    canMovePokemon: Boolean,
    orbitalState: PageOrbitalState = PageOrbitalState(),
    modifier: Modifier = Modifier,
    viewport: @Composable () -> Unit,
) {
    val palette = activeThemePalette
    val foreground = palette.controlForeground()
    BoxWithConstraints(modifier = modifier) {
        val expanded = maxWidth >= 600.dp || maxWidth > maxHeight
        val showScannerControls = pageRegistry.enabledPage(page)?.showScannerControls ?: true
        // This height drives both the mechanical controls and the scan-frame bottom edge.
        val controlHeight by animateDpAsState(
            targetValue = if (showScannerControls) 184.dp else 0.dp,
            animationSpec = tween(durationMillis = 500, easing = LinearEasing),
            label = "scanner_controls_height",
        )
        val controlAlpha by animateFloatAsState(
            targetValue = if (showScannerControls) 1f else 0f,
            animationSpec = tween(durationMillis = 180),
            label = "scanner_controls_alpha",
        )
        val controlsGap by animateDpAsState(
            targetValue = if (showScannerControls) 10.dp else 0.dp,
            animationSpec = tween(durationMillis = 500, easing = LinearEasing),
            label = "scanner_controls_gap",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .scannerShellBackground(expanded && showScannerControls, controlHeight, !showScannerControls)
                .padding(14.dp),
        ) {
            if (expanded && showScannerControls) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.65f)
                            .fillMaxHeight(),
                    ) {
                        ScannerHeader(
                            mode = mode,
                            page = page,
                            trainerScore = trainerScore,
                            orbitalState = orbitalState,
                            pageRegistry = pageRegistry,
                            compact = true,
                            settingsEnabled = mode != ScannerMode.Capturing,
                            onOpenSettings = onOpenSettings,
                            backgroundMusicEnabled = backgroundMusicEnabled,
                            onToggleBackgroundMusic = onToggleBackgroundMusic,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .sharedPrimaryViewportFrame(page, pageRegistry),
                        ) { viewport() }
                    }

                    ScannerControls(
                        mode = mode,
                        page = page,
                        expanded = true,
                        onPrimaryAction = onPrimaryAction,
                        onOpenCatalog = onOpenCatalog,
                        onBackToCamera = onBackToCamera,
                        onMoveDetailsPage = onMoveDetailsPage,
                        onMovePokemon = onMovePokemon,
                        canMoveDetailsPage = canMoveDetailsPage,
                        canMovePokemon = canMovePokemon,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    ScannerHeader(
                        mode = mode,
                        page = page,
                        trainerScore = trainerScore,
                        orbitalState = orbitalState,
                        pageRegistry = pageRegistry,
                        compact = false,
                        settingsEnabled = mode != ScannerMode.Capturing,
                        onOpenSettings = onOpenSettings,
                        backgroundMusicEnabled = backgroundMusicEnabled,
                        onToggleBackgroundMusic = onToggleBackgroundMusic,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .sharedPrimaryViewportFrame(page, pageRegistry),
                    ) { viewport() }
                    Spacer(Modifier.height(controlsGap))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(controlHeight)
                            .clipToBounds()
                            .graphicsLayer(alpha = controlAlpha),
                    ) {
                        ScannerControls(
                            mode = mode,
                            page = page,
                            expanded = false,
                            onPrimaryAction = onPrimaryAction,
                            onOpenCatalog = onOpenCatalog,
                            onBackToCamera = onBackToCamera,
                            onMoveDetailsPage = onMoveDetailsPage,
                            onMovePokemon = onMovePokemon,
                            canMoveDetailsPage = canMoveDetailsPage,
                            canMovePokemon = canMovePokemon,
                            modifier = Modifier.fillMaxWidth().height(184.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.sharedPrimaryViewportFrame(page: ScannerPage, registry: PageRegistry): Modifier {
    if (registry.enabledPage(page)?.primaryNavigation != true) return this
    return this
        .background(ScannerPanel, RoundedCornerShape(10.dp))
        .border(4.dp, ScannerBorder, RoundedCornerShape(10.dp))
        .padding(10.dp)
        .clip(RoundedCornerShape(5.dp))
        .testTag("shared_scanner_frame")
}

private fun Modifier.scannerShellBackground(
    expanded: Boolean,
    controlsHeight: androidx.compose.ui.unit.Dp,
    gamesPage: Boolean,
): Modifier = drawBehind {
    val strokeWidth = 4.dp.toPx()
    val inset = strokeWidth / 2
    val corner = 22.dp.toPx()

    drawRoundRect(
        color = ScannerRed,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
    )
    drawRoundRect(
        color = ScannerBorder,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        style = Stroke(width = strokeWidth),
    )

    if (expanded) {
        val dividerX = size.width * 0.63f
        drawLine(
            color = ScannerRedDark,
            start = androidx.compose.ui.geometry.Offset(dividerX, 14.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(dividerX, size.height - 14.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    } else {
        val bottom = if (gamesPage) inset else controlsHeight.toPx() + 14.dp.toPx()
        drawLine(
            color = ScannerRedDark,
            start = androidx.compose.ui.geometry.Offset(16.dp.toPx(), size.height - bottom),
            end = androidx.compose.ui.geometry.Offset(size.width - 16.dp.toPx(), size.height - bottom),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

}

@Composable
private fun ScannerHeader(
    mode: ScannerMode,
    page: ScannerPage,
    trainerScore: Int,
    orbitalState: PageOrbitalState,
    pageRegistry: PageRegistry,
    compact: Boolean,
    settingsEnabled: Boolean,
    onOpenSettings: () -> Unit,
    backgroundMusicEnabled: Boolean,
    onToggleBackgroundMusic: () -> Unit,
) {
    val matched = mode is ScannerMode.Result
    var lensLit by remember { mutableStateOf(true) }
    LaunchedEffect(matched) {
        lensLit = true
        if (matched) {
            repeat(3) {
                delay(130)
                lensLit = false
                delay(130)
                lensLit = true
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 76.dp else 92.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageOrbitalIndicator(
            page = page,
            state = orbitalState,
            pageRegistry = pageRegistry,
            compact = compact,
            illuminated = lensLit,
            modifier = Modifier.testTag("page_orbital_indicator"),
        )
        Spacer(Modifier.width(14.dp))
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackgroundMusicButton(enabled = backgroundMusicEnabled, onClick = onToggleBackgroundMusic)
                SettingsButton(enabled = settingsEnabled, onClick = onOpenSettings)
            }
            Spacer(Modifier.height(4.dp))
            ScorePanel(
                score = trainerScore,
                compact = compact,
                modifier = Modifier.testTag("trainer_score"),
            )
        }
    }
}

@Composable
private fun ScorePanel(
    score: Int,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .width(if (compact) 94.dp else 108.dp)
            .height(if (compact) 20.dp else 24.dp)
            .background(Color.Black, shape)
            .border(1.dp, ScannerSignalGreen.copy(alpha = 0.85f), shape)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val gridColor = ScannerSignalGreen.copy(alpha = 0.48f)
            for (column in 1 until 5) {
                val x = size.width * column / 5f
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
            }
            for (row in 1 until 2) {
                val y = size.height * row / 2f
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
            }
        }
        Text(
            text = "积分:$score",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = if (compact) 9.sp else 11.sp,
        )
    }
}

@Composable
private fun PageOrbitalIndicator(
    page: ScannerPage,
    state: PageOrbitalState,
    pageRegistry: PageRegistry,
    compact: Boolean,
    illuminated: Boolean,
    modifier: Modifier = Modifier,
) {
    val registered = pageRegistry.primaryPages.map { PageOrbitalItem(it.page, it.indicatorColor) }
    val activeIndex = registered.indexOfFirst { it.page == page }.coerceAtLeast(0)
    val queue = registered.drop(activeIndex) + registered.take(activeIndex)
    val visibleQueue = queue + pageRegistry.orbitalItems.filter { it.page == null }
    val progress = state.progress.coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .width(if (compact) 178.dp else 212.dp)
            .height(if (compact) 72.dp else 86.dp),
    ) {
        if (queue.size < 2) return@Canvas
        val largeRadius = size.minDimension * 0.46f
        val smallRadius = size.minDimension * 0.13f
        val largeCenter = androidx.compose.ui.geometry.Offset(largeRadius + 3.dp.toPx(), size.height * 0.56f)
        val smallStart = largeCenter.x + largeRadius + smallRadius + 8.dp.toPx()
        val smallSpacing = smallRadius * 2f + 4.dp.toPx()
        fun slotCenter(index: Int): androidx.compose.ui.geometry.Offset = when (index) {
            0 -> largeCenter
            else -> androidx.compose.ui.geometry.Offset(smallStart + (index - 1) * smallSpacing, size.height * 0.34f)
        }
        fun pointOnArc(
            start: androidx.compose.ui.geometry.Offset,
            end: androidx.compose.ui.geometry.Offset,
            bend: Float,
        ): androidx.compose.ui.geometry.Offset {
            val control = androidx.compose.ui.geometry.Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f + bend)
            val inverse = 1f - progress
            return androidx.compose.ui.geometry.Offset(
                inverse * inverse * start.x + 2f * inverse * progress * control.x + progress * progress * end.x,
                inverse * inverse * start.y + 2f * inverse * progress * control.y + progress * progress * end.y,
            )
        }
        visibleQueue.forEachIndexed { sourceIndex, descriptor ->
            val participates = sourceIndex < queue.size
            val targetIndex = if (!participates) {
                sourceIndex
            } else when (state.direction) {
                PageOrbitalDirection.Forward -> if (sourceIndex == 0) queue.lastIndex else sourceIndex - 1
                PageOrbitalDirection.Backward -> if (sourceIndex == queue.lastIndex) 0 else sourceIndex + 1
            }
            val isIncoming = targetIndex == 0 && sourceIndex != 0
            val isOutgoing = sourceIndex == 0 && targetIndex != 0
            val bend = when {
                isIncoming -> -largeRadius * 0.9f
                isOutgoing -> largeRadius * 0.78f
                else -> -smallRadius * 0.45f
            }
            val center = pointOnArc(slotCenter(sourceIndex), slotCenter(targetIndex), bend)
            val startRadius = if (sourceIndex == 0) largeRadius else smallRadius
            val endRadius = if (targetIndex == 0) largeRadius else smallRadius
            val radius = startRadius + (endRadius - startRadius) * progress
            val alpha = when {
                !participates -> 0.72f
                sourceIndex == 0 && !illuminated -> 0.25f
                else -> 1f
            }
            drawCircle(ScannerBorder, radius = radius, center = center)
            drawCircle(ScannerPanel, radius = radius * 0.93f, center = center)
            drawCircle(ScannerBorder, radius = radius * 0.78f, center = center)
            drawCircle(descriptor.color.copy(alpha = alpha), radius = radius * 0.62f, center = center)
            drawCircle(
                Color.White.copy(alpha = 0.62f * alpha),
                radius = radius * 0.13f,
                center = androidx.compose.ui.geometry.Offset(center.x - radius * 0.2f, center.y - radius * 0.24f),
            )
        }
    }
}

@Composable
private fun BackgroundMusicButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentDescription = if (enabled) "暂停游戏背景音乐" else "播放游戏背景音乐"
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag("background_music_toggle")
            .semantics { this.contentDescription = contentDescription },
    ) {
        Image(
            painter = painterResource(if (enabled) R.drawable.ic_music_pause else R.drawable.ic_music_play),
            contentDescription = null,
            modifier = Modifier.size(27.dp),
        )
    }
}

@Composable
private fun SettingsButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .testTag("recognition_settings_button")
            .semantics { contentDescription = "宝可梦识别设置" },
    ) {
        Canvas(modifier = Modifier.size(27.dp)) {
            val alpha = if (enabled) 1f else 0.38f
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val innerRadius = size.minDimension * 0.16f
            val outerRadius = size.minDimension * 0.36f
            repeat(8) { index ->
                val angle = index * PI / 4.0
                drawLine(
                    color = ScannerOutline.copy(alpha = alpha),
                    start = androidx.compose.ui.geometry.Offset(
                        center.x + cos(angle).toFloat() * outerRadius * 0.76f,
                        center.y + sin(angle).toFloat() * outerRadius * 0.76f,
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        center.x + cos(angle).toFloat() * outerRadius,
                        center.y + sin(angle).toFloat() * outerRadius,
                    ),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(
                color = ScannerOutline.copy(alpha = alpha),
                radius = outerRadius * 0.78f,
                center = center,
                style = Stroke(width = 3.dp.toPx()),
            )
            drawCircle(
                color = ScannerPanel.copy(alpha = alpha),
                radius = innerRadius,
                center = center,
            )
            drawCircle(
                color = ScannerOutline.copy(alpha = alpha),
                radius = innerRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

private fun ScannerMode.statusText(): String = when (this) {
    ScannerMode.PermissionRequired -> "CAMERA PERMISSION"
    ScannerMode.Preview -> "OPTICAL LINK READY"
    ScannerMode.Capturing -> "TARGET ANALYSIS"
    is ScannerMode.Result -> "SPECIMEN MATCHED"
    is ScannerMode.Error -> "SYSTEM CHECK"
}

@Composable
private fun Lens(
    illuminated: Boolean,
    modifier: Modifier = Modifier,
) {
    val lensColor = Color(0xFF7E57C2)
    val lensHighlight = Color(0xFFB39DDB)
    Canvas(modifier = modifier) {
        val stroke = 4.dp.toPx()
        drawCircle(color = ScannerBorder)
        drawCircle(color = ScannerPanel, radius = size.minDimension * 0.43f)
        drawCircle(color = ScannerBorder, radius = size.minDimension * 0.35f)
        drawCircle(
            color = lensColor.copy(alpha = if (illuminated) 1f else 0.22f),
            radius = size.minDimension * 0.29f,
        )
        drawArc(
            color = lensHighlight.copy(alpha = if (illuminated) 1f else 0.16f),
            startAngle = 205f,
            sweepAngle = 62f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.29f, size.height * 0.25f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.35f, size.height * 0.35f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun StatusLamp(
    color: Color,
    active: Boolean,
) {
    Canvas(modifier = Modifier.size(18.dp)) {
        drawCircle(color = ScannerBorder)
        drawCircle(
            color = if (active) color else ScannerPanel,
            radius = size.minDimension * 0.32f,
        )
        if (active) {
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = size.minDimension * 0.08f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.38f),
            )
        }
    }
}

@Composable
private fun ScannerControls(
    mode: ScannerMode,
    page: ScannerPage,
    expanded: Boolean,
    onPrimaryAction: () -> Unit,
    onOpenCatalog: () -> Unit,
    onBackToCamera: () -> Unit,
    onMoveDetailsPage: (Int) -> Unit,
    onMovePokemon: (Int) -> Unit,
    canMoveDetailsPage: Boolean,
    canMovePokemon: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
    if (expanded) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SignalPanel(modifier = Modifier.fillMaxWidth().height(76.dp))
            PokeballButton(
                enabled = page == ScannerPage.Scanner &&
                    (mode == ScannerMode.Preview || mode is ScannerMode.Result || mode is ScannerMode.Error),
                onClick = onPrimaryAction,
                modifier = Modifier.size(88.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MechanicalButton(
                    color = ScannerRedLight,
                    onClick = onOpenCatalog,
                    tag = "catalog_button",
                    description = "Open Pokemon catalog",
                    enabled = page != ScannerPage.Catalog,
                )
                MechanicalButton(
                    color = ScannerGraphite,
                    onClick = onBackToCamera,
                    tag = "back_button",
                    description = "Back to camera",
                    enabled = page != ScannerPage.Scanner || mode != ScannerMode.Preview,
                )
            }
            DPad(canMoveDetailsPage, canMovePokemon, onMoveDetailsPage, onMovePokemon)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 112.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MechanicalButton(
                        color = ScannerRedLight,
                        onClick = onOpenCatalog,
                        tag = "catalog_button",
                        description = "Open Pokemon catalog",
                        enabled = page != ScannerPage.Catalog,
                    )
                    MechanicalButton(
                        color = ScannerGraphite,
                        onClick = onBackToCamera,
                        tag = "back_button",
                        description = "Back to camera",
                        enabled = page != ScannerPage.Scanner || mode != ScannerMode.Preview,
                    )
                }
                Spacer(Modifier.height(8.dp))
                SignalPanel(modifier = Modifier.fillMaxWidth().height(66.dp))
            }

            PokeballButton(
                enabled = page == ScannerPage.Scanner &&
                    (mode == ScannerMode.Preview || mode is ScannerMode.Result || mode is ScannerMode.Error),
                onClick = onPrimaryAction,
                modifier = Modifier.size(82.dp),
            )
            DPad(canMoveDetailsPage, canMovePokemon, onMoveDetailsPage, onMovePokemon)
        }
    }
    }
}

@Composable
private fun MechanicalButton(
    color: Color,
    onClick: () -> Unit,
    tag: String,
    description: String,
    enabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val palette = activeThemePalette
    val foreground = palette.controlForeground()
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag(tag)
            .semantics { contentDescription = description }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(if (pressed) 15.dp else 18.dp)
                .background(color, RoundedCornerShape(5.dp))
                .border(3.dp, ScannerBorder, RoundedCornerShape(5.dp)),
        )
    }
}

@Composable
private fun PokeballButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "pokeball press")

    Canvas(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CircleShape)
            .testTag("capture_button")
            .semantics { contentDescription = "Identify Pokemon" }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        val diameter = min(size.width, size.height)
        val center = this.center
        drawCircle(color = Color.Black.copy(alpha = 0.26f), radius = diameter * 0.46f, center = center.copy(y = center.y + 4.dp.toPx()))
        drawCircle(color = ScannerBorder, radius = diameter * 0.46f)
        drawCircle(color = ScannerPanel, radius = diameter * 0.38f)
        drawArc(
            color = ScannerRed,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(center.x - diameter * 0.38f, center.y - diameter * 0.38f),
            size = androidx.compose.ui.geometry.Size(diameter * 0.76f, diameter * 0.76f),
        )
        drawLine(
            color = ScannerBorder,
            start = androidx.compose.ui.geometry.Offset(center.x - diameter * 0.38f, center.y),
            end = androidx.compose.ui.geometry.Offset(center.x + diameter * 0.38f, center.y),
            strokeWidth = 6.dp.toPx(),
        )
        drawCircle(color = ScannerBorder, radius = diameter * 0.14f)
        drawCircle(color = ScannerPanel, radius = diameter * 0.075f)
    }
}

@Composable
private fun SignalPanel(
    modifier: Modifier = Modifier,
) {
    val palette = activeThemePalette
    val foreground = palette.controlForeground()
    Box(
        modifier = modifier
            .testTag("signal_panel"),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(ScannerBorder, RoundedCornerShape(5.dp))
                .padding(4.dp),
        ) {
        drawRoundRect(
            color = ScannerSignalGreen,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
        )

        val gridColor = ScannerPanel.copy(alpha = 0.3f)
        for (i in 1 until 5) {
            val x = size.width * i / 5f
            drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
        }
        for (i in 1 until 3) {
            val y = size.height * i / 3f
            drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
        }

        val waveform = Path().apply {
            moveTo(0f, size.height * 0.64f)
            lineTo(size.width * 0.12f, size.height * 0.62f)
            lineTo(size.width * 0.2f, size.height * 0.28f)
            lineTo(size.width * 0.3f, size.height * 0.78f)
            lineTo(size.width * 0.42f, size.height * 0.42f)
            lineTo(size.width * 0.54f, size.height * 0.64f)
            lineTo(size.width * 0.68f, size.height * 0.31f)
            lineTo(size.width * 0.78f, size.height * 0.69f)
            lineTo(size.width, size.height * 0.53f)
        }
        drawPath(
            path = waveform,
            color = ScannerPanel,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        }
    }
}

@Composable
private fun DPad(
    canMoveDetailsPage: Boolean,
    canMovePokemon: Boolean,
    onMoveDetailsPage: (Int) -> Unit,
    onMovePokemon: (Int) -> Unit,
) {
    val luxuryTheme = activeThemePalette.id == "luxury"
    val frameColor = if (luxuryTheme) ScannerBorder else lerp(ScannerGraphite, ScannerRed, 0.5f)
    Box(modifier = Modifier.requiredSize(112.dp).aspectRatio(1f).testTag("dpad")) {
        Box(modifier = Modifier.requiredSize(112.dp).aspectRatio(1f).align(Alignment.BottomCenter)) {
            Canvas(modifier = Modifier.requiredSize(112.dp).aspectRatio(1f)) {
                val unit = size.minDimension / 3f
                val outer = Path().apply {
                    moveTo(unit, 0f); lineTo(unit * 2f, 0f); lineTo(unit * 2f, unit)
                    lineTo(size.width, unit); lineTo(size.width, unit * 2f); lineTo(unit * 2f, unit * 2f)
                    lineTo(unit * 2f, size.height); lineTo(unit, size.height); lineTo(unit, unit * 2f)
                    lineTo(0f, unit * 2f); lineTo(0f, unit); lineTo(unit, unit); close()
                }
                drawPath(outer, frameColor)
                val inset = 4.dp.toPx()
                val inner = Path().apply {
                    moveTo(unit + inset, inset); lineTo(unit * 2f - inset, inset); lineTo(unit * 2f - inset, unit + inset)
                    lineTo(size.width - inset, unit + inset); lineTo(size.width - inset, unit * 2f - inset)
                    lineTo(unit * 2f - inset, unit * 2f - inset); lineTo(unit * 2f - inset, size.height - inset)
                    lineTo(unit + inset, size.height - inset); lineTo(unit + inset, unit * 2f - inset)
                    lineTo(inset, unit * 2f - inset); lineTo(inset, unit + inset); lineTo(unit + inset, unit + inset); close()
                }
                drawPath(inner, ScannerGraphite)
                fun arrow(cx: Float, cy: Float, rotation: Float) {
                    val arrow = Path().apply {
                        moveTo(cx, cy - 8.dp.toPx()); lineTo(cx - 7.dp.toPx(), cy + 5.dp.toPx()); lineTo(cx + 7.dp.toPx(), cy + 5.dp.toPx()); close()
                    }
                    rotate(rotation, pivot = androidx.compose.ui.geometry.Offset(cx, cy)) { drawPath(arrow, ScannerPanel) }
                }
                arrow(size.width / 2f, unit / 2f, 0f)
                arrow(size.width / 2f, size.height - unit / 2f, 180f)
                arrow(unit / 2f, size.height / 2f, 270f)
                arrow(size.width - unit / 2f, size.height / 2f, 90f)
            }
            DPadHotspot("上一资料页", "dpad_left", canMoveDetailsPage, { onMoveDetailsPage(-1) }, Modifier.align(Alignment.CenterStart))
            DPadHotspot("下一资料页", "dpad_right", canMoveDetailsPage, { onMoveDetailsPage(1) }, Modifier.align(Alignment.CenterEnd))
            DPadHotspot("上一个宝可梦", "dpad_up", canMovePokemon, { onMovePokemon(-1) }, Modifier.align(Alignment.TopCenter))
            DPadHotspot("下一个宝可梦", "dpad_down", canMovePokemon, { onMovePokemon(1) }, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun DPadHotspot(
    description: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.size(42.dp).testTag(tag).semantics { contentDescription = description }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    )
}
