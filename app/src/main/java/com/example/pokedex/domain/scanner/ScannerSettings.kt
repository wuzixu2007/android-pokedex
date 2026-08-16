package com.example.pokedex.domain.scanner

import com.example.pokedex.data.scanner.NarrationSettings
import com.example.pokedex.data.scanner.SoundEffectSettings

enum class CloudApiProtocol { Responses, ChatCompletions, AnthropicMessages }
enum class ThinkingMode { Auto, Enabled, Disabled }
enum class CloudAiProvider(val displayName: String) {
    VolcArk("火山方舟"), OpenAI("OpenAI"), Alibaba("阿里百炼"), Gemini("Google Gemini"),
    Anthropic("Anthropic Claude"), Custom("自定义接口"),
}

data class UserAiSettings(
    val apiKey: String = "",
    val model: String = DEFAULT_ARK_MODEL,
    val timeoutSeconds: Int = 60,
    val provider: CloudAiProvider = CloudAiProvider.VolcArk,
) {
    fun sanitized() = copy(
        apiKey = apiKey.trim(),
        model = model.trim().ifBlank { DEFAULT_ARK_MODEL },
        timeoutSeconds = timeoutSeconds.coerceIn(15, 180),
    )
}

data class DeveloperAiSettings(
    val protocol: CloudApiProtocol = CloudApiProtocol.Responses,
    val apiUrl: String = DEFAULT_ARK_RESPONSES_URL,
    val authHeader: String = "Authorization",
    val authScheme: String = "Bearer",
    val temperature: Float = 0.1f,
    val maxTokens: Int = 256,
    val thinking: ThinkingMode = ThinkingMode.Disabled,
    val systemPrompt: String = DEFAULT_RECOGNITION_PROMPT,
    val extraHeadersJson: String = "{}",
    val extraBodyJson: String = "{}",
) {
    fun sanitized() = copy(
        apiUrl = apiUrl.trim().ifBlank { defaultUrl(protocol) },
        authHeader = authHeader.trim().ifBlank { "Authorization" },
        authScheme = authScheme.trim(),
        temperature = temperature.takeIf(Float::isFinite)?.coerceIn(0f, 2f) ?: 0.1f,
        maxTokens = maxTokens.coerceIn(32, 4096),
        systemPrompt = systemPrompt.trim().ifBlank { DEFAULT_RECOGNITION_PROMPT },
        extraHeadersJson = extraHeadersJson.trim().ifBlank { "{}" },
        extraBodyJson = extraBodyJson.trim().ifBlank { "{}" },
    )

    fun validate(): String? = runCatching {
        require(apiUrl.startsWith("https://") || apiUrl.startsWith("http://")) { "API 地址必须以 http:// 或 https:// 开头" }
        org.json.JSONObject(extraHeadersJson)
        org.json.JSONObject(extraBodyJson)
    }.exceptionOrNull()?.message

    companion object {
        fun defaultUrl(protocol: CloudApiProtocol) = when (protocol) {
            CloudApiProtocol.Responses -> DEFAULT_ARK_RESPONSES_URL
            CloudApiProtocol.ChatCompletions -> DEFAULT_ARK_CHAT_URL
            CloudApiProtocol.AnthropicMessages -> ANTHROPIC_MESSAGES_URL
        }
    }
}

fun ScannerSettings.withProvider(provider: CloudAiProvider): ScannerSettings {
    if (provider == CloudAiProvider.Custom) return copy(userAi = userAi.copy(provider = provider))
    val preset = when (provider) {
        CloudAiProvider.VolcArk -> Triple(DEFAULT_ARK_MODEL, CloudApiProtocol.Responses, DEFAULT_ARK_RESPONSES_URL)
        CloudAiProvider.OpenAI -> Triple("gpt-4.1-mini", CloudApiProtocol.Responses, OPENAI_RESPONSES_URL)
        CloudAiProvider.Alibaba -> Triple("qwen-vl-plus", CloudApiProtocol.ChatCompletions, ALIBABA_CHAT_URL)
        CloudAiProvider.Gemini -> Triple("gemini-2.5-flash", CloudApiProtocol.ChatCompletions, GEMINI_CHAT_URL)
        CloudAiProvider.Anthropic -> Triple("claude-sonnet-4-20250514", CloudApiProtocol.AnthropicMessages, ANTHROPIC_MESSAGES_URL)
        CloudAiProvider.Custom -> error("Handled above")
    }
    val anthropic = provider == CloudAiProvider.Anthropic
    return copy(
        userAi = userAi.copy(provider = provider, model = preset.first),
        developerAi = developerAi.copy(
            protocol = preset.second,
            apiUrl = preset.third,
            authHeader = if (anthropic) "x-api-key" else "Authorization",
            authScheme = if (anthropic) "" else "Bearer",
            extraHeadersJson = if (anthropic) "{\"anthropic-version\":\"2023-06-01\"}" else "{}",
            extraBodyJson = "{}",
        ),
    )
}

data class ImageProcessingSettings(
    val maxEdge: Int = 1024,
    val jpegQuality: Int = 90,
) {
    fun sanitized() = copy(
        maxEdge = maxEdge.coerceIn(448, 2048),
        jpegQuality = jpegQuality.coerceIn(70, 100),
    )
}

data class ScannerSettings(
    val userAi: UserAiSettings = UserAiSettings(),
    val developerAi: DeveloperAiSettings = DeveloperAiSettings(),
    val imageProcessing: ImageProcessingSettings = ImageProcessingSettings(),
    val narrationSettings: NarrationSettings = NarrationSettings(),
    val soundEffectSettings: SoundEffectSettings = SoundEffectSettings(),
    val aiHistoryLimit: Int = 5,
) {
    fun sanitized() = copy(
        userAi = userAi.sanitized(),
        developerAi = developerAi.sanitized(),
        imageProcessing = imageProcessing.sanitized(),
        narrationSettings = narrationSettings.sanitized(),
        soundEffectSettings = soundEffectSettings.sanitized(),
        aiHistoryLimit = aiHistoryLimit.coerceIn(2, 20),
    )
}

const val DEFAULT_ARK_MODEL = "doubao-seed-2-0-mini-260428"
const val DEFAULT_ARK_RESPONSES_URL = "https://ark.cn-beijing.volces.com/api/v3/responses"
const val DEFAULT_ARK_CHAT_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
const val OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses"
const val ALIBABA_CHAT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
const val GEMINI_CHAT_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
const val ANTHROPIC_MESSAGES_URL = "https://api.anthropic.com/v1/messages"
const val DEFAULT_RECOGNITION_PROMPT =
    "你是宝可梦图像识别器。只识别图片中唯一的主要宝可梦及其准确形态，并判断是否为异色个体。" +
        "如果画面中出现两只或更多宝可梦，只选择画面占比最大、最靠近中心且最清晰的一只；" +
        "若仍无法区分，只选择你判断概率最高的一只，绝对不要同时返回多只宝可梦。" +
        "禁止输出解释、Markdown、代码块或额外字段。"
