package com.kairo.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/**
 * Downloads a compact, open-weight Qwen3 0.6B INT4 no-think model into the app's private files directory.
 * The model is separate from the APK and can be deleted by clearing app storage.
 */
object LocalModelManager {
    const val MODEL_NAME = "Qwen3 0.6B INT4 No-Think (347 MB)"
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
    private const val MODEL_FILE = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
    private const val LEGACY_MODEL_FILE = "SmolLM2_360M_instruct.litertlm"
    private const val MIN_MODEL_BYTES = 320_000_000L
    private const val MAX_MODEL_BYTES = 380_000_000L

    fun modelFile(context: Context) = File(context.filesDir, MODEL_FILE)
    fun isDownloaded(context: Context): Boolean =
        modelFile(context).let { it.exists() && it.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES }

    suspend fun download(context: Context, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val target = modelFile(context)
            if (isDownloaded(context)) {
                onProgress(100)
                return@withContext target
            }
            target.delete()
            val partial = File(context.filesDir, "$MODEL_FILE.part")
            partial.delete()
            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            try {
                connection.connect()
                val status = connection.responseCode
                check(status in 200..299) { "Model download failed (HTTP $status)." }
                val expected = connection.contentLengthLong
                if (expected > 0L) {
                    check(expected in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
                        "Unexpected model size from the download server."
                    }
                }
                var received = 0L
                connection.inputStream.buffered().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            received += count
                            if (expected > 0L) onProgress(((received * 100) / expected).toInt().coerceIn(0, 99))
                        }
                    }
                }
                check(received in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
                    "The downloaded model is incomplete ($received bytes). Please retry."
                }
                check(partial.renameTo(target)) { "Could not save the model on this device." }
                File(context.filesDir, LEGACY_MODEL_FILE).delete()
                onProgress(100)
                target
            } catch (error: Exception) {
                partial.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        }
}

/**
 * A persistent on-device chat engine. All native engine calls are serialized on
 * one background thread, and the conversation retains its chat history.
 */
class LocalAIEngine(context: Context) : AIEngine, AutoCloseable {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var closed = false

    override suspend fun reply(message: String, memoryContext: String): String = withContext(dispatcher) {
        check(!closed) { "Local AI engine is closed. Reopen KAIRO to use it again." }
        val model = LocalModelManager.modelFile(appContext)
        check(LocalModelManager.isDownloaded(appContext)) {
            "Download the local AI model from Settings first."
        }
        if (engine == null) {
            val candidate = Engine(
                EngineConfig(
                    modelPath = model.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = appContext.cacheDir.absolutePath
                )
            )
            candidate.initialize()
            engine = candidate
            conversation = candidate.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are KAIRO, a helpful, friendly Android assistant. " +
                            "Be concise: answer in 1-3 short sentences unless more detail is requested. Understand English and Telugu-English. Use saved memories when relevant. Never guess personal facts. If a personal fact is unknown, simply say it has not been shared yet. Avoid generic privacy disclaimers."
                    )
                )
            )
        }
        val output = StringBuilder()
        val prompt = if (memoryContext.isBlank()) message else memoryContext + "\n\nUser message: " + message
        checkNotNull(conversation).sendMessageAsync(prompt).collect { chunk ->
            output.append(chunk.toString())
        }
        output.toString().trim().ifBlank { "The local model returned an empty response." }
    }

    override fun close() {
        if (closed) return
        closed = true
        executor.execute {
            runCatching { conversation?.close() }
            conversation = null
            runCatching { engine?.close() }
            engine = null
            dispatcher.close()
        }
    }
}
