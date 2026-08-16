package com.example.pokedex.presentation.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.pokedex.data.scanner.VoicePackCatalog
import com.example.pokedex.data.scanner.VoicePackId
import com.example.pokedex.data.scanner.VoicePackRedeemResult
import com.example.pokedex.data.scanner.VoicePackRepository
import com.example.pokedex.data.scanner.VoicePackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Voice packs are deliberately local-only in the public build. */
class VoicePackViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VoicePackRepository(application)
    private val ids = VoicePackCatalog.definitions.map { it.id }.filter { it != VoicePackId.Custom }
    private val _statuses = MutableStateFlow(ids.associateWith(repository::quickStatus))
    val statuses = _statuses.asStateFlow()
    private val _unlockedVoicePackIds = MutableStateFlow<Set<VoicePackId>>(emptySet())
    val unlockedVoicePackIds = _unlockedVoicePackIds.asStateFlow()

    fun ensureOriginalAvailable() = refresh()
    suspend fun redeem(code: String): VoicePackRedeemResult = VoicePackRedeemResult.Failure("公开版本不提供远程兑换，请按文档导入本地语音包")
    fun confirmMeteredDownload(id: VoicePackId) = updateStatus(id, VoicePackStatus.Failed("公开版本不联网下载语音包，请导入本地文件"))
    fun download(id: VoicePackId) = confirmMeteredDownload(id)
    fun update(id: VoicePackId) = confirmMeteredDownload(id)
    fun repair(id: VoicePackId) { repository.deleteInstalled(id); updateStatus(id, VoicePackStatus.NotInstalled) }
    fun cancel(id: VoicePackId) = updateStatus(id, repository.quickStatus(id))
    fun refresh() { ids.forEach { updateStatus(it, repository.quickStatus(it)) } }
    private fun updateStatus(id: VoicePackId, status: VoicePackStatus) { _statuses.value = _statuses.value + (id to status) }
}
