package com.kairo.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, device-local memory with conservative preference-only automatic learning. */
class MemoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kairo_memory", Context.MODE_PRIVATE)
    private val alias = "kairo_memory_key"

    @Synchronized
    fun all(): List<String> = runCatching {
        val iv = prefs.getString("iv", null) ?: return emptyList()
        val data = prefs.getString("data", null) ?: return emptyList()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        val json = JSONArray(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)).toString(Charsets.UTF_8))
        (0 until json.length()).mapNotNull { json.optString(it).takeIf { value -> value.isNotBlank() } }
    }.getOrDefault(emptyList())

    @Synchronized
    fun remember(value: String): Boolean {
        val clean = value.trim().replace(Regex("\\s+"), " ").take(180)
        if (clean.length < 3) return false
        val current = all().toMutableList()
        if (current.any { it.equals(clean, ignoreCase = true) }) return false
        current.add(clean)
        save(current)
        return true
    }

    @Synchronized
    fun forgetMatching(query: String): Int {
        val current = all()
        val remaining = current.filterNot { it.contains(query, ignoreCase = true) }
        save(remaining)
        return current.size - remaining.size
    }

    @Synchronized
    fun clear() = save(emptyList())

    fun context(): String = all().takeIf { it.isNotEmpty() }
        ?.joinToString("\\n", prefix = "Relevant saved memories about the user (use only when relevant):\\n") { "- $it" }
        .orEmpty()

    /** Only explicit low-risk preference wording is eligible for automatic memory. */
    fun autoLearn(message: String): String? {
        val patterns = listOf(
            Regex("(?i)\\bI prefer ([^.!?]{3,100})"),
            Regex("(?i)\\bI like ([^.!?]{3,100})"),
            Regex("(?i)\\bmy preferred (?:language|style|format) is ([^.!?]{3,100})"),
            Regex("(?i)\\bI usually use ([^.!?]{3,100})")
        )
        val match = patterns.firstNotNullOfOrNull { it.find(message) } ?: return null
        val value = match.groupValues[1].trim()
        val blocked = listOf("password", "api key", "address", "phone number", "medical", "diagnosis", "politic", "religion")
        if (blocked.any { value.contains(it, ignoreCase = true) }) return null
        val memory = "Preference: $value"
        return if (remember(memory)) memory else null
    }

    private fun save(items: List<String>) {
        if (items.isEmpty()) {
            prefs.edit().clear().apply()
            return
        }
        val json = JSONArray()
        items.takeLast(100).forEach { json.put(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}
