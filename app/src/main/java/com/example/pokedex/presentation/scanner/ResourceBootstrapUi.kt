package com.example.pokedex.presentation.scanner

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokedex.data.scanner.ResourceBootstrapState
import com.example.pokedex.data.scanner.ResourceBundleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ResourceBackgroundStatus {
    data object Checking : ResourceBackgroundStatus
    data class Current(val version: String) : ResourceBackgroundStatus
    data class UpdateAvailable(val currentVersion: String, val availableVersion: String) : ResourceBackgroundStatus
    data class CheckFailed(val version: String, val message: String) : ResourceBackgroundStatus
}

class ResourceBootstrapViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ResourceBundleRepository(application)
    private val _state = MutableStateFlow<ResourceBootstrapState>(ResourceBootstrapState.Checking)
    val state = _state.asStateFlow()
    private val _backgroundStatus = MutableStateFlow<ResourceBackgroundStatus>(ResourceBackgroundStatus.Checking)
    val backgroundStatus = _backgroundStatus.asStateFlow()

    init { start() }

    fun start() = viewModelScope.launch {
        _state.value = ResourceBootstrapState.Checking
        runCatching { repository.ensureBundledResources() }
            .onSuccess {
                _state.value = ResourceBootstrapState.Ready
                _backgroundStatus.value = ResourceBackgroundStatus.Current("bundled-v1")
            }
            .onFailure { error ->
                _state.value = ResourceBootstrapState.Failed(error.message ?: "内置资源不可用")
                _backgroundStatus.value = ResourceBackgroundStatus.CheckFailed("bundled-v1", error.message ?: "内置资源不可用")
            }
    }

    fun update() = start()
    fun dismissUpdate() { _state.value = ResourceBootstrapState.Ready }
    fun repair() = start()
}

@Composable
fun ResourceBootstrapGate(viewModel: ResourceBootstrapViewModel, content: @Composable () -> Unit) {
    val state by viewModel.state.collectAsState()
    when (val current = state) {
        ResourceBootstrapState.Ready -> content()
        ResourceBootstrapState.Checking -> {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ResourceBootstrapState.Failed -> {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Text(current.message)
            }
            LaunchedEffect(Unit) { viewModel.start() }
        }
    }
}
