package com.kairo.ai

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AIProviderConfig(val baseUrl: String, val model: String, val apiKey: String)

interface AIEngine {
    suspend fun reply(message: String, memoryContext: String = ""): String
}

class DemoAIEngine : AIEngine {
    override suspend fun reply(message: String, memoryContext: String): String {
        val text = message.trim()
        if (text.isBlank()) return "I'm listening. What would you like to do?"
        return when {
            text.contains("hello", true) || text.contains("hi", true) ->
                "Hello! KAIRO systems are online. How can I help?"
            text.contains("namaste", true) -> "Namaste! Ela unnaru? I'm ready to help."
            else -> "I received: “$text”. Demo mode is active. Add a compatible AI provider in Settings to enable live AI."
        }
    }
}

/** Adapter for services implementing the OpenAI Chat Completions-compatible API. */
class OpenAICompatibleEngine(private val config: AIProviderConfig) : AIEngine {
    override suspend fun reply(message: String, memoryContext: String): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(config.baseUrl.startsWith("https://")) { "Provider URL must use HTTPS." }
            require(config.model.isNotBlank()) { "Enter a model name in KAIRO settings." }
            require(config.apiKey.isNotBlank()) { "Add your provider API key in KAIRO settings." }
            val connection = (URL(config.baseUrl.trimEnd('/') + "/chat/completions")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            try {
                val payload = JSONObject()
                    .put("model", config.model)
                    .put("messages", org.json.JSONArray().put(
                        JSONObject().put("role", "system").put("content", "You are KAIRO, a helpful Android assistant. Use saved memories only when relevant.\n" + memoryContext),
                    JSONObject().put("role", "user").put("content", message)
                    ))
                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val detail = runCatching {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    throw IllegalStateException(
                        detail?.takeIf { it.isNotBlank() } ?: "Provider request failed (HTTP $status)."
                    )
                }
                JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content")?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "The AI provider returned an empty response."
            } finally {
                connection.disconnect()
            }
        }
}
