package com.kairo.ai

enum class KairoSkillType { OPEN_APP, REMEMBER, FORGET, RECALL, GENERAL }

data class KairoSkillRequest(val type: KairoSkillType, val argument: String = "")

/**
 * Lightweight deterministic skill router. It identifies supported intents;
 * actions that affect the device still require the UI's confirmation flow.
 */
object KairoSkillRegistry {
    private val openAppPattern = Regex("(?i)^(?:please\\s+)?(?:open|launch|start)\\s+(.+)$")
    private val rememberPattern = Regex("(?i)^(?:kairo[, ]*)?remember(?: that)?\\s+(.+)$")
    private val forgetPattern = Regex("(?i)^(?:kairo[, ]*)?forget\\s+(.+)$")

    fun detect(message: String): KairoSkillRequest {
        val text = message.trim()
        if (text.isEmpty()) return KairoSkillRequest(KairoSkillType.GENERAL)
        openAppPattern.find(text)?.let {
            return KairoSkillRequest(KairoSkillType.OPEN_APP, it.groupValues[1].trim())
        }
        rememberPattern.find(text)?.let {
            return KairoSkillRequest(KairoSkillType.REMEMBER, it.groupValues[1].trim())
        }
        forgetPattern.find(text)?.let {
            return KairoSkillRequest(KairoSkillType.FORGET, it.groupValues[1].trim())
        }
        val lower = text.lowercase()
        if (lower in setOf("forget all memories", "clear all memories", "delete all memories")) {
            return KairoSkillRequest(KairoSkillType.FORGET, "*")
        }
        if (lower.contains("what do you remember") || lower in setOf("show memories", "list memories")) {
            return KairoSkillRequest(KairoSkillType.RECALL)
        }
        return KairoSkillRequest(KairoSkillType.GENERAL)
    }
}
