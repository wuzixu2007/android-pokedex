package com.example.pokedex.data.scanner

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Resources are shipped in the APK under app/src/main/assets. */
object ResourceBundleConfig {
    const val ROOT_DIRECTORY = "pokedex-resources"
    private const val ASSETS_PREFIX = "assets/"

    fun assetPath(path: String): String = ASSETS_PREFIX + path.removePrefix(ASSETS_PREFIX)
}

data class ResourceFileEntry(val path: String, val sizeBytes: Long, val sha256: String)

data class ResourceBundleManifest(
    val version: String,
    val bundleUrl: String,
    val bundleSizeBytes: Long,
    val bundleSha256: String,
    val files: List<ResourceFileEntry>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("bundle", JSONObject().apply {
            put("url", bundleUrl)
            put("sizeBytes", bundleSizeBytes)
            put("sha256", bundleSha256)
        })
        put("files", JSONArray().apply {
            files.forEach { entry -> put(JSONObject().apply {
                put("path", entry.path)
                put("sizeBytes", entry.sizeBytes)
                put("sha256", entry.sha256)
            }) }
        })
    }

    companion object {
        fun fromJson(value: String): ResourceBundleManifest {
            val root = JSONObject(value.removePrefix("\uFEFF"))
            val bundle = root.getJSONObject("bundle")
            val fileArray = root.getJSONArray("files")
            val files = List(fileArray.length()) { index ->
                val file = fileArray.getJSONObject(index)
                ResourceFileEntry(file.getString("path"), file.getLong("sizeBytes"), file.getString("sha256"))
            }
            return ResourceBundleManifest(
                version = root.getString("version"),
                bundleUrl = bundle.getString("url"),
                bundleSizeBytes = bundle.getLong("sizeBytes"),
                bundleSha256 = bundle.getString("sha256"),
                files = files,
            )
        }
    }
}

sealed interface ResourceBootstrapState {
    data object Checking : ResourceBootstrapState
    data object Ready : ResourceBootstrapState
    data class Failed(val message: String, val canUseInstalledResources: Boolean = false) : ResourceBootstrapState
}

class ResourceBundleRepository(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, ResourceBundleConfig.ROOT_DIRECTORY)
    private val current = File(root, "current")
    private val marker = File(current, ".bundled")

    fun assetFile(path: String): File = file(ResourceBundleConfig.assetPath(path))

    fun file(path: String): File {
        val safe = requireSafePath(path)
        val target = File(current, safe)
        if (!target.isFile) materialize(safe, target)
        return target
    }

    fun openAsset(path: String) = appContext.assets.open(ResourceBundleConfig.assetPath(path))

    fun hasInstalledManifestFile(): Boolean = marker.isFile

    suspend fun ensureBundledResources(onProgress: (ResourceBootstrapState) -> Unit = {}) = withContext(Dispatchers.IO) {
        onProgress(ResourceBootstrapState.Checking)
        if (!marker.isFile) {
            root.mkdirs()
            copyTree("", current)
            marker.writeText("bundled-v1")
        }
        onProgress(ResourceBootstrapState.Ready)
    }

    suspend fun installedManifestOrNull(): ResourceBundleManifest? = null
    suspend fun installedManifestMetadataOrNull(): ResourceBundleManifest? = null
    suspend fun repair(onProgress: (ResourceBootstrapState) -> Unit = {}) = ensureBundledResources(onProgress)

    private fun copyTree(assetPath: String, target: File) {
        val children = appContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            materialize(assetPath, target)
            return
        }
        target.mkdirs()
        children.forEach { child ->
            val childAsset = if (assetPath.isEmpty()) child else "$assetPath/$child"
            copyTree(childAsset, File(target, child))
        }
    }

    private fun materialize(assetPath: String, target: File) {
        target.parentFile?.mkdirs()
        try {
            appContext.assets.open(assetPath).use { input -> target.outputStream().use(input::copyTo) }
        } catch (error: IOException) {
            throw IOException("内置资源不存在：$assetPath", error)
        }
    }

    private fun requireSafePath(path: String): String {
        val normalized = path.replace('\\', '/').removePrefix("/")
        require(normalized.isNotBlank() && normalized != "." && !normalized.split('/').contains("..")) {
            "非法资源路径"
        }
        return normalized
    }
}
