package moe.antimony.hoshi.features.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiGrammarClientTest {
    @Test
    fun sendsChatCompletionsRequestWithThinkingDisabledByDefault() {
        val transport = FakeAiGrammarTransport(ok("解析结果"))
        val client = DeepSeekAiGrammarClient(transport)

        val outcome = client.analyze(AiGrammarSettings(apiKey = "hoshi-secret"), "请解析这个句子。")

        assertEquals(AiGrammarOutcome.Success("解析结果"), outcome)
        assertEquals("https://api.deepseek.com/chat/completions", transport.url)
        assertEquals("hoshi-secret", transport.apiKey)
        assertEquals(60_000, transport.timeoutMillis)

        val body = Json.parseToJsonElement(transport.body.orEmpty()).jsonObject
        assertEquals("deepseek-flash", body.getValue("model").jsonPrimitive.content)
        assertEquals("false", body.getValue("stream").jsonPrimitive.content)
        assertEquals("1024", body.getValue("max_tokens").jsonPrimitive.content)
        assertEquals(
            "disabled",
            body.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content,
        )

        val messages = body.getValue("messages").jsonArray
        assertEquals("system", messages[0].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals(
            DEFAULT_AI_GRAMMAR_SYSTEM_PROMPT,
            messages[0].jsonObject.getValue("content").jsonPrimitive.content,
        )
        assertEquals("user", messages[1].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("请解析这个句子。", messages[1].jsonObject.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun omitsThinkingWhenThinkingModeIsEnabled() {
        val transport = FakeAiGrammarTransport(ok("解析结果"))
        val client = DeepSeekAiGrammarClient(transport)

        client.analyze(AiGrammarSettings(apiKey = "hoshi-secret", thinkingEnabled = true), "请解析这个句子。")

        val body = Json.parseToJsonElement(transport.body.orEmpty()).jsonObject
        assertFalse("thinking" in body)
    }

    @Test
    fun respectsConfiguredEndpointAndModel() {
        val transport = FakeAiGrammarTransport(ok("解析结果"))
        val client = DeepSeekAiGrammarClient(transport)

        client.analyze(
            AiGrammarSettings(
                apiKey = "hoshi-secret",
                baseUrl = "https://gateway.example/anthropic/",
                model = "deepseek-v4.1-flash",
                timeoutSeconds = 90,
            ),
            "请解析这个句子。",
        )

        assertEquals("https://gateway.example/anthropic/chat/completions", transport.url)
        assertEquals(90_000, transport.timeoutMillis)
        val body = Json.parseToJsonElement(transport.body.orEmpty()).jsonObject
        assertEquals("deepseek-v4.1-flash", body.getValue("model").jsonPrimitive.content)
    }

    @Test
    fun mapsHttpFailuresToStableReasons() {
        assertEquals(AiGrammarFailure.Auth, failureFor(401))
        assertEquals(AiGrammarFailure.Auth, failureFor(403))
        assertEquals(AiGrammarFailure.Quota, failureFor(402))
        assertEquals(AiGrammarFailure.RateLimited, failureFor(429))
        assertEquals(AiGrammarFailure.InvalidRequest, failureFor(400))
        assertEquals(AiGrammarFailure.InvalidRequest, failureFor(422))
        assertEquals(AiGrammarFailure.Server, failureFor(500))
        assertEquals(AiGrammarFailure.Server, failureFor(503))
    }

    @Test
    fun reportsEmptyResponseWhenContentIsMissing() {
        val transport = FakeAiGrammarTransport(AiGrammarHttpResponse(200, """{"choices":[]}"""))
        val client = DeepSeekAiGrammarClient(transport)

        assertEquals(
            AiGrammarOutcome.Failure(AiGrammarFailure.EmptyResponse),
            client.analyze(AiGrammarSettings(apiKey = "hoshi-secret"), "请解析这个句子。"),
        )
    }

    @Test
    fun reportsEmptyResponseWhenContentIsBlank() {
        val transport = FakeAiGrammarTransport(ok("   "))
        val client = DeepSeekAiGrammarClient(transport)

        assertEquals(
            AiGrammarOutcome.Failure(AiGrammarFailure.EmptyResponse),
            client.analyze(AiGrammarSettings(apiKey = "hoshi-secret"), "请解析这个句子。"),
        )
    }

    private fun failureFor(statusCode: Int): AiGrammarFailure {
        val transport = FakeAiGrammarTransport(AiGrammarHttpResponse(statusCode, """{"error":{"message":"nope"}}"""))
        val outcome = DeepSeekAiGrammarClient(transport)
            .analyze(AiGrammarSettings(apiKey = "hoshi-secret"), "请解析这个句子。")
        return (outcome as AiGrammarOutcome.Failure).reason
    }

    private fun ok(content: String): AiGrammarHttpResponse = AiGrammarHttpResponse(
        statusCode = 200,
        body = """
            {"choices":[{"message":{"role":"assistant","content":"$content"}}]}
        """.trimIndent(),
    )

    private class FakeAiGrammarTransport(
        private val response: AiGrammarHttpResponse,
    ) : AiGrammarTransport {
        var url: String? = null
        var apiKey: String? = null
        var body: String? = null
        var timeoutMillis: Int? = null

        override fun post(
            url: String,
            apiKey: String,
            body: String,
            timeoutMillis: Int,
        ): AiGrammarHttpResponse {
            this.url = url
            this.apiKey = apiKey
            this.body = body
            this.timeoutMillis = timeoutMillis
            return response
        }
    }
}
