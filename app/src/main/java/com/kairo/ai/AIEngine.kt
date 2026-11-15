package com.kairo.ai

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AIProviderConfig(val baseUrl: String, val model: String, val apiKey: String, val protocol: String = "openai")

interface AIEngine {
    suspend fun reply(message: String, memoryContext: String = ""): String
}

/**
 * Provider adapter. DeepSeek and xAI use Chat Completions; Anthropic uses its
 * native Messages API so Claude does not depend on the compatibility layer.
 */
class OpenAICompatibleEngine(private val config: AIProviderConfig) : AIEngine {\n    private companion object { const val MAX_RESPONSE_BYTES = 1_048_576 }\n\n    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {\n        val output = java.io.ByteArrayOutputStream()\n        val buffer = ByteArray(8192)\n        while (true) {\n            val count = input.read(buffer)\n            if (count < 0) break\n            require(output.size() + count <= limit) { "Provider response was too large." }\n            output.write(buffer, 0, count)\n        }\n        return output.toByteArray()\n    }
    override suspend fun reply(message: String, memoryContext: String): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(config.baseUrl.startsWith("https://", ignoreCase = true)) { "Provider URL must use HTTPS." }
            require(config.model.isNotBlank()) { "Enter a model name in KAIRO settings." }
            require(config.apiKey.isNotBlank()) { "Add your provider API key in KAIRO settings." }
            val systemPrompt = KairoPersonality.systemInstruction(memoryContext)
            val isAnthropic = config.protocol.equals("anthropic", ignoreCase = true)
            val endpoint = ProviderEndpoints.chatCompletions(config.baseUrl, isAnthropic)
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
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
                val body = stream?.use { input ->\n                    val bytes = readBounded(input, MAX_RESPONSE_BYTES)\n                    require(bytes.size <= MAX_RESPONSE_BYTES) { "Provider response was too large." }\n                    bytes.toString(Charsets.UTF_8)\n                }.orEmpty()
                if (status !in 200..299) {
                    val detail = runCatching {
                        val json = JSONObject(body)
                        json.optJSONObject("error")?.optString("message") ?: json.optString("message")
                    }.getOrNull()
                    throw IllegalStateException(detail?.takeIf { it.isNotBlank() }
                        ?: "Provider request failed (HTTP $status).")
                }
                val answer = if (isAnthropic) {
                    val blocks = JSONObject(body).optJSONArray("content")
                    (0 until (blocks?.length() ?: 0)).mapNotNull { index ->
                        blocks?.optJSONObject(index)?.takeIf { it.optString("type") == "text" }
                            ?.optString("text")?.takeIf { it.isNotBlank() }
                    }.joinToString("\n")
                } else JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content").orEmpty()
                answer.trim().takeIf { it.isNotBlank() } ?: "The AI provider returned an empty response."
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
        for ((index, provider) in providers.withIndex()) {
            try {
                return OpenAICompatibleEngine(provider).reply(message, memoryContext)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                val reason = error.message?.take(120) ?: "Unknown provider error"
                failures += "${ProviderEndpoints.providerLabel(provider.baseUrl)} (${provider.model}): $reason"
                if (index == providers.lastIndex) {
                    throw IllegalStateException(
                        "All ${providers.size} configured AI provider(s) are unavailable. " +
                            failures.joinToString(" | ")
                    )
                }
            }
        }
        throw IllegalStateException("No AI provider could answer.")
    }
}
