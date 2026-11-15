package com.kairo.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ConversationEntry(val text: String, val fromKairo: Boolean)

/** Bounded, encrypted, device-local chat history. */
class ConversationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kairo_conversation", Context.MODE_PRIVATE)
    private val alias = "kairo_conversation_key"

    @Synchronized
    fun load(): List<ConversationEntry> = runCatching {
        val iv = prefs.getString("iv", null) ?: return emptyList()
        val data = prefs.getString("data", null) ?: return emptyList()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        val array = JSONArray(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)).toString(Charsets.UTF_8))
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                val text = item.optString("text").take(2_000)
                if (text.isBlank()) null else ConversationEntry(text, item.optBoolean("fromKairo"))
            }
        }.takeLast(MAX_ENTRIES)
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(entries: List<ConversationEntry>) {
        val recent = entries.takeLast(MAX_ENTRIES)
        if (recent.isEmpty()) {
            clear()
            return
        }
        val array = JSONArray()
        recent.forEach { entry ->
            array.put(JSONObject().put("text", entry.text.take(2_000)).put("fromKairo", entry.fromKairo))
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
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

    private companion object {
        const val MAX_ENTRIES = 40
    }
}
