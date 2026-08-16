package com.example.pokedex.data.scanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

enum class VoicePackId(val wireValue: String) {
    Original("original"), Rotom("rotom"), GgBond("ggbond"), LazySheep("lazy_sheep"), GrayWolf("gray_wolf"), Custom("custom");
    companion object { fun fromWire(value: String): VoicePackId? = entries.firstOrNull { it.wireValue == value } }
}

enum class VoicePackAccess { Free, Redeem, Contact }
enum class VoicePackDevelopmentState { Available, ComingSoon }

data class VoicePackDefinition(
    val id: VoicePackId,
    val displayName: String,
    val access: VoicePackAccess,
    val developmentState: VoicePackDevelopmentState,
)

object VoicePackCatalog {
    val definitions = listOf(
        VoicePackDefinition(VoicePackId.Original, "原版图鉴", VoicePackAccess.Free, VoicePackDevelopmentState.Available),
        VoicePackDefinition(VoicePackId.Rotom, "洛托姆图鉴", VoicePackAccess.Redeem, VoicePackDevelopmentState.ComingSoon),
        VoicePackDefinition(VoicePackId.GgBond, "猪猪侠图鉴", VoicePackAccess.Redeem, VoicePackDevelopmentState.ComingSoon),
        VoicePackDefinition(VoicePackId.LazySheep, "懒羊羊图鉴", VoicePackAccess.Redeem, VoicePackDevelopmentState.ComingSoon),
        VoicePackDefinition(VoicePackId.GrayWolf, "灰太狼图鉴", VoicePackAccess.Redeem, VoicePackDevelopmentState.ComingSoon),
        VoicePackDefinition(VoicePackId.Custom, "定制专属图鉴声音", VoicePackAccess.Contact, VoicePackDevelopmentState.ComingSoon),
    )
    fun definition(id: VoicePackId): VoicePackDefinition = definitions.first { it.id == id }
}

data class VoicePackFileEntry(val path: String, val sizeBytes: Long, val sha256: String)
data class VoicePackManifest(
    val voiceId: VoicePackId,
    val revision: Long,
    val contentVersion: String,
    val minAppVersion: Int,
    val bundleUrl: String,
    val bundleSizeBytes: Long,
    val bundleSha256: String,
    val files: List<VoicePackFileEntry>,
) {
    fun toJson() = JSONObject().apply {
        put("voiceId", voiceId.wireValue).put("revision", revision).put("contentVersion", contentVersion)
            .put("minAppVersion", minAppVersion).put("bundle", JSONObject().apply {
                put("url", bundleUrl).put("sizeBytes", bundleSizeBytes).put("sha256", bundleSha256)
            }).put("files", JSONArray().apply { files.forEach { put(JSONObject().apply {
                put("path", it.path).put("sizeBytes", it.sizeBytes).put("sha256", it.sha256)
            }) } })
    }
    companion object {
        fun fromJson(json: String): VoicePackManifest {
            val root = JSONObject(json.removePrefix("\uFEFF"))
            val bundle = root.getJSONObject("bundle")
            val files = root.getJSONArray("files").let { array -> List(array.length()) { i ->
                array.getJSONObject(i).let { VoicePackFileEntry(it.getString("path"), it.getLong("sizeBytes"), it.getString("sha256")) }
            } }
            return VoicePackManifest(
                requireNotNull(VoicePackId.fromWire(root.getString("voiceId"))), root.getLong("revision"),
                root.getString("contentVersion"), root.optInt("minAppVersion", 1), bundle.getString("url"),
                bundle.getLong("sizeBytes"), bundle.getString("sha256"), files,
            )
        }
    }
}

sealed interface VoicePackStatus {
    data object NotInstalled : VoicePackStatus
    data object WaitingForNetwork : VoicePackStatus
    data class AwaitingMeteredConsent(val sizeBytes: Long) : VoicePackStatus
    data class Downloading(val receivedBytes: Long, val totalBytes: Long) : VoicePackStatus
    data object Verifying : VoicePackStatus
    data class Installing(val completedFiles: Int, val totalFiles: Int) : VoicePackStatus
    data class Installed(val revision: Long, val contentVersion: String) : VoicePackStatus
    data class UpdateAvailable(val installedRevision: Long, val availableRevision: Long) : VoicePackStatus
    data class RepairRequired(val message: String) : VoicePackStatus
    data class Failed(val message: String) : VoicePackStatus
}

enum class VoicePackAction { Download, ConfirmMetered, Cancel, Update, Repair, Redeem, Contact }

object VoicePackConfig { const val ROOT_DIRECTORY = "voices" }

/** Local-only voice pack storage. Importing an archive is intentionally outside the network layer. */
class VoicePackRepository(context: Context) {
    private val root = File(context.applicationContext.filesDir, VoicePackConfig.ROOT_DIRECTORY)
    fun currentDirectory(id: VoicePackId): File = File(root, "${id.wireValue}/current")
    fun audioFile(id: VoicePackId, pokemonKey: String): File = File(currentDirectory(id), safeRelativePath("$pokemonKey.aac"))
    fun installedManifestFile(id: VoicePackId): File = File(currentDirectory(id), ".manifest.json")
    fun installedManifest(id: VoicePackId): VoicePackManifest? = runCatching { VoicePackManifest.fromJson(installedManifestFile(id).readText()) }.getOrNull()
    fun quickStatus(id: VoicePackId): VoicePackStatus {
        val manifest = installedManifest(id) ?: return VoicePackStatus.NotInstalled
        val valid = manifest.files.all { entry -> File(currentDirectory(id), safeRelativePath(entry.path)).let { it.isFile && it.length() == entry.sizeBytes } }
        return if (valid) VoicePackStatus.Installed(manifest.revision, manifest.contentVersion) else VoicePackStatus.RepairRequired("本地语音文件不完整")
    }
    fun deleteInstalled(id: VoicePackId) = currentDirectory(id).deleteRecursively()
    fun markRepairRequired(id: VoicePackId, message: String) { File(root, "${id.wireValue}/repair-required.txt").apply { parentFile?.mkdirs(); writeText(message) } }
    fun repairMessage(id: VoicePackId): String? = File(root, "${id.wireValue}/repair-required.txt").takeIf(File::isFile)?.readText()
}

internal fun safeRelativePath(path: String): String {
    val normalized = path.replace('\\', '/')
    require(!normalized.startsWith('/') && normalized.isNotBlank() && normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) { "不安全的文件路径" }
    return normalized
}

internal fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { digest ->
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(128 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

fun formatVoiceBytes(value: Long): String = when {
    value >= 1024L * 1024 * 1024 -> "%.2f GB".format(value / 1024f / 1024f / 1024f)
    value >= 1024L * 1024 -> "%.1f MB".format(value / 1024f / 1024f)
    else -> "$value B"
}
