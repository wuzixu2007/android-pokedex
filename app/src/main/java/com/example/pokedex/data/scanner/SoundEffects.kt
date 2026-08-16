/* Replaceable short-effect storage and playback. / 可替换短音效的存储与播放模块。 */
package com.example.pokedex.data.scanner

import com.example.pokedex.domain.scanner.*

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

/**
 * Identifies every replaceable application sound slot.
 * 定义应用中每一个可替换的音效槽位。
 */
enum class AppSoundEffect(
    val chineseName: String,
    val englishName: String,
    val fileStem: String,
    val defaultFileName: String,
) {
    ScanStarted("开始扫描", "Scan started", "scan_started", "scan_start.wav"),
    RecognitionSuccess("识别成功", "Recognition success", "recognition_success", "recognition_success.wav"),
    RecognitionFailure("识别失败", "Recognition failure", "recognition_failure", "recognition_failure.wav"),
    Interaction("交互点击", "Interaction", "interaction", "scan_start.wav"),
    GameCorrect("游戏答对", "Game correct", "game_correct", "recognition_success.wav"),
    GameIncorrect("游戏答错", "Game incorrect", "game_incorrect", "recognition_failure.wav"),
}

fun AppSoundEffect.localizedName(language: AppLanguage): String =
    localizedText(language, chineseName, englishName)

/**
 * User-adjustable playback settings shared by every effect.
 * 所有音效共用的用户可调播放设置。
 */
data class SoundEffectSettings(
    val enabled: Boolean = true,
    val volume: Float = DEFAULT_VOLUME,
) {
    fun sanitized(): SoundEffectSettings = copy(volume = volume.finiteOr(DEFAULT_VOLUME).coerceIn(0f, 1f))

    companion object {
        const val DEFAULT_VOLUME = 0.82f
    }
}

enum class SoundAssetSource { BuiltIn, Custom }

/**
 * Immutable status displayed by the settings console.
 * 设置控制台展示的不可变音效状态。
 */
data class SoundAssetStatus(
    val effect: AppSoundEffect,
    val source: SoundAssetSource,
    val fileName: String,
)

sealed interface ResolvedSoundAsset {
    data class BuiltIn(val file: File) : ResolvedSoundAsset
    data class Custom(val file: File) : ResolvedSoundAsset
}

/**
 * Contract used by UI and recognition layers without depending on MediaPlayer.
 * UI 与识别层使用的音效调用接口，不直接依赖 MediaPlayer。
 */
interface PokemonSoundPlayer : Closeable {
    fun updateSettings(settings: SoundEffectSettings)
    fun play(effect: AppSoundEffect)
    fun stop()
}

/**
 * Owns custom sound files in app-private storage and falls back to packaged resources.
 * 管理应用私有目录中的自定义音频，并在缺失时回退到 APK 内置资源。
 */
class SoundEffectStore(private val context: Context) {
    private val directory = File(context.filesDir, DIRECTORY_NAME)

    fun statuses(): List<SoundAssetStatus> = AppSoundEffect.entries.map { effect ->
        customFile(effect)?.let { file ->
            SoundAssetStatus(effect, SoundAssetSource.Custom, file.name)
        } ?: SoundAssetStatus(effect, SoundAssetSource.BuiltIn, "内置默认音效")
    }

    fun resolve(effect: AppSoundEffect): ResolvedSoundAsset =
        customFile(effect)?.let(ResolvedSoundAsset::Custom)
            ?: ResolvedSoundAsset.BuiltIn(ResourceBundleRepository(context).file("raw/${effect.defaultFileName}"))

    suspend fun import(effect: AppSoundEffect, uri: Uri): SoundAssetStatus = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?: "custom_audio"
        val extension = extensionFor(displayName, resolver.getType(uri))
        require(extension in SUPPORTED_EXTENSIONS) { "仅支持 MP3、WAV、OGG、M4A 或 AAC 音频" }
        validateSource(uri)
        directory.mkdirs()
        val target = File(directory, "${effect.fileStem}.$extension")
        val part = File(directory, "${effect.fileStem}.$extension.part")
        part.delete()
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选音频文件" }
                part.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= MAX_FILE_BYTES) { "音频文件必须小于 15 MB" }
                        output.write(buffer, 0, count)
                    }
                    require(copied > 0) { "音频文件为空" }
                }
            }
            validateFile(part)
            moveReplacing(part, target)
            customFiles(effect).filterNot { it == target }.forEach(File::delete)
            SoundAssetStatus(effect, SoundAssetSource.Custom, displayName)
        } finally {
            part.delete()
        }
    }

    suspend fun reset(effect: AppSoundEffect): SoundAssetStatus = withContext(Dispatchers.IO) {
        customFiles(effect).forEach(File::delete)
        SoundAssetStatus(effect, SoundAssetSource.BuiltIn, "内置默认音效")
    }

    private fun customFile(effect: AppSoundEffect): File? =
        customFiles(effect).firstOrNull { it.isFile && it.length() > 0 }

    private fun customFiles(effect: AppSoundEffect): List<File> =
        directory.listFiles { file ->
            file.nameWithoutExtension == effect.fileStem && file.extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS
        }?.toList().orEmpty()

    private fun validateSource(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            requireValidDuration(retriever)
        } finally {
            retriever.release()
        }
    }

    private fun validateFile(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            requireValidDuration(retriever)
        } finally {
            retriever.release()
        }
    }

    private fun requireValidDuration(retriever: MediaMetadataRetriever) {
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        require(duration != null && duration in MIN_DURATION_MS..MAX_DURATION_MS) {
            "音效时长必须在 0.05 到 30 秒之间"
        }
    }

    /** Atomically replaces the same-extension target when the file system supports it. / 文件系统支持时原子替换同扩展名目标。 */
    private fun moveReplacing(source: File, destination: File) {
        runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun extensionFor(displayName: String, mimeType: String?): String {
        val nameExtension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (nameExtension in SUPPORTED_EXTENSIONS) return nameExtension
        return when (mimeType?.lowercase(Locale.ROOT)) {
            "audio/mpeg" -> "mp3"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/ogg" -> "ogg"
            "audio/mp4", "audio/m4a" -> "m4a"
            "audio/aac" -> "aac"
            else -> nameExtension
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "sound_effects"
        const val COPY_BUFFER_SIZE = 64 * 1024
        const val MAX_FILE_BYTES = 15L * 1024L * 1024L
        const val MIN_DURATION_MS = 50L
        const val MAX_DURATION_MS = 30_000L
        val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "aac")
    }
}

/**
 * MediaPlayer implementation that resolves the latest custom asset on every play.
 * 每次播放都会解析最新自定义文件的 MediaPlayer 实现。
 */
class AndroidPokemonSoundPlayer(
    context: Context,
    private val store: SoundEffectStore,
    private val onUnavailable: (String) -> Unit = {},
) : PokemonSoundPlayer {
    private val appContext = context.applicationContext
    private var settings = SoundEffectSettings()
    private var currentPlayer: MediaPlayer? = null
    private var closed = false

    override fun updateSettings(settings: SoundEffectSettings) {
        this.settings = settings.sanitized()
    }

    override fun play(effect: AppSoundEffect) {
        val safe = settings.sanitized()
        if (closed || !safe.enabled) return
        stop()
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        // OEMs commonly mute the sonification stream independently from media volume.
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                when (val asset = store.resolve(effect)) {
                    is ResolvedSoundAsset.Custom -> setDataSource(asset.file.absolutePath)
                    is ResolvedSoundAsset.BuiltIn -> setDataSource(asset.file.absolutePath)
                }
                setVolume(safe.volume, safe.volume)
                setOnPreparedListener { prepared ->
                    if (currentPlayer === prepared && !closed) prepared.start() else prepared.release()
                }
                setOnCompletionListener { completed ->
                    if (currentPlayer === completed) currentPlayer = null
                    completed.release()
                }
                setOnErrorListener { failed, _, _ ->
                    if (currentPlayer === failed) currentPlayer = null
                    failed.release()
                    onUnavailable("Unable to play sound / 无法播放音效: ${effect.fileStem}")
                    true
                }
                currentPlayer = this
                prepareAsync()
            }
        }.onFailure { error ->
            currentPlayer?.release()
            currentPlayer = null
            onUnavailable(error.message ?: "无法播放应用音效")
        }
    }

    override fun stop() {
        currentPlayer?.runCatching { stop() }
        currentPlayer?.release()
        currentPlayer = null
    }

    override fun close() {
        if (closed) return
        closed = true
        stop()
    }
}

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
