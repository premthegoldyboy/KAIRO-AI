package com.kairo.ai

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
class OpenAICompatibleEngine(private val config: AIProviderConfig) : AIEngine {
    override suspend fun reply(message: String, memoryContext: String): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(config.baseUrl.startsWith("https://")) { "Provider URL must use HTTPS." }
            require(config.model.isNotBlank()) { "Enter a model name in KAIRO settings." }
            require(config.apiKey.isNotBlank()) { "Add your provider API key in KAIRO settings." }
            val systemPrompt = KairoPersonality.systemInstruction(memoryContext)
            val isAnthropic = config.protocol.equals("anthropic", ignoreCase = true)
            val endpoint = if (isAnthropic) config.baseUrl.trimEnd('/') + "/v1/messages"
                else config.baseUrl.trimEnd('/') + "/chat/completions"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6_000
                readTimeout = 18_000
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
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
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
                    }.joinToString("\\n")
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
            } catch (error: Exception) {
                val reason = error.message?.take(120) ?: "Unknown provider error"
                failures += "${provider.model}: $reason"
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
