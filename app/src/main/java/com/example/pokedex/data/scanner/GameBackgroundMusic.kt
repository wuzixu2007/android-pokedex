package com.example.pokedex.data.scanner

import com.example.pokedex.domain.scanner.*

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import java.io.File

enum class GameBackgroundTrack(val displayName: String, val resourceFileName: String?) {
    Pallet("真新镇主题", "bgm_pallet.wav"),
    Pewter("尼比市主题", "bgm_pewter.wav"),
    Cerulean("华蓝市主题", "bgm_cerulean.wav"),
    Vermilion("枯叶市主题", "bgm_vermilion.wav"),
    Lavender("紫苑镇主题", "bgm_lavender.wav"),
    Celadon("玉虹市主题", "bgm_celadon.wav"),
    Cinnabar("红莲镇主题", "bgm_cinnabar.wav"),
    Custom("自定义背景音乐", null),
}

data class BackgroundMusicSettings(
    val enabled: Boolean = true,
    val volume: Float = 0.42f,
    val track: GameBackgroundTrack = GameBackgroundTrack.Pallet,
) {
    fun sanitized() = copy(volume = volume.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.42f)
}

class GameBackgroundMusicStore(private val context: Context) {
    private val directory = File(context.filesDir, "game_background_music")
    private val customFile get() = File(directory, "custom_bgm")

    fun hasCustomTrack() = customFile.isFile && customFile.length() > 0

    fun import(uri: Uri) {
        directory.mkdirs()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取背景音乐" }
            customFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    fun customPath(): String? = customFile.takeIf { it.isFile && it.length() > 0 }?.absolutePath

    fun resetCustomTrack() { customFile.delete() }
}

class GameBackgroundMusicPlayer(context: Context, private val store: GameBackgroundMusicStore) : AutoCloseable {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private var activeTrack: GameBackgroundTrack? = null
    private var closed = false

    fun update(settings: BackgroundMusicSettings) {
        val safe = settings.sanitized()
        if (closed || !safe.enabled || (safe.track == GameBackgroundTrack.Custom && !store.hasCustomTrack())) {
            stop()
            return
        }
        if (activeTrack != safe.track || player == null) start(safe.track, safe.volume) else {
            player?.let { active ->
                runCatching { active.setVolume(safe.volume, safe.volume) }.onFailure { stop() }
            }
        }
    }

    private fun start(track: GameBackgroundTrack, volume: Float) {
        stop()
        runCatching {
            val created = MediaPlayer()
            try {
                created.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                if (track == GameBackgroundTrack.Custom) {
                    setDataSource(requireNotNull(store.customPath()))
                } else {
                    setDataSource(ResourceBundleRepository(appContext).file("raw/${requireNotNull(track.resourceFileName)}").absolutePath)
                }
                isLooping = true
                setVolume(volume, volume)
                setOnPreparedListener { prepared ->
                    if (player === prepared && !closed) runCatching { prepared.start() }.onFailure { stop() }
                    else prepared.runCatching { release() }
                }
                setOnCompletionListener { completed ->
                    if (player === completed && !closed) runCatching { completed.start() }.onFailure { stop() }
                }
                setOnErrorListener { failed, _, _ ->
                    if (player === failed) {
                        player = null
                        activeTrack = null
                    }
                    failed.runCatching { release() }
                    true
                }
                player = this
                activeTrack = track
                prepareAsync()
                }
                created
            } catch (error: Throwable) {
                created.runCatching { release() }
                throw error
            }
        }.onFailure { stop() }
    }

    fun stop() {
        player?.runCatching { stop() }
        player?.runCatching { release() }
        player = null
        activeTrack = null
    }

    override fun close() {
        closed = true
        stop()
    }
}

class GameBackgroundMusicSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("game_background_music", Context.MODE_PRIVATE)

    fun load() = BackgroundMusicSettings(
        enabled = preferences.getBoolean("enabled", true),
        volume = preferences.getFloat("volume", 0.42f),
        track = preferences.getString("track", GameBackgroundTrack.Pallet.name)
            ?.let { runCatching { GameBackgroundTrack.valueOf(it) }.getOrNull() }
            ?: GameBackgroundTrack.Pallet,
    ).sanitized()

    fun save(settings: BackgroundMusicSettings) {
        val safe = settings.sanitized()
        preferences.edit().putBoolean("enabled", safe.enabled).putFloat("volume", safe.volume).putString("track", safe.track.name).apply()
    }
}
