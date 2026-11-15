package com.kairo.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ResearchResult(val title: String, val url: String, val content: String)

/** Tavily-backed live search. The key is encrypted with an Android Keystore AES-GCM key. */
class WebResearchClient(private val context: Context) {
    private companion object { const val MAX_RESPONSE_BYTES = 1_048_576 }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "The search service returned an oversized response." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private val prefs = context.applicationContext.getSharedPreferences("kairo_research", Context.MODE_PRIVATE)
    private val alias = "kairo_research_key"

    fun saveApiKey(value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) { clearApiKey(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun loadApiKey(): String = runCatching {
        val iv = prefs.getString("iv", null) ?: return ""
        val data = prefs.getString("data", null) ?: return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }.getOrDefault("")

    fun clearApiKey() {
        prefs.edit().clear().apply()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
    }

    suspend fun search(query: String): List<ResearchResult> = withContext(Dispatchers.IO) {
        val key = loadApiKey()
        require(key.isNotBlank()) { "Add a Tavily search API key in Settings to enable live research." }
        require(query.isNotBlank() && query.length <= 500) { "Enter a shorter research question." }
        val connection = (URL("https://api.tavily.com/search").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; instanceFollowRedirects = false
            connectTimeout = 8_000; readTimeout = 15_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = JSONObject().put("api_key", key).put("query", query)
                .put("search_depth", "basic").put("max_results", 5)
                .put("include_answer", false).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            coroutineContext.ensureActive()
            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = runCatching {
                    (connection.errorStream ?: connection.inputStream).use { input ->
                        readBounded(input, MAX_RESPONSE_BYTES).toString(Charsets.UTF_8)
                    }
                }.getOrDefault("")
                val detail = when (status) {
                    401, 403 -> "The search key was rejected. Check the Tavily key."
                    429 -> "The search service rate limit or quota was reached."
                    400 -> "The search request was rejected. Try a shorter question."
                    else -> "The search service returned HTTP $status."
                }
                throw IllegalStateException(if (errorBody.isBlank()) detail else detail + " " + errorBody.take(180))
            }
            val responseBytes = connection.inputStream.use { readBounded(it, MAX_RESPONSE_BYTES) }
            require(responseBytes.size <= MAX_RESPONSE_BYTES) { "The search service returned an oversized response." }
            val json = JSONObject(responseBytes.toString(Charsets.UTF_8))
            coroutineContext.ensureActive()
            val results = json.optJSONArray("results") ?: JSONArray()
            (0 until minOf(results.length(), 5)).mapNotNull { i ->
                val item = results.optJSONObject(i) ?: return@mapNotNull null
                val safeUrl = ResearchPresentation.safeHttpsUrl(item.optString("url")) ?: return@mapNotNull null
                ResearchResult(
                    item.optString("title").ifBlank { "Untitled source" }.take(180),
                    safeUrl,
                    item.optString("content").take(1200)
                )
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (timeout: SocketTimeoutException) {
            throw IllegalStateException("Live search timed out. Check your connection and try again.", timeout)
        } finally { connection.disconnect() }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}
