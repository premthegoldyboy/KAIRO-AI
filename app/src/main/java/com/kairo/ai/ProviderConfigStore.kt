package com.kairo.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts provider settings with an AES-GCM key protected by Android Keystore. */
class ProviderConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kairo_provider", Context.MODE_PRIVATE)
    private val alias = "kairo_provider_key"

    fun save(config: AIProviderConfig) = saveAll(listOf(config))

    fun saveAll(configs: List<AIProviderConfig>) {
        require(configs.isNotEmpty()) { "At least one provider is required." }
        require(configs.size <= 4) { "KAIRO supports up to four cloud providers." }
        configs.forEach { config ->
            require(config.baseUrl.length <= 2048 && config.model.length <= 200 && config.apiKey.length <= 4096) {
                "Provider settings exceed the supported size limits."
            }
            require(config.model.isNotBlank() && config.apiKey.isNotBlank()) {
                "Each provider needs a model name and API key."
            }
            ProviderEndpoints.chatCompletions(config.baseUrl, config.protocol.equals("anthropic", ignoreCase = true))
        }
        val plain = org.json.JSONArray().apply {
            configs.forEach { config ->
                put(org.json.JSONObject()
                    .put("baseUrl", config.baseUrl)
                    .put("model", config.model)
                    .put("apiKey", config.apiKey)
                    .put("protocol", config.protocol))
            }
        }.toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(): AIProviderConfig? = loadAll().firstOrNull()

    fun loadAll(): List<AIProviderConfig> {
        val iv = prefs.getString("iv", null) ?: return emptyList()
        val data = prefs.getString("data", null) ?: return emptyList()
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            val plain = cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)).toString(Charsets.UTF_8)
            if (plain.trimStart().startsWith("[")) {
                val array = org.json.JSONArray(plain)
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { item ->
                        AIProviderConfig(item.optString("baseUrl"), item.optString("model"), item.optString("apiKey"), item.optString("protocol", "openai"))
                            .takeIf { it.baseUrl.isNotBlank() && it.model.isNotBlank() && it.apiKey.isNotBlank() }
                    }
                }
            } else {
                val parts = plain.split('\n', limit = 3)
                if (parts.size == 3) listOf(AIProviderConfig(parts[0], parts[1], parts[2])) else emptyList()
            }
        }.getOrDefault(emptyList())
    }

    fun clear() {
        prefs.edit().clear().apply()
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
