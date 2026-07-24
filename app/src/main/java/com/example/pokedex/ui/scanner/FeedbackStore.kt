/* Atomic local feedback persistence and ZIP export. / 本地反馈数据的原子持久化与 ZIP 导出。 */
package com.example.pokedex.ui.scanner

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class FeedbackPrediction(
    val pokemonKey: String,
    val standardName: String,
    val probability: Float?,
)

data class FeedbackSaveRequest(
    val imageJpeg: ByteArray,
    val correctLabel: FeedbackLabel,
    val predictions: List<FeedbackPrediction>,
    val confirmedCorrect: Boolean,
    val modelVersion: String,
)

data class FeedbackLabel(
    val key: String,
    val standardName: String,
)

data class FeedbackSaveResult(
    val sampleId: String,
    val created: Boolean,
)

class FeedbackStore(
    context: Context,
    private val rootDirectory: File = File(context.filesDir, "recognition-feedback"),
    private val exportDirectory: File = File(context.cacheDir, "feedback-exports"),
) {
    private val samplesDirectory = File(rootDirectory, "samples")
    private val mutex = Mutex()
    private val _sampleCount = MutableStateFlow(0)

    val sampleCount: StateFlow<Int> = _sampleCount.asStateFlow()

    init {
        samplesDirectory.mkdirs()
        exportDirectory.mkdirs()
        _sampleCount.value = metadataFiles().size
    }

    suspend fun save(request: FeedbackSaveRequest): FeedbackSaveResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(request.imageJpeg.isNotEmpty()) { "没有可保存的拍摄图片" }
            val imageSha256 = sha256(request.imageJpeg)
            val existing = metadataFiles()
                .mapNotNull(::readMetadata)
                .firstOrNull { metadata -> metadata.optString("imageSha256") == imageSha256 }
            val sampleId = existing?.optString("sampleId")
                ?.takeIf(String::isNotBlank)
                ?: UUID.randomUUID().toString()
            val imageFileName = "$sampleId.jpg"
            val metadata = JSONObject().apply {
                put("schemaVersion", SCHEMA_VERSION)
                put("sampleId", sampleId)
                put("imageFile", "images/$imageFileName")
                put("imageSha256", imageSha256)
                put("correctKey", request.correctLabel.key)
                put("correctName", request.correctLabel.standardName)
                put("confirmedCorrect", request.confirmedCorrect)
                put("modelVersion", request.modelVersion)
                put("createdAtEpochMillis", System.currentTimeMillis())
                put(
                    "predictions",
                    JSONArray().apply {
                        request.predictions.forEach { prediction ->
                            put(
                                JSONObject().apply {
                                    put("pokemonKey", prediction.pokemonKey)
                                    put("standardName", prediction.standardName)
                                    if (prediction.probability == null) put("probability", JSONObject.NULL)
                                    else put("probability", prediction.probability.toDouble())
                                },
                            )
                        }
                    },
                )
            }

            writeAtomic(File(samplesDirectory, imageFileName), request.imageJpeg)
            writeAtomic(
                File(samplesDirectory, "$sampleId.json"),
                metadata.toString().toByteArray(Charsets.UTF_8),
            )
            _sampleCount.value = metadataFiles().size
            FeedbackSaveResult(sampleId = sampleId, created = existing == null)
        }
    }

    suspend fun remove(sampleId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!SAMPLE_ID.matches(sampleId)) return@withLock false
            val metadata = File(samplesDirectory, "$sampleId.json")
            val image = File(samplesDirectory, "$sampleId.jpg")
            val removed = metadata.delete()
            image.delete()
            _sampleCount.value = metadataFiles().size
            removed
        }
    }

    suspend fun export(): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            val metadata = metadataFiles().mapNotNull(::readMetadata)
            require(metadata.isNotEmpty()) { "还没有可导出的标注样本" }
            exportDirectory.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
            val destination = File(exportDirectory, "pokedex-feedback-$timestamp.zip")
            val partial = File(exportDirectory, "${destination.name}.part")

            runCatching {
                ZipOutputStream(FileOutputStream(partial).buffered()).use { zip ->
                    val annotationLines = StringBuilder()
                    metadata.sortedBy { it.optString("sampleId") }.forEach { entry ->
                        val sampleId = entry.getString("sampleId")
                        val image = File(samplesDirectory, "$sampleId.jpg")
                        if (!image.isFile || sha256(image.readBytes()) != entry.getString("imageSha256")) {
                            error("样本 $sampleId 的图片缺失或校验失败")
                        }
                        zip.putNextEntry(ZipEntry("images/$sampleId.jpg"))
                        image.inputStream().buffered().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                        annotationLines.append(entry.toString()).append('\n')
                    }
                    zip.putNextEntry(ZipEntry("annotations.jsonl"))
                    zip.write(annotationLines.toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    val manifest = JSONObject().apply {
                        put("schemaVersion", SCHEMA_VERSION)
                        put("sampleCount", metadata.size)
                        put("createdAtEpochMillis", System.currentTimeMillis())
                        put("containsPersonalIdentifiers", false)
                    }
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                moveReplacing(partial, destination)
            }.onFailure {
                partial.delete()
                throw it
            }
            destination
        }
    }

    private fun metadataFiles(): List<File> = samplesDirectory
        .listFiles { file -> file.isFile && file.extension == "json" }
        ?.toList()
        .orEmpty()

    private fun readMetadata(file: File): JSONObject? = runCatching {
        JSONObject(file.readText(Charsets.UTF_8))
    }.getOrNull()

    private fun writeAtomic(destination: File, bytes: ByteArray) {
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, "${destination.name}.part")
        FileOutputStream(partial).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        moveReplacing(partial, destination)
    }

    private fun moveReplacing(source: File, destination: File) {
        runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private val SAMPLE_ID = Regex("[0-9a-fA-F-]{36}")

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
