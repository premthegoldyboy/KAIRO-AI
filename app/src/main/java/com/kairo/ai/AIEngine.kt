package com.kairo.ai

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AIProviderConfig(val baseUrl: String, val model: String, val apiKey: String, val protocol: String = "openai")

internal fun validateProviderRequestSize(message: String, memoryContext: String) {
    require(message.length <= 32_000) { "Your message is too long. Please shorten it and try again." }
    require(memoryContext.length <= 16_000) { "The conversation context is too large. Start a new chat or clear some history." }
}

interface AIEngine {
    suspend fun reply(message: String, memoryContext: String = ""): String
}

/**
 * Provider adapter. DeepSeek and xAI use Chat Completions; Anthropic uses its
 * native Messages API so Claude does not depend on the compatibility layer.
 */
class OpenAICompatibleEngine(private val config: AIProviderConfig) : AIEngine {
    private companion object { const val MAX_RESPONSE_BYTES = 1_048_576 }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "Provider response was too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    override suspend fun reply(message: String, memoryContext: String): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            validateProviderRequestSize(message, memoryContext)
            require(config.baseUrl.startsWith("https://", ignoreCase = true)) { "Provider URL must use HTTPS." }
            require(config.model.isNotBlank()) { "Enter a model name in KAIRO settings." }
            require(config.apiKey.isNotBlank()) { "Add your provider API key in KAIRO settings." }
            val systemPrompt = KairoPersonality.systemInstruction(memoryContext)
            val isAnthropic = config.protocol.equals("anthropic", ignoreCase = true)
            val endpoint = ProviderEndpoints.chatCompletions(config.baseUrl, isAnthropic)
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 60_000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (isAnthropic) {
                    setRequestProperty("x-api-key", config.apiKey)
                    setRequestProperty("anthropic-version", "2023-06-01")
                } else setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            try {
                val payload = if (isAnthropic) {
                    JSONObject().put("model", config.model).put("max_tokens", 2048)
                        .put("system", systemPrompt)
                        .put("messages", org.json.JSONArray().put(
                            JSONObject().put("role", "user").put("content", message)))
                } else {
                    JSONObject().put("model", config.model)
                        .put("messages", org.json.JSONArray()
                            .put(JSONObject().put("role", "system").put("content", systemPrompt))
                            .put(JSONObject().put("role", "user").put("content", message)))
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.use { input ->
                    readBounded(input, MAX_RESPONSE_BYTES).toString(Charsets.UTF_8)
                }.orEmpty()
                if (status !in 200..299) {
                    val detail = runCatching {
                        val json = JSONObject(body)
                        json.optJSONObject("error")?.optString("message") ?: json.optString("message")
                    }.getOrNull()
                    val reason = when (status) {
                        401, 403 -> "API key rejected"
                        402 -> "provider balance or credit is insufficient"
                        404 -> "model or endpoint not found"
                        408 -> "request timed out"
                        429 -> "rate limit or quota reached"
                        in 500..599 -> "provider service error"
                        else -> "request failed"
                    }
                    throw ProviderException(
                        "HTTP $status ($reason)" +
                            (detail?.takeIf { it.isNotBlank() }?.let { ": ${it.take(160)}" } ?: ""),
                        status
                    )
                }
                val answer = if (isAnthropic) {
                    val blocks = JSONObject(body).optJSONArray("content")
                    (0 until (blocks?.length() ?: 0)).mapNotNull { index ->
                        blocks?.optJSONObject(index)?.takeIf { it.optString("type") == "text" }
                            ?.optString("text")?.takeIf { it.isNotBlank() }
                    }.joinToString("\n")
                } else JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content").orEmpty()
                answer.trim().takeIf { it.isNotBlank() }
                    ?: throw ProviderException("Provider returned an empty response.")
            } finally { connection.disconnect() }
        }
}

/**
 * Tries configured providers in order. Quota, rate-limit, timeout and service errors
 * move to the next provider. No provider is treated as unlimited.
 */
class FailoverAIEngine(private val providers: List<AIProviderConfig>) : AIEngine {
    override suspend fun reply(message: String, memoryContext: String): String {
        if (providers.isEmpty()) throw IllegalStateException("No cloud AI providers are configured.")
        val failures = mutableListOf<String>()
        val routedProviders = ProviderRouter.order(providers)
        for (provider in routedProviders) {
            try {
                val answer = OpenAICompatibleEngine(provider).reply(message, memoryContext)
                ProviderRouter.recordSuccess(provider)
                return answer
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                ProviderRouter.recordFailure(provider, error)
                val reason = error.message?.take(160) ?: "Unknown provider error"
                failures += "${ProviderEndpoints.providerLabel(provider.baseUrl)} (${provider.model}): $reason"
            }
        }
        throw IllegalStateException(
            "All ${providers.size} configured AI provider(s) are unavailable. " +
                failures.joinToString(" | ")
        )
    }
}
