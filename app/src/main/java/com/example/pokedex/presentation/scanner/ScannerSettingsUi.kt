/* Scanner settings UI. */
/* Compose scanner screens, dialogs, and Android UI integration. / Compose 扫描页面、弹窗与 Android UI 集成。 */
package com.example.pokedex.presentation.scanner

import com.example.pokedex.data.scanner.*
import com.example.pokedex.domain.scanner.*

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.example.pokedex.ui.theme.ScannerError
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

private enum class SettingsConsoleTab(val label: String) {
    Common("通用"),
    General("AI 设置"),
    Voice("语音"),
    Sound("音效"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecognitionSettingsDialog(
    scannerSettings: ScannerSettings,
    lastInferenceMillis: Long?,
    settingsTest: SettingsTestUiState,
    voicePackStatuses: Map<VoicePackId, VoicePackStatus>,
    unlockedVoicePackIds: Set<VoicePackId>,
    soundAssets: List<SoundAssetStatus>,
    backgroundMusicSettings: BackgroundMusicSettings,
    hasCustomBackgroundMusic: Boolean,
    onPreviewNarration: (NarrationSettings) -> Unit,
    onDownloadVoicePack: (VoicePackId) -> Unit,
    onUpdateVoicePack: (VoicePackId) -> Unit,
    onRepairVoicePack: (VoicePackId) -> Unit,
    onCancelVoicePack: (VoicePackId) -> Unit,
    onRedeemVoicePack: suspend (String) -> VoicePackRedeemResult,
    onPreviewSound: (AppSoundEffect, SoundEffectSettings) -> Unit,
    onImportSound: (AppSoundEffect) -> Unit,
    onResetSound: (AppSoundEffect) -> Unit,
    onBackgroundMusicChanged: (BackgroundMusicSettings) -> Unit,
    onImportBackgroundMusic: () -> Unit,
    onResetBackgroundMusic: () -> Unit,
    onSave: (ScannerSettings) -> Unit,
    onTest: (ScannerSettings) -> Unit,
    aiHistory: List<AiResponseHistoryEntry>,
    onRemoveAiHistory: (String) -> Unit,
    onClearAiHistory: () -> Unit,
    onClearTransientCache: () -> Unit,
    onRepairResources: () -> Unit,
    resourceBackgroundStatus: ResourceBackgroundStatus,
    onUpdateResources: () -> Unit,
    palette: ThemePalette,
    onThemeSelected: (String) -> Unit,
    authorShareModeEnabled: Boolean,
    onActivateAuthorShareMode: (String) -> Boolean,
    onExitAuthorShareMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTabName by rememberSaveable { mutableStateOf(SettingsConsoleTab.Common.name) }
    val selectedTab = runCatching { SettingsConsoleTab.valueOf(selectedTabName) }
        .getOrDefault(SettingsConsoleTab.Common)
    var draft by remember(scannerSettings) { mutableStateOf(scannerSettings.sanitized()) }
    var historyVisible by rememberSaveable { mutableStateOf(false) }
    var themeVisible by rememberSaveable { mutableStateOf(false) }
    var aboutVisible by rememberSaveable { mutableStateOf(false) }
    var repairVisible by rememberSaveable { mutableStateOf(false) }
    var voiceRepairVisible by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).widthIn(max = 520.dp).heightIn(max = 720.dp)
                .background(ScannerPanel, RoundedCornerShape(6.dp))
                .border(4.dp, ScannerBorder, RoundedCornerShape(6.dp))
                .padding(16.dp).testTag("recognition_settings_dialog"),
        ) {
            Text("设置", color = ScannerOutline, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SettingsConsoleTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTabName = tab.name },
                        shape = SegmentedButtonDefaults.itemShape(index, SettingsConsoleTab.entries.size),
                        modifier = Modifier.testTag("settings_tab_${tab.name}"),
                    ) { Text(tab.label, fontSize = 10.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (selectedTab) {
                    SettingsConsoleTab.Common -> GeneralSettingsPanel(
                        onOpenHistory = { historyVisible = true },
                        onOpenThemes = { themeVisible = true },
                        onOpenAbout = { aboutVisible = true },
                        onClearCache = onClearTransientCache,
                        onRepairResources = { repairVisible = true },
                        resourceBackgroundStatus = resourceBackgroundStatus,
                        onUpdateResources = onUpdateResources,
                    )
                    SettingsConsoleTab.General -> GeneralAiSettingsPanel(
                        settings = draft,
                        onChange = { draft = it },
                        onTest = { onTest(draft.sanitized()) },
                        testing = settingsTest.running,
                        testMessage = settingsTest.message,
                        authorShareModeEnabled = authorShareModeEnabled,
                        onActivateAuthorShareMode = onActivateAuthorShareMode,
                        onExitAuthorShareMode = onExitAuthorShareMode,
                    )
                    SettingsConsoleTab.Voice -> VoiceSettingsPanel(
                        narration = draft.narrationSettings,
                        voicePackStatuses = voicePackStatuses,
                        unlockedVoicePackIds = unlockedVoicePackIds,
                        onChange = { draft = draft.copy(narrationSettings = it) },
                        onPreview = onPreviewNarration,
                        onDownload = onDownloadVoicePack,
                        onUpdate = onUpdateVoicePack,
                        onRepair = { voiceRepairVisible = true },
                        onCancel = onCancelVoicePack,
                        onContact = { aboutVisible = true },
                        onRedeem = onRedeemVoicePack,
                    )
                    SettingsConsoleTab.Sound -> SoundSettingsPanel(
                        settings = draft.soundEffectSettings,
                        assets = soundAssets,
                        backgroundMusicSettings = backgroundMusicSettings,
                        hasCustomBackgroundMusic = hasCustomBackgroundMusic,
                        onChange = { draft = draft.copy(soundEffectSettings = it) },
                        onPreview = onPreviewSound,
                        onImport = onImportSound,
                        onReset = onResetSound,
                        onBackgroundMusicChanged = onBackgroundMusicChanged,
                        onImportBackgroundMusic = onImportBackgroundMusic,
                        onResetBackgroundMusic = onResetBackgroundMusic,
                    )
                }
                lastInferenceMillis?.let { Text("最近请求：${it}ms", color = ScannerGraphiteLight, fontSize = 10.sp) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消", color = ScannerRedDark) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(draft.sanitized()) },
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerRedDark),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("settings_save"),
                ) { Text("保存", fontWeight = FontWeight.Black) }
            }
        }
    }
    if (historyVisible) {
        AiResponseHistoryDialog(
            entries = aiHistory,
            historyLimit = draft.aiHistoryLimit,
            onHistoryLimitChange = { draft = draft.copy(aiHistoryLimit = it) },
            onRemove = onRemoveAiHistory,
            onClear = onClearAiHistory,
        ) { historyVisible = false }
    }
    if (themeVisible) ThemePickerDialog(palette, onThemeSelected) { themeVisible = false }
    if (aboutVisible) AboutApplicationDialog { aboutVisible = false }
    if (repairVisible) ResourceRepairDialog(
        onDismiss = { repairVisible = false },
        onConfirm = {
            repairVisible = false
            onDismiss()
            onRepairResources()
        },
    )
    if (voiceRepairVisible) VoicePackRepairDialog(
        onDismiss = { voiceRepairVisible = false },
        onConfirm = {
            voiceRepairVisible = false
            onRepairVoicePack(draft.narrationSettings.selectedVoicePackId)
        },
    )
}

@Composable
private fun VoicePackRepairDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修复当前语音包") },
        text = { Text("这会停止当前播报，删除选中的语音包并重新下载。图鉴资源、设置、历史和图库不会删除。") },
        confirmButton = { Button(onClick = onConfirm) { Text("开始修复") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun GeneralSettingsPanel(
    onOpenHistory: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenAbout: () -> Unit,
    onClearCache: () -> Unit,
    onRepairResources: () -> Unit,
    resourceBackgroundStatus: ResourceBackgroundStatus,
    onUpdateResources: () -> Unit,
) {
    Text("通用", color = ScannerOutline, fontSize = 14.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(8.dp))
    GeneralSettingsGroup("外观") {
        GeneralSettingItem("主题管理", "选择并应用全局外观", onOpenThemes, "theme_manager")
    }
    GeneralSettingsGroup("诊断与存储") {
        GeneralSettingItem("AI 返回历史", "查看服务端原始响应与错误记录", onOpenHistory, "ai_response_history")
        GeneralSettingItem("一键清除缓存", "仅清理临时缓存，不会删除设置和历史", onClearCache, "clear_cache")
        when (val status = resourceBackgroundStatus) {
            ResourceBackgroundStatus.Checking -> GeneralSettingItem(
                "资源包", "正在后台检查本地资源和更新", {}, "resource_checking",
            )
            is ResourceBackgroundStatus.Current -> GeneralSettingItem(
                "资源包", "当前版本 v${status.version}", {}, "resource_current",
            )
            is ResourceBackgroundStatus.UpdateAvailable -> GeneralSettingItem(
                "资源包更新", "当前 v${status.currentVersion}，可更新至 v${status.availableVersion}", onUpdateResources, "resource_update",
            )
            is ResourceBackgroundStatus.CheckFailed -> GeneralSettingItem(
                "资源包检查失败", "v${status.version} 可继续使用：${status.message}", {}, "resource_check_failed",
            )
        }
        GeneralSettingItem("修复资源包", "删除已下载资源并重新下载，不会删除设置和图库", onRepairResources, "repair_resources")
    }
    GeneralSettingsGroup("应用") {
        GeneralSettingItem("关于本应用", "作者抖音账号与应用信息", onOpenAbout, "about_application")
    }
}

@Composable
private fun ResourceRepairDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(ScannerPanel, RoundedCornerShape(8.dp))
                .border(3.dp, ScannerBorder, RoundedCornerShape(8.dp)).padding(18.dp),
        ) {
            Text("修复资源包", color = ScannerOutline, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            Text("这会删除当前下载的图鉴、图片、小游戏和音视频资源，并重新下载。设置、识别历史和图库不会删除。", color = ScannerGraphite, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(10.dp))
                Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = ScannerRedDark)) { Text("开始修复") }
            }
        }
    }
}

@Composable
internal fun GeneralSettingsGroup(title: String, content: @Composable () -> Unit) {
    Text(title, color = ScannerGraphite, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp))
    Column(
        modifier = Modifier.fillMaxWidth().background(ScannerPanel.copy(alpha = 0.62f), RoundedCornerShape(5.dp))
            .border(1.dp, ScannerBorder.copy(alpha = 0.65f), RoundedCornerShape(5.dp)),
    ) { content() }
}

@Composable
internal fun GeneralSettingItem(title: String, subtitle: String, onClick: () -> Unit, tag: String) {
    Box(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(title, color = ScannerOutline, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = ScannerGraphite, fontSize = 10.sp)
        }
    }
}

@Composable
internal fun AboutApplicationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.background(ScannerPanel, RoundedCornerShape(6.dp)).border(3.dp, ScannerBorder, RoundedCornerShape(6.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painter = painterResource(R.mipmap.ic_launcher_foreground), contentDescription = "应用图标", modifier = Modifier.size(88.dp))
            Spacer(Modifier.height(14.dp))
            Text("关于本应用", color = ScannerOutline, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            Text("宝可梦图鉴V3.2.1", color = ScannerGraphite, fontSize = 12.sp)
            Text("作者抖音/微信同号：wuzixu2026", color = ScannerRedDark, fontWeight = FontWeight.Black, modifier = Modifier.clickable {
                clipboard.setText(AnnotatedString("wuzixu2026"))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }.padding(10.dp))
            OutlinedTextField(
                value = "VERSTAPPEN\n胜天半子。\n裁决\n若相惜、卟弃",
                onValueChange = {},
                readOnly = true,
                label = { Text("赞助者", color = ScannerGraphiteLight) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = ScannerOutline,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                ),
                singleLine = false,
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
            )
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}

@Composable
internal fun GeneralAiSettingsPanel(
    settings: ScannerSettings,
    onChange: (ScannerSettings) -> Unit,
    onTest: () -> Unit,
    testing: Boolean,
    testMessage: String?,
    authorShareModeEnabled: Boolean,
    onActivateAuthorShareMode: (String) -> Boolean,
    onExitAuthorShareMode: () -> Unit,
) {
    var cdkVisible by remember { mutableStateOf(false) }
    var noticeVisible by remember { mutableStateOf(false) }
    var exitTapCount by remember { mutableIntStateOf(0) }
    var lastExitTapAt by remember { mutableStateOf(0L) }
    if (authorShareModeEnabled) {
        Text("作者 API 分享模式", color = ScannerOutline, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("分享配置有效期为 7 日，期间云端 AI 参数不可查看或编辑。", color = ScannerGraphite, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val now = System.currentTimeMillis()
                exitTapCount = if (now - lastExitTapAt > 3_000L) 1 else exitTapCount + 1
                lastExitTapAt = now
                if (exitTapCount >= 5) {
                    exitTapCount = 0
                    onExitAuthorShareMode()
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("author_share_exit"),
            shape = RoundedCornerShape(6.dp),
        ) { Text("连续点击5次退出分享模式", fontWeight = FontWeight.Black) }
        if (noticeVisible) AuthorShareNoticeDialog { noticeVisible = false }
        return
    }
    var advancedEnabled by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("高级设置(请不要随意更改里内容)", color = ScannerOutline, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { advancedEnabled = !advancedEnabled },
            modifier = Modifier.height(36.dp).testTag("ai_advanced_toggle"),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = if (advancedEnabled) ScannerRedDark else ScannerGraphite),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
        ) { Text(if (advancedEnabled) "已展开" else "展开", fontSize = 10.sp, fontWeight = FontWeight.Black) }
    }
    if (advancedEnabled) {
        var providerMenuExpanded by remember { mutableStateOf(false) }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ScannerGraphiteLight.copy(alpha = 0.35f)))
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onTest,
            enabled = !testing && settings.userAi.apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("settings_test"),
            colors = ButtonDefaults.buttonColors(containerColor = ScannerGraphite),
            shape = RoundedCornerShape(4.dp),
        ) { Text(if (testing) "正在测试" else "测试连接") }
        testMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = if (testing) ScannerWarning else ScannerGraphite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("settings_test_status"),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("云端 AI", color = ScannerOutline, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth()) {
            Button(
                onClick = { providerMenuExpanded = true },
                modifier = Modifier.fillMaxWidth().testTag("cloud_provider_selector"),
                colors = ButtonDefaults.buttonColors(containerColor = ScannerGraphite),
                shape = RoundedCornerShape(4.dp),
            ) { Text(settings.userAi.provider.displayName) }
            DropdownMenu(expanded = providerMenuExpanded, onDismissRequest = { providerMenuExpanded = false }) {
                CloudAiProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.displayName) },
                        onClick = {
                            providerMenuExpanded = false
                            onChange(settings.withProvider(provider))
                        },
                        modifier = Modifier.testTag("cloud_provider_${provider.name}"),
                    )
                }
            }
        }
        SettingsTextField("API Key", settings.userAi.apiKey, { onChange(settings.copy(userAi = settings.userAi.copy(apiKey = it))) }, "cloud_api_key")
        SettingsTextField("模型 ID", settings.userAi.model, { onChange(settings.copy(userAi = settings.userAi.copy(model = it))) }, "cloud_model")
        SettingsTextField("请求超时（秒）", settings.userAi.timeoutSeconds.toString(), {
            it.toIntOrNull()?.let { value -> onChange(settings.copy(userAi = settings.userAi.copy(timeoutSeconds = value))) }
        }, "cloud_timeout")
        Spacer(Modifier.height(12.dp))
        DeveloperSettingsPanel(
            settings = settings.developerAi,
            onChange = { onChange(settings.copy(developerAi = it)) },
            onReset = { onChange(settings.withProvider(CloudAiProvider.VolcArk)) },
        )
        Spacer(Modifier.height(10.dp))
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { cdkVisible = true },
        modifier = Modifier.fillMaxWidth().testTag("author_share_entry"),
        colors = ButtonDefaults.buttonColors(containerColor = ScannerRedDark),
        shape = RoundedCornerShape(6.dp),
    ) { Text("点击这里使用作者的API Key", fontSize = 11.sp, fontWeight = FontWeight.Black) }
    if (cdkVisible) {
        AuthorCdkDialog(
            onDismiss = { cdkVisible = false },
            onConfirm = { cdk ->
                if (onActivateAuthorShareMode(cdk)) {
                    cdkVisible = false
                    noticeVisible = true
                    null
                } else "兑换码错误，请重新输入"
            },
        )
    }
    if (noticeVisible) AuthorShareNoticeDialog { noticeVisible = false }
}

@Composable
internal fun AuthorCdkDialog(onDismiss: () -> Unit, onConfirm: (String) -> String?) {
    var cdk by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(ScannerPanel, RoundedCornerShape(8.dp)).border(3.dp, ScannerBorder, RoundedCornerShape(8.dp)).padding(18.dp)) {
            Text("启用作者 API 分享", color = ScannerOutline, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = cdk,
                onValueChange = { cdk = it; error = null },
                label = { Text("请输入“作者伍子胥免费分享”") },
                placeholder = { Text("请输入“作者伍子胥免费分享”", color = ScannerGraphiteLight) },
                singleLine = true,
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier.fillMaxWidth().testTag("author_share_cdk"),
            )
            error?.let { Text(it, color = ScannerError, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { error = onConfirm(cdk) }, modifier = Modifier.testTag("author_share_confirm")) { Text("确认") }
            }
        }
    }
}

@Composable
internal fun AuthorShareNoticeDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ScannerPanel).border(3.dp, ScannerBorder, RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("点击任意位置关闭弹窗", color = ScannerOutline, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(22.dp))
            Text("一次输入仅可使用7日，7日后需要重新输入，本功能开启后将使用作者的API，API由作者自费免费提供给大家使用，此设置后续可能会下架", color = ScannerGraphite, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun ThemePickerDialog(current: ThemePalette, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 420.dp).heightIn(max = 680.dp).background(ScannerPanel, RoundedCornerShape(6.dp)).border(3.dp, ScannerBorder, RoundedCornerShape(6.dp)).padding(16.dp)) {
            Text("主题管理", color = ScannerOutline, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ThemeCatalog.themes.forEach { theme ->
                    Button(onClick = { onSelect(theme.id); onDismiss() }, Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.primary)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            themePreviewResource(theme.id)?.let { resource ->
                                Image(painter = painterResource(resource), contentDescription = null, modifier = Modifier.size(34.dp))
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(if (theme.id == current.id) "${theme.name}（当前）" else theme.name)
                        }
                    }
                }
            }
        }
    }
}

internal fun themePreviewResource(themeId: String): Int? = when (themeId) {
    "default" -> R.mipmap.ic_launcher_foreground
    "great" -> R.drawable.theme_great_ball
    "ultra" -> R.drawable.theme_ultra_ball
    "luxury" -> R.drawable.theme_luxury_ball
    "dream" -> R.drawable.theme_dream_ball
    "quick" -> R.drawable.theme_quick_ball
    "master" -> R.drawable.theme_master_ball
    "beast" -> R.drawable.theme_beast_ball
    "moon" -> R.drawable.theme_moon_ball
    "cherish" -> R.drawable.theme_cherish_ball
    else -> null
}

@Composable
internal fun AiResponseHistoryDialog(
    entries: List<AiResponseHistoryEntry>,
    historyLimit: Int,
    onHistoryLimitChange: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val entry = entries.firstOrNull { it.id == selected }
    val context = LocalContext.current
    var copyConfirmation by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.94f).heightIn(max = 720.dp).background(ScannerPanel, RoundedCornerShape(6.dp)).border(4.dp, ScannerBorder, RoundedCornerShape(6.dp)).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (entry == null) "AI 返回历史" else "返回详情", color = ScannerOutline, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                if (entry == null && entries.isNotEmpty()) TextButton(onClick = onClear) { Text("清空") }
                if (entry != null) TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("AI 返回详情", entry.copyableText()))
                        copyConfirmation = true
                    },
                ) { Text(if (copyConfirmation) "已复制" else "复制全部信息") }
                TextButton(onClick = { if (entry == null) onDismiss() else selected = null }) { Text("返回") }
            }
            if (entry == null) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("最多保留", color = ScannerOutline, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onHistoryLimitChange((historyLimit - 1).coerceAtLeast(2)) }) { Text("−") }
                    Text("${historyLimit} 条", color = ScannerGraphite, fontSize = 12.sp)
                    TextButton(onClick = { onHistoryLimitChange((historyLimit + 1).coerceAtMost(20)) }) { Text("+") }
                }
            }
            if (entry == null) LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(entries, key = { _, item -> item.id }) { _, item ->
                    Column(Modifier.fillMaxWidth().clickable { selected = item.id }.padding(vertical = 10.dp).border(1.dp, ScannerBorder, RoundedCornerShape(4.dp)).padding(8.dp)) {
                        Text("${item.source} · ${item.model}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(item.error ?: item.parsedName ?: "已返回", color = if (item.error == null) ScannerSuccess else ScannerError, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text("${entry.provider} · ${entry.protocol} · ${entry.model}", fontSize = 11.sp)
                    Text("HTTP ${entry.httpStatus ?: "—"} · ${entry.elapsedMillis ?: "—"}ms", fontSize = 11.sp)
                    entry.requestId?.let { Text("请求 ID：$it", fontSize = 10.sp) }
                    entry.error?.let { Text(it, color = ScannerError, fontSize = 11.sp) }
                    Text(entry.rawResponse.ifBlank { "服务端未返回正文" }, fontSize = 10.sp)
                }
                TextButton(onClick = { onRemove(entry.id); selected = null }) { Text("删除此条", color = ScannerRedDark) }
            }
        }
    }
}

private fun AiResponseHistoryEntry.copyableText(): String = buildString {
    appendLine("来源：$source")
    appendLine("服务商：$provider")
    appendLine("协议：$protocol")
    appendLine("模型：$model")
    appendLine("HTTP 状态：${httpStatus ?: "—"}")
    appendLine("耗时：${elapsedMillis ?: "—"}ms")
    appendLine("请求 ID：${requestId ?: "—"}")
    appendLine("解析结果：${parsedName ?: "—"}")
    appendLine("错误：${error ?: "—"}")
    appendLine()
    appendLine("原始响应：")
    append(rawResponse.ifBlank { "服务端未返回正文" })
}

@Composable
internal fun DeveloperSettingsPanel(
    settings: DeveloperAiSettings,
    onChange: (DeveloperAiSettings) -> Unit,
    onReset: () -> Unit,
) {
    Text("开发者设置", color = ScannerOutline, fontSize = 14.sp, fontWeight = FontWeight.Black)
    Text("修改这些参数可能导致接口不可用。", color = ScannerWarning, fontSize = 10.sp)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        CloudApiProtocol.entries.forEachIndexed { index, protocol ->
            SegmentedButton(
                selected = settings.protocol == protocol,
                onClick = { onChange(settings.copy(protocol = protocol)) },
                shape = SegmentedButtonDefaults.itemShape(index, CloudApiProtocol.entries.size),
            ) { Text(when (protocol) { CloudApiProtocol.Responses -> "Responses"; CloudApiProtocol.ChatCompletions -> "Chat"; CloudApiProtocol.AnthropicMessages -> "Claude" }, fontSize = 10.sp) }
        }
    }
    SettingsTextField("完整 API 地址", settings.apiUrl, { onChange(settings.copy(apiUrl = it)) }, "cloud_api_url")
    SettingsTextField("鉴权请求头", settings.authHeader, { onChange(settings.copy(authHeader = it)) }, "cloud_auth_header")
    SettingsTextField("鉴权前缀", settings.authScheme, { onChange(settings.copy(authScheme = it)) }, "cloud_auth_scheme")
    SettingsTextField("Temperature", settings.temperature.toString(), { it.toFloatOrNull()?.let { value -> onChange(settings.copy(temperature = value)) } }, "cloud_temperature")
    SettingsTextField("最大输出 Token", settings.maxTokens.toString(), { it.toIntOrNull()?.let { value -> onChange(settings.copy(maxTokens = value)) } }, "cloud_max_tokens")
    Text("思考模式", color = ScannerOutline, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ThinkingMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(selected = settings.thinking == mode, onClick = { onChange(settings.copy(thinking = mode)) }, shape = SegmentedButtonDefaults.itemShape(index, ThinkingMode.entries.size)) {
                Text(when (mode) { ThinkingMode.Auto -> "自动"; ThinkingMode.Enabled -> "开启"; ThinkingMode.Disabled -> "关闭" }, fontSize = 10.sp)
            }
        }
    }
    SettingsTextField("额外请求头 JSON", settings.extraHeadersJson, { onChange(settings.copy(extraHeadersJson = it)) }, "cloud_extra_headers", false)
    SettingsTextField("额外请求体 JSON", settings.extraBodyJson, { onChange(settings.copy(extraBodyJson = it)) }, "cloud_extra_body", false)
    SettingsTextField("识别提示词", settings.systemPrompt, { onChange(settings.copy(systemPrompt = it)) }, "cloud_system_prompt", false)
    settings.validate()?.let { Text(it, color = ScannerError, fontSize = 10.sp) }
    TextButton(onClick = onReset, modifier = Modifier.testTag("developer_reset")) { Text("恢复火山默认值") }
}

@Composable
internal fun SettingsTextField(label: String, value: String, onChange: (String) -> Unit, tag: String, singleLine: Boolean = true) {
    Spacer(Modifier.height(7.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        label = { Text(label, fontSize = 11.sp) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        visualTransformation = if (tag == "cloud_api_key") PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (tag == "cloud_api_key" && value.isNotEmpty()) {
            { TextButton(onClick = { onChange("") }, modifier = Modifier.testTag("clear_cloud_api_key")) { Text("×", fontSize = 20.sp) } }
        } else null,
        shape = RoundedCornerShape(4.dp),
    )
}

@Composable
internal fun VoiceSettingsPanel(
    narration: NarrationSettings,
    voicePackStatuses: Map<VoicePackId, VoicePackStatus>,
    unlockedVoicePackIds: Set<VoicePackId>,
    onChange: (NarrationSettings) -> Unit,
    onPreview: (NarrationSettings) -> Unit,
    onDownload: (VoicePackId) -> Unit,
    onUpdate: (VoicePackId) -> Unit,
    onRepair: (VoicePackId) -> Unit,
    onCancel: (VoicePackId) -> Unit,
    onContact: () -> Unit,
    onRedeem: suspend (String) -> VoicePackRedeemResult,
) {
    var redeemVisible by remember { mutableStateOf(false) }
    var codeRequestContent by rememberSaveable { mutableStateOf<String?>(null) }
    var showContactFallback by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            appText("图鉴播报音色", "Pokedex voice"),
            color = ScannerOutline,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                codeRequestContent = VoicePackCodeRequestCache(context).read()
                showContactFallback = true
            },
            modifier = Modifier.testTag("voice_redeem_request_code"),
        ) { Text("本地导入说明") }
        TextButton(
            onClick = { redeemVisible = true },
            modifier = Modifier.testTag("voice_redeem_entry"),
        ) { Text("输入兑换码") }
    }
    Spacer(Modifier.height(8.dp))
    VoicePackCatalog.definitions.forEach { definition ->
        val isSelected = narration.selectedVoicePackId == definition.id
        val packStatus = voicePackStatuses[definition.id] ?: VoicePackStatus.NotInstalled
        val unlocked = definition.id in unlockedVoicePackIds
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    definition.displayName + if (isSelected) " · 当前" else "",
                    color = if (definition.developmentState == VoicePackDevelopmentState.Available) ScannerOutline else ScannerGraphiteLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    when {
                        definition.id == VoicePackId.Custom -> "联系作者获取定制服务"
                        definition.id == VoicePackId.Original || unlocked -> voicePackStatusLabel(packStatus)
                        else -> "未安装"
                    },
                    color = ScannerGraphite,
                    fontSize = 9.sp,
                )
            }
            when {
                definition.id == VoicePackId.Custom -> TextButton(onClick = onContact, modifier = Modifier.testTag("voice_contact")) { Text("联系") }
                definition.access == VoicePackAccess.Redeem && unlocked -> VoicePackActionButton(
                    status = packStatus,
                    onSelect = { onChange(narration.copy(selectedVoicePackId = definition.id)) },
                    onDownload = { onDownload(definition.id) },
                    onUpdate = { onUpdate(definition.id) },
                    onRepair = { onRepair(definition.id) },
                    onCancel = { onCancel(definition.id) },
                )
                definition.access == VoicePackAccess.Redeem -> Unit
                definition.id == VoicePackId.Original -> VoicePackActionButton(
                    status = packStatus,
                    onSelect = { onChange(narration.copy(selectedVoicePackId = VoicePackId.Original)) },
                    onDownload = { onDownload(VoicePackId.Original) },
                    onUpdate = { onUpdate(VoicePackId.Original) },
                    onRepair = { onRepair(VoicePackId.Original) },
                    onCancel = { onCancel(VoicePackId.Original) },
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
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
        enabled = (voicePackStatuses[narration.selectedVoicePackId] ?: VoicePackStatus.NotInstalled) is VoicePackStatus.Installed ||
            (voicePackStatuses[narration.selectedVoicePackId] ?: VoicePackStatus.NotInstalled) is VoicePackStatus.UpdateAvailable,
        modifier = Modifier.testTag("narration_preview"),
    ) {
        Text(appText("试听", "Preview"), color = ScannerGraphite, fontWeight = FontWeight.Black)
    }
    if (redeemVisible) {
        VoicePackRedeemDialog(
            onDismiss = { redeemVisible = false },
            onRedeem = onRedeem,
        )
    }
    if (codeRequestContent != null || showContactFallback) {
        VoicePackCodeRequestResultDialog(
            content = codeRequestContent,
            onDismiss = {
                codeRequestContent = null
                showContactFallback = false
            },
            onContact = {
                codeRequestContent = null
                showContactFallback = false
                onContact()
            },
        )
    }
}

@Composable
internal fun VoicePackRedeemDialog(
    onDismiss: () -> Unit,
    onRedeem: suspend (String) -> VoicePackRedeemResult,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var locallyValidated by rememberSaveable { mutableStateOf(false) }
    var redeeming by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(ScannerPanel, RoundedCornerShape(8.dp))
                .border(3.dp, ScannerBorder, RoundedCornerShape(8.dp))
                .padding(18.dp),
        ) {
            Text("兑换图鉴音色", color = ScannerOutline, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("兑换码仅绑定当前设备", color = ScannerGraphite, fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { value ->
                    code = value.uppercase().take(24)
                    error = null
                    locallyValidated = false
                },
                label = { Text("兑换码") },
                placeholder = { Text("16 位兑换码") },
                singleLine = true,
                isError = error != null,
                supportingText = {
                    when {
                        error != null -> Text(error.orEmpty())
                        locallyValidated -> Text("格式校验通过，尚未提交到云端")
                        else -> Text("可直接输入，或使用带空格、短横线的格式")
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("voice_redeem_code"),
                shape = RoundedCornerShape(6.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        when (val result = prepareVoicePackRedemption(code)) {
                            is VoicePackRedemptionPreparation.Invalid -> error = result.message
                            is VoicePackRedemptionPreparation.Ready -> {
                                code = result.request.code
                                redeeming = true
                                scope.launch {
                                    when (val redeemResult = onRedeem(result.request.code)) {
                                        is VoicePackRedeemResult.Success -> onDismiss()
                                        is VoicePackRedeemResult.Failure -> error = redeemResult.message
                                    }
                                    redeeming = false
                                }
                            }
                        }
                    },
                    enabled = code.isNotBlank() && !redeeming,
                    modifier = Modifier.testTag("voice_redeem_submit"),
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerRedDark),
                    shape = RoundedCornerShape(6.dp),
                ) { Text(if (redeeming) "验证中..." else "验证") }
            }
        }
    }
}

@Composable
private fun VoicePackCodeRequestResultDialog(
    content: String?,
    onDismiss: () -> Unit,
    onContact: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .background(ScannerPanel, RoundedCornerShape(8.dp))
                .border(3.dp, ScannerBorder, RoundedCornerShape(8.dp))
                .padding(18.dp),
        ) {
            Text("请求结果", color = ScannerOutline, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            if (content != null) {
                Text(
                    content,
                    color = ScannerGraphite,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("请", color = ScannerGraphite, fontSize = 13.sp)
                Text(
                    "联系",
                    color = ScannerRedDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable(onClick = onContact).testTag("voice_code_request_contact"),
                )
                Text("作者获取", color = ScannerGraphite, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ScannerRedDark),
                    shape = RoundedCornerShape(6.dp),
                ) { Text("关闭") }
            }
        }
    }
}

@Composable
private fun VoicePackActionButton(
    status: VoicePackStatus,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onUpdate: () -> Unit,
    onRepair: () -> Unit,
    onCancel: () -> Unit,
) {
    when (status) {
        VoicePackStatus.NotInstalled, VoicePackStatus.WaitingForNetwork, is VoicePackStatus.AwaitingMeteredConsent ->
            Button(onClick = { onSelect(); onDownload() }, modifier = Modifier.testTag("voice_download")) { Text("下载") }
        is VoicePackStatus.Downloading, VoicePackStatus.Verifying, is VoicePackStatus.Installing ->
            Button(onClick = onCancel, modifier = Modifier.testTag("voice_cancel")) { Text("取消") }
        is VoicePackStatus.UpdateAvailable ->
            Button(onClick = { onSelect(); onUpdate() }, modifier = Modifier.testTag("voice_update")) { Text("更新") }
        is VoicePackStatus.RepairRequired, is VoicePackStatus.Failed ->
            Button(onClick = { onSelect(); onRepair() }, modifier = Modifier.testTag("voice_repair")) { Text("修复") }
        is VoicePackStatus.Installed -> TextButton(onClick = onSelect) { Text("选择") }
    }
}

private fun voicePackStatusLabel(status: VoicePackStatus): String = when (status) {
    VoicePackStatus.NotInstalled -> "未安装"
    VoicePackStatus.WaitingForNetwork -> "等待网络"
    is VoicePackStatus.AwaitingMeteredConsent -> "等待确认移动网络下载"
    is VoicePackStatus.Downloading -> "下载中 ${formatVoiceBytes(status.receivedBytes)} / ${formatVoiceBytes(status.totalBytes)}"
    VoicePackStatus.Verifying -> "正在校验"
    is VoicePackStatus.Installing -> "正在安装 ${status.completedFiles} / ${status.totalFiles}"
    is VoicePackStatus.Installed -> "已安装 ${status.contentVersion}"
    is VoicePackStatus.UpdateAvailable -> "发现新版本 r${status.availableRevision}"
    is VoicePackStatus.RepairRequired -> status.message
    is VoicePackStatus.Failed -> status.message
}

/**
 * Manages packaged defaults and user-imported replacements for each sound slot.
 * 管理每个音效槽位的内置默认资源与用户导入替换文件。
 */
@Composable
internal fun SoundSettingsPanel(
    settings: SoundEffectSettings,
    assets: List<SoundAssetStatus>,
    onChange: (SoundEffectSettings) -> Unit,
    onPreview: (AppSoundEffect, SoundEffectSettings) -> Unit,
    onImport: (AppSoundEffect) -> Unit,
    onReset: (AppSoundEffect) -> Unit,
    backgroundMusicSettings: BackgroundMusicSettings,
    hasCustomBackgroundMusic: Boolean,
    onBackgroundMusicChanged: (BackgroundMusicSettings) -> Unit,
    onImportBackgroundMusic: () -> Unit,
    onResetBackgroundMusic: () -> Unit,
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
    Spacer(Modifier.height(14.dp))
    Text("游戏背景音乐", color = ScannerOutline, fontSize = 14.sp, fontWeight = FontWeight.Black)
    ParameterHeader("播放状态", "背景音乐仅在游戏页面播放，离开游戏页面自动停止。")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(true to "开启", false to "关闭").forEachIndexed { index, option ->
            SegmentedButton(
                selected = backgroundMusicSettings.enabled == option.first,
                onClick = { onBackgroundMusicChanged(backgroundMusicSettings.copy(enabled = option.first)) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                modifier = Modifier.testTag("background_music_enabled_${option.first}"),
            ) { Text(option.second, fontSize = 10.sp) }
        }
    }
    NarrationSlider(
        label = "音量",
        value = backgroundMusicSettings.volume,
        valueRange = 0f..1f,
        valueText = "${(backgroundMusicSettings.volume * 100).roundToInt()}%",
        tag = "background_music_volume",
        onValueChange = { onBackgroundMusicChanged(backgroundMusicSettings.copy(volume = it)) },
    )
    var musicMenuVisible by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { musicMenuVisible = true },
            modifier = Modifier.testTag("background_music_selector"),
        ) { Text("曲目：${backgroundMusicSettings.track.displayName}") }
        DropdownMenu(expanded = musicMenuVisible, onDismissRequest = { musicMenuVisible = false }) {
            GameBackgroundTrack.entries.forEach { track ->
                DropdownMenuItem(
                    text = { Text(track.displayName) },
                    enabled = track != GameBackgroundTrack.Custom || hasCustomBackgroundMusic,
                    onClick = {
                        onBackgroundMusicChanged(backgroundMusicSettings.copy(track = track))
                        musicMenuVisible = false
                    },
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onImportBackgroundMusic, modifier = Modifier.testTag("background_music_import")) { Text("上传背景音乐") }
        TextButton(
            onClick = onResetBackgroundMusic,
            enabled = hasCustomBackgroundMusic,
            modifier = Modifier.testTag("background_music_reset"),
        ) { Text("移除自定义音乐") }
    }
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
internal fun ParameterHeader(label: String, help: String) {
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
internal fun IntSliderSetting(
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
internal fun DiscreteSliderSetting(
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
internal fun FloatSliderSetting(
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

@Composable
internal fun NarrationSlider(
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
