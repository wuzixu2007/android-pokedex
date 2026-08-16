package com.example.pokedex.data.scanner

import com.example.pokedex.domain.scanner.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.TimeUnit

enum class ArkErrorKind { Authentication, NotFound, RateLimited, Timeout, Network, UnsupportedVision, InvalidResponse, Configuration, Server }

class ArkApiException(
    val kind: ArkErrorKind,
    override val message: String,
    val statusCode: Int? = null,
    val requestId: String? = null,
    val rawResponse: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

data class ArkRecognitionResponse(
    val candidate: RecognitionCandidate,
    val elapsedMillis: Long,
    val requestId: String?,
    val rawResponse: String,
)

class VolcArkRecognitionClient(
    private val catalog: PokemonCatalog,
    private val clientFactory: (Int) -> OkHttpClient = ::defaultHttpClient,
) {
    @Volatile private var activeCall: Call? = null

    suspend fun recognize(
        image: NormalizedImage,
        user: UserAiSettings,
        developer: DeveloperAiSettings,
    ): ArkRecognitionResponse = withContext(Dispatchers.IO) {
        val safeUser = user.sanitized()
        val safeDeveloper = developer.sanitized()
        validateConfiguration(safeUser, safeDeveloper)
        val request = buildRequest(image.jpeg, safeUser, safeDeveloper)
        val started = System.nanoTime()
        val call = clientFactory(safeUser.timeoutSeconds).newCall(request)
        activeCall = call
        try {
            call.execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val requestId = response.header("request-id") ?: response.header("x-request-id") ?: response.header("x-tt-logid")
                if (!response.isSuccessful) throw httpError(response.code, raw, requestId)
                if (raw.isBlank()) throw ArkApiException(ArkErrorKind.InvalidResponse, "云端 AI 返回了空响应", requestId = requestId, rawResponse = raw)
                val text = runCatching { extractText(JSONObject(raw), safeDeveloper.protocol) }
                    .getOrElse { throw ArkApiException(ArkErrorKind.InvalidResponse, "无法读取云端 AI 返回内容：${it.message}", requestId = requestId, rawResponse = raw, cause = it) }
                val candidate = runCatching { PokemonCandidateProtocol.parse(text, catalog) }
                    .getOrElse { throw ArkApiException(ArkErrorKind.InvalidResponse, "AI 返回格式不符合识别协议：${it.message}", requestId = requestId, rawResponse = raw, cause = it) }
                ArkRecognitionResponse(candidate, (System.nanoTime() - started) / 1_000_000L, requestId, raw)
            }
        } catch (error: ArkApiException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw ArkApiException(ArkErrorKind.Timeout, "云端 AI 请求超时，请检查网络或调高超时时间", cause = error)
        } catch (error: IOException) {
            if (call.isCanceled()) throw error
            throw ArkApiException(ArkErrorKind.Network, "无法连接云端 AI：${error.message ?: "网络错误"}", cause = error)
        } finally {
            activeCall = null
        }
    }

    fun cancel() = activeCall?.cancel()

    internal fun buildRequest(
        jpeg: ByteArray,
        user: UserAiSettings,
        developer: DeveloperAiSettings,
    ): Request {
        val encodedImage = Base64.getEncoder().encodeToString(jpeg)
        val dataUrl = "data:image/jpeg;base64,$encodedImage"
        val body = when (developer.protocol) {
            CloudApiProtocol.Responses -> responsesBody(user, developer, dataUrl)
            CloudApiProtocol.ChatCompletions -> chatBody(user, developer, dataUrl)
            CloudApiProtocol.AnthropicMessages -> anthropicBody(user, developer, encodedImage)
        }
        mergeJson(body, JSONObject(developer.extraBodyJson))
        return Request.Builder()
            .url(developer.apiUrl)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .apply {
                val auth = listOf(developer.authScheme, user.apiKey)
                    .map(::sanitizeAuthPart)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                if (auth.isNotBlank()) header(developer.authHeader.trim(), auth)
                val headers = JSONObject(developer.extraHeadersJson)
                headers.keys().forEach { key -> header(key, headers.getString(key)) }
            }
            .build()
    }

    private fun responsesBody(user: UserAiSettings, developer: DeveloperAiSettings, image: String) = JSONObject().apply {
        put("model", user.model)
        put("max_output_tokens", developer.maxTokens)
        put("temperature", developer.temperature)
        if (user.provider == CloudAiProvider.VolcArk) put("thinking", thinkingJson(developer.thinking))
        put("input", JSONArray().put(JSONObject()
            .put("type", "message")
            .put("role", "user")
            .put("content", JSONArray()
                .put(JSONObject().put("type", "input_text").put("text", prompt(developer.systemPrompt, catalog)))
                .put(JSONObject().put("type", "input_image").put("image_url", image)))))
    }

    private fun chatBody(user: UserAiSettings, developer: DeveloperAiSettings, image: String) = JSONObject().apply {
        put("model", user.model)
        put("max_tokens", developer.maxTokens)
        put("temperature", developer.temperature)
        if (user.provider == CloudAiProvider.VolcArk) put("thinking", thinkingJson(developer.thinking))
        put("messages", JSONArray().put(JSONObject()
            .put("role", "user")
            .put("content", JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt(developer.systemPrompt, catalog)))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", image))))))
    }

    private fun anthropicBody(user: UserAiSettings, developer: DeveloperAiSettings, imageBase64: String) = JSONObject().apply {
        put("model", user.model)
        put("max_tokens", developer.maxTokens)
        put("temperature", developer.temperature)
        put("system", developer.systemPrompt)
        val content = JSONArray()
            .put(JSONObject().put("type", "image").put("source", JSONObject()
                .put("type", "base64").put("media_type", "image/jpeg").put("data", imageBase64)))
            .put(JSONObject().put("type", "text").put("text", prompt(developer.systemPrompt, catalog)))
        put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        internal fun prompt(base: String, catalog: PokemonCatalog): String = buildString {
            append(base)
            append("\n不可覆盖的单目标规则：无论图片中出现多少只宝可梦，都只能识别并返回一只。")
            append("优先选择画面占比最大、最靠近中心且最清晰的主体；无法区分时只选择概率最高的一只。")
            append("name 必须是与宝可梦相关的名称，禁止返回物品、人物、动物或其他无关对象名称。")
            append("如果 100% 确定图片不是宝可梦，name 必须返回百变怪；如果不能确定，仍须选择最可能的宝可梦，不得使用百变怪代替不确定判断。")
            append("地区形态可写成地区名称加宝可梦名称，或宝可梦名称加括号地区说明；超级进化使用超级加名称；超极巨化可写成名称-超极巨化或超极巨化加名称。")
            append("允许使用括号、姿态、颜色、普通形态等自然语言表达，客户端会将其映射到标准图鉴名称。")
            append("必须根据图片判断该宝可梦是否为异色个体：确认是异色时 isShiny 为 true，否则为 false。")
            append("禁止返回 JSON 数组、多个 JSON 对象、多个名称或并列候选。")
            append("不可覆盖的输出协议：只输出一个 JSON 对象，字段必须且只能是 name、probability、isShiny。")
            append("probability 是 0 到 100 的数字，isShiny 是布尔值。")
        }

        internal fun extractText(json: JSONObject, protocol: CloudApiProtocol): String = when (protocol) {
            CloudApiProtocol.ChatCompletions -> json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            CloudApiProtocol.AnthropicMessages -> json.getJSONArray("content").let { content ->
                buildString {
                    repeat(content.length()) { index ->
                        val part = content.getJSONObject(index)
                        if (part.optString("type") == "text") append(part.optString("text"))
                    }
                }.ifBlank { error("响应中没有文本内容") }
            }
            CloudApiProtocol.Responses -> json.optString("output_text").ifBlank {
                val output = json.getJSONArray("output")
                buildString {
                    repeat(output.length()) { outputIndex ->
                        val item = output.getJSONObject(outputIndex)
                        val content = item.optJSONArray("content") ?: return@repeat
                        repeat(content.length()) { contentIndex ->
                            val part = content.getJSONObject(contentIndex)
                            if (part.optString("type") == "output_text") append(part.optString("text"))
                        }
                    }
                }.ifBlank { error("响应中没有 output_text") }
            }
        }

        private fun validateConfiguration(user: UserAiSettings, developer: DeveloperAiSettings) {
            if (user.apiKey.isBlank()) throw ArkApiException(ArkErrorKind.Configuration, "请在右上角设置内的 AI 设置中填写 APIKey")
            if (user.model.isBlank()) throw ArkApiException(ArkErrorKind.Configuration, "模型 ID 不能为空")
            val authParts = listOf(developer.authScheme, user.apiKey)
            if (authParts.any { part -> part.trim().trim('\"', '\'').any { it.code !in 0x21..0x7e && !it.isWhitespace() } }) {
                throw ArkApiException(ArkErrorKind.Configuration, "Authorization/API Key 只能包含 ASCII 字符，请检查是否粘贴了中文说明或不可见字符")
            }
            developer.validate()?.let { throw ArkApiException(ArkErrorKind.Configuration, it) }
        }

        private fun sanitizeAuthPart(value: String): String {
            val normalized = value.trim().trim('"', '\'').replace(Regex("\\s+"), " ")
            require(normalized.all { it.code in 0x21..0x7e }) {
                "Authorization/API Key 只能包含 ASCII 字符，请检查是否粘贴了中文说明或不可见字符"
            }
            return normalized
        }

        private fun httpError(code: Int, raw: String, requestId: String?): ArkApiException {
            val detail = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                ?.takeIf(String::isNotBlank) ?: raw.take(300).ifBlank { "服务未返回错误详情" }
            val kind = when (code) {
                401, 403 -> ArkErrorKind.Authentication
                404 -> ArkErrorKind.NotFound
                429 -> ArkErrorKind.RateLimited
                in 500..599 -> ArkErrorKind.Server
                else -> if (detail.contains("image", true) || detail.contains("vision", true)) ArkErrorKind.UnsupportedVision else ArkErrorKind.Server
            }
            val message = when (kind) {
                ArkErrorKind.Authentication -> "API Key 无效或没有模型访问权限：$detail"
                ArkErrorKind.NotFound -> "请求地址或模型 ID 不存在，请检查开发者设置和模型权限：$detail"
                ArkErrorKind.RateLimited -> "请求过于频繁或账户额度不足：$detail"
                ArkErrorKind.UnsupportedVision -> "当前模型不支持图片输入：$detail"
                else -> "云端 AI 请求失败 (HTTP $code)：$detail"
            }
            return ArkApiException(kind, message, code, requestId, raw)
        }

        private fun thinkingJson(mode: ThinkingMode) = JSONObject().put("type", when (mode) {
            ThinkingMode.Auto -> "auto"
            ThinkingMode.Enabled -> "enabled"
            ThinkingMode.Disabled -> "disabled"
        })

        private fun mergeJson(target: JSONObject, source: JSONObject) {
            source.keys().forEach { key -> target.put(key, source.get(key)) }
        }

        private fun defaultHttpClient(timeoutSeconds: Int) = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
    }
}

class CloudRecognitionRepository(
    private val settings: () -> ScannerSettings,
    private val client: VolcArkRecognitionClient,
    private val history: AiResponseHistoryStore? = null,
) : RecognitionRepository {
    override suspend fun recognize(image: NormalizedImage): RecognitionResult {
        val config = settings().sanitized()
        return runRecognition(image, config, "recognition")
    }

    override suspend fun testConnection(image: NormalizedImage): ConnectionTestResult {
        val config = settings().sanitized()
        return try {
            val response = client.recognize(image, config.userAi, config.developerAi)
            history?.append(AiResponseHistoryStore.newEntry("connection_test", config.userAi, config.developerAi, response.elapsedMillis, 200, response.requestId, response.candidate.standardName, null, response.rawResponse), config.aiHistoryLimit)
            ConnectionTestResult.Success(config.userAi.model, response.candidate, response.elapsedMillis, response.requestId)
        } catch (error: ArkApiException) {
            history?.append(AiResponseHistoryStore.newEntry("connection_test", config.userAi, config.developerAi, null, error.statusCode, error.requestId, null, error.message, error.rawResponse), config.aiHistoryLimit)
            ConnectionTestResult.Failure(error.kind, error.message, error.requestId)
        }
    }

    private suspend fun runRecognition(image: NormalizedImage, config: ScannerSettings, source: String): RecognitionResult = try {
        val response = client.recognize(image, config.userAi, config.developerAi)
        history?.append(AiResponseHistoryStore.newEntry(source, config.userAi, config.developerAi, response.elapsedMillis, 200, response.requestId, response.candidate.standardName, null, response.rawResponse), config.aiHistoryLimit)
        RecognitionResult.Success(response.candidate)
    } catch (error: ArkApiException) {
        history?.append(AiResponseHistoryStore.newEntry(source, config.userAi, config.developerAi, null, error.statusCode, error.requestId, null, error.message, error.rawResponse), config.aiHistoryLimit)
        RecognitionResult.Failure(error.message)
    }

    override fun cancel() { client.cancel() }
    override fun close() { cancel() }
}
