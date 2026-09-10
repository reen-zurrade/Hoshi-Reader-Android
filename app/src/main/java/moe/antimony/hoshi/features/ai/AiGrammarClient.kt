package moe.antimony.hoshi.features.ai

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Result of one grammar-analysis request. Failure reasons are mapped to localized copy by the UI layer. */
sealed interface AiGrammarOutcome {
    data class Success(val text: String) : AiGrammarOutcome

    data class Failure(val reason: AiGrammarFailure) : AiGrammarOutcome
}

enum class AiGrammarFailure {
    NotConfigured,
    NoSentence,
    Network,
    Timeout,
    Auth,
    RateLimited,
    Quota,
    InvalidRequest,
    Server,
    EmptyResponse,
}

internal data class AiGrammarHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal fun interface AiGrammarTransport {
    fun post(url: String, apiKey: String, body: String, timeoutMillis: Int): AiGrammarHttpResponse
}

internal class HttpAiGrammarTransport : AiGrammarTransport {
    override fun post(
        url: String,
        apiKey: String,
        body: String,
        timeoutMillis: Int,
    ): AiGrammarHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            AiGrammarHttpResponse(
                statusCode = statusCode,
                body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(),
            )
        } finally {
            connection.disconnect()
        }
    }
}

internal fun interface AiGrammarClient {
    fun analyze(settings: AiGrammarSettings, userPrompt: String): AiGrammarOutcome
}

/**
 * DeepSeek chat-completions client. The wire format is the OpenAI-compatible one DeepSeek serves at
 * `https://api.deepseek.com/chat/completions`; `thinking` is sent explicitly because the model
 * defaults to thinking mode and a popup-sized explanation does not need it.
 */
internal class DeepSeekAiGrammarClient(
    private val transport: AiGrammarTransport = HttpAiGrammarTransport(),
) : AiGrammarClient {
    override fun analyze(settings: AiGrammarSettings, userPrompt: String): AiGrammarOutcome {
        val messages = buildJsonArray {
            add(
                buildJsonObject {
                    put("role", "system")
                    put("content", settings.systemPrompt)
                },
            )
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                },
            )
        }
        val body = buildJsonObject {
            put("model", settings.model)
            put("stream", false)
            put("max_tokens", settings.maxTokens)
            put("messages", messages)
            if (!settings.thinkingEnabled) {
                put("thinking", buildJsonObject { put("type", "disabled") })
            }
        }.toString()

        val response = try {
            transport.post(
                url = settings.chatCompletionsUrl,
                apiKey = settings.apiKey,
                body = body,
                timeoutMillis = settings.timeoutMillis,
            )
        } catch (_: SocketTimeoutException) {
            return AiGrammarOutcome.Failure(AiGrammarFailure.Timeout)
        } catch (_: IOException) {
            return AiGrammarOutcome.Failure(AiGrammarFailure.Network)
        } catch (_: RuntimeException) {
            return AiGrammarOutcome.Failure(AiGrammarFailure.Network)
        }

        if (response.statusCode !in 200..299) {
            return AiGrammarOutcome.Failure(failureForStatus(response.statusCode))
        }

        val content = runCatching {
            json.parseToJsonElement(response.body)
                .jsonObject["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()

        return content
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(AiGrammarOutcome::Success)
            ?: AiGrammarOutcome.Failure(AiGrammarFailure.EmptyResponse)
    }

    private fun failureForStatus(statusCode: Int): AiGrammarFailure = when (statusCode) {
        HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> AiGrammarFailure.Auth
        402 -> AiGrammarFailure.Quota
        429 -> AiGrammarFailure.RateLimited
        HttpURLConnection.HTTP_BAD_REQUEST,
        HttpURLConnection.HTTP_ENTITY_TOO_LARGE,
        422,
        -> AiGrammarFailure.InvalidRequest
        in 500..599 -> AiGrammarFailure.Server
        else -> AiGrammarFailure.Network
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
