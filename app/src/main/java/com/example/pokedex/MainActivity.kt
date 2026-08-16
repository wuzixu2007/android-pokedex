/* App entry point and Compose host. / 应用入口与 Compose 宿主。 */
package com.example.pokedex

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pokedex.presentation.scanner.PokedexScannerScreen
import com.example.pokedex.presentation.scanner.ResourceBootstrapGate
import com.example.pokedex.presentation.scanner.ResourceBootstrapViewModel
import com.example.pokedex.presentation.scanner.VoicePackViewModel
import com.example.pokedex.data.scanner.VoicePackStatus
import com.example.pokedex.data.scanner.VoicePackId
import com.example.pokedex.data.scanner.formatVoiceBytes
import androidx.core.content.ContextCompat
import com.example.pokedex.ui.theme.PokedexTheme
import com.example.pokedex.ui.theme.ThemeStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeStore = remember { ThemeStore(applicationContext) }
            val palette by themeStore.palette.collectAsState()
            val resourceBootstrapViewModel: ResourceBootstrapViewModel = viewModel()
            val voicePackViewModel: VoicePackViewModel = viewModel()
            val resourceBackgroundStatus by resourceBootstrapViewModel.backgroundStatus.collectAsState()
            val voicePackStatuses by voicePackViewModel.statuses.collectAsState()
            val unlockedVoicePackIds by voicePackViewModel.unlockedVoicePackIds.collectAsState()
            val meteredVoicePack = voicePackStatuses.entries.firstOrNull {
                it.value is VoicePackStatus.AwaitingMeteredConsent
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            PokedexTheme(palette) {
                ResourceBootstrapGate(resourceBootstrapViewModel) {
                    LaunchedEffect(Unit) {
                        voicePackViewModel.ensureOriginalAvailable()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    PokedexScannerScreen(
                        palette = palette,
                        onThemeSelected = themeStore::select,
                        onRepairResources = resourceBootstrapViewModel::repair,
                        resourceBackgroundStatus = resourceBackgroundStatus,
                        onUpdateResources = resourceBootstrapViewModel::update,
                        voicePackStatuses = voicePackStatuses,
                        unlockedVoicePackIds = unlockedVoicePackIds,
                        onDownloadVoicePack = voicePackViewModel::download,
                        onUpdateVoicePack = voicePackViewModel::update,
                        onRepairVoicePack = voicePackViewModel::repair,
                        onCancelVoicePack = voicePackViewModel::cancel,
                        onRefreshVoicePack = voicePackViewModel::refresh,
                        onRedeemVoicePack = voicePackViewModel::redeem,
                    )
                    if (meteredVoicePack != null && meteredVoicePack.value is VoicePackStatus.AwaitingMeteredConsent) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("使用移动网络下载语音包") },
                            text = { Text("图鉴语音约 ${formatVoiceBytes((meteredVoicePack.value as VoicePackStatus.AwaitingMeteredConsent).sizeBytes)}，是否继续？") },
                            confirmButton = { Button(onClick = { voicePackViewModel.confirmMeteredDownload(meteredVoicePack.key) }) { Text("继续下载") } },
                            dismissButton = { Button(onClick = { voicePackViewModel.cancel(meteredVoicePack.key) }) { Text("稍后") } },
                        )
                    }
                }
            }
        }
    }

}
