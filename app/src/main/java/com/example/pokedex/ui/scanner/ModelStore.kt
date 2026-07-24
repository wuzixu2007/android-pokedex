/* Verified GGUF import and model manifest storage. / 经校验的 GGUF 导入与模型清单存储。 */
package com.example.pokedex.ui.scanner

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class ModelRole { LANGUAGE, VISION }

data class ModelSetStatus(
    val languageReady: Boolean,
    val visionReady: Boolean,
    val importingRole: ModelRole? = null,
    val importProgress: Float? = null,
    val error: String? = null,
) {
    val allReady: Boolean get() = languageReady && visionReady
}

data class ModelPaths(
    val languageModelPath: String,
    val visionModelPath: String,
)

class ModelStore(private val context: Context) {
    private val modelsDirectory = File(context.filesDir, "models")
    private val preferences = context.getSharedPreferences("model_manifest", Context.MODE_PRIVATE)
    private val specs = mapOf(
        ModelRole.LANGUAGE to ModelSpec(
            role = ModelRole.LANGUAGE,
            outputName = "MiniCPM-V-4_6-Q4_K_M.gguf",
            size = 529_101_504L,
            sha256 = "6B0C74962C44BC6BF4B655B9B02C13EDA9D5A0491543AE976D1AC18E4B7892E2",
            metadata = mapOf(
                "general.architecture" to "qwen35",
                "general.type" to "model",
                "general.name" to "MiniCPM V 4_6",
                "general.file_type" to "15",
            ),
        ),
        ModelRole.VISION to ModelSpec(
            role = ModelRole.VISION,
            outputName = "mmproj-model-f16.gguf",
            size = 1_108_746_944L,
            sha256 = "CA931D861D0801D9003E50697CD764721A334107C0E0415A51168EE1938462DE",
            metadata = mapOf(
                "general.architecture" to "clip",
                "general.type" to "mmproj",
                "general.name" to "MiniCPM V 4_6",
                "clip.projector_type" to "minicpmv4_6",
            ),
        ),
    )

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<ModelSetStatus> = _status.asStateFlow()

    fun pathsOrNull(): ModelPaths? {
        val current = _status.value
        if (!current.allReady) return null
        return ModelPaths(
            languageModelPath = modelFile(specs.getValue(ModelRole.LANGUAGE)).absolutePath,
            visionModelPath = modelFile(specs.getValue(ModelRole.VISION)).absolutePath,
        )
    }

    suspend fun importModel(role: ModelRole, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val spec = specs.getValue(role)
        val previous = readStatus()
        _status.value = previous.copy(importingRole = role, importProgress = 0f, error = null)

        runCatching {
            modelsDirectory.mkdirs()
            val reportedSize = querySize(uri)
            if (reportedSize != null && reportedSize != spec.size) {
                error("\u6a21\u578b\u6587\u4ef6\u5927\u5c0f\u4e0d\u5339\u914d")
            }
            val requiredBytes = spec.size + MIN_FREE_SPACE_BYTES
            if (StatFs(modelsDirectory.absolutePath).availableBytes < requiredBytes) {
                error("\u5b58\u50a8\u7a7a\u95f4\u4e0d\u8db3\uff0c\u8bf7\u81f3\u5c11\u9884\u7559 1.4 GB \u53ef\u7528\u7a7a\u95f4")
            }

            val partFile = File(modelsDirectory, "${spec.outputName}.part")
            if (partFile.exists() && !partFile.delete()) error("\u65e0\u6cd5\u6e05\u7406\u4e0a\u6b21\u5bfc\u5165\u7684\u4e34\u65f6\u6587\u4ef6")
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "\u65e0\u6cd5\u8bfb\u53d6\u6240\u9009\u6587\u4ef6" }
                    FileOutputStream(partFile).buffered().use { output ->
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            copied += count
                            _status.value = _status.value.copy(
                                importProgress = (copied.toDouble() / spec.size).toFloat().coerceIn(0f, 1f),
                            )
                        }
                    }
                }

                if (copied != spec.size) error("\u6a21\u578b\u6587\u4ef6\u5927\u5c0f\u4e0d\u5339\u914d")
                val actualHash = digest.digest().joinToString("") { "%02X".format(it) }
                if (actualHash != spec.sha256) error("\u6a21\u578b SHA-256 \u6821\u9a8c\u5931\u8d25")
                verifyGguf(partFile, spec)

                val target = modelFile(spec)
                if (target.exists() && !target.delete()) error("\u65e0\u6cd5\u66ff\u6362\u5df2\u5bfc\u5165\u6a21\u578b")
                if (!partFile.renameTo(target)) error("\u65e0\u6cd5\u5b8c\u6210\u6a21\u578b\u5bfc\u5165")
                preferences.edit().putString(hashPreferenceKey(role), actualHash).apply()
            } finally {
                if (partFile.exists()) partFile.delete()
            }
        }.onSuccess {
            _status.value = readStatus()
        }.onFailure { error ->
            _status.value = readStatus().copy(error = error.message ?: "\u6a21\u578b\u5bfc\u5165\u5931\u8d25")
        }
    }

    fun clearError() {
        _status.value = _status.value.copy(error = null)
    }

    private fun readStatus(): ModelSetStatus = ModelSetStatus(
        languageReady = isReady(specs.getValue(ModelRole.LANGUAGE)),
        visionReady = isReady(specs.getValue(ModelRole.VISION)),
    )

    private fun isReady(spec: ModelSpec): Boolean {
        val file = modelFile(spec)
        return file.isFile &&
            file.length() == spec.size &&
            preferences.getString(hashPreferenceKey(spec.role), null) == spec.sha256
    }

    private fun modelFile(spec: ModelSpec) = File(modelsDirectory, spec.outputName)

    private fun hashPreferenceKey(role: ModelRole) = "${role.name.lowercase()}_sha256"

    private fun querySize(uri: Uri): Long? = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }

    private fun verifyGguf(file: File, spec: ModelSpec) {
        val metadata = GgufMetadataReader.read(file, spec.metadata.keys)
        spec.metadata.forEach { (key, expected) ->
            check(metadata[key] == expected) {
                "\u6a21\u578b GGUF \u5143\u6570\u636e\u4e0d\u5339\u914d: $key"
            }
        }
    }

    private data class ModelSpec(
        val role: ModelRole,
        val outputName: String,
        val size: Long,
        val sha256: String,
        val metadata: Map<String, String>,
    )

    companion object {
        private const val COPY_BUFFER_SIZE = 1024 * 1024
        private const val MIN_FREE_SPACE_BYTES = 256L * 1024L * 1024L
    }
}

private object GgufMetadataReader {
    fun read(file: File, wantedKeys: Set<String>): Map<String, String> {
        LittleEndianReader(BufferedInputStream(FileInputStream(file), 1024 * 1024)).use { reader ->
            check(reader.readAscii(4) == "GGUF") { "\u4e0d\u662f GGUF \u6587\u4ef6" }
            check(reader.readUInt32() == 3L) { "\u4e0d\u652f\u6301\u7684 GGUF \u7248\u672c" }
            reader.readUInt64() // tensor count
            val metadataCount = reader.readUInt64()
            val result = mutableMapOf<String, String>()
            repeat(metadataCount.toInt()) {
                val key = reader.readString()
                val type = reader.readUInt32().toInt()
                if (key in wantedKeys) {
                    result[key] = reader.readScalarAsString(type)
                } else {
                    reader.skipValue(type)
                }
            }
            return result
        }
    }
}

private class LittleEndianReader(private val input: InputStream) : AutoCloseable {
    fun readAscii(count: Int): String = String(readBytes(count), Charsets.US_ASCII)

    fun readUInt32(): Long {
        val bytes = readBytes(4)
        return (bytes[0].toLong() and 0xFF) or
            ((bytes[1].toLong() and 0xFF) shl 8) or
            ((bytes[2].toLong() and 0xFF) shl 16) or
            ((bytes[3].toLong() and 0xFF) shl 24)
    }

    fun readUInt64(): Long {
        val bytes = readBytes(8)
        var result = 0L
        repeat(8) { index -> result = result or ((bytes[index].toLong() and 0xFF) shl (index * 8)) }
        return result
    }

    fun readString(): String {
        val length = readUInt64()
        require(length in 0..Int.MAX_VALUE.toLong()) { "Invalid GGUF string length" }
        return String(readBytes(length.toInt()), Charsets.UTF_8)
    }

    fun readScalarAsString(type: Int): String = when (type) {
        TYPE_UINT32 -> readUInt32().toString()
        TYPE_UINT64 -> readUInt64().toString()
        TYPE_STRING -> readString()
        else -> error("Unsupported GGUF metadata type $type")
    }

    fun skipValue(type: Int) {
        when (type) {
            TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> skipFully(1)
            TYPE_UINT16, TYPE_INT16 -> skipFully(2)
            TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> skipFully(4)
            TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> skipFully(8)
            TYPE_STRING -> skipFully(readUInt64())
            TYPE_ARRAY -> {
                val elementType = readUInt32().toInt()
                val count = readUInt64()
                val scalarSize = scalarSize(elementType)
                if (scalarSize != null) {
                    skipFully(Math.multiplyExact(count, scalarSize.toLong()))
                } else {
                    repeat(count.toInt()) { skipValue(elementType) }
                }
            }
            else -> error("Unsupported GGUF metadata type $type")
        }
    }

    private fun scalarSize(type: Int): Int? = when (type) {
        TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> 1
        TYPE_UINT16, TYPE_INT16 -> 2
        TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> 4
        TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> 8
        else -> null
    }

    private fun readBytes(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(bytes, offset, count - offset)
            if (read < 0) error("Unexpected end of GGUF file")
            offset += read
        }
        return bytes
    }

    private fun skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                if (input.read() < 0) error("Unexpected end of GGUF file")
                remaining--
            }
        }
    }

    override fun close() = input.close()

    companion object {
        private const val TYPE_UINT8 = 0
        private const val TYPE_INT8 = 1
        private const val TYPE_UINT16 = 2
        private const val TYPE_INT16 = 3
        private const val TYPE_UINT32 = 4
        private const val TYPE_INT32 = 5
        private const val TYPE_FLOAT32 = 6
        private const val TYPE_BOOL = 7
        private const val TYPE_STRING = 8
        private const val TYPE_ARRAY = 9
        private const val TYPE_UINT64 = 10
        private const val TYPE_INT64 = 11
        private const val TYPE_FLOAT64 = 12
    }
}
