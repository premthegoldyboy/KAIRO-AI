package com.kairo.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ChatTurn(val role: String, val content: String) {
    companion object {
        /** Remove invalid leading assistant turns and merge adjacent turns of the same role. */
        fun normalize(turns: List<ChatTurn>): List<ChatTurn> {
            val result = mutableListOf<ChatTurn>()
            turns.forEach { turn ->
                if (turn.content.isBlank() || turn.role !in setOf("user", "assistant")) return@forEach
                if (result.isEmpty() && turn.role != "user") return@forEach
                val last = result.lastOrNull()
                if (last?.role == turn.role) result[result.lastIndex] =
                    last.copy(content = last.content + "\n\n" + turn.content)
                else result += turn
            }
            return result
        }
    }
}

class ProviderException(message: String, val status: Int = 0) : IOException(message)

interface StreamingAIEngine {
    fun stream(history: List<ChatTurn>, memoryContext: String): Flow<String>
}

/** Server-sent-event adapter for OpenAI-compatible and Anthropic Messages APIs. */
class StreamingProviderEngine(private val config: AIProviderConfig) : StreamingAIEngine {
    override fun stream(history: List<ChatTurn>, memoryContext: String): Flow<String> = flow {
        require(config.baseUrl.startsWith("https://", ignoreCase = true)) { "Provider URL must use HTTPS." }
        require(config.model.isNotBlank()) { "Enter a model name in KAIRO settings." }
        require(config.apiKey.isNotBlank()) { "Add your provider API key in KAIRO settings." }
        val turns = ChatTurn.normalize(history)
        require(turns.isNotEmpty()) { "Nothing to send." }

        val anthropic = config.protocol.equals("anthropic", true)
        val endpoint = ProviderEndpoints.chatCompletions(config.baseUrl, anthropic)
        val messages = JSONArray()
        if (!anthropic) messages.put(JSONObject().put("role", "system").put("content", KairoPersonality.systemInstruction(memoryContext)))
        turns.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val payload = JSONObject().put("model", config.model).put("stream", true).put("messages", messages)
        if (anthropic) payload.put("max_tokens", 2048).put("system", KairoPersonality.systemInstruction(memoryContext))

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 60_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            if (anthropic) {
                setRequestProperty("x-api-key", config.apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            } else setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val detail = runCatching {
                    val json = JSONObject(body)
                    json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: json.optString("message").takeIf { it.isNotBlank() }
                }.getOrNull()
                val reason = when (status) {
                    401, 403 -> "API key rejected"
                    402 -> "insufficient balance/credit"
                    404 -> "model or endpoint not found"
                    408 -> "request timed out"
                    429 -> "rate limit or quota reached"
                    in 500..599 -> "provider service error"
                    else -> "request failed"
                }
                throw ProviderException("HTTP $status ($reason)" + (detail?.let { ": ${it.take(160)}" } ?: ""), status)
            }

            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") {
                        if (data == "[DONE]") break
                        continue
                    }
                    val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    if (anthropic) {
                        when (json.optString("type")) {
                            "content_block_delta" -> {
                                val delta = json.optJSONObject("delta")
                                if (delta?.optString("type") == "text_delta") {
                                    val chunk = delta.optString("text")
                                    if (chunk.isNotEmpty()) emit(chunk)
                                }
                            }
                            "error" -> throw ProviderException(
                                json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                                    ?: "Provider stream error."
                            )
                            "message_stop" -> break
                        }
                    } else {
                        val choice = json.optJSONArray("choices")?.optJSONObject(0)
                        val chunk = choice?.optJSONObject("delta")?.optString("content").orEmpty()
                        if (chunk.isNotEmpty() && chunk != "null") emit(chunk)
                        val finish = choice?.optString("finish_reason")
                        if (finish != null && finish != "null" && finish.isNotBlank()) break
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}

/** Fail over only before the first visible token; never splice two models into one answer. */
class FailoverStreamingEngine(private val providers: List<AIProviderConfig>) : StreamingAIEngine {
    override fun stream(history: List<ChatTurn>, memoryContext: String): Flow<String> = flow {
        if (providers.isEmpty()) throw IllegalStateException("No cloud AI providers are configured.")
        val failures = mutableListOf<String>()
        for (provider in providers) {
            var emitted = false
            try {
                StreamingProviderEngine(provider).stream(history, memoryContext).collect { chunk ->
                    emitted = true
                    emit(chunk)
                }
                if (emitted) return@flow
                failures += "${provider.model}: empty response"
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                if (emitted) throw error
                failures += "${ProviderEndpoints.providerLabel(provider.baseUrl)} (${provider.model}): ${error.message?.take(160) ?: "unknown provider error"}"
            }
        }
        throw IllegalStateException("All ${providers.size} configured AI provider(s) are unavailable. " + failures.joinToString(" | "))
    }
}
