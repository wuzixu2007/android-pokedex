package com.example.pokedex.data.scanner

import com.example.pokedex.domain.scanner.*

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class VolcArkRecognitionClientTest {
    private lateinit var server: MockWebServer
    private lateinit var catalog: PokemonCatalog

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        catalog = PokemonCatalog.fromJson(
            JSONObject().put("records", JSONArray()
                .put(recordJson("0001:一般", "皮卡丘"))
                .put(recordJson("0019:阿罗拉", "阿罗拉小拉达"))
                .put(recordJson("0199:伽勒尔", "伽勒尔呆呆王"))
                .put(recordJson("0003:超极巨化", "妙蛙花-超极巨化"))
                .put(recordJson("0006:超级", "超级喷火龙Ｘ"))
                .put(recordJson("0132:一般", "百变怪")))
                .put("aliases", JSONObject().put("小拉达(阿罗拉的样子)", "阿罗拉小拉达"))
                .toString(),
        )
    }

    @After fun tearDown() = server.shutdown()

    @Test fun responsesRequest_containsAuthenticationImageModelAndStrictProtocol() = runBlocking {
        server.enqueue(successResponse("{\"name\":\"皮卡丘\",\"probability\":99,\"isShiny\":false}"))
        val result = client().recognize(NormalizedImage(byteArrayOf(1, 2, 3)), user(), developer())
        assertEquals("皮卡丘", result.candidate.standardName)
        assertFalse(result.candidate.isShiny)
        val request = server.takeRequest()
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        val json = JSONObject(request.body.readUtf8())
        assertEquals(DEFAULT_ARK_MODEL, json.getString("model"))
        assertTrue(json.toString().contains("data:image/jpeg;base64,AQID"))
        assertTrue(json.toString().contains("字段必须且只能是"))
        assertTrue(json.toString().contains("禁止返回 JSON 数组"))
        assertTrue(json.toString().contains("100% 确定图片不是宝可梦"))
        assertTrue(json.toString().contains("地区形态"))
        assertTrue(json.toString().contains("是否为异色个体"))
    }

    @Test fun parser_readsNestedOutputFenceAliasAndShiny() = runBlocking {
        server.enqueue(successResponse("```json\n{\"name\":\"小拉达(阿罗拉的样子)\",\"probability\":88,\"isShiny\":true}\n```"))
        val result = client().recognize(NormalizedImage(byteArrayOf(1)), user(), developer())
        assertEquals("阿罗拉小拉达", result.candidate.standardName)
        assertEquals(0.88f, result.candidate.probability)
        assertTrue(result.candidate.isShiny)
    }

    @Test fun prompt_containsRulesWithoutFullCatalog() {
        val prompt = VolcArkRecognitionClient.prompt(DEFAULT_RECOGNITION_PROMPT, catalog)
        assertTrue(prompt.contains("只输出一个 JSON 对象"))
        assertTrue(prompt.contains("无论图片中出现多少只宝可梦，都只能识别并返回一只"))
        assertTrue(prompt.contains("只选择概率最高的一只"))
        assertTrue(prompt.contains("百变怪"))
        assertTrue(prompt.contains("isShiny 为 true"))
        assertFalse(prompt.contains("阿罗拉小拉达"))
    }

    @Test fun parser_mapsNaturalLanguageFormNames() = runBlocking {
        val cases = listOf(
            "呆呆王（伽勒尔地区）" to "伽勒尔呆呆王",
            "超极巨化妙蛙花" to "妙蛙花-超极巨化",
            "超级喷火龙X" to "超级喷火龙Ｘ",
        )
        cases.forEachIndexed { index, (rawName, expected) ->
            server.enqueue(successResponse("{\"name\":\"$rawName\",\"probability\":99,\"isShiny\":false}"))
            val result = client().recognize(NormalizedImage(byteArrayOf(index.toByte())), user(), developer())
            assertEquals(expected, result.candidate.standardName)
        }
    }

    @Test fun parser_acceptsDittoFallback() = runBlocking {
        server.enqueue(successResponse("{\"name\":\"百变怪\",\"probability\":100,\"isShiny\":false}"))
        assertEquals("百变怪", client().recognize(NormalizedImage(byteArrayOf(1)), user(), developer()).candidate.standardName)
    }

    @Test fun providerPresets_keepKeyAndConfigureVisionEndpoints() {
        val original = ScannerSettings(userAi = UserAiSettings(apiKey = "test-key"))
        val gemini = original.withProvider(CloudAiProvider.Gemini)
        assertEquals("test-key", gemini.userAi.apiKey)
        assertEquals("gemini-2.5-flash", gemini.userAi.model)
        assertEquals(CloudApiProtocol.ChatCompletions, gemini.developerAi.protocol)
        assertEquals(GEMINI_CHAT_URL, gemini.developerAi.apiUrl)

        val claude = original.withProvider(CloudAiProvider.Anthropic)
        assertEquals(CloudApiProtocol.AnthropicMessages, claude.developerAi.protocol)
        assertEquals("x-api-key", claude.developerAi.authHeader)
        assertTrue(claude.developerAi.extraHeadersJson.contains("anthropic-version"))
    }

    @Test fun anthropicRequest_usesNativeVisionMessageAndParsesText() {
        val developer = DeveloperAiSettings(
            protocol = CloudApiProtocol.AnthropicMessages,
            apiUrl = server.url("/v1/messages").toString(),
            authHeader = "x-api-key",
            authScheme = "",
            extraHeadersJson = "{\"anthropic-version\":\"2023-06-01\"}",
        )
        val request = client().buildRequest(byteArrayOf(1, 2, 3), UserAiSettings("test-key", provider = CloudAiProvider.Anthropic), developer)
        assertEquals("test-key", request.header("x-api-key"))
        assertEquals("2023-06-01", request.header("anthropic-version"))
        val buffer = Buffer().also { request.body!!.writeTo(it) }
        val json = JSONObject(buffer.readUtf8())
        assertEquals("base64", json.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0).getJSONObject("source").getString("type"))
        val response = JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "ok")))
        assertEquals("ok", VolcArkRecognitionClient.extractText(response, CloudApiProtocol.AnthropicMessages))
    }

    @Test fun invalidExtraField_isRejected() = runBlocking {
        server.enqueue(successResponse("{\"name\":\"皮卡丘\",\"probability\":99,\"isShiny\":false,\"extra\":1}"))
        val error = runCatching { client().recognize(NormalizedImage(byteArrayOf(1)), user(), developer()) }.exceptionOrNull() as ArkApiException
        assertEquals(ArkErrorKind.InvalidResponse, error.kind)
    }

    @Test fun httpErrors_areMappedToActionableKinds() = runBlocking {
        listOf(401 to ArkErrorKind.Authentication, 403 to ArkErrorKind.Authentication, 404 to ArkErrorKind.NotFound,
            429 to ArkErrorKind.RateLimited, 500 to ArkErrorKind.Server).forEach { (code, kind) ->
            server.enqueue(MockResponse().setResponseCode(code).setBody("{\"error\":{\"message\":\"test error\"}}"))
            val error = runCatching { client().recognize(NormalizedImage(byteArrayOf(1)), user(), developer()) }.exceptionOrNull() as ArkApiException
            assertEquals(kind, error.kind)
        }
    }

    @Test fun timeout_isReportedSeparately() = runBlocking {
        server.enqueue(MockResponse().setBodyDelay(1, TimeUnit.SECONDS).setBody("{}"))
        val timeoutClient = VolcArkRecognitionClient(catalog) { OkHttpClient.Builder().readTimeout(50, TimeUnit.MILLISECONDS).build() }
        val error = runCatching { timeoutClient.recognize(NormalizedImage(byteArrayOf(1)), user(), developer()) }.exceptionOrNull() as ArkApiException
        assertEquals(ArkErrorKind.Timeout, error.kind)
    }

    private fun client() = VolcArkRecognitionClient(catalog) { OkHttpClient() }
    private fun user() = UserAiSettings(apiKey = "test-key")
    private fun developer() = DeveloperAiSettings(apiUrl = server.url("/responses").toString())
    private fun successResponse(text: String) = MockResponse().setResponseCode(200).setHeader("x-request-id", "request-123")
        .setBody(JSONObject().put("output", JSONArray().put(JSONObject().put("type", "message")
            .put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", text))))).toString())

    private fun recordJson(key: String, name: String) = JSONObject()
        .put("key", key).put("id", key.take(4)).put("sourceFormName", name).put("nameZh", name)
        .put("nameEn", name).put("types", JSONArray().put("电")).put("attributeLabel", "宝可梦")
        .put("category", "宝可梦").put("height", "1m").put("weight", "1kg")
        .put("description", "").put("profile", "").put("imageAsset", "")
}
