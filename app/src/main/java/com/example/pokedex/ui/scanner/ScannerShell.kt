/* Responsive Canvas-based mechanical shell and controls. / 基于 Canvas 的响应式机械外壳与控制组件。 */
package com.example.pokedex.ui.scanner

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pokedex.ui.theme.ScannerGraphite
import com.example.pokedex.ui.theme.ScannerGraphiteLight
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
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun ScannerShell(
    mode: ScannerMode,
    page: ScannerPage,
    recognitionMode: RecognitionMode,
    canMoveResult: Boolean,
    feedbackCount: Int,
    onPrimaryAction: () -> Unit,
    onOpenCatalog: () -> Unit,
    onBackToCamera: () -> Unit,
    onMoveResult: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onExportFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    viewport: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val expanded = maxWidth >= 600.dp || maxWidth > maxHeight

        Box(
            modifier = Modifier
                .fillMaxSize()
                .scannerShellBackground(expanded)
                .padding(14.dp),
        ) {
            if (expanded) {
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
                            recognitionMode = recognitionMode,
                            compact = true,
                            settingsEnabled = mode != ScannerMode.Capturing && mode != ScannerMode.LoadingModels,
                            onOpenSettings = onOpenSettings,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(modifier = Modifier.weight(1f)) { viewport() }
                    }

                    ScannerControls(
                        mode = mode,
                        page = page,
                        expanded = true,
                        onPrimaryAction = onPrimaryAction,
                        onOpenCatalog = onOpenCatalog,
                        onBackToCamera = onBackToCamera,
                        onMoveResult = onMoveResult,
                        canMoveResult = canMoveResult,
                        feedbackCount = feedbackCount,
                        onExportFeedback = onExportFeedback,
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
                        recognitionMode = recognitionMode,
                        compact = false,
                        settingsEnabled = mode != ScannerMode.Capturing && mode != ScannerMode.LoadingModels,
                        onOpenSettings = onOpenSettings,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.weight(1f)) { viewport() }
                    Spacer(Modifier.height(10.dp))
                    ScannerControls(
                        mode = mode,
                        page = page,
                        expanded = false,
                        onPrimaryAction = onPrimaryAction,
                        onOpenCatalog = onOpenCatalog,
                        onBackToCamera = onBackToCamera,
                        onMoveResult = onMoveResult,
                        canMoveResult = canMoveResult,
                        feedbackCount = feedbackCount,
                        onExportFeedback = onExportFeedback,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(184.dp),
                    )
                }
            }
        }
    }
}

private fun Modifier.scannerShellBackground(expanded: Boolean): Modifier = drawBehind {
    val strokeWidth = 4.dp.toPx()
    val inset = strokeWidth / 2
    val corner = 22.dp.toPx()

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.32f),
        topLeft = androidx.compose.ui.geometry.Offset(5.dp.toPx(), 7.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width - 5.dp.toPx(), size.height - 7.dp.toPx()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
    )
    drawRoundRect(
        color = ScannerRed,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
    )
    drawRoundRect(
        color = ScannerOutline,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        style = Stroke(width = strokeWidth),
    )

    val highlight = Path().apply {
        moveTo(28.dp.toPx(), 12.dp.toPx())
        lineTo(size.width * if (expanded) 0.58f else 0.7f, 12.dp.toPx())
        lineTo(size.width * if (expanded) 0.61f else 0.74f, 26.dp.toPx())
    }
    drawPath(
        path = highlight,
        color = ScannerRedLight.copy(alpha = 0.85f),
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
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
        drawLine(
            color = ScannerRedDark,
            start = androidx.compose.ui.geometry.Offset(16.dp.toPx(), size.height - 198.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(size.width - 16.dp.toPx(), size.height - 198.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ScannerHeader(
    mode: ScannerMode,
    page: ScannerPage,
    recognitionMode: RecognitionMode,
    compact: Boolean,
    settingsEnabled: Boolean,
    onOpenSettings: () -> Unit,
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
        Lens(
            illuminated = lensLit,
            modifier = Modifier
                .size(if (compact) 62.dp else 76.dp)
                .testTag("blue_lens"),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                val active = when (mode) {
                    ScannerMode.Capturing -> ScannerWarning
                    is ScannerMode.Result -> ScannerSuccess
                    is ScannerMode.Error -> Color(0xFFFF6B6B)
                    ScannerMode.ModelSetup -> ScannerWarning
                    ScannerMode.LoadingModels -> ScannerLensBlue
                    else -> ScannerLensBlue
                }
                StatusLamp(color = active, active = true)
                StatusLamp(color = ScannerWarning, active = mode == ScannerMode.Capturing)
                StatusLamp(color = ScannerSuccess, active = mode is ScannerMode.Result)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "POKEDEX // SCANNER 01",
                color = ScannerOutline,
                fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                maxLines = 1,
            )
            Text(
                text = when (page) {
                    ScannerPage.Catalog -> "POKEMON CATALOG"
                    ScannerPage.CatalogDetail -> "POKEMON DETAIL"
                    ScannerPage.Scanner -> mode.statusText()
                },
                color = ScannerPanel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 1,
            )
        }
        SettingsButton(
            mode = recognitionMode,
            enabled = settingsEnabled,
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun SettingsButton(
    mode: RecognitionMode,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .testTag("recognition_settings_button")
            .semantics { contentDescription = "宝可梦识别设置，当前${if (mode == RecognitionMode.Single) "单个" else "多个"}模式" },
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
    ScannerMode.ModelSetup -> "MODEL FILES REQUIRED"
    ScannerMode.LoadingModels -> "LOCAL AI STARTING"
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
    Canvas(modifier = modifier) {
        val stroke = 4.dp.toPx()
        drawCircle(color = ScannerOutline)
        drawCircle(color = ScannerPanel, radius = size.minDimension * 0.43f)
        drawCircle(color = ScannerOutline, radius = size.minDimension * 0.35f)
        drawCircle(
            color = ScannerLensBlue.copy(alpha = if (illuminated) 1f else 0.22f),
            radius = size.minDimension * 0.29f,
        )
        drawArc(
            color = ScannerLensHighlight.copy(alpha = if (illuminated) 1f else 0.16f),
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
        drawCircle(color = ScannerOutline)
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
    onMoveResult: (Int) -> Unit,
    canMoveResult: Boolean,
    feedbackCount: Int,
    onExportFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (expanded) {
        Column(
            modifier = modifier.padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SignalPanel(
                sampleCount = feedbackCount,
                onExport = onExportFeedback,
                modifier = Modifier.fillMaxWidth().height(76.dp),
            )
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
            DPad(
                enabled = page == ScannerPage.Scanner && mode is ScannerMode.Result && canMoveResult,
                onMoveResult = onMoveResult,
            )
        }
    } else {
        Row(
            modifier = modifier.padding(horizontal = 8.dp, vertical = 12.dp),
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
                SignalPanel(
                    sampleCount = feedbackCount,
                    onExport = onExportFeedback,
                    modifier = Modifier.fillMaxWidth().height(66.dp),
                )
            }

            PokeballButton(
                enabled = page == ScannerPage.Scanner &&
                    (mode == ScannerMode.Preview || mode is ScannerMode.Result || mode is ScannerMode.Error),
                onClick = onPrimaryAction,
                modifier = Modifier.size(82.dp),
            )

            DPad(
                enabled = page == ScannerPage.Scanner && mode is ScannerMode.Result && canMoveResult,
                onMoveResult = onMoveResult,
            )
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
                .border(3.dp, ScannerOutline, RoundedCornerShape(5.dp)),
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
        drawCircle(color = ScannerOutline, radius = diameter * 0.46f)
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
            color = ScannerOutline,
            start = androidx.compose.ui.geometry.Offset(center.x - diameter * 0.38f, center.y),
            end = androidx.compose.ui.geometry.Offset(center.x + diameter * 0.38f, center.y),
            strokeWidth = 6.dp.toPx(),
        )
        drawCircle(color = ScannerOutline, radius = diameter * 0.14f)
        drawCircle(color = ScannerPanel, radius = diameter * 0.075f)
    }
}

@Composable
private fun SignalPanel(
    sampleCount: Int,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .testTag("signal_export")
            .semantics { contentDescription = "Export $sampleCount labeled samples" }
            .clickable(
                enabled = sampleCount > 0,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExport,
            ),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(ScannerOutline, RoundedCornerShape(5.dp))
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
        Text(
            text = "DATA ${sampleCount.toString().padStart(3, '0')}",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(ScannerOutline.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
            color = ScannerPanel,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun DPad(enabled: Boolean, onMoveResult: (Int) -> Unit) {
    Box(modifier = Modifier.size(112.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val unit = size.minDimension / 3f
            val dPadPath = Path().apply {
                moveTo(unit, 0f)
                lineTo(unit * 2f, 0f)
                lineTo(unit * 2f, unit)
                lineTo(size.width, unit)
                lineTo(size.width, unit * 2f)
                lineTo(unit * 2f, unit * 2f)
                lineTo(unit * 2f, size.height)
                lineTo(unit, size.height)
                lineTo(unit, unit * 2f)
                lineTo(0f, unit * 2f)
                lineTo(0f, unit)
                lineTo(unit, unit)
                close()
            }
            drawPath(dPadPath, ScannerOutline)

            val inset = 4.dp.toPx()
            val inner = Path().apply {
                moveTo(unit + inset, inset)
                lineTo(unit * 2f - inset, inset)
                lineTo(unit * 2f - inset, unit + inset)
                lineTo(size.width - inset, unit + inset)
                lineTo(size.width - inset, unit * 2f - inset)
                lineTo(unit * 2f - inset, unit * 2f - inset)
                lineTo(unit * 2f - inset, size.height - inset)
                lineTo(unit + inset, size.height - inset)
                lineTo(unit + inset, unit * 2f - inset)
                lineTo(inset, unit * 2f - inset)
                lineTo(inset, unit + inset)
                lineTo(unit + inset, unit + inset)
                close()
            }
            drawPath(inner, ScannerGraphite)

            fun arrow(cx: Float, cy: Float, rotation: Float) {
                val arrowPath = Path().apply {
                    moveTo(cx, cy - 8.dp.toPx())
                    lineTo(cx - 7.dp.toPx(), cy + 5.dp.toPx())
                    lineTo(cx + 7.dp.toPx(), cy + 5.dp.toPx())
                    close()
                }
                rotate(rotation, pivot = androidx.compose.ui.geometry.Offset(cx, cy)) {
                    drawPath(arrowPath, ScannerOutline)
                }
            }
            arrow(size.width / 2f, unit / 2f, 0f)
            arrow(size.width / 2f, size.height - unit / 2f, 180f)
            arrow(unit / 2f, size.height / 2f, 270f)
            arrow(size.width - unit / 2f, size.height / 2f, 90f)
        }

        DPadHotspot(
            description = "Previous Pokemon",
            tag = "dpad_left",
            enabled = enabled,
            onClick = { onMoveResult(-1) },
            modifier = Modifier.align(Alignment.CenterStart),
        )
        DPadHotspot(
            description = "Next Pokemon",
            tag = "dpad_right",
            enabled = enabled,
            onClick = { onMoveResult(1) },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        DPadHotspot(
            description = "Move up",
            tag = "dpad_up",
            enabled = false,
            onClick = {},
            modifier = Modifier.align(Alignment.TopCenter),
        )
        DPadHotspot(
            description = "Move down",
            tag = "dpad_down",
            enabled = false,
            onClick = {},
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DPadHotspot(
    description: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .testTag(tag)
            .semantics { contentDescription = description }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}
