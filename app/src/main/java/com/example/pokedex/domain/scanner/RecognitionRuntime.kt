package com.example.pokedex.domain.scanner

import com.example.pokedex.data.scanner.ArkErrorKind
import com.example.pokedex.data.scanner.PokemonCatalog

import org.json.JSONObject
import java.io.Closeable

data class NormalizedImage(val jpeg: ByteArray, val feedbackJpeg: ByteArray = jpeg)
data class RecognitionCandidate(val standardName: String, val probability: Float?, val isShiny: Boolean = false)

sealed interface RecognitionResult {
    data class Success(val candidate: RecognitionCandidate) : RecognitionResult
    data class Failure(val message: String) : RecognitionResult
}

sealed interface ConnectionTestResult {
    data class Success(
        val model: String,
        val candidate: RecognitionCandidate,
        val elapsedMillis: Long,
        val requestId: String?,
    ) : ConnectionTestResult

    data class Failure(val kind: ArkErrorKind, val message: String, val requestId: String? = null) : ConnectionTestResult
}

interface RecognitionRepository : Closeable {
    suspend fun recognize(image: NormalizedImage): RecognitionResult
    suspend fun testConnection(image: NormalizedImage): ConnectionTestResult
    fun cancel()
}

object PokemonCandidateProtocol {
    private val requiredKeys = setOf("name", "probability", "isShiny")

    fun stripCodeFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed.substringAfter('\n', "")
            .removeSuffix("```")
            .trim()
    }

    fun parse(raw: String, catalog: PokemonCatalog): RecognitionCandidate {
        val item = JSONObject(stripCodeFence(raw))
        require(item.keys().asSequence().toSet() == requiredKeys) { "返回字段必须且只能包含 name、probability、isShiny" }
        val rawName = item.getString("name").trim()
        val canonicalName = catalog.canonicalName(rawName) ?: error("未知的宝可梦官方形态名称：$rawName")
        val probability = item.getDouble("probability")
        require(probability.isFinite() && probability in 0.0..100.0) { "识别概率超出范围" }
        require(item.get("isShiny") is Boolean) { "isShiny 必须是布尔值" }
        return RecognitionCandidate(canonicalName, (probability / 100.0).toFloat(), item.getBoolean("isShiny"))
    }
}
