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
    private companion object {
        const val MAX_ERROR_BYTES = 1_048_576
        const val MAX_STREAM_BYTES = 4_194_304
        const val MAX_STREAM_RESPONSE_BYTES = 1_048_576
        const val MAX_SSE_LINE_CHARS = 65_536
    }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "Provider error response was too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun readBoundedLine(reader: BufferedReader, limit: Int): String? {
        val line = StringBuilder()
        while (true) {
            val value = reader.read()
            if (value < 0) return if (line.isEmpty()) null else line.toString()
            if (value == '\n'.code) return line.toString().removeSuffix("\r")
            require(line.length < limit) { "Provider stream sent an oversized event line." }
            line.append(value.toChar())
        }
    }

    override fun stream(history: List<ChatTurn>, memoryContext: String): Flow<String> = flow {
        require(config.baseUrl.startsWith("https://", ignoreCase = true)) { "Provider URL must use HTTPS." }
        require(config.model.isNotBlank()) { "Enter a model name in KAIRO settings." }
        require(config.apiKey.isNotBlank()) { "Add your provider API key in KAIRO settings." }
        val turns = ChatTurn.normalize(history)
        require(turns.isNotEmpty()) { "Nothing to send." }
        validateProviderRequestSize(turns.joinToString("\n") { it.content }, memoryContext)

        val anthropic = config.protocol.equals("anthropic", true)
        val endpoint = ProviderEndpoints.chatCompletions(config.baseUrl, anthropic)
        val messages = JSONArray()
        if (!anthropic) messages.put(JSONObject().put("role", "system").put("content", KairoPersonality.systemInstruction(memoryContext)))
        turns.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val payload = JSONObject().put("model", config.model).put("stream", true).put("messages", messages)
        if (anthropic) payload.put("max_tokens", 2048).put("system", KairoPersonality.systemInstruction(memoryContext))

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
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
                val body = connection.errorStream?.use { readBounded(it, MAX_ERROR_BYTES).toString(Charsets.UTF_8) }.orEmpty()
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

            var totalStreamBytes = 0
            var emittedResponseBytes = 0
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = readBoundedLine(reader, MAX_SSE_LINE_CHARS) ?: break
                    totalStreamBytes += line.toByteArray(Charsets.UTF_8).size + 1
                    require(totalStreamBytes <= MAX_STREAM_BYTES) { "Provider stream exceeded the response size limit." }
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
                                    if (chunk.isNotEmpty()) {
                                        emittedResponseBytes += chunk.toByteArray(Charsets.UTF_8).size
                                        require(emittedResponseBytes <= MAX_STREAM_RESPONSE_BYTES) { "Provider response was too large." }
                                        emit(chunk)
                                    }
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
                        if (chunk.isNotEmpty() && chunk != "null") {
                            emittedResponseBytes += chunk.toByteArray(Charsets.UTF_8).size
                            require(emittedResponseBytes <= MAX_STREAM_RESPONSE_BYTES) { "Provider response was too large." }
                            emit(chunk)
                        }
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
        val routedProviders = ProviderRouter.order(providers)
        for (provider in routedProviders) {
            var emitted = false
            try {
                StreamingProviderEngine(provider).stream(history, memoryContext).collect { chunk ->
                    emitted = true
                    emit(chunk)
                }
                if (emitted) {
                    ProviderRouter.recordSuccess(provider)
                    return@flow
                }
                val emptyError = IllegalStateException("Provider returned an empty response.")
                ProviderRouter.recordFailure(provider, emptyError)
                failures += "${provider.model}: empty response"
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                ProviderRouter.recordFailure(provider, error)
                if (emitted) throw error
                failures += "${ProviderEndpoints.providerLabel(provider.baseUrl)} (${provider.model}): ${error.message?.take(160) ?: "unknown provider error"}"
            }
        }
        throw IllegalStateException("All ${providers.size} configured AI provider(s) are unavailable. " + failures.joinToString(" | "))
    }
}
