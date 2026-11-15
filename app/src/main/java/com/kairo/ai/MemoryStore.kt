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

/** Pure filtering helper: blank forget requests are intentionally non-destructive. */
internal fun memoriesAfterForget(current: List<String>, query: String): List<String> =
    if (query.isBlank()) current else current.filterNot { it.contains(query, ignoreCase = true) }

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
        // An empty or whitespace-only query must never match every saved memory.
        val current = all()
        val remaining = memoriesAfterForget(current, query)
        save(remaining)
        return current.size - remaining.size
    }

    @Synchronized
    fun clear() = save(emptyList())

    /** Return only memories relevant to this turn, keeping prompts compact. */
    fun context(query: String = ""): String {
        val memories = all()
        if (memories.isEmpty()) return ""
        val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }.toSet()
        val ranked = memories.mapIndexed { index, item ->
            val words = item.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).toSet()
            Triple(item, (terms.intersect(words).size * 4) + (if (query.trim().length >= 4 && item.contains(query.trim(), ignoreCase = true)) 6 else 0), index)
        }.sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third })
        val selected = if (terms.isEmpty()) memories.takeLast(8)
            else ranked.filter { it.second > 0 }.take(8).map { it.first }
        return selected.takeIf { it.isNotEmpty() }
            ?.joinToString("\n", prefix = "Relevant saved memories (use only when helpful):\n") { "- $it" }
            .orEmpty()
    }

    /** Answer known personal facts directly from device memory. */
    fun answerPersonalFact(message: String): String? {
        val nameQuestion = Regex("(?i)\\b(?:what(?:'s| is)\\s+my\\s+name|who\\s+am\\s+i)\\b").containsMatchIn(message)
        if (nameQuestion) {
            val saved = all().firstOrNull { it.startsWith("Name:", ignoreCase = true) }
            return saved?.let { "You're ${it.substringAfter(':').trim()}." }
                ?: "You haven't told me your name yet. Tell me “I'm Prem” and I'll remember it."
        }
        val colourQuestion = Regex("(?i)\\b(?:what(?:'s| is)|do you know)\\s+my\\s+(?:favou?rite)\\s+colou?r\\b").containsMatchIn(message)
        if (colourQuestion) {
            val saved = all().firstOrNull { it.startsWith("Favourite colour:", ignoreCase = true) }
            return saved?.let { "Your favourite colour is ${it.substringAfter(':').trim()}." }
                ?: "You haven't told me your favourite colour yet. Tell me “I like blue colour” and I'll remember it."
        }
        return null
    }

    /** Learn only explicit, low-risk facts and preferences stated by the user. */
    fun autoLearn(message: String): String? {
        val normalized = message.trim()
        val sensitiveMarkers = listOf(
            "password", "passcode", "api key", "secret key", "token", "bank account",
            "credit card", "debit card", "upi id", "otp", "one-time password",
            "medical", "diagnosis", "medication", "health condition", "religion",
            "political party", "politics", "sexual", "private address", "home address",
            "phone number", "email address", "passport", "aadhaar", "pan number"
        )
        val looksSensitive = sensitiveMarkers.any { normalized.contains(it, ignoreCase = true) } ||
            Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b").containsMatchIn(normalized) ||
            Regex("\\b\\d{10,}\\b").containsMatchIn(normalized) ||
            Regex("(?i)https?://").containsMatchIn(normalized)
        if (looksSensitive) return null

        val name = Regex("(?i)^(?:hi[, ]+)?(?:i am|i'm|my name is)\\s+([A-Za-z][A-Za-z'-]{1,35})(?:\\s+and\\b|[.!?]|$)").find(message.trim())
        if (name != null) {
            val value = name.groupValues[1].trim().trimEnd('.')
            val memory = "Name: $value"
            return if (remember(memory)) memory else null
        }

        val favouriteColour = Regex("(?i)\\bmy\\s+favou?rite\\s+colou?r\\s+is\\s+([A-Za-z -]{3,30})").find(message)
            ?: Regex("(?i)\\bi\\s+like\\s+([A-Za-z -]{3,25}?)\\s+colou?r\\b").find(message)
        if (favouriteColour != null) {
            val value = favouriteColour.groupValues[1].trim().trimEnd()
            val memory = "Favourite colour: $value"
            return if (remember(memory)) memory else null
        }

        val patterns = listOf(
            Regex("(?i)\\bI prefer ([^.!?]{3,100})"),
            Regex("(?i)\\bI like ([^.!?]{3,100})"),
            Regex("(?i)\\bmy preferred (?:language|style|format) is ([^.!?]{3,100})"),
            Regex("(?i)\\bI usually use ([^.!?]{3,100})")
        )
        val match = patterns.firstNotNullOfOrNull { it.find(message) } ?: return null
        val value = match.groupValues[1].trim()
        if (value.length > 100 || value.any(Char::isDigit)) return null
        val memory = "Preference: $value"
        return if (remember(memory)) memory else null
    }

    private fun save(items: List<String>) {
        if (items.isEmpty()) {
            prefs.edit().clear().apply()
            // Remove the per-store encryption key as well as ciphertext when all memories are erased.
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
            }
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
