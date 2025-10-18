package com.safeguardme.app.data.source

import com.safeguardme.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Lightweight OpenAI client tailored for SafeguardMe's AI assistant use case.
 */
class OpenAIService(
    private val httpClient: OkHttpClient,
    private val json: Json
) {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun createChatCompletion(request: ChatCompletionRequest): Result<ChatCompletionResponse> {
        if (BuildConfig.OPENAI_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("OpenAI API key not configured"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val payload = json.encodeToString(request)
                val httpRequest = Request.Builder()
                    .url(buildChatCompletionsUrl())
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(mediaType))
                    .build()

                httpClient.newCall(httpRequest).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val errorMessage = parseError(body)
                        return@use Result.failure(IOException(errorMessage))
                    }

                    return@use runCatching {
                        json.decodeFromString<ChatCompletionResponse>(body)
                    }
                }
            } catch (io: IOException) {
                Result.failure(io)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun isConfigured(): Boolean = BuildConfig.OPENAI_API_KEY.isNotBlank()

    private fun buildChatCompletionsUrl(): String {
        val base = BuildConfig.OPENAI_BASE_URL.trimEnd('/')
        return "$base/chat/completions"
    }

    private fun parseError(body: String): String {
        if (body.isBlank()) {
            return "OpenAI request failed"
        }

        return runCatching {
            val error = json.decodeFromString<OpenAIErrorResponse>(body)
            error.error?.message?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: body
    }
}

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<OpenAIChatMessage>,
    val temperature: Double = 0.2,
    @SerialName("max_tokens") val maxTokens: Int = 512,
    @SerialName("response_format") val responseFormat: ResponseFormat = ResponseFormat()
)

@Serializable
data class OpenAIChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ResponseFormat(
    val type: String = "text"
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatCompletionChoice> = emptyList()
) {
    fun primaryText(): String {
        val messageContent = choices.firstOrNull()?.message?.content ?: return ""
        return extractText(messageContent).trim()
    }

    private fun extractText(element: JsonElement): String = when (element) {
        is JsonPrimitive -> element.content
        is JsonArray -> element.joinToString(separator = "\n") { extractText(it) }
        is JsonObject -> {
            element["text"]?.let { extractText(it) }
                ?: element["content"]?.let { extractText(it) }
                ?: element.values.joinToString(separator = "\n") { extractText(it) }
        }
        else -> element.toString()
    }
}

@Serializable
data class ChatCompletionChoice(
    val index: Int = 0,
    val message: ChatCompletionMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatCompletionMessage(
    val role: String,
    val content: JsonElement
)

@Serializable
data class OpenAIErrorResponse(
    val error: OpenAIErrorDetail? = null
)

@Serializable
data class OpenAIErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
